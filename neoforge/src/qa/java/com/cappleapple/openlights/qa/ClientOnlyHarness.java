package com.cappleapple.openlights.qa;

import com.cappleapple.openlights.beam.BeamProfiles;
import com.cappleapple.openlights.client.*;
import com.cappleapple.openlights.client.render.OpenLightRenderer;
import com.cappleapple.openlights.config.ClientConfig;
import com.cappleapple.openlights.content.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Real network checks against servers with and without the mod, in the same client process. */
@EventBusSubscriber(modid="openlightsqa",value=Dist.CLIENT)
public final class ClientOnlyHarness {
    private static boolean connecting;
    private static int ticks,stage;
    private static int absentPort(){return Integer.getInteger("openlights.absentPort",25587);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("openlights.clientOnlySmoke"))return;
        var mc=Minecraft.getInstance();mc.mouseHandler.releaseMouse();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);mc.options.pauseOnLostFocus=false;
        if(!connecting&&mc.screen instanceof TitleScreen){connecting=true;connect(absentPort());}
        if(!connecting)return;
        if(++ticks>2400)throw new IllegalStateException("Client-only timeout stage="+stage+" screen="+mc.screen);
        if(mc.player==null||ticks<200||(mc.screen!=null&&stage!=5))return;
        switch(stage){
            case 0->{
                absent();compatibility();TextureUploadRegression.managedBindings();ClientConfig.ENABLED.set(false);ClientConfig.GI_ENABLED.set(true);ClientConfig.LIGHTING_MODE.set(ClientConfig.LightingMode.CACHED);
                ClientConfig.AGGREGATE_ENABLED.set(true);ClientConfig.BLOCK_LIGHT_STYLE.set(ClientConfig.BlockLightStyle.OPEN_LIGHTS);LightingBudgets.FAST.apply();
                commands("gamemode creative","gamerule doDaylightCycle false","time set midnight","fill -10 160 0 10 170 24 stone hollow","fill -8 161 9 8 168 9 purple_stained_glass","fill -1 161 14 1 163 16 glowstone","tp @s 0.5 161 2.5 0 15","clear @s","give @s deepslate");
            }
            case 1->{
                capture("native");ClientConfig.ENABLED.set(true);
                // Attempt stale saved-hotbar items. Neither packet is valid on this server.
                mc.gameMode.handleCreativeModeItemAdd(new net.minecraft.world.item.ItemStack(ModContent.FLASHLIGHT.get()),36);
                mc.gameMode.handleCreativeModeItemDrop(new net.minecraft.world.item.ItemStack(ModContent.POINT_LIGHT.get()));
                mc.gameMode.handleCreativeModeItemAdd(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND),37);
            }
            case 2->{
                absent();require(mc.player.getInventory().getItem(1).is(net.minecraft.world.item.Items.DIAMOND),"Valid creative submission was blocked");var cache=OpenLightRenderer.cacheStatistics();
                if((!shaderPack()&&(cache.aggregatePending()||cache.blockPending()!=0))||ticks<400)return;
                lighting();
                if(!shaderPack())require(cache.blockSections()>0&&cache.aggregateCells()>0,"Replacement caches did not populate: "+cache);
                capture("openlights");if(!Boolean.getBoolean("openlights.openStyleOnly"))ClientConfig.BLOCK_LIGHT_STYLE.set(ClientConfig.BlockLightStyle.MINECRAFT);
            }
            case 3->{absent();capture(Boolean.getBoolean("openlights.openStyleOnly")?"openlights-settled":"minecraft");mc.reloadResourcePacks();}
            case 4->{absent();lighting();mc.setScreen(new LightingSettingsScreen(null));}
            case 5->{require(mc.screen.getTitle().getString().contains("client-only"),"Mode label absent");capture("settings");mc.setScreen(null);commands("execute in minecraft:the_nether run tp @s 0.5 180 2.5");}
            case 6->{absent();require(mc.level.dimension()==Level.NETHER,"Dimension change failed");commands("execute in minecraft:overworld run tp @s 0.5 161 2.5 0 15");}
            case 7->{absent();require(mc.level.dimension()==Level.OVERWORLD,"Return failed");disconnect();connect(25586);}
            case 8->{
                require(!ServerLightingSupport.isClientOnly()&&BeamProfiles.clientRevision()>0,"Full server capability/profile missing");tabs(true);
                commands("gamemode creative","fill -7 160 -2 7 160 18 stone","tp @s 0.5 161 -1 0 0","setblock 0 162 4 openlights:point_light{Color:3381759,Enabled:1b}","clear @s","give @s openlights:flashlight");
            }
            case 9->{require(mc.level.getBlockEntity(new BlockPos(0,162,4)) instanceof LightSourceBlockEntity,"Full server content missing");require(mc.player.getMainHandItem().is(ModContent.FLASHLIGHT.get()),"Full server item missing");mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
            case 10->{modelsPresent();require(FlashlightItem.isEnabled(mc.player.getMainHandItem()),"Full server toggle failed");capture("full-server");disconnect();connect(absentPort());}
            case 11->{absent();lighting();capture("rejoined");if(Boolean.getBoolean("openlights.openStyleOnly")&&Boolean.getBoolean("openlights.expectShaderPack"))toggleShaders(false);else success();}
            case 12->{
                absent();var cache=OpenLightRenderer.cacheStatistics();if(cache.aggregatePending()||cache.blockPending()!=0||ticks<400)return;
                lighting();require(cache.aggregateCells()>0&&cache.blockSections()>0,"Open Lights style did not resume after disabling shaders");capture("shader-disabled-openlights");toggleShaders(true);
            }
            case 13->{absent();lighting();capture("shader-reenabled");success();}
        }
        LogUtils.getLogger().info("CLIENT_ONLY_STAGE {} cache={}",stage,OpenLightRenderer.cacheStatistics());stage++;ticks=0;
    }
    private static boolean shaderPack(){return com.cappleapple.openlights.api.client.ShaderCompatibility.isShaderPackInUse();}
    private static void success(){LogUtils.getLogger().info("CLIENT_ONLY_SUCCESS absent port={}, shaderPack={}, lighting caches, creative filtering, reload, dimensions, full-server transition and return",absentPort(),shaderPack());Minecraft.getInstance().stop();}
    private static void toggleShaders(boolean enabled){
        try{
            var api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");var instance=api.getMethod("getInstance").invoke(null);
            var config=api.getMethod("getConfig").invoke(instance);
            Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("setShadersEnabledAndApply",boolean.class).invoke(config,enabled);
            System.setProperty("openlights.expectShaderPack",Boolean.toString(enabled));
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Shader toggle failed",error);}
    }
    private static void compatibility(){
        var mods=net.neoforged.fml.ModList.get();
        for(String id:java.util.List.of("sodium","iris")){
            String expected=System.getProperty("openlights.expect."+id);
            if(expected!=null)require(mods.isLoaded(id)==Boolean.parseBoolean(expected),"Unexpected mod presence: "+id);
        }
        String expected=System.getProperty("openlights.expectShaderPack");
        if(expected!=null)require(shaderPack()==Boolean.parseBoolean(expected),"Unexpected shader-pack state");
        LogUtils.getLogger().info("COMPATIBILITY_STATE sodium={} iris={} shaderPack={}",mods.isLoaded("sodium"),mods.isLoaded("iris"),shaderPack());
    }
    private static void lighting(){
        compatibility();var cache=OpenLightRenderer.cacheStatistics();
        if(Boolean.getBoolean("openlights.openStyleOnly"))require(ClientConfig.BLOCK_LIGHT_STYLE.get()==ClientConfig.BlockLightStyle.OPEN_LIGHTS,"Open Lights style changed during test");
        require(cache.replacement()!=shaderPack()&&cache.giProbes()>0,"Lighting ownership or GI incorrect: "+cache);
    }
    private static void modelsPresent(){
        var models=Minecraft.getInstance().getModelManager();
        for(var block:java.util.List.of(ModContent.POINT_LIGHT.get(),ModContent.SPOT_LIGHT.get(),ModContent.AREA_LIGHT.get()))
            for(var state:block.getStateDefinition().getPossibleStates())
                require(models.getBlockModelShaper().getBlockModel(state)!=models.getMissingModel(),"Model missing after registry transition: "+state);
    }
    private static void absent(){require(ServerLightingSupport.isClientOnly(),"Server unexpectedly advertises Open Lights");require(BeamProfiles.clientRevision()==0&&BeamProfiles.clientSnapshot().isEmpty(),"Stale server profiles retained");tabs(false);}
    private static void tabs(boolean expected){
        var mc=Minecraft.getInstance();CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(),true,mc.level.registryAccess());
        long count=CreativeModeTabs.allTabs().stream().flatMap(t->t.getDisplayItems().stream()).filter(s->BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals("openlights")).count();
        require(expected?count>=4:count==0,"Creative server content availability incorrect: "+count);
    }
    private static void connect(int port){var mc=Minecraft.getInstance();String address="127.0.0.1:"+port;ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(address),new ServerData("Open Lights optional-server QA",address,ServerData.Type.OTHER),false,null);}
    private static void disconnect(){var mc=Minecraft.getInstance();mc.level.disconnect();mc.disconnect();mc.setScreen(new TitleScreen());}
    private static void commands(String...commands){for(String command:commands)Minecraft.getInstance().player.connection.sendCommand(command);}
    private static void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"client-only-"+name+".png",mc.getMainRenderTarget(),m->{});}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
