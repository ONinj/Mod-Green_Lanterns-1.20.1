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
import net.minecraft.nbt.CompoundTag;
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
        // 0.49.6: morto pelo Arauto da Morte (dono do Anel Preto), o anel se corrompe sempre (100%), em qualquer nível,
        // mesmo recém-purificado ou já corrompido: volta como Portador Negro
        if (ceifador != null && AnelPreto.arauto(ceifador) && !Supremos.supremo(ring.corps()) && (d.morte() == 0 || d.morte() == 2)) {
            CompoundTag tg = persist(vitima);
            tg.putInt("lanterna_mortes_sem_corrupcao", 0);
            tg.putBoolean(CONVERSAO, true);
            d.setMorte(1);
            LanternaMod.LOGGER.info("Lanterna: {} foi ceifado pelo Arauto da Morte", vitima.getGameProfile().getName());
            return;
        }
        if (d.morte() != 0 || Supremos.supremo(ring.corps()) || Nivel.nivel(vitima) < LanternaConfig.MORTE_NIVEL_MIN.get()) return;
        // recém-purificado: as próximas mortes não corrompem
        CompoundTag prot = vitima.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        if (prot.getInt(PROTEGIDO) > 0) {
            prot.putInt(PROTEGIDO, prot.getInt(PROTEGIDO) - 1);
            return;
        }
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

    /** Estado da linha da Morte de quem virou Portador Negro (não é o Arauto, não forja o Anel Preto). */
    public static final int PORTADOR = 4;
    /** A luz do Portador Negro: um preto que ainda aparece no escuro. */
    public static final int COR_PORTADOR = 0x2C2C34;

    /**
     * Corrompido pelo Arauto da Morte (ou por um Lanterna Negro de Ryut): vira Portador Negro (0.49.3). Continua da
     * própria Tropa, com os mesmos poderes, mas tudo fica preto (anel rachado, traje, construtos), ganha só os dois dons
     * menores da Morte (Farejar e Drenar Vida) e um pouco mais de força, bem menos que o Arauto.
     */
    public static void converter(ServerPlayer p, LoreData d) {
        RingData r = ModCapabilities.get(p).orElse(null);
        ItemStack anel = com.lanterna.power.EquipmentHelper.ring(p);
        ResourceLocation tropa = !anel.isEmpty() ? com.lanterna.item.PowerRingItem.getCorpsVisual(anel)
                : r != null && r.hasRing() ? r.corps() : com.lanterna.corps.BuiltinCorps.GREEN.id();
        if (Supremos.supremo(tropa)) {
            p.sendSystemMessage(Component.translatable("morte.lanterna.portador_recusa").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return;
        }
        if (anel.isEmpty()) {
            anel = com.lanterna.item.PowerRingItem.create(tropa, com.lanterna.corps.BuiltinCorps.colorOf(tropa), -1);
            com.lanterna.item.PowerRingItem.setOwner(anel, p);
            com.lanterna.power.EquipmentHelper.equipOrGive(p, RingData.RING_SLOT, anel);
        } else {
            anel.getOrCreateTag().putString("Corps", tropa.toString());
        }
        com.lanterna.item.PowerRingItem.setCorrupcao(anel, 2, tropa);
        // as linhas da Purificação e da Morte acabam aqui
        for (String k : new java.util.ArrayList<>(d.active().keySet()))
            if (k.startsWith("purif_") || k.startsWith("morte_")) MissionManager.abandon(p, k);
        persist(p).remove(TEMPO);
        d.setMorte(PORTADOR);
        d.setUltimaCeifa(System.currentTimeMillis());
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 8, 0, false, false));
        p.level().playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1f, 0.5f);
        p.serverLevel().sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 1, p.getZ(), 90, 0.7, 1.2, 0.7, 0.04);
        p.serverLevel().sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1, p.getZ(), 60, 0.5, 1, 0.5, 0.02);
        p.sendSystemMessage(Component.translatable("morte.lanterna.convertido_1").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
        p.sendSystemMessage(Component.translatable("morte.lanterna.convertido_2").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        for (ServerPlayer o : p.server.getPlayerList().getPlayers())
            if (o != p) o.sendSystemMessage(Component.translatable("morte.lanterna.convertido_servidor", p.getDisplayName()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        LanternaMod.LOGGER.info("Lanterna: {} virou Portador Negro ({})", p.getGameProfile().getName(), tropa);
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
        if (nivel == 0 && origem != null && Supremos.preto(com.lanterna.item.PowerRingItem.getCorps(anel))) {
            // anel convertido da 0.49.1 (que virava preto): volta a ser o anel da Tropa de antes
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
        comecarPurificacao(p, d);
    }

    // ===================== Purificação (0.49.6) =====================

    private static final String TEMPO = "lanterna_corrupcao_tempo", AVISOS = "lanterna_corrupcao_avisos", PROTEGIDO = "lanterna_purificado";

    /** Começa a linha da Purificação e o prazo (só conta enquanto o jogador está jogando). */
    static void comecarPurificacao(ServerPlayer p, LoreData d) {
        CompoundTag t = persist(p);
        t.putLong(TEMPO, 0);
        t.putInt(AVISOS, 0);
        if (!d.isActive("purif_1") && !algumaAtiva(d, "purif_")) {
            d.completed().removeIf(k -> k.startsWith("purif_"));
            MissionManager.start(p, "purif_1");
        }
        double h = LanternaConfig.PURIFICACAO_HORAS.get();
        if (h > 0) p.sendSystemMessage(Component.translatable("morte.lanterna.purif_escolha", horas(h)).withStyle(s -> s.withColor(0xCFE8FF)));
    }

    private static boolean algumaAtiva(LoreData d, String prefixo) {
        return d.active().keySet().stream().anyMatch(k -> k.startsWith(prefixo));
    }

    private static CompoundTag persist(ServerPlayer p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)) raiz.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, new CompoundTag());
        return raiz.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
    }

    private static String horas(double h) {
        long min = Math.max(0, Math.round(h * 60));
        return min >= 60 ? (min / 60) + "h" + (min % 60 == 0 ? "" : String.format("%02d", min % 60)) : min + " min";
    }

    /** Fim da linha da Purificação: o anel volta a ser limpo e fica protegido por um tempo. */
    public static void purificar(ServerPlayer p, LoreData d) {
        d.setMorte(0);
        for (String k : new java.util.ArrayList<>(d.active().keySet())) if (k.startsWith("morte_") || k.startsWith("purif_")) MissionManager.abandon(p, k);
        ItemStack anel = com.lanterna.power.EquipmentHelper.ring(p);
        if (!anel.isEmpty()) com.lanterna.item.PowerRingItem.setCorrupcao(anel, 0, null);
        CompoundTag t = persist(p);
        t.remove(TEMPO);
        t.remove(AVISOS);
        t.putInt(PROTEGIDO, 10);   // as próximas 10 mortes não corrompem
        t.putInt("lanterna_mortes_sem_corrupcao", 0);
        p.removeEffect(MobEffects.DARKNESS);
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 20, 1));
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 1.2f);
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 1.4f);
        p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 120, 0.6, 1.2, 0.6, 0.08);
        p.sendSystemMessage(Component.translatable("morte.lanterna.purificado_1").withStyle(s -> s.withColor(0xCFE8FF).withBold(true)));
        p.sendSystemMessage(Component.translatable("morte.lanterna.purificado_2").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        for (ServerPlayer o : p.server.getPlayerList().getPlayers())
            if (o != p) o.sendSystemMessage(Component.translatable("morte.lanterna.purificado_servidor", p.getDisplayName()).withStyle(s -> s.withColor(0xCFE8FF).withItalic(true)));
    }

    /** O prazo acabou (ou desistiu): a Morte o toma de vez. */
    static void tomado(ServerPlayer p, LoreData d) {
        p.sendSystemMessage(Component.translatable("morte.lanterna.tomado").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
        converter(p, d);
    }

    /** A cada segundo, para quem está corrompido: conta o prazo, avisa e, no fim, a Morte o toma. */
    private static void prazo(ServerPlayer p, LoreData d) {
        // quem já seguia a linha da Morte e ficou sem o Anel Preto (outro é o Arauto) vira Portador Negro
        if (d.isActive("morte_8") && !AnelPreto.livre(p)) {
            p.sendSystemMessage(Component.translatable("morte.lanterna.anel_unico", AnelPreto.nomeDono(p.server)).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            tomado(p, d);
            return;
        }
        double h = LanternaConfig.PURIFICACAO_HORAS.get();
        if (h <= 0) return;
        // quem abraçou a Morte de verdade (já perto de forjar o Anel Preto livre) não é tomado pelo prazo
        if ((d.isActive("morte_6") || d.isActive("morte_7") || d.isActive("morte_8")) && AnelPreto.dono(p.server) == null) return;
        // mundo de antes desta versão: quem já estava corrompido ganha a linha e o prazo agora
        if (!algumaAtiva(d, "purif_") && !persist(p).contains(TEMPO)) { comecarPurificacao(p, d); return; }
        CompoundTag t = persist(p);
        long limite = (long) (h * 3600 * 20);
        long agora = t.getLong(TEMPO) + 20;
        t.putLong(TEMPO, agora);
        int avisos = t.getInt(AVISOS);
        double[] marcas = {0.5, 0.75, 0.9, 0.97};
        for (int i = 0; i < marcas.length; i++) {
            if ((avisos & (1 << i)) == 0 && agora >= limite * marcas[i]) {
                t.putInt(AVISOS, avisos | (1 << i));
                p.sendSystemMessage(Component.translatable("morte.lanterna.purif_prazo", horas((limite - agora) / 72000.0)).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                p.level().playSound(null, p.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1f, 0.5f);
            }
        }
        if (agora >= limite) tomado(p, d);
    }

    /** Quanto falta (em horas) para a Morte tomar o jogador, ou -1. */
    public static double horasRestantes(ServerPlayer p) {
        CompoundTag t = persist(p);
        if (!t.contains(TEMPO)) return -1;
        return Math.max(0, LanternaConfig.PURIFICACAO_HORAS.get() - t.getLong(TEMPO) / 72000.0);
    }

    /** Ganchos de fim de missão da linha da Morte. */
    public static void aoConcluir(ServerPlayer p, LoreData d, Mission m) {
        if (m.id().equals("purif_5") && d.morte() == 2) purificar(p, d);
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
        // o Anel Preto é um só por mundo: se já tem dono, ninguém mais forja (ForgeRituals avisa quem é)
        return d != null && (d.isActive("morte_8") || d.morte() == 3) && AnelPreto.livre(p);
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
        if (p.tickCount % 20 == 11) {
            LoreData dp = LoreCapability.get(p).orElse(null);
            if (dp != null && dp.morte() == 2) prazo(p, dp);
        }
        // o anel de quem está corrompido mostra a corrupção (também em mundos de antes desta versão)
        if (p.tickCount % 40 == 7) {
            ItemStack an = com.lanterna.power.EquipmentHelper.ring(p);
            // 0.49.1 deixava o convertido com o anel da Tropa Negra: agora ele é Portador Negro da própria Tropa
            ResourceLocation org = com.lanterna.item.PowerRingItem.getOrigem(an);
            if (!an.isEmpty() && com.lanterna.item.PowerRingItem.getCorrupcao(an) == 2 && org != null && Supremos.preto(com.lanterna.item.PowerRingItem.getCorps(an))) {
                an.getOrCreateTag().putString("Corps", org.toString());
                an.getOrCreateTag().putInt("Color", com.lanterna.corps.BuiltinCorps.colorOf(org));
                LoreCapability.get(p).ifPresent(dd -> { if (dd.morte() == 3) dd.setMorte(PORTADOR); });
            }
            // mundo de antes desta versão: o primeiro com o Anel Preto de verdade fica como o dono dele
            if (AnelPreto.dono(p.server) == null && AnelPreto.arauto(p)) AnelPreto.registrar(p);
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
