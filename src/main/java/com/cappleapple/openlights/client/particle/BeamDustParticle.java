package com.cappleapple.openlights.client.particle;

import com.cappleapple.openlights.api.client.LightDefinition;
import com.cappleapple.openlights.api.client.LightKey;
import com.cappleapple.openlights.beam.BeamProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;

/** A world-space mote whose illumination follows its owning beam. */
final class BeamDustParticle extends TextureSheetParticle {
    private LightKey owner;

    BeamDustParticle(ClientLevel level, double x, double y, double z,
                     double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.hasPhysics = true;
        this.gravity = 0;
        this.friction = 1;
        this.alpha = 0;
        this.lifetime = 1;
        pickSprite(sprites);
    }

    void attach(LightKey key, BeamProfile.Dust dust) {
        this.owner = key;
        this.lifetime = dust.lifetimeTicks();
        this.quadSize = dust.size() * .5f;
        setSize(dust.size(), dust.size());
        illuminate();
    }

    @Override public void tick() {
        super.tick();
        if (isAlive()) illuminate();
    }

    private void illuminate() {
        LightDefinition.Spot beam = BeamDustParticles.beam(owner);
        if (beam == null) { remove(); return; }
        Vec3 color = BeamDustSampling.color(beam, new Vec3(x, y, z), BeamDustParticles.intensityMultiplier());
        double brightness = Math.max(color.x, Math.max(color.y, color.z));
        if (brightness <= 0.0001) { remove(); return; }
        double normalization = brightness;
        this.rCol = (float) (color.x / normalization);
        this.gCol = (float) (color.y / normalization);
        this.bCol = (float) (color.z / normalization);
        float fadeIn = Math.min(1, (age + 1) / 4.0f);
        float fadeOut = Math.min(1, (lifetime - age) / 6.0f);
        // Visibility increases gently with illumination; distant motes retain
        // their configured physical size rather than shrinking to subpixels.
        this.alpha = (float) Math.min(.8, Math.sqrt(brightness) * .65) * fadeIn * fadeOut;
    }

    @Override public void remove() {
        super.remove();
        BeamDustParticles.removed(this);
    }

    @Override protected int getLightColor(float partialTick) { return LightTexture.FULL_BRIGHT; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
}
