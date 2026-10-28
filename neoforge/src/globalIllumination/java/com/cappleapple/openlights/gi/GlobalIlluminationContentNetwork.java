package com.cappleapple.openlights.gi;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Optional server-content handshake, independent of the rendering library's profile channel. */
public final class GlobalIlluminationContentNetwork {
    private GlobalIlluminationContentNetwork() {}
    public record Presence() implements CustomPacketPayload {
        public static final Type<Presence> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ol_global_illumination", "content"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Presence> CODEC = StreamCodec.of((buffer, value) -> {}, buffer -> new Presence());
        @Override public Type<Presence> type() { return TYPE; }
    }
    public static void register(IEventBus bus) { bus.addListener(GlobalIlluminationContentNetwork::registerPayload); }
    private static void registerPayload(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(Presence.TYPE, Presence.CODEC, (payload, context) -> {});
    }
}
