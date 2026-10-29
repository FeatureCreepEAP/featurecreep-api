package featurecreep.api.model;

import java.util.List;
import java.util.Objects;

/**
 * Triangle geometry. Arrays are stored flat: positions as xyz triples, normals as xyz, tangents as xyzw, and UV sets as
 * uv pairs. Index arrays reference vertices. Arrays are not copied, so callers must not mutate them after construction.
 */
public record Mesh(float[] positions, float[] normals, float[] tangents, float[][] uvSets,
        List<Primitive> primitives, Skin skin) implements Geometry {

    public record Primitive(int material, int[] indices) {

        public Primitive {
            Objects.requireNonNull(indices, "indices");
            if (material < 0) {
                throw new IllegalArgumentException("material index must not be negative");
            }
        }
    }

    public Mesh {
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(uvSets, "uvSets");
        Objects.requireNonNull(primitives, "primitives");
        if (positions.length % 3 != 0) {
            throw new IllegalArgumentException("positions length is not a multiple of 3");
        }
        int vertices = positions.length / 3;
        if (normals != null && normals.length != vertices * 3) {
            throw new IllegalArgumentException("normals length does not match vertex count");
        }
        if (tangents != null && tangents.length != vertices * 4) {
            throw new IllegalArgumentException("tangents length does not match vertex count");
        }
        for (float[] uv : uvSets) {
            if (uv.length != vertices * 2) {
                throw new IllegalArgumentException("UV set length does not match vertex count");
            }
        }
        primitives = List.copyOf(primitives);
        for (Primitive primitive : primitives) {
            int[] indices = primitive.indices();
            if (indices.length % 3 != 0) {
                throw new IllegalArgumentException("primitive index count is not a multiple of 3");
            }
            for (int index : indices) {
                if (index < 0 || index >= vertices) {
                    throw new IllegalArgumentException("index " + index + " is outside " + vertices + " vertices");
                }
            }
        }
        if (skin != null && skin.vertexCount() != vertices) {
            throw new IllegalArgumentException("skin vertex count does not match mesh");
        }
    }

    public int vertexCount() {
        return positions.length / 3;
    }
}
