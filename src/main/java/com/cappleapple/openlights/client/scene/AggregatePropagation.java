package com.cappleapple.openlights.client.scene;

import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import java.util.*;

/** Worker-only propagation and brick baking. No ClientLevel, texture access, config reads or GL. */
final class AggregatePropagation {
    interface Materials {
        int optics(long position);
        int blocked(long position);
        Long ready();
        long await() throws InterruptedException;
        default void request(long section) {}
    }
    record Result(Long2IntOpenHashMap field,Map<Long,byte[]> bricks,Set<Long> sections,boolean limited,String thread,long nanos,
                  Map<Long,byte[]> directions,boolean analytic,RadialLightChannels channels){
        Result(Long2IntOpenHashMap field,Map<Long,byte[]> bricks,Set<Long> sections,boolean limited,String thread,long nanos,Map<Long,byte[]> directions,boolean analytic){
            this(field,bricks,sections,limited,thread,nanos,directions,analytic,null);
        }
        Result(Long2IntOpenHashMap field,Map<Long,byte[]> bricks,Set<Long> sections,boolean limited,String thread,long nanos){
            this(field,bricks,sections,limited,thread,nanos,Map.of(),false,null);
        }
    }
    private record Wave(long position,int step,int level,int color){}
    private static final Direction[] FACES=Direction.values();
    private final List<AggregateLightCache.Source> sources;
    private final Materials materials;
    private final int cx,cz,radius,minY,maxY,limit;
    private final double multiplier,strength;
    private final Long2ObjectOpenHashMap<AggregateLightCache.Source> lookup=new Long2ObjectOpenHashMap<>();
    private final Map<Integer,Long2ByteOpenHashMap> visited=new HashMap<>();
    private final PriorityQueue<Wave> waves=new PriorityQueue<>(Comparator.comparingInt(Wave::level).reversed());
    private final Map<Long,List<Wave>> waiting=new HashMap<>();
    private final Long2IntOpenHashMap result=new Long2IntOpenHashMap();
    private final Set<Long> sections=new HashSet<>();
    private int count;
    private boolean limited;
    AggregatePropagation(List<AggregateLightCache.Source> sources,Materials materials,int cx,int cz,int radius,int minY,int maxY,int limit,double multiplier,double strength){
        this.sources=new ArrayList<>(sources);this.materials=materials;this.cx=cx;this.cz=cz;this.radius=radius;
        this.minY=minY;this.maxY=maxY;this.limit=limit;this.multiplier=multiplier;this.strength=strength;
    }
    Result run() throws InterruptedException {
        long start=System.nanoTime();
        sources.sort(Comparator.comparingLong(AggregateLightCache.Source::position));
        for(var source:sources){check();lookup.put(source.position(),source);}
        for(var source:sources){check();seed(source);}
        while(!waves.isEmpty()||!waiting.isEmpty()) {
            check();Long ready;
            while((ready=materials.ready())!=null)resume(ready);
            if(waves.isEmpty()){if(!waiting.isEmpty())resume(materials.await());continue;}
            Wave wave=waves.remove();
            try{spread(wave);}catch(LightMaterialSnapshots.Missing missing){waiting.computeIfAbsent(missing.section,unused->new ArrayList<>()).add(wave);}
        }
        Map<Long,byte[]> bricks=bake();
        return new Result(result,bricks,sections,limited,Thread.currentThread().getName(),System.nanoTime()-start);
    }
    private void resume(long key){var deferred=waiting.remove(key);if(deferred!=null)waves.addAll(deferred);}
    private static void check() throws InterruptedException{if(Thread.currentThread().isInterrupted())throw new InterruptedException();}
    private void seed(AggregateLightCache.Source source){
        int px=BlockPos.getX(source.position()),py=BlockPos.getY(source.position()),pz=BlockPos.getZ(source.position());
        double total=0;
        for(int z=-2;z<=2;z++)for(int y=-2;y<=2;y++)for(int x=-2;x<=2;x++){
            var other=lookup.get(BlockPos.asLong(px+x,py+y,pz+z));if(other!=null)total+=other.emission();
        }
        int step=AggregateFalloff.step(total/source.emission(),multiplier,strength);
        if(step<17)offer(source.position(),step,source.emission()*17,source.color());
    }
    private void offer(long position,int step,int light,int tint){
        var layer=visited.computeIfAbsent(step,unused->new Long2ByteOpenHashMap());
        int old=Byte.toUnsignedInt(layer.get(position));if(light<=old)return;
        if(old==0){if(count>=limit){limited=true;return;}count++;}
        layer.put(position,(byte)light);waves.add(new Wave(position,step,light,tint));
        if(light>(result.get(position)>>>24)){
            result.put(position,(light<<24)|tint);
            sections.add(SectionPos.asLong(BlockPos.getX(position)>>4,BlockPos.getY(position)>>4,BlockPos.getZ(position)>>4));
        }
    }
    private void spread(Wave wave){
        if(Byte.toUnsignedInt(visited.get(wave.step).get(wave.position))!=wave.level||wave.level<=wave.step)return;
        int px=BlockPos.getX(wave.position),py=BlockPos.getY(wave.position),pz=BlockPos.getZ(wave.position);
        int blocked=materials.blocked(wave.position);
        for(Direction face:FACES){
            if((blocked&(1<<face.ordinal()))!=0)continue;
            int x=px+face.getStepX(),y=py+face.getStepY(),z=pz+face.getStepZ();
            if(y<minY||y>=maxY||Math.abs((x>>4)-cx)>radius||Math.abs((z>>4)-cz)>radius)continue;
            long next=BlockPos.asLong(x,y,z);int material=materials.optics(next),opacity=material>>>24;
            if(opacity>=15)continue;
            int light=wave.level-wave.step-Math.max(0,opacity-1)*17;if(light<=0)continue;
            int tint=filter(wave.color,material);
            offer(next,wave.step,light,tint);
        }
    }
    static int filter(int color,int filter){
        return ((int)Math.round((color>>16&255)*(filter>>16&255)/255.0)<<16)
                |((int)Math.round((color>>8&255)*(filter>>8&255)/255.0)<<8)
                |(int)Math.round((color&255)*(filter&255)/255.0);
    }
    private Map<Long,byte[]> bake() throws InterruptedException{
        Set<Long> candidates=new HashSet<>();
        for(long section:sections)for(int z=-1;z<=1;z++)for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++){
            int sx=SectionPos.x(section)+x,sy=SectionPos.y(section)+y,sz=SectionPos.z(section)+z;
            if(Math.abs(sx-cx)<=radius&&Math.abs(sz-cz)<=radius&&sy*16<maxY&&(sy+1)*16>minY)candidates.add(SectionPos.asLong(sx,sy,sz));
        }
        Map<Long,byte[]> bricks=new HashMap<>();
        for(long key:candidates){
            check();byte[] data=new byte[18*18*18*4];boolean lit=false;
            int sx=SectionPos.x(key)*16,sy=SectionPos.y(key)*16,sz=SectionPos.z(key)*16;
            for(int i=0;i<18*18*18;i++){
                int x=sx+i%18-1,y=sy+i/18%18-1,z=sz+i/(18*18)-1;
                int value=result.get(BlockPos.asLong(x,y,z));
                for(Direction face:FACES){int other=result.get(BlockPos.asLong(x+face.getStepX(),y+face.getStepY(),z+face.getStepZ()));if((other>>>24)>(value>>>24))value=other;}
                int alpha=value>>>24;if(alpha==0)continue;lit=true;int offset=i*4;
                data[offset]=(byte)Math.round((value>>16&255)*alpha/255.0);
                data[offset+1]=(byte)Math.round((value>>8&255)*alpha/255.0);
                data[offset+2]=(byte)Math.round((value&255)*alpha/255.0);data[offset+3]=(byte)alpha;
            }
            if(lit)bricks.put(key,data);
        }
        return bricks;
    }
}
