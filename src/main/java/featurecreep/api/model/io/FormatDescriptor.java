package featurecreep.api.model.io;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Identity of a format. {@code extensions} are lower-case, without the dot. {@code sniff} receives the first
 * {@link #HEADER_BYTES} bytes of a file and returns true when they look like this format.
 */
public record FormatDescriptor(String id, Set<String> extensions, Predicate<byte[]> sniff) {

    public static final int HEADER_BYTES = 64;

    public FormatDescriptor {
        Objects.requireNonNull(id, "id");
        extensions = Set.copyOf(extensions);
        Objects.requireNonNull(sniff, "sniff");
    }
}
