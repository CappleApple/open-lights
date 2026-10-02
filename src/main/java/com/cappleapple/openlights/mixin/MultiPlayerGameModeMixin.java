package com.cappleapple.openlights.mixin;

import com.cappleapple.openlights.client.ServerLightingSupport;
import com.cappleapple.openlights.content.FlashlightItem;
import com.cappleapple.openlights.content.LightSourceBlock;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Saved creative hotbars can retain items that the current server cannot decode. */
@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
    @Inject(method="handleCreativeModeItemAdd", at=@At("HEAD"), cancellable=true)
    private void openlights$filterCreativeItem(ItemStack stack, int slot, CallbackInfo ci) {
        if (unsupported(stack)) ci.cancel();
    }

    @Inject(method="handleCreativeModeItemDrop", at=@At("HEAD"), cancellable=true)
    private void openlights$filterCreativeDrop(ItemStack stack, CallbackInfo ci) {
        if (unsupported(stack)) ci.cancel();
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean unsupported(ItemStack stack) {
        return ServerLightingSupport.isClientOnly() && (stack.getItem() instanceof FlashlightItem
                || stack.getItem() instanceof BlockItem block && block.getBlock() instanceof LightSourceBlock);
    }
}
