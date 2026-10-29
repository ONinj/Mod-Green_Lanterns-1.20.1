package com.lanterna.client.uniform;

import com.lanterna.LanternaMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Uniforme surgindo PIXEL POR PIXEL, dos pés até a cabeça.
 *
 * Cada pixel da textura do uniforme sabe em que altura do corpo ele fica (pé = 0, topo da cabeça = 1).
 * Com o progresso p da transformação, aparecem os pixels abaixo da "frente" de energia, que sobe
 * pelo corpo. Um pouco de sorteio por pixel deixa a borda quebrada, pixelada, em vez de uma linha reta.
 * A faixa logo abaixo da frente (os pixels recém-formados) brilha na cor da Tropa.
 *
 * As etapas são texturas prontas (geradas uma vez por Tropa), então não pesa no FPS.
 */
public final class UniformDissolve {
    public static final int STAGES = 48;
    /** Quanto o sorteio por pixel espalha a borda (0 = linha reta). */
    private static final float SCATTER = 0.16f;
    /** Espessura da faixa brilhante. */
    private static final float BAND = 0.07f;

    /** Partes do corpo na textura 64x64: (u, v, largura, altura, profundidade, altura do topo em pixels). */
    private static final int[][] BOXES = {
            {0, 0, 8, 8, 8, 32},     // cabeça
            {16, 16, 8, 12, 4, 24},  // tronco
            {40, 16, 4, 12, 4, 24},  // braço direito
            {32, 48, 4, 12, 4, 24},  // braço esquerdo
            {0, 16, 4, 12, 4, 12},   // perna direita
            {16, 48, 4, 12, 4, 12},  // perna esquerda
    };

    private record Pair(ResourceLocation reveal, ResourceLocation band) {}

    private static final Map<String, Pair[]> CACHE = new HashMap<>();
    private static final Map<String, float[]> THRESHOLDS = new HashMap<>();

    /** Altura (0..1) do pixel no corpo, ou -1 se o pixel não pertence às partes do corpo. */
    static float heightOf(int x, int y) {
        for (int[] b : BOXES) {
            int u = b[0], v = b[1], w = b[2], h = b[3], d = b[4], top = b[5];
            if (x < u || x >= u + 2 * d + 2 * w || y < v || y >= v + d + h) continue;
            if (y < v + d) {
                if (x >= u + d && x < u + d + w) return top / 32f;                 // face de cima
                if (x >= u + d + w && x < u + d + 2 * w) return (top - h) / 32f;   // face de baixo
                return -1f;
            }
            int row = y - (v + d);
            return (top - row - 0.5f) / 32f;
        }
        return -1f;
    }

    private static float[] thresholds(String corps, int width, int height) {
        return THRESHOLDS.computeIfAbsent(corps + "_" + width + "x" + height, k -> {
            float[] t = new float[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int sx = x * 64 / width, sy = y * 64 / height;
                    float h = heightOf(sx, sy);
                    // Sorteio fixo por pixel (sempre o mesmo, para a animação não "piscar")
                    int hash = (sx * 73856093) ^ (sy * 19349663) ^ 0x5bd1e995;
                    hash ^= hash >>> 13; hash *= 0x5bd1e995; hash ^= hash >>> 15;
                    float noise = (hash & 0xFFFF) / 65535f;
                    t[y * width + x] = h < 0 ? 0f : h * (1f - SCATTER) + noise * SCATTER;
                }
            }
            return t;
        });
    }

    /** Texturas da etapa para o progresso p (0..1). Null se não der para montar (usa a textura inteira). */
    public static ResourceLocation[] stage(String corps, float p) {
        NativeImage src = UniformTextures.image(corps);
        if (src == null) return null;
        Pair[] stages = CACHE.computeIfAbsent(corps, c -> new Pair[STAGES]);
        int s = Math.min(STAGES - 1, Math.max(0, (int) (p * STAGES)));
        if (stages[s] == null) stages[s] = build(corps, src, (s + 1f) / STAGES, s);
        return new ResourceLocation[]{stages[s].reveal, stages[s].band};
    }

    private static Pair build(String corps, NativeImage src, float p, int s) {
        int w = src.getWidth(), h = src.getHeight();
        float[] t = thresholds(corps, w, h);
        NativeImage reveal = new NativeImage(w, h, true);
        NativeImage band = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int color = src.getPixelRGBA(x, y);
                float th = t[y * w + x];
                boolean shown = th <= p;
                reveal.setPixelRGBA(x, y, shown ? color : 0);
                // Faixa brilhante: pixels recém-formados (só onde o uniforme tem algo pintado)
                boolean fresh = shown && th > p - BAND && ((color >>> 24) & 0xFF) > 0;
                int alpha = fresh ? (int) (255 * (1f - (p - th) / BAND)) : 0;
                band.setPixelRGBA(x, y, fresh ? (alpha << 24) | 0xFFFFFF : 0);
            }
        }
        var tm = Minecraft.getInstance().getTextureManager();
        ResourceLocation r = LanternaMod.id("dynamic/dissolve_" + corps + "_" + s + "_r");
        ResourceLocation b = LanternaMod.id("dynamic/dissolve_" + corps + "_" + s + "_b");
        tm.register(r, new DynamicTexture(reveal));
        tm.register(b, new DynamicTexture(band));
        return new Pair(r, b);
    }

    /** Altura (0..1) em que cada peça 3D aparece: quando a frente passa pelo meio da parte. */
    public static float pieceThreshold(String part) {
        return switch (part) {
            case "head" -> 0.88f;
            case "body", "right_arm", "left_arm" -> 0.62f;
            default -> 0.25f;
        };
    }

    public static void clear() {
        var tm = Minecraft.getInstance().getTextureManager();
        CACHE.values().forEach(arr -> {
            for (Pair pr : arr) if (pr != null) { tm.release(pr.reveal); tm.release(pr.band); }
        });
        CACHE.clear();
    }

    private UniformDissolve() {}
}
