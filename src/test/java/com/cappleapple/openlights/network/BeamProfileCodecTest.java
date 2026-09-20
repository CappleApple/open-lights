package com.cappleapple.openlights.network;

import com.cappleapple.openlights.beam.BeamProfile;
import com.cappleapple.openlights.beam.BeamProfileParser;
import com.cappleapple.openlights.beam.BeamProfiles;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BeamProfileCodecTest {
    @Test
    void roundTripPreservesEverySettingAndRevisionExactly() {
        var profile = BeamProfileParser.parse(JsonParser.parseString("""
                {"inner":{"color":"#12AB34","intensity":3,"range":12,"angleDegrees":12,"edgeSoftness":0.8,"falloff":4},
                 "outer":{"color":"#654321","intensity":0.4,"range":32,"angleDegrees":60,"edgeSoftness":0.1,"falloff":1},
                 "fogDensity":1.2,"dust":{"rate":25,"size":0.1,"lifetimeTicks":110,"speed":0.4},"shadows":false}
                """), BeamProfile.DEFAULT);
        var snapshot = new BeamProfiles.Snapshot(25, BeamProfile.DEFAULT,
                Map.of(new ResourceLocation("tacz", "laser_lopro"), profile));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            BeamProfileCodec.encode(snapshot, buffer);
            assertEquals(snapshot, BeamProfileCodec.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void maximumSnapshotFitsBelowNinetySixKibibytes() {
        var profiles = new LinkedHashMap<ResourceLocation, BeamProfile>();
        for (int index = 0; index < BeamProfiles.MAX_PROFILES; index++) {
            String prefix = "test:profile_" + index + "_";
            profiles.put(new ResourceLocation(prefix + "a".repeat(BeamProfiles.MAX_ID_LENGTH - prefix.length())),
                    BeamProfile.DEFAULT);
        }
        var snapshot = new BeamProfiles.Snapshot(9, BeamProfile.DEFAULT, profiles);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            BeamProfileCodec.encode(snapshot, buffer);
            assertTrue(buffer.readableBytes() < 96 * 1024);
            assertEquals(snapshot, BeamProfileCodec.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsTrailingDataAndTruncatedSnapshots() {
        var snapshot = new BeamProfiles.Snapshot(1, BeamProfile.DEFAULT, Map.of());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            BeamProfileCodec.encode(snapshot, buffer);
            buffer.writeByte(0);
            assertThrows(IllegalArgumentException.class, () -> BeamProfileCodec.decode(buffer));
            buffer.clear();
            buffer.writeVarLong(1);
            assertThrows(IndexOutOfBoundsException.class, () -> BeamProfileCodec.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsOversizedProfileSetsBeforeTheyCanBeSent() {
        var profiles = new LinkedHashMap<ResourceLocation, BeamProfile>();
        for (int index = 0; index <= BeamProfiles.MAX_PROFILES; index++) {
            profiles.put(new ResourceLocation("test", "profile_" + index), BeamProfile.DEFAULT);
        }
        assertThrows(IllegalArgumentException.class, () ->
                new BeamProfiles.Snapshot(1, BeamProfile.DEFAULT, profiles));
        assertThrows(IllegalArgumentException.class, () ->
                new BeamProfiles.Snapshot(-1, BeamProfile.DEFAULT, Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new BeamProfiles.Snapshot(1, BeamProfile.DEFAULT,
                        Map.of(new ResourceLocation("test", "a".repeat(256)), BeamProfile.DEFAULT)));
    }
}
