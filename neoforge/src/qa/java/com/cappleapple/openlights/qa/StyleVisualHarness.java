package com.cappleapple.openlights.qa;

import com.cappleapple.openlights.client.LightingBudgets;
import com.cappleapple.openlights.client.LightingSettingsScreen;
import com.cappleapple.openlights.client.render.OpenLightRenderer;
import com.cappleapple.openlights.client.scene.WorldLightCache;
import com.cappleapple.openlights.config.ClientConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

/** Actual packaged-client comparisons; disposable fixture only. */
@EventBusSubscriber(modid="openlightsqa",value=Dist.CLIENT)
public final class StyleVisualHarness {
    private static final String[] NAMES={"minecraft","openlights-balanced","openlights-rapid","opaque-wall","purple-item","purple-hand","minecraft-purple-item","settings"};
    private static boolean loading,teleported,prepared;
    private static volatile boolean built;
    private static int stage=-1,ticks,settled=-1,idle;
    private static boolean itemBound,handBound,menuTested;
    private static boolean openOnly(){return Boolean.getBoolean("openlights.openStyleOnly");}
    private static String captureName(){return openOnly()&&stage==0?"openlights-initial":openOnly()&&stage==6?"openlights-purple-item":NAMES[stage];}
    @SubscribeEvent public static void hand(net.neoforged.neoforge.client.event.RenderHandEvent event) {
        if(!Boolean.getBoolean("openlights.styleSmoke")||(stage!=4&&stage!=5)||ticks<120||event.getHand()!=net.minecraft.world.InteractionHand.MAIN_HAND)return;
        try {
            var type=Class.forName("com.cappleapple.openlights.client.render.FirstPersonLighting");var field=type.getDeclaredField("texture");field.setAccessible(true);
            var texture=(net.minecraft.client.renderer.texture.DynamicTexture)field.get(null);
            if(texture==null||com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(2)!=texture.getId())throw new IllegalStateException("Main-hand color lightmap is not bound at stage "+stage);
            int color=texture.getPixels().getPixelRGBA(0,0);
            if((color>>16&255)<=(color>>8&255)||(color&255)<=(color>>8&255))throw new IllegalStateException("First-person map lacks purple tint: "+Integer.toHexString(color));
            if(stage==4)itemBound=true;else handBound=true;
        }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("openlights.styleSmoke"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        mc.mouseHandler.releaseMouse();GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(!loading&&mc.screen instanceof TitleScreen title) {
            loading=true;mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(60);mc.options.hideGui=true;mc.options.fov().set(85);
            QaBootstrap.open(title);
        }
        if(mc.player==null||mc.getSingleplayerServer()==null)return;
        if(!teleported) {
            teleported=true;ClientConfig.ENABLED.set(true);ClientConfig.GI_ENABLED.set(false);ClientConfig.PERIODIC_CACHE_REFRESH.set(false);
            ClientConfig.LIGHTING_MODE.set(ClientConfig.LightingMode.CACHED);ClientConfig.BLOCK_LIGHT_STYLE.set(openOnly()?ClientConfig.BlockLightStyle.OPEN_LIGHTS:ClientConfig.BlockLightStyle.MINECRAFT);
            ClientConfig.AGGREGATE_ENABLED.set(true);ClientConfig.AGGREGATE_MULTIPLIER.set(3.0);ClientConfig.AGGREGATE_STRENGTH.set(1.0);LightingBudgets.BALANCED.apply();
            move(false,false);
        }
        ticks++;
        if(!prepared&&ticks>100&&mc.level.hasChunk(35,35)) {
            prepared=true;mc.getSingleplayerServer().execute(()->{
                var world=mc.getSingleplayerServer().overworld();var pos=new BlockPos.MutableBlockPos();
                world.setDayTime(18000);world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,mc.getSingleplayerServer());
                world.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,mc.getSingleplayerServer());world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,mc.getSingleplayerServer());
                for(int z=-52;z<=52;z++)for(int x=-52;x<=52;x++) {
                    world.setBlock(pos.set(512+x,160,512+z),Blocks.SMOOTH_STONE.defaultBlockState(),3);world.setBlock(pos.set(512+x,185,512+z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
                    for(int y=161;y<185;y++)world.setBlock(pos.set(512+x,y,512+z),Math.abs(x)==52||Math.abs(z)==52?Blocks.SMOOTH_STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
                }
                for(int z=-1;z<=1;z++)for(int y=161;y<=163;y++)for(int x=-1;x<=1;x++)world.setBlock(new BlockPos(512+x,y,512+z),Blocks.GLOWSTONE.defaultBlockState(),3);
                built=true;
            });
        }
        if(built&&stage<0){stage=0;ticks=0;}
        if(stage>=0&&stage<NAMES.length&&ticks>3600)throw new IllegalStateException("Style timeout "+NAMES[stage]+" "+OpenLightRenderer.cacheStatistics());
        if(stage>=NAMES.length&&ticks>20)mc.stop();
        var stats=OpenLightRenderer.cacheStatistics();
        boolean ready=stage>=0&&!stats.aggregatePending()&&!stats.lightUploadsPending()&&stats.blockPending()==0&&stats.aggregateCells()>0;
        if(ready&&ticks>5&&settled<0){settled=ticks;LogUtils.getLogger().info("STYLE_SETTLED {} ticks={} workerWallMillis={}",NAMES[Math.min(stage,NAMES.length-1)],ticks,world().aggregates().workerMillis());}
        if(stage==2&&ready&&stats.worldUpdated()==0&&stats.aggregateUpdated()==0&&stats.lightUploads()==0)idle++;else if(stage==2)idle=0;
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if(!Boolean.getBoolean("openlights.styleSmoke")||stage<0||stage>=NAMES.length||ticks<160)return;
        var mc=Minecraft.getInstance();var stats=OpenLightRenderer.cacheStatistics();
        if(stage!=7&&(stats.aggregatePending()||stats.blockPending()!=0||stats.lightUploadsPending()))return;
        if(settled<0)settled=ticks;
        if(stage==2&&idle<100)return;
        if(!stats.replacement())throw new IllegalStateException("Cached replacement inactive");
        if(stage==1||stage==2) {
            int a=sample(20,162,0)>>>24,b=sample(14,162,14)>>>24;
            if(!world().aggregates().analytic()||a==0||Math.abs(a-b)>4)throw new IllegalStateException("Radial falloff mismatch: "+a+" / "+b);
        }
        if(stage==3&&((sample(0,162,30)>>>24)!=0||(sample(12,162,20)>>>24)==0))throw new IllegalStateException("Finite wall failed to cast a local direct shadow");
        if(stage==4||stage==5) {
            int value=world().aggregates().sample(BlockPos.containing(mc.gameRenderer.getMainCamera().getPosition()));
            if((value>>>24)==0||(value>>8&255)>=(value&255))throw new IllegalStateException("No purple light at first-person camera: "+Integer.toHexString(value));
            if(stage==4&&!itemBound||stage==5&&!handBound)throw new IllegalStateException("Colored hand pass was not observed");
        }
        if(stage==7&&!menuTested) {
            menuTested=true;button("Lighting update speed").onPress();if(LightingBudgets.current()!=LightingBudgets.FAST)throw new IllegalStateException("Fast preset button failed");
            button("Lighting update speed").onPress();if(LightingBudgets.current()!=LightingBudgets.RAPID)throw new IllegalStateException("Rapid preset button failed");
            if(!openOnly()){
                button("Block lighting style").onPress();if(ClientConfig.BLOCK_LIGHT_STYLE.get()!=ClientConfig.BlockLightStyle.MINECRAFT)throw new IllegalStateException("Minecraft style button failed");
                button("Block lighting style").onPress();
            }return;
        }
        if(openOnly()&&ClientConfig.BLOCK_LIGHT_STYLE.get()!=ClientConfig.BlockLightStyle.OPEN_LIGHTS)throw new IllegalStateException("Open Lights style changed");
        Screenshot.grab(mc.gameDirectory,"style-"+captureName()+".png",mc.getMainRenderTarget(),m->{});
        LogUtils.getLogger().info("STYLE_CAPTURE {} settledTicks={} idle={} cache={}",captureName(),settled,idle,stats);
        ticks=0;settled=-1;stage++;
        switch(stage) {
            case 1->{ClientConfig.BLOCK_LIGHT_STYLE.set(ClientConfig.BlockLightStyle.OPEN_LIGHTS);world().clear();}
            case 2->{LightingBudgets.RAPID.apply();world().clear();}
            case 3->wall(false);
            case 4->{wall(true);mc.options.hideGui=false;move(true,false);}
            case 5->move(true,true);
            case 6->{ClientConfig.BLOCK_LIGHT_STYLE.set(openOnly()?ClientConfig.BlockLightStyle.OPEN_LIGHTS:ClientConfig.BlockLightStyle.MINECRAFT);move(true,false);}
            case 7->{ClientConfig.BLOCK_LIGHT_STYLE.set(ClientConfig.BlockLightStyle.OPEN_LIGHTS);LightingBudgets.BALANCED.apply();mc.setScreen(new LightingSettingsScreen(null));}
            default->{LightingBudgets.BALANCED.apply();ClientConfig.BLOCK_LIGHT_STYLE.save();LogUtils.getLogger().info("STYLE_SUCCESS radial falloff, finite-wall shadow, colored item/hand bindings and captures, menu presets and idle; Open Lights only={}",openOnly());}
        }
    }
    private static void move(boolean hand,boolean empty) {
        var mc=Minecraft.getInstance();var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);player.setGameMode(hand?GameType.CREATIVE:GameType.SPECTATOR);
            player.getAbilities().flying=true;player.onUpdateAbilities();player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,empty?ItemStack.EMPTY:new ItemStack(Blocks.DEEPSLATE));
            player.teleportTo(player.serverLevel(),512.5,hand?162:176,hand?504.5:468.5,0,hand?16:20);
        });
    }
    private static void wall(boolean glass) {
        var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{
            var world=mc.getSingleplayerServer().overworld();
            for(int x=-51;x<=51;x++)for(int y=161;y<185;y++) {
                world.setBlock(new BlockPos(512+x,y,520),!glass&&Math.abs(x)<=2&&y<=165?Blocks.SMOOTH_STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
                if(glass)world.setBlock(new BlockPos(512+x,y,509),Blocks.PURPLE_STAINED_GLASS.defaultBlockState(),3);
            }
            if(glass)for(int z=-1;z<=1;z++)for(int y=161;y<=163;y++)for(int x=-1;x<=1;x++)world.setBlock(new BlockPos(512+x,y,512+z),Blocks.SEA_LANTERN.defaultBlockState(),3);
        });
    }
    private static int sample(int x,int y,int z){return world().aggregates().sample(new BlockPos(512+x,y,512+z));}
    private static net.minecraft.client.gui.components.Button button(String prefix){return Minecraft.getInstance().screen.children().stream().filter(child->child instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().startsWith(prefix)).map(child->(net.minecraft.client.gui.components.Button)child).findFirst().orElseThrow();}
    private static WorldLightCache world(){try{var field=OpenLightRenderer.class.getDeclaredField("WORLD_LIGHT");field.setAccessible(true);return (WorldLightCache)field.get(null);}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
}
