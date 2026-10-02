package com.cappleapple.openlights.beam.client;

import com.cappleapple.openlights.OpenLightsMod;
import com.cappleapple.openlights.beam.BeamProfiles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = OpenLightsMod.MOD_ID, value = Dist.CLIENT)
public final class BeamProfileClientEvents {
    private BeamProfileClientEvents() {}

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        BeamProfiles.resetClient();
    }
}
