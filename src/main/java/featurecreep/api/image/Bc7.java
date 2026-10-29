package featurecreep.api.image;

import java.util.Arrays;

/**
 * Decodes one BC7 block (16 bytes) into 16 ARGB pixels.
 *
 * <p>Mode is the position of the lowest set bit in the first byte. A zero first byte is a
 * reserved mode and decodes to opaque black, matching the reference decoder.
 */
final class Bc7 {

    private static final int[] WEIGHTS2 = {0, 21, 43, 64};
    private static final int[] WEIGHTS3 = {0, 9, 18, 27, 37, 46, 55, 64};
    private static final int[] WEIGHTS4 = {0, 4, 9, 13, 17, 21, 26, 30, 34, 38, 43, 47, 51, 55, 60, 64};

    private static final int PBIT_NONE = 0;
    private static final int PBIT_ENDPOINT = 1;
    private static final int PBIT_SUBSET = 2;

    private static final int[] PARTITION2 = {
            0xCCCC, 0x8888, 0xEEEE, 0xECC8, 0xC880, 0xFEEC, 0xFEC8, 0xEC80,
            0xC800, 0xFFEC, 0xFE80, 0xE800, 0xFFE8, 0xFF00, 0xFFF0, 0xF000,
            0xF710, 0x008E, 0x7100, 0x08CE, 0x008C, 0x7310, 0x3100, 0x8CCE,
            0x088C, 0x3110, 0x6666, 0x366C, 0x17E8, 0x0FF0, 0x718E, 0x399C,
            0xAAAA, 0xF0F0, 0x5A5A, 0x33CC, 0x3C3C, 0x55AA, 0x9696, 0xA55A,
            0x73CE, 0x13C8, 0x324C, 0x3BDC, 0x6996, 0xC33C, 0x9966, 0x0660,
            0x0272, 0x04E4, 0x4E40, 0x2720, 0xC936, 0x936C, 0x39C6, 0x639C,
            0x9336, 0x9CC6, 0x817E, 0xE718, 0xCCF0, 0x0FCC, 0x7744, 0xEE22,
    };

    private static final int[] ANCHOR2 = {
            15, 15, 15, 15, 15, 15, 15, 15,
            15, 15, 15, 15, 15, 15, 15, 15,
            15, 2, 8, 2, 2, 8, 8, 15,
            2, 8, 2, 2, 8, 8, 2, 2,
            15, 15, 6, 8, 2, 8, 15, 15,
            2, 8, 2, 2, 2, 15, 15, 6,
            6, 2, 6, 8, 15, 15, 2, 2,
            15, 15, 15, 15, 15, 2, 2, 15,
    };

    private static final int[] PARTITION3 = {
            0xAA685050, 0x6A5A5040, 0x5A5A4200, 0x5450A0A8, 0xA5A50000, 0xA0A05050, 0x5555A0A0, 0x5A5A5050,
            0xAA550000, 0xAA555500, 0xAAAA5500, 0x90909090, 0x94949494, 0xA4A4A4A4, 0xA9A59450, 0x2A0A4250,
            0xA5945040, 0x0A425054, 0xA5A5A500, 0x55A0A0A0, 0xA8A85454, 0x6A6A4040, 0xA4A45000, 0x1A1A0500,
            0x0050A4A4, 0xAAA59090, 0x14696914, 0x69691400, 0xA08585A0, 0xAA821414, 0x50A4A450, 0x6A5A0200,
            0xA9A58000, 0x5090A0A8, 0xA8A09050, 0x24242424, 0x00AA5500, 0x24924924, 0x24499224, 0x50A50A50,
            0x500AA550, 0xAAAA4444, 0x66660000, 0xA5A0A5A0, 0x50A050A0, 0x69286928, 0x44AAAA44, 0x66666600,
            0xAA444444, 0x54A854A8, 0x95809580, 0x96969600, 0xA85454A8, 0x80959580, 0xAA141414, 0x96960000,
            0xAAAA1414, 0xA05050A0, 0xA0A5A5A0, 0x96000000, 0x40804080, 0xA9A8A9A8, 0xAAAAAA44, 0x2A4A5254,
    };

    private static final int[] ANCHOR3_1 = {
            3, 3, 15, 15, 8, 3, 15, 15,
            8, 8, 6, 6, 6, 5, 3, 3,
            3, 3, 8, 15, 3, 3, 6, 10,
            5, 8, 8, 6, 8, 5, 15, 15,
            8, 15, 3, 5, 6, 10, 8, 15,
            15, 3, 15, 5, 15, 15, 15, 15,
            3, 15, 5, 5, 5, 8, 5, 10,
            5, 10, 8, 13, 15, 12, 3, 3,
    };

    private static final int[] ANCHOR3_2 = {
            15, 8, 8, 3, 15, 15, 3, 8,
            15, 15, 15, 15, 15, 15, 15, 8,
            15, 8, 15, 3, 15, 8, 15, 8,
            3, 15, 6, 10, 15, 15, 10, 8,
            15, 3, 15, 10, 10, 8, 9, 10,
            6, 15, 8, 15, 3, 6, 6, 8,
            15, 3, 15, 15, 15, 15, 15, 15,
            15, 15, 15, 15, 3, 15, 15, 8,
    };

    private Bc7() {
    }

    static void decodeBlock(byte[] src, int pos, int[] out) {
        int first = src[pos] & 0xFF;
        if (first == 0) {
            Arrays.fill(out, 0, 16, 0xFF000000);
            return;
        }
        int mode = Integer.numberOfTrailingZeros(first);
        BitReader in = new BitReader(src, pos);
        in.read(mode + 1);

        int subsets = 1;
        int partitionBits = 0;
        int colourBits;
        int alphaBits = 0;
        int pbitMode = PBIT_NONE;
        int colourIndexBits;
        int alphaIndexBits = 0;
        int rotation = 0;
        boolean indexSelector = false;
        switch (mode) {
            case 0 -> {
                subsets = 3;
                partitionBits = 4;
                colourBits = 4;
                pbitMode = PBIT_ENDPOINT;
                colourIndexBits = 3;
            }
            case 1 -> {
                subsets = 2;
                partitionBits = 6;
                colourBits = 6;
                pbitMode = PBIT_SUBSET;
                colourIndexBits = 3;
            }
            case 2 -> {
                subsets = 3;
                partitionBits = 6;
                colourBits = 5;
                colourIndexBits = 2;
            }
            case 3 -> {
                subsets = 2;
                partitionBits = 6;
                colourBits = 7;
                pbitMode = PBIT_ENDPOINT;
                colourIndexBits = 2;
            }
            case 4 -> {
                rotation = in.read(2);
                indexSelector = in.read(1) == 1;
                colourBits = 5;
                alphaBits = 6;
                colourIndexBits = indexSelector ? 3 : 2;
                alphaIndexBits = indexSelector ? 2 : 3;
            }
            case 5 -> {
                rotation = in.read(2);
                colourBits = 7;
                alphaBits = 8;
                colourIndexBits = 2;
                alphaIndexBits = 2;
            }
            case 6 -> {
                colourBits = 7;
                alphaBits = 7;
                pbitMode = PBIT_ENDPOINT;
                colourIndexBits = 4;
            }
            default -> {
                subsets = 2;
                partitionBits = 6;
                colourBits = 5;
                alphaBits = 5;
                pbitMode = PBIT_ENDPOINT;
                colourIndexBits = 2;
            }
        }
        int partition = partitionBits > 0 ? in.read(partitionBits) : 0;

        int endpoints = 2 * subsets;
        int[][] raw = new int[4][endpoints];
        for (int ch = 0; ch < 4; ch++) {
            int bits = ch < 3 ? colourBits : alphaBits;
            if (bits == 0) {
                continue;
            }
            for (int e = 0; e < endpoints; e++) {
                raw[ch][e] = in.read(bits);
            }
        }
        int[] pbit = new int[endpoints];
        if (pbitMode == PBIT_ENDPOINT) {
            for (int e = 0; e < endpoints; e++) {
                pbit[e] = in.read(1);
            }
        } else if (pbitMode == PBIT_SUBSET) {
            for (int s = 0; s < subsets; s++) {
                int p = in.read(1);
                pbit[2 * s] = p;
                pbit[2 * s + 1] = p;
            }
        }
        int[][] ep = new int[4][endpoints];
        for (int ch = 0; ch < 4; ch++) {
            int bits = ch < 3 ? colourBits : alphaBits;
            for (int e = 0; e < endpoints; e++) {
                if (bits == 0) {
                    ep[ch][e] = 255;
                    continue;
                }
                int v = raw[ch][e];
                int n = bits;
                if (pbitMode != PBIT_NONE) {
                    v = (v << 1) | pbit[e];
                    n++;
                }
                ep[ch][e] = unquantize(v, n);
            }
        }

        boolean[] anchor = anchors(subsets, partition);
        int[] colourIndex = new int[16];
        int[] alphaIndex = new int[16];
        if (mode == 4 && indexSelector) {
            readIndices(in, alphaIndexBits, anchor, alphaIndex);
            readIndices(in, colourIndexBits, anchor, colourIndex);
        } else {
            readIndices(in, colourIndexBits, anchor, colourIndex);
            if (alphaIndexBits > 0) {
                readIndices(in, alphaIndexBits, anchor, alphaIndex);
            }
        }
        boolean separateAlpha = alphaIndexBits > 0;
        int[] alphaSource = separateAlpha ? alphaIndex : colourIndex;
        int alphaWeightBits = separateAlpha ? alphaIndexBits : colourIndexBits;
        int[] colourWeights = weights(colourIndexBits);
        int[] alphaWeights = weights(alphaWeightBits);

        for (int p = 0; p < 16; p++) {
            int s = subset(subsets, partition, p);
            int[] px = new int[4];
            for (int ch = 0; ch < 4; ch++) {
                int[] w = ch == 3 ? alphaWeights : colourWeights;
                int idx = ch == 3 ? alphaSource[p] : colourIndex[p];
                px[ch] = interpolate(ep[ch][2 * s], ep[ch][2 * s + 1], w[idx]);
            }
            if (alphaBits == 0) {
                px[3] = 255;
            }
            rotate(px, rotation);
            out[p] = (px[3] << 24) | (px[0] << 16) | (px[1] << 8) | px[2];
        }
    }

    private static boolean[] anchors(int subsets, int partition) {
        boolean[] anchor = new boolean[16];
        anchor[0] = true;
        if (subsets == 2) {
            anchor[ANCHOR2[partition]] = true;
        } else if (subsets == 3) {
            anchor[ANCHOR3_1[partition]] = true;
            anchor[ANCHOR3_2[partition]] = true;
        }
        return anchor;
    }

    private static void readIndices(BitReader in, int bits, boolean[] anchor, int[] out) {
        for (int p = 0; p < 16; p++) {
            out[p] = in.read(anchor[p] ? bits - 1 : bits);
        }
    }

    private static int subset(int subsets, int partition, int p) {
        if (subsets == 1) {
            return 0;
        }
        if (subsets == 2) {
            return (PARTITION2[partition] >>> p) & 1;
        }
        return (PARTITION3[partition] >>> (2 * p)) & 3;
    }

    private static int[] weights(int bits) {
        return switch (bits) {
            case 2 -> WEIGHTS2;
            case 3 -> WEIGHTS3;
            default -> WEIGHTS4;
        };
    }

    private static int unquantize(int v, int n) {
        return (v << (8 - n)) | (v >>> (2 * n - 8));
    }

    private static int interpolate(int e0, int e1, int w) {
        return ((64 - w) * e0 + w * e1 + 32) >> 6;
    }

    private static void rotate(int[] px, int rotation) {
        if (rotation == 0) {
            return;
        }
        int channel = rotation - 1;
        int tmp = px[3];
        px[3] = px[channel];
        px[channel] = tmp;
    }

    private static final class BitReader {
        private final byte[] src;
        private final int base;
        private int pos;

        BitReader(byte[] src, int base) {
            this.src = src;
            this.base = base;
        }

        int read(int n) {
            int value = 0;
            for (int i = 0; i < n; i++, pos++) {
                value |= ((src[base + (pos >>> 3)] >>> (pos & 7)) & 1) << i;
            }
            return value;
        }
    }
}
