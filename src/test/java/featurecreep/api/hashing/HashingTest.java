package featurecreep.api.hashing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class HashingTest {

    @Test
    void md5MatchesKnownVector() throws Exception {
        byte[] data = "abc".getBytes(StandardCharsets.UTF_8);
        assertEquals("900150983cd24fb0d6963f7d28e17f72", Md5.getHashFromBytesAsString(data));
        assertEquals("900150983cd24fb0d6963f7d28e17f72",
                Md5.getHashFromInputStreamAsString(new ByteArrayInputStream(data)));
    }

    @Test
    void sha256MatchesKnownVector() throws Exception {
        byte[] data = "abc".getBytes(StandardCharsets.UTF_8);
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Sha256.getHashFromBytesAsString(data));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Sha256.getHashFromInputStreamAsString(new ByteArrayInputStream(data)));
    }
}
