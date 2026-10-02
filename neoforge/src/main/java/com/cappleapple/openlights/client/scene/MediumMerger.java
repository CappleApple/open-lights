package com.cappleapple.openlights.client.scene;

import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact axis-aligned unions: unlike bounding-box merging, this never fills pane corners or water gaps. */
final class MediumMerger {
    static List<Medium> merge(List<Medium> source) {
        List<Medium> merged = new ArrayList<>(source);
        for (int cycle = 0; cycle < 3; cycle++) {
            int previous = merged.size();
            for (int axis = 0; axis < 3; axis++) merged = mergeAxis(merged, axis);
            if (merged.size() == previous) break;
        }
        return List.copyOf(merged);
    }

    private static List<Medium> mergeAxis(List<Medium> source, int axis) {
        Map<Key, List<Medium>> groups = new LinkedHashMap<>();
        int u = (axis + 1) % 3, v = (axis + 2) % 3;
        for (Medium medium : source) {
            AABB b = medium.bounds();
            Key key = new Key(min(b, u), max(b, u), min(b, v), max(b, v),
                    medium.tint().x, medium.tint().y, medium.tint().z,
                    medium.throughput(), medium.densityBoost());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(medium);
        }
        List<Medium> output = new ArrayList<>();
        for (List<Medium> group : groups.values()) {
            group.sort(Comparator.comparingDouble((Medium m) -> min(m.bounds(), axis))
                    .thenComparingDouble(m -> max(m.bounds(), axis)));
            Medium current = null;
            for (Medium next : group) {
                if (current != null && min(next.bounds(), axis) <= max(current.bounds(), axis)) {
                    AABB a = current.bounds(), b = next.bounds();
                    current = new Medium(new AABB(Math.min(a.minX, b.minX), Math.min(a.minY, b.minY),
                            Math.min(a.minZ, b.minZ), Math.max(a.maxX, b.maxX), Math.max(a.maxY, b.maxY),
                            Math.max(a.maxZ, b.maxZ)), current.tint(), current.throughput(), current.densityBoost());
                } else {
                    if (current != null) output.add(current);
                    current = next;
                }
            }
            if (current != null) output.add(current);
        }
        return output;
    }

    private static double min(AABB b, int axis) { return axis == 0 ? b.minX : axis == 1 ? b.minY : b.minZ; }
    private static double max(AABB b, int axis) { return axis == 0 ? b.maxX : axis == 1 ? b.maxY : b.maxZ; }
    private record Key(double u0, double u1, double v0, double v1, double r, double g, double b,
                       float throughput, float density) {}
    private MediumMerger() {}
}
