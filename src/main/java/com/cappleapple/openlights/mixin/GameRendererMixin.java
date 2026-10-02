package com.cappleapple.openlights.mixin;

import com.cappleapple.openlights.client.render.OpenLightRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method="renderItemInHand",at=@At("HEAD"))
    private void openlights$beginHand(CallbackInfo ci){OpenLightRenderer.beginHand();}
    @Inject(method="renderItemInHand",at=@At("RETURN"))
    private void openlights$endHand(CallbackInfo ci){OpenLightRenderer.endHand();}
}
