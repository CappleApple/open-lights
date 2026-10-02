package com.cappleapple.openlights.qa;

import com.cappleapple.openlights.client.LightingSettingsScreen;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

/** Paired packaged-client renders and propagation assertions in a disposable test room. */
@EventBusSubscriber(modid="openlightsqa",value=Dist.CLIENT)
public final class AggregateVisualHarness {
    private static final int X=512,Z=512;
    private static final String[] NAMES={"single","cluster","disabled","restored","opaque-wall","red-glass","removed","settings"};
    private static int stage=-1,ticks,idle;
    private static boolean loading,teleported,prepared;
    private static boolean removalInjected;
    private static int discardBaseline;
    private static int maximumCaptured,maximumApplied,maximumUploads;
    private static volatile boolean built;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("openlights.aggregateSmoke"))return;
        var mc=Minecraft.getInstance();
        mc.options.pauseOnLostFocus=false;mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        mc.mouseHandler.releaseMouse();GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(!loading&&mc.screen instanceof TitleScreen title) {
            loading=true;mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);
            mc.options.framerateLimit().set(60);mc.options.hideGui=true;mc.options.fov().set(85);
            QaBootstrap.open(title);
        }
        if(mc.player==null||mc.getSingleplayerServer()==null)return;
        if(!teleported) {
            teleported=true;
            ClientConfig.ENABLED.set(true);ClientConfig.GI_ENABLED.set(false);
            ClientConfig.PERIODIC_CACHE_REFRESH.set(false);ClientConfig.LIGHTING_MODE.set(ClientConfig.LightingMode.CACHED);
            ClientConfig.BLOCK_LIGHT_STYLE.set(ClientConfig.BlockLightStyle.MINECRAFT);
            ClientConfig.AGGREGATE_ENABLED.set(true);ClientConfig.AGGREGATE_MULTIPLIER.set(3.0);
            ClientConfig.AGGREGATE_STRENGTH.set(1.0);
            ClientConfig.AGGREGATE_APPLY_SECTIONS.set(2);ClientConfig.LIGHT_UPLOAD_SECTIONS.set(1);
            var id=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{
                var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(player.serverLevel(),X+.5,176,Z-43.5,0,20);
                var world=player.serverLevel();world.setDayTime(18000);
                world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,mc.getSingleplayerServer());
                world.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,mc.getSingleplayerServer());
                world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,mc.getSingleplayerServer());
            });
        }
        ticks++;
        var metrics=OpenLightRenderer.cacheStatistics();
        maximumCaptured=Math.max(maximumCaptured,metrics.snapshotCells());
        maximumApplied=Math.max(maximumApplied,metrics.appliedSections());
        if(metrics.snapshotCells()>ClientConfig.AGGREGATE_CELLS_PER_TICK.get()||metrics.appliedSections()>ClientConfig.AGGREGATE_APPLY_SECTIONS.get())
            throw new IllegalStateException("Lighting exceeded snapshot/adoption count budget: "+metrics);
        if(!prepared&&ticks>120&&mc.player.getX()>500&&mc.level.hasChunk((X+52)>>4,(Z+52)>>4)) {
            prepared=true;ticks=0;
            mc.getSingleplayerServer().execute(()->{
                var world=mc.getSingleplayerServer().overworld();
                var pos=new BlockPos.MutableBlockPos();
                for(int z=-52;z<=52;z++)for(int x=-52;x<=52;x++) {
                    world.setBlock(pos.set(X+x,160,Z+z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
                    world.setBlock(pos.set(X+x,185,Z+z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
                    for(int y=161;y<185;y++)world.setBlock(pos.set(X+x,y,Z+z),
                            Math.abs(x)==52||Math.abs(z)==52?Blocks.SMOOTH_STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
                }
                world.setBlock(new BlockPos(X,161,Z),Blocks.GLOWSTONE.defaultBlockState(),3);
                built=true;
            });
        }
        if(built&&stage<0){stage=0;ticks=0;}
        if(stage>=0&&stage<NAMES.length&&ticks>3600)throw new IllegalStateException("Aggregate visual timeout stage="+NAMES[stage]+" cache="+OpenLightRenderer.cacheStatistics());
        if(stage>=NAMES.length&&ticks>20)mc.stop();
        if(stage==6&&!removalInjected&&OpenLightRenderer.cacheStatistics().aggregateWorker()) {
            removalInjected=true;discardBaseline=metrics.discardedJobs();cluster(false);
        }
        if(stage==1&&ticks>160) {
            var cache=OpenLightRenderer.cacheStatistics();
            if(!cache.aggregatePending()&&!cache.lightUploadsPending()&&cache.blockPending()==0&&cache.aggregateCells()>0) {
                if(cache.worldUpdated()!=0||cache.aggregateUpdated()!=0||cache.colorUpdated()!=0||cache.lightUploads()!=0)idle=0;
                else idle++;
            }else idle=0;
        }
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if(!Boolean.getBoolean("openlights.aggregateSmoke"))return;
        var mc=Minecraft.getInstance();var cache=OpenLightRenderer.cacheStatistics();
        maximumUploads=Math.max(maximumUploads,cache.lightUploads());
        if(teleported&&cache.lightUploads()>ClientConfig.LIGHT_UPLOAD_SECTIONS.get())throw new IllegalStateException("Lighting exceeded per-frame upload count budget: "+cache);
        if(stage<0||stage>=NAMES.length||ticks<160)return;
        if(stage!=7&&(cache.aggregatePending()||cache.blockPending()!=0||cache.lightUploadsPending()))return;
        if((stage==2||stage==3)&&(cache.giProbes()<125||cache.farGiProbes()<729||cache.giUpdated()!=0))return;
        if(!cache.replacement())throw new IllegalStateException("Aggregate test lost cached rendering");
        int far=sample(0,162,30),peak=sample(0,161,0);
        if(stage==1&&idle<100)return;
        if(stage==0||stage==2||stage==4||stage==6) {
            if((far>>>24)!=0)throw new IllegalStateException("Unexpected distant aggregate light at "+NAMES[stage]+": "+Integer.toHexString(far));
        }
        if(stage==1||stage==3||stage==5) {
            if((far>>>24)<20) return;
            if(peak>>>24!=255)throw new IllegalStateException("Cluster changed source peak");
        }
        if(stage==5&&((far>>16&255)<(far>>8&255)*2||(far>>16&255)<(far&255)*2))throw new IllegalStateException("Extended light did not pick up red glass: "+Integer.toHexString(far));
        double bounce=energy();
        if(stage==2&&bounce>0.000001)throw new IllegalStateException("Native light unexpectedly reaches distant GI fixture: "+bounce);
        if(stage==3&&bounce<0.000001)throw new IllegalStateException("Aggregate field did not contribute to distant GI");
        if(stage==6&&(!removalInjected||cache.discardedJobs()!=discardBaseline))throw new IllegalStateException("Source removal interrupted the active snapshot instead of converging through a follow-up");
        String worker=worker();
        if(stage==1&&!worker.equals("OpenLights-lighting"))throw new IllegalStateException("Propagation did not run on lighting worker: "+worker);
        Screenshot.grab(mc.gameDirectory,"aggregate-"+NAMES[stage]+".png",mc.getMainRenderTarget(),m->{});
        if(stage==7) {
            double capture=ClientConfig.AGGREGATE_BUDGET_MILLIS.get(),upload=ClientConfig.LIGHT_UPLOAD_MILLIS.get();
            int cells=ClientConfig.AGGREGATE_CELLS_PER_TICK.get();
            for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button
                    &&(button.getMessage().getString().startsWith("Snapshot capture budget")||button.getMessage().getString().startsWith("Lighting upload budget")))button.onPress();
            if(ClientConfig.AGGREGATE_BUDGET_MILLIS.get()==capture||ClientConfig.LIGHT_UPLOAD_MILLIS.get()==upload)
                throw new IllegalStateException("Visible lighting budget buttons did not change configuration");
            ClientConfig.AGGREGATE_BUDGET_MILLIS.set(capture);ClientConfig.AGGREGATE_CELLS_PER_TICK.set(cells);
            ClientConfig.LIGHT_UPLOAD_MILLIS.set(upload);ClientConfig.AGGREGATE_APPLY_SECTIONS.set(16);ClientConfig.LIGHT_UPLOAD_SECTIONS.set(8);
            ClientConfig.AGGREGATE_BUDGET_MILLIS.save();
            LogUtils.getLogger().info("ASYNC_BUDGET_SUCCESS maxSnapshotCells={} maxAppliedSections={} maxUploadsPerFrame={} buttonsChanged=true",maximumCaptured,maximumApplied,maximumUploads);
        }
        LogUtils.getLogger().info("AGGREGATE_CAPTURE {} far={} peak={} idle={} bounce={} worker={} cache={}",NAMES[stage],Integer.toHexString(far),Integer.toHexString(peak),idle,bounce,worker,cache);
        ticks=0;stage++;
        switch(stage) {
            case 1->cluster(true);
            case 2->{ClientConfig.AGGREGATE_ENABLED.set(false);ClientConfig.GI_ENABLED.set(true);ClientConfig.GI_BLOCK_LIGHT.set(true);}
            case 3->ClientConfig.AGGREGATE_ENABLED.set(true);
            case 4->{ClientConfig.GI_ENABLED.set(false);wall(Blocks.SMOOTH_STONE.defaultBlockState());}
            case 5->wall(Blocks.RED_STAINED_GLASS.defaultBlockState());
            case 6->ClientConfig.AGGREGATE_STRENGTH.set(.9);
            case 7->{ClientConfig.AGGREGATE_STRENGTH.set(1.0);mc.setScreen(new LightingSettingsScreen(null));}
            default->LogUtils.getLogger().info("AGGREGATE_SUCCESS single/cluster, bounded peak, 30-block reach, 100 idle ticks, disable, wall, glass, in-flight removal and settings");
        }
    }
    private static void cluster(boolean enabled) {
        var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{
            var world=mc.getSingleplayerServer().overworld();
            for(int z=-1;z<=1;z++)for(int y=161;y<=163;y++)for(int x=-1;x<=1;x++)
                world.setBlock(new BlockPos(X+x,y,Z+z),enabled?Blocks.GLOWSTONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
        });
    }
    private static void wall(BlockState block) {
        var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{
            var world=mc.getSingleplayerServer().overworld();
            for(int x=-51;x<=51;x++)for(int y=161;y<185;y++)world.setBlock(new BlockPos(X+x,y,Z+8),block,3);
        });
    }
    private static int sample(int x,int y,int z) {
        try {
            var field=OpenLightRenderer.class.getDeclaredField("WORLD_LIGHT");field.setAccessible(true);
            return ((WorldLightCache)field.get(null)).aggregates().sample(new BlockPos(X+x,y,Z+z));
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    private static double energy() {
        try {
            var field=OpenLightRenderer.class.getDeclaredField("INDIRECT");field.setAccessible(true);
            var grid=((com.cappleapple.openlights.client.scene.IndirectLightCache)field.get(null)).grid();
            double result=0;
            if(grid!=null)for(int i=0;i<grid.data.length;i+=4)result+=grid.data[i]+grid.data[i+1]+grid.data[i+2];
            return result;
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    private static String worker() {
        try{var field=OpenLightRenderer.class.getDeclaredField("WORLD_LIGHT");field.setAccessible(true);return ((WorldLightCache)field.get(null)).aggregates().workerThread();}
        catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
}
