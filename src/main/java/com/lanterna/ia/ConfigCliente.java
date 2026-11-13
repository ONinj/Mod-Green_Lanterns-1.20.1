package com.lanterna.ia;

import net.minecraftforge.common.ForgeConfigSpec;

/** Opções só do cliente (config/lanterna-client.toml): a voz do anel. */
public final class ConfigCliente {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue VOZ;
    public static final ForgeConfigSpec.IntValue VELOCIDADE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("anel");
        VOZ = b.comment("The ring speaks its answers out loud (Windows voices, macOS 'say', Linux espeak)",
                "O anel fala as respostas em voz alta (vozes do Windows, 'say' do Mac, espeak no Linux)").define("voz_do_anel", true);
        VELOCIDADE = b.comment("Speech rate of the ring's voice (-10 slow .. 10 fast)", "Velocidade da voz do anel (-10 devagar .. 10 rápido)")
                .defineInRange("velocidade_voz", 0, -10, 10);
        b.pop();
        SPEC = b.build();
    }

    private ConfigCliente() {}
}
