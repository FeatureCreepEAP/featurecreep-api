package featurecreep.api.model;

import java.util.Map;

/** Texture mapping for a cuboid: either the Minecraft box unwrap from one corner, or an explicit rectangle per face. */
public sealed interface CuboidUv permits CuboidUv.Box, CuboidUv.Faces {

    /** Minecraft box unwrap with its origin at texel (u, v). */
    record Box(int u, int v) implements CuboidUv {
    }

    /** Explicit rectangle per face, in texel coordinates. Faces not listed are untextured. */
    record Faces(Map<Face, UvRect> faces) implements CuboidUv {

        public Faces {
            faces = Map.copyOf(faces);
        }
    }

    enum Face {
        EAST, WEST, UP, DOWN, NORTH, SOUTH
    }

    record UvRect(float u0, float v0, float u1, float v1) {
    }
}
