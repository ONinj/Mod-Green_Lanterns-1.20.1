package com.lanterna.client.uniform;

import com.lanterna.LanternaMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Etapas da textura do uniforme 3D (128x128) durante a transformação: só a camada de base
 * (o canto 64x64 em layout de skin) aparece pixel por pixel, subindo dos pés até a cabeça.
 * As peças 3D aparecem cubinho por cubinho (veja {@link RingUniformRenderer}).
 */
public final class UniformDissolve3D {
    public static final int STAGES = 48;
    private static final float SCATTER = 0.16f;
    private static final float BAND = 0.07f;

    private record Pair(ResourceLocation reveal, ResourceLocation band) {}

    private static final Map<String, Pair[]> CACHE = new HashMap<>();
    private static final Map<String, NativeImage> SOURCE = new HashMap<>();

    public static ResourceLocation texture(String tropa) {
        return LanternaMod.id("textures/item/armor/uniforme_" + tropa + ".png");
    }

    private static NativeImage source(String tropa) {
        return SOURCE.computeIfAbsent(tropa, t -> {
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(texture(t))) {
                return NativeImage.read(in);
            } catch (Exception e) {
                return null;
            }
        });
    }

    /** {revelada, faixa brilhante} para o progresso p, ou null se a textura não existir. */
    public static ResourceLocation[] stage(String tropa, float p) {
        NativeImage src = source(tropa);
        if (src == null) return null;
        Pair[] stages = CACHE.computeIfAbsent(tropa, t -> new Pair[STAGES]);
        int s = Math.min(STAGES - 1, Math.max(0, (int) (p * STAGES)));
        if (stages[s] == null) stages[s] = build(tropa, src, (s + 1f) / STAGES, s);
        return new ResourceLocation[]{stages[s].reveal, stages[s].band};
    }

    private static Pair build(String tropa, NativeImage src, float p, int s) {
        int w = src.getWidth(), h = src.getHeight();
        int unit = w / 128 == 0 ? 1 : w / 128;     // pixels de textura por "pixel" do layout 128
        NativeImage reveal = new NativeImage(w, h, true);
        NativeImage band = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int lx = x / unit, ly = y / unit;
                int color = src.getPixelRGBA(x, y);
                if (lx >= 64 || ly >= 64) { reveal.setPixelRGBA(x, y, 0); band.setPixelRGBA(x, y, 0); continue; }
                float height = UniformDissolve.heightOf(lx, ly);
                int hash = (lx * 73856093) ^ (ly * 19349663) ^ 0x5bd1e995;
                hash ^= hash >>> 13; hash *= 0x5bd1e995; hash ^= hash >>> 15;
                float noise = (hash & 0xFFFF) / 65535f;
                float th = height < 0 ? 0f : height * (1f - SCATTER) + noise * SCATTER;
                boolean shown = th <= p;
                reveal.setPixelRGBA(x, y, shown ? color : 0);
                boolean fresh = shown && th > p - BAND && ((color >>> 24) & 0xFF) > 0;
                int alpha = fresh ? (int) (255 * (1f - (p - th) / BAND)) : 0;
                band.setPixelRGBA(x, y, fresh ? (alpha << 24) | 0xFFFFFF : 0);
            }
        }
        var tm = Minecraft.getInstance().getTextureManager();
        ResourceLocation r = LanternaMod.id("dynamic/uniform3d_" + tropa + "_" + s + "_r");
        ResourceLocation b = LanternaMod.id("dynamic/uniform3d_" + tropa + "_" + s + "_b");
        tm.register(r, new DynamicTexture(reveal));
        tm.register(b, new DynamicTexture(band));
        return new Pair(r, b);
    }

    public static boolean exists(String tropa) {
        return tropa != null && source(tropa) != null;
    }

    public static void clear() {
        var tm = Minecraft.getInstance().getTextureManager();
        CACHE.values().forEach(arr -> {
            for (Pair pr : arr) if (pr != null) { tm.release(pr.reveal); tm.release(pr.band); }
        });
        CACHE.clear();
    }

    private UniformDissolve3D() {}
}
