package com.cappleapple.openlights.mixin;

import com.cappleapple.openlights.client.render.OpenLightRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method="blockChanged",at=@At("HEAD"))
    private void openlights$blockChanged(BlockGetter level, BlockPos pos, BlockState before,
                                        BlockState after,int flags,CallbackInfo ci) {
        OpenLightRenderer.invalidate(pos.getX()-1,pos.getY()-1,pos.getZ()-1,pos.getX()+1,pos.getY()+1,pos.getZ()+1);
    }
    @Inject(method="setBlocksDirty",at=@At("HEAD"))
    private void openlights$regionChanged(int minX,int minY,int minZ,int maxX,int maxY,int maxZ,CallbackInfo ci) {
        OpenLightRenderer.invalidate(minX,minY,minZ,maxX,maxY,maxZ);
    }
}
