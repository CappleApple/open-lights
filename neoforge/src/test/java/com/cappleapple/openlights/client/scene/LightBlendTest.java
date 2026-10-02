package com.cappleapple.openlights.client.scene;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LightBlendTest {
    @Test void singleLightRetainsColorFalloffAndDirection() {
        var blend=new LightBlend();blend.add(.25,0xff8040,1,0,0);
        assertEquals(0x80ff8040,blend.value());assertEquals(0xff8080,blend.direction());
    }
    @Test void equalOverlappingLightsBlendWithoutIncreasingPeak() {
        var blend=new LightBlend();blend.add(.5,0xff0000,1,0,0);blend.add(.5,0x0000ff,-1,0,0);
        assertEquals(0x800080,blend.value()&0xffffff);
        assertEquals(Math.round(Math.sqrt(.5)*255),blend.value()>>>24);
        assertEquals(0x808080,blend.direction(),"Opposing directions must cancel rather than flip");
    }
    @Test void aFadingWeakLightDoesNotChangeTheDominantColorAbruptly() {
        var blend=new LightBlend();blend.add(.5,0xff0000,1,0,0);blend.add(.01,0x0000ff,-1,0,0);
        assertTrue((blend.value()>>16&255)>=249);assertTrue((blend.value()&255)<=6);
        assertTrue(Math.abs(Math.round(Math.sqrt(.5)*255)-(blend.value()>>>24))<=3);
        assertTrue((blend.direction()>>16&255)>=249);
    }
    @Test void manyLightsStayBoundedAndOrderIndependent() {
        var forward=new LightBlend();var reverse=new LightBlend();
        for(int i=1;i<=100;i++)forward.add(i/100.0,0xffffff,0,1,0);
        for(int i=100;i>=1;i--)reverse.add(i/100.0,0xffffff,0,1,0);
        assertEquals(forward.value(),reverse.value());assertEquals(0xffffff,forward.value()&0xffffff);
        assertTrue((forward.value()>>>24)<=255);assertTrue((forward.value()>>>24)>200);
    }
}
