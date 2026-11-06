package com.lanterna.fonte;

import com.lanterna.LanternaMod;
import com.lanterna.baterias.Tropa;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.construct.Affinity;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.LoreCapability;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.Mission;
import com.lanterna.mission.MissionManager;
import com.lanterna.power.EquipmentHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * O Chamado da Fonte: o caminho para a Bateria Central reconhecer um Lanterna.
 *
 *   1  Confiança   afinidade Unido (Bonded), nível 20, respeito 6 com o povo do planeta e a bênção do líder
 *                  (em Oa, dos Guardiões)
 *   2  Provação    a prova da emoção da Tropa, na praça da Bateria (clique na Bateria para começar)
 *   3  Juramento   diante da Bateria, o Lanterna faz o juramento da Tropa e recebe o Selo da Fonte
 *
 * A linha começa sozinha quando o anel da Tropa chega a Unido.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FonteChamado {
    /** Afinidade Unido (Bonded). */
    public static final int AFINIDADE = 4;
    public static final int NIVEL = 20;
    /** Praça da Bateria na megacidade (para o marcador da missão). */
    public static final BlockPos PRACA = new BlockPos(-388, 0, 38);

    /** Tropa da Bateria do anel que o jogador usa (null: sem anel ou Tropa sem Bateria). */
    @Nullable
    public static Tropa tropaDoAnel(ServerPlayer p) {
        RingData r = ModCapabilities.get(p).orElse(null);
        if (r == null || !r.hasRing()) return null;
        for (Tropa t : Tropa.values()) if (t.corps().id().equals(r.corps())) return t;
        return null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 200 != 37) return;
        Tropa t = tropaDoAnel(p);
        if (t == null || FonteDados.temSelo(p, t.nome)) return;
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null) return;
        String um = "fonte_" + t.nome + "_1";
        if (d.isDone(um) || d.isActive(um) || d.isActive("fonte_" + t.nome + "_2") || d.isActive("fonte_" + t.nome + "_3")) return;
        if (Affinity.level(Affinity.points(EquipmentHelper.ring(p))) < AFINIDADE) return;
        MissionManager.start(p, um);
        p.sendSystemMessage(Component.translatable("fonte.lanterna.chamado", Component.translatable("bateria.lanterna." + t.nome))
                .withStyle(s -> s.withColor(t.cor).withBold(true)));
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 0.7f);
    }

    /** Clique na Bateria Central: começa a provação ou o juramento (quando é a hora). */
    public static boolean usarBateria(ServerPlayer p, BlockPos pos) {
        PlanetLore pl = PlanetLore.byDimension(p.level().dimension());
        Tropa t = pl == null ? null : Tropa.doPlaneta(pl);
        if (t == null) return false;
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null) return false;
        if (ProvaFonte.ativa(p)) { ProvaFonte.usarBateria(p); return true; }
        // o juramento do anel (primeira missão de quem acabou de ser escolhido)
        if (d.isActive(com.lanterna.juramento.Juramento.missao(t))) {
            if (tropaDoAnel(p) != t) { p.displayClientMessage(Component.translatable("fonte.lanterna.anel_errado"), true); return true; }
            com.lanterna.juramento.Juramento.abrir(p, com.lanterna.juramento.Juramento.ANEL, t, pos);
            return true;
        }
        if (d.isActive("fonte_" + t.nome + "_2")) {
            if (tropaDoAnel(p) != t) { p.displayClientMessage(Component.translatable("fonte.lanterna.anel_errado"), true); return true; }
            ProvaFonte.comecar(p, t, pos);
            return true;
        }
        if (d.isActive("fonte_" + t.nome + "_3")) {
            com.lanterna.juramento.Juramento.abrir(p, com.lanterna.juramento.Juramento.FONTE, t, pos);
            return true;
        }
        if (FonteDados.temSelo(p, t.nome)) p.displayClientMessage(Component.translatable("fonte.lanterna.reconhece").withStyle(s -> s.withColor(t.cor)), true);
        else p.displayClientMessage(Component.translatable("fonte.lanterna.nao_conhece"), true);
        return true;
    }

    // ===================== Juramento =====================

    private static final Map<UUID, int[]> JURANDO = new HashMap<>();
    private static final Map<UUID, Tropa> JURA_TROPA = new HashMap<>();

    private static void juramento(ServerPlayer p, Tropa t) {
        if (JURANDO.containsKey(p.getUUID())) return;
        JURANDO.put(p.getUUID(), new int[]{0});
        JURA_TROPA.put(p.getUUID(), t);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 0.6f);
    }

    @SubscribeEvent
    public static void onJura(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        int[] k = JURANDO.get(p.getUUID());
        if (k == null) return;
        Tropa t = JURA_TROPA.get(p.getUUID());
        k[0]++;
        // quatro versos, um a cada 3 s, depois a Bateria responde
        if (k[0] % 60 == 1 && k[0] / 60 < 4) {
            int verso = k[0] / 60 + 1;
            p.sendSystemMessage(Component.literal("✦ ").append(Component.translatable("juramento.lanterna." + t.nome + "." + verso))
                    .withStyle(s -> s.withColor(t.cor).withItalic(true)));
            p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.6f + verso * 0.15f);
        }
        if (k[0] >= 4 * 60 + 20) {
            JURANDO.remove(p.getUUID());
            JURA_TROPA.remove(p.getUUID());
            FonteDados.chamado(p, t.nome + ":juramento", 1);
            LoreData d = LoreCapability.get(p).orElse(null);
            if (d != null) MissionManager.evaluate(p, d);
        }
    }

    public static void aoConcluir(ServerPlayer p, LoreData d, Mission m) {
        if (!m.id().startsWith("fonte_") || !m.id().endsWith("_3")) return;
        String nome = m.id().substring(6, m.id().length() - 2);
        FonteDados.darSelo(p, nome);
        FonteDados.essencia(p, FonteDados.essencia(p) + 10);
        Tropa t = null;
        for (Tropa x : Tropa.values()) if (x.nome.equals(nome)) t = x;
        int cor = t != null ? t.cor : 0xFFFFFF;
        p.sendSystemMessage(Component.translatable("fonte.lanterna.selo", Component.translatable("bateria.lanterna." + nome))
                .withStyle(s -> s.withColor(cor).withBold(true)));
        p.sendSystemMessage(Component.translatable("fonte.lanterna.selo2"));
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1.1f);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        JURANDO.remove(e.getEntity().getUUID());
        JURA_TROPA.remove(e.getEntity().getUUID());
    }

    private FonteChamado() {}
}
