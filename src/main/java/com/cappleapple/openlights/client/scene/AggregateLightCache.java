package com.cappleapple.openlights.client.scene;

import com.cappleapple.openlights.config.ClientConfig;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Client-thread coordinator. Workers own propagation state and consume immutable materials. */
public final class AggregateLightCache {
    public record Source(long position,int emission,int color) {}
    private final Map<Long,List<Source>> sourceSections=new HashMap<>();
    private final LinkedHashMap<Long,LightMaterialSnapshots.Section> snapshots=new LinkedHashMap<>(64,.75f,true);
    private final LightingTask<AggregatePropagation.Result> task=new LightingTask<>();
    private LightMaterialSnapshots.Broker broker;
    private LightMaterialSnapshots.Capture capture;
    private Long2IntOpenHashMap field=new Long2IntOpenHashMap();
    private Set<Long> sections=Set.of();
    private Map<Long,byte[]> publishedBricks=Map.of();
    private Map<Long,byte[]> publishedDirections=Map.of();
    private boolean analytic;
    private final Set<Long> appliedSections=new HashSet<>();
    private final LinkedHashMap<Long,byte[]> applying=new LinkedHashMap<>();
    private ClientLevel world;
    private boolean dirty,limited;
    private long tick,lastStart=-20,revision,version;
    private int centerX,centerZ,radius,updated,captured,applied,discarded;
    private String settings="",lastWorker="";
    private double workerMillis;
    private int jobCenterX,jobCenterZ,jobRadius;
    private boolean captureInvalidated;
    private RadialLightChannels channels;
    private final Set<Long> invalidated=new HashSet<>();
    private final Map<Long,Source> recentSources=new HashMap<>();
    private boolean invalidateAllChannels;
    private long sourceRevision,liveRevision=-1;
    private RadialLightChannels liveGeneration;
    private Set<AnalyticBlockLighting.Emitter> liveKeys=Set.of();

    private void changed() {
        // A running snapshot is allowed to finish. New edits belong to the next job.
        dirty=true;
        sourceRevision++;
    }
    private void cancelJob() {
        version++;dirty=true;
        if(task.active()){discarded++;task.cancel();}
        broker=null;capture=null;captureInvalidated=false;
    }
    public void replaceSources(long section,List<Source> sources) {
        if(world!=null) {
            var current=new HashMap<Long,Source>();
            for(var source:sources) {
                var p=BlockPos.of(source.position());
                if(world.hasChunk(p.getX()>>4,p.getZ()>>4)) {
                    var state=world.getBlockState(p);int emission=state.getLightEmission(world,p);
                    if(emission>0)current.put(source.position(),recentSources.getOrDefault(source.position(),emission==source.emission()?source:new Source(source.position(),emission,color(TextureColors.emission(world,p,state)))));
                }
                if(source.equals(recentSources.get(source.position())))recentSources.remove(source.position());
            }
            for(var entry:recentSources.entrySet()) {
                var p=BlockPos.of(entry.getKey());
                if(SectionPos.asLong(p.getX()>>4,p.getY()>>4,p.getZ()>>4)==section&&world.hasChunk(p.getX()>>4,p.getZ()>>4)&&world.getBlockState(p).getLightEmission(world,p)>0)current.put(entry.getKey(),entry.getValue());
            }
            sources=current.values().stream().sorted(Comparator.comparingLong(Source::position)).toList();
        }
        List<Source> old=sourceSections.get(section);
        if(sources.isEmpty()) { if(sourceSections.remove(section)!=null)changed(); }
        else if(!sources.equals(old)){sourceSections.put(section,List.copyOf(sources));changed();}
    }
    void prepare(ClientLevel level,long now) {
        if(world!=level||now<tick){clear();world=level;}
    }
    public void update(ClientLevel level,Vec3 camera,int distance,long now,BlockLightSections bricks) {
        prepare(level,now);
        tick=now;updated=captured=applied=0;
        int cx=(int)Math.floor(camera.x)>>4,cz=(int)Math.floor(camera.z)>>4,range=distance+1;
        if(radius!=range||centerX!=cx||centerZ!=cz) {
            centerX=cx;centerZ=cz;radius=range;
            sourceSections.keySet().removeIf(key->outside(key));
            snapshots.keySet().removeIf(key->outside(key));
            invalidated.removeIf(this::outside);
            recentSources.keySet().removeIf(position->outside(SectionPos.asLong(BlockPos.getX(position)>>4,BlockPos.getY(position)>>4,BlockPos.getZ(position)>>4)));
            changed();
            applying.keySet().removeIf(this::outside);
            appliedSections.removeIf(this::outside);
            for(var entry:publishedBricks.entrySet())if(!outside(entry.getKey())&&!bricks.hasAggregate(entry.getKey(),entry.getValue()))
                applying.putIfAbsent(entry.getKey(),entry.getValue());
        }
        String next=ClientConfig.AGGREGATE_ENABLED.get()+":"+ClientConfig.AGGREGATE_MULTIPLIER.get()+":"
                +ClientConfig.AGGREGATE_STRENGTH.get()+":"+ClientConfig.AGGREGATE_MAX_CELLS.get()+":"+ClientConfig.AGGREGATE_SNAPSHOT_SECTIONS.get()+":"+ClientConfig.BLOCK_LIGHT_STYLE.get()+":"+ClientConfig.SOURCE_CACHE_MIB.get();
        if(!next.equals(settings)){settings=next;cancelJob();channels=null;}
        boolean openLights=ClientConfig.BLOCK_LIGHT_STYLE.get()==ClientConfig.BlockLightStyle.OPEN_LIGHTS;
        if(!ClientConfig.AGGREGATE_ENABLED.get()&&!openLights) {
            if(!field.isEmpty()||!publishedBricks.isEmpty()||(!appliedSections.isEmpty()&&applying.isEmpty()))publish(new AggregatePropagation.Result(new Long2IntOpenHashMap(),Map.of(),Set.of(),false,"",0));
            dirty=false;limited=false;apply(bricks,now);return;
        }
        // Drain publication before accepting another generation. Updates must not erase
        // sections that have already been calculated but have not reached the GPU yet.
        collectCompleted();
        if(!task.active()&&dirty&&applying.isEmpty()&&now-lastStart>=2
                && (analytic||!sourceSections.isEmpty()||bricks.pending()==0)) {
            lastStart=now;dirty=false;
            jobCenterX=cx;jobCenterZ=cz;jobRadius=range;
            List<Source> sources=new ArrayList<>();sourceSections.values().forEach(sources::addAll);
            broker=new LightMaterialSnapshots.Broker(snapshots,ClientConfig.AGGREGATE_SNAPSHOT_SECTIONS.get());
            var work=new AggregatePropagation(sources,broker,cx,cz,range,world.getMinBuildHeight(),world.getMaxBuildHeight(),
                    ClientConfig.AGGREGATE_MAX_CELLS.get(),ClientConfig.AGGREGATE_MULTIPLIER.get(),ClientConfig.AGGREGATE_STRENGTH.get());
            if(openLights) {
                var radial=new AnalyticBlockLighting(sources,broker,cx,cz,range,world.getMinBuildHeight(),world.getMaxBuildHeight(),
                        ClientConfig.AGGREGATE_MAX_CELLS.get(),ClientConfig.AGGREGATE_MULTIPLIER.get(),ClientConfig.AGGREGATE_STRENGTH.get(),ClientConfig.AGGREGATE_ENABLED.get());
                radial.cache(invalidateAllChannels?null:channels,invalidated,ClientConfig.SOURCE_CACHE_MIB.get()*1024L*1024);
                invalidateAllChannels=false;
                invalidated.clear();
                task.submit(version,radial::run);
            } else task.submit(version,work::run);
        }
        if(task.active()) {
            if(!task.done())capture();
            collectCompleted();
        }
        apply(bricks,now);
    }
    private void collectCompleted() {
        if(!task.active()||!applying.isEmpty())return;
        try {
            var completed=task.poll(version);
            if(completed!=null){limited=completed.limited()||broker.limited;lastWorker=completed.thread();workerMillis=completed.nanos()/1_000_000.0;publish(completed);broker=null;capture=null;}
        }catch(RuntimeException failure){
            com.mojang.logging.LogUtils.getLogger().error("Aggregate lighting worker failed; retaining native lighting",failure);
            publish(new AggregatePropagation.Result(new Long2IntOpenHashMap(),Map.of(),Set.of(),false,"",0));broker=null;capture=null;
        }
    }
    private boolean outside(long key){return Math.abs(SectionPos.x(key)-centerX)>radius||Math.abs(SectionPos.z(key)-centerZ)>radius;}
    private void capture() {
        long deadline=System.nanoTime()+(long)(ClientConfig.AGGREGATE_BUDGET_MILLIS.get()*1_000_000);
        int budget=ClientConfig.AGGREGATE_CELLS_PER_TICK.get(),inspected=0;
        while(captured<budget&&(captured==0||System.nanoTime()<deadline)&&inspected++<budget) {
            if(capture==null) {
                Long key=broker.requests.poll();if(key==null)break;
                var known=snapshots.get(key);
                if(known!=null){broker.provide(key,known);continue;}
                if(Math.abs(SectionPos.x(key)-jobCenterX)>jobRadius||Math.abs(SectionPos.z(key)-jobCenterZ)>jobRadius
                        ||!world.hasChunk(SectionPos.x(key),SectionPos.z(key))){broker.provide(key,LightMaterialSnapshots.Section.SOLID);continue;}
                capture=new LightMaterialSnapshots.Capture(key);captureInvalidated=false;
            }
            capture.cell(world);captured++;updated++;
            if(capture.cursor==4096) {
                var snapshot=capture.finish();
                // A mid-capture edit must be captured again by the follow-up job.
                if(!captureInvalidated&&!outside(capture.key))snapshots.put(capture.key,snapshot);
                broker.provide(capture.key,snapshot);capture=null;
                while(snapshots.size()>ClientConfig.AGGREGATE_SNAPSHOT_SECTIONS.get())snapshots.remove(snapshots.keySet().iterator().next());
            }
        }
    }
    private void publish(AggregatePropagation.Result result) {
        var oldBricks=publishedBricks;var oldDirections=publishedDirections;
        field=result.field();sections=result.sections();publishedBricks=result.bricks();publishedDirections=result.directions();analytic=result.analytic();revision++;
        channels=result.channels();
        applying.clear();for(long key:appliedSections)if(!publishedBricks.containsKey(key))applying.put(key,null);
        for(var entry:publishedBricks.entrySet())if(entry.getValue()!=oldBricks.get(entry.getKey())||publishedDirections.get(entry.getKey())!=oldDirections.get(entry.getKey())||!appliedSections.contains(entry.getKey()))applying.put(entry.getKey(),entry.getValue());
    }
    private void apply(BlockLightSections bricks,long now) {
        var iterator=applying.entrySet().iterator();
        while(iterator.hasNext()&&applied<ClientConfig.AGGREGATE_APPLY_SECTIONS.get()) {
            var entry=iterator.next();long key=entry.getKey();byte[] data=entry.getValue();
            bricks.applyAggregate(key,data,publishedDirections.get(key));
            if(data==null)appliedSections.remove(key);else appliedSections.add(key);
            iterator.remove();applied++;updated++;
        }
    }
    public void invalidate(int x0,int y0,int z0,int x1,int y1,int z1) {
        boolean small=world!=null&&(long)x1-x0<4&&(long)y1-y0<4&&(long)z1-z0<4
                &&(long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1)<=64;
        Set<Long> unchanged=new HashSet<>();
        var iterator=snapshots.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();long key=entry.getKey();
            if(!intersects(key,x0-1,y0-1,z0-1,x1+1,y1+1,z1+1))continue;
            if(small&&world.hasChunk(SectionPos.x(key),SectionPos.z(key))) {
                var previous=entry.getValue();var patched=LightMaterialSnapshots.patch(key,previous,world,x0-1,y0-1,z0-1,x1+1,y1+1,z1+1);
                if(previous==patched)unchanged.add(key);else entry.setValue(patched);
            }
            else iterator.remove();
        }
        // View-radius notifications can cover the entire world. Never enumerate them.
        if((long)x1-x0>2048||(long)z1-z0>2048)invalidateAllChannels=true;
        else if(world!=null) {
            int lowX=Math.min(centerX-radius,task.active()?jobCenterX-jobRadius:centerX-radius);
            int highX=Math.max(centerX+radius,task.active()?jobCenterX+jobRadius:centerX+radius);
            int lowZ=Math.min(centerZ-radius,task.active()?jobCenterZ-jobRadius:centerZ-radius);
            int highZ=Math.max(centerZ+radius,task.active()?jobCenterZ+jobRadius:centerZ+radius);
            for(int z=Math.max((z0-1)>>4,lowZ);z<=Math.min((z1+1)>>4,highZ);z++)
                for(int y=Math.max((y0-1)>>4,world.getMinSection());y<Math.min(((y1+1)>>4)+1,world.getMaxSection());y++)
                    for(int x=Math.max((x0-1)>>4,lowX);x<=Math.min((x1+1)>>4,highX);x++) {
                        long key=SectionPos.asLong(x,y,z);if(!unchanged.contains(key))invalidated.add(key);
                    }
        }
        if(small)for(int z=z0;z<=z1;z++)for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++) {
            var p=new BlockPos(x,y,z);if(!world.hasChunk(x>>4,z>>4))continue;
            long section=SectionPos.asLong(x>>4,y>>4,z>>4);var next=new ArrayList<>(sourceSections.getOrDefault(section,List.of()));
            next.removeIf(source->source.position()==p.asLong());recentSources.remove(p.asLong());
            var state=world.getBlockState(p);int emission=state.getLightEmission(world,p);
            if(emission>0) {
                var source=new Source(p.asLong(),emission,color(TextureColors.emission(world,p,state)));next.add(source);recentSources.put(p.asLong(),source);
            }
            next.sort(Comparator.comparingLong(Source::position));replaceSources(section,next);
            if(emission>0)recentSources.put(p.asLong(),next.stream().filter(source->source.position()==p.asLong()).findFirst().orElseThrow());
        }
        if(capture!=null&&intersects(capture.key,x0-1,y0-1,z0-1,x1+1,y1+1,z1+1))captureInvalidated=true;
        // Source scans replace authoritative lists; a dirty notification alone does not
        // mean every emitter in that region has disappeared. Include dark sections:
        // removing an occluder can create light where no previous lit section existed.
        if(inView(x0,z0,x1,z1,centerX,centerZ,radius)
                ||task.active()&&inView(x0,z0,x1,z1,jobCenterX,jobCenterZ,jobRadius))changed();
    }
    private static boolean inView(int x0,int z0,int x1,int z1,int cx,int cz,int range) {
        return range>0&&x1>=(cx-range)*16-1&&x0<=(cx+range+1)*16
                &&z1>=(cz-range)*16-1&&z0<=(cz+range+1)*16;
    }
    private static boolean intersects(long key,int x0,int y0,int z0,int x1,int y1,int z1){
        int x=SectionPos.x(key)*16,y=SectionPos.y(key)*16,z=SectionPos.z(key)*16;
        return x+15>=x0&&x<=x1&&y+15>=y0&&y<=y1&&z+15>=z0&&z<=z1;
    }
    public int sample(BlockPos pos){return field.get(pos.asLong());}
    public boolean analytic(){return analytic;}
    public int cells(){return field.size();}
    public long revision(){return revision;}
    public int updated(){return updated;}
    public boolean pending(){return dirty||task.active()||!applying.isEmpty();}
    public boolean limited(){return limited;}
    public boolean workerActive(){return task.active();}
    public int captured(){return captured;}
    public int applied(){return applied;}
    public int discarded(){return discarded;}
    public String workerThread(){return lastWorker;}
    public double workerMillis(){return workerMillis;}
    RadialLightChannels channels(){return channels;}
    Set<AnalyticBlockLighting.Emitter> liveChannelKeys() {
        if(channels==null){liveGeneration=null;liveKeys=Set.of();return liveKeys;}
        if(liveGeneration==channels&&liveRevision==sourceRevision)return liveKeys;
        var live=new it.unimi.dsi.fastutil.longs.LongOpenHashSet();sourceSections.values().forEach(list->list.forEach(source->live.add(source.position())));
        Set<AnalyticBlockLighting.Emitter> result=new HashSet<>();
        for(var source:channels.channels.keySet())if(live.containsAll(source.members()))result.add(source);
        liveGeneration=channels;liveRevision=sourceRevision;liveKeys=Set.copyOf(result);return liveKeys;
    }
    public int sourceChannels(){return channels==null?0:channels.channels.size();}
    public int tracedChannels(){return channels==null?0:channels.traced;}
    public int reusedChannels(){return channels==null?0:channels.reused;}
    public int recomposedCells(){return channels==null?0:channels.recomposed;}
    public long channelBytes(){return channels==null?0:channels.bytes;}
    public void clear(){
        cancelJob();sourceSections.clear();snapshots.clear();field=new Long2IntOpenHashMap();sections=Set.of();publishedBricks=Map.of();publishedDirections=Map.of();analytic=false;
        appliedSections.clear();applying.clear();world=null;dirty=false;updated=captured=applied=radius=0;
        channels=null;invalidated.clear();recentSources.clear();invalidateAllChannels=false;
        liveGeneration=null;liveKeys=Set.of();liveRevision=-1;
        settings="";tick=0;lastStart=-20;limited=false;revision++;
    }
    public static int color(Vec3 tint){
        return ((int)Math.round(Math.max(0,Math.min(1,tint.x))*255)<<16)
                |((int)Math.round(Math.max(0,Math.min(1,tint.y))*255)<<8)
                |(int)Math.round(Math.max(0,Math.min(1,tint.z))*255);
    }
}
