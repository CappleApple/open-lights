package com.cappleapple.openlights.client.render;

import com.cappleapple.openlights.api.client.CollectLightsEvent;
import com.cappleapple.openlights.api.client.LightDefinition;
import com.cappleapple.openlights.api.client.LightKey;
import com.cappleapple.openlights.api.client.OpenLightsApi;
import com.cappleapple.openlights.config.ClientConfig;
import com.cappleapple.openlights.client.scene.SceneCache;
import com.cappleapple.openlights.client.scene.SceneSnapshot;
import com.cappleapple.openlights.client.scene.WorldLightCache;
import com.cappleapple.openlights.client.scene.IndirectLightCache;
import com.cappleapple.openlights.client.scene.LightCoverage;
import com.cappleapple.openlights.api.client.ShaderCompatibility;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import org.slf4j.Logger;

import java.util.*;

/** Deferred light renderer with normal-chunk shading and optional Distant Horizons receivers. */
public final class OpenLightRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final RenderTargets TARGETS = new RenderTargets();
    private static final SceneCache SCENE = new SceneCache();
    private static final WorldLightCache WORLD_LIGHT = new WorldLightCache();
    private static final IndirectLightCache INDIRECT = new IndirectLightCache();
    private static final ProbeTexture WORLD_TEXTURE = new ProbeTexture(), NEAR_TEXTURE = new ProbeTexture(), GI_TEXTURE = new ProbeTexture();
    private static final ProbeTexture COLOR_TEXTURE = new ProbeTexture(), NEAR_COLOR_TEXTURE = new ProbeTexture();
    private static final ProbeTexture FAR_GI_TEXTURE = new ProbeTexture();
    private static final BlockSectionTexture BLOCK_TEXTURE = new BlockSectionTexture();
    private static List<LightDefinition> indirectSources = List.of();
    private static int neutralLightmap, nativeLightmap, skyLightmap;
    private static boolean worldPass, replacementFrame, nativeSkyFrame;
    private static double worldCacheMillis;
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
        return new CacheStatistics(replacementFrame, WORLD_LIGHT.grid() == null ? 0 : WORLD_LIGHT.grid().populated(),
                WORLD_LIGHT.grid() == null ? 0 : WORLD_LIGHT.grid().count(),
                INDIRECT.grid() == null ? 0 : INDIRECT.grid().populated(), INDIRECT.updated(), worldCacheMillis, INDIRECT.millis(),
                WORLD_LIGHT.updated(), SCENE.updated(), com.cappleapple.openlights.client.scene.ColoredLightCache.INSTANCE.updated(),
                INDIRECT.far() == null ? 0 : INDIRECT.far().populated(), INDIRECT.far() == null ? 0 : INDIRECT.far().count(),
                WORLD_LIGHT.blocks().tiles().size(), WORLD_LIGHT.blocks().pending(), WORLD_LIGHT.aggregates().cells(),
                WORLD_LIGHT.aggregates().updated(), WORLD_LIGHT.aggregates().pending(), WORLD_LIGHT.aggregates().limited(),
                WORLD_LIGHT.aggregates().workerActive(), WORLD_LIGHT.aggregates().captured(),WORLD_LIGHT.aggregates().applied(),
                WORLD_LIGHT.aggregates().discarded(),BLOCK_TEXTURE.uploaded(),BLOCK_TEXTURE.pending());
    }

    public static void nativeLightmap(net.minecraft.client.renderer.texture.DynamicTexture texture) {
        nativeLightmap = texture.getId();
        FirstPersonLighting.palette(texture);
        if (worldPass && replacementFrame && nativeSkyFrame) {
            skyLightmap = NativeSkyLightmap.update(texture);
            if (skyLightmap == 0) nativeSkyFrame = false;
        }
    }
    public static int worldLightmap() {
        return worldPass && replacementFrame ? (nativeSkyFrame ? skyLightmap : neutralLightmap) : FirstPersonLighting.texture();
    }
    /** Internal diagnostics for the ambient palette used by this world frame. */
    public static boolean nativeSkyLighting() { return replacementFrame && nativeSkyFrame; }
    public static void beginHand() {
        try {FirstPersonLighting.begin(WORLD_LIGHT,replacementFrame&&!failed&&ClientConfig.ENABLED.get()&&!ShaderCompatibility.isShaderPackInUse());}
        catch(Exception exception){FirstPersonLighting.end();LOGGER.error("Unable to update first-person lighting",exception);}
    }
    public static void endHand() {FirstPersonLighting.end();if(nativeLightmap!=0)RenderSystem.setShaderTexture(2,nativeLightmap);}
    public static void beginWorld() {
        worldPass = true; replacementFrame = false; nativeSkyFrame = false;
        Minecraft mc = Minecraft.getInstance();
        if (!ClientConfig.GI_ENABLED.get() && INDIRECT.grid() != null) INDIRECT.clear();
        if (mc.level == null || failed || !ClientConfig.ENABLED.get() || ShaderCompatibility.isRenderingShadowPass()) return;
        distantDepthMerged = false; distantDepthTexture = 0;
        DistantHorizonsCompatibility.beginFrame();
        if (ClientConfig.LIGHTING_MODE.get() == ClientConfig.LightingMode.CACHED && !ShaderCompatibility.isShaderPackInUse()) {
            long start = System.nanoTime();
            try {
                // Take ownership before any geometry, including the first frame after a reload.
                // Cache population controls quality, never which renderer owns the world.
                if (lighting == null) try (var ignored = new GlState()) { initialize(); }
                WORLD_LIGHT.update(mc.level, mc.gameRenderer.getMainCamera().getPosition(), mc.options.getEffectiveRenderDistance(), cacheTick);
                replacementFrame = true;
                nativeSkyFrame = DistantHorizonsCompatibility.preserveNativeSky();
                mc.gameRenderer.lightTexture().turnOnLightLayer();
            } catch (Exception exception) { fail(exception); }
            worldCacheMillis = (System.nanoTime() - start) / 1_000_000.0;
        } else { WORLD_LIGHT.clear(); worldCacheMillis = 0; }
    }
    public static void endWorld() {
        worldPass = false;
        if (nativeLightmap != 0) RenderSystem.setShaderTexture(2, nativeLightmap);
    }
    private static boolean hasWork() {
        return !FRAME_LIGHTS.isEmpty() || replacementFrame || ClientConfig.GI_ENABLED.get()
                || ClientConfig.LIGHTING_MODE.get() == ClientConfig.LightingMode.CACHED;
    }

    public static void stage(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !ClientConfig.ENABLED.get() || failed) return;
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            depthReady = false;
            matricesReady = true;
            VIEW.set(event.getModelViewMatrix()).setTranslation(0,0,0);
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
        FRAME_LIGHTS.clear();
        var gather = new CollectLightsEvent(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        OpenLightsApi.snapshot().forEach(gather::add);
        NeoForge.EVENT_BUS.post(gather);
        Vec3 camera = event.getCamera().getPosition();
        int limit = Math.min(8, ClientConfig.MAX_LIGHTS.get());
        float maximumRange = ClientConfig.MAX_RANGE.get().floatValue();
        int renderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance();
        AABB view = LightCoverage.view(camera, LightCoverage.distanceBlocks(renderDistance, ClientConfig.LIGHT_RENDER_DISTANCE.get()));
        AABB giView = LightCoverage.view(camera, renderDistance * 16.0 + ClientConfig.GI_TRACE_DISTANCE.get());
        indirectSources = gather.lights().values().stream().filter(light -> light.intensity() > 0)
                .map(light -> bounded(light, maximumRange))
                .filter(light -> LightCoverage.bounds(light).intersects(giView))
                .sorted(Comparator.comparingDouble(light -> light.position().distanceToSqr(camera)))
                .limit(ClientConfig.GI_SOURCE_LIMIT.get()).toList();
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
    }

    private static void initialize() throws Exception {
        if (lighting != null) return;
        depthCopy = new GlProgram("fullscreen.vsh","depth_copy.fsh");
        shadow = new GlProgram("shadow.vsh","shadow.fsh");
        lighting = new GlProgram("fullscreen.vsh","lighting.fsh");
        composite = new GlProgram("fullscreen.vsh","composite.fsh");
        neutralLightmap = GL11.glGenTextures();
        texture(0, GL11.GL_TEXTURE_2D, neutralLightmap);
        // Terrain shaders use texelFetch with integer light coordinates in [0,15].
        float[] neutral = new float[16 * 16 * 4];
        Arrays.fill(neutral, 1);
        try (var ignored = new TextureUploadState()) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 16, 16, 0, GL11.GL_RGBA, GL11.GL_FLOAT, neutral);
        }
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
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
        if (ClientConfig.GI_ENABLED.get()) {
            INDIRECT.update(mc.level, camera, indirectSources, cacheTick, replacementFrame ? WORLD_LIGHT.aggregates() : null);
            GI_TEXTURE.upload(INDIRECT.grid(), INDIRECT.revision());
            FAR_GI_TEXTURE.upload(INDIRECT.far(), INDIRECT.revision());
        } else INDIRECT.clear();
        WORLD_TEXTURE.upload(WORLD_LIGHT.grid(), WORLD_LIGHT.revision());
        NEAR_TEXTURE.upload(WORLD_LIGHT.near(), WORLD_LIGHT.revision());
        COLOR_TEXTURE.upload(WORLD_LIGHT.color(), WORLD_LIGHT.revision());
        NEAR_COLOR_TEXTURE.upload(WORLD_LIGHT.nearColor(), WORLD_LIGHT.revision());
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
        WORLD_TEXTURE.bind(lighting, "SkyWorld", 5, WORLD_LIGHT.grid(), scene.origin());
        NEAR_TEXTURE.bind(lighting, "SkyNear", 6, WORLD_LIGHT.near(), scene.origin());
        texture(7, GL11.GL_TEXTURE_2D, nativeLightmap);
        lighting.integer("SkyLightmap",7);
        GI_TEXTURE.bind(lighting, "Gi", 3, INDIRECT.grid(), scene.origin());
        FAR_GI_TEXTURE.bind(lighting, "FarGi", 4, INDIRECT.far(), scene.origin());
        lighting.scalar("GiStrength", ClientConfig.GI_ENABLED.get() ? ClientConfig.GI_STRENGTH.get().floatValue() : 0);
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
        composite.integer("BlockLightStyle",WORLD_LIGHT.aggregates().analytic()?1:0);
        composite.scalar("BlockLightIntensity",2f*ClientConfig.INTENSITY_MULTIPLIER.get().floatValue()*ClientConfig.BLOCK_LIGHT_EXPOSURE.get().floatValue());
        WORLD_TEXTURE.bind(composite, "World", 3, WORLD_LIGHT.grid(), scene.origin());
        NEAR_TEXTURE.bind(composite, "Near", 5, WORLD_LIGHT.near(), scene.origin());
        COLOR_TEXTURE.bind(composite, "Color", 6, WORLD_LIGHT.color(), scene.origin());
        NEAR_COLOR_TEXTURE.bind(composite, "NearColor", 7, WORLD_LIGHT.nearColor(), scene.origin());
        BLOCK_TEXTURE.bind(composite, WORLD_LIGHT.blocks(), scene.origin());
        texture(4, GL11.GL_TEXTURE_2D, nativeLightmap);
        composite.integer("NativeLightmap", 4);
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
        WORLD_LIGHT.aggregates().invalidate(minX,minY,minZ,maxX,maxY,maxZ);
        SCENE.invalidateRegion(minX,minY,minZ,maxX,maxY,maxZ);
        WORLD_LIGHT.invalidateRegion(minX-15,minY-15,minZ-15,maxX+15,maxY+15,maxZ+15);
        com.cappleapple.openlights.client.scene.ColoredLightCache.INSTANCE.invalidate(minX,minY,minZ,maxX,maxY,maxZ);
        INDIRECT.invalidateRegion(minX,minY,minZ,maxX,maxY,maxZ,true);
    }

    public static void invalidateLightSection(net.minecraft.world.level.LightLayer layer, net.minecraft.core.SectionPos section) {
        int x = section.minBlockX(), y = section.minBlockY(), z = section.minBlockZ();
        if (layer == net.minecraft.world.level.LightLayer.BLOCK) WORLD_LIGHT.invalidateRegion(x,y,z,x+15,y+15,z+15);
        else WORLD_LIGHT.invalidateSkyRegion(x,y,z,x+15,y+15,z+15);
        if (layer == net.minecraft.world.level.LightLayer.BLOCK) {
            com.cappleapple.openlights.client.scene.ColoredLightCache.INSTANCE.invalidate(x,y,z,x+15,y+15,z+15);
            WORLD_LIGHT.invalidateRegion(x-15,y-15,z-15,x+30,y+30,z+30);
        }
        if (layer == net.minecraft.world.level.LightLayer.BLOCK) INDIRECT.invalidateRegion(x,y,z,x+15,y+15,z+15,false);
    }
    public static void clearWorld() {
        FirstPersonLighting.end();
        FRAME_LIGHTS.clear();SHADOWS.clear();SCENE.clear();OpenLightsApi.clear();
        WORLD_LIGHT.clear(); INDIRECT.clear(); indirectSources = List.of();
        com.cappleapple.openlights.client.scene.ColoredLightCache.INSTANCE.clear();
        replacementFrame = false; worldPass = false; nativeSkyFrame = false;
        NativeSkyLightmap.close(); skyLightmap = 0;
        DistantHorizonsCompatibility.clearFrame();
        distantDepthMerged = false; distantDepthTexture = 0;
        cacheTick = 0;
        uploadedRevision=Long.MIN_VALUE;depthReady=false;matricesReady=false;
    }
    public static void reload() {
        FirstPersonLighting.close();
        clearWorld();TARGETS.close();
        WORLD_TEXTURE.close(); NEAR_TEXTURE.close(); GI_TEXTURE.close();
        COLOR_TEXTURE.close(); NEAR_COLOR_TEXTURE.close(); FAR_GI_TEXTURE.close();
        BLOCK_TEXTURE.close();
        com.cappleapple.openlights.client.scene.TextureColors.clear();
        if (nativeLightmap != 0) RenderSystem.setShaderTexture(2, nativeLightmap);
        if (neutralLightmap != 0) com.mojang.blaze3d.platform.GlStateManager._deleteTexture(neutralLightmap);
        neutralLightmap = 0;
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
