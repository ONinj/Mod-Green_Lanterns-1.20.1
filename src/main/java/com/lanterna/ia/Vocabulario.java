package com.lanterna.ia;

import java.util.*;

/**
 * Conserta o que o reconhecimento de fala ouviu errado. O Vosk só conhece palavras comuns do português e do inglês, então
 * os nomes do mod viram outra coisa: "Nok" vira "noque", "no que" ou "nó"; "Zamaron" vira "zamarão" ou "sama rom";
 * "Korugar" vira "coro gar". Aqui cada pedaço da frase é comparado pelo SOM (uma chave fonética simples do português)
 * com os nomes do mod, e o nome certo entra no lugar.
 *
 * Também troca sinônimos por uma palavra só ("meu planeta", "planeta natal", "minha base" -> "casa"), para o anel
 * entender a mesma coisa dita de jeitos diferentes.
 */
public final class Vocabulario {

    /** Nomes do mod (como devem ficar no texto). Os lugares têm prioridade depois de "para", "pra", "em"... */
    private static final String[] LUGARES = {"oa", "nok", "zamaron", "korugar", "ysmault", "okaara", "odym", "ryut", "genese",
            "mogo", "hanks", "terra", "espaco", "aurora"};
    private static final String[] NOMES = {"lanterna", "lanternas", "veltari", "ghallos", "brakkari", "zaelith", "skrell vath",
            "ves kai", "aelinos", "silentes", "vorgath", "nyrielle", "thessara", "mammok", "morrow", "penitente", "guardioes",
            "guardiao", "construto", "construtos", "juramento", "cinzelada", "afinidade", "hiperpropulsao", "lendarios", "lendario",
            "larfleeze", "sinestro", "tarvok", "kael", "grath", "mizzuk", "aurel", "nhale", "seraphine", "malachar", "karvhul",
            "morthaal", "verdan", "calithe", "seraphel", "vigia partido", "jade", "jade viva", "jade estelar", "ecos", "eco cinzento",
            "fonte", "nulo", "gênese", "insignia", "meritos", "bateria", "anel", "aneis", "tropa", "tropas", "lagrima indigo",
            "fragmento estelar", "fragmentos estelares", "mestre toran", "livia", "contaminados", "oficina", "forja", "forja do espectro"};

    /** Palavras de destino: o que vem depois delas é, quase sempre, um lugar. */
    private static final Set<String> ANTES_DE_LUGAR = new HashSet<>(Arrays.asList("para", "pra", "pro", "ate", "em", "no", "na",
            "ao", "a", "to", "for", "sobre", "de", "do", "da", "about", "planeta", "planet"));

    /** Sinônimos (já sem acento): a expressão da esquerda vira a da direita. Os mais longos primeiro. */
    private static final String[][] SINONIMOS = {
            {"meu planeta natal", "casa"}, {"seu planeta natal", "casa"}, {"o seu planeta", "casa"}, {"o meu planeta", "casa"},
            {"o nosso planeta", "casa"}, {"a minha base", "casa"}, {"planeta da minha tropa", "casa"}, {"planeta da tropa", "casa"}, {"planeta natal", "casa"}, {"meu planeta", "casa"},
            {"seu planeta", "casa"}, {"teu planeta", "casa"}, {"nosso planeta", "casa"}, {"o planeta da gente", "casa"},
            {"minha base", "casa"}, {"nossa base", "casa"}, {"meu lar", "casa"}, {"pro lar", "para casa"}, {"pra base", "para casa"},
            {"para a base", "para casa"}, {"pro quartel", "para casa"}, {"para o quartel", "para casa"},
            {"my planet", "home"}, {"your planet", "home"}, {"our planet", "home"}, {"home world", "home"}, {"homeworld", "home"},
            {"my base", "home"}, {"the base", "home"},
            // pedidos de viagem ditos de outro jeito
            {"me manda para", "me leve para"}, {"me manda pra", "me leve para"}, {"me manda pro", "me leve para"},
            {"me teleporta para", "me leve para"}, {"me teleporta pra", "me leve para"}, {"bora para", "me leve para"},
            {"bora pra", "me leve para"}, {"bora pro", "me leve para"}, {"vamos para", "me leve para"}, {"vamos pra", "me leve para"},
            {"vamos pro", "me leve para"}, {"vamo pra", "me leve para"}, {"leva eu para", "me leve para"}, {"leva eu pra", "me leve para"},
            {"quero voltar para", "me leve para"}, {"quero voltar pra", "me leve para"}, {"quero voltar pro", "me leve para"},
            {"voltar para", "me leve para"}, {"voltar pra", "me leve para"}, {"volta para", "me leve para"}, {"volta pra", "me leve para"},
            {"volte para", "me leve para"}, {"volte pra", "me leve para"}, {"retornar para", "me leve para"}, {"retorne para", "me leve para"},
            {"me leva de volta para", "me leve para"}, {"me leva de volta pra", "me leve para"}, {"me leve de volta para", "me leve para"},
            {"me leve de volta pra", "me leve para"}, {"me leva embora para", "me leve para"}, {"quero ir embora para", "me leve para"},
            {"me bota em", "me leve para"}, {"me coloca em", "me leve para"}, {"leve nos para", "me leve para"}, {"nos leve para", "me leve para"},
            {"take us to", "take me to"}, {"bring us to", "take me to"}, {"send me to", "take me to"}, {"go back to", "take me to"},
            {"return to", "take me to"}, {"head back to", "take me to"}, {"lets head to", "take me to"},
            // "me leve pra casa" dito sem o "para"
            {"me leve casa", "me leve para casa"}, {"voltar casa", "me leve para casa"}, {"quero ir pra casa", "me leve para casa"},
            {"quero ir para casa", "me leve para casa"}, {"quero voltar pra casa", "me leve para casa"}, {"go home", "take me to home"},
            {"take me home", "take me to home"}, {"bring me home", "take me to home"},
    };

    /** O texto (já limpo pelo {@link Texto#limpo}) com nomes consertados e sinônimos trocados. */
    public static String consertar(String limpo) {
        List<String> ws = new ArrayList<>(Texto.palavras(limpo));
        if (ws.isEmpty()) return limpo;
        List<String> saida = new ArrayList<>();
        int i = 0;
        while (i < ws.size()) {
            boolean lugar = i > 0 && ANTES_DE_LUGAR.contains(ws.get(i - 1));
            String achou = null;
            int usou = 0;
            // tenta juntar 3, 2 e 1 palavras ("no que" -> nok, "sama rom" -> zamaron, "coro gar" -> korugar)
            for (int n = Math.min(3, ws.size() - i); n >= 1 && achou == null; n--) {
                String pedaco = String.join(" ", ws.subList(i, i + n));
                String junto = String.join("", ws.subList(i, i + n));
                String m = melhor(junto, pedaco, lugar);
                if (m != null) { achou = m; usou = n; }
            }
            if (achou != null) { saida.add(achou); i += usou; }
            else { saida.add(ws.get(i)); i++; }
        }
        String t = " " + String.join(" ", saida) + " ";
        for (String[] s : SINONIMOS) t = t.replace(" " + s[0] + " ", " " + s[1] + " ");
        return t.trim().replaceAll("\\s+", " ");
    }

    /** O nome do mod que soa como o pedaço, ou null. Depois de "para/em/sobre" aceita mais diferença (é um lugar). */
    private static String melhor(String junto, String pedaco, boolean lugar) {
        String chave = som(junto);
        if (chave.length() < 2) return null;
        String melhor = null;
        double nota = 0;
        for (String nome : LUGARES) {
            String alvo = som(nome.replace(" ", ""));
            double s = parecido(chave, alvo);
            // nomes curtos (Oa, Nok) só com o mesmo som: "noque", "no que", "nock"; "novo" e "nó" não
            double minimo = alvo.length() <= 3 ? 1 : lugar ? 0.67 : 0.86;
            if (junto.equals(nome)) s = 1.01;
            if (s >= minimo && s > nota) { nota = s; melhor = nome; }
        }
        for (String nome : NOMES) {
            String n = nome.replace(" ", "");
            if (n.length() < 4) continue;
            double s = parecido(chave, som(n));
            if (junto.equals(n)) s = 1.01;
            if (s >= 0.84 && s > nota) { nota = s; melhor = nome; }
        }
        if (melhor == null) return null;
        // palavras comuns curtas só viram nome do mod depois de "para/em..." ("no", "o a" sozinhos continuam iguais)
        if (!lugar && junto.length() <= 3) return null;
        // não troca uma palavra pelo mesmo texto juntando outras à toa
        if (pedaco.contains(" ") && melhor.equals(pedaco)) return melhor;
        return melhor;
    }

    /** Quão parecidas são duas chaves de som (0..1). */
    static double parecido(String a, String b) {
        if (a.equals(b)) return 1;
        int d = Texto.distancia(a, b);
        return 1 - d / (double) Math.max(a.length(), b.length());
    }

    /**
     * Chave de som do português (e do inglês, por aproximação): letras que soam igual viram a mesma, letras dobradas
     * viram uma, o "h" mudo sai e a vogal do fim depois de consoante cai ("noque" e "nok" ficam iguais).
     */
    public static String som(String w) {
        String s = w.toLowerCase(Locale.ROOT);
        s = s.replace("ph", "f").replace("ch", "x").replace("sh", "x").replace("lh", "l").replace("nh", "n")
                .replace("qu", "k").replace("gu", "g").replace("ck", "k").replace("ç", "s");
        s = s.replaceAll("g(?=[ei])", "j");
        s = s.replaceAll("c(?=[ei])", "s").replace("c", "k").replace("q", "k").replace("z", "s").replace("w", "v")
                .replace("y", "i").replace("h", "").replace("ao", "on").replace("x", "s");
        s = s.replaceAll("am$", "on");
        s = s.replaceAll("([a-z])\\1+", "$1");
        s = s.replaceAll("(?<=[^aeiou])[eiou]$", "");
        s = s.replaceAll("m$", "n");
        return s;
    }

    private Vocabulario() {}
}
