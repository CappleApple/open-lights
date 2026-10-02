package com.cappleapple.openlights.client.scene;

import com.cappleapple.openlights.config.ClientConfig;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import java.util.LinkedHashMap;

/** Lazily follows Minecraft's increasing block-light gradient back to textured emitters. */
public final class ColoredLightCache {
    public static final ColoredLightCache INSTANCE = new ColoredLightCache();
    private static final int MAX_CELLS = 131072;
    private final LinkedHashMap<Long, Int2IntOpenHashMap> sections = new LinkedHashMap<>(64, .75f, true);
    private ClientLevel level;
    private long tick = Long.MIN_VALUE;
    private int remaining, cells, updated;
    public static final class Pending extends RuntimeException {
        private Pending() { super(null, null, false, false); }
    }
    private static final Pending PENDING = new Pending();

    public void begin(ClientLevel world, long now) {
        if (world != level) { clear(); level = world; }
        if (now != tick) {
            tick = now; remaining = ClientConfig.COLOR_SAMPLES_PER_TICK.get(); updated = 0;
            if (ClientConfig.PERIODIC_CACHE_REFRESH.get() && now % 20 == 0) { sections.clear(); cells = 0; }
        }
    }
    public int updated() { return updated; }
    public Vec3 tint(BlockPos position) {
        int packed = resolve(position);
        return new Vec3((packed >> 16 & 255)/255.0, (packed >> 8 & 255)/255.0, (packed & 255)/255.0);
    }
    private int resolve(BlockPos pos) {
        if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return 0xffffff;
        int brightness = level.getBrightness(LightLayer.BLOCK, pos);
        if (brightness == 0) return 0xffffff;
        long section = SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        int key = (pos.getX() & 15) | (pos.getY() & 15) << 4 | (pos.getZ() & 15) << 8;
        var cache = sections.get(section);
        if (cache != null && cache.containsKey(key)) return cache.get(key);
        if (remaining-- <= 0) throw PENDING;
        updated++;
        var state = level.getBlockState(pos);
        Vec3 value;
        if (state.getLightEmission(level, pos) >= brightness) value = TextureColors.emission(level, pos, state);
        else {
            Vec3 sum = Vec3.ZERO; double count = 0;
            for (Direction face : Direction.values()) {
                BlockPos next = pos.relative(face);
                if (!level.hasChunk(next.getX() >> 4, next.getZ() >> 4)) continue;
                int parent = level.getBrightness(LightLayer.BLOCK, next);
                if (parent <= brightness) continue;
                var adjacent = level.getBlockState(next);
                if (state.canOcclude() && adjacent.canOcclude()
                        && net.minecraft.world.phys.shapes.Shapes.faceShapeOccludes(state.getFaceOcclusionShape(level,pos,face),
                            adjacent.getFaceOcclusionShape(level,next,face.getOpposite()))) continue;
                int color = resolve(next);
                double weight = parent * parent;
                sum = sum.add(new Vec3((color >> 16 & 255)/255.0, (color >> 8 & 255)/255.0, (color & 255)/255.0).scale(weight));
                count += weight;
            }
            value = count > 0 ? sum.scale(1/count) : new Vec3(1,1,1);
            var optical = OpticalMaterials.block(level, pos, state);
            if (optical != null) value = value.multiply(optical.tint());
            if (state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) value = value.multiply(OpticalMaterials.water(level,pos).tint());
        }
        int packed = pack(value);
        cache = sections.computeIfAbsent(section, unused -> new Int2IntOpenHashMap());
        if (!cache.containsKey(key)) cells++;
        cache.put(key, packed);
        while (cells > MAX_CELLS && sections.size() > 1) {
            var iterator = sections.entrySet().iterator();
            cells -= iterator.next().getValue().size(); iterator.remove();
        }
        return packed;
    }
    private static int pack(Vec3 v) {
        return (int)Math.round(Math.max(0, Math.min(1,v.x))*255) << 16
                | (int)Math.round(Math.max(0, Math.min(1,v.y))*255) << 8
                | (int)Math.round(Math.max(0, Math.min(1,v.z))*255);
    }
    public void invalidate(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
        // Native level 15 can influence at most 15 blocks. Drop dependent sections, not the whole cache.
        sections.entrySet().removeIf(entry -> {
            var section = SectionPos.of(entry.getKey());
            boolean hit = section.maxBlockX() >= minX-15 && section.minBlockX() <= maxX+15
                    && section.maxBlockY() >= minY-15 && section.minBlockY() <= maxY+15
                    && section.maxBlockZ() >= minZ-15 && section.minBlockZ() <= maxZ+15;
            if (hit) cells -= entry.getValue().size();
            return hit;
        });
    }
    public void clear() { sections.clear(); cells = updated = 0; level = null; tick = Long.MIN_VALUE; }
}
