package com.cappleapple.openlights.client.scene;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TriangleBuilderTest {
    @Test
    void isolatedBoxHasTwelveOutwardFacingTriangles() {
        TriangleBuilder builder = new TriangleBuilder();
        builder.box(new AABB(0,0,0,1,1,1), TriangleBuilder.ALL);
        float[] vertices = builder.toArray();
        assertEquals(36*3, vertices.length);
        Vec3 center = new Vec3(.5,.5,.5);
        for (int i=0; i<vertices.length; i+=9) {
            Vec3 a=point(vertices,i), b=point(vertices,i+3), c=point(vertices,i+6);
            Vec3 normal=b.subtract(a).cross(c.subtract(a));
            assertTrue(normal.dot(a.subtract(center)) > 0, "Triangle must face outside the shadow caster");
        }
    }

    @Test
    void coveredFacesAreOmittedAndSectorOffsetsPreserveCoordinates() {
        TriangleBuilder sector = new TriangleBuilder();
        sector.box(new AABB(0,0,0,1,.5,1), TriangleBuilder.UP);
        TriangleBuilder scene = new TriangleBuilder();
        scene.append(sector.toArray(), 8, -16, 24);
        float[] vertices=scene.toArray();
        assertEquals(6*3, vertices.length);
        for (int i=0;i<vertices.length;i+=3) {
            assertTrue(vertices[i]>=8 && vertices[i]<=9);
            assertEquals(-15.5F, vertices[i+1]);
            assertTrue(vertices[i+2]>=24 && vertices[i+2]<=25);
        }
    }

    private static Vec3 point(float[] v,int offset) { return new Vec3(v[offset],v[offset+1],v[offset+2]); }
}
