package featurecreep.api.image;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;

final class PngCodec {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final int[] ADAM7_X0 = {0, 4, 0, 2, 0, 1, 0};
    private static final int[] ADAM7_Y0 = {0, 0, 4, 0, 2, 0, 1};
    private static final int[] ADAM7_DX = {8, 8, 4, 4, 2, 2, 1};
    private static final int[] ADAM7_DY = {8, 8, 8, 4, 4, 2, 2};

    private PngCodec() {
    }

    private record Header(int width, int height, int bitDepth, int colorType, int interlace) {

        int channels() {
            return switch (colorType) {
                case 0, 3 -> 1;
                case 2 -> 3;
                case 4 -> 2;
                default -> 4;
            };
        }

        int bitsPerPixel() {
            return channels() * bitDepth;
        }
    }

    private static final class Chunks {
        Header header;
        byte[] palette;
        byte[] transparency;
        byte[] imageData;
    }

    static ImageInfo readInfo(byte[] data) throws ImageDecodeException {
        Chunks chunks = readChunks(data, true);
        Header header = chunks.header;
        boolean supportsAlpha = header.colorType() == 4 || header.colorType() == 6 || chunks.transparency != null;
        return new ImageInfo(ImageFormat.PNG, header.width(), header.height(), 1, supportsAlpha);
    }

    static FCImage decode(byte[] data) throws ImageDecodeException {
        Chunks chunks = readChunks(data, false);
        Header header = chunks.header;
        if (header.colorType() == 3 && chunks.palette == null) {
            throw new ImageDecodeException("indexed PNG has no PLTE chunk");
        }
        if (chunks.imageData == null) {
            throw new ImageDecodeException("PNG has no IDAT chunk");
        }
        int bitsPerPixel = header.bitsPerPixel();
        long expected = 0;
        if (header.interlace() == 0) {
            expected = passBytes(header.width(), header.height(), bitsPerPixel);
        } else {
            for (int pass = 0; pass < 7; pass++) {
                int pw = passSize(header.width(), ADAM7_X0[pass], ADAM7_DX[pass]);
                int ph = passSize(header.height(), ADAM7_Y0[pass], ADAM7_DY[pass]);
                if (pw > 0 && ph > 0) {
                    expected += passBytes(pw, ph, bitsPerPixel);
                }
            }
        }
        byte[] raw = inflate(chunks.imageData, expected);

        int width = header.width();
        int height = header.height();
        int[] out = new int[width * height];
        Pixels pixels = new Pixels(header, chunks.palette, chunks.transparency);
        if (header.interlace() == 0) {
            decodePass(raw, 0, width, height, 0, 0, 1, 1, width, out, pixels);
        } else {
            int offset = 0;
            for (int pass = 0; pass < 7; pass++) {
                int pw = passSize(width, ADAM7_X0[pass], ADAM7_DX[pass]);
                int ph = passSize(height, ADAM7_Y0[pass], ADAM7_DY[pass]);
                if (pw == 0 || ph == 0) {
                    continue;
                }
                decodePass(raw, offset, pw, ph, ADAM7_X0[pass], ADAM7_Y0[pass], ADAM7_DX[pass], ADAM7_DY[pass],
                        width, out, pixels);
                offset += (int) passBytes(pw, ph, bitsPerPixel);
            }
        }
        return FCImage.adopt(width, height, out);
    }

    static byte[] encode(FCImage image) {
        int width = image.width();
        int height = image.height();
        int stride = width * 4;
        byte[] filtered = new byte[height * (stride + 1)];
        byte[] row = new byte[stride];
        byte[] prev = new byte[stride];
        byte[][] candidates = new byte[5][stride];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getArgb(x, y);
                int o = x * 4;
                row[o] = (byte) (argb >>> 16);
                row[o + 1] = (byte) (argb >>> 8);
                row[o + 2] = (byte) argb;
                row[o + 3] = (byte) (argb >>> 24);
            }
            int best = 0;
            long bestScore = Long.MAX_VALUE;
            for (int f = 0; f < 5; f++) {
                long score = 0;
                byte[] candidate = candidates[f];
                for (int i = 0; i < stride; i++) {
                    int left = i >= 4 ? row[i - 4] & 0xFF : 0;
                    int up = prev[i] & 0xFF;
                    int upLeft = i >= 4 ? prev[i - 4] & 0xFF : 0;
                    int value = row[i] & 0xFF;
                    int predicted = switch (f) {
                        case 0 -> 0;
                        case 1 -> left;
                        case 2 -> up;
                        case 3 -> (left + up) >>> 1;
                        default -> paeth(left, up, upLeft);
                    };
                    byte residual = (byte) (value - predicted);
                    candidate[i] = residual;
                    score += Math.abs(residual);
                }
                if (score < bestScore) {
                    bestScore = score;
                    best = f;
                }
            }
            int rowStart = y * (stride + 1);
            filtered[rowStart] = (byte) best;
            System.arraycopy(candidates[best], 0, filtered, rowStart + 1, stride);
            System.arraycopy(row, 0, prev, 0, stride);
        }

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
            deflater.write(filtered);
        } catch (IOException e) {
            throw new IllegalStateException("in-memory PNG compression failed", e);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(out)) {
            dos.write(SIGNATURE);
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            try (DataOutputStream h = new DataOutputStream(header)) {
                h.writeInt(width);
                h.writeInt(height);
                h.write(8);
                h.write(6);
                h.write(0);
                h.write(0);
                h.write(0);
            }
            writeChunk(dos, "IHDR", header.toByteArray());
            writeChunk(dos, "IDAT", compressed.toByteArray());
            writeChunk(dos, "IEND", new byte[0]);
        } catch (IOException e) {
            throw new IllegalStateException("in-memory PNG write failed", e);
        }
        return out.toByteArray();
    }

    private static void writeChunk(DataOutputStream out, String type, byte[] body) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.writeInt(body.length);
        out.write(typeBytes);
        out.write(body);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(body);
        out.writeInt((int) crc.getValue());
    }

    private static Chunks readChunks(byte[] data, boolean headerOnly) throws ImageDecodeException {
        if (data.length < 8 || !isSignature(data)) {
            throw new ImageDecodeException("not a PNG file");
        }
        Chunks chunks = new Chunks();
        ByteArrayOutputStream idat = null;
        int pos = 8;
        boolean first = true;
        while (true) {
            if (data.length - pos < 12) {
                throw new ImageDecodeException("truncated PNG chunk header");
            }
            long length = readUInt(data, pos);
            if (length > data.length - pos - 12L) {
                throw new ImageDecodeException("PNG chunk length exceeds file");
            }
            int len = (int) length;
            String type = new String(data, pos + 4, 4, StandardCharsets.US_ASCII);
            CRC32 crc = new CRC32();
            crc.update(data, pos + 4, 4 + len);
            if ((int) crc.getValue() != (int) readUInt(data, pos + 8 + len)) {
                throw new ImageDecodeException("PNG CRC mismatch in " + type + " chunk");
            }
            int body = pos + 8;
            if (first && !type.equals("IHDR")) {
                throw new ImageDecodeException("PNG must begin with IHDR");
            }
            first = false;
            switch (type) {
                case "IHDR" -> chunks.header = parseHeader(data, body, len);
                case "PLTE" -> {
                    if (len == 0 || len % 3 != 0 || len > 256 * 3) {
                        throw new ImageDecodeException("invalid PLTE chunk");
                    }
                    chunks.palette = Arrays.copyOfRange(data, body, body + len);
                }
                case "tRNS" -> chunks.transparency = Arrays.copyOfRange(data, body, body + len);
                case "IDAT" -> {
                    if (headerOnly) {
                        return finish(chunks);
                    }
                    if (idat == null) {
                        idat = new ByteArrayOutputStream(Math.max(len, 1024));
                    }
                    idat.write(data, body, len);
                }
                case "IEND" -> {
                    if (idat != null) {
                        chunks.imageData = idat.toByteArray();
                    }
                    return finish(chunks);
                }
                default -> {
                }
            }
            pos = body + len + 4;
        }
    }

    private static Chunks finish(Chunks chunks) throws ImageDecodeException {
        if (chunks.header == null) {
            throw new ImageDecodeException("PNG has no IHDR chunk");
        }
        if (chunks.header.colorType() == 3 && chunks.palette == null) {
            throw new ImageDecodeException("indexed PNG has no PLTE chunk");
        }
        if (chunks.transparency != null) {
            validateTransparency(chunks);
        }
        return chunks;
    }

    private static void validateTransparency(Chunks chunks) throws ImageDecodeException {
        int colorType = chunks.header.colorType();
        int length = chunks.transparency.length;
        if (colorType == 0 && length != 2 || colorType == 2 && length != 6) {
            throw new ImageDecodeException("invalid tRNS chunk");
        }
        if (colorType == 3 && chunks.palette == null) {
            throw new ImageDecodeException("tRNS before PLTE");
        }
        if (colorType == 3 && length > chunks.palette.length / 3) {
            throw new ImageDecodeException("tRNS has more entries than PLTE");
        }
        if (colorType == 4 || colorType == 6) {
            throw new ImageDecodeException("tRNS is not allowed for this color type");
        }
    }

    private static Header parseHeader(byte[] data, int body, int len) throws ImageDecodeException {
        if (len != 13) {
            throw new ImageDecodeException("invalid IHDR length");
        }
        long width = readUInt(data, body);
        long height = readUInt(data, body + 4);
        int bitDepth = data[body + 8] & 0xFF;
        int colorType = data[body + 9] & 0xFF;
        int compression = data[body + 10] & 0xFF;
        int filter = data[body + 11] & 0xFF;
        int interlace = data[body + 12] & 0xFF;
        if (width <= 0 || height <= 0 || width > Integer.MAX_VALUE || height > Integer.MAX_VALUE) {
            throw new ImageDecodeException("invalid PNG dimensions");
        }
        if (width * height > FCImage.MAX_PIXELS) {
            throw new ImageDecodeException("PNG image too large");
        }
        if (compression != 0 || filter != 0 || interlace > 1) {
            throw new ImageDecodeException("unsupported PNG compression, filter or interlace method");
        }
        boolean valid = switch (colorType) {
            case 0 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8 || bitDepth == 16;
            case 2, 4, 6 -> bitDepth == 8 || bitDepth == 16;
            case 3 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8;
            default -> false;
        };
        if (!valid) {
            throw new ImageDecodeException("unsupported PNG color type " + colorType + " at bit depth " + bitDepth);
        }
        return new Header((int) width, (int) height, bitDepth, colorType, interlace);
    }

    private static boolean isSignature(byte[] data) {
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (data[i] != SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private static long readUInt(byte[] data, int offset) {
        return ((data[offset] & 0xFFL) << 24) | ((data[offset + 1] & 0xFF) << 16) | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static int passSize(int size, int start, int step) {
        return size <= start ? 0 : (size - start + step - 1) / step;
    }

    private static long passBytes(int width, int height, int bitsPerPixel) {
        long stride = ((long) width * bitsPerPixel + 7) / 8;
        return (stride + 1) * height;
    }

    private static byte[] inflate(byte[] compressed, long expected) throws ImageDecodeException {
        if (expected > Integer.MAX_VALUE - 8) {
            throw new ImageDecodeException("PNG image data too large");
        }
        int size = (int) expected;
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            byte[] out = new byte[size];
            int n = 0;
            while (n < size) {
                int k = inflater.inflate(out, n, size - n);
                n += k;
                if (k == 0) {
                    break;
                }
            }
            if (n != size) {
                throw new ImageDecodeException("PNG image data is truncated");
            }
            return out;
        } catch (DataFormatException e) {
            throw new ImageDecodeException("corrupt PNG image data");
        } finally {
            inflater.end();
        }
    }

    private static void decodePass(byte[] raw, int offset, int pw, int ph, int x0, int y0, int dx, int dy,
            int outWidth, int[] out, Pixels pixels) throws ImageDecodeException {
        int bitsPerPixel = pixels.header.bitsPerPixel();
        int bpp = Math.max(1, bitsPerPixel / 8);
        int stride = (pw * bitsPerPixel + 7) / 8;
        byte[] prev = new byte[stride];
        byte[] cur = new byte[stride];
        int rowLength = stride + 1;
        for (int row = 0; row < ph; row++) {
            int start = offset + row * rowLength;
            int filter = raw[start] & 0xFF;
            unfilter(raw, start + 1, cur, prev, stride, bpp, filter);
            int y = y0 + row * dy;
            for (int col = 0; col < pw; col++) {
                out[y * outWidth + x0 + col * dx] = pixels.argbAt(cur, col);
            }
            byte[] swap = prev;
            prev = cur;
            cur = swap;
        }
    }

    private static void unfilter(byte[] raw, int src, byte[] cur, byte[] prev, int stride, int bpp, int filter)
            throws ImageDecodeException {
        switch (filter) {
            case 0 -> System.arraycopy(raw, src, cur, 0, stride);
            case 1 -> {
                for (int i = 0; i < stride; i++) {
                    int left = i >= bpp ? cur[i - bpp] & 0xFF : 0;
                    cur[i] = (byte) (raw[src + i] + left);
                }
            }
            case 2 -> {
                for (int i = 0; i < stride; i++) {
                    cur[i] = (byte) (raw[src + i] + (prev[i] & 0xFF));
                }
            }
            case 3 -> {
                for (int i = 0; i < stride; i++) {
                    int left = i >= bpp ? cur[i - bpp] & 0xFF : 0;
                    int up = prev[i] & 0xFF;
                    cur[i] = (byte) (raw[src + i] + ((left + up) >>> 1));
                }
            }
            case 4 -> {
                for (int i = 0; i < stride; i++) {
                    int left = i >= bpp ? cur[i - bpp] & 0xFF : 0;
                    int up = prev[i] & 0xFF;
                    int upLeft = i >= bpp ? prev[i - bpp] & 0xFF : 0;
                    cur[i] = (byte) (raw[src + i] + paeth(left, up, upLeft));
                }
            }
            default -> throw new ImageDecodeException("invalid PNG filter type " + filter);
        }
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) {
            return a;
        }
        return pb <= pc ? b : c;
    }

    private static final class Pixels {
        final Header header;
        final int[] palette;
        final int[] transparency;
        final boolean hasKey;
        final int keyGray;
        final int keyRed;
        final int keyGreen;
        final int keyBlue;
        final int maxValue;

        Pixels(Header header, byte[] paletteBytes, byte[] trns) {
            this.header = header;
            this.maxValue = (1 << header.bitDepth()) - 1;
            if (paletteBytes != null) {
                int entries = paletteBytes.length / 3;
                palette = new int[entries];
                for (int i = 0; i < entries; i++) {
                    int o = i * 3;
                    palette[i] = 0xFF000000 | ((paletteBytes[o] & 0xFF) << 16) | ((paletteBytes[o + 1] & 0xFF) << 8)
                            | (paletteBytes[o + 2] & 0xFF);
                }
            } else {
                palette = null;
            }
            if (trns != null && header.colorType() == 3) {
                transparency = new int[trns.length];
                for (int i = 0; i < trns.length; i++) {
                    transparency[i] = trns[i] & 0xFF;
                }
            } else {
                transparency = null;
            }
            hasKey = trns != null && header.colorType() != 3;
            keyGray = hasKey && header.colorType() == 0 ? u16(trns, 0) : -1;
            keyRed = hasKey && header.colorType() == 2 ? u16(trns, 0) : -1;
            keyGreen = hasKey && header.colorType() == 2 ? u16(trns, 2) : -1;
            keyBlue = hasKey && header.colorType() == 2 ? u16(trns, 4) : -1;
        }

        private static int u16(byte[] bytes, int offset) {
            return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
        }

        int argbAt(byte[] row, int col) throws ImageDecodeException {
            int depth = header.bitDepth();
            int channels = header.channels();
            int base = col * channels;
            switch (header.colorType()) {
                case 0 -> {
                    int v = sample(row, base, depth);
                    int g = scale(v, maxValue);
                    int alpha = hasKey && v == keyGray ? 0 : 0xFF;
                    return (alpha << 24) | (g << 16) | (g << 8) | g;
                }
                case 2 -> {
                    int r = sample(row, base, depth);
                    int g = sample(row, base + 1, depth);
                    int b = sample(row, base + 2, depth);
                    int alpha = hasKey && r == keyRed && g == keyGreen && b == keyBlue ? 0 : 0xFF;
                    return (alpha << 24) | (scale(r, maxValue) << 16) | (scale(g, maxValue) << 8) | scale(b, maxValue);
                }
                case 3 -> {
                    int index = sample(row, base, depth);
                    if (index >= palette.length) {
                        throw new ImageDecodeException("PNG palette index out of range");
                    }
                    int color = palette[index];
                    int alpha = transparency != null && index < transparency.length ? transparency[index] : 0xFF;
                    return (alpha << 24) | (color & 0x00FFFFFF);
                }
                case 4 -> {
                    int g = scale(sample(row, base, depth), maxValue);
                    int alpha = scale(sample(row, base + 1, depth), maxValue);
                    return (alpha << 24) | (g << 16) | (g << 8) | g;
                }
                default -> {
                    int r = scale(sample(row, base, depth), maxValue);
                    int g = scale(sample(row, base + 1, depth), maxValue);
                    int b = scale(sample(row, base + 2, depth), maxValue);
                    int a = scale(sample(row, base + 3, depth), maxValue);
                    return (a << 24) | (r << 16) | (g << 8) | b;
                }
            }
        }

        private static int sample(byte[] row, int index, int depth) {
            switch (depth) {
                case 16 -> {
                    return ((row[index * 2] & 0xFF) << 8) | (row[index * 2 + 1] & 0xFF);
                }
                case 8 -> {
                    return row[index] & 0xFF;
                }
                default -> {
                    int bit = index * depth;
                    int value = row[bit >>> 3] & 0xFF;
                    int shift = 8 - depth - (bit & 7);
                    return (value >>> shift) & ((1 << depth) - 1);
                }
            }
        }

        private static int scale(int value, int max) {
            if (max == 255) {
                return value;
            }
            return (int) ((value * 255L + max / 2) / max);
        }
    }
}
