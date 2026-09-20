package com.cappleapple.openlights.beam;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class BeamProfileTest {
    private static BeamProfile parse(String json) {
        return BeamProfileParser.parse(JsonParser.parseString(json), BeamProfile.DEFAULT);
    }

    @Test
    void emptyEntryInheritsEveryServerDefault() {
        assertEquals(BeamProfile.DEFAULT, parse("{}"));
    }

    @Test
    void partialLayersInheritIndependentlyAndHaveIndependentRanges() {
        var profile = parse("""
                {"inner":{"color":"#FF0000","range":12,"intensity":3},
                 "outer":{"color":"#0080FF","range":32,"falloff":4}}
                """);
        assertEquals(new Vec3(1, 0, 0), profile.inner().color());
        assertEquals(new Vec3(0, 128 / 255.0, 1), profile.outer().color());
        assertEquals(12, profile.inner().range());
        assertEquals(32, profile.outer().range());
        assertEquals(3, profile.inner().intensity());
        assertEquals(4, profile.outer().falloff());
        assertEquals(BeamProfile.DEFAULT.inner().angleDegrees(), profile.inner().angleDegrees());
        assertEquals(BeamProfile.DEFAULT.outer().edgeSoftness(), profile.outer().edgeSoftness());
        assertEquals(BeamProfile.DEFAULT.dust(), profile.dust());
    }

    @Test
    void customServerDefaultsAreTheBaseOfPartialEntries() {
        var defaults = new BeamProfile(
                new BeamProfile.Layer(BeamProfile.color("#123456"), 4, 20, 8, 0.5f, 3),
                new BeamProfile.Layer(BeamProfile.color("#ABCDEF"), 2, 60, 16, 0.1f, 1),
                0, new BeamProfile.Dust(0, 0.1f, 80, 0), false);
        var profile = BeamProfileParser.parse(JsonParser.parseString("""
                {"inner":{"intensity":1},"dust":{"rate":2}}
                """), defaults);
        assertEquals(defaults.inner().color(), profile.inner().color());
        assertEquals(1, profile.inner().intensity());
        assertEquals(defaults.outer(), profile.outer());
        assertEquals(new BeamProfile.Dust(2, 0.1f, 80, 0), profile.dust());
        assertFalse(profile.shadows());
        assertEquals(0, profile.fogDensity());
    }

    @Test
    void zeroDisablesFogDustAndIndividualLayerBrightness() {
        var profile = parse("""
                {"inner":{"intensity":0},"outer":{"intensity":0},"fogDensity":0,
                 "dust":{"rate":0,"speed":0},"shadows":false}
                """);
        assertEquals(0, profile.inner().intensity());
        assertEquals(0, profile.outer().intensity());
        assertEquals(0, profile.fogDensity());
        assertEquals(0, profile.dust().rate());
        assertFalse(profile.shadows());
    }

    @Test
    void rejectsInvertedConesAndInvalidPhysicalBounds() {
        for (String json : new String[] {
                "{\"inner\":{\"angleDegrees\":47}}",
                "{\"outer\":{\"angleDegrees\":0}}",
                "{\"outer\":{\"angleDegrees\":176}}",
                "{\"inner\":{\"range\":33}}",
                "{\"inner\":{\"range\":0}}",
                "{\"inner\":{\"intensity\":17}}",
                "{\"inner\":{\"edgeSoftness\":1.1}}",
                "{\"outer\":{\"falloff\":0}}",
                "{\"fogDensity\":9}",
                "{\"dust\":{\"rate\":81}}",
                "{\"dust\":{\"size\":0.001}}",
                "{\"dust\":{\"lifetimeTicks\":0}}",
                "{\"dust\":{\"speed\":2}}"
        }) assertThrows(IllegalArgumentException.class, () -> parse(json), json);
    }

    @Test
    void rejectsWrongJsonTypesAndMalformedColors() {
        for (String json : new String[] {
                "[]", "null", "{\"inner\":null}", "{\"outer\":[]}",
                "{\"inner\":{\"color\":\"red\"}}",
                "{\"inner\":{\"color\":[1,0,0]}}",
                "{\"inner\":{\"intensity\":\"2\"}}",
                "{\"shadows\":\"true\"}",
                "{\"dust\":{\"lifetimeTicks\":2.5}}",
                "{\"fogDensity\":1e999}"
        }) assertThrows(IllegalArgumentException.class, () -> parse(json), json);
    }

    @Test
    void apiConstructionRejectsNonFiniteScalarsAndColors() {
        assertThrows(IllegalArgumentException.class, () ->
                new BeamProfile.Layer(new Vec3(Double.NaN, 0, 0), 1, 10, 10, 0, 1));
        assertThrows(IllegalArgumentException.class, () ->
                new BeamProfile.Layer(new Vec3(-0.000001, 0, 0), 1, 10, 10, 0, 1));
        assertThrows(IllegalArgumentException.class, () ->
                new BeamProfile.Layer(new Vec3(1, 1, 1), Float.POSITIVE_INFINITY, 10, 10, 0, 1));
        assertThrows(IllegalArgumentException.class, () ->
                new BeamProfile(BeamProfile.DEFAULT.inner(), BeamProfile.DEFAULT.outer(),
                        Float.NaN, BeamProfile.DEFAULT.dust(), true));
    }

    @Test
    void synchronizedSnapshotIsAtomicAndDoesNotRetainMutableInputs() {
        var id = new ResourceLocation("tacz", "laser_lopro");
        var profiles = new HashMap<ResourceLocation, BeamProfile>();
        profiles.put(id, parse("{\"fogDensity\":1}"));
        var snapshot = new BeamProfiles.Snapshot(5, BeamProfile.DEFAULT, profiles);
        profiles.clear();
        BeamProfiles.receive(snapshot);
        try {
            assertEquals(5, BeamProfiles.clientRevision());
            assertEquals(1, BeamProfiles.clientProfile(id).fogDensity());
            assertSame(BeamProfile.DEFAULT, BeamProfiles.clientProfile(new ResourceLocation("other", "missing")));
            assertThrows(UnsupportedOperationException.class, () -> BeamProfiles.clientSnapshot().clear());
        } finally {
            BeamProfiles.resetClient();
        }
        assertEquals(0, BeamProfiles.clientRevision());
        assertEquals(BeamProfile.DEFAULT, BeamProfiles.clientProfile(id));
    }
}
