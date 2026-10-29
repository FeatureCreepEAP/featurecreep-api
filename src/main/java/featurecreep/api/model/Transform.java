package featurecreep.api.model;

import java.util.Objects;

/** Local translation, rotation, and scale of a node relative to its parent. */
public record Transform(Vec3 translation, Quat rotation, Vec3 scale) {

    public static final Transform IDENTITY = new Transform(Vec3.ZERO, Quat.IDENTITY, Vec3.ONE);

    public Transform {
        Objects.requireNonNull(translation, "translation");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(scale, "scale");
    }
}
