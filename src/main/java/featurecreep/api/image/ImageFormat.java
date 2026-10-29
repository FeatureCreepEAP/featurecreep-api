package featurecreep.api.image;

import java.util.Objects;
import java.util.Optional;

public enum ImageFormat {
    PNG("image/png", "png"),
    DDS("image/vnd-ms.dds", "dds");

    private final String mimeType;
    private final String extension;

    ImageFormat(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public String mimeType() {
        return mimeType;
    }

    public String extension() {
        return extension;
    }

    /** Identifies the container from its leading magic bytes, or returns empty when unknown. */
    public static Optional<ImageFormat> detect(byte[] data) {
        Objects.requireNonNull(data, "data");
        if (startsWith(data, PNG_MAGIC)) {
            return Optional.of(PNG);
        }
        if (startsWith(data, DDS_MAGIC)) {
            return Optional.of(DDS);
        }
        return Optional.empty();
    }

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] DDS_MAGIC = {'D', 'D', 'S', ' '};

    private static boolean startsWith(byte[] data, byte[] magic) {
        if (data.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
