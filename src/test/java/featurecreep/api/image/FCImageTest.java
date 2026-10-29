package featurecreep.api.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FCImageTest {

    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;
    private static final int WHITE = 0xFFFFFFFF;

    @Test
    void ofCopiesInputArray() {
        int[] pixels = {RED, GREEN, BLUE, WHITE};
        FCImage image = FCImage.of(2, 2, pixels);
        pixels[0] = 0;
        assertEquals(RED, image.getArgb(0, 0));
    }

    @Test
    void ofRejectsMismatchedLength() {
        assertThrows(IllegalArgumentException.class, () -> FCImage.of(2, 2, new int[3]));
    }

    @Test
    void ofRejectsNonPositiveDimensions() {
        assertThrows(IllegalArgumentException.class, () -> FCImage.of(0, 2, new int[0]));
    }

    @Test
    void cropReturnsSubRegion() {
        FCImage image = FCImage.of(3, 2, new int[] {RED, GREEN, BLUE, WHITE, RED, GREEN});
        FCImage cropped = image.crop(1, 0, 2, 2);
        assertEquals(2, cropped.width());
        assertEquals(2, cropped.height());
        assertArrayEquals(new int[] {GREEN, BLUE, RED, GREEN}, cropped.toArgbArray());
    }

    @Test
    void cropOutsideBoundsThrows() {
        FCImage image = FCImage.of(2, 2, new int[4]);
        assertThrows(IllegalArgumentException.class, () -> image.crop(1, 1, 2, 2));
    }

    @Test
    void flipHorizontalReversesRows() {
        FCImage image = FCImage.of(2, 1, new int[] {RED, BLUE});
        assertArrayEquals(new int[] {BLUE, RED}, image.flipHorizontal().toArgbArray());
    }

    @Test
    void flipVerticalReversesColumns() {
        FCImage image = FCImage.of(1, 2, new int[] {RED, BLUE});
        assertArrayEquals(new int[] {BLUE, RED}, image.flipVertical().toArgbArray());
    }

    @Test
    void mapPixelsAppliesOperator() {
        FCImage image = FCImage.of(1, 1, new int[] {0xFF102030});
        FCImage inverted = image.mapPixels(argb -> argb ^ 0x00FFFFFF);
        assertEquals(0xFFEFDFCF, inverted.getArgb(0, 0));
    }

    @Test
    void resizeNearestDuplicatesPixels() {
        FCImage image = FCImage.of(2, 1, new int[] {RED, BLUE});
        assertArrayEquals(new int[] {RED, RED, BLUE, BLUE}, image.resizeNearest(4, 1).toArgbArray());
    }

    @Test
    void resizeBilinearKeepsUniformImageUniform() {
        FCImage image = FCImage.of(2, 2, new int[] {GREEN, GREEN, GREEN, GREEN});
        assertArrayEquals(new int[] {GREEN, GREEN, GREEN, GREEN, GREEN, GREEN, GREEN, GREEN, GREEN},
                image.resizeBilinear(3, 3).toArgbArray());
    }

    @Test
    void equalityIsByContent() {
        FCImage a = FCImage.of(1, 1, new int[] {RED});
        FCImage b = FCImage.of(1, 1, new int[] {RED});
        FCImage c = FCImage.of(1, 1, new int[] {BLUE});
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
