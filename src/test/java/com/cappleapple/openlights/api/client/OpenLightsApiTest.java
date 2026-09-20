package com.cappleapple.openlights.api.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OpenLightsApiTest {
    private static final ResourceLocation OWNER = new ResourceLocation("openlights", "test");

    @AfterEach
    void clear() { OpenLightsApi.clear(); }

    @Test
    void snapshotsRemainImmutableAfterHandleUpdates() {
        var initial = point(1);
        var handle = OpenLightsApi.create(OWNER, initial);
        var snapshot = OpenLightsApi.snapshot();
        var updated = point(2);
        handle.update(updated);
        assertSame(initial, snapshot.get(handle.key()));
        assertSame(updated, OpenLightsApi.snapshot().get(handle.key()));
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test
    void closingOneHandleDoesNotRemoveAnotherProvidersLight() {
        var first = OpenLightsApi.create(OWNER, point(1));
        var second = OpenLightsApi.create(OWNER, point(1));
        assertNotEquals(first.key(), second.key());
        first.close();
        first.close();
        assertFalse(first.isValid());
        assertTrue(second.isValid());
        assertEquals(1, OpenLightsApi.snapshot().size());
        assertThrows(IllegalStateException.class, () -> first.update(point(1)));
    }

    @Test
    void worldClearInvalidatesRetainedHandles() {
        var handle = OpenLightsApi.create(OWNER, point(1));
        OpenLightsApi.clear();
        assertFalse(handle.isValid());
        assertThrows(IllegalStateException.class, () -> handle.update(point(1)));
        handle.close();
        assertTrue(OpenLightsApi.snapshot().isEmpty());
    }

    @Test
    void collectionKeysReplaceOnlyTheirOwnDefinition() {
        var event = new CollectLightsEvent(0.25f);
        var key = new LightKey(OWNER, UUID.randomUUID());
        event.add(key, point(1));
        var updated = point(2);
        event.add(key, updated);
        assertEquals(0.25f, event.partialTick());
        assertEquals(1, event.lights().size());
        assertSame(updated, event.lights().get(key));
        assertThrows(UnsupportedOperationException.class, () -> event.lights().clear());
    }

    private static LightDefinition.Point point(float intensity) {
        return new LightDefinition.Point(Vec3.ZERO, new Vec3(1, 1, 1), intensity, 20, true, 0.5f);
    }
}
