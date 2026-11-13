package com.lanterna.ia;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Ferramentas de texto da "IA" do mod (anel e NPCs): tudo roda dentro do jogo, sem serviço de fora.
 * O texto é normalizado (minúsculas, sem acento, só letras, números e espaço) e comparado por palavras-chave e por
 * semelhança (distância de Levenshtein), para aguentar erros de digitação e o que o reconhecimento de voz entende torto.
 */
public final class Texto {

    /** "Me LEVE para Zamaron!!" -> "me leve para zamaron". */
    public static String limpo(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        StringBuilder b = new StringBuilder(n.length());
        boolean espaco = true;
        for (char c : n.toCharArray()) {
            if (Character.getType(c) == Character.NON_SPACING_MARK) continue;
            if (Character.isLetterOrDigit(c) || c == '\'') {
                b.append(Character.toLowerCase(c));
                espaco = false;
            } else if (!espaco) {
                b.append(' ');
                espaco = true;
            }
        }
        return b.toString().trim();
    }

    public static List<String> palavras(String limpo) {
        List<String> l = new ArrayList<>();
        for (String w : limpo.split(" ")) if (!w.isEmpty()) l.add(w);
        return l;
    }

    /** Alguma das expressões aparece no texto (como palavras inteiras)? */
    public static boolean tem(String limpo, String... expressoes) {
        String t = " " + limpo + " ";
        for (String e : expressoes) if (t.contains(" " + e + " ")) return true;
        return false;
    }

    /** Começa com alguma das expressões? Devolve o resto do texto (ou null). */
    public static String depoisDe(String limpo, String... expressoes) {
        String t = " " + limpo + " ";
        int melhor = -1, fim = -1;
        for (String e : expressoes) {
            int i = t.indexOf(" " + e + " ");
            if (i >= 0 && (melhor < 0 || i < melhor || (i == melhor && i + e.length() + 2 > fim))) {
                melhor = i;
                fim = i + e.length() + 2;
            }
        }
        return melhor < 0 ? null : t.substring(Math.min(fim, t.length())).trim();
    }

    public static int distancia(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + c);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    /**
     * Quão parecido o nome é com algum pedaço do texto (0..1). Testa o nome contra cada sequência de palavras do
     * mesmo tamanho ("zamaron", "planeta zamaron", "zamarom" quase igual...).
     */
    public static double semelhanca(String limpoTexto, String limpoNome) {
        if (limpoNome.isEmpty() || limpoTexto.isEmpty()) return 0;
        if ((" " + limpoTexto + " ").contains(" " + limpoNome + " ")) return 1;
        List<String> ws = palavras(limpoTexto);
        int n = palavras(limpoNome).size();
        double melhor = 0;
        for (int k = Math.max(1, n - 1); k <= n + 1; k++) {
            for (int i = 0; i + k <= ws.size(); i++) {
                String trecho = String.join(" ", ws.subList(i, i + k));
                int d = distancia(trecho, limpoNome);
                double s = 1 - d / (double) Math.max(trecho.length(), limpoNome.length());
                if (s > melhor) melhor = s;
            }
        }
        return melhor;
    }

    private Texto() {}
}
