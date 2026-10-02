package com.cappleapple.openlights.qa;

import com.cappleapple.openlights.api.client.*;
import com.cappleapple.openlights.beam.BeamProfile;
import com.cappleapple.openlights.client.LightingBudgets;
import com.cappleapple.openlights.client.render.OpenLightRenderer;
import com.cappleapple.openlights.config.ClientConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Visible influence, off-screen sources, budget pressure, and opaque-depth ray clipping. */
@EventBusSubscriber(modid="openlightsqa",value=Dist.CLIENT)
public final class VisibilityVisualHarness {
    private static boolean loading,moved,prepared;
    private static volatile boolean built;
    private static int stage,ticks,total;
    private static final List<LightHandle> hidden=new ArrayList<>();
    private static LightHandle visible,occluded;
    private static final ResourceLocation OWNER=ResourceLocation.fromNamespaceAndPath("openlightsqa","visibility");
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("openlights.visibilitySmoke"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        mc.mouseHandler.releaseMouse();GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(!loading&&mc.screen instanceof TitleScreen title) {
            loading=true;mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);
            mc.options.framerateLimit().set(60);mc.options.hideGui=true;mc.options.fov().set(65);QaBootstrap.open(title);
        }
        if(mc.player==null||mc.getSingleplayerServer()==null)return;
        if(!moved) {
            moved=true;ClientConfig.ENABLED.set(true);ClientConfig.GI_ENABLED.set(false);
            ClientConfig.BLOCK_LIGHT_STYLE.set(ClientConfig.BlockLightStyle.OPEN_LIGHTS);
            ClientConfig.LIGHTING_MODE.set(ClientConfig.LightingMode.CACHED);
            ClientConfig.BLOOM_STRENGTH.set(0.0);ClientConfig.VOLUMETRIC_STEPS.set(12);
            ClientConfig.MAX_LIGHTS.set(8);ClientConfig.MAX_SHADOW_LIGHTS.set(4);
            ClientConfig.PERIODIC_CACHE_REFRESH.set(false);LightingBudgets.RAPID.apply();
            pose(505.5,0);
        }
        ticks++;total++;
        if(!prepared&&ticks>100&&mc.level.hasChunk(33,33)) {
            prepared=true;mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();level.setDayTime(18000);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,mc.getSingleplayerServer());
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,mc.getSingleplayerServer());
                for(int z=485;z<=525;z++)for(int x=498;x<=526;x++)for(int y=160;y<=172;y++) {
                    var state=y==160?Blocks.POLISHED_DEEPSLATE.defaultBlockState():y==172||x==498||x==526||z==485||z==525?Blocks.SMOOTH_QUARTZ.defaultBlockState():Blocks.AIR.defaultBlockState();
                    level.setBlock(new BlockPos(x,y,z),state,3);
                }
                for(int x=499;x<526;x++)for(int y=161;y<172;y++)level.setBlock(new BlockPos(x,y,524),Blocks.DIORITE.defaultBlockState(),3);
                built=true;
            });
        }
        if(total>3200)throw new IllegalStateException("Visibility fixture timed out at "+stage+": "+OpenLightRenderer.cacheStatistics());
        if(stage==5&&ticks>20)mc.stop();
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if(!Boolean.getBoolean("openlights.visibilitySmoke")||!built||stage>=5||ticks<160)return;
        var mc=Minecraft.getInstance();var stats=OpenLightRenderer.cacheStatistics();
        if(stats.aggregatePending()||stats.lightUploadsPending()||stats.blockPending()!=0)return;
        if(visible==null&&stage==0) {
            visible=OpenLightsApi.create(OWNER,new LightDefinition.Point(new Vec3(512.5,165,503),new Vec3(1,.7,.3),16,32,true,.5f));
            for(int i=0;i<10;i++)hidden.add(OpenLightsApi.create(OWNER,new LightDefinition.Spot(new Vec3(512.5+(i-5)*.01,165,504),
                    new Vec3(0,0,-1),new Vec3(0,1,0),new Vec3(.1,.2,1),2,24,30,20,true,1)));
            hidden.add(OpenLightsApi.create(OWNER,new LightDefinition.Area(new Vec3(512.5,165,503),new Vec3(0,0,-1),new Vec3(0,1,0),new Vec3(1,0,0),2,16,8,4,70,true,1)));
            hidden.add(OpenLightsApi.create(OWNER,new LightDefinition.Spot(new Vec3(512.5,165,503),new Vec3(0,0,-1),new Vec3(0,1,0),new Vec3(0,1,0),2,24,20,10,true,1,BeamProfile.DEFAULT)));
            ticks=0;return;
        }
        if(stage==0) {
            var selected=OpenLightRenderer.frameLights();
            if(selected.size()!=1||!selected.containsKey(visible.key()))throw new IllegalStateException("Invisible beams consume budget or off-screen source lost: "+selected);
            capture("visibility-offscreen-source");stage++;ticks=0;pose(505.5,180);
        } else if(stage==1) {
            long returned=hidden.stream().filter(light->OpenLightRenderer.frameLights().containsKey(light.key())).count();
            if(returned<6)throw new IllegalStateException("Turning toward culled beams did not restore them: "+returned);
            capture("visibility-turned-back");hidden.forEach(LightHandle::close);visible.close();
            occluded=OpenLightsApi.create(OWNER,new LightDefinition.Spot(new Vec3(512.5,165,513),new Vec3(0,0,1),new Vec3(0,1,0),new Vec3(1,0,0),8,8,80,50,true,2));
            mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();
                for(int x=499;x<526;x++)for(int y=161;y<172;y++)level.setBlock(new BlockPos(x,y,510),Blocks.DEEPSLATE_TILES.defaultBlockState(),3);
            });
            pose(505.5,0);stage++;ticks=0;
        } else if(stage==2) {
            if(!OpenLightRenderer.frameLights().containsKey(occluded.key()))throw new IllegalStateException("Occluded depth fixture not selected");
            capture("visibility-behind-wall");occluded.update(new LightDefinition.Spot(new Vec3(512.5,165,513),new Vec3(0,0,1),new Vec3(0,1,0),new Vec3(1,0,0),0,8,80,50,true,2));
            stage++;ticks=0;
        } else if(stage==3) {
            if(!OpenLightRenderer.frameLights().isEmpty())throw new IllegalStateException("Disabled fixture still renders");
            capture("visibility-behind-wall-control");occluded.update(new LightDefinition.Spot(new Vec3(512.5,165,513),new Vec3(0,0,1),new Vec3(0,1,0),new Vec3(1,0,0),8,8,80,50,true,2));
            pose(514,0);stage++;ticks=0;
        } else {
            if(!OpenLightRenderer.frameLights().containsKey(occluded.key()))throw new IllegalStateException("Camera inside beam volume was culled");
            capture("visibility-inside-beam");occluded.close();
            LogUtils.getLogger().info("VISIBILITY_SUCCESS away-facing point/spot/area/profile influence, eight-light budget, off-screen source, turn restoration, opaque-depth paired captures and camera inside volume");
            stage++;ticks=0;
        }
    }
    private static void pose(double z,float yaw) {
        var mc=Minecraft.getInstance();var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);p.setGameMode(GameType.SPECTATOR);
            p.teleportTo(p.serverLevel(),512.5,164,z,yaw,0);
        });
    }
    private static void capture(String name) {
        var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),m->{});
        LogUtils.getLogger().info("VISIBILITY_CAPTURE {} renderer={} cache={}",name,OpenLightRenderer.statistics(),OpenLightRenderer.cacheStatistics());
    }
}
