package com.cappleapple.openlights.client.render;

import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

/** Restore every OpenGL binding/state touched by our passes, including Minecraft's cached state. */
final class GlState implements AutoCloseable {
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
    private final int buffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    private final int uniformBuffer=GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
    private final int indexedUniformBuffer=GL30.glGetIntegeri(GL31.GL_UNIFORM_BUFFER_BINDING,0);
    private final long uniformStart=GL32.glGetInteger64i(GL31.GL_UNIFORM_BUFFER_START,0);
    private final long uniformSize=GL32.glGetInteger64i(GL31.GL_UNIFORM_BUFFER_SIZE,0);
    private final int drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int[] samplers = new int[4];
    private final boolean srgb = GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);
    private final double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
    private final int[] texture2d = new int[4], textureArray = new int[4];
    private final int[] viewport = new int[4];
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    private final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final boolean[] colorMask = new boolean[4];

    GlState() {
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        try (var stack = MemoryStack.stackPush()) {
            var mask = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            for (int i = 0; i < 4; i++) colorMask[i] = mask.get(i) != 0;
        }
        for (int i = 0; i < 4; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            samplers[i] = GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, i);
            GL33.glBindSampler(i, 0);
            texture2d[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            textureArray[i] = GL11.glGetInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY);
        }
        GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glColorMask(true, true, true, true);
    }

    @Override public void close() {
        if(indexedUniformBuffer!=0 && uniformSize>0) {
            GL30.glBindBufferRange(GL31.GL_UNIFORM_BUFFER,0,indexedUniformBuffer,uniformStart,uniformSize);
        } else GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER,0,indexedUniformBuffer);
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER,uniformBuffer);
        GL20.glUseProgram(program);
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        set(GL11.GL_DEPTH_TEST, depth);
        set(GL11.GL_BLEND, blend);
        set(GL11.GL_CULL_FACE, cull);
        set(GL11.GL_SCISSOR_TEST, scissor);
        set(GL30.GL_FRAMEBUFFER_SRGB, srgb);
        GL11.glClearDepth(clearDepth);
        GL11.glDepthMask(depthMask);
        GL11.glDepthFunc(depthFunc);
        GL11.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        for (int i = 0; i < 4; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            GL33.glBindSampler(i, samplers[i]);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture2d[i]);
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, textureArray[i]);
        }
        GL13.glActiveTexture(activeTexture);
    }
    private static void set(int state, boolean enabled) {
        if (enabled) GL11.glEnable(state); else GL11.glDisable(state);
    }
}
