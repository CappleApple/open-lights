package com.cappleapple.openlights.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeSkyLightmapTest {
    @Test
    void preservesSkyCoordinatesAndRgbaAtEveryBlockLightLevel() {
        int[] sky = new int[16];
        for (int level = 0; level < sky.length; level++) {
            // Distinct RGBA channels reveal transposed coordinates and channel conversion.
            sky[level] = ((0x80 + level) << 24) | ((0x40 + level) << 16) | ((0x20 + level) << 8) | (0x10 + level);
        }
        int[] pixels = NativeSkyLightmap.expandRows(sky);
        assertEquals(256, pixels.length);
        for (int level = 0; level < sky.length; level++) {
            for (int block = 0; block < 16; block++) {
                assertEquals(sky[level], pixels[level * 16 + block], "sky=" + level + ", block=" + block);
            }
        }
        pixels[0] = 0;
        assertEquals(0x80402010, sky[0], "Expanded texture must not overwrite the native palette");
    }
}
