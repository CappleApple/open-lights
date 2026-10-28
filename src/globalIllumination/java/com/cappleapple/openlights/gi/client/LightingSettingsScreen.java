package com.cappleapple.openlights.gi.client;

import com.cappleapple.openlights.OpenLightsMod;
import com.cappleapple.openlights.gi.config.GlobalIlluminationConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client lighting controls; cached changes apply under their work budgets. */
@Mod.EventBusSubscriber(modid = com.cappleapple.openlights.gi.GlobalIlluminationMod.MOD_ID, value = Dist.CLIENT)
public final class LightingSettingsScreen extends Screen {
    private static final int[] DISTANCES = {0,2,4,8,12,16,24,32,48,64};
    private final Screen parent;
    public LightingSettingsScreen(Screen parent) { super(Component.translatable(com.cappleapple.openlights.gi.GiServerSupport.isClientOnly() ? "screen.openlights.title.client_only" : "screen.openlights.title")); this.parent = parent; }

    @SubscribeEvent public static void videoSettings(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof VideoSettingsScreen screen)) return;
        for (var listener : event.getListenersList()) {
            if (listener instanceof Button done && done.getMessage().equals(CommonComponents.GUI_DONE)) {
                done.setX(screen.width/2+5); done.setWidth(150);
                event.addListener(Button.builder(Component.translatable("screen.openlights.title"), button ->
                        Minecraft.getInstance().setScreen(new LightingSettingsScreen(screen)))
                        .bounds(screen.width/2-155, done.getY(), 150,20).build());
                break;
            }
        }
    }
    @Override protected void init() {
        addRenderableWidget(Button.builder(styleLabel(),button->{
            GlobalIlluminationConfig.BLOCK_LIGHT_STYLE.set(GlobalIlluminationConfig.BLOCK_LIGHT_STYLE.get()==GlobalIlluminationConfig.BlockLightStyle.OPEN_LIGHTS?GlobalIlluminationConfig.BlockLightStyle.MINECRAFT:GlobalIlluminationConfig.BlockLightStyle.OPEN_LIGHTS);
            GlobalIlluminationConfig.BLOCK_LIGHT_STYLE.save();button.setMessage(styleLabel());
        }).bounds(width/2-130,35,260,20).build());
        addRenderableWidget(Button.builder(distanceLabel(), button -> {
            int current = GlobalIlluminationConfig.LIGHT_RENDER_DISTANCE.get(), next = 0;
            for (int i = 0; i < DISTANCES.length; i++) if (DISTANCES[i] == current) { next = DISTANCES[(i+1)%DISTANCES.length]; break; }
            GlobalIlluminationConfig.LIGHT_RENDER_DISTANCE.set(next); GlobalIlluminationConfig.LIGHT_RENDER_DISTANCE.save();
            button.setMessage(distanceLabel());
        }).bounds(width/2-130,59,260,20).build());
        addRenderableWidget(Button.builder(aggregateLabel(),button->{
            GlobalIlluminationConfig.AGGREGATE_ENABLED.set(!GlobalIlluminationConfig.AGGREGATE_ENABLED.get());
            GlobalIlluminationConfig.AGGREGATE_ENABLED.save(); button.setMessage(aggregateLabel());
        }).bounds(width/2-130,83,128,20).build());
        addRenderableWidget(Button.builder(multiplierLabel(),button->{
            double current=GlobalIlluminationConfig.AGGREGATE_MULTIPLIER.get();
            double next=current<1.5?1.5:current<2?2:current<3?3:current<4?4:1;
            GlobalIlluminationConfig.AGGREGATE_MULTIPLIER.set(next); GlobalIlluminationConfig.AGGREGATE_MULTIPLIER.save();
            button.setMessage(multiplierLabel());
        }).bounds(width/2+2,83,128,20).build());
        addRenderableWidget(Button.builder(budgetLabel(),button->{
            double current=GlobalIlluminationConfig.AGGREGATE_BUDGET_MILLIS.get();
            double next=current<1?1:current<2?2:.35;
            GlobalIlluminationConfig.AGGREGATE_BUDGET_MILLIS.set(next);
            GlobalIlluminationConfig.AGGREGATE_CELLS_PER_TICK.set(next<1?2048:next<2?8192:16384);
            GlobalIlluminationConfig.AGGREGATE_BUDGET_MILLIS.save(); GlobalIlluminationConfig.AGGREGATE_CELLS_PER_TICK.save();
            button.setMessage(budgetLabel());
        }).bounds(width/2-130,107,128,20).build());
        addRenderableWidget(Button.builder(uploadLabel(),button->{
            double current=GlobalIlluminationConfig.LIGHT_UPLOAD_MILLIS.get();
            double next=current<.5?.5:current<1?1:current<2?2:.25;
            GlobalIlluminationConfig.LIGHT_UPLOAD_MILLIS.set(next);GlobalIlluminationConfig.LIGHT_UPLOAD_MILLIS.save();button.setMessage(uploadLabel());
        }).bounds(width/2+2,107,128,20).build());
        addRenderableWidget(Button.builder(speedLabel(),button->{
            var current=LightingBudgets.current();LightingBudgets.values()[current==null?1:(current.ordinal()+1)%LightingBudgets.values().length].apply();
            rebuildWidgets();
        }).bounds(width/2-130,131,260,20).build());
        addRenderableWidget(Button.builder(exposureLabel(),button->{
            double current=GlobalIlluminationConfig.BLOCK_LIGHT_EXPOSURE.get();
            GlobalIlluminationConfig.BLOCK_LIGHT_EXPOSURE.set(current<1?1:current<1.5?1.5:current<2?2:current<3?3:current<4?4:.75);
            GlobalIlluminationConfig.BLOCK_LIGHT_EXPOSURE.save();button.setMessage(exposureLabel());
        }).bounds(width/2-130,155,128,20).build());
        addRenderableWidget(Button.builder(cacheLabel(),button->{
            int current=GlobalIlluminationConfig.SOURCE_CACHE_MIB.get();
            GlobalIlluminationConfig.SOURCE_CACHE_MIB.set(current<32?32:current<64?64:current<128?128:current<256?256:current<512?512:0);
            GlobalIlluminationConfig.SOURCE_CACHE_MIB.save();button.setMessage(cacheLabel());
        }).bounds(width/2+2,155,128,20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(width/2-100,height-27,200,20).build());
    }
    private Component distanceLabel() {
        int chunks = GlobalIlluminationConfig.LIGHT_RENDER_DISTANCE.get();
        Component value = chunks == 0 ? Component.translatable("screen.openlights.render_distance")
                : Component.translatable("screen.openlights.chunks", chunks);
        return Component.translatable("screen.openlights.light_distance", value);
    }
    private Component styleLabel(){return Component.translatable("screen.openlights.style",Component.translatable("screen.openlights.style."+GlobalIlluminationConfig.BLOCK_LIGHT_STYLE.get().name().toLowerCase(java.util.Locale.ROOT)));}
    private Component speedLabel(){var current=LightingBudgets.current();return Component.translatable("screen.openlights.speed",Component.translatable("screen.openlights.speed."+(current==null?"custom":current.name().toLowerCase(java.util.Locale.ROOT))));}
    private Component aggregateLabel() { return Component.translatable("screen.openlights.aggregate",Component.translatable(GlobalIlluminationConfig.AGGREGATE_ENABLED.get()?"options.on":"options.off")); }
    private Component multiplierLabel() { return Component.translatable("screen.openlights.aggregate_range",GlobalIlluminationConfig.AGGREGATE_MULTIPLIER.get()); }
    private Component budgetLabel() { return Component.translatable("screen.openlights.aggregate_budget",GlobalIlluminationConfig.AGGREGATE_BUDGET_MILLIS.get()); }
    private Component uploadLabel() { return Component.translatable("screen.openlights.upload_budget",GlobalIlluminationConfig.LIGHT_UPLOAD_MILLIS.get()); }
    private Component exposureLabel(){return Component.translatable("screen.openlights.exposure",GlobalIlluminationConfig.BLOCK_LIGHT_EXPOSURE.get());}
    private Component cacheLabel(){return Component.translatable("screen.openlights.source_cache",GlobalIlluminationConfig.SOURCE_CACHE_MIB.get());}
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font,title,width/2,15,0xffffff);
        int y = 183;
        for (var line : font.split(Component.translatable("screen.openlights.aggregate_help"),260)) {
            graphics.drawString(font,line,width/2-130,y,0xaaaaaa); y += 11;
        }
        super.render(graphics,mouseX,mouseY,partialTick);
    }
}
