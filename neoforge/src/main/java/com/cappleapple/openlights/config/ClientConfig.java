package com.cappleapple.openlights.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Settings for API-requested analytic lights. World lighting belongs to the GI addon. */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue BEAM_DUST;
    public static final ModConfigSpec.IntValue MAX_LIGHTS;
    public static final ModConfigSpec.IntValue LIGHT_RENDER_DISTANCE;
    public static final ModConfigSpec.IntValue MAX_SHADOW_LIGHTS;
    public static final ModConfigSpec.IntValue SHADOW_RESOLUTION;
    public static final ModConfigSpec.IntValue VOLUMETRIC_STEPS;
    public static final ModConfigSpec.DoubleValue RENDER_SCALE;
    public static final ModConfigSpec.DoubleValue MAX_RANGE;
    public static final ModConfigSpec.DoubleValue INTENSITY_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue PERIODIC_CACHE_REFRESH;
    public static final ModConfigSpec.IntValue MEDIUM_UPDATE_TICKS;
    public static final ModConfigSpec.IntValue SCENE_BLOCK_BUDGET;
    public static final ModConfigSpec.IntValue MAX_MEDIA;
    public static final ModConfigSpec.DoubleValue BLOOM_STRENGTH;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ENABLED = builder.define("enabled", true);
        BEAM_DUST = builder.comment("Render profile-defined beam dust; also respects Minecraft particle settings.").define("beamDust", true);
        MAX_LIGHTS = builder.comment("Maximum lights processed per frame.")
                .defineInRange("maxLights", 8, 1, 8);
        LIGHT_RENDER_DISTANCE = builder.comment("Camera viewing distance for analytic lights, in chunks. Zero follows Minecraft render distance; does not change each source's reach or the world/GI caches.")
                .defineInRange("lightRenderDistanceChunks", 0, 0, 64);
        MAX_SHADOW_LIGHTS = builder.comment("Maximum lights receiving shadow calculations.")
                .defineInRange("maxShadowLights", 4, 0, 4);
        SHADOW_RESOLUTION = builder.comment("Shadow-map resolution per face, in pixels.")
                .defineInRange("shadowResolution", 256, 64, 2048);
        VOLUMETRIC_STEPS = builder.comment("Samples per volumetric ray.")
                .defineInRange("volumetricSteps", 12, 0, 24);
        RENDER_SCALE = builder.comment("Shared direct, indirect and volumetric lighting buffer scale relative to the window.")
                .defineInRange("renderScale", 0.5, 0.25, 1.0);
        MAX_RANGE = builder.comment("Maximum reach of each point, spot, or area light from its source, in blocks. This is NOT a camera-distance cutoff.",
                        "Camera visibility is controlled separately by lightRenderDistanceChunks; zero there follows Minecraft render distance.")
                .defineInRange("maxRange", 24.0, 1.0, 32.0);
        INTENSITY_MULTIPLIER = builder.comment("Multiplier applied to all light intensities.")
                .defineInRange("intensityMultiplier", 1.0, 0.0, 8.0);
        PERIODIC_CACHE_REFRESH = builder.comment("Rescan unchanged lighting and geometry periodically. Leave false for event-driven caching; enable as a fallback for mods that bypass block/chunk/light notifications.")
                .define("periodicCacheRefresh", false);
        MEDIUM_UPDATE_TICKS = builder.comment("Ticks between scene/medium safety rescans when periodicCacheRefresh is enabled.")
                .defineInRange("mediumUpdateTicks", 10, 1, 200);
        SCENE_BLOCK_BUDGET = builder.comment("Occupied block positions scanned per tick, rounded down to 512-block sectors.")
                .defineInRange("sceneBlockBudget", 8192, 512, 32768);
        MAX_MEDIA = builder.comment("Transparent-medium regions processed per pixel; zero disables colored transmission.")
                .defineInRange("maxMedia", 32, 0, 32);
        BLOOM_STRENGTH = builder.comment("Added-light bloom; zero skips neighboring bloom samples.")
                .defineInRange("bloomStrength", 0.08, 0, 2);
        SPEC = builder.build();
    }
    private ClientConfig() {}
}
