package com.lanterna.client.vida;

import com.lanterna.client.ClientRingData;
import com.lanterna.client.ClientVisibleRings;
import com.lanterna.construct.ConstructType;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/** 0.53: o estado da Árvore das Emoções deste jogador, no cliente. */
public final class ClientEmocoes {
    private static CompoundTag tag = new CompoundTag();

    static {
        ConstructType.CLIENTE = new ConstructType.Contexto() {
            public int nivel(String a) { return ClientEmocoes.nivel(a); }
            @Nullable public String segundo() { return ClientEmocoes.segundo(); }
        };
    }

    public static void receber(CompoundTag t) {
        tag = t;
    }

    public static int nivel(String a) { return tag.getCompound("nivel").getInt(a); }
    @Nullable public static String treino() { String t = tag.getString("treino"); return t.isEmpty() ? null : t; }
    public static boolean doisAneis() { return tag.getBoolean("dois"); }
    public static boolean dominada() { return tag.getBoolean("dominada"); }

    public static boolean comAnelDaVida() {
        return ClientRingData.hasRing && ClientRingData.corps != null && ClientRingData.corps.getPath().equals("white");
    }

    /** O botão da árvore aparece com o Anel da Vida ou com o corpo já preparado para Dois Anéis. */
    public static boolean temArvore() {
        return comAnelDaVida() || doisAneis();
    }

    @Nullable
    public static String segundo() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        String s = ClientVisibleRings.of(mc.player.getId()).segundo();
        return s.isEmpty() ? null : s;
    }

    public static void limpar() {
        tag = new CompoundTag();
    }

    private ClientEmocoes() {}
}
