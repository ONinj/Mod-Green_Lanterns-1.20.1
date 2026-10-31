package com.lanterna.guilda;

import com.lanterna.LanternaConfig;
import com.lanterna.cidade.Kit;
import com.lanterna.cidade.Obra;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.mission.LoreCapability;
import com.lanterna.mission.LoreData;
import com.lanterna.space.ProceduralPlanets;
import com.lanterna.space.SpaceBodies;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Planetas das Tropas NPC: alguns planetas gerados (raros, ~7%) já são de uma Tropa — base pronta, minerador, general
 * e defensores da cor dela. Dá para invadir como um planeta de guilda; quem invade vira inimigo daquela Tropa
 * (os Lanternas dela atacam você em qualquer lugar e as cidades dela não atendem mais).
 * A defesa de um planeta de Tropa se recompõe sozinha. Depois de tomado, o planeta nunca mais volta a ser da Tropa.
 */
public final class TropasNpc {
    /** Tropas que podem ter planetas (o Branco não tem exército). */
    private static final BuiltinCorps[] TROPAS = {BuiltinCorps.GREEN, BuiltinCorps.YELLOW, BuiltinCorps.RED, BuiltinCorps.BLUE,
            BuiltinCorps.VIOLET, BuiltinCorps.INDIGO, BuiltinCorps.ORANGE, BuiltinCorps.BLACK};
    private static final int DEFESA_BASE = 10;
    private static final long RECOMPOR_MS = 10 * 60_000L;

    /** De qual Tropa é este planeta gerado (sempre a mesma resposta para a mesma semente), ou null. */
    @Nullable
    public static BuiltinCorps donoDe(SpaceBodies.Body b) {
        if (b.procedural() == null) return null;
        long h = b.procedural().seed() * 0x9E3779B97F4A7C15L ^ 0x5DEECE66DL;
        h ^= (h >>> 29);
        double r = (h & 0xFFFFFF) / (double) 0x1000000;
        if (r >= LanternaConfig.TROPAS_CHANCE_PLANETA.get()) return null;
        return TROPAS[(int) ((h >>> 24) & 0x7FFFFFFF) % TROPAS.length];
    }

    public static UUID idDa(BuiltinCorps c) {
        return UUID.nameUUIDFromBytes(("lanterna:tropa:" + c.id().getPath()).getBytes());
    }

    /** A "guilda" que representa a Tropa (criada na primeira vez). */
    public static Guilda guildaDa(Guildas gs, BuiltinCorps c) {
        UUID id = idDa(c);
        Guilda g = gs.guildas.get(id);
        if (g == null) {
            g = new Guilda(id, nome(c), id, "Conselho da Tropa", c.id().getPath());
            g.npc = true;
            g.simbolo = simbolo(c);
            gs.guildas.put(id, g);
            gs.mudou();
        }
        return g;
    }

    private static String nome(BuiltinCorps c) {
        return switch (c) {
            case GREEN -> "Tropa Verde";
            case YELLOW -> "Tropa Amarela";
            case RED -> "Tropa Vermelha";
            case BLUE -> "Tropa Azul";
            case VIOLET -> "Tropa Violeta";
            case INDIGO -> "Tropa Índigo";
            case ORANGE -> "Tropa Laranja";
            case BLACK -> "Tropa Negra";
            default -> "Tropa";
        };
    }

    /** Símbolo geométrico simples por Tropa (só formas: nada de emblema oficial). */
    private static byte[] simbolo(BuiltinCorps c) {
        byte[] s = new byte[256];
        int cor = switch (c) {
            case GREEN -> 9; case YELLOW -> 7; case RED -> 5; case BLUE -> 11; case VIOLET -> 14; case INDIGO -> 13; case ORANGE -> 6;
            default -> 3;
        };
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double dx = x - 7.5, dy = y - 7.5, d = Math.hypot(dx, dy);
            boolean on = switch (c) {
                case GREEN -> Math.max(Math.abs(dx), Math.abs(dy)) > 5 && Math.max(Math.abs(dx), Math.abs(dy)) < 7 || d < 2;
                case YELLOW -> Math.abs(dx) + Math.abs(dy) < 7.5 && Math.abs(dx) + Math.abs(dy) > 5 || (Math.abs(dx) < 1 && dy < 3);
                case RED -> d < 7 && d > 5 || (Math.abs(dx) < 1 && Math.abs(dy) < 4);
                case BLUE -> d < 7 && Math.abs(dy) < 1.2 || Math.abs(dx) < 1.2 && d < 7 || d < 3 && d > 1.8;
                case VIOLET -> Math.abs(dx) + Math.abs(dy) * 0.8 < 6.5 && y < 13 && !(d < 2.5);
                case INDIGO -> Math.abs(dy - Math.abs(dx) * 0.9 + 1) < 1.2 && Math.abs(dx) < 7 || d < 1.6;
                case ORANGE -> d < 7 && d > 4.5 && dy > -2 || d < 2.2;
                case BLACK -> d < 6.5 && d > 4.8 || (Math.abs(dx) > 1.5 && Math.abs(dx) < 3.5 && Math.abs(dy + 1) < 1.5);
                default -> false;
            };
            s[y * 16 + x] = (byte) (on ? cor : 0);
        }
        return s;
    }

    // ===================== Surgimento e base =====================

    /** Chamado a cada 2 s: registra os planetas de Tropa perto dos jogadores e constrói a base de quem chegou perto. */
    public static void verificar(MinecraftServer server) {
        if (LanternaConfig.TROPAS_CHANCE_PLANETA.get() <= 0) return;
        Guildas gs = Guildas.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension().equals(SpaceBodies.SPACE)) {
                for (SpaceBodies.Body b : ProceduralPlanets.near(p.position(), 1)) registrar(gs, b);
            } else {
                SpaceBodies.Body b = SpaceBodies.bodyAt(p.level().dimension(), p.getX(), p.getZ());
                if (b == null || b.procedural() == null) continue;
                Conquista c = registrar(gs, b);
                if (c != null && c.daTropa && !c.construida && !Obra.ocupada()
                        && p.blockPosition().distSqr(c.bandeira.atY(p.getBlockY())) < 200 * 200) construir(p.serverLevel(), gs, c);
            }
        }
    }

    @Nullable
    private static Conquista registrar(Guildas gs, SpaceBodies.Body b) {
        Conquista c = gs.conquistas.get(b.id());
        if (c != null || gs.liberados.contains(b.id())) return c;
        BuiltinCorps dono = donoDe(b);
        if (dono == null || b.dimension() == null) return null;
        Guilda g = guildaDa(gs, dono);
        // a base fica perto de onde se chega no planeta (o centro da região), deslocada para não cair em cima de quem pousa
        BlockPos pos = new BlockPos(b.procedural().regionX() + 48, 0, b.procedural().regionZ() + 48);
        c = new Conquista(b.id(), g.id, b.dimension(), pos);
        c.daTropa = true;
        c.construida = false;
        c.defesa = DEFESA_BASE + (int) (Math.abs(b.procedural().seed()) % 7);
        c.conquistado = System.currentTimeMillis();
        c.ultimaRecomposicao = c.conquistado;
        gs.conquistas.put(b.id(), c);
        gs.mudou();
        return c;
    }

    private static void construir(ServerLevel level, Guildas gs, Conquista c) {
        Guilda g = gs.guildas.get(c.guilda);
        if (g == null) return;
        int y = Kit.altura(level, c.bandeira.getX(), c.bandeira.getZ());
        if (y <= level.getMinBuildHeight() + 2) y = 70;
        c.bandeira = new BlockPos(c.bandeira.getX(), y, c.bandeira.getZ());
        c.construida = true;
        gs.mudou();
        BaseGuilda.construir(level, c.bandeira, g, c.planeta);
    }

    /** Defesa de Tropa volta aos poucos (1 a cada 10 min). */
    static boolean recompor(Conquista c, long agora) {
        int alvo = DEFESA_BASE + 4;
        if (c.defesa >= alvo || Invasao.ativa(c.planeta) != null) {
            c.ultimaRecomposicao = agora;
            return false;
        }
        if (agora - c.ultimaRecomposicao < RECOMPOR_MS) return false;
        c.defesa++;
        c.ultimaRecomposicao = agora;
        return true;
    }

    // ===================== Inimizade =====================

    @Nullable
    public static BuiltinCorps tropaDe(Guilda g) {
        if (g == null || !g.npc) return null;
        for (BuiltinCorps c : TROPAS) if (c.id().getPath().equals(g.tropa)) return c;
        return null;
    }

    /** O jogador atacou um planeta desta Tropa: ela vira inimiga dele. */
    public static void declararInimigo(ServerPlayer p, BuiltinCorps c) {
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null || d.tropasHostis().contains(c.id().getPath())) return;
        d.addTropaHostil(c.id().getPath());
        p.sendSystemMessage(Component.translatable("guilda.lanterna.tropa_inimiga", nome(c))
                .withStyle(s -> s.withColor(c.color()).withBold(true)));
    }

    /** Esta Tropa é inimiga pessoal do jogador? */
    public static boolean hostil(Player p, @Nullable BuiltinCorps c) {
        if (c == null) return false;
        LoreData d = LoreCapability.get(p).orElse(null);
        return d != null && d.tropasHostis().contains(c.id().getPath());
    }

    private TropasNpc() {}
}
