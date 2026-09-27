package featurecreep.attach;

import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Agent payload used by {@link AgentAttachIntegrationTest}. */
public final class AttachTestAgent {
    private AttachTestAgent() {}

    public static void agentmain(String args, Instrumentation instrumentation) throws Exception {
        if (args == null || args.isBlank()) throw new IllegalArgumentException("marker path missing");
        String value = "agentmain=" + args + System.lineSeparator()
                + "instrumentation=" + (instrumentation != null) + System.lineSeparator();
        Files.writeString(Path.of(args), value, StandardCharsets.UTF_8);
    }
}
