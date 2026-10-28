package com.cappleapple.openlights.gi;

import com.cappleapple.openlights.gi.config.GlobalIlluminationConfig;
import com.cappleapple.openlights.gi.content.ModContent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.Dist;

@Mod(GlobalIlluminationMod.MOD_ID)
public final class GlobalIlluminationMod {
    public static final String MOD_ID = "ol_global_illumination";
    public GlobalIlluminationMod() {
        IEventBus bus=FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT,GlobalIlluminationConfig.SPEC);
        ModContent.register(bus);
        GlobalIlluminationContentNetwork.register(bus);
    }
    @Mod.EventBusSubscriber(modid=MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Client {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> com.cappleapple.openlights.client.render.OpenLightRenderer.installWorldLighting(
                    new com.cappleapple.openlights.gi.client.render.GlobalIlluminationLighting()));
        }
    }
}
