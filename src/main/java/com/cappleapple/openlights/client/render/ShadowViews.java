package com.cappleapple.openlights.client.render;

import com.cappleapple.openlights.api.client.LightDefinition;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

final class ShadowViews {
    private static final Vec3[] DIRECTIONS = {new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,1,0),
            new Vec3(0,-1,0),new Vec3(0,0,1),new Vec3(0,0,-1)};
    private static final Vec3[] UPS = {new Vec3(0,-1,0),new Vec3(0,-1,0),new Vec3(0,0,1),
            new Vec3(0,0,-1),new Vec3(0,-1,0),new Vec3(0,-1,0)};

    static List<Matrix4f> create(LightDefinition light, Vec3 sceneOrigin) {
        Vec3 source = light.position().subtract(sceneOrigin);
        List<Matrix4f> views = new ArrayList<>();
        if (light instanceof LightDefinition.Point) {
            for (int i=0;i<6;i++) views.add(view(source, DIRECTIONS[i], UPS[i], 90, light.range()));
        } else if (light instanceof LightDefinition.Spot spot) {
            views.add(view(source, spot.forward(), spot.up(), spot.outerConeAngleDegrees(), light.range()));
        } else if (light instanceof LightDefinition.Area area) {
            Vec3 right = area.forward().cross(area.up()).normalize();
            for (int y : new int[]{-1,1}) for (int x : new int[]{-1,1}) {
                Vec3 sample = source.add(right.scale(x * area.width() * .25)).add(area.up().scale(y * area.height() * .25));
                views.add(view(sample, area.forward(), area.up(), area.spreadAngleDegrees(), light.range()));
            }
        }
        return views;
    }

    private static Matrix4f view(Vec3 eye, Vec3 direction, Vec3 up, float degrees, float range) {
        return new Matrix4f().perspective((float)Math.toRadians(Math.max(1,Math.min(175,degrees))),
                1, Math.min(.04f,range*.01f), range).lookAt(vector(eye),vector(eye.add(direction)),vector(up));
    }
    private static Vector3f vector(Vec3 v) { return new Vector3f((float)v.x,(float)v.y,(float)v.z); }
    private ShadowViews() {}
}
