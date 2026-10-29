package featurecreep.api.model.animation;

import featurecreep.api.model.BindingKey;
import java.util.Objects;

/**
 * One animated property. {@code ref} is the node name, node index, or node uuid, depending on {@code key}. Morph tracks
 * use the morph name or index in the same way.
 */
public record Track(Kind kind, BindingKey key, String ref, Keys keys) {

    public enum Kind {
        TRANSLATION(3), ROTATION(4), SCALE(1, 3), VISIBILITY(1), MORPH(1);

        private final int[] allowed;

        Kind(int... allowed) {
            this.allowed = allowed;
        }

        public boolean allows(int components) {
            for (int c : allowed) {
                if (c == components) {
                    return true;
                }
            }
            return false;
        }
    }

    public Track {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(keys, "keys");
        if (!kind.allows(keys.components())) {
            throw new IllegalArgumentException(kind + " track cannot have " + keys.components() + " components");
        }
    }
}
