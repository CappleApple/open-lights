package com.cappleapple.openlights.client.scene;

import java.util.Arrays;
import java.util.BitSet;

/** Bounded, world-aligned RGB(A) cache. Recentring retains only exact overlapping samples. */
public final class ProbeGrid {
    public final int size, spacing;
    public final float[] data;
    private final long[] sampledAt;
    private final int[] order;
    private final BitSet dirty = new BitSet();
    private int x, y, z, cursor, populated;
    private boolean located;

    public ProbeGrid(int size, int spacing) {
        if (size < 2 || size > 65 || spacing < 1) throw new IllegalArgumentException("Invalid grid dimensions");
        this.size = size;
        this.spacing = spacing;
        data = new float[size * size * size * 4];
        sampledAt = new long[size * size * size];
        Arrays.fill(sampledAt, Long.MIN_VALUE);
        dirty.set(0, sampledAt.length);
        Integer[] indices = new Integer[sampledAt.length];
        for (int i = 0; i < indices.length; i++) indices[i] = i;
        Arrays.sort(indices, java.util.Comparator.comparingDouble(i -> {
            double half = (size - 1) * .5;
            double dx = i % size - half, dy = i / size % size - half, dz = i / (size * size) - half;
            return dx * dx + dy * dy + dz * dz;
        }));
        order = Arrays.stream(indices).mapToInt(Integer::intValue).toArray();
    }

    public boolean move(double centerX, double centerY, double centerZ) {
        int half = (size - 1) / 2;
        int nx = Math.floorDiv((int)Math.floor(centerX), spacing) * spacing - half * spacing;
        int ny = Math.floorDiv((int)Math.floor(centerY), spacing) * spacing - half * spacing;
        int nz = Math.floorDiv((int)Math.floor(centerZ), spacing) * spacing - half * spacing;
        if (located && nx == x && ny == y && nz == z) return false;
        float[] previous = data.clone();
        long[] ages = sampledAt.clone();
        BitSet previousDirty = (BitSet) dirty.clone();
        dirty.set(0, sampledAt.length);
        Arrays.fill(data, 0);
        Arrays.fill(sampledAt, Long.MIN_VALUE);
        populated = 0;
        if (located) {
            int dx = (nx - x) / spacing, dy = (ny - y) / spacing, dz = (nz - z) / spacing;
            for (int i = 0; i < ages.length; i++) {
                int ox = i % size + dx, oy = i / size % size + dy, oz = i / (size * size) + dz;
                if (ox < 0 || oy < 0 || oz < 0 || ox >= size || oy >= size || oz >= size) continue;
                int old = ox + size * (oy + size * oz);
                System.arraycopy(previous, old * 4, data, i * 4, 4);
                sampledAt[i] = ages[old];
                dirty.set(i, previousDirty.get(old));
                if (ages[old] != Long.MIN_VALUE) populated++;
            }
        }
        x = nx; y = ny; z = nz; located = true; cursor = 0;
        return true;
    }

    /** Fair sweep, including after invalidation; dirty events never restart a running sweep. */
    public int next(long tick, int minimumAge) {
        return next(tick, minimumAge, false);
    }

    public int next(long tick, int minimumAge, boolean periodic) {
        if (!periodic && dirty.isEmpty()) return -1;
        for (int visited = 0; visited < order.length; visited++) {
            int index = order[cursor];
            cursor = (cursor + 1) % order.length;
            long age = sampledAt[index];
            if (age == Long.MIN_VALUE || ((periodic || dirty.get(index)) && tick - age >= minimumAge)) return index;
        }
        return -1;
    }

    public boolean set(int index, float r, float g, float b, float a, long tick) {
        if (sampledAt[index] == Long.MIN_VALUE) populated++;
        r = finite(r); g = finite(g); b = finite(b); a = finite(a);
        boolean changed = data[index * 4] != r || data[index * 4 + 1] != g
                || data[index * 4 + 2] != b || data[index * 4 + 3] != a;
        data[index * 4] = r; data[index * 4 + 1] = g;
        data[index * 4 + 2] = b; data[index * 4 + 3] = a;
        sampledAt[index] = tick;
        dirty.clear(index);
        return changed;
    }

    /** Retain usable stale values while changed inputs wait for their refresh budget. */
    public void invalidate() { dirty.set(0, sampledAt.length); }

    /** Interpolate a 5³ skylight bootstrap without marking detailed samples completed. */
    public void seedMissingSky(float[] sky, float[] exposed) {
        if (sky.length != 125 || exposed.length != 125) throw new IllegalArgumentException("Expected 5 cubed sky samples");
        for (int i = 0; i < count(); i++) {
            if (data[i * 4 + 3] != 0) continue;
            float x = (i % size) * 4f / (size - 1), y = (i / size % size) * 4f / (size - 1), z = (i / (size * size)) * 4f / (size - 1);
            int bx = Math.min(3, (int)x), by = Math.min(3, (int)y), bz = Math.min(3, (int)z);
            float fx = x - bx, fy = y - by, fz = z - bz;
            for (int corner = 0; corner < 8; corner++) {
                int dx = corner & 1, dy = corner >> 1 & 1, dz = corner >> 2;
                float weight = (dx == 0 ? 1-fx : fx) * (dy == 0 ? 1-fy : fy) * (dz == 0 ? 1-fz : fz);
                int sample = bx + dx + 5 * (by + dy + 5 * (bz + dz));
                data[i * 4 + 1] += sky[sample] * weight;
                data[i * 4 + 2] += exposed[sample] * weight;
            }
            data[i * 4 + 3] = 1;
        }
    }

    public boolean intersects(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int margin) {
        int span = (size - 1) * spacing;
        return located && x <= maxX + margin && x + span >= minX - margin
                && y <= maxY + margin && y + span >= minY - margin
                && z <= maxZ + margin && z + span >= minZ - margin;
    }

    /** Inclusive block bounds; only samples whose input cell lies in the region become dirty. */
    public void invalidateRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (!intersects(minX, minY, minZ, maxX, maxY, maxZ, 0)) return;
        int ax = Math.max(0, -Math.floorDiv(x - minX, spacing));
        int ay = Math.max(0, -Math.floorDiv(y - minY, spacing));
        int az = Math.max(0, -Math.floorDiv(z - minZ, spacing));
        int bx = Math.min(size - 1, Math.floorDiv(maxX - x, spacing));
        int by = Math.min(size - 1, Math.floorDiv(maxY - y, spacing));
        int bz = Math.min(size - 1, Math.floorDiv(maxZ - z, spacing));
        for (int iz = az; iz <= bz; iz++) for (int iy = ay; iy <= by; iy++) {
            if (ax <= bx) dirty.set(ax + size * (iy + size * iz), bx + size * (iy + size * iz) + 1);
        }
    }
    public boolean ready() { return populated == sampledAt.length; }
    public boolean hasPending() { return !dirty.isEmpty(); }
    public int populated() { return populated; }
    public int count() { return sampledAt.length; }
    public double x(int i) { return x + (i % size) * spacing + .5; }
    public double y(int i) { return y + (i / size % size) * spacing + .5; }
    public double z(int i) { return z + (i / (size * size)) * spacing + .5; }
    public int minimumX() { return x; }
    public int minimumY() { return y; }
    public int minimumZ() { return z; }
    private static float finite(float value) { return Float.isFinite(value) ? Math.max(0, Math.min(16, value)) : 0; }
}
