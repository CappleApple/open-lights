package com.cappleapple.openlights.beam.client;

import com.cappleapple.openlights.OpenLightsMod;
import com.cappleapple.openlights.beam.BeamProfiles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = OpenLightsMod.MOD_ID, value = Dist.CLIENT)
public final class BeamProfileClientEvents {
    private BeamProfileClientEvents() {}

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        BeamProfiles.resetClient();
    }
}
