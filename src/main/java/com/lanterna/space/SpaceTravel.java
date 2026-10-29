package com.lanterna.space;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.power.RingPowers;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.SpaceSeedPacket;
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
        if (SpaceBodies.isPlanet(player.level().dimension()) && player.getY() > EXIT_HEIGHT) {
            SpaceBodies.Body here = SpaceBodies.bodyAt(player.level().dimension(), player.getX(), player.getZ());
            // Sai ao lado do planeta (olhando para fora dele), sem passar do teto do espaço
            Vec3 out = here != null ? here.center().add(here.radius() + 60, 0, 0)
                    : ProceduralPlanets.spacePointForRegion(player.getX(), player.getZ());
            travel(player, SpaceBodies.SPACE, out, -90f, 0f, "space.lanterna.leave_planet");
            return;
        }

        if (!dim.is(SpaceBodies.SPACE)) return;

        // Voando pelo espaço (vazio) o servidor não precisa calcular colisão com blocos. Sem isso ele
        // pararia esperando carregar cada chunk vazio pelo caminho, travando o jogo em alta velocidade.
        RingData flyer = ModCapabilities.get(player).orElse(null);
        if (flyer != null && flyer.hasRing() && flyer.flightEnabled()) player.noPhysics = true;

        // Dentro do espaço: chegou num planeta (ou na Terra)?
        SpaceBodies.Body body = SpaceBodies.inside(player.position());
        if (body != null && SpaceBodies.isHidden(body.id())
                && !com.lanterna.mission.LoreCapability.get(player).map(com.lanterna.mission.LoreData::geneseRevealed).orElse(false)
                && !player.isCreative()) {
            body = null; // Gênese ainda escondido: não dá para pousar
        }
        if (body != null) {
            land(player, body);
            return;
        }

        // Limites do espaço: não deixa cair no "fundo do mundo"
        // (o espaço só tem blocos bem lá embaixo, de -512 a -449; acima disso é vazio e leve para o servidor)
        if (player.getY() < -500) player.teleportTo(player.getX(), -480, player.getZ());

        // Oxigênio: só o anel ligado protege
        if (player.tickCount % 20 == 0 && !player.isCreative()) {
            RingData data = ModCapabilities.get(player).orElse(null);
            boolean protectedByRing = data != null && data.hasRing() && data.powerActive();
            if (protectedByRing && !RingPowers.homing(player)) {
                // Manter o escudo de vida gasta um pouco de carga (a primeira viagem para casa é por conta do anel)
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
        flat = flat.normalize().scale(body.procedural() != null ? 256 : 40);
        // Planetas gerados ficam numa região própria do mundo do tipo deles
        double ox = body.procedural() != null ? body.procedural().regionX() : 0;
        double oz = body.procedural() != null ? body.procedural().regionZ() : 0;
        x = ox + flat.x;
        z = oz + flat.z;
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
        LanternaMod.LOGGER.debug("Lanterna: {} viajou para {} em {}", player.getName().getString(), dimension.location(), pos);
        player.teleportTo(level, pos.x, pos.y, pos.z, yaw, pitch);
        player.fallDistance = 0;
        level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.25f, 1.6f);
        SpaceBodies.Body body = SpaceBodies.bodyAt(dimension, pos.x, pos.z);
        Component name = body != null ? body.displayName() : Component.translatable("planet.lanterna.space");
        player.displayClientMessage(Component.translatable(messageKey, name), true);
    }

    // ===================== Gravidade baixa no espaço =====================

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            updateGravity(player);
            // A hiperpropulsão acaba ao sair do espaço (o piloto automático continua)
            ModCapabilities.get(player).ifPresent(d -> d.setFlightMode(false, d.autopilot()));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            updateGravity(player);
            ModNetwork.sendTo(player, new SpaceSeedPacket(ProceduralPlanets.seed()));
        }
    }

    /**
     * O espaço é vazio: não precisa carregar muitos chunks em volta do jogador.
     * Distância de visão curta lá (só para o servidor) deixa a chegada nos planetas bem mais rápida.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 100 != 0) return;
        ServerLevel space = event.getServer().getLevel(SpaceBodies.SPACE);
        if (space == null) return;
        int wanted = Math.min(SPACE_VIEW_DISTANCE, event.getServer().getPlayerList().getViewDistance());
        space.getChunkSource().setViewDistance(wanted);
        space.getChunkSource().setSimulationDistance(Math.min(wanted, event.getServer().getPlayerList().getSimulationDistance()));
    }

    private static final int SPACE_VIEW_DISTANCE = 4;

    /** Semente dos planetas gerados (a mesma para todos no servidor). */
    @SubscribeEvent
    public static void onServerStarting(net.minecraftforge.event.server.ServerAboutToStartEvent event) {
        long worldSeed = event.getServer().getWorldData().worldGenOptions().seed();
        ProceduralPlanets.setSeed(worldSeed ^ 0x4C616E7465726E61L);
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
