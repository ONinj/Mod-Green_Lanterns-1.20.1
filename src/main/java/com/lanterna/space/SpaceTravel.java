package com.lanterna.space;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.power.RingPowers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Viagem entre Terra, espaço e planetas (tudo no servidor):
 *
 *   Terra   --(subir acima de Y 330)-->   espaço, logo acima da Terra
 *   espaço  --(entrar num planeta)---->   mundo do planeta, na superfície
 *   planeta --(subir acima de Y 330)-->   espaço, ao lado do planeta
 *   espaço  --(descer até a Terra)---->   mundo normal, de volta onde você saiu
 *
 * No espaço: quase sem gravidade, e sem o anel ligado não há oxigênio.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpaceTravel {
    public static final int EXIT_HEIGHT = 330;
    private static final String RETURN_TAG = "lanterna_space_return";
    private static final UUID LOW_GRAVITY = UUID.fromString("6f0c5d2a-3c1e-4a8e-9a51-7d7a1e0b1a01");

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.isSpectator() || player.isPassenger()) return;
        ResourceKeyHolder dim = new ResourceKeyHolder(player.level().dimension());

        // Terra -> espaço
        if (dim.is(Level.OVERWORLD) && player.getY() > EXIT_HEIGHT) {
            CompoundTag back = new CompoundTag();
            back.putDouble("x", player.getX());
            back.putDouble("z", player.getZ());
            player.getPersistentData().put(RETURN_TAG, back);
            Vec3 arrive = SpaceBodies.arrivalFromEarth(player.getX(), player.getZ());
            travel(player, SpaceBodies.SPACE, arrive, player.getYRot(), 20f, "space.lanterna.enter");
            return;
        }

        // Planeta -> espaço (ao lado do planeta)
        SpaceBodies.Body here = SpaceBodies.byDimension(player.level().dimension());
        if (here != null && here != SpaceBodies.EARTH && player.getY() > EXIT_HEIGHT) {
            // Sai ao lado do planeta (olhando para fora dele), sem passar do teto do espaço
            Vec3 out = here.center().add(here.radius() + 60, 0, 0);
            travel(player, SpaceBodies.SPACE, out, -90f, 0f, "space.lanterna.leave_planet");
            return;
        }

        if (!dim.is(SpaceBodies.SPACE)) return;

        // Dentro do espaço: chegou num planeta (ou na Terra)?
        SpaceBodies.Body body = SpaceBodies.inside(player.position());
        if (body != null) {
            land(player, body);
            return;
        }

        // Limites do espaço: não deixa cair no "fundo do mundo"
        if (player.getY() < -500) player.teleportTo(player.getX(), -480, player.getZ());

        // Oxigênio: só o anel ligado protege
        if (player.tickCount % 20 == 0 && !player.isCreative()) {
            RingData data = ModCapabilities.get(player).orElse(null);
            boolean protectedByRing = data != null && data.hasRing() && data.powerActive();
            if (protectedByRing) {
                // Manter o escudo de vida gasta um pouco de carga
                RingPowers.setCharge(player, data, data.charge() - 1);
                if (data.charge() <= 0) {
                    data.setPowerActive(false);
                    player.displayClientMessage(Component.translatable("space.lanterna.ring_failed"), true);
                }
            } else {
                player.hurt(player.damageSources().drown(), 3.0f);
                player.displayClientMessage(Component.translatable("space.lanterna.no_oxygen"), true);
            }
        }
    }

    /** Entrou num corpo celeste: Terra (volta para onde saiu) ou planeta (pousa na superfície). */
    private static void land(ServerPlayer player, SpaceBodies.Body body) {
        ServerLevel target = player.server.getLevel(body.dimension());
        if (target == null) {
            LanternaMod.LOGGER.warn("Lanterna: o mundo do planeta {} não existe", body.id());
            return;
        }

        double x, z;
        if (body == SpaceBodies.EARTH) {
            CompoundTag back = player.getPersistentData().getCompound(RETURN_TAG);
            BlockPos spawn = target.getSharedSpawnPos();
            x = back.contains("x") ? back.getDouble("x") : spawn.getX();
            z = back.contains("z") ? back.getDouble("z") : spawn.getZ();
            travel(player, target.dimension(), new Vec3(x, EXIT_HEIGHT - 20, z), player.getYRot(), 30f, "space.lanterna.back_to_earth");
            return;
        }

        // Ponto de pouso depende de que lado do planeta você entrou
        Vec3 dir = player.position().subtract(body.center());
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(1, 0, 0);
        flat = flat.normalize().scale(256);
        x = flat.x;
        z = flat.z;
        // Chega no alto do céu e desce enquanto o terreno carrega (sem travar o servidor)
        travel(player, target.dimension(), new Vec3(x, 300, z), player.getYRot(), 35f, "space.lanterna.landing");
        RingData data = ModCapabilities.get(player).orElse(null);
        boolean flying = data != null && data.hasRing() && data.powerActive() && data.flightEnabled();
        if (!flying) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SLOW_FALLING, 20 * 30, 0, false, false));
        }
    }

    private static void travel(ServerPlayer player, net.minecraft.resources.ResourceKey<Level> dimension, Vec3 pos,
                               float yaw, float pitch, String messageKey) {
        ServerLevel level = player.server.getLevel(dimension);
        if (level == null) return;
        player.teleportTo(level, pos.x, pos.y, pos.z, yaw, pitch);
        player.fallDistance = 0;
        level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.25f, 1.6f);
        SpaceBodies.Body body = SpaceBodies.byDimension(dimension);
        Component name = body != null ? Component.translatable(body.nameKey()) : Component.translatable("planet.lanterna.space");
        player.displayClientMessage(Component.translatable(messageKey, name), true);
    }

    // ===================== Gravidade baixa no espaço =====================

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) updateGravity(player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) updateGravity(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) updateGravity(player);
    }

    private static void updateGravity(ServerPlayer player) {
        AttributeInstance gravity = player.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
        if (gravity == null) return;
        gravity.removeModifier(LOW_GRAVITY);
        if (player.level().dimension().equals(SpaceBodies.SPACE)) {
            gravity.addTransientModifier(new AttributeModifier(LOW_GRAVITY, "lanterna_space", -0.072,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    /** Pequeno atalho para comparar dimensões. */
    private record ResourceKeyHolder(net.minecraft.resources.ResourceKey<Level> key) {
        boolean is(net.minecraft.resources.ResourceKey<Level> other) { return key.equals(other); }
    }

    private SpaceTravel() {}
}
