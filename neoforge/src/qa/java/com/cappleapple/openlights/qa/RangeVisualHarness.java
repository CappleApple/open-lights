package com.cappleapple.openlights.qa;

import com.cappleapple.openlights.api.client.*;
import com.cappleapple.openlights.client.LightingSettingsScreen;
import com.cappleapple.openlights.client.render.OpenLightRenderer;
import com.cappleapple.openlights.config.ClientConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.options.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;

/** Distant direct lighting, shadow, camera-boundary and actual video-menu checks. Never packaged. */
@EventBusSubscriber(modid="openlightsqa", value=Dist.CLIENT)
public final class RangeVisualHarness {
    private static final String[] NAMES={"off","far-lit","far-unshadowed","limited","restored","boundary","far-again","video-settings","light-settings"};
    private static final List<LightHandle> HANDLES=new ArrayList<>();
    private static boolean loading, warming, prepared;
    private static volatile boolean ready;
    private static int stage=-1,ticks,warmTicks,shadowPasses;
    private static Screen video;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("openlights.rangeSmoke")) return;
        var mc=Minecraft.getInstance();
        mc.options.pauseOnLostFocus=false; mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        mc.mouseHandler.releaseMouse(); GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if (!loading && mc.screen instanceof TitleScreen title) {
            loading=true; mc.options.renderDistance().set(6); mc.options.framerateLimit().set(60);
            mc.options.fov().set(55); mc.options.hideGui=true;
            QaBootstrap.open(title);
        }
        if (!prepared && mc.player!=null && mc.getSingleplayerServer()!=null && mc.screen==null) {
            if (!warming) { warming=true; teleport(0.5,174,48.5,30); return; }
            if (++warmTicks<80) return;
            prepared=true;
            ClientConfig.GI_ENABLED.set(false); ClientConfig.LIGHT_RENDER_DISTANCE.set(0);
            ClientConfig.LIGHTING_MODE.set(ClientConfig.LightingMode.CACHED);
            ClientConfig.VOLUMETRIC_STEPS.set(12); ClientConfig.MAX_LIGHTS.set(8); ClientConfig.MAX_SHADOW_LIGHTS.set(4);
            mc.getSingleplayerServer().execute(() -> {
                var world=mc.getSingleplayerServer().overworld();
                world.setDayTime(18000); world.setWeatherParameters(100000,0,false,false);
                world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,mc.getSingleplayerServer());
                world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,mc.getSingleplayerServer());
                // Remove earlier smoke fixtures between the camera and the distant test wall.
                for(int x=-14;x<=14;x++) for(int z=-4;z<44;z++) for(int y=160;y<=172;y++)
                    world.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
                for(int x=-14;x<=14;x++) for(int z=44;z<=76;z++) for(int y=160;y<=172;y++)
                    world.setBlock(new BlockPos(x,y,z),(y==160 || z==72&&y<=169)?Blocks.WHITE_CONCRETE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
                for(int x=-9;x<=-7;x++) for(int y=161;y<=166;y++) world.setBlock(new BlockPos(x,y,65),Blocks.STONE.defaultBlockState(),3);
                ready=true;
            });
            teleport(0.5,166,0.5,2); stage=0; ticks=0;
        }
        if(ready) ticks++;
        if(stage>=NAMES.length && ticks>20) mc.stop();
        if(stage>=0 && stage<NAMES.length && ticks>800) throw new IllegalStateException("Range visual timeout "+stage);
    }
    private static void teleport(double x,double y,double z,float pitch) {
        var mc=Minecraft.getInstance(); var id=mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
            player.setGameMode(GameType.CREATIVE); player.getInventory().clearContent();
            player.teleportTo(player.serverLevel(),x,y,z,0,pitch);
            player.getAbilities().flying=true; player.onUpdateAbilities();
        });
    }
    private static void lights(boolean shadows) {
        List<LightDefinition> definitions=List.of(
                new LightDefinition.Point(new Vec3(-8,164,60),new Vec3(.15,.4,1),20,16,shadows,.4f),
                new LightDefinition.Spot(new Vec3(0,165,60),new Vec3(0,-.2,1),new Vec3(0,1,0),new Vec3(1,.25,.1),20,16,100,65,shadows,.4f),
                new LightDefinition.Area(new Vec3(8,164,60),new Vec3(0,0,1),new Vec3(0,1,0),new Vec3(.15,1,.25),20,16,2,2,120,shadows,.4f));
        for(int i=0;i<definitions.size();i++) {
            if(HANDLES.size()<=i) HANDLES.add(OpenLightsApi.create(ResourceLocation.fromNamespaceAndPath("openlights","range_smoke"),definitions.get(i)));
            else HANDLES.get(i).update(definitions.get(i));
        }
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if(!Boolean.getBoolean("openlights.rangeSmoke") || !ready || stage<0 || stage>=NAMES.length) return;
        shadowPasses+=OpenLightRenderer.statistics().shadowPasses();
        if(ticks<(stage>=7?15:140)) return;
        var mc=Minecraft.getInstance();
        if(stage<7 && mc.screen!=null) return;
        if(stage<7) {
            int expected=stage==0||stage==3?0:3;
            if(OpenLightRenderer.frameLights().size()!=expected) throw new IllegalStateException("Distant light selection at "+NAMES[stage]+": "+OpenLightRenderer.frameLights());
            for(var light:OpenLightRenderer.frameLights().values()) if(light.range()!=16 || light.intensity()!=20)
                throw new IllegalStateException("Camera distance changed source reach/brightness");
            if(stage==1 && (shadowPasses==0 || OpenLightRenderer.statistics().triangles()==0)) throw new IllegalStateException("Distant shadows were not rendered");
            if(stage==1) {
                var camera=mc.gameRenderer.getMainCamera().getPosition();
                if(OpenLightRenderer.frameLights().values().stream().anyMatch(light -> light.position().distanceTo(camera)<=48))
                    throw new IllegalStateException("Fixture is not beyond old camera range");
                assertDistantGeometry();
            }
        }
        Screenshot.grab(mc.gameDirectory,"range-"+NAMES[stage]+".png",mc.getMainRenderTarget(),m -> {});
        LogUtils.getLogger().info("RANGE_CAPTURE {} lights={} shadows={} stats={}",NAMES[stage],OpenLightRenderer.frameLights().size(),shadowPasses,OpenLightRenderer.statistics());
        stage++; ticks=0; shadowPasses=0;
        switch(stage) {
            case 1 -> lights(true);
            case 2 -> lights(false);
            case 3 -> ClientConfig.LIGHT_RENDER_DISTANCE.set(2);
            case 4 -> {ClientConfig.LIGHT_RENDER_DISTANCE.set(0); lights(true);}
            case 5 -> teleport(0.5,166,28.5,2);
            case 6 -> teleport(0.5,166,0.5,2);
            case 7 -> {
                var parent=new OptionsScreen(null,mc.options); mc.setScreen(parent);
                video=new VideoSettingsScreen(parent,mc,mc.options); mc.setScreen(video);
            }
            case 8 -> {
                var entry=video.children().stream().filter(child -> child instanceof Button button && button.getMessage().equals(Component.translatable("screen.openlights.title"))).findFirst().orElseThrow();
                ((Button)entry).onPress();
                if(!(mc.screen instanceof LightingSettingsScreen)) throw new IllegalStateException("Video settings did not open lighting controls");
                Button distance=(Button)mc.screen.children().stream().filter(child->child instanceof Button b&&b.getMessage().getString().startsWith("Lighting distance:")).findFirst().orElseThrow(); distance.onPress();
                if(ClientConfig.LIGHT_RENDER_DISTANCE.get()!=2) throw new IllegalStateException("Distance control failed");
                for(int i=0;i<9;i++) distance.onPress();
                if(ClientConfig.LIGHT_RENDER_DISTANCE.get()!=0) throw new IllegalStateException("Distance control did not restore render-distance default");
            }
            default -> {
                mc.screen.onClose(); if(mc.screen!=video) throw new IllegalStateException("Settings did not return to video menu");
                mc.setScreen(null); HANDLES.forEach(LightHandle::close); ClientConfig.GI_ENABLED.set(true);
                LogUtils.getLogger().info("RANGE_SUCCESS distant Point/Spot/Area, source-centered shadows, optional distance limit, camera crossing and video controls passed; inspect captures");
            }
        }
    }
    private static void assertDistantGeometry() {
        try {
            var field=OpenLightRenderer.class.getDeclaredField("SCENE");field.setAccessible(true);Object scene=field.get(null);
            var snapshotField=scene.getClass().getDeclaredField("snapshot");snapshotField.setAccessible(true);
            var snapshot=(com.cappleapple.openlights.client.scene.SceneSnapshot)snapshotField.get(scene);
            float[] vertices=snapshot.opaqueTriangles(); boolean far=false;
            for(int i=2;i<vertices.length;i+=3) if(vertices[i]+snapshot.origin().z>=72) far=true;
            if(!far) throw new IllegalStateException("Shadow mesh omitted distant wall");
        }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
}
