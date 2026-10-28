package com.cappleapple.openlights.client.render;

import com.cappleapple.openlights.api.client.LightDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import java.util.Collection;

/** Internal client-render-thread bridge for the separately packaged world-lighting addon. */
public interface WorldLightingExtension {
    WorldLightingExtension NONE = new WorldLightingExtension() {};
    default void beginWorld(long tick) {}
    default boolean replacement() { return false; }
    default boolean nativeSky() { return false; }
    default boolean hasWork() { return false; }
    default void nativeLightmap(DynamicTexture texture, boolean worldPass) {}
    default int worldLightmap(boolean worldPass) { return 0; }
    default void beginHand(boolean enabled) {}
    default void endHand() {}
    default void collect(Collection<LightDefinition> lights, Vec3 camera, int renderDistance, float maximumRange) {}
    default void update(ClientLevel level, Vec3 camera, long tick) {}
    default void bindLighting(GlProgram shader, Vec3 origin, int nativeLightmap) {
        // All sampler types need distinct compatible units even when the cached branches are disabled.
        shader.integer("SkyWorldTexture",5); shader.integer("SkyNearTexture",6);
        shader.integer("SkyLightmap",7); shader.integer("GiTexture",3); shader.integer("FarGiTexture",4);
        shader.scalar("GiStrength",0);
    }
    default void bindComposite(GlProgram shader, Vec3 origin, int nativeLightmap) {
        shader.integer("WorldTexture",3); shader.integer("NativeLightmap",4);
        shader.integer("NearTexture",5); shader.integer("ColorTexture",6); shader.integer("NearColorTexture",7);
        shader.integer("BlockSectionAtlas",8); shader.integer("BlockSectionTable",9);
        shader.integer("AggregateSectionAtlas",10); shader.integer("BlockDirectionAtlas",11);
    }
    default void invalidate(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {}
    default void invalidateLightSection(LightLayer layer, SectionPos section) {}
    default void clear() {}
    default void close() { clear(); }
    default OpenLightRenderer.CacheStatistics statistics(int sceneUpdated) {
        return new OpenLightRenderer.CacheStatistics(false,0,0,0,0,0,0,0,sceneUpdated,0,0,0,0,0,0,0,false,false,false,0,0,0,0,false);
    }
}
