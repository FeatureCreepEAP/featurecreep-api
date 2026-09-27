package featurecreep.attach;

import java.io.IOException;

/** AIX HotSpot attach provider. */
public final class AttachAix extends AttachUnix {
    private static volatile UnixAttachSession session;

    private AttachAix() {}

    public static void attach() throws IOException {
        attach(Math.toIntExact(ProcessHandle.current().pid()));
    }

    public static synchronized void attach(int pid) throws IOException {
        detach();
        session = UnixAttachSession.openAix(pid);
    }

    public static void loadAgent(String agent, String options) throws IOException {
        current().loadAgent(agent, options);
    }

    public static synchronized void detach() {
        if (session != null) session.close();
        session = null;
    }

    private static UnixAttachSession current() throws IOException {
        UnixAttachSession value = session;
        if (value == null) throw new IOException("Detached from target VM");
        return value;
    }
}
