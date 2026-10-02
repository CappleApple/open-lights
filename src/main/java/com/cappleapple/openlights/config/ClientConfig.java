package com.cappleapple.openlights.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue BEAM_DUST;
    public static final ForgeConfigSpec.IntValue MAX_LIGHTS;
    public static final ForgeConfigSpec.IntValue LIGHT_RENDER_DISTANCE;
    public static final ForgeConfigSpec.IntValue MAX_SHADOW_LIGHTS;
    public static final ForgeConfigSpec.IntValue SHADOW_RESOLUTION;
    public static final ForgeConfigSpec.IntValue VOLUMETRIC_STEPS;
    public static final ForgeConfigSpec.DoubleValue RENDER_SCALE;
    public static final ForgeConfigSpec.DoubleValue MAX_RANGE;
    public static final ForgeConfigSpec.DoubleValue INTENSITY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue BLOCK_LIGHT_EXPOSURE;
    public static final ForgeConfigSpec.IntValue SOURCE_CACHE_MIB;
    public static final ForgeConfigSpec.IntValue MEDIUM_UPDATE_TICKS;
    public static final ForgeConfigSpec.BooleanValue PERIODIC_CACHE_REFRESH;
    public static final ForgeConfigSpec.IntValue COLOR_SAMPLES_PER_TICK;
    public enum LightingMode { ADDITIVE, CACHED }
    public enum BlockLightStyle { OPEN_LIGHTS, MINECRAFT }
    public static final ForgeConfigSpec.EnumValue<BlockLightStyle> BLOCK_LIGHT_STYLE;
    public static final ForgeConfigSpec.EnumValue<LightingMode> LIGHTING_MODE;
    public static final ForgeConfigSpec.BooleanValue GI_ENABLED;
    public static final ForgeConfigSpec.BooleanValue GI_BLOCK_LIGHT;
    public static final ForgeConfigSpec.IntValue GI_SPACING, GI_PROBES_PER_TICK, GI_REFRESH_TICKS;
    public static final ForgeConfigSpec.IntValue GI_SOURCE_LIMIT, WORLD_GRID_SIZE, WORLD_SAMPLES_PER_TICK;
    public static final ForgeConfigSpec.IntValue SCENE_BLOCK_BUDGET, MAX_MEDIA;
    public static final ForgeConfigSpec.DoubleValue GI_STRENGTH, GI_TRACE_DISTANCE, GI_BUDGET_MILLIS, BLOOM_STRENGTH;
    public static final ForgeConfigSpec.BooleanValue AGGREGATE_ENABLED;
    public static final ForgeConfigSpec.DoubleValue AGGREGATE_MULTIPLIER, AGGREGATE_STRENGTH, AGGREGATE_BUDGET_MILLIS;
    public static final ForgeConfigSpec.IntValue AGGREGATE_CELLS_PER_TICK, AGGREGATE_MAX_CELLS;
    public static final ForgeConfigSpec.IntValue AGGREGATE_SNAPSHOT_SECTIONS, AGGREGATE_APPLY_SECTIONS, LIGHT_UPLOAD_SECTIONS;
    public static final ForgeConfigSpec.DoubleValue LIGHT_UPLOAD_MILLIS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
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
        BLOCK_LIGHT_EXPOSURE=builder.comment("Brightness of OPEN_LIGHTS-style cached block illumination. Applied before texture reflectance; does not change source reach or gameplay light.").defineInRange("blockLightExposure",2.0,.25,4.0);
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
        LIGHTING_MODE = builder.comment("CACHED replaces the world lightmap with a cached block/sky field across render distance.",
                "Experimental: coarse probes can blur lighting across walls. Shader packs use ADDITIVE automatically.",
                "Minecraft still computes gameplay light levels. ADDITIVE preserves vanilla surface shading.")
                .defineEnum("lightingMode", LightingMode.CACHED);
        BLOCK_LIGHT_STYLE = builder.comment("OPEN_LIGHTS uses cached radial falloff, directional shading and direct occlusion. MINECRAFT keeps propagated block-light shading.")
                .defineEnum("blockLightStyle",BlockLightStyle.OPEN_LIGHTS);
        WORLD_GRID_SIZE = builder.comment("Samples per axis for replacement lighting. Higher values reduce spatial blur but increase warmup, memory and upload cost.")
                .defineInRange("worldGridSize", 33, 9, 65);
        WORLD_SAMPLES_PER_TICK = builder.comment("Maximum cached block/sky samples refreshed per tick. Half updates full-resolution block-light sections; the rest updates near/far grids. No chunk loading.")
                .defineInRange("worldSamplesPerTick", 4096, 128, 32768);
        COLOR_SAMPLES_PER_TICK = builder.comment("Maximum new color-propagation cells resolved per tick, shared by world lighting and GI. Clean cells are cached.")
                .defineInRange("colorSamplesPerTick", 4096, 128, 32768);
        LIGHT_UPLOAD_SECTIONS = builder.comment("Maximum cached light sections uploaded per frame, shared by active and growing atlases.").defineInRange("lightUploadsPerFrame",8,1,128);
        LIGHT_UPLOAD_MILLIS = builder.comment("Soft client-thread texture-upload time budget per frame; one upload can exceed it.").defineInRange("lightUploadBudgetMillis",0.5,0.1,8.0);
        builder.push("aggregateLight");
        AGGREGATE_ENABLED = builder.comment("Nearby emissive blocks extend cached lighting reach with diminishing returns. Visual only; CACHED mode required.").define("enabled", true);
        AGGREGATE_MULTIPLIER = builder.comment("Maximum reach multiplier. Emitters within two blocks pool power using a cube root; source brightness is unchanged.").defineInRange("maximumMultiplier", 3.0, 1.0, 4.0);
        AGGREGATE_STRENGTH = builder.comment("Range-growth strength; zero preserves ordinary block lighting.").defineInRange("strength", 1.0, 0.0, 2.0);
        AGGREGATE_CELLS_PER_TICK = builder.comment("Maximum material cells captured on the client thread per tick. Propagation and texture construction run on a background worker.").defineInRange("cellsPerTick", 8192, 128, 65536);
        AGGREGATE_BUDGET_MILLIS = builder.comment("Soft client-thread snapshot capture budget. Does not throttle the background worker.").defineInRange("budgetMillis", 1.0, 0.1, 8.0);
        AGGREGATE_MAX_CELLS = builder.comment("Maximum stored radial-light cells in OPEN_LIGHTS style, or propagated cells across falloff tiers in MINECRAFT style. Reaching this cap truncates worker lighting.").defineInRange("maximumCells", 1048576, 65536, 4194304);
        AGGREGATE_SNAPSHOT_SECTIONS = builder.comment("Maximum reusable material snapshots and newly requested sections per worker job. Reaching the limit truncates extension.").defineInRange("snapshotSections",2048,64,8192);
        AGGREGATE_APPLY_SECTIONS = builder.comment("Maximum completed worker texture sections adopted per client tick; uploads have a separate per-frame budget.").defineInRange("applySectionsPerTick",16,1,256);
        SOURCE_CACHE_MIB=builder.comment("MiB budget for sparse per-source/cluster direct-light channels. Reuses unchanged sources and recomposes affected sections; zero uses full-field rebuilding. Overflow falls back to rebuilding without dropping light. Working generations and final fields use additional memory.").defineInRange("sourceCacheMiB",128,0,512);
        builder.pop();
        builder.push("globalIllumination");
        GI_ENABLED = builder.comment("Optional cached indirect bounce lighting; disabled by default.").define("enabled", false);
        GI_BLOCK_LIGHT = builder.comment("Include bounced vanilla/modded block light using Minecraft's propagated block-light field.")
                .define("blockLight", true);
        GI_SPACING = builder.comment("Probe spacing in blocks in the nearby 32-block-wide GI volume.")
                .defineInRange("probeSpacing", 8, 2, 8);
        GI_PROBES_PER_TICK = builder.comment("Maximum probes traced each tick; each probe uses six surface rays and at most six rays per source.")
                .defineInRange("probesPerTick", 4, 1, 64);
        GI_REFRESH_TICKS = builder.comment("Minimum age before a dirty probe is retraced. Clean probes are retained unless periodicCacheRefresh is enabled. The work budget may delay refreshes further.")
                .defineInRange("refreshTicks", 20, 1, 200);
        GI_SOURCE_LIMIT = builder.comment("Nearest API lights considered for bounced light, including lights outside the view frustum.")
                .defineInRange("sourceLimit", 8, 1, 32);
        GI_STRENGTH = builder.defineInRange("strength", 0.35, 0, 2);
        GI_TRACE_DISTANCE = builder.comment("Maximum distance from a probe to a bounce surface, in blocks.")
                .defineInRange("traceDistance", 8.0, 2, 16);
        GI_BUDGET_MILLIS = builder.comment("Soft CPU time limit per tick; checked between probes, so one probe can exceed it.")
                .defineInRange("budgetMillis", 0.5, 0.1, 8);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {}
}
