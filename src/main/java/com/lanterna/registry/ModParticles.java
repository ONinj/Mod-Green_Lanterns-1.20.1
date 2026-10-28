package com.lanterna.registry;

import com.lanterna.LanternaMod;
import com.lanterna.particle.GlowOrbOptions;
import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, LanternaMod.MODID);

    /** Bolinha de energia do rastro de voo. */
    public static final RegistryObject<ParticleType<GlowOrbOptions>> GLOW_ORB = PARTICLES.register("glow_orb",
            () -> new ParticleType<>(false, GlowOrbOptions.DESERIALIZER) {
                @Override
                public Codec<GlowOrbOptions> codec() {
                    return GlowOrbOptions.CODEC;
                }
            });

    private ModParticles() {}
}
