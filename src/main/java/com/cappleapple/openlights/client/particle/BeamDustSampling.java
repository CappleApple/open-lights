package com.cappleapple.openlights.client.particle;

import com.cappleapple.openlights.api.client.LightDefinition;
import com.cappleapple.openlights.beam.BeamProfile;
import net.minecraft.world.phys.Vec3;

/** Matches the renderer's two independently colored angular/radial light layers. */
final class BeamDustSampling {
    private BeamDustSampling() {}

    static Vec3 color(LightDefinition.Spot beam, Vec3 position) {
        return color(beam, position, 1);
    }

    static Vec3 color(LightDefinition.Spot beam, Vec3 position, double intensityMultiplier) {
        BeamProfile profile = beam.beamProfile();
        if (profile == null || beam.intensity() <= 0 || intensityMultiplier <= 0) return Vec3.ZERO;
        Vec3 delta = position.subtract(beam.position());
        double distance = delta.length();
        if (distance < .0001 || distance > beam.range()) return Vec3.ZERO;
        double cosine = delta.dot(beam.forward()) / distance;
        double inner = strength(profile.inner(), distance, cosine, beam.range());
        double outer = strength(profile.outer(), distance, cosine, beam.range());
        return profile.inner().color().scale(inner).add(profile.outer().color().scale(outer))
                .multiply(beam.color()).scale(Math.min(64, beam.intensity() * intensityMultiplier));
    }

    static double strength(BeamProfile.Layer layer, double distance, double cosine) {
        return strength(layer, distance, cosine, layer.range());
    }

    static double strength(BeamProfile.Layer layer, double distance, double cosine, float rangeBound) {
        double range = Math.min(layer.range(), rangeBound);
        if (distance >= range || layer.intensity() <= 0) return 0;
        double edge = Math.cos(Math.toRadians(layer.angleDegrees() * .5));
        if (cosine < edge) return 0;
        double full = Math.cos(Math.toRadians(layer.angleDegrees() * .5 * (1 - layer.edgeSoftness())));
        double angular = full <= edge + .000001 ? 1 : Math.min(1, Math.max(0, (cosine - edge) / (full - edge)));
        angular = angular * angular * (3 - 2 * angular);
        double radial = Math.pow(Math.max(0, 1 - distance / range), layer.falloff());
        return layer.intensity() * angular * radial / (1 + distance * distance * .06);
    }
}
