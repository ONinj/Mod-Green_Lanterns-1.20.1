package com.lanterna.fonte;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.network.ModNetwork;
import com.lanterna.power.RingPowers;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A Fonte de energia de cada Tropa (dimensões lanterna:fonte_&lt;tropa&gt;, atrás do portal da Bateria Central).
 *
 * Pressão da Fonte: a energia é forte demais para um corpo. Cada segundo lá dentro enche a pressão; com 2 horas
 * reais acumuladas o jogador é expulso (náusea, fraqueza, lentidão, anel vazio) e só volta quando a pressão cair
 * abaixo da metade. Fora da Fonte ela baixa duas vezes mais rápido (1 h para esvaziar).
 *
 * Banho de Luz: 10 minutos dentro da Fonte enchem o anel e somam +150% de sobrecarga (o anel vai a 250%), com
 * uma aura na cor da Tropa enquanto a sobrecarga durar (ela é gasta antes da carga normal).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Fonte {
    /** 2 horas reais. */
    public static final int PRESSAO_MAX = 20 * 60 * 60 * 2;
    /** 10 minutos reais. */
    public static final int BANHO = 20 * 60 * 10;
    /** Sobrecarga do Banho: +150% da carga máxima (o anel fica com 250%). */
    public static final float SOBRECARGA = 1.5f;

    /** O jogador está numa Fonte? Devolve a Tropa (nome da Bateria: "verde"...) ou null. */
    @Nullable
    public static String fonteDe(Level level) {
        var id = level.dimension().location();
        if (!id.getNamespace().equals(LanternaMod.MODID) || !id.getPath().startsWith("fonte_")) return null;
        return id.getPath().substring("fonte_".length());
    }

    public static float pressao01(Player p) {
        return Math.min(1f, FonteDados.pressao(p) / (float) PRESSAO_MAX);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        String tropa = fonteDe(p.level());
        RingData ring = ModCapabilities.get(p).orElse(null);
        int antes = FonteDados.pressao(p);
        if (tropa != null && !p.isSpectator()) {
            dentro(p, ring, tropa, antes);
        } else {
            FonteDados.pressao(p, antes - 40);
            if (FonteDados.banho(p) > 0 || FonteDados.banhoFeito(p)) { FonteDados.banho(p, 0); FonteDados.banhoFeito(p, false); }
            if (FonteDados.bloqueado(p) && FonteDados.pressao(p) < PRESSAO_MAX / 2) {
                FonteDados.bloqueado(p, false);
                p.displayClientMessage(Component.translatable("msg.lanterna.fonte.desbloqueada").withStyle(s -> s.withColor(0x9BE7FF)), false);
            }
        }
        // a sobrecarga some se o jogador tirar o anel
        if ((ring == null || !ring.hasRing()) && FonteDados.sobrecarga(p) > 0) FonteDados.sobrecarga(p, 0);
        sincronizar(p, ring);
    }

    private static void dentro(ServerPlayer p, @Nullable RingData ring, String tropa, int antes) {
        int agora = antes + 20;
        FonteDados.pressao(p, agora);
        FonteDados.banho(p, FonteDados.banho(p) + 20);
        // avisos da pressão
        for (int pct : new int[]{50, 75, 90, 97}) {
            int marca = PRESSAO_MAX / 100 * pct;
            if (antes < marca && agora >= marca)
                p.displayClientMessage(Component.translatable("msg.lanterna.fonte.pressao_" + pct).withStyle(s -> s.withColor(pct >= 90 ? 0xFF6060 : 0xFFD070)), false);
        }
        if (agora >= PRESSAO_MAX) {
            expulsar(p, ring);
            return;
        }
        // Banho de Luz
        if (!FonteDados.banhoFeito(p) && FonteDados.banho(p) >= BANHO && ring != null && ring.hasRing()) {
            FonteDados.banhoFeito(p, true);
            RingPowers.setCharge(p, ring, ring.maxCharge());
            FonteDados.sobrecarga(p, Math.round(ring.maxCharge() * SOBRECARGA));
            p.displayClientMessage(Component.translatable("msg.lanterna.fonte.banho").withStyle(s -> s.withColor(ring.color()).withBold(true)), false);
            p.displayClientMessage(Component.translatable("msg.lanterna.fonte.banho2"), false);
            ServerLevel l = p.serverLevel();
            l.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.4f, 0.8f);
            l.playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.4f);
            int c = ring.color();
            l.sendParticles(new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f), 2f),
                    p.getX(), p.getY() + 1, p.getZ(), 120, 1.2, 1.4, 1.2, 0.1);
            com.lanterna.construct.Affinity.add(p, 25);
        }
        // Essência da Fonte: 1 a cada minuto dentro (mais nas provas e chefes)
        if (FonteDados.banho(p) % (20 * 60) == 0) FonteDados.essencia(p, FonteDados.essencia(p) + 2);
    }

    /** Pressão no limite: a Fonte expulsa o jogador de volta à Bateria. */
    public static void expulsar(ServerPlayer p, @Nullable RingData ring) {
        FonteDados.bloqueado(p, true);
        FonteDados.sobrecarga(p, 0);
        if (ring != null && ring.hasRing()) RingPowers.setCharge(p, ring, 0);
        p.displayClientMessage(Component.translatable("msg.lanterna.fonte.expulso").withStyle(s -> s.withColor(0xFF5050).withBold(true)), false);
        com.lanterna.baterias.BateriaTeleporte.voltar(p);
        p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 12, 0));
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 120, 1));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 60, 1));
        p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20 * 120, 1));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1f, 0.6f);
    }

    /** Gasta a sobrecarga antes da carga normal. Devolve quanto do gasto a sobrecarga cobriu. */
    public static int gastarSobrecarga(Player p, int gasto) {
        int s = FonteDados.sobrecarga(p);
        if (s <= 0 || gasto <= 0) return 0;
        int usa = Math.min(s, gasto);
        FonteDados.sobrecarga(p, s - usa);
        return usa;
    }

    private static String selos(Player p) {
        StringBuilder sb = new StringBuilder();
        for (var t : com.lanterna.baterias.Tropa.values()) if (FonteDados.temSelo(p, t.nome)) { if (sb.length() > 0) sb.append(','); sb.append(t.nome); }
        return sb.toString();
    }

    private static final java.util.Map<java.util.UUID, Boolean> AURA = new java.util.HashMap<>();

    private static void sincronizar(ServerPlayer p, @Nullable RingData ring) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new FontePackets.Estado(FonteDados.pressao(p), FonteDados.banho(p),
                FonteDados.sobrecarga(p), fonteDe(p.level()) != null, FonteDados.bloqueado(p), FonteDados.essencia(p),
                com.lanterna.construct.fonte.ArvoreDaLuz.nosDoAnel(p), selos(p)));
        boolean ligada = FonteDados.sobrecarga(p) > 0 && ring != null && ring.hasRing() && ring.powerActive();
        boolean era = AURA.getOrDefault(p.getUUID(), false);
        // liga/desliga na hora e repete a cada 2 s para quem acabou de chegar perto
        if (ligada != era || (ligada && p.tickCount % 40 == 0)) {
            AURA.put(p.getUUID(), ligada);
            ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new FontePackets.Aura(p.getId(), ring != null ? ring.color() : 0xFFFFFF, ligada));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        AURA.remove(e.getEntity().getUUID());
    }

    private Fonte() {}
}
