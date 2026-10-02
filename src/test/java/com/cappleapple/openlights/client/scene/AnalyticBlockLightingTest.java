package com.cappleapple.openlights.client.scene;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalyticBlockLightingTest {
    @Test void overlappingColorsAndDirectionsTransitionSmoothlyThroughEqualStrength() throws Exception {
        var sources=List.of(new AggregateLightCache.Source(BlockPos.asLong(-5,0,0),15,0xff0000),new AggregateLightCache.Source(BlockPos.asLong(5,0,0),15,0x0000ff));
        var materials=new AggregatePropagation.Materials(){
            public int optics(long p){return 0xffffff;}public int blocked(long p){return 0;}
            public Long ready(){return null;}public long await(){throw new AssertionError();}
        };
        var result=new AnalyticBlockLighting(sources,materials,0,0,2,-1,2,200000,3,1,false).run();
        int midpoint=result.field().get(BlockPos.ZERO.asLong());
        assertTrue(Math.abs(128-(midpoint>>16&255))<=1);assertTrue(Math.abs(128-(midpoint&255))<=1);
        int previous=result.field().get(BlockPos.asLong(-2,0,0));
        for(int x=-1;x<=2;x++) {
            int next=result.field().get(BlockPos.asLong(x,0,0));
            assertTrue((next>>16&255)<=(previous>>16&255));
            assertTrue((next&255)>=(previous&255));
            assertTrue(Math.abs((next>>16&255)-(previous>>16&255))<80,"No winner-switch color step");
            assertTrue(Math.abs((next>>>24)-(previous>>>24))<35,"Intensity must remain continuous");
            previous=next;
        }
        byte[] direction=result.directions().get(net.minecraft.core.SectionPos.asLong(0,0,0));
        double decoded=(direction[(18*18+18+1)*4]&255)*255.0/(midpoint>>>24);
        assertTrue(Math.abs(decoded-128)<=255.0/(midpoint>>>24),"Premultiplied opposing directions must decode to the neutral moment");
    }
    private static AggregatePropagation.Result calculate(boolean cluster,int wall) throws Exception {
        List<AggregateLightCache.Source> sources=new ArrayList<>();
        int radius=cluster?1:0;
        for(int z=-radius;z<=radius;z++)for(int y=-radius;y<=radius;y++)for(int x=-radius;x<=radius;x++)sources.add(new AggregateLightCache.Source(BlockPos.asLong(x,y,z),15,0xffffff));
        var materials=new AggregatePropagation.Materials(){
            public int optics(long p){return BlockPos.getZ(p)==8?wall:0xffffff;}
            public int blocked(long p){return 0;}
            public Long ready(){return null;}
            public long await(){throw new AssertionError("Complete test snapshot");}
        };
        return new AnalyticBlockLighting(sources,materials,0,0,3,-2,3,200000,3,1,true).run();
    }
    @Test void radialFalloffMatchesPointFormulaAndClusterKeepsPeak() throws Exception {
        assertEquals(Math.pow(1-7.0/15,2)/(1+49*.06),AnalyticBlockLighting.attenuation(7,15,1),1e-12);
        var result=calculate(true,0xffffff);
        int axis=result.field().get(BlockPos.asLong(20,0,0))>>>24,diagonal=result.field().get(BlockPos.asLong(14,0,14))>>>24;
        assertTrue(Math.abs(axis-diagonal)<3,"Equal radial distances should have equal falloff");
        assertEquals(255,result.field().get(BlockPos.asLong(0,0,0))>>>24);
        assertTrue(result.analytic());assertFalse(result.directions().isEmpty());
        assertEquals(255,result.directions().get(net.minecraft.core.SectionPos.asLong(0,0,0))[(18*18+18+1)*4+3]&255,"Emitter mask must preserve glowing textures");
        assertEquals(0,calculate(false,0xffffff).field().get(BlockPos.asLong(20,0,0)));
    }
    @Test void directRaysStopAtOpaqueWallsAndPickUpGlassColor() throws Exception {
        assertEquals(0,calculate(true,15<<24).field().get(BlockPos.asLong(0,0,20)));
        int value=calculate(true,0xff2020).field().get(BlockPos.asLong(0,0,20));
        assertTrue(value>>>24>0);assertEquals(255,value>>16&255);assertEquals(32,value>>8&255);
    }
    @Test void unrelatedEmitterDoesNotMakeAnOpaqueBlockTransparent() throws Exception {
        var sources=List.of(new AggregateLightCache.Source(BlockPos.asLong(0,0,0),15,0xffffff),new AggregateLightCache.Source(BlockPos.asLong(0,0,8),1,0xff0000));
        var materials=new AggregatePropagation.Materials(){
            public int optics(long p){return p==BlockPos.asLong(0,0,8)?15<<24:0xffffff;}
            public int blocked(long p){return 0;}public Long ready(){return null;}public long await(){throw new AssertionError();}
        };
        var result=new AnalyticBlockLighting(sources,materials,0,0,2,-2,3,200000,3,1,true).run();
        assertEquals(0,result.field().get(BlockPos.asLong(0,0,10)));
    }
}
