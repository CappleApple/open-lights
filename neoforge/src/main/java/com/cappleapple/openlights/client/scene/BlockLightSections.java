package com.cappleapple.openlights.client.scene;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Block-resolution colored light bricks. Empty light sections need only a page-table entry. */
public final class BlockLightSections {
    public static final int EDGE = 18, CELLS = EDGE * EDGE * EDGE;
    private static final byte[] ZERO_LIGHT = new byte[2048];
    public static final byte[] EMPTY_BRICK = new byte[CELLS*4];
    public static final class Tile {
        public final int slot;
        public final long identity;
        public byte[] data;
        public byte[] aggregateData;
        public byte[] directionData;
        public long aggregateRevision;
        public long revision;
        private byte[] building;
        private int cursor;
        private boolean dirtyAgain;
        private List<AggregateLightCache.Source> sources;
        private Tile(int slot,long identity) { this.slot = slot; this.identity=identity; }
    }
    private final Map<Long, Tile> tiles = new HashMap<>();
    private final LinkedHashSet<Long> pending = new LinkedHashSet<>();
    private final Map<Long, Long> queuedAt = new HashMap<>();
    private final PriorityQueue<Integer> freeSlots = new PriorityQueue<>();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
    private ClientLevel level;
    private int centerX = Integer.MIN_VALUE, centerZ, radius, nextSlot;
    private long tick = Long.MIN_VALUE, revision, generation, identities;
    private boolean reorder;
    private int priorityPhase = -1;
    public int minX, minY, minZ, width, height, updated;
    public int[] pages = new int[0];
    private final AggregateLightCache aggregates;
    public BlockLightSections(AggregateLightCache aggregates) { this.aggregates = aggregates; }

    public void update(ClientLevel world, Vec3 camera, int distance, long now, int budget) {
        if (world != level || now < tick) { clear(); level = world; }
        if (now == tick) return;
        tick = now; updated = 0;
        int cx = (int)Math.floor(camera.x) >> 4, cz = (int)Math.floor(camera.z) >> 4;
        int range = distance + 1;
        if (cx != centerX || cz != centerZ || range != radius) relocate(world, camera, cx, cz, range);
        if (com.cappleapple.openlights.config.ClientConfig.PERIODIC_CACHE_REFRESH.get() && now % 200 == 0) {
            for (int z=minZ; z<minZ+width; z++) for (int x=minX; x<minX+width; x++) {
                if (!world.hasChunk(x,z)) continue;
                for (int y=minY; y<minY+height; y++) enqueue(SectionPos.asLong(x,y,z));
            }
        }
        for (var entry : tiles.entrySet()) {
            Tile tile = entry.getValue();
            if (tile.dirtyAgain && tile.building == null) { enqueue(entry.getKey()); tile.dirtyAgain = false; }
        }
        if (pending.isEmpty()) return;
        int phase = (int)(now / 10 % 2);
        if (reorder || phase != priorityPhase) {
            var sorted = new ArrayList<>(pending);
            // Alternate responsive nearby work with age-ordered background work. This keeps
            // arriving light packets responsive without letting nearby edits starve the view.
            sorted.sort(Comparator.comparingDouble(key -> phase == 0 ? distanceSquared(key,camera)
                    : queuedAt.getOrDefault(key,now) + Math.min(200.0,Math.sqrt(distanceSquared(key,camera))*.5)));
            pending.clear(); pending.addAll(sorted); reorder = false; priorityPhase = phase;
        }
        int inspected = 0;
        var iterator = pending.iterator();
        while (iterator.hasNext() && updated < budget && inspected++ < 256) {
            long key = iterator.next();
            int sx = SectionPos.x(key), sy = SectionPos.y(key), sz = SectionPos.z(key);
            int page = index(sx, sy, sz);
            if (page < 0) { iterator.remove(); queuedAt.remove(key); continue; }
            if (!world.hasChunk(sx, sz)) { remove(key); setPage(page, 0); iterator.remove(); queuedAt.remove(key); continue; }
            var light = world.getLightEngine().getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(key));
            boolean nativeEmpty = light == null || light.isEmpty() || Arrays.equals(light.getData(), ZERO_LIGHT);
            if (nativeEmpty) aggregates.replaceSources(key,List.of());
            if (nativeEmpty) {
                Tile existing=tiles.get(key);
                if(existing!=null&&existing.aggregateData!=null) {
                    if(existing.data!=EMPTY_BRICK){existing.data=EMPTY_BRICK;existing.revision=++revision;}
                    existing.building=null;existing.sources=null;existing.dirtyAgain=false;
                    setPage(page,existing.slot+1);
                } else { remove(key);setPage(page,-1); }
                iterator.remove();queuedAt.remove(key);continue;
            }
            Tile tile = tiles.computeIfAbsent(key, unused -> newTile());
            if (tile.building == null) { tile.building = new byte[CELLS * 4]; tile.cursor = 0; tile.sources = new ArrayList<>(); }
            while (tile.cursor < CELLS && updated < budget) {
                int i = tile.cursor;
                pos.set(sx * 16 + i % EDGE - 1, sy * 16 + i / EDGE % EDGE - 1, sz * 16 + i / (EDGE * EDGE) - 1);
                try { sample(world, tile.building, i); }
                catch (ColoredLightCache.Pending wait) { return; }
                if (i%EDGE>=1 && i%EDGE<=16 && i/EDGE%EDGE>=1 && i/EDGE%EDGE<=16 && i/(EDGE*EDGE)>=1 && i/(EDGE*EDGE)<=16) {
                    var state=world.getBlockState(pos);
                    int emission=state.getLightEmission(world,pos);
                    if(emission>0)tile.sources.add(new AggregateLightCache.Source(pos.asLong(),emission,AggregateLightCache.color(TextureColors.emission(world,pos,state))));
                }
                tile.cursor++; updated++;
            }
            if (tile.cursor == CELLS) {
                tile.data = tile.building; tile.building = null; tile.revision = ++revision;
                // Adopt this scan even if another is queued; continuous updates must
                // not prevent source removal/discovery from ever reaching the worker.
                aggregates.replaceSources(key,tile.sources); tile.sources=null;
                setPage(page, tile.slot + 1); iterator.remove(); queuedAt.remove(key);
            } else break;
        }
    }

    private void sample(ClientLevel world, byte[] data, int index) {
        int brightness = world.getBrightness(LightLayer.BLOCK, pos);
        Vec3 tint = brightness > 0 ? ColoredLightCache.INSTANCE.tint(pos) : Vec3.ZERO;
        // Surface reconstruction may land just inside a face: borrow the brightest touching cell.
        for (Direction face : Direction.values()) {
            neighbor.setWithOffset(pos, face);
            if (!world.hasChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4)) continue;
            int next = world.getBrightness(LightLayer.BLOCK, neighbor);
            if (next > brightness) { brightness = next; tint = ColoredLightCache.INSTANCE.tint(neighbor); }
        }
        int alpha = brightness * 17, offset = index * 4;
        data[offset] = (byte)Math.round(tint.x * alpha);
        data[offset + 1] = (byte)Math.round(tint.y * alpha);
        data[offset + 2] = (byte)Math.round(tint.z * alpha);
        data[offset + 3] = (byte)alpha;
    }

    private void relocate(ClientLevel world, Vec3 camera, int cx, int cz, int range) {
        centerX = cx; centerZ = cz; radius = range;
        minX = cx - range; minZ = cz - range; minY = world.getMinSection();
        width = range * 2 + 1; height = world.getSectionsCount();
        pages = new int[width * height * width]; revision++;
        var old = tiles.entrySet().iterator();
        while (old.hasNext()) {
            var entry = old.next(); long key = entry.getKey();
            int i = index(SectionPos.x(key), SectionPos.y(key), SectionPos.z(key));
            if (i < 0) { freeSlots.add(entry.getValue().slot); old.remove(); aggregates.replaceSources(key,List.of()); }
            else if (entry.getValue().data != null) pages[i] = entry.getValue().slot + 1;
        }
        pending.removeIf(key -> index(SectionPos.x(key), SectionPos.y(key), SectionPos.z(key)) < 0);
        queuedAt.keySet().retainAll(pending);
        List<Long> additions = new ArrayList<>();
        for (int z = minZ; z < minZ + width; z++) for (int x = minX; x < minX + width; x++) {
            if (!world.hasChunk(x, z)) continue;
            for (int y = minY; y < minY + height; y++) {
                long key = SectionPos.asLong(x, y, z);
                if (!tiles.containsKey(key)) additions.add(key);
            }
        }
        additions.sort(Comparator.comparingDouble(key -> {
            double x = SectionPos.x(key)*16+8-camera.x, y=SectionPos.y(key)*16+8-camera.y, z=SectionPos.z(key)*16+8-camera.z;
            return x*x+y*y+z*z;
        }));
        additions.forEach(this::enqueue);
        reorder = true;
    }

    public void invalidate(int x0, int y0, int z0, int x1, int y1, int z1) {
        if (width == 0) return;
        // One border texel plus the touching-cell sample: invalidate neighboring bricks too.
        int ax = Math.max(minX, Math.floorDiv(x0 - 2, 16)), bx = Math.min(minX + width - 1, Math.floorDiv(x1 + 2, 16));
        int ay = Math.max(minY, Math.floorDiv(y0 - 2, 16)), by = Math.min(minY + height - 1, Math.floorDiv(y1 + 2, 16));
        int az = Math.max(minZ, Math.floorDiv(z0 - 2, 16)), bz = Math.min(minZ + width - 1, Math.floorDiv(z1 + 2, 16));
        for (int z = az; z <= bz; z++) for (int y = ay; y <= by; y++) for (int x = ax; x <= bx; x++) {
            long key = SectionPos.asLong(x,y,z);
            Tile tile = tiles.get(key);
            if (tile != null && tile.building != null) tile.dirtyAgain = true;
            else {
                // An empty section may have received new light since it was inspected.
                // Use the populated grid estimate until the new brick is ready.
                if (tile == null) setPage(index(x,y,z),0);
                enqueue(key);
            }
        }
    }
    private static double distanceSquared(long key, Vec3 camera) {
        double x=SectionPos.x(key)*16+8-camera.x, y=SectionPos.y(key)*16+8-camera.y, z=SectionPos.z(key)*16+8-camera.z;
        return x*x+y*y+z*z;
    }
    private void enqueue(long key) {
        if (pending.add(key)) { queuedAt.put(key,tick); reorder=true; }
    }
    private Tile newTile(){return new Tile(freeSlots.isEmpty()?nextSlot++:freeSlots.remove(),++identities);}
    /** Adopt an already baked worker array; no per-cell world sampling or merging here. */
    public void applyAggregate(long key,byte[] data,byte[] directions) {
        int sx=SectionPos.x(key),sy=SectionPos.y(key),sz=SectionPos.z(key),page=index(sx,sy,sz);
        if(page<0||level==null||!level.hasChunk(sx,sz))return;
        Tile tile=tiles.get(key);
        if(tile==null&&data==null)return;
        if(tile==null){tile=newTile();tiles.put(key,tile);}
        if(tile.aggregateData==data)return;
        tile.aggregateData=data;tile.directionData=directions;tile.aggregateRevision=++revision;
        if(data==null&&tile.data==EMPTY_BRICK){remove(key);setPage(page,-1);return;}
        if(tile.data==null) {
            var light=level.getLightEngine().getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(key));
            if(light==null||light.isEmpty()||Arrays.equals(light.getData(),ZERO_LIGHT)) {tile.data=EMPTY_BRICK;tile.revision=++revision;}
            else enqueue(key);
        }
        if(tile.data!=null)setPage(page,tile.slot+1);
    }
    boolean hasAggregate(long key,byte[] data) {
        Tile tile=tiles.get(key);return tile!=null&&tile.aggregateData==data;
    }
    private int index(int x, int y, int z) {
        x-=minX; y-=minY; z-=minZ;
        return x<0||x>=width||y<0||y>=height||z<0||z>=width ? -1 : x + width * (y + height*z);
    }
    private void setPage(int index, int value) { if (pages[index] != value) { pages[index] = value; revision++; } }
    private void remove(long key) { Tile old = tiles.remove(key); if (old != null) freeSlots.add(old.slot); aggregates.replaceSources(key,List.of()); }
    public Collection<Tile> tiles() { return tiles.values(); }
    public int slots() { return nextSlot; }
    public int pending() { return pending.size(); }
    /** Nearest cached native-light texel for first-person lighting; never resolves world colors. */
    public int sample(Vec3 position) {
        int x=(int)Math.floor(position.x),y=(int)Math.floor(position.y),z=(int)Math.floor(position.z);
        Tile tile=tiles.get(SectionPos.asLong(x>>4,y>>4,z>>4));
        if(tile==null||tile.data==null)return 0;
        int i=(((z&15)+1)*18*18+((y&15)+1)*18+(x&15)+1)*4;
        int alpha=tile.data[i+3]&255;if(alpha==0)return 0;
        return (alpha<<24)|(Math.min(255,(tile.data[i]&255)*255/alpha)<<16)
                |(Math.min(255,(tile.data[i+1]&255)*255/alpha)<<8)|Math.min(255,(tile.data[i+2]&255)*255/alpha);
    }
    public long revision() { return revision; }
    public long generation() { return generation; }
    public void clear() {
        tiles.clear(); pending.clear(); queuedAt.clear(); freeSlots.clear(); nextSlot = 0;
        pages = new int[0]; width = height = updated = 0; centerX = Integer.MIN_VALUE;
        level = null; tick = Long.MIN_VALUE; revision++; generation++;
    }
}
