package featurecreep.api.model.io.pmx;

import featurecreep.api.model.Cuboid;
import featurecreep.api.model.Cuboids;
import featurecreep.api.model.Geometry;
import featurecreep.api.model.Material;
import featurecreep.api.model.Mesh;
import featurecreep.api.model.Model;
import featurecreep.api.model.ModelFormatException;
import featurecreep.api.model.Node;
import featurecreep.api.model.Parametric;
import featurecreep.api.model.Quat;
import featurecreep.api.model.Skin;
import featurecreep.api.model.Space;
import featurecreep.api.model.Vec3;
import featurecreep.api.model.io.FormatDescriptor;
import featurecreep.api.model.io.ModelWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Writes PMX 2.0, or 2.1 when the model's {@code "pmx"} extension was read from a 2.1 file. The model is mirrored into
 * PMX space first. Bones are nodes in pre-order, and a bone order kept in the extension is preserved so that PMX indices
 * in morphs, IK and rigid bodies stay valid. Each mesh is written with its node's translation baked into its vertices,
 * so node transforms must be pure translation. Cuboids are unwrapped onto material 0 with a 64 by 64 texture. Meshes
 * without a skin bind rigidly to their node.
 */
public final class PmxWriter implements ModelWriter {

    private static final float[] ORIGIN = {0f, 0f, 0f};
    private static final float DEFAULT_CUBOID_TEXTURE = 64f;

    @Override
    public FormatDescriptor descriptor() {
        return PmxReader.DESCRIPTOR;
    }

    @Override
    public byte[] write(Model original) throws ModelFormatException {
        Model model = Space.mirrorY(original);
        PmxDocument document = model.extension(PmxReader.EXTENSION, PmxDocument.class).orElse(null);
        float version = document != null && Float.compare(document.version(), 2.1f) == 0 ? 2.1f : 2.0f;
        int addCount = additionalUvCount(model);

        Map<String, Placed> placed = new LinkedHashMap<>();
        for (Node root : model.roots()) {
            place(root, null, ORIGIN, placed);
        }
        List<String> order = boneOrder(document, placed);
        Map<String, Integer> pmxIndex = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            pmxIndex.put(order.get(i), i);
        }
        Gathered gathered = gather(model, placed, pmxIndex, addCount);

        List<PmxDocument.Material> materialRecords = new ArrayList<>(model.materials().size());
        Map<String, Integer> textures = new LinkedHashMap<>();
        int[][] slots = new int[model.materials().size()][3];
        for (int i = 0; i < model.materials().size(); i++) {
            Material m = model.materials().get(i);
            PmxDocument.Material nativeMaterial = document != null && document.materials().size() == model.materials().size()
                    ? document.materials().get(i) : null;
            PmxDocument.Material record = nativeMaterial == null ? defaultMaterial(m.name())
                    : withName(m.name(), nativeMaterial);
            materialRecords.add(record);
            slots[i][0] = textureIndex(textures, m.textures().get("diffuse"));
            slots[i][1] = textureIndex(textures, m.textures().get("sphere"));
            slots[i][2] = record.sharedToon() == 0 ? textureIndex(textures, m.textures().get("toon")) : -1;
        }

        PmxBinaryWriter w = new PmxBinaryWriter();
        writeHeader(w, model, version, addCount);
        writeVertices(w, gathered, document, addCount);
        writeFaces(w, gathered.faces());
        w.s32(textures.size());
        for (String path : textures.keySet()) {
            w.text(path);
        }
        w.s32(materialRecords.size());
        for (int i = 0; i < materialRecords.size(); i++) {
            PmxSectionWriter.material(w, materialRecords.get(i), slots[i][0], slots[i][1], slots[i][2],
                    gathered.faces().get(i).size());
        }
        writeBones(w, document, placed, order, pmxIndex);
        writeMorphs(w, model.morphNames(), document);
        List<PmxDocument.DisplayFrame> frames = document == null ? List.of() : document.displayFrames();
        w.s32(frames.size());
        for (PmxDocument.DisplayFrame frame : frames) {
            PmxSectionWriter.displayFrame(w, frame);
        }
        List<PmxDocument.RigidBody> rigidBodies = document == null ? List.of() : document.rigidBodies();
        w.s32(rigidBodies.size());
        for (PmxDocument.RigidBody body : rigidBodies) {
            PmxSectionWriter.rigidBody(w, body);
        }
        List<PmxDocument.Joint> joints = document == null ? List.of() : document.joints();
        w.s32(joints.size());
        for (PmxDocument.Joint joint : joints) {
            PmxSectionWriter.joint(w, joint);
        }
        if (version == 2.1f) {
            w.s32(0);
        }
        return w.toByteArray();
    }

    private static void writeHeader(PmxBinaryWriter w, Model model, float version, int addCount) {
        w.u8('P');
        w.u8('M');
        w.u8('X');
        w.u8(' ');
        w.f32(version);
        w.u8(8);
        w.u8(0);
        w.u8(addCount);
        for (int i = 0; i < 6; i++) {
            w.u8(PmxBinaryWriter.INDEX_SIZE);
        }
        w.text(model.metadata().getOrDefault("name", ""));
        w.text(model.metadata().getOrDefault("name.en", ""));
        w.text(model.metadata().getOrDefault("comment", ""));
        w.text(model.metadata().getOrDefault("comment.en", ""));
    }

    private static void writeVertices(PmxBinaryWriter w, Gathered g, PmxDocument document, int addCount) {
        int total = g.positions().length / 3;
        w.s32(total);
        List<PmxDocument.Vertex> extras = document != null && document.vertices().size() == total
                ? document.vertices() : null;
        for (int v = 0; v < total; v++) {
            put(w, g.positions(), v * 3, 3);
            put(w, g.normals(), v * 3, 3);
            put(w, g.uv(), v * 2, 2);
            PmxDocument.Vertex extra = extras == null ? null : extras.get(v);
            for (int set = 0; set < addCount; set++) {
                put(w, g.addUv()[set], v * 2, 2);
                boolean hasZw = extra != null && extra.additionalUv().length >= set * 4 + 4;
                w.f32(hasZw ? extra.additionalUv()[set * 4 + 2] : 0f);
                w.f32(hasZw ? extra.additionalUv()[set * 4 + 3] : 0f);
            }
            writeSkin(w, g, v, extra);
            w.f32(extra != null ? extra.edgeScale() : 1f);
        }
    }

    /** Joints are compacted to the front of each vertex, so the count of non-negative slots is the influence count. */
    private static void writeSkin(PmxBinaryWriter w, Gathered g, int v, PmxDocument.Vertex extra) {
        int[] joints = g.joints();
        float[] weights = g.weights();
        int type = extra == null ? -1 : extra.skinType();
        int count = 0;
        for (int k = 0; k < 4; k++) {
            if (joints[v * 4 + k] >= 0) {
                count++;
            }
        }
        int j0 = joints[v * 4];
        int j1 = joints[v * 4 + 1];
        float w0 = weights[v * 4];
        boolean explicit = type == PmxSectionReader.SKIN_BDEF4 || type == PmxSectionReader.SKIN_QDEF;
        if (type == PmxSectionReader.SKIN_SDEF && extra.sdef() != null && count >= 1 && count <= 2) {
            w.u8(PmxSectionReader.SKIN_SDEF);
            w.index(j0);
            w.index(count == 2 ? j1 : j0);
            w.f32(w0);
            w.floats(extra.sdef());
        } else if (count <= 1 && !explicit) {
            w.u8(PmxSectionReader.SKIN_BDEF1);
            w.index(count == 1 ? j0 : -1);
        } else if (count == 2 && !explicit) {
            w.u8(PmxSectionReader.SKIN_BDEF2);
            w.index(j0);
            w.index(j1);
            w.f32(w0);
        } else {
            w.u8(type == PmxSectionReader.SKIN_QDEF ? PmxSectionReader.SKIN_QDEF : PmxSectionReader.SKIN_BDEF4);
            for (int k = 0; k < 4; k++) {
                w.index(joints[v * 4 + k]);
            }
            put(w, weights, v * 4, 4);
        }
    }

    private static void writeFaces(PmxBinaryWriter w, List<List<Integer>> faces) {
        int total = 0;
        for (List<Integer> material : faces) {
            total += material.size();
        }
        w.s32(total);
        for (List<Integer> material : faces) {
            for (int index : material) {
                w.index(index);
            }
        }
    }

    private static void writeBones(PmxBinaryWriter w, PmxDocument document, Map<String, Placed> placed,
            List<String> order, Map<String, Integer> pmxIndex) {
        Map<String, PmxDocument.Bone> nativeBones = new HashMap<>();
        if (document != null) {
            for (PmxDocument.Bone bone : document.bones()) {
                nativeBones.putIfAbsent(bone.name(), bone);
            }
        }
        w.s32(order.size());
        for (String name : order) {
            Placed p = placed.get(name);
            PmxDocument.Bone base = nativeBones.get(name);
            String nameEn = p.node().metadata().getOrDefault("name.en", base == null ? "" : base.nameEn());
            int flags = base == null ? PmxDocument.BONE_FLAG_DEFAULT : base.flags();
            flags = p.node().visible() ? flags | PmxDocument.BONE_FLAG_VISIBLE : flags & ~PmxDocument.BONE_FLAG_VISIBLE;
            PmxDocument.Bone bone = base == null
                    ? new PmxDocument.Bone(name, nameEn, flags, 0, -1, new float[3], -1, 0f, null, null, null, 0, null)
                    : new PmxDocument.Bone(name, nameEn, flags, base.level(), base.tailIndex(), base.tailOffset(),
                            base.inheritParent(), base.inheritRatio(), base.fixedAxis(), base.localX(), base.localZ(),
                            base.externalParent(), base.ik());
            int parent = p.parent() == null ? -1 : pmxIndex.get(p.parent());
            PmxSectionWriter.bone(w, bone, p.abs(), parent);
        }
    }

    private static void writeMorphs(PmxBinaryWriter w, List<String> names, PmxDocument document) {
        w.s32(names.size());
        for (int i = 0; i < names.size(); i++) {
            PmxDocument.Morph base = document != null && i < document.morphs().size()
                    ? document.morphs().get(i) : new PmxDocument.Morph("", "", 4, 1, List.of());
            PmxSectionWriter.morph(w, new PmxDocument.Morph(names.get(i), base.nameEn(), base.panel(), base.kind(),
                    base.items()));
        }
    }

    private static Gathered gather(Model model, Map<String, Placed> placed, Map<String, Integer> pmxIndex,
            int addCount) throws ModelFormatException {
        List<Node> meshNodes = new ArrayList<>();
        List<Mesh> meshes = new ArrayList<>();
        for (Node node : model.nodes()) {
            Mesh mesh = asMesh(node.geometry(), model.materials().size());
            if (mesh != null) {
                meshNodes.add(node);
                meshes.add(mesh);
            }
        }
        int total = 0;
        for (Mesh mesh : meshes) {
            total += mesh.vertexCount();
        }
        float[] positions = new float[total * 3];
        float[] normals = new float[total * 3];
        float[] uv = new float[total * 2];
        float[][] addUv = new float[addCount][total * 2];
        int[] joints = new int[total * 4];
        Arrays.fill(joints, -1);
        float[] weights = new float[total * 4];
        List<List<Integer>> faces = new ArrayList<>(model.materials().size());
        for (int i = 0; i < model.materials().size(); i++) {
            faces.add(new ArrayList<>());
        }
        int base = 0;
        for (int m = 0; m < meshes.size(); m++) {
            Node node = meshNodes.get(m);
            Mesh mesh = meshes.get(m);
            float[] origin = placed.get(node.name()).abs();
            for (int v = 0; v < mesh.vertexCount(); v++) {
                int dst = base + v;
                for (int k = 0; k < 3; k++) {
                    positions[dst * 3 + k] = mesh.positions()[v * 3 + k] + origin[k];
                }
                if (mesh.normals() != null) {
                    System.arraycopy(mesh.normals(), v * 3, normals, dst * 3, 3);
                }
                if (mesh.uvSets().length > 0) {
                    uv[dst * 2] = mesh.uvSets()[0][v * 2];
                    uv[dst * 2 + 1] = mesh.uvSets()[0][v * 2 + 1];
                }
                for (int set = 0; set + 1 < mesh.uvSets().length; set++) {
                    addUv[set][dst * 2] = mesh.uvSets()[set + 1][v * 2];
                    addUv[set][dst * 2 + 1] = mesh.uvSets()[set + 1][v * 2 + 1];
                }
                writeInfluences(model, mesh, node, pmxIndex, v, dst, joints, weights);
            }
            for (Mesh.Primitive primitive : mesh.primitives()) {
                for (int index : primitive.indices()) {
                    faces.get(primitive.material()).add(index + base);
                }
            }
            base += mesh.vertexCount();
        }
        return new Gathered(positions, normals, uv, addUv, joints, weights, faces);
    }

    private static void writeInfluences(Model model, Mesh mesh, Node node, Map<String, Integer> pmxIndex, int v,
            int dst, int[] joints, float[] weights) throws ModelFormatException {
        Skin skin = mesh.skin();
        if (skin == null) {
            joints[dst * 4] = pmxIndex.get(node.name());
            weights[dst * 4] = 1f;
            return;
        }
        int slots = 0;
        for (int k = 0; k < skin.influences(); k++) {
            int slot = skin.jointIndices()[v * skin.influences() + k];
            float weight = skin.weights()[v * skin.influences() + k];
            if (slot < 0 || weight <= 0f) {
                continue;
            }
            if (slots == 4) {
                throw new ModelFormatException("PMX allows at most 4 bone influences per vertex, node " + node.name());
            }
            joints[dst * 4 + slots] = pmxIndex.get(model.nodes().get(skin.jointNodes()[slot]).name());
            weights[dst * 4 + slots] = weight;
            slots++;
        }
    }

    private static Mesh asMesh(Geometry geometry, int materialCount) throws ModelFormatException {
        if (geometry == null) {
            return null;
        }
        if (geometry instanceof Mesh mesh) {
            return mesh;
        }
        if (geometry instanceof Cuboid cuboid) {
            if (materialCount == 0) {
                throw new ModelFormatException("cuboid geometry needs a material to be written as PMX");
            }
            return Cuboids.toMesh(cuboid, DEFAULT_CUBOID_TEXTURE, DEFAULT_CUBOID_TEXTURE, 0);
        }
        if (geometry instanceof Parametric) {
            throw new ModelFormatException("parametric geometry must be tessellated before writing PMX");
        }
        throw new ModelFormatException("unsupported geometry for PMX");
    }

    private static void place(Node node, String parent, float[] parentAbs, Map<String, Placed> placed)
            throws ModelFormatException {
        Quat rotation = node.transform().rotation();
        Vec3 scale = node.transform().scale();
        boolean identityRotation = rotation.x() == 0f && rotation.y() == 0f && rotation.z() == 0f && rotation.w() == 1f;
        boolean unitScale = scale.x() == 1f && scale.y() == 1f && scale.z() == 1f;
        if (!identityRotation || !unitScale) {
            throw new ModelFormatException("node " + node.name() + " has rotation or scale, which PMX bones cannot express");
        }
        Vec3 t = node.transform().translation();
        float[] abs = {parentAbs[0] + t.x(), parentAbs[1] + t.y(), parentAbs[2] + t.z()};
        placed.put(node.name(), new Placed(node, abs, parent));
        for (Node child : node.children()) {
            place(child, node.name(), abs, placed);
        }
    }

    private static List<String> boneOrder(PmxDocument document, Map<String, Placed> placed)
            throws ModelFormatException {
        LinkedHashSet<String> order = new LinkedHashSet<>();
        if (document != null) {
            for (PmxDocument.Bone bone : document.bones()) {
                if (!placed.containsKey(bone.name())) {
                    throw new ModelFormatException("PMX bone " + bone.name() + " is missing from the model");
                }
                order.add(bone.name());
            }
        }
        order.addAll(placed.keySet());
        return new ArrayList<>(order);
    }

    private static int additionalUvCount(Model model) throws ModelFormatException {
        int max = 0;
        for (Node node : model.nodes()) {
            if (node.geometry() instanceof Mesh mesh) {
                max = Math.max(max, mesh.uvSets().length - 1);
            }
        }
        if (max > 4) {
            throw new ModelFormatException("PMX allows at most 4 additional UV sets");
        }
        return max;
    }

    private static int textureIndex(Map<String, Integer> textures, String path) {
        if (path == null) {
            return -1;
        }
        Integer existing = textures.get(path);
        if (existing != null) {
            return existing;
        }
        int index = textures.size();
        textures.put(path, index);
        return index;
    }

    private static PmxDocument.Material defaultMaterial(String name) {
        return new PmxDocument.Material(name, "", new float[] {0.8f, 0.8f, 0.8f, 1f}, new float[3], 5f,
                new float[] {0.5f, 0.5f, 0.5f}, 0, new float[] {0f, 0f, 0f, 1f}, 1f, 0, 0, 0, "");
    }

    private static PmxDocument.Material withName(String name, PmxDocument.Material nm) {
        return new PmxDocument.Material(name, nm.nameEn(), nm.diffuse(), nm.specular(), nm.specularity(), nm.ambient(),
                nm.flags(), nm.edgeColor(), nm.edgeSize(), nm.sphereMode(), nm.sharedToon(), nm.toonIndex(),
                nm.comment());
    }

    private static void put(PmxBinaryWriter w, float[] values, int from, int length) {
        for (int i = 0; i < length; i++) {
            w.f32(values[from + i]);
        }
    }

    private record Placed(Node node, float[] abs, String parent) {
    }

    private record Gathered(float[] positions, float[] normals, float[] uv, float[][] addUv, int[] joints,
            float[] weights, List<List<Integer>> faces) {
    }
}
