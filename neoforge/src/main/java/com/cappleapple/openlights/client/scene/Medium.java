package com.cappleapple.openlights.client.scene;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/** A world-space optical volume. All members are immutable. */
public record Medium(AABB bounds, Vec3 tint, float throughput, float densityBoost) {
    public Medium {
        Objects.requireNonNull(bounds);
        Objects.requireNonNull(tint);
    }
}
