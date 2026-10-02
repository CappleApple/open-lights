package com.cappleapple.openlights.qa;

import com.cappleapple.openlights.config.ClientConfig;
import com.cappleapple.openlights.client.render.OpenLightRenderer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

/** Real client renders; never packaged. Every stage saves an image for human visual inspection. */
@EventBusSubscriber(modid = "openlightsqa", value = Dist.CLIENT)
public final class LightingVisualHarness {
    private static int stage = -1, ticks;
    private static boolean loading, prepared;
    private static boolean warming;
    private static int warmTicks;
    private static volatile boolean ready;
    private static final String[] NAMES = {"midday-native", "midday-cached", "texture-emission", "texture-transmission", "texture-source-change", "texture-glass-pack", "texture-emissive-mask", "render-distance", "texture-dark-filter", "cache-moving", "cache-resized", "cache-teleport"};
    private static java.util.List<String> originalPacks;
    private static String visualPack;
    private static float[] nightSky;
    private static int ownedFrames, incompleteFrames;
    private static final java.util.Set<Integer> firstCaptures = new java.util.HashSet<>();

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void ownership(net.neoforged.neoforge.client.event.RenderLevelStageEvent e) {
        if (!Boolean.getBoolean("openlights.visualSmoke")
                || ClientConfig.LIGHTING_MODE.get() != ClientConfig.LightingMode.CACHED) return;
        if (e.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            int texture = OpenLightRenderer.worldLightmap();
            int bound = com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(2);
            if (!OpenLightRenderer.cacheStatistics().replacement() || texture == 0
                    || (bound != 0 && bound != texture))
                throw new IllegalStateException("Global lighting handed geometry back to vanilla: stage=" + stage
                        + " expected=" + texture + " bound=" + bound + " cache=" + OpenLightRenderer.cacheStatistics());
        }
        if (e.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        // Forge dispatches AFTER_LEVEL from GameRenderer after LevelRenderer returns and restores the hand binding.
        if (!OpenLightRenderer.cacheStatistics().replacement())
            throw new IllegalStateException("Global composite disabled: stage=" + stage);
        ownedFrames++;
        var cache = OpenLightRenderer.cacheStatistics();
        if (cache.worldProbes() < cache.worldCapacity()) incompleteFrames++;
        if (stage > 0 && stage < NAMES.length && firstCaptures.add(stage)) {
            var mc = Minecraft.getInstance();
            Screenshot.grab(mc.gameDirectory, "visual-first-" + NAMES[stage] + ".png", mc.getMainRenderTarget(), m -> {});
            LogUtils.getLogger().info("VISUAL_FIRST_FRAME {} {}", NAMES[stage], cache);
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("openlights.visualSmoke")) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        mc.mouseHandler.releaseMouse();
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if (!loading && mc.screen instanceof TitleScreen title) {
            loading = true;
            mc.options.renderDistance().set(6); mc.options.simulationDistance().set(5);
            mc.options.framerateLimit().set(60); mc.options.hideGui = true;
            mc.options.fov().set(90);
            QaBootstrap.open(title);
        }
        if (!prepared && mc.player != null && mc.getSingleplayerServer() != null && mc.screen == null) {
            if (!warming) {
                warming = true;
                var playerId = mc.player.getUUID();
                mc.getSingleplayerServer().execute(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                    player.setGameMode(GameType.CREATIVE);
                    player.teleportTo(player.serverLevel(),131.5,174,124.5,0,24);
                    player.getAbilities().flying = true; player.onUpdateAbilities();
                });
                return;
            }
            // Edit only after the destination's normal chunk/light pipeline is active.
            if (mc.player.getX() < 116 || mc.player.getZ() < 120 || ++warmTicks < 80) return;
            prepared = true;
            originalPacks = java.util.List.copyOf(mc.getResourcePackRepository().getSelectedIds());
            var id = mc.player.getUUID();
            ClientConfig.GI_ENABLED.set(false); ClientConfig.PERIODIC_CACHE_REFRESH.set(false);
            ClientConfig.LIGHTING_MODE.set(ClientConfig.LightingMode.ADDITIVE);
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                var world = player.serverLevel();
                world.setDayTime(6000); world.setWeatherParameters(100000, 0, false, false);
                world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, mc.getSingleplayerServer());
                world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, mc.getSingleplayerServer());
                player.setGameMode(GameType.CREATIVE); player.getInventory().clearContent();
                for (int x = 116; x <= 148; x++) for (int z = 120; z <= 158; z++) {
                    for (int y = 159; y <= 172; y++) {
                        int top = 160 + Math.max(0, (z - 137) / 3) + (x > 137 ? 2 : 0);
                        world.setBlock(new BlockPos(x,y,z), y <= top ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                player.teleportTo(world, 131.5, 164, 124.5, 0, 24);
                player.getAbilities().flying = true; player.onUpdateAbilities();
                ready = true;
            });
            stage = 0; ticks = 0;
        }
        if (ready) ticks++;
        if (stage == 9 && ready && ticks % 5 == 0 && ticks < 80) {
            var id = mc.player.getUUID(); double x = 131.5 + 12 * Math.sin(ticks * .12);
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                player.teleportTo(player.serverLevel(), x, 174, 124.5, 0, 24);
            });
        }
        if (stage >= NAMES.length && ticks > 30) mc.stop();
        if (ticks > 600 && stage < NAMES.length) throw new IllegalStateException("Visual stage timed out: " + stage);
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (!Boolean.getBoolean("openlights.visualSmoke") || !ready || stage < 0 || stage >= NAMES.length || ticks < 100) return;
        var mc = Minecraft.getInstance();
        if (mc.screen != null || (stage >= 1 && !OpenLightRenderer.cacheStatistics().replacement())) return;
        if (stage <= 1) {
            for (var pos : new BlockPos[]{new BlockPos(124,161,132),new BlockPos(132,161,132),new BlockPos(140,163,132)})
                if (mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,pos) != 15) return;
        }
        if (stage == 7) assertCoverage();
        if (stage == 0) TextureUploadRegression.run();
        if (stage >= 2 && stage <= 8) assertPalette();
        Screenshot.grab(mc.gameDirectory, "visual-" + NAMES[stage] + ".png", mc.getMainRenderTarget(), m -> {});
        LogUtils.getLogger().info("VISUAL_CAPTURE {} {}", NAMES[stage], OpenLightRenderer.cacheStatistics());
        stage++; ticks = 0;
        if (stage == 1) ClientConfig.LIGHTING_MODE.set(ClientConfig.LightingMode.CACHED);
        else if (stage >= 2 && stage <= 4) prepareColors(stage);
        else if (stage == 5) installTexturePack();
        else if (stage == 6) installEmissiveMask();
        else if (stage == 7) {
            mc.options.renderDistance().set(10);
            ClientConfig.GI_ENABLED.set(true); ClientConfig.GI_PROBES_PER_TICK.set(64); ClientConfig.GI_BUDGET_MILLIS.set(8.0);
        } else if (stage == 8) {
            ClientConfig.GI_ENABLED.set(false);
            ready = false;
            mc.getSingleplayerServer().execute(() -> {
                for (int x = 130; x <= 134; x++) for (int y = 161; y <= 165; y++)
                    mc.getSingleplayerServer().overworld().setBlock(new BlockPos(x,y,138), Blocks.BLACK_STAINED_GLASS.defaultBlockState(), 3);
                ready = true;
            });
        } else if (stage == 9) {
            ClientConfig.WORLD_SAMPLES_PER_TICK.set(128);
            ready = false;
            var id = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                player.serverLevel().setDayTime(6000);
                player.teleportTo(player.serverLevel(), 131.5, 174, 124.5, 0, 24);
                ready = true;
            });
        } else if (stage == 10) {
            ClientConfig.WORLD_GRID_SIZE.set(65); mc.options.renderDistance().set(6);
        } else if (stage == 11) {
            ready = false;
            var id = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                int ground = player.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 1155, 124);
                player.teleportTo(player.serverLevel(), 1155.5, ground + 12, 124.5, 0, 40);
                ready = true;
            });
        } else {
            if (incompleteFrames < 100) throw new IllegalStateException("Incomplete-cache ownership was not exercised");
            LogUtils.getLogger().info("CONTINUOUS_OWNERSHIP_SUCCESS frames={} incompleteCacheFrames={}", ownedFrames, incompleteFrames);
            ClientConfig.WORLD_GRID_SIZE.set(33); ClientConfig.WORLD_SAMPLES_PER_TICK.set(4096);
            ClientConfig.GI_ENABLED.set(true); ClientConfig.GI_PROBES_PER_TICK.set(4); ClientConfig.GI_BUDGET_MILLIS.set(.5);
            LogUtils.getLogger().info("VISUAL_SUCCESS captures complete; inspect the images before claiming visual correctness");
            ready = false;
            mc.getResourcePackRepository().setSelected(originalPacks);
            mc.reloadResourcePacks().thenRun(() -> ready = true);
        }
    }

    private static void prepareColors(int next) {
        ready = false;
        var mc = Minecraft.getInstance(); var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id); var world = player.serverLevel();
            world.setDayTime(18000);
            if (next == 2) {
                for (int x = 119; x <= 145; x++) for (int z = 126; z <= 145; z++) for (int y = 160; y <= 172; y++)
                    world.setBlock(new BlockPos(x,y,z), y == 160 ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                for (int center : new int[]{124,132,140}) {
                    for (int x = center-3; x <= center+3; x++) for (int z = 130; z <= 145; z++) for (int y = 161; y <= 166; y++) {
                        if (x == center-3 || x == center+3 || z == 145 || y == 166)
                            world.setBlock(new BlockPos(x,y,z), Blocks.WHITE_CONCRETE.defaultBlockState(), 3);
                    }
                    var source = center == 124 ? Blocks.GLOWSTONE : center == 132 ? Blocks.SEA_LANTERN : Blocks.SOUL_LANTERN;
                    world.setBlock(new BlockPos(center,163,140), source.defaultBlockState(), 3);
                }
                player.teleportTo(world,132.5,162.5,125.5,0,7);
                player.getAbilities().flying = true; player.onUpdateAbilities();
            } else if (next == 3) {
                for (int x = 130; x <= 134; x++) for (int y = 161; y <= 165; y++)
                    world.setBlock(new BlockPos(x,y,138), Blocks.BLUE_STAINED_GLASS.defaultBlockState(), 3);
            } else world.setBlock(new BlockPos(124,163,140), Blocks.SEA_LANTERN.defaultBlockState(), 3);
            ready = true;
        });
    }

    private static void installTexturePack() {
        var mc = Minecraft.getInstance();
        visualPack = "openlights_visual_" + System.nanoTime();
        var root = mc.gameDirectory.toPath().resolve("resourcepacks").resolve(visualPack);
        try {
            var textures = root.resolve("assets/minecraft/textures/block");
            java.nio.file.Files.createDirectories(textures);
            java.nio.file.Files.writeString(root.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":34,\"description\":\"Disposable Open Lights texture-color visual fixture\"}}");
            try (var glass = new com.mojang.blaze3d.platform.NativeImage(16,16,false)) {
                glass.fillRect(0,0,16,16,0x8030ff30);
                glass.writeToFile(textures.resolve("blue_stained_glass.png"));
            }
            ready = false;
            mc.getResourcePackRepository().reload();
            var selected = new java.util.ArrayList<>(originalPacks); selected.add("file/" + visualPack);
            mc.getResourcePackRepository().setSelected(selected);
            mc.reloadResourcePacks().thenRun(() -> ready = true);
        } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }

    private static void installEmissiveMask() {
        var mc = Minecraft.getInstance();
        try (var mask = new com.mojang.blaze3d.platform.NativeImage(16,16,false)) {
            mask.fillRect(0,0,16,16,0); mask.fillRect(4,4,8,8,0xffff20ff);
            mask.writeToFile(mc.gameDirectory.toPath().resolve("resourcepacks").resolve(visualPack)
                    .resolve("assets/minecraft/textures/block/sea_lantern_e.png"));
            ready = false;
            mc.reloadResourcePacks().thenRun(() -> ready = true);
        } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }

    private static void assertCoverage() {
        try {
            var worldField = OpenLightRenderer.class.getDeclaredField("WORLD_LIGHT"); worldField.setAccessible(true);
            var giField = OpenLightRenderer.class.getDeclaredField("INDIRECT"); giField.setAccessible(true);
            var world = ((com.cappleapple.openlights.client.scene.WorldLightCache)worldField.get(null)).grid();
            var gi = ((com.cappleapple.openlights.client.scene.IndirectLightCache)giField.get(null)).far();
            var mc = Minecraft.getInstance(); var camera = mc.gameRenderer.getMainCamera().getPosition();
            int distance = mc.options.getEffectiveRenderDistance()*16;
            for (var grid : new com.cappleapple.openlights.client.scene.ProbeGrid[]{world,gi}) {
                int span = (grid.size-1)*grid.spacing;
                if (grid.minimumX() > camera.x-distance || grid.minimumX()+span < camera.x+distance
                        || grid.minimumY() > camera.y-distance || grid.minimumY()+span < camera.y+distance
                        || grid.minimumZ() > camera.z-distance || grid.minimumZ()+span < camera.z+distance)
                    throw new IllegalStateException("Cache does not cover render distance");
            }
            LogUtils.getLogger().info("VISUAL_COVERAGE worldSpacing={} farGiSpacing={} renderDistanceBlocks={}", world.spacing,gi.spacing,distance);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    private static void assertPalette() {
        int active = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        int binding = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
        try {
            var field = OpenLightRenderer.class.getDeclaredField("nativeLightmap"); field.setAccessible(true);
            int texture = field.getInt(null);
            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,texture);
            int width = org.lwjgl.opengl.GL11.glGetTexLevelParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,0,org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH);
            int height = org.lwjgl.opengl.GL11.glGetTexLevelParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,0,org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT);
            if (width != 16 || height != 16) throw new IllegalStateException("Native palette dimensions changed: " + width + "x" + height);
            float[] pixels = new float[16*16*4];
            org.lwjgl.opengl.GL11.glGetTexImage(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,0,org.lwjgl.opengl.GL11.GL_RGBA,org.lwjgl.opengl.GL11.GL_FLOAT,pixels);
            float[] sky = java.util.Arrays.copyOfRange(pixels,15*16*4,15*16*4+3);
            if (nightSky == null) nightSky = sky;
            else for (int i = 0; i < 3; i++) if (Math.abs(sky[i]-nightSky[i]) > .02)
                throw new IllegalStateException("Resource reload corrupted native sky palette: " + java.util.Arrays.toString(sky));
            LogUtils.getLogger().info("VISUAL_PALETTE id={} sky={}",texture,java.util.Arrays.toString(sky));
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
        finally {
            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,binding);
            org.lwjgl.opengl.GL13.glActiveTexture(active);
        }
    }
}
