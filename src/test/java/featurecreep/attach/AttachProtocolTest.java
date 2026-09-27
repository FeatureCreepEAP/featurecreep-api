package featurecreep.attach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class AttachProtocolTest {
    @Test
    void readsHotSpotCompletionStatusWithoutReadingAhead() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream("0\nreturn code: 0\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, UnixAttachSession.readInt(in));
        assertEquals('r', in.read());
    }

    @Test
    void rejectsMalformedCompletionStatus() {
        assertThrows(IOException.class,
                () -> UnixAttachSession.readInt(new ByteArrayInputStream("nope\n".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void publicAttachRejectsBadPidBeforeNativeCode() {
        assertThrows(IllegalArgumentException.class, () -> Attach.attach(0, "agent.jar", null));
    }
}
