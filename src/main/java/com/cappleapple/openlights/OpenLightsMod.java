package com.cappleapple.openlights;

import com.cappleapple.openlights.config.ClientConfig;
import com.cappleapple.openlights.config.ServerConfig;
import com.cappleapple.openlights.beam.BeamProfiles;
import com.cappleapple.openlights.content.ModContent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(OpenLightsMod.MOD_ID)
public final class OpenLightsMod {
    public static final String MOD_ID = "openlights";

    public OpenLightsMod() {
        ModContent.register(FMLJavaModLoadingContext.get().getModEventBus());
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        BeamProfiles.register();
    }
}
