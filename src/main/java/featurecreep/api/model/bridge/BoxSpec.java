package featurecreep.api.model.bridge;

import featurecreep.api.model.Cuboid;
import featurecreep.api.model.CuboidUv;
import java.util.Optional;

/**
 * A cuboid expressed as the arguments a vanilla box builder takes: min corner, size, inflate, and a Minecraft box-unwrap
 * origin. Returns empty for per-face UV, which a box unwrap cannot express. Those cuboids use {@code Cuboids.toMesh}.
 */
public record BoxSpec(float x, float y, float z, float dx, float dy, float dz, float inflate, int texU, int texV,
        boolean mirror) {

    public static Optional<BoxSpec> of(Cuboid cuboid) {
        if (!(cuboid.uv() instanceof CuboidUv.Box box)) {
            return Optional.empty();
        }
        return Optional.of(new BoxSpec(
                cuboid.min().x(), cuboid.min().y(), cuboid.min().z(),
                cuboid.size().x(), cuboid.size().y(), cuboid.size().z(),
                cuboid.inflate(), box.u(), box.v(), cuboid.mirror()));
    }
}
