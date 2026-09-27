package featurecreep.attach;

import featurecreep.attach.panama.PosixNative;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/** Shared HotSpot Unix-domain-socket attach protocol implementation. */
final class UnixAttachSession implements AutoCloseable {
    private static final int ATTACH_ERROR_BADVERSION = 101;
    private static final String PROTOCOL_VERSION = "1";

    private final int pid;
    private final Path socketPath;
    private volatile boolean closed;

    private UnixAttachSession(int pid, Path socketPath) {
        this.pid = pid;
        this.socketPath = socketPath;
    }

    static UnixAttachSession openLinux(int pid) throws IOException {
        validatePid(pid);
        int namespacePid = linuxNamespacePid(pid);
        Path socket = Paths.get("/proc", Integer.toString(pid), "root", "tmp", ".java_pid" + namespacePid);
        Path primaryAttach = Paths.get("/proc", Integer.toString(pid), "cwd", ".attach_pid" + namespacePid);
        Path fallbackAttach = pid == namespacePid
                ? Paths.get("/tmp", ".attach_pid" + namespacePid)
                : Paths.get("/proc", Integer.toString(pid), "root", "tmp", ".attach_pid" + namespacePid);
        return open(pid, socket, primaryAttach, fallbackAttach, Duration.ofSeconds(10));
    }

    static UnixAttachSession openAix(int pid) throws IOException {
        validatePid(pid);
        Path socket = Paths.get("/tmp", ".java_pid" + pid);
        Path primaryAttach = Paths.get("/proc", Integer.toString(pid), "cwd", ".attach_pid" + pid);
        Path fallbackAttach = Paths.get("/tmp", ".attach_pid" + pid);
        return open(pid, socket, primaryAttach, fallbackAttach, Duration.ofSeconds(10));
    }

    static UnixAttachSession openBsd(int pid) throws IOException {
        validatePid(pid);
        String configured = System.getenv("TMPDIR");
        Path tmp = Paths.get(configured == null || configured.isBlank() ? "/tmp" : configured);
        Path socket = tmp.resolve(".java_pid" + pid);
        Path attach = tmp.resolve(".attach_pid" + pid);
        return open(pid, socket, attach, attach, Duration.ofSeconds(30));
    }

    private static UnixAttachSession open(int pid, Path socket, Path primaryAttach, Path fallbackAttach,
            Duration timeout) throws IOException {
        if (!Files.exists(socket)) {
            Path attach = createAttachFile(primaryAttach, fallbackAttach);
            try {
                PosixNative.sendQuit(pid);
                waitForSocket(pid, socket, timeout);
            } finally {
                Files.deleteIfExists(attach);
            }
        }

        // Fail early on permission/path problems rather than at the first command.
        try (SocketChannel probe = connect(socket)) {
            if (!probe.isConnected()) throw new IOException("Could not connect to attach socket " + socket);
        }
        return new UnixAttachSession(pid, socket);
    }

    private static Path createAttachFile(Path primary, Path fallback) throws IOException {
        IOException first = null;
        try {
            Path parent = primary.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                return Files.createFile(primary);
            }
        } catch (IOException ex) {
            first = ex;
        }
        try {
            Files.deleteIfExists(fallback);
            return Files.createFile(fallback);
        } catch (IOException ex) {
            if (first != null) ex.addSuppressed(first);
            throw ex;
        }
    }

    private static void waitForSocket(int pid, Path socket, Duration timeout) throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        long resendAt = System.nanoTime() + timeout.toNanos() / 2;
        boolean resent = false;
        long sleepMillis = 25;
        while (System.nanoTime() < deadline) {
            if (Files.exists(socket)) return;
            if (!resent && System.nanoTime() >= resendAt) {
                PosixNative.sendQuit(pid);
                resent = true;
            }
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for target JVM attach socket", ex);
            }
            sleepMillis = Math.min(250, sleepMillis + 25);
        }
        throw new IOException("Target JVM " + pid + " did not create attach socket " + socket
                + " within " + timeout.toMillis() + " ms");
    }

    InputStream execute(String command, String... arguments) throws IOException {
        if (closed) throw new IOException("Detached from target VM " + pid);
        if (arguments.length > 3) throw new IllegalArgumentException("HotSpot attach protocol accepts at most 3 arguments");
        rejectNul(command);
        for (String argument : arguments) rejectNul(argument);

        SocketChannel channel = connect(socketPath);
        try {
            OutputStream out = Channels.newOutputStream(channel);
            writeField(out, PROTOCOL_VERSION);
            writeField(out, command);
            for (int i = 0; i < 3; i++) {
                writeField(out, i < arguments.length && arguments[i] != null ? arguments[i] : "");
            }
            out.flush();
            try {
                channel.shutdownOutput();
            } catch (UnsupportedOperationException ignored) {
                // Some Unix-domain SocketChannel implementations do not expose half-close.
            }

            InputStream raw = Channels.newInputStream(channel);
            InputStream response = new FilterInputStream(raw) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        channel.close();
                    }
                }
            };

            int completionStatus;
            try {
                completionStatus = readInt(response);
            } catch (IOException ex) {
                response.close();
                throw ex;
            }
            if (completionStatus != 0) {
                String message = readRemainder(response);
                response.close();
                if (completionStatus == ATTACH_ERROR_BADVERSION) {
                    throw new IOException("Attach protocol mismatch with target VM");
                }
                String prefix = "load".equals(command) ? "Failed to load agent" : "Attach command failed";
                throw new IOException(prefix + " (status=" + completionStatus + ")"
                        + (message.isBlank() ? "" : ": " + message));
            }
            return response;
        } catch (Throwable ex) {
            if (channel.isOpen()) channel.close();
            if (ex instanceof IOException ioe) throw ioe;
            if (ex instanceof RuntimeException runtime) throw runtime;
            throw new IOException("Attach command failed", ex);
        }
    }

    void loadAgent(String agentJar, String options) throws IOException {
        if (agentJar == null || agentJar.isBlank()) throw new IllegalArgumentException("agentJar cannot be blank");
        String payload = options == null ? agentJar : agentJar + "=" + options;
        try (InputStream in = execute("load", "instrument", "false", payload);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String result = reader.readLine();
            if (result == null || result.isBlank()) {
                // Some HotSpot versions only return the command completion status on success.
                return;
            }
            if (result.startsWith("return code: ")) {
                int code;
                try {
                    code = Integer.parseInt(result.substring("return code: ".length()).trim());
                } catch (NumberFormatException ex) {
                    throw new IOException("Malformed agent response: " + result, ex);
                }
                if (code != 0) throw new IOException(agentError(code));
                return;
            }
            if (!"0".equals(result.trim())) {
                throw new IOException("Agent load failed: " + result);
            }
        }
    }

    @Override
    public void close() {
        closed = true;
    }

    Path socketPath() {
        return socketPath;
    }

    private static SocketChannel connect(Path socket) throws IOException {
        SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
        try {
            channel.connect(UnixDomainSocketAddress.of(socket));
            return channel;
        } catch (IOException | RuntimeException ex) {
            channel.close();
            throw ex;
        }
    }

    static int readInt(InputStream in) throws IOException {
        StringBuilder value = new StringBuilder();
        for (;;) {
            int b = in.read();
            if (b == -1 || b == '\n') break;
            value.append((char) b);
        }
        if (value.isEmpty()) throw new IOException("Premature EOF reading attach status");
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ex) {
            throw new IOException("Non-numeric attach status: " + value, ex);
        }
    }

    private static String readRemainder(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    private static void writeField(OutputStream out, String value) throws IOException {
        String actual = value == null ? "" : value;
        rejectNul(actual);
        out.write(actual.getBytes(StandardCharsets.UTF_8));
        out.write(0);
    }

    private static void rejectNul(String value) {
        if (value != null && value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Attach arguments cannot contain NUL characters");
        }
    }

    private static int linuxNamespacePid(int pid) throws IOException {
        Path status = Paths.get("/proc", Integer.toString(pid), "status");
        if (!Files.isRegularFile(status)) return pid;
        for (String line : Files.readAllLines(status, StandardCharsets.UTF_8)) {
            if (line.startsWith("NSpid:")) {
                String[] values = line.substring("NSpid:".length()).trim().split("\\s+");
                if (values.length > 0) {
                    try {
                        return Integer.parseInt(values[values.length - 1]);
                    } catch (NumberFormatException ex) {
                        throw new IOException("Invalid NSpid in " + status + ": " + line, ex);
                    }
                }
            }
        }
        return pid;
    }

    private static void validatePid(int pid) {
        if (pid <= 0) throw new IllegalArgumentException("pid must be positive");
    }

    private static String agentError(int code) {
        return switch (code) {
            case -4 -> "Insufficient memory while loading agent";
            case 100 -> "Agent JAR not found or missing Agent-Class";
            case 101 -> "Unable to add agent JAR to the target class path";
            case 102 -> "Agent loaded but agentmain failed to initialize";
            default -> "Agent_OnAttach failed with return code " + code;
        };
    }
}
