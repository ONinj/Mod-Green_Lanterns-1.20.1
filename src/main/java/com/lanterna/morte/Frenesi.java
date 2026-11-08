package com.lanterna.morte;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.combate.Supremos;
import com.lanterna.construct.ConstructEntity;
import com.lanterna.mission.LoreCapability;
import com.lanterna.lore.PlanetLore;
import com.lanterna.network.ModNetwork;
import com.lanterna.space.SpaceBodies;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A Fome do Anel Preto: o Lanterna Negro que passa dias demais sem ceifar (LanternaConfig.MORTE_DIAS_FOME) não
 * enfraquece — ele fica FAMINTO. O anel toma o controle: o jogador não mexe mais o boneco, é levado para um planeta de
 * Tropa ao acaso (nunca a Gênese nem Ryut) e sai em frenesi, matando qualquer ser que vê pela frente (criaturas, NPCs,
 * aliados ou não; jogadores não) até se saciar: 25 a 30 mortes. Depois o controle volta e a fome some por mais uns dias.
 *
 * O servidor escolhe o alvo e dá os golpes; o cliente (ClienteFrenesi) move e vira o jogador até o alvo, sem deixar o
 * mouse e o teclado mandarem.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Frenesi {
    private static final List<PlanetLore> PLANETAS = List.of(PlanetLore.OA, PlanetLore.KORUGAR, PlanetLore.YSMAULT,
            PlanetLore.ODYM, PlanetLore.ZAMARON, PlanetLore.NOK, PlanetLore.OKAARA);
    private static final int DURACAO_MAX = 20 * 60 * 8;

    private static final class Estado {
        final int meta;
        int mortes, alvo = -1, semAlvo, golpe, mesmoAlvo;
        float vidaAlvo;
        /** Quem não toma dano (estátuas, NPCs protegidos): o anel desiste deles. */
        final java.util.Set<Integer> ignorados = new java.util.HashSet<>();
        final long inicio;

        Estado(int meta, long inicio) { this.meta = meta; this.inicio = inicio; }
    }

    private static final Map<UUID, Estado> ATIVOS = new HashMap<>();

    public static boolean ativo(Player p) {
        return ATIVOS.containsKey(p.getUUID());
    }

    /** A cada 20 s: o Lanterna Negro faminto perde o controle. */
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        Estado s = ATIVOS.get(p.getUUID());
        if (s == null) {
            if (p.tickCount % 400 == 77 && !p.isCreative() && !p.isSpectator() && p.isAlive() && podeComecar(p)) comecar(p);
            return;
        }
        guiar(p, s);
    }

    private static boolean podeComecar(ServerPlayer p) {
        var r = ModCapabilities.get(p).orElse(null);
        if (r == null || !r.hasRing() || !Supremos.preto(r.corps())) return false;
        return Supremos.faminto(p);
    }

    /** O anel toma o controle (também usado pelo comando de teste). */
    public static void comecar(ServerPlayer p) {
        if (ativo(p)) return;
        Estado s = new Estado(25 + p.getRandom().nextInt(6), p.server.overworld().getGameTime());
        ATIVOS.put(p.getUUID(), s);
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("morte.lanterna.frenesi.titulo").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("morte.lanterna.frenesi.sub").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        p.level().playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1f, 0.5f);
        levar(p);
        ModNetwork.sendTo(p, new Pacote(true, -1));
    }

    /** Teleporta para um planeta de Tropa ao acaso (nunca Gênese, Ryut ou o Mogo), perto da cidade. */
    private static void levar(ServerPlayer p) {
        PlanetLore pl = PLANETAS.get(p.getRandom().nextInt(PLANETAS.size()));
        SpaceBodies.Body b = SpaceBodies.byId(pl.planet);
        if (b == null || b.dimension() == null) return;
        ServerLevel l = p.server.getLevel(b.dimension());
        if (l == null) return;
        int x = PlanetLore.HUB.getX() + 30 + p.getRandom().nextInt(40), z = PlanetLore.HUB.getZ() + 30 + p.getRandom().nextInt(40);
        l.getChunk(x >> 4, z >> 4);
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 3;
        if (p.getVehicle() != null) p.stopRiding();
        p.teleportTo(l, x + 0.5, y, z + 0.5, p.getYRot(), 0);
        LanternaMod.LOGGER.info("Lanterna: frenesi de {} em {} ({}, {}, {}) -> agora em {}", p.getGameProfile().getName(), pl.planet, x, y, z, p.level().dimension().location());
        l.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL, x + 0.5, y + 1, z + 0.5, 50, 0.6, 1, 0.6, 0.05);
    }

    private static void guiar(ServerPlayer p, Estado s) {
        ServerLevel l = p.serverLevel();
        p.fallDistance = 0;
        if (!p.isAlive() || l.getGameTime() - s.inicio > DURACAO_MAX) { acabar(p, false); return; }
        // forte e difícil de derrubar enquanto a fome manda
        if (p.tickCount % 40 == 0) {
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 2, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
        }
        LivingEntity alvo = l.getEntity(s.alvo) instanceof LivingEntity le && le.isAlive() ? le : null;
        if (alvo != null) {
            // 5 s no mesmo alvo sem tirar vida dele: desiste
            if (++s.mesmoAlvo % 100 == 0) {
                if (alvo.getHealth() >= s.vidaAlvo) { s.ignorados.add(alvo.getId()); alvo = null; }
                else s.vidaAlvo = alvo.getHealth();
            }
        }
        if (alvo == null || alvo.distanceTo(p) > 96) {
            alvo = procurar(p, l, s);
            s.mesmoAlvo = 0;
            if (alvo != null) s.vidaAlvo = alvo.getHealth();
            int id = alvo == null ? -1 : alvo.getId();
            if (id != s.alvo) {
                s.alvo = id;
                ModNetwork.sendTo(p, new Pacote(true, id));
            }
        }
        if (alvo == null) {
            // nada vivo por perto: depois de 15 s o anel leva para outro planeta
            if (++s.semAlvo > 20 * 15) { s.semAlvo = 0; s.ignorados.clear(); levar(p); }
            return;
        }
        s.semAlvo = 0;
        if (s.golpe > 0) s.golpe--;
        double d = alvo.distanceTo(p);
        if (d < 3.6 && s.golpe == 0) {
            s.golpe = 9;
            p.swing(InteractionHand.MAIN_HAND, true);
            alvo.hurt(p.damageSources().playerAttack(p), 14f);
            alvo.invulnerableTime = 0;
            l.playSound(null, alvo.blockPosition(), SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.7f, 1.4f);
        } else if (d < 22 && s.golpe == 0 && p.getRandom().nextInt(5) == 0) {
            // de longe: o feixe do anel
            s.golpe = 14;
            ConstructEntity.spawnBeam(p, 0x2A2A2A, 8f, 14);
        }
    }

    @Nullable
    private static LivingEntity procurar(ServerPlayer p, ServerLevel l, Estado s) {
        LivingEntity melhor = null;
        double md = Double.MAX_VALUE;
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(80),
                x -> x != p && x.isAlive() && !(x instanceof Player) && !(x instanceof ArmorStand) && !x.isInvulnerable()
                        && !s.ignorados.contains(x.getId()))) {
            double d = e.distanceToSqr(p);
            if (d < md) { md = d; melhor = e; }
        }
        return melhor;
    }

    @SubscribeEvent
    public static void matou(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        Estado s = ATIVOS.get(p.getUUID());
        if (s == null || e.getEntity() == p) return;
        s.mortes++;
        p.displayClientMessage(Component.translatable("morte.lanterna.frenesi.conta", s.mortes, s.meta).withStyle(ChatFormatting.DARK_RED), true);
        if (s.mortes >= s.meta) acabar(p, true);
    }

    /** O anel solta o controle. Saciado = a fome some por mais uns dias. */
    public static void acabar(ServerPlayer p, boolean saciado) {
        Estado s = ATIVOS.remove(p.getUUID());
        if (s == null) return;
        ModNetwork.sendTo(p, new Pacote(false, -1));
        p.removeEffect(MobEffects.DARKNESS);
        if (saciado) {
            LoreCapability.get(p).ifPresent(d -> d.setUltimaCeifa(System.currentTimeMillis()));
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("morte.lanterna.frenesi.saciado").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("morte.lanterna.frenesi.volta").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
            p.level().playSound(null, p.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.5f, 0.6f);
        }
    }

    @SubscribeEvent
    public static void saiu(PlayerEvent.PlayerLoggedOutEvent e) {
        ATIVOS.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void morreu(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && ativo(p)) acabar(p, false);
    }

    public record Pacote(boolean ligado, int alvo) {
        public static void encode(Pacote m, FriendlyByteBuf b) { b.writeBoolean(m.ligado); b.writeVarInt(m.alvo); }
        public static Pacote decode(FriendlyByteBuf b) { return new Pacote(b.readBoolean(), b.readVarInt()); }
        public static void handle(Pacote m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.lanterna.client.morte.ClienteFrenesi.receber(m.ligado, m.alvo)));
            ctx.get().setPacketHandled(true);
        }
    }

    private Frenesi() {}
}
