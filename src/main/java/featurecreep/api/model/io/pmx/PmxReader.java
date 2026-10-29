package featurecreep.api.model.io.pmx;

import featurecreep.api.model.Geometry;
import featurecreep.api.model.Material;
import featurecreep.api.model.Mesh;
import featurecreep.api.model.Model;
import featurecreep.api.model.ModelCapabilities;
import featurecreep.api.model.ModelFormatException;
import featurecreep.api.model.Node;
import featurecreep.api.model.Quat;
import featurecreep.api.model.Skin;
import featurecreep.api.model.Space;
import featurecreep.api.model.Transform;
import featurecreep.api.model.Vec3;
import featurecreep.api.model.io.FormatDescriptor;
import featurecreep.api.model.io.ModelReadResult;
import featurecreep.api.model.io.ModelReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads PMX 2.0 and 2.1. Bones become nodes in pre-order, with the Japanese name as the node name and the English name
 * in metadata {@code name.en}. The mesh hangs on the first bone's node with one primitive per material. Everything else
 * is kept in the {@code "pmx"} extension as a {@link PmxDocument}. Rigid bodies and joints are read but not simulated.
 * The result is mirrored from PMX space into model space.
 */
public final class PmxReader implements ModelReader {

    static final FormatDescriptor DESCRIPTOR = new FormatDescriptor("pmx", Set.of("pmx"), PmxReader::sniff);
    static final String EXTENSION = "pmx";

    private static final float[] ZERO = {0f, 0f, 0f};

    @Override
    public FormatDescriptor descriptor() {
        return DESCRIPTOR;
    }

    static boolean sniff(byte[] head) {
        return head.length >= 4 && head[0] == 'P' && head[1] == 'M' && head[2] == 'X' && head[3] == ' ';
    }

    @Override
    public ModelReadResult read(byte[] data, String sourceName) throws ModelFormatException {
        PmxBinaryReader bin = new PmxBinaryReader(data, sourceName);
        PmxSectionReader sections = new PmxSectionReader(bin);
        PmxSectionReader.Header h = sections.header();

        Map<String, String> metadata = new HashMap<>();
        putNonEmpty(metadata, "name", bin.text(h.encoding()));
        putNonEmpty(metadata, "name.en", bin.text(h.encoding()));
        putNonEmpty(metadata, "comment", bin.text(h.encoding()));
        putNonEmpty(metadata, "comment.en", bin.text(h.encoding()));

        PmxSectionReader.Vertices vertices = sections.vertices(h);
        int[] faces = sections.faces(h);
        List<String> textures = sections.textures(h);
        List<PmxSectionReader.MaterialRaw> materialsRaw = sections.materials(h);
        List<PmxSectionReader.BoneRaw> bonesRaw = sections.bones(h);
        PmxSectionReader.Morphs morphs = sections.morphs(h);
        List<PmxDocument.DisplayFrame> frames = sections.displayFrames(h);
        List<PmxDocument.RigidBody> rigidBodies = sections.rigidBodies(h);
        List<PmxDocument.Joint> joints = sections.joints(h);
        if (Float.compare(h.version(), 2.1f) == 0 && bin.s32() != 0) {
            throw error(sourceName, "PMX 2.1 soft bodies are not supported");
        }

        validate(sourceName, vertices, faces, textures, materialsRaw, bonesRaw);

        List<String> warnings = new ArrayList<>();
        if (vertices.hasSdef()) {
            warnings.add("SDEF vertices are approximated as BDEF2; C, R0 and R1 are kept in the pmx extension");
        }
        if (vertices.hasQdef()) {
            warnings.add("QDEF vertices are approximated as BDEF4");
        }

        PmxDocument document = new PmxDocument(h.version(), h.additionalUvCount(),
                bonesRaw.stream().map(PmxSectionReader.BoneRaw::bone).toList(), vertices.extras(),
                materialsRaw.stream().map(PmxSectionReader.MaterialRaw::material).toList(),
                morphs.morphs(), frames, rigidBodies, joints);

        Model model;
        try {
            model = buildModel(document, vertices, faces, textures, materialsRaw, bonesRaw, metadata);
        } catch (IllegalArgumentException e) {
            throw error(sourceName, e.getMessage());
        }
        return new ModelReadResult(Space.mirrorY(model), warnings);
    }

    private static Model buildModel(PmxDocument document, PmxSectionReader.Vertices vertices, int[] faces,
            List<String> textures, List<PmxSectionReader.MaterialRaw> materialsRaw,
            List<PmxSectionReader.BoneRaw> bonesRaw, Map<String, String> metadata) throws ModelFormatException {
        Hierarchy hierarchy = Hierarchy.of(bonesRaw);
        int addUv = document.additionalUvCount();
        float[] origin = bonesRaw.isEmpty() ? ZERO : bonesRaw.get(0).position();
        Skin skin = bonesRaw.isEmpty() ? null
                : new Skin(hierarchy.nodeOf(), 4, vertices.joints(), vertices.weights());
        Mesh mesh = vertices.positions().length == 0 ? null
                : buildMesh(vertices, faces, materialsRaw, addUv, origin, skin);

        List<Node> roots = new ArrayList<>();
        if (bonesRaw.isEmpty()) {
            if (mesh != null) {
                roots.add(new Node("mesh", Transform.IDENTITY, List.of(), mesh));
            }
        } else {
            for (int root : hierarchy.roots()) {
                roots.add(buildBone(root, hierarchy, bonesRaw, mesh));
            }
        }

        List<Material> materials = new ArrayList<>(materialsRaw.size());
        for (PmxSectionReader.MaterialRaw raw : materialsRaw) {
            Map<String, String> slots = new HashMap<>();
            if (raw.texture() >= 0) {
                slots.put("diffuse", textures.get(raw.texture()));
            }
            if (raw.sphere() >= 0) {
                slots.put("sphere", textures.get(raw.sphere()));
            }
            if (raw.material().sharedToon() == 0 && raw.toon() >= 0) {
                slots.put("toon", textures.get(raw.toon()));
            }
            materials.add(new Material(raw.material().name(), "pmx", slots));
        }
        List<String> morphNames = document.morphs().stream().map(PmxDocument.Morph::name).toList();
        return new Model(roots, materials, morphNames, ModelCapabilities.any(), metadata,
                Map.<String, Object>of(EXTENSION, document));
    }

    private static Mesh buildMesh(PmxSectionReader.Vertices vertices, int[] faces,
            List<PmxSectionReader.MaterialRaw> materialsRaw, int addUv, float[] origin, Skin skin) {
        int count = vertices.positions().length / 3;
        float[] positions = new float[count * 3];
        for (int v = 0; v < count; v++) {
            for (int k = 0; k < 3; k++) {
                positions[v * 3 + k] = vertices.positions()[v * 3 + k] - origin[k];
            }
        }
        float[][] uvSets = new float[1 + addUv][];
        uvSets[0] = vertices.uv();
        int addLen = addUv * 4;
        for (int set = 0; set < addUv; set++) {
            float[] uv = new float[count * 2];
            for (int v = 0; v < count; v++) {
                uv[v * 2] = vertices.additionalUv()[v * addLen + set * 4];
                uv[v * 2 + 1] = vertices.additionalUv()[v * addLen + set * 4 + 1];
            }
            uvSets[1 + set] = uv;
        }
        List<Mesh.Primitive> primitives = new ArrayList<>();
        int offset = 0;
        for (int m = 0; m < materialsRaw.size(); m++) {
            int n = materialsRaw.get(m).faceCount();
            if (n > 0) {
                primitives.add(new Mesh.Primitive(m, Arrays.copyOfRange(faces, offset, offset + n)));
            }
            offset += n;
        }
        return new Mesh(positions, vertices.normals(), null, uvSets, primitives, skin);
    }

    private static Node buildBone(int bone, Hierarchy hierarchy, List<PmxSectionReader.BoneRaw> bonesRaw, Mesh mesh) {
        PmxSectionReader.BoneRaw raw = bonesRaw.get(bone);
        float[] parentPos = raw.parent() < 0 ? ZERO : bonesRaw.get(raw.parent()).position();
        float[] p = raw.position();
        Vec3 translation = new Vec3(p[0] - parentPos[0], p[1] - parentPos[1], p[2] - parentPos[2]);
        List<Node> children = new ArrayList<>();
        for (int child : hierarchy.children().get(bone)) {
            children.add(buildBone(child, hierarchy, bonesRaw, mesh));
        }
        Geometry geometry = bone == 0 ? mesh : null;
        Map<String, String> metadata = raw.bone().nameEn().isEmpty() ? Map.of()
                : Map.of("name.en", raw.bone().nameEn());
        boolean visible = (raw.bone().flags() & PmxDocument.BONE_FLAG_VISIBLE) != 0;
        return new Node(raw.bone().name(), new Transform(translation, Quat.IDENTITY, Vec3.ONE), children, geometry,
                visible, metadata);
    }

    private static void validate(String source, PmxSectionReader.Vertices vertices, int[] faces,
            List<String> textures, List<PmxSectionReader.MaterialRaw> materials,
            List<PmxSectionReader.BoneRaw> bones) throws ModelFormatException {
        int vertexCount = vertices.positions().length / 3;
        int boneCount = bones.size();
        for (int v = 0; v < vertexCount; v++) {
            for (int k = 0; k < 4; k++) {
                int joint = vertices.joints()[v * 4 + k];
                if (joint < -1 || joint >= boneCount) {
                    throw error(source, "vertex " + v + " references missing bone " + joint);
                }
            }
        }
        for (int face : faces) {
            if (face < 0 || face >= vertexCount) {
                throw error(source, "face references missing vertex " + face);
            }
        }
        int total = 0;
        for (PmxSectionReader.MaterialRaw m : materials) {
            total += m.faceCount();
            for (int tex : new int[] {m.texture(), m.sphere(), m.toon()}) {
                if (tex < -1 || tex >= textures.size()) {
                    throw error(source, "material " + m.material().name() + " references missing texture " + tex);
                }
            }
        }
        if (total != faces.length) {
            throw error(source, "material face counts sum to " + total + " but the model has " + faces.length);
        }
        for (PmxSectionReader.BoneRaw b : bones) {
            if (b.parent() < -1 || b.parent() >= boneCount) {
                throw error(source, "bone " + b.bone().name() + " has missing parent " + b.parent());
            }
        }
    }

    private static void putNonEmpty(Map<String, String> map, String key, String value) {
        if (!value.isEmpty()) {
            map.put(key, value);
        }
    }

    static ModelFormatException error(String source, String message) {
        return new ModelFormatException(source + ": " + message);
    }

    /** Pre-order node numbering that matches {@link Model}'s indexing, plus the child lists used to build nodes. */
    private record Hierarchy(int[] nodeOf, List<List<Integer>> children, List<Integer> roots) {

        static Hierarchy of(List<PmxSectionReader.BoneRaw> bones) throws ModelFormatException {
            int n = bones.size();
            List<List<Integer>> children = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                children.add(new ArrayList<>());
            }
            List<Integer> roots = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int parent = bones.get(i).parent();
                if (parent < 0) {
                    roots.add(i);
                } else {
                    children.get(parent).add(i);
                }
            }
            int[] nodeOf = new int[n];
            Arrays.fill(nodeOf, -1);
            int[] next = {0};
            for (int root : roots) {
                number(root, children, nodeOf, next);
            }
            for (int node : nodeOf) {
                if (node < 0) {
                    throw new ModelFormatException("bone hierarchy contains a cycle");
                }
            }
            return new Hierarchy(nodeOf, children, roots);
        }

        private static void number(int bone, List<List<Integer>> children, int[] nodeOf, int[] next) {
            nodeOf[bone] = next[0]++;
            for (int child : children.get(bone)) {
                number(child, children, nodeOf, next);
            }
        }
    }
}
