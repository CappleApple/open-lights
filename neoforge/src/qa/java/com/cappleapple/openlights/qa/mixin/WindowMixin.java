package com.cappleapple.openlights.qa.mixin;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.glfw.GLFW;
@Mixin(Window.class)
abstract class WindowMixin {
    @Inject(method="<init>",at=@At("RETURN"))
    private void hide(CallbackInfo ci) {
        long handle=((Window)(Object)this).getWindow();
        GLFW.glfwSetWindowAttrib(handle,GLFW.GLFW_FOCUS_ON_SHOW,GLFW.GLFW_FALSE);
        GLFW.glfwHideWindow(handle);
    }
}
