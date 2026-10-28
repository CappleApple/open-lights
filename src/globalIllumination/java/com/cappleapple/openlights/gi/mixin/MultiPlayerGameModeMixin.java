package com.cappleapple.openlights.gi.mixin;

import com.cappleapple.openlights.gi.GiServerSupport;
import com.cappleapple.openlights.gi.content.LightSourceBlock;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevent saved creative hotbars from sending GI items to a server without GI content. */
@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
    @Inject(method="handleCreativeModeItemAdd", at=@At("HEAD"), cancellable=true)
    private void olgi$filterCreativeItem(ItemStack stack, int slot, CallbackInfo ci) {
        if (unsupported(stack)) ci.cancel();
    }
    @Inject(method="handleCreativeModeItemDrop", at=@At("HEAD"), cancellable=true)
    private void olgi$filterCreativeDrop(ItemStack stack, CallbackInfo ci) {
        if (unsupported(stack)) ci.cancel();
    }
    @org.spongepowered.asm.mixin.Unique
    private static boolean unsupported(ItemStack stack) {
        return GiServerSupport.isClientOnly() && stack.getItem() instanceof BlockItem block
                && block.getBlock() instanceof LightSourceBlock;
    }
}
