package com.cappleapple.openlights.flashlight;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Required content handshake; a library-only server cannot decode flashlight items. */
public final class FlashlightNetwork {
    private FlashlightNetwork() {}
    public record Presence() implements CustomPacketPayload {
        public static final Type<Presence> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FlashlightMod.MOD_ID, "presence"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Presence> CODEC = StreamCodec.of((buffer, value) -> {}, buffer -> new Presence());
        @Override public Type<Presence> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Presence.TYPE, Presence.CODEC, (payload, context) -> {});
    }
}
