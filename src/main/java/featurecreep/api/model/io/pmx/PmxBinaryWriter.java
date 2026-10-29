package featurecreep.api.model.io.pmx;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

final class PmxBinaryWriter {

    static final int INDEX_SIZE = 4;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteBuffer scratch = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);

    void bytes(byte[] data) {
        out.writeBytes(data);
    }

    void u8(int value) {
        out.write(value);
    }

    void s32(int value) {
        scratch.clear();
        scratch.putInt(value);
        out.writeBytes(scratch.array());
    }

    void f32(float value) {
        scratch.clear();
        scratch.putFloat(value);
        out.writeBytes(scratch.array());
    }

    void floats(float[] values) {
        for (float v : values) {
            f32(v);
        }
    }

    /** Writes a PMX text as length-prefixed UTF-16LE. */
    void text(String value) {
        byte[] raw = value.getBytes(StandardCharsets.UTF_16LE);
        s32(raw.length);
        out.writeBytes(raw);
    }

    void index(int value) {
        s32(value);
    }

    byte[] toByteArray() {
        return out.toByteArray();
    }
}
