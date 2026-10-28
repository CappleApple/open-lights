package com.cappleapple.openlights.flashlight;

import com.cappleapple.openlights.flashlight.FlashlightBattery;
import com.cappleapple.openlights.flashlight.FlashlightConfig;
import com.cappleapple.openlights.flashlight.FlashlightEnergy;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;
import java.util.List;

/** Server-owned toggle and RF/FE battery; vanilla stack synchronization carries both to clients. */
public final class FlashlightItem extends Item {
    public static final String ENABLED_TAG = "OpenLightsEnabled";
    public static final String ENERGY_TAG = "OpenLightsEnergy";
    public FlashlightItem(Properties properties) { super(properties); }

    private static boolean toggled(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(ENABLED_TAG); }
    public static boolean isEnabled(ItemStack stack) {
        return stack.getItem() instanceof FlashlightItem && toggled(stack)
                && (!FlashlightConfig.requiresEnergy() || FlashlightConfig.energyPerTick() == 0 || FlashlightBattery.canRun(energy(stack), FlashlightConfig.capacity(), FlashlightConfig.energyPerTick()));
    }
    public static int energy(ItemStack stack) {
        return FlashlightBattery.clamp(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(ENERGY_TAG), FlashlightConfig.capacity());
    }
    public static void setEnergy(ItemStack stack, int value) {
        int stored = FlashlightBattery.clamp(value, FlashlightConfig.capacity());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(ENERGY_TAG, stored));
    }
    private static void setEnabled(ItemStack stack, boolean enabled) { CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(ENABLED_TAG, enabled)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            boolean enabled = !toggled(stack);
            if (enabled && FlashlightConfig.requiresEnergy() && FlashlightConfig.energyPerTick() > 0 && !FlashlightBattery.canRun(energy(stack), FlashlightConfig.capacity(), FlashlightConfig.energyPerTick())) {
                player.displayClientMessage(Component.translatable("message.openlights.flashlight_empty"), true);
                return InteractionResultHolder.fail(stack);
            }
            setEnabled(stack, enabled);
            level.playSound(null, player.blockPosition(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS,
                    0.3f, enabled ? 0.7f : 0.5f);
            player.displayClientMessage(Component.translatable(enabled
                    ? "message.openlights.flashlight_on" : "message.openlights.flashlight_off"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !toggled(stack) || !FlashlightConfig.requiresEnergy()
                || FlashlightConfig.energyPerTick() == 0 || !(entity instanceof LivingEntity living)
                || (stack != living.getMainHandItem() && stack != living.getOffhandItem())) return;
        // The main hand takes precedence in the collector; a second, hidden beam must not drain.
        if (stack == living.getOffhandItem() && isEnabled(living.getMainHandItem())) return;
        int stored = energy(stack);
        int cost = FlashlightConfig.energyPerTick();
        if (!FlashlightBattery.canRun(stored, FlashlightConfig.capacity(), cost)) {
            setEnabled(stack, false);
            return;
        }
        int remaining = FlashlightBattery.remaining(stored, FlashlightConfig.capacity(), cost);
        setEnergy(stack, remaining);
        if (!FlashlightBattery.canRun(remaining, FlashlightConfig.capacity(), cost)) setEnabled(stack, false);
    }

    @Override public boolean isBarVisible(ItemStack stack) { return FlashlightConfig.requiresEnergy() && FlashlightConfig.energyPerTick() > 0; }
    @Override public int getBarWidth(ItemStack stack) { return Math.round(13f * energy(stack) / FlashlightConfig.capacity()); }
    @Override public int getBarColor(ItemStack stack) { return 0x55ccff; }
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(isEnabled(stack) ? "tooltip.openlights.on" : "tooltip.openlights.off"));
        tooltip.add(Component.translatable("tooltip.openlights.flashlight_use"));
        tooltip.add(Component.translatable("tooltip.openlights.energy", energy(stack), FlashlightConfig.capacity()));
        tooltip.add(Component.translatable(FlashlightConfig.requiresEnergy() && FlashlightConfig.energyPerTick() > 0
                ? "tooltip.openlights.energy_required" : "tooltip.openlights.energy_free"));
    }
}
