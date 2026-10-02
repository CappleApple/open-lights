package com.cappleapple.openlights.client.render;

import com.cappleapple.openlights.client.scene.ProbeGrid;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.*;

/** Uploaded only when CPU probes change. The frame cost is a trilinear texture lookup. */
final class ProbeTexture implements AutoCloseable {
    private int id, size;
    private long revision = Long.MIN_VALUE;
    void upload(ProbeGrid grid, long version) {
        if (grid == null || revision == version) return;
        GL13.glActiveTexture(GL13.GL_TEXTURE3);
        if (id == 0) id = GL11.glGenTextures();
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, id);
        try (var ignored = new TextureUploadState()) {
        if (size != grid.size) {
            size = grid.size;
            GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_RGBA16F, size, size, size, 0,
                    GL11.GL_RGBA, GL11.GL_FLOAT, (java.nio.ByteBuffer)null);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        }
        GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, 0, 0, 0, size, size, size, GL11.GL_RGBA, GL11.GL_FLOAT, grid.data);
        }
        revision = version;
    }
    void bind(GlProgram shader, String prefix, int unit, ProbeGrid grid, Vec3 origin) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, id);
        shader.integer(prefix + "Texture", unit);
        if (grid == null) return;
        shader.vec3(prefix + "Minimum", (float)(grid.minimumX() + .5 - origin.x),
                (float)(grid.minimumY() + .5 - origin.y), (float)(grid.minimumZ() + .5 - origin.z));
        shader.vec2(prefix + "Grid", grid.spacing, grid.size);
    }
    @Override public void close() {
        if (id != 0) com.mojang.blaze3d.platform.GlStateManager._deleteTexture(id);
        id = size = 0; revision = Long.MIN_VALUE;
    }
}
