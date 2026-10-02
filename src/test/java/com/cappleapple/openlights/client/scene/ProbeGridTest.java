package com.cappleapple.openlights.client.scene;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class ProbeGridTest {
    @Test void skyBootstrapKeepsDetailedWorkPendingAndPreservesExistingLighting() {
        var grid = new ProbeGrid(9, 4); grid.move(0, 0, 0);
        grid.set(0, .5f, .25f, 1, 1, 0);
        float[] sky = new float[125], exposed = new float[125];
        for (int i = 0; i < 125; i++) { sky[i] = (i % 5) / 4f; exposed[i] = 1; }
        grid.seedMissingSky(sky, exposed);
        assertEquals(1, grid.populated()); assertFalse(grid.ready());
        assertArrayEquals(new float[]{.5f,.25f,1,1}, java.util.Arrays.copyOf(grid.data, 4));
        assertEquals(.5f, grid.data[4*4+1]); assertEquals(1, grid.data[8*4+1]);
        assertEquals(0, grid.data[4*4]); // Never seed vanilla's uncolored block light.
        int updates = 0;
        for (int i; (i = grid.next(1, 10)) >= 0;) { grid.set(i, .2f, .3f, 1, 1, 1); updates++; }
        assertEquals(grid.count()-1, updates); assertTrue(grid.ready());
        grid.move(4, 0, 0); grid.seedMissingSky(sky, exposed);
        assertEquals(648, grid.populated());
        assertEquals(.2f, grid.data[0]); assertEquals(1, grid.data[8*4+3]);
    }

    @Test void refreshVisitsEveryProbeOnceAndRespectsAge() {
        var grid = new ProbeGrid(9, 4);
        grid.move(0, 0, 0);
        var visited = new HashSet<Integer>();
        for (int n = 0; n < grid.count(); n++) {
            int i = grid.next(10, 20);
            assertTrue(visited.add(i));
            grid.set(i, 1, 2, 3, 1, 10);
        }
        assertTrue(grid.ready());
        assertEquals(-1, grid.next(29, 20));
        assertEquals(-1, grid.next(30_000, 20));
        grid.invalidate();
        assertTrue(grid.ready());
        assertEquals(-1, grid.next(29, 20));
        assertTrue(grid.next(30, 20) >= 0);
    }

    @Test void regionInvalidationRetainsReadinessAndOnlyRefreshesAffectedCells() {
        var grid = new ProbeGrid(3, 4);
        grid.move(-1, 0, 0);
        for (int i = 0; i < grid.count(); i++) grid.set(i, 1, 0, 0, 1, 0);
        grid.invalidateRegion(-5, -1, -1, -3, 1, 1);
        assertTrue(grid.ready());
        int index = grid.next(20, 10);
        assertEquals(-3.5, grid.x(index));
        assertEquals(.5, grid.y(index));
        assertEquals(.5, grid.z(index));
        grid.set(index, 0, 0, 0, 1, 20);
        assertEquals(-1, grid.next(20, 10));
        assertTrue(grid.next(20, 10, true) >= 0);
    }

    @Test void recenterPreservesDirtyOverlapAndSchedulesOnlyNewBorder() {
        var grid = new ProbeGrid(3, 1);
        grid.move(0, 0, 0);
        for (int i = 0; i < grid.count(); i++) grid.set(i, 1, 0, 0, 1, 0);
        grid.invalidateRegion(0, 0, 0, 0, 0, 0);
        grid.move(1, 0, 0);
        int updates = 0;
        for (int i; (i = grid.next(20, 10)) >= 0;) {
            grid.set(i, 1, 0, 0, 1, 20);
            updates++;
        }
        assertEquals(10, updates);
        assertTrue(grid.ready());
    }

    @Test void recenterPreservesWorldCoordinatesAndDropsNewBorder() {
        var grid = new ProbeGrid(3, 4);
        grid.move(-1, 0, 0);
        assertEquals(-8, grid.minimumX());
        for (int i = 0; i < grid.count(); i++) grid.set(i, i + 1, 0, 0, 1, 2);
        float oldValue = grid.data[1 * 4];
        double worldX = grid.x(1);
        assertTrue(grid.move(0, 0, 0));
        assertEquals(worldX, grid.x(0));
        assertEquals(oldValue, grid.data[0]);
        assertEquals(18, grid.populated());
        assertFalse(grid.ready());
        assertEquals(0, grid.data[2 * 4 + 3]);
        assertFalse(grid.move(1, 0, 0));
    }

    @Test void teleportDropsHistoryAndInvalidationDoesNotStarveSweep() {
        var grid = new ProbeGrid(3, 1);
        grid.move(0, 0, 0);
        int first = grid.next(0, 20);
        grid.set(first, 1, 0, 0, 1, 0);
        grid.invalidate();
        assertNotEquals(first, grid.next(1, 20));
        grid.move(200, 0, 0);
        assertEquals(0, grid.populated());
        for (float value : grid.data) assertEquals(0, value);
    }

    @Test void malformedSamplesCannotPoisonGpuTexture() {
        var grid = new ProbeGrid(3, 1);
        grid.set(0, Float.NaN, Float.POSITIVE_INFINITY, -3, 50, 1);
        assertArrayEquals(new float[]{0, 0, 0, 16}, java.util.Arrays.copyOf(grid.data, 4));
    }

    @Test void evenGridCoversBothSidesAtEndOfCameraCell() {
        var grid = new ProbeGrid(10, 136);
        grid.move(135, 0, 0);
        assertTrue(135 - grid.x(0) >= 512);
        assertTrue(grid.x(9) - 135 >= 512);
        grid.move(29_999_999, -60, -29_999_999);
        assertTrue(Math.abs(grid.x(0) - 29_999_999) < 1500);
    }

    @Test void identicalSamplesRefreshAgeWithoutRequestingAnotherUpload() {
        var grid = new ProbeGrid(3, 1);
        assertTrue(grid.set(0, 1, 2, 3, 1, 10));
        assertFalse(grid.set(0, 1, 2, 3, 1, 20));
        assertTrue(grid.set(0, 1, 2, 4, 1, 21));
    }
}
