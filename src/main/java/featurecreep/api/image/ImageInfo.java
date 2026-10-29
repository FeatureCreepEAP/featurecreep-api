package featurecreep.api.image;

import java.util.Objects;

/**
 * Header-level description of an encoded image, read without decoding its pixels.
 *
 * @param format the container format
 * @param width pixel width of the top mip level
 * @param height pixel height of the top mip level
 * @param mipLevels number of mip levels declared by the container, at least 1
 * @param supportsAlpha whether the encoding can store per-pixel alpha; BC1 counts because of its one-bit transparent mode
 */
public record ImageInfo(ImageFormat format, int width, int height, int mipLevels, boolean supportsAlpha) {

    public ImageInfo {
        Objects.requireNonNull(format, "format");
        FCImage.checkDimensions(width, height);
        if (mipLevels < 1) {
            throw new IllegalArgumentException("mipLevels must be at least 1");
        }
    }

    public static ImageInfo read(byte[] data) throws ImageDecodeException {
        ImageFormat format = ImageFormat.detect(data)
                .orElseThrow(() -> new ImageDecodeException("unrecognized image format"));
        return switch (format) {
            case PNG -> PngCodec.readInfo(data);
            case DDS -> DdsCodec.readInfo(data);
        };
    }
}
