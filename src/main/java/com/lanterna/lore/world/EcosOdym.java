package com.lanterna.lore.world;

import com.lanterna.LanternaMod;
import com.lanterna.lore.LoreEntities;
import com.lanterna.lore.PlanetLore;
import com.lanterna.lore.entity.LoreCreatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Odym é quase todo mar, e o spawn normal do Minecraft quase nunca acerta as poucas ilhas: os Ecos sumiam do planeta.
 * Aqui, de tempos em tempos, perto de quem está em Odym, um Eco sobe do mar numa ilha (terra firme, a céu aberto,
 * acima da água, fora das cidades). Nunca mais de 5 por perto, nada no Pacífico.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EcosOdym {
    private static final int INTERVALO = 20 * 14;

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % INTERVALO != 7) return;
        if (p.isSpectator() || p.isCreative()) return;
        tentar(p);
    }

    /** Uma tentativa de fazer um Eco subir do mar perto do jogador. Devolve quantos nasceram. */
    public static int tentar(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        if (PlanetLore.byDimension(l.dimension()) != PlanetLore.ODYM || l.getDifficulty() == Difficulty.PEACEFUL) return 0;
        if (!LoreCreatures.Eco.allowSpawn(l, l.random)) return 0;
        if (l.getEntitiesOfClass(LoreCreatures.Eco.class, p.getBoundingBox().inflate(48)).size() >= 5) return 0;
        BlockPos onde = procurar(l, p);
        if (onde == null) return 0;
        int feitos = 0;
        int n = 1 + (l.random.nextFloat() < 0.45f ? 1 : 0);
        for (int i = 0; i < n; i++) {
            var eco = LoreEntities.ECO_CINZENTO.get().create(l);
            if (eco == null) return feitos;
            eco.moveTo(onde.getX() + 0.5 + i, onde.getY(), onde.getZ() + 0.5, l.random.nextFloat() * 360, 0);
            if (!l.noCollision(eco)) continue;
            eco.finalizeSpawn(l, l.getCurrentDifficultyAt(onde), MobSpawnType.EVENT, null, null);
            l.addFreshEntity(eco);
            feitos++;
        }
        l.sendParticles(ParticleTypes.SPLASH, onde.getX() + 0.5, onde.getY() + 0.5, onde.getZ() + 0.5, 40, 0.6, 0.6, 0.6, 0.2);
        l.sendParticles(ParticleTypes.LARGE_SMOKE, onde.getX() + 0.5, onde.getY() + 1, onde.getZ() + 0.5, 12, 0.4, 0.8, 0.4, 0.01);
        l.playSound(null, onde, SoundEvents.DROWNED_AMBIENT_WATER, SoundSource.HOSTILE, 1.2f, 0.6f);
        return feitos;
    }

    /** Um lugar de ilha a 20-48 blocos do jogador (onde ele não está olhando de perto). */
    private static BlockPos procurar(ServerLevel l, ServerPlayer p) {
        for (int t = 0; t < 24; t++) {
            double a = l.random.nextDouble() * Math.PI * 2, r = 20 + l.random.nextDouble() * 28;
            int x = (int) Math.floor(p.getX() + Math.cos(a) * r), z = (int) Math.floor(p.getZ() + Math.sin(a) * r);
            if (!l.hasChunk(x >> 4, z >> 4)) continue;
            BlockPos pos = new BlockPos(x, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!l.getBlockState(pos.below()).isValidSpawn(l, pos.below(), LoreEntities.ECO_CINZENTO.get())) continue;
            if (!ZonasDeNascimento.ecoPodeNascer(l, pos, MobSpawnType.NATURAL, l.random)) continue;
            if (!l.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(12)).isEmpty()) continue;
            return pos;
        }
        return null;
    }

    private EcosOdym() {}
}
