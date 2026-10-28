package com.cappleapple.openlights.flashlight;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FlashlightBatteryTest {
    @Test void chargingHonorsSpaceAndTransferLimitWithoutOverflow() {
        assertEquals(1000, FlashlightBattery.accepted(0, 100000, Integer.MAX_VALUE, 1000));
        assertEquals(3, FlashlightBattery.accepted(99997, 100000, 500, 1000));
        assertEquals(0, FlashlightBattery.accepted(100000, 100000, 1000, 1000));
        assertEquals(Integer.MAX_VALUE, FlashlightBattery.accepted(0, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
    @Test void invalidChargeRequestsCannotCreateOrRemoveEnergy() {
        assertEquals(0, FlashlightBattery.accepted(20, 100, -1, 1000));
        assertEquals(0, FlashlightBattery.accepted(20, 100, 10, -1));
        assertEquals(0, FlashlightBattery.accepted(500, 100, 10, 10));
        assertEquals(10, FlashlightBattery.accepted(-500, 100, 10, 10));
    }
    @Test void heldDrainStopsAtZeroAndCapacityChangesClampSavedState() {
        assertEquals(24, FlashlightBattery.remaining(25, 100, 1));
        assertEquals(0, FlashlightBattery.remaining(1, 100, 5));
        assertEquals(99, FlashlightBattery.remaining(500, 100, 1));
        assertEquals(25, FlashlightBattery.remaining(25, 100, 0));
        assertEquals(25, FlashlightBattery.remaining(25, 100, -1));
        assertEquals(0, FlashlightBattery.clamp(Integer.MIN_VALUE, 100));
    }
    @Test void aBeamRequiresEnoughChargeForOneWholeTick() {
        assertFalse(FlashlightBattery.canRun(1, 1000, 100));
        assertFalse(FlashlightBattery.canRun(99, 1000, 100));
        assertTrue(FlashlightBattery.canRun(100, 1000, 100));
        assertTrue(FlashlightBattery.canRun(0, 1000, 0));
        assertFalse(FlashlightBattery.canRun(1000, 50, 100));
        int afterTick = FlashlightBattery.remaining(150, 1000, 100);
        assertEquals(50, afterTick);
        assertFalse(FlashlightBattery.canRun(afterTick, 1000, 100));
    }

}
