package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.mission.MissionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 0.59.0: as versões de teste da saga do Necron. Cada jar de teste é compilado com uma fase (gradle build -Pfase=N,
 * gravada em lanterna_fase.txt) e só libera a saga até ela:
 *
 *   1  Capítulos 1 e 2: a Vila Aurora, a cratera e o primeiro encontro com o Necron
 *   2  Capítulos 3 e 4: Duas Luzes (o segundo anel), o treino, o duelo e a defesa das Âncoras
 *   3  Capítulos 5 e 6: o socorro às outras Tropas e as Ruínas
 *   4  Capítulos 7 e 8: a Terra escurece e a batalha final (e a Forma do Necron, do Arauto da Morte)
 *   5  Capítulo 9, o epílogo, a Tentação e a linha corrompida: a saga inteira
 *
 * Fase 0 (o jar normal) libera tudo. Uma missão que tentou começar e estava trancada fica guardada no jogador e começa
 * sozinha quando ele entrar no mundo com uma versão que a libere.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Fases {
    public static final int LIBERADA = ler();
    private static final String PENDENTES = "lanterna_fase_pendentes";

    private static int ler() {
        try (InputStream in = Fases.class.getResourceAsStream("/lanterna_fase.txt")) {
            if (in == null) return 0;
            int f = Integer.parseInt(new String(in.readAllBytes(), StandardCharsets.UTF_8).trim());
            if (f > 0) LanternaMod.LOGGER.info("Lanterna: versão de teste, saga do Necron liberada até a fase {}", f);
            return Math.max(0, f);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Em que fase de teste a missão entra. */
    public static int fase(String id) {
        if (!MissoesNecron.eDoNecron(id)) return 0;
        String r = id.substring("necron_".length());
        if (r.equals("espectro") || r.startsWith("c")) return 5;
        if (r.equals("7") || r.equals("8") || r.equals("9") || r.equals("10")) return 5;
        if (r.startsWith("5") || r.startsWith("6")) return 4;
        if (r.startsWith("s_") || r.startsWith("q_")) return 3;
        if (r.startsWith("3") || r.startsWith("4")) return 2;
        return 1;
    }

    public static boolean bloqueada(int fase) {
        return LIBERADA > 0 && fase > LIBERADA;
    }

    public static boolean bloqueada(String id) {
        return bloqueada(fase(id));
    }

    /** Chamado por MissionManager.start: true = não começa (e avisa). */
    public static boolean barrar(ServerPlayer p, String id) {
        if (!bloqueada(id)) return false;
        Set<String> ps = pendentes(p);
        if (ps.add(id)) {
            salvar(p, ps);
            p.sendSystemMessage(Component.translatable("lanterna.fase.fim", LIBERADA + 1).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return true;
    }

    /** A Forma do Necron chega na fase 4. */
    public static boolean barrarForma(ServerPlayer p) {
        if (!bloqueada(4)) return false;
        p.displayClientMessage(Component.translatable("lanterna.fase.forma", 4).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    @SubscribeEvent
    public static void entrou(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Set<String> ps = pendentes(p);
        if (ps.isEmpty()) return;
        Set<String> agora = new LinkedHashSet<>();
        for (String id : ps) if (!bloqueada(id)) agora.add(id);
        if (agora.isEmpty()) return;
        ps.removeAll(agora);
        salvar(p, ps);
        for (String id : agora) MissionManager.start(p, id);
    }

    private static Set<String> pendentes(ServerPlayer p) {
        Set<String> s = new LinkedHashSet<>();
        String v = p.getPersistentData().getString(PENDENTES);
        for (String x : v.split(",")) if (!x.isBlank()) s.add(x);
        return s;
    }

    private static void salvar(ServerPlayer p, Set<String> s) {
        CompoundTag t = p.getPersistentData();
        if (s.isEmpty()) t.remove(PENDENTES);
        else t.putString(PENDENTES, String.join(",", s));
    }

    private Fases() {}
}
