package com.cappleapple.openlights.client.scene;

/** Quantized propagation cost: power expands reach, never source brightness. */
public final class AggregateFalloff {
    public static int step(double equivalentSources, double maximumMultiplier, double strength) {
        double gain = Math.min(maximumMultiplier, 1 + strength * (Math.cbrt(Math.max(1, equivalentSources)) - 1));
        return Math.max(1, Math.min(17, (int)Math.ceil(17 / gain)));
    }
    private AggregateFalloff() {}
}
