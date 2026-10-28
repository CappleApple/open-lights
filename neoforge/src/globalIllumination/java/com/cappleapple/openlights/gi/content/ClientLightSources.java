package com.cappleapple.openlights.gi.content;

import com.cappleapple.openlights.OpenLightsMod;
import com.cappleapple.openlights.api.client.CollectLightsEvent;
import com.cappleapple.openlights.api.client.LightDefinition;
import com.cappleapple.openlights.beam.BeamProfiles;
import com.cappleapple.openlights.api.client.LightKey;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.UUID;

@EventBusSubscriber(modid = "ol_global_illumination", value = Dist.CLIENT)
public final class ClientLightSources {
    private static final ResourceLocation OWNER = ResourceLocation.fromNamespaceAndPath(OpenLightsMod.MOD_ID, "block");

    private ClientLightSources() {}

    @SubscribeEvent
    public static void login(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        if (com.cappleapple.openlights.gi.GiServerSupport.isClientOnly()) return;
        var mc = Minecraft.getInstance();
        var models = mc.getModelManager();
        for (var block : java.util.List.of(ModContent.POINT_LIGHT.get(), ModContent.SPOT_LIGHT.get(), ModContent.AREA_LIGHT.get())) {
            if (models.getBlockModelShaper().getBlockModel(block.defaultBlockState()) == models.getMissingModel()) {
                mc.reloadResourcePacks();
                return;
            }
        }
    }

    @net.neoforged.fml.common.EventBusSubscriber(modid="ol_global_illumination", value=Dist.CLIENT, bus=net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent public static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
            if (com.cappleapple.openlights.gi.GiServerSupport.isClientOnly()) return;
            if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) {
                event.accept(ModContent.POINT_LIGHT.get());
                event.accept(ModContent.SPOT_LIGHT.get());
                event.accept(ModContent.AREA_LIGHT.get());
            }
        }
    }

    @SubscribeEvent
    public static void collect(CollectLightsEvent event) {
        var level = Minecraft.getInstance().level;
        if (level == null || com.cappleapple.openlights.gi.GiServerSupport.isClientOnly()) return;
        for (LightSourceBlockEntity light : LightSourceTracker.snapshot(level)) {
            if (!light.enabled() || !(light.getBlockState().getBlock() instanceof LightSourceBlock block)) continue;
            Direction facing = light.getBlockState().getValue(LightSourceBlock.FACING);
            Vec3 forward = Vec3.atLowerCornerOf(facing.getNormal());
            Vec3 up = facing.getAxis() == Direction.Axis.Y ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
            Vec3 center = Vec3.atCenterOf(light.getBlockPos());
            int rgb = light.color();
            Vec3 color = new Vec3(((rgb >> 16) & 255) / 255.0, ((rgb >> 8) & 255) / 255.0, (rgb & 255) / 255.0);
            var profile=BeamProfiles.clientProfile(ResourceLocation.fromNamespaceAndPath("openlights","spot_light"));
            LightDefinition definition = switch (block.shape()) {
                case POINT -> new LightDefinition.Point(center, color, light.intensity(), light.range(), true, 0.25f);
                case SPOT -> new LightDefinition.Spot(center.add(forward.scale(0.55)), forward, up, color,
                        light.intensity(), Math.min(light.range(),Math.max(profile.inner().range(),profile.outer().range())),
                        profile.outer().angleDegrees(), profile.inner().angleDegrees(), profile.shadows(), profile.fogDensity(),profile);
                case AREA -> new LightDefinition.Area(center.add(forward.scale(0.55)), forward, up, color,
                        light.intensity(), light.range(), 1.5f, 0.75f, 100, true, 0.4f);
            };
            event.add(new LightKey(OWNER, new UUID(light.getBlockPos().asLong(), block.shape().ordinal())), definition);
        }
    }
}
