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
