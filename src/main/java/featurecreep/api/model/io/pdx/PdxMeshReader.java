package featurecreep.api.model.io.pdx;

import featurecreep.api.model.Space;
import featurecreep.api.model.Skin;
import featurecreep.api.model.Material;
import featurecreep.api.model.Mesh;
import featurecreep.api.model.Model;
import featurecreep.api.model.ModelCapabilities;
import featurecreep.api.model.ModelFormatException;
import featurecreep.api.model.Node;
import featurecreep.api.model.Quat;
import featurecreep.api.model.Transform;
import featurecreep.api.model.Vec3;
import featurecreep.api.model.io.FormatDescriptor;
import featurecreep.api.model.io.ModelReadResult;
import featurecreep.api.model.io.ModelReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a PDX {@code .mesh}. Each {@code object} becomes a root node, each shape a child node carrying its {@code lod},
 * and each mesh element a leaf node with one primitive. Skeleton bones become nested transform nodes under their shape,
 * and skin joints refer to them. Locators become root nodes tagged with {@code pdx.kind=locator}.
 */
public final class PdxMeshReader implements ModelReader {

    static final String ID = "pdx-mesh";
    static final String KIND_KEY = "pdx.kind";
    static final String KIND_LOCATOR = "locator";
    static final String PARENT_KEY = "pdx.parent";

    private static final FormatDescriptor DESCRIPTOR =
            new FormatDescriptor(ID, Set.of("mesh"), PdxBinary::sniff);

    @Override
    public FormatDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ModelReadResult read(byte[] data, String sourceName) throws ModelFormatException {
        PdxElement root = PdxBinary.parse(data, sourceName);
        try {
            return build(root, sourceName);
        } catch (IllegalArgumentException e) {
            throw new ModelFormatException(sourceName + ": " + e.getMessage());
        }
    }

    private static ModelReadResult build(PdxElement root, String source) throws ModelFormatException {
        Layout layout = new Layout(source);
        List<PdxElement> objects = root.childrenNamed("object");
        for (PdxElement object : objects) {
            layout.visitObject(object);
        }
        PdxElement locators = root.child("locator");
        if (locators != null) {
            for (PdxElement locator : locators.children) {
                layout.name(locator, locator.name);
            }
        }

        Builder builder = new Builder(layout, source);
        List<Node> roots = new ArrayList<>();
        for (PdxElement object : objects) {
            roots.add(builder.object(object));
        }
        if (locators != null) {
            for (PdxElement locator : locators.children) {
                roots.add(builder.locator(locator));
            }
        }
        Model model = new Model(roots, builder.materials, List.of(), ModelCapabilities.any(), Map.of(), Map.of());
        return new ModelReadResult(Space.mirrorY(model), List.of());
    }

    /** Assigns unique node names and records the preorder of nodes, which fixes the joint indices. */
    private static final class Layout {

        private final String source;
        private final Map<PdxElement, String> names = new IdentityHashMap<>();
        private final Map<PdxElement, BoneTree> trees = new IdentityHashMap<>();
        private final List<String> preorder = new ArrayList<>();
        private final Set<String> used = new HashSet<>();

        Layout(String source) {
            this.source = source;
        }

        void visitObject(PdxElement object) throws ModelFormatException {
            name(object, object.name);
            for (PdxElement shape : object.children) {
                name(shape, shape.name);
                for (PdxElement mesh : shape.childrenNamed("mesh")) {
                    name(mesh, shape.name + "_mesh");
                }
                PdxElement skeleton = shape.child("skeleton");
                if (skeleton != null) {
                    BoneTree tree = new BoneTree(skeleton, source);
                    trees.put(skeleton, tree);
                    for (PdxElement bone : tree.roots()) {
                        visitBone(bone, tree);
                    }
                }
            }
        }

        private void visitBone(PdxElement bone, BoneTree tree) {
            name(bone, bone.name);
            for (PdxElement child : tree.childrenOf(bone)) {
                visitBone(child, tree);
            }
        }

        void name(PdxElement element, String preferred) {
            String candidate = preferred;
            for (int n = 2; !used.add(candidate); n++) {
                candidate = preferred + "_" + n;
            }
            names.put(element, candidate);
            preorder.add(candidate);
        }

        String nameOf(PdxElement element) {
            return names.get(element);
        }

        BoneTree tree(PdxElement skeleton) {
            return trees.get(skeleton);
        }

        Map<String, Integer> indexByName() {
            Map<String, Integer> index = new HashMap<>();
            for (int i = 0; i < preorder.size(); i++) {
                index.put(preorder.get(i), i);
            }
            return index;
        }
    }

    /** Bones of one skeleton. Parents are referenced by index, so the tree is rebuilt from {@code pa} links. */
    private static final class BoneTree {

        private final List<PdxElement> roots = new ArrayList<>();
        private final Map<PdxElement, List<PdxElement>> children = new IdentityHashMap<>();
        private final Map<Integer, PdxElement> byIx = new LinkedHashMap<>();
        private final int count;

        BoneTree(PdxElement skeleton, String source) throws ModelFormatException {
            List<PdxElement> bones = skeleton.children;
            for (PdxElement bone : bones) {
                int[] ix = bone.ints("ix");
                if (ix == null || ix.length != 1) {
                    throw PdxBinary.error(source, 0, "bone " + bone.name + " has no index");
                }
                if (byIx.put(ix[0], bone) != null) {
                    throw PdxBinary.error(source, 0, "duplicate bone index " + ix[0]);
                }
                float[] tx = bone.floats("tx");
                if (tx == null || tx.length != 12) {
                    throw PdxBinary.error(source, 0, "bone " + bone.name + " needs a 12-float tx");
                }
            }
            this.count = byIx.size();
            for (PdxElement bone : bones) {
                int[] pa = bone.ints("pa");
                if (pa == null) {
                    roots.add(bone);
                    continue;
                }
                PdxElement parent = byIx.get(pa[0]);
                if (parent == null) {
                    throw PdxBinary.error(source, 0, "bone " + bone.name + " has missing parent " + pa[0]);
                }
                children.computeIfAbsent(parent, k -> new ArrayList<>()).add(bone);
            }
            int reached = 0;
            List<PdxElement> stack = new ArrayList<>(roots);
            while (!stack.isEmpty()) {
                PdxElement bone = stack.remove(stack.size() - 1);
                reached++;
                stack.addAll(childrenOf(bone));
            }
            if (reached != count) {
                throw PdxBinary.error(source, 0, "skeleton has cyclic bone parents");
            }
        }

        List<PdxElement> roots() {
            return roots;
        }

        List<PdxElement> childrenOf(PdxElement bone) {
            return children.getOrDefault(bone, List.of());
        }

        int count() {
            return count;
        }

        PdxElement byIndex(int ix) {
            return byIx.get(ix);
        }
    }

    private static final class Builder {

        private final Layout layout;
        private final String source;
        private final Map<String, Integer> index;
        private final List<Material> materials = new ArrayList<>();
        private final Map<String, Integer> materialIds = new HashMap<>();

        Builder(Layout layout, String source) {
            this.layout = layout;
            this.source = source;
            this.index = layout.indexByName();
        }

        Node object(PdxElement object) throws ModelFormatException {
            List<Node> shapes = new ArrayList<>();
            for (PdxElement shape : object.children) {
                shapes.add(shape(shape));
            }
            return new Node(layout.nameOf(object), Transform.IDENTITY, shapes, null);
        }

        private Node shape(PdxElement shape) throws ModelFormatException {
            Map<String, String> metadata = new HashMap<>();
            int[] lod = shape.ints("lod");
            if (lod != null && lod.length > 0) {
                metadata.put("lod", Integer.toString(lod[0]));
            }
            PdxElement skeleton = shape.child("skeleton");
            BoneTree tree = skeleton == null ? null : layout.tree(skeleton);
            int[] jointNodes = joints(tree);

            List<Node> children = new ArrayList<>();
            for (PdxElement mesh : shape.childrenNamed("mesh")) {
                children.add(mesh(mesh, jointNodes));
            }
            if (tree != null) {
                for (PdxElement root : tree.roots()) {
                    children.add(bone(root, tree, PdxMath.IDENTITY));
                }
            }
            return new Node(layout.nameOf(shape), Transform.IDENTITY, children, null, true, metadata);
        }

        private int[] joints(BoneTree tree) throws ModelFormatException {
            if (tree == null) {
                return new int[0];
            }
            int[] joints = new int[tree.count()];
            for (int ix = 0; ix < joints.length; ix++) {
                PdxElement bone = tree.byIndex(ix);
                if (bone == null) {
                    throw PdxBinary.error(source, 0, "skeleton bone indices are not contiguous");
                }
                joints[ix] = index.get(layout.nameOf(bone));
            }
            return joints;
        }

        private Node bone(PdxElement bone, BoneTree tree, float[] parentWorld) throws ModelFormatException {
            float[] world = PdxMath.fromTx(bone.floats("tx"));
            world = PdxMath.invertAffine(world);
            float[] local = PdxMath.multiply(PdxMath.invertAffine(parentWorld), world);
            List<Node> children = new ArrayList<>();
            for (PdxElement child : tree.childrenOf(bone)) {
                children.add(bone(child, tree, world));
            }
            return new Node(layout.nameOf(bone), PdxMath.decompose(local), children, null);
        }

        private Node mesh(PdxElement mesh, int[] jointNodes) throws ModelFormatException {
            float[] p = mesh.floats("p");
            if (p == null || p.length % 3 != 0) {
                throw PdxBinary.error(source, 0, "mesh " + mesh.name + " has no valid positions");
            }
            int vertices = p.length / 3;
            float[][] uvSets = uvSets(mesh);
            int[] tri = mesh.ints("tri");
            PdxElement material = mesh.child("material");
            int materialIndex = materialIndex(material);
            Skin skin = null;
            PdxElement skinElement = mesh.child("skin");
            if (skinElement != null) {
                skin = skin(skinElement, vertices, jointNodes);
            }
            Mesh geometry = new Mesh(p, mesh.floats("n"), mesh.floats("ta"), uvSets,
                    List.of(new Mesh.Primitive(materialIndex, tri == null ? new int[0] : tri)), skin);
            return new Node(layout.nameOf(mesh), Transform.IDENTITY, List.of(), geometry);
        }

        private float[][] uvSets(PdxElement mesh) {
            List<float[]> sets = new ArrayList<>();
            for (String key : new String[] {"u0", "u1", "u2", "u3"}) {
                float[] uv = mesh.floats(key);
                if (uv == null) {
                    continue;
                }
                float[] flipped = uv.clone();
                for (int i = 1; i < flipped.length; i += 2) {
                    flipped[i] = 1f - flipped[i];
                }
                sets.add(flipped);
            }
            return sets.toArray(new float[0][]);
        }

        private Skin skin(PdxElement skinElement, int vertices, int[] jointNodes) throws ModelFormatException {
            int[] bones = skinElement.ints("bones");
            if (bones == null || bones.length != 1 || bones[0] <= 0) {
                throw PdxBinary.error(source, 0, "skin needs a positive bones count");
            }
            int[] ix = skinElement.ints("ix");
            float[] w = skinElement.floats("w");
            if (ix == null || w == null || ix.length != vertices * bones[0] || w.length != ix.length) {
                throw PdxBinary.error(source, 0, "skin arrays do not match vertex count");
            }
            return new Skin(jointNodes, bones[0], ix, w);
        }

        private int materialIndex(PdxElement material) {
            String shader = material == null ? "" : valueOr(material.string("shader"));
            String diff = material == null ? null : material.string("diff");
            String normal = material == null ? null : material.string("n");
            String spec = material == null ? null : material.string("spec");
            String key = shader + "|" + valueOr(diff) + "|" + valueOr(normal) + "|" + valueOr(spec);
            Integer existing = materialIds.get(key);
            if (existing != null) {
                return existing;
            }
            Map<String, String> textures = new LinkedHashMap<>();
            putIfPresent(textures, "diff", diff);
            putIfPresent(textures, "n", normal);
            putIfPresent(textures, "spec", spec);
            materials.add(new Material("material" + materials.size(), shader, textures));
            materialIds.put(key, materials.size() - 1);
            return materials.size() - 1;
        }

        private static String valueOr(String value) {
            return value == null ? "" : value;
        }

        private static void putIfPresent(Map<String, String> textures, String slot, String value) {
            if (value != null && !value.isEmpty()) {
                textures.put(slot, value);
            }
        }

        Node locator(PdxElement locator) throws ModelFormatException {
            Vec3 position = vec3(locator.floats("p"), locator, Vec3.ZERO);
            float[] q = locator.floats("q");
            Quat rotation = Quat.IDENTITY;
            if (q != null) {
                if (q.length != 4) {
                    throw PdxBinary.error(source, 0, "locator " + locator.name + " quaternion needs 4 floats");
                }
                rotation = new Quat(q[0], q[1], q[2], q[3]);
            }
            Map<String, String> metadata = new HashMap<>();
            metadata.put(KIND_KEY, KIND_LOCATOR);
            String parent = locator.string("pa");
            if (parent != null && !parent.isEmpty()) {
                metadata.put(PARENT_KEY, parent);
            }
            return new Node(layout.nameOf(locator), new Transform(position, rotation, Vec3.ONE), List.of(), null,
                    true, metadata);
        }

        private Vec3 vec3(float[] values, PdxElement owner, Vec3 fallback) throws ModelFormatException {
            if (values == null) {
                return fallback;
            }
            if (values.length != 3) {
                throw PdxBinary.error(source, 0, owner.name + " position needs 3 floats");
            }
            return new Vec3(values[0], values[1], values[2]);
        }
    }
}
