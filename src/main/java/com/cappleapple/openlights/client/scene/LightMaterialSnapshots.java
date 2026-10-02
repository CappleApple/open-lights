package com.cappleapple.openlights.client.scene;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import java.util.*;
import java.util.concurrent.*;

/** Live-world access is confined to capture(); workers receive only immutable primitive arrays. */
final class LightMaterialSnapshots {
    record Section(int[] optics, byte[] blocked) {
        static final Section SOLID;
        static { int[] cells=new int[4096];Arrays.fill(cells,15<<24);SOLID=new Section(cells,new byte[4096]); }
    }
    static final class Missing extends RuntimeException {
        final long section;
        Missing(long section){super(null,null,false,false);this.section=section;}
    }
    static final class Broker implements AggregatePropagation.Materials {
        private final ConcurrentHashMap<Long,Section> available=new ConcurrentHashMap<>();
        private final Set<Long> requested=ConcurrentHashMap.newKeySet();
        final ConcurrentLinkedQueue<Long> requests=new ConcurrentLinkedQueue<>();
        private final LinkedBlockingQueue<Long> ready=new LinkedBlockingQueue<>();
        private final int limit;
        volatile boolean limited;
        Broker(Map<Long,Section> cache,int limit){available.putAll(cache);this.limit=limit;}
        public void request(long key) {
            if(available.containsKey(key)||!requested.add(key))return;
            if(requested.size()>limit){limited=true;available.put(key,Section.SOLID);return;}
            requests.add(key);
        }
        private Section section(long position) {
            long key=SectionPos.asLong(BlockPos.getX(position)>>4,BlockPos.getY(position)>>4,BlockPos.getZ(position)>>4);
            Section value=available.get(key);
            if(value!=null)return value;
            request(key);
            value=available.get(key);if(value!=null)return value;
            throw new Missing(key);
        }
        private static int index(long p){return (BlockPos.getX(p)&15)|((BlockPos.getY(p)&15)<<4)|((BlockPos.getZ(p)&15)<<8);}
        public int optics(long p){return section(p).optics[index(p)];}
        public int blocked(long p){return section(p).blocked[index(p)]&255;}
        public Long ready(){return ready.poll();}
        public long await() throws InterruptedException{return ready.take();}
        void provide(long key,Section section){available.put(key,section);ready.add(key);}
    }
    static final class Capture {
        final long key;
        private final int[] optics=new int[4096];
        private final byte[] blocked=new byte[4096];
        private final BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos(),neighbor=new BlockPos.MutableBlockPos();
        int cursor;
        Capture(long key){this.key=key;}
        Capture(long key,Section previous){this.key=key;System.arraycopy(previous.optics(),0,optics,0,4096);System.arraycopy(previous.blocked(),0,blocked,0,4096);}
        void cell(ClientLevel world) {
            cell(world,cursor++);
        }
        void cell(ClientLevel world,int i) {
            if(!Minecraft.getInstance().isSameThread())throw new IllegalStateException("Live lighting snapshot accessed off client thread");
            blocked[i]=0;
            pos.set(SectionPos.x(key)*16+(i&15),SectionPos.y(key)*16+(i>>4&15),SectionPos.z(key)*16+(i>>8));
            var state=world.getBlockState(pos);
            if(state.isAir()){optics[i]=0xffffff;return;}
            int opacity=Math.max(0,Math.min(15,state.getLightBlock(world,pos)));
            int tint=0xffffff;
            if(opacity<15) {
                Vec3 transmission=new Vec3(1,1,1);
                var material=OpticalMaterials.block(world,pos,state);
                if(material!=null)transmission=material.tint();
                if(state.getFluidState().is(net.minecraft.tags.FluidTags.WATER))transmission=transmission.multiply(OpticalMaterials.water(world,pos).tint());
                tint=AggregateLightCache.color(transmission);
            }
            optics[i]=(opacity<<24)|tint;
            if(state.canOcclude()&&(opacity<15||state.getLightEmission(world,pos)>0))for(Direction face:Direction.values()) {
                neighbor.setWithOffset(pos,face);
                if(!world.hasChunk(neighbor.getX()>>4,neighbor.getZ()>>4)){blocked[i]|=(byte)(1<<face.ordinal());continue;}
                var adjacent=world.getBlockState(neighbor);
                if(adjacent.canOcclude()&&Shapes.faceShapeOccludes(state.getFaceOcclusionShape(world,pos,face),adjacent.getFaceOcclusionShape(world,neighbor,face.getOpposite())))blocked[i]|=(byte)(1<<face.ordinal());
            }
        }
        Section finish(){if(cursor!=4096)throw new IllegalStateException("Incomplete snapshot");return new Section(optics,blocked);}
    }
    static Section patch(long key,Section previous,ClientLevel world,int x0,int y0,int z0,int x1,int y1,int z1) {
        var capture=new Capture(key,previous);int sx=SectionPos.x(key)*16,sy=SectionPos.y(key)*16,sz=SectionPos.z(key)*16;
        for(int z=Math.max(z0,sz);z<=Math.min(z1,sz+15);z++)for(int y=Math.max(y0,sy);y<=Math.min(y1,sy+15);y++)for(int x=Math.max(x0,sx);x<=Math.min(x1,sx+15);x++)
            capture.cell(world,(x-sx)|((y-sy)<<4)|((z-sz)<<8));
        capture.cursor=4096;var next=capture.finish();
        return Arrays.equals(previous.optics(),next.optics())&&Arrays.equals(previous.blocked(),next.blocked())?previous:next;
    }
    private LightMaterialSnapshots(){}
}
