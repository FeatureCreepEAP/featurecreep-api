package featurecreep.attach;

import featurecreep.attach.panama.PosixNative;
import featurecreep.attach.panama.SolarisNative;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Solaris HotSpot attach provider using doors and Java 25 FFM. */
public final class AttachSolaris extends AttachUnix {
    private static final long TIMEOUT_MILLIS = 10_000;
    private static volatile int doorFd = -1;

    private AttachSolaris() {}

    public static void attach() throws IOException {
        attach(Math.toIntExact(ProcessHandle.current().pid()));
    }

    public static synchronized void attach(int pid) throws IOException {
        detach();
        if (pid <= 0) throw new IllegalArgumentException("pid must be positive");
        Path door = Paths.get("/tmp", ".java_pid" + pid);
        try {
            doorFd = SolarisNative.openDoor(door.toString());
            return;
        } catch (java.io.FileNotFoundException ignored) {
            // Start the target attach listener below.
        }

        Path attachFile = createAttachFile(pid);
        try {
            PosixNative.sendQuit(pid);
            long deadline = System.nanoTime() + TIMEOUT_MILLIS * 1_000_000L;
            boolean resent = false;
            while (System.nanoTime() < deadline) {
                try {
                    doorFd = SolarisNative.openDoor(door.toString());
                    return;
                } catch (java.io.FileNotFoundException ignored) {
                    // Keep waiting.
                }
                if (!resent && System.nanoTime() + (TIMEOUT_MILLIS / 2) * 1_000_000L >= deadline) {
                    PosixNative.sendQuit(pid);
                    resent = true;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted waiting for Solaris attach door", ex);
                }
            }
            throw new IOException("Target JVM " + pid + " did not create attach door " + door);
        } finally {
            Files.deleteIfExists(attachFile);
        }
    }

    public static synchronized void detach() throws IOException {
        if (doorFd >= 0) {
            PosixNative.close(doorFd);
            doorFd = -1;
        }
    }

    static InputStream execute(String command, String... args) throws IOException {
        if (doorFd < 0) throw new IOException("Detached from target VM");
        if (args.length > 3) throw new IllegalArgumentException("HotSpot attach protocol accepts at most 3 arguments");

        ByteArrayOutputStream request = new ByteArrayOutputStream();
        writeField(request, "1");
        writeField(request, command);
        for (int i = 0; i < 3; i++) writeField(request, i < args.length && args[i] != null ? args[i] : "");

        SolarisNative.DoorResult result = SolarisNative.doorCall(doorFd, request.toByteArray());
        if (result.status() != 0) {
            throw new IOException("Solaris attach door returned status " + result.status());
        }
        if (result.responseFd() < 0) throw new IOException("Solaris attach door returned no response descriptor");
        return new NativeFdInputStream(result.responseFd());
    }

    public static void loadAgent(String agent, String options) throws IOException {
        if (agent == null || agent.isBlank()) throw new IllegalArgumentException("agent cannot be blank");
        String payload = options == null ? agent : agent + "=" + options;
        try (InputStream in = execute("load", "instrument", "false", payload);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String response = reader.readLine();
            if (response == null || response.isBlank()) return;
            if (response.startsWith("return code: ")) {
                int code = Integer.parseInt(response.substring("return code: ".length()).trim());
                if (code != 0) throw new IOException("Agent_OnAttach failed with return code " + code);
            } else if (!"0".equals(response.trim())) {
                throw new IOException("Agent load failed: " + response);
            }
        }
    }

    private static Path createAttachFile(int pid) throws IOException {
        Path primary = Paths.get("/proc", Integer.toString(pid), "cwd", ".attach_pid" + pid);
        try {
            if (Files.isDirectory(primary.getParent())) return Files.createFile(primary);
        } catch (IOException ignored) {
        }
        Path fallback = Paths.get("/tmp", ".attach_pid" + pid);
        Files.deleteIfExists(fallback);
        return Files.createFile(fallback);
    }

    private static void writeField(ByteArrayOutputStream out, String value) {
        if (value != null && value.indexOf('\0') >= 0) throw new IllegalArgumentException("NUL in attach argument");
        if (value != null) out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
        out.write(0);
    }

    private static final class NativeFdInputStream extends InputStream {
        private int fd;

        NativeFdInputStream(int fd) {
            this.fd = fd;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int read = read(one, 0, 1);
            return read < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (fd < 0) return -1;
            java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
            return PosixNative.read(fd, bytes, offset, length);
        }

        @Override
        public void close() throws IOException {
            if (fd >= 0) {
                int old = fd;
                fd = -1;
                PosixNative.close(old);
            }
        }
    }
}
