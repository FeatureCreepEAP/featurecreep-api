package featurecreep.attach;

import featurecreep.api.lowlevel.OS;
import featurecreep.attach.panama.PosixNative;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PanamaNativeTest {
    @Test
    void posixIdentityCallWorksThroughPanama() {
        Assumptions.assumeTrue(OS.current().isUnixLike());
        assertTrue(PosixNative.effectiveUid() >= 0);
        assertTrue(PosixNative.effectiveGid() >= 0);
    }
}
