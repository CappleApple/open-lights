package com.cappleapple.openlights.client.scene;

import com.cappleapple.openlights.api.client.LightDefinition;
import com.cappleapple.openlights.beam.BeamProfile;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndirectLightCacheTest {
    private static final Vec3 WHITE = new Vec3(1,1,1), FORWARD = new Vec3(0,0,1), UP = new Vec3(0,1,0);

    @Test void pointBounceHasFiniteSupportAndPreservesColor() {
        var light = new LightDefinition.Point(Vec3.ZERO, new Vec3(1,0,0), 2, 8, true, 0);
        Vec3 near = IndirectLightCache.radiance(light, FORWARD);
        assertTrue(near.x > 0); assertEquals(0, near.y); assertEquals(0, near.z);
        assertTrue(near.x > IndirectLightCache.radiance(light, FORWARD.scale(4)).x);
        assertEquals(Vec3.ZERO, IndirectLightCache.radiance(light, FORWARD.scale(8)));
    }

    @Test void spotAndAreaDoNotBounceBehindEmitter() {
        var spot = new LightDefinition.Spot(Vec3.ZERO, FORWARD, UP, WHITE, 1, 8, 60, 30, true, 0);
        var area = new LightDefinition.Area(Vec3.ZERO, FORWARD, UP, WHITE, 1, 8, 2, 2, 90, true, 0);
        for (var light : new LightDefinition[]{spot, area}) {
            assertTrue(IndirectLightCache.radiance(light, FORWARD).lengthSqr() > 0);
            assertEquals(0, IndirectLightCache.radiance(light, FORWARD.scale(-1)).lengthSqr());
        }
    }

    @Test void beamLayersRespectIndependentRanges() {
        var inner = new BeamProfile.Layer(new Vec3(1,0,0), 1, 30, 2, 0, 2);
        var outer = new BeamProfile.Layer(new Vec3(0,0,1), 1, 60, 8, .2f, 2);
        var profile = new BeamProfile(inner, outer, 0, new BeamProfile.Dust(0,.02f,20,0), true);
        var spot = new LightDefinition.Spot(Vec3.ZERO, FORWARD, UP, WHITE, 1, 8, 60, 30, true, 0, profile);
        Vec3 near = IndirectLightCache.radiance(spot, FORWARD);
        Vec3 far = IndirectLightCache.radiance(spot, FORWARD.scale(3));
        assertTrue(near.x > 0 && near.z > 0);
        assertEquals(0, far.x); assertTrue(far.z > 0);
    }
}
