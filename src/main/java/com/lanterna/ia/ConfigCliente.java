package com.lanterna.ia;

import net.minecraftforge.common.ForgeConfigSpec;

/** Opções só do cliente (config/lanterna-client.toml): a voz do anel. */
public final class ConfigCliente {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue VOZ;
    public static final ForgeConfigSpec.IntValue VELOCIDADE;
    public static final ForgeConfigSpec.BooleanValue VOZ_NEURAL;
    public static final ForgeConfigSpec.ConfigValue<String> VOZ_PT, VOZ_EN;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("anel");
        VOZ = b.comment("The ring speaks its answers out loud (Windows voices, macOS 'say', Linux espeak)",
                "O anel fala as respostas em voz alta (vozes do Windows, 'say' do Mac, espeak no Linux)").define("voz_do_anel", true);
        VELOCIDADE = b.comment("Speech rate of the ring's voice (-10 slow .. 10 fast)", "Velocidade da voz do anel (-10 devagar .. 10 rápido)")
                .defineInRange("velocidade_voz", 0, -10, 10);
        VOZ_NEURAL = b.comment("Human-sounding neural voice (Piper, offline). Downloaded once (~90 MB) to config/lanterna/piper. false = system voice",
                "Voz neural, com som de gente (Piper, sem internet). Baixa uma vez (~90 MB) para config/lanterna/piper. false = voz do sistema")
                .define("voz_neural", true);
        VOZ_PT = b.comment("Piper voice for Portuguese (https://huggingface.co/rhasspy/piper-voices): pt_BR-faber-medium, pt_BR-cadu-medium, pt_BR-jeff-medium",
                "Voz do Piper em português: pt_BR-faber-medium, pt_BR-cadu-medium, pt_BR-jeff-medium").define("voz_neural_pt", "pt_BR-faber-medium");
        VOZ_EN = b.comment("Piper voice for English: en_US-ryan-medium, en_US-lessac-medium, en_US-joe-medium, en_US-amy-medium...",
                "Voz do Piper em inglês").define("voz_neural_en", "en_US-ryan-medium");
        b.pop();
        SPEC = b.build();
    }

    private ConfigCliente() {}
}
