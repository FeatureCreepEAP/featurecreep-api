package featurecreep.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelTest {

    private static Node node(String name, List<Node> children, Geometry geometry) {
        return new Node(name, Transform.IDENTITY, children, geometry);
    }

    private static Mesh triangle(int material) {
        return new Mesh(new float[] {0, 0, 0, 1, 0, 0, 0, 1, 0}, null, null, new float[0][],
                List.of(new Mesh.Primitive(material, new int[] {0, 1, 2})), null);
    }

    private static Model withMaterials(List<Node> roots, int materialCount) {
        List<Material> materials = new ArrayList<>();
        for (int i = 0; i < materialCount; i++) {
            materials.add(new Material("m" + i, "", Map.of()));
        }
        return new Model(roots, materials, List.of(), ModelCapabilities.any(), Map.of(), Map.of());
    }

    @Test
    void duplicateRootNamesAreRejected() {
        List<Node> roots = List.of(node("a", List.of(), null), node("a", List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> Model.of(roots));
    }

    @Test
    void duplicateNestedNamesAreRejected() {
        List<Node> roots = List.of(node("root", List.of(node("a", List.of(), null)), null),
                node("a", List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> Model.of(roots));
    }

    @Test
    void nodesAreIndexedInPreOrder() {
        Model model = Model.of(List.of(node("A", List.of(
                node("B", List.of(node("D", List.of(), null)), null),
                node("C", List.of(), null)), null)));
        assertEquals(4, model.nodeCount());
        assertEquals(0, model.indexOf("A").getAsInt());
        assertEquals(1, model.indexOf("B").getAsInt());
        assertEquals(2, model.indexOf("D").getAsInt());
        assertEquals(3, model.indexOf("C").getAsInt());
        assertTrue(model.indexOf("missing").isEmpty());
    }

    @Test
    void meshReferencingMissingMaterialIsRejected() {
        List<Node> roots = List.of(node("m", List.of(), triangle(2)));
        assertThrows(IllegalArgumentException.class, () -> withMaterials(roots, 2));
    }

    @Test
    void skinJointOutsideTheModelIsRejected() {
        Skin skin = new Skin(new int[] {5}, 1, new int[] {0, 0, 0}, new float[] {1, 1, 1});
        Mesh mesh = new Mesh(new float[] {0, 0, 0, 1, 0, 0, 0, 1, 0}, null, null, new float[0][],
                List.of(new Mesh.Primitive(0, new int[] {0, 1, 2})), skin);
        List<Node> roots = List.of(node("m", List.of(), mesh));
        assertThrows(IllegalArgumentException.class, () -> withMaterials(roots, 1));
    }

    @Test
    void parametricGeometryIsAcceptedAndTessellates() {
        Parametric procedural = tolerance -> triangle(0);
        Model model = Model.of(List.of(node("p", List.of(), procedural)));
        Geometry geometry = model.nodes().get(0).geometry();
        assertTrue(geometry instanceof Parametric);
        assertEquals(3, ((Parametric) geometry).tessellate(0.1).vertexCount());
    }

    @Test
    void extensionsAreTypedByFormatId() {
        Model model = new Model(List.of(), List.of(), List.of(), ModelCapabilities.any(), Map.of(),
                Map.of("pmx", "rigid-bodies"));
        assertEquals("rigid-bodies", model.extension("pmx", String.class).orElseThrow());
        assertFalse(model.extension("pmx", Integer.class).isPresent());
        assertFalse(model.extension("pdx-mesh", String.class).isPresent());
    }
}
