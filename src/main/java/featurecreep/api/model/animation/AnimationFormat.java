package featurecreep.api.model.animation;

import featurecreep.api.model.BindingKey;
import java.util.Objects;
import java.util.Set;

/**
 * Describes an animation source: its id, how its tracks address nodes, and which skeleton features it needs. A model
 * accepts or rejects a format through {@code ModelCapabilities}, and {@link ClipAnimator#bind} checks both.
 */
public record AnimationFormat(String id, BindingKey bindingKey, Set<SkeletonTrait> traits) {

    public enum SkeletonTrait {
        NAMED_BONES, SKIN_JOINTS, MORPH_TARGETS
    }

    public AnimationFormat {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bindingKey, "bindingKey");
        traits = Set.copyOf(traits);
    }

    public static final AnimationFormat PDX_ANIM =
            new AnimationFormat("pdx-anim", BindingKey.NAME, Set.of(SkeletonTrait.NAMED_BONES));
}
