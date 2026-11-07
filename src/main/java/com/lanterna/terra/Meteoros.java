package com.lanterna.terra;

import com.lanterna.LanternaMod;
import com.lanterna.mission.MissionManager;
import com.lanterna.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Estrelas cadentes na Terra. De noite, um rastro de fogo risca o céu e cai a algumas dezenas de blocos do jogador,
 * abrindo uma cratera com uma Rocha Estelar (Fragmentos Estelares) e trazendo alguns Contaminados.
 *
 * Quem está na missão "Estrelas Cadentes" vê uma por minuto, mais ou menos; os outros, raramente (uma por noite,
 * de vez em quando), para os fragmentos nunca acabarem.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Meteoros {
    private static final int QUEDA = 50;

    private record Queda(ServerLevel level, Vec3 de, Vec3 ate, UUID jogador, long inicio) {}

    private record Brilho(ServerLevel level, BlockPos pos, long ate) {}

    private static final List<Queda> CAINDO = new ArrayList<>();
    private static final List<Brilho> BRILHOS = new ArrayList<>();
    private static final Map<UUID, Long> PROXIMO = new HashMap<>();

    /** O mob que veio com a estrela: mais rápido e mais forte, com o brilho verde. */
    public static void contaminar(Mob m, boolean maior) {
        m.setCustomName(Component.translatable(maior ? "entity.lanterna.contaminado_maior" : "entity.lanterna.contaminado"));
        m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60 * 5, 0, false, false));
        m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60 * 5, maior ? 1 : 0, false, false));
        var hp = m.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.addPermanentModifier(new AttributeModifier("lanterna contaminado", maior ? 30 : 6, AttributeModifier.Operation.ADDITION));
            m.setHealth(m.getMaxHealth());
        }
        if (maior) {
            var dano = m.getAttribute(Attributes.ATTACK_DAMAGE);
            if (dano != null) dano.addPermanentModifier(new AttributeModifier("lanterna contaminado", 3, AttributeModifier.Operation.ADDITION));
        }
        m.getPersistentData().putBoolean("lanterna_contaminado", true);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = e.getServer();
        ServerLevel ow = server.overworld();
        long agora = ow.getGameTime();
        CAINDO.removeIf(q -> cair(q, agora));
        BRILHOS.removeIf(b -> {
            if (agora > b.ate()) return true;
            if (agora % 8 == 0 && b.level().getBlockState(b.pos()).is(ModBlocks.ROCHA_ESTELAR.get()))
                for (int k = 0; k < 3; k++)
                    b.level().sendParticles(ParticleTypes.END_ROD, b.pos().getX() + 0.5, b.pos().getY() + 1 + b.level().getRandom().nextDouble() * 30, b.pos().getZ() + 0.5, 1, 0.05, 0, 0.05, 0);
            return !b.level().getBlockState(b.pos()).is(ModBlocks.ROCHA_ESTELAR.get());
        });
        if (agora % 20 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() != ow || p.isSpectator() || !ow.isNight()) continue;
            var d = MissionManager.data(p);
            boolean missao = d != null && d.isActive("terra_3");
            long prox = PROXIMO.getOrDefault(p.getUUID(), 0L);
            if (missao) {
                if (prox == 0) PROXIMO.put(p.getUUID(), prox = agora + 300);
                if (agora >= prox) {
                    PROXIMO.put(p.getUUID(), agora + 1200 + ow.getRandom().nextInt(800));
                    lancar(p);
                }
            } else if (ow.getRandom().nextInt(1500) == 0) {
                lancar(p);
            }
        }
    }

    /** Lança uma estrela cadente perto do jogador. */
    public static boolean lancar(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        var rng = l.getRandom();
        for (int tent = 0; tent < 8; tent++) {
            double a = rng.nextDouble() * Math.PI * 2, raio = 35 + rng.nextDouble() * 45;
            int x = (int) Math.floor(p.getX() + Math.cos(a) * raio), z = (int) Math.floor(p.getZ() + Math.sin(a) * raio);
            if (!l.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockState chao = l.getBlockState(new BlockPos(x, y - 1, z));
            if (!chao.getFluidState().isEmpty() || y <= l.getMinBuildHeight() + 2) continue;
            Vec3 ate = new Vec3(x + 0.5, y, z + 0.5);
            double b = rng.nextDouble() * Math.PI * 2;
            Vec3 de = ate.add(Math.cos(b) * 90, 140, Math.sin(b) * 90);
            CAINDO.add(new Queda(l, de, ate, p.getUUID(), l.getGameTime()));
            l.playSound(null, p.blockPosition(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.AMBIENT, 2f, 0.5f);
            p.displayClientMessage(Component.translatable("message.lanterna.meteoro.ceu"), true);
            return true;
        }
        return false;
    }

    private static boolean cair(Queda q, long agora) {
        ServerLevel l = q.level();
        float f = (agora - q.inicio()) / (float) QUEDA;
        if (f >= 1) {
            impacto(q);
            return true;
        }
        Vec3 p = q.de().lerp(q.ate(), f * f);
        Vec3 atras = q.de().lerp(q.ate(), Math.max(0, f * f - 0.05));
        for (int k = 0; k < 6; k++) {
            Vec3 r = atras.lerp(p, k / 6.0);
            l.sendParticles(ParticleTypes.FLAME, r.x, r.y, r.z, 2, 0.25, 0.25, 0.25, 0.01);
            l.sendParticles(ParticleTypes.LARGE_SMOKE, r.x, r.y, r.z, 1, 0.2, 0.2, 0.2, 0.0);
        }
        l.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 4, 0.3, 0.3, 0.3, 0.02);
        if ((agora - q.inicio()) % 10 == 0) l.playSound(null, p.x, p.y, p.z, SoundEvents.BLAZE_SHOOT, SoundSource.AMBIENT, 4f, 0.4f);
        return false;
    }

    private static void impacto(Queda q) {
        ServerLevel l = q.level();
        Vec3 c = q.ate();
        BlockPos centro = BlockPos.containing(c);
        l.playSound(null, centro, SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 6f, 0.6f);
        l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        l.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 30, 1.5, 0.5, 1.5, 0.1);
        var rng = l.getRandom();
        // cratera rasa (raio 3), borda queimada
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 3.4) continue;
            int fundo = d < 1.5 ? 2 : d < 2.6 ? 1 : 0;
            int topo = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centro.getX() + dx, centro.getZ() + dz);
            for (int k = 0; k <= 4; k++) {
                BlockPos b = new BlockPos(centro.getX() + dx, topo + k, centro.getZ() + dz);
                BlockState st = l.getBlockState(b);
                if (!st.isAir() && st.getDestroySpeed(l, b) >= 0 && st.getFluidState().isEmpty()) l.removeBlock(b, false);
            }
            for (int k = 1; k <= fundo; k++) {
                BlockPos b = new BlockPos(centro.getX() + dx, topo - k, centro.getZ() + dz);
                if (l.getBlockState(b).getDestroySpeed(l, b) >= 0 && l.getBlockEntity(b) == null) l.removeBlock(b, false);
            }
            BlockPos chao = new BlockPos(centro.getX() + dx, topo - fundo - 1, centro.getZ() + dz);
            if (l.getBlockEntity(chao) == null && l.getBlockState(chao).getDestroySpeed(l, chao) >= 0 && !l.getBlockState(chao).isAir())
                l.setBlockAndUpdate(chao, rng.nextInt(5) == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                        : rng.nextBoolean() ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.COBBLED_DEEPSLATE.defaultBlockState());
            if (d > 2.6 && rng.nextInt(4) == 0 && l.getBlockState(chao.above()).isAir()) l.setBlockAndUpdate(chao.above(), Blocks.FIRE.defaultBlockState());
        }
        int topo = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centro.getX(), centro.getZ());
        BlockPos rocha = new BlockPos(centro.getX(), topo, centro.getZ());
        if (l.getBlockState(rocha).is(Blocks.FIRE)) l.removeBlock(rocha, false);
        l.setBlockAndUpdate(rocha, ModBlocks.ROCHA_ESTELAR.get().defaultBlockState());
        BRILHOS.add(new Brilho(l, rocha, l.getGameTime() + 20 * 150));
        // os Contaminados
        int n = 2 + rng.nextInt(2);
        for (int i = 0; i < n && l.isNight(); i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            int x = rocha.getX() + (int) Math.round(Math.cos(a) * 5), z = rocha.getZ() + (int) Math.round(Math.sin(a) * 5);
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            Mob m = (rng.nextInt(3) == 0 ? EntityType.HUSK : EntityType.ZOMBIE).create(l);
            if (m == null) continue;
            m.moveTo(x + 0.5, y, z + 0.5, rng.nextFloat() * 360, 0);
            m.finalizeSpawn(l, l.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null);
            contaminar(m, false);
            l.addFreshEntity(m);
        }
        ServerPlayer p = l.getServer().getPlayerList().getPlayer(q.jogador());
        if (p != null && p.level() == l) {
            double dx = rocha.getX() - p.getX(), dz = rocha.getZ() - p.getZ();
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            p.displayClientMessage(Component.translatable("message.lanterna.meteoro.caiu", dist, Component.translatable(direcao(dx, dz))), false);
        }
    }

    private static String direcao(double dx, double dz) {
        String[] d = {"sul", "sudoeste", "oeste", "noroeste", "norte", "nordeste", "leste", "sudeste"};
        double ang = Math.toDegrees(Math.atan2(-dx, dz));
        int i = (int) Math.floorMod(Math.round(ang / 45.0), 8);
        return "direcao.lanterna." + d[i];
    }

    private Meteoros() {}
}
