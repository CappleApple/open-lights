package com.cappleapple.openlights.client.scene;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Resource-pack texture summaries, rebuilt on reload. All access stays on the client thread. */
public final class TextureColors {
    private static final Vec3 WHITE = new Vec3(1,1,1);
    private static final Map<BlockState, List<Face>> MODELS = new IdentityHashMap<>();
    private static final Map<ResourceLocation, Summary> SPRITES = new HashMap<>();
    private static String emissiveSuffix;
    private record Face(Summary pixels, int tint) {}
    record Summary(Vec3 average, Vec3 emission, double score, boolean mask) {}

    public static Vec3 emission(ClientLevel world, BlockPos pos, BlockState state) {
        if (state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) return normalize(fluid("lava_still").emission);
        return color(world, pos, state, true);
    }
    public static Vec3 surface(ClientLevel world, BlockPos pos, BlockState state) { return color(world, pos, state, false); }
    public static Vec3 transmission(ClientLevel world, BlockPos pos, BlockState state) {
        // Retain absorption: normalizing a dark gray/black texture would make it pass white light.
        return surface(world, pos, state);
    }
    public static Vec3 water(ClientLevel world, BlockPos pos) {
        return normalize(fluid("water_still").average.multiply(rgb(net.minecraft.client.renderer.BiomeColors.getAverageWaterColor(world,pos))));
    }
    private static Summary fluid(String name) {
        return sprite(Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
                .apply(ResourceLocation.fromNamespaceAndPath("minecraft", "block/" + name)));
    }

    private static Vec3 color(ClientLevel world, BlockPos pos, BlockState state, boolean emission) {
        List<Face> faces = MODELS.computeIfAbsent(state, TextureColors::model);
        boolean masked = emission && faces.stream().anyMatch(f -> f.pixels.mask);
        double best = faces.stream().filter(f -> !masked || f.pixels.mask).mapToDouble(f -> f.pixels.score).max().orElse(0);
        Vec3 sum = Vec3.ZERO; double weight = 0;
        for (Face face : faces) {
            if (masked && !face.pixels.mask) continue;
            if (emission && face.pixels.score < best * .65) continue;
            Vec3 value = emission ? face.pixels.emission : face.pixels.average;
            if (face.tint >= 0) {
                int tint = Minecraft.getInstance().getBlockColors().getColor(state, world, pos, face.tint);
                if (tint != -1) value = value.multiply(rgb(tint));
            }
            double w = emission ? Math.max(.001, face.pixels.score) : 1;
            sum = sum.add(value.scale(w)); weight += w;
        }
        Vec3 value = weight > 0 ? sum.scale(1 / weight) : WHITE;
        return emission ? normalize(value) : value;
    }

    private static List<Face> model(BlockState state) {
        var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        var result = new ArrayList<Face>();
        Set<String> seen = new HashSet<>();
        var random = RandomSource.create(0);
        for (int face = 0; face < 7; face++) {
            random.setSeed(0);
            for (BakedQuad quad : model.getQuads(state, face == 6 ? null : Direction.values()[face], random)) {
                String key = quad.getSprite().contents().name() + ":" + quad.getTintIndex();
                if (seen.add(key)) result.add(new Face(sprite(quad.getSprite()), quad.getTintIndex()));
            }
        }
        if (result.isEmpty()) result.add(new Face(sprite(model.getParticleIcon()), -1));
        return List.copyOf(result);
    }

    private static Summary sprite(TextureAtlasSprite sprite) {
        return SPRITES.computeIfAbsent(sprite.contents().name(), id -> {
            if (id.getPath().equals("missingno")) return new Summary(WHITE, WHITE, 0, false);
            var contents = sprite.contents();
            var pixels = new ArrayList<Integer>();
            // Average a bounded sample of animation frames; animation does not invalidate light every frame.
            contents.getUniqueFrames().limit(4).forEach(frame -> {
                int stepX = Math.max(1, contents.width() / 16), stepY = Math.max(1, contents.height() / 16);
                for (int y = 0; y < contents.height(); y += stepY) for (int x = 0; x < contents.width(); x += stepX)
                    pixels.add(sprite.getPixelRGBA(frame, x, y));
            });
            Summary base = summarize(pixels, false);
            var resources = Minecraft.getInstance().getResourceManager();
            var mask = resources.getResource(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath() + suffix() + ".png"));
            if (mask.isPresent()) try (var stream = mask.get().open(); var image = NativeImage.read(stream)) {
                pixels.clear();
                for (int y = 0; y < image.getHeight(); y += Math.max(1, image.getHeight() / 32))
                    for (int x = 0; x < image.getWidth(); x += Math.max(1, image.getWidth() / 32)) pixels.add(image.getPixelRGBA(x,y));
                Summary glow = summarize(pixels, true);
                if (glow.score > 0) return new Summary(base.average, glow.emission, glow.score, true);
            } catch (java.io.IOException ignored) { /* Invalid optional masks use the visible texture. */ }
            return base;
        });
    }

    private static String suffix() {
        if (emissiveSuffix != null) return emissiveSuffix;
        emissiveSuffix = "_e";
        var resource = Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("minecraft", "optifine/emissive.properties"));
        if (resource.isPresent()) try (var stream = resource.get().open()) {
            Properties properties = new Properties(); properties.load(stream);
            String suffix = properties.getProperty("suffix.emissive", "_e");
            if (suffix.matches("[a-z0-9_]+")) emissiveSuffix = suffix;
        } catch (java.io.IOException ignored) {}
        return emissiveSuffix;
    }

    /** ABGR pixels, matching NativeImage. Bright saturated pixels outrank dark housings. */
    static Summary summarize(List<Integer> pixels, boolean mask) {
        Vec3 average = Vec3.ZERO, glow = Vec3.ZERO; double count = 0, total = 0, best = 0;
        for (int pixel : pixels) if ((pixel >>> 24) > 0) best = Math.max(best, score(pixel));
        for (int pixel : pixels) {
            double alpha = (pixel >>> 24) / 255.0;
            if (alpha == 0) continue;
            Vec3 rgb = new Vec3((pixel & 255) / 255.0, (pixel >> 8 & 255) / 255.0, (pixel >> 16 & 255) / 255.0);
            average = average.add(rgb.scale(alpha)); count += alpha;
            double score = score(pixel);
            if (mask || score >= best * .75) {
                double weight = alpha * score * score;
                glow = glow.add(rgb.scale(weight)); total += weight;
            }
        }
        return new Summary(count > 0 ? average.scale(1/count) : WHITE,
                total > 0 ? glow.scale(1/total) : WHITE, best, mask);
    }
    private static double score(int pixel) {
        double r = (pixel & 255) / 255.0, g = (pixel >> 8 & 255) / 255.0, b = (pixel >> 16 & 255) / 255.0;
        double max = Math.max(r, Math.max(g,b)), min = Math.min(r, Math.min(g,b));
        return max * (.3 + .7 * (max - min));
    }
    static Vec3 normalize(Vec3 value) {
        double max = Math.max(value.x, Math.max(value.y, value.z));
        return max < .001 ? WHITE : new Vec3(Math.max(.015, value.x/max), Math.max(.015, value.y/max), Math.max(.015, value.z/max));
    }
    private static Vec3 rgb(int color) { return new Vec3((color >> 16 & 255)/255.0, (color >> 8 & 255)/255.0, (color & 255)/255.0); }
    public static void clear() { MODELS.clear(); SPRITES.clear(); emissiveSuffix = null; }
    private TextureColors() {}
}
