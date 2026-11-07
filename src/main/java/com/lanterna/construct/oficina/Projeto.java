package com.lanterna.construct.oficina;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

import java.util.Arrays;

/**
 * Um construto desenhado pelo jogador: cubinhos numa grade de 16x16x16 (x, y, z de 0 a 15; o "chão" do desenho é
 * y = 0 e o meio é x = z = 7,5), cada um com um tom (0 = cor do anel, 1 = mais claro, 2 = mais escuro, 3 = branco).
 *
 * A frente do desenho é -Z (o lado que fica virado para o alvo/para onde se olha). Numa arma, a mão segura o
 * desenho na parte de baixo (y = 0, no meio).
 */
public final class Projeto {
    public static final int N = 16;
    public static final int MAX = 900;
    public static final int SLOTS = 8;

    public String nome = "";
    public Comportamento comp = Comportamento.ARMA;
    /** Escala (0,5 a 2). */
    public float escala = 1f;
    /** x,y,z,tom por cubinho. */
    private byte[] cel = new byte[N * N * N];
    private int quantos;

    public Projeto() {}

    public Projeto copia() {
        Projeto p = new Projeto();
        p.nome = nome;
        p.comp = comp;
        p.escala = escala;
        p.cel = cel.clone();
        p.quantos = quantos;
        return p;
    }

    private static int idx(int x, int y, int z) {
        return (y * N + z) * N + x;
    }

    public static boolean dentro(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < N && y < N && z < N;
    }

    /** 0 = vazio; 1..4 = tom + 1. */
    public int get(int x, int y, int z) {
        return dentro(x, y, z) ? cel[idx(x, y, z)] : 0;
    }

    public boolean tem(int x, int y, int z) {
        return get(x, y, z) != 0;
    }

    public boolean set(int x, int y, int z, int tomMais1) {
        if (!dentro(x, y, z)) return false;
        int i = idx(x, y, z);
        int antes = cel[i];
        if (antes == tomMais1) return false;
        if (antes == 0 && tomMais1 != 0 && quantos >= MAX) return false;
        cel[i] = (byte) tomMais1;
        quantos += (antes == 0 ? 0 : -1) + (tomMais1 == 0 ? 0 : 1);
        return true;
    }

    public void limpar() {
        Arrays.fill(cel, (byte) 0);
        quantos = 0;
    }

    public int quantos() {
        return quantos;
    }

    public boolean vazio() {
        return quantos == 0;
    }

    /** Caixa ocupada: {minX, minY, minZ, maxX, maxY, maxZ} (inclusive) ou null se vazio. */
    public int[] limites() {
        if (quantos == 0) return null;
        int[] b = {N, N, N, -1, -1, -1};
        for (int y = 0; y < N; y++) for (int z = 0; z < N; z++) for (int x = 0; x < N; x++) {
            if (cel[idx(x, y, z)] == 0) continue;
            b[0] = Math.min(b[0], x); b[1] = Math.min(b[1], y); b[2] = Math.min(b[2], z);
            b[3] = Math.max(b[3], x); b[4] = Math.max(b[4], y); b[5] = Math.max(b[5], z);
        }
        return b;
    }

    /** Tamanho de um cubinho no mundo. */
    public float bloco() {
        return comp.tamanhoBloco * Mth.clamp(escala, 0.5f, 2f);
    }

    public int custo() {
        return comp.custoBase + quantos / 30;
    }

    /** Assinatura simples (para o cache da malha). */
    public int assinatura() {
        return Arrays.hashCode(cel) * 31 + comp.ordinal();
    }

    // ===================== Gravar =====================

    /** Formato compacto: lista de (x, y, z, tom+1). */
    public byte[] compactar() {
        byte[] out = new byte[quantos * 4];
        int k = 0;
        for (int y = 0; y < N; y++) for (int z = 0; z < N; z++) for (int x = 0; x < N; x++) {
            int v = cel[idx(x, y, z)];
            if (v == 0) continue;
            out[k++] = (byte) x; out[k++] = (byte) y; out[k++] = (byte) z; out[k++] = (byte) v;
        }
        return out;
    }

    public void descompactar(byte[] b) {
        limpar();
        for (int i = 0; i + 3 < b.length; i += 4) set(b[i], b[i + 1], b[i + 2], Mth.clamp(b[i + 3], 1, 4));
    }

    public CompoundTag salvar() {
        CompoundTag t = new CompoundTag();
        t.putString("nome", nome);
        t.putInt("comp", comp.ordinal());
        t.putFloat("escala", escala);
        t.putByteArray("cubos", compactar());
        return t;
    }

    public static Projeto ler(CompoundTag t) {
        Projeto p = new Projeto();
        p.nome = t.getString("nome");
        if (p.nome.length() > 24) p.nome = p.nome.substring(0, 24);
        p.comp = Comportamento.of(t.getInt("comp"));
        p.escala = t.contains("escala") ? Mth.clamp(t.getFloat("escala"), 0.5f, 2f) : 1f;
        p.descompactar(t.getByteArray("cubos"));
        return p;
    }

    public void escrever(FriendlyByteBuf b) {
        b.writeUtf(nome, 32);
        b.writeVarInt(comp.ordinal());
        b.writeFloat(escala);
        b.writeByteArray(compactar());
    }

    public static Projeto ler(FriendlyByteBuf b) {
        Projeto p = new Projeto();
        p.nome = b.readUtf(32);
        p.comp = Comportamento.of(b.readVarInt());
        p.escala = Mth.clamp(b.readFloat(), 0.5f, 2f);
        p.descompactar(b.readByteArray(MAX * 4 + 16));
        return p;
    }
}
