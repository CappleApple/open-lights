package com.cappleapple.openlights.api.client;

import net.minecraftforge.eventbus.api.Event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Posted to the Forge event bus on the render thread for each normal world frame.
 * Add frame-local lights here; do not retain the event or modify it asynchronously.
 * Persistent providers may use OpenLightsApi instead. Repeated keys replace their
 * previous definition in this collection.
 */
public final class CollectLightsEvent extends Event {
    private final float partialTick;
    private final Map<LightKey, LightDefinition> lights = new LinkedHashMap<>();

    public CollectLightsEvent(float partialTick) {
        this.partialTick = partialTick;
    }

    public float partialTick() { return partialTick; }

    public void add(LightKey key, LightDefinition definition) {
        lights.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(definition, "definition"));
    }

    public Map<LightKey, LightDefinition> lights() {
        return Collections.unmodifiableMap(lights);
    }
}
