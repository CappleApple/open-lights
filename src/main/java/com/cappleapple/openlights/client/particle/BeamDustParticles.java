package com.cappleapple.openlights.client.particle;

import com.cappleapple.openlights.api.client.LightDefinition;
import com.cappleapple.openlights.api.client.LightKey;
import com.cappleapple.openlights.client.render.OpenLightRenderer;
import com.cappleapple.openlights.content.ModContent;
import com.cappleapple.openlights.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;

import java.util.*;

/** Client-tick scheduler. Beam motes never create light sources or network packets. */
public final class BeamDustParticles {
    private static final int MAX_ACTIVE = 256, MAX_ATTEMPTS_PER_TICK = 8;
    private static final Map<LightKey, LightDefinition.Spot> BEAMS = new LinkedHashMap<>();
    private static final Map<LightKey, Double> CREDIT = new HashMap<>();
    private static final Set<BeamDustParticle> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());
    private static ClientLevel world;
    private static int nextBeam;
    private static long totalSpawned;
    private static double intensityMultiplier = 1;

    private BeamDustParticles() {}

    public static void registerProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModContent.BEAM_DUST.get(), sprites ->
                (type, level, x, y, z, dx, dy, dz) -> new BeamDustParticle(level, x, y, z, dx, dy, dz, sprites));
    }

    /** Call once at the end of an unpaused client tick, on the client thread. */
    public static void tick(boolean enabled) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != world) { clear(); world = mc.level; }
        intensityMultiplier = ClientConfig.INTENSITY_MULTIPLIER.get();
        if (!enabled || intensityMultiplier <= 0 || world == null || mc.options.particles().get() == ParticleStatus.MINIMAL) {
            clear();
            return;
        }
        BEAMS.clear();
        OpenLightRenderer.frameLights().forEach((key, light) -> {
            if (light instanceof LightDefinition.Spot spot && spot.beamProfile() != null
                    && spot.beamProfile().dust().rate() > 0 && spot.beamProfile().dust().size() > 0) {
                BEAMS.put(key, spot);
            }
        });
        CREDIT.keySet().retainAll(BEAMS.keySet());
        // Also handles particles removed by Minecraft's own queue limits.
        ACTIVE.removeIf(particle -> !particle.isAlive());
        double density = mc.options.particles().get() == ParticleStatus.DECREASED ? .25 : 1;
        BEAMS.forEach((key, beam) -> CREDIT.merge(key, beam.beamProfile().dust().rate() * density / 20,
                (previous, amount) -> Math.min(4, previous + amount)));
        if (BEAMS.isEmpty() || ACTIVE.size() >= MAX_ACTIVE) return;

        List<Map.Entry<LightKey, LightDefinition.Spot>> candidates = new ArrayList<>(BEAMS.entrySet());
        int attempts = 0, skipped = 0;
        while (attempts < MAX_ATTEMPTS_PER_TICK && ACTIVE.size() < MAX_ACTIVE && skipped < candidates.size()) {
            var candidate = candidates.get(Math.floorMod(nextBeam++, candidates.size()));
            double credit = CREDIT.getOrDefault(candidate.getKey(), 0.0);
            if (credit < 1) { skipped++; continue; }
            skipped = 0;
            CREDIT.put(candidate.getKey(), credit - 1);
            attempts++;
            spawn(mc, candidate.getKey(), candidate.getValue());
        }
    }

    private static void spawn(Minecraft mc, LightKey key, LightDefinition.Spot beam) {
        var profile = beam.beamProfile();
        var dust = profile.dust();
        RandomSource random = world.random;
        double range = Math.min(beam.range(), Math.max(profile.inner().range(), profile.outer().range()));
        // Uniform radial distance keeps motes near the visible emitter in small rooms;
        // sampling uniformly by volume would spend most attempts behind the far wall.
        double distance = range * (.01 + .99 * random.nextDouble());
        double edge = Math.cos(Math.toRadians(beam.outerConeAngleDegrees() * .5));
        double cosine = edge + random.nextDouble() * (1 - edge);
        double sine = Math.sqrt(Math.max(0, 1 - cosine * cosine));
        double rotation = random.nextDouble() * Math.PI * 2;
        Vec3 right = beam.forward().cross(beam.up());
        Vec3 position = beam.position().add(beam.forward().scale(distance * cosine))
                .add(right.scale(distance * sine * Math.cos(rotation)))
                .add(beam.up().scale(distance * sine * Math.sin(rotation)));
        if (BeamDustSampling.color(beam, position, intensityMultiplier).lengthSqr() < .00000001
                || !BeamDustOcclusion.clear(world, beam.position(), position)) return;
        double drift = dust.speed();
        var created = mc.particleEngine.createParticle(ModContent.BEAM_DUST.get(), position.x, position.y, position.z,
                (random.nextDouble() * 2 - 1) * drift, (random.nextDouble() * 2 - 1) * drift,
                (random.nextDouble() * 2 - 1) * drift);
        if (created instanceof BeamDustParticle particle) {
            ACTIVE.add(particle);
            particle.attach(key, dust);
            if (particle.isAlive()) totalSpawned++;
        }
    }

    static double intensityMultiplier() { return intensityMultiplier; }
    static LightDefinition.Spot beam(LightKey key) { return BEAMS.get(key); }
    static void removed(BeamDustParticle particle) { ACTIVE.remove(particle); }

    /** Remove existing motes and reset per-world counters on disconnect or world unload. */
    public static void clear() {
        for (BeamDustParticle particle : new ArrayList<>(ACTIVE)) particle.remove();
        ACTIVE.clear();
        BEAMS.clear();
        CREDIT.clear();
        world = null;
        nextBeam = 0;
        totalSpawned = 0;
    }

    public static int activeCount() { return ACTIVE.size(); }
    public static long totalSpawned() { return totalSpawned; }
}
