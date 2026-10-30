package com.lanterna.guilda;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Uma guilda: nome, dono, membros, cor da Tropa, símbolo 16x16 e as tropas de ataque guardadas. */
public final class Guilda {
    /** 16 cores do editor de símbolo (0 = transparente). */
    public static final int[] PALETA = {
            0x00000000, 0xFFFFFFFF, 0xFFA8A8A8, 0xFF5A5A5A, 0xFF141414, 0xFFE53935, 0xFFFF9800, 0xFFFFEB3B,
            0xFF8BC34A, 0xFF2E9E3E, 0xFF26C6DA, 0xFF64B5F6, 0xFF1E3FD8, 0xFF8E44D0, 0xFFE056C8, 0xFF7A4E2A};

    public final UUID id;
    public String nome;
    public UUID dono;
    /** Membros (com o dono) e o nome de cada um. */
    public final Map<UUID, String> membros = new LinkedHashMap<>();
    /** Tropa (path do id: "green", "yellow"...): a cor do escudo e das tropas. */
    public String tropa;
    public byte[] simbolo = new byte[256];
    /** Tropas de ataque prontas (treinadas por qualquer general da guilda). */
    public int ataque;
    public long criada;

    public Guilda(UUID id, String nome, UUID dono, String donoNome, String tropa) {
        this.id = id;
        this.nome = nome;
        this.dono = dono;
        this.tropa = tropa;
        membros.put(dono, donoNome);
        simboloPadrao();
    }

    public String donoNome() {
        return membros.getOrDefault(dono, "?");
    }

    public int cor() {
        for (var c : com.lanterna.corps.BuiltinCorps.values()) if (c.id().getPath().equals(tropa)) return c.color();
        return 0x46E66E;
    }

    /** Começa com um losango simples (o dono desenha o dele depois). */
    private void simboloPadrao() {
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int d = Math.abs(x - 7) + Math.abs(y - 7);
            simbolo[y * 16 + x] = (byte) (d <= 6 && d >= 4 ? 1 : d < 2 ? 1 : 0);
        }
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("nome", nome);
        t.putUUID("dono", dono);
        t.putString("tropa", tropa);
        t.putByteArray("simbolo", simbolo);
        t.putInt("ataque", ataque);
        t.putLong("criada", criada);
        ListTag l = new ListTag();
        for (var e : membros.entrySet()) {
            CompoundTag m = new CompoundTag();
            m.putUUID("u", e.getKey());
            m.putString("n", e.getValue());
            l.add(m);
        }
        t.put("membros", l);
        return t;
    }

    public static Guilda load(CompoundTag t) {
        Guilda g = new Guilda(t.getUUID("id"), t.getString("nome"), t.getUUID("dono"), "", t.getString("tropa"));
        g.membros.clear();
        for (Tag x : t.getList("membros", Tag.TAG_COMPOUND)) {
            CompoundTag m = (CompoundTag) x;
            g.membros.put(m.getUUID("u"), m.getString("n"));
        }
        byte[] s = t.getByteArray("simbolo");
        if (s.length == 256) g.simbolo = s;
        g.ataque = t.getInt("ataque");
        g.criada = t.getLong("criada");
        return g;
    }
}
