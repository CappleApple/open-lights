package com.cappleapple.openlights.config;

import com.cappleapple.openlights.beam.BeamProfile;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;

public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final LayerValues INNER;
    public static final LayerValues OUTER;
    public static final ForgeConfigSpec.DoubleValue FOG_DENSITY;
    public static final ForgeConfigSpec.BooleanValue SHADOWS;
    public static final ForgeConfigSpec.DoubleValue DUST_RATE;
    public static final ForgeConfigSpec.DoubleValue DUST_SIZE;
    public static final ForgeConfigSpec.IntValue DUST_LIFETIME_TICKS;
    public static final ForgeConfigSpec.DoubleValue DUST_SPEED;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Default beam appearance. Datapack fields override these values. Run /reload after editing.")
                .push("defaults");
        INNER = new LayerValues(builder, "inner", BeamProfile.DEFAULT.inner());
        OUTER = new LayerValues(builder, "outer", BeamProfile.DEFAULT.outer());
        FOG_DENSITY = builder.comment("Strength of visible scattering inside the beam; 0 disables it.")
                .defineInRange("fogDensity", 0.3, 0, 8);
        SHADOWS = builder.define("shadows", true);
        builder.push("dust");
        DUST_RATE = builder.comment("Particles per second per beam; 0 disables dust.")
                .defineInRange("rate", 6.0, 0, 80);
        DUST_SIZE = builder.defineInRange("size", 0.025, 0.01, 0.2);
        DUST_LIFETIME_TICKS = builder.defineInRange("lifetimeTicks", 30, 1, 200);
        DUST_SPEED = builder.defineInRange("speed", 0.02, 0, 1);
        builder.pop(2);
        SPEC = builder.build();
    }

    private ServerConfig() {}

    public static BeamProfile defaults() {
        if (!SPEC.isLoaded()) return BeamProfile.DEFAULT;
        BeamProfile.Layer outer = OUTER.get();
        BeamProfile.Layer inner = INNER.get();
        if (inner.angleDegrees() > outer.angleDegrees()) {
            LogUtils.getLogger().warn("Open Lights defaults.inner.angleDegrees exceeds outer.angleDegrees; clamping inner angle to {}", outer.angleDegrees());
            inner = new BeamProfile.Layer(inner.color(), inner.intensity(), outer.angleDegrees(),
                    inner.range(), inner.edgeSoftness(), inner.falloff());
        }
        return new BeamProfile(inner, outer, FOG_DENSITY.get().floatValue(),
                new BeamProfile.Dust(DUST_RATE.get().floatValue(), DUST_SIZE.get().floatValue(),
                        DUST_LIFETIME_TICKS.get(), DUST_SPEED.get().floatValue()), SHADOWS.get());
    }

    public static final class LayerValues {
        public final ForgeConfigSpec.ConfigValue<String> COLOR;
        public final ForgeConfigSpec.DoubleValue INTENSITY;
        public final ForgeConfigSpec.DoubleValue ANGLE_DEGREES;
        public final ForgeConfigSpec.DoubleValue RANGE;
        public final ForgeConfigSpec.DoubleValue EDGE_SOFTNESS;
        public final ForgeConfigSpec.DoubleValue FALLOFF;

        private LayerValues(ForgeConfigSpec.Builder builder, String name, BeamProfile.Layer fallback) {
            builder.push(name);
            COLOR = builder.comment("Beam color in #RRGGBB format.")
                    .define("color", "#FFF5E0", value -> value instanceof String hex && hex.matches("#[0-9a-fA-F]{6}"));
            INTENSITY = builder.comment("Layers add together; center brightness is inner + outer.")
                    .defineInRange("intensity", decimal(fallback.intensity()), 0, 16);
            ANGLE_DEGREES = builder.comment("Full cone angle in degrees; inner must not exceed outer.")
                    .defineInRange("angleDegrees", decimal(fallback.angleDegrees()), 0.1, 175);
            RANGE = builder.comment("Beam reach in blocks.")
                    .defineInRange("range", decimal(fallback.range()), 0.1, 32);
            EDGE_SOFTNESS = builder.comment("Fraction of the cone edge that fades from full brightness to zero.")
                    .defineInRange("edgeSoftness", decimal(fallback.edgeSoftness()), 0, 1);
            FALLOFF = builder.comment("Distance attenuation exponent; larger values fall off faster.")
                    .defineInRange("falloff", decimal(fallback.falloff()), 0.1, 8);
            builder.pop();
        }

        private static double decimal(float value) {
            return Double.parseDouble(Float.toString(value));
        }

        private BeamProfile.Layer get() {
            return new BeamProfile.Layer(BeamProfile.color(COLOR.get()), INTENSITY.get().floatValue(),
                    ANGLE_DEGREES.get().floatValue(), RANGE.get().floatValue(),
                    EDGE_SOFTNESS.get().floatValue(), FALLOFF.get().floatValue());
        }
    }
}
