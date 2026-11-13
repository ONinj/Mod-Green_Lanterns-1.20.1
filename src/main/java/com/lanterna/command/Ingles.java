package com.lanterna.command;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Os comandos do mod são em inglês (0.47.0). Por dentro o código continua usando os nomes antigos em português para as
 * opções (ações, tipos, kits...): {@link #pt} traduz o que o jogador digitou e {@link #en} traduz as sugestões.
 */
public final class Ingles {
    private static final Map<String, String> PT_EN = new HashMap<>();
    private static final Map<String, String> EN_PT = new HashMap<>();

    private static void par(String pt, String en) {
        PT_EN.putIfAbsent(pt, en);
        EN_PT.putIfAbsent(en, pt);
    }

    static {
        // a Fonte
        par("selo", "seal"); par("pressao", "pressure"); par("banho", "bath"); par("essencia", "essence"); par("prova", "trial");
        par("juramento", "oath"); par("arvore", "tree"); par("zerar", "reset"); par("evento", "event"); par("chefe", "boss");
        par("ir", "go"); par("ilha", "island");
        // poderes
        par("autorecarga", "autorecharge"); par("honra", "honor"); par("genese", "genesis");
        // anel da Morte
        par("corromper", "corrupt"); par("dominar", "master"); par("faminto", "hungry"); par("alimentar", "feed"); par("limpar", "clear");
        // tudo
        par("tudo", "all"); par("todas", "all"); par("todos", "all");
        // kits
        par("aneis", "rings"); par("materiais", "materials"); par("reliquias", "relics"); par("guilda", "guild"); par("naves", "ships");
        // encontros
        par("pirata", "pirate"); par("capitania", "flagship"); par("patrulha", "patrol"); par("inimiga", "enemy");
        par("cargueiro", "freighter"); par("destrocos", "wreck");
        // /lantern spawn: chaves e valores
        par("papel", "role"); par("tropa", "corps"); par("roupa", "outfit"); par("pele", "skin"); par("oficial", "officer");
        par("corrompido", "corrupted"); par("lanterna", "lantern"); par("capitao", "captain"); par("mercador", "merchant");
        par("nenhuma", "none");
    }

    /** O que o jogador digitou (inglês) para o nome interno (português). Sem tradução, devolve igual. */
    public static String pt(String en) {
        return EN_PT.getOrDefault(en, en);
    }

    public static String en(String pt) {
        return PT_EN.getOrDefault(pt, pt);
    }

    public static String[] en(String... pts) {
        return Arrays.stream(pts).map(Ingles::en).distinct().toArray(String[]::new);
    }

    public static java.util.List<String> en(java.util.Collection<String> pts) {
        return pts.stream().map(Ingles::en).distinct().toList();
    }

    /** "tudo"/"todas"/"todos"/"all". */
    public static boolean todos(String s) {
        return s.equals("tudo") || s.equals("todas") || s.equals("todos") || s.equals("all");
    }

    private Ingles() {}
}
