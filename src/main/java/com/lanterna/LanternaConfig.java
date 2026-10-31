package com.lanterna;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configurações ajustáveis (arquivo config/lanterna-common.toml).
 */
public final class LanternaConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue GREEN_SLEEP_CHANCE;
    public static final ForgeConfigSpec.IntValue RED_KILLS_REQUIRED;
    public static final ForgeConfigSpec.IntValue RED_WINDOW_SECONDS;
    public static final ForgeConfigSpec.IntValue SELECTION_ANIMATION_TICKS;
    public static final ForgeConfigSpec.IntValue POCKET_RECHARGE_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue YELLOW_FEAR_SECONDS;
    public static final ForgeConfigSpec.IntValue VIOLET_BREEDS;
    public static final ForgeConfigSpec.IntValue ORANGE_TREASURE_VALUE;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> CALL_ADVANCEMENTS;
    public static final ForgeConfigSpec.IntValue CALL_REQUIRED;

    public static final ForgeConfigSpec.DoubleValue VIDA_POR_NIVEL;
    public static final ForgeConfigSpec.DoubleValue ARMADURA_POR_NIVEL;
    public static final ForgeConfigSpec.DoubleValue DUREZA_POR_NIVEL;
    public static final ForgeConfigSpec.DoubleValue PVP_DANO;
    public static final ForgeConfigSpec.DoubleValue PVP_RESISTENCIA_POR_NIVEL;
    public static final ForgeConfigSpec.DoubleValue PVP_DIFERENCA_NIVEL;
    public static final ForgeConfigSpec.DoubleValue PVP_TETO_GOLPE;
    public static final ForgeConfigSpec.BooleanValue PVP_MESMA_TROPA;
    public static final ForgeConfigSpec.BooleanValue PVP_MESMA_GUILDA;

    public static final ForgeConfigSpec.IntValue GUILDA_MAX_PLANETAS;
    public static final ForgeConfigSpec.IntValue GUILDA_MAX_MEMBROS;
    public static final ForgeConfigSpec.IntValue GUILDA_MAX_DEFESA;
    public static final ForgeConfigSpec.IntValue GUILDA_MAX_ATAQUE;
    public static final ForgeConfigSpec.IntValue GUILDA_MIN_ESSENCIA;
    public static final ForgeConfigSpec.IntValue GUILDA_ESSENCIA_ANEL;
    public static final ForgeConfigSpec.IntValue GUILDA_SEG_TREINO;
    public static final ForgeConfigSpec.DoubleValue GUILDA_ESCUDO_CONQUISTA;
    public static final ForgeConfigSpec.DoubleValue GUILDA_ESCUDO_INVASAO;
    public static final ForgeConfigSpec.IntValue GUILDA_MIN_INVASAO;
    public static final ForgeConfigSpec.IntValue GUILDA_RAIO;
    public static final ForgeConfigSpec.DoubleValue TROPAS_CHANCE_PLANETA;

    public static final ForgeConfigSpec.DoubleValue MORTE_CHANCE;
    public static final ForgeConfigSpec.DoubleValue MORTE_CHANCE_CEIFADOR;
    public static final ForgeConfigSpec.IntValue MORTE_NIVEL_MIN;
    public static final ForgeConfigSpec.IntValue MORTE_DIAS_FOME;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Como cada Tropa escolhe o seu portador").push("selecao");

        GREEN_SLEEP_CHANCE = b
                .comment("Verde: chance (0 a 1) de ser escolhido ao acordar depois de dormir a noite inteira")
                .defineInRange("chance_verde_dormindo", 0.20, 0.0, 1.0);

        RED_KILLS_REQUIRED = b
                .comment("Vermelho: quantos mobs hostis matar dentro da janela de tempo para despertar a fúria")
                .defineInRange("abates_vermelho", 30, 1, 10000);

        RED_WINDOW_SECONDS = b
                .comment("Vermelho: janela de tempo em segundos para contar os abates")
                .defineInRange("janela_vermelho_segundos", 300, 10, 36000);

        YELLOW_FEAR_SECONDS = b
                .comment("Amarelo: segundos na Escuridão do Warden, com ele por perto, para ser escolhido")
                .defineInRange("segundos_medo_amarelo", 30, 5, 3600);

        VIOLET_BREEDS = b
                .comment("Violeta: filhotes que é preciso criar com seu bicho domado por perto")
                .defineInRange("filhotes_violeta", 5, 1, 1000);

        ORANGE_TREASURE_VALUE = b
                .comment("Laranja: valor do tesouro no inventário (ouro=1, esmeralda=3, diamante=5, netherite=40; blocos x9)")
                .defineInRange("tesouro_laranja", 1000, 10, 100000);

        CALL_ADVANCEMENTS = b
                .comment("O Chamado: conquistas que chamam a atenção dos anéis. Cada uma conta 1 sinal.")
                .defineListAllowEmpty(java.util.List.of("conquistas_chamado"), () -> java.util.List.of(
                        "minecraft:story/mine_diamond",
                        "minecraft:story/enter_the_nether",
                        "minecraft:story/enchant_item",
                        "minecraft:story/cure_zombie_villager",
                        "minecraft:nether/find_fortress",
                        "minecraft:nether/obtain_blaze_rod",
                        "minecraft:story/follow_ender_eye",
                        "minecraft:adventure/hero_of_the_village",
                        "minecraft:adventure/kill_all_mobs",
                        "minecraft:nether/summon_wither",
                        "minecraft:end/kill_dragon"), o -> o instanceof String);

        CALL_REQUIRED = b
                .comment("O Chamado: quantos sinais (conquistas da lista) até um anel aleatório escolher você. 0 desliga.")
                .defineInRange("sinais_chamado", 3, 0, 50);

        SELECTION_ANIMATION_TICKS = b
                .comment("Duração da animação de entrega do anel, em ticks (20 ticks = 1 segundo)")
                .defineInRange("duracao_animacao_ticks", 120, 20, 600);

        b.pop();

        b.comment("Anel e bateria").push("anel");
        POCKET_RECHARGE_COOLDOWN_SECONDS = b
                .comment("Tempo de espera em segundos entre recargas usando a bateria do slot (tecla R)")
                .defineInRange("espera_recarga_bolso_segundos", 60, 0, 3600);
        b.pop();

        b.comment("Combate e equilíbrio (pensado para multiplayer)").push("combate");
        VIDA_POR_NIVEL = b
                .comment("Vida máxima a mais por nível da Tropa (2 = um coração). No nível 50 com 0.8: +40 de vida (20 corações a mais)")
                .defineInRange("vida_por_nivel", 0.8, 0.0, 10.0);
        ARMADURA_POR_NIVEL = b
                .comment("Armadura a mais por nível (vale contra tudo). No nível 50 com 0.16: +8 de armadura")
                .defineInRange("armadura_por_nivel", 0.16, 0.0, 2.0);
        DUREZA_POR_NIVEL = b
                .comment("Dureza de armadura a mais por nível (reduz golpes fortes). No nível 50 com 0.1: +5")
                .defineInRange("dureza_por_nivel", 0.1, 0.0, 2.0);
        PVP_DANO = b
                .comment("Jogador contra jogador: fração do dano que passa (os poderes do anel são feitos para derrubar monstros grandes)")
                .defineInRange("pvp_dano", 0.4, 0.0, 1.0);
        PVP_RESISTENCIA_POR_NIVEL = b
                .comment("Jogador contra jogador: resistência a mais por nível de quem apanha. No nível 50 com 0.005: 25% a menos")
                .defineInRange("pvp_resistencia_por_nivel", 0.005, 0.0, 0.02);
        PVP_DIFERENCA_NIVEL = b
                .comment("Jogador contra jogador: quanto cada nível de diferença muda o dano (0.015 = 1,5% por nível; limitado entre -40% e +25%)")
                .defineInRange("pvp_diferenca_nivel", 0.015, 0.0, 0.1);
        PVP_TETO_GOLPE = b
                .comment("Jogador contra jogador: um golpe tira no máximo esta fração da vida máxima (0.3 = 30%; acaba com o hit kill)")
                .defineInRange("pvp_teto_golpe", 0.3, 0.05, 1.0);
        PVP_MESMA_TROPA = b
                .comment("Jogadores da mesma Tropa podem se ferir? (o pvp do servidor também precisa estar ligado)")
                .define("pvp_mesma_tropa", true);
        PVP_MESMA_GUILDA = b
                .comment("Jogadores da mesma guilda podem se ferir?")
                .define("pvp_mesma_guilda", false);
        b.pop();

        b.comment("Guildas e conquista de planetas").push("guildas");
        GUILDA_MAX_PLANETAS = b.comment("Quantos planetas uma guilda pode ter ao mesmo tempo").defineInRange("max_planetas", 3, 1, 50);
        GUILDA_MAX_MEMBROS = b.comment("Máximo de jogadores numa guilda").defineInRange("max_membros", 20, 1, 500);
        GUILDA_MAX_DEFESA = b.comment("Máximo de tropas de defesa em cada planeta").defineInRange("max_defesa", 20, 1, 200);
        GUILDA_MAX_ATAQUE = b.comment("Máximo de tropas de ataque guardadas pela guilda").defineInRange("max_ataque", 30, 1, 300);
        GUILDA_MIN_ESSENCIA = b.comment("Minutos (tempo real) para o minerador juntar 1 Essência de Poder").defineInRange("minutos_por_essencia", 3, 1, 1440);
        GUILDA_ESSENCIA_ANEL = b.comment("Essências para forjar 1 Anel de Recruta").defineInRange("essencias_por_anel", 8, 1, 640);
        GUILDA_SEG_TREINO = b.comment("Segundos (tempo real) para o general treinar 1 tropa").defineInRange("segundos_treino", 60, 1, 86400);
        GUILDA_ESCUDO_CONQUISTA = b.comment("Horas de escudo (sem invasões) depois de fincar a bandeira").defineInRange("escudo_horas_conquista", 2.0, 0.0, 720.0);
        GUILDA_ESCUDO_INVASAO = b.comment("Horas de escudo depois de cada invasão (ganhando ou perdendo)").defineInRange("escudo_horas_invasao", 4.0, 0.0, 720.0);
        GUILDA_MIN_INVASAO = b.comment("Duração máxima de uma invasão, em minutos").defineInRange("minutos_invasao", 10, 1, 120);
        GUILDA_RAIO = b.comment("Raio do território em volta da bandeira (só a guilda constrói/quebra; a invasão começa aqui)").defineInRange("raio_territorio", 64, 24, 512);
        b.pop();

        b.comment("Planetas das Tropas (NPC)").push("tropas");
        TROPAS_CHANCE_PLANETA = b.comment("Chance de um planeta gerado já ser de uma Tropa (com base e defensores). Invadir deixa a Tropa hostil a você.")
                .defineInRange("chance_planeta_tropa", 0.07, 0.0, 1.0);
        b.pop();

        b.comment("O Anel Preto (Morte)").push("morte");
        MORTE_CHANCE = b.comment("Chance de o anel se corromper quando o portador morre (0.004 = 0,4%)").defineInRange("chance_corrupcao", 0.004, 0.0, 1.0);
        MORTE_CHANCE_CEIFADOR = b.comment("Chance quando quem matou foi um Lanterna Negro (jogador)").defineInRange("chance_corrupcao_ceifador", 0.08, 0.0, 1.0);
        MORTE_NIVEL_MIN = b.comment("Nível mínimo para o anel poder se corromper").defineInRange("nivel_minimo", 15, 1, 50);
        MORTE_DIAS_FOME = b.comment("Dias (tempo real) que o Lanterna Negro aguenta sem ceifar um portador de anel antes de enfraquecer (0 desliga)")
                .defineInRange("dias_fome", 3, 0, 365);
        b.pop();

        SPEC = b.build();
    }

    private LanternaConfig() {}
}
