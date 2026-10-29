package featurecreep.api.model.io.pdx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One parsed PDX object: a name, its depth in the hierarchy, its properties, and its child objects. */
final class PdxElement {

    final String name;
    final int depth;
    final Map<String, Object> props = new LinkedHashMap<>();
    final List<PdxElement> children = new ArrayList<>();

    PdxElement(String name, int depth) {
        this.name = name;
        this.depth = depth;
    }

    PdxElement child(String childName) {
        for (PdxElement c : children) {
            if (c.name.equals(childName)) {
                return c;
            }
        }
        return null;
    }

    List<PdxElement> childrenNamed(String childName) {
        List<PdxElement> found = new ArrayList<>();
        for (PdxElement c : children) {
            if (c.name.equals(childName)) {
                found.add(c);
            }
        }
        return found;
    }

    int[] ints(String key) {
        return props.get(key) instanceof int[] a ? a : null;
    }

    float[] floats(String key) {
        return props.get(key) instanceof float[] a ? a : null;
    }

    String string(String key) {
        return props.get(key) instanceof String s ? s : null;
    }
}
