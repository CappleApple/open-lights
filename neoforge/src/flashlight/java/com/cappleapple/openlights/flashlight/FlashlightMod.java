package com.cappleapple.openlights.flashlight;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.bus.api.IEventBus;

@Mod(FlashlightMod.MOD_ID)
public final class FlashlightMod {
    public static final String MOD_ID = "openlights_flashlight";
    public FlashlightMod(IEventBus bus, ModContainer container) {
        FlashlightContent.register(bus);
        container.registerConfig(ModConfig.Type.SERVER, FlashlightConfig.SPEC);
        bus.addListener(FlashlightNetwork::register);
    }
}
