package com.cappleapple.openlights.client;

import com.cappleapple.openlights.OpenLightsMod;
import com.cappleapple.openlights.beam.BeamProfiles;
import com.cappleapple.openlights.client.particle.BeamDustParticles;
import com.cappleapple.openlights.config.ClientConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import com.cappleapple.openlights.api.client.*;
import com.cappleapple.openlights.client.render.OpenLightRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

@EventBusSubscriber(modid=OpenLightsMod.MOD_ID,value=Dist.CLIENT)
public final class ClientEvents {
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (!ShaderCompatibility.isRenderingShadowPass()) OpenLightRenderer.stage(event);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if(event.getLevel().isClientSide()) { OpenLightRenderer.clearWorld(); BeamDustParticles.clear(); }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { OpenLightRenderer.clearWorld(); BeamDustParticles.clear(); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Minecraft.getInstance().isPaused()) {
            OpenLightRenderer.clientTick();
            BeamDustParticles.tick(ClientConfig.ENABLED.get() && ClientConfig.BEAM_DUST.get());
        }
    }



    @EventBusSubscriber(modid=OpenLightsMod.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
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
