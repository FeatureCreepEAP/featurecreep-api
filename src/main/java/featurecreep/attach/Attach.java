package featurecreep.attach;

import featurecreep.api.lowlevel.OS;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * FeatureCreep's direct HotSpot attach implementation.
 *
 * <p>This deliberately does not call {@code jdk.attach}. On Unix-like systems it
 * speaks the HotSpot attach protocol directly; on Windows it uses the JVM's
 * enqueue-operation entry point. Native OS calls are made through Java 25 FFM.</p>
 */
public final class Attach {
    private Attach() {}

    /** Attach an agent to this JVM using the direct FeatureCreep attach path. */
    public static void attach(String agent, String args) {
        try {
            attach(ProcessHandle.current().pid(), agent, args);
        } catch (IOException ex) {
            throw new UncheckedIOException("FeatureCreep attach failed", ex);
        }
    }

    /**
     * Attach an agent to an arbitrary target JVM without using {@code jdk.attach}.
     *
     * @param pid target process ID
     * @param agent path to the Java agent JAR
     * @param args agentmain arguments, or {@code null}
     */
    public static void attach(long pid, String agent, String args) throws IOException {
        if (pid <= 0 || pid > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid process identifier: " + pid);
        if (agent == null || agent.isBlank()) throw new IllegalArgumentException("agent cannot be blank");
        int targetPid = (int) pid;

        switch (OS.current()) {
            case LINUX -> {
                try {
                    AttachLinux.attach(targetPid);
                    AttachLinux.loadAgent(agent, args);
                } finally {
                    AttachLinux.detach();
                }
            }
            case AIX -> {
                try {
                    AttachAix.attach(targetPid);
                    AttachAix.loadAgent(agent, args);
                } finally {
                    AttachAix.detach();
                }
            }
            case SOLARIS -> {
                try {
                    AttachSolaris.attach(targetPid);
                    AttachSolaris.loadAgent(agent, args);
                } finally {
                    AttachSolaris.detach();
                }
            }
            case MAC, BSD -> {
                try (BSDAttach bsd = new BSDAttach(targetPid)) {
                    bsd.loadAgent(agent, args);
                }
            }
            case WINDOWS -> {
                try (AttachWindows windows = new AttachWindows(targetPid)) {
                    windows.loadAgent(agent, args);
                }
            }
            default -> throw new IOException("Direct HotSpot attach is unsupported on " + System.getProperty("os.name"));
        }
    }
}
