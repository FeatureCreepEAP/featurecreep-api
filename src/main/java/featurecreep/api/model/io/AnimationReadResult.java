package featurecreep.api.model.io;

import featurecreep.api.model.animation.AnimationClip;
import java.util.List;
import java.util.Objects;

public record AnimationReadResult(AnimationClip clip, List<String> warnings) {

    public AnimationReadResult {
        Objects.requireNonNull(clip, "clip");
        warnings = List.copyOf(warnings);
    }
}
