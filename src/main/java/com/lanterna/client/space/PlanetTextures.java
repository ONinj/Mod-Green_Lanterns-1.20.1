package com.lanterna.client.space;

import com.lanterna.space.Archetype;
import com.lanterna.space.SpaceBodies;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.HashSet;
import java.util.Set;

/**
 * Textura de cada planeta gerado, criada na hora a partir da semente dele:
 * continentes, mares, montanhas, nuvens e calotas polares nas cores do tipo do planeta.
 * O ruído é calculado sobre a esfera, então não há emenda na textura.
 */
public final class PlanetTextures {
    private static final int W = 128, H = 64;
    private static final Set<ResourceLocation> MADE = new HashSet<>();

    public static void ensure(SpaceBodies.Body body) {
        ResourceLocation id = body.texture();
        if (MADE.contains(id) || body.procedural() == null) return;
        MADE.add(id);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(paint(body)));
    }

    private static NativeImage paint(SpaceBodies.Body body) {
        Archetype type = body.procedural().type();
        long seed = body.procedural().seed();
        int[] pal = type.palette;
        NativeImage img = new NativeImage(W, H, false);
        double scale = 1.6 + (seed & 7) * 0.25;
        for (int y = 0; y < H; y++) {
            double theta = Math.PI * (y + 0.5) / H;
            for (int x = 0; x < W; x++) {
                double phi = Math.PI * 2 * (x + 0.5) / W;
                double nx = Math.sin(theta) * Math.cos(phi), ny = Math.cos(theta), nz = Math.sin(theta) * Math.sin(phi);
                double h = fbm(nx * scale, ny * scale, nz * scale, seed, 5);
                int color;
                if (h < type.sea - 0.12) color = pal[0];
                else if (h < type.sea) color = mix(pal[0], pal[1], (h - (type.sea - 0.12)) / 0.12);
                else if (h < type.sea + 0.22) color = mix(pal[2], pal[3], (h - type.sea) / 0.22);
                else color = mix(pal[3], pal[4], Mth.clamp((h - type.sea - 0.22) / 0.2, 0, 1));

                // Lava brilhando nos volcânicos / cristais brilhando
                if (type == Archetype.VOLCANIC && h < type.sea) color = mix(color, 0xFFD060, 0.25);
                // Calotas polares
                if (type.polarCaps && Math.abs(ny) > 0.82 - fbm(nx * 4, ny * 4, nz * 4, seed + 7, 2) * 0.15) color = mix(color, 0xF4F8FF, 0.9);
                // Nuvens (onde há atmosfera)
                if (type.breathable) {
                    double c = fbm(nx * 3.2 + 9, ny * 5.0, nz * 3.2, seed + 99, 4);
                    if (c > 0.58) color = mix(color, 0xFFFFFF, Mth.clamp((c - 0.58) * 3, 0, 0.85));
                }
                // Crateras nos sem atmosfera
                if (type == Archetype.BARREN) {
                    double cr = fbm(nx * 9, ny * 9, nz * 9, seed + 3, 2);
                    if (cr > 0.68) color = mix(color, 0x2A2A30, 0.5);
                }
                img.setPixelRGBA(x, y, 0xFF000000 | abgr(color));
            }
        }
        return img;
    }

    // ---- ruído ----

    private static double fbm(double x, double y, double z, long seed, int octaves) {
        double sum = 0, amp = 0.5, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise(x, y, z, seed + i * 1013);
            norm += amp;
            x *= 2.03; y *= 2.03; z *= 2.03;
            amp *= 0.5;
        }
        return sum / norm;
    }

    private static double noise(double x, double y, double z, long seed) {
        int x0 = Mth.floor(x), y0 = Mth.floor(y), z0 = Mth.floor(z);
        double fx = smooth(x - x0), fy = smooth(y - y0), fz = smooth(z - z0);
        double c000 = hash(x0, y0, z0, seed), c100 = hash(x0 + 1, y0, z0, seed);
        double c010 = hash(x0, y0 + 1, z0, seed), c110 = hash(x0 + 1, y0 + 1, z0, seed);
        double c001 = hash(x0, y0, z0 + 1, seed), c101 = hash(x0 + 1, y0, z0 + 1, seed);
        double c011 = hash(x0, y0 + 1, z0 + 1, seed), c111 = hash(x0 + 1, y0 + 1, z0 + 1, seed);
        double a = Mth.lerp(fx, c000, c100), b = Mth.lerp(fx, c010, c110);
        double c = Mth.lerp(fx, c001, c101), d = Mth.lerp(fx, c011, c111);
        return Mth.lerp(fz, Mth.lerp(fy, a, b), Mth.lerp(fy, c, d));
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double hash(int x, int y, int z, long seed) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h ^= h >>> 33; h *= 0xFF51AFD7ED558CCDL; h ^= h >>> 33; h *= 0xC4CEB9FE1A85EC53L; h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static int mix(int a, int b, double t) {
        t = Mth.clamp(t, 0, 1);
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    private static int abgr(int rgb) {
        return ((rgb & 0xFF) << 16) | (rgb & 0xFF00) | ((rgb >> 16) & 0xFF);
    }

    public static void clear() {
        var tm = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation id : MADE) tm.release(id);
        MADE.clear();
    }

    private PlanetTextures() {}
}
