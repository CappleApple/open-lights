package com.cappleapple.openlights.client.scene;

import com.cappleapple.openlights.config.ClientConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/** Cached lighting input, independent of the eight analytic-light slots and the nearby shadow mesh. */
public final class WorldLightCache {
    private final AggregateLightCache aggregates = new AggregateLightCache();
    private final BlockLightSections blocks = new BlockLightSections(aggregates);
    public BlockLightSections blocks() { return blocks; }
    public AggregateLightCache aggregates() { return aggregates; }
    private ProbeGrid grid;
    private ProbeGrid near;
    private ProbeGrid color, nearColor;
    private long tick = Long.MIN_VALUE, revision;
    private ClientLevel level;
    private int updated;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();

    public void update(ClientLevel world, Vec3 camera, int renderDistance, long now) {
        int size = ClientConfig.WORLD_GRID_SIZE.get();
        // Includes the view-distance corners, vertical view range, and a movement guard band.
        int spacing = Math.max(1, (int)Math.ceil((renderDistance * 16.0 + 32) / (size / 2 - 1)));
        if (world != level || grid == null || grid.size != size || grid.spacing != spacing || now < tick) {
            grid = new ProbeGrid(size, spacing); near = new ProbeGrid(33, 1);
            color = new ProbeGrid(size, spacing); nearColor = new ProbeGrid(33, 1); level = world; tick = Long.MIN_VALUE;
        }
        if (tick == now) return;
        tick = now;
        updated = 0;
        ColoredLightCache.INSTANCE.begin(world, now);
        aggregates.prepare(world,now);
        boolean moved = grid.move(camera.x, camera.y, camera.z);
        boolean changed = moved;
        changed |= near.move(camera.x, camera.y, camera.z);
        changed |= color.move(camera.x, camera.y, camera.z);
        changed |= nearColor.move(camera.x, camera.y, camera.z);
        if (moved) seedSky(world);
        int budget = ClientConfig.WORLD_SAMPLES_PER_TICK.get();
        boolean gridsIdle = !ClientConfig.PERIODIC_CACHE_REFRESH.get() && !near.hasPending() && !grid.hasPending();
        blocks.update(world, camera, renderDistance, now, gridsIdle ? budget : budget / 2);
        updated += blocks.updated;
        aggregates.update(world,camera,renderDistance,now,blocks);
        updated += aggregates.updated();
        changed |= sample(world, near, nearColor, now, budget / 4);
        changed |= sample(world, grid, color, now, budget - budget / 2 - budget / 4);
        if (changed) revision++;
    }

    private void seedSky(ClientLevel world) {
        // Bounded bootstrap for newly exposed cells. No uncolored block-light fallback.
        // Detailed samples remain pending and replace this estimate under their normal budget.
        // Void air below the build floor is not an exposed sky sample.
        float[] sky = new float[125], exposed = new float[125];
        int span = (grid.size - 1) * grid.spacing;
        for (int i = 0; i < 125; i++) {
            pos.set(grid.minimumX() + (i % 5) * span / 4,
                    grid.minimumY() + (i / 5 % 5) * span / 4,
                    grid.minimumZ() + (i / 25) * span / 4);
            if (!world.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            int brightness = world.getBrightness(LightLayer.SKY, pos);
            boolean air = pos.getY() >= world.getMinBuildHeight() && !world.getBlockState(pos).isSolidRender(world, pos);
            for (Direction face : Direction.values()) {
                neighbor.setWithOffset(pos, face);
                if (!world.hasChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4)) continue;
                brightness = Math.max(brightness, world.getBrightness(LightLayer.SKY, neighbor));
                air |= neighbor.getY() >= world.getMinBuildHeight() && !world.getBlockState(neighbor).isSolidRender(world, neighbor);
            }
            sky[i] = air ? brightness / 15f : 0;
            exposed[i] = air ? 1 : 0;
        }
        grid.seedMissingSky(sky, exposed);
    }

    private boolean sample(ClientLevel world, ProbeGrid target, ProbeGrid colors, long now, int budget) {
        boolean changed = false;
        for (int n = 0; n < Math.min(target.count(), budget); n++) {
            int i = target.next(now, 10, ClientConfig.PERIODIC_CACHE_REFRESH.get());
            if (i < 0) break;
            updated++;
            pos.set(target.x(i), target.y(i), target.z(i));
            int block = 0, sky = 0;
            boolean exposed = false;
            Vec3 tint = new Vec3(1,1,1);
            try {
            if (world.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                block = world.getBrightness(LightLayer.BLOCK, pos);
                sky = world.getBrightness(LightLayer.SKY, pos);
                exposed = pos.getY() >= world.getMinBuildHeight() && !world.getBlockState(pos).isSolidRender(world,pos);
                tint = block > 0 ? ColoredLightCache.INSTANCE.tint(pos) : tint;
                // Probes inside solid blocks borrow the brightest touching cell.
                for (Direction face : Direction.values()) {
                    neighbor.setWithOffset(pos, face);
                    if (!world.hasChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4)) continue;
                    int next = world.getBrightness(LightLayer.BLOCK, neighbor);
                    if (next > block) { block = next; tint = ColoredLightCache.INSTANCE.tint(neighbor); }
                    sky = Math.max(sky, world.getBrightness(LightLayer.SKY, neighbor));
                    exposed |= neighbor.getY() >= world.getMinBuildHeight() && !world.getBlockState(neighbor).isSolidRender(world,neighbor);
                }
            }
            } catch (ColoredLightCache.Pending pending) { break; }
            // Normalize sky interpolation over exposed cells. Buried coarse probes must not darken a sunlit surface.
            changed |= target.set(i, block / 15f, exposed ? sky / 15f : 0, exposed ? 1 : 0, 1, now);
            changed |= colors.set(i, (float)tint.x, (float)tint.y, (float)tint.z, 1, now);
        }
        return changed;
    }

    public ProbeGrid grid() { return grid; }
    public int updated() { return updated; }
    public void invalidateRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        blocks.invalidate(minX,minY,minZ,maxX,maxY,maxZ);
        invalidateSkyRegion(minX,minY,minZ,maxX,maxY,maxZ);
    }
    public void invalidateSkyRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        // Each cached sample also reads the six touching cells.
        if (grid != null) grid.invalidateRegion(minX - 1, minY - 1, minZ - 1, maxX + 1, maxY + 1, maxZ + 1);
        if (near != null) near.invalidateRegion(minX - 1, minY - 1, minZ - 1, maxX + 1, maxY + 1, maxZ + 1);
    }
    public ProbeGrid near() { return near; }
    public ProbeGrid color() { return color; }
    public ProbeGrid nearColor() { return nearColor; }
    public long revision() { return revision; }
    public boolean ready() { return grid != null && grid.ready(); }
    public void clear() { blocks.clear(); aggregates.clear(); grid = near = color = nearColor = null; level = null; tick = Long.MIN_VALUE; updated = 0; revision++; }
}
