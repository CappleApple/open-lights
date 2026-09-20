package com.cappleapple.openlights.client.particle;

import com.cappleapple.openlights.content.LightSourceBlock;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Bounded voxel traversal, used only for the at-most-eight spawn attempts per tick. */
final class BeamDustOcclusion {
    private BeamDustOcclusion() {}

    static boolean clear(ClientLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        int x = Mth.floor(from.x), y = Mth.floor(from.y), z = Mth.floor(from.z);
        int endX = Mth.floor(to.x), endY = Mth.floor(to.y), endZ = Mth.floor(to.z);
        int stepX = sign(delta.x), stepY = sign(delta.y), stepZ = sign(delta.z);
        double dx = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / delta.x);
        double dy = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / delta.y);
        double dz = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / delta.z);
        double tx = boundary(from.x, x, delta.x, stepX);
        double ty = boundary(from.y, y, delta.y, stepY);
        double tz = boundary(from.z, z, delta.z, stepZ);
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int visited = 0; visited < 128; visited++) {
            position.set(x, y, z);
            if (!level.hasChunkAt(position)) return false;
            BlockState state = level.getBlockState(position);
            if (!state.isAir() && state.getRenderShape() != RenderShape.INVISIBLE && !transmitting(state)) {
                var shape = state.getShape(level, position);
                if (!shape.isEmpty() && shape.clip(from, to, position) != null) return false;
            }
            if (x == endX && y == endY && z == endZ) return true;
            if (tx <= ty && tx <= tz) { x += stepX; tx += dx; }
            else if (ty <= tz) { y += stepY; ty += dy; }
            else { z += stepZ; tz += dz; }
        }
        return false;
    }

    private static int sign(double value) { return value > 0 ? 1 : value < 0 ? -1 : 0; }
    private static double boundary(double start, int block, double delta, int step) {
        return step == 0 ? Double.POSITIVE_INFINITY : ((block + (step > 0 ? 1 : 0)) - start) / delta;
    }

    private static boolean transmitting(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof TintedGlassBlock) return false;
        return block instanceof LightSourceBlock || block instanceof AbstractGlassBlock
                || block instanceof StainedGlassPaneBlock || block == Blocks.GLASS_PANE
                || block instanceof IceBlock || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE;
        // Water has an empty block shape. Waterlogged solids retain their shape.
    }
}
