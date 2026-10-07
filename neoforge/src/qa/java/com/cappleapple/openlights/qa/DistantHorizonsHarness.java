package com.cappleapple.openlights.qa;

import com.cappleapple.openlights.api.client.LightDefinition;
import com.cappleapple.openlights.api.client.LightHandle;
import com.cappleapple.openlights.api.client.OpenLightsApi;
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
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.concurrent.CompletableFuture;

/** Packaged DH integration fixture; far geometry is 384 blocks away at four chunks. */
@EventBusSubscriber(modid="openlightsqa", value=Dist.CLIENT)
public final class DistantHorizonsHarness {
    private static final ResourceLocation OWNER = ResourceLocation.fromNamespaceAndPath("openlightsqa", "distant_horizons");
    private static boolean loading, configured, started;
    private static volatile boolean built;
    private static int stage, ticks, total;
    private static Object graphics, renderProxy;
    private static LightHandle light, beam;
    private static CompletableFuture<Void> reload;
    private static TextureData nativeColor;
    private static volatile boolean occluderBuilt;
    private static boolean droppedOccluder;
    private static TextureData occlusionColor;
    private static final String[] NAMES = {
            "dh-native", "dh-cached-no-api", "dh-cached-beam", "dh-additive", "dh-reloaded", "dh-resized",
            "dh-disabled", "dh-restored", "dh-night-native", "dh-night-cached-no-api"
    };

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("openlights.dhSmoke")) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        mc.mouseHandler.releaseMouse();
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(!loading && mc.screen instanceof TitleScreen title) {
            loading = true;
            configureDh();
            mc.options.renderDistance().set(4);
            mc.options.simulationDistance().set(5);
            mc.options.framerateLimit().set(60);
            mc.options.fov().set(60);
            mc.options.hideGui = true;
            QaBootstrap.open(title);
        }
        if(mc.player == null || mc.getSingleplayerServer() == null || mc.screen != null) return;
        if(!configured) {
            configured = true;
            ClientConfig.ENABLED.set(false);
            ClientConfig.GI_ENABLED.set(false);
            ClientConfig.LIGHTING_MODE.set(ClientConfig.LightingMode.CACHED);
            ClientConfig.BLOCK_LIGHT_STYLE.set(ClientConfig.BlockLightStyle.OPEN_LIGHTS);
            ClientConfig.BLOOM_STRENGTH.set(0.0);
            LightingBudgets.RAPID.apply();
            pose(0.5, 368.5);
        }
        ticks++;
        total++;
        if(!started && ticks > 120 && mc.level.hasChunk(0,24)) {
            started = true;
            mc.getSingleplayerServer().execute(() -> {
                var level = mc.getSingleplayerServer().overworld();
                level.setDayTime(6000);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,mc.getSingleplayerServer());
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,mc.getSingleplayerServer());
                // Preload every modified distant chunk before writing the skyline.
                for(int cx=-4;cx<=4;cx++) for(int cz=23;cz<=25;cz++) level.getChunk(cx,cz);
                for(int x=-60;x<=60;x++) for(int z=384;z<=388;z++) for(int y=64;y<=134;y++) {
                    var block = x<-20 ? Blocks.RED_TERRACOTTA : x>20 ? Blocks.BLUE_TERRACOTTA : Blocks.SMOOTH_QUARTZ;
                    level.setBlock(new BlockPos(x,y,z),block.defaultBlockState(),3);
                }
                for(int x=-9;x<=9;x+=3) for(int y=94;y<=118;y+=3) level.setBlock(new BlockPos(x,y,384),Blocks.GLOWSTONE.defaultBlockState(),3);
                for(int cx=-2;cx<=2;cx++) for(int cz=-2;cz<=3;cz++) level.getChunk(cx,cz);
                for(int x=-20;x<=20;x++) for(int z=-20;z<=40;z++) {
                    level.setBlock(new BlockPos(x,80,z),((x+z&1)==0 ? Blocks.POLISHED_DEEPSLATE : Blocks.DIORITE).defaultBlockState(),3);
                }
                for(int y=81;y<=89;y++) for(int z=10;z<=30;z++) {
                    level.setBlock(new BlockPos(-12,y,z),Blocks.SMOOTH_QUARTZ.defaultBlockState(),3);
                    level.setBlock(new BlockPos(12,y,z),Blocks.SMOOTH_QUARTZ.defaultBlockState(),3);
                }
                level.setBlock(new BlockPos(-11,84,16),Blocks.SEA_LANTERN.defaultBlockState(),3);
                level.setBlock(new BlockPos(11,84,16),Blocks.SHROOMLIGHT.defaultBlockState(),3);
                built = true;
            });
            ticks = 0;
        }
        if(total>20000) throw new IllegalStateException("DH fixture timed out at " + stage + ": " + OpenLightRenderer.cacheStatistics());
        int lastStage=NAMES.length+(Boolean.getBoolean("openlights.dhOcclusion")?2:0);
        if(stage==lastStage && ticks>40) mc.stop();
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if(!Boolean.getBoolean("openlights.dhSmoke") || !built) return;
        var mc = Minecraft.getInstance();
        if(mc.screen != null) return;
        if(stage>=NAMES.length) {renderOcclusion();return;}
        if(stage==0 && ticks<1000) {
            // Visit both ends, then leave and revisit so DH receives completed chunks.
            if(ticks==180) pose(-40.5,368.5);
            if(ticks==360) pose(40.5,368.5);
            if(ticks==540) pose(0.5,0.5);
            if(ticks==700) pose(0.5,368.5);
            if(ticks==820) pose(0.5,0.5);
            return;
        }
        if(stage>0 && ticks<160) return;
        if(reload!=null && !reload.isDone()) return;
        if(stage==4 && light!=null && !light.isValid()) {createLights();ticks=0;return;}
        Depth depth = readDhDepth();
        if(stage!=6 && depth.skylinePixels<100) {
            if(ticks%200==0) LogUtils.getLogger().info("DH_WAIT stage={} ticks={} depth={}",stage,ticks,depth);
            return;
        }
        if(stage>0 && stage!=3 && stage!=8 && !Boolean.getBoolean("openlights.expectShaderPack") && !OpenLightRenderer.cacheStatistics().replacement()) {
            throw new IllegalStateException("DH disabled nearby cached replacement at stage " + stage);
        }
        validateReceivers();
        capture(NAMES[stage], depth);
        if(stage==1 && Boolean.getBoolean("openlights.dhDiagnostic")) {
            stage=NAMES.length;ticks=0;
            LogUtils.getLogger().info("DH_SUCCESS diagnostic native/cached receiver overlap");
            return;
        }
        ticks=0;
        stage++;
        switch(stage) {
            case 1 -> ClientConfig.ENABLED.set(true);
            case 2 -> createLights();
            case 3 -> ClientConfig.LIGHTING_MODE.set(ClientConfig.LightingMode.ADDITIVE);
            case 4 -> {ClientConfig.LIGHTING_MODE.set(ClientConfig.LightingMode.CACHED);reload=mc.reloadResourcePacks();}
            case 5 -> GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1024,640);
            case 6 -> setConfig(graphics,"renderingEnabled",false);
            case 7 -> setConfig(graphics,"renderingEnabled",true);
            case 8 -> {
                if(light!=null)light.close();if(beam!=null)beam.close();
                ClientConfig.ENABLED.set(false);
                mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setDayTime(18000));
            }
            case 9 -> ClientConfig.ENABLED.set(true);
            default -> {
                if(light!=null) light.close();
                if(beam!=null) beam.close();
                if(Boolean.getBoolean("openlights.dhOcclusion"))prepareOcclusion();else success();
            }
        }
    }

    private static void prepareOcclusion() {
        var mc=Minecraft.getInstance();
        mc.getSingleplayerServer().execute(() -> {
            var level=mc.getSingleplayerServer().overworld();
            for(int cx=-1;cx<=1;cx++)level.getChunk(cx,3);
            for(int x=-16;x<=16;x++)for(int y=80;y<=100;y++)level.setBlock(new BlockPos(x,y,48),Blocks.SMOOTH_QUARTZ.defaultBlockState(),3);
            occluderBuilt=true;
        });
    }

    private static void renderOcclusion() {
        if(!Boolean.getBoolean("openlights.dhOcclusion") || stage>NAMES.length+1 || !occluderBuilt)return;
        var mc=Minecraft.getInstance();
        if(!droppedOccluder) {
            if(ticks<320)return;
            // Native chunk holes expose recorded LODs within ordinary analytic range.
            for(int cx=-1;cx<=1;cx++)mc.level.getChunkSource().drop(new net.minecraft.world.level.ChunkPos(cx,3));
            mc.levelRenderer.allChanged();droppedOccluder=true;ticks=0;return;
        }
        if(ticks<160)return;
        var stats=OpenLightRenderer.distantStatistics();
        if(!stats.merged())throw new IllegalStateException("DH occluder depth not merged");
        TextureData receivers=readTexture(stats.receiverTexture(),GL11.GL_RGBA,4);
        TextureData vanilla=readTexture(vanillaDepthTexture(),GL11.GL_RED,1);
        TextureData color=readTexture(mc.getMainRenderTarget().getColorTextureId(),GL11.GL_RGBA,4);
        int count=0,overlap=0;double difference=0;float minimum=Float.POSITIVE_INFINITY,maximum=0;
        for(int pixel=0;pixel<receivers.width*receivers.height;pixel++) {
            int offset=pixel*4;float owner=receivers.pixels.get(offset+1),distance=receivers.pixels.get(offset+2);
            if(owner>.5f && distance>40 && distance<64) {
                count++;minimum=Math.min(minimum,distance);maximum=Math.max(maximum,distance);
                if(vanilla.pixels.get(pixel)<.999999f)overlap++;
                if(occlusionColor!=null)for(int channel=0;channel<3;channel++)difference+=Math.abs(color.pixels.get(offset+channel)-occlusionColor.pixels.get(offset+channel));
            }
        }
        if(count<100) {
            if(ticks%100==0)LogUtils.getLogger().info("DH_OCCLUSION_WAIT stage={} ticks={} lodPlanePixels={}",stage,ticks,count);
            if(ticks>1600)throw new IllegalStateException("DH did not retain the manually exposed 48-block LOD plane");
            return;
        }
        if(overlap!=0)throw new IllegalStateException("DH occluder unexpectedly has native chunk depth: "+overlap);
        if(stage==NAMES.length) {
            occlusionColor=color;
            capture("dh-occluder-off",readDhDepth());
            beam=OpenLightsApi.create(OWNER,new LightDefinition.Spot(new Vec3(.5,84,56),new Vec3(0,0,1),new Vec3(0,1,0),new Vec3(1,.55,.1),8,16,35,20,false,1));
            stage++;ticks=0;
        } else {
            if(!OpenLightRenderer.frameLights().containsKey(beam.key()))throw new IllegalStateException("Behind-LOD beam was not selected, so fog clipping would be untested");
            double average=difference/Math.max(1,count*3);
            if(average>.005)throw new IllegalStateException("Behind-LOD beam altered the opaque LOD plane: RGBmean="+average);
            capture("dh-occluder-on",readDhDepth());
            LogUtils.getLogger().info("DH_OCCLUSION_SUCCESS planePixels={} nativeOverlap={} trueDistanceMin={} max={} behindBeamRgbMeanDifference={}",count,overlap,minimum,maximum,average);
            beam.close();stage++;ticks=0;success();
        }
    }

    private static void success() {
        LogUtils.getLogger().info("DH_SUCCESS actual 384-block skyline at four vanilla chunks; native/cached/additive, resource reload, framebuffer resize and DH disable/restore captures; shaders={} occlusion={}",Boolean.getBoolean("openlights.expectShaderPack"),Boolean.getBoolean("openlights.dhOcclusion"));
    }

    static void configureDh() {
        try {
            var delayed=Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
            Object configs=delayed.getField("configs").get(null);
            renderProxy=delayed.getField("renderProxy").get(null);
            if(configs==null || renderProxy==null) throw new IllegalStateException("DH API did not initialize");
            graphics=call(configs,"graphics");
            setConfig(graphics,"renderingEnabled",true);
            setConfig(graphics,"chunkRenderDistance",32);
            setConfig(graphics,"overdrawPreventionRadius",0.0f);
            setConfig(graphics,"caveCullingEnabled",false);
            Object fog=call(graphics,"fog");
            setConfig(fog,"enableDhFog",false);
            setConfig(fog,"enableVanillaFog",false);
            setConfig(call(configs,"worldGenerator"),"enableDistantWorldGeneration",false);
            LogUtils.getLogger().info("DH_API_READY renderProxy={} depthDirection={}",renderProxy.getClass().getName(),depthDirection());
        } catch(ReflectiveOperationException error) {throw new IllegalStateException("DH QA API inspection failed",error);}
    }

    private static void createLights() {
        if(light!=null)light.close();if(beam!=null)beam.close();
        light=OpenLightsApi.create(OWNER,new LightDefinition.Point(new Vec3(0.5,84,16),new Vec3(1,.15,.65),6,28,true,.8f));
        beam=OpenLightsApi.create(OWNER,new LightDefinition.Spot(new Vec3(0.5,84,8),new Vec3(0,0,1),new Vec3(0,1,0),new Vec3(1,.55,.1),8,640,35,20,false,1));
    }

    static void setConfig(Object group,String key,Object value) {
        Object entry=call(group,key);
        Object type=call(entry,"getTrueValue");
        if(value instanceof Number number) {
            if(type instanceof Double) value=number.doubleValue();
            else if(type instanceof Float) value=number.floatValue();
            else if(type instanceof Long) value=number.longValue();
            else if(type instanceof Integer) value=number.intValue();
        }
        if(!Boolean.TRUE.equals(call(entry,"setValue",value))) throw new IllegalStateException("DH refused QA config " + key + "=" + value);
    }

    static Object call(Object target,String name,Object... args) {
        if(target==null) throw new IllegalStateException("Missing DH API object for " + name);
        try {
            for(Method method:target.getClass().getMethods()) {
                if(method.getName().equals(name) && method.getParameterCount()==args.length) return method.invoke(target,args);
            }
            throw new NoSuchMethodException(target.getClass().getName()+"."+name);
        } catch(ReflectiveOperationException error) {throw new IllegalStateException("DH QA call " + name + " failed",error);}
    }

    private static Depth readDhDepth() {
        String getter=java.util.Arrays.stream(renderProxy.getClass().getMethods()).anyMatch(method->method.getName().equals("getDhDepthTextureGlId"))?"getDhDepthTextureGlId":"getDhDepthTextureId";
        Object result=call(renderProxy,getter);
        try {
            if(!result.getClass().getField("success").getBoolean(result)) return new Depth(0,0,0,0,1);
            int id=((Number)result.getClass().getField("payload").get(result)).intValue();
            if(id<=0 || !GL11.glIsTexture(id)) return new Depth(id,0,0,0,1);
            boolean reversed=depthDirection().contains("REVERSE");
            TextureData data=readTexture(id,GL11.GL_DEPTH_COMPONENT,1);
            int count=0;float minimum=1;
            for(int y=data.height*48/100;y<data.height*72/100;y++) for(int x=data.width*32/100;x<data.width*68/100;x++) {
                float value=data.pixels.get(x+y*data.width);
                if(!Float.isFinite(value) || value<0 || value>1) throw new IllegalStateException("Invalid DH depth " + value);
                if(reversed?value>1e-6f:value<.999999f) count++;
                minimum=Math.min(minimum,value);
            }
            return new Depth(id,data.width,data.height,count,minimum);
        } catch(ReflectiveOperationException error) {throw new IllegalStateException("DH QA depth result failed",error);}
    }

    private static String depthDirection() {
        boolean supported=java.util.Arrays.stream(renderProxy.getClass().getMethods()).anyMatch(method->method.getName().equals("getDepthDirection"));
        return supported?String.valueOf(call(renderProxy,"getDepthDirection")):"FORWARD_Z";
    }

    private static void validateReceivers() {
        var mc=Minecraft.getInstance();
        var stats=OpenLightRenderer.distantStatistics();
        if(stage==0 || stage==8) {
            nativeColor=readTexture(mc.getMainRenderTarget().getColorTextureId(),GL11.GL_RGBA,4);
            return;
        }
        if(stage==6) {
            if(stats.merged())throw new IllegalStateException("Stale DH receiver merge when renderer disabled: "+stats);
            LogUtils.getLogger().info("DH_RECEIVER_SKIPPED stage={} stats={}",stage,stats);
            return;
        }
        boolean shader=Boolean.getBoolean("openlights.expectShaderPack");
        if(shader && OpenLightRenderer.cacheStatistics().replacement())throw new IllegalStateException("Cached replacement active while shader pack owns terrain");
        if(!stats.merged() || stats.receiverTexture()<=0)throw new IllegalStateException("Current DH depth was not merged: "+stats);
        TextureData receivers=readTexture(stats.receiverTexture(),GL11.GL_RGBA,4);
        TextureData vanilla=readTexture(vanillaDepthTexture(),GL11.GL_RED,1);
        org.joml.Matrix4f inverse=projectionMatrix().invert();
        TextureData color=(stage==1 || stage==9)?readTexture(mc.getMainRenderTarget().getColorTextureId(),GL11.GL_RGBA,4):null;
        int far=0,near=0,beyond=0;
        int dhNear=0,overlap=0,coplanar=0,allOverlap=0;
        double minDelta=Double.POSITIVE_INFINITY,maxDelta=Double.NEGATIVE_INFINITY,sumDelta=0;
        double projectionFar=projectionFarDistance();
        double difference=0;
        for(int pixel=0;pixel<receivers.width*receivers.height;pixel++) {
            int index=pixel*4;
            float projected=receivers.pixels.get(index),owner=receivers.pixels.get(index+1),distance=receivers.pixels.get(index+2);
            if(!Float.isFinite(projected) || !Float.isFinite(owner) || !Float.isFinite(distance))throw new IllegalStateException("Nonfinite receiver metadata at "+pixel);
            if(owner>.5f && vanilla.pixels.get(pixel)<.999999f)allOverlap++;
            if(owner>.5f && distance<96) {
                dhNear++;
                float nativeDepth=vanilla.pixels.get(pixel);
                if(nativeDepth<.999999f) {
                    int x=pixel%receivers.width,y=pixel/receivers.width;
                    float nx=2*(x+.5f)/receivers.width-1,ny=2*(y+.5f)/receivers.height-1,nz=nativeDepth*2-1;
                    float vx=inverse.m00()*nx+inverse.m10()*ny+inverse.m20()*nz+inverse.m30();
                    float vy=inverse.m01()*nx+inverse.m11()*ny+inverse.m21()*nz+inverse.m31();
                    float vz=inverse.m02()*nx+inverse.m12()*ny+inverse.m22()*nz+inverse.m32();
                    float vw=inverse.m03()*nx+inverse.m13()*ny+inverse.m23()*nz+inverse.m33();
                    double nativeDistance=Math.sqrt(vx*vx+vy*vy+vz*vz)/Math.abs(vw);
                    double delta=nativeDistance-distance;
                    overlap++;sumDelta+=delta;minDelta=Math.min(minDelta,delta);maxDelta=Math.max(maxDelta,delta);
                    if(Math.abs(delta)<.25)coplanar++;
                }
            }
            if(owner>.5f && distance>300) {
                far++;
                if(distance>projectionFar && projected>=.999997f)beyond++;
                if(color!=null && nativeColor.width==color.width && nativeColor.height==color.height) {
                    for(int channel=0;channel<3;channel++)difference+=Math.abs(color.pixels.get(index+channel)-nativeColor.pixels.get(index+channel));
                }
            } else if(owner<.5f && distance>1 && distance<96)near++;
        }
        if(far<100 || near<100 || (!shader && beyond<100))throw new IllegalStateException("DH/native receiver ownership or far-plane reprojection failed: far="+far+", near="+near+", beyond="+beyond+", projectionFar="+projectionFar+", "+stats);
        double average=difference/Math.max(1,far*3);
        if(!shader && color!=null && average>.02)throw new IllegalStateException("Cached replacement changed DH-owned native shading: mean RGB difference="+average);
        LogUtils.getLogger().info("DH_RECEIVERS stage={} stats={} far={} near={} beyondVanillaFar={} projectionFar={} nativeColorMeanDifference={}",stage,stats,far,near,beyond,projectionFar,average);
        LogUtils.getLogger().info("DH_NEAR_OVERLAP stage={} dhNear={} nativeAlsoValid={} coplanarQuarterBlock={} nativeMinusDhMean={} min={} max={} allDistancesNativeAlsoValid={}",stage,dhNear,overlap,coplanar,overlap>0?sumDelta/overlap:0,minDelta,maxDelta,allOverlap);
        if(!Boolean.getBoolean("openlights.dhDiagnostic") && allOverlap!=0)throw new IllegalStateException("DH receiver overrode visible native terrain color: overlap="+allOverlap+", near="+overlap);
        if(stage==2 || stage==5) observeThinReceivers(receivers);
    }

    private static void observeThinReceivers(TextureData receivers) {
        TextureData lighting=readTexture(targetTexture("lighting"),GL11.GL_RGBA,4);
        int thin=0,pureSamples=0,coloredPureSamples=0;
        double minimum=Double.POSITIVE_INFINITY,maximum=Double.NEGATIVE_INFINITY;
        String example="none";
        for(int y=0;y<receivers.height;y++) for(int x=0;x<receivers.width;x++) {
            int index=(x+y*receivers.width)*4;
            if(receivers.pixels.get(index+1)<.5f)continue;
            int bx=(int)Math.floor((x+.5)*lighting.width/receivers.width-.5);
            int by=(int)Math.floor((y+.5)*lighting.height/receivers.height-.5);
            boolean represented=false;
            int pure=0,colored=0;
            String alphas="";
            for(int corner=0;corner<4;corner++) {
                int sx=Math.max(0,Math.min(lighting.width-1,bx+(corner&1)));
                int sy=Math.max(0,Math.min(lighting.height-1,by+(corner>>1)));
                int rx=Math.min(receivers.width-1,(int)((sx+.5)*receivers.width/lighting.width));
                int ry=Math.min(receivers.height-1,(int)((sy+.5)*receivers.height/lighting.height));
                if(receivers.pixels.get((rx+ry*receivers.width)*4+1)>.5f)represented=true;
                int sample=(sx+sy*lighting.width)*4;
                float alpha=lighting.pixels.get(sample+3);
                if(!Float.isFinite(alpha) || alpha<0 || alpha>1)throw new IllegalStateException("Invalid lighting coverage alpha "+alpha);
                if(alpha==0) {
                    pure++;
                    if(lighting.pixels.get(sample)+lighting.pixels.get(sample+1)+lighting.pixels.get(sample+2)>1e-5)colored++;
                }
                alphas+=(corner==0?"":",")+alpha;
            }
            if(represented)continue;
            float distance=receivers.pixels.get(index+2);
            thin++;pureSamples+=pure;coloredPureSamples+=colored;
            minimum=Math.min(minimum,distance);maximum=Math.max(maximum,distance);
            if(example.equals("none"))example="pixel="+x+","+y+" distance="+distance+" lightingAlpha="+alphas;
        }
        LogUtils.getLogger().info("DH_THIN_RECEIVERS stage={} lighting={}x{} absentFromAllFourOwners={} trueDistanceMin={} max={} pureVolumeSamples={} coloredPureVolumeSamples={} example={}",stage,lighting.width,lighting.height,thin,minimum,maximum,pureSamples,coloredPureSamples,example);
    }

    private static int vanillaDepthTexture() {
        return targetTexture("vanillaDepth");
    }

    private static int targetTexture(String name) {
        try {
            var targetField=OpenLightRenderer.class.getDeclaredField("TARGETS");targetField.setAccessible(true);
            Object targets=targetField.get(null);
            var depthField=targets.getClass().getDeclaredField(name);depthField.setAccessible(true);
            return depthField.getInt(targets);
        } catch(ReflectiveOperationException error) {throw new IllegalStateException("Unable to inspect target "+name,error);}
    }

    static org.joml.Matrix4f projectionMatrix() {
        try {
            var field=OpenLightRenderer.class.getDeclaredField("PROJECTION");field.setAccessible(true);
            return new org.joml.Matrix4f((org.joml.Matrix4f)field.get(null));
        } catch(ReflectiveOperationException error) {throw new IllegalStateException("Unable to inspect native projection",error);}
    }

    private static double projectionFarDistance() {
        var matrix=projectionMatrix();
        return Math.abs(matrix.m32()/(matrix.m22()+1.0));
    }

    static TextureData readTexture(int id,int format,int components) {
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int pbo=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int align=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT),row=GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        int skipRows=GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS),skipPixels=GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        int imageHeight=GL11.glGetInteger(GL12.GL_PACK_IMAGE_HEIGHT),skipImages=GL11.glGetInteger(GL12.GL_PACK_SKIP_IMAGES);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);
            int width=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH);
            int height=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT);
            if(width<=0 || height<=0)throw new IllegalStateException("Missing GPU QA texture "+id);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,0);GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,0);
            GL11.glPixelStorei(GL12.GL_PACK_IMAGE_HEIGHT,0);GL11.glPixelStorei(GL12.GL_PACK_SKIP_IMAGES,0);
            FloatBuffer pixels=BufferUtils.createFloatBuffer(width*height*components);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,format,GL11.GL_FLOAT,pixels);
            return new TextureData(width,height,pixels);
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,align);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,row);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,skipRows);GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,skipPixels);
            GL11.glPixelStorei(GL12.GL_PACK_IMAGE_HEIGHT,imageHeight);GL11.glPixelStorei(GL12.GL_PACK_SKIP_IMAGES,skipImages);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pbo);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL13.glActiveTexture(active);
        }
    }

    private static void pose(double x,double z) {
        var mc=Minecraft.getInstance();var id=mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(player.serverLevel(),x,84,z,0,0);
        });
    }

    private static void capture(String name,Depth depth) {
        var mc=Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),message -> {});
        LogUtils.getLogger().info("DH_CAPTURE {} position={} depth={} renderer={} cache={}",name,mc.player.position(),depth,OpenLightRenderer.statistics(),OpenLightRenderer.cacheStatistics());
    }

    private record Depth(int texture,int width,int height,int skylinePixels,float minimum) {}
    record TextureData(int width,int height,FloatBuffer pixels) {}
}
