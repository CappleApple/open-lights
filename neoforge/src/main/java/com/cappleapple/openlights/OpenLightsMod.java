package com.cappleapple.openlights;
import com.cappleapple.openlights.config.ClientConfig;
import com.cappleapple.openlights.config.ServerConfig;
import com.cappleapple.openlights.beam.BeamProfiles;
import com.cappleapple.openlights.content.ModContent;
import com.cappleapple.openlights.network.BeamProfileNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
@Mod(OpenLightsMod.MOD_ID)
public final class OpenLightsMod {
    public static final String MOD_ID = "openlights";
    public OpenLightsMod(IEventBus bus, ModContainer container) {
        ModContent.register(bus);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        bus.addListener(BeamProfileNetwork::register);
        BeamProfiles.register();
    }
}
