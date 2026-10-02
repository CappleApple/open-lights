package com.cappleapple.openlights.qa;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.core.registries.Registries;
import net.neoforged.fml.common.Mod;
@Mod("openlightsqa")
public final class QaBootstrap {
    public static void open(Screen parent) {
        var mc=Minecraft.getInstance();
        if (com.cappleapple.openlights.config.ClientConfig.GI_ENABLED.get()) throw new IllegalStateException("GI should default off in a fresh config");
        com.mojang.logging.LogUtils.getLogger().info("DEFAULT_GI_OFF_VERIFIED");
        if(new java.io.File(mc.gameDirectory,"saves/SmokeWorld/level.dat").exists()) {
            mc.createWorldOpenFlows().openWorld("SmokeWorld", ()->mc.setScreen(parent));
        } else {
            var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("SmokeWorld",
                    new LevelSettings("Open Lights disposable QA",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),
                    new WorldOptions(187L,false,false),
                    registry->registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),parent);
        }
    }
}
