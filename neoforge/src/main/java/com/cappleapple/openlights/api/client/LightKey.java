package com.cappleapple.openlights.api.client;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for one provider-owned light. */
public record LightKey(ResourceLocation owner, UUID id) {
    public LightKey {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(id, "id");
    }
}
