package com.lanterna.client.combate;

import com.lanterna.LanternaMod;
import com.lanterna.lore.block.AltarPurificacao;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 0.51: deitado no Altar da Purificação, o jogo não abre a tela de "sair da cama" (o ritual termina sozinho).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RitualCliente {
    @SubscribeEvent
    public static void tela(ScreenEvent.Opening e) {
        if (!(e.getNewScreen() instanceof InBedChatScreen)) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        var cama = mc.player.getSleepingPos().orElse(null);
        if (cama != null && mc.level.getBlockState(cama).getBlock() instanceof AltarPurificacao) e.setCanceled(true);
    }

    private static long inicio = -1;

    /** Durante a cena, só os títulos aparecem (sem barra, painéis e chat). */
    @SubscribeEvent
    public static void hud(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre e) {
        if (inicio < 0) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isSleeping()) return;
        if (e.getOverlay() == net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.TITLE_TEXT.type()) return;
        e.setCanceled(true);
    }

    /** Os painéis do mod (anel, missão) também somem durante a cena. */
    public static boolean emCena() {
        var mc = Minecraft.getInstance();
        return inicio >= 0 && mc.player != null && mc.player.isSleeping() || com.lanterna.client.vida.CenaEmocoes.ativa();
    }

    /**
     * A câmera de cinema do ritual: deitado no altar, a câmera gira devagar em volta, de cima para mais perto, sempre
     * olhando para o corpo. {x, y, z, yaw, pitch} ou null.
     */
    public static double[] camera(net.minecraft.world.entity.Entity e, float pt) {
        var mc = Minecraft.getInstance();
        if (e != mc.player || mc.level == null || !mc.player.isSleeping()) { inicio = -1; return null; }
        var cama = mc.player.getSleepingPos().orElse(null);
        if (cama == null || !(mc.level.getBlockState(cama).getBlock() instanceof AltarPurificacao)) { inicio = -1; return null; }
        long agora = mc.level.getGameTime();
        if (inicio < 0) inicio = agora;
        double t = (agora - inicio) + pt;
        var f = mc.level.getBlockState(cama).getValue(AltarPurificacao.FACING);
        double[] m = AltarPurificacao.gira(0, 0.55, f);
        double cx = cama.getX() + 0.5 + m[0], cy = cama.getY() + 0.9, cz = cama.getZ() + 0.5 + m[1];
        double ang = Math.toRadians(f.toYRot()) + 0.9 + t * 0.011;
        double raio = 4.6 - Math.min(1.4, t * 0.008), alt = 3.4 - Math.min(1.5, t * 0.009);
        double x = cx + Math.cos(ang) * raio, z = cz + Math.sin(ang) * raio, y = cy + alt;
        double dx = cx - x, dy = cy - y, dz = cz - z;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz)), pitch = Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return new double[]{x, y, z, yaw, pitch};
    }

    private RitualCliente() {}
}
