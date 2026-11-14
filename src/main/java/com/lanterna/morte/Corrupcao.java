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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
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
        // Lanternas Negros do mod (NPCs, como os de Ryut) também ceifam: assim dá para ter o Anel Preto jogando sozinho
        boolean negroNpc = event.getSource().getEntity() instanceof com.lanterna.especies.LanternaEntity le && le.tropa() == com.lanterna.corps.BuiltinCorps.BLACK;
        double chance = negro || negroNpc ? LanternaConfig.MORTE_CHANCE_CEIFADOR.get() : LanternaConfig.MORTE_CHANCE.get();
        // a Morte vai chegando perto: cada morte sem corromper soma 0,4% (até +10%)
        var raiz = vitima.getPersistentData();
        if (!raiz.contains(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)) raiz.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, new net.minecraft.nbt.CompoundTag());
        var tag = raiz.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        int perto = tag.getInt("lanterna_mortes_sem_corrupcao");
        chance += Math.min(0.10, perto * 0.004);
        if (vitima.getRandom().nextDouble() >= chance) {
            tag.putInt("lanterna_mortes_sem_corrupcao", perto + 1);
            return;
        }
        tag.putInt("lanterna_mortes_sem_corrupcao", 0);
        // 0.49.1: morto por um Lanterna Negro (jogador ou NPC) e corrompido, não passa pela linha da Morte:
        // volta já como Lanterna Negro, com o anel da Tropa de antes tingido de preto
        if (negro || negroNpc) tag.putBoolean(CONVERSAO, true);
        d.setMorte(1);
        LanternaMod.LOGGER.info("Lanterna: o anel de {} foi corrompido pela morte", vitima.getGameProfile().getName());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer p)) return;
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null || d.morte() != 1) return;
        var t = p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        if (t.getBoolean(CONVERSAO)) {
            t.remove(CONVERSAO);
            // o anel pode voltar para a mão um instante depois do renascer: converte no próximo segundo
            p.getPersistentData().putInt(CONVERTER_EM, p.tickCount + 20);
            d.setMorte(3);
            return;
        }
        corromper(p, d);
    }

    private static final String CONVERSAO = "lanterna_conversao_negra", CONVERTER_EM = "lanterna_converter_em";

    /**
     * Morto por um Lanterna Negro: ergue-se como Lanterna Negro. O anel da Tropa de antes fica preto, com rachaduras
     * brancas e o símbolo antigo; o traje continua o da Tropa de antes, em preto e branco. Os poderes são os da Morte.
     */
    public static void converter(ServerPlayer p, LoreData d) {
        RingData r = ModCapabilities.get(p).orElse(null);
        ItemStack anel = com.lanterna.power.EquipmentHelper.ring(p);
        ResourceLocation origem = !anel.isEmpty() ? com.lanterna.item.PowerRingItem.getCorpsVisual(anel)
                : r != null && r.hasRing() ? r.corps() : com.lanterna.corps.BuiltinCorps.GREEN.id();
        if (Supremos.preto(origem) || Supremos.supremo(origem)) origem = com.lanterna.corps.BuiltinCorps.GREEN.id();
        var negra = com.lanterna.corps.BuiltinCorps.BLACK;
        if (anel.isEmpty()) {
            anel = com.lanterna.item.PowerRingItem.create(negra.id(), negra.color(), -1);
            com.lanterna.item.PowerRingItem.setOwner(anel, p);
            com.lanterna.power.EquipmentHelper.equipOrGive(p, RingData.RING_SLOT, anel);
        } else {
            anel.getOrCreateTag().putString("Corps", negra.id().toString());
            anel.getOrCreateTag().putInt("Color", negra.color());
        }
        com.lanterna.item.PowerRingItem.setCorrupcao(anel, 2, origem);
        d.setMorte(3);
        d.setUltimaCeifa(System.currentTimeMillis());
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 8, 0, false, false));
        p.level().playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1f, 0.5f);
        p.serverLevel().sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 1, p.getZ(), 90, 0.7, 1.2, 0.7, 0.04);
        p.serverLevel().sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1, p.getZ(), 60, 0.5, 1, 0.5, 0.02);
        p.sendSystemMessage(Component.translatable("morte.lanterna.convertido_1").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
        p.sendSystemMessage(Component.translatable("morte.lanterna.convertido_2").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        for (ServerPlayer o : p.server.getPlayerList().getPlayers())
            if (o != p) o.sendSystemMessage(Component.translatable("morte.lanterna.convertido_servidor", p.getDisplayName()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        LanternaMod.LOGGER.info("Lanterna: {} foi convertido em Lanterna Negro (era {})", p.getGameProfile().getName(), origem);
    }

    /** Marca (ou limpa) a corrupção do anel que o jogador está usando. Comando de teste e linha da Morte. */
    public static void marcarAnel(ServerPlayer p, int nivel) {
        ItemStack anel = com.lanterna.power.EquipmentHelper.ring(p);
        if (anel.isEmpty()) return;
        if (nivel == 2) {
            LoreData d = LoreCapability.get(p).orElse(null);
            if (d != null) converter(p, d);
            return;
        }
        ResourceLocation origem = com.lanterna.item.PowerRingItem.getOrigem(anel);
        if (nivel == 0 && com.lanterna.item.PowerRingItem.getCorrupcao(anel) > 0) LoreCapability.get(p).ifPresent(d -> d.setMorte(0));
        if (nivel == 0 && origem != null && com.lanterna.item.PowerRingItem.getCorrupcao(anel) == 2) {
            // desfaz a conversão: volta a ser o anel da Tropa de antes
            anel.getOrCreateTag().putString("Corps", origem.toString());
            anel.getOrCreateTag().putInt("Color", com.lanterna.corps.BuiltinCorps.colorOf(origem));
        }
        com.lanterna.item.PowerRingItem.setCorrupcao(anel, nivel, null);
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
            // o Anel Preto de verdade nasceu: o anel antigo deixa de mostrar a corrupção
            ItemStack anel = com.lanterna.power.EquipmentHelper.ring(p);
            if (!anel.isEmpty() && com.lanterna.item.PowerRingItem.getCorrupcao(anel) == 1) com.lanterna.item.PowerRingItem.setCorrupcao(anel, 0, null);
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
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        // conversão pendente (logo depois de renascer)
        var raiz = p.getPersistentData();
        if (raiz.contains(CONVERTER_EM) && p.tickCount >= raiz.getInt(CONVERTER_EM)) {
            raiz.remove(CONVERTER_EM);
            LoreData dc = LoreCapability.get(p).orElse(null);
            if (dc != null) converter(p, dc);
        }
        // o anel de quem está corrompido mostra a corrupção (também em mundos de antes desta versão)
        if (p.tickCount % 40 == 7) {
            LoreData dm = LoreCapability.get(p).orElse(null);
            ItemStack anel = com.lanterna.power.EquipmentHelper.ring(p);
            if (dm != null && dm.morte() == 2 && !anel.isEmpty() && com.lanterna.item.PowerRingItem.getCorrupcao(anel) == 0
                    && !Supremos.preto(com.lanterna.item.PowerRingItem.getCorps(anel)) && !Supremos.supremo(com.lanterna.item.PowerRingItem.getCorps(anel)))
                com.lanterna.item.PowerRingItem.setCorrupcao(anel, 1, null);
        }
        if (p.tickCount % 6000 != 1234) return;
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null || d.morte() != 2) return;
        p.sendSystemMessage(Component.translatable("morte.lanterna.sussurro." + p.getRandom().nextInt(4)).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 3, 0, false, false));
    }

    private Corrupcao() {}
}
