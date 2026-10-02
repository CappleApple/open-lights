package com.cappleapple.openlights.client.scene;

import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Immutable worker-generation channels. Only the final combined bricks are uploaded. */
final class RadialLightChannels {
    record Channel(AnalyticBlockLighting.Emitter source,AABB coverage,Long2LongOpenHashMap values) {
        long bytes() {
            long capacity=1;while(capacity<Math.ceil((values.size()+1)/.75))capacity*=2;
            return (capacity+1)*16+256;
        }
        boolean dirty(Set<Long> regions) {
            for(long section:regions) {
                int x=SectionPos.x(section)*16,y=SectionPos.y(section)*16,z=SectionPos.z(section)*16;
                if(coverage.intersects(x,y,z,x+16,y+16,z+16))return true;
            }
            return false;
        }
        Vec3 sample(long position) {
            long encoded=values.get(position);int value=(int)(encoded>>>32);
            double power=(value>>>24)/255.0;power*=power;
            return new Vec3((value>>16&255)/255.0,(value>>8&255)/255.0,(value&255)/255.0).scale(power);
        }
    }
    final Map<AnalyticBlockLighting.Emitter,Channel> channels;
    final Map<Long,List<Channel>> bySection;
    final Long2IntOpenHashMap field,directions;
    final Map<Long,byte[]> bricks,directionBricks;
    final long bytes;
    final int traced,reused,recomposed;
    final boolean limited;
    RadialLightChannels(Map<AnalyticBlockLighting.Emitter,Channel> channels,Long2IntOpenHashMap field,Long2IntOpenHashMap directions,
                        Map<Long,byte[]> bricks,Map<Long,byte[]> directionBricks,int traced,int reused,int recomposed,boolean limited) {
        this.channels=Map.copyOf(channels);this.field=field;this.directions=directions;
        this.bricks=Map.copyOf(bricks);this.directionBricks=Map.copyOf(directionBricks);
        this.traced=traced;this.reused=reused;this.recomposed=recomposed;this.limited=limited;
        bytes=channels.values().stream().mapToLong(Channel::bytes).sum();
        Map<Long,List<Channel>> index=new HashMap<>();
        for(var channel:channels.values()) {
            AABB b=channel.coverage();
            for(int z=((int)b.minZ)>>4;z<=((int)b.maxZ-1)>>4;z++)for(int y=((int)b.minY)>>4;y<=((int)b.maxY-1)>>4;y++)for(int x=((int)b.minX)>>4;x<=((int)b.maxX-1)>>4;x++)
                index.computeIfAbsent(SectionPos.asLong(x,y,z),unused->new ArrayList<>()).add(channel);
        }
        index.replaceAll((key,value)->List.copyOf(value));bySection=Map.copyOf(index);
    }
    List<Channel> at(long position) {
        return bySection.getOrDefault(SectionPos.asLong(BlockPos.getX(position)>>4,BlockPos.getY(position)>>4,BlockPos.getZ(position)>>4),List.of());
    }
}
