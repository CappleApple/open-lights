package com.cappleapple.openlights.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/** A placeable directional light with server-owned color, range, and toggle state. */
public final class LightSourceBlock extends DirectionalBlock implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(4, 4, 4, 12, 12, 12);
    private final LightShape shape;

    public LightSourceBlock(Properties properties, LightShape shape) {
        super(properties);
        this.shape = shape;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public LightShape shape() { return shape; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LightSourceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (!level.isClientSide || type != ModContent.LIGHT_SOURCE_BLOCK_ENTITY.get()) {
            return null;
        }
        return (tickLevel, pos, blockState, entity) -> LightSourceTracker.see(tickLevel, pos);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof LightSourceBlockEntity light)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty() && !(held.getItem() instanceof DyeItem)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (held.getItem() instanceof DyeItem dye) {
                int color = dye.getDyeColor().getTextColor();
                if (light.color() != color) {
                    light.setColor(color);
                    if (!player.getAbilities().instabuild) held.shrink(1);
                }
            } else if (player.isShiftKeyDown()) {
                light.cycleRange();
                player.displayClientMessage(Component.translatable("message.openlights.range", (int) light.range()), true);
            } else {
                light.toggle();
                player.displayClientMessage(Component.translatable(light.enabled()
                        ? "message.openlights.light_on" : "message.openlights.light_off"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
