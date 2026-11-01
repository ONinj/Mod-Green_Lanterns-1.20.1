package com.lanterna.fonte;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;

/**
 * O que a Fonte guarda de cada jogador (sobrevive à morte: fica no NBT "persistido" do jogador).
 *
 *   pressao      ticks acumulados de Pressão da Fonte (2 h = 144 000 ticks); cai pela metade do tempo fora
 *   banho        ticks dentro da Fonte nesta visita (10 min = Banho de Luz completo)
 *   banhoFeito   já ganhou o Banho nesta visita
 *   sobrecarga   carga extra acima do máximo do anel (gasta antes da carga normal)
 *   bloqueado    foi expulso pela pressão: só volta quando a pressão cair abaixo da metade
 *   selos        Tropas cuja Fonte reconhece o jogador (Chamado da Fonte completo)
 *   chamado      etapa do Chamado da Fonte por Tropa ("verde:2"...)
 *   essencia     Essência da Fonte (moeda da Árvore da Luz)
 *   arvore       nós desbloqueados da Árvore da Luz
 */
public final class FonteDados {
    private static final String CHAVE = "lanterna_fonte";

    public static CompoundTag tag(Player p) {
        CompoundTag persist = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!p.getPersistentData().contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persist);
        if (!persist.contains(CHAVE, Tag.TAG_COMPOUND)) persist.put(CHAVE, new CompoundTag());
        return persist.getCompound(CHAVE);
    }

    public static int pressao(Player p) { return tag(p).getInt("pressao"); }
    public static void pressao(Player p, int v) { tag(p).putInt("pressao", Math.max(0, v)); }

    public static int banho(Player p) { return tag(p).getInt("banho"); }
    public static void banho(Player p, int v) { tag(p).putInt("banho", Math.max(0, v)); }

    public static boolean banhoFeito(Player p) { return tag(p).getBoolean("banhoFeito"); }
    public static void banhoFeito(Player p, boolean v) { tag(p).putBoolean("banhoFeito", v); }

    public static int sobrecarga(Player p) { return tag(p).getInt("sobrecarga"); }
    public static void sobrecarga(Player p, int v) { tag(p).putInt("sobrecarga", Math.max(0, v)); }

    public static boolean bloqueado(Player p) { return tag(p).getBoolean("bloqueado"); }
    public static void bloqueado(Player p, boolean v) { tag(p).putBoolean("bloqueado", v); }

    public static int essencia(Player p) { return tag(p).getInt("essencia"); }
    public static void essencia(Player p, int v) { tag(p).putInt("essencia", Math.max(0, v)); }

    // ---- listas de texto (selos, nós da árvore) ----

    private static Set<String> lista(Player p, String nome) {
        Set<String> s = new HashSet<>();
        ListTag l = tag(p).getList(nome, Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) s.add(l.getString(i));
        return s;
    }

    private static void lista(Player p, String nome, Set<String> s) {
        ListTag l = new ListTag();
        for (String x : s) l.add(StringTag.valueOf(x));
        tag(p).put(nome, l);
    }

    public static boolean temSelo(Player p, String tropa) { return lista(p, "selos").contains(tropa); }

    public static void darSelo(Player p, String tropa) {
        Set<String> s = lista(p, "selos");
        s.add(tropa);
        lista(p, "selos", s);
    }

    public static Set<String> arvore(Player p) { return lista(p, "arvore"); }
    public static boolean temNo(Player p, String no) { return arvore(p).contains(no); }

    public static void darNo(Player p, String no) {
        Set<String> s = arvore(p);
        s.add(no);
        lista(p, "arvore", s);
    }

    public static void zerarArvore(Player p) { lista(p, "arvore", new HashSet<>()); }

    public static int chamado(Player p, String tropa) { return tag(p).getCompound("chamado").getInt(tropa); }

    public static void chamado(Player p, String tropa, int etapa) {
        CompoundTag c = tag(p).getCompound("chamado");
        c.putInt(tropa, etapa);
        tag(p).put("chamado", c);
    }

    private FonteDados() {}
}
