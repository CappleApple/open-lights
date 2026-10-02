package com.cappleapple.openlights.qa;

import com.cappleapple.openlights.client.LightingBudgets;
import com.cappleapple.openlights.client.render.OpenLightRenderer;
import com.cappleapple.openlights.client.scene.IndirectLightCache;
import com.cappleapple.openlights.client.scene.WorldLightCache;
import com.cappleapple.openlights.config.ClientConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;

/** Remove cached bounce energy while freshly sampled probes cannot pass their age gate. */
@EventBusSubscriber(modid="openlightsqa",value=Dist.CLIENT)
public final class SourceGiHarness {
    private static boolean loading,moved,prepared;
    private static volatile boolean built;
    private static int stage,ticks;
    private static double before;
    private static final BlockPos SOURCE=new BlockPos(508,164,514);
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("openlights.sourceGiSmoke"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);mc.mouseHandler.releaseMouse();GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(!loading&&mc.screen instanceof TitleScreen title){loading=true;mc.options.renderDistance().set(6);mc.options.framerateLimit().set(60);mc.options.hideGui=true;QaBootstrap.open(title);}
        if(mc.player==null||mc.getSingleplayerServer()==null)return;
        if(!moved){moved=true;ClientConfig.ENABLED.set(true);ClientConfig.BLOCK_LIGHT_STYLE.set(ClientConfig.BlockLightStyle.OPEN_LIGHTS);ClientConfig.LIGHTING_MODE.set(ClientConfig.LightingMode.CACHED);ClientConfig.AGGREGATE_ENABLED.set(true);ClientConfig.GI_ENABLED.set(false);ClientConfig.GI_BLOCK_LIGHT.set(true);ClientConfig.GI_SPACING.set(4);ClientConfig.GI_TRACE_DISTANCE.set(8.0);ClientConfig.GI_REFRESH_TICKS.set(1);ClientConfig.GI_PROBES_PER_TICK.set(64);ClientConfig.GI_BUDGET_MILLIS.set(8.0);ClientConfig.PERIODIC_CACHE_REFRESH.set(false);LightingBudgets.RAPID.apply();
            mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());p.setGameMode(GameType.SPECTATOR);p.teleportTo(p.serverLevel(),512.5,164,503.5,0,0);});}
        ticks++;
        if(!prepared&&ticks>100&&mc.level.hasChunk(32,32)){prepared=true;mc.getSingleplayerServer().execute(()->{
            var w=mc.getSingleplayerServer().overworld();w.setDayTime(18000);w.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,mc.getSingleplayerServer());w.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,mc.getSingleplayerServer());
            for(int z=500;z<=518;z++)for(int y=160;y<=172;y++)for(int x=504;x<=520;x++)w.setBlock(new BlockPos(x,y,z),y==160?Blocks.DEEPSLATE_TILES.defaultBlockState():y==172||x==504||x==520||z==500||z==518?Blocks.DIORITE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
            w.setBlock(SOURCE,Blocks.END_ROD.defaultBlockState(),3);w.setBlock(new BlockPos(516,164,514),Blocks.END_ROD.defaultBlockState(),3);built=true;
        });}
        if(ticks>3600)throw new IllegalStateException("Source GI timeout stage="+stage+" "+OpenLightRenderer.cacheStatistics());
        if(stage==4&&ticks>20)mc.stop();
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event){
        if(!Boolean.getBoolean("openlights.sourceGiSmoke")||!built||stage>=4)return;
        var mc=Minecraft.getInstance();var a=world().aggregates();var gi=gi();var stats=OpenLightRenderer.cacheStatistics();
        if(stage==0&&ticks>160&&!stats.aggregatePending()&&!stats.lightUploadsPending()&&stats.blockPending()==0){ClientConfig.GI_ENABLED.set(true);stage=1;ticks=0;}
        else if(stage==1&&gi.grid()!=null&&gi.grid().ready()&&gi.far().ready()&&gi.updated()==0){
            before=energy();if(before<.01||gi.sourceChannels()<2)throw new IllegalStateException("No per-source GI: "+before+" columns="+gi.sourceChannels());
            capture("source-gi-before");ClientConfig.GI_REFRESH_TICKS.set(200);ClientConfig.GI_PROBES_PER_TICK.set(1);stage=2;ticks=0;
            mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().overworld().setBlock(SOURCE,Blocks.AIR.defaultBlockState(),3));
        }else if(stage==2&&mc.level.getBlockState(SOURCE).isAir()&&energy()<before*.9){
            if(gi.updated()!=0)throw new IllegalStateException("Removal waited for probe traces");
            if(energy()<=0)throw new IllegalStateException("Remaining source lost its GI");
            LogUtils.getLogger().info("SOURCE_GI_REMOVAL before={} after={} ticks={} traced={} columns={} MiB={}",before,energy(),ticks,gi.updated(),gi.sourceChannels(),gi.channelBytes()/1048576.0);
            capture("source-gi-immediate-removal");stage=3;ticks=0;
        }else if(stage==3&&ticks>30&&!stats.aggregatePending()&&!stats.lightUploadsPending()){
            capture("source-gi-removed");LogUtils.getLogger().info("SOURCE_GI_SUCCESS cached removal bypasses 200-tick probe age and retains other source; cache={}",stats);stage=4;ticks=0;
        }
    }
    private static double energy(){var g=gi();double total=0;for(var grid:new com.cappleapple.openlights.client.scene.ProbeGrid[]{g.grid(),g.far()})if(grid!=null)for(int i=0;i<grid.data.length;i++)if(i%4!=3)total+=grid.data[i];return total;}
    private static void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),m->{});}
    private static Object field(String name){try{var f=OpenLightRenderer.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static WorldLightCache world(){return (WorldLightCache)field("WORLD_LIGHT");}
    private static IndirectLightCache gi(){return (IndirectLightCache)field("INDIRECT");}
}
