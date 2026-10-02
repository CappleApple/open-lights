package com.cappleapple.openlights.client.scene;

import com.cappleapple.openlights.api.client.LightDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Source influence bounds, independent of camera distance and shadow-map resolution. */
public final class LightCoverage {
    public static double radius(LightDefinition light) {
        return light.range() + (light instanceof LightDefinition.Area area ? .25 * Math.hypot(area.width(), area.height()) : 0);
    }
    public static AABB bounds(LightDefinition light) {
        Vec3 p = light.position(); double r = radius(light);
        return new AABB(p.x-r,p.y-r,p.z-r,p.x+r,p.y+r,p.z+r);
    }
    /** Conservative bounds of emitted rays, including profile layers and all four panel emitters. */
    public static AABB renderingBounds(LightDefinition light) {
        AABB result;
        if (light instanceof LightDefinition.Spot spot) {
            if (spot.beamProfile() == null) result = cone(spot.position(),spot.forward(),spot.range(),
                    Math.min(.9999,Math.cos(Math.toRadians(spot.outerConeAngleDegrees()*.5))));
            else {
                var inner=spot.beamProfile().inner();var outer=spot.beamProfile().outer();
                result=cone(spot.position(),spot.forward(),Math.min(spot.range(),inner.range()),Math.cos(Math.toRadians(inner.angleDegrees()*.5)))
                        .minmax(cone(spot.position(),spot.forward(),Math.min(spot.range(),outer.range()),Math.cos(Math.toRadians(outer.angleDegrees()*.5))));
            }
        } else if (light instanceof LightDefinition.Area area) {
            result=cone(area.position(),area.forward(),area.range(),Math.min(.9999,Math.cos(Math.toRadians(area.spreadAngleDegrees()*.5))));
            Vec3 right=area.forward().cross(area.up()).normalize();
            // The renderer samples quarter-width/height offsets, not the panel's outer corners.
            double x=.25*(Math.abs(right.x)*area.width()+Math.abs(area.up().x)*area.height());
            double y=.25*(Math.abs(right.y)*area.width()+Math.abs(area.up().y)*area.height());
            double z=.25*(Math.abs(right.z)*area.width()+Math.abs(area.up().z)*area.height());
            result=result.inflate(x,y,z);
        } else result=bounds(light);
        // Uniforms and shader transforms use floats; pad exact cone boundaries conservatively.
        return result.inflate(.001);
    }
    private static AABB cone(Vec3 position,Vec3 forward,double range,double cosine) {
        double sine=Math.sqrt(Math.max(0,1-cosine*cosine));
        return new AABB(position.x+range*Math.min(0,-extent(-forward.x,cosine,sine)),
                position.y+range*Math.min(0,-extent(-forward.y,cosine,sine)),
                position.z+range*Math.min(0,-extent(-forward.z,cosine,sine)),
                position.x+range*Math.max(0,extent(forward.x,cosine,sine)),
                position.y+range*Math.max(0,extent(forward.y,cosine,sine)),
                position.z+range*Math.max(0,extent(forward.z,cosine,sine)));
    }
    private static double extent(double axis,double cosine,double sine) {
        return axis>=cosine?1:axis*cosine+Math.sqrt(Math.max(0,1-axis*axis))*sine;
    }
    public static AABB view(Vec3 camera, double distance) {
        return new AABB(camera.x-distance,camera.y-distance,camera.z-distance,
                camera.x+distance,camera.y+distance,camera.z+distance);
    }
    public static int distanceBlocks(int renderChunks, int limitChunks) {
        return Math.max(1, limitChunks > 0 ? Math.min(renderChunks, limitChunks) : renderChunks) * 16;
    }
    private LightCoverage() {}
}
