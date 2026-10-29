package featurecreep.api.model.io.pdx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecreep.api.model.Material;
import featurecreep.api.model.Mesh;
import featurecreep.api.model.Model;
import featurecreep.api.model.ModelCapabilities;
import featurecreep.api.model.ModelFormatException;
import featurecreep.api.model.Node;
import featurecreep.api.model.Quat;
import featurecreep.api.model.Skin;
import featurecreep.api.model.Transform;
import featurecreep.api.model.Vec3;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PdxMeshTest {

    private static final float EPS = 1e-5f;

    private static Model skinnedModel() {
        Mesh mesh = new Mesh(
                new float[] {0, 0, 0, 1, 0, 0, 0, 1, 0},
                new float[] {0, 0, 1, 0, 0, 1, 0, 0, 1},
                new float[] {1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1},
                new float[][] {{0, 0, 1, 0, 0, 1}, {0.5f, 0.5f, 0.25f, 0.75f, 1, 1}},
                List.of(new Mesh.Primitive(0, new int[] {0, 1, 2})),
                new Skin(new int[] {3, 4}, 2, new int[] {0, -1, 0, 1, 1, -1}, new float[] {1, 0, 0.25f, 0.75f, 1, 0}));
        Node meshNode = new Node("mesh", Transform.IDENTITY, List.of(), mesh);
        Node boneB = new Node("bone_b", new Transform(new Vec3(0, 0, 1), Quat.IDENTITY, Vec3.ONE), List.of(), null);
        Node boneA = new Node("bone_a", new Transform(new Vec3(0, 1, 0), Quat.IDENTITY, Vec3.ONE),
                List.of(boneB), null);
        Node shape = new Node("body", Transform.IDENTITY, List.of(meshNode, boneA), null, true, Map.of("lod", "1"));
        Node object = new Node("object", Transform.IDENTITY, List.of(shape), null);
        Material material = new Material("mat", "pdx_std", Map.of("diff", "body_d.dds", "n", "body_n.dds"));
        return new Model(List.of(object), List.of(material), List.of(), ModelCapabilities.any(), Map.of(), Map.of());
    }

    @Test
    void roundTripKeepsGeometryMaterialsSkinAndLod() throws ModelFormatException {
        Model original = skinnedModel();
        Model read = new PdxMeshReader().read(new PdxMeshWriter().write(original), "test.mesh").model();

        Mesh before = (Mesh) original.nodes().get(2).geometry();
        Mesh after = (Mesh) read.nodes().get(read.indexOf("body_mesh").getAsInt()).geometry();
        assertArrayEquals(before.positions(), after.positions(), EPS);
        assertArrayEquals(before.normals(), after.normals(), EPS);
        assertArrayEquals(before.tangents(), after.tangents(), EPS);
        assertArrayEquals(before.uvSets()[0], after.uvSets()[0], EPS);
        assertArrayEquals(before.uvSets()[1], after.uvSets()[1], EPS);
        assertArrayEquals(before.primitives().get(0).indices(), after.primitives().get(0).indices());

        Material material = read.materials().get(after.primitives().get(0).material());
        assertEquals("pdx_std", material.shader());
        assertEquals(Map.of("diff", "body_d.dds", "n", "body_n.dds"), material.textures());

        Skin skin = after.skin();
        assertEquals(List.of("bone_a", "bone_b"),
                Arrays.stream(skin.jointNodes()).mapToObj(i -> read.nodes().get(i).name()).toList());
        assertArrayEquals(new int[] {0, -1, 0, 1, 1, -1}, skin.jointIndices());
        assertArrayEquals(new float[] {1, 0, 0.25f, 0.75f, 1, 0}, skin.weights(), EPS);

        Node boneB = read.nodes().get(read.indexOf("bone_b").getAsInt());
        assertArrayEquals(new float[] {0, 0, 1}, new float[] {
                boneB.transform().translation().x(), boneB.transform().translation().y(),
                boneB.transform().translation().z()}, 1e-4f);

        Node shape = read.nodes().get(read.indexOf("body").getAsInt());
        assertEquals("1", shape.metadata().get("lod"));
    }

    @Test
    void writesHeaderAndPdxassetAsFirstProperty() throws ModelFormatException {
        byte[] bytes = new PdxMeshWriter().write(skinnedModel());
        assertEquals("@@b@", new String(bytes, 0, 4, StandardCharsets.ISO_8859_1));
        assertEquals('!', bytes[4]);
        assertEquals(8, bytes[5]);
        assertEquals("pdxasset", new String(bytes, 6, 8, StandardCharsets.ISO_8859_1));
        assertEquals('i', bytes[14]);
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(2, buf.getInt(15));
        assertEquals(1, buf.getInt(19));
        assertEquals(0, buf.getInt(23));
    }

    @Test
    void truncatedFileIsRejected() throws ModelFormatException {
        byte[] bytes = new PdxMeshWriter().write(skinnedModel());
        byte[] cut = Arrays.copyOf(bytes, bytes.length - 3);
        assertThrows(ModelFormatException.class, () -> new PdxMeshReader().read(cut, "cut.mesh"));
    }

    @Test
    void badHeaderIsRejected() {
        byte[] bad = "nope-not-pdx".getBytes(StandardCharsets.ISO_8859_1);
        ModelFormatException e = assertThrows(ModelFormatException.class,
                () -> new PdxMeshReader().read(bad, "bad.mesh"));
        assertTrue(e.getMessage().contains("@@b@"));
    }
}
