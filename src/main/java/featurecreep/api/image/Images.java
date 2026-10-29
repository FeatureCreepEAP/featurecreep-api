package featurecreep.api.image;

import java.util.Objects;

public final class Images {

    private Images() {
    }

    /** Decodes an image, detecting the container from its magic bytes. */
    public static FCImage decode(byte[] data) throws ImageDecodeException {
        ImageFormat format = ImageFormat.detect(data)
                .orElseThrow(() -> new ImageDecodeException("unrecognized image format"));
        return switch (format) {
            case PNG -> PngCodec.decode(data);
            case DDS -> DdsCodec.decode(data);
        };
    }

    /** Encodes an image. DDS output is uncompressed 32-bit A8R8G8B8. */
    public static byte[] encode(FCImage image, ImageFormat format) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(format, "format");
        return switch (format) {
            case PNG -> PngCodec.encode(image);
            case DDS -> DdsCodec.encodeUncompressed(image);
        };
    }

    /** Decodes {@code data} in whatever format it is, then encodes it as {@code target}. */
    public static byte[] convert(byte[] data, ImageFormat target) throws ImageDecodeException {
        return encode(decode(data), target);
    }
}
