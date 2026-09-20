package com.cappleapple.openlights.content;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class LightSourceBlockEntity extends BlockEntity {
    private int color = 0xffffff;
    private float intensity = 1;
    private float range = 24;
    private boolean enabled = true;

    public LightSourceBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.LIGHT_SOURCE_BLOCK_ENTITY.get(), pos, state);
    }

    public int color() { return color; }
    public float intensity() { return intensity; }
    public float range() { return range; }
    public boolean enabled() { return enabled; }

    public void setColor(int color) {
        this.color = color & 0xffffff;
        changed();
    }

    public void toggle() {
        enabled = !enabled;
        changed();
    }

    public void cycleRange() {
        range = range < 16 ? 16 : range < 24 ? 24 : range < 32 ? 32 : range < 48 ? 48 : 8;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Color", color);
        tag.putFloat("Intensity", intensity);
        tag.putFloat("Range", range);
        tag.putBoolean("Enabled", enabled);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        color = tag.contains("Color") ? tag.getInt("Color") & 0xffffff : 0xffffff;
        intensity = finiteClamped(tag.getFloat("Intensity"), 1, 0, 8, tag.contains("Intensity"));
        range = finiteClamped(tag.getFloat("Range"), 24, 1, 128, tag.contains("Range"));
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
    }

    private static float finiteClamped(float value, float fallback, float min, float max, boolean present) {
        return !present || !Float.isFinite(value) ? fallback : Math.max(min, Math.min(max, value));
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void setRemoved() {
        if (level != null) LightSourceTracker.forget(level, worldPosition);
        super.setRemoved();
    }
}
