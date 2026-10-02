package com.cappleapple.openlights.client.scene;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TextureColorsTest {
    @Test void transparentPixelsDoNotColorLight() {
        var pixels = TextureColors.summarize(List.of(0x000000ff, 0xffff0000), false);
        assertEquals(0, pixels.average().x);
        assertEquals(1, pixels.average().z);
    }
    @Test void glowingPixelsOutrankDarkHousing() {
        var pixels = TextureColors.summarize(List.of(0xff303030, 0xff404040, 0xffffcc33), false);
        assertTrue(pixels.emission().z > pixels.emission().x * 3);
    }
    @Test void emissiveMaskCanSelectDimColoredPixels() {
        var pixels = TextureColors.summarize(List.of(0x00000000, 0xff002000), true);
        assertTrue(pixels.mask());
        assertTrue(pixels.emission().y > 0);
        assertEquals(0, pixels.emission().x);
    }
}
