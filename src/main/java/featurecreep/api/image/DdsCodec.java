package featurecreep.api.image;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Reads and writes DirectDraw Surface files: legacy FourCC and mask formats, and the DX10
 * extension for the DXGI subset listed in {@link #dxgiFormat(int)}.
 *
 * <p>sRGB DXGI variants decode to their stored values; no transfer function is applied.
 * Premultiplied alpha, cube maps, volumes, texture arrays, and SNORM formats are rejected
 * rather than decoded into wrong colours.
 */
final class DdsCodec {

    private static final int DATA_OFFSET_LEGACY = 128;
    private static final int DATA_OFFSET_DX10 = 148;
    private static final int DDS_HEADER_SIZE = 124;
    private static final int PIXEL_FORMAT_SIZE = 32;

    private static final int DDSD_CAPS = 0x1;
    private static final int DDSD_HEIGHT = 0x2;
    private static final int DDSD_WIDTH = 0x4;
    private static final int DDSD_PITCH = 0x8;
    private static final int DDSD_PIXELFORMAT = 0x1000;
    private static final int DDSD_MIPMAPCOUNT = 0x20000;
    private static final int DDSD_DEPTH = 0x800000;

    private static final int DDSCAPS_TEXTURE = 0x1000;

    private static final int DDPF_ALPHAPIXELS = 0x1;
    private static final int DDPF_ALPHA = 0x2;
    private static final int DDPF_FOURCC = 0x4;
    private static final int DDPF_PALETTEINDEXED = 0x38;
    private static final int DDPF_RGB = 0x40;
    private static final int DDPF_LUMINANCE = 0x20000;

    private static final int DDSCAPS2_UNSUPPORTED = 0xFE00 | 0x200000;

    private static final int FOURCC_DXT1 = fourCC('D', 'X', 'T', '1');
    private static final int FOURCC_DXT2 = fourCC('D', 'X', 'T', '2');
    private static final int FOURCC_DXT3 = fourCC('D', 'X', 'T', '3');
    private static final int FOURCC_DXT4 = fourCC('D', 'X', 'T', '4');
    private static final int FOURCC_DXT5 = fourCC('D', 'X', 'T', '5');
    private static final int FOURCC_ATI1 = fourCC('A', 'T', 'I', '1');
    private static final int FOURCC_ATI2 = fourCC('A', 'T', 'I', '2');
    private static final int FOURCC_BC4U = fourCC('B', 'C', '4', 'U');
    private static final int FOURCC_BC5U = fourCC('B', 'C', '5', 'U');
    private static final int FOURCC_DX10 = fourCC('D', 'X', '1', '0');

    private static final int DX10_RESOURCE_TEXTURE2D = 3;
    private static final int DX10_MISC_TEXTURECUBE = 0x4;
    private static final int DX10_ALPHA_PREMULTIPLIED = 2;

    private DdsCodec() {
    }

    private sealed interface Format permits BlockFormat, MaskedFormat {
    }

    private record BlockFormat(BlockCompression.Kind kind) implements Format {
    }

    private record MaskedFormat(int bitCount, long r, long g, long b, long a, boolean gray) implements Format {

        boolean supportsAlpha() {
            return a != 0;
        }
    }

    private record Header(int width, int height, int mipLevels, int dataOffset, long pitch, Format format) {
    }

    static ImageInfo readInfo(byte[] data) throws ImageDecodeException {
        Header header = parseHeader(data);
        return new ImageInfo(ImageFormat.DDS, header.width(), header.height(), header.mipLevels(),
                supportsAlpha(header.format()));
    }

    static FCImage decode(byte[] data) throws ImageDecodeException {
        Header header = parseHeader(data);
        int width = header.width();
        int height = header.height();
        int[] argb = switch (header.format()) {
            case BlockFormat block -> {
                requireBytes(data, header.dataOffset(), BlockCompression.byteSize(width, height, block.kind()));
                yield BlockCompression.decode(data, header.dataOffset(), width, height, block.kind());
            }
            case MaskedFormat masked -> {
                int bytesPerPixel = masked.bitCount() / 8;
                requireBytes(data, header.dataOffset(),
                        (height - 1) * header.pitch() + (long) width * bytesPerPixel);
                yield decodeMasked(data, header, masked);
            }
        };
        return FCImage.adopt(width, height, argb);
    }

    static byte[] encodeUncompressed(FCImage image) {
        int width = image.width();
        int height = image.height();
        ByteBuffer buf = ByteBuffer.allocate(DATA_OFFSET_LEGACY + width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(new byte[] {'D', 'D', 'S', ' '});
        buf.putInt(DDS_HEADER_SIZE);
        buf.putInt(DDSD_CAPS | DDSD_HEIGHT | DDSD_WIDTH | DDSD_PITCH | DDSD_PIXELFORMAT);
        buf.putInt(height);
        buf.putInt(width);
        buf.putInt(width * 4);
        buf.putInt(0);
        buf.putInt(0);
        for (int i = 0; i < 11; i++) {
            buf.putInt(0);
        }
        buf.putInt(PIXEL_FORMAT_SIZE);
        buf.putInt(DDPF_RGB | DDPF_ALPHAPIXELS);
        buf.putInt(0);
        buf.putInt(32);
        buf.putInt(0x00FF0000);
        buf.putInt(0x0000FF00);
        buf.putInt(0x000000FF);
        buf.putInt(0xFF000000);
        buf.putInt(DDSCAPS_TEXTURE);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        for (int argb : image.toArgbArray()) {
            buf.putInt(argb);
        }
        return buf.array();
    }

    private static Header parseHeader(byte[] data) throws ImageDecodeException {
        if (data.length < DATA_OFFSET_LEGACY) {
            throw new ImageDecodeException("truncated DDS header");
        }
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (buf.getInt(4) != DDS_HEADER_SIZE) {
            throw new ImageDecodeException("bad DDS header size");
        }
        int flags = buf.getInt(8);
        int height = buf.getInt(12);
        int width = buf.getInt(16);
        int pitchOrLinearSize = buf.getInt(20);
        int depth = buf.getInt(24);
        int mipCount = buf.getInt(28);
        if (buf.getInt(76) != PIXEL_FORMAT_SIZE) {
            throw new ImageDecodeException("bad DDS pixel format size");
        }
        int pfFlags = buf.getInt(80);
        int fourCC = buf.getInt(84);
        int bitCount = buf.getInt(88);
        long rMask = Integer.toUnsignedLong(buf.getInt(92));
        long gMask = Integer.toUnsignedLong(buf.getInt(96));
        long bMask = Integer.toUnsignedLong(buf.getInt(100));
        long aMask = Integer.toUnsignedLong(buf.getInt(104));
        int caps2 = buf.getInt(112);

        if ((caps2 & DDSCAPS2_UNSUPPORTED) != 0) {
            throw new ImageDecodeException("DDS cube maps and volume textures are not supported");
        }
        if ((flags & DDSD_DEPTH) != 0 && depth > 1) {
            throw new ImageDecodeException("DDS volume textures are not supported");
        }
        if (width <= 0 || height <= 0 || (long) width * height > FCImage.MAX_PIXELS) {
            throw new ImageDecodeException("invalid DDS dimensions " + width + "x" + height);
        }
        int mipLevels = (flags & DDSD_MIPMAPCOUNT) != 0 ? Math.max(1, mipCount) : 1;

        if ((pfFlags & DDPF_FOURCC) != 0) {
            if (fourCC == FOURCC_DX10) {
                if (data.length < DATA_OFFSET_DX10) {
                    throw new ImageDecodeException("truncated DDS DX10 header");
                }
                return new Header(width, height, mipLevels, DATA_OFFSET_DX10, 0,
                        dx10Format(buf));
            }
            return new Header(width, height, mipLevels, DATA_OFFSET_LEGACY, 0, legacyFourCc(fourCC));
        }

        MaskedFormat masked = legacyMasked(pfFlags, bitCount, rMask, gMask, bMask, aMask);
        long rowBytes = (long) width * (masked.bitCount() / 8);
        long pitch = (flags & DDSD_PITCH) != 0 ? Integer.toUnsignedLong(pitchOrLinearSize) : rowBytes;
        if (pitch < rowBytes) {
            throw new ImageDecodeException("DDS pitch is smaller than a row");
        }
        return new Header(width, height, mipLevels, DATA_OFFSET_LEGACY, pitch, masked);
    }

    private static Format dx10Format(ByteBuffer buf) throws ImageDecodeException {
        int dxgi = buf.getInt(128);
        int dimension = buf.getInt(132);
        int miscFlag = buf.getInt(136);
        int arraySize = buf.getInt(140);
        int miscFlags2 = buf.getInt(144);
        if (dimension != DX10_RESOURCE_TEXTURE2D) {
            throw new ImageDecodeException("DDS DX10 resource dimension " + dimension + " is not a 2D texture");
        }
        if ((miscFlag & DX10_MISC_TEXTURECUBE) != 0) {
            throw new ImageDecodeException("DDS cube maps are not supported");
        }
        if (arraySize != 1) {
            throw new ImageDecodeException("DDS texture arrays are not supported");
        }
        if ((miscFlags2 & 0x7) == DX10_ALPHA_PREMULTIPLIED) {
            throw new ImageDecodeException("DDS premultiplied alpha is not supported");
        }
        return dxgiFormat(dxgi);
    }

    private static Format dxgiFormat(int dxgi) throws ImageDecodeException {
        return switch (dxgi) {
            case 28, 29 -> masked(32, 0x000000FFL, 0x0000FF00L, 0x00FF0000L, 0xFF000000L, false);
            case 87, 91 -> masked(32, 0x00FF0000L, 0x0000FF00L, 0x000000FFL, 0xFF000000L, false);
            case 88 -> masked(32, 0x00FF0000L, 0x0000FF00L, 0x000000FFL, 0L, false);
            case 61 -> masked(8, 0xFFL, 0L, 0L, 0L, true);
            case 71, 72 -> new BlockFormat(BlockCompression.Kind.BC1);
            case 74, 75 -> new BlockFormat(BlockCompression.Kind.BC2);
            case 77, 78 -> new BlockFormat(BlockCompression.Kind.BC3);
            case 80 -> new BlockFormat(BlockCompression.Kind.BC4);
            case 83 -> new BlockFormat(BlockCompression.Kind.BC5);
            case 98, 99 -> new BlockFormat(BlockCompression.Kind.BC7);
            case 94, 95, 96 -> throw new ImageDecodeException("DDS BC6H (HDR) is not supported");
            default -> throw new ImageDecodeException("unsupported DDS DXGI format " + dxgi);
        };
    }

    private static Format legacyFourCc(int fourCC) throws ImageDecodeException {
        if (fourCC == FOURCC_DXT1) {
            return new BlockFormat(BlockCompression.Kind.BC1);
        }
        if (fourCC == FOURCC_DXT3) {
            return new BlockFormat(BlockCompression.Kind.BC2);
        }
        if (fourCC == FOURCC_DXT5) {
            return new BlockFormat(BlockCompression.Kind.BC3);
        }
        if (fourCC == FOURCC_ATI1 || fourCC == FOURCC_BC4U) {
            return new BlockFormat(BlockCompression.Kind.BC4);
        }
        if (fourCC == FOURCC_ATI2 || fourCC == FOURCC_BC5U) {
            return new BlockFormat(BlockCompression.Kind.BC5);
        }
        if (fourCC == FOURCC_DXT2 || fourCC == FOURCC_DXT4) {
            throw new ImageDecodeException("DDS premultiplied alpha is not supported");
        }
        throw new ImageDecodeException("unsupported DDS FourCC 0x" + Integer.toHexString(fourCC));
    }

    private static MaskedFormat legacyMasked(int pfFlags, int bitCount, long r, long g, long b, long a)
            throws ImageDecodeException {
        if ((pfFlags & DDPF_PALETTEINDEXED) != 0) {
            throw new ImageDecodeException("palettized DDS is not supported");
        }
        if ((pfFlags & DDPF_RGB) != 0) {
            if ((pfFlags & DDPF_ALPHAPIXELS) == 0) {
                a = 0;
            }
            if ((r | g | b) == 0) {
                throw new ImageDecodeException("DDS RGB format has no colour masks");
            }
            return masked(bitCount, r, g, b, a, false);
        }
        if ((pfFlags & DDPF_LUMINANCE) != 0) {
            long alpha = (pfFlags & DDPF_ALPHAPIXELS) != 0 ? a : 0;
            return masked(bitCount, r, 0, 0, alpha, true);
        }
        if ((pfFlags & DDPF_ALPHA) != 0) {
            if (a == 0) {
                throw new ImageDecodeException("DDS alpha format has no alpha mask");
            }
            return masked(bitCount, 0, 0, 0, a, false);
        }
        throw new ImageDecodeException("unsupported DDS pixel format");
    }

    private static MaskedFormat masked(int bitCount, long r, long g, long b, long a, boolean gray)
            throws ImageDecodeException {
        if (bitCount != 8 && bitCount != 16 && bitCount != 24 && bitCount != 32) {
            throw new ImageDecodeException("unsupported DDS bit count " + bitCount);
        }
        long limit = bitCount == 32 ? 0xFFFFFFFFL : (1L << bitCount) - 1;
        long all = r | g | b | a;
        if ((all & ~limit) != 0) {
            throw new ImageDecodeException("DDS channel mask exceeds bit count");
        }
        if (Long.bitCount(all) != Long.bitCount(r) + Long.bitCount(g) + Long.bitCount(b) + Long.bitCount(a)) {
            throw new ImageDecodeException("DDS channel masks overlap");
        }
        requireContiguous(r);
        requireContiguous(g);
        requireContiguous(b);
        requireContiguous(a);
        if (gray && r == 0) {
            throw new ImageDecodeException("DDS luminance format has no luminance mask");
        }
        return new MaskedFormat(bitCount, r, g, b, a, gray);
    }

    private static void requireContiguous(long mask) throws ImageDecodeException {
        if (mask == 0) {
            return;
        }
        long shifted = mask >>> Long.numberOfTrailingZeros(mask);
        if ((shifted & (shifted + 1)) != 0) {
            throw new ImageDecodeException("DDS channel mask is not contiguous");
        }
    }

    private static boolean supportsAlpha(Format format) {
        return switch (format) {
            case BlockFormat block -> block.kind() == BlockCompression.Kind.BC1
                    || block.kind() == BlockCompression.Kind.BC2
                    || block.kind() == BlockCompression.Kind.BC3
                    || block.kind() == BlockCompression.Kind.BC7;
            case MaskedFormat masked -> masked.supportsAlpha();
        };
    }

    private static int[] decodeMasked(byte[] data, Header header, MaskedFormat masked) {
        int width = header.width();
        int height = header.height();
        int bytesPerPixel = masked.bitCount() / 8;
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            int row = header.dataOffset() + (int) (y * header.pitch());
            for (int x = 0; x < width; x++) {
                long value = readLittleEndian(data, row + x * bytesPerPixel, bytesPerPixel);
                argb[y * width + x] = toArgb(value, masked);
            }
        }
        return argb;
    }

    private static int toArgb(long value, MaskedFormat masked) {
        int r = channel(value, masked.r());
        int g = masked.gray() ? r : channel(value, masked.g());
        int b = masked.gray() ? r : channel(value, masked.b());
        int a = masked.a() == 0 ? 0xFF : channel(value, masked.a());
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(long pixel, long mask) {
        if (mask == 0) {
            return 0;
        }
        int bits = Long.bitCount(mask);
        long max = (1L << bits) - 1;
        long value = (pixel & mask) >>> Long.numberOfTrailingZeros(mask);
        return (int) ((value * 255 + max / 2) / max);
    }

    private static long readLittleEndian(byte[] data, int offset, int length) {
        long value = 0;
        for (int i = length - 1; i >= 0; i--) {
            value = (value << 8) | (data[offset + i] & 0xFF);
        }
        return value;
    }

    private static void requireBytes(byte[] data, int offset, long size) throws ImageDecodeException {
        if ((long) data.length - offset < size) {
            throw new ImageDecodeException("truncated DDS pixel data");
        }
    }

    private static int fourCC(char a, char b, char c, char d) {
        return a | (b << 8) | (c << 16) | (d << 24);
    }
}
