package featurecreep.api.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PngCodecTest {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @Test
    void roundTripPreservesEveryChannel() throws ImageDecodeException {
        Random random = new Random(42);
        int[] pixels = new int[17 * 9];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = random.nextInt();
        }
        FCImage original = FCImage.of(17, 9, pixels);
        FCImage decoded = Images.decode(Images.encode(original, ImageFormat.PNG));
        assertEquals(original, decoded);
    }

    @Test
    void encodedPngIsReadableByImageIO() throws IOException {
        Random random = new Random(7);
        int[] pixels = new int[8 * 5];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = random.nextInt();
        }
        byte[] encoded = Images.encode(FCImage.of(8, 5, pixels), ImageFormat.PNG);
        BufferedImage read = ImageIO.read(new ByteArrayInputStream(encoded));
        assertEquals(8, read.getWidth());
        assertEquals(5, read.getHeight());
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 8; x++) {
                assertEquals(pixels[y * 8 + x], read.getRGB(x, y), "pixel " + x + "," + y);
            }
        }
    }

    @Test
    void imageIoPngIsDecodedIdentically() throws IOException, ImageDecodeException {
        BufferedImage source = new BufferedImage(6, 4, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(3);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 6; x++) {
                source.setRGB(x, y, random.nextInt());
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(source, "png", out));
        FCImage decoded = Images.decode(out.toByteArray());
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 6; x++) {
                assertEquals(source.getRGB(x, y), decoded.getArgb(x, y), "pixel " + x + "," + y);
            }
        }
    }

    @Test
    void sixteenBitGrayscaleScalesToEightBits() throws ImageDecodeException {
        byte[] raw = {0, 0x00, 0x00, (byte) 0x80, 0x00, (byte) 0xFF, (byte) 0xFF};
        byte[] png = png(3, 1, 16, 0, null, null, raw);
        FCImage image = Images.decode(png);
        assertEquals(0xFF000000, image.getArgb(0, 0));
        assertEquals(0xFF808080, image.getArgb(1, 0));
        assertEquals(0xFFFFFFFF, image.getArgb(2, 0));
    }

    @Test
    void indexedPaletteWithTransparencyDecodes() throws ImageDecodeException {
        byte[] palette = {(byte) 0xFF, 0, 0, 0, 0, (byte) 0xFF};
        byte[] trns = {0};
        byte[] raw = {0, 0, 1};
        FCImage image = Images.decode(png(2, 1, 8, 3, palette, trns, raw));
        assertEquals(0x00FF0000, image.getArgb(0, 0));
        assertEquals(0xFF0000FF, image.getArgb(1, 0));
    }

    @Test
    void indexedPngWithoutPaletteIsRejected() {
        byte[] raw = {0, 0};
        assertThrows(ImageDecodeException.class, () -> Images.decode(png(1, 1, 8, 3, null, null, raw)));
    }

    @Test
    void grayscaleWithKeyColourMakesMatchingPixelsTransparent() throws ImageDecodeException {
        byte[] trns = {0x00, 0x05};
        byte[] raw = {0, 0x05, 0x06};
        FCImage image = Images.decode(png(2, 1, 8, 0, null, trns, raw));
        assertEquals(0x00050505, image.getArgb(0, 0));
        assertEquals(0xFF060606, image.getArgb(1, 0));
    }

    @Test
    void corruptChunkCrcIsRejected() {
        byte[] good = png(1, 1, 8, 0, null, null, new byte[] {0, 0x7F});
        good[41] ^= 1;
        assertThrows(ImageDecodeException.class, () -> Images.decode(good));
    }

    @Test
    void truncatedFileIsRejected() {
        byte[] good = Images.encode(FCImage.of(2, 2, new int[4]), ImageFormat.PNG);
        byte[] truncated = Arrays.copyOf(good, good.length - 4);
        assertThrows(ImageDecodeException.class, () -> Images.decode(truncated));
    }

    @Test
    void infoReportsHeaderOnly() throws ImageDecodeException {
        ImageInfo info = ImageInfo.read(Images.encode(FCImage.of(5, 3, new int[15]), ImageFormat.PNG));
        assertEquals(ImageFormat.PNG, info.format());
        assertEquals(5, info.width());
        assertEquals(3, info.height());
        assertEquals(1, info.mipLevels());
        assertTrue(info.supportsAlpha());
    }

    @Test
    void unknownBytesAreRejected() {
        assertThrows(ImageDecodeException.class, () -> Images.decode(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}));
        assertFalse(ImageFormat.detect(new byte[] {1, 2}).isPresent());
    }

    private static byte[] png(int width, int height, int bitDepth, int colorType, byte[] palette, byte[] trns,
            byte[] raw) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.write(SIGNATURE);
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(header);
            h.writeInt(width);
            h.writeInt(height);
            h.write(bitDepth);
            h.write(colorType);
            h.write(0);
            h.write(0);
            h.write(0);
            chunk(bytes, "IHDR", header.toByteArray());
            if (palette != null) {
                chunk(bytes, "PLTE", palette);
            }
            if (trns != null) {
                chunk(bytes, "tRNS", trns);
            }
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed, new Deflater())) {
                deflater.write(raw);
            }
            chunk(bytes, "IDAT", compressed.toByteArray());
            chunk(bytes, "IEND", new byte[0]);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] body) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        DataOutputStream d = new DataOutputStream(out);
        d.writeInt(body.length);
        d.write(typeBytes);
        d.write(body);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(body);
        d.writeInt((int) crc.getValue());
    }
}
