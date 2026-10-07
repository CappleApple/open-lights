package com.cappleapple.openlights.client.render;

import net.minecraft.client.renderer.texture.DynamicTexture;

import java.util.Arrays;

/** Native per-vertex skylight without Minecraft block light. Access only on the render thread. */
final class NativeSkyLightmap {
    private static DynamicTexture texture;
    private static final int[] rows = new int[16], previousRows = new int[16];
    private static boolean cached;

    private NativeSkyLightmap() {}

    /** Returns zero when the native palette is unavailable; uploads only changed sky colors. */
    static int update(DynamicTexture palette) {
        if (palette == null || palette.getPixels() == null) return 0;
        var nativePixels = palette.getPixels();
        for (int sky = 0; sky < rows.length; sky++) rows[sky] = nativePixels.getPixelRGBA(0, sky);
        if (texture != null && cached && Arrays.equals(previousRows, rows)) return texture.getId();

        int[] pixels = expandRows(rows);
        try (var ignored = new GlState(); var unpack = new TextureUploadState()) {
            if (texture == null) texture = new DynamicTexture(16, 16, false);
            for (int i = 0; i < pixels.length; i++) {
                texture.getPixels().setPixelRGBA(i % 16, i / 16, pixels[i]);
            }
            texture.upload();
            // Vanilla terrain samples between lightmap rows, even for integer SKY levels.
            // DynamicTexture uploads reset the filters, so restore these after every upload.
            texture.setFilter(true, false);
        }
        System.arraycopy(rows, 0, previousRows, 0, rows.length);
        cached = true;
        return texture.getId();
    }

    /** Keeps sky on the native Y axis and removes dependence on the block-light X axis. */
    static int[] expandRows(int[] rows) {
        if (rows.length != 16) throw new IllegalArgumentException("Expected sixteen sky colors");
        int[] pixels = new int[256];
        for (int sky = 0; sky < rows.length; sky++) Arrays.fill(pixels, sky * 16, (sky + 1) * 16, rows[sky]);
        return pixels;
    }

    static void close() {
        if (texture != null) texture.close();
        texture = null;
        cached = false;
    }
}
