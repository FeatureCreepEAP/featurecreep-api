package featurecreep.attach;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Plain target JVM used to prove that no jdk.attach enabling flags are needed. */
public final class AttachTargetMain {
    private AttachTargetMain() {}

    public static void main(String[] args) throws Exception {
        Path ready = Path.of(args[0]);
        Path attached = Path.of(args[1]);
        Files.writeString(ready, Long.toString(ProcessHandle.current().pid()), StandardCharsets.UTF_8);
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        while (!Files.exists(attached) && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        if (!Files.exists(attached)) System.exit(2);
    }
}
