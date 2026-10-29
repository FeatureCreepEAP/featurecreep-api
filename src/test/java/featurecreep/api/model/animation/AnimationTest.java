package featurecreep.api.model.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecreep.api.model.BindingKey;
import featurecreep.api.model.Model;
import featurecreep.api.model.ModelCapabilities;
import featurecreep.api.model.Node;
import featurecreep.api.model.Pose;
import featurecreep.api.model.Transform;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AnimationTest {

    private static final float EPS = 1e-5f;

    private static final AnimationFormat BY_NAME = new AnimationFormat("by-name", BindingKey.NAME, Set.of());
    private static final AnimationFormat BY_INDEX = new AnimationFormat("by-index", BindingKey.INDEX, Set.of());
    private static final AnimationFormat BY_UUID = new AnimationFormat("by-uuid", BindingKey.UUID, Set.of());

    private static Model rig(ModelCapabilities capabilities) {
        Node arm = new Node("arm", Transform.IDENTITY, List.of(), null, true, Map.of("uuid", "u-arm"));
        Node root = new Node("root", Transform.IDENTITY, List.of(arm), null);
        return new Model(List.of(root), List.of(), List.of("smile"), capabilities, Map.of(), Map.of());
    }

    private static Model rig() {
        return rig(ModelCapabilities.any());
    }

    private static float[] eval(Keys keys, double t) {
        float[] out = new float[keys.components()];
        keys.evaluate(t, out);
        return out;
    }

    private static void assertFloats(float[] expected, float[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], EPS, "index " + i);
        }
    }

    private static Track translation(BindingKey key, String ref, Keys keys) {
        return new Track(Track.Kind.TRANSLATION, key, ref, keys);
    }

    private static AnimationClip clip(boolean looping, Track... tracks) {
        return new AnimationClip("clip", 1.0, looping, List.of(tracks), Map.of());
    }

    @Test
    void sampledKeysInterpolateBetweenFramesAndClamp() {
        Keys.Sampled keys = new Keys.Sampled(2f, 3, new float[] {0, 10, 20}, 1);
        assertEquals(0f, eval(keys, -1)[0], EPS);
        assertEquals(5f, eval(keys, 0.25)[0], EPS);
        assertEquals(20f, eval(keys, 5)[0], EPS);
        assertEquals(1.0, keys.endTime(), 1e-9);
    }

    @Test
    void sampledAndKeyedFormsAgreeAtKeyTimesAndBetweenThem() {
        Keys.Sampled sampled = new Keys.Sampled(4f, 4, new float[] {
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 3);
        Keys keyed = sampled.toKeyed();
        for (int i = 0; i < 4; i++) {
            double t = i / 4.0;
            assertFloats(eval(sampled, t), eval(keyed, t));
        }
        assertFloats(eval(sampled, 0.1), eval(keyed, 0.1));
        assertFloats(eval(sampled, 0.6), eval(keyed, 0.6));
    }

    @Test
    void stepKeysHoldTheEarlierValue() {
        Keys.Keyed keys = new Keys.Keyed(new double[] {0, 1}, new float[] {0, 10},
                new Interp[] {Interp.STEP, Interp.STEP}, null, null, 1);
        assertEquals(0f, eval(keys, 0.99)[0], EPS);
        assertEquals(10f, eval(keys, 1)[0], EPS);
    }

    @Test
    void linearKeysInterpolateByTime() {
        Keys.Keyed keys = new Keys.Keyed(new double[] {0, 2}, new float[] {0, 4},
                new Interp[] {Interp.LINEAR, Interp.LINEAR}, null, null, 1);
        assertEquals(2f, eval(keys, 1)[0], EPS);
    }

    @Test
    void bezierWithLinearHandlesMatchesLinearInterpolation() {
        Keys.Keyed keys = new Keys.Keyed(new double[] {0, 1}, new float[] {0, 1},
                new Interp[] {Interp.BEZIER, Interp.BEZIER},
                new float[] {0, 1f / 3f, -1f / 3f, 0}, new float[] {0, 1f / 3f, 2f / 3f, 1}, 1);
        assertEquals(0.5f, eval(keys, 0.5)[0], 1e-3f);
        assertEquals(0.25f, eval(keys, 0.25)[0], 1e-3f);
    }

    @Test
    void bezierKeysWithoutHandlesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Keys.Keyed(new double[] {0, 1},
                new float[] {0, 1}, new Interp[] {Interp.BEZIER, Interp.BEZIER}, null, null, 1));
    }

    @Test
    void keyTimesMustIncrease() {
        assertThrows(IllegalArgumentException.class, () -> new Keys.Keyed(new double[] {1, 0},
                new float[] {0, 1}, new Interp[] {Interp.LINEAR, Interp.LINEAR}, null, null, 1));
    }

    @Test
    void trackRejectsComponentCountOutsideItsKind() {
        Keys one = new Keys.Sampled(1f, 1, new float[] {0}, 1);
        assertThrows(IllegalArgumentException.class, () -> translation(BindingKey.NAME, "arm", one));
    }

    @Test
    void boundTranslationWritesInterpolatedPose() {
        Model model = rig();
        Track track = translation(BindingKey.NAME, "arm",
                new Keys.Sampled(1f, 2, new float[] {0, 0, 0, 2, 4, 6}, 3));
        ClipAnimator animator = ClipAnimator.bind(clip(false, track), BY_NAME, model).requireComplete();
        Pose pose = Pose.rest(model);
        animator.apply(model, 0.5, pose);
        assertFloats(new float[] {1, 2, 3}, new float[] {
                pose.translations()[3], pose.translations()[4], pose.translations()[5]});
    }

    @Test
    void loopingClipWrapsTimeAndNonLoopingClampsIt() {
        Model model = rig();
        Track track = translation(BindingKey.NAME, "arm",
                new Keys.Sampled(1f, 2, new float[] {0, 0, 0, 2, 4, 6}, 3));
        Pose pose = Pose.rest(model);

        ClipAnimator looping = ClipAnimator.bind(clip(true, track), BY_NAME, model).requireComplete();
        looping.apply(model, 1.25, pose);
        assertEquals(0.5f, pose.translations()[3], EPS);

        ClipAnimator once = ClipAnimator.bind(clip(false, track), BY_NAME, model).requireComplete();
        once.apply(model, 1.25, pose);
        assertEquals(2f, pose.translations()[3], EPS);
    }

    @Test
    void rotationKeysAreNormalisedOnApply() {
        Model model = rig();
        Track track = new Track(Track.Kind.ROTATION, BindingKey.NAME, "arm",
                new Keys.Sampled(1f, 1, new float[] {0, 0, 0, 2}, 4));
        ClipAnimator animator = ClipAnimator.bind(clip(false, track), BY_NAME, model).requireComplete();
        Pose pose = Pose.rest(model);
        animator.apply(model, 0, pose);
        assertFloats(new float[] {0, 0, 0, 1}, new float[] {
                pose.rotations()[4], pose.rotations()[5], pose.rotations()[6], pose.rotations()[7]});
    }

    @Test
    void morphTrackWritesMorphWeight() {
        Model model = rig();
        Track track = new Track(Track.Kind.MORPH, BindingKey.NAME, "smile",
                new Keys.Sampled(1f, 1, new float[] {0.75f}, 1));
        ClipAnimator animator = ClipAnimator.bind(clip(false, track), BY_NAME, model).requireComplete();
        Pose pose = Pose.rest(model);
        animator.apply(model, 0, pose);
        assertEquals(0.75f, pose.morphWeights()[0], EPS);
    }

    @Test
    void uuidAndIndexBindingsResolveToTheSameNode() {
        Model model = rig();
        Keys.Sampled values = new Keys.Sampled(1f, 1, new float[] {7, 8, 9}, 3);

        ClipAnimator byUuid = ClipAnimator.bind(clip(false, translation(BindingKey.UUID, "u-arm", values)),
                BY_UUID, model).requireComplete();
        ClipAnimator byIndex = ClipAnimator.bind(clip(false, translation(BindingKey.INDEX, "1", values)),
                BY_INDEX, model).requireComplete();

        Pose fromUuid = Pose.rest(model);
        Pose fromIndex = Pose.rest(model);
        byUuid.apply(model, 0, fromUuid);
        byIndex.apply(model, 0, fromIndex);
        assertFloats(fromIndex.translations(), fromUuid.translations());
        assertEquals(7f, fromUuid.translations()[3], EPS);
    }

    @Test
    void unresolvedTracksAreReportedAndRequireCompleteThrows() {
        Model model = rig();
        Keys.Sampled values = new Keys.Sampled(1f, 1, new float[] {0, 0, 0}, 3);
        BindReport report = ClipAnimator.bind(clip(false, translation(BindingKey.NAME, "nope", values)),
                BY_NAME, model);
        assertFalse(report.complete());
        assertEquals(1, report.unresolved().size());
        assertEquals("nope", report.unresolved().get(0).track().ref());
        AnimationBindException error = assertThrows(AnimationBindException.class, report::requireComplete);
        assertTrue(error.getMessage().contains("nope"));
    }

    @Test
    void indexOutOfRangeIsUnresolved() {
        Model model = rig();
        Keys.Sampled values = new Keys.Sampled(1f, 1, new float[] {0, 0, 0}, 3);
        BindReport report = ClipAnimator.bind(clip(false, translation(BindingKey.INDEX, "9", values)),
                BY_INDEX, model);
        assertEquals(1, report.unresolved().size());
    }

    @Test
    void trackKeyDifferingFromFormatBindingIsReported() {
        Model model = rig();
        Keys.Sampled values = new Keys.Sampled(1f, 1, new float[] {0, 0, 0}, 3);
        BindReport report = ClipAnimator.bind(clip(false, translation(BindingKey.INDEX, "1", values)),
                BY_NAME, model);
        assertFalse(report.complete());
        assertTrue(report.unresolved().get(0).reason().contains("binds by"));
    }

    @Test
    void modelRejectsFormatOutsideItsCapabilities() {
        Model model = rig(new ModelCapabilities(Set.of("other"), EnumSet.allOf(BindingKey.class)));
        assertThrows(AnimationBindException.class, () -> ClipAnimator.bind(clip(false), BY_NAME, model));
    }

    @Test
    void modelRejectsBindingOutsideItsCapabilities() {
        Model model = rig(new ModelCapabilities(Set.of(), EnumSet.of(BindingKey.INDEX)));
        assertThrows(AnimationBindException.class, () -> ClipAnimator.bind(clip(false), BY_NAME, model));
    }

    @Test
    void formatNeedingNamedBonesRejectsEmptyModel() {
        Model empty = Model.of(List.of());
        assertThrows(AnimationBindException.class,
                () -> ClipAnimator.bind(clip(false), AnimationFormat.PDX_ANIM, empty));
    }

    @Test
    void applyRejectsADifferentModel() {
        Model bound = rig();
        Model other = rig();
        ClipAnimator animator = ClipAnimator.bind(clip(false), BY_NAME, bound).requireComplete();
        assertThrows(IllegalArgumentException.class, () -> animator.apply(other, 0, Pose.rest(other)));
    }
}
