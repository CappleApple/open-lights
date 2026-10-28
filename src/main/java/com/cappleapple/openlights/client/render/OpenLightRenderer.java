package com.cappleapple.openlights.client.render;

import com.cappleapple.openlights.api.client.CollectLightsEvent;
import com.cappleapple.openlights.api.client.LightDefinition;
import com.cappleapple.openlights.api.client.LightKey;
import com.cappleapple.openlights.api.client.OpenLightsApi;
import com.cappleapple.openlights.config.ClientConfig;
import com.cappleapple.openlights.client.scene.SceneCache;
import com.cappleapple.openlights.client.scene.SceneSnapshot;
import com.cappleapple.openlights.client.scene.LightCoverage;
import com.cappleapple.openlights.api.client.ShaderCompatibility;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import org.slf4j.Logger;

import java.util.*;

/** Deferred light renderer with normal-chunk shading and optional Distant Horizons receivers. */
public final class OpenLightRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final RenderTargets TARGETS = new RenderTargets();
    private static final SceneCache SCENE = new SceneCache();
    private static WorldLightingExtension WORLD_LIGHTING = WorldLightingExtension.NONE;
    private static int nativeLightmap;
    private static boolean worldPass, replacementFrame, nativeSkyFrame;
    /** Internal extension point for separately packaged world-lighting implementations. */
    public static void installWorldLighting(WorldLightingExtension extension) {
        if (WORLD_LIGHTING != WorldLightingExtension.NONE) throw new IllegalStateException("World lighting is already installed");
        WORLD_LIGHTING = Objects.requireNonNull(extension);
    }
    public static boolean hasWorldLightingExtension() { return WORLD_LIGHTING != WorldLightingExtension.NONE; }
    public static boolean hasActiveLighting() { return !FRAME_LIGHTS.isEmpty() || WORLD_LIGHTING.hasWork(); }
    private static long cacheTick;
    /** A monotonic local clock: server time synchronization must not repeatedly discard warmed caches. */
    public static void clientTick() { cacheTick++; }
    private static final Map<LightKey, ShadowCache> SHADOWS = new HashMap<>();
    private static final LinkedHashMap<LightKey, LightDefinition> FRAME_LIGHTS = new LinkedHashMap<>();
    private static final Matrix4f VIEW = new Matrix4f(), PROJECTION = new Matrix4f();
    private static GlProgram depthCopy, shadow, lighting, composite;
    private static int fullScreenVao, geometryVao, geometryBuffer, mediumBuffer, vertexCount;
    private static long uploadedRevision = Long.MIN_VALUE;
    private static boolean failed, depthReady, matricesReady;
    private static boolean distantDepthMerged;
    private static int distantDepthTexture;
    private static long frames;
    private static Statistics statistics = new Statistics(0,0,0,0,0,0);
    private static final int[] timingQueries = new int[3];
    private static int timingIndex;
    private static final boolean[] queryIssued = new boolean[3];
    private static double gpuMillis;

    private OpenLightRenderer() {}

    public static Map<LightKey, LightDefinition> frameLights() { return Collections.unmodifiableMap(FRAME_LIGHTS); }
    public static Statistics statistics() { return statistics; }
    /** Internal diagnostics for current-frame LOD depth; borrowed textures remain DH-owned. */
    public record DistantStatistics(boolean merged, int depthTexture, int receiverTexture) {}
    public static DistantStatistics distantStatistics() {
        return new DistantStatistics(distantDepthMerged, distantDepthTexture, TARGETS.opaqueDepth);
    }
    public record Statistics(int lights, int shadowPasses, int triangles, int media, double cpuMillis, double gpuMillis) {}
    public record CacheStatistics(boolean replacement, int worldProbes, int worldCapacity,
                                  int giProbes, int giUpdated, double worldCpuMillis, double giCpuMillis,
                                  int worldUpdated, int sceneUpdated, int colorUpdated, int farGiProbes, int farGiCapacity,
                                  int blockSections, int blockPending, int aggregateCells, int aggregateUpdated,
                                  boolean aggregatePending, boolean aggregateLimited, boolean aggregateWorker,
                                  int snapshotCells, int appliedSections, int discardedJobs,
                                  int lightUploads, boolean lightUploadsPending) {}
    public static CacheStatistics cacheStatistics() {
        return WORLD_LIGHTING.statistics(SCENE.updated());
    }

    public static void nativeLightmap(net.minecraft.client.renderer.texture.DynamicTexture texture) {
        nativeLightmap = texture.getId();
        WORLD_LIGHTING.nativeLightmap(texture, worldPass && replacementFrame);
        nativeSkyFrame = WORLD_LIGHTING.nativeSky();
    }
    public static int worldLightmap() {
        if (WORLD_LIGHTING == WorldLightingExtension.NONE) return 0;
        return !failed && ClientConfig.ENABLED.get() ? WORLD_LIGHTING.worldLightmap(worldPass && replacementFrame) : 0;
    }
    /** Internal diagnostics for the ambient palette used by this world frame. */
    public static boolean nativeSkyLighting() { return replacementFrame && nativeSkyFrame; }
    public static void beginHand() {
        WORLD_LIGHTING.beginHand(!failed && ClientConfig.ENABLED.get());
    }
    public static void endHand() {
        WORLD_LIGHTING.endHand();
        if (nativeLightmap != 0) RenderSystem.setShaderTexture(2, nativeLightmap);
    }
    public static void beginWorld() {
        worldPass = true; replacementFrame = false; nativeSkyFrame = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || failed || !ClientConfig.ENABLED.get() || ShaderCompatibility.isRenderingShadowPass()) return;
        distantDepthMerged = false; distantDepthTexture = 0;
        if (!FRAME_LIGHTS.isEmpty() || WORLD_LIGHTING.hasWork() || !OpenLightsApi.snapshot().isEmpty()) DistantHorizonsCompatibility.beginFrame();
        else DistantHorizonsCompatibility.clearFrame();
        try {
            WORLD_LIGHTING.beginWorld(cacheTick);
            replacementFrame = WORLD_LIGHTING.replacement();
            nativeSkyFrame = WORLD_LIGHTING.nativeSky();
            if (replacementFrame) mc.gameRenderer.lightTexture().turnOnLightLayer();
        } catch (Exception exception) { fail(exception); }
    }
    public static void endWorld() {
        worldPass = false;
        if (nativeLightmap != 0) RenderSystem.setShaderTexture(2, nativeLightmap);
    }
    private static boolean hasWork() {
        return !FRAME_LIGHTS.isEmpty() || WORLD_LIGHTING.hasWork();
    }

    public static void stage(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !ClientConfig.ENABLED.get() || failed) return;
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            depthReady = false;
            matricesReady = true;
            VIEW.set(event.getPoseStack().last().pose()).setTranslation(0,0,0);
            PROJECTION.set(event.getProjectionMatrix());
            collect(event);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES
                && matricesReady && hasWork()) {
            try (var ignored = new GlState()) {
                initialize();
                prepareTargets();
                copyDepth(mc.getMainRenderTarget().getDepthTextureId());
                depthReady = true;
            } catch (Exception exception) { fail(exception); }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL && matricesReady) {
            matricesReady = false;
            if (!hasWork()) {
                statistics = new Statistics(0,0,vertexCount/3,0,0,0);
                return;
            }
            long started = System.nanoTime();
            try (var ignored = new GlState()) {
                initialize();
                prepareTargets();
                if (!depthReady) copyDepth(mc.getMainRenderTarget().getDepthTextureId());
                mergeDepth();
                int query = timingQueries[timingIndex];
                if (queryIssued[timingIndex] && GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
                    gpuMillis = GL33.glGetQueryObjectui64(query, GL15.GL_QUERY_RESULT) / 1_000_000.0;
                }
                boolean time = GL15.glGetQueryi(GL33.GL_TIME_ELAPSED, GL15.GL_CURRENT_QUERY) == 0;
                if(time) GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query);
                try {
                    render(event);
                } finally {
                    if(time) {
                        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
                        queryIssued[timingIndex] = true;
                        timingIndex = (timingIndex+1)%timingQueries.length;
                    }
                }
                statistics = new Statistics(statistics.lights(),statistics.shadowPasses(),statistics.triangles(),
                        statistics.media(),(System.nanoTime()-started)/1_000_000.0,gpuMillis);
                frames++;
            } catch (Exception exception) { fail(exception); }
        }
    }

    private static void collect(RenderLevelStageEvent event) {
        boolean hadLights = !FRAME_LIGHTS.isEmpty();
        FRAME_LIGHTS.clear();
        var gather = new CollectLightsEvent(event.getPartialTick());
        OpenLightsApi.snapshot().forEach(gather::add);
        MinecraftForge.EVENT_BUS.post(gather);
        Vec3 camera = event.getCamera().getPosition();
        int limit = Math.min(8, ClientConfig.MAX_LIGHTS.get());
        float maximumRange = ClientConfig.MAX_RANGE.get().floatValue();
        int renderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance();
        AABB view = LightCoverage.view(camera, LightCoverage.distanceBlocks(renderDistance, ClientConfig.LIGHT_RENDER_DISTANCE.get()));
        WORLD_LIGHTING.collect(gather.lights().values(), camera, renderDistance, maximumRange);
        gather.lights().entrySet().stream()
                .filter(entry -> entry.getValue().intensity() > 0)
                .map(entry -> Map.entry(entry.getKey(), bounded(entry.getValue(), maximumRange)))
                .filter(entry -> {
                    var light = entry.getValue();
                    AABB bounds = LightCoverage.renderingBounds(light);
                    return bounds.intersects(view) && event.getFrustum().isVisible(bounds);
                })
                .sorted(Comparator.comparingDouble(entry -> entry.getValue().position().distanceToSqr(camera)))
                .limit(limit).forEach(entry -> FRAME_LIGHTS.put(entry.getKey(),entry.getValue()));
        SHADOWS.keySet().retainAll(FRAME_LIGHTS.keySet());
        if (hadLights && FRAME_LIGHTS.isEmpty()) { SCENE.clear(); uploadedRevision=Long.MIN_VALUE; vertexCount=0; }
    }

    private static void initialize() throws Exception {
        if (lighting != null) return;
        depthCopy = new GlProgram("fullscreen.vsh","depth_copy.fsh");
        shadow = new GlProgram("shadow.vsh","shadow.fsh");
        lighting = new GlProgram("fullscreen.vsh","lighting.fsh");
        composite = new GlProgram("fullscreen.vsh","composite.fsh");
        mediumBuffer=GL15.glGenBuffers();
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER,mediumBuffer);
        GL15.glBufferData(GL31.GL_UNIFORM_BUFFER,1536,GL15.GL_DYNAMIC_DRAW);
        GL31.glUniformBlockBinding(lighting.id,GL31.glGetUniformBlockIndex(lighting.id,"MediumBlock"),0);
        fullScreenVao = GL30.glGenVertexArrays();
        geometryVao = GL30.glGenVertexArrays();
        geometryBuffer = GL15.glGenBuffers();
        GL30.glBindVertexArray(geometryVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, geometryBuffer);
        GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,12,0);
        GL20.glEnableVertexAttribArray(0);
        for (int i=0;i<timingQueries.length;i++) timingQueries[i] = GL15.glGenQueries();
        LOGGER.info("Open Lights initialized independent renderer on {}", GL11.glGetString(GL11.GL_RENDERER));
    }

    private static void prepareTargets() {
        var target = Minecraft.getInstance().getMainRenderTarget();
        if(TARGETS.resize(target.width,target.height,ClientConfig.RENDER_SCALE.get().floatValue(),
                ClientConfig.SHADOW_RESOLUTION.get())) { SHADOWS.clear(); depthReady = false; }
    }

    private static void copyDepth(int source) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,TARGETS.vanillaFbo);
        GL11.glViewport(0,0,TARGETS.width,TARGETS.height);
        screenState();
        depthCopy.bind();
        texture(0,GL11.GL_TEXTURE_2D,source);
        depthCopy.integer("SourceDepth",0);
        depthCopy.integer("MergeDistant",0);
        depthCopy.matrix("InverseProjection",new Matrix4f(PROJECTION).invert());
        drawScreen();
    }

    private static void mergeDepth() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,TARGETS.opaqueFbo);
        GL11.glViewport(0,0,TARGETS.width,TARGETS.height);
        screenState();
        depthCopy.bind();
        texture(0,GL11.GL_TEXTURE_2D,TARGETS.vanillaDepth);
        depthCopy.integer("SourceDepth",0);
        depthCopy.matrix("InverseProjection",new Matrix4f(PROJECTION).invert());
        var distant = DistantHorizonsCompatibility.snapshot();
        boolean usable = distant != null && GL11.glIsTexture(distant.depthTexture());
        depthCopy.integer("MergeDistant",usable?1:0);
        if (usable) {
            texture(1,GL11.GL_TEXTURE_2D,distant.depthTexture());
            depthCopy.integer("DistantDepth",1);
            depthCopy.integer("DistantReversedDepth",distant.reversedDepth()?1:0);
            depthCopy.integer("DistantZeroToOneDepth",distant.zeroToOneDepth()?1:0);
            depthCopy.matrix("DistantInverseProjection",distant.inverseProjection());
            depthCopy.matrix("DistantToVanillaView",new Matrix4f(VIEW).mul(distant.inverseView()));
            depthCopy.matrix("Projection",PROJECTION);
            distantDepthTexture = distant.depthTexture();
        }
        distantDepthMerged = usable;
        drawScreen();
    }

    private static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 camera = event.getCamera().getPosition();
        SceneSnapshot scene = SCENE.update(mc.level,camera,32,List.copyOf(FRAME_LIGHTS.values()),cacheTick);
        WORLD_LIGHTING.update(mc.level, camera, cacheTick);
        if (uploadedRevision != scene.revision()) {
            float[] vertices = scene.opaqueTriangles();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,geometryBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER,vertices,GL15.GL_DYNAMIC_DRAW);
            vertexCount = vertices.length/3;
            uploadedRevision = scene.revision();
        }
        float[] positions=new float[32],colors=new float[32],directions=new float[32],
                ups=new float[32],shapes=new float[32],volumes=new float[32],matrices=new float[24*16];
        float[] innerColors=new float[32],outerColors=new float[32],innerShapes=new float[32],outerShapes=new float[32];
        float[] boundsMinimum=new float[32],boundsMaximum=new float[32];
        int index=0, shadowSlot=0, passes=0;
        double fogDistanceSquared = 0;
        int shadowLimit=Math.min(4,ClientConfig.MAX_SHADOW_LIGHTS.get());
        for (var entry : FRAME_LIGHTS.entrySet()) {
            LightDefinition light=entry.getValue();
            Vec3 local=light.position().subtract(scene.origin());
            AABB influence=LightCoverage.renderingBounds(light);
            if (light.volumetricStrength() > 0 && light.intensity() > 0 && light.range() > 0) {
                double dx = Math.max(Math.abs(influence.minX-camera.x), Math.abs(influence.maxX-camera.x));
                double dy = Math.max(Math.abs(influence.minY-camera.y), Math.abs(influence.maxY-camera.y));
                double dz = Math.max(Math.abs(influence.minZ-camera.z), Math.abs(influence.maxZ-camera.z));
                fogDistanceSquared = Math.max(fogDistanceSquared, dx*dx+dy*dy+dz*dz);
            }
            put(boundsMinimum,index,new Vec3(influence.minX,influence.minY,influence.minZ).subtract(scene.origin()),0);
            put(boundsMaximum,index,new Vec3(influence.maxX,influence.maxY,influence.maxZ).subtract(scene.origin()),0);
            put(positions,index,local,light.range());
            put(colors,index,light.color(),light.intensity()*ClientConfig.INTENSITY_MULTIPLIER.get().floatValue());
            Vec3 forward=new Vec3(0,0,-1),up=new Vec3(0,1,0);
            float outer=-1,inner=-1,type=0,width=0,height=0,spread=-1;
            if (light instanceof LightDefinition.Spot spot) {
                forward=spot.forward();up=spot.up();type=1;
                outer=cos(spot.outerConeAngleDegrees());inner=cos(spot.innerConeAngleDegrees());
                if(spot.beamProfile()!=null) {
                    width=1;
                    beamLayer(innerColors,innerShapes,index,spot.beamProfile().inner(),light.range());
                    beamLayer(outerColors,outerShapes,index,spot.beamProfile().outer(),light.range());
                }
            } else if (light instanceof LightDefinition.Area area) {
                forward=area.forward();up=area.up();type=2;width=area.width();height=area.height();
                spread=cos(area.spreadAngleDegrees());
            }
            put(directions,index,forward,outer);put(ups,index,up,inner);
            shapes[index*4]=type;shapes[index*4+1]=width;shapes[index*4+2]=height;shapes[index*4+3]=spread;
            volumes[index*4]=light.volumetricStrength();volumes[index*4+1]=-1;
            if (light.shadows() && shadowSlot<shadowLimit) {
                int base=shadowSlot*6;
                List<Matrix4f> views=ShadowViews.create(light,scene.origin());
                for (int face=0;face<views.size();face++) views.get(face).get(matrices,(base+face)*16);
                volumes[index*4+1]=base;volumes[index*4+2]=views.size();
                ShadowCache cached=SHADOWS.get(entry.getKey());
                // Moving lights update immediately. Stationary lights reuse GPU depth until geometry changes.
                if (cached==null || cached.slot()!=shadowSlot || cached.revision()!=scene.revision()
                        || !cached.definition().equals(light)) {
                    passes += drawShadows(base,views);
                    SHADOWS.put(entry.getKey(),new ShadowCache(shadowSlot,scene.revision(),light));
                }
                shadowSlot++;
            } else {
                // A light without a current slot must not retain ownership of overwritten depth.
                SHADOWS.remove(entry.getKey());
            }
            index++;
        }
        var target=mc.getMainRenderTarget();
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,target.frameBufferId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,TARGETS.sceneFbo);
        GL30.glBlitFramebuffer(0,0,TARGETS.width,TARGETS.height,0,0,TARGETS.width,TARGETS.height,
                GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,TARGETS.lightingFbo);
        GL11.glViewport(0,0,TARGETS.lightWidth,TARGETS.lightHeight);
        screenState();
        lighting.bind();
        texture(0,GL11.GL_TEXTURE_2D,TARGETS.opaqueDepth);
        texture(1,GL11.GL_TEXTURE_2D,TARGETS.sceneColor);
        texture(2,GL30.GL_TEXTURE_2D_ARRAY,TARGETS.shadowDepth);
        lighting.integer("OpaqueDepth",0);lighting.integer("SceneColor",1);lighting.integer("ShadowDepth",2);
        lighting.integer("ReplaceLighting",replacementFrame?1:0);
        lighting.integer("KeepNativeSky",nativeSkyFrame?1:0);
        WORLD_LIGHTING.bindLighting(lighting, scene.origin(), nativeLightmap);
        lighting.matrix("InverseProjection",new Matrix4f(PROJECTION).invert());
        lighting.matrix("InverseViewRotation",new Matrix4f(VIEW).invert());
        Vec3 cameraLocal=camera.subtract(scene.origin());
        lighting.vec3("CameraLocal",(float)cameraLocal.x,(float)cameraLocal.y,(float)cameraLocal.z);
        lighting.integer("LightCount",index);lighting.integer("VolumetricSteps",ClientConfig.VOLUMETRIC_STEPS.get());
        int viewDistance = LightCoverage.distanceBlocks(mc.options.getEffectiveRenderDistance(), ClientConfig.LIGHT_RENDER_DISTANCE.get());
        float viewRayLength = (float)(viewDistance * Math.sqrt(3));
        lighting.scalar("ViewRayLength", viewRayLength);
        lighting.scalar("LightViewLimit", ClientConfig.LIGHT_RENDER_DISTANCE.get() > 0 ? viewDistance : 0);
        lighting.vectors("LightPositionRange",positions);lighting.vectors("LightColorIntensity",colors);
        lighting.vectors("LightDirectionOuter",directions);lighting.vectors("LightUpInner",ups);
        lighting.vectors("LightShape",shapes);lighting.vectors("LightVolumeShadow",volumes);
        lighting.vectors("LightBoundsMinimum",boundsMinimum);lighting.vectors("LightBoundsMaximum",boundsMaximum);
        lighting.vectors("InnerBeamColor",innerColors);lighting.vectors("OuterBeamColor",outerColors);
        lighting.vectors("InnerBeamShape",innerShapes);lighting.vectors("OuterBeamShape",outerShapes);
        lighting.matrices("ShadowMatrix",matrices);lighting.vec2("ShadowTexel",1f/TARGETS.resolution,1f/TARGETS.resolution);
        var media = scene.media().stream().sorted(Comparator.comparingDouble(medium ->
                distanceSquared(medium.bounds(),camera))).limit(ClientConfig.MAX_MEDIA.get()).toList();
        float[] minimum=new float[128],maximum=new float[128],tints=new float[128];
        for (int i=0;i<media.size();i++) {
            var medium=media.get(i); var bounds=medium.bounds();
            put(minimum,i,new Vec3(bounds.minX,bounds.minY,bounds.minZ).subtract(scene.origin()),medium.throughput());
            put(maximum,i,new Vec3(bounds.maxX,bounds.maxY,bounds.maxZ).subtract(scene.origin()),medium.densityBoost());
            tints[i*4+3]=medium.densityBoost();
            tints[i*4]=(float)-Math.log(Math.max(.0001,Math.min(1,medium.tint().x*medium.throughput())));
            tints[i*4+1]=(float)-Math.log(Math.max(.0001,Math.min(1,medium.tint().y*medium.throughput())));
            tints[i*4+2]=(float)-Math.log(Math.max(.0001,Math.min(1,medium.tint().z*medium.throughput())));
        }
        lighting.integer("MediumCount",media.size());
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER,mediumBuffer);
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()) {
            var data=stack.mallocFloat(384);
            data.put(minimum).put(maximum).put(tints).flip();
            GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER,0,data);
        }
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER,0,mediumBuffer);
        drawScreen();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,target.frameBufferId);
        GL11.glViewport(0,0,TARGETS.width,TARGETS.height);
        composite.bind();
        texture(0,GL11.GL_TEXTURE_2D,TARGETS.sceneColor);texture(1,GL11.GL_TEXTURE_2D,TARGETS.lighting);
        texture(2,GL11.GL_TEXTURE_2D,TARGETS.opaqueDepth);
        composite.integer("SceneColor",0);composite.integer("LightTexture",1);composite.integer("OpaqueDepth",2);
        composite.integer("ReplaceLighting", replacementFrame ? 1 : 0);
        composite.integer("KeepNativeSky", nativeSkyFrame ? 1 : 0);
        WORLD_LIGHTING.bindComposite(composite, scene.origin(), nativeLightmap);
        composite.matrix("InverseProjection", new Matrix4f(PROJECTION).invert());
        composite.matrix("InverseViewRotation", new Matrix4f(VIEW).invert());
        composite.vec3("CameraLocal", (float)cameraLocal.x, (float)cameraLocal.y, (float)cameraLocal.z);
        composite.vec2("LightTexel",1f/TARGETS.lightWidth,1f/TARGETS.lightHeight);
        composite.scalar("BloomStrength",ClientConfig.BLOOM_STRENGTH.get().floatValue());
        // Beyond every selected fog bound, sky and terrain share the complete foreground interval.
        composite.scalar("UnclippedFogDistance", Math.min(viewRayLength, Math.nextUp((float)Math.sqrt(fogDistanceSquared))));
        drawScreen();
        statistics=new Statistics(index,passes,vertexCount/3,media.size(),0,gpuMillis);
    }

    private static int drawShadows(int base,List<Matrix4f> views) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,TARGETS.shadowFbo);
        GL11.glViewport(0,0,TARGETS.resolution,TARGETS.resolution);
        GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);GL11.glDepthFunc(GL11.GL_LESS);
        GL11.glColorMask(false,false,false,false);
        GL30.glBindVertexArray(geometryVao);
        shadow.bind();
        for (int i=0;i<views.size();i++) {
            GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,TARGETS.shadowDepth,0,base+i);
            GL11.glClearDepth(1);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            shadow.matrix("LightMatrix",views.get(i));
            GL11.glDrawArrays(GL11.GL_TRIANGLES,0,vertexCount);
        }
        GL11.glColorMask(true,true,true,true);
        return views.size();
    }

    private static LightDefinition bounded(LightDefinition light,float range) {
        if(light.range()<=range)return light;
        if(light instanceof LightDefinition.Point point)return new LightDefinition.Point(point.position(),point.color(),
                point.intensity(),range,point.shadows(),point.volumetricStrength());
        if(light instanceof LightDefinition.Spot spot)return new LightDefinition.Spot(spot.position(),spot.forward(),spot.up(),
                spot.color(),spot.intensity(),range,spot.outerConeAngleDegrees(),spot.innerConeAngleDegrees(),spot.shadows(),spot.volumetricStrength(),spot.beamProfile());
        var area=(LightDefinition.Area)light;
        return new LightDefinition.Area(area.position(),area.forward(),area.up(),area.color(),area.intensity(),range,
                area.width(),area.height(),area.spreadAngleDegrees(),area.shadows(),area.volumetricStrength());
    }

    private static void beamLayer(float[] colors,float[] shapes,int index,
                                  com.cappleapple.openlights.beam.BeamProfile.Layer layer,float range) {
        put(colors,index,layer.color(),layer.intensity());
        shapes[index*4]=cos(layer.angleDegrees());
        shapes[index*4+1]=cos(layer.angleDegrees()*(1-layer.edgeSoftness()));
        shapes[index*4+2]=Math.min(range,layer.range());
        shapes[index*4+3]=layer.falloff();
    }
    private static double distanceSquared(AABB box,Vec3 point) {
        double x=Math.max(0,Math.max(box.minX-point.x,point.x-box.maxX));
        double y=Math.max(0,Math.max(box.minY-point.y,point.y-box.maxY));
        double z=Math.max(0,Math.max(box.minZ-point.z,point.z-box.maxZ));
        return x*x+y*y+z*z;
    }
    private static void put(float[] array,int index,Vec3 vector,float last) {
        array[index*4]=(float)vector.x;array[index*4+1]=(float)vector.y;array[index*4+2]=(float)vector.z;array[index*4+3]=last;
    }
    private static float cos(float fullDegrees) { return (float)Math.cos(Math.toRadians(fullDegrees*.5)); }
    private static void texture(int unit,int target,int id) { GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glBindTexture(target,id); }
    private static void screenState() { GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);GL11.glDisable(GL11.GL_BLEND); }
    private static void drawScreen() { GL30.glBindVertexArray(fullScreenVao);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3); }
    private static void fail(Exception exception) {
        failed=true;
        replacementFrame = false; endWorld();
        LOGGER.error("Open Lights disabled its renderer after an error; reload resources to retry",exception);
    }
    public static void invalidate(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
        if (!FRAME_LIGHTS.isEmpty()) SCENE.invalidateRegion(minX,minY,minZ,maxX,maxY,maxZ);
        WORLD_LIGHTING.invalidate(minX,minY,minZ,maxX,maxY,maxZ);
    }

    public static void invalidateLightSection(net.minecraft.world.level.LightLayer layer, net.minecraft.core.SectionPos section) {
        WORLD_LIGHTING.invalidateLightSection(layer, section);
    }
    public static void clearWorld() {
        WORLD_LIGHTING.clear();
        FRAME_LIGHTS.clear();SHADOWS.clear();SCENE.clear();OpenLightsApi.clear();
        replacementFrame = false; worldPass = false; nativeSkyFrame = false;
        DistantHorizonsCompatibility.clearFrame();
        distantDepthMerged = false; distantDepthTexture = 0;
        cacheTick = 0;
        uploadedRevision=Long.MIN_VALUE;depthReady=false;matricesReady=false;
    }
    public static void reload() {
        WORLD_LIGHTING.close();
        clearWorld();TARGETS.close();
        com.cappleapple.openlights.client.scene.TextureColors.clear();
        if (nativeLightmap != 0) RenderSystem.setShaderTexture(2, nativeLightmap);
        for (GlProgram program:new GlProgram[]{depthCopy,shadow,lighting,composite}) if(program!=null)program.close();
        depthCopy=shadow=lighting=composite=null;
        if(fullScreenVao!=0)GL30.glDeleteVertexArrays(fullScreenVao);
        if(geometryVao!=0)GL30.glDeleteVertexArrays(geometryVao);
        if(geometryBuffer!=0)GL15.glDeleteBuffers(geometryBuffer);
        if(mediumBuffer!=0)GL15.glDeleteBuffers(mediumBuffer);mediumBuffer=0;
        for(int query:timingQueries)if(query!=0)GL15.glDeleteQueries(query);
        Arrays.fill(timingQueries,0);Arrays.fill(queryIssued,false);fullScreenVao=geometryVao=geometryBuffer=0;
        failed=false;
    }
    private record ShadowCache(int slot,long revision,LightDefinition definition) {}
}
