package featurecreep.api.model.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecreep.api.model.Model;
import featurecreep.api.model.ModelFormatException;
import featurecreep.api.model.io.aimcube.AimCubeReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelFormatsTest {

    private static final byte[] AIMCUBE = """
            aimcube:1

            texture_size:[64,64]

            part body
            pivot:[0,24,0]

            cube:
                tex=[0,0]
                pos=[-4,-12,-2]
                size=[8,12,4]
            """.getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path dir;

    @Test
    void readerIsFoundById() {
        assertTrue(ModelFormats.readerById(AimCubeReader.FORMAT_ID).isPresent());
        assertFalse(ModelFormats.readerById("no-such-format").isPresent());
    }

    @Test
    void writerIsFoundById() {
        assertTrue(ModelFormats.writerById("pdx-mesh").isPresent());
        assertFalse(ModelFormats.writerById("no-such-format").isPresent());
    }

    @Test
    void readerIsFoundByExtensionIgnoringCase() {
        assertInstanceOf(AimCubeReader.class, ModelFormats.readerForExtension("AIMCUBE").orElseThrow());
        assertFalse(ModelFormats.readerForExtension("nope").isPresent());
    }

    @Test
    void readerIsDetectedFromHeaderBytes() {
        assertEquals(AimCubeReader.FORMAT_ID,
                ModelFormats.detect(AIMCUBE).orElseThrow().descriptor().id());
        assertFalse(ModelFormats.detect("definitely not a model".getBytes(StandardCharsets.UTF_8)).isPresent());
    }

    @Test
    void readFallsBackToSniffingWhenExtensionIsUnknown() throws IOException {
        Path file = Files.write(dir.resolve("model.dat"), AIMCUBE);
        Model model = ModelFormats.read(file);
        assertEquals(1, model.nodeCount());
        assertEquals("body", model.nodes().get(0).name());
    }

    @Test
    void readOfUnrecognisedContentThrows() throws IOException {
        Path file = Files.write(dir.resolve("model.xyz"), "garbage".getBytes(StandardCharsets.UTF_8));
        assertThrows(ModelFormatException.class, () -> ModelFormats.read(file));
    }
}
