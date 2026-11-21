package com.lanterna.morte;

import com.lanterna.LanternaMod;
import com.lanterna.construct.ConstructEntity;
import com.lanterna.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 0.58.2: a Forma do Necron (construto do Anel Preto, só do Arauto da Morte). O Arauto vira o Necron por 60 segundos:
 * o corpo dele é desenhado como o modelo do Necron, com a foice (ver client/necron/NecroformaCliente).
 *
 *  · ao se transformar: o Grito da Morte, uma onda de almas que fere, empurra e cega quem está perto;
 *  · a foice: cada ataque corta em arco os inimigos na frente (até 4,5 blocos) e devolve vida;
 *  · a aura da Morte: quem é inimigo a até 6 blocos seca (Wither) e fica lento;
 *  · força II e resistência I; os outros dons da Morte ficam 50% mais fortes e recarregam na metade do tempo.
 *
 * Tudo no servidor; o cliente só desenha (multiplayer: todos veem o Necron no lugar do jogador).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Necroforma {
    public static final int DURACAO = 20 * 60;
    private static final Map<UUID, Integer> RESTA = new HashMap<>();

    public static boolean ativa(Player p) {
        return RESTA.containsKey(p.getUUID());
    }

    /** Multiplicador do poder dos construtos (1,5 transformado). */
    public static float poder(Player p) { return ativa(p) ? 1.5f : 1f; }

    /** Multiplicador da recarga dos construtos (metade transformado). */
    public static float recarga(Player p) { return ativa(p) ? 0.5f : 1f; }

    public static boolean ativar(ServerPlayer p, float pow) {
        if (!AnelPreto.arauto(p)) return false;
        RESTA.put(p.getUUID(), DURACAO);
        avisar(p, DURACAO);
        ServerLevel l = p.serverLevel();
        // o Grito da Morte
        double r = 8;
        for (int k = 0; k < 64; k++) {
            double a = Math.PI * 2 * k / 64;
            for (double d = 1.5; d <= r; d += 1.6)
                l.sendParticles(ParticleTypes.SCULK_SOUL, p.getX() + Math.cos(a) * d, p.getY() + 0.3, p.getZ() + Math.sin(a) * d, 1, 0, 0.05, 0, 0.02);
        }
        l.sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1.2, p.getZ(), 120, 0.6, 1.4, 0.6, 0.12);
        l.sendParticles(new DustParticleOptions(new Vector3f(0.25f, 0.05f, 0.35f), 3f), p.getX(), p.getY() + 1.5, p.getZ(), 80, 0.8, 1.6, 0.8, 0);
        l.playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.2f, 0.55f);
        l.playSound(null, p.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1f, 0.6f);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), x -> ConstructEntity.inimigo(p, x))) {
            com.lanterna.combate.Equilibrio.ferirComPoder(e, p.damageSources().playerAttack(p), 8f * pow);
            Vec3 v = e.position().subtract(p.position()).multiply(1, 0, 1);
            v = v.lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : v.normalize().scale(1.6);
            e.setDeltaMovement(v.x, 0.6, v.z);
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
            e.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
        }
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, DURACAO, 1, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, DURACAO, 0, false, false));
        p.sendSystemMessage(Component.translatable("morte.lanterna.necroforma_comeca").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        return true;
    }

    public static void encerrar(ServerPlayer p) {
        if (RESTA.remove(p.getUUID()) == null) return;
        avisar(p, 0);
        p.removeEffect(MobEffects.DAMAGE_BOOST);
        p.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        p.serverLevel().sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1.2, p.getZ(), 60, 0.5, 1.2, 0.5, 0.06);
        p.level().playSound(null, p.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.2f, 0.7f);
        p.displayClientMessage(Component.translatable("morte.lanterna.necroforma_fim").withStyle(ChatFormatting.GRAY), true);
    }

    private static void avisar(ServerPlayer p, int ticks) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new Pacote(p.getId(), ticks));
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        Integer t = RESTA.get(p.getUUID());
        if (t == null) return;
        if (t <= 0 || !p.isAlive() || !AnelPreto.arauto(p)) { encerrar(p); return; }
        RESTA.put(p.getUUID(), t - 1);
        ServerLevel l = p.serverLevel();
        // a aura da Morte
        if (t % 20 == 0) {
            for (LivingEntity x : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(6), y -> ConstructEntity.inimigo(p, y))) {
                x.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
                x.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            }
        }
        if (t % 4 == 0) {
            double a = l.random.nextDouble() * Math.PI * 2;
            l.sendParticles(ParticleTypes.SOUL, p.getX() + Math.cos(a) * 1.2, p.getY() + 0.2 + l.random.nextDouble() * 2.2, p.getZ() + Math.sin(a) * 1.2, 1, 0, 0.03, 0, 0.01);
            l.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 0.1, p.getZ(), 2, 0.5, 0.05, 0.5, 0.005);
        }
        if (t == 200) p.displayClientMessage(Component.translatable("morte.lanterna.necroforma_aviso").withStyle(ChatFormatting.GRAY), true);
    }

    private static final Map<UUID, Long> ULTIMO_CORTE = new HashMap<>();
    /** Enquanto o corte fere (esses ferimentos não fazem outro corte). */
    private static boolean cortando;

    /**
     * 0.58.3: a foice. Cada vez que o Arauto transformado ataca (o cliente avisa quando o braço balança, acertando
     * alguém ou não), a lâmina corta em arco tudo o que é inimigo na frente, a até 4,5 blocos, e devolve vida.
     */
    public static void cortar(ServerPlayer p) {
        if (!ativa(p) || cortando) return;
        long agora = p.serverLevel().getGameTime();
        if (agora - ULTIMO_CORTE.getOrDefault(p.getUUID(), 0L) < 10) return;
        ULTIMO_CORTE.put(p.getUUID(), agora);
        ServerLevel l = p.serverLevel();
        var ring = com.lanterna.capability.ModCapabilities.get(p).orElse(null);
        float pow = ring == null ? 1f : com.lanterna.construct.Constructs.power(p, ring);
        float dano = 6f + 8f * pow;
        Vec3 olhar = p.getViewVector(1f).multiply(1, 0, 1);
        olhar = olhar.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : olhar.normalize();
        float total = 0;
        cortando = true;
        try {
            for (LivingEntity x : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(4.5, 2, 4.5), y -> ConstructEntity.inimigo(p, y))) {
                Vec3 d = x.position().subtract(p.position()).multiply(1, 0, 1);
                if (d.length() > 4.5 || d.lengthSqr() > 0.25 && d.normalize().dot(olhar) < 0.25) continue;   // só o arco na frente
                x.invulnerableTime = 0;
                com.lanterna.combate.Equilibrio.ferirComPoder(x, p.damageSources().playerAttack(p), dano);
                Vec3 emp = d.lengthSqr() > 1e-3 ? d.normalize().scale(0.6) : olhar.scale(0.6);
                x.push(emp.x, 0.2, emp.z);
                total += dano;
            }
        } finally {
            cortando = false;
        }
        if (total > 0) p.heal(Math.min(8f, total * 0.15f));
        // o rastro da lâmina: um arco de almas na altura do peito
        for (int k = 0; k <= 20; k++) {
            double a = Math.toRadians(-75 + 150 * k / 20.0);
            Vec3 dir = new Vec3(olhar.x * Math.cos(a) - olhar.z * Math.sin(a), 0, olhar.x * Math.sin(a) + olhar.z * Math.cos(a));
            Vec3 q = p.position().add(0, 1.3 - 0.6 * k / 20.0, 0).add(dir.scale(3.0));
            l.sendParticles(ParticleTypes.SCULK_SOUL, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0);
            if (k % 4 == 0) l.sendParticles(ParticleTypes.SWEEP_ATTACK, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
        l.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.1f, 0.55f);
        if (total > 0) l.playSound(null, p.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.5f, 0.7f);
    }

    /** Cliente → servidor: o Arauto transformado balançou a foice. */
    public record Golpe() {
        public static void encode(Golpe m, FriendlyByteBuf b) {}
        public static Golpe decode(FriendlyByteBuf b) { return new Golpe(); }
        public static void handle(Golpe m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> { ServerPlayer p = ctx.get().getSender(); if (p != null) cortar(p); });
            ctx.get().setPacketHandled(true);
        }
    }

    @SubscribeEvent
    public static void morreu(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) encerrar(p);
    }

    @SubscribeEvent
    public static void saiu(PlayerEvent.PlayerLoggedOutEvent e) {
        RESTA.remove(e.getEntity().getUUID());
        ULTIMO_CORTE.remove(e.getEntity().getUUID());
    }

    /** Quem passa a ver o jogador (chegou perto, entrou no servidor) fica sabendo da forma dele. */
    @SubscribeEvent
    public static void comecouAVer(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer alvo && e.getEntity() instanceof ServerPlayer quem && ativa(alvo))
            ModNetwork.sendTo(quem, new Pacote(alvo.getId(), RESTA.get(alvo.getUUID())));
    }

    @SubscribeEvent
    public static void mudouDeMundo(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && ativa(p)) avisar(p, RESTA.get(p.getUUID()));
    }

    /** Servidor → cliente: este jogador está (ticks > 0) ou não (0) na Forma do Necron. */
    public record Pacote(int entidade, int ticks) {
        public static void encode(Pacote m, FriendlyByteBuf b) { b.writeVarInt(m.entidade); b.writeVarInt(m.ticks); }
        public static Pacote decode(FriendlyByteBuf b) { return new Pacote(b.readVarInt(), b.readVarInt()); }
        public static void handle(Pacote m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.lanterna.client.necron.NecroformaCliente.receber(m.entidade, m.ticks)));
            ctx.get().setPacketHandled(true);
        }
    }

    private Necroforma() {}
}
