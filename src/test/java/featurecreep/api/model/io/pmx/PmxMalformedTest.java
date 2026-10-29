package featurecreep.api.model.io.pmx;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecreep.api.model.ModelFormatException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PmxMalformedTest {

    private static final int VERTEX_INDEX_SIZE_OFFSET = 11;

    private static byte[] valid() throws ModelFormatException {
        return new PmxWriter().write(PmxFixtures.handBuilt(2.0f));
    }

    @Test
    void rejectsEmptyInput() {
        assertThrows(ModelFormatException.class, () -> new PmxReader().read(new byte[0], "empty.pmx"));
    }

    @Test
    void rejectsMissingSignature() throws ModelFormatException {
        byte[] data = valid();
        data[0] = 'X';

        assertThrows(ModelFormatException.class, () -> new PmxReader().read(data, "signature.pmx"));
    }

    @Test
    void rejectsUnsupportedVersion() throws ModelFormatException {
        byte[] data = valid();
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putFloat(4, 3.0f);

        assertThrows(ModelFormatException.class, () -> new PmxReader().read(data, "version.pmx"));
    }

    @Test
    void rejectsIllegalIndexSize() throws ModelFormatException {
        byte[] data = valid();
        data[VERTEX_INDEX_SIZE_OFFSET] = 3;

        ModelFormatException error = assertThrows(ModelFormatException.class,
                () -> new PmxReader().read(data, "index.pmx"));
        assertTrue(error.getMessage().contains("index size 3"));
    }

    @Test
    void rejectsEveryTruncation() throws ModelFormatException {
        byte[] data = valid();
        for (int length = 0; length < data.length; length++) {
            byte[] cut = Arrays.copyOf(data, length);
            assertThrows(ModelFormatException.class, () -> new PmxReader().read(cut, "cut.pmx"),
                    "length " + length + " of " + data.length);
        }
    }

    @Test
    void rejectsNonPmxText() {
        byte[] data = "PMD not a pmx file".getBytes(StandardCharsets.US_ASCII);

        assertThrows(ModelFormatException.class, () -> new PmxReader().read(data, "text.pmx"));
    }
}
