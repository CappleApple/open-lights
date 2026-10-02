package com.cappleapple.openlights.api.client;

import net.minecraft.world.phys.Vec3;
import com.cappleapple.openlights.beam.BeamProfile;

import java.util.Objects;

/** Immutable world-space light descriptions. Angles are full angles in degrees. */
public sealed interface LightDefinition permits LightDefinition.Point, LightDefinition.Spot, LightDefinition.Area {
    Vec3 position();
    Vec3 color();
    float intensity();
    float range();
    boolean shadows();
    float volumetricStrength();

    record Point(Vec3 position, Vec3 color, float intensity, float range,
                 boolean shadows, float volumetricStrength) implements LightDefinition {
        public Point {
            validateCommon(position, color, intensity, range, volumetricStrength);
        }
    }

    record Spot(Vec3 position, Vec3 forward, Vec3 up, Vec3 color, float intensity, float range,
                float outerConeAngleDegrees, float innerConeAngleDegrees,
                boolean shadows, float volumetricStrength, BeamProfile beamProfile) implements LightDefinition {
        public Spot(Vec3 position, Vec3 forward, Vec3 up, Vec3 color, float intensity, float range,
                    float outerConeAngleDegrees, float innerConeAngleDegrees, boolean shadows, float volumetricStrength) {
            this(position,forward,up,color,intensity,range,outerConeAngleDegrees,innerConeAngleDegrees,
                    shadows,volumetricStrength,null);
        }
        public Spot {
            validateCommon(position, color, intensity, range, volumetricStrength);
            angle(outerConeAngleDegrees, "outerConeAngleDegrees");
            nonnegative(innerConeAngleDegrees, "innerConeAngleDegrees");
            if (innerConeAngleDegrees > outerConeAngleDegrees) {
                throw new IllegalArgumentException("innerConeAngleDegrees exceeds outerConeAngleDegrees");
            }
            forward = direction(forward);
            up = orthogonalUp(forward, up);
        }
    }

    record Area(Vec3 position, Vec3 forward, Vec3 up, Vec3 color, float intensity, float range,
                float width, float height, float spreadAngleDegrees,
                boolean shadows, float volumetricStrength) implements LightDefinition {
        public Area {
            validateCommon(position, color, intensity, range, volumetricStrength);
            positive(width, "width");
            positive(height, "height");
            angle(spreadAngleDegrees, "spreadAngleDegrees");
            forward = direction(forward);
            up = orthogonalUp(forward, up);
        }
    }

    private static void validateCommon(Vec3 position, Vec3 color, float intensity,
                                       float range, float volumetricStrength) {
        finiteVector(position, "position");
        finiteVector(color, "color");
        if (color.x < 0 || color.y < 0 || color.z < 0) {
            throw new IllegalArgumentException("color components must be nonnegative");
        }
        nonnegative(intensity, "intensity");
        positive(range, "range");
        nonnegative(volumetricStrength, "volumetricStrength");
    }

    private static Vec3 direction(Vec3 value) {
        finiteVector(value, "forward");
        if (!Double.isFinite(value.lengthSqr()) || value.lengthSqr() < 1.0e-12) {
            throw new IllegalArgumentException("forward must have a finite nonzero length");
        }
        return value.scale(1.0 / Math.sqrt(value.lengthSqr()));
    }

    private static Vec3 orthogonalUp(Vec3 forward, Vec3 preferredUp) {
        finiteVector(preferredUp, "up");
        Vec3 up = preferredUp.subtract(forward.scale(preferredUp.dot(forward)));
        if (!Double.isFinite(up.lengthSqr())) {
            throw new IllegalArgumentException("up magnitude is too large");
        }
        if (up.lengthSqr() < 1.0e-12) {
            up = Math.abs(forward.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            up = up.subtract(forward.scale(up.dot(forward)));
        }
        return up.scale(1.0 / Math.sqrt(up.lengthSqr()));
    }

    private static void finiteVector(Vec3 value, String name) {
        Objects.requireNonNull(value, name);
        if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
            throw new IllegalArgumentException(name + " must contain finite coordinates");
        }
    }

    private static void nonnegative(float value, String name) {
        if (!Float.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be finite and nonnegative");
        }
    }

    private static void positive(float value, String name) {
        if (!Float.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void angle(float value, String name) {
        if (!Float.isFinite(value) || value <= 0 || value >= 180) {
            throw new IllegalArgumentException(name + " must be between 0 and 180 degrees, exclusive");
        }
    }
}
