package featurecreep.api.model.animation;

import featurecreep.api.model.Model;
import featurecreep.api.model.Pose;

/** Writes a pose for time {@code t} seconds. Data animators come from clips, and procedural animators are code callbacks. */
@FunctionalInterface
public interface Animator {

    void apply(Model model, double t, Pose pose);
}
