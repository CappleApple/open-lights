package com.cappleapple.openlights.qa;

import com.cappleapple.openlights.client.render.OpenLightRenderer;
import com.cappleapple.openlights.client.scene.WorldLightCache;
import com.cappleapple.openlights.config.ClientConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;

/** Real block-update traffic while cold lighting, publication and source removal converge. */
@EventBusSubscriber(modid="openlightsqa",value=Dist.CLIENT)
public final class UpdateProgressHarness {
    private static boolean loading,moved,prepared;
    private static volatile boolean built;
    private static int stage,ticks,traffic,idle,baselineDiscards;
    private static long baselineRevision;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("openlights.progressSmoke"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        mc.mouseHandler.releaseMouse();GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(!loading&&mc.screen instanceof TitleScreen title) {
            loading=true;mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);
            mc.options.framerateLimit().set(60);mc.options.hideGui=true;QaBootstrap.open(title);
        }
        if(mc.player==null||mc.getSingleplayerServer()==null)return;
        if(!moved) {
            moved=true;ClientConfig.ENABLED.set(true);ClientConfig.GI_ENABLED.set(false);
            ClientConfig.BLOCK_LIGHT_STYLE.set(ClientConfig.BlockLightStyle.OPEN_LIGHTS);
            ClientConfig.LIGHTING_MODE.set(ClientConfig.LightingMode.CACHED);
            ClientConfig.PERIODIC_CACHE_REFRESH.set(false);ClientConfig.AGGREGATE_ENABLED.set(true);
            ClientConfig.AGGREGATE_CELLS_PER_TICK.set(8192);ClientConfig.AGGREGATE_BUDGET_MILLIS.set(1.0);
            ClientConfig.AGGREGATE_APPLY_SECTIONS.set(1);ClientConfig.LIGHT_UPLOAD_SECTIONS.set(1);
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayer(mc.player.getUUID());
                p.setGameMode(GameType.SPECTATOR);p.teleportTo(p.serverLevel(),512.5,169,496.5,0,20);
            });
        }
        ticks++;
        if(!prepared&&ticks>100&&mc.level.hasChunk(34,34)) {
            prepared=true;mc.getSingleplayerServer().execute(()->{
                var world=mc.getSingleplayerServer().overworld();
                world.setDayTime(18000);world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,mc.getSingleplayerServer());
                world.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,mc.getSingleplayerServer());
                for(int z=-24;z<=24;z++)for(int x=-24;x<=24;x++)for(int y=160;y<=176;y++)
                    world.setBlock(new BlockPos(512+x,y,512+z),y==160||y==176||Math.abs(x)==24||Math.abs(z)==24?Blocks.SMOOTH_STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
                sources(true);built=true;
            });
        }
        if(stage==0&&built&&ticks>180&&mc.level.getBrightness(LightLayer.BLOCK,new BlockPos(512,162,512))==15) {
            world().clear();stage=1;ticks=0;baselineDiscards=world().aggregates().discarded();
        }
        if(stage>=1&&stage<=2) {
            int sequence=traffic++;
            mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().overworld().setBlock(new BlockPos(544,161,544),
                    (sequence%2==0?Blocks.STONE:Blocks.DIRT).defaultBlockState(),3));
            // An additional nearby dirty region exercises edits to an in-flight snapshot.
            OpenLightRenderer.invalidate(514,163,514,514,163,514);
            // Cross a chunk boundary every tick without leaving the room.
            mc.player.setPos(sequence%2==0?511.5:512.5,169,496.5);
            if(world().aggregates().discarded()!=baselineDiscards)throw new IllegalStateException("Ordinary edits canceled work");
        }
        if(stage==1&&world().aggregates().analytic()&&world().aggregates().cells()==0)
            throw new IllegalStateException("Empty startup calculation selected analytic lighting");
        if(stage==3) {
            var stats=OpenLightRenderer.cacheStatistics();
            if(!stats.aggregatePending()&&!stats.lightUploadsPending()&&stats.blockPending()==0&&stats.worldUpdated()==0&&stats.aggregateUpdated()==0)idle++;else idle=0;
        }
        if(ticks>3600)throw new IllegalStateException("Progress timeout stage="+stage+" "+OpenLightRenderer.cacheStatistics());
        if(stage==4&&ticks>20)mc.stop();
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if(!Boolean.getBoolean("openlights.progressSmoke")||stage<1||stage>3)return;
        var mc=Minecraft.getInstance();var aggregate=world().aggregates();var stats=OpenLightRenderer.cacheStatistics();
        if(stats.appliedSections()>1||stats.lightUploads()>1)throw new IllegalStateException("Publication exceeded configured budget");
        if(stage==1&&aggregate.revision()>=3&&aggregate.sample(new BlockPos(512,162,518))>>>24>0&&ticks>160
                &&aggregate.workerActive()&&!stats.lightUploadsPending()) {
            Screenshot.grab(mc.gameDirectory,"progress-lit-under-updates.png",mc.getMainRenderTarget(),m->{});
            LogUtils.getLogger().info("PROGRESS_LIT traffic={} revisions={} discarded={} cache={}",traffic,aggregate.revision(),aggregate.discarded(),stats);
            baselineRevision=aggregate.revision();stage=2;ticks=0;mc.getSingleplayerServer().execute(()->sources(false));
        } else if(stage==2&&aggregate.revision()>baselineRevision&&aggregate.cells()==0&&!stats.lightUploadsPending()&&ticks>160) {
            Screenshot.grab(mc.gameDirectory,"progress-removed-under-updates.png",mc.getMainRenderTarget(),m->{});
            LogUtils.getLogger().info("PROGRESS_REMOVED traffic={} revisions={} cache={}",traffic,aggregate.revision(),stats);
            stage=3;ticks=0;
        } else if(stage==3&&idle>=100) {
            LogUtils.getLogger().info("PROGRESS_SUCCESS cold start, continuous edits, bounded application, removal and 100 idle ticks; traffic={}",traffic);
            stage=4;ticks=0;
        }
    }
    private static void sources(boolean present) {
        var level=Minecraft.getInstance().getSingleplayerServer().overworld();
        for(int z=-1;z<=1;z++)for(int y=161;y<=163;y++)for(int x=-1;x<=1;x++)
            level.setBlock(new BlockPos(512+x,y,512+z),(present?Blocks.GLOWSTONE:Blocks.AIR).defaultBlockState(),3);
    }
    private static WorldLightCache world() {
        try {var field=OpenLightRenderer.class.getDeclaredField("WORLD_LIGHT");field.setAccessible(true);return (WorldLightCache)field.get(null);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
}
