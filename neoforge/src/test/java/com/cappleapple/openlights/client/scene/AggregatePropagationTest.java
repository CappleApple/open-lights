package com.cappleapple.openlights.client.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AggregatePropagationTest {
    private static AggregatePropagation.Materials material(int divider) {
        return new AggregatePropagation.Materials() {
            public int optics(long p){return BlockPos.getZ(p)==8?divider:0xffffff;}
            public int blocked(long p){return 0;}
            public Long ready(){return null;}
            public long await(){throw new AssertionError("Synthetic materials are complete");}
        };
    }
    private static List<AggregateLightCache.Source> cube(){
        List<AggregateLightCache.Source> result=new ArrayList<>();
        for(int z=-1;z<=1;z++)for(int y=0;y<=2;y++)for(int x=-1;x<=1;x++)result.add(new AggregateLightCache.Source(BlockPos.asLong(x,y,z),15,0xffcb71));
        return result;
    }
    private static AggregatePropagation.Result calculate(int divider,int limit) throws InterruptedException {
        return new AggregatePropagation(cube(),material(divider),0,0,2,-8,8,limit,3,1).run();
    }
    @Test void immutableInputsProduceExpectedRangeAndPremultipliedBricks() throws Exception {
        var result=calculate(0xffffff,100000);
        assertEquals(0x51ffcb71,result.field().get(BlockPos.asLong(0,1,30)));
        assertEquals(255,result.field().get(BlockPos.asLong(0,1,0))>>>24);
        byte[] brick=result.bricks().get(SectionPos.asLong(0,0,1));
        int offset=((15*18+2)*18+1)*4;
        assertEquals(87,Byte.toUnsignedInt(brick[offset+3]));
        assertEquals(87,Byte.toUnsignedInt(brick[offset]));
        assertFalse(result.limited());
    }
    @Test void opacityAndColorFilteringWorkWithoutLiveWorldAccess() throws Exception {
        assertEquals(0,calculate(15<<24,100000).field().get(BlockPos.asLong(0,1,30)));
        int filtered=calculate(0xff2020,100000).field().get(BlockPos.asLong(0,1,30));
        assertEquals(81,filtered>>>24);
        assertEquals(255,filtered>>16&255);
        assertTrue((filtered>>8&255)<40);assertTrue((filtered&255)<30);
    }
    @Test void memoryLimitAndCancellationBoundWork() throws Exception {
        var limited=calculate(0xffffff,256);assertTrue(limited.limited());assertTrue(limited.field().size()<=256);
        Thread.currentThread().interrupt();
        try{assertThrows(InterruptedException.class,()->calculate(0xffffff,100000));}
        finally{Thread.interrupted();}
    }
}
