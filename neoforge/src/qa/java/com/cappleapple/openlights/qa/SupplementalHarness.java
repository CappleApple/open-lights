package com.cappleapple.openlights.qa;
import com.cappleapple.openlights.api.client.*;
import com.cappleapple.openlights.beam.BeamProfiles;
import com.cappleapple.openlights.client.LightingBudgets;
import com.cappleapple.openlights.client.render.OpenLightRenderer;
import com.cappleapple.openlights.config.ClientConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.util.*;

@EventBusSubscriber(modid="openlightsqa",value=Dist.CLIENT)
public final class SupplementalHarness {
    private static final String[] NAMES={"quarter-scale","full-scale","volume-off","volume-high","bloom-off","bloom-high","one-light","no-shadows","high-shadows","no-transmission","transmission","event-source","periodic-refresh","dimension","overworld","reload","disabled","restored","disconnect"};
    private static final List<LightHandle> handles=new ArrayList<>();
    private static boolean loading,started,eventSource,ready;
    private static int stage,ticks;
    private static long revision;
    @SubscribeEvent public static void collect(CollectLightsEvent event) {
        if(eventSource)event.add(new LightKey(ResourceLocation.fromNamespaceAndPath("openlights","qa_event"),new UUID(0,7)),definition(0));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("openlights.extraSmoke"))return;
        var mc=Minecraft.getInstance();mc.mouseHandler.releaseMouse();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);mc.options.pauseOnLostFocus=false;
        if(!loading&&mc.screen instanceof TitleScreen title){loading=true;QaBootstrap.open(title);}
        if(!started&&mc.player!=null&&mc.screen==null) {
            started=true;ClientConfig.ENABLED.set(true);ClientConfig.GI_ENABLED.set(false);ClientConfig.RENDER_SCALE.set(.25);
            for(var preset:LightingBudgets.values()){preset.apply();if(LightingBudgets.current()!=preset)throw new IllegalStateException("Preset round trip "+preset);}
            LightingBudgets.BALANCED.apply();
            mc.getSingleplayerServer().execute(()->{
                var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                player.getInventory().clearContent();NativeAssertions.verify(player);player.getAbilities().flying=true;player.onUpdateAbilities();
                player.teleportTo(player.serverLevel(),.5,166,-5,0,15);
                for(int x=-7;x<=7;x++)for(int z=-2;z<=18;z++)player.serverLevel().setBlock(new BlockPos(x,160,z),Blocks.STONE.defaultBlockState(),3);
                for(int y=161;y<=167;y++)player.serverLevel().setBlock(new BlockPos(0,y,7),Blocks.RED_STAINED_GLASS.defaultBlockState(),3);
                ready=true;
            });
            for(int i=0;i<3;i++)handles.add(OpenLightsApi.create(ResourceLocation.fromNamespaceAndPath("openlights","extra"),definition(i)));
            TextureUploadRegression.run();
        }
        if(started)ticks++;
        if(stage==NAMES.length&&ticks>20)mc.stop();
        if(started&&ticks>1600)throw new IllegalStateException("Extra timeout "+NAMES[Math.min(stage,NAMES.length-1)]);
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if(!Boolean.getBoolean("openlights.extraSmoke")||!ready||ticks<100||stage>=NAMES.length)return;
        var mc=Minecraft.getInstance();
        if(stage!=18&&(mc.level==null||mc.screen!=null))return;
        var stats=OpenLightRenderer.statistics();
        if(stage==6&&stats.lights()!=1)throw new IllegalStateException("Light count cap ignored");
        if(stage==7&&stats.shadowPasses()!=0)throw new IllegalStateException("Shadows disabled but rendered");
        if(stage==9&&stats.media()!=0)throw new IllegalStateException("Transmission cap ignored");
        if(stage==11&&!OpenLightRenderer.frameLights().keySet().stream().anyMatch(k->k.owner().getPath().equals("qa_event")))throw new IllegalStateException("Frame event light absent");
        if(stage==13&&(mc.level.dimension()!=Level.NETHER||handles.stream().anyMatch(LightHandle::isValid)||BeamProfiles.clientRevision()!=revision))throw new IllegalStateException("Dimension lifecycle failed");
        if(stage==14&&mc.level.dimension()!=Level.OVERWORLD)throw new IllegalStateException("Return failed");
        if(stage==15&&handles.stream().anyMatch(LightHandle::isValid))throw new IllegalStateException("Reload retained handles");
        if(stage==16&&OpenLightRenderer.cacheStatistics().replacement())throw new IllegalStateException("Disable retained replacement");
        if(stage==17&&!OpenLightRenderer.cacheStatistics().replacement())throw new IllegalStateException("Restore failed");
        if(stage==18&&(mc.level!=null||!OpenLightsApi.snapshot().isEmpty()||BeamProfiles.clientRevision()!=0))throw new IllegalStateException("Disconnect did not clear world/profile state");
        Screenshot.grab(mc.gameDirectory,"extra-"+NAMES[stage]+".png",mc.getMainRenderTarget(),m->{});
        LogUtils.getLogger().info("EXTRA_PASS {} {}",NAMES[stage],stats);stage++;ticks=0;
        switch(stage) {
            case 1->ClientConfig.RENDER_SCALE.set(1.0);
            case 2->ClientConfig.VOLUMETRIC_STEPS.set(0);
            case 3->ClientConfig.VOLUMETRIC_STEPS.set(24);
            case 4->ClientConfig.BLOOM_STRENGTH.set(0.0);
            case 5->ClientConfig.BLOOM_STRENGTH.set(2.0);
            case 6->ClientConfig.MAX_LIGHTS.set(1);
            case 7->{ClientConfig.MAX_LIGHTS.set(8);ClientConfig.MAX_SHADOW_LIGHTS.set(0);}
            case 8->{ClientConfig.MAX_SHADOW_LIGHTS.set(4);ClientConfig.SHADOW_RESOLUTION.set(2048);}
            case 9->{ClientConfig.SHADOW_RESOLUTION.set(64);ClientConfig.MAX_MEDIA.set(0);}
            case 10->ClientConfig.MAX_MEDIA.set(32);
            case 11->{handles.forEach(LightHandle::close);eventSource=true;}
            case 12->{eventSource=false;ClientConfig.PERIODIC_CACHE_REFRESH.set(true);ClientConfig.MEDIUM_UPDATE_TICKS.set(1);}
            case 13->{ClientConfig.PERIODIC_CACHE_REFRESH.set(false);handles.clear();handles.add(OpenLightsApi.create(ResourceLocation.fromNamespaceAndPath("openlights","lifecycle"),definition(0)));revision=BeamProfiles.clientRevision();travel(Level.NETHER);}
            case 14->travel(Level.OVERWORLD);
            case 15->{handles.add(OpenLightsApi.create(ResourceLocation.fromNamespaceAndPath("openlights","reload"),definition(0)));mc.reloadResourcePacks();}
            case 16->ClientConfig.ENABLED.set(false);
            case 17->ClientConfig.ENABLED.set(true);
            case 18->{mc.level.disconnect();mc.disconnect();mc.setScreen(new TitleScreen());}
            default->{ClientConfig.RENDER_SCALE.set(.5);ClientConfig.VOLUMETRIC_STEPS.set(12);ClientConfig.BLOOM_STRENGTH.set(.08);ClientConfig.SHADOW_RESOLUTION.set(256);LogUtils.getLogger().info("EXTRA_SUCCESS presets, rendering controls, frame submissions, dimension/reload/disconnect lifecycle");}
        }
    }
    private static LightDefinition definition(int i){return new LightDefinition.Point(new Vec3(-2+i*2,164,4),new Vec3(i==0?1:.1,i==1?1:.1,i==2?1:.1),2,24,true,.7f);}
    private static void travel(net.minecraft.resources.ResourceKey<Level> dimension){var mc=Minecraft.getInstance();var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);p.teleportTo(mc.getSingleplayerServer().getLevel(dimension),.5,166,-5,0,15);});}
}
