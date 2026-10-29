package featurecreep.api.model.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecreep.api.model.Cuboid;
import featurecreep.api.model.CuboidUv;
import featurecreep.api.model.Cuboids;
import featurecreep.api.model.Mesh;
import featurecreep.api.model.Vec3;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BridgeHelpersTest {

    private static final float EPS = 1e-5f;

    private static Mesh triangle(float[] positions, float[] normals, float[][] uvSets, int[] indices) {
        return new Mesh(positions, normals, null, uvSets, List.of(new Mesh.Primitive(0, indices)), null);
    }

    private static float[] bounds(Mesh mesh) {
        float[] p = mesh.positions();
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i < p.length; i += 3) {
            for (int k = 0; k < 3; k++) {
                b[k] = Math.min(b[k], p[i + k]);
                b[k + 3] = Math.max(b[k + 3], p[i + k]);
            }
        }
        return b;
    }

    @Test
    void boxSpecCarriesTheBoxUnwrapArguments() {
        Cuboid cuboid = new Cuboid(new Vec3(1, 2, 3), new Vec3(4, 5, 6), 0.5f, new CuboidUv.Box(7, 8), true);
        assertEquals(new BoxSpec(1, 2, 3, 4, 5, 6, 0.5f, 7, 8, true), BoxSpec.of(cuboid).orElseThrow());
    }

    @Test
    void boxSpecIsEmptyForPerFaceUv() {
        Cuboid cuboid = new Cuboid(Vec3.ZERO, Vec3.ONE, 0f, new CuboidUv.Faces(Map.of()), false);
        assertTrue(BoxSpec.of(cuboid).isEmpty());
    }

    @Test
    void triangleCornersExpandPositionNormalAndUv() {
        Mesh mesh = triangle(new float[] {0, 0, 0, 1, 0, 0, 0, 1, 0},
                new float[] {0, 0, 1, 0, 0, 1, 0, 0, 1},
                new float[][] {{0, 0, 1, 0, 0, 1}}, new int[] {2, 1, 0});
        float[] corners = MeshBuffers.triangleCorners(mesh, 0);
        assertEquals(3 * MeshBuffers.FLOATS_PER_CORNER, corners.length);
        float[] first = {corners[0], corners[1], corners[2], corners[3], corners[4], corners[5],
                corners[6], corners[7]};
        float[] expected = {0, 1, 0, 0, 0, 1, 0, 1};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], first[i], EPS, "corner 0 float " + i);
        }
    }

    @Test
    void missingNormalsAndUvsBecomeZero() {
        Mesh mesh = triangle(new float[] {0, 0, 0, 1, 0, 0, 0, 1, 0}, null, new float[0][], new int[] {0, 1, 2});
        float[] corners = MeshBuffers.triangleCorners(mesh, 0);
        for (int corner = 0; corner < 3; corner++) {
            for (int k = 3; k < MeshBuffers.FLOATS_PER_CORNER; k++) {
                assertEquals(0f, corners[corner * MeshBuffers.FLOATS_PER_CORNER + k], EPS);
            }
        }
    }

    @Test
    void cuboidMeshKeepsBoundsIncludingInflate() {
        Cuboid cuboid = new Cuboid(new Vec3(1, 2, 3), new Vec3(4, 5, 6), 0.5f, new CuboidUv.Box(0, 0), false);
        Mesh mesh = Cuboids.toMesh(cuboid, 64, 64, 2);
        assertEquals(24, mesh.vertexCount());
        assertEquals(2, mesh.primitives().get(0).material());
        float[] b = bounds(mesh);
        float[] expected = {0.5f, 1.5f, 2.5f, 5.5f, 7.5f, 9.5f};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], b[i], EPS, "bound " + i);
        }
    }

    @Test
    void mirrorSwapsUAcrossEveryFace() {
        Cuboid plain = new Cuboid(Vec3.ZERO, new Vec3(2, 3, 4), 0f, new CuboidUv.Box(0, 0), false);
        Cuboid mirrored = new Cuboid(Vec3.ZERO, new Vec3(2, 3, 4), 0f, new CuboidUv.Box(0, 0), true);
        float[] uvPlain = Cuboids.toMesh(plain, 64, 64, 0).uvSets()[0];
        float[] uvMirror = Cuboids.toMesh(mirrored, 64, 64, 0).uvSets()[0];
        int[] partner = {1, 0, 3, 2};
        for (int face = 0; face < 6; face++) {
            for (int k = 0; k < 4; k++) {
                int v = face * 4 + k;
                int p = face * 4 + partner[k];
                assertEquals(uvPlain[p * 2], uvMirror[v * 2], EPS, "u of vertex " + v);
                assertEquals(uvPlain[v * 2 + 1], uvMirror[v * 2 + 1], EPS, "v of vertex " + v);
            }
        }
    }

    @Test
    void perFaceUvIsUsedDirectlyAndMissingFacesAreUntextured() {
        CuboidUv.Faces faces = new CuboidUv.Faces(
                Map.of(CuboidUv.Face.EAST, new CuboidUv.UvRect(8, 16, 24, 32)));
        Cuboid cuboid = new Cuboid(Vec3.ZERO, Vec3.ONE, 0f, faces, false);
        float[] uv = Cuboids.toMesh(cuboid, 64, 64, 0).uvSets()[0];
        assertEquals(8f / 64f, uv[0], EPS);
        assertEquals(16f / 64f, uv[1], EPS);
        assertEquals(24f / 64f, uv[2], EPS);
        assertEquals(16f / 64f, uv[3], EPS);
        for (int i = 4 * 2; i < 24 * 2; i++) {
            assertEquals(0f, uv[i], EPS, "uv float " + i);
        }
    }
}
