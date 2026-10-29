package featurecreep.api.model;

import java.util.Objects;

/**
 * Axis-aligned box in node space. {@code min} is the corner with the smallest coordinates, {@code size} is the extent,
 * and {@code inflate} grows the box on every side without moving its centre.
 */
public record Cuboid(Vec3 min, Vec3 size, float inflate, CuboidUv uv, boolean mirror) implements Geometry {

    public Cuboid {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(uv, "uv");
        if (size.x() < 0f || size.y() < 0f || size.z() < 0f) {
            throw new IllegalArgumentException("cuboid size must not be negative");
        }
    }
}
