package com.cappleapple.openlights.content;

import com.cappleapple.openlights.OpenLightsMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModContent {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, OpenLightsMod.MOD_ID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, OpenLightsMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, OpenLightsMod.MOD_ID);

    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, OpenLightsMod.MOD_ID);
    public static final RegistryObject<SimpleParticleType> BEAM_DUST = PARTICLES.register("beam_dust", () -> new SimpleParticleType(false));

    public static final RegistryObject<FlashlightItem> FLASHLIGHT = ITEMS.register("flashlight",
            () -> new FlashlightItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<LightSourceBlock> POINT_LIGHT = light("point_light", LightShape.POINT);
    public static final RegistryObject<LightSourceBlock> SPOT_LIGHT = light("spot_light", LightShape.SPOT);
    public static final RegistryObject<LightSourceBlock> AREA_LIGHT = light("area_light", LightShape.AREA);
    public static final RegistryObject<BlockEntityType<LightSourceBlockEntity>> LIGHT_SOURCE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("light_source", () -> BlockEntityType.Builder.of(LightSourceBlockEntity::new,
                    POINT_LIGHT.get(), SPOT_LIGHT.get(), AREA_LIGHT.get()).build(null));

    private ModContent() {}

    private static RegistryObject<LightSourceBlock> light(String name, LightShape shape) {
        RegistryObject<LightSourceBlock> block = BLOCKS.register(name, () -> new LightSourceBlock(
                BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL).noOcclusion(), shape));
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        PARTICLES.register(bus);
        bus.addListener(ModContent::creativeTabs);
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(FLASHLIGHT.get());
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(POINT_LIGHT.get());
            event.accept(SPOT_LIGHT.get());
            event.accept(AREA_LIGHT.get());
        }
    }
}
