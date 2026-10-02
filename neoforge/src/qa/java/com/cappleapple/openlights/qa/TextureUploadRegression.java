package com.cappleapple.openlights.qa;

import com.cappleapple.openlights.client.scene.ProbeGrid;
import com.mojang.logging.LogUtils;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL21.*;

/** GPU round trip under atlas-style offsets and an unrelated pixel buffer. Never packaged. */
final class TextureUploadRegression {
    static void managedBindings() {
        int active=glGetInteger(GL_ACTIVE_TEXTURE);
        com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL_TEXTURE0);
        int original=glGetInteger(GL_TEXTURE_BINDING_2D);
        net.minecraft.client.renderer.texture.DynamicTexture sentinel=null,temporary=null;
        try {
            sentinel=new net.minecraft.client.renderer.texture.DynamicTexture(4,4,false);
            sentinel.upload();
            var constructor=Class.forName("com.cappleapple.openlights.client.render.GlState").getDeclaredConstructor();constructor.setAccessible(true);
            try(var state=(AutoCloseable)constructor.newInstance()) {
                if(glGetInteger(GL_ACTIVE_TEXTURE)!=GL_TEXTURE0)throw new IllegalStateException("Guard changed managed upload unit");
                temporary=new net.minecraft.client.renderer.texture.DynamicTexture(4,4,false);
                temporary.upload();
            }
            if(glGetInteger(GL_TEXTURE_BINDING_2D)!=sentinel.getId())throw new IllegalStateException("Guard lost original GL binding");
            temporary.bind();
            if(glGetInteger(GL_TEXTURE_BINDING_2D)!=temporary.getId())throw new IllegalStateException("Minecraft cached binding disagrees with GL after upload");
            LogUtils.getLogger().info("MANAGED_TEXTURE_STATE_SUCCESS");
        } catch(Exception error){throw new IllegalStateException(error);}
        finally {
            if(temporary!=null)temporary.close();if(sentinel!=null)sentinel.close();
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(original);
            com.mojang.blaze3d.platform.GlStateManager._activeTexture(active);
        }
    }
    static void run() {
        int[] unpack = {GL_UNPACK_ALIGNMENT, GL_UNPACK_ROW_LENGTH, GL_UNPACK_SKIP_PIXELS,
                GL_UNPACK_SKIP_ROWS, GL_UNPACK_IMAGE_HEIGHT, GL_UNPACK_SKIP_IMAGES, GL_UNPACK_SWAP_BYTES, GL_UNPACK_LSB_FIRST};
        int[] pack = {GL_PACK_ALIGNMENT, GL_PACK_ROW_LENGTH, GL_PACK_SKIP_PIXELS,
                GL_PACK_SKIP_ROWS, GL_PACK_IMAGE_HEIGHT, GL_PACK_SKIP_IMAGES, GL_PACK_SWAP_BYTES, GL_PACK_LSB_FIRST};
        int[] originalUnpack = new int[8], originalPack = new int[8];
        int[] poison = {8, 64, 3, 5, 80, 2, 1, 1};
        int active = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE3);
        int binding = glGetInteger(GL_TEXTURE_BINDING_3D);
        int unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING), packBuffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        int buffer = glGenBuffers();
        AutoCloseable texture = null;
        for (int i = 0; i < 8; i++) {
            originalUnpack[i] = glGetInteger(unpack[i]); originalPack[i] = glGetInteger(pack[i]);
            glPixelStorei(unpack[i], poison[i]); glPixelStorei(pack[i], i == 0 ? 4 : 0);
        }
        try {
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, buffer);
            glBufferData(GL_PIXEL_UNPACK_BUFFER, 4096, GL_STATIC_DRAW);
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
            var type = Class.forName("com.cappleapple.openlights.client.render.ProbeTexture");
            var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
            texture = (AutoCloseable)constructor.newInstance();
            var upload = type.getDeclaredMethod("upload", ProbeGrid.class, long.class); upload.setAccessible(true);
            var grid = new ProbeGrid(2, 1);
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < grid.data.length; i++) grid.data[i] = ((i + pass * 7) % 16) / 16f;
                upload.invoke(texture, grid, (long)pass);
                for (int i = 0; i < 8; i++) if (glGetInteger(unpack[i]) != poison[i])
                    throw new IllegalStateException("Upload did not restore unpack parameter " + unpack[i]);
                if (glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING) != buffer)
                    throw new IllegalStateException("Upload did not restore pixel buffer");
                float[] actual = new float[grid.data.length];
                glGetTexImage(GL_TEXTURE_3D, 0, GL_RGBA, GL_FLOAT, actual);
                for (int i = 0; i < actual.length; i++) if (Math.abs(actual[i] - grid.data[i]) > .001f)
                    throw new IllegalStateException("Corrupt probe texel " + i + ": " + actual[i] + " != " + grid.data[i]);
            }
            LogUtils.getLogger().info("TEXTURE_UPLOAD_SUCCESS allocation and refresh preserve texels and pixel-unpack state");
        } catch (Exception failure) {
            throw new IllegalStateException("GPU upload regression", failure);
        } finally {
            try { if (texture != null) texture.close(); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
            finally {
                for (int i = 0; i < 8; i++) { glPixelStorei(unpack[i], originalUnpack[i]); glPixelStorei(pack[i], originalPack[i]); }
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer); glBindBuffer(GL_PIXEL_PACK_BUFFER, packBuffer);
                glDeleteBuffers(buffer); glBindTexture(GL_TEXTURE_3D, binding); glActiveTexture(active);
            }
        }
    }
}
