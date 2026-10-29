package featurecreep.api.model.io;

import featurecreep.api.model.Model;
import java.util.List;
import java.util.Objects;

/** A parsed model and the non-fatal issues found while parsing it. */
public record ModelReadResult(Model model, List<String> warnings) {

    public ModelReadResult {
        Objects.requireNonNull(model, "model");
        warnings = List.copyOf(warnings);
    }
}
