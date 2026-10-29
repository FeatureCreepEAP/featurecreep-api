package featurecreep.api.model;

/** Converts a cuboid to triangle geometry. Texture coordinates are normalised by the given texture size. */
public final class Cuboids {

    private Cuboids() {
    }

    public static Mesh toMesh(Cuboid cuboid, float texWidth, float texHeight, int material) {
        float x0 = cuboid.min().x() - cuboid.inflate();
        float y0 = cuboid.min().y() - cuboid.inflate();
        float z0 = cuboid.min().z() - cuboid.inflate();
        float x1 = x0 + cuboid.size().x() + 2f * cuboid.inflate();
        float y1 = y0 + cuboid.size().y() + 2f * cuboid.inflate();
        float z1 = z0 + cuboid.size().z() + 2f * cuboid.inflate();

        float[][] corners = {
                {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0},
                {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0},
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1},
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0},
        };
        float[][] normals = {
                {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
        };
        CuboidUv.Face[] faces = {
                CuboidUv.Face.EAST, CuboidUv.Face.WEST, CuboidUv.Face.UP,
                CuboidUv.Face.DOWN, CuboidUv.Face.SOUTH, CuboidUv.Face.NORTH,
        };

        float[] positions = new float[24 * 3];
        float[] nrm = new float[24 * 3];
        float[] uv = new float[24 * 2];
        int[] indices = new int[36];
        for (int f = 0; f < 6; f++) {
            CuboidUv.UvRect rect = uvRect(cuboid, faces[f]);
            float u0 = rect.u0();
            float u1 = rect.u1();
            if (cuboid.mirror()) {
                float swap = u0;
                u0 = u1;
                u1 = swap;
            }
            float[] uvCorners = {u0, rect.v0(), u1, rect.v0(), u1, rect.v1(), u0, rect.v1()};
            for (int k = 0; k < 4; k++) {
                int v = f * 4 + k;
                positions[v * 3] = corners[f][k * 3];
                positions[v * 3 + 1] = corners[f][k * 3 + 1];
                positions[v * 3 + 2] = corners[f][k * 3 + 2];
                nrm[v * 3] = normals[f][0];
                nrm[v * 3 + 1] = normals[f][1];
                nrm[v * 3 + 2] = normals[f][2];
                uv[v * 2] = uvCorners[k * 2] / texWidth;
                uv[v * 2 + 1] = uvCorners[k * 2 + 1] / texHeight;
            }
            int base = f * 4;
            int i = f * 6;
            indices[i] = base;
            indices[i + 1] = base + 1;
            indices[i + 2] = base + 2;
            indices[i + 3] = base;
            indices[i + 4] = base + 2;
            indices[i + 5] = base + 3;
        }
        return new Mesh(positions, nrm, null, new float[][] {uv},
                java.util.List.of(new Mesh.Primitive(material, indices)), null);
    }

    private static CuboidUv.UvRect uvRect(Cuboid cuboid, CuboidUv.Face face) {
        if (cuboid.uv() instanceof CuboidUv.Faces faces) {
            CuboidUv.UvRect rect = faces.faces().get(face);
            return rect == null ? new CuboidUv.UvRect(0f, 0f, 0f, 0f) : rect;
        }
        CuboidUv.Box box = (CuboidUv.Box) cuboid.uv();
        float w = cuboid.size().x();
        float h = cuboid.size().y();
        float d = cuboid.size().z();
        float u = box.u();
        float v = box.v();
        return switch (face) {
            case WEST -> new CuboidUv.UvRect(u, v + d, u + d, v + d + h);
            case NORTH -> new CuboidUv.UvRect(u + d, v + d, u + d + w, v + d + h);
            case EAST -> new CuboidUv.UvRect(u + d + w, v + d, u + 2 * d + w, v + d + h);
            case SOUTH -> new CuboidUv.UvRect(u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h);
            case UP -> new CuboidUv.UvRect(u + d, v, u + d + w, v + d);
            case DOWN -> new CuboidUv.UvRect(u + d + w, v, u + d + 2 * w, v + d);
        };
    }
}
