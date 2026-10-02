package com.cappleapple.openlights.qa;

import com.cappleapple.openlights.client.LightingBudgets;
import com.cappleapple.openlights.client.LightingSettingsScreen;
import com.cappleapple.openlights.client.render.OpenLightRenderer;
import com.cappleapple.openlights.client.scene.WorldLightCache;
import com.cappleapple.openlights.config.ClientConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
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

/** Packaged dense-emitter timing and rendering, with the same fixture on the old JAR. */
@EventBusSubscriber(modid="openlightsqa",value=Dist.CLIENT)
public final class DenseLightsHarness {
    private static boolean loading,moved,prepared;
    private static volatile boolean built;
    private static int stage,ticks,idle,count;
    private static long start,revision;
    private static double gpu,cpu;
    private static int frames;
    private static double giGpu;
    private static int giFrames;
    private static final BlockPos EDIT=new BlockPos(512,169,531);
    private static boolean baseline(){return Boolean.getBoolean("openlights.denseBaseline");}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("openlights.denseSmoke"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);mc.mouseHandler.releaseMouse();GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(!loading&&mc.screen instanceof TitleScreen title){loading=true;mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(60);mc.options.hideGui=true;mc.options.fov().set(70);QaBootstrap.open(title);}
        if(mc.player==null||mc.getSingleplayerServer()==null)return;
        if(!moved){moved=true;ClientConfig.ENABLED.set(true);ClientConfig.BLOCK_LIGHT_STYLE.set(ClientConfig.BlockLightStyle.OPEN_LIGHTS);ClientConfig.LIGHTING_MODE.set(ClientConfig.LightingMode.CACHED);ClientConfig.AGGREGATE_ENABLED.set(true);ClientConfig.GI_ENABLED.set(false);ClientConfig.PERIODIC_CACHE_REFRESH.set(false);ClientConfig.BLOOM_STRENGTH.set(0.0);ClientConfig.INTENSITY_MULTIPLIER.set(1.0);if(!baseline()){ClientConfig.BLOCK_LIGHT_EXPOSURE.set(2.0);ClientConfig.SOURCE_CACHE_MIB.set(128);}LightingBudgets.BALANCED.apply();
            mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());p.setGameMode(GameType.SPECTATOR);p.teleportTo(p.serverLevel(),512.5,165,497.5,0,4);});}
        ticks++;
        if(!prepared&&ticks>100&&mc.level.hasChunk(33,33)){prepared=true;mc.getSingleplayerServer().execute(()->{
            var world=mc.getSingleplayerServer().overworld();world.setDayTime(18000);world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,mc.getSingleplayerServer());world.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,mc.getSingleplayerServer());
            for(int z=492;z<=532;z++)for(int y=160;y<=172;y++)for(int x=488;x<=536;x++)world.setBlock(new BlockPos(x,y,z),y==160?Blocks.DEEPSLATE_TILES.defaultBlockState():y==172||x==488||x==536||z==492||z==532?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
            int step=Boolean.getBoolean("openlights.denseBars")?1:2;
            for(int y=step==1?167:163;y<=169;y+=2){
                for(int x=490;x<=534;x+=step)for(int z:new int[]{493,531}){world.setBlock(new BlockPos(x,y,z),Blocks.END_ROD.defaultBlockState(),3);count++;}
                for(int z=495;z<=529;z+=step)for(int x:new int[]{489,535}){world.setBlock(new BlockPos(x,y,z),Blocks.END_ROD.defaultBlockState(),3);count++;}
            }built=true;
        });}
        if(stage==0&&built&&ticks>220&&mc.level.getBlockState(EDIT).is(Blocks.END_ROD)){world().clear();stage=1;ticks=0;start=System.nanoTime();}
        if(stage==2&&mc.level.getBlockState(EDIT).isAir()){stage=3;start=System.nanoTime();ticks=idle=0;}
        if(stage==4&&mc.level.getBlockState(EDIT).is(Blocks.END_ROD)){stage=5;start=System.nanoTime();ticks=idle=0;}
        if(stage>=1&&stage<=7){var s=OpenLightRenderer.cacheStatistics();if(!s.aggregatePending()&&!s.lightUploadsPending()&&s.blockPending()==0&&s.worldUpdated()==0&&s.aggregateUpdated()==0)idle++;else idle=0;}
        if(ticks>8000)throw new IllegalStateException("Dense lighting timeout stage="+stage+" "+OpenLightRenderer.cacheStatistics());
        if(stage==9&&ticks>20)mc.stop();
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event){
        if(!Boolean.getBoolean("openlights.denseSmoke")||stage<1||stage>=9)return;
        var mc=Minecraft.getInstance();var a=world().aggregates();var s=OpenLightRenderer.cacheStatistics();
        if(stage==6){var timing=OpenLightRenderer.statistics();gpu+=timing.gpuMillis();cpu+=timing.cpuMillis();frames++;}
        if(stage==7&&ticks>100&&s.giUpdated()==0){giGpu+=OpenLightRenderer.statistics().gpuMillis();giFrames++;}
        if(stage==1&&idle>=10){
            if(!a.analytic()||a.cells()==0||count<300)throw new IllegalStateException("Dense source fixture missing");
            report("cold");capture("dense-lit");stage=2;ticks=idle=0;revision=a.revision();edit(false);
        }else if(stage==3&&a.revision()>revision&&!s.aggregatePending()&&!s.lightUploadsPending()){
            report("remove");capture("dense-removed");stage=4;ticks=idle=0;revision=a.revision();edit(true);
        }else if(stage==5&&a.revision()>revision&&!s.aggregatePending()&&!s.lightUploadsPending()){
            report("place");capture("dense-replaced");stage=6;ticks=idle=0;
        }else if(stage==6&&idle>=100){
            LogUtils.getLogger().info("DENSE_IDLE 100 ticks frames={} meanRendererGpuMs={} meanRendererCpuMs={} cache={}",frames,gpu/frames,cpu/frames,s);ClientConfig.GI_ENABLED.set(true);ClientConfig.GI_PROBES_PER_TICK.set(64);ClientConfig.GI_BUDGET_MILLIS.set(8.0);stage=7;ticks=idle=0;
        }else if(stage==7&&ticks>300&&idle>=20){
            capture("dense-gi");LogUtils.getLogger().info("DENSE_GI frames={} meanRendererGpuMs={} cache={}",giFrames,giGpu/giFrames,s);
            if(!baseline()){mc.options.hideGui=false;mc.setScreen(new LightingSettingsScreen(null));button("Block exposure").onPress();if(ClientConfig.BLOCK_LIGHT_EXPOSURE.get()!=3.0)throw new IllegalStateException("Exposure control failed");button("Source cache").onPress();if(ClientConfig.SOURCE_CACHE_MIB.get()!=256)throw new IllegalStateException("Cache control failed");}
            stage=8;ticks=0;
        }else if(stage==8&&ticks>20){
            if(!baseline())capture("dense-settings");LogUtils.getLogger().info("DENSE_SUCCESS sources={} baseline={}; cold, placement, removal, idle, GI and menu",count,baseline());stage=9;ticks=0;
        }
    }
    private static void report(String name){var a=world().aggregates();LogUtils.getLogger().info("DENSE_TIMING {} elapsedMs={} workerMs={} sources={} channels={} traced={} reused={} recomposed={} channelMiB={} cache={}",name,(System.nanoTime()-start)/1e6,a.workerMillis(),count,metric("sourceChannels"),metric("tracedChannels"),metric("reusedChannels"),metric("recomposedCells"),metric("channelBytes")/1048576.0,OpenLightRenderer.cacheStatistics());}
    private static long metric(String method){try{return ((Number)world().aggregates().getClass().getMethod(method).invoke(world().aggregates())).longValue();}catch(ReflectiveOperationException absent){return -1;}}
    private static void edit(boolean on){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().overworld().setBlock(EDIT,(on?Blocks.END_ROD:Blocks.AIR).defaultBlockState(),3));}
    private static void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),m->{});}
    private static Button button(String name){return Minecraft.getInstance().screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().startsWith(name)).map(c->(Button)c).findFirst().orElseThrow();}
    private static WorldLightCache world(){try{var f=OpenLightRenderer.class.getDeclaredField("WORLD_LIGHT");f.setAccessible(true);return (WorldLightCache)f.get(null);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
}
