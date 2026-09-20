package com.cappleapple.openlights.content;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Server-owned toggle stored on the item; vanilla inventory/equipment sync carries it to clients. */
public final class FlashlightItem extends Item {
    public static final String ENABLED_TAG = "OpenLightsEnabled";

    public FlashlightItem(Properties properties) {
        super(properties);
    }

    public static boolean isEnabled(ItemStack stack) {
        return stack.getItem() instanceof FlashlightItem && stack.hasTag()
                && stack.getTag().getBoolean(ENABLED_TAG);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            boolean enabled = !isEnabled(stack);
            stack.getOrCreateTag().putBoolean(ENABLED_TAG, enabled);
            level.playSound(null, player.blockPosition(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS,
                    0.3f, enabled ? 0.7f : 0.5f);
            player.displayClientMessage(Component.translatable(enabled
                    ? "message.openlights.flashlight_on" : "message.openlights.flashlight_off"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(isEnabled(stack)
                ? "tooltip.openlights.on" : "tooltip.openlights.off"));
        tooltip.add(Component.translatable("tooltip.openlights.flashlight_use"));
    }
}
