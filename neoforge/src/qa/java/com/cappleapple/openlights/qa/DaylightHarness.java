package com.cappleapple.openlights.qa;
import com.cappleapple.openlights.client.render.OpenLightRenderer;
import com.cappleapple.openlights.config.ClientConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
@EventBusSubscriber(modid="openlightsqa",value=Dist.CLIENT)
public final class DaylightHarness {
    private static boolean loading,started;private static int ticks,stage;
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!Boolean.getBoolean("openlights.daylightSmoke"))return;var mc=Minecraft.getInstance();mc.mouseHandler.releaseMouse();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);mc.options.pauseOnLostFocus=false;
        if(!loading&&mc.screen instanceof TitleScreen title){loading=true;QaBootstrap.open(title);}
        if(!started&&mc.player!=null&&mc.screen==null){started=true;ClientConfig.ENABLED.set(false);mc.options.hideGui=true;
            var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);var w=p.serverLevel();w.setDayTime(6000);p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(w,1155.5,-48,124.5,0,40);});}
        if(started)ticks++;if(stage==4&&ticks>30)mc.stop();
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post e){
        if(!Boolean.getBoolean("openlights.daylightSmoke")||!started||ticks<300||stage>=4)return;var mc=Minecraft.getInstance();if(mc.screen!=null)return;
        if(stage>=2)assertSky();
        Screenshot.grab(mc.gameDirectory,"daylight-"+stage+".png",mc.getMainRenderTarget(),m->{});
        LogUtils.getLogger().info("DAYLIGHT_CAPTURE {} position={} cache={}",stage,mc.player.position(),OpenLightRenderer.cacheStatistics());ticks=0;stage++;
        switch(stage){case 1->{ClientConfig.ENABLED.set(true);ClientConfig.GI_ENABLED.set(false);ClientConfig.WORLD_GRID_SIZE.set(9);ClientConfig.WORLD_SAMPLES_PER_TICK.set(128);}
            case 2->ClientConfig.WORLD_SAMPLES_PER_TICK.set(32768);
            case 3->{mc.options.entityShadows().set(false);}
            default->{ClientConfig.WORLD_SAMPLES_PER_TICK.set(4096);ClientConfig.WORLD_GRID_SIZE.set(33);mc.options.entityShadows().set(true);LogUtils.getLogger().info("DAYLIGHT_SUCCESS");}}
    }
    private static void assertSky(){try{
        var field=OpenLightRenderer.class.getDeclaredField("WORLD_LIGHT");field.setAccessible(true);
        var grid=((com.cappleapple.openlights.client.scene.WorldLightCache)field.get(null)).grid();
        for(int x=-32;x<=32;x+=32)for(int z=-32;z<=32;z+=32){
            double fx=(1155.5+x-grid.minimumX())/grid.spacing,fy=(-59.45-grid.minimumY())/grid.spacing,fz=(124.5+z-grid.minimumZ())/grid.spacing;
            int ix=(int)Math.floor(fx),iy=(int)Math.floor(fy),iz=(int)Math.floor(fz);fx-=ix;fy-=iy;fz-=iz;
            double sky=0,exposed=0;
            for(int dx=0;dx<=1;dx++)for(int dy=0;dy<=1;dy++)for(int dz=0;dz<=1;dz++){
                int cell=(ix+dx+grid.size*(iy+dy+grid.size*(iz+dz)))*4;
                double w=(dx==0?1-fx:fx)*(dy==0?1-fy:fy)*(dz==0?1-fz:fz);sky+=grid.data[cell+1]*w;exposed+=grid.data[cell+2]*w;
            }
            if(exposed<=0||sky/exposed<.99)throw new IllegalStateException("Exposed low-altitude skylight darkened "+x+","+z+" level="+sky/exposed);
        }
    }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}

}
