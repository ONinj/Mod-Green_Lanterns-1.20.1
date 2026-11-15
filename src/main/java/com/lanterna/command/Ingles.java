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
        // 0.50.2: técnicas, invocações e naves também em inglês
        par("olhar", "gaze"); par("bastiao", "bastion"); par("onda", "wave"); par("salto", "leap"); par("armadura", "armor"); par("comando", "command");
        par("eco", "echo"); par("alvo_treino", "training_dummy"); par("broto", "sprout");
        par("nave_lanceira", "lancer_ship"); par("nave_pirata", "pirate_ship"); par("nave_patrulha", "patrol_ship");
        par("nave_cargueiro", "freighter_ship"); par("nave_pirata_capitania", "pirate_flagship");
        // 0.50.3: chefes em inglês no /lantern summon (o nome na barra do chefe segue a língua do jogo)
        par("boss_vigia_partido", "boss_broken_sentinel");
        par("boss_penitente", "boss_faceless_penitent");
        par("boss_devorador_de_ecos", "boss_echo_devourer");
        par("boss_nulo", "boss_the_null");
        par("boss_colosso_de_jade", "boss_jade_colossus");
        par("boss_mae_das_sombras", "boss_mother_of_shadows");
        par("boss_serpente_escarlate", "boss_scarlet_serpent");
        par("boss_rei_faminto", "boss_starving_king");
        par("boss_arcanjo_da_aurora", "boss_archangel_of_dawn");
        par("boss_cervo_sereno", "boss_serene_stag");
        par("boss_rainha_de_cristal", "boss_crystal_queen");
        par("boss_karvhul_forjador_rubro", "boss_karvhul_crimson_forger");
        par("boss_vesk_morrath_carcereiro", "boss_vesk_morrath_jailer");
        par("boss_ghormaddu_arca_viva", "boss_ghormaddu_living_hoard");
        par("boss_seraphel_do_vitral", "boss_seraphel_stained_glass");
        par("boss_gemeos_velados", "boss_veiled_twins");
        par("boss_calithe_rainha_do_espelho", "boss_calithe_mirror_queen");
        par("boss_morthaal_coletor_de_lanternas", "boss_morthaal_lantern_collector");
        par("boss_verdan_o_arquiteto", "boss_verdan_the_architect");
    }

    /** O que o jogador digitou (inglês) para o nome interno (português). Sem tradução, devolve igual. */
    public static String pt(String en) {
        String v = EN_PT.get(en);
        if (v != null) return v;
        // nomes compostos ("lantern_green", "officer_red"): traduz só a primeira parte
        int i = en.indexOf('_');
        if (i > 0 && EN_PT.containsKey(en.substring(0, i))) return EN_PT.get(en.substring(0, i)) + en.substring(i);
        return en;
    }

    public static String en(String pt) {
        String v = PT_EN.get(pt);
        if (v != null) return v;
        int i = pt.indexOf('_');
        if (i > 0 && PT_EN.containsKey(pt.substring(0, i))) return PT_EN.get(pt.substring(0, i)) + pt.substring(i);
        return pt;
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
