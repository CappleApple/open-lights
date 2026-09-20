package com.cappleapple.openlights.client.render;

import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;

final class RenderTargets implements AutoCloseable {
    int width, height, lightWidth, lightHeight, resolution;
    int opaqueDepth, opaqueFbo, sceneColor, sceneFbo, lighting, lightingFbo, shadowDepth, shadowFbo;

    boolean resize(int width, int height, float scale, int resolution) {
        int lw = Math.max(1, Math.round(width * scale)), lh = Math.max(1, Math.round(height * scale));
        if (this.width == width && this.height == height && lightWidth == lw && lightHeight == lh
                && this.resolution == resolution) return false;
        close();
        this.width = width; this.height = height; lightWidth = lw; lightHeight = lh;
        this.resolution = resolution;
        opaqueDepth = texture(width, height, GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, GL11.GL_NEAREST);
        opaqueFbo = framebuffer(opaqueDepth);
        sceneColor = texture(width, height, GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, GL11.GL_NEAREST);
        sceneFbo = framebuffer(sceneColor);
        lighting = texture(lw, lh, GL30.GL_RGBA16F, GL11.GL_RGBA, GL11.GL_FLOAT, GL11.GL_LINEAR);
        lightingFbo = framebuffer(lighting);
        shadowDepth = GL11.glGenTextures();
        GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, shadowDepth);
        GL12.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, GL14.GL_DEPTH_COMPONENT24,
                resolution, resolution, 24, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        shadowFbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, shadowFbo);
        GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, shadowDepth, 0, 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);
        check();
        return true;
    }

    private static int texture(int width, int height, int internal, int format, int type, int filter) {
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internal, width, height, 0, format, type, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return id;
    }
    private static int framebuffer(int texture) {
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        try { check(); }
        catch(RuntimeException exception) {
            GL30.glDeleteFramebuffers(fbo);
            throw exception;
        }
        return fbo;
    }
    private static void check() {
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Open Lights framebuffer status " + status);
    }
    @Override public void close() {
        for (int id : new int[]{opaqueDepth,sceneColor,lighting,shadowDepth}) if (id != 0) GL11.glDeleteTextures(id);
        for (int id : new int[]{opaqueFbo,sceneFbo,lightingFbo,shadowFbo}) if (id != 0) GL30.glDeleteFramebuffers(id);
        opaqueDepth=sceneColor=lighting=shadowDepth=opaqueFbo=sceneFbo=lightingFbo=shadowFbo=0;
        width=height=lightWidth=lightHeight=resolution=0;
    }
}
