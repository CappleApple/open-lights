package com.cappleapple.openlights.flashlight;

import com.cappleapple.openlights.api.client.*;
import com.cappleapple.openlights.beam.BeamProfiles;
import com.cappleapple.openlights.flashlight.FlashlightItem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid=FlashlightMod.MOD_ID,value=Dist.CLIENT)
public final class FlashlightClientEvents {
    private FlashlightClientEvents() {}
    @SubscribeEvent public static void handheld(CollectLightsEvent event) {
        if (ShaderCompatibility.isRenderingShadowPass()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (var player : mc.level.players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            var stack = player.getMainHandItem();
            boolean offhand = false;
            if (!FlashlightItem.isEnabled(stack)) { stack = player.getOffhandItem(); offhand = true; }
            if (!FlashlightItem.isEnabled(stack)) continue;
            Vec3 direction = player.getViewVector(event.partialTick()).normalize();
            double yaw = Math.toRadians(player.getViewYRot(event.partialTick()));
            double hand = (player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1) * (offhand ? -1 : 1);
            Vec3 origin = player.getEyePosition(event.partialTick())
                    .add(new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)).scale(hand * .22))
                    .add(0, -.24, 0).add(direction.scale(.28));
            event.add(new LightKey(ResourceLocation.fromNamespaceAndPath(FlashlightMod.MOD_ID, "handheld"), player.getUUID()),
                    BeamLights.spot(origin, direction, new Vec3(0, 1, 0),
                            BeamProfiles.clientProfile(ResourceLocation.fromNamespaceAndPath("openlights", "flashlight"))));
        }
    }

    @EventBusSubscriber(modid=FlashlightMod.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent public static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(FlashlightContent.FLASHLIGHT.get());
        }
    }
}
