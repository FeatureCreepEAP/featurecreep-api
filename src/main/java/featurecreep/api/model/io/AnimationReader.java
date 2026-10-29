package featurecreep.api.model.io;

import featurecreep.api.model.ModelFormatException;
import featurecreep.api.model.animation.AnimationClip;
import featurecreep.api.model.animation.AnimationFormat;

public interface AnimationReader {

    FormatDescriptor descriptor();

    AnimationFormat format();

    AnimationReadResult read(byte[] data, String sourceName) throws ModelFormatException;
}
