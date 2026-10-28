package com.cappleapple.openlights.client;

import com.cappleapple.openlights.network.BeamProfileNetwork;
import net.minecraft.client.Minecraft;

/** Connection-scoped capability; never inferred from a delayed profile packet or local mod list. */
public final class ServerLightingSupport {
    private ServerLightingSupport() {}

    public static boolean isClientOnly() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && !connection.hasChannel(BeamProfileNetwork.ProfilesPayload.TYPE);
    }
}
