package featurecreep.api.model;

public record Vec3(float x, float y, float z) {

    public static final Vec3 ZERO = new Vec3(0f, 0f, 0f);
    public static final Vec3 ONE = new Vec3(1f, 1f, 1f);
}
