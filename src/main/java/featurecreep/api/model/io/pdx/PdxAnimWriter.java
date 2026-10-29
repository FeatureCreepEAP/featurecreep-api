package featurecreep.api.model.io.pdx;

import featurecreep.api.model.BindingKey;
import featurecreep.api.model.Mesh;
import featurecreep.api.model.Model;
import featurecreep.api.model.ModelFormatException;
import featurecreep.api.model.Node;
import featurecreep.api.model.Quat;
import featurecreep.api.model.Space;
import featurecreep.api.model.Transform;
import featurecreep.api.model.Vec3;
import featurecreep.api.model.animation.AnimationClip;
import featurecreep.api.model.animation.AnimationFormat;
import featurecreep.api.model.animation.Keys;
import featurecreep.api.model.animation.Track;
import featurecreep.api.model.io.AnimationWriter;
import featurecreep.api.model.io.FormatDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Writes a PDX {@code .anim}. Bones are the model's skin joints in preorder, or, when the model has no skins, the nodes
 * the clip animates. Tracks on other nodes, and visibility or morph tracks, are dropped. Samples are taken at the first
 * sampled track's rate, or 30 fps. Values are mirrored from model space into PDX space.
 */
public final class PdxAnimWriter implements AnimationWriter {

    static final float DEFAULT_FPS = 30f;

    private static final FormatDescriptor DESCRIPTOR =
            new FormatDescriptor(PdxAnimReader.ID, Set.of("anim"), PdxBinary::sniff);

    @Override
    public FormatDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AnimationFormat format() {
        return AnimationFormat.PDX_ANIM;
    }

    @Override
    public byte[] write(Model model, AnimationClip clip) throws ModelFormatException {
        List<Node> nodes = model.nodes();
        List<Integer> bones = boneIndices(model, clip);
        Map<Integer, Track[]> slots = new HashMap<>();
        for (Track track : clip.tracks()) {
            int channel = channel(track.kind());
            int index = resolve(model, track);
            if (channel < 0 || index < 0) {
                continue;
            }
            Track[] perBone = slots.computeIfAbsent(index, k -> new Track[3]);
            if (perBone[channel] != null) {
                throw new ModelFormatException("duplicate " + track.kind() + " track for " + track.ref());
            }
            perBone[channel] = track;
        }

        float fps = DEFAULT_FPS;
        for (Track track : clip.tracks()) {
            if (track.keys() instanceof Keys.Sampled sampled) {
                fps = sampled.fps();
                break;
            }
        }
        int frames = Math.max(1, (int) Math.round(clip.duration() * fps) + 1);

        int[] counts = new int[3];
        for (int index : bones) {
            Track[] perBone = slots.get(index);
            for (int c = 0; perBone != null && c < 3; c++) {
                if (perBone[c] != null) {
                    counts[c]++;
                }
            }
        }
        float[][] samples = new float[3][];
        for (int c = 0; c < 3; c++) {
            samples[c] = new float[frames * counts[c] * PdxAnimReader.COMPONENTS[c]];
        }
        int[] cursor = new int[3];
        for (int f = 0; f < frames; f++) {
            double t = f / (double) fps;
            for (int index : bones) {
                Track[] perBone = slots.get(index);
                for (int c = 0; perBone != null && c < 3; c++) {
                    if (perBone[c] == null) {
                        continue;
                    }
                    int comp = PdxAnimReader.COMPONENTS[c];
                    float[] value = evaluate(perBone[c].keys(), t);
                    if (c == 0) {
                        Vec3 v = Space.mirrorY(new Vec3(value[0], value[1], value[2]));
                        value = new float[] {v.x(), v.y(), v.z()};
                    } else if (c == 1) {
                        Quat q = Space.mirrorY(new Quat(value[0], value[1], value[2], value[3]));
                        value = new float[] {q.x(), q.y(), q.z(), q.w()};
                    }
                    System.arraycopy(value, 0, samples[c], cursor[c] * comp, comp);
                    cursor[c]++;
                }
            }
        }

        PdxBinary.Writer out = new PdxBinary.Writer();
        out.prop("pdxasset", new int[] {1, 0});
        out.object(1, "info");
        out.prop("fps", new float[] {fps});
        out.prop("sa", new int[] {frames});
        out.prop("j", new int[] {bones.size()});
        for (int index : bones) {
            Node node = nodes.get(index);
            Track[] perBone = slots.get(index);
            StringBuilder channels = new StringBuilder();
            for (int c = 0; perBone != null && c < 3; c++) {
                if (perBone[c] != null) {
                    channels.append(PdxAnimReader.CHANNELS.charAt(c));
                }
            }
            Transform pose = Space.mirrorY(node.transform());
            out.object(2, node.name());
            out.prop("sa", channels.toString());
            out.prop("t", new float[] {pose.translation().x(), pose.translation().y(), pose.translation().z()});
            out.prop("q", new float[] {pose.rotation().x(), pose.rotation().y(), pose.rotation().z(),
                    pose.rotation().w()});
            out.prop("s", new float[] {pose.scale().x(), pose.scale().y(), pose.scale().z()});
        }
        out.object(1, "samples");
        for (int c = 0; c < 3; c++) {
            if (samples[c].length > 0) {
                out.prop(String.valueOf(PdxAnimReader.CHANNELS.charAt(c)), samples[c]);
            }
        }
        return out.toBytes();
    }

    private static float[] evaluate(Keys keys, double t) {
        float[] value = new float[keys.components()];
        keys.evaluate(t, value);
        if (value.length == 1) {
            return new float[] {value[0], value[0], value[0]};
        }
        return value;
    }

    private static List<Integer> boneIndices(Model model, AnimationClip clip) {
        TreeSet<Integer> indices = new TreeSet<>();
        for (Node node : model.nodes()) {
            if (node.geometry() instanceof Mesh mesh && mesh.skin() != null) {
                for (int joint : mesh.skin().jointNodes()) {
                    indices.add(joint);
                }
            }
        }
        if (indices.isEmpty()) {
            for (Track track : clip.tracks()) {
                int index = resolve(model, track);
                if (index >= 0) {
                    indices.add(index);
                }
            }
        }
        return new ArrayList<>(indices);
    }

    private static int channel(Track.Kind kind) {
        return switch (kind) {
            case TRANSLATION -> 0;
            case ROTATION -> 1;
            case SCALE -> 2;
            default -> -1;
        };
    }

    private static int resolve(Model model, Track track) {
        if (track.key() == BindingKey.NAME) {
            return model.indexOf(track.ref()).orElse(-1);
        }
        if (track.key() == BindingKey.INDEX) {
            try {
                int index = Integer.parseInt(track.ref());
                return index >= 0 && index < model.nodeCount() ? index : -1;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        List<Node> nodes = model.nodes();
        for (int i = 0; i < nodes.size(); i++) {
            if (track.ref().equals(nodes.get(i).metadata().get("uuid"))) {
                return i;
            }
        }
        return -1;
    }
}
