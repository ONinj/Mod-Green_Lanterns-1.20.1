package com.lanterna.juramento;

import com.lanterna.LanternaMod;
import com.lanterna.baterias.Tropa;
import com.lanterna.fonte.FonteDados;
import com.lanterna.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Os juramentos falados. O próprio jogador recita, em voz alta, palavra por palavra, diante da Bateria Central:
 * o jogo só avança a palavra enquanto o microfone capta a voz (não confere o que foi dito).
 *
 *   anel    o juramento de quem acabou de ser escolhido: é a primeira missão do Lanterna (juramento_<corps>),
 *           antes de falar com o líder e com os Guardiões;
 *   fonte   o juramento do Chamado da Fonte (fonte_<tropa>_3).
 *
 * O texto vem do arquivo de idioma do cliente (português ou inglês): juramento_anel.lanterna.<tropa>.1..4 e
 * juramento.lanterna.<tropa>.1..4.
 */
public final class Juramento {
    public static final String ANEL = "anel", FONTE = "fonte";
    /** Distância máxima da Bateria para jurar. */
    private static final double PERTO = 24;

    private record Aberto(String tipo, Tropa tropa, BlockPos bateria, long desde) {}

    private static final Map<UUID, Aberto> ABERTOS = new HashMap<>();

    /** Missão do juramento do anel desta Tropa (null para Preto e Branco, que não têm Bateria Central). */
    public static String missao(Tropa t) {
        return "juramento_" + t.corps().id().getPath();
    }

    public static void abrir(ServerPlayer p, String tipo, Tropa t, BlockPos bateria) {
        ABERTOS.put(p.getUUID(), new Aberto(tipo, t, bateria.immutable(), p.serverLevel().getGameTime()));
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 0.6f);
        ModNetwork.sendTo(p, new Abrir(tipo, t.nome, t.cor));
    }

    static void concluido(ServerPlayer p, String tipo, String tropa, int palavras) {
        Aberto a = ABERTOS.remove(p.getUUID());
        if (a == null || !a.tipo().equals(tipo) || !a.tropa().nome.equals(tropa)) return;
        if (p.distanceToSqr(a.bateria().getX() + 0.5, p.getY(), a.bateria().getZ() + 0.5) > PERTO * PERTO * 4) return;
        // não dá para "jurar" em menos de 0,12 s por palavra
        long ticks = p.serverLevel().getGameTime() - a.desde();
        if (palavras <= 0 || ticks < palavras * 2L) return;
        Tropa t = a.tropa();
        ServerLevel l = p.serverLevel();
        BlockPos b = a.bateria();
        // a Bateria responde: coluna de luz e o anel brilha
        for (int y = 0; y < 40; y += 2)
            l.sendParticles(ParticleTypes.END_ROD, b.getX() + 0.5, b.getY() + y, b.getZ() + 0.5, 6, 0.6, 0.6, 0.6, 0.02);
        l.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
        l.playSound(null, b, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3f, 0.8f);
        l.playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.4f);
        if (tipo.equals(ANEL)) {
            FonteDados.chamado(p, t.nome + ":anel", 1);
            Component nome = Component.translatable("bateria.lanterna." + t.nome);
            for (ServerPlayer o : p.server.getPlayerList().getPlayers())
                o.sendSystemMessage(Component.translatable("juramento.lanterna.anel.todos", p.getDisplayName(), nome).withStyle(s -> s.withColor(t.cor)));
            com.lanterna.construct.Affinity.add(p, 400);
        } else {
            FonteDados.chamado(p, t.nome + ":juramento", 1);
        }
        LanternaMod.LOGGER.info("Lanterna: {} fez o juramento ({}) da Tropa {}", p.getGameProfile().getName(), tipo, t.nome);
    }

    public static void cancelado(ServerPlayer p) {
        ABERTOS.remove(p.getUUID());
    }

    // ===================== Pacotes =====================

    /** Servidor → cliente: abre a tela do juramento. */
    public record Abrir(String tipo, String tropa, int cor) {
        public static void encode(Abrir m, FriendlyByteBuf b) { b.writeUtf(m.tipo); b.writeUtf(m.tropa); b.writeInt(m.cor); }

        public static Abrir decode(FriendlyByteBuf b) { return new Abrir(b.readUtf(), b.readUtf(), b.readInt()); }

        public static void handle(Abrir m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.lanterna.client.juramento.JuramentoScreen.abrir(m.tipo, m.tropa, m.cor));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Cliente → servidor: recitou tudo (ou desistiu, com palavras = -1). */
    public record Feito(String tipo, String tropa, int palavras) {
        public static void encode(Feito m, FriendlyByteBuf b) { b.writeUtf(m.tipo); b.writeUtf(m.tropa); b.writeVarInt(m.palavras + 1); }

        public static Feito decode(FriendlyByteBuf b) { return new Feito(b.readUtf(), b.readUtf(), b.readVarInt() - 1); }

        public static void handle(Feito m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) {
                if (m.palavras < 0) cancelado(p);
                else concluido(p, m.tipo, m.tropa, m.palavras);
            }
            ctx.get().setPacketHandled(true);
        }
    }

    private Juramento() {}
}
