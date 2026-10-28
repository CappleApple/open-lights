package com.cappleapple.openlights.content;

import com.cappleapple.openlights.OpenLightsMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;

/** Internal renderer particle type; the library registers no gameplay content. */
public final class ParticleContent {
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, OpenLightsMod.MOD_ID);
    public static final RegistryObject<SimpleParticleType> BEAM_DUST = PARTICLES.register("beam_dust", () -> new SimpleParticleType(false));
    private ParticleContent() {}
    public static void register(IEventBus bus) { PARTICLES.register(bus); }
}
