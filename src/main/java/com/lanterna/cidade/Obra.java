package com.lanterna.cidade;

import com.lanterna.LanternaMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Construções grandes em etapas: cada etapa é um pedaço (um prédio, uma faixa de terreno) e o servidor faz
 * algumas por tick, sem passar de ~15 ms. Assim a cidade e o palácio aparecem em poucos segundos sem travar.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Obra {
    private record Etapa(ServerLevel level, Consumer<ServerLevel> passo, java.util.function.Predicate<ServerLevel> pronto) {}

    /** Ticket que mantém os pedaços da obra carregados (some sozinho depois de 20 s). */
    private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> TICKET =
            net.minecraft.server.level.TicketType.create("lanterna_obra", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 400);

    private static final Deque<Etapa> FILA = new ArrayDeque<>();

    public static void etapa(ServerLevel level, Consumer<ServerLevel> passo) {
        FILA.add(new Etapa(level, passo, null));
    }

    /**
     * Pede os pedaços do mundo (gerados nas threads de geração, sem travar o servidor) e segura a fila até estarem
     * todos prontos.
     */
    public static void carregar(ServerLevel level, java.util.List<net.minecraft.world.level.ChunkPos> pedacos) {
        FILA.add(new Etapa(level, l -> {}, l -> {
            for (var c : pedacos) l.getChunkSource().addRegionTicket(TICKET, c, 1, c);
            return true;
        }));
        int[] espera = {0};
        FILA.add(new Etapa(level, l -> {}, l -> {
            boolean tudo = true;
            for (var c : pedacos) if (l.getChunkSource().getChunkNow(c.x, c.z) == null) {
                tudo = false;
                if (espera[0] % 20 == 0) l.getChunkSource().addRegionTicket(TICKET, c, 1, c); // renova o ticket
            }
            // não espera para sempre: depois de ~2 min segue (o pedaço que faltar é carregado na hora)
            return tudo || ++espera[0] > 2400;
        }));
    }

    public static boolean ocupada() {
        return !FILA.isEmpty();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FILA.isEmpty()) return;
        long fim = System.nanoTime() + 15_000_000L;
        while (!FILA.isEmpty() && System.nanoTime() < fim) {
            Etapa e = FILA.peek();
            try {
                if (e.pronto != null && !e.pronto.test(e.level)) break; // esperando o mundo carregar
                FILA.poll();
                long t0 = System.nanoTime();
                e.passo.accept(e.level);
                long ms = (System.nanoTime() - t0) / 1_000_000L;
                if (ms > 250) LanternaMod.LOGGER.debug("Lanterna: etapa de obra lenta ({} ms)", ms);
            } catch (Exception ex) {
                LanternaMod.LOGGER.error("Lanterna: falha numa etapa de construção", ex);
            }
        }
    }

    private Obra() {}
}
