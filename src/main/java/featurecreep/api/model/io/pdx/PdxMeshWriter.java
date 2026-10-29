package featurecreep.api.model.io.pdx;

import featurecreep.api.model.Cuboid;
import featurecreep.api.model.Cuboids;
import featurecreep.api.model.Material;
import featurecreep.api.model.Mesh;
import featurecreep.api.model.Model;
import featurecreep.api.model.ModelFormatException;
import featurecreep.api.model.Node;
import featurecreep.api.model.Quat;
import featurecreep.api.model.Skin;
import featurecreep.api.model.Space;
import featurecreep.api.model.Transform;
import featurecreep.api.model.io.FormatDescriptor;
import featurecreep.api.model.io.ModelWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes a PDX {@code .mesh}. Non-locator roots become a single {@code object} element, each child of a root becomes a
 * shape, and each mesh primitive becomes its own {@code mesh} element with compacted vertices. Joints used by skins
 * become the shape's {@code skeleton}. Locator roots are written under {@code locator}.
 *
 * <p>Lossy by design: root and material names, intermediate transform nodes between joints, and skin-less transform
 * nodes are not stored. Cuboid geometry is converted with {@code texture_width} and {@code texture_height} node
 * metadata, defaulting to 64.
 */
public final class PdxMeshWriter implements ModelWriter {

    static final String TEXTURE_WIDTH = "texture_width";
    static final String TEXTURE_HEIGHT = "texture_height";
    static final String LOD_KEY = "lod";
    private static final int DEFAULT_TEXTURE = 64;
    private static final int MAX_UV_SETS = 4;

    private static final FormatDescriptor DESCRIPTOR =
            new FormatDescriptor(PdxMeshReader.ID, Set.of("mesh"), PdxBinary::sniff);

    @Override
    public FormatDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public byte[] write(Model source) throws ModelFormatException {
        try {
            return encode(Space.mirrorY(source));
        } catch (IllegalArgumentException e) {
            throw new ModelFormatException(e.getMessage());
        }
    }

    private static byte[] encode(Model model) throws ModelFormatException {
        Map<Node, float[]> world = new IdentityHashMap<>();
        Map<Node, Node> parents = new IdentityHashMap<>();
        for (Node root : model.roots()) {
            index(root, PdxMath.IDENTITY, null, world, parents);
        }
        PdxBinary.Writer out = new PdxBinary.Writer();
        out.prop("pdxasset", new int[] {1, 0});
        List<Node> locators = new ArrayList<>();
        for (Node root : model.roots()) {
            if (PdxMeshReader.KIND_LOCATOR.equals(root.metadata().get(PdxMeshReader.KIND_KEY))) {
                locators.add(root);
                continue;
            }
            if (root.geometry() != null) {
                throw new ModelFormatException("root node " + root.name() + " has geometry; PDX needs it inside a shape");
            }
            out.object(1, "object");
            for (Node shape : root.children()) {
                writeShape(out, shape, model, world, parents);
            }
        }
        if (!locators.isEmpty()) {
            out.object(1, "locator");
            for (Node locator : locators) {
                writeLocator(out, locator);
            }
        }
        return out.toBytes();
    }

    private static void index(Node node, float[] parentWorld, Node parent, Map<Node, float[]> world,
            Map<Node, Node> parents) {
        float[] w = PdxMath.multiply(parentWorld, PdxMath.fromTransform(node.transform()));
        world.put(node, w);
        if (parent != null) {
            parents.put(node, parent);
        }
        for (Node child : node.children()) {
            index(child, w, node, world, parents);
        }
    }

    private static void writeShape(PdxBinary.Writer out, Node shape, Model model, Map<Node, float[]> world,
            Map<Node, Node> parents) throws ModelFormatException {
        if (shape.geometry() != null) {
            throw new ModelFormatException("shape " + shape.name() + " has geometry; wrap it in a child node");
        }
        out.object(2, shape.name());
        Integer lod = optionalInt(shape, LOD_KEY);
        if (lod != null) {
            out.prop(LOD_KEY, new int[] {lod});
        }
        int[] joints = null;
        List<Node> geometry = new ArrayList<>();
        for (Node child : shape.children()) {
            if (child.geometry() == null) {
                continue;
            }
            geometry.add(child);
            if (child.geometry() instanceof Mesh mesh && mesh.skin() != null) {
                int[] jointNodes = mesh.skin().jointNodes();
                if (joints != null && !Arrays.equals(joints, jointNodes)) {
                    throw new ModelFormatException("shape " + shape.name() + " mixes skin joint sets");
                }
                joints = jointNodes;
            }
        }
        for (Node child : geometry) {
            writeGeometry(out, child, model.materials());
        }
        if (joints != null && joints.length > 0) {
            writeSkeleton(out, joints, model, world, parents);
        }
    }

    private static void writeGeometry(PdxBinary.Writer out, Node node, List<Material> materials)
            throws ModelFormatException {
        Mesh mesh;
        if (node.geometry() instanceof Mesh m) {
            mesh = m;
        } else if (node.geometry() instanceof Cuboid c) {
            mesh = Cuboids.toMesh(c, dimension(node, TEXTURE_WIDTH), dimension(node, TEXTURE_HEIGHT), 0);
        } else {
            throw new ModelFormatException("node " + node.name() + " has geometry that PDX cannot store");
        }
        for (Mesh.Primitive primitive : mesh.primitives()) {
            Material material = primitive.material() < materials.size() ? materials.get(primitive.material()) : null;
            writePrimitive(out, mesh, primitive, material);
        }
    }

    private static void writePrimitive(PdxBinary.Writer out, Mesh mesh, Mesh.Primitive primitive, Material material)
            throws ModelFormatException {
        if (mesh.uvSets().length > MAX_UV_SETS) {
            throw new ModelFormatException("PDX stores at most " + MAX_UV_SETS + " UV sets");
        }
        int[] source = primitive.indices();
        boolean[] referenced = new boolean[mesh.vertexCount()];
        for (int v : source) {
            referenced[v] = true;
        }
        int[] remap = new int[mesh.vertexCount()];
        List<Integer> used = new ArrayList<>();
        for (int v = 0; v < referenced.length; v++) {
            if (referenced[v]) {
                remap[v] = used.size();
                used.add(v);
            }
        }
        int[] tri = new int[source.length];
        for (int i = 0; i < source.length; i++) {
            tri[i] = remap[source[i]];
        }
        float[] p = gather(mesh.positions(), used, 3);
        float[][] box = bounds(p);
        out.object(3, "mesh");
        out.prop("p", p);
        if (mesh.normals() != null) {
            out.prop("n", gather(mesh.normals(), used, 3));
        }
        if (mesh.tangents() != null) {
            out.prop("ta", gather(mesh.tangents(), used, 4));
        }
        for (int s = 0; s < mesh.uvSets().length; s++) {
            float[] uv = gather(mesh.uvSets()[s], used, 2);
            for (int i = 1; i < uv.length; i += 2) {
                uv[i] = 1f - uv[i];
            }
            out.prop("u" + s, uv);
        }
        out.prop("tri", tri);
        out.prop("boundingsphere", sphere(p, box));
        out.object(4, "aabb");
        out.prop("min", box[0]);
        out.prop("max", box[1]);
        if (material != null) {
            out.object(4, "material");
            if (material.shader() != null && !material.shader().isEmpty()) {
                out.prop("shader", material.shader());
            }
            for (String slot : List.of("diff", "n", "spec")) {
                String texture = material.textures().get(slot);
                if (texture != null && !texture.isEmpty()) {
                    out.prop(slot, texture);
                }
            }
        }
        if (mesh.skin() != null && mesh.skin().influences() > 0) {
            writeSkin(out, mesh.skin(), used);
        }
    }

    private static void writeSkin(PdxBinary.Writer out, Skin skin, List<Integer> used) {
        int k = skin.influences();
        int[] ix = new int[used.size() * k];
        float[] w = new float[used.size() * k];
        for (int i = 0; i < used.size(); i++) {
            int from = used.get(i) * k;
            System.arraycopy(skin.jointIndices(), from, ix, i * k, k);
            System.arraycopy(skin.weights(), from, w, i * k, k);
        }
        out.object(4, "skin");
        out.prop("bones", new int[] {k});
        out.prop("ix", ix);
        out.prop("w", w);
    }

    private static void writeSkeleton(PdxBinary.Writer out, int[] joints, Model model, Map<Node, float[]> world,
            Map<Node, Node> parents) {
        List<Node> nodes = model.nodes();
        Map<Node, Integer> ixOf = new IdentityHashMap<>();
        for (int i = 0; i < joints.length; i++) {
            ixOf.put(nodes.get(joints[i]), i);
        }
        out.object(3, "skeleton");
        for (int i = 0; i < joints.length; i++) {
            Node joint = nodes.get(joints[i]);
            out.object(4, joint.name());
            out.prop("ix", new int[] {i});
            Integer pa = nearestJointIx(joint, ixOf, parents);
            if (pa != null) {
                out.prop("pa", new int[] {pa});
            }
            out.prop("tx", PdxMath.toTx(PdxMath.invertAffine(world.get(joint))));
        }
    }

    private static Integer nearestJointIx(Node node, Map<Node, Integer> ixOf, Map<Node, Node> parents) {
        Node ancestor = parents.get(node);
        while (ancestor != null) {
            Integer ix = ixOf.get(ancestor);
            if (ix != null) {
                return ix;
            }
            ancestor = parents.get(ancestor);
        }
        return null;
    }

    private static void writeLocator(PdxBinary.Writer out, Node locator) {
        Transform t = locator.transform();
        Quat q = t.rotation();
        out.object(2, locator.name());
        out.prop("p", new float[] {t.translation().x(), t.translation().y(), t.translation().z()});
        out.prop("q", new float[] {q.x(), q.y(), q.z(), q.w()});
        String parent = locator.metadata().get(PdxMeshReader.PARENT_KEY);
        if (parent != null && !parent.isEmpty()) {
            out.prop("pa", parent);
        }
    }

    private static Integer optionalInt(Node node, String key) throws ModelFormatException {
        String value = node.metadata().get(key);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ModelFormatException("node " + node.name() + " has non-integer " + key + ": " + value);
        }
    }

    private static int dimension(Node node, String key) throws ModelFormatException {
        Integer value = optionalInt(node, key);
        return value == null ? DEFAULT_TEXTURE : value;
    }

    private static float[] gather(float[] source, List<Integer> used, int stride) {
        float[] out = new float[used.size() * stride];
        for (int i = 0; i < used.size(); i++) {
            System.arraycopy(source, used.get(i) * stride, out, i * stride, stride);
        }
        return out;
    }

    private static float[][] bounds(float[] p) {
        float[] min = new float[3];
        float[] max = new float[3];
        if (p.length == 0) {
            return new float[][] {min, max};
        }
        Arrays.fill(min, Float.POSITIVE_INFINITY);
        Arrays.fill(max, Float.NEGATIVE_INFINITY);
        for (int i = 0; i < p.length; i += 3) {
            for (int a = 0; a < 3; a++) {
                min[a] = Math.min(min[a], p[i + a]);
                max[a] = Math.max(max[a], p[i + a]);
            }
        }
        return new float[][] {min, max};
    }

    private static float[] sphere(float[] p, float[][] box) {
        float cx = (box[0][0] + box[1][0]) / 2f;
        float cy = (box[0][1] + box[1][1]) / 2f;
        float cz = (box[0][2] + box[1][2]) / 2f;
        float radius = 0f;
        for (int i = 0; i < p.length; i += 3) {
            float dx = p[i] - cx;
            float dy = p[i + 1] - cy;
            float dz = p[i + 2] - cz;
            radius = Math.max(radius, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        return new float[] {cx, cy, cz, radius};
    }
}
