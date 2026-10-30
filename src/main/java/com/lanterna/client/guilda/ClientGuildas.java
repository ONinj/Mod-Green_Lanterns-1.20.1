package com.lanterna.client.guilda;

import com.lanterna.LanternaMod;
import com.lanterna.guilda.Guilda;
import com.lanterna.guilda.GuildasSyncPacket;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** O que o cliente sabe das guildas: dono de cada planeta, defesa, escudo e a textura do símbolo de cada guilda. */
public final class ClientGuildas {
    private static final Map<UUID, GuildasSyncPacket.G> GUILDAS = new HashMap<>();
    private static final Map<String, GuildasSyncPacket.P> PLANETAS = new HashMap<>();
    private static final Map<UUID, DynamicTexture> SIMBOLOS = new HashMap<>();
    private static final Map<UUID, DynamicTexture> ESTANDARTES = new HashMap<>();
    private static final Map<UUID, byte[]> FEITOS = new HashMap<>();
    private static long recebido;

    public static void receber(GuildasSyncPacket m) {
        GUILDAS.clear();
        for (var g : m.guildas()) GUILDAS.put(g.id(), g);
        PLANETAS.clear();
        for (var p : m.planetas()) PLANETAS.put(p.planeta(), p);
        recebido = System.currentTimeMillis();
        // texturas: refaz as que mudaram
        for (var g : m.guildas()) {
            byte[] antes = FEITOS.get(g.id());
            if (antes == null || !Arrays.equals(antes, g.simbolo())) {
                FEITOS.put(g.id(), g.simbolo().clone());
                atualizar(g);
            }
        }
    }

    @Nullable
    public static GuildasSyncPacket.P planeta(String id) {
        return PLANETAS.get(id);
    }

    @Nullable
    public static GuildasSyncPacket.G guilda(UUID id) {
        return GUILDAS.get(id);
    }

    @Nullable
    public static GuildasSyncPacket.G minha() {
        var p = Minecraft.getInstance().player;
        if (p == null) return null;
        for (var g : GUILDAS.values()) if (g.membros().contains(p.getUUID())) return g;
        return null;
    }

    /** Quanto falta do escudo agora (descontando o tempo desde a última sincronia). */
    public static long escudoRestante(GuildasSyncPacket.P p) {
        return Math.max(0, p.escudoMs() - (System.currentTimeMillis() - recebido));
    }

    public static ResourceLocation simbolo(UUID guilda) {
        return LanternaMod.id("dynamic/guilda/" + guilda);
    }

    public static ResourceLocation estandarte(UUID guilda) {
        return LanternaMod.id("dynamic/estandarte/" + guilda);
    }

    private static void atualizar(GuildasSyncPacket.G g) {
        var tm = Minecraft.getInstance().getTextureManager();
        // Símbolo puro (fundo transparente), 64x64 com contorno escuro para ler de longe
        NativeImage img = new NativeImage(64, 64, true);
        NativeImage est = new NativeImage(32, 48, true);
        int fundo = escurecer(g.cor(), 0.45f);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) img.setPixelRGBA(x, y, 0);
        for (int y = 0; y < 48; y++) for (int x = 0; x < 32; x++) {
            boolean borda = x == 0 || x == 31 || y == 0 || (y >= 40 && Math.abs(x - 15.5) > (47 - y) * 2);
            boolean corte = y >= 40 && Math.abs(x - 15.5) > (47 - y) * 2 + 1;
            est.setPixelRGBA(x, y, corte ? 0 : abgr(borda ? g.cor() : fundo));
        }
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int idx = g.simbolo()[y * 16 + x];
            if (idx <= 0 || idx >= Guilda.PALETA.length) continue;
            int c = Guilda.PALETA[idx];
            for (int a = 0; a < 4; a++) for (int b = 0; b < 4; b++) img.setPixelRGBA(x * 4 + a, y * 4 + b, abgr(c));
            est.setPixelRGBA(8 + x, 12 + y, abgr(c));
        }
        // contorno
        NativeImage out = new NativeImage(64, 64, true);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            int c = img.getPixelRGBA(x, y);
            if (((c >>> 24) & 255) > 0) { out.setPixelRGBA(x, y, c); continue; }
            boolean perto = false;
            for (int dy = -2; dy <= 2 && !perto; dy++) for (int dx = -2; dx <= 2; dx++) {
                int xx = x + dx, yy = y + dy;
                if (xx < 0 || yy < 0 || xx > 63 || yy > 63) continue;
                if (((img.getPixelRGBA(xx, yy) >>> 24) & 255) > 0) { perto = true; break; }
            }
            out.setPixelRGBA(x, y, perto ? abgr(0xC0000000 | (escurecer(g.cor(), 0.25f) & 0xFFFFFF)) : 0);
        }
        img.close();
        DynamicTexture velho = SIMBOLOS.put(g.id(), new DynamicTexture(out));
        tm.register(simbolo(g.id()), SIMBOLOS.get(g.id()));
        if (velho != null) velho.close();
        DynamicTexture velhoE = ESTANDARTES.put(g.id(), new DynamicTexture(est));
        tm.register(estandarte(g.id()), ESTANDARTES.get(g.id()));
        if (velhoE != null) velhoE.close();
    }

    static int escurecer(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 255) * f), gg = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    /** ARGB -> ABGR (formato do NativeImage). */
    static int abgr(int argb) {
        int a = (argb >>> 24) & 255, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        if (a == 0 && argb != 0) a = 255;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private ClientGuildas() {}
}
