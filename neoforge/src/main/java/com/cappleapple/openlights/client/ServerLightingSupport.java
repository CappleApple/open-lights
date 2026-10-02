package com.cappleapple.openlights.client;

import com.cappleapple.openlights.network.BeamProfileNetwork;
import net.minecraft.client.Minecraft;

/** Connection-scoped capability; never inferred from a delayed profile packet or local mod list. */
public final class ServerLightingSupport {
    private ServerLightingSupport() {}

    /** A resource reload under an absent server's registry can omit our block models. */
    static void restoreServerModels() {
        if (isClientOnly()) return;
        var mc = Minecraft.getInstance();
        var models = mc.getModelManager();
        var shaper = models.getBlockModelShaper();
        for (var block : java.util.List.of(
                com.cappleapple.openlights.content.ModContent.POINT_LIGHT.get(),
                com.cappleapple.openlights.content.ModContent.SPOT_LIGHT.get(),
                com.cappleapple.openlights.content.ModContent.AREA_LIGHT.get())) {
            if (shaper.getBlockModel(block.defaultBlockState()) == models.getMissingModel()) {
                mc.reloadResourcePacks();
                return;
            }
        }
    }

    public static boolean isClientOnly() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && !connection.hasChannel(BeamProfileNetwork.ProfilesPayload.TYPE);
    }
}
