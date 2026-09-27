package featurecreep.attach;

import featurecreep.api.lowlevel.OS;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAttachIntegrationTest {
    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void selfAttachDoesNotUseJdkAttachAllowAttachSelf() throws Exception {
        Assumptions.assumeTrue(OS.current() != OS.UNKNOWN, "unsupported OS");
        assumeHotSpot();
        Path dir = Files.createTempDirectory("featurecreep-self-attach-test-");
        Path marker = dir.resolve("self-agent-attached.txt");
        Path agent = createAgentJar(dir.resolve("self-attach-test-agent.jar"));

        // The Surefire JVM is not given -Djdk.attach.allowAttachSelf. This call
        // exercises FeatureCreep's direct VM protocol rather than VirtualMachine.attach.
        System.clearProperty("jdk.attach.allowAttachSelf");
        assertFalse(Boolean.getBoolean("jdk.attach.allowAttachSelf"));
        Attach.attach(ProcessHandle.current().pid(), agent.toString(), marker.toString());
        waitForFile(marker, ProcessHandle.current(), Duration.ofSeconds(5));
        String result = Files.readString(marker, StandardCharsets.UTF_8);
        assertTrue(result.contains("agentmain=" + marker));
        assertTrue(result.contains("instrumentation=true"));
    }

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    void attachesAgentToPlainChildJvmWithoutJdkAttachFlags() throws Exception {
        Assumptions.assumeTrue(OS.current() != OS.UNKNOWN, "unsupported OS");
        assumeHotSpot();
        Path dir = Files.createTempDirectory("featurecreep-attach-test-");
        Path ready = dir.resolve("ready.txt");
        Path marker = dir.resolve("agent-attached.txt");
        Path agent = createAgentJar(dir.resolve("attach-test-agent.jar"));

        List<String> command = childCommand(ready, marker);
        assertFalse(command.stream().anyMatch(s -> s.contains("jdk.attach.allowAttachSelf")));
        assertFalse(command.stream().anyMatch(s -> s.contains("EnableDynamicAgentLoading")));
        assertFalse(command.stream().anyMatch(s -> s.contains("StartAttachListener")));

        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(dir.resolve("target.log").toFile());
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        builder.environment().remove("_JAVA_OPTIONS");
        Process child = builder.start();
        try {
            waitForFile(ready, child, Duration.ofSeconds(10));
            long pid = Long.parseLong(Files.readString(ready, StandardCharsets.UTF_8).trim());
            assertEquals(child.pid(), pid);

            Attach.attach(pid, agent.toString(), marker.toString());
            waitForFile(marker, child, Duration.ofSeconds(10));

            String result = Files.readString(marker, StandardCharsets.UTF_8);
            assertTrue(result.contains("agentmain=" + marker));
            assertTrue(result.contains("instrumentation=true"));
        } finally {
            child.destroy();
            if (!child.waitFor(2, TimeUnit.SECONDS)) child.destroyForcibly();
        }
    }

    private static void assumeHotSpot() {
        String vm = System.getProperty("java.vm.name", "").toLowerCase(java.util.Locale.ROOT);
        Assumptions.assumeTrue(vm.contains("hotspot") || vm.contains("openjdk") || vm.contains("server vm"),
                "direct attach test requires HotSpot-compatible VM");
    }

    private static List<String> childCommand(Path ready, Path marker) throws Exception {
        String exe = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", exe);
        List<String> command = new ArrayList<>();
        command.add(java.toString());
        // Deliberately no -Djdk.attach.allowAttachSelf, -XX:+StartAttachListener,
        // -XX:+EnableDynamicAgentLoading, or --enable-native-access flag here.
        command.add("-cp");
        Path testClasses = Path.of(AttachTargetMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        command.add(testClasses.toString());
        command.add(AttachTargetMain.class.getName());
        command.add(ready.toString());
        command.add(marker.toString());
        return command;
    }

    private static Path createAgentJar(Path jar) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue("Agent-Class", AttachTestAgent.class.getName());
        attrs.putValue("Can-Redefine-Classes", "true");
        attrs.putValue("Can-Retransform-Classes", "true");

        String resource = AttachTestAgent.class.getName().replace('.', '/') + ".class";
        try (InputStream classBytes = AttachTestAgent.class.getClassLoader().getResourceAsStream(resource);
             JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            if (classBytes == null) throw new IOException("Cannot locate test agent class: " + resource);
            out.putNextEntry(new JarEntry(resource));
            classBytes.transferTo(out);
            out.closeEntry();
        }
        return jar;
    }

    private static void waitForFile(Path file, Process process, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(file)) return;
            if (!process.isAlive()) {
                throw new AssertionError("Target JVM exited early with code " + process.exitValue());
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for " + file);
    }

    private static void waitForFile(Path file, ProcessHandle process, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(file)) return;
            if (!process.isAlive()) throw new AssertionError("Target JVM exited early");
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for " + file);
    }
}
