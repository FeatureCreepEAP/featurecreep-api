package featurecreep.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PKZipUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void zipsNestedDirectoryStructure() throws Exception {
        Path input = Files.createDirectories(tempDir.resolve("input/nested"));
        Files.write(input.resolve("file.txt"), "hello".getBytes(StandardCharsets.UTF_8));
        File output = tempDir.resolve("archive.zip").toFile();

        PKZipUtils.zipDirectory(tempDir.resolve("input").toFile(), output.getAbsolutePath());

        try (ZipFile zip = new ZipFile(output)) {
            assertNotNull(zip.getEntry("nested/file.txt"));
            java.io.InputStream in = zip.getInputStream(zip.getEntry("nested/file.txt"));
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[256];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            assertEquals("hello", new String(out.toByteArray(), StandardCharsets.UTF_8));
        }
    }
}
