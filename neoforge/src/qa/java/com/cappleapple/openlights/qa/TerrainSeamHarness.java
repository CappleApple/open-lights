package com.cappleapple.openlights.qa;

import com.cappleapple.openlights.client.LightingBudgets;
import com.cappleapple.openlights.client.render.OpenLightRenderer;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

/** Contiguous sky-lit terrain with the actual DH vanilla fade band. */
@EventBusSubscriber(modid="openlightsqa",value=Dist.CLIENT)
public final class TerrainSeamHarness {
    private record Phase(String name,String fade,boolean night,boolean gi,boolean fog,boolean dh,boolean wide,boolean enabled,boolean additive) {}
    private static final boolean FULL=Boolean.getBoolean("openlights.seamFull");
    private static final int FIRST_STAGE=Integer.getInteger("openlights.seamStart",0);
    private static final Phase[] PHASES=phases();
    private static final String[] NAMES=java.util.Arrays.stream(PHASES).map(Phase::name).toArray(String[]::new);
    private static final String[] MATERIALS={"snow","sand","grass","dirt","water","trees"};
    private static final String[] REGIONS={"native-before-fade","native-fade","native-after-fade","lod"};
    private static boolean loading,started,prepared;
    private static volatile boolean built;
    private static int ticks,stage=FIRST_STAGE,total;
    private static Object graphics,fadeEntry;
    private static String requestedFade;
    private static DistantHorizonsHarness.TextureData cachedColor;
    private static byte[] mask;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("openlights.seamSmoke"))return;
        var mc=Minecraft.getInstance();
        mc.options.pauseOnLostFocus=false;mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        mc.mouseHandler.releaseMouse();GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(!loading && mc.screen instanceof TitleScreen title) {
            loading=true;mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);
            mc.options.gamma().set(1.0);mc.options.fov().set(70);mc.options.hideGui=true;
            DistantHorizonsHarness.configureDh();
            try {
                var delayed=Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
                graphics=DistantHorizonsHarness.call(delayed.getField("configs").get(null),"graphics");
                fadeEntry=Class.forName("com.seibel.distanthorizons.core.config.Config$Client$Advanced$Graphics$Quality").getField("vanillaFadeMode").get(null);
            }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
            DistantHorizonsHarness.setConfig(graphics,"overdrawPreventionRadius",-1f);
            DistantHorizonsHarness.setConfig(graphics,"brightnessMultiplier",1f);
            DistantHorizonsHarness.setConfig(graphics,"saturationMultiplier",1f);
            DistantHorizonsHarness.setConfig(DistantHorizonsHarness.call(graphics,"fog"),"enableDhFog",true);
            fade("DOUBLE_PASS");QaBootstrap.open(title);
        }
        if(mc.player==null || mc.getSingleplayerServer()==null || mc.screen!=null)return;
        if(!started) {
            started=true;ClientConfig.ENABLED.set(true);ClientConfig.GI_ENABLED.set(false);
            ClientConfig.LIGHTING_MODE.set(ClientConfig.LightingMode.CACHED);
            ClientConfig.BLOCK_LIGHT_STYLE.set(ClientConfig.BlockLightStyle.OPEN_LIGHTS);ClientConfig.BLOOM_STRENGTH.set(0.0);
            LightingBudgets.RAPID.apply();apply(FIRST_STAGE);pose(0,FULL?384:160);
        }
        ticks++;total++;
        if(!prepared && ticks>100 && mc.level.hasChunk(0,FULL?24:10)) {
            prepared=true;mc.getSingleplayerServer().execute(()->{
                var world=mc.getSingleplayerServer().overworld();
                world.setDayTime(18000);world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,mc.getSingleplayerServer());
                world.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,mc.getSingleplayerServer());
                int end=FULL?768:320;
                for(int cx=-4;cx<=4;cx++)for(int cz=-2;cz<=end/16;cz++)world.getChunk(cx,cz);
                for(int x=-64;x<=64;x++)for(int z=-24;z<=end;z++) {
                    var surface=x<-40?Blocks.SNOW_BLOCK:x<-16?Blocks.SAND:x<16?Blocks.GRASS_BLOCK:x<40?Blocks.DIRT:Blocks.WATER;
                    world.setBlock(new BlockPos(x,63,z),Blocks.DIRT.defaultBlockState(),3);
                    world.setBlock(new BlockPos(x,64,z),surface.defaultBlockState(),3);
                }
                for(int z=40;z<=end-40;z+=32)for(int x:new int[]{-12,12}) {
                    for(int y=65;y<=69;y++)world.setBlock(new BlockPos(x,y,z),Blocks.OAK_LOG.defaultBlockState(),3);
                    for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int y=68;y<=71;y++)
                        if(dx!=0 || dz!=0 || y>69)world.setBlock(new BlockPos(x+dx,y,z+dz),Blocks.OAK_LEAVES.defaultBlockState(),3);
                }
                built=true;
            });ticks=0;
        }
        if(total>20000)throw new IllegalStateException("Seam fixture timeout stage="+stage);
        if(stage==NAMES.length && ticks>20)mc.stop();
    }

    @SubscribeEvent public static void render(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        if(!Boolean.getBoolean("openlights.seamSmoke") || !built)return;
        var mc=Minecraft.getInstance();if(mc.screen!=null || stage==NAMES.length)return;
        if(stage==FIRST_STAGE && ticks<(FULL?1220:900)) {
            if(ticks==100)pose(-48,FULL?640:256);
            if(ticks==260)pose(48,FULL?640:256);
            if(ticks==420)pose(-48,FULL?320:64);
            if(ticks==580)pose(48,FULL?320:64);
            if(ticks==740)pose(FULL?-48:0,FULL?64:0);
            if(FULL && ticks==900)pose(48,64);
            if(FULL && ticks==1060)pose(0,0);
            return;
        }
        if(stage>0 && ticks<160)return;
        if(!DistantHorizonsHarness.call(fadeEntry,"get").toString().equals(requestedFade))throw new IllegalStateException("DH fade control changed unexpectedly");
        var color=DistantHorizonsHarness.readTexture(mc.getMainRenderTarget().getColorTextureId(),GL11.GL_RGBA,4);
        if(stage%2==0) {
            var stats=OpenLightRenderer.distantStatistics();
            if(PHASES[stage].dh() && !stats.merged())return;
            if(!PHASES[stage].dh() && stats.merged())throw new IllegalStateException("DH metadata survived disabled renderer");
            cachedColor=color;buildMask(DistantHorizonsHarness.readTexture(stats.receiverTexture(),GL11.GL_RGBA,4));
        }
        Screenshot.grab(mc.gameDirectory,"seam-"+NAMES[stage]+".png",mc.getMainRenderTarget(),message->{});
        diagnostics(color);
        measure(color);
        LogUtils.getLogger().info("SEAM_CAPTURE {} renderer={} cache={} fade={}",NAMES[stage],OpenLightRenderer.statistics(),OpenLightRenderer.cacheStatistics(),DistantHorizonsHarness.call(fadeEntry,"get"));
        stage++;ticks=0;
        if(stage<NAMES.length)apply(stage);
        if(stage==NAMES.length)LogUtils.getLogger().info("SEAM_SUCCESS captures={} start={} totalPhases={} full={} natural materials, gamma1, verified fade controls, cached/control ambient match required={}",NAMES.length-FIRST_STAGE,FIRST_STAGE,NAMES.length,FULL,Boolean.getBoolean("openlights.seamRequireMatch"));
    }

    private static void fade(String name) {
        Object current=DistantHorizonsHarness.call(fadeEntry,"getTrueValue");
        Object selected=java.util.Arrays.stream(current.getClass().getEnumConstants()).filter(value->value.toString().equals(name)).findFirst().orElseThrow();
        DistantHorizonsHarness.call(fadeEntry,"setWithoutSaving",selected);
        requestedFade=name;
        if(!DistantHorizonsHarness.call(fadeEntry,"get").toString().equals(name))throw new IllegalStateException("DH refused fade control "+name);
    }

    private static double fadeNear() {
        try{return ((Number)Class.forName("com.seibel.distanthorizons.core.util.RenderUtil").getMethod("getNearClipPlaneInBlocks").invoke(null)).doubleValue()+16;}
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }

    private static void buildMask(DistantHorizonsHarness.TextureData data) {
        mask=new byte[data.width()*data.height()];
        var projection=DistantHorizonsHarness.projectionMatrix().invert();
        Matrix4f inverseView;
        try{var field=OpenLightRenderer.class.getDeclaredField("VIEW");field.setAccessible(true);inverseView=new Matrix4f((Matrix4f)field.get(null)).invert();}
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double start=fadeNear()*1.5,end=fadeNear()*1.9;
        int fadePixels=0,lodPixels=0;var v=new Vector4f();var direction=new Vector3f();
        for(int y=0;y<data.height();y++)for(int x=0;x<data.width();x++) {
            int p=x+y*data.width(),i=p*4;float owner=data.pixels().get(i+1),distance=data.pixels().get(i+2);
            if(distance<16 || distance>(PHASES[stage].wide()?700:180))continue;
            projection.transform(v.set(2*(x+.5f)/data.width()-1,2*(y+.5f)/data.height()-1,1,1));
            direction.set(v.x/v.w,v.y/v.w,v.z/v.w).normalize().mul(distance);inverseView.transformDirection(direction);
            double wx=camera.x+direction.x,wy=camera.y+direction.y;
            int material;
            if(wy>=67 && wy<=73 && Math.abs(Math.abs(wx)-12)<3)material=5;
            else if(wy>63.5 && wy<66 && wx>-64 && wx<64)material=wx<-40?0:wx<-16?1:wx<16?2:wx<40?3:4;
            else continue;
            int region=owner>.5?3:distance<start*.8?0:distance>=start && distance<=end?1:distance>end?2:-1;
            if(region<0)continue;
            mask[p]=(byte)(1+material*4+region);
            if(region==1)fadePixels++;if(region==3)lodPixels++;
        }
        LogUtils.getLogger().info("SEAM_MASK fadeStart={} fadeEnd={} nativeFadePixels={} lodPixels={}",start,end,fadePixels,lodPixels);
        if(fadePixels<100 || (PHASES[stage].dh() && lodPixels<100))throw new IllegalStateException("Actual native fade band/LOD fixture missing");
    }

    private static void measure(DistantHorizonsHarness.TextureData color) {
        int[] count=new int[24];double[][] rgb=new double[24][3];double[] cachedLuma=new double[24],absoluteDifference=new double[24];
        for(int p=0;p<mask.length;p++) {
            int group=mask[p]-1;if(group<0)continue;count[group]++;
            for(int channel=0;channel<3;channel++) {
                rgb[group][channel]+=color.pixels().get(p*4+channel);
                absoluteDifference[group]+=Math.abs(color.pixels().get(p*4+channel)-cachedColor.pixels().get(p*4+channel));
            }
            cachedLuma[group]+=.2126*cachedColor.pixels().get(p*4)+.7152*cachedColor.pixels().get(p*4+1)+.0722*cachedColor.pixels().get(p*4+2);
        }
        String failure=null;
        for(int group=0;group<24;group++)if(count[group]>0) {
            double r=rgb[group][0]/count[group],g=rgb[group][1]/count[group],b=rgb[group][2]/count[group];
            double luma=.2126*r+.7152*g+.0722*b;
            double difference=absoluteDifference[group]/count[group]/3;
            LogUtils.getLogger().info("SEAM_COLOR capture={} material={} region={} pixels={} rgb={},{},{} luma={} cachedToThisLumaRatio={} cachedToThisRgbAbsMean={}",NAMES[stage],MATERIALS[group/4],REGIONS[group%4],count[group],r,g,b,luma,luma>1e-6?cachedLuma[group]/count[group]/luma:0,difference);
            if(stage%2==1 && Boolean.getBoolean("openlights.seamRequireMatch") && count[group]>100 && difference>.02)
                failure="Cached ambient seam mismatch: "+NAMES[stage]+" "+MATERIALS[group/4]+" "+REGIONS[group%4]+" RGB mean="+difference;
        }
        if(failure!=null)throw new IllegalStateException(failure);
    }

    private static Object rendererField(String name) {
        try{var field=OpenLightRenderer.class.getDeclaredField(name);field.setAccessible(true);return field.get(null);}
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }

    private static void diagnostics(DistantHorizonsHarness.TextureData color) {
        boolean nativeSky=false;
        try{nativeSky=(Boolean)OpenLightRenderer.class.getMethod("nativeSkyLighting").invoke(null);}
        catch(NoSuchMethodException expectedBaseline){}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        var mc=Minecraft.getInstance();var camera=mc.gameRenderer.getMainCamera().getPosition();
        if(Math.abs(camera.x)>0.01 || Math.abs(camera.z)>0.01 || Math.abs(mc.player.getY()-68)>.01 || Math.abs(mc.player.getYRot())>.01 || Math.abs(mc.player.getXRot()-7)>.01 || mc.level.getDayTime()!=(PHASES[stage].night()?18000:6000))throw new IllegalStateException("Seam camera/time changed");
        if(Boolean.getBoolean("openlights.seamRequireMatch") && stage%2==0 && !nativeSky)throw new IllegalStateException("Cached DH ambient palette missing, including DH-off control");
        if(color.width()!=(PHASES[stage].wide()?1600:1280) || color.height()!=(PHASES[stage].wide()?500:800))throw new IllegalStateException("Seam framebuffer size changed");
        var cache=OpenLightRenderer.cacheStatistics();
        if(stage%2==0 && (OpenLightRenderer.statistics().lights()!=0 || cache.blockSections()!=0 || cache.aggregateCells()!=0 || (!PHASES[stage].gi() && cache.giProbes()!=0)))
            throw new IllegalStateException("Seam ambient fixture contains source/cache lighting");
        int serverView=mc.getSingleplayerServer().getPlayerList().getViewDistance(),requested=serverView;
        var serverPlayer=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        try{requested=((Number)serverPlayer.getClass().getMethod("requestedViewDistance").invoke(serverPlayer)).intValue();}
        catch(NoSuchMethodException olderMinecraft){}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        int expectedView=PHASES[stage].wide()?15:4;
        if(requested!=expectedView || serverView!=expectedView || mc.options.getEffectiveRenderDistance()!=expectedView)throw new IllegalStateException("Native client/server requested distance changed");
        LogUtils.getLogger().info("SEAM_STATE capture={} nativeSky={} camera={} rotation={}/{} time={} image={}x{} configuredNative={} effectiveNative={} requestedNative={} serverNative={} brightness(sand,grass)={}/{},{}/{}",NAMES[stage],nativeSky,camera,mc.player.getYRot(),mc.player.getXRot(),mc.level.getDayTime(),color.width(),color.height(),mc.options.renderDistance().get(),mc.options.getEffectiveRenderDistance(),requested,serverView,
                mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,new BlockPos(-20,65,20)),mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,new BlockPos(-20,65,20)),
                mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,new BlockPos(0,65,20)),mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,new BlockPos(0,65,20)));
        if(stage%2!=0)return;
        Object targets=rendererField("TARGETS");int sceneId;
        try{var field=targets.getClass().getDeclaredField("sceneColor");field.setAccessible(true);sceneId=field.getInt(targets);}
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        var scene=DistantHorizonsHarness.readTexture(sceneId,GL11.GL_RGBA,4);
        double[] difference=new double[24];int[] count=new int[24];
        for(int p=0;p<mask.length;p++){int group=mask[p]-1;if(group<0)continue;count[group]++;for(int c=0;c<3;c++)difference[group]+=Math.abs(scene.pixels().get(p*4+c)-color.pixels().get(p*4+c));}
        for(int group=0;group<24;group++)if(count[group]>100)LogUtils.getLogger().info("SEAM_COMPOSITE capture={} material={} region={} sceneToFinalRgbAbsMean={}",NAMES[stage],MATERIALS[group/4],REGIONS[group%4],difference[group]/count[group]/3);
        int nativeId=(Integer)rendererField("nativeLightmap");
        LogUtils.getLogger().info("SEAM_PALETTE capture={} native={} nativeFilter={} sky={} skyFilter={}",NAMES[stage],nativeId,filters(nativeId),rendererFieldOptional("skyLightmap"),filters(rendererFieldOptional("skyLightmap")));
    }

    private static int rendererFieldOptional(String name) {
        try{var field=OpenLightRenderer.class.getDeclaredField(name);field.setAccessible(true);return field.getInt(null);}
        catch(NoSuchFieldException expectedBaseline){return 0;}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }

    private static String filters(int id) {
        if(id==0)return "absent";
        int binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try{GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);return GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER)+","+GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER)+","+GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S)+","+GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T);}
        finally{GL11.glBindTexture(GL11.GL_TEXTURE_2D,binding);}
    }

    private static Phase[] phases() {
        var result=new java.util.ArrayList<Phase>();
        pair(result,"double","DOUBLE_PASS",true,false,true,true,false,false);
        pair(result,"none","NONE",true,false,true,true,false,false);
        if(FULL) {
            pair(result,"single","SINGLE_PASS",true,false,true,true,false,false);
            for(String fade:new String[]{"DOUBLE_PASS","SINGLE_PASS","NONE"}) {
                String id=fade.toLowerCase(java.util.Locale.ROOT).replace("_pass","");
                pair(result,id,fade,false,false,true,true,false,false);
                for(boolean night:new boolean[]{true,false})pair(result,id+"-gi",fade,night,true,true,true,false,false);
            }
            for(boolean night:new boolean[]{true,false}) {
                pair(result,"double-no-fog","DOUBLE_PASS",night,false,false,true,false,false);
                pair(result,"double-additive","DOUBLE_PASS",night,false,true,true,false,true);
                pair(result,"double-dh-off","DOUBLE_PASS",night,false,true,false,false,false);
                pair(result,"double-wide15","DOUBLE_PASS",night,false,true,true,true,false);
            }
        }
        return result.toArray(Phase[]::new);
    }

    private static void pair(java.util.List<Phase> target,String id,String fade,boolean night,boolean gi,boolean fog,boolean dh,boolean wide,boolean additive) {
        String suffix=night?"night":"day";
        target.add(new Phase(id+"-cached-"+suffix,fade,night,gi,fog,dh,wide,true,false));
        target.add(new Phase(id+(additive?"-control-":"-disabled-")+suffix,fade,night,gi,fog,dh,wide,additive,additive));
    }

    private static void apply(int index) {
        var phase=PHASES[index];var mc=Minecraft.getInstance();fade(phase.fade());
        ClientConfig.ENABLED.set(phase.enabled());ClientConfig.GI_ENABLED.set(phase.gi());
        ClientConfig.LIGHTING_MODE.set(phase.additive()?ClientConfig.LightingMode.ADDITIVE:ClientConfig.LightingMode.CACHED);
        DistantHorizonsHarness.setConfig(graphics,"renderingEnabled",phase.dh());
        DistantHorizonsHarness.setConfig(DistantHorizonsHarness.call(graphics,"fog"),"enableDhFog",phase.fog());
        mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().overworld().setDayTime(phase.night()?18000:6000));
        mc.options.renderDistance().set(phase.wide()?15:4);
        mc.options.broadcastOptions();
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),phase.wide()?1600:1280,phase.wide()?500:800);
        LogUtils.getLogger().info("SEAM_PHASE {} fade={} night={} gi={} fog={} dh={} renderChunks={} enabled={} additive={}",phase.name(),phase.fade(),phase.night(),phase.gi(),phase.fog(),phase.dh(),phase.wide()?15:4,phase.enabled(),phase.additive());
    }

    private static void pose(double x,double z) {
        var mc=Minecraft.getInstance();var id=mc.player.getUUID();
        mc.getSingleplayerServer().execute(()->{var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);player.setGameMode(GameType.SPECTATOR);player.teleportTo(player.serverLevel(),x,68,z,0,7);});
    }
}
