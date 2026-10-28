package com.cappleapple.openlights.flashlight;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(FlashlightMod.MOD_ID)
public final class FlashlightMod {
    public static final String MOD_ID = "openlights_flashlight";
    public FlashlightMod() {
        FlashlightContent.register(FMLJavaModLoadingContext.get().getModEventBus());
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, FlashlightConfig.SPEC);
        FlashlightNetwork.register();
    }
}
