package com.cappleapple.openlights.content;

import com.cappleapple.openlights.OpenLightsMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.Registries;

/** Internal renderer particle type; the library registers no gameplay content. */
public final class ParticleContent {
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, OpenLightsMod.MOD_ID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BEAM_DUST = PARTICLES.register("beam_dust", () -> new SimpleParticleType(false));
    private ParticleContent() {}
    public static void register(IEventBus bus) { PARTICLES.register(bus); }
}
