package com.lanterna.mixin;

import com.lanterna.client.uniform.UniformTransition;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Com o uniforme do anel vestido, a segunda camada da skin (cabelo, chapéu, jaqueta, mangas, calça) fica escondida
 * onde o uniforme cobre. Antes ela ficava por cima do uniforme (é maior que ele) e aparecia como faixas pretas
 * (ou da cor do cabelo) em cima e embaixo da máscara.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    @Inject(method = "setModelProperties", at = @At("TAIL"))
    private void lanterna$escondeSegundaCamada(AbstractClientPlayer player, CallbackInfo ci) {
        if (player.isSpectator()) return;
        float p = UniformTransition.progress(player, 1f);
        if (p <= 0f) return;
        boolean pele = com.lanterna.client.uniform.PeleTraje.arquivo(player) != null;
        if (!pele && p < 0.35f) return;
        if ("nenhum".equals(com.lanterna.client.visual.ClientVisual.of(player.getId()).traje())) return;
        PlayerModel<AbstractClientPlayer> m = ((PlayerRenderer) (Object) this).getModel();
        String var = com.lanterna.client.visual.ClientVisual.of(player.getId()).traje();
        if (player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            // Trajes "segunda pele": a máscara fica POR CIMA da segunda camada da skin (cabeça inflada 0,62 > 0,5),
            // então a camada (cabelo, chapéu, barba...) continua aparecendo, como se a máscara fosse da própria skin.
            // Só os que cobrem o rosto/cabeça (caveira, capuzes) escondem a segunda camada da cabeça.
            // Roupa pintada na skin: a segunda camada da cabeça (cabelo, chapéu, máscara da skin) fica SEMPRE, por cima
            // da máscara do mod.
            if (!pele && (!com.lanterna.visual.Visual.eSegundaPele(var) || cobreRosto(player, var))) m.hat.visible = false;
        }
        if (player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
            m.jacket.visible = false;
            m.leftSleeve.visible = false;
            m.rightSleeve.visible = false;
        }
        if (player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()) {
            m.leftPants.visible = false;
            m.rightPants.visible = false;
        }
    }

    /** Trajes cujo capacete cobre o rosto ou a cabeça: aí o cabelo da frente da skin some e fica só a máscara. */
    private static boolean cobreRosto(AbstractClientPlayer player, String var) {
        String tropa = com.lanterna.armor.ModArmorItems.tropaOf(com.lanterna.LanternaMod.id(UniformTransition.corpsOf(player)));
        String t = tropa + "_" + var;
        return t.equals("preto_a") || t.equals("verde_b") || t.equals("laranja_b");
    }
}
