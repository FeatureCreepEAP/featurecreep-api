package featurecreep.attach;

import java.io.IOException;

/** Linux HotSpot attach provider using the native attach socket protocol. */
public final class AttachLinux extends AttachUnix {
    private static volatile UnixAttachSession session;

    private AttachLinux() {}

    public static void attach() throws IOException {
        attach(Math.toIntExact(ProcessHandle.current().pid()));
    }

    public static synchronized void attach(int pid) throws IOException {
        detach();
        session = UnixAttachSession.openLinux(pid);
    }

    public static void loadAgent(String agent, String options) throws IOException {
        current().loadAgent(agent, options);
    }

    public static synchronized void detach() {
        if (session != null) session.close();
        session = null;
    }

    static int readInt(java.io.InputStream in) throws IOException {
        return UnixAttachSession.readInt(in);
    }

    private static UnixAttachSession current() throws IOException {
        UnixAttachSession value = session;
        if (value == null) throw new IOException("Detached from target VM");
        return value;
    }
}
