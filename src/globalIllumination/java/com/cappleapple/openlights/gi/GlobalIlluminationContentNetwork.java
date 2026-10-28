package com.cappleapple.openlights.gi;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Optional server-content handshake, independent of the rendering library's profile channel. */
public final class GlobalIlluminationContentNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("ol_global_illumination", "content"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));
    private GlobalIlluminationContentNetwork() {}
    public static void register(IEventBus bus) { CHANNEL.getClass(); }
    public static boolean supports(net.minecraft.network.Connection connection) { return CHANNEL.isRemotePresent(connection); }
}
