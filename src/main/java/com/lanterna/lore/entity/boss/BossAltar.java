package com.lanterna.lore.entity.boss;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.lore.LoreEntities;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.MissionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Altar do Desafio: com 4 do minério do planeta na mão/inventário, chama o boss do planeta.
 * Rachadura do Nulo (Gênese): com o Anel Branco ligado, chama o Nulo.
 */
public final class BossAltar {
    public static final int ORE_COST = 4;

    public static void use(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        PlanetLore planet = PlanetLore.byDimension(level.dimension());
        if (planet == null || planet == PlanetLore.GENESE) {
            player.displayClientMessage(Component.translatable("message.lanterna.altar_wrong_place"), true);
            return;
        }
        EntityType<?> type = LoreEntities.bossType(planet.boss);
        if (type == null) return;
        if (alive(level, pos)) {
            player.displayClientMessage(Component.translatable("message.lanterna.altar_busy"), true);
            return;
        }
        int req = com.lanterna.tropa.Nivel.nivelDoPlaneta(planet, com.lanterna.lore.Factions.playerCorps(player));
        if (!player.isCreative() && com.lanterna.tropa.Nivel.nivel(player) < req) {
            player.displayClientMessage(Component.translatable("nivel.lanterna.precisa", req, planet.bossName()), true);
            return;
        }
        if (!player.isCreative() && MissionManager.countItem(player, planet.oreItem()) < ORE_COST) {
            player.displayClientMessage(Component.translatable("message.lanterna.altar_needs", ORE_COST, planet.oreItem().getDescription(),
                    planet.bossName()), true);
            return;
        }
        if (!player.isCreative()) MissionManager.removeItems(player, planet.oreItem(), ORE_COST);
        // Morrow nasce no meio do lago; os outros um pouco à frente do altar
        Vec3 at = planet == PlanetLore.ODYM ? Vec3.atCenterOf(pos).add(0, 0, -9) : Vec3.atBottomCenterOf(pos).add(0, 1, -9);
        LoreBoss boss = Bosses.summon(level, type, at, pos);
        if (boss != null) boss.setTarget(player);
        effects(level, pos, planet.color());
        for (ServerPlayer p : level.getPlayers(p -> p.distanceToSqr(Vec3.atCenterOf(pos)) < 96 * 96)) {
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                    planet.bossName().copy().withStyle(s -> s.withColor(planet.color()).withBold(true))));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                    Component.translatable("boss.lanterna." + planet.boss + ".intro").withStyle(s -> s.withItalic(true))));
        }
    }

    public static void useRift(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        RingData ring = ModCapabilities.get(player).orElse(null);
        boolean white = ring != null && ring.hasRing() && ring.powerActive() && ring.corps().equals(BuiltinCorps.WHITE.id());
        if (!white && !player.isCreative()) {
            player.displayClientMessage(Component.translatable("message.lanterna.rift_needs_white"), true);
            return;
        }
        if (alive(level, pos)) {
            player.displayClientMessage(Component.translatable("message.lanterna.altar_busy"), true);
            return;
        }
        LoreBoss boss = Bosses.summon(level, LoreEntities.NULO.get(), Vec3.atBottomCenterOf(pos).add(0, 0, 5), pos);
        if (boss != null) boss.setTarget(player);
        effects(level, pos, 0xA0A0A8);
        for (ServerPlayer p : level.getPlayers(p -> true)) {
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                    Component.translatable("entity.lanterna.nulo").withStyle(s -> s.withColor(0xB0B0B4).withBold(true))));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                    Component.translatable("boss.lanterna.nulo.intro").withStyle(s -> s.withItalic(true))));
        }
    }

    private static boolean alive(ServerLevel level, BlockPos pos) {
        return !level.getEntitiesOfClass(LoreBoss.class, new AABB(pos).inflate(64), b -> !b.isEcho() && b.isAlive()).isEmpty();
    }

    private static void effects(ServerLevel level, BlockPos pos, int color) {
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 80, 1, 2, 1, 0.1);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() - 8.5, 1, 0, 0, 0, 0);
        level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.5f, 0.6f);
        level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 1f, 0.8f);
    }

    private BossAltar() {}
}
