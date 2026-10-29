package featurecreep.api.model.bridge;

import featurecreep.api.model.Mesh;

/**
 * Expands one primitive of a mesh into a non-indexed triangle list. Each corner is 8 floats: position xyz, normal xyz,
 * then uv0. Missing normals become zero, and missing UVs become zero. Bridges use this for {@code VertexConsumer} output.
 */
public final class MeshBuffers {

    public static final int FLOATS_PER_CORNER = 8;

    private MeshBuffers() {
    }

    public static float[] triangleCorners(Mesh mesh, int primitive) {
        int[] indices = mesh.primitives().get(primitive).indices();
        float[] positions = mesh.positions();
        float[] normals = mesh.normals();
        float[] uv = mesh.uvSets().length > 0 ? mesh.uvSets()[0] : null;
        float[] out = new float[indices.length * FLOATS_PER_CORNER];
        for (int i = 0; i < indices.length; i++) {
            int v = indices[i];
            int o = i * FLOATS_PER_CORNER;
            out[o] = positions[v * 3];
            out[o + 1] = positions[v * 3 + 1];
            out[o + 2] = positions[v * 3 + 2];
            if (normals != null) {
                out[o + 3] = normals[v * 3];
                out[o + 4] = normals[v * 3 + 1];
                out[o + 5] = normals[v * 3 + 2];
            }
            if (uv != null) {
                out[o + 6] = uv[v * 2];
                out[o + 7] = uv[v * 2 + 1];
            }
        }
        return out;
    }
}
