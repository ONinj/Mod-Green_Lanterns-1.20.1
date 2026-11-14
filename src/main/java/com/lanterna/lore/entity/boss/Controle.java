package com.lanterna.lore.entity.boss;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.power.RingPowers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Poderes de controle dos chefes (todos, menos os lendários), para a luta não virar só "atirar até morrer":
 *  - Silêncio: por alguns segundos, o anel não faz construtos;
 *  - Prisão: o jogador fica preso no lugar (correntes de luz em volta);
 *  - Queda: o voo apaga por alguns segundos (quem voava cai).
 * Tudo fica marcado no jogador com a hora em que acaba; {@link #tick} aplica e devolve o voo depois.
 */
public final class Controle {
    public static final String SILENCIO = "lanterna_silencio_ate", PRESO = "lanterna_preso_ate", SEM_VOO = "lanterna_sem_voo_ate";
    private static final String PRESO_POS = "lanterna_preso_pos", VOO_VOLTA = "lanterna_voo_volta";

    public static boolean silenciado(ServerPlayer p) {
        return p.level().getGameTime() < p.getPersistentData().getLong(SILENCIO);
    }

    public static boolean semVoo(ServerPlayer p) {
        return p.level().getGameTime() < p.getPersistentData().getLong(SEM_VOO);
    }

    public static void silenciar(ServerPlayer p, int ticks, int cor) {
        p.getPersistentData().putLong(SILENCIO, p.level().getGameTime() + ticks);
        p.displayClientMessage(Component.translatable("boss.lanterna.controle.silencio", ticks / 20).withStyle(s -> s.withColor(cor).withBold(true)), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.2f, 0.6f);
        ((ServerLevel) p.level()).sendParticles(new DustParticleOptions(cor(cor), 1.4f), p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.8, 0.5, 0);
    }

    public static void prender(ServerPlayer p, int ticks, int cor) {
        p.getPersistentData().putLong(PRESO, p.level().getGameTime() + ticks);
        p.getPersistentData().putLong(PRESO_POS, BlockPos.containing(p.position()).asLong());
        p.getPersistentData().putDouble(PRESO_POS + "y", p.getY());
        p.displayClientMessage(Component.translatable("boss.lanterna.controle.prisao", ticks / 20).withStyle(s -> s.withColor(cor).withBold(true)), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 2f, 0.5f);
        derrubar(p, ticks, cor, false);
    }

    public static void derrubar(ServerPlayer p, int ticks, int cor, boolean avisar) {
        p.getPersistentData().putLong(SEM_VOO, Math.max(p.getPersistentData().getLong(SEM_VOO), p.level().getGameTime() + ticks));
        if (avisar) {
            p.displayClientMessage(Component.translatable("boss.lanterna.controle.queda", ticks / 20).withStyle(s -> s.withColor(cor).withBold(true)), true);
            p.level().playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1f, 0.5f);
        }
    }

    /** A cada tick do jogador (servidor). */
    public static void tick(ServerPlayer p) {
        long agora = p.level().getGameTime();
        var tag = p.getPersistentData();
        // Queda: apaga o voo e devolve depois
        if (agora < tag.getLong(SEM_VOO)) {
            ModCapabilities.get(p).ifPresent(d -> {
                if (d.flightEnabled()) {
                    d.setFlightEnabled(false);
                    RingPowers.applyFlightAbility(p, d);
                    tag.putBoolean(VOO_VOLTA, true);
                }
            });
        } else if (tag.getBoolean(VOO_VOLTA)) {
            tag.remove(VOO_VOLTA);
            ModCapabilities.get(p).ifPresent(d -> {
                if (d.hasRing() && d.powerActive() && !d.flightEnabled() && d.charge() > 0) {
                    d.setFlightEnabled(true);
                    RingPowers.applyFlightAbility(p, d);
                }
            });
        }
        // Prisão: volta para o lugar a cada passo
        if (agora < tag.getLong(PRESO)) {
            BlockPos b = BlockPos.of(tag.getLong(PRESO_POS));
            Vec3 alvo = new Vec3(b.getX() + 0.5, tag.getDouble(PRESO_POS + "y"), b.getZ() + 0.5);
            if (p.position().distanceToSqr(alvo) > 0.3) {
                p.teleportTo(alvo.x, alvo.y, alvo.z);
                p.setDeltaMovement(Vec3.ZERO);
                p.hurtMarked = true;
            }
            if (agora % 4 == 0) {
                ServerLevel l = (ServerLevel) p.level();
                for (int i = 0; i < 8; i++) {
                    double a = (agora * 0.2 + i * Math.PI / 4);
                    l.sendParticles(new DustParticleOptions(new Vector3f(0.75f, 0.75f, 0.8f), 1.2f),
                            alvo.x + Math.cos(a) * 0.9, alvo.y + (i % 4) * 0.55, alvo.z + Math.sin(a) * 0.9, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private static Vector3f cor(int rgb) {
        return new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
    }

    private Controle() {}
}
