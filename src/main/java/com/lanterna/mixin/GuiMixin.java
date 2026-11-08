package com.lanterna.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A mensagem do meio da tela (acima da barra de itens) é uma linha só e não quebra: com interface grande, texto longo
 * saía pelos dois lados. Quando ela não cabe na tela, vai para o chat, onde quebra em linhas. A mesma mensagem não é
 * repetida no chat em menos de 6 segundos (algumas são reenviadas a cada poucos ticks).
 */
@Mixin(Gui.class)
public abstract class GuiMixin {
    private static String lanterna$ultima = "";
    private static long lanterna$quando;

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void lanterna$naoCabe(Component msg, boolean animate, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (msg == null || mc.font == null) return;
        int largura = mc.getWindow().getGuiScaledWidth();
        if (mc.font.width(msg) <= largura - 24) return;
        ci.cancel();
        String texto = msg.getString();
        long agora = System.currentTimeMillis();
        if (texto.equals(lanterna$ultima) && agora - lanterna$quando < 6000) return;
        lanterna$ultima = texto;
        lanterna$quando = agora;
        mc.gui.getChat().addMessage(msg);
    }

    /** O título (4x) e o subtítulo (2x) também não quebram: se não cabem, vão inteiros para o chat e na tela ficam cortados. */
    @ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true)
    private Component lanterna$titulo(Component c) {
        return lanterna$caber(c, 4);
    }

    @ModifyVariable(method = "setSubtitle", at = @At("HEAD"), argsOnly = true)
    private Component lanterna$subtitulo(Component c) {
        return lanterna$caber(c, 2);
    }

    private static Component lanterna$caber(Component c, int escala) {
        Minecraft mc = Minecraft.getInstance();
        if (c == null || mc.font == null) return c;
        int cabe = (mc.getWindow().getGuiScaledWidth() - 16) / escala;
        if (mc.font.width(c) <= cabe) return c;
        String texto = c.getString();
        long agora = System.currentTimeMillis();
        if (!(texto.equals(lanterna$ultima) && agora - lanterna$quando < 6000)) {
            lanterna$ultima = texto;
            lanterna$quando = agora;
            mc.gui.getChat().addMessage(c);
        }
        return com.lanterna.client.hud.Hud.fit(c, cabe);
    }
}
