package featurecreep.api.model.io;

import featurecreep.api.model.Model;
import featurecreep.api.model.ModelFormatException;
import featurecreep.api.model.animation.AnimationClip;
import featurecreep.api.model.animation.AnimationFormat;

/** Writes a clip for {@code model}. The model supplies the bone order and joint count the format needs. */
public interface AnimationWriter {

    FormatDescriptor descriptor();

    AnimationFormat format();

    byte[] write(Model model, AnimationClip clip) throws ModelFormatException;
}
