package featurecreep.api.model.io;

import featurecreep.api.model.ModelFormatException;

/** Parses one model format. {@code sourceName} is used in error messages and may be a file name or a label. */
public interface ModelReader {

    FormatDescriptor descriptor();

    ModelReadResult read(byte[] data, String sourceName) throws ModelFormatException;
}
