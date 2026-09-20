package com.cappleapple.openlights.client;

import com.cappleapple.openlights.OpenLightsMod;
import com.cappleapple.openlights.beam.BeamProfiles;
import com.cappleapple.openlights.client.particle.BeamDustParticles;
import com.cappleapple.openlights.config.ClientConfig;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import com.cappleapple.openlights.api.client.*;
import com.cappleapple.openlights.client.render.OpenLightRenderer;
import com.cappleapple.openlights.content.FlashlightItem;
import com.cappleapple.openlights.content.ModContent;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

@Mod.EventBusSubscriber(modid=OpenLightsMod.MOD_ID,value=Dist.CLIENT)
public final class ClientEvents {
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (!ShaderCompatibility.isRenderingShadowPass()) OpenLightRenderer.stage(event);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if(event.getLevel().isClientSide()) { OpenLightRenderer.clearWorld(); BeamDustParticles.clear(); }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { OpenLightRenderer.clearWorld(); BeamDustParticles.clear(); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase==TickEvent.Phase.END && !Minecraft.getInstance().isPaused()) BeamDustParticles.tick(ClientConfig.ENABLED.get() && ClientConfig.BEAM_DUST.get());
    }

    @SubscribeEvent public static void handheld(CollectLightsEvent event) {
        if (ShaderCompatibility.isRenderingShadowPass()) return;
        var mc=Minecraft.getInstance();
        if(mc.level==null) return;
        for(var player:mc.level.players()) {
            if(!player.isAlive()||player.isSpectator())continue;
            var stack=player.getMainHandItem();
            boolean offhand=false;
            if(!stack.is(ModContent.FLASHLIGHT.get()) || !FlashlightItem.isEnabled(stack)) {
                stack=player.getOffhandItem();offhand=true;
            }
            if(!stack.is(ModContent.FLASHLIGHT.get())||!FlashlightItem.isEnabled(stack))continue;
            Vec3 direction=player.getViewVector(event.partialTick()).normalize();
            double yaw=Math.toRadians(player.getViewYRot(event.partialTick()));
            double hand=(player.getMainArm()==HumanoidArm.RIGHT?1:-1)*(offhand?-1:1);
            Vec3 origin=player.getEyePosition(event.partialTick())
                    .add(new Vec3(-Math.cos(yaw),0,-Math.sin(yaw)).scale(hand*.22))
                    .add(0,-.24,0).add(direction.scale(.28));
            event.add(new LightKey(new ResourceLocation("openlights","handheld"),player.getUUID()),
                    BeamLights.spot(origin,direction,new Vec3(0,1,0),BeamProfiles.clientProfile(new ResourceLocation("openlights","flashlight"))));
        }
    }

    @Mod.EventBusSubscriber(modid=OpenLightsMod.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent public static void particles(RegisterParticleProvidersEvent event) { BeamDustParticles.registerProviders(event); }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> {
                if(RenderSystem.isOnRenderThread()) { BeamDustParticles.clear(); OpenLightRenderer.reload(); }
                else RenderSystem.recordRenderCall(() -> { BeamDustParticles.clear(); OpenLightRenderer.reload(); });
            });
        }
    }
    private ClientEvents() {}
}
