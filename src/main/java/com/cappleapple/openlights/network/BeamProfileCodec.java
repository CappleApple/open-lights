package com.cappleapple.openlights.network;

import com.cappleapple.openlights.beam.BeamProfile;
import com.cappleapple.openlights.beam.BeamProfiles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;

/** Wire format for a complete immutable profile snapshot. */
public final class BeamProfileCodec {
    private BeamProfileCodec() {}

    public static void encode(BeamProfiles.Snapshot snapshot, FriendlyByteBuf buffer) {
        buffer.writeVarLong(snapshot.revision());
        writeProfile(buffer, snapshot.defaults());
        buffer.writeVarInt(snapshot.profiles().size());
        snapshot.profiles().forEach((id, profile) -> {
            buffer.writeUtf(id.toString(), BeamProfiles.MAX_ID_LENGTH);
            writeProfile(buffer, profile);
        });
    }

    public static BeamProfiles.Snapshot decode(FriendlyByteBuf buffer) {
        long revision = buffer.readVarLong();
        BeamProfile defaults = readProfile(buffer);
        int count = buffer.readVarInt();
        if (count < 0 || count > BeamProfiles.MAX_PROFILES) {
            throw new IllegalArgumentException("Beam profile count exceeds limit");
        }
        var profiles = new LinkedHashMap<ResourceLocation, BeamProfile>();
        for (int index = 0; index < count; index++) {
            ResourceLocation id = new ResourceLocation(buffer.readUtf(BeamProfiles.MAX_ID_LENGTH));
            if (profiles.put(id, readProfile(buffer)) != null) {
                throw new IllegalArgumentException("Duplicate beam profile ID");
            }
        }
        if (buffer.isReadable()) throw new IllegalArgumentException("Trailing beam profile packet data");
        return new BeamProfiles.Snapshot(revision, defaults, profiles);
    }

    private static void writeProfile(FriendlyByteBuf buffer, BeamProfile profile) {
        writeLayer(buffer, profile.inner());
        writeLayer(buffer, profile.outer());
        buffer.writeFloat(profile.fogDensity());
        buffer.writeFloat(profile.dust().rate());
        buffer.writeFloat(profile.dust().size());
        buffer.writeVarInt(profile.dust().lifetimeTicks());
        buffer.writeFloat(profile.dust().speed());
        buffer.writeBoolean(profile.shadows());
    }

    private static BeamProfile readProfile(FriendlyByteBuf buffer) {
        BeamProfile.Layer inner = readLayer(buffer);
        BeamProfile.Layer outer = readLayer(buffer);
        float fog = buffer.readFloat();
        BeamProfile.Dust dust = new BeamProfile.Dust(buffer.readFloat(), buffer.readFloat(),
                buffer.readVarInt(), buffer.readFloat());
        return new BeamProfile(inner, outer, fog, dust, buffer.readBoolean());
    }

    private static void writeLayer(FriendlyByteBuf buffer, BeamProfile.Layer layer) {
        // Preserve exact parsed color values across sync, rather than perturbing profile equality.
        buffer.writeDouble(layer.color().x);
        buffer.writeDouble(layer.color().y);
        buffer.writeDouble(layer.color().z);
        buffer.writeFloat(layer.intensity());
        buffer.writeFloat(layer.angleDegrees());
        buffer.writeFloat(layer.range());
        buffer.writeFloat(layer.edgeSoftness());
        buffer.writeFloat(layer.falloff());
    }

    private static BeamProfile.Layer readLayer(FriendlyByteBuf buffer) {
        return new BeamProfile.Layer(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }
}
