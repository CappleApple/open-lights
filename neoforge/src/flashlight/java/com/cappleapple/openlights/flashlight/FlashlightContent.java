package com.cappleapple.openlights.flashlight;

import com.cappleapple.openlights.flashlight.FlashlightItem;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities;

public final class FlashlightContent {
    // Keep the pre-split ID so existing inventory stacks retain their toggle and battery data.
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "openlights");
    public static final DeferredHolder<Item, FlashlightItem> FLASHLIGHT = ITEMS.register("flashlight",
            () -> new FlashlightItem(new Item.Properties().stacksTo(1)));
    private FlashlightContent() {}
    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener((RegisterCapabilitiesEvent event) -> event.registerItem(
                Capabilities.EnergyStorage.ITEM, (stack, context) -> new FlashlightEnergy(stack), FLASHLIGHT.get()));
    }
}
