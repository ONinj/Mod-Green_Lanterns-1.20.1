package com.lanterna.portal;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.construct.Affinity;
import com.lanterna.construct.ConstructEntity;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.LoreCapability;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.Mission;
import com.lanterna.mission.MissionManager;
import com.lanterna.naves.NaveInterior;
import com.lanterna.network.ModNetwork;
import com.lanterna.power.EquipmentHelper;
import com.lanterna.power.RingPowers;
import com.lanterna.space.SpaceBodies;
import com.lanterna.tropa.Nivel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A Porta Estelar: o anel abre uma porta de luz e o Lanterna atravessa até um planeta que já visitou.
 *
 * Liberada pelos Guardiões (linha "portal_1..3") a partir do nível {@link #NIVEL} com afinidade "Confiável" (3).
 * Custa {@link #CUSTO} de carga e tem {@link #ESPERA_MS} de espera.
 *
 * Linha do tempo (ticks do servidor):
 *   0  o Lanterna sobe um pouco no ar, estende o braço; o anel brilha
 *  10  a porta nasce na frente dele, desenhando a moldura de baixo para cima
 *  32  ele voa para dentro da porta (clarão)
 *  44  chega ao planeta, saindo de outra porta
 *  66  a porta de chegada se fecha
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PortalEstelar {
    public static final int NIVEL = 32;
    public static final int AFINIDADE = 3;
    public static final int CUSTO = 300;
    public static final long ESPERA_MS = 3 * 60_000L;
    public static final int T_PORTA = 10, T_ATRAVESSA = 44, T_FIM = 66;

    private static final class Viagem {
        final String destino;
        final float yaw;
        final Vec3 porta;
        final int cor;
        int t;

        Viagem(String destino, float yaw, Vec3 porta, int cor) {
            this.destino = destino;
            this.yaw = yaw;
            this.porta = porta;
            this.cor = cor;
        }
    }

    private static final Map<UUID, Viagem> VIAGENS = new HashMap<>();

    public static boolean viajando(UUID p) {
        return VIAGENS.containsKey(p);
    }

    // ===================== Destinos =====================

    /** Para onde este jogador pode abrir uma porta: a Terra e os planetas que já visitou. */
    public static List<String> destinos(ServerPlayer p) {
        LoreData d = LoreCapability.get(p).orElse(null);
        List<String> l = new ArrayList<>();
        l.add("earth");
        if (d != null) for (String id : d.visitados()) {
            SpaceBodies.Body b = SpaceBodies.byId(id);
            if (b != null && b.landable() && b != SpaceBodies.EARTH && b.dimension() != null && !l.contains(id)) l.add(id);
        }
        return l;
    }

    /** Ponto de chegada (no chão) e o mundo. */
    @Nullable
    private static Map.Entry<ServerLevel, Vec3> chegada(ServerPlayer p, String destino) {
        SpaceBodies.Body b = SpaceBodies.byId(destino);
        if (b == null || b.dimension() == null) return null;
        ServerLevel level = p.server.getLevel(b.dimension());
        if (level == null) return null;
        int x, z;
        if (b == SpaceBodies.EARTH) {
            Vec3 back = com.lanterna.space.SpaceTravel.earthReturn(p, level);
            x = (int) Math.floor(back.x);
            z = (int) Math.floor(back.z);
        } else if (b.procedural() != null) {
            x = b.procedural().regionX() + 20;
            z = b.procedural().regionZ() + 20;
        } else {
            PlanetLore pl = PlanetLore.byPlanet(b.id());
            BlockPos hub = pl != null ? PlanetLore.HUB : BlockPos.ZERO;
            x = hub.getX();
            z = hub.getZ() + 26;
        }
        // procura chão firme e seco em volta (nada de cair no mar ou na lava)
        for (int r = 0; r <= 48; r += 4) {
            for (int k = 0; k < Math.max(1, r * 2); k++) {
                double ang = k * Math.PI * 2 / Math.max(1, r * 2);
                int xx = x + (int) Math.round(Math.cos(ang) * r), zz = z + (int) Math.round(Math.sin(ang) * r);
                level.getChunk(xx >> 4, zz >> 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, xx, zz);
                if (y <= level.getMinBuildHeight() + 1) continue;
                BlockPos sob = new BlockPos(xx, y - 1, zz);
                if (!level.getFluidState(sob).isEmpty() || !level.getFluidState(sob.above()).isEmpty()) continue;
                if (!level.getBlockState(sob).isFaceSturdy(level, sob, net.minecraft.core.Direction.UP)) continue;
                return Map.entry(level, new Vec3(xx + 0.5, y, zz + 0.5));
            }
        }
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinBuildHeight() + 1) y = 100;
        return Map.entry(level, new Vec3(x + 0.5, y, z + 0.5));
    }

    // ===================== Pedidos =====================

    @Nullable
    private static Component bloqueio(ServerPlayer p, LoreData d, RingData r) {
        if (!d.portal()) return Component.translatable("portal.lanterna.erro.bloqueado", NIVEL);
        if (r == null || !r.hasRing() || !r.powerActive()) return Component.translatable("message.lanterna.power_needed");
        if (p.level().dimension().equals(NaveInterior.DIM) || p.isPassenger()) return Component.translatable("portal.lanterna.erro.lugar");
        if (VIAGENS.containsKey(p.getUUID())) return Component.translatable("portal.lanterna.erro.viajando");
        long falta = d.portalCd() - System.currentTimeMillis();
        if (falta > 0 && !p.isCreative()) return Component.translatable("portal.lanterna.erro.espera", com.lanterna.guilda.Invasao.tempo(falta));
        if (r.charge() < CUSTO && !p.isCreative()) return Component.translatable("portal.lanterna.erro.carga", CUSTO);
        return null;
    }

    public static void abrirLista(ServerPlayer p) {
        LoreData d = LoreCapability.get(p).orElse(null);
        RingData r = ModCapabilities.get(p).orElse(null);
        if (d == null) return;
        if (!d.portal()) {
            p.displayClientMessage(Component.translatable("portal.lanterna.erro.bloqueado", NIVEL).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        long espera = Math.max(0, d.portalCd() - System.currentTimeMillis());
        ModNetwork.sendTo(p, new PortalPackets.Lista(destinos(p), p.isCreative() ? 0 : espera, CUSTO, r == null ? 0 : r.charge(), r == null ? 0x2BFF4A : r.color()));
    }

    public static void pedir(ServerPlayer p, String destino) {
        LoreData d = LoreCapability.get(p).orElse(null);
        RingData r = ModCapabilities.get(p).orElse(null);
        if (d == null || r == null) return;
        Component erro = bloqueio(p, d, r);
        if (erro == null && !destinos(p).contains(destino)) erro = Component.translatable("portal.lanterna.erro.destino");
        if (erro != null) {
            p.displayClientMessage(erro.copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!p.isCreative()) RingPowers.setCharge(p, r, r.charge() - CUSTO);
        d.setPortalCd(System.currentTimeMillis() + ESPERA_MS);
        float yaw = p.getYRot();
        Vec3 fwd = Vec3.directionFromRotation(0, yaw);
        Vec3 porta = p.position().add(fwd.scale(3.6)).add(0, 0.3, 0);
        Viagem v = new Viagem(destino, yaw, porta, r.color());
        VIAGENS.put(p.getUUID(), v);
        enviar(p, new PortalPackets.Anim(p.getId(), 0, porta.x, porta.y, porta.z, yaw, v.cor, destino), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 1.4f);
        com.lanterna.construct.Affinity.add(p, 10);
    }

    private static void enviar(ServerPlayer p, Object msg, boolean todos) {
        if (todos) ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), msg);
        else ModNetwork.sendTo(p, msg);
    }

    // ===================== A viagem =====================

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        Viagem v = VIAGENS.get(p.getUUID());
        if (v == null) {
            if (p.tickCount % 100 == 47) liberarLinha(p);
            return;
        }
        v.t++;
        p.fallDistance = 0;
        if (p.isDeadOrDying()) {
            VIAGENS.remove(p.getUUID());
            return;
        }
        ServerLevel level = p.serverLevel();
        if (v.t == T_PORTA) {
            ConstructEntity porta = ConstructEntity.create(p, ConstructEntity.Kind.DOOR, v.cor, T_ATRAVESSA - T_PORTA + 6, 0, 1f);
            porta.setPos(v.porta.x, v.porta.y, v.porta.z);
            porta.setYRot(v.yaw);
            level.addFreshEntity(porta);
            level.playSound(null, v.porta.x, v.porta.y, v.porta.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.2f, 0.8f);
        }
        if (v.t == T_ATRAVESSA - 6) level.playSound(null, p.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.6f, 1.6f);
        if (v.t == T_ATRAVESSA) {
            var dest = chegada(p, v.destino);
            if (dest == null) {
                VIAGENS.remove(p.getUUID());
                enviar(p, new PortalPackets.Anim(p.getId(), 2, 0, 0, 0, v.yaw, v.cor, v.destino), true);
                return;
            }
            ServerLevel alvo = dest.getKey();
            Vec3 chao = dest.getValue();
            Vec3 fwd = Vec3.directionFromRotation(0, v.yaw);
            // a porta de chegada fica de pé no chão; o Lanterna aparece dentro dela
            Vec3 porta = chao.add(0, 0.3, 0);
            level.playSound(null, p.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.3f, 1.8f);
            p.teleportTo(alvo, porta.x, porta.y + 0.2, porta.z, v.yaw, 5f);
            p.fallDistance = 0;
            ConstructEntity saida = ConstructEntity.create(p, ConstructEntity.Kind.DOOR, v.cor, T_FIM - T_ATRAVESSA + 40, 0, 1f);
            saida.setPos(porta.x, porta.y, porta.z);
            saida.setYRot(v.yaw);
            alvo.addFreshEntity(saida);
            alvo.playSound(null, porta.x, porta.y, porta.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.get(), SoundSource.PLAYERS, 1.2f, 1.3f);
            enviar(p, new PortalPackets.Anim(p.getId(), 1, porta.x, porta.y, porta.z, v.yaw, v.cor, v.destino), false);
            LanternaMod.LOGGER.debug("Lanterna: porta estelar de {} chegou em {} {}", p.getGameProfile().getName(), alvo.dimension().location(), porta);
            SpaceBodies.Body b = SpaceBodies.byId(v.destino);
            p.displayClientMessage(Component.translatable("portal.lanterna.chegou", b == null ? v.destino : b.displayName())
                    .withStyle(s -> s.withColor(v.cor)), true);
            MissionManager.evaluate(p, LoreCapability.get(p).orElse(null));
        }
        if (v.t >= T_FIM + 40) {
            VIAGENS.remove(p.getUUID());
            enviar(p, new PortalPackets.Anim(p.getId(), 2, 0, 0, 0, v.yaw, v.cor, v.destino), true);
        }
    }

    /** Ninguém fere quem está atravessando a porta. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && VIAGENS.containsKey(p.getUUID())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        VIAGENS.remove(event.getEntity().getUUID());
    }

    // ===================== A linha dos Guardiões =====================

    /** Nível 32 e afinidade Confiável: os Guardiões chamam para ensinar a Porta. */
    private static void liberarLinha(ServerPlayer p) {
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null || d.portal() || d.isDone("portal_1") || d.isActive("portal_1")) return;
        RingData r = ModCapabilities.get(p).orElse(null);
        if (r == null || !r.hasRing()) return;
        if (Nivel.nivel(p) < NIVEL || Affinity.level(Affinity.points(EquipmentHelper.ring(p))) < AFINIDADE) return;
        MissionManager.start(p, "portal_1");
        p.sendSystemMessage(Component.translatable("portal.lanterna.chamado").withStyle(s -> s.withColor(0x7CFFB0).withBold(true)));
    }

    public static void aoConcluir(ServerPlayer p, LoreData d, Mission m) {
        if (!m.id().equals("portal_3")) return;
        d.setPortal(true);
        p.sendSystemMessage(Component.translatable("portal.lanterna.aprendeu").withStyle(s -> s.withColor(0x7CFFB0).withBold(true)));
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1.2f);
    }

    private PortalEstelar() {}
}
