package com.cappleapple.openlights.client.scene;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MediumMergerTest {
    private static Medium clear(AABB bounds) { return new Medium(bounds, new Vec3(.96,.985,1), .97F, 2.5F); }

    @Test
    void aSolidGridCoalescesIntoItsExactVolume() {
        List<Medium> media = new ArrayList<>();
        for (int x=0; x<4; x++) for (int z=0; z<3; z++) media.add(clear(new AABB(x,0,z,x+1,1,z+1)));
        List<Medium> result = MediumMerger.merge(media);
        assertEquals(1, result.size());
        assertEquals(new AABB(0,0,0,4,1,3), result.get(0).bounds());
    }

    @Test
    void lShapedPaneDoesNotFillItsEmptyCorner() {
        List<Medium> result = MediumMerger.merge(List.of(
                clear(new AABB(0,0,.4375,1,1,.5625)),
                clear(new AABB(.4375,0,.5625,.5625,1,1))));
        assertEquals(2, result.size());
        assertFalse(result.stream().anyMatch(m -> m.bounds().contains(.9,.5,.9)));
        assertEquals(.125 + .125 * .4375, volume(result), 1e-12);
    }

    @Test
    void adjoiningPanesMergeWithoutInflatingTheirThickness() {
        List<Medium> result = MediumMerger.merge(List.of(
                clear(new AABB(0,0,.4375,1,1,.5625)),
                clear(new AABB(1,0,.4375,2,1,.5625))));
        assertEquals(1, result.size());
        assertEquals(new AABB(0,0,.4375,2,1,.5625), result.get(0).bounds());
    }

    @Test
    void differentFluidHeightsAndSeparatedIntervalsStaySeparate() {
        List<Medium> result = MediumMerger.merge(List.of(
                clear(new AABB(0,0,0,1,.875,1)),
                clear(new AABB(1,0,0,2,.75,1)),
                clear(new AABB(3,0,0,4,.75,1))));
        assertEquals(3, result.size());
        assertEquals(2.375, volume(result), 1e-12);
    }

    @Test
    void neighboringColorsAndOpticalCoefficientsCannotMerge() {
        List<Medium> result = MediumMerger.merge(List.of(
                new Medium(new AABB(0,0,0,1,1,1), new Vec3(1,.04,.02), .98F, 2.5F),
                new Medium(new AABB(1,0,0,2,1,1), new Vec3(.02,.28,1), .98F, 2.5F),
                new Medium(new AABB(2,0,0,3,1,1), new Vec3(.02,.28,1), .90F, 2.5F),
                new Medium(new AABB(3,0,0,4,1,1), new Vec3(.02,.28,1), .90F, 2.3F)));
        assertEquals(4, result.size());
    }

    private static double volume(List<Medium> media) {
        return media.stream().mapToDouble(m -> {
            AABB b=m.bounds(); return (b.maxX-b.minX)*(b.maxY-b.minY)*(b.maxZ-b.minZ);
        }).sum();
    }
}
