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
    private record Etapa(ServerLevel level, Consumer<ServerLevel> passo) {}

    private static final Deque<Etapa> FILA = new ArrayDeque<>();

    public static void etapa(ServerLevel level, Consumer<ServerLevel> passo) {
        FILA.add(new Etapa(level, passo));
    }

    public static boolean ocupada() {
        return !FILA.isEmpty();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FILA.isEmpty()) return;
        long fim = System.nanoTime() + 15_000_000L;
        while (!FILA.isEmpty() && System.nanoTime() < fim) {
            Etapa e = FILA.poll();
            try {
                e.passo.accept(e.level);
            } catch (Exception ex) {
                LanternaMod.LOGGER.error("Lanterna: falha numa etapa de construção", ex);
            }
        }
    }

    private Obra() {}
}
