package com.lanterna.energia;

import com.lanterna.LanternaConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Ataques às mineradoras: o barulho da broca atrai criaturas do planeta. Só acontece com algum jogador por perto
 * (ninguém perde a mineradora sem chance de defender). Os atacantes vão até a máquina e, encostados nela, vão
 * quebrando a estrutura; com integridade 0 ela para até ser consertada com barras de ferro.
 */
public final class MineradoraAtaques {
    public static final String TAG = "lanterna_ataca_mineradora";

    static void tick(MineradoraBE m) {
        if (!(m.getLevel() instanceof ServerLevel l)) return;
        BlockPos pos = m.getBlockPos();
        long t = l.getGameTime();
        if ((t + pos.asLong()) % 20 != 0) return;
        AABB perto = new AABB(pos).inflate(32);
        List<Mob> atacantes = l.getEntitiesOfClass(Mob.class, perto, e -> e.isAlive() && e.getPersistentData().contains(TAG));
        // atacantes andam até a máquina e batem nela
        for (Mob a : atacantes) {
            if (a.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) < 3.2 * 3.2) {
                if ((t / 20) % 2 == 0) m.danificar(1); // cada atacante: 1% a cada 2 s
                a.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                l.playSound(null, pos, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 0.6f, 1.1f);
            } else if (a.getTarget() == null) {
                a.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.1);
            }
        }
        if (!LanternaConfig.ENERGIA_ATAQUES.get() || !atacantes.isEmpty()) return;
        List<ServerPlayer> jogadores = l.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(48), p -> !p.isSpectator() && !p.isCreative());
        if (jogadores.isEmpty()) return;
        // chance por segundo para dar a média configurada
        double chance = 1.0 / (LanternaConfig.ENERGIA_ATAQUE_MINUTOS.get() * 60.0);
        if (l.random.nextDouble() >= chance) return;
        ataque(l, pos);
        for (ServerPlayer p : jogadores)
            p.displayClientMessage(Component.translatable("energia.lanterna.mineradora.ataque").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
    }

    /** Uma leva de atacantes saindo de um lado aleatório, a uns 16 blocos. */
    public static void ataque(ServerLevel l, BlockPos pos) {
        int n = 3 + l.random.nextInt(3);
        double base = l.random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = base + (l.random.nextDouble() - 0.5) * 0.9;
            int x = pos.getX() + (int) Math.round(Math.cos(a) * 16), z = pos.getZ() + (int) Math.round(Math.sin(a) * 16);
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            EntityType<? extends Mob> tipo = switch (l.random.nextInt(4)) {
                case 0 -> EntityType.HUSK;
                case 1 -> EntityType.SPIDER;
                default -> EntityType.ZOMBIE;
            };
            Mob m = tipo.create(l);
            if (m == null) continue;
            m.moveTo(x + 0.5, y, z + 0.5, l.random.nextFloat() * 360, 0);
            m.finalizeSpawn(l, l.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null);
            m.getPersistentData().putBoolean(TAG, true);
            m.setPersistenceRequired();
            com.lanterna.terra.Meteoros.contaminar(m, false);
            l.addFreshEntity(m);
        }
        l.playSound(null, pos, SoundEvents.RAID_HORN.get(), SoundSource.HOSTILE, 2f, 1.2f);
    }

    private MineradoraAtaques() {}
}
