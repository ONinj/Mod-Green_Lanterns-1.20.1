package com.lanterna.client.guilda;

import com.lanterna.client.hud.Hud;
import com.lanterna.guilda.GuildasSyncPacket;
import com.lanterna.space.SpaceBodies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * No espaço, ao olhar para um planeta conquistado: de qual guilda, quem é o dono, quantas tropas defendem,
 * quantas tropas de ataque a SUA guilda tem e se dá para invadir agora.
 */
public final class GuildaHud {
    private static final int W = 210;

    public static final IGuiOverlay HUD = (ForgeGui gui, GuiGraphics g, float pt, int width, int height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null || !mc.player.level().dimension().equals(SpaceBodies.SPACE)) return;
        Vec3 eye = mc.player.getEyePosition(pt);
        Vec3 look = mc.player.getViewVector(pt);
        SpaceBodies.Body alvo = null;
        GuildasSyncPacket.P dado = null;
        double melhor = Double.MAX_VALUE;
        for (SpaceBodies.Body b : SpaceBodies.near(eye, 1)) {
            GuildasSyncPacket.P p = ClientGuildas.planeta(b.id());
            if (p == null) continue;
            Vec3 rel = b.center().subtract(eye);
            double dist = rel.length();
            if (dist - b.radius() > 5000) continue;
            double ang = Math.acos(Math.max(-1, Math.min(1, rel.normalize().dot(look))));
            double raio = Math.asin(Math.min(1, b.radius() * 1.16 / dist)) + Math.toRadians(2);
            if (ang <= raio && dist < melhor) {
                melhor = dist;
                alvo = b;
                dado = p;
            }
        }
        if (alvo == null) return;
        GuildasSyncPacket.G dona = ClientGuildas.guilda(dado.guilda());
        if (dona == null) return;
        GuildasSyncPacket.G minha = ClientGuildas.minha();
        boolean minhaDona = minha != null && minha.id().equals(dona.id());
        Font font = mc.font;
        int x = 8, y = Math.max(120, height / 2 - 30);
        int h = 104;
        int cor = dona.cor();
        Hud.panel(g, x, y, W, h, cor, 0.92f);
        g.blit(ClientGuildas.simbolo(dona.id()), x + 8, y + 8, 0, 0, 30, 30, 30, 30);
        g.drawString(font, Hud.caps(Component.translatable("guilda.lanterna.hud.conquistado")), x + 44, y + 7, Hud.argb(cor, 255), false);
        g.drawString(font, Hud.bold(alvo.displayName()), x + 44, y + 19, 0xFFFFFFFF, false);
        g.drawString(font, Hud.body(Component.translatable("guilda.lanterna.hud.guilda", dona.nome())), x + 44, y + 30, 0xFFD8E0E4, false);
        int ly = y + 44;
        g.fill(x + 8, ly, x + W - 8, ly + 1, 0x30FFFFFF);
        ly += 5;
        g.drawString(font, Hud.body(Component.translatable("guilda.lanterna.hud.dono", dona.dono())), x + 10, ly, 0xFFD8E0E4, false);
        ly += 11;
        g.drawString(font, Hud.body(Component.translatable("guilda.lanterna.hud.defesa", dado.defesa())), x + 10, ly, 0xFFFF9A80, false);
        ly += 11;
        Component atk = minhaDona ? Component.translatable("guilda.lanterna.hud.sua")
                : minha == null ? Component.translatable("guilda.lanterna.hud.sem_guilda")
                : Component.translatable("guilda.lanterna.hud.ataque", minha.ataque());
        g.drawString(font, Hud.body(atk), x + 10, ly, minhaDona ? 0xFF9FE0A0 : 0xFF9AC8FF, false);
        ly += 13;
        long escudo = ClientGuildas.escudoRestante(dado);
        Component st;
        int sc;
        if (dado.invadindo()) {
            st = Component.translatable("guilda.lanterna.hud.invadindo");
            sc = (mc.player.tickCount / 8) % 2 == 0 ? 0xFFFF4040 : 0xFFB02020;
        } else if (escudo > 0) {
            st = Component.translatable("guilda.lanterna.hud.escudo", com.lanterna.guilda.Invasao.tempo(escudo));
            sc = 0xFF80D8FF;
        } else {
            st = Component.translatable(minhaDona ? "guilda.lanterna.hud.vulneravel_seu" : "guilda.lanterna.hud.vulneravel");
            sc = 0xFFFFC040;
        }
        Hud.textCenter(g, Hud.bold(st), x + W / 2, ly, sc);
    };

    private GuildaHud() {}
}
