package com.lanterna.particle;

import com.lanterna.registry.ModParticles;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Locale;

/** Bolinha de energia brilhante: cor (0 a 1) e tamanho. */
public record GlowOrbOptions(float r, float g, float b, float size) implements ParticleOptions {

    public static final Codec<GlowOrbOptions> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("r").forGetter(GlowOrbOptions::r),
            Codec.FLOAT.fieldOf("g").forGetter(GlowOrbOptions::g),
            Codec.FLOAT.fieldOf("b").forGetter(GlowOrbOptions::b),
            Codec.FLOAT.fieldOf("size").forGetter(GlowOrbOptions::size)
    ).apply(i, GlowOrbOptions::new));

    @SuppressWarnings("deprecation")
    public static final ParticleOptions.Deserializer<GlowOrbOptions> DESERIALIZER = new ParticleOptions.Deserializer<>() {
        @Override
        public GlowOrbOptions fromCommand(ParticleType<GlowOrbOptions> type, StringReader reader) throws CommandSyntaxException {
            reader.expect(' ');
            float r = reader.readFloat();
            reader.expect(' ');
            float g = reader.readFloat();
            reader.expect(' ');
            float b = reader.readFloat();
            reader.expect(' ');
            float size = reader.readFloat();
            return new GlowOrbOptions(r, g, b, size);
        }

        @Override
        public GlowOrbOptions fromNetwork(ParticleType<GlowOrbOptions> type, FriendlyByteBuf buf) {
            return new GlowOrbOptions(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
        }
    };

    public static GlowOrbOptions of(int rgb, float size) {
        return new GlowOrbOptions(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, size);
    }

    @Override
    public ParticleType<?> getType() {
        return ModParticles.GLOW_ORB.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeFloat(r);
        buf.writeFloat(g);
        buf.writeFloat(b);
        buf.writeFloat(size);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "lanterna:glow_orb %.2f %.2f %.2f %.2f", r, g, b, size);
    }
}
