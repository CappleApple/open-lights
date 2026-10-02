package com.cappleapple.openlights.client.particle;

import com.cappleapple.openlights.api.client.LightDefinition;
import com.cappleapple.openlights.beam.BeamProfile;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BeamDustSamplingTest {
    private static final BeamProfile.Layer INNER = new BeamProfile.Layer(new Vec3(1, 0, 0), 1, 30, 8, .2f, 2);
    private static final BeamProfile.Layer OUTER = new BeamProfile.Layer(new Vec3(0, 0, 1), 1, 60, 16, .2f, 2);
    private static final BeamProfile PROFILE = new BeamProfile(INNER, OUTER, .3f, BeamProfile.DEFAULT.dust(), true);

    @Test void centerCombinesIndependentlyColoredLayers() {
        Vec3 color = BeamDustSampling.color(beam(PROFILE, 16), new Vec3(0, 0, -4));
        assertEquals(.25 / 1.96, color.x, 1.e-7);
        assertEquals(0, color.y, 1.e-7);
        assertEquals(.5625 / 1.96, color.z, 1.e-7);
    }

    @Test void spillCanIlluminateBeyondInnerConeAndInnerRange() {
        double angle = Math.toRadians(22);
        Vec3 outsideInnerCone = new Vec3(Math.sin(angle) * 4, 0, -Math.cos(angle) * 4);
        Vec3 spill = BeamDustSampling.color(beam(PROFILE, 16), outsideInnerCone);
        assertEquals(0, spill.x, 1.e-7);
        assertTrue(spill.z > 0);
        Vec3 beyondInnerRange = BeamDustSampling.color(beam(PROFILE, 16), new Vec3(0, 0, -10));
        assertEquals(0, beyondInnerRange.x, 1.e-7);
        assertTrue(beyondInnerRange.z > 0);
    }

    @Test void outOfConeAndClampedRangeMotesAreUnlit() {
        assertEquals(Vec3.ZERO, BeamDustSampling.color(beam(PROFILE, 16), new Vec3(4, 0, 0)));
        assertEquals(Vec3.ZERO, BeamDustSampling.color(beam(PROFILE, 16), new Vec3(0, 0, 4)));
        assertEquals(Vec3.ZERO, BeamDustSampling.color(beam(PROFILE, 6), new Vec3(0, 0, -8)));
    }

    @Test void fogAmountDoesNotChangeDustIllumination() {
        BeamProfile clear = new BeamProfile(INNER, OUTER, 0, PROFILE.dust(), true);
        BeamProfile fog = new BeamProfile(INNER, OUTER, 8, PROFILE.dust(), true);
        assertEquals(BeamDustSampling.color(beam(clear, 16), new Vec3(0, 0, -4)),
                BeamDustSampling.color(beam(fog, 16), new Vec3(0, 0, -4)));
    }

    @Test void hardEdgeRemainsFiniteAndFullyLitInsideCone() {
        BeamProfile.Layer hard = new BeamProfile.Layer(new Vec3(1, 1, 1), 1, 30, 8, 0, 2);
        assertEquals(.25 / 1.96, BeamDustSampling.strength(hard, 4, 1), 1.e-7);
        assertEquals(0, BeamDustSampling.strength(hard, 4, Math.cos(Math.toRadians(16))), 1.e-7);
    }

    @Test void rangeLimitChangesAttenuationBeforeItsBoundary() {
        Vec3 clamped = BeamDustSampling.color(beam(PROFILE, 4), new Vec3(0, 0, -2));
        assertEquals(.25 / 1.24, clamped.x, 1.e-7);
        assertEquals(.25 / 1.24, clamped.z, 1.e-7);
    }

    @Test void blockTintAndIntensityMultiplyBothLayers() {
        var beam = new LightDefinition.Spot(Vec3.ZERO, new Vec3(0, 0, -1), new Vec3(0, 1, 0),
                new Vec3(.25, .5, 1), 2, 16, 60, 30, true, .3f, PROFILE);
        Vec3 color = BeamDustSampling.color(beam, new Vec3(0, 0, -4));
        assertEquals(.25 / 1.96 * .5, color.x, 1.e-7);
        assertEquals(.5625 / 1.96 * 2, color.z, 1.e-7);
        var off = new LightDefinition.Spot(beam.position(), beam.forward(), beam.up(), beam.color(),
                0, 16, 60, 30, true, .3f, PROFILE);
        assertEquals(Vec3.ZERO, BeamDustSampling.color(off, new Vec3(0, 0, -4)));
        assertEquals(Vec3.ZERO, BeamDustSampling.color(beam, new Vec3(0, 0, -4), 0));
        Vec3 doubled = BeamDustSampling.color(beam, new Vec3(0, 0, -4), 2);
        assertEquals(color.scale(2), doubled);
    }
    private static LightDefinition.Spot beam(BeamProfile profile, float range) {
        return new LightDefinition.Spot(Vec3.ZERO, new Vec3(0, 0, -1), new Vec3(0, 1, 0),
                new Vec3(1, 1, 1), 1, range, 60, 30, true, profile.fogDensity(), profile);
    }
}
