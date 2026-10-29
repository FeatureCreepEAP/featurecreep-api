package featurecreep.api.model;

import java.io.IOException;

public final class ModelFormatException extends IOException {

    private static final long serialVersionUID = 1L;

    public ModelFormatException(String message) {
        super(message);
    }
}
