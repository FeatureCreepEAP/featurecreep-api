package featurecreep.api.model.io.aimcube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecreep.api.model.Cuboid;
import featurecreep.api.model.CuboidUv;
import featurecreep.api.model.Model;
import featurecreep.api.model.ModelFormatException;
import featurecreep.api.model.Node;
import featurecreep.api.model.Quat;
import featurecreep.api.model.Vec3;
import featurecreep.api.model.io.FormatDescriptor;
import featurecreep.api.model.io.ModelReadResult;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AimCubeReaderTest {

    private static final float EPS = 1e-5f;

    private static final String CANONICAL = """
            aimcube:1

            texture_size:[64,64]

            part body
            pivot:[0,24,0]

            cube:
                tex=[0,0]
                pos=[-4,-12,-2]
                size=[8,12,4]

            part head
            parent:body
            pivot:[0,-12,0]

            cube:
                tex=[0,16]
                pos=[-4,-8,-4]
                size=[8,8,8]

            part left_arm
            parent:body
            pivot:[5,-10,0]

            cube:
                tex=[24,0]
                pos=[0,-2,-2]
                size=[4,12,4]

            part right_arm
            parent:body
            pivot:[-5,-10,0]

            cube:
                tex=[24,0]
                pos=[-4,-2,-2]
                size=[4,12,4]
            """;

    private static ModelReadResult read(String text) throws ModelFormatException {
        return new AimCubeReader().read(text.getBytes(StandardCharsets.UTF_8), "model.aimcube");
    }

    private static Model model(String text) throws ModelFormatException {
        return read(text).model();
    }

    private static Node node(Model model, String name) {
        int index = model.indexOf(name).orElseThrow(() -> new AssertionError("no node " + name));
        return model.nodes().get(index);
    }

    private static Cuboid cuboid(Node node) {
        assertTrue(node.geometry() instanceof Cuboid, node.name() + " has no cuboid");
        return (Cuboid) node.geometry();
    }

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x(), actual.x(), EPS);
        assertEquals(expected.y(), actual.y(), EPS);
        assertEquals(expected.z(), actual.z(), EPS);
    }

    private static void assertQuat(Quat expected, Quat actual) {
        assertEquals(expected.x(), actual.x(), EPS);
        assertEquals(expected.y(), actual.y(), EPS);
        assertEquals(expected.z(), actual.z(), EPS);
        assertEquals(expected.w(), actual.w(), EPS);
    }

    private static ModelFormatException error(String text) {
        return assertThrows(ModelFormatException.class, () -> read(text));
    }

    @Test
    void descriptorIdentifiesAimcube() {
        FormatDescriptor descriptor = new AimCubeReader().descriptor();
        assertEquals("aimcube", descriptor.id());
        assertEquals(Set.of("aimcube"), descriptor.extensions());
    }

    @Test
    void sniffAcceptsHeaderAndRejectsOthers() {
        FormatDescriptor descriptor = new AimCubeReader().descriptor();
        assertTrue(descriptor.sniff().test("aimcube:1\npart a".getBytes(StandardCharsets.UTF_8)));
        assertTrue(descriptor.sniff().test("﻿aimcube:1".getBytes(StandardCharsets.UTF_8)));
        assertFalse(descriptor.sniff().test("{\"a\":1}".getBytes(StandardCharsets.UTF_8)));
        assertFalse(descriptor.sniff().test("// aimcube:1".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void canonicalExampleBuildsHierarchyInModelSpace() throws ModelFormatException {
        Model model = model(CANONICAL);

        assertEquals(1, model.roots().size());
        Node body = model.roots().get(0);
        assertEquals("body", body.name());
        assertEquals(3, body.children().size());
        assertVec(new Vec3(0f, -24f, 0f), body.transform().translation());

        Cuboid bodyCube = cuboid(body);
        assertVec(new Vec3(-4f, 0f, -2f), bodyCube.min());
        assertVec(new Vec3(8f, 12f, 4f), bodyCube.size());

        Node head = node(model, "head");
        assertVec(new Vec3(0f, 12f, 0f), head.transform().translation());
        assertVec(new Vec3(-4f, 0f, -4f), cuboid(head).min());
        assertEquals(new CuboidUv.Box(0, 16), cuboid(head).uv());
    }

    @Test
    void textureSizeDefaultsAndOverrides() throws ModelFormatException {
        assertEquals("64", model(CANONICAL).metadata().get("texture_width"));
        String sized = "aimcube:1\ntexture_size:[128,256]\npart a\npivot:[0,0,0]\ncube:\n tex=[0,0]\n pos=[0,0,0]\n size=[1,1,1]\n";
        Model model = model(sized);
        assertEquals("128", model.metadata().get("texture_width"));
        assertEquals("256", model.metadata().get("texture_height"));
    }

    @Test
    void rotationIsMirroredForXAndZButNotY() throws ModelFormatException {
        Model rotX = model("aimcube:1\npart a\npivot:[0,0,0]\nrotation:[0.5,0,0]\n");
        float s = (float) Math.sin(0.25);
        float c = (float) Math.cos(0.25);
        assertQuat(new Quat(-s, 0f, 0f, c), node(rotX, "a").transform().rotation());

        Model rotY = model("aimcube:1\npart a\npivot:[0,0,0]\nrotation:[0,1.5708,0]\n");
        float h = (float) Math.sin(1.5708 / 2);
        float hc = (float) Math.cos(1.5708 / 2);
        assertQuat(new Quat(0f, h, 0f, hc), node(rotY, "a").transform().rotation());
    }

    @Test
    void singleCubeIsAttachedDirectlyAndMultipleCubesBecomeChildren() throws ModelFormatException {
        Model single = model("aimcube:1\npart a\npivot:[0,0,0]\ncube:\n tex=[0,0]\n pos=[0,0,0]\n size=[1,1,1]\n");
        assertTrue(node(single, "a").geometry() instanceof Cuboid);

        Model multi = model("""
                aimcube:1
                part a
                pivot:[0,0,0]
                cube:
                    tex=[0,0]
                    pos=[0,0,0]
                    size=[1,1,1]
                cube:
                    tex=[4,4]
                    pos=[2,0,0]
                    size=[1,1,1]
                """);
        Node a = node(multi, "a");
        assertNull(a.geometry());
        assertEquals(2, a.children().size());
        assertEquals("a/cube0", a.children().get(0).name());
        assertEquals("a/cube1", a.children().get(1).name());
    }

    @Test
    void inlineCubeFieldsInlineWithCubeKeyword() throws ModelFormatException {
        Model model = model("aimcube:1\npart a\npivot:[0,0,0]\ncube: tex=[1,2] pos=[0,0,0] size=[3,4,5]\n");
        Cuboid c = cuboid(node(model, "a"));
        assertEquals(new CuboidUv.Box(1, 2), c.uv());
        assertVec(new Vec3(3f, 4f, 5f), c.size());
    }

    @Test
    void inflateMirrorAndVisibilityArePreserved() throws ModelFormatException {
        Model model = model("""
                aimcube:1
                part hidden
                pivot:[0,0,0]
                visible:false
                cube:
                    tex=[0,0]
                    pos=[0,0,0]
                    size=[2,2,2]
                    inflate=0.5
                    mirror=true
                """);
        Node hidden = node(model, "hidden");
        assertFalse(hidden.visible());
        Cuboid c = cuboid(hidden);
        assertEquals(0.5f, c.inflate(), EPS);
        assertTrue(c.mirror());
    }

    @Test
    void whitespaceCommentsAndSpacingAreNormalised() throws ModelFormatException {
        Model model = model("aimcube:1 # header\n\npart  body\npivot : [ 0 , 24 , 0 ] # pivot\n");
        assertVec(new Vec3(0f, -24f, 0f), node(model, "body").transform().translation());
    }

    @Test
    void unknownKeysWarnAndParsingContinues() throws ModelFormatException {
        ModelReadResult result = read("aimcube:1\ncustom_animation:walk\npart a\npivot:[0,0,0]\n");
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("custom_animation"));
        assertTrue(result.model().indexOf("a").isPresent());
    }

    @Test
    void missingHeaderIsRejected() {
        ModelFormatException e = error("part a\npivot:[0,0,0]\n");
        assertTrue(e.getMessage().startsWith("model.aimcube:1:"));
    }

    @Test
    void unsupportedVersionIsRejected() {
        ModelFormatException e = error("aimcube:2\npart a\npivot:[0,0,0]\n");
        assertTrue(e.getMessage().contains("unsupported"));
    }

    @Test
    void malformedArraysAreRejected() {
        assertThrows(ModelFormatException.class, () -> read("aimcube:1\npart a\npivot:[0,24]\n"));
        assertThrows(ModelFormatException.class, () -> read("aimcube:1\npart a\npivot:0,24,0\n"));
        assertThrows(ModelFormatException.class, () -> read("aimcube:1\npart a\npivot:[0,,0]\n"));
        assertThrows(ModelFormatException.class, () -> read("aimcube:1\npart a\npivot:[0,x,0]\n"));
        assertThrows(ModelFormatException.class, () -> read("aimcube:1\npart a\npivot:[0,0,0\n"));
    }

    @Test
    void missingRequiredCubeFieldsAreRejected() {
        String head = "aimcube:1\npart a\npivot:[0,0,0]\ncube:\n";
        assertTrue(error(head + " pos=[0,0,0]\n size=[1,1,1]\n").getMessage().contains("tex"));
        assertTrue(error(head + " tex=[0,0]\n size=[1,1,1]\n").getMessage().contains("pos"));
        assertTrue(error(head + " tex=[0,0]\n pos=[0,0,0]\n").getMessage().contains("size"));
    }

    @Test
    void missingPivotIsRejected() {
        assertTrue(error("aimcube:1\npart a\n").getMessage().contains("pivot"));
    }

    @Test
    void duplicatePartNamesAreRejected() {
        ModelFormatException e = error("aimcube:1\npart a\npivot:[0,0,0]\npart a\npivot:[0,0,0]\n");
        assertTrue(e.getMessage().contains("duplicate part name 'a'"));
        assertTrue(e.getMessage().startsWith("model.aimcube:4:"));
    }

    @Test
    void missingOrLaterParentIsRejected() {
        ModelFormatException missing = error("aimcube:1\npart a\nparent:ghost\npivot:[0,0,0]\n");
        assertTrue(missing.getMessage().contains("unknown parent 'ghost'"));

        ModelFormatException later = error("aimcube:1\npart a\nparent:b\npivot:[0,0,0]\npart b\npivot:[0,0,0]\n");
        assertTrue(later.getMessage().contains("unknown parent 'b'"));

        ModelFormatException self = error("aimcube:1\npart a\nparent:a\npivot:[0,0,0]\n");
        assertTrue(self.getMessage().contains("unknown parent 'a'"));
    }

    @Test
    void cubeFieldOutsideCubeBlockIsRejected() {
        assertTrue(error("aimcube:1\npart a\npivot:[0,0,0]\npos=[0,0,0]\n").getMessage().contains("outside a cube"));
    }

    @Test
    void partKeywordOutsidePartIsRejected() {
        assertTrue(error("aimcube:1\npivot:[0,0,0]\n").getMessage().contains("outside a part"));
    }

    @Test
    void modelWithNoPartsIsRejected() {
        assertTrue(error("aimcube:1\ntexture_size:[64,64]\n").getMessage().contains("no parts"));
    }

    @Test
    void negativeCubeSizeIsRejected() {
        assertThrows(ModelFormatException.class, () -> read(
                "aimcube:1\npart a\npivot:[0,0,0]\ncube:\n tex=[0,0]\n pos=[0,0,0]\n size=[-1,1,1]\n"));
    }

    @Test
    void invalidUtf8IsRejected() {
        byte[] bytes = {'a', 'i', 'm', 'c', 'u', 'b', 'e', ':', '1', '\n', (byte) 0xC3, (byte) 0x28};
        assertThrows(ModelFormatException.class, () -> new AimCubeReader().read(bytes, "bad.aimcube"));
    }
}
