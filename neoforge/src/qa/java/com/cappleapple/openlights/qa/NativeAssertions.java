package com.cappleapple.openlights.qa;
import com.cappleapple.openlights.content.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Runs against loaded server registries and real block/item interaction methods. */
final class NativeAssertions {
    static void verify(ServerPlayer player) {
        var level=player.serverLevel();var registries=level.registryAccess();
        for(String id:new String[]{"flashlight","point_light","spot_light","area_light"}) {
            var key=ResourceLocation.fromNamespaceAndPath("openlights",id);
            var recipe=level.getRecipeManager().byKey(key).orElseThrow(()->new IllegalStateException("Missing recipe "+key));
            if(recipe.value().getResultItem(registries).isEmpty())throw new IllegalStateException("Empty recipe "+key);
        }
        var flashlight=new ItemStack(ModContent.FLASHLIGHT.get());player.setItemInHand(InteractionHand.MAIN_HAND,flashlight);
        ModContent.FLASHLIGHT.get().use(level,player,InteractionHand.MAIN_HAND);
        var saved=flashlight.save(registries);
        if(!FlashlightItem.isEnabled(ItemStack.parse(registries,saved).orElseThrow()))throw new IllegalStateException("Flashlight component save/load failed");
        for(var block:new LightSourceBlock[]{ModContent.POINT_LIGHT.get(),ModContent.SPOT_LIGHT.get(),ModContent.AREA_LIGHT.get()}) {
            for(Direction direction:Direction.values()) {
                var pos=new BlockPos(12,165,2+direction.ordinal());level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
                level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
                player.setYRot(direction==Direction.NORTH?180:direction==Direction.EAST?-90:direction==Direction.WEST?90:0);
                player.setYHeadRot(player.getYRot());
                player.setXRot(direction==Direction.UP?-90:direction==Direction.DOWN?90:0);
                var item=new ItemStack(block);player.setItemInHand(InteractionHand.MAIN_HAND,item);
                var context=new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false)));
                if(!((BlockItem)item.getItem()).place(context).consumesAction()||!level.getBlockState(pos).is(block))throw new IllegalStateException("Placement failed "+block);
                if(level.getBlockState(pos).getValue(LightSourceBlock.FACING)!=direction.getOpposite())throw new IllegalStateException("Placement orientation failed "+direction+" actual="+level.getBlockState(pos)+" look="+context.getNearestLookingDirection());
                var entity=(LightSourceBlockEntity)level.getBlockEntity(pos);entity.setColor(0x3366ff);entity.toggle();entity.cycleRange();
                var tag=entity.saveWithFullMetadata(registries);var restored=new LightSourceBlockEntity(pos,level.getBlockState(pos));restored.loadWithComponents(tag,registries);
                if(restored.color()!=0x3366ff||restored.enabled()||restored.range()!=32)throw new IllegalStateException("Light block persistence failed");
                for(float expected:new float[]{48,8,16,24,32}) {entity.cycleRange();if(entity.range()!=expected)throw new IllegalStateException("Range cycle failed");}
                var drops=Block.getDrops(level.getBlockState(pos),level,pos,entity,player,ItemStack.EMPTY);
                if(drops.size()!=1||!drops.getFirst().is(block.asItem()))throw new IllegalStateException("Light loot missing");
                level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);level.setBlock(pos.below(),Blocks.AIR.defaultBlockState(),3);
            }
        }
        player.setXRot(0);player.setYRot(0);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        LogUtils.getLogger().info("NATIVE_ASSERTIONS_SUCCESS four recipes, flashlight save/load, all three block placements in six orientations, block save/load, range cycles and loot");
    }
}
