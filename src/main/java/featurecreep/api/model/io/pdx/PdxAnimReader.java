package featurecreep.api.model.io.pdx;

import featurecreep.api.model.BindingKey;
import featurecreep.api.model.ModelFormatException;
import featurecreep.api.model.Quat;
import featurecreep.api.model.Space;
import featurecreep.api.model.Vec3;
import featurecreep.api.model.animation.AnimationClip;
import featurecreep.api.model.animation.AnimationFormat;
import featurecreep.api.model.animation.Keys;
import featurecreep.api.model.animation.Track;
import featurecreep.api.model.io.AnimationReadResult;
import featurecreep.api.model.io.AnimationReader;
import featurecreep.api.model.io.FormatDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a PDX {@code .anim}. Each bone under {@code info} lists its channels in {@code sa} as any of {@code t}, {@code q},
 * {@code s}. The {@code samples} element holds one flat array per channel, frame-major with bones in order within each
 * frame. Values are mirrored from PDX space into model space.
 */
public final class PdxAnimReader implements AnimationReader {

    static final String ID = "pdx-anim";

    private static final FormatDescriptor DESCRIPTOR =
            new FormatDescriptor(ID, Set.of("anim"), PdxBinary::sniff);

    static final String CHANNELS = "tqs";
    static final int[] COMPONENTS = {3, 4, 3};
    private static final Track.Kind[] KINDS = {Track.Kind.TRANSLATION, Track.Kind.ROTATION, Track.Kind.SCALE};

    @Override
    public FormatDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AnimationFormat format() {
        return AnimationFormat.PDX_ANIM;
    }

    @Override
    public AnimationReadResult read(byte[] data, String sourceName) throws ModelFormatException {
        PdxElement root = PdxBinary.parse(data, sourceName);
        PdxElement info = root.child("info");
        PdxElement samples = root.child("samples");
        if (info == null || samples == null) {
            throw new ModelFormatException(sourceName + ": missing info or samples");
        }
        float[] fps = info.floats("fps");
        int[] frameCount = info.ints("sa");
        if (fps == null || fps.length != 1 || !(fps[0] > 0f) || !Float.isFinite(fps[0])) {
            throw new ModelFormatException(sourceName + ": info needs one positive fps");
        }
        if (frameCount == null || frameCount.length != 1 || frameCount[0] < 0) {
            throw new ModelFormatException(sourceName + ": info needs a non-negative frame count");
        }
        int[] jointCount = info.ints("j");
        List<PdxElement> bones = info.children;
        if (jointCount != null && (jointCount.length != 1 || jointCount[0] != bones.size())) {
            throw new ModelFormatException(sourceName + ": joint count does not match bone list");
        }
        try {
            return new AnimationReadResult(clip(sourceName, fps[0], frameCount[0], bones, samples, sourceName),
                    List.of());
        } catch (IllegalArgumentException e) {
            throw new ModelFormatException(sourceName + ": " + e.getMessage());
        }
    }

    private static AnimationClip clip(String name, float fps, int frames, List<PdxElement> bones,
            PdxElement samples, String source) throws ModelFormatException {
        boolean[][] has = new boolean[bones.size()][3];
        int[] counts = new int[3];
        for (int b = 0; b < bones.size(); b++) {
            String channels = bones.get(b).string("sa");
            if (channels == null) {
                channels = "";
            }
            for (int i = 0; i < channels.length(); i++) {
                int c = CHANNELS.indexOf(channels.charAt(i));
                if (c < 0) {
                    throw new ModelFormatException(source + ": unknown channel '" + channels.charAt(i) + "' on "
                            + bones.get(b).name);
                }
                has[b][c] = true;
                counts[c]++;
            }
        }
        float[][] raw = new float[3][];
        for (int c = 0; c < 3; c++) {
            String key = String.valueOf(CHANNELS.charAt(c));
            raw[c] = samples.floats(key);
            int expected = frames * counts[c] * COMPONENTS[c];
            int actual = raw[c] == null ? 0 : raw[c].length;
            if (actual != expected) {
                throw new ModelFormatException(source + ": samples " + key + " has " + actual + " values, expected "
                        + expected);
            }
        }
        List<Track> tracks = new ArrayList<>();
        if (frames > 0) {
            int[] seen = new int[3];
            for (int b = 0; b < bones.size(); b++) {
                for (int c = 0; c < 3; c++) {
                    if (!has[b][c]) {
                        continue;
                    }
                    int comp = COMPONENTS[c];
                    float[] values = new float[frames * comp];
                    for (int f = 0; f < frames; f++) {
                        int from = (f * counts[c] + seen[c]) * comp;
                        System.arraycopy(raw[c], from, values, f * comp, comp);
                        mirror(c, values, f * comp);
                    }
                    seen[c]++;
                    tracks.add(new Track(KINDS[c], BindingKey.NAME, bones.get(b).name,
                            new Keys.Sampled(fps, frames, values, comp)));
                }
            }
        }
        double duration = frames > 0 ? (frames - 1) / (double) fps : 0.0;
        return new AnimationClip(name, duration, false, tracks, Map.of());
    }

    private static void mirror(int channel, float[] values, int at) {
        if (channel == 0) {
            Vec3 v = Space.mirrorY(new Vec3(values[at], values[at + 1], values[at + 2]));
            values[at] = v.x();
            values[at + 1] = v.y();
            values[at + 2] = v.z();
        } else if (channel == 1) {
            Quat q = Space.mirrorY(new Quat(values[at], values[at + 1], values[at + 2], values[at + 3]));
            values[at] = q.x();
            values[at + 1] = q.y();
            values[at + 2] = q.z();
            values[at + 3] = q.w();
        }
    }
}
