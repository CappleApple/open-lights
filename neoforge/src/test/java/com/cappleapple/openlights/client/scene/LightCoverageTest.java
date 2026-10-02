package com.cappleapple.openlights.client.scene;

import com.cappleapple.openlights.api.client.LightDefinition;
import com.cappleapple.openlights.beam.BeamProfile;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LightCoverageTest {
    @Test void cameraDistanceDoesNotChangeSourceReach() {
        var light = new LightDefinition.Point(new Vec3(0,4,80), new Vec3(1,1,1), 1, 12, true, 0);
        assertTrue(LightCoverage.bounds(light).intersects(LightCoverage.view(Vec3.ZERO,96)));
        assertEquals(68, LightCoverage.bounds(light).minZ);
        assertEquals(92, LightCoverage.bounds(light).maxZ);
        assertEquals(12, light.range());
        assertFalse(LightCoverage.bounds(light).intersects(LightCoverage.view(Vec3.ZERO,32)));
    }
    @Test void sourceOutsideViewCanIlluminateInsideItAndCornerChunksRemainEligible() {
        var light = new LightDefinition.Point(new Vec3(100,0,100), new Vec3(1,1,1), 1, 12, false, 0);
        assertTrue(LightCoverage.bounds(light).intersects(LightCoverage.view(Vec3.ZERO,96)));
        var area = new LightDefinition.Area(new Vec3(0,0,104),new Vec3(0,0,-1),new Vec3(0,1,0),new Vec3(1,1,1),1,8,16,12,90,true,0);
        assertEquals(13, LightCoverage.radius(area));
        assertTrue(LightCoverage.bounds(area).intersects(LightCoverage.view(Vec3.ZERO,96)));
    }
    @Test void optionalViewingLimitNeverExpandsMinecraftDistance() {
        assertEquals(192, LightCoverage.distanceBlocks(12,0));
        assertEquals(64, LightCoverage.distanceBlocks(12,4));
        assertEquals(192, LightCoverage.distanceBlocks(12,32));
    }
    @Test void awayFacingBeamDoesNotRetainItsUnusedSphericalHalf() {
        var spot=new LightDefinition.Spot(new Vec3(0,0,-2),new Vec3(0,0,-1),new Vec3(0,1,0),new Vec3(1,1,1),1,24,30,20,true,1);
        assertTrue(LightCoverage.bounds(spot).maxZ>0);
        assertEquals(-2,LightCoverage.renderingBounds(spot).maxZ,.002);
        assertTrue(LightCoverage.renderingBounds(spot).maxX<7);
    }
    @Test void coneBoundsIncludeEverySampledRayInArbitraryOrientations() {
        var random=new java.util.Random(917L);
        for(int n=0;n<80;n++) {
            var forward=new Vec3(random.nextDouble()*2-1,random.nextDouble()*2-1,random.nextDouble()*2-1).normalize();
            float angle=1+random.nextFloat()*177;
            var spot=new LightDefinition.Spot(new Vec3(-130,46,218),forward,new Vec3(0,1,0),new Vec3(1,1,1),1,32,angle,0,false,0);
            var bounds=LightCoverage.renderingBounds(spot);var right=spot.forward().cross(spot.up());
            for(int a=0;a<=12;a++)for(int b=0;b<24;b++) {
                double theta=Math.toRadians(angle*.5)*a/12,phi=Math.PI*2*b/24;
                var ray=forward.scale(Math.cos(theta)).add(right.scale(Math.sin(theta)*Math.cos(phi))).add(spot.up().scale(Math.sin(theta)*Math.sin(phi)));
                assertTrue(bounds.contains(spot.position().add(ray.scale(32))),"cone clipped at "+angle);
            }
        }
    }
    @Test void areaBoundsContainRaysFromAllFourEmitterOffsets() {
        var area=new LightDefinition.Area(new Vec3(4,3,2),new Vec3(.3,.4,.5),new Vec3(0,1,0),new Vec3(1,1,1),1,16,12,8,100,true,1);
        var bounds=LightCoverage.renderingBounds(area);var right=area.forward().cross(area.up());
        for(int x:new int[]{-1,1})for(int y:new int[]{-1,1})for(int a=0;a<36;a++) {
            var emitter=area.position().add(right.scale(x*3)).add(area.up().scale(y*2));
            double theta=Math.toRadians(50),phi=a*Math.PI/18;
            var ray=area.forward().scale(Math.cos(theta)).add(right.scale(Math.sin(theta)*Math.cos(phi))).add(area.up().scale(Math.sin(theta)*Math.sin(phi)));
            assertTrue(bounds.contains(emitter));assertTrue(bounds.contains(emitter.add(ray.scale(16))));
        }
    }
    @Test void profileLayersCanBeWiderThanTheSpotDefinitionAndHaveTheirOwnReach() {
        var profile=new BeamProfile(new BeamProfile.Layer(new Vec3(1,1,1),1,20,8,.2f,2),
                new BeamProfile.Layer(new Vec3(1,1,1),1,120,12,.2f,2),0,new BeamProfile.Dust(0,.02f,20,0),false);
        var spot=new LightDefinition.Spot(Vec3.ZERO,new Vec3(0,0,1),new Vec3(0,1,0),new Vec3(1,1,1),1,24,20,10,false,0,profile);
        var bounds=LightCoverage.renderingBounds(spot);
        assertTrue(bounds.contains(new Vec3(10,0,6)));
        assertEquals(12,bounds.maxZ,.002);
        assertTrue(bounds.maxX>10&&bounds.maxX<11);
    }
    @Test void pointInfluenceRemainsVisibleWhenOnlyItsSourceIsOffscreen() {
        var light=new LightDefinition.Point(new Vec3(0,0,-4),new Vec3(1,1,1),1,16,true,1);
        assertTrue(LightCoverage.renderingBounds(light).contains(new Vec3(0,0,10)));
    }
    @Test void veryNarrowConesCoverTheShaderCosineClamp() {
        var spot=new LightDefinition.Spot(Vec3.ZERO,new Vec3(0,0,1),new Vec3(0,1,0),new Vec3(1,1,1),1,24,.01f,0,false,0);
        assertTrue(LightCoverage.renderingBounds(spot).maxX>.3);
    }
}
