package featurecreep.api.model;

/** Unit quaternion, stored as x, y, z, w. */
public record Quat(float x, float y, float z, float w) {

    public static final Quat IDENTITY = new Quat(0f, 0f, 0f, 1f);
}
