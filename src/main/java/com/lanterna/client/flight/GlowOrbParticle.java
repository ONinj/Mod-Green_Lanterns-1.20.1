package com.lanterna.client.flight;

import com.lanterna.particle.GlowOrbOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import org.jetbrains.annotations.Nullable;

/**
 * Bolinha de energia: sempre acesa (brilha até no escuro), na cor da Tropa,
 * vai diminuindo e sumindo. É o "rastro molecular" do voo.
 */
public class GlowOrbParticle extends TextureSheetParticle {

    private final float baseSize;

    protected GlowOrbParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                              GlowOrbOptions options, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.rCol = options.r();
        this.gCol = options.g();
        this.bCol = options.b();
        this.baseSize = options.size();
        this.quadSize = baseSize;
        this.lifetime = 12 + random.nextInt(10);
        this.hasPhysics = false;
        this.gravity = 0;
        this.friction = 0.86f;
        this.alpha = 0.9f;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float t = (float) age / lifetime;
        this.alpha = 0.9f * (1f - t * t);
        this.quadSize = baseSize * (1f - t * 0.7f);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0; // sempre aceso
    }

    public static class Provider implements ParticleProvider<GlowOrbOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Nullable
        @Override
        public Particle createParticle(GlowOrbOptions options, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new GlowOrbParticle(level, x, y, z, dx, dy, dz, options, sprites);
        }
    }
}
