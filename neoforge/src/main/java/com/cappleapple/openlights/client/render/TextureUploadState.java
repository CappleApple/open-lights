package com.cappleapple.openlights.client.render;

import org.lwjgl.opengl.*;

/** NativeImage/animated atlas uploads leave pixel-unpack offsets set. Our arrays are tightly packed. */
final class TextureUploadState implements AutoCloseable {
    private static final int[] PARAMETERS = {GL11.GL_UNPACK_ALIGNMENT, GL11.GL_UNPACK_ROW_LENGTH,
            GL11.GL_UNPACK_SKIP_PIXELS, GL11.GL_UNPACK_SKIP_ROWS, GL12.GL_UNPACK_IMAGE_HEIGHT,
            GL12.GL_UNPACK_SKIP_IMAGES, GL11.GL_UNPACK_SWAP_BYTES, GL11.GL_UNPACK_LSB_FIRST};
    private final int[] values = new int[PARAMETERS.length];
    private final int buffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
    TextureUploadState() {
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        for (int i = 0; i < PARAMETERS.length; i++) {
            values[i] = GL11.glGetInteger(PARAMETERS[i]);
            GL11.glPixelStorei(PARAMETERS[i], i == 0 ? 4 : 0);
        }
    }
    @Override public void close() {
        for (int i = 0; i < PARAMETERS.length; i++) GL11.glPixelStorei(PARAMETERS[i], values[i]);
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, buffer);
    }
}
