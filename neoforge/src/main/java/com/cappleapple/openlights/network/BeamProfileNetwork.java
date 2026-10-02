package com.cappleapple.openlights.network;
import com.cappleapple.openlights.beam.BeamProfiles;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Bounded play-stage synchronization, handled on the client main thread. */
public final class BeamProfileNetwork {
    private BeamProfileNetwork() {}
    public record ProfilesPayload(BeamProfiles.Snapshot snapshot) implements CustomPacketPayload {
        public static final Type<ProfilesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("openlights", "beam_profiles"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ProfilesPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> BeamProfileCodec.encode(payload.snapshot(), buffer),
                buffer -> new ProfilesPayload(BeamProfileCodec.decode(buffer)));
        @Override public Type<ProfilesPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(ProfilesPayload.TYPE, ProfilesPayload.CODEC,
                (payload, context) -> BeamProfiles.receive(payload.snapshot()));
    }
    public static void send(ServerPlayer player, BeamProfiles.Snapshot snapshot) {
        if (player.connection.hasChannel(ProfilesPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new ProfilesPayload(snapshot));
        }
    }
}
