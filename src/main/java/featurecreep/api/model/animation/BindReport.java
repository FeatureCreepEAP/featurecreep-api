package featurecreep.api.model.animation;

import java.util.List;
import java.util.Objects;

/**
 * Result of binding a clip to a model. {@link #animator()} applies only the resolved tracks. Unresolved tracks are an
 * error unless the caller accepts them via {@link #requireComplete()} or by checking {@link #complete()}.
 */
public record BindReport(ClipAnimator animator, List<Unresolved> unresolved) {

    public record Unresolved(Track track, String reason) {
    }

    public BindReport {
        Objects.requireNonNull(animator, "animator");
        unresolved = List.copyOf(unresolved);
    }

    public boolean complete() {
        return unresolved.isEmpty();
    }

    public ClipAnimator requireComplete() {
        if (!unresolved.isEmpty()) {
            StringBuilder message = new StringBuilder("clip ")
                    .append(animator.clip().name()).append(" has ").append(unresolved.size())
                    .append(" unresolved track(s):");
            for (Unresolved u : unresolved) {
                message.append("\n  ").append(u.track().kind()).append(" '").append(u.track().ref())
                        .append("': ").append(u.reason());
            }
            throw new AnimationBindException(message.toString());
        }
        return animator;
    }
}
