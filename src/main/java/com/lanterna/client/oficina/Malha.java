package com.lanterna.client.oficina;

import com.lanterna.client.construct.EnergyShapes;
import com.lanterna.construct.oficina.Projeto;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A malha de um construto desenhado: só as faces de fora (as de dentro, entre dois cubinhos, não aparecem) e os
 * contornos de luz só nas bordas da forma (não em cada cubinho), para parecer uma peça única de luz sólida.
 * Cada face tem uma "ordem" (0 a 1) para a animação de montar: os cubinhos aparecem a partir da base.
 */
public final class Malha {
    /** Direções: -X, +X, -Y, +Y, -Z, +Z. */
    static final int[][] DIR = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};

    public record Face(int x, int y, int z, int d, int tom, float ordem, int bordas) {}

    public final List<Face> faces = new ArrayList<>();

    private static final Map<Integer, Malha> CACHE = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Malha> e) {
            return size() > 48;
        }
    };

    public static Malha de(Projeto p) {
        return CACHE.computeIfAbsent(p.assinatura(), k -> new Malha(p));
    }

    /** Ponto de onde a montagem começa (a base do desenho, no meio). */
    private Malha(Projeto p) {
        float maxD = 1;
        for (int y = 0; y < Projeto.N; y++) for (int z = 0; z < Projeto.N; z++) for (int x = 0; x < Projeto.N; x++)
            if (p.tem(x, y, z)) maxD = Math.max(maxD, dist(x, y, z));
        for (int y = 0; y < Projeto.N; y++) for (int z = 0; z < Projeto.N; z++) for (int x = 0; x < Projeto.N; x++) {
            int v = p.get(x, y, z);
            if (v == 0) continue;
            float ordem = dist(x, y, z) / maxD;
            for (int d = 0; d < 6; d++) {
                int[] o = DIR[d];
                if (p.tem(x + o[0], y + o[1], z + o[2])) continue;
                // bordas: os 4 lados da face; acende onde o vizinho no mesmo plano não continua a mesma face
                int[][] lados = lados(d);
                int bordas = 0;
                for (int i = 0; i < 4; i++) {
                    int[] l = lados[i];
                    int nx = x + l[0], ny = y + l[1], nz = z + l[2];
                    boolean continua = p.tem(nx, ny, nz) && !p.tem(nx + o[0], ny + o[1], nz + o[2]);
                    if (!continua) bordas |= 1 << i;
                }
                faces.add(new Face(x, y, z, d, v - 1, ordem, bordas));
            }
        }
    }

    private static float dist(int x, int y, int z) {
        return Mth.sqrt((x - 7.5f) * (x - 7.5f) + y * y * 1.0f + (z - 7.5f) * (z - 7.5f));
    }

    /** Os 4 vizinhos no plano da face, na ordem das arestas de {@link #cantos}. */
    static int[][] lados(int d) {
        return switch (d) {
            case 0, 1 -> new int[][]{{0, -1, 0}, {0, 0, -1}, {0, 1, 0}, {0, 0, 1}};
            case 2, 3 -> new int[][]{{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}};
            default -> new int[][]{{0, -1, 0}, {1, 0, 0}, {0, 1, 0}, {-1, 0, 0}};
        };
    }

    /** 4 cantos da face (em unidades de cubinho), na ordem em que as arestas i ligam canto i a canto i+1. */
    static float[][] cantos(int x, int y, int z, int d) {
        float x0 = x, x1 = x + 1, y0 = y, y1 = y + 1, z0 = z, z1 = z + 1;
        return switch (d) {
            case 0 -> new float[][]{{x0, y0, z1}, {x0, y0, z0}, {x0, y1, z0}, {x0, y1, z1}};
            case 1 -> new float[][]{{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}};
            case 2 -> new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
            case 3 -> new float[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
            case 4 -> new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}};
            default -> new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
        };
    }

    public static int corTom(int cor, int tom) {
        return switch (tom) {
            case 1 -> EnergyShapes.clarear(cor, 0.22f);
            case 2 -> escurecer(cor, 0.5f);
            case 3 -> EnergyShapes.clarear(cor, 0.6f);
            default -> cor;
        };
    }

    static int escurecer(int rgb, float k) {
        int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Desenha no mundo. k = tamanho do cubinho; centro: x e z no meio da grade, y na base.
     * mostra = até que "ordem" já apareceu (montar); some = a partir de que ordem já sumiu (desmontar).
     */
    public void desenhar(PoseStack ps, VertexConsumer corpo, VertexConsumer brilho, int cor, float alfa, float k, float mostra, float some, float tempo) {
        Matrix4f m = ps.last().pose();
        int gc = EnergyShapes.brilho(cor);
        int borda = EnergyShapes.clarear(gc, 0.25f);
        float br = ((borda >> 16) & 255) / 255f, bg = ((borda >> 8) & 255) / 255f, bb = (borda & 255) / 255f;
        float w = 0.09f; // largura do contorno, em cubinhos
        for (Face f : faces) {
            if (f.ordem > mostra || f.ordem > some) continue;
            float entrada = Mth.clamp((mostra - f.ordem) * 6f, 0, 1);
            float a = alfa * entrada;
            int c = corTom(cor, f.tom);
            float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
            float sombra = f.d == 3 ? 1f : f.d == 2 ? 0.62f : (f.d == 4 || f.d == 5) ? 0.84f : 0.76f;
            float tr = 0.9f + 0.1f * Mth.sin(tempo * 0.3f + (f.x + f.y * 1.7f + f.z * 2.3f));
            float[][] q = cantos(f.x, f.y, f.z, f.d);
            quad(corpo, m, q, k, r * sombra, g * sombra, b * sombra, a * 0.78f * tr, tempo);
            if (f.bordas != 0) {
                for (int i = 0; i < 4; i++) {
                    if ((f.bordas & (1 << i)) == 0) continue;
                    float[] p0 = q[i], p1 = q[(i + 1) % 4];
                    // ponto do meio da face, para "entrar" um pouco na face a partir da aresta
                    float[] mid = {(q[0][0] + q[2][0]) / 2, (q[0][1] + q[2][1]) / 2, (q[0][2] + q[2][2]) / 2};
                    float[] a0 = lerp(p0, mid, w * 2), a1 = lerp(p1, mid, w * 2);
                    quad(brilho, m, new float[][]{p0, p1, a1, a0}, k, br, bg, bb, a * 0.4f * tr, tempo);
                }
            }
            // núcleo claro nos tons brancos
            if (f.tom == 3) quad(brilho, m, q, k, 1f, 1f, 1f, a * 0.1f, tempo);
        }
    }

    private static float[] lerp(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float[][] q, float k, float r, float g, float b, float a, float tempo) {
        for (float[] p : q) {
            float x = (p[0] - 8f) * k, y = p[1] * k, z = (p[2] - 8f) * k;
            float u = x * 0.55f + z * 0.32f + tempo * 0.011f, v = y * 0.55f - z * 0.27f + x * 0.12f - tempo * 0.017f;
            vc.vertex(m, x, y, z).color(r, g, b, a).uv(u, v).endVertex();
        }
    }
}
