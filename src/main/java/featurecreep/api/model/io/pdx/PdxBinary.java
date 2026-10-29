package featurecreep.api.model.io.pdx;

import featurecreep.api.model.ModelFormatException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes the PDX binary container: {@code @@b@} header, then objects and properties, little-endian. */
final class PdxBinary {

    static final String ROOT = "File";

    private PdxBinary() {
    }

    static boolean sniff(byte[] header) {
        return header.length >= 4 && header[0] == '@' && header[1] == '@' && header[2] == 'b' && header[3] == '@';
    }

    static PdxElement parse(byte[] data, String source) throws ModelFormatException {
        if (!sniff(data)) {
            throw new ModelFormatException(source + ": missing @@b@ header");
        }
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        buf.position(4);
        PdxElement root = new PdxElement(ROOT, 0);
        List<PdxElement> stack = new ArrayList<>();
        stack.add(root);
        while (buf.hasRemaining()) {
            int at = buf.position();
            byte marker = buf.get();
            if (marker == '[') {
                buf.position(at);
                int depth = 0;
                while (buf.hasRemaining() && buf.get(buf.position()) == '[') {
                    buf.get();
                    depth++;
                }
                String name = readCString(buf, source, at);
                if (depth > stack.size()) {
                    throw error(source, at, "object depth " + depth + " has no parent");
                }
                while (stack.size() > depth) {
                    stack.remove(stack.size() - 1);
                }
                PdxElement element = new PdxElement(name, depth);
                stack.get(depth - 1).children.add(element);
                stack.add(element);
            } else if (marker == '!') {
                need(buf, 1, source, at);
                int nameLength = buf.get();
                if (nameLength < 0) {
                    throw error(source, at, "negative property name length");
                }
                String propName = readString(buf, nameLength, source, at);
                Object value = readData(buf, source);
                stack.get(stack.size() - 1).props.put(propName, value);
            } else {
                throw error(source, at, "expected '[' or '!' but found 0x" + Integer.toHexString(marker & 0xff));
            }
        }
        return root;
    }

    private static Object readData(ByteBuffer buf, String source) throws ModelFormatException {
        int at = buf.position();
        need(buf, 5, source, at);
        byte type = buf.get();
        int count = buf.getInt();
        if (count < 0) {
            throw error(source, at, "negative data count");
        }
        switch (type) {
            case 'i' -> {
                need(buf, 4L * count, source, at);
                int[] values = new int[count];
                for (int i = 0; i < count; i++) {
                    values[i] = buf.getInt();
                }
                return values;
            }
            case 'f' -> {
                need(buf, 4L * count, source, at);
                float[] values = new float[count];
                for (int i = 0; i < count; i++) {
                    values[i] = buf.getFloat();
                }
                return values;
            }
            case 's' -> {
                if (count != 1) {
                    throw error(source, at, "string arrays are not supported");
                }
                need(buf, 4, source, at);
                int length = buf.getInt();
                if (length < 1) {
                    throw error(source, at, "invalid string length");
                }
                String value = readString(buf, length, source, at);
                return value.endsWith("\0") ? value.substring(0, value.length() - 1) : value;
            }
            default -> throw error(source, at, "unknown data type " + (char) type);
        }
    }

    private static String readCString(ByteBuffer buf, String source, int at) throws ModelFormatException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (true) {
            need(buf, 1, source, at);
            byte b = buf.get();
            if (b == 0) {
                break;
            }
            bytes.write(b);
        }
        return bytes.toString(StandardCharsets.ISO_8859_1);
    }

    private static String readString(ByteBuffer buf, int length, String source, int at)
            throws ModelFormatException {
        need(buf, length, source, at);
        byte[] bytes = new byte[length];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private static void need(ByteBuffer buf, long n, String source, int at) throws ModelFormatException {
        if (buf.remaining() < n) {
            throw error(source, at, "truncated, needed " + n + " bytes");
        }
    }

    static ModelFormatException error(String source, int offset, String message) {
        return new ModelFormatException(source + " at byte " + offset + ": " + message);
    }

    static final class Writer {

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Writer() {
            out.write('@');
            out.write('@');
            out.write('b');
            out.write('@');
        }

        void object(int depth, String name) {
            for (int i = 0; i < depth; i++) {
                out.write('[');
            }
            writeBytes(name.getBytes(StandardCharsets.ISO_8859_1));
            out.write(0);
        }

        void prop(String name, int[] values) {
            beginProp(name);
            out.write('i');
            writeInt(values.length);
            for (int v : values) {
                writeInt(v);
            }
        }

        void prop(String name, float[] values) {
            beginProp(name);
            out.write('f');
            writeInt(values.length);
            for (float v : values) {
                writeFloat(v);
            }
        }

        void prop(String name, String value) {
            beginProp(name);
            out.write('s');
            writeInt(1);
            byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
            writeInt(bytes.length + 1);
            writeBytes(bytes);
            out.write(0);
        }

        byte[] toBytes() {
            return out.toByteArray();
        }

        private void beginProp(String name) {
            byte[] bytes = name.getBytes(StandardCharsets.ISO_8859_1);
            if (bytes.length > 127) {
                throw new IllegalArgumentException("property name too long: " + name);
            }
            out.write('!');
            out.write(bytes.length);
            writeBytes(bytes);
        }

        private void writeBytes(byte[] bytes) {
            out.write(bytes, 0, bytes.length);
        }

        private void writeInt(int v) {
            out.write(v & 0xff);
            out.write((v >>> 8) & 0xff);
            out.write((v >>> 16) & 0xff);
            out.write((v >>> 24) & 0xff);
        }

        private void writeFloat(float v) {
            writeInt(Float.floatToRawIntBits(v));
        }
    }
}
