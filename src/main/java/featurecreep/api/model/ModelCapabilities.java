package featurecreep.api.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * What a model accepts from animation sources. An empty {@code animationFormats} set accepts every format.
 * PDX skeletons bind by bone name and reject sources that bind differently.
 */
public record ModelCapabilities(Set<String> animationFormats, Set<BindingKey> bindings) {

    public ModelCapabilities {
        animationFormats = Set.copyOf(animationFormats);
        bindings = Set.copyOf(bindings);
    }

    public static ModelCapabilities any() {
        return new ModelCapabilities(Set.of(), EnumSet.allOf(BindingKey.class));
    }

    public boolean acceptsAnimationFormat(String formatId) {
        return animationFormats.isEmpty() || animationFormats.contains(formatId);
    }

    public boolean acceptsBinding(BindingKey key) {
        return bindings.contains(key);
    }
}
