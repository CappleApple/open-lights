package com.cappleapple.openlights.qa.mixin;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Inject(method="grabMouse",at=@At("HEAD"),cancellable=true)
    private void release(CallbackInfo ci){ci.cancel();}
}
