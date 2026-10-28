package com.cappleapple.openlights.flashlight;

import com.cappleapple.openlights.flashlight.FlashlightItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;

public final class FlashlightContent {
    // Keep the pre-split ID so existing inventory stacks retain their toggle and battery data.
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "openlights");
    public static final RegistryObject<FlashlightItem> FLASHLIGHT = ITEMS.register("flashlight",
            () -> new FlashlightItem(new Item.Properties().stacksTo(1)));
    private FlashlightContent() {}
    public static void register(IEventBus bus) {
        ITEMS.register(bus);

    }
}
