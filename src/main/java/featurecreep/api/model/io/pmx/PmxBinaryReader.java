package featurecreep.api.model.io.pmx;

import featurecreep.api.model.ModelFormatException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

final class PmxBinaryReader {

    private final ByteBuffer buf;
    private final String source;

    PmxBinaryReader(byte[] data, String source) {
        this.buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        this.source = source;
    }

    int position() {
        return buf.position();
    }

    int remaining() {
        return buf.remaining();
    }

    ModelFormatException fail(String message) {
        return new ModelFormatException(source + " at byte " + buf.position() + ": " + message);
    }

    void skip(int bytes) throws ModelFormatException {
        require(bytes);
        buf.position(buf.position() + bytes);
    }

    byte[] bytes(int count) throws ModelFormatException {
        require(count);
        byte[] out = new byte[count];
        buf.get(out);
        return out;
    }

    int u8() throws ModelFormatException {
        require(1);
        return buf.get() & 0xFF;
    }

    int s32() throws ModelFormatException {
        require(4);
        return buf.getInt();
    }

    float f32() throws ModelFormatException {
        require(4);
        return buf.getFloat();
    }

    float[] floats(int count) throws ModelFormatException {
        require(count * 4);
        float[] out = new float[count];
        for (int i = 0; i < count; i++) {
            out[i] = buf.getFloat();
        }
        return out;
    }

    /** Reads a count that must be non-negative and cannot exceed the bytes left, given the minimum size of one element. */
    int count(int minElementBytes) throws ModelFormatException {
        int count = s32();
        if (count < 0 || (long) count * minElementBytes > buf.remaining()) {
            throw fail("element count " + count + " exceeds the remaining data");
        }
        return count;
    }

    /** Reads an index whose width comes from the header. Signed indices use -1 for nil, unsigned ones are vertex indices. */
    int index(int size, boolean signed) throws ModelFormatException {
        require(size);
        return switch (size) {
            case 1 -> signed ? buf.get() : buf.get() & 0xFF;
            case 2 -> signed ? buf.getShort() : buf.getShort() & 0xFFFF;
            case 4 -> buf.getInt();
            default -> throw fail("unsupported index size " + size);
        };
    }

    String text(int encoding) throws ModelFormatException {
        int length = s32();
        if (length < 0) {
            throw fail("negative text length");
        }
        byte[] raw = bytes(length);
        return switch (encoding) {
            case 0 -> new String(raw, StandardCharsets.UTF_16LE);
            case 1 -> new String(raw, StandardCharsets.UTF_8);
            default -> throw fail("unknown text encoding " + encoding);
        };
    }

    private void require(int bytes) throws ModelFormatException {
        if (bytes < 0 || bytes > buf.remaining()) {
            throw fail("unexpected end of data, needed " + bytes + " bytes");
        }
    }
}
