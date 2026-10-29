package featurecreep.api.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DdsCodecTest {

    private static final int DDSD_BASE = 0x1007;
    private static final int DDSD_PITCH = 0x8;
    private static final int DDSD_MIPMAPCOUNT = 0x20000;
    private static final int DDSD_DEPTH = 0x800000;
    private static final int DDPF_ALPHA = 0x2;
    private static final int DDPF_FOURCC = 0x4;
    private static final int DDPF_PALETTEINDEXED8 = 0x20;
    private static final int DDPF_RGB = 0x40;
    private static final int DDPF_LUMINANCE = 0x20000;

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;
    private static final int WHITE = 0xFFFFFFFF;

    @Test
    void bc1FourColourBlockUsesInterpolatedPalette() throws ImageDecodeException {
        byte[] payload = bytes(0x00, 0xF8, 0x1F, 0x00, 0xE4, 0x00, 0x00, 0x00);
        FCImage image = Images.decode(fourCcDds(4, 4, fourCC("DXT1"), payload));
        assertEquals(RED, image.getArgb(0, 0));
        assertEquals(BLUE, image.getArgb(1, 0));
        assertEquals(0xFFAA0055, image.getArgb(2, 0));
        assertEquals(0xFF5500AA, image.getArgb(3, 0));
        assertEquals(RED, image.getArgb(3, 3));
    }

    @Test
    void bc1ThreeColourBlockHasTransparentIndex() throws ImageDecodeException {
        byte[] payload = bytes(0x00, 0x00, 0x1F, 0x00, 0x63, 0x00, 0x00, 0x00);
        FCImage image = Images.decode(fourCcDds(4, 4, fourCC("DXT1"), payload));
        assertEquals(0x00000000, image.getArgb(0, 0));
        assertEquals(0xFF000000, image.getArgb(1, 0));
        assertEquals(0xFF00007F, image.getArgb(2, 0));
        assertEquals(BLUE, image.getArgb(3, 0));
    }

    @Test
    void bc2ExplicitFourBitAlpha() throws ImageDecodeException {
        byte[] payload = bytes(0xF0, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
                0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00);
        FCImage image = Images.decode(fourCcDds(4, 4, fourCC("DXT3"), payload));
        assertEquals(0x00FFFFFF, image.getArgb(0, 0));
        assertEquals(WHITE, image.getArgb(1, 0));
        assertEquals(WHITE, image.getArgb(2, 0));
    }

    @Test
    void bc3EightValueAlphaTable() throws ImageDecodeException {
        byte[] payload = bytes(0xFF, 0x00, 0x88, 0x00, 0x00, 0x00, 0x00, 0x00,
                0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00);
        FCImage image = Images.decode(fourCcDds(4, 4, fourCC("DXT5"), payload));
        assertEquals(WHITE, image.getArgb(0, 0));
        assertEquals(0x00FFFFFF, image.getArgb(1, 0));
        assertEquals(0xDAFFFFFF, image.getArgb(2, 0));
    }

    @Test
    void bc3SixValueAlphaTableHasZeroAndFullEntries() throws ImageDecodeException {
        byte[] payload = bytes(0x00, 0xFF, 0xBE, 0x00, 0x00, 0x00, 0x00, 0x00,
                0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00);
        FCImage image = Images.decode(fourCcDds(4, 4, fourCC("DXT5"), payload));
        assertEquals(0x00FFFFFF, image.getArgb(0, 0));
        assertEquals(WHITE, image.getArgb(1, 0));
        assertEquals(0x33FFFFFF, image.getArgb(2, 0));
    }

    @Test
    void bc4SingleChannelAsGray() throws ImageDecodeException {
        byte[] payload = bytes(0x00, 0xFF, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00);
        FCImage image = Images.decode(dx10Dds(4, 4, 80, payload));
        assertEquals(0xFF333333, image.getArgb(0, 0));
        assertEquals(0xFF000000, image.getArgb(1, 0));
    }

    @Test
    void bc5TwoChannelsWithBlueZero() throws ImageDecodeException {
        byte[] payload = bytes(0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0xFF, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00);
        FCImage image = Images.decode(dx10Dds(4, 4, 83, payload));
        assertEquals(0xFFFF3300, image.getArgb(0, 0));
        assertEquals(0xFFFF0000, image.getArgb(1, 0));
    }

    @Test
    void bc7Mode4IndexSelectorReadsAlphaIndicesFirst() throws ImageDecodeException {
        byte[] payload = bytes(0x90, 0xF2, 0x26, 0x65, 0xA6, 0x0C, 0x12, 0xD2,
                0x89, 0x18, 0x5D, 0x95, 0x0E, 0xE8, 0x81, 0x36);
        FCImage image = Images.decode(dx10Dds(4, 4, 98, payload));
        assertEquals(0xCBA04C4F, image.getArgb(0, 0));
        assertEquals(0x9AB1507E, image.getArgb(1, 1));
        assertEquals(0x9A9A4B40, image.getArgb(3, 3));
    }

    @Test
    void bc7Mode6SingleSubset() throws ImageDecodeException {
        byte[] payload = bytes(0x40, 0x16, 0x6F, 0x6B, 0x11, 0x3D, 0x17, 0x8D,
                0x6C, 0x0F, 0xD3, 0x90, 0x1F, 0xF2, 0x39, 0xA1);
        FCImage image = Images.decode(dx10Dds(4, 4, 99, payload));
        assertEquals(0x18667F69, image.getArgb(0, 0));
        assertEquals(0x1A744091, image.getArgb(1, 1));
        assertEquals(0x196E5A81, image.getArgb(3, 3));
    }

    @Test
    void bc7Mode1TwoSubsets() throws ImageDecodeException {
        byte[] payload = bytes(0x02, 0x95, 0xF2, 0x0F, 0x93, 0x95, 0x65, 0x0C,
                0xF9, 0x38, 0x0B, 0x8E, 0xDB, 0x22, 0x4A, 0x6B);
        FCImage image = Images.decode(dx10Dds(4, 4, 98, payload));
        assertEquals(0xFF4A514D, image.getArgb(0, 0));
        assertEquals(0xFF43535B, image.getArgb(1, 1));
        assertEquals(0xFFDD663D, image.getArgb(3, 3));
    }

    @Test
    void bc7ReservedZeroModeDecodesToOpaqueBlack() throws ImageDecodeException {
        FCImage image = Images.decode(dx10Dds(4, 4, 98, new byte[16]));
        assertEquals(0xFF000000, image.getArgb(0, 0));
        assertEquals(0xFF000000, image.getArgb(3, 3));
    }

    @Test
    void bc7ReportsAlphaSupport() throws ImageDecodeException {
        assertTrue(ImageInfo.read(dx10Dds(4, 4, 98, new byte[16])).supportsAlpha());
        assertTrue(ImageInfo.read(dx10Dds(4, 4, 99, new byte[16])).supportsAlpha());
    }

    @Test
    void bc6hHdrIsRejected() {
        assertThrows(ImageDecodeException.class, () -> Images.decode(dx10Dds(4, 4, 95, new byte[16])));
        assertThrows(ImageDecodeException.class, () -> Images.decode(dx10Dds(4, 4, 94, new byte[16])));
        assertThrows(ImageDecodeException.class, () -> Images.decode(dx10Dds(4, 4, 96, new byte[16])));
    }

    @Test
    void blockFormatsCropPartialEdgeBlocks() throws ImageDecodeException {
        byte[] payload = new byte[32];
        FCImage image = Images.decode(fourCcDds(5, 5, fourCC("DXT1"), payload));
        assertEquals(5, image.width());
        assertEquals(5, image.height());
        assertEquals(0xFF000000, image.getArgb(4, 4));
    }

    @Test
    void truncatedBlockDataIsRejected() {
        byte[] truncated = fourCcDds(5, 5, fourCC("DXT1"), new byte[31]);
        assertThrows(ImageDecodeException.class, () -> Images.decode(truncated));
    }

    @Test
    void legacyRgb565Masks() throws ImageDecodeException {
        byte[] payload = bytes(0x00, 0xF8, 0x10, 0x00);
        byte[] dds = maskDds(2, 1, DDSD_BASE, 0, DDPF_RGB, 16, 0xF800, 0x07E0, 0x001F, 0, payload);
        FCImage image = Images.decode(dds);
        assertEquals(RED, image.getArgb(0, 0));
        assertEquals(0xFF000084, image.getArgb(1, 0));
    }

    @Test
    void legacyRgb24RespectsPitchPadding() throws ImageDecodeException {
        byte[] payload = bytes(
                0x01, 0x02, 0x03, 0x06, 0x05, 0x04, 0xEE, 0xEE,
                0x09, 0x08, 0x07, 0x0C, 0x0B, 0x0A);
        byte[] dds = maskDds(2, 2, DDSD_BASE | DDSD_PITCH, 8, DDPF_RGB, 24,
                0xFF0000, 0xFF00, 0xFF, 0, payload);
        FCImage image = Images.decode(dds);
        assertEquals(0xFF030201, image.getArgb(0, 0));
        assertEquals(0xFF040506, image.getArgb(1, 0));
        assertEquals(0xFF070809, image.getArgb(0, 1));
        assertEquals(0xFF0A0B0C, image.getArgb(1, 1));
    }

    @Test
    void legacyLuminanceIsGray() throws ImageDecodeException {
        byte[] dds = maskDds(2, 1, DDSD_BASE, 0, DDPF_LUMINANCE, 8, 0xFF, 0, 0, 0, bytes(0x7F, 0x80));
        FCImage image = Images.decode(dds);
        assertEquals(0xFF7F7F7F, image.getArgb(0, 0));
        assertEquals(0xFF808080, image.getArgb(1, 0));
    }

    @Test
    void legacyAlphaOnlyHasBlackColour() throws ImageDecodeException {
        byte[] dds = maskDds(1, 1, DDSD_BASE, 0, DDPF_ALPHA, 8, 0, 0, 0, 0xFF, bytes(0x40));
        assertEquals(0x40000000, Images.decode(dds).getArgb(0, 0));
    }

    @Test
    void dx10Bgra8AndBgrxDiffer() throws ImageDecodeException {
        assertEquals(0x80302010, Images.decode(dx10Dds(1, 1, 87, bytes(0x10, 0x20, 0x30, 0x80))).getArgb(0, 0));
        assertEquals(0xFF302010, Images.decode(dx10Dds(1, 1, 88, bytes(0x10, 0x20, 0x30, 0x00))).getArgb(0, 0));
    }

    @Test
    void dx10Rgba8() throws ImageDecodeException {
        assertEquals(0x80302010, Images.decode(dx10Dds(1, 1, 28, bytes(0x30, 0x20, 0x10, 0x80))).getArgb(0, 0));
    }

    @Test
    void dx10R8IsGray() throws ImageDecodeException {
        assertEquals(0xFF7F7F7F, Images.decode(dx10Dds(1, 1, 61, bytes(0x7F))).getArgb(0, 0));
    }

    @Test
    void infoReportsMipsAndAlphaSupport() throws ImageDecodeException {
        byte[] bc3 = withInt(fourCcDds(4, 4, fourCC("DXT5"), new byte[16]), 8, DDSD_BASE | DDSD_MIPMAPCOUNT);
        bc3 = withInt(bc3, 28, 3);
        ImageInfo info = ImageInfo.read(bc3);
        assertEquals(ImageFormat.DDS, info.format());
        assertEquals(4, info.width());
        assertEquals(4, info.height());
        assertEquals(3, info.mipLevels());
        assertTrue(info.supportsAlpha());

        assertFalse(ImageInfo.read(dx10Dds(4, 4, 80, new byte[8])).supportsAlpha());
        assertFalse(ImageInfo.read(dx10Dds(4, 4, 83, new byte[16])).supportsAlpha());
        assertTrue(ImageInfo.read(dx10Dds(1, 1, 87, bytes(0, 0, 0, 0))).supportsAlpha());
        assertFalse(ImageInfo.read(dx10Dds(1, 1, 88, bytes(0, 0, 0, 0))).supportsAlpha());
        assertTrue(ImageInfo.read(fourCcDds(4, 4, fourCC("DXT1"), new byte[8])).supportsAlpha());
    }

    @Test
    void snormFormatsAreRejected() {
        assertThrows(ImageDecodeException.class, () -> Images.decode(dx10Dds(4, 4, 81, new byte[8])));
        assertThrows(ImageDecodeException.class, () -> Images.decode(dx10Dds(4, 4, 84, new byte[16])));
        assertThrows(ImageDecodeException.class, () -> Images.decode(fourCcDds(4, 4, fourCC("BC4S"), new byte[8])));
    }

    @Test
    void premultipliedAlphaIsRejected() {
        assertThrows(ImageDecodeException.class, () -> Images.decode(fourCcDds(4, 4, fourCC("DXT2"), new byte[16])));
        assertThrows(ImageDecodeException.class, () -> Images.decode(fourCcDds(4, 4, fourCC("DXT4"), new byte[16])));
        byte[] dx10 = withInt(dx10Dds(4, 4, 77, new byte[16]), 144, 2);
        assertThrows(ImageDecodeException.class, () -> Images.decode(dx10));
    }

    @Test
    void cubeMapsVolumesAndArraysAreRejected() {
        byte[] cube = withInt(fourCcDds(4, 4, fourCC("DXT1"), new byte[8]), 112, 0x200);
        assertThrows(ImageDecodeException.class, () -> Images.decode(cube));

        byte[] volume = withInt(withInt(fourCcDds(4, 4, fourCC("DXT1"), new byte[8]), 8, DDSD_BASE | DDSD_DEPTH), 24, 4);
        assertThrows(ImageDecodeException.class, () -> Images.decode(volume));

        byte[] dxCube = withInt(dx10Dds(4, 4, 71, new byte[8]), 136, 0x4);
        assertThrows(ImageDecodeException.class, () -> Images.decode(dxCube));

        byte[] array = withInt(dx10Dds(4, 4, 71, new byte[8]), 140, 2);
        assertThrows(ImageDecodeException.class, () -> Images.decode(array));
    }

    @Test
    void invalidMasksAndPalettesAreRejected() {
        assertThrows(ImageDecodeException.class,
                () -> Images.decode(maskDds(1, 1, DDSD_BASE, 0, DDPF_RGB, 16, 0x0F0F, 0, 0, 0, new byte[2])));
        assertThrows(ImageDecodeException.class,
                () -> Images.decode(maskDds(1, 1, DDSD_BASE, 0, DDPF_PALETTEINDEXED8 | DDPF_RGB, 8, 0xFF, 0, 0, 0, new byte[1])));
        assertThrows(ImageDecodeException.class,
                () -> Images.decode(maskDds(1, 1, DDSD_BASE, 0, DDPF_RGB, 16, 0xF800, 0xF800, 0, 0, new byte[2])));
    }

    @Test
    void unknownFourCcIsRejected() {
        assertThrows(ImageDecodeException.class, () -> Images.decode(fourCcDds(4, 4, fourCC("ABCD"), new byte[16])));
    }

    @Test
    void encodedDdsRoundTripsAndDeclaresUncompressedFormat() throws ImageDecodeException {
        FCImage image = FCImage.of(3, 2, new int[] {RED, BLUE, WHITE, 0x80102030, 0x00000000, 0xFF0A0B0C});
        byte[] encoded = Images.encode(image, ImageFormat.DDS);
        assertEquals(128 + 4 * 6, encoded.length);
        assertEquals(124, ByteBuffer.wrap(encoded, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
        assertEquals(image, Images.decode(encoded));
        ImageInfo info = ImageInfo.read(encoded);
        assertEquals(3, info.width());
        assertEquals(2, info.height());
        assertTrue(info.supportsAlpha());
    }

    @Test
    void convertDdsToPngPreservesPixels() throws ImageDecodeException {
        byte[] dds = fourCcDds(4, 4, fourCC("DXT5"), bytes(
                0xFF, 0x00, 0x88, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0xF8, 0x1F, 0x00, 0xE4, 0x00, 0x00, 0x00));
        byte[] png = Images.convert(dds, ImageFormat.PNG);
        assertEquals(ImageFormat.PNG, ImageFormat.detect(png).orElseThrow());
        assertArrayEquals(Images.decode(dds).toArgbArray(), Images.decode(png).toArgbArray());
    }

    private static byte[] header(int width, int height, int ddsFlags, int pitch, int mips, int pfFlags,
            int fourCC, int bitCount, long r, long g, long b, long a) {
        ByteBuffer buf = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(new byte[] {'D', 'D', 'S', ' '});
        buf.putInt(124).putInt(ddsFlags).putInt(height).putInt(width).putInt(pitch).putInt(0).putInt(mips);
        for (int i = 0; i < 11; i++) {
            buf.putInt(0);
        }
        buf.putInt(32).putInt(pfFlags).putInt(fourCC).putInt(bitCount)
                .putInt((int) r).putInt((int) g).putInt((int) b).putInt((int) a);
        buf.putInt(0x1000).putInt(0).putInt(0).putInt(0).putInt(0);
        return buf.array();
    }

    private static byte[] fourCcDds(int width, int height, int fourCC, byte[] payload) {
        return concat(header(width, height, DDSD_BASE, 0, 0, DDPF_FOURCC, fourCC, 0, 0, 0, 0, 0), payload);
    }

    private static byte[] maskDds(int width, int height, int ddsFlags, int pitch, int pfFlags, int bitCount,
            long r, long g, long b, long a, byte[] payload) {
        return concat(header(width, height, ddsFlags, pitch, 0, pfFlags, 0, bitCount, r, g, b, a), payload);
    }

    private static byte[] dx10Dds(int width, int height, int dxgi, byte[] payload) {
        ByteBuffer ext = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
        ext.putInt(dxgi).putInt(3).putInt(0).putInt(1).putInt(0);
        byte[] head = concat(header(width, height, DDSD_BASE, 0, 0, DDPF_FOURCC, fourCC("DX10"), 0, 0, 0, 0, 0),
                ext.array());
        return concat(head, payload);
    }

    private static byte[] withInt(byte[] source, int offset, int value) {
        byte[] copy = source.clone();
        ByteBuffer.wrap(copy).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value);
        return copy;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    private static int fourCC(String s) {
        return s.charAt(0) | (s.charAt(1) << 8) | (s.charAt(2) << 16) | (s.charAt(3) << 24);
    }
}
