package featurecreep.api.model.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import featurecreep.api.model.Cuboid;
import featurecreep.api.model.Cuboids;
import featurecreep.api.model.Mesh;
import featurecreep.api.model.Model;
import featurecreep.api.model.Node;
import featurecreep.api.model.io.aimcube.AimCubeReader;
import featurecreep.api.model.io.pdx.PdxMeshReader;
import featurecreep.api.model.io.pdx.PdxMeshWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrossFormatConversionTest {

    private static final float EPS = 1e-3f;

    private static final String AIMCUBE = """
            aimcube:1

            texture_size:[64,64]

            part skeleton
            pivot:[0,0,0]

            part body
            parent:skeleton
            pivot:[0,24,0]

            cube:
                tex=[0,0]
                pos=[-4,-12,-2]
                size=[8,12,4]

            cube:
                tex=[0,16]
                pos=[-4,-8,-4]
                size=[8,8,8]
            """;

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
    void aimcubeToPdxKeepsEveryCubesBounds() throws Exception {
        Model source = new AimCubeReader()
                .read(AIMCUBE.getBytes(StandardCharsets.UTF_8), "fixture.aimcube").model();
        byte[] pdx = new PdxMeshWriter().write(source);
        Model target = new PdxMeshReader().read(pdx, "fixture.mesh").model();

        List<float[]> expected = new ArrayList<>();
        for (Node node : source.nodes()) {
            if (node.geometry() instanceof Cuboid cuboid) {
                expected.add(bounds(Cuboids.toMesh(cuboid, 64, 64, 0)));
            }
        }
        List<float[]> actual = new ArrayList<>();
        for (Node node : target.nodes()) {
            if (node.geometry() instanceof Mesh mesh) {
                actual.add(bounds(mesh));
            }
        }

        assertEquals(2, expected.size());
        assertEquals(expected.size(), actual.size());
        expected.sort((a, b) -> Arrays.compare(a, b));
        actual.sort((a, b) -> Arrays.compare(a, b));
        for (int i = 0; i < expected.size(); i++) {
            for (int k = 0; k < 6; k++) {
                assertEquals(expected.get(i)[k], actual.get(i)[k], EPS, "cube " + i + " bound " + k);
            }
        }
    }
}
