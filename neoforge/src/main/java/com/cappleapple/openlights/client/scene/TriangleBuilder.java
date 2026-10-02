package com.cappleapple.openlights.client.scene;

import net.minecraft.world.phys.AABB;
import java.util.Arrays;

/** Triangle winding is outward; individual cube faces may be omitted when a full opaque neighbor covers them. */
final class TriangleBuilder {
    static final int DOWN = 1, UP = 2, NORTH = 4, SOUTH = 8, WEST = 16, EAST = 32, ALL = 63;
    private float[] values = new float[1024];
    private int size;

    void box(AABB b, int faces) {
        float x0=(float)b.minX, y0=(float)b.minY, z0=(float)b.minZ;
        float x1=(float)b.maxX, y1=(float)b.maxY, z1=(float)b.maxZ;
        if ((faces & DOWN) != 0) quad(x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1);
        if ((faces & UP) != 0) quad(x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0);
        if ((faces & NORTH) != 0) quad(x1,y0,z0, x0,y0,z0, x0,y1,z0, x1,y1,z0);
        if ((faces & SOUTH) != 0) quad(x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1);
        if ((faces & WEST) != 0) quad(x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0);
        if ((faces & EAST) != 0) quad(x1,y0,z1, x1,y0,z0, x1,y1,z0, x1,y1,z1);
    }

    void append(float[] source, float dx, float dy, float dz) {
        capacity(source.length);
        for (int i = 0; i < source.length; i += 3) {
            values[size++] = source[i] + dx;
            values[size++] = source[i+1] + dy;
            values[size++] = source[i+2] + dz;
        }
    }

    private void quad(float ax,float ay,float az, float bx,float by,float bz,
                      float cx,float cy,float cz, float dx,float dy,float dz) {
        capacity(18);
        vertex(ax,ay,az); vertex(bx,by,bz); vertex(cx,cy,cz);
        vertex(ax,ay,az); vertex(cx,cy,cz); vertex(dx,dy,dz);
    }

    private void vertex(float x,float y,float z) { values[size++]=x; values[size++]=y; values[size++]=z; }
    private void capacity(int count) {
        if (size + count > values.length) values = Arrays.copyOf(values, Math.max(size + count, values.length * 2));
    }
    float[] toArray() { return Arrays.copyOf(values, size); }
}
