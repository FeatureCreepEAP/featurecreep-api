package featurecreep.api.model.animation;

import featurecreep.api.model.BindingKey;
import featurecreep.api.model.Model;
import featurecreep.api.model.Node;
import featurecreep.api.model.Pose;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/** Data animator for one clip bound to one model. Track targets are resolved once at bind time. */
public final class ClipAnimator implements Animator {

    private record Resolved(Track track, int slot) {
    }

    private final AnimationClip clip;
    private final Model model;
    private final List<Resolved> resolved;

    private ClipAnimator(AnimationClip clip, Model model, List<Resolved> resolved) {
        this.clip = clip;
        this.model = model;
        this.resolved = List.copyOf(resolved);
    }

    public AnimationClip clip() {
        return clip;
    }

    public Model model() {
        return model;
    }

    /**
     * Binds {@code clip} to {@code model} for {@code format}. Throws {@link AnimationBindException} when the model does
     * not accept the format or binding key, or when the model lacks a required skeleton trait. Track-level failures are
     * returned in the report.
     */
    public static BindReport bind(AnimationClip clip, AnimationFormat format, Model model) {
        if (!model.capabilities().acceptsAnimationFormat(format.id())) {
            throw new AnimationBindException("model does not accept animation format " + format.id());
        }
        if (!model.capabilities().acceptsBinding(format.bindingKey())) {
            throw new AnimationBindException("model does not accept binding " + format.bindingKey()
                    + " for format " + format.id());
        }
        checkTraits(format, model);

        Map<String, Integer> byUuid = null;
        List<Resolved> resolved = new ArrayList<>();
        List<BindReport.Unresolved> unresolved = new ArrayList<>();
        for (Track track : clip.tracks()) {
            if (track.key() != format.bindingKey()) {
                unresolved.add(new BindReport.Unresolved(track,
                        "track binds by " + track.key() + " but format " + format.id() + " binds by "
                                + format.bindingKey()));
                continue;
            }
            if (track.kind() == Track.Kind.MORPH) {
                OptionalInt slot = resolveMorph(track, model);
                if (slot.isPresent()) {
                    resolved.add(new Resolved(track, slot.getAsInt()));
                } else {
                    unresolved.add(new BindReport.Unresolved(track, "no morph target with that name or index"));
                }
                continue;
            }
            int slot;
            switch (track.key()) {
                case NAME -> {
                    OptionalInt found = model.indexOf(track.ref());
                    slot = found.orElse(-1);
                }
                case INDEX -> slot = parseIndex(track.ref(), model.nodeCount());
                case UUID -> {
                    if (byUuid == null) {
                        byUuid = indexByUuid(model);
                    }
                    slot = byUuid.getOrDefault(track.ref(), -1);
                }
                default -> slot = -1;
            }
            if (slot < 0) {
                unresolved.add(new BindReport.Unresolved(track, "no node matches " + track.key() + " reference"));
            } else {
                resolved.add(new Resolved(track, slot));
            }
        }
        return new BindReport(new ClipAnimator(clip, model, resolved), unresolved);
    }

    private static void checkTraits(AnimationFormat format, Model model) {
        for (AnimationFormat.SkeletonTrait trait : format.traits()) {
            boolean present = switch (trait) {
                case NAMED_BONES -> model.nodeCount() > 0;
                case SKIN_JOINTS -> model.nodes().stream()
                        .anyMatch(n -> n.geometry() instanceof featurecreep.api.model.Mesh m && m.skin() != null);
                case MORPH_TARGETS -> !model.morphNames().isEmpty();
            };
            if (!present) {
                throw new AnimationBindException("model lacks " + trait + " required by " + format.id());
            }
        }
    }

    private static OptionalInt resolveMorph(Track track, Model model) {
        List<String> names = model.morphNames();
        if (track.key() == BindingKey.INDEX) {
            int index = parseIndex(track.ref(), names.size());
            return index < 0 ? OptionalInt.empty() : OptionalInt.of(index);
        }
        if (track.key() == BindingKey.NAME) {
            int index = names.indexOf(track.ref());
            return index < 0 ? OptionalInt.empty() : OptionalInt.of(index);
        }
        return OptionalInt.empty();
    }

    private static int parseIndex(String ref, int limit) {
        try {
            int index = Integer.parseInt(ref);
            return index >= 0 && index < limit ? index : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Map<String, Integer> indexByUuid(Model model) {
        Map<String, Integer> index = new HashMap<>();
        List<Node> nodes = model.nodes();
        for (int i = 0; i < nodes.size(); i++) {
            String uuid = nodes.get(i).metadata().get("uuid");
            if (uuid != null) {
                index.putIfAbsent(uuid, i);
            }
        }
        return index;
    }

    @Override
    public void apply(Model applied, double t, Pose pose) {
        if (applied != model || pose.model() != model) {
            throw new IllegalArgumentException("animator is bound to a different model");
        }
        double time = clampTime(t);
        float[] out = new float[4];
        for (Resolved r : resolved) {
            Track track = r.track();
            int comps = track.keys().components();
            track.keys().evaluate(time, out);
            int slot = r.slot();
            switch (track.kind()) {
                case TRANSLATION -> System.arraycopy(out, 0, pose.translations(), slot * 3, 3);
                case ROTATION -> {
                    normalize(out);
                    System.arraycopy(out, 0, pose.rotations(), slot * 4, 4);
                }
                case SCALE -> {
                    float[] scales = pose.scales();
                    if (comps == 3) {
                        System.arraycopy(out, 0, scales, slot * 3, 3);
                    } else {
                        scales[slot * 3] = out[0];
                        scales[slot * 3 + 1] = out[0];
                        scales[slot * 3 + 2] = out[0];
                    }
                }
                case VISIBILITY -> pose.visible()[slot] = out[0] >= 0.5f;
                case MORPH -> pose.morphWeights()[slot] = out[0];
            }
        }
    }

    private double clampTime(double t) {
        double duration = clip.duration();
        if (clip.looping() && duration > 0.0) {
            double wrapped = t % duration;
            return wrapped < 0.0 ? wrapped + duration : wrapped;
        }
        return Math.max(0.0, Math.min(t, duration));
    }

    private static void normalize(float[] q) {
        float len = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        if (len > 0f) {
            q[0] /= len;
            q[1] /= len;
            q[2] /= len;
            q[3] /= len;
        }
    }
}
