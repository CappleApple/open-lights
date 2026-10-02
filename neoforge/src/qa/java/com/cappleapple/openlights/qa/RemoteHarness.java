package com.cappleapple.openlights.qa;

import com.cappleapple.openlights.beam.BeamProfiles;
import com.cappleapple.openlights.client.render.OpenLightRenderer;
import com.cappleapple.openlights.content.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Packaged client connected to a separate packaged dedicated server. */
@EventBusSubscriber(modid="openlightsqa",value=Dist.CLIENT)
public final class RemoteHarness {
    private static boolean connecting;
    private static int ticks, stage;
    private static long revision;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("openlights.remoteSmoke"))return;
        var mc=Minecraft.getInstance();mc.mouseHandler.releaseMouse();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);mc.options.pauseOnLostFocus=false;
        if(!connecting&&mc.screen instanceof TitleScreen title){connecting=true;ConnectScreen.startConnecting(title,mc,ServerAddress.parseString("127.0.0.1:25586"),new ServerData("Open Lights QA","127.0.0.1:25586",ServerData.Type.OTHER),false,null);}
        if(!connecting)return;
        if(++ticks>1800)throw new IllegalStateException("Remote timeout at stage "+stage+" screen="+mc.screen);
        if(mc.player==null||mc.screen!=null||ticks<100)return;
        if(stage==0){
            require(mc.getSingleplayerServer()==null,"Expected a remote server");require(BeamProfiles.clientRevision()>0,"Profiles did not synchronize at login");
            for(String command:new String[]{"gamemode creative","time set midnight","gamerule doDaylightCycle false","fill -7 160 -2 7 160 18 stone","fill -7 161 18 7 168 18 stone_bricks","tp @s 0.5 161 -1 0 0","setblock 0 162 4 openlights:point_light{Color:3381759,Range:24f,Enabled:1b}","clear @s","give @s openlights:flashlight"})mc.player.connection.sendCommand(command);
        }else if(stage==1){
            require(mc.player.getMainHandItem().is(ModContent.FLASHLIGHT.get()),"Flashlight inventory not synchronized");
            var block=mc.level.getBlockEntity(new BlockPos(0,162,4));require(block instanceof LightSourceBlockEntity light&&light.color()==3381759&&light.enabled(),"Placed light NBT not synchronized");
            mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
        }else if(stage==2){
            require(FlashlightItem.isEnabled(mc.player.getMainHandItem()),"Server item toggle not synchronized");
            require(OpenLightRenderer.frameLights().keySet().stream().anyMatch(k->k.owner().getPath().equals("handheld")),"Remote flashlight not rendered");
            capture("remote-on");revision=BeamProfiles.clientRevision();mc.player.connection.sendCommand("reload");
        }else if(stage==3){
            require(BeamProfiles.clientRevision()>revision,"Reload profile payload not synchronized");
            mc.player.connection.sendCommand("data merge block 0 162 4 {Color:16724838,Enabled:0b}");mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
        }else if(stage==4){
            require(!FlashlightItem.isEnabled(mc.player.getMainHandItem()),"Off toggle did not synchronize");
            var block=mc.level.getBlockEntity(new BlockPos(0,162,4));require(block instanceof LightSourceBlockEntity light&&light.color()==16724838&&!light.enabled(),"Block update packet not synchronized");
            capture("remote-off");LogUtils.getLogger().info("REMOTE_SUCCESS separate server login, item toggles, placed-light NBT updates, renderer, profile reload");mc.stop();
        }
        LogUtils.getLogger().info("REMOTE_STAGE {}",stage);stage++;ticks=0;
    }
    private static void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),m->{});}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
