package com.cappleapple.openlights.client.scene;

import com.cappleapple.openlights.config.ClientConfig;
import com.cappleapple.openlights.content.LightSourceBlock;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client-thread scene cache. Eight-block sectors are reused while the camera moves.
 * Each game tick scans at most 8,192 occupied-section block positions; render-only calls reuse the snapshot.
 * Block update hooks should call invalidate so edits take priority over the periodic background sweep.
 */
public final class SceneCache {
    public static final int MAX_RADIUS = 32;
    public static final int BLOCK_BUDGET_PER_TICK = 8192;
    private static final int SECTOR_SIZE = 8;
    private static final int SECTOR_VOLUME = 512;
    private static final int MAX_SECTORS_PER_TICK = 128;
    private static final int REFRESH_TICKS = 10;
    private static final float[] NO_TRIANGLES = new float[0];
    private static final List<Medium> NO_MEDIA = List.of();

    private final Map<Sector, SectorData> sectors = new HashMap<>();
    private final Set<Sector> dirty = new HashSet<>();
    private Set<Sector> desired = Set.of();
    private ClientLevel currentLevel;
    private SceneSnapshot snapshot = new SceneSnapshot(0, Vec3.ZERO, NO_TRIANGLES, NO_MEDIA);
    private long revision;
    private long lastUpdate = Long.MIN_VALUE;
    private int centerX = Integer.MIN_VALUE, centerY = Integer.MIN_VALUE, centerZ = Integer.MIN_VALUE;
    private int cachedRadius = -1;

    public SceneSnapshot update(ClientLevel level, Vec3 center, float radius, long gameTime) {
        if (level == null) {
            if (currentLevel != null) clear();
            return snapshot;
        }
        if (level != currentLevel || gameTime < lastUpdate) {
            clear();
            currentLevel = level;
        }
        if (gameTime == lastUpdate) return snapshot;
        lastUpdate = gameTime;
        if (!Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)) return snapshot;
        int x = Math.floorDiv((int) Math.floor(center.x), 2) * 2;
        int y = Math.floorDiv((int) Math.floor(center.y), 2) * 2;
        int z = Math.floorDiv((int) Math.floor(center.z), 2) * 2;
        int boundedRadius = Float.isFinite(radius) ? Math.max(1, Math.min(MAX_RADIUS, (int) Math.ceil(radius))) : 1;
        boolean changed = false;
        if (x != centerX || y != centerY || z != centerZ || cachedRadius != boundedRadius) {
            centerX = x; centerY = y; centerZ = z; cachedRadius = boundedRadius;
            Set<Sector> next = desiredSectors(level, new Vec3(x + 1, y + 1, z + 1), boundedRadius + 2);
            changed = !desired.equals(next);
            desired = next;
            sectors.keySet().retainAll(desired);
            dirty.retainAll(desired);
        }

        int refreshTicks = ClientConfig.SPEC.isLoaded() ? ClientConfig.MEDIUM_UPDATE_TICKS.get() : REFRESH_TICKS;
        List<Sector> work = new ArrayList<>();
        for (Sector sector : desired) {
            SectorData data = sectors.get(sector);
            if (dirty.contains(sector) || data == null || gameTime - data.scannedAt >= refreshTicks) work.add(sector);
        }
        work.sort(Comparator.comparingInt(this::priority)
                .thenComparingLong(s -> {
                    SectorData data = sectors.get(s);
                    return data == null || dirty.contains(s) ? Long.MIN_VALUE : data.scannedAt;
                })
                .thenComparingDouble(s -> s.distanceSquared(center)));
        int scanned = 0, visited = 0;
        for (Sector sector : work) {
            if (scanned + SECTOR_VOLUME > BLOCK_BUDGET_PER_TICK || visited >= MAX_SECTORS_PER_TICK) break;
            visited++;
            boolean empty = emptySection(level, sector);
            SectorData next = empty ? new SectorData(NO_TRIANGLES, NO_MEDIA, gameTime) : scan(level, sector, gameTime);
            if (!empty) scanned += SECTOR_VOLUME;
            SectorData previous = sectors.put(sector, next);
            dirty.remove(sector);
            if (previous == null) {
                changed |= next.triangles.length != 0 || !next.media.isEmpty();
            } else {
                changed |= !Arrays.equals(previous.triangles, next.triangles) || !previous.media.equals(next.media);
            }
        }
        if (changed) publish(center);
        return snapshot;
    }

    /** Mark the changed block and neighboring sectors, including newly exposed faces. */
    public void invalidate(BlockPos position) {
        mark(position.getX(), position.getY(), position.getZ());
        for (Direction direction : Direction.values()) {
            mark(position.getX() + direction.getStepX(), position.getY() + direction.getStepY(),
                    position.getZ() + direction.getStepZ());
        }
    }

    /** Inclusive block coordinates; useful for client section and chunk notifications. */
    public void invalidateRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (Sector sector : desired) {
            if (sector.x * 8 <= maxX + 1 && sector.x * 8 + 7 >= minX - 1
                    && sector.y * 8 <= maxY + 1 && sector.y * 8 + 7 >= minY - 1
                    && sector.z * 8 <= maxZ + 1 && sector.z * 8 + 7 >= minZ - 1) dirty.add(sector);
        }
    }

    public void invalidateChunk(int chunkX, int chunkZ) {
        for (Sector sector : desired) {
            if (Math.abs((sector.x >> 1) - chunkX) <= 1 && Math.abs((sector.z >> 1) - chunkZ) <= 1) dirty.add(sector);
        }
    }

    public void clear() {
        sectors.clear();
        desired = Set.of();
        dirty.clear();
        currentLevel = null;
        lastUpdate = Long.MIN_VALUE;
        centerX = centerY = centerZ = Integer.MIN_VALUE;
        cachedRadius = -1;
        snapshot = new SceneSnapshot(++revision, Vec3.ZERO, NO_TRIANGLES, NO_MEDIA);
    }

    private void mark(int x, int y, int z) {
        Sector sector = new Sector(Math.floorDiv(x, 8), Math.floorDiv(y, 8), Math.floorDiv(z, 8));
        if (desired.contains(sector)) dirty.add(sector);
    }

    private int priority(Sector sector) {
        return dirty.contains(sector) ? 0 : sectors.containsKey(sector) ? 2 : 1;
    }

    private static boolean emptySection(ClientLevel level, Sector sector) {
        if (!level.hasChunk(sector.x >> 1, sector.z >> 1)) return true;
        var chunk = level.getChunk(sector.x >> 1, sector.z >> 1);
        return chunk.getSection(level.getSectionIndex(sector.y * 8)).hasOnlyAir();
    }

    private static Set<Sector> desiredSectors(ClientLevel level, Vec3 center, int radius) {
        Set<Sector> result = new LinkedHashSet<>();
        int minX = Math.floorDiv((int)Math.floor(center.x) - radius, 8);
        int maxX = Math.floorDiv((int)Math.floor(center.x) + radius, 8);
        int minY = Math.floorDiv(Math.max(level.getMinBuildHeight(), (int)Math.floor(center.y) - radius), 8);
        int maxY = Math.floorDiv(Math.min(level.getMaxBuildHeight() - 1, (int)Math.floor(center.y) + radius), 8);
        int minZ = Math.floorDiv((int)Math.floor(center.z) - radius, 8);
        int maxZ = Math.floorDiv((int)Math.floor(center.z) + radius, 8);
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    double dx = distanceToInterval(center.x, x * 8, x * 8 + 8);
                    double dy = distanceToInterval(center.y, y * 8, y * 8 + 8);
                    double dz = distanceToInterval(center.z, z * 8, z * 8 + 8);
                    if (dx*dx + dy*dy + dz*dz <= radius * radius) result.add(new Sector(x,y,z));
                }
            }
        }
        return result;
    }

    private static double distanceToInterval(double value, double min, double max) {
        return Math.max(0, Math.max(min - value, value - max));
    }

    private static SectorData scan(ClientLevel level, Sector sector, long time) {
        TriangleBuilder mesh = new TriangleBuilder();
        List<Medium> media = new ArrayList<>();
        int baseX = sector.x * 8, baseY = sector.y * 8, baseZ = sector.z * 8;
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (int y = 0; y < 8; y++) {
            for (int z = 0; z < 8; z++) {
                for (int x = 0; x < 8; x++) {
                    position.set(baseX + x, baseY + y, baseZ + z);
                    BlockState state = level.getBlockState(position);
                    if (state.isAir()) continue;
                    VoxelShape shape = state.getShape(level, position);
                    OpticalMaterials.Properties optical = OpticalMaterials.block(state);
                    if (optical != null) {
                        for (AABB box : shape.toAabbs()) addMedium(media, box.move(position), optical);
                    } else if (!(state.getBlock() instanceof LightSourceBlock)
                            && state.getRenderShape() != RenderShape.INVISIBLE && !shape.isEmpty()) {
                        int faces = TriangleBuilder.ALL;
                        if (Block.isShapeFullBlock(shape)) {
                            for (Direction direction : Direction.values()) {
                                neighbor.set(position.getX() + direction.getStepX(),
                                        position.getY() + direction.getStepY(), position.getZ() + direction.getStepZ());
                                BlockState adjacent = level.getBlockState(neighbor);
                                if (!adjacent.isAir() && !(adjacent.getBlock() instanceof LightSourceBlock)
                                        && OpticalMaterials.block(adjacent) == null
                                        && adjacent.getRenderShape() != RenderShape.INVISIBLE
                                        && Block.isShapeFullBlock(adjacent.getShape(level, neighbor))) {
                                    faces &= ~face(direction);
                                }
                            }
                        }
                        if (faces != 0) for (AABB box : shape.toAabbs()) mesh.box(box.move(x, y, z), faces);
                    }
                    var fluid = state.getFluidState();
                    if (fluid.is(FluidTags.WATER)) {
                        float height = Math.max(0, Math.min(1, fluid.getHeight(level, position)));
                        if (height > 0) {
                            VoxelShape water = Shapes.box(0, 0, 0, 1, height, 1);
                            // A waterlogged pane/fence has separate glass/solid and water volumes.
                            if (!shape.isEmpty() && state.getRenderShape() != RenderShape.INVISIBLE) {
                                water = Shapes.joinUnoptimized(water, shape, BooleanOp.ONLY_FIRST);
                            }
                            OpticalMaterials.Properties properties = OpticalMaterials.water(level, position);
                            for (AABB box : water.toAabbs()) addMedium(media, box.move(position), properties);
                        }
                    }
                }
            }
        }
        return new SectorData(mesh.toArray(), MediumMerger.merge(media), time);
    }

    private static int face(Direction direction) {
        return switch (direction) {
            case DOWN -> TriangleBuilder.DOWN;
            case UP -> TriangleBuilder.UP;
            case NORTH -> TriangleBuilder.NORTH;
            case SOUTH -> TriangleBuilder.SOUTH;
            case WEST -> TriangleBuilder.WEST;
            case EAST -> TriangleBuilder.EAST;
        };
    }

    private static void addMedium(List<Medium> output, AABB bounds, OpticalMaterials.Properties properties) {
        output.add(new Medium(bounds, properties.tint(), properties.throughput(), properties.density()));
    }

    private void publish(Vec3 center) {
        Vec3 origin = new Vec3(Math.floor(center.x / 16) * 16, Math.floor(center.y / 16) * 16,
                Math.floor(center.z / 16) * 16);
        TriangleBuilder triangles = new TriangleBuilder();
        List<Medium> media = new ArrayList<>();
        for (Sector sector : desired) {
            SectorData data = sectors.get(sector);
            if (data == null) continue;
            triangles.append(data.triangles, (float)(sector.x * 8 - origin.x),
                    (float)(sector.y * 8 - origin.y), (float)(sector.z * 8 - origin.z));
            media.addAll(data.media);
        }
        snapshot = new SceneSnapshot(++revision, origin, triangles.toArray(), MediumMerger.merge(media));
    }

    private record Sector(int x, int y, int z) {
        double distanceSquared(Vec3 center) {
            double dx=x*8+4-center.x, dy=y*8+4-center.y, dz=z*8+4-center.z;
            return dx*dx+dy*dy+dz*dz;
        }
    }

    private record SectorData(float[] triangles, List<Medium> media, long scannedAt) {}
}
