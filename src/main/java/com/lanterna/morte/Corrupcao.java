package com.lanterna.morte;

import com.lanterna.LanternaConfig;
import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.combate.Supremos;
import com.lanterna.mission.LoreCapability;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.Mission;
import com.lanterna.mission.MissionManager;
import com.lanterna.tropa.Nivel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * O Anel Preto não se forja, não se acha e não se pede. Ele escolhe quem morre.
 *
 * Toda vez que um portador de anel morre há uma chance mínima de o anel voltar corrompido. A partir daí o jogador
 * entra na linha "A Morte" (longa: almas, o Coletor, os Silentes, o Devorador de Ecos, sete túmulos...) e só no fim,
 * na Forja de Ryut, o Anel Preto nasce. O Lanterna Negro tem um dever: levar a morte a outros portadores — quem ele
 * mata tem uma chance bem maior de ser corrompido também. Se passar dias sem ceifar, o anel enfraquece.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Corrupcao {

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer vitima)) return;
        RingData ring = ModCapabilities.get(vitima).orElse(null);
        LoreData d = LoreCapability.get(vitima).orElse(null);
        if (ring == null || d == null || !ring.hasRing()) return;
        ServerPlayer ceifador = event.getSource().getEntity() instanceof ServerPlayer k && k != vitima ? k : null;
        LoreData dk = ceifador == null ? null : LoreCapability.get(ceifador).orElse(null);
        boolean negro = dk != null && dk.morte() >= 3 && ModCapabilities.get(ceifador).map(r -> r.hasRing() && Supremos.preto(r.corps())).orElse(false);
        // O dever cumprido: o Lanterna Negro ceifou um portador de anel
        if (negro) {
            dk.setUltimaCeifa(System.currentTimeMillis());
            ceifador.sendSystemMessage(Component.translatable("morte.lanterna.ceifou", vitima.getDisplayName()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        if (d.morte() != 0 || Supremos.supremo(ring.corps()) || Nivel.nivel(vitima) < LanternaConfig.MORTE_NIVEL_MIN.get()) return;
        double chance = negro ? LanternaConfig.MORTE_CHANCE_CEIFADOR.get() : LanternaConfig.MORTE_CHANCE.get();
        if (vitima.getRandom().nextDouble() >= chance) return;
        d.setMorte(1);
        LanternaMod.LOGGER.info("Lanterna: o anel de {} foi corrompido pela morte", vitima.getGameProfile().getName());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer p)) return;
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null || d.morte() != 1) return;
        corromper(p, d);
    }

    /** O anel volta corrompido: começa a linha da Morte. */
    public static void corromper(ServerPlayer p, LoreData d) {
        d.setMorte(2);
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 12, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.WITHER, 20 * 4, 0, false, false));
        p.level().playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.8f, 0.6f);
        p.serverLevel().sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 1, p.getZ(), 60, 0.6, 1, 0.6, 0.03);
        p.sendSystemMessage(Component.translatable("morte.lanterna.corrompido_1").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
        p.sendSystemMessage(Component.translatable("morte.lanterna.corrompido_2").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        for (ServerPlayer o : p.server.getPlayerList().getPlayers())
            if (o != p) o.sendSystemMessage(Component.translatable("morte.lanterna.aviso_servidor").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        if (!d.isDone("morte_1")) MissionManager.start(p, "morte_1");
    }

    /** Ganchos de fim de missão da linha da Morte. */
    public static void aoConcluir(ServerPlayer p, LoreData d, Mission m) {
        if (m.id().equals("morte_8")) {
            d.setMorte(3);
            d.setUltimaCeifa(System.currentTimeMillis());
            p.level().playSound(null, p.blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 1f, 0.7f);
            for (ServerPlayer o : p.server.getPlayerList().getPlayers())
                o.sendSystemMessage(Component.translatable("morte.lanterna.negro_ergueu", p.getDisplayName()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            p.sendSystemMessage(Component.translatable("morte.lanterna.dever", LanternaConfig.MORTE_DIAS_FOME.get()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    /** Pode forjar o Anel Preto agora? (só no último passo da linha da Morte) */
    public static boolean podeForjar(ServerPlayer p) {
        LoreData d = LoreCapability.get(p).orElse(null);
        return d != null && (d.isActive("morte_8") || d.morte() >= 3);
    }

    /** Quem está corrompido (ou já é Lanterna Negro) recolhe almas ao matar, mesmo sem o Anel Preto. */
    public static boolean colheAlmas(ServerPlayer p) {
        LoreData d = LoreCapability.get(p).orElse(null);
        return d != null && d.morte() >= 2;
    }

    /** Sussurros: a cada ~5 min o anel corrompido lembra o que quer. */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 6000 != 1234) return;
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null || d.morte() != 2) return;
        p.sendSystemMessage(Component.translatable("morte.lanterna.sussurro." + p.getRandom().nextInt(4)).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 3, 0, false, false));
    }

    private Corrupcao() {}
}
