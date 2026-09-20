package com.cappleapple.openlights.api.client;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistent client lights independent of the rendering implementation.
 * Mutations and snapshots are synchronized and may be called from any thread.
 * The renderer clears this registry when leaving a world.
 */
public final class OpenLightsApi {
    private static final Map<LightKey, LightDefinition> LIGHTS = new LinkedHashMap<>();

    private OpenLightsApi() {}

    public static synchronized LightHandle create(ResourceLocation owner, LightDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        LightKey key = new LightKey(owner, UUID.randomUUID());
        LIGHTS.put(key, definition);
        return new Handle(key);
    }

    /** Immutable snapshot; changing a handle does not mutate prior snapshots. */
    public static synchronized Map<LightKey, LightDefinition> snapshot() {
        return Map.copyOf(LIGHTS);
    }

    /** Invalidates all handles, including handles retained by providers. */
    public static synchronized void clear() {
        LIGHTS.clear();
    }

    private record Handle(LightKey key) implements LightHandle {
        @Override
        public void update(LightDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            synchronized (OpenLightsApi.class) {
                if (!LIGHTS.containsKey(key)) {
                    throw new IllegalStateException("Light handle is closed or belongs to an unloaded world");
                }
                LIGHTS.put(key, definition);
            }
        }

        @Override
        public boolean isValid() {
            synchronized (OpenLightsApi.class) {
                return LIGHTS.containsKey(key);
            }
        }

        @Override
        public void close() {
            synchronized (OpenLightsApi.class) {
                LIGHTS.remove(key);
            }
        }
    }
}
