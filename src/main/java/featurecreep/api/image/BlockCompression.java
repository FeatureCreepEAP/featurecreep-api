package featurecreep.api.image;

/**
 * Decodes BC1 to BC5 block-compressed surfaces into ARGB pixels.
 *
 * <p>Interpolated colour and alpha values use truncating integer division, matching the
 * reference decoders for these formats. No floating point is involved.
 */
final class BlockCompression {

    enum Kind {
        BC1(8), BC2(16), BC3(16), BC4(8), BC5(16), BC7(16);

        final int blockBytes;

        Kind(int blockBytes) {
            this.blockBytes = blockBytes;
        }
    }

    private BlockCompression() {
    }

    static long byteSize(int width, int height, Kind kind) {
        return (long) ((width + 3) / 4) * ((height + 3) / 4) * kind.blockBytes;
    }

    static int[] decode(byte[] src, int offset, int width, int height, Kind kind) {
        int[] out = new int[width * height];
        int blocksWide = (width + 3) / 4;
        int blocksHigh = (height + 3) / 4;
        int[] pixels = new int[16];
        int[] alphas = new int[16];
        int[] table = new int[8];
        int pos = offset;
        for (int by = 0; by < blocksHigh; by++) {
            for (int bx = 0; bx < blocksWide; bx++) {
                switch (kind) {
                    case BC1 -> decodeColorBlock(src, pos, pixels, true);
                    case BC2 -> {
                        decodeColorBlock(src, pos + 8, pixels, false);
                        for (int p = 0; p < 16; p++) {
                            int nibble = (src[pos + p / 2] >>> ((p & 1) * 4)) & 0xF;
                            pixels[p] = (pixels[p] & 0x00FFFFFF) | ((nibble * 17) << 24);
                        }
                    }
                    case BC3 -> {
                        decodeColorBlock(src, pos + 8, pixels, false);
                        decodeAlphaBlock(src, pos, alphas, table);
                        for (int p = 0; p < 16; p++) {
                            pixels[p] = (pixels[p] & 0x00FFFFFF) | (alphas[p] << 24);
                        }
                    }
                    case BC4 -> {
                        decodeAlphaBlock(src, pos, alphas, table);
                        for (int p = 0; p < 16; p++) {
                            int v = alphas[p];
                            pixels[p] = 0xFF000000 | (v << 16) | (v << 8) | v;
                        }
                    }
                    case BC5 -> {
                        decodeAlphaBlock(src, pos, alphas, table);
                        decodeAlphaBlock(src, pos + 8, pixels, table);
                        for (int p = 0; p < 16; p++) {
                            pixels[p] = 0xFF000000 | (alphas[p] << 16) | (pixels[p] << 8);
                        }
                    }
                    case BC7 -> Bc7.decodeBlock(src, pos, pixels);
                }
                storeBlock(out, width, height, bx, by, pixels);
                pos += kind.blockBytes;
            }
        }
        return out;
    }

    private static void storeBlock(int[] out, int width, int height, int bx, int by, int[] pixels) {
        for (int py = 0; py < 4; py++) {
            int y = by * 4 + py;
            if (y >= height) {
                break;
            }
            for (int px = 0; px < 4; px++) {
                int x = bx * 4 + px;
                if (x < width) {
                    out[y * width + x] = pixels[py * 4 + px];
                }
            }
        }
    }

    private static void decodeColorBlock(byte[] src, int pos, int[] pixels, boolean allowTransparent) {
        int c0 = u16(src, pos);
        int c1 = u16(src, pos + 2);
        int[] palette = new int[4];
        palette[0] = expand565(c0);
        palette[1] = expand565(c1);
        if (!allowTransparent || c0 > c1) {
            palette[2] = blend(palette[0], palette[1], 2, 1, 3);
            palette[3] = blend(palette[0], palette[1], 1, 2, 3);
        } else {
            palette[2] = blend(palette[0], palette[1], 1, 1, 2);
            palette[3] = 0;
        }
        long indices = u32(src, pos + 4);
        for (int p = 0; p < 16; p++) {
            pixels[p] = palette[(int) ((indices >>> (2 * p)) & 3)];
        }
    }

    private static void decodeAlphaBlock(byte[] src, int pos, int[] values, int[] table) {
        int a0 = src[pos] & 0xFF;
        int a1 = src[pos + 1] & 0xFF;
        buildAlphaTable(a0, a1, table);
        long bits = 0;
        for (int i = 0; i < 6; i++) {
            bits |= (long) (src[pos + 2 + i] & 0xFF) << (8 * i);
        }
        for (int p = 0; p < 16; p++) {
            values[p] = table[(int) ((bits >>> (3 * p)) & 7)];
        }
    }

    private static void buildAlphaTable(int a0, int a1, int[] table) {
        table[0] = a0;
        table[1] = a1;
        if (a0 > a1) {
            for (int k = 2; k < 8; k++) {
                table[k] = ((8 - k) * a0 + (k - 1) * a1) / 7;
            }
        } else {
            for (int k = 2; k < 6; k++) {
                table[k] = ((6 - k) * a0 + (k - 1) * a1) / 5;
            }
            table[6] = 0;
            table[7] = 255;
        }
    }

    private static int expand565(int c) {
        int r = (c >>> 11) & 31;
        int g = (c >>> 5) & 63;
        int b = c & 31;
        return 0xFF000000
                | (((r << 3) | (r >>> 2)) << 16)
                | (((g << 2) | (g >>> 4)) << 8)
                | ((b << 3) | (b >>> 2));
    }

    private static int blend(int a, int b, int wa, int wb, int divisor) {
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int x = (a >>> shift) & 0xFF;
            int y = (b >>> shift) & 0xFF;
            out |= ((wa * x + wb * y) / divisor) << shift;
        }
        return out;
    }

    private static int u16(byte[] src, int pos) {
        return (src[pos] & 0xFF) | ((src[pos + 1] & 0xFF) << 8);
    }

    private static long u32(byte[] src, int pos) {
        long v = 0;
        for (int i = 3; i >= 0; i--) {
            v = (v << 8) | (src[pos + i] & 0xFF);
        }
        return v;
    }
}
