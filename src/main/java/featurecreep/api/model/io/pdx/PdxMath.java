package featurecreep.api.model.io.pdx;

import featurecreep.api.model.Quat;
import featurecreep.api.model.Transform;
import featurecreep.api.model.Vec3;

/** Column-major 4x4 matrices (element {@code m[col * 4 + row]}) for PDX bone and locator transforms. */
final class PdxMath {

    static final float[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

    private PdxMath() {
    }

    static float[] fromTransform(Transform t) {
        Quat q = t.rotation();
        float x = q.x();
        float y = q.y();
        float z = q.z();
        float w = q.w();
        float r00 = 1f - 2f * (y * y + z * z);
        float r01 = 2f * (x * y - z * w);
        float r02 = 2f * (x * z + y * w);
        float r10 = 2f * (x * y + z * w);
        float r11 = 1f - 2f * (x * x + z * z);
        float r12 = 2f * (y * z - x * w);
        float r20 = 2f * (x * z - y * w);
        float r21 = 2f * (y * z + x * w);
        float r22 = 1f - 2f * (x * x + y * y);
        float sx = t.scale().x();
        float sy = t.scale().y();
        float sz = t.scale().z();
        Vec3 p = t.translation();
        return new float[] {
                r00 * sx, r10 * sx, r20 * sx, 0f,
                r01 * sy, r11 * sy, r21 * sy, 0f,
                r02 * sz, r12 * sz, r22 * sz, 0f,
                p.x(), p.y(), p.z(), 1f,
        };
    }

    /** Expands the 12-float PDX {@code tx} layout (three basis columns, then translation) to a 4x4 matrix. */
    static float[] fromTx(float[] tx) {
        return new float[] {
                tx[0], tx[1], tx[2], 0f,
                tx[3], tx[4], tx[5], 0f,
                tx[6], tx[7], tx[8], 0f,
                tx[9], tx[10], tx[11], 1f,
        };
    }

    static float[] toTx(float[] m) {
        return new float[] {
                m[0], m[1], m[2],
                m[4], m[5], m[6],
                m[8], m[9], m[10],
                m[12], m[13], m[14],
        };
    }

    static float[] multiply(float[] a, float[] b) {
        float[] out = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                float sum = 0f;
                for (int k = 0; k < 4; k++) {
                    sum += a[k * 4 + r] * b[c * 4 + k];
                }
                out[c * 4 + r] = sum;
            }
        }
        return out;
    }

    static float[] invertAffine(float[] m) {
        float r00 = m[0];
        float r10 = m[1];
        float r20 = m[2];
        float r01 = m[4];
        float r11 = m[5];
        float r21 = m[6];
        float r02 = m[8];
        float r12 = m[9];
        float r22 = m[10];
        float det = r00 * (r11 * r22 - r12 * r21) - r01 * (r10 * r22 - r12 * r20) + r02 * (r10 * r21 - r11 * r20);
        if (Math.abs(det) < 1e-12f) {
            throw new IllegalArgumentException("singular bone transform");
        }
        float i00 = (r11 * r22 - r12 * r21) / det;
        float i01 = (r02 * r21 - r01 * r22) / det;
        float i02 = (r01 * r12 - r02 * r11) / det;
        float i10 = (r12 * r20 - r10 * r22) / det;
        float i11 = (r00 * r22 - r02 * r20) / det;
        float i12 = (r02 * r10 - r00 * r12) / det;
        float i20 = (r10 * r21 - r11 * r20) / det;
        float i21 = (r01 * r20 - r00 * r21) / det;
        float i22 = (r00 * r11 - r01 * r10) / det;
        float tx = m[12];
        float ty = m[13];
        float tz = m[14];
        return new float[] {
                i00, i10, i20, 0f,
                i01, i11, i21, 0f,
                i02, i12, i22, 0f,
                -(i00 * tx + i01 * ty + i02 * tz),
                -(i10 * tx + i11 * ty + i12 * tz),
                -(i20 * tx + i21 * ty + i22 * tz),
                1f,
        };
    }

    static Transform decompose(float[] m) {
        float sx = length(m[0], m[1], m[2]);
        float sy = length(m[4], m[5], m[6]);
        float sz = length(m[8], m[9], m[10]);
        float[] n = {
                nz(m[0], sx), nz(m[1], sx), nz(m[2], sx),
                nz(m[4], sy), nz(m[5], sy), nz(m[6], sy),
                nz(m[8], sz), nz(m[9], sz), nz(m[10], sz),
        };
        float r00 = n[0];
        float r10 = n[1];
        float r20 = n[2];
        float r01 = n[3];
        float r11 = n[4];
        float r21 = n[5];
        float r02 = n[6];
        float r12 = n[7];
        float r22 = n[8];
        Quat q = quaternion(r00, r01, r02, r10, r11, r12, r20, r21, r22);
        return new Transform(new Vec3(m[12], m[13], m[14]), q, new Vec3(sx, sy, sz));
    }

    private static float length(float x, float y, float z) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        return len == 0f ? 1f : len;
    }

    private static float nz(float v, float len) {
        return v / len;
    }

    private static Quat quaternion(float r00, float r01, float r02, float r10, float r11, float r12,
            float r20, float r21, float r22) {
        float trace = r00 + r11 + r22;
        if (trace > 0f) {
            float s = (float) Math.sqrt(trace + 1f) * 2f;
            return new Quat((r21 - r12) / s, (r02 - r20) / s, (r10 - r01) / s, 0.25f * s);
        }
        if (r00 > r11 && r00 > r22) {
            float s = (float) Math.sqrt(1f + r00 - r11 - r22) * 2f;
            return new Quat(0.25f * s, (r01 + r10) / s, (r02 + r20) / s, (r21 - r12) / s);
        }
        if (r11 > r22) {
            float s = (float) Math.sqrt(1f + r11 - r00 - r22) * 2f;
            return new Quat((r01 + r10) / s, 0.25f * s, (r12 + r21) / s, (r02 - r20) / s);
        }
        float s = (float) Math.sqrt(1f + r22 - r00 - r11) * 2f;
        return new Quat((r02 + r20) / s, (r12 + r21) / s, 0.25f * s, (r10 - r01) / s);
    }
}
