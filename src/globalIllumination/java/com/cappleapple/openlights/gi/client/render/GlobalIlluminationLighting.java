package com.cappleapple.openlights.gi.client.render;

import com.cappleapple.openlights.client.render.*;
import com.cappleapple.openlights.api.client.LightDefinition;
import com.cappleapple.openlights.api.client.ShaderCompatibility;
import com.cappleapple.openlights.gi.client.scene.*;
import com.cappleapple.openlights.client.scene.LightCoverage;
import com.cappleapple.openlights.gi.config.GlobalIlluminationConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.*;
import java.util.*;

/** Owns every world/probe cache and its GPU textures. Only constructed by the GI addon. */
public final class GlobalIlluminationLighting implements WorldLightingExtension {
    private static final WorldLightCache WORLD_LIGHT = new WorldLightCache();
    private static final IndirectLightCache INDIRECT = new IndirectLightCache();
    private final ProbeTexture worldTexture = new ProbeTexture(), nearTexture = new ProbeTexture(), giTexture = new ProbeTexture();
    private final ProbeTexture colorTexture = new ProbeTexture(), nearColorTexture = new ProbeTexture(), farGiTexture = new ProbeTexture();
    private final BlockSectionTexture blockTexture = new BlockSectionTexture();
    private List<LightDefinition> sources = List.of();
    private static int neutralLightmap, skyLightmap;
    private boolean replacement, nativeSky;
    private double worldMillis;

    @Override public void beginWorld(long tick) {
        replacement = false; nativeSky = false;
        var mc = Minecraft.getInstance();
        if (!GlobalIlluminationConfig.GI_ENABLED.get() && INDIRECT.grid() != null) INDIRECT.clear();
        if (GlobalIlluminationConfig.LIGHTING_MODE.get() == GlobalIlluminationConfig.LightingMode.CACHED
                && !ShaderCompatibility.isShaderPackInUse()) {
            long started = System.nanoTime();
            try (var ignored = new GlState()) {
                initializeLightmap();
                WORLD_LIGHT.update(mc.level,mc.gameRenderer.getMainCamera().getPosition(),mc.options.getEffectiveRenderDistance(),tick);
                replacement = true; nativeSky = DistantHorizonsCompatibility.preserveNativeSky();
            }
            worldMillis = (System.nanoTime()-started)/1_000_000.0;
        } else { WORLD_LIGHT.clear(); worldMillis = 0; }
    }
    private void initializeLightmap() {
        if (neutralLightmap != 0) return;
        neutralLightmap = GL11.glGenTextures();
        GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glBindTexture(GL11.GL_TEXTURE_2D,neutralLightmap);
        float[] neutral = new float[16*16*4]; Arrays.fill(neutral,1);
        try (var ignored = new TextureUploadState()) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,16,16,0,GL11.GL_RGBA,GL11.GL_FLOAT,neutral);
        }
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
    }
    @Override public boolean replacement() { return replacement; }
    @Override public boolean nativeSky() { return replacement && nativeSky; }
    @Override public boolean hasWork() { return replacement || GlobalIlluminationConfig.GI_ENABLED.get(); }
    @Override public void nativeLightmap(DynamicTexture texture, boolean worldPass) {
        FirstPersonLighting.palette(texture);
        if (worldPass && replacement && nativeSky) {
            skyLightmap = NativeSkyLightmap.update(texture);
            if (skyLightmap == 0) nativeSky = false;
        }
    }
    @Override public int worldLightmap(boolean worldPass) {
        return worldPass && replacement ? (nativeSky ? skyLightmap : neutralLightmap) : FirstPersonLighting.texture();
    }
    @Override public void beginHand(boolean enabled) {
        try { FirstPersonLighting.begin(WORLD_LIGHT,replacement && enabled && !ShaderCompatibility.isShaderPackInUse()); }
        catch (Exception error) { FirstPersonLighting.end(); com.mojang.logging.LogUtils.getLogger().error("Unable to update first-person lighting",error); }
    }
    @Override public void endHand() { FirstPersonLighting.end(); }
    @Override public void collect(Collection<LightDefinition> lights, Vec3 camera, int renderDistance, float maximumRange) {
        var view = LightCoverage.view(camera,renderDistance*16.0+GlobalIlluminationConfig.GI_TRACE_DISTANCE.get());
        sources = lights.stream().filter(light -> light.intensity()>0)
                .map(light -> bounded(light,maximumRange))
                .filter(light -> LightCoverage.bounds(light).intersects(view))
                .sorted(Comparator.comparingDouble(light -> light.position().distanceToSqr(camera)))
                .limit(GlobalIlluminationConfig.GI_SOURCE_LIMIT.get()).toList();
    }
    @Override public void update(ClientLevel level, Vec3 camera, long tick) {
        if (GlobalIlluminationConfig.GI_ENABLED.get()) {
            INDIRECT.update(level,camera,sources,tick,replacement ? WORLD_LIGHT.aggregates() : null);
            giTexture.upload(INDIRECT.grid(),INDIRECT.revision());
            farGiTexture.upload(INDIRECT.far(),INDIRECT.revision());
        } else INDIRECT.clear();
        worldTexture.upload(WORLD_LIGHT.grid(),WORLD_LIGHT.revision()); nearTexture.upload(WORLD_LIGHT.near(),WORLD_LIGHT.revision());
        colorTexture.upload(WORLD_LIGHT.color(),WORLD_LIGHT.revision()); nearColorTexture.upload(WORLD_LIGHT.nearColor(),WORLD_LIGHT.revision());
    }
    @Override public void bindLighting(GlProgram shader, Vec3 origin, int nativeLightmap) {
        worldTexture.bind(shader,"SkyWorld",5,WORLD_LIGHT.grid(),origin); nearTexture.bind(shader,"SkyNear",6,WORLD_LIGHT.near(),origin);
        GL13.glActiveTexture(GL13.GL_TEXTURE7); GL11.glBindTexture(GL11.GL_TEXTURE_2D,nativeLightmap); shader.integer("SkyLightmap",7);
        giTexture.bind(shader,"Gi",3,INDIRECT.grid(),origin); farGiTexture.bind(shader,"FarGi",4,INDIRECT.far(),origin);
        shader.scalar("GiStrength",GlobalIlluminationConfig.GI_ENABLED.get() ? GlobalIlluminationConfig.GI_STRENGTH.get().floatValue() : 0);
    }
    @Override public void bindComposite(GlProgram shader, Vec3 origin, int nativeLightmap) {
        shader.integer("BlockLightStyle",WORLD_LIGHT.aggregates().analytic()?1:0);
        shader.scalar("BlockLightIntensity",2f*GlobalIlluminationConfig.INTENSITY_MULTIPLIER.get().floatValue()*GlobalIlluminationConfig.BLOCK_LIGHT_EXPOSURE.get().floatValue());
        worldTexture.bind(shader,"World",3,WORLD_LIGHT.grid(),origin); nearTexture.bind(shader,"Near",5,WORLD_LIGHT.near(),origin);
        colorTexture.bind(shader,"Color",6,WORLD_LIGHT.color(),origin); nearColorTexture.bind(shader,"NearColor",7,WORLD_LIGHT.nearColor(),origin);
        blockTexture.bind(shader,WORLD_LIGHT.blocks(),origin);
        GL13.glActiveTexture(GL13.GL_TEXTURE4); GL11.glBindTexture(GL11.GL_TEXTURE_2D,nativeLightmap); shader.integer("NativeLightmap",4);
    }
    @Override public void invalidate(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
        WORLD_LIGHT.aggregates().invalidate(minX,minY,minZ,maxX,maxY,maxZ);
        WORLD_LIGHT.invalidateRegion(minX-15,minY-15,minZ-15,maxX+15,maxY+15,maxZ+15);
        ColoredLightCache.INSTANCE.invalidate(minX,minY,minZ,maxX,maxY,maxZ);
        INDIRECT.invalidateRegion(minX,minY,minZ,maxX,maxY,maxZ,true);
    }
    @Override public void invalidateLightSection(LightLayer layer, SectionPos section) {
        int x=section.minBlockX(),y=section.minBlockY(),z=section.minBlockZ();
        if (layer==LightLayer.BLOCK) {
            WORLD_LIGHT.invalidateRegion(x,y,z,x+15,y+15,z+15);
            ColoredLightCache.INSTANCE.invalidate(x,y,z,x+15,y+15,z+15);
            WORLD_LIGHT.invalidateRegion(x-15,y-15,z-15,x+30,y+30,z+30);
            INDIRECT.invalidateRegion(x,y,z,x+15,y+15,z+15,false);
        } else WORLD_LIGHT.invalidateSkyRegion(x,y,z,x+15,y+15,z+15);
    }
    @Override public OpenLightRenderer.CacheStatistics statistics(int sceneUpdated) {
        return new OpenLightRenderer.CacheStatistics(replacement,WORLD_LIGHT.grid()==null?0:WORLD_LIGHT.grid().populated(),WORLD_LIGHT.grid()==null?0:WORLD_LIGHT.grid().count(),
                INDIRECT.grid()==null?0:INDIRECT.grid().populated(),INDIRECT.updated(),worldMillis,INDIRECT.millis(),WORLD_LIGHT.updated(),sceneUpdated,ColoredLightCache.INSTANCE.updated(),
                INDIRECT.far()==null?0:INDIRECT.far().populated(),INDIRECT.far()==null?0:INDIRECT.far().count(),WORLD_LIGHT.blocks().tiles().size(),WORLD_LIGHT.blocks().pending(),WORLD_LIGHT.aggregates().cells(),
                WORLD_LIGHT.aggregates().updated(),WORLD_LIGHT.aggregates().pending(),WORLD_LIGHT.aggregates().limited(),WORLD_LIGHT.aggregates().workerActive(),WORLD_LIGHT.aggregates().captured(),WORLD_LIGHT.aggregates().applied(),
                WORLD_LIGHT.aggregates().discarded(),blockTexture.uploaded(),blockTexture.pending());
    }
    @Override public void clear() {
        FirstPersonLighting.end(); WORLD_LIGHT.clear(); INDIRECT.clear(); sources=List.of(); ColoredLightCache.INSTANCE.clear();
        replacement=false; nativeSky=false; worldMillis=0; NativeSkyLightmap.close(); skyLightmap=0;
    }
    @Override public void close() {
        FirstPersonLighting.close(); clear();
        worldTexture.close(); nearTexture.close(); giTexture.close(); colorTexture.close(); nearColorTexture.close(); farGiTexture.close(); blockTexture.close();
        if (neutralLightmap!=0) com.mojang.blaze3d.platform.GlStateManager._deleteTexture(neutralLightmap); neutralLightmap=0;
    }
    private static LightDefinition bounded(LightDefinition light,float range) {
        if(light.range()<=range)return light;
        if(light instanceof LightDefinition.Point point)return new LightDefinition.Point(point.position(),point.color(),point.intensity(),range,point.shadows(),point.volumetricStrength());
        if(light instanceof LightDefinition.Spot spot)return new LightDefinition.Spot(spot.position(),spot.forward(),spot.up(),spot.color(),spot.intensity(),range,spot.outerConeAngleDegrees(),spot.innerConeAngleDegrees(),spot.shadows(),spot.volumetricStrength(),spot.beamProfile());
        var area=(LightDefinition.Area)light;
        return new LightDefinition.Area(area.position(),area.forward(),area.up(),area.color(),area.intensity(),range,area.width(),area.height(),area.spreadAngleDegrees(),area.shadows(),area.volumetricStrength());
    }
}
