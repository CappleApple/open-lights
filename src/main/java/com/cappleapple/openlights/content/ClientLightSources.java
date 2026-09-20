package com.cappleapple.openlights.content;

import com.cappleapple.openlights.OpenLightsMod;
import com.cappleapple.openlights.api.client.CollectLightsEvent;
import com.cappleapple.openlights.api.client.LightDefinition;
import com.cappleapple.openlights.beam.BeamProfiles;
import com.cappleapple.openlights.api.client.LightKey;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = OpenLightsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientLightSources {
    private static final ResourceLocation OWNER = new ResourceLocation(OpenLightsMod.MOD_ID, "block");

    private ClientLightSources() {}

    @SubscribeEvent
    public static void collect(CollectLightsEvent event) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (LightSourceBlockEntity light : LightSourceTracker.snapshot(level)) {
            if (!light.enabled() || !(light.getBlockState().getBlock() instanceof LightSourceBlock block)) continue;
            Direction facing = light.getBlockState().getValue(LightSourceBlock.FACING);
            Vec3 forward = Vec3.atLowerCornerOf(facing.getNormal());
            Vec3 up = facing.getAxis() == Direction.Axis.Y ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
            Vec3 center = Vec3.atCenterOf(light.getBlockPos());
            int rgb = light.color();
            Vec3 color = new Vec3(((rgb >> 16) & 255) / 255.0, ((rgb >> 8) & 255) / 255.0, (rgb & 255) / 255.0);
            var profile=BeamProfiles.clientProfile(new ResourceLocation("openlights","spot_light"));
            LightDefinition definition = switch (block.shape()) {
                case POINT -> new LightDefinition.Point(center, color, light.intensity(), light.range(), true, 0.25f);
                case SPOT -> new LightDefinition.Spot(center.add(forward.scale(0.55)), forward, up, color,
                        light.intensity(), Math.min(light.range(),Math.max(profile.inner().range(),profile.outer().range())),
                        profile.outer().angleDegrees(), profile.inner().angleDegrees(), profile.shadows(), profile.fogDensity(),profile);
                case AREA -> new LightDefinition.Area(center.add(forward.scale(0.55)), forward, up, color,
                        light.intensity(), light.range(), 1.5f, 0.75f, 100, true, 0.4f);
            };
            event.add(new LightKey(OWNER, new UUID(light.getBlockPos().asLong(), block.shape().ordinal())), definition);
        }
    }
}
