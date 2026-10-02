package com.cappleapple.openlights.client.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SourceChannelsTest {
    static class Materials implements AggregatePropagation.Materials {
        int reads;
        public int optics(long p){reads++;return 0xffffff;}
        public int blocked(long p){reads++;return 0;}
        public Long ready(){return null;}
        public long await(){throw new AssertionError("Complete materials");}
    }
    private AggregatePropagation.Result run(List<AggregateLightCache.Source> sources,Materials materials,RadialLightChannels previous,Set<Long> dirty,long budget)throws Exception {
        return new AnalyticBlockLighting(sources,materials,0,0,4,-1,2,200000,3,1,false).cache(previous,dirty,budget).run();
    }
    private final List<AggregateLightCache.Source> sources=List.of(
            new AggregateLightCache.Source(BlockPos.asLong(0,0,0),15,0xff2020),
            new AggregateLightCache.Source(BlockPos.asLong(40,0,0),15,0x2020ff));
    @Test void unchangedSourcesReuseAllRaysAndBrickArrays()throws Exception {
        var materials=new Materials();var first=run(sources,materials,null,Set.of(),16<<20);materials.reads=0;
        var next=run(sources,materials,first.channels(),Set.of(),16<<20);
        assertEquals(0,materials.reads);assertEquals(0,next.channels().traced);assertEquals(2,next.channels().reused);
        assertEquals(0,next.channels().recomposed);assertEquals(first.field(),next.field());
        first.bricks().forEach((key,data)->assertSame(data,next.bricks().get(key)));
    }
    @Test void sourceRemovalRetracesNothingAndPreservesDistantSource()throws Exception {
        var materials=new Materials();var first=run(sources,materials,null,Set.of(),16<<20);materials.reads=0;
        var next=run(List.of(sources.get(1)),materials,first.channels(),Set.of(),16<<20);
        assertEquals(0,materials.reads);assertEquals(0,next.channels().traced);assertEquals(1,next.channels().reused);
        assertEquals(0,next.field().get(BlockPos.ZERO.asLong()));assertEquals(first.field().get(sources.get(1).position()),next.field().get(sources.get(1).position()));
        var fresh=run(List.of(sources.get(1)),new Materials(),null,Set.of(),16<<20);assertEquals(fresh.field(),next.field());
    }
    @Test void occlusionInvalidationRetracesOnlyIntersectingChannel()throws Exception {
        var materials=new Materials();var first=run(sources,materials,null,Set.of(),16<<20);
        var next=run(sources,materials,first.channels(),Set.of(SectionPos.asLong(-1,0,0)),16<<20);
        assertEquals(1,next.channels().traced);assertEquals(1,next.channels().reused);assertEquals(first.field(),next.field());
    }
    @Test void memoryOverflowFallsBackWithoutDroppingLight()throws Exception {
        var fallback=run(sources,new Materials(),null,Set.of(),1);
        var normal=run(sources,new Materials(),null,Set.of(),0);
        assertNull(fallback.channels());assertEquals(normal.field(),fallback.field());assertFalse(fallback.limited());
    }
    @Test void hundredWeakLightsNeverDimStrongIrradiance(){
        var blend=new LightBlend();blend.add(.8,0xffffff,0,1,0);int peak=blend.value()>>>24;
        for(int i=0;i<500;i++)blend.add(.0001,0xffffff,0,-1,0);
        assertEquals(peak,blend.value()>>>24);
    }
    @Test void changingClusterReusesSeparateClusterChannel()throws Exception {
        var members=new ArrayList<AggregateLightCache.Source>();
        for(int center:new int[]{0,40})for(int x=center-1;x<=center+1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)members.add(new AggregateLightCache.Source(BlockPos.asLong(x,y,z),15,0xffffff));
        var first=new AnalyticBlockLighting(members,new Materials(),0,0,4,-2,3,200000,3,1,true).cache(null,Set.of(),16<<20).run();
        members.removeIf(source->source.position()==BlockPos.ZERO.asLong());
        var next=new AnalyticBlockLighting(members,new Materials(),0,0,4,-2,3,200000,3,1,true).cache(first.channels(),Set.of(),16<<20).run();
        assertEquals(1,next.channels().reused);assertEquals(1,next.channels().traced);
        assertEquals(2,next.channels().channels.size());
    }
    @Test void removingGiColumnBypassesAgeAndRetainsUnclampedSum(){
        var grid=new ProbeGrid(3,1);grid.move(0,0,0);var channels=new GiSourceChannels(grid,1<<20);
        var total=channels.replace(0,Map.of("a",new Vec3(20,2,0),"b",new Vec3(7,0,3)));
        grid.set(0,(float)total.x,(float)total.y,(float)total.z,1,100);
        assertEquals(16,grid.data[0]);assertTrue(channels.retain(Set.of("b")));
        assertArrayEquals(new float[]{7,0,3,1},Arrays.copyOf(grid.data,4));
        assertEquals(1,channels.count());assertTrue(channels.clear());assertEquals(0,grid.data[0]);
    }
    @Test void giMovementKeepsWorldCoordinatesAndDropsNewBorder(){
        var grid=new ProbeGrid(3,1);grid.move(0,0,0);var channels=new GiSourceChannels(grid,1<<20);
        for(int i=0;i<grid.count();i++){channels.replace(i,Map.of("a",new Vec3(i+1,0,0),"b",new Vec3(0,2,0)));grid.set(i,i+1,2,0,1,0);}
        grid.move(1,0,0);channels.move();channels.retain(Set.of("a"));
        assertEquals(2,grid.data[0]);assertEquals(0,grid.data[1]);assertEquals(0,grid.data[2*4+3]);
        assertEquals(0,grid.data[2*4]);
    }
    @Test void giOverflowRetainsEnergyWithBoundedColumns(){
        var grid=new ProbeGrid(3,1);var channels=new GiSourceChannels(grid,grid.count()*12*3);
        var total=channels.replace(0,Map.of("a",new Vec3(1,0,0),"b",new Vec3(0,2,0),"c",new Vec3(0,0,3)));
        assertEquals(new Vec3(1,2,3),total);assertTrue(channels.bytes()<=grid.count()*12*3);
        grid.set(0,1,2,3,1,0);channels.clear();assertEquals(0,grid.data[0]);assertEquals(0,grid.data[1]);assertEquals(0,grid.data[2]);
    }
    @Test void patchCaptureClonesPublishedArrays(){
        var optics=new int[4096];var blocked=new byte[4096];optics[0]=123;blocked[0]=5;
        var previous=new LightMaterialSnapshots.Section(optics,blocked);var capture=new LightMaterialSnapshots.Capture(0,previous);capture.cursor=4096;
        var next=capture.finish();assertNotSame(optics,next.optics());assertNotSame(blocked,next.blocked());
        next.optics()[0]=0;next.blocked()[0]=0;assertEquals(123,optics[0]);assertEquals(5,blocked[0]);
    }
    @Test void changedOcclusionCannotReuseStaleLightThroughWall()throws Exception {
        var first=run(sources,new Materials(),null,Set.of(),16<<20);
        var wall=new Materials(){public int optics(long p){return BlockPos.getZ(p)==8&&BlockPos.getX(p)<20?15<<24:0xffffff;}};
        var next=run(sources,wall,first.channels(),Set.of(SectionPos.asLong(0,0,0)),16<<20);
        assertTrue(first.field().get(BlockPos.asLong(0,0,12))>>>24>0);
        assertEquals(0,next.field().get(BlockPos.asLong(0,0,12)));
        assertEquals(1,next.channels().traced);assertEquals(1,next.channels().reused);
    }
    @Test void malformedGiSourceCannotPoisonTextureOnRetirement(){
        var grid=new ProbeGrid(3,1);var channels=new GiSourceChannels(grid,1<<20);
        var total=channels.replace(0,Map.of("bad",new Vec3(Double.NaN,Double.POSITIVE_INFINITY,-1),"good",new Vec3(1,2,3)));
        grid.set(0,(float)total.x,(float)total.y,(float)total.z,1,0);channels.retain(Set.of("good"));
        assertArrayEquals(new float[]{1,2,3,1},Arrays.copyOf(grid.data,4));
    }
}
