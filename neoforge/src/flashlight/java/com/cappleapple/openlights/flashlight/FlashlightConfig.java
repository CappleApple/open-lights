package com.cappleapple.openlights.flashlight;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned battery policy, synchronized by the loader's server config system. */
public final class FlashlightConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue REQUIRE_ENERGY;
    public static final ModConfigSpec.IntValue CAPACITY, MAX_RECEIVE, ENERGY_PER_TICK;
    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("battery");
        REQUIRE_ENERGY = builder.comment("Require RF/FE power while a flashlight is enabled and held.").define("requireEnergy", true);
        CAPACITY = builder.comment("Battery capacity in RF/FE. New flashlights start empty.").defineInRange("capacity", 100000, 1, Integer.MAX_VALUE);
        MAX_RECEIVE = builder.comment("Maximum RF/FE accepted per charging operation.").defineInRange("maxReceive", 1000, 1, Integer.MAX_VALUE);
        ENERGY_PER_TICK = builder.comment("RF/FE consumed per server tick while held and enabled. Zero disables consumption.").defineInRange("energyPerTick", 1, 0, Integer.MAX_VALUE);
        builder.pop();
        SPEC = builder.build();
    }
    private FlashlightConfig() {}
    public static boolean requiresEnergy() { try { return REQUIRE_ENERGY.get(); } catch (IllegalStateException unloaded) { return true; } }
    public static int capacity() { return value(CAPACITY, 100000); }
    public static int maxReceive() { return value(MAX_RECEIVE, 1000); }
    public static int energyPerTick() { return value(ENERGY_PER_TICK, 1); }
    private static int value(ModConfigSpec.IntValue value, int fallback) {
        try { return value.get(); } catch (IllegalStateException unloaded) { return fallback; }
    }
}
