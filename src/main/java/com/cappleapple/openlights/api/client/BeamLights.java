package com.cappleapple.openlights.api.client;

import com.cappleapple.openlights.beam.BeamProfile;
import net.minecraft.world.phys.Vec3;

/** Converts a synchronized beam profile into one light and one shadow allocation. */
public final class BeamLights {
    private BeamLights() {}

    public static LightDefinition.Spot spot(Vec3 position, Vec3 forward, Vec3 up, BeamProfile profile) {
        return new LightDefinition.Spot(position,forward,up,new Vec3(1,1,1),1,
                Math.max(profile.inner().range(),profile.outer().range()),
                profile.outer().angleDegrees(),profile.inner().angleDegrees(),
                profile.shadows(),profile.fogDensity(),profile);
    }
}
