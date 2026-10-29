package featurecreep.api.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A named node in the model tree. Node names are unique within a model. {@code geometry} is null for pure transform
 * nodes such as bones or groups. Metadata holds format-specific values such as a PDX LOD tag or a stable UUID.
 */
public record Node(String name, Transform transform, List<Node> children, Geometry geometry,
        boolean visible, Map<String, String> metadata) {

    public Node {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("node name is required");
        }
        Objects.requireNonNull(transform, "transform");
        children = List.copyOf(children);
        metadata = Map.copyOf(metadata);
    }

    public Node(String name, Transform transform, List<Node> children, Geometry geometry) {
        this(name, transform, children, geometry, true, Map.of());
    }
}
