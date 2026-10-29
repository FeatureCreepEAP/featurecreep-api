package featurecreep.api.model;

import java.util.Map;
import java.util.Objects;

/** Surface definition. {@code shader} is a format-specific shader name. {@code textures} maps slot names to texture paths. */
public record Material(String name, String shader, Map<String, String> textures) {

    public Material {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(shader, "shader");
        textures = Map.copyOf(textures);
    }
}
