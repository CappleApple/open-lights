/*
 * SPDX-License-Identifier: MIT
 * Optical tint constants adapted from Veil Volume Lights.
 * Copyright (c) 2026 CappleApple
 */
package com.cappleapple.openlights.client.scene;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class OpticalMaterials {
    record Properties(Vec3 tint, float throughput, float density) {}
    private static final Properties CLEAR = new Properties(new Vec3(.96, .985, 1), .97F, 2.50F);
    private static final Properties ICE = new Properties(new Vec3(.72, .88, 1), .90F, 2.30F);

    static Properties block(BlockState state) {
        Block block = state.getBlock();
        // Tinted glass deliberately blocks light, despite its glass superclass.
        if (block instanceof TintedGlassBlock) return null;
        if (block instanceof StainedGlassBlock glass) return dye(glass.getColor());
        if (block instanceof StainedGlassPaneBlock pane) return dye(pane.getColor());
        if (block instanceof IceBlock || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE) return ICE;
        if (block instanceof AbstractGlassBlock || block == Blocks.GLASS_PANE) return CLEAR;
        return null;
    }

    static Properties water(ClientLevel level, BlockPos position) {
        int color = BiomeColors.getAverageWaterColor(level, position);
        double red = (color >> 16 & 255) / 255.0, green = (color >> 8 & 255) / 255.0, blue = (color & 255) / 255.0;
        double brightest = Math.max(.001, Math.max(red, Math.max(green, blue)));
        return new Properties(new Vec3(1 + (red / brightest - 1) * .62,
                1 + (green / brightest - 1) * .62, 1 + (blue / brightest - 1) * .62), .98F, 2.50F);
    }

    private static Properties dye(DyeColor color) {
        Vec3 tint = switch (color) {
            case WHITE -> new Vec3(.98, .98, .98);
            case ORANGE -> new Vec3(1, .34, .03);
            case MAGENTA -> new Vec3(1, .04, .78);
            case LIGHT_BLUE -> new Vec3(.08, .58, 1);
            case YELLOW -> new Vec3(1, .92, .03);
            case LIME -> new Vec3(.18, 1, .08);
            case PINK -> new Vec3(1, .24, .46);
            case GRAY -> new Vec3(.22, .23, .24);
            case LIGHT_GRAY -> new Vec3(.60, .62, .64);
            case CYAN -> new Vec3(.03, 1, 1);
            case PURPLE -> new Vec3(.46, .04, 1);
            case BLUE -> new Vec3(.02, .28, 1);
            case BROWN -> new Vec3(.36, .13, .03);
            case GREEN -> new Vec3(.04, 1, .28);
            case RED -> new Vec3(1, .04, .02);
            case BLACK -> new Vec3(.025, .025, .03);
        };
        return new Properties(tint, .98F, 2.50F);
    }

    private OpticalMaterials() {}
}
