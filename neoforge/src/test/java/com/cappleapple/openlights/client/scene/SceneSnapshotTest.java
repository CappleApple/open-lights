package com.cappleapple.openlights.client.scene;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SceneSnapshotTest {
    @Test
    void snapshotCannotBeChangedThroughItsConstructorInputsOrReturnedCollections() {
        float[] triangles={1,2,3};
        List<Medium> media=new ArrayList<>();
        media.add(new Medium(new AABB(0,0,0,1,1,1), new Vec3(1,1,1),1,1));
        SceneSnapshot snapshot=new SceneSnapshot(9,new Vec3(16,32,-16),triangles,media);
        triangles[0]=99;
        media.clear();
        float[] output=snapshot.opaqueTriangles();
        output[1]=99;
        assertArrayEquals(new float[]{1,2,3},snapshot.opaqueTriangles());
        assertEquals(1,snapshot.media().size());
        assertThrows(UnsupportedOperationException.class,()->snapshot.media().clear());
        assertEquals(9,snapshot.revision());
        assertEquals(new Vec3(16,32,-16),snapshot.origin());
    }
}
