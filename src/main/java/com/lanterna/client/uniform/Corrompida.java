package com.lanterna.client.uniform;

import com.lanterna.LanternaMod;
import com.lanterna.client.ClientVisibleRings;
import com.lanterna.item.PowerRingItem;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * O traje de quem a Morte tocou (0.49.1):
 *  - corrompido (1): manchas cinzas espalhadas pela roupa, como se a cor estivesse morrendo aos poucos;
 *  - convertido em Lanterna Negro (2): o traje da Tropa de antes, todo em preto e branco, com contraste forte.
 *
 * As texturas são feitas na hora a partir das originais (nada novo para desenhar à mão), e guardadas.
 */
public final class Corrompida {
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();

    /** Corrupção do anel que o jogador está usando (0, 1 ou 2). */
    public static int nivel(Player p) {
        var ring = ClientVisibleRings.of(p.getId()).ring();
        return ring.isEmpty() ? 0 : PowerRingItem.getCorrupcao(ring);
    }

    /** Uma cópia da textura com a corrupção aplicada (ou a própria, se nível 0 ou se não der para ler). */
    public static ResourceLocation variante(ResourceLocation tex, int nivel) {
        if (nivel <= 0 || tex == null) return tex;
        String chave = tex + "#" + nivel;
        ResourceLocation pronto = CACHE.get(chave);
        if (pronto != null) return pronto;
        ResourceLocation id = tex;
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(tex)) {
            NativeImage img = NativeImage.read(in);
            aplicar(img, nivel, Math.max(1, img.getWidth() / 64), false);
            id = LanternaMod.id("dynamic/corrompida_" + nivel + "/" + tex.getNamespace() + "/" + tex.getPath().replace(".png", ""));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
        } catch (Exception e) {
            LanternaMod.LOGGER.warn("Lanterna: sem versão corrompida de {}: {}", tex, e.toString());
        }
        CACHE.put(chave, id);
        return id;
    }

    /**
     * Pinta a imagem (mantém o alfa de cada pixel). esc = quantos pixels da imagem por pixel de skin (as manchas
     * seguem a grade de 64x64, para ficarem do mesmo tamanho em qualquer resolução). brilho = textura do que brilha.
     */
    public static void aplicar(NativeImage img, int nivel, int esc, boolean brilho) {
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
            int c = img.getPixelRGBA(x, y); // ABGR
            int a = (c >>> 24) & 255;
            if (a == 0) continue;
            int r = c & 255, g = (c >> 8) & 255, b = (c >> 16) & 255;
            float l = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
            float nr, ng, nb;
            if (nivel >= 2) {
                // preto e branco, com contraste: o escuro vira preto e o claro vira branco
                // 0.49.3: traje preto (do Portador Negro), com os detalhes claros em cinza e branco
                float v = brilho ? Math.min(1f, l * 1.25f + 0.1f) : clamp((l - 0.5f) * 1.7f + 0.36f);
                v = v * v * (3 - 2 * v);
                nr = ng = nb = v;
            } else {
                float m = mancha(x / (float) esc, y / (float) esc);
                if (m <= 0f) continue;
                float cinza = l * 0.82f + 0.04f;
                nr = r / 255f + (cinza - r / 255f) * m;
                ng = g / 255f + (cinza - g / 255f) * m;
                nb = b / 255f + (cinza - b / 255f) * m;
            }
            img.setPixelRGBA(x, y, (a << 24) | (px(nb) << 16) | (px(ng) << 8) | px(nr));
        }
    }

    /** Força da mancha cinza (0..1) num ponto da skin: manchas de tamanhos variados, com borda suave. */
    static float mancha(float x, float y) {
        float n = ruido(x / 6f, y / 6f) * 0.65f + ruido(x / 2.5f + 31f, y / 2.5f + 17f) * 0.35f;
        float m = (n - 0.5f) / 0.12f;
        return clamp(m) * 0.95f;
    }

    private static float ruido(float x, float y) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        float fx = x - xi, fy = y - yi;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float a = h(xi, yi), b = h(xi + 1, yi), c = h(xi, yi + 1), d = h(xi + 1, yi + 1);
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
    }

    private static float h(int x, int y) {
        int n = x * 374761393 + y * 668265263;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) & 0xFFFF) / 65535f;
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int px(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    public static void limpar() {
        var tm = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation id : CACHE.values()) if (id.getPath().startsWith("dynamic/")) tm.release(id);
        CACHE.clear();
    }

    private Corrompida() {}
}
