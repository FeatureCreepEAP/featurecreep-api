package featurecreep.api.image;

import java.io.IOException;

public final class ImageDecodeException extends IOException {

    private static final long serialVersionUID = 1L;

    public ImageDecodeException(String message) {
        super(message);
    }
}
