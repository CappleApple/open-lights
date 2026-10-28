package com.cappleapple.openlights.flashlight;

import com.cappleapple.openlights.flashlight.FlashlightItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Receive-only item battery; state lives on the stack so copies, saves and inventory sync preserve it. */
public final class FlashlightEnergy implements IEnergyStorage {
    private final ItemStack stack;
    public FlashlightEnergy(ItemStack stack) { this.stack = stack; }
    @Override public int receiveEnergy(int requested, boolean simulate) {
        int stored = getEnergyStored();
        int accepted = FlashlightBattery.accepted(stored, getMaxEnergyStored(), requested, FlashlightConfig.maxReceive());
        if (!simulate && accepted > 0) FlashlightItem.setEnergy(stack, stored + accepted);
        return accepted;
    }
    @Override public int extractEnergy(int requested, boolean simulate) { return 0; }
    @Override public int getEnergyStored() { return FlashlightItem.energy(stack); }
    @Override public int getMaxEnergyStored() { return FlashlightConfig.capacity(); }
    @Override public boolean canExtract() { return false; }
    @Override public boolean canReceive() { return true; }
}
