package com.cappleapple.openlights.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Tracks ticking client fixtures without scanning loaded chunks every frame. */
public final class LightSourceTracker {
    private static final Map<Level, Map<BlockPos, Long>> SOURCES = new WeakHashMap<>();

    private LightSourceTracker() {}

    public static void see(Level level, BlockPos pos) {
        if (level.isClientSide) {
            SOURCES.computeIfAbsent(level, ignored -> new HashMap<>()).put(pos.immutable(), level.getGameTime());
        }
    }

    public static void forget(Level level, BlockPos pos) {
        Map<BlockPos, Long> sources = SOURCES.get(level);
        if (sources != null) sources.remove(pos);
    }

    public static List<LightSourceBlockEntity> snapshot(Level level) {
        Map<BlockPos, Long> sources = SOURCES.get(level);
        if (sources == null) return List.of();
        List<LightSourceBlockEntity> result = new ArrayList<>();
        var iterator = sources.entrySet().iterator();
        while (iterator.hasNext()) {
            var source = iterator.next();
            if (level.getGameTime() - source.getValue() > 3 || !level.hasChunkAt(source.getKey())) {
                iterator.remove();
            } else if (level.getBlockEntity(source.getKey()) instanceof LightSourceBlockEntity light) {
                result.add(light);
            } else {
                iterator.remove();
            }
        }
        return result;
    }
}
