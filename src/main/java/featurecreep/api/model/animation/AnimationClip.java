package featurecreep.api.model.animation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A named animation: duration in seconds, a looping flag, and tracks. Looping clips wrap time modulo the duration. */
public record AnimationClip(String name, double duration, boolean looping, List<Track> tracks,
        Map<String, String> metadata) {

    public AnimationClip {
        Objects.requireNonNull(name, "name");
        if (!(duration >= 0.0) || !Double.isFinite(duration)) {
            throw new IllegalArgumentException("duration must be finite and not negative");
        }
        tracks = List.copyOf(tracks);
        metadata = Map.copyOf(metadata);
    }
}
