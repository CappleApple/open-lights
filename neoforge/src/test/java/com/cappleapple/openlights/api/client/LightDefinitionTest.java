package com.cappleapple.openlights.api.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LightDefinitionTest {
    private static final Vec3 WHITE = new Vec3(1, 1, 1);

    @Test
    void normalizesDirectionAndBuildsAnOrthogonalBasis() {
        var spot = new LightDefinition.Spot(Vec3.ZERO, new Vec3(0, 0, -4), new Vec3(0, 2, 1),
                WHITE, 1, 20, 50, 30, true, 0.5f);
        assertEquals(1, spot.forward().length(), 1.0e-10);
        assertEquals(1, spot.up().length(), 1.0e-10);
        assertEquals(0, spot.up().dot(spot.forward()), 1.0e-10);
        assertEquals(new Vec3(0, 0, -1), spot.forward());
    }

    @Test
    void normalizesSmallValidVectorsWithoutVanillaZeroCutoff() {
        var spot = new LightDefinition.Spot(Vec3.ZERO, new Vec3(0, 0, -0.00001), new Vec3(0, 0.00001, 0),
                WHITE, 1, 20, 50, 30, true, 0);
        assertEquals(1, spot.forward().length(), 1.0e-10);
        assertEquals(1, spot.up().length(), 1.0e-10);
    }
    @Test
    void acceptsVerticalAreaLightsWithParallelPreferredUp() {
        var area = new LightDefinition.Area(Vec3.ZERO, new Vec3(0, 1, 0), new Vec3(0, 1, 0),
                WHITE, 1, 20, 2, 1, 90, false, 0);
        assertEquals(1, area.up().length(), 1.0e-10);
        assertEquals(0, area.up().dot(area.forward()), 1.0e-10);
    }

    @Test
    void rejectsUndefinedDirectionsAndInvalidConeOrdering() {
        assertThrows(IllegalArgumentException.class, () -> new LightDefinition.Spot(Vec3.ZERO,
                Vec3.ZERO, new Vec3(0, 1, 0), WHITE, 1, 20, 50, 30, true, 1));
        assertThrows(IllegalArgumentException.class, () -> new LightDefinition.Spot(Vec3.ZERO,
                new Vec3(0, 0, -1), new Vec3(0, 1, 0), WHITE, 1, 20, 30, 50, true, 1));
        assertThrows(IllegalArgumentException.class, () -> new LightDefinition.Spot(Vec3.ZERO,
                new Vec3(0, 0, -1), new Vec3(0, 1, 0), WHITE, 1, 20, 180, 30, true, 1));
    }

    @Test
    void rejectsNonFiniteGeometryAndScalarsBeforeTheyReachGpuUniforms() {
        assertThrows(IllegalArgumentException.class, () -> new LightDefinition.Point(
                new Vec3(Double.NaN, 0, 0), WHITE, 1, 20, false, 0));
        assertThrows(IllegalArgumentException.class, () -> new LightDefinition.Point(
                Vec3.ZERO, WHITE, Float.POSITIVE_INFINITY, 20, false, 0));
        assertThrows(IllegalArgumentException.class, () -> new LightDefinition.Point(
                Vec3.ZERO, WHITE, 1, 0, false, 0));
        assertThrows(IllegalArgumentException.class, () -> new LightDefinition.Point(
                Vec3.ZERO, new Vec3(-1, 0, 0), 1, 20, false, 0));
        assertThrows(IllegalArgumentException.class, () -> new LightDefinition.Area(Vec3.ZERO,
                new Vec3(0, 0, -1), new Vec3(0, 1, 0), WHITE, 1, 20, -1, 1, 90, false, 0));
    }

    @Test
    void preservesWorldCoordinatePrecision() {
        Vec3 position = new Vec3(29_999_999.125, 64.25, -29_999_998.875);
        var point = new LightDefinition.Point(position, WHITE, 0, 20, false, 0);
        assertEquals(position, point.position());
    }
}
