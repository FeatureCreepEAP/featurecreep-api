package featurecreep.api.model.io.pmx;

import featurecreep.api.model.Geometry;
import featurecreep.api.model.Material;
import featurecreep.api.model.Mesh;
import featurecreep.api.model.Model;
import featurecreep.api.model.ModelCapabilities;
import featurecreep.api.model.ModelFormatException;
import featurecreep.api.model.Node;
import featurecreep.api.model.Quat;
import featurecreep.api.model.Transform;
import featurecreep.api.model.Vec3;
import featurecreep.api.model.io.ModelReadResult;
import java.util.List;
import java.util.Map;

final class PmxFixtures {

    static final String UPPER = "上半身";
    static final String LEFT_LEG = "左足";

    private PmxFixtures() {
    }

    static Model handBuilt(float version) {
        Mesh mesh = new Mesh(
                new float[] {0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f},
                new float[] {0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f},
                null,
                new float[][] {{0f, 0f, 1f, 0f, 0f, 1f}},
                List.of(new Mesh.Primitive(0, new int[] {0, 1, 2})),
                null);
        Node root = node("root", "Root", 0f, 0f, 0f, mesh,
                node(UPPER, "Upper", 0f, 8f, 0f, null),
                node(LEFT_LEG, "LeftLeg", 1f, 0f, 0f, null),
                node("ik", "IK", 0f, 0f, 2f, null),
                node("append", "Append", 0f, 4f, 0f, null));

        PmxDocument.Ik ik = new PmxDocument.Ik(2, 40, 0.5f, List.of(
                new PmxDocument.IkLink(1, false, null, null),
                new PmxDocument.IkLink(0, true, new float[] {-1f, 0f, 0f}, new float[] {1f, 0f, 0f})));
        float[] tail = {0f, 1f, 0f};
        List<PmxDocument.Bone> bones = List.of(
                bone("root", "Root", 0x001E, 0, -1, tail, -1, 0f, null),
                bone(UPPER, "Upper", 0x001E, 1, -1, tail, -1, 0f, null),
                bone(LEFT_LEG, "LeftLeg", 0x001F, 2, 1, null, -1, 0f, null),
                bone("ik", "IK", 0x003E, 0, -1, tail, -1, 0f, ik),
                bone("append", "Append", 0x011E, 0, -1, tail, 1, 0.5f, null));

        float[] material = new float[28];
        for (int i = 0; i < material.length; i++) {
            material[i] = i * 0.25f;
        }
        List<PmxDocument.Morph> morphs = List.of(
                new PmxDocument.Morph("blink", "Blink", 1, 1, List.of(
                        new PmxDocument.Morph.Item(0, 0, 0, new float[] {0.1f, 0.2f, 0.3f}))),
                new PmxDocument.Morph("mat", "Mat", 4, 8, List.of(
                        new PmxDocument.Morph.Item(0, 1, 2, material))));
        List<PmxDocument.DisplayFrame> frames = List.of(new PmxDocument.DisplayFrame("frame", "Frame", 1, List.of(
                new PmxDocument.DisplayFrame.Element(0, 2),
                new PmxDocument.DisplayFrame.Element(1, 0))));
        List<PmxDocument.RigidBody> bodies = List.of(new PmxDocument.RigidBody("body", "Body", 0, 1, 0x0002, 0,
                new float[] {1f, 1f, 1f}, new float[] {0f, 8f, 0f}, new float[3], 1f, 0.5f, 0.5f, 0f, 0.5f, 0));
        List<PmxDocument.Joint> joints = List.of(new PmxDocument.Joint("joint", "Joint", 0, 0, 0,
                new float[] {0f, 8f, 0f}, new float[3], new float[] {-1f, -1f, -1f}, new float[] {1f, 1f, 1f},
                new float[] {-0.5f, 0f, 0f}, new float[] {0.5f, 0f, 0f},
                new float[] {1f, 1f, 1f}, new float[] {1f, 1f, 1f}));

        PmxDocument document = new PmxDocument(version, 0, bones, List.of(), List.of(), morphs, frames, bodies, joints);
        return new Model(List.of(root),
                List.of(new Material("skin", "pmx", Map.of("diffuse", "tex/skin.png"))),
                List.of("blink", "mat"), ModelCapabilities.any(), Map.of("name", "hand"),
                Map.<String, Object>of(PmxReader.EXTENSION, document));
    }

    static Model singleNode(Node root) {
        return new Model(List.of(root), List.of(new Material("body", "pmx", Map.of("diffuse", "body.png"))),
                List.of(), ModelCapabilities.any(), Map.of(), Map.of());
    }

    static Node node(String name, String nameEn, float x, float y, float z, Geometry geometry, Node... children) {
        Map<String, String> metadata = nameEn.isEmpty() ? Map.of() : Map.of("name.en", nameEn);
        return new Node(name, new Transform(new Vec3(x, y, z), Quat.IDENTITY, Vec3.ONE), List.of(children),
                geometry, true, metadata);
    }

    static PmxDocument.Bone bone(String name, String nameEn, int flags, int level, int tailIndex, float[] tailOffset,
            int inheritParent, float inheritRatio, PmxDocument.Ik ik) {
        return new PmxDocument.Bone(name, nameEn, flags, level, tailIndex, tailOffset, inheritParent, inheritRatio,
                null, null, null, 0, ik);
    }

    static ModelReadResult roundTrip(Model model) throws ModelFormatException {
        return new PmxReader().read(new PmxWriter().write(model), "round-trip.pmx");
    }

    static List<String> nodeNames(Model model) {
        return model.nodes().stream().map(Node::name).toList();
    }

    /** Min x, y, z followed by max x, y, z. */
    static float[] bounds(float[] positions) {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
            -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i < positions.length; i++) {
            int axis = i % 3;
            b[axis] = Math.min(b[axis], positions[i]);
            b[axis + 3] = Math.max(b[axis + 3], positions[i]);
        }
        return b;
    }
}
