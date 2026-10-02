package com.cappleapple.openlights.mixin;

import com.cappleapple.openlights.client.render.OpenLightRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightTexture.class)
abstract class LightTextureMixin {
    @Shadow @Final private DynamicTexture lightTexture;

    @Inject(method = "turnOnLightLayer", at = @At("RETURN"))
    private void openlights$worldLightmap(CallbackInfo ci) {
        OpenLightRenderer.nativeLightmap(lightTexture);
        int replacement = OpenLightRenderer.worldLightmap();
        if (replacement != 0) RenderSystem.setShaderTexture(2, replacement);
    }
}
