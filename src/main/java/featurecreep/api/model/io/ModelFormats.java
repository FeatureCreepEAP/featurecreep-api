package featurecreep.api.model.io;

import featurecreep.api.model.Model;
import featurecreep.api.model.ModelFormatException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ServiceLoader;

/** Registry of format plugins discovered through {@link ServiceLoader}. */
public final class ModelFormats {

    private static final class Holder {
        static final List<ModelReader> READERS = load(ModelReader.class);
        static final List<ModelWriter> WRITERS = load(ModelWriter.class);
        static final List<AnimationReader> ANIMATION_READERS = load(AnimationReader.class);
        static final List<AnimationWriter> ANIMATION_WRITERS = load(AnimationWriter.class);

        private static <T> List<T> load(Class<T> type) {
            return ServiceLoader.load(type, ModelFormats.class.getClassLoader()).stream()
                    .map(ServiceLoader.Provider::get)
                    .toList();
        }
    }

    private ModelFormats() {
    }

    public static List<ModelReader> readers() {
        return Holder.READERS;
    }

    public static List<ModelWriter> writers() {
        return Holder.WRITERS;
    }

    public static List<AnimationReader> animationReaders() {
        return Holder.ANIMATION_READERS;
    }

    public static List<AnimationWriter> animationWriters() {
        return Holder.ANIMATION_WRITERS;
    }

    public static Optional<ModelReader> readerById(String id) {
        return Holder.READERS.stream().filter(r -> r.descriptor().id().equals(id)).findFirst();
    }

    public static Optional<ModelWriter> writerById(String id) {
        return Holder.WRITERS.stream().filter(w -> w.descriptor().id().equals(id)).findFirst();
    }

    public static Optional<ModelReader> readerForExtension(String extension) {
        String ext = extension.toLowerCase(Locale.ROOT);
        return Holder.READERS.stream().filter(r -> r.descriptor().extensions().contains(ext)).findFirst();
    }

    public static Optional<ModelReader> detect(byte[] data) {
        byte[] header = Arrays.copyOf(data, Math.min(data.length, FormatDescriptor.HEADER_BYTES));
        return Holder.READERS.stream().filter(r -> r.descriptor().sniff().test(header)).findFirst();
    }

    /** Reads a file by extension, falling back to header sniffing when the extension is unknown. */
    public static Model read(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        Optional<ModelReader> reader = dot < 0 ? Optional.empty()
                : readerForExtension(name.substring(dot + 1));
        if (reader.isEmpty()) {
            reader = detect(data);
        }
        ModelReader chosen = reader.orElseThrow(
                () -> new ModelFormatException("unrecognised model format: " + name));
        return chosen.read(data, name).model();
    }
}
