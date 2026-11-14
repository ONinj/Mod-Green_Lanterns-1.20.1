package com.lanterna.client.ia;

import com.lanterna.ia.ConfigCliente;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * /lanternmic — lista os microfones do computador; /lanternmic <n> escolhe o que o anel ouve; /lanternmic 0 volta ao
 * automático (o mesmo do Simple Voice Chat, ou o padrão do sistema). Só no cliente: cada jogador escolhe o seu.
 */
@Mod.EventBusSubscriber(modid = com.lanterna.LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ComandoMicrofone {

    @SubscribeEvent
    public static void registrar(RegisterClientCommandsEvent e) {
        if (!VozAnel.OUVIR_VOZ) return;   // 0.48.12: voz desligada (só chat)
        e.getDispatcher().register(Commands.literal("lanternmic")
                .executes(c -> {
                    List<String> l = Microfone.lista();
                    c.getSource().sendSystemMessage(Component.translatable("anel.ia.voz.mic_lista", ConfigCliente.MICROFONE.get(), Microfone.pedido().isEmpty() ? "-" : Microfone.pedido()));
                    c.getSource().sendSystemMessage(Component.literal("  0) auto"));
                    for (int i = 0; i < l.size(); i++) c.getSource().sendSystemMessage(Component.literal("  " + (i + 1) + ") " + l.get(i)));
                    return l.size();
                })
                .then(Commands.argument("n", IntegerArgumentType.integer(0, 64)).executes(c -> {
                    int n = IntegerArgumentType.getInteger(c, "n");
                    List<String> l = Microfone.lista();
                    String nome = n == 0 || n > l.size() ? "auto" : l.get(n - 1);
                    ConfigCliente.MICROFONE.set(nome);
                    ConfigCliente.MICROFONE.save();
                    c.getSource().sendSystemMessage(Component.translatable("anel.ia.voz.mic_escolhido", nome));
                    return 1;
                })));
    }

    private ComandoMicrofone() {}
}
