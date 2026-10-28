package com.cappleapple.openlights.flashlight;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Required content handshake; a library-only server cannot decode flashlight items. */
public final class FlashlightNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FlashlightMod.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);
    private FlashlightNetwork() {}
    public static void register() { CHANNEL.getClass(); }
}
