package com.lanterna.lore.world;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.lore.LoreBlocks;
import com.lanterna.lore.PlanetLore;
import com.lanterna.lore.block.LoreBlockTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * O clima de cada planeta (da lore):
 *
 *  Korugar  névoa e "Pavor": ficar ao ar livre aumenta o pavor (escuridão e fraqueza). Quem tem o anel amarelo não sente.
 *  Odym     depois de cada chuva, a aurora azul dá Regeneração a quem está ao ar livre.
 *  Ysmault  gêiseres explodem do nada perto de você.
 *  Zamaron  o Cristal Devoto só brilha com outro jogador ou um bicho domado por perto.
 *  Ryut     nada regenera a vida aqui (só efeitos e itens).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlanetEffects {
    private static final Map<UUID, Integer> DREAD = new HashMap<>();
    private static final Map<ResourceKey<Level>, Boolean> WAS_RAINING = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 20 != 3) return;
        if (p.isCreative() || p.isSpectator()) return;
        PlanetLore planet = PlanetLore.byDimension(p.level().dimension());
        if (planet == null) {
            DREAD.remove(p.getUUID());
            return;
        }
        ServerLevel level = p.serverLevel();
        switch (planet) {
            case KORUGAR -> dread(p, level);
            case YSMAULT -> {
                if (p.getRandom().nextInt(25) == 0) geyser(p, level);
            }
            case ZAMARON -> crystals(p, level);
            default -> {}
        }
    }

    private static boolean outdoors(ServerPlayer p) {
        return p.level().getHeight(Heightmap.Types.MOTION_BLOCKING, p.getBlockX(), p.getBlockZ()) <= p.getBlockY() + 1;
    }

    private static void dread(ServerPlayer p, ServerLevel level) {
        RingData ring = ModCapabilities.get(p).orElse(null);
        boolean fearless = ring != null && ring.hasRing() && ring.corps().equals(BuiltinCorps.YELLOW.id());
        int d = DREAD.getOrDefault(p.getUUID(), 0);
        d = outdoors(p) && !fearless ? Math.min(240, d + 1) : Math.max(0, d - 3);
        DREAD.put(p.getUUID(), d);
        if (d > 60 && d % 15 == 0) {
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
            p.displayClientMessage(Component.translatable("planet.lanterna.korugar.dread", d * 100 / 240).withStyle(s -> s.withColor(0xFFE01A)), true);
        }
        if (d > 150) p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false));
        if (d > 210) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, false));
    }

    private static void geyser(ServerPlayer p, ServerLevel level) {
        BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                p.blockPosition().offset(p.getRandom().nextInt(17) - 8, 0, p.getRandom().nextInt(17) - 8));
        for (int k = 0; k < 12; k++) {
            level.sendParticles(ParticleTypes.LAVA, at.getX() + 0.5, at.getY() + k * 0.6, at.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.getX() + 0.5, at.getY() + k * 0.7, at.getZ() + 0.5, 2, 0.3, 0.2, 0.3, 0.01);
        }
        level.playSound(null, at, SoundEvents.LAVA_POP, SoundSource.AMBIENT, 2f, 0.5f);
        level.playSound(null, at, SoundEvents.FIRECHARGE_USE, SoundSource.AMBIENT, 1.5f, 0.6f);
        for (Player near : level.getEntitiesOfClass(Player.class, new AABB(at).inflate(1.5, 3, 1.5))) {
            near.push(0, 1.1, 0);
            near.hurtMarked = true;
            near.hurt(level.damageSources().hotFloor(), 2);
        }
    }

    private static void crystals(ServerPlayer p, ServerLevel level) {
        BlockPos c = p.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(c.offset(-8, -4, -8), c.offset(8, 4, 8))) {
            BlockState s = level.getBlockState(pos);
            if (!(s.getBlock() instanceof LoreBlockTypes.CristalDevoto)) continue;
            AABB box = new AABB(pos).inflate(6);
            boolean company = level.getEntitiesOfClass(Player.class, box, o -> !o.isSpectator()).size() >= 2
                    || !level.getEntitiesOfClass(TamableAnimal.class, box, TamableAnimal::isTame).isEmpty();
            if (s.getValue(LoreBlockTypes.CristalDevoto.ACESO) != company) {
                level.setBlock(pos, s.setValue(LoreBlockTypes.CristalDevoto.ACESO, company), 3);
                if (company) level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.6f);
            }
        }
    }

    /** Aurora de Odym: quando a chuva para, quem está ao ar livre ganha Regeneração. */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.getGameTime() % 40 != 0) return;
        if (PlanetLore.byDimension(level.dimension()) != PlanetLore.ODYM) return;
        boolean raining = level.isRaining();
        Boolean was = WAS_RAINING.put(level.dimension(), raining);
        if (was != null && was && !raining) {
            for (ServerPlayer p : level.players()) {
                if (!outdoors(p)) continue;
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 60, 0));
                p.displayClientMessage(Component.translatable("planet.lanterna.odym.aurora").withStyle(s -> s.withColor(0x60B0FF)), true);
                level.sendParticles(ParticleTypes.GLOW, p.getX(), p.getY() + 8, p.getZ(), 80, 12, 2, 12, 0);
            }
        }
    }

    /** Ryut: a comida não regenera vida (a regeneração natural é cancelada). */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (PlanetLore.byDimension(p.level().dimension()) != PlanetLore.RYUT) return;
        if (event.getAmount() <= 1.0f && !p.hasEffect(MobEffects.REGENERATION) && !p.hasEffect(MobEffects.HEAL)) {
            event.setCanceled(true);
        }
    }

    private PlanetEffects() {}
}
