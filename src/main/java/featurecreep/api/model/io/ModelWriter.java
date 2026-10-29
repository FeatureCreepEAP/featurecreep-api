package featurecreep.api.model.io;

import featurecreep.api.model.Model;
import featurecreep.api.model.ModelFormatException;

/** Serialises a model. Geometry the format cannot represent is converted, and the conversion is documented per writer. */
public interface ModelWriter {

    FormatDescriptor descriptor();

    byte[] write(Model model) throws ModelFormatException;
}
