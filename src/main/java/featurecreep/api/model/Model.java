package featurecreep.api.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Immutable model: a node tree, its materials, morph target names, and animation capabilities. Nodes are indexed in
 * pre-order, and that index is what {@link Pose} and skin joint references use.
 *
 * <p>{@code extensions} holds format-private native data keyed by format id, such as PMX rigid bodies. Bridges ignore
 * it. A format's writer may reuse its own extension for data the core cannot represent.
 */
public final class Model {

    private final List<Node> roots;
    private final List<Node> nodes;
    private final Map<String, Integer> indexByName;
    private final List<Material> materials;
    private final List<String> morphNames;
    private final ModelCapabilities capabilities;
    private final Map<String, String> metadata;
    private final Map<String, Object> extensions;

    public Model(List<Node> roots, List<Material> materials, List<String> morphNames,
            ModelCapabilities capabilities, Map<String, String> metadata, Map<String, Object> extensions) {
        this.roots = List.copyOf(roots);
        this.materials = List.copyOf(materials);
        this.morphNames = List.copyOf(morphNames);
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.metadata = Map.copyOf(metadata);
        this.extensions = Map.copyOf(extensions);
        List<Node> flat = new ArrayList<>();
        Map<String, Integer> index = new HashMap<>();
        for (Node root : this.roots) {
            collect(root, flat, index);
        }
        this.nodes = List.copyOf(flat);
        this.indexByName = Map.copyOf(index);
        validateReferences();
    }

    public static Model of(List<Node> roots) {
        return new Model(roots, List.of(), List.of(), ModelCapabilities.any(), Map.of(), Map.of());
    }

    private static void collect(Node node, List<Node> flat, Map<String, Integer> index) {
        if (index.putIfAbsent(node.name(), flat.size()) != null) {
            throw new IllegalArgumentException("duplicate node name: " + node.name());
        }
        flat.add(node);
        for (Node child : node.children()) {
            collect(child, flat, index);
        }
    }

    private void validateReferences() {
        for (Node node : nodes) {
            if (!(node.geometry() instanceof Mesh mesh)) {
                continue;
            }
            for (Mesh.Primitive primitive : mesh.primitives()) {
                if (primitive.material() >= materials.size()) {
                    throw new IllegalArgumentException(
                            "node " + node.name() + " references missing material " + primitive.material());
                }
            }
            if (mesh.skin() != null) {
                for (int joint : mesh.skin().jointNodes()) {
                    if (joint < 0 || joint >= nodes.size()) {
                        throw new IllegalArgumentException(
                                "node " + node.name() + " references missing joint node " + joint);
                    }
                }
            }
        }
    }

    public List<Node> roots() {
        return roots;
    }

    public List<Node> nodes() {
        return nodes;
    }

    public int nodeCount() {
        return nodes.size();
    }

    public OptionalInt indexOf(String nodeName) {
        Integer index = indexByName.get(nodeName);
        return index == null ? OptionalInt.empty() : OptionalInt.of(index);
    }

    public List<Material> materials() {
        return materials;
    }

    public List<String> morphNames() {
        return morphNames;
    }

    public ModelCapabilities capabilities() {
        return capabilities;
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    public Map<String, Object> extensions() {
        return extensions;
    }

    public <T> Optional<T> extension(String formatId, Class<T> type) {
        Object value = extensions.get(formatId);
        return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
    }
}
