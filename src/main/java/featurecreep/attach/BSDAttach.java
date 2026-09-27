package featurecreep.attach;

import java.io.IOException;

/** macOS/BSD HotSpot attach provider. */
public final class BSDAttach implements AutoCloseable {
    private final UnixAttachSession session;

    public BSDAttach(String vmid) throws IOException {
        this(Integer.parseInt(vmid));
    }

    public BSDAttach(int pid) throws IOException {
        session = UnixAttachSession.openBsd(pid);
    }

    public void loadAgent(String agent, String options) throws IOException {
        session.loadAgent(agent, options);
    }

    public void detach() {
        session.close();
    }

    @Override
    public void close() {
        detach();
    }
}
