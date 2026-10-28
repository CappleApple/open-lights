package com.cappleapple.openlights.gi;

import com.cappleapple.openlights.gi.config.GlobalIlluminationConfig;
import com.cappleapple.openlights.gi.content.ModContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

@Mod(GlobalIlluminationMod.MOD_ID)
public final class GlobalIlluminationMod {
    public static final String MOD_ID = "ol_global_illumination";
    public GlobalIlluminationMod(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT,GlobalIlluminationConfig.SPEC);
        ModContent.register(bus);
        GlobalIlluminationContentNetwork.register(bus);
    }
    @EventBusSubscriber(modid=MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Client {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> com.cappleapple.openlights.client.render.OpenLightRenderer.installWorldLighting(
                    new com.cappleapple.openlights.gi.client.render.GlobalIlluminationLighting()));
        }
    }
}
