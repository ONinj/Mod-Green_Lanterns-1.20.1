package com.lanterna.client.voz;

import com.lanterna.network.ModNetwork;
import com.lanterna.voz.Ligacoes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** O que o cliente sabe da ligação atual. */
public final class ClientLigacao {
    public record Pessoa(UUID id, String nome, boolean falando) {}

    public static int modo; // 0 nada, 1 chamando, 2 recebendo, 3 em ligação
    public static int segundos;
    public static long recebidoEm;
    public static boolean voz;
    public static List<Pessoa> membros = List.of(), tocando = List.of();
    private static long ultimoToque;

    public static void estado(Ligacoes.Estado e) {
        int antes = modo;
        modo = e.modo();
        segundos = e.segundos();
        recebidoEm = System.currentTimeMillis();
        voz = e.voz();
        membros = conv(e.membros());
        tocando = conv(e.tocando());
        var mc = Minecraft.getInstance();
        if (mc.player != null && antes != modo) {
            if (modo == 3) mc.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.5f);
            if (modo == 0 && antes != 0) mc.player.playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 0.8f, 0.6f);
        }
    }

    private static List<Pessoa> conv(List<String[]> l) {
        List<Pessoa> out = new ArrayList<>();
        for (String[] x : l) out.add(new Pessoa(UUID.fromString(x[0]), x[1], x[2].equals("1")));
        return out;
    }

    /** Outros na ligação (o holograma mostra eles). */
    public static List<Pessoa> outros() {
        var mc = Minecraft.getInstance();
        if (modo != 3 || mc.player == null) return List.of();
        List<Pessoa> o = new ArrayList<>();
        for (Pessoa p : membros) if (!p.id().equals(mc.player.getUUID())) o.add(p);
        return o;
    }

    public static int segundosAgora() {
        return segundos + (int) ((System.currentTimeMillis() - recebidoEm) / 1000);
    }

    /** Tecla do comunicador: atende quando está tocando (Shift recusa); senão abre o comunicador. */
    public static void tecla() {
        if (modo == 2) {
            ModNetwork.sendToServer(new Ligacoes.Acao(Screen.hasShiftDown() ? Ligacoes.RECUSAR : Ligacoes.ATENDER, new UUID(0, 0)));
            return;
        }
        Minecraft.getInstance().setScreen(new ComunicadorScreen());
    }

    public static void acao(int tipo, UUID alvo) {
        ModNetwork.sendToServer(new Ligacoes.Acao(tipo, alvo));
    }

    /** Toque de chamada (no cliente). */
    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) { modo = 0; return; }
        long agora = System.currentTimeMillis();
        if (modo == 2 && agora - ultimoToque > 1400) {
            ultimoToque = agora;
            mc.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.9f, 1.2f);
            mc.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7f, 1.6f);
        } else if (modo == 1 && agora - ultimoToque > 2200) {
            ultimoToque = agora;
            mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.4f, 0.8f);
        }
    }

    private ClientLigacao() {}
}
