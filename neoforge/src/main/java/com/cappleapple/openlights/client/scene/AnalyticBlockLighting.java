package com.cappleapple.openlights.client.scene;

import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import java.util.*;

/** Cached point-light approximation. All world/material access goes through immutable snapshots. */
final class AnalyticBlockLighting {
    record Emitter(double x,double y,double z,double gain,double reach,double power,int color,Set<Long> members) {}
    private final List<AggregateLightCache.Source> sources;
    private final AggregatePropagation.Materials materials;
    private final int cx,cz,radius,minY,maxY,limit;
    private final double multiplier,strength;
    private final boolean aggregate;
    private final Long2ObjectOpenHashMap<AggregateLightCache.Source> lookup=new Long2ObjectOpenHashMap<>();
    private final Long2IntOpenHashMap field=new Long2IntOpenHashMap(),directions=new Long2IntOpenHashMap();
    private final Set<Long> sections=new HashSet<>();
    private boolean limited;
    private RadialLightChannels previous;
    private Set<Long> invalidated=Set.of();
    private long cacheBudget;
    AnalyticBlockLighting(List<AggregateLightCache.Source> sources,AggregatePropagation.Materials materials,
                         int cx,int cz,int radius,int minY,int maxY,int limit,double multiplier,double strength,boolean aggregate) {
        this.sources=new ArrayList<>(sources);this.materials=materials;this.cx=cx;this.cz=cz;this.radius=radius;
        this.minY=minY;this.maxY=maxY;this.limit=limit;this.multiplier=multiplier;this.strength=strength;this.aggregate=aggregate;
    }
    AnalyticBlockLighting cache(RadialLightChannels previous,Set<Long> invalidated,long budget) {
        this.previous=previous;this.invalidated=Set.copyOf(invalidated);cacheBudget=budget;return this;
    }
    AggregatePropagation.Result run() throws InterruptedException {
        if(cacheBudget>0)try{return runCached();}catch(CacheOverflow overflow){
            field.clear();directions.clear();sections.clear();lookup.clear();limited=false;
        }
        return runUncached();
    }
    private static final class CacheOverflow extends RuntimeException {}
    private AggregatePropagation.Result runCached() throws InterruptedException {
        long start=System.nanoTime();sources.sort(Comparator.comparingLong(AggregateLightCache.Source::position));
        for(var source:sources){check();lookup.put(source.position(),source);}
        Map<Emitter,RadialLightChannels.Channel> channels=new LinkedHashMap<>();
        var changed=new LongOpenHashSet();long bytes=0;int traced=0,reused=0;
        if(previous!=null&&!previous.limited){field.putAll(previous.field);directions.putAll(previous.directions);}
        else previous=null;
        for(var light:emitters()) {
            check();var coverage=coverage(light);var old=previous==null?null:previous.channels.get(light);
            RadialLightChannels.Channel channel;
            if(old!=null&&covers(old.coverage(),coverage)&&!old.dirty(invalidated)){channel=old;reused++;}
            else {
                channel=trace(light,coverage);traced++;
                if(old!=null)changed.addAll(old.values().keySet());changed.addAll(channel.values().keySet());
            }
            channels.put(light,channel);bytes+=channel.bytes();if(bytes>cacheBudget)throw new CacheOverflow();
        }
        if(previous!=null)for(var old:previous.channels.values())if(!channels.containsKey(old.source()))changed.addAll(old.values().keySet());
        var iterator=field.keySet().iterator();
        while(iterator.hasNext()) {
            long p=iterator.nextLong();if(!inside(p)){iterator.remove();directions.remove(p);changed.add(p);}
        }
        // Local source index keeps removal recomposition independent of distant emitter count.
        var index=new RadialLightChannels(channels,field,directions,Map.of(),Map.of(),traced,reused,0,false);
        Set<Long> affected=new HashSet<>();
        for(long p:changed) {
            check();var blend=new LightBlend();
            if(inside(p))for(var channel:index.at(p)) {
                long packed=channel.values().get(p);if(packed==0)continue;
                int value=(int)(packed>>>32),direction=(int)packed;double alpha=(value>>>24)/255.0;
                blend.add(alpha*alpha,value&0xffffff,(direction>>16&255)/127.5-1,(direction>>8&255)/127.5-1,(direction&255)/127.5-1);
            }
            int value=blend.value();
            if(value==0){field.remove(p);directions.remove(p);}
            else if(field.containsKey(p)||field.size()<limit){field.put(p,value);directions.put(p,blend.direction());}
            else limited=true;
            affected.add(SectionPos.asLong(BlockPos.getX(p)>>4,BlockPos.getY(p)>>4,BlockPos.getZ(p)>>4));
        }
        Map<Long,byte[]> bricks=previous==null?new HashMap<>():new HashMap<>(previous.bricks);
        Map<Long,byte[]> directionBricks=previous==null?new HashMap<>():new HashMap<>(previous.directionBricks);
        bricks.keySet().removeIf(key->!sectionInside(key));directionBricks.keySet().retainAll(bricks.keySet());
        bake(affected,bricks,directionBricks);
        sections.addAll(bricks.keySet());
        var state=new RadialLightChannels(channels,field,directions,bricks,directionBricks,traced,reused,changed.size(),limited);
        return new AggregatePropagation.Result(field,bricks,sections,limited,Thread.currentThread().getName(),System.nanoTime()-start,directionBricks,true,state);
    }
    private boolean inside(long p){return BlockPos.getY(p)>=minY&&BlockPos.getY(p)<maxY&&Math.abs((BlockPos.getX(p)>>4)-cx)<=radius&&Math.abs((BlockPos.getZ(p)>>4)-cz)<=radius;}
    private boolean sectionInside(long key){return Math.abs(SectionPos.x(key)-cx)<=radius&&Math.abs(SectionPos.z(key)-cz)<=radius&&SectionPos.y(key)*16<maxY&&(SectionPos.y(key)+1)*16>minY;}
    private static boolean covers(net.minecraft.world.phys.AABB a,net.minecraft.world.phys.AABB b){return a.minX<=b.minX&&a.minY<=b.minY&&a.minZ<=b.minZ&&a.maxX>=b.maxX&&a.maxY>=b.maxY&&a.maxZ>=b.maxZ;}
    private net.minecraft.world.phys.AABB coverage(Emitter light) {
        int reach=(int)Math.ceil(light.reach);
        return new net.minecraft.world.phys.AABB(Math.max((cx-radius)*16,(int)Math.floor(light.x)-reach),Math.max(minY,(int)Math.floor(light.y)-reach),Math.max((cz-radius)*16,(int)Math.floor(light.z)-reach),
                Math.min((cx+radius+1)*16,(int)Math.floor(light.x)+reach+1),Math.min(maxY,(int)Math.floor(light.y)+reach+1),Math.min((cz+radius+1)*16,(int)Math.floor(light.z)+reach+1));
    }
    private RadialLightChannels.Channel trace(Emitter light,net.minecraft.world.phys.AABB b) throws InterruptedException {
        for(int z=((int)b.minZ)>>4;z<=((int)b.maxZ-1)>>4;z++)for(int y=((int)b.minY)>>4;y<=((int)b.maxY-1)>>4;y++)for(int x=((int)b.minX)>>4;x<=((int)b.maxX-1)>>4;x++)materials.request(SectionPos.asLong(x,y,z));
        var values=new Long2LongOpenHashMap();
        for(int z=(int)b.minZ;z<b.maxZ;z++)for(int y=(int)b.minY;y<b.maxY;y++)for(int x=(int)b.minX;x<b.maxX;x++) {
            check();long p=BlockPos.asLong(x,y,z);
            double dx=light.x-x-.5,dy=light.y-y-.5,dz=light.z-z-.5,distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
            double value=attenuation(distance,light.reach,light.gain)*light.power;if(value<1.0/(255*255))continue;
            int tint;
            while(true)try{tint=transmission(x,y,z,light);break;}catch(LightMaterialSnapshots.Missing missing){check();materials.await();}
            if(tint<0)continue;
            if(values.size()>=limit){limited=true;continue;}
            int alpha=byteChannel(Math.sqrt(value)*255),color=(alpha<<24)|AggregatePropagation.filter(light.color,tint);
            double inverse=distance>.001?1/distance:0;
            int direction=(byteChannel((dx*inverse*.5+.5)*255)<<16)|(byteChannel((dy*inverse*.5+.5)*255)<<8)|byteChannel((dz*inverse*.5+.5)*255);
            values.put(p,((long)color<<32)|(direction&0xffffffffL));
            if(values.size()*32L>cacheBudget)throw new CacheOverflow();
        }
        return new RadialLightChannels.Channel(light,b,values);
    }
    private static int byteChannel(double v){return Math.max(0,Math.min(255,(int)Math.round(v)));}
    private AggregatePropagation.Result runUncached() throws InterruptedException {
        long start=System.nanoTime();
        sources.sort(Comparator.comparingLong(AggregateLightCache.Source::position));
        for(var source:sources){check();lookup.put(source.position(),source);}
        List<Emitter> emitters=emitters();
        var blended=new Long2ObjectOpenHashMap<LightBlend>();
        // Request ahead so high capture budgets can fill many sections in the same tick.
        for(var light:emitters) {
            check();int reach=(int)Math.ceil(light.reach)+1;
            for(int z=((int)light.z-reach)>>4;z<=((int)light.z+reach)>>4;z++)
                for(int x=((int)light.x-reach)>>4;x<=((int)light.x+reach)>>4;x++) {
                    if(Math.abs(x-cx)>radius||Math.abs(z-cz)>radius)continue;
                    for(int y=Math.max(minY,((int)light.y-reach))>>4;y<=Math.min(maxY-1,((int)light.y+reach))>>4;y++)
                        materials.request(SectionPos.asLong(x,y,z));
                }
        }
        for(var light:emitters) {
            int reach=(int)Math.ceil(light.reach);
            int ax=Math.max((cx-radius)*16,(int)Math.floor(light.x)-reach),bx=Math.min((cx+radius+1)*16-1,(int)Math.floor(light.x)+reach);
            int az=Math.max((cz-radius)*16,(int)Math.floor(light.z)-reach),bz=Math.min((cz+radius+1)*16-1,(int)Math.floor(light.z)+reach);
            for(int z=az;z<=bz;z++)for(int y=Math.max(minY,(int)Math.floor(light.y)-reach);y<=Math.min(maxY-1,(int)Math.floor(light.y)+reach);y++)for(int x=ax;x<=bx;x++) {
                check();long position=BlockPos.asLong(x,y,z);
                double dx=light.x-x-.5,dy=light.y-y-.5,dz=light.z-z-.5,distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
                double value=attenuation(distance,light.reach,light.gain)*light.power;
                if(value<1.0/(255*255))continue;
                if(!blended.containsKey(position)&&blended.size()>=limit){limited=true;continue;}
                int tint;
                while(true) {
                    try { tint=transmission(x,y,z,light);break; }
                    catch(LightMaterialSnapshots.Missing missing){check();materials.await();}
                }
                if(tint<0)continue;
                tint=AggregatePropagation.filter(light.color,tint);
                double inv=distance>0.001?1/distance:0;
                blended.computeIfAbsent(position,unused->new LightBlend()).add(value,tint,dx*inv,dy*inv,dz*inv);
                sections.add(SectionPos.asLong(x>>4,y>>4,z>>4));
            }
        }
        var iterator=blended.long2ObjectEntrySet().fastIterator();
        while(iterator.hasNext()) {
            check();var entry=iterator.next();
            field.put(entry.getLongKey(),entry.getValue().value());
            directions.put(entry.getLongKey(),entry.getValue().direction());
            iterator.remove();
        }
        Map<Long,byte[]> bricks=new HashMap<>(),directionBricks=new HashMap<>();
        bake(sections,bricks,directionBricks);
        return new AggregatePropagation.Result(field,bricks,sections,limited,Thread.currentThread().getName(),System.nanoTime()-start,directionBricks,true);
    }
    private void bake(Set<Long> affected,Map<Long,byte[]> bricks,Map<Long,byte[]> directionBricks) throws InterruptedException {
        Set<Long> candidates=new HashSet<>();
        for(long section:affected)for(int z=-1;z<=1;z++)for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++) {
            int sx=SectionPos.x(section)+x,sy=SectionPos.y(section)+y,sz=SectionPos.z(section)+z;
            if(Math.abs(sx-cx)<=radius&&Math.abs(sz-cz)<=radius&&sy*16<maxY&&(sy+1)*16>minY)candidates.add(SectionPos.asLong(sx,sy,sz));
        }
        for(long key:candidates) {
            check();byte[] data=new byte[18*18*18*4],direction=new byte[data.length];boolean lit=false;
            int sx=SectionPos.x(key)*16,sy=SectionPos.y(key)*16,sz=SectionPos.z(key)*16;
            for(int i=0;i<18*18*18;i++) {
                long pos=BlockPos.asLong(sx+i%18-1,sy+i/18%18-1,sz+i/324-1),chosen=pos;
                int value=field.get(pos);
                // Surface reconstruction can land just inside a solid face.
                if(value==0)for(Direction face:Direction.values()) {
                    long neighbor=BlockPos.asLong(BlockPos.getX(pos)+face.getStepX(),BlockPos.getY(pos)+face.getStepY(),BlockPos.getZ(pos)+face.getStepZ());
                    int other=field.get(neighbor);if((other>>>24)>(value>>>24)){value=other;chosen=neighbor;}
                }
                int alpha=value>>>24;if(alpha==0)continue;lit=true;
                pack(data,i,value&0xffffff,alpha);pack(direction,i,directions.get(chosen),alpha);
                var emitter=lookup.get(pos);direction[i*4+3]=(byte)(emitter==null?0:emitter.emission()*17);
            }
            if(lit){
                if(!Arrays.equals(data,bricks.get(key)))bricks.put(key,data);
                if(!Arrays.equals(direction,directionBricks.get(key)))directionBricks.put(key,direction);
            } else {bricks.remove(key);directionBricks.remove(key);}
        }
    }
    static double attenuation(double distance,double reach,double gain) {
        double edge=Math.max(0,1-distance/reach),scaled=distance/gain;
        return edge*edge/(1+scaled*scaled*.06);
    }
    private List<Emitter> emitters() throws InterruptedException {
        Set<Long> used=new HashSet<>();List<Emitter> result=new ArrayList<>();
        for(var source:sources) {
            check();if(!used.add(source.position()))continue;
            int x=BlockPos.getX(source.position()),y=BlockPos.getY(source.position()),z=BlockPos.getZ(source.position());
            double total=0,px=0,py=0,pz=0;int peak=source.emission();
            Set<Long> members=new HashSet<>();ArrayDeque<AggregateLightCache.Source> connected=new ArrayDeque<>();connected.add(source);
            while(!connected.isEmpty()) {
                var member=connected.remove();long p=member.position();members.add(p);
                double power=member.emission();total+=power;peak=Math.max(peak,member.emission());
                px+=(BlockPos.getX(p)+.5)*power;py+=(BlockPos.getY(p)+.5)*power;pz+=(BlockPos.getZ(p)+.5)*power;
                if(aggregate)for(Direction face:Direction.values()) {
                    int nx=BlockPos.getX(p)+face.getStepX(),ny=BlockPos.getY(p)+face.getStepY(),nz=BlockPos.getZ(p)+face.getStepZ();
                    if(Math.abs(nx-x)>2||Math.abs(ny-y)>2||Math.abs(nz-z)>2)continue;
                    var other=lookup.get(BlockPos.asLong(nx,ny,nz));
                    if(other!=null&&other.color()==source.color()&&used.add(other.position()))connected.add(other);
                }
            }
            double gain=aggregate?17.0/AggregateFalloff.step(total/peak,multiplier,strength):1;
            result.add(new Emitter(px/total,py/total,pz/total,gain,peak*gain,peak/15.0,source.color(),Set.copyOf(members)));
        }
        return result;
    }
    /** Voxel DDA: opaque blocks stop direct light, translucent textures filter its color. */
    private int transmission(int x,int y,int z,Emitter light) {
        int ex=(int)Math.floor(light.x),ey=(int)Math.floor(light.y),ez=(int)Math.floor(light.z);
        double dx=light.x-x-.5,dy=light.y-y-.5,dz=light.z-z-.5;
        int sx=Double.compare(dx,0),sy=Double.compare(dy,0),sz=Double.compare(dz,0);
        double ax=dx==0?Double.POSITIVE_INFINITY:Math.abs(1/dx),ay=dy==0?Double.POSITIVE_INFINITY:Math.abs(1/dy),az=dz==0?Double.POSITIVE_INFINITY:Math.abs(1/dz);
        double tx=ax*.5,ty=ay*.5,tz=az*.5;int tint=0xffffff;
        for(int n=0;n<512;n++) {
            long p=BlockPos.asLong(x,y,z);int material=materials.optics(p);
            if(!light.members.contains(p)) {
                if((material>>>24)>=15)return -1;
                tint=AggregatePropagation.filter(tint,material);
            }
            if(x==ex&&y==ey&&z==ez)return tint;
            int face;
            if(tx<=ty&&tx<=tz){tx+=ax;x+=sx;face=sx>0?5:4;}
            else if(ty<=tz){ty+=ay;y+=sy;face=sy>0?1:0;}
            else{tz+=az;z+=sz;face=sz>0?3:2;}
            if(!light.members.contains(p)&&(materials.blocked(p)&(1<<face))!=0)return -1;
        }
        return -1;
    }
    private static void pack(byte[] data,int i,int rgb,int alpha){int o=i*4;data[o]=(byte)Math.round((rgb>>16&255)*alpha/255.0);data[o+1]=(byte)Math.round((rgb>>8&255)*alpha/255.0);data[o+2]=(byte)Math.round((rgb&255)*alpha/255.0);data[o+3]=(byte)alpha;}
    private static void check() throws InterruptedException {if(Thread.currentThread().isInterrupted())throw new InterruptedException();}
}
