package com.cappleapple.openlights.network;

import com.cappleapple.openlights.beam.BeamProfiles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;


/** Bounded play-stage synchronization; no client classes are loaded on a dedicated server. */
public final class BeamProfileNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("openlights", "beam_profiles"), () -> VERSION, VERSION::equals, VERSION::equals);
    private static boolean registered;

    private BeamProfileNetwork() {}

    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(BeamProfiles.Snapshot.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BeamProfileCodec::encode)
                .decoder(BeamProfileCodec::decode)
                .consumerMainThread((snapshot, context) -> BeamProfiles.receive(snapshot))
                .add();
    }

    public static void send(ServerPlayer player, BeamProfiles.Snapshot snapshot) {
        CHANNEL.sendTo(snapshot, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

}
