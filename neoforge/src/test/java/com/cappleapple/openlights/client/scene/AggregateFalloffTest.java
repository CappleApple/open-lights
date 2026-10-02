package com.cappleapple.openlights.client.scene;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AggregateFalloffTest {
    @Test void isolatedSourcesAndDisabledStrengthKeepNativeFalloff() {
        assertEquals(17,AggregateFalloff.step(1,3,1));
        assertEquals(17,AggregateFalloff.step(27,3,0));
        assertEquals(17,AggregateFalloff.step(27,1,1));
    }
    @Test void clusterReachesBeyondOrdinaryLight() {
        int single=AggregateFalloff.step(1,3,1), cube=AggregateFalloff.step(27,3,1);
        assertEquals(6,cube);
        assertTrue(255-cube*30>0);
        assertTrue(255-single*30<0);
    }
    @Test void returnsDiminishAndHugeClustersRespectCap() {
        assertTrue(AggregateFalloff.step(8,3,1)>AggregateFalloff.step(27,3,1));
        assertEquals(AggregateFalloff.step(27,3,1),AggregateFalloff.step(1e9,3,1));
    }
}
