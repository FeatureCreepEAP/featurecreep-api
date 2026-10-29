package featurecreep.api.model;

import java.util.Arrays;
import java.util.List;

/**
 * Per-instance local transforms, indexed by node index. Arrays are exposed directly so bridges can copy them without
 * allocating. Translations and scales take 3 floats per node, rotations take 4 (xyzw).
 */
public final class Pose {

    private final Model model;
    private final float[] translations;
    private final float[] rotations;
    private final float[] scales;
    private final boolean[] visible;
    private final float[] morphWeights;

    private Pose(Model model) {
        this.model = model;
        int n = model.nodeCount();
        this.translations = new float[n * 3];
        this.rotations = new float[n * 4];
        this.scales = new float[n * 3];
        this.visible = new boolean[n];
        this.morphWeights = new float[model.morphNames().size()];
    }

    public static Pose rest(Model model) {
        Pose pose = new Pose(model);
        pose.reset();
        return pose;
    }

    public void reset() {
        List<Node> nodes = model.nodes();
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            Transform t = node.transform();
            translations[i * 3] = t.translation().x();
            translations[i * 3 + 1] = t.translation().y();
            translations[i * 3 + 2] = t.translation().z();
            rotations[i * 4] = t.rotation().x();
            rotations[i * 4 + 1] = t.rotation().y();
            rotations[i * 4 + 2] = t.rotation().z();
            rotations[i * 4 + 3] = t.rotation().w();
            scales[i * 3] = t.scale().x();
            scales[i * 3 + 1] = t.scale().y();
            scales[i * 3 + 2] = t.scale().z();
            visible[i] = node.visible();
        }
        Arrays.fill(morphWeights, 0f);
    }

    public Model model() {
        return model;
    }

    public float[] translations() {
        return translations;
    }

    public float[] rotations() {
        return rotations;
    }

    public float[] scales() {
        return scales;
    }

    public boolean[] visible() {
        return visible;
    }

    public float[] morphWeights() {
        return morphWeights;
    }
}
