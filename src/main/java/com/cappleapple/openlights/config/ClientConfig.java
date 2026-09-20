package com.cappleapple.openlights.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue BEAM_DUST;
    public static final ForgeConfigSpec.IntValue MAX_LIGHTS;
    public static final ForgeConfigSpec.IntValue MAX_SHADOW_LIGHTS;
    public static final ForgeConfigSpec.IntValue SHADOW_RESOLUTION;
    public static final ForgeConfigSpec.IntValue VOLUMETRIC_STEPS;
    public static final ForgeConfigSpec.DoubleValue RENDER_SCALE;
    public static final ForgeConfigSpec.DoubleValue MAX_RANGE;
    public static final ForgeConfigSpec.DoubleValue INTENSITY_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MEDIUM_UPDATE_TICKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        ENABLED = builder.define("enabled", true);
        BEAM_DUST = builder.comment("Render profile-defined beam dust; also respects Minecraft particle settings.").define("beamDust", true);
        MAX_LIGHTS = builder.comment("Maximum lights processed per frame.")
                .defineInRange("maxLights", 8, 1, 8);
        MAX_SHADOW_LIGHTS = builder.comment("Maximum lights receiving shadow calculations.")
                .defineInRange("maxShadowLights", 4, 0, 4);
        SHADOW_RESOLUTION = builder.comment("Shadow-map resolution per face, in pixels.")
                .defineInRange("shadowResolution", 256, 64, 2048);
        VOLUMETRIC_STEPS = builder.comment("Samples per volumetric ray.")
                .defineInRange("volumetricSteps", 12, 4, 24);
        RENDER_SCALE = builder.comment("Volumetric buffer scale relative to the window.")
                .defineInRange("renderScale", 0.5, 0.25, 1.0);
        MAX_RANGE = builder.comment("Maximum rendered light range in blocks.")
                .defineInRange("maxRange", 24.0, 1.0, 32.0);
        INTENSITY_MULTIPLIER = builder.comment("Multiplier applied to all light intensities.")
                .defineInRange("intensityMultiplier", 1.0, 0.0, 8.0);
        MEDIUM_UPDATE_TICKS = builder.comment("Ticks between transparent-medium cache refreshes.")
                .defineInRange("mediumUpdateTicks", 10, 1, 200);
        SPEC = builder.build();
    }

    private ClientConfig() {}
}
