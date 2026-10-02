package com.cappleapple.openlights.beam;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/** Immutable, server-authoritative settings for the two additive layers of a flashlight beam. */
public record BeamProfile(Layer inner, Layer outer, float fogDensity, Dust dust, boolean shadows) {
    public static final BeamProfile DEFAULT = new BeamProfile(
            new Layer(color("#FFF5E0"), 1.5f, 30, 24, 0.2f, 2),
            new Layer(color("#FFF5E0"), 0.7f, 46, 24, 0.2f, 2),
            0.3f, new Dust(6, 0.025f, 30, 0.02f), true);

    public BeamProfile {
        Objects.requireNonNull(inner, "inner");
        Objects.requireNonNull(outer, "outer");
        Objects.requireNonNull(dust, "dust");
        bounded("fogDensity", fogDensity, 0, 8);
        if (inner.angleDegrees() > outer.angleDegrees()) {
            throw new IllegalArgumentException("inner.angleDegrees must not exceed outer.angleDegrees");
        }
    }

    public record Layer(Vec3 color, float intensity, float angleDegrees, float range,
                        float edgeSoftness, float falloff) {
        public Layer {
            Objects.requireNonNull(color, "color");
            bounded("color.r", color.x, 0, 1);
            bounded("color.g", color.y, 0, 1);
            bounded("color.b", color.z, 0, 1);
            bounded("intensity", intensity, 0, 16);
            positive("angleDegrees", angleDegrees, 175);
            positive("range", range, 32);
            bounded("edgeSoftness", edgeSoftness, 0, 1);
            bounded("falloff", falloff, 0.1f, 8);
        }
    }

    public record Dust(float rate, float size, int lifetimeTicks, float speed) {
        public Dust {
            bounded("dust.rate", rate, 0, 80);
            bounded("dust.size", size, 0.01f, 0.2f);
            bounded("dust.lifetimeTicks", lifetimeTicks, 1, 200);
            bounded("dust.speed", speed, 0, 1);
        }
    }

    public static Vec3 color(String hex) {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("color must use #RRGGBB");
        }
        int rgb = Integer.parseInt(hex.substring(1), 16);
        return new Vec3(((rgb >> 16) & 255) / 255.0, ((rgb >> 8) & 255) / 255.0, (rgb & 255) / 255.0);
    }

    private static void bounded(String field, double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(field + " must be in [" + minimum + ", " + maximum + "]");
        }
    }

    private static void positive(String field, float value, float maximum) {
        if (!Float.isFinite(value) || value <= 0 || value > maximum) {
            throw new IllegalArgumentException(field + " must be greater than 0 and at most " + maximum);
        }
    }
}
