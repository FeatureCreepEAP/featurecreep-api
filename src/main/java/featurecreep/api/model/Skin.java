package featurecreep.api.model;

import java.util.Objects;

/**
 * Skin weights for a mesh. {@code jointNodes} maps joint slots to model node indices. {@code jointIndices} and
 * {@code weights} hold {@code influences} entries per vertex. Unused slots use joint index -1 and weight 0.
 */
public record Skin(int[] jointNodes, int influences, int[] jointIndices, float[] weights) {

    public Skin {
        Objects.requireNonNull(jointNodes, "jointNodes");
        Objects.requireNonNull(jointIndices, "jointIndices");
        Objects.requireNonNull(weights, "weights");
        if (influences <= 0) {
            throw new IllegalArgumentException("influences must be positive");
        }
        if (jointIndices.length != weights.length || jointIndices.length % influences != 0) {
            throw new IllegalArgumentException("skin arrays do not match influence count");
        }
        for (int joint : jointIndices) {
            if (joint < -1 || joint >= jointNodes.length) {
                throw new IllegalArgumentException("joint slot " + joint + " is out of range");
            }
        }
    }

    public int vertexCount() {
        return jointIndices.length / influences;
    }
}
