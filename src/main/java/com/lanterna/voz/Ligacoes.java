package com.lanterna.voz;

import com.lanterna.LanternaMod;
import com.lanterna.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Ligações entre jogadores (o comunicador do anel). Até 4 pessoas por ligação.
 * Com o Simple Voice Chat instalado, quem está na ligação entra num grupo de voz escondido e todos se ouvem de
 * qualquer distância, em qualquer planeta. Sem ele, a ligação mostra o holograma e o painel, sem voz.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Ligacoes {
    public static final int MAX = 4;
    /** Tempo tocando até desistir (ticks). */
    private static final int TOQUE = 20 * 30;

    static final class Ligacao {
        final UUID id = UUID.randomUUID();
        final Set<UUID> membros = new LinkedHashSet<>();
        final Map<UUID, Long> tocando = new LinkedHashMap<>();
        long inicio;
        @Nullable UUID grupoVoz;
    }

    private static final Map<UUID, Ligacao> LIGACOES = new HashMap<>();
    private static final Map<UUID, Ligacao> DE = new HashMap<>();
    /** Último momento em que cada jogador falou no Voice Chat (thread do Voice Chat escreve). */
    static final Map<UUID, Long> FALOU = new ConcurrentHashMap<>();

    public static boolean svc() {
        return ModList.get().isLoaded("voicechat");
    }

    @Nullable
    private static Ligacao naLigacao(UUID p) {
        Ligacao l = DE.get(p);
        if (l != null) return l;
        for (Ligacao x : LIGACOES.values()) if (x.tocando.containsKey(p)) return x;
        return null;
    }

    // ===================== Ações =====================

    static void ligar(ServerPlayer de, UUID para) {
        MinecraftServer s = de.server;
        ServerPlayer alvo = s.getPlayerList().getPlayer(para);
        if (alvo == null || alvo == de) return;
        if (naLigacao(para) != null) { de.displayClientMessage(Component.translatable("ligacao.lanterna.ocupado", alvo.getDisplayName()), true); return; }
        Ligacao l = DE.get(de.getUUID());
        if (l == null) {
            if (naLigacao(de.getUUID()) != null) return; // está recebendo outra
            l = new Ligacao();
            l.membros.add(de.getUUID());
            LIGACOES.put(l.id, l);
            DE.put(de.getUUID(), l);
        }
        if (l.membros.size() + l.tocando.size() >= MAX) { de.displayClientMessage(Component.translatable("ligacao.lanterna.cheia"), true); return; }
        l.tocando.put(para, s.overworld().getGameTime());
        alvo.displayClientMessage(Component.translatable("ligacao.lanterna.recebendo", de.getDisplayName()), false);
        enviar(s, l);
    }

    static void atender(ServerPlayer p) {
        Ligacao l = naLigacao(p.getUUID());
        if (l == null || !l.tocando.containsKey(p.getUUID())) return;
        l.tocando.remove(p.getUUID());
        l.membros.add(p.getUUID());
        DE.put(p.getUUID(), l);
        if (l.inicio == 0) l.inicio = p.server.overworld().getGameTime();
        if (svc()) VozBridge.juntar(p.server, l);
        enviar(p.server, l);
    }

    static void recusar(ServerPlayer p) {
        Ligacao l = naLigacao(p.getUUID());
        if (l == null || l.tocando.remove(p.getUUID()) == null) return;
        for (UUID m : l.membros) {
            ServerPlayer o = p.server.getPlayerList().getPlayer(m);
            if (o != null) o.displayClientMessage(Component.translatable("ligacao.lanterna.recusou", p.getDisplayName()), true);
        }
        enviarVazio(p);
        talvezAcabar(p.server, l);
    }

    static void desligar(MinecraftServer s, UUID p) {
        Ligacao l = naLigacao(p);
        if (l == null) return;
        boolean tocava = l.tocando.remove(p) != null;
        if (!tocava) {
            l.membros.remove(p);
            DE.remove(p);
            if (svc()) VozBridge.sair(s, p);
        }
        ServerPlayer sp = s.getPlayerList().getPlayer(p);
        if (sp != null) enviarVazio(sp);
        talvezAcabar(s, l);
    }

    private static void talvezAcabar(MinecraftServer s, Ligacao l) {
        boolean acabou = l.membros.size() + l.tocando.size() < 2 || (l.membros.size() < 2 && l.inicio != 0);
        if (!acabou) { enviar(s, l); return; }
        for (UUID u : new ArrayList<>(l.membros)) {
            DE.remove(u);
            if (svc()) VozBridge.sair(s, u);
            ServerPlayer sp = s.getPlayerList().getPlayer(u);
            if (sp != null) enviarVazio(sp);
        }
        for (UUID u : l.tocando.keySet()) { ServerPlayer sp = s.getPlayerList().getPlayer(u); if (sp != null) enviarVazio(sp); }
        if (svc()) VozBridge.fim(s, l);
        LIGACOES.remove(l.id);
    }

    // ===================== Tick e saídas =====================

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || LIGACOES.isEmpty()) return;
        MinecraftServer s = e.getServer();
        long agora = s.overworld().getGameTime();
        for (Ligacao l : new ArrayList<>(LIGACOES.values())) {
            boolean mudou = l.tocando.entrySet().removeIf(en -> {
                if (agora - en.getValue() < TOQUE && s.getPlayerList().getPlayer(en.getKey()) != null) return false;
                ServerPlayer sp = s.getPlayerList().getPlayer(en.getKey());
                if (sp != null) enviarVazio(sp);
                return true;
            });
            if (mudou) { talvezAcabar(s, l); continue; }
            if (s.getTickCount() % 4 == 0) enviar(s, l);
        }
    }

    @SubscribeEvent
    public static void onSair(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) desligar(p.server, p.getUUID());
    }

    // ===================== Estado para os clientes =====================

    private static void enviar(MinecraftServer s, Ligacao l) {
        long agora = System.currentTimeMillis();
        List<String[]> membros = new ArrayList<>(), tocando = new ArrayList<>();
        for (UUID u : l.membros) membros.add(new String[]{u.toString(), nome(s, u), agora - FALOU.getOrDefault(u, 0L) < 300 ? "1" : "0"});
        for (UUID u : l.tocando.keySet()) tocando.add(new String[]{u.toString(), nome(s, u), "0"});
        int seg = l.inicio == 0 ? 0 : (int) ((s.overworld().getGameTime() - l.inicio) / 20);
        Set<UUID> todos = new LinkedHashSet<>(l.membros);
        todos.addAll(l.tocando.keySet());
        for (UUID u : todos) {
            ServerPlayer sp = s.getPlayerList().getPlayer(u);
            if (sp == null) continue;
            int modo = l.tocando.containsKey(u) ? 2 : l.inicio == 0 ? 1 : 3;
            ModNetwork.sendTo(sp, new Estado(modo, seg, svc(), membros, tocando));
        }
    }

    private static void enviarVazio(ServerPlayer sp) {
        ModNetwork.sendTo(sp, new Estado(0, 0, svc(), List.of(), List.of()));
    }

    private static String nome(MinecraftServer s, UUID u) {
        ServerPlayer p = s.getPlayerList().getPlayer(u);
        return p != null ? p.getGameProfile().getName() : "?";
    }

    // ===================== Pacotes =====================

    public static final int LIGAR = 0, ATENDER = 1, RECUSAR = 2, DESLIGAR = 3;

    /** Cliente → servidor. */
    public record Acao(int tipo, UUID alvo) {
        public static void encode(Acao m, FriendlyByteBuf b) { b.writeVarInt(m.tipo); b.writeUUID(m.alvo); }

        public static Acao decode(FriendlyByteBuf b) { return new Acao(b.readVarInt(), b.readUUID()); }

        public static void handle(Acao m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) switch (m.tipo) {
                case LIGAR -> ligar(p, m.alvo);
                case ATENDER -> atender(p);
                case RECUSAR -> recusar(p);
                case DESLIGAR -> desligar(p.server, p.getUUID());
                default -> {}
            }
            ctx.get().setPacketHandled(true);
        }
    }

    /** Servidor → cliente: 0 nada, 1 chamando, 2 recebendo, 3 em ligação. Pessoas = {uuid, nome, falando}. */
    public record Estado(int modo, int segundos, boolean voz, List<String[]> membros, List<String[]> tocando) {
        public static void encode(Estado m, FriendlyByteBuf b) {
            b.writeVarInt(m.modo); b.writeVarInt(m.segundos); b.writeBoolean(m.voz);
            for (List<String[]> l : List.of(m.membros, m.tocando)) {
                b.writeVarInt(l.size());
                for (String[] x : l) { b.writeUtf(x[0]); b.writeUtf(x[1]); b.writeBoolean(x[2].equals("1")); }
            }
        }

        public static Estado decode(FriendlyByteBuf b) {
            int modo = b.readVarInt(), seg = b.readVarInt();
            boolean voz = b.readBoolean();
            List<List<String[]>> ls = new ArrayList<>();
            for (int k = 0; k < 2; k++) {
                int n = b.readVarInt();
                List<String[]> l = new ArrayList<>();
                for (int i = 0; i < n; i++) l.add(new String[]{b.readUtf(), b.readUtf(), b.readBoolean() ? "1" : "0"});
                ls.add(l);
            }
            return new Estado(modo, seg, voz, ls.get(0), ls.get(1));
        }

        public static void handle(Estado m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.lanterna.client.voz.ClientLigacao.estado(m));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Para o teste automático e comandos. */
    public static void ligarPorComando(ServerPlayer de, ServerPlayer para, boolean atenderJa) {
        ligar(de, para.getUUID());
        if (atenderJa) atender(para);
    }

    /** Teste automático: marca alguém como falando. */
    public static void marcarFala(UUID u) {
        FALOU.put(u, System.currentTimeMillis());
    }

    private Ligacoes() {}
}
