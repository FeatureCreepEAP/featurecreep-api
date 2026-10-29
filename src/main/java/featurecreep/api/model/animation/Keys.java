package featurecreep.api.model.animation;

import java.util.Arrays;
import java.util.Objects;

/** Animation values for one track. Sampled keys are evenly spaced frames. Keyed keys have explicit times. */
public sealed interface Keys permits Keys.Sampled, Keys.Keyed {

    /** Values per key: 1 for scalars, 3 for vectors, 4 for quaternions. */
    int components();

    /** Time of the last key, in seconds. */
    double endTime();

    /** Writes the interpolated value at {@code t} seconds into {@code out}, which must hold {@link #components()} floats. */
    void evaluate(double t, float[] out);

    /** Frame samples at {@code fps}. Frame {@code i} is at time {@code i / fps}. */
    record Sampled(float fps, int frames, float[] values, int components) implements Keys {

        public Sampled {
            Objects.requireNonNull(values, "values");
            if (!(fps > 0f) || !Float.isFinite(fps)) {
                throw new IllegalArgumentException("fps must be positive and finite");
            }
            if (frames < 1) {
                throw new IllegalArgumentException("sampled track needs at least one frame");
            }
            if (components < 1) {
                throw new IllegalArgumentException("components must be positive");
            }
            if (values.length != frames * components) {
                throw new IllegalArgumentException(
                        "expected " + (frames * components) + " values, got " + values.length);
            }
        }

        @Override
        public double endTime() {
            return (frames - 1) / (double) fps;
        }

        @Override
        public void evaluate(double t, float[] out) {
            double frame = t * fps;
            if (frame <= 0.0) {
                copyFrame(0, out);
                return;
            }
            if (frame >= frames - 1) {
                copyFrame(frames - 1, out);
                return;
            }
            int i0 = (int) Math.floor(frame);
            int i1 = Math.min(i0 + 1, frames - 1);
            float u = (float) (frame - i0);
            for (int c = 0; c < components; c++) {
                float a = values[i0 * components + c];
                float b = values[i1 * components + c];
                out[c] = a + (b - a) * u;
            }
        }

        private void copyFrame(int frame, float[] out) {
            System.arraycopy(values, frame * components, out, 0, components);
        }

        /** Linear keyed form with one key per frame. Evaluating it at key times returns the sampled values. */
        public Keyed toKeyed() {
            double[] times = new double[frames];
            Interp[] interp = new Interp[frames];
            for (int i = 0; i < frames; i++) {
                times[i] = i / (double) fps;
                interp[i] = Interp.LINEAR;
            }
            return new Keyed(times, values.clone(), interp, null, null, components);
        }
    }

    /**
     * Keys at explicit times. {@code handleTimes} holds two offsets per key (left, right), and {@code handleValues}
     * holds two values per key per component (left, right). Both are only needed when some key is {@link Interp#BEZIER}.
     */
    record Keyed(double[] times, float[] values, Interp[] interp, float[] handleTimes, float[] handleValues,
            int components) implements Keys {

        public Keyed {
            Objects.requireNonNull(times, "times");
            Objects.requireNonNull(values, "values");
            Objects.requireNonNull(interp, "interp");
            if (components < 1) {
                throw new IllegalArgumentException("components must be positive");
            }
            int n = times.length;
            if (n < 1) {
                throw new IllegalArgumentException("keyed track needs at least one key");
            }
            if (values.length != n * components || interp.length != n) {
                throw new IllegalArgumentException("keyed arrays do not match key count");
            }
            for (int i = 0; i < n; i++) {
                if (!Double.isFinite(times[i]) || (i > 0 && !(times[i] > times[i - 1]))) {
                    throw new IllegalArgumentException("key times must be finite and strictly increasing");
                }
                Objects.requireNonNull(interp[i], "interp[" + i + "]");
            }
            boolean bezier = Arrays.asList(interp).contains(Interp.BEZIER);
            if (bezier) {
                if (handleTimes == null || handleTimes.length != n * 2
                        || handleValues == null || handleValues.length != n * 2 * components) {
                    throw new IllegalArgumentException("bezier keys need handle arrays");
                }
            }
        }

        @Override
        public double endTime() {
            return times[times.length - 1];
        }

        @Override
        public void evaluate(double t, float[] out) {
            int n = times.length;
            if (t <= times[0]) {
                copyKey(0, out);
                return;
            }
            if (t >= times[n - 1]) {
                copyKey(n - 1, out);
                return;
            }
            int lo = 0;
            int hi = n - 1;
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (times[mid] <= t) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            switch (interp[lo]) {
                case STEP -> copyKey(lo, out);
                case LINEAR -> {
                    double u = (t - times[lo]) / (times[lo + 1] - times[lo]);
                    for (int c = 0; c < components; c++) {
                        float a = values[lo * components + c];
                        float b = values[(lo + 1) * components + c];
                        out[c] = (float) (a + (b - a) * u);
                    }
                }
                case BEZIER -> evaluateBezier(lo, t, out);
            }
        }

        private void evaluateBezier(int k, double t, float[] out) {
            double t0 = times[k];
            double t1 = times[k + 1];
            double x1 = t0 + handleTimes[k * 2 + 1];
            double x2 = t1 + handleTimes[(k + 1) * 2];
            double s = solveBezierParameter(t, t0, x1, x2, t1);
            for (int c = 0; c < components; c++) {
                double p0 = values[k * components + c];
                double p1 = handleValues[(k * 2 + 1) * components + c];
                double p2 = handleValues[((k + 1) * 2) * components + c];
                double p3 = values[(k + 1) * components + c];
                out[c] = (float) cubic(s, p0, p1, p2, p3);
            }
        }

        private static double solveBezierParameter(double t, double x0, double x1, double x2, double x3) {
            double lo = 0.0;
            double hi = 1.0;
            for (int i = 0; i < 40; i++) {
                double mid = (lo + hi) * 0.5;
                if (cubic(mid, x0, x1, x2, x3) < t) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            return (lo + hi) * 0.5;
        }

        private static double cubic(double s, double p0, double p1, double p2, double p3) {
            double u = 1.0 - s;
            return u * u * u * p0 + 3.0 * u * u * s * p1 + 3.0 * u * s * s * p2 + s * s * s * p3;
        }

        private void copyKey(int k, float[] out) {
            System.arraycopy(values, k * components, out, 0, components);
        }
    }
}
