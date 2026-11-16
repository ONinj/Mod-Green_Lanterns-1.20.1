package com.lanterna.client.ia;

import net.minecraft.client.Minecraft;

/**
 * 0.52.4: a voz do anel (ouvir pelo microfone com o Vosk e falar com o Piper ou as vozes do sistema) saiu do mod.
 * O anel conversa só pelo chat ("anel, ..."). Sobrou só o idioma do jogo, usado pelas respostas.
 */
public final class VozAnel {
    /** Mantido para os testes automáticos (não faz mais nada). */
    public static boolean mudo;

    /** Antes lia a resposta em voz alta; agora não faz nada (a resposta já aparece no chat). */
    public static void falar(String texto) {
    }

    static String idioma() {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        return code != null && code.startsWith("pt") ? "pt-BR" : "en-US";
    }

    private VozAnel() {}
}
