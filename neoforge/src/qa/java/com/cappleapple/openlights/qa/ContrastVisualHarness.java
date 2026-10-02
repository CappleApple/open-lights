package com.cappleapple.openlights.qa;

import com.cappleapple.openlights.client.LightingBudgets;
import com.cappleapple.openlights.client.render.OpenLightRenderer;
import com.cappleapple.openlights.client.scene.WorldLightCache;
import com.cappleapple.openlights.config.ClientConfig;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;

/** Paired packaged renders of texture contrast and competing emitter spectra. */
@EventBusSubscriber(modid="openlightsqa",value=Dist.CLIENT)
public final class ContrastVisualHarness {
    private static boolean loading,moved,prepared,installed;
    private static volatile boolean ready,built;
    private static int stage,ticks,idle;
    private static Path textures;
    private static List<String> original;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("openlights.contrastSmoke"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        mc.mouseHandler.releaseMouse();GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(!installed&&mc.screen instanceof TitleScreen) {
            installed=true;original=new ArrayList<>(mc.getResourcePackRepository().getSelectedIds());pack(false);
        }
        if(!loading&&ready&&mc.screen instanceof TitleScreen title) {
            loading=true;mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);
            mc.options.framerateLimit().set(60);mc.options.hideGui=true;mc.options.fov().set(65);QaBootstrap.open(title);
        }
        if(mc.player==null||mc.getSingleplayerServer()==null)return;
        if(!moved) {
            moved=true;ClientConfig.ENABLED.set(true);ClientConfig.GI_ENABLED.set(false);
            ClientConfig.BLOCK_LIGHT_STYLE.set(ClientConfig.BlockLightStyle.OPEN_LIGHTS);
            ClientConfig.LIGHTING_MODE.set(ClientConfig.LightingMode.CACHED);ClientConfig.AGGREGATE_ENABLED.set(true);
            ClientConfig.INTENSITY_MULTIPLIER.set(2.0);ClientConfig.BLOOM_STRENGTH.set(0.0);
            ClientConfig.PERIODIC_CACHE_REFRESH.set(false);LightingBudgets.RAPID.apply();
            var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);p.setGameMode(GameType.SPECTATOR);
                p.teleportTo(p.serverLevel(),512.5,164,505.5,0,0);
            });
        }
        ticks++;
        if(!prepared&&ticks>100&&mc.level.hasChunk(33,33)) {
            prepared=true;mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();level.setDayTime(18000);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,mc.getSingleplayerServer());
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,mc.getSingleplayerServer());
                for(int z=500;z<=525;z++)for(int x=498;x<=526;x++)for(int y=160;y<=172;y++) {
                    var state=y==160?Blocks.POLISHED_DEEPSLATE.defaultBlockState():y==172||x==498||x==526||z==500||z==525?Blocks.SMOOTH_QUARTZ.defaultBlockState():Blocks.AIR.defaultBlockState();
                    level.setBlock(new BlockPos(x,y,z),state,3);
                }
                for(int x=499;x<526;x++)for(int y=161;y<172;y++) {
                    var block=y<=162?Blocks.DIORITE:y==163?Blocks.DEEPSLATE_TILES:Blocks.WHITE_CONCRETE;
                    level.setBlock(new BlockPos(x,y,524),block.defaultBlockState(),3);
                }
                level.setBlock(new BlockPos(509,166,522),Blocks.SEA_LANTERN.defaultBlockState(),3);
                level.setBlock(new BlockPos(515,166,522),Blocks.GLOWSTONE.defaultBlockState(),3);built=true;
            });
        }
        if(stage==2) {
            var stats=OpenLightRenderer.cacheStatistics();
            if(!stats.aggregatePending()&&!stats.lightUploadsPending()&&stats.blockPending()==0&&stats.aggregateUpdated()==0&&stats.worldUpdated()==0)idle++;else idle=0;
        }
        if(ticks>3600)throw new IllegalStateException("Contrast fixture timed out: "+OpenLightRenderer.cacheStatistics());
        if(stage==3&&ticks>20)mc.stop();
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if(!Boolean.getBoolean("openlights.contrastSmoke")||!built||!ready||stage>2||ticks<160)return;
        var stats=OpenLightRenderer.cacheStatistics();
        if(stats.aggregatePending()||stats.lightUploadsPending()||stats.blockPending()!=0)return;
        if(!world().aggregates().analytic())throw new IllegalStateException("Open Lights style not active");
        if(stage<2) {
            var field=world().aggregates();
            int midpoint=field.sample(new BlockPos(512,166,522));
            if((midpoint>>>24)==0)throw new IllegalStateException("Fixture sources do not illuminate their overlap");
            if(stage==1&&!Boolean.getBoolean("openlights.contrastBaseline")) {
                if((midpoint&255)<60||(midpoint>>16&255)<60)throw new IllegalStateException("Overlap lacks both emitter colors: "+Integer.toHexString(midpoint));
            }
            Screenshot.grab(Minecraft.getInstance().gameDirectory,"contrast-"+(stage==0?"warm-white":"red-blue")+".png",Minecraft.getInstance().getMainRenderTarget(),m->{});
            LogUtils.getLogger().info("CONTRAST_CAPTURE stage={} midpoint={} workerMillis={} cache={}",stage,Integer.toHexString(midpoint),field.workerMillis(),stats);
            stage++;ticks=0;if(stage==1)pack(true);
        } else if(idle>=100) {
            LogUtils.getLogger().info("CONTRAST_SUCCESS texture fixture, mixed color midpoint and 100 idle ticks; baseline={}",Boolean.getBoolean("openlights.contrastBaseline"));
            var mc=Minecraft.getInstance();mc.getResourcePackRepository().setSelected(original);
            mc.reloadResourcePacks();stage=3;ticks=0;
        }
    }
    private static void pack(boolean colored) {
        var mc=Minecraft.getInstance();var root=mc.gameDirectory.toPath().resolve("resourcepacks/openlights_contrast_qa");
        textures=root.resolve("assets/minecraft/textures/block");
        try {
            Files.createDirectories(textures);Files.writeString(root.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":34,\"description\":\"Disposable texture contrast QA\"}}");
            try(var image=new NativeImage(16,16,false)) {
                for(int y=0;y<16;y++)for(int x=0;x<16;x++) {
                    int value=((x/4+y/4)%2==0)?210:70;image.setPixelRGBA(x,y,0xff000000|value<<16|value<<8|value);
                }
                image.writeToFile(textures.resolve("white_concrete.png"));
                image.fillRect(0,0,16,16,colored?0xffff2020:0xffffffff);image.writeToFile(textures.resolve("sea_lantern_e.png"));
                image.fillRect(0,0,16,16,colored?0xff2020ff:0xff20a0ff);image.writeToFile(textures.resolve("glowstone_e.png"));
            }
            mc.getResourcePackRepository().reload();var selected=new ArrayList<>(original);selected.add("file/openlights_contrast_qa");
            mc.getResourcePackRepository().setSelected(selected);ready=false;mc.reloadResourcePacks().thenRun(()->ready=true);
        }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
    }
    private static WorldLightCache world() {
        try{var field=OpenLightRenderer.class.getDeclaredField("WORLD_LIGHT");field.setAccessible(true);return (WorldLightCache)field.get(null);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
}
