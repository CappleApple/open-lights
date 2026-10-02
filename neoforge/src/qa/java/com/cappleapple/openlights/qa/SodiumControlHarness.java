package com.cappleapple.openlights.qa;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Bulk-edit control fixture, also packaged separately without any Open Lights classes. */
@EventBusSubscriber(modid="openlightsqa",value=Dist.CLIENT)
public final class SodiumControlHarness {
    private static boolean connecting,largeLoaded;
    private static int ticks,stage;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("openlights.sodiumControlSmoke"))return;
        var mc=Minecraft.getInstance();mc.mouseHandler.releaseMouse();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);mc.options.pauseOnLostFocus=false;
        if(!connecting&&mc.screen instanceof TitleScreen){
            connecting=true;String address="127.0.0.1:25587";
            ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(address),new ServerData("Sodium control",address,ServerData.Type.OTHER),false,null);
        }
        if(++ticks>2400)throw new IllegalStateException("Control timeout");
        if(mc.player==null||ticks<200||mc.screen!=null)return;
        if(Boolean.getBoolean("openlights.largeControl")&&!largeLoaded){
            commands("forceload add 460 460 564 564","gamemode spectator","tp @s 512.5 176 468.5 0 20");largeLoaded=true;ticks=0;return;
        }
        switch(stage){
            case 0->{
                if(net.neoforged.fml.ModList.get().isLoaded("openlights"))throw new IllegalStateException("Control unexpectedly contains Open Lights");
                if(Boolean.getBoolean("openlights.largeControl"))commands("gamemode spectator","gamerule doDaylightCycle false","time set midnight",
                        "fill 460 160 460 564 160 564 smooth_stone","fill 460 185 460 564 185 564 smooth_stone",
                        "fill 460 161 460 460 184 564 smooth_stone","fill 564 161 460 564 184 564 smooth_stone",
                        "fill 461 161 460 563 184 460 smooth_stone","fill 461 161 564 563 184 564 smooth_stone",
                        "fill 511 161 511 513 163 513 glowstone","tp @s 512.5 176 468.5 0 20");
                else commands("gamemode creative","gamerule doDaylightCycle false","time set midnight","fill -10 160 0 10 170 24 stone hollow","fill -8 161 9 8 168 9 purple_stained_glass","fill -1 161 14 1 163 16 glowstone","tp @s 0.5 161 2.5 0 15","clear @s","give @s deepslate");
            }
            case 1->capture("before-rebuild");
            case 2->{
                boolean large=Boolean.getBoolean("openlights.largeControl");int count=0;for(int x=-1;x<=1;x++)for(int y=161;y<=163;y++)for(int z=-1;z<=1;z++)if(mc.level.getBlockState(new BlockPos(x+(large?512:0),y,z+(large?512:15))).is(Blocks.GLOWSTONE))count++;
                if(count!=27)throw new IllegalStateException("Incomplete source fixture: "+count);
                capture("settled");mc.levelRenderer.allChanged();
            }
            case 3->{capture("after-rebuild");if(Boolean.getBoolean("openlights.largeControl"))commands("forceload remove 460 460 564 564");com.mojang.logging.LogUtils.getLogger().info("SODIUM_CONTROL_SUCCESS Open Lights absent, 27 glowstone blocks present");mc.stop();}
        }
        stage++;ticks=0;
    }
    private static void commands(String...commands){for(String c:commands)Minecraft.getInstance().player.connection.sendCommand(c);}
    private static void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"sodium-control-"+name+".png",mc.getMainRenderTarget(),m->{});}
}
