package featurecreep.api.model.io.pmx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecreep.api.model.Cuboid;
import featurecreep.api.model.CuboidUv;
import featurecreep.api.model.Cuboids;
import featurecreep.api.model.Mesh;
import featurecreep.api.model.Model;
import featurecreep.api.model.ModelFormatException;
import featurecreep.api.model.Vec3;
import featurecreep.api.model.io.ModelReadResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class PmxRoundTripTest {

    private static final float EPS = 1e-5f;

    @Test
    void descriptorIdentifiesPmx() {
        assertEquals("pmx", new PmxWriter().descriptor().id());
        assertTrue(new PmxWriter().descriptor().extensions().contains("pmx"));
        assertTrue(PmxReader.DESCRIPTOR.sniff().test("PMX ".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(PmxReader.DESCRIPTOR.sniff().test("PMD ".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void handBuiltDocumentSurvivesRoundTrip() throws ModelFormatException {
        ModelReadResult result = PmxFixtures.roundTrip(PmxFixtures.handBuilt(2.0f));
        Model model = result.model();

        assertTrue(result.warnings().isEmpty());
        assertEquals(List.of("root", PmxFixtures.UPPER, PmxFixtures.LEFT_LEG, "ik", "append"),
                PmxFixtures.nodeNames(model));
        assertEquals("hand", model.metadata().get("name"));
        assertEquals("Root", model.nodes().get(0).metadata().get("name.en"));
        assertEquals("LeftLeg", model.nodes().get(2).metadata().get("name.en"));
        assertTranslation(new Vec3(0f, 8f, 0f), model.nodes().get(1).transform().translation());
        assertTranslation(new Vec3(1f, 0f, 0f), model.nodes().get(2).transform().translation());
        assertTranslation(new Vec3(0f, 0f, 2f), model.nodes().get(3).transform().translation());
        assertTranslation(new Vec3(0f, 4f, 0f), model.nodes().get(4).transform().translation());
        assertEquals(List.of("blink", "mat"), model.morphNames());

        Mesh mesh = (Mesh) model.nodes().get(0).geometry();
        assertArrayEquals(new float[] {0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f}, mesh.positions(), EPS);
        assertArrayEquals(new int[] {0, 1, 2}, mesh.primitives().get(0).indices());
        assertEquals("skin", model.materials().get(0).name());
        assertEquals("tex/skin.png", model.materials().get(0).textures().get("diffuse"));

        PmxDocument doc = model.extension(PmxReader.EXTENSION, PmxDocument.class).orElseThrow();
        assertEquals(2.0f, doc.version());
        assertEquals(5, doc.bones().size());
        assertEquals(0x001E, doc.bones().get(0).flags());
        assertArrayEquals(new float[] {0f, 1f, 0f}, doc.bones().get(1).tailOffset());
        assertEquals(1, doc.bones().get(2).tailIndex());
        assertEquals(0x003E, doc.bones().get(3).flags());
        assertEquals(2, doc.bones().get(3).ik().target());
        assertEquals(40, doc.bones().get(3).ik().loops());
        assertEquals(0.5f, doc.bones().get(3).ik().angle());
        assertEquals(1, doc.bones().get(3).ik().links().get(0).bone());
        assertFalse(doc.bones().get(3).ik().links().get(0).limited());
        assertTrue(doc.bones().get(3).ik().links().get(1).limited());
        assertArrayEquals(new float[] {-1f, 0f, 0f}, doc.bones().get(3).ik().links().get(1).min());
        assertEquals(0x011E, doc.bones().get(4).flags());
        assertEquals(1, doc.bones().get(4).inheritParent());
        assertEquals(0.5f, doc.bones().get(4).inheritRatio());

        PmxDocument.Morph blink = doc.morphs().get(0);
        assertEquals("blink", blink.name());
        assertEquals(1, blink.kind());
        assertEquals(0, blink.items().get(0).index());
        assertArrayEquals(new float[] {0.1f, 0.2f, 0.3f}, blink.items().get(0).values(), EPS);
        PmxDocument.Morph mat = doc.morphs().get(1);
        assertEquals(8, mat.kind());
        assertEquals(1, mat.items().get(0).op());
        assertEquals(2, mat.items().get(0).blend());
        assertEquals(28, mat.items().get(0).values().length);
        assertEquals(27 * 0.25f, mat.items().get(0).values()[27], EPS);

        PmxDocument.DisplayFrame frame = doc.displayFrames().get(0);
        assertEquals(1, frame.special());
        assertEquals(0, frame.elements().get(0).type());
        assertEquals(2, frame.elements().get(0).index());
        assertEquals(1, frame.elements().get(1).type());

        PmxDocument.RigidBody body = doc.rigidBodies().get(0);
        assertEquals(0, body.bone());
        assertEquals(1, body.group());
        assertEquals(0x0002, body.nonCollisionMask());
        assertEquals(1f, body.mass());
        assertEquals(0.5f, body.friction());

        PmxDocument.Joint joint = doc.joints().get(0);
        assertEquals("joint", joint.name());
        assertArrayEquals(new float[] {0f, 8f, 0f}, joint.position());
        assertArrayEquals(new float[] {-0.5f, 0f, 0f}, joint.rotMin());
    }

    @Test
    void version21RoundTripKeepsVersion() throws ModelFormatException {
        Model model = PmxFixtures.roundTrip(PmxFixtures.handBuilt(2.1f)).model();
        PmxDocument doc = model.extension(PmxReader.EXTENSION, PmxDocument.class).orElseThrow();

        assertEquals(2.1f, doc.version());
        assertEquals(5, doc.bones().size());
        assertEquals(2, doc.morphs().size());
    }

    @Test
    void cuboidMeshRoundTripKeepsBounds() throws ModelFormatException {
        Cuboid cuboid = new Cuboid(new Vec3(-4f, -12f, -2f), new Vec3(8f, 12f, 4f), 0f, new CuboidUv.Box(0, 0), false);
        Mesh mesh = Cuboids.toMesh(cuboid, 64f, 64f, 0);
        Model model = PmxFixtures.singleNode(PmxFixtures.node("body", "", 0f, 0f, 0f, mesh));

        Mesh read = (Mesh) PmxFixtures.roundTrip(model).model().nodes().get(0).geometry();

        assertArrayEquals(PmxFixtures.bounds(mesh.positions()), PmxFixtures.bounds(read.positions()), EPS);
    }

    @Test
    void cuboidGeometryIsWrittenAsItsMesh() throws ModelFormatException {
        Cuboid cuboid = new Cuboid(new Vec3(-4f, -12f, -2f), new Vec3(8f, 12f, 4f), 0f, new CuboidUv.Box(0, 0), false);
        Model model = PmxFixtures.singleNode(PmxFixtures.node("body", "", 0f, 0f, 0f, cuboid));

        Mesh read = (Mesh) PmxFixtures.roundTrip(model).model().nodes().get(0).geometry();

        assertArrayEquals(PmxFixtures.bounds(Cuboids.toMesh(cuboid, 64f, 64f, 0).positions()),
                PmxFixtures.bounds(read.positions()), EPS);
    }

    /** Mirroring turns zero components into -0.0, which record equality would treat as different. */
    private static void assertTranslation(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x(), actual.x(), EPS);
        assertEquals(expected.y(), actual.y(), EPS);
        assertEquals(expected.z(), actual.z(), EPS);
    }
}
