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

        SPEC = b.build();
    }

    private LanternaConfig() {}
}
