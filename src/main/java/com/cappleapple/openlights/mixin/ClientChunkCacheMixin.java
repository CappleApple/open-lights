package com.cappleapple.openlights.mixin;

import com.cappleapple.openlights.client.render.OpenLightRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(ClientChunkCache.class)
abstract class ClientChunkCacheMixin {
    @Shadow @Final private ClientLevel level;

    @Inject(method = "onLightUpdate", at = @At("RETURN"))
    private void openlights$lightChanged(LightLayer layer, SectionPos section, CallbackInfo ci) {
        var world = level;
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().level == world) OpenLightRenderer.invalidateLightSection(layer, section);
        });
    }

    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void openlights$chunkLoaded(int x, int z, FriendlyByteBuf buffer, CompoundTag tag,
            Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer,
            CallbackInfoReturnable<LevelChunk> ci) {
        if (ci.getReturnValue() != null) openlights$chunkChanged(x, z);
    }

    @Inject(method = "drop", at = @At("RETURN"))
    private void openlights$chunkUnloaded(int x, int z, CallbackInfo ci) { openlights$chunkChanged(x, z); }

    @Inject(method = "replaceBiomes", at = @At("RETURN"))
    private void openlights$biomesChanged(int x, int z, FriendlyByteBuf buffer, CallbackInfo ci) { openlights$chunkChanged(x, z); }

    @Inject(method = "updateViewRadius", at = @At("RETURN"))
    private void openlights$viewRadiusChanged(int radius, CallbackInfo ci) {
        var world = level;
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().level == world) OpenLightRenderer.invalidate(-30_000_000,
                    world.getMinBuildHeight(), -30_000_000, 30_000_000, world.getMaxBuildHeight(), 30_000_000);
        });
    }

    private void openlights$chunkChanged(int x, int z) {
        var world = level;
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().level == world) OpenLightRenderer.invalidate(x * 16,
                    world.getMinBuildHeight(), z * 16, x * 16 + 15, world.getMaxBuildHeight() - 1, z * 16 + 15);
        });
    }
}
