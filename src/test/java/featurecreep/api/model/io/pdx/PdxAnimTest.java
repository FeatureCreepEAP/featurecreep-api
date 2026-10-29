package featurecreep.api.model.io.pdx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import featurecreep.api.model.BindingKey;
import featurecreep.api.model.Model;
import featurecreep.api.model.ModelFormatException;
import featurecreep.api.model.Node;
import featurecreep.api.model.Transform;
import featurecreep.api.model.animation.AnimationClip;
import featurecreep.api.model.animation.Interp;
import featurecreep.api.model.animation.Keys;
import featurecreep.api.model.animation.Track;
import featurecreep.api.model.io.AnimationReadResult;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PdxAnimTest {

    private static final float EPS = 1e-5f;

    private static Model skeleton() {
        Node arm = new Node("arm", Transform.IDENTITY, List.of(), null);
        Node hip = new Node("hip", Transform.IDENTITY, List.of(arm), null);
        return Model.of(List.of(hip));
    }

    private static AnimationReadResult roundTrip(Model model, AnimationClip clip) throws ModelFormatException {
        byte[] bytes = new PdxAnimWriter().write(model, clip);
        return new PdxAnimReader().read(bytes, "walk.anim");
    }

    @Test
    void sampledTracksRoundTrip() throws ModelFormatException {
        float[] translation = {0, 1, 0, 0.5f, 1.5f, 0.25f, 1, 2, 0.5f};
        float[] rotation = {0, 0, 0, 1, 0, 0.5f, 0, 0.866f, 0, 0, 0, 1};
        Track hip = new Track(Track.Kind.TRANSLATION, BindingKey.NAME, "hip",
                new Keys.Sampled(30f, 3, translation, 3));
        Track arm = new Track(Track.Kind.ROTATION, BindingKey.NAME, "arm",
                new Keys.Sampled(30f, 3, rotation, 4));
        AnimationClip clip = new AnimationClip("walk", 2.0 / 30.0, false, List.of(hip, arm), Map.of());

        AnimationClip read = roundTrip(skeleton(), clip).clip();

        assertEquals(2, read.tracks().size());
        Track readHip = read.tracks().get(0);
        Track readArm = read.tracks().get(1);
        assertEquals(Track.Kind.TRANSLATION, readHip.kind());
        assertEquals("hip", readHip.ref());
        assertEquals(Track.Kind.ROTATION, readArm.kind());
        assertEquals("arm", readArm.ref());
        assertArrayEquals(translation, ((Keys.Sampled) readHip.keys()).values(), EPS);
        assertArrayEquals(rotation, ((Keys.Sampled) readArm.keys()).values(), EPS);
        assertEquals(2.0 / 30.0, read.duration(), 1e-9);
    }

    @Test
    void keyedTrackIsSampledAtClipFrameRate() throws ModelFormatException {
        Keys.Keyed keyed = new Keys.Keyed(new double[] {0.0, 0.1}, new float[] {0, 0, 0, 1, 2, 3},
                new Interp[] {Interp.LINEAR, Interp.LINEAR}, null, null, 3);
        Track hip = new Track(Track.Kind.TRANSLATION, BindingKey.NAME, "hip", keyed);
        AnimationClip clip = new AnimationClip("keyed", 0.1, false, List.of(hip), Map.of());

        AnimationClip read = roundTrip(skeleton(), clip).clip();

        Keys.Sampled sampled = (Keys.Sampled) read.tracks().get(0).keys();
        assertEquals(4, sampled.frames());
        assertEquals(30f, sampled.fps(), EPS);
        float[] expected = new float[sampled.frames() * 3];
        for (int f = 0; f < sampled.frames(); f++) {
            float[] out = new float[3];
            keyed.evaluate(f / 30.0, out);
            System.arraycopy(out, 0, expected, f * 3, 3);
        }
        assertArrayEquals(expected, sampled.values(), EPS);
    }

    @Test
    void writesHeaderAndPdxassetAsFirstProperty() throws ModelFormatException {
        Track hip = new Track(Track.Kind.TRANSLATION, BindingKey.NAME, "hip",
                new Keys.Sampled(30f, 1, new float[] {0, 0, 0}, 3));
        AnimationClip clip = new AnimationClip("idle", 0.0, false, List.of(hip), Map.of());
        byte[] bytes = new PdxAnimWriter().write(skeleton(), clip);
        assertEquals("@@b@", new String(bytes, 0, 4, StandardCharsets.ISO_8859_1));
        assertEquals('!', bytes[4]);
        assertEquals("pdxasset", new String(bytes, 6, 8, StandardCharsets.ISO_8859_1));
    }

    @Test
    void truncatedAnimIsRejected() throws ModelFormatException {
        Track hip = new Track(Track.Kind.TRANSLATION, BindingKey.NAME, "hip",
                new Keys.Sampled(30f, 2, new float[] {0, 0, 0, 1, 1, 1}, 3));
        AnimationClip clip = new AnimationClip("short", 1.0 / 30.0, false, List.of(hip), Map.of());
        byte[] bytes = new PdxAnimWriter().write(skeleton(), clip);
        byte[] cut = Arrays.copyOf(bytes, bytes.length - 2);
        assertThrows(ModelFormatException.class, () -> new PdxAnimReader().read(cut, "cut.anim"));
    }
}
