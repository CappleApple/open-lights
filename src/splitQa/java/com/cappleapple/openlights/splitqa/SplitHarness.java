package com.cappleapple.openlights.splitqa;

import com.cappleapple.openlights.api.client.LightDefinition;
import com.cappleapple.openlights.api.client.LightHandle;
import com.cappleapple.openlights.api.client.OpenLightsApi;
import com.cappleapple.openlights.client.render.OpenLightRenderer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

/** Packaged smoke helper: compiled against the library, with no addon class references. */
@Mod.EventBusSubscriber(modid="openlights_split_qa", value=Dist.CLIENT)
public final class SplitHarness {
    private static final boolean EXPECT_GI = Boolean.getBoolean("openlights.splitGi");
    private static final boolean EXPECT_FLASHLIGHT = Boolean.getBoolean("openlights.splitFlashlight");
    private static boolean loading, started, finished;
    private static int ticks, bootTicks;
    private static LightHandle handle;
    private static java.util.concurrent.Future<?> serverTask;
    private static int chargedEnergy;
    private static long heldAt;
    private static int maximumShadowPasses;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        mc.mouseHandler.releaseMouse();
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        mc.options.pauseOnLostFocus = false;
        if (++bootTicks == 1 || bootTicks % 200 == 0) {
            LogUtils.getLogger().info("SPLIT_PROGRESS screen={} loading={} started={} ticks={}",
                    mc.screen == null ? "none" : mc.screen.getClass().getName(), loading, started, ticks);
        }
        try {
            if (!loading && mc.screen instanceof TitleScreen title) {
                loading = true;
                mc.options.renderDistance().set(4);
                mc.options.framerateLimit().set(60);
                var rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                mc.createWorldOpenFlows().createFreshLevel("SplitSmokeWorld",
                        new LevelSettings("Disposable Open Lights split QA", GameType.SURVIVAL, false,
                                Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(187L, false, false),
                        registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions());
            }
            if (!started && mc.player != null && mc.screen == null && mc.getSingleplayerServer() != null) {
                started = true;
                mc.options.hideGui = true;
                var uuid = mc.player.getUUID();
                serverTask = mc.getSingleplayerServer().submit(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
                    var level = player.serverLevel();
                    level.setDayTime(18000);
                    for (int x = -8; x <= 8; x++) for (int z = -8; z <= 12; z++) {
                        level.setBlock(new BlockPos(x, -60, z), Blocks.STONE.defaultBlockState(), 3);
                        for (int y = -59; y <= -54; y++) {
                            level.setBlock(new BlockPos(x, y, z),
                                    z == 12 ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                    player.teleportTo(level, 0.5, -59, 0.5, 0, 8);
                });
            }
            if (!started || finished) return;
            ticks++;
            checkTask();
            if (ticks == 140) {
                require(serverTask == null || serverTask.isDone(), "World setup did not finish");
                require(OpenLightRenderer.class.getMethod("hasWorldLightingExtension").invoke(null).equals(EXPECT_GI),
                        "GI extension presence did not match installed addons");
                require(BuiltInRegistries.ITEM.containsKey(id("openlights", "flashlight")) == EXPECT_FLASHLIGHT,
                        "Flashlight registry entry did not match installed addons");
                if (!EXPECT_GI) {
                    var cache = OpenLightRenderer.cacheStatistics();
                    require(!cache.replacement() && cache.worldProbes() == 0 && cache.giProbes() == 0
                            && cache.aggregateCells() == 0 && !cache.aggregateWorker(),
                            "Library performed world lighting work while idle: " + cache);
                    try {
                        Class.forName("com.cappleapple.openlights.gi.client.scene.WorldLightCache", false, SplitHarness.class.getClassLoader());
                        throw new IllegalStateException("GI implementation was packaged without the GI addon");
                    } catch (ClassNotFoundException expected) { }
                }
                require(OpenLightRenderer.frameLights().isEmpty(), "Library added an unsolicited light");
                if (EXPECT_GI) {
                    require(OpenLightRenderer.cacheStatistics().replacement()
                            && OpenLightRenderer.cacheStatistics().worldProbes() > 0,
                            "Installed GI addon did not populate cached world lighting");
                }
                LogUtils.getLogger().info("SPLIT_IDLE_VERIFIED gi={} flashlight={} cache={}",
                        EXPECT_GI, EXPECT_FLASHLIGHT, OpenLightRenderer.cacheStatistics());
                handle = OpenLightsApi.create(id("openlights_split_qa", "test"),
                        new LightDefinition.Point(mc.player.getEyePosition().add(0, 0, 4),
                                new Vec3(1, 0.65, 0.25), 4, 16, false, 0));
            }
            if (ticks == 220) {
                require(handle.isValid() && OpenLightRenderer.frameLights().containsKey(handle.key()),
                        "API light was not collected");
                require(OpenLightRenderer.statistics().lights() > 0, "API light was not rendered");
                Screenshot.grab(mc.gameDirectory, "split-api.png", mc.getMainRenderTarget(), message -> { });
                LogUtils.getLogger().info("SPLIT_API_VERIFIED statistics={}", OpenLightRenderer.statistics());
                handle.close();
            }
            if (ticks == 250 && EXPECT_FLASHLIGHT) {
                Screenshot.grab(mc.gameDirectory, "split-flashlight-off.png", mc.getMainRenderTarget(), message -> { });
            }
            if (ticks == 260) {
                require(!OpenLightRenderer.frameLights().containsKey(handle.key()), "Closed API light remained active");
                if (EXPECT_FLASHLIGHT) beginFlashlight(mc);
            }
            if (ticks == 340 && EXPECT_FLASHLIGHT) {
                require(serverTask == null || serverTask.isDone(), "Flashlight setup did not finish");
                require(OpenLightRenderer.frameLights().keySet().stream().anyMatch(key -> key.owner().getPath().contains("handheld")),
                        "Charged handheld flashlight was not collected");
                require(OpenLightRenderer.statistics().lights() > 0 && maximumShadowPasses > 0,
                        "Flashlight did not render a light and a shadow pass");
                Screenshot.grab(mc.gameDirectory, "split-flashlight.png", mc.getMainRenderTarget(), message -> { });
                LogUtils.getLogger().info("SPLIT_FLASHLIGHT_CAPTURE lights={} statistics={} maximumShadowPasses={}",
                        OpenLightRenderer.frameLights(), OpenLightRenderer.statistics(), maximumShadowPasses);
            }
            if (ticks == 355 && EXPECT_FLASHLIGHT) {
                finishFlashlight(mc);
            }
            if (ticks == 390 && EXPECT_FLASHLIGHT) {
                require(OpenLightRenderer.frameLights().isEmpty(), "Depleted flashlight remained collected");
                Screenshot.grab(mc.gameDirectory, "split-flashlight-depleted.png", mc.getMainRenderTarget(), message -> { });
            }
            if (ticks >= 420) {
                checkTask();
                require(serverTask == null || serverTask.isDone(), "Server assertions did not finish");
                finished = true;
                LogUtils.getLogger().info("SPLIT_SUCCESS gi={} flashlight={}", EXPECT_GI, EXPECT_FLASHLIGHT);
                mc.stop();
            }
        } catch (Throwable failure) {
            finished = true;
            LogUtils.getLogger().error("SPLIT_FAILURE", failure);
            mc.stop();
        }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL && ticks > 260 && EXPECT_FLASHLIGHT) {
            maximumShadowPasses = Math.max(maximumShadowPasses, OpenLightRenderer.statistics().shadowPasses());
        }
    }

    private static void beginFlashlight(Minecraft mc) {
        var uuid = mc.player.getUUID();
        serverTask = mc.getSingleplayerServer().submit(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
            var stack = new ItemStack(BuiltInRegistries.ITEM.get(id("openlights", "flashlight")));
            var storage = energy(stack);
            require(storage != null && storage.getEnergyStored() == 0, "New flashlight battery was not empty");
            require(storage.receiveEnergy(1000, true) > 0 && storage.getEnergyStored() == 0,
                    "Simulated charging mutated battery");
            require(storage.receiveEnergy(1000, false) > 0, "Battery rejected charging");
            require(storage.extractEnergy(1000, false) == 0 && !storage.canExtract(), "Flashlight battery allowed extraction");
            chargedEnergy = storage.getEnergyStored();
            require(energy(roundTrip(stack, player)).getEnergyStored() == chargedEnergy,
                    "Charge did not survive item serialization");
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            stack.getItem().use(player.serverLevel(), player, InteractionHand.MAIN_HAND);
            require(enabled(stack), "Charged flashlight did not toggle on");
            heldAt = player.tickCount;
            LogUtils.getLogger().info("SPLIT_CHARGE_VERIFIED energy={}", chargedEnergy);
        });
    }

    private static void finishFlashlight(Minecraft mc) {
        var uuid = mc.player.getUUID();
        serverTask = mc.getSingleplayerServer().submit(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
            var stack = player.getMainHandItem();
            int remaining = energy(stack).getEnergyStored();
            require(player.tickCount > heldAt && remaining < chargedEnergy && remaining > 0,
                    "Held flashlight failed to consume charge over server ticks");
            var restored = roundTrip(stack, player);
            require(energy(restored).getEnergyStored() == remaining && enabled(restored),
                    "Toggle/charge did not survive item serialization");
            for (int i = 0; i < 1100; i++) {
                stack.getItem().inventoryTick(stack, player.serverLevel(), player, player.getInventory().selected, true);
            }
            require(energy(stack).getEnergyStored() == 0 && !enabled(stack), "Exhausted flashlight remained on");
            stack.getItem().use(player.serverLevel(), player, InteractionHand.MAIN_HAND);
            require(!enabled(stack), "Empty flashlight could be turned on");
            LogUtils.getLogger().info("SPLIT_FLASHLIGHT_VERIFIED normalTicks={} remainingBeforeExhaustion={}",
                    player.tickCount - heldAt, remaining);
        });
    }

    private static boolean enabled(ItemStack stack) {
        try { return (boolean) stack.getItem().getClass().getMethod("isEnabled", ItemStack.class).invoke(null, stack); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static ItemStack roundTrip(ItemStack stack, ServerPlayer player) { return ItemStack.of(stack.save(new CompoundTag())); }
    private static IEnergyStorage energy(ItemStack stack) { return stack.getCapability(ForgeCapabilities.ENERGY).orElse(null); }
    private static ResourceLocation id(String namespace, String path) { return new ResourceLocation(namespace, path); }
    private static void checkTask() throws Exception {
        if (serverTask != null && serverTask.isDone()) serverTask.get();
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
