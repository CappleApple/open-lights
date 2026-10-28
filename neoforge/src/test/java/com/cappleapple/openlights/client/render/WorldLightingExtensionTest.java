package com.cappleapple.openlights.client.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldLightingExtensionTest {
    @Test void absentAddonLeavesWorldLightingInactive() {
        var extension = WorldLightingExtension.NONE;
        assertFalse(extension.hasWork());
        assertFalse(extension.replacement());
        assertFalse(extension.nativeSky());
        assertEquals(0,extension.worldLightmap(true));
        var stats = extension.statistics(7);
        assertFalse(stats.replacement());
        assertEquals(0,stats.worldCapacity());
        assertEquals(0,stats.giProbes());
        assertEquals(0,stats.aggregateCells());
        assertFalse(stats.aggregateWorker());
        assertEquals(7,stats.sceneUpdated());
    }
}
