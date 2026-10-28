package com.cappleapple.openlights.gi;

import net.minecraft.client.Minecraft;

/** Native GI content is available only when the remote server has the GI addon. */
public final class GiServerSupport {
    private GiServerSupport() {}
    public static boolean isClientOnly() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && !GlobalIlluminationContentNetwork.supports(connection.getConnection());
    }
}
