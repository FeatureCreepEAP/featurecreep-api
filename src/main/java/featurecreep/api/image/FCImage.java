package featurecreep.api.image;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

/**
 * Immutable raster image. Pixels are 32-bit ARGB ({@code 0xAARRGGBB}), row-major, top row first, and not premultiplied.
 */
public final class FCImage {

    /** Upper bound on width times height, shared by every decoder to bound allocations. */
    public static final long MAX_PIXELS = 1L << 26;

    private final int width;
    private final int height;
    private final int[] argb;

    private FCImage(int width, int height, int[] argb) {
        this.width = width;
        this.height = height;
        this.argb = argb;
    }

    /**
     * Creates an image from a copy of the given ARGB pixels.
     *
     * @throws IllegalArgumentException if the dimensions are not positive, exceed {@link #MAX_PIXELS}, or do not match the array length
     */
    public static FCImage of(int width, int height, int[] argb) {
        checkDimensions(width, height);
        Objects.requireNonNull(argb, "argb");
        if (argb.length != width * height) {
            throw new IllegalArgumentException("expected " + width * height + " pixels but got " + argb.length);
        }
        return new FCImage(width, height, argb.clone());
    }

    static FCImage adopt(int width, int height, int[] argb) {
        return new FCImage(width, height, argb);
    }

    static void checkDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("image dimensions must be positive: " + width + "x" + height);
        }
        if ((long) width * height > MAX_PIXELS) {
            throw new IllegalArgumentException("image too large: " + width + "x" + height);
        }
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int getArgb(int x, int y) {
        Objects.checkIndex(x, width);
        Objects.checkIndex(y, height);
        return argb[y * width + x];
    }

    /** Returns a copy of the pixel array, row-major, top row first. */
    public int[] toArgbArray() {
        return argb.clone();
    }

    public FCImage crop(int x, int y, int w, int h) {
        checkDimensions(w, h);
        if (x < 0 || y < 0 || (long) x + w > width || (long) y + h > height) {
            throw new IllegalArgumentException("crop " + x + "," + y + " " + w + "x" + h + " is outside " + width + "x" + height);
        }
        int[] out = new int[w * h];
        for (int row = 0; row < h; row++) {
            System.arraycopy(argb, (y + row) * width + x, out, row * w, w);
        }
        return adopt(w, h, out);
    }

    public FCImage flipHorizontal() {
        int[] out = new int[argb.length];
        for (int y = 0; y < height; y++) {
            int base = y * width;
            for (int x = 0; x < width; x++) {
                out[base + x] = argb[base + width - 1 - x];
            }
        }
        return adopt(width, height, out);
    }

    public FCImage flipVertical() {
        int[] out = new int[argb.length];
        for (int y = 0; y < height; y++) {
            System.arraycopy(argb, (height - 1 - y) * width, out, y * width, width);
        }
        return adopt(width, height, out);
    }

    public FCImage mapPixels(IntUnaryOperator operator) {
        Objects.requireNonNull(operator, "operator");
        int[] out = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            out[i] = operator.applyAsInt(argb[i]);
        }
        return adopt(width, height, out);
    }

    /** Nearest-neighbour resampling with pixel-centre sampling. */
    public FCImage resizeNearest(int w, int h) {
        checkDimensions(w, h);
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            int sy = (int) (((2L * y + 1) * height) / (2L * h));
            for (int x = 0; x < w; x++) {
                int sx = (int) (((2L * x + 1) * width) / (2L * w));
                out[y * w + x] = argb[sy * width + sx];
            }
        }
        return adopt(w, h, out);
    }

    /**
     * Bilinear resampling. Colour is averaged weighted by alpha, so fully transparent pixels do not bleed their colour
     * into neighbours.
     */
    public FCImage resizeBilinear(int w, int h) {
        checkDimensions(w, h);
        int[] x0 = new int[w];
        int[] x1 = new int[w];
        double[] fx = new double[w];
        for (int x = 0; x < w; x++) {
            double sx = clamp(((x + 0.5) * width) / w - 0.5, 0, width - 1);
            x0[x] = (int) sx;
            x1[x] = Math.min(x0[x] + 1, width - 1);
            fx[x] = sx - x0[x];
        }
        int[] y0 = new int[h];
        int[] y1 = new int[h];
        double[] fy = new double[h];
        for (int y = 0; y < h; y++) {
            double sy = clamp(((y + 0.5) * height) / h - 0.5, 0, height - 1);
            y0[y] = (int) sy;
            y1[y] = Math.min(y0[y] + 1, height - 1);
            fy[y] = sy - y0[y];
        }
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            int r0 = y0[y] * width;
            int r1 = y1[y] * width;
            for (int x = 0; x < w; x++) {
                int a = argb[r0 + x0[x]];
                int b = argb[r0 + x1[x]];
                int c = argb[r1 + x0[x]];
                int d = argb[r1 + x1[x]];
                double wx0 = 1 - fx[x];
                double wy0 = 1 - fy[y];
                out[y * w + x] = blend(a, b, c, d, wx0 * wy0, fx[x] * wy0, wx0 * fy[y], fx[x] * fy[y]);
            }
        }
        return adopt(w, h, out);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int blend(int a, int b, int c, int d, double wa, double wb, double wc, double wd) {
        double[] acc = new double[4];
        accumulate(a, wa, acc);
        accumulate(b, wb, acc);
        accumulate(c, wc, acc);
        accumulate(d, wd, acc);
        double alphaSum = acc[0];
        if (alphaSum <= 0) {
            return 0;
        }
        int alpha = (int) Math.round(clamp(alphaSum, 0, 255));
        int r = (int) Math.round(clamp(acc[1] / alphaSum, 0, 255));
        int g = (int) Math.round(clamp(acc[2] / alphaSum, 0, 255));
        int bl = (int) Math.round(clamp(acc[3] / alphaSum, 0, 255));
        return (alpha << 24) | (r << 16) | (g << 8) | bl;
    }

    private static void accumulate(int pixel, double weight, double[] acc) {
        double alpha = ((pixel >>> 24) & 0xFF) * weight;
        acc[0] += alpha;
        acc[1] += alpha * ((pixel >>> 16) & 0xFF);
        acc[2] += alpha * ((pixel >>> 8) & 0xFF);
        acc[3] += alpha * (pixel & 0xFF);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FCImage other)) {
            return false;
        }
        return width == other.width && height == other.height && Arrays.equals(argb, other.argb);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * width + height) + Arrays.hashCode(argb);
    }

    @Override
    public String toString() {
        return "FCImage[" + width + "x" + height + "]";
    }
}
