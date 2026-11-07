package com.lanterna.client.oficina;

import com.lanterna.client.construct.EnergyShapes;
import com.lanterna.construct.oficina.Projeto;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.lanterna.client.render.LanternaRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A malha de um construto desenhado. Dois jeitos:
 *  liso      superfície contínua por cima dos cubinhos (Surface Nets): os degraus viram curvas, a peça parece
 *            uma forma só, de luz sólida;
 *  quadrado  só as faces de fora dos cubinhos, com contorno de luz nas bordas da forma (estilo bloco).
 * Os dois são desenhados com luz de verdade: sombra suave pela direção da face, brilho de borda (as beiradas
 * que viram para longe do olhar acendem, como vidro de energia), reflexo e um halo fino em volta. As faces de
 * trás não são desenhadas, para a peça não ficar leitosa com camadas umas sobre as outras.
 * Cada face tem uma "ordem" (0 a 1) para a animação de montar: aparece a partir da base, com uma faixa de luz
 * passando na frente da montagem.
 */
public final class Malha {
    /** Direções: -X, +X, -Y, +Y, -Z, +Z. */
    static final int[][] DIR = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};

    /** Por quadrilátero: 4 cantos (12 floats), 4 normais (12 floats), tom, ordem, bordas (só no quadrado). */
    private float[] pos = new float[1200], nor = new float[1200];
    private int[] tons = new int[100], bordas = new int[100];
    private float[] ordens = new float[100];
    public int quads;
    public final boolean liso;
    /** Ponto médio do desenho e meia-altura, em cubinhos. */
    public final float meioY;

    private static final Map<Integer, Malha> CACHE = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Malha> e) {
            return size() > 48;
        }
    };

    public static Malha de(Projeto p) {
        return CACHE.computeIfAbsent(p.assinatura(), k -> new Malha(p));
    }

    private Malha(Projeto p) {
        liso = p.liso;
        int[] b = p.limites();
        meioY = b == null ? 0 : (b[1] + b[4] + 1) / 2f;
        float maxD = 1;
        for (int y = 0; y < Projeto.N; y++) for (int z = 0; z < Projeto.N; z++) for (int x = 0; x < Projeto.N; x++)
            if (p.tem(x, y, z)) maxD = Math.max(maxD, dist(x, y, z));
        if (liso) superficie(p, maxD);
        else cubos(p, maxD);
    }

    private static float dist(int x, int y, int z) {
        float c = Projeto.MEIO - 0.5f;
        return Mth.sqrt((x - c) * (x - c) + y * y * 1.0f + (z - c) * (z - c));
    }

    private void add(float[] q, float[] n, int tom, float ordem, int borda) {
        if (quads * 12 + 12 > pos.length) {
            pos = Arrays.copyOf(pos, pos.length * 2);
            nor = Arrays.copyOf(nor, nor.length * 2);
            tons = Arrays.copyOf(tons, tons.length * 2);
            ordens = Arrays.copyOf(ordens, ordens.length * 2);
            bordas = Arrays.copyOf(bordas, bordas.length * 2);
        }
        System.arraycopy(q, 0, pos, quads * 12, 12);
        System.arraycopy(n, 0, nor, quads * 12, 12);
        tons[quads] = tom;
        ordens[quads] = ordem;
        bordas[quads] = borda;
        quads++;
    }

    // ===================== Quadrado =====================

    private void cubos(Projeto p, float maxD) {
        for (int y = 0; y < Projeto.N; y++) for (int z = 0; z < Projeto.N; z++) for (int x = 0; x < Projeto.N; x++) {
            int v = p.get(x, y, z);
            if (v == 0) continue;
            float ordem = dist(x, y, z) / maxD;
            for (int d = 0; d < 6; d++) {
                int[] o = DIR[d];
                if (p.tem(x + o[0], y + o[1], z + o[2])) continue;
                int[][] lados = lados(d);
                int bs = 0;
                for (int i = 0; i < 4; i++) {
                    int[] l = lados[i];
                    int nx = x + l[0], ny = y + l[1], nz = z + l[2];
                    boolean continua = p.tem(nx, ny, nz) && !p.tem(nx + o[0], ny + o[1], nz + o[2]);
                    if (!continua) bs |= 1 << i;
                }
                float[][] c = cantos(x, y, z, d);
                float[] q = new float[12], n = new float[12];
                for (int i = 0; i < 4; i++) {
                    System.arraycopy(c[i], 0, q, i * 3, 3);
                    n[i * 3] = o[0]; n[i * 3 + 1] = o[1]; n[i * 3 + 2] = o[2];
                }
                add(q, n, v - 1, ordem, bs);
            }
        }
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

    // ===================== Liso (Surface Nets) =====================

    /**
     * Um vértice em cada célula entre 8 centros de cubinho que tem cheio e vazio misturados (no meio das
     * arestas que cruzam a borda), depois relaxado algumas vezes na direção dos vizinhos, sem sair da célula.
     * Cada aresta entre um centro cheio e um vazio vira um quadrilátero ligando as 4 células em volta dela.
     */
    private void superficie(Projeto p, float maxD) {
        final int N = Projeto.N, C = N + 1; // células de -1 a N-1 em cada eixo
        int[] vid = new int[C * C * C];
        Arrays.fill(vid, -1);
        float[] vp = new float[3 * 4096];
        float[] cel = new float[3 * 4096];
        int nv = 0;
        for (int cx = -1; cx < N; cx++) for (int cy = -1; cy < N; cy++) for (int cz = -1; cz < N; cz++) {
            int mask = 0;
            for (int i = 0; i < 8; i++) if (p.tem(cx + (i & 1), cy + ((i >> 1) & 1), cz + ((i >> 2) & 1))) mask |= 1 << i;
            if (mask == 0 || mask == 255) continue;
            float sx = 0, sy = 0, sz = 0;
            int cnt = 0;
            for (int i = 0; i < 8; i++) for (int a = 0; a < 3; a++) {
                int j = i | (1 << a);
                if (j == i) continue;
                if (((mask >> i) & 1) == ((mask >> j) & 1)) continue;
                sx += (i & 1) + ((j & 1) - (i & 1)) * 0.5f;
                sy += ((i >> 1) & 1) + (((j >> 1) & 1) - ((i >> 1) & 1)) * 0.5f;
                sz += ((i >> 2) & 1) + (((j >> 2) & 1) - ((i >> 2) & 1)) * 0.5f;
                cnt++;
            }
            if (nv * 3 + 3 > vp.length) { vp = Arrays.copyOf(vp, vp.length * 2); cel = Arrays.copyOf(cel, cel.length * 2); }
            // centro do cubinho (x) fica em x + 0,5
            vp[nv * 3] = cx + 0.5f + sx / cnt;
            vp[nv * 3 + 1] = cy + 0.5f + sy / cnt;
            vp[nv * 3 + 2] = cz + 0.5f + sz / cnt;
            cel[nv * 3] = cx + 0.5f; cel[nv * 3 + 1] = cy + 0.5f; cel[nv * 3 + 2] = cz + 0.5f;
            vid[((cx + 1) * C + (cy + 1)) * C + (cz + 1)] = nv++;
        }
        // quadriláteros (índices de vértice), com o lado de fora conhecido
        int[] qi = new int[4 * 4096];
        int[] qInfo = new int[3 * 1024]; // eixo e sinal, tom, cubinho de dentro (para a ordem)
        int nq = 0;
        for (int x = -1; x < N; x++) for (int y = -1; y < N; y++) for (int z = -1; z < N; z++) {
            boolean a0 = p.tem(x, y, z);
            for (int a = 0; a < 3; a++) {
                int qx = x + (a == 0 ? 1 : 0), qy = y + (a == 1 ? 1 : 0), qz = z + (a == 2 ? 1 : 0);
                boolean a1 = p.tem(qx, qy, qz);
                if (a0 == a1) continue;
                // as 4 células que têm esta aresta: no eixo a, a célula começa em x; nos outros dois, x-1 e x
                int[] ids = new int[4];
                boolean ok = true;
                for (int k = 0; k < 4; k++) {
                    int du = (k == 1 || k == 2) ? 0 : -1, dv = (k >= 2) ? 0 : -1;
                    int ccx = x, ccy = y, ccz = z;
                    if (a == 0) { ccy += du; ccz += dv; }
                    else if (a == 1) { ccz += du; ccx += dv; }
                    else { ccx += du; ccy += dv; }
                    if (ccx < -1 || ccy < -1 || ccz < -1 || ccx >= N || ccy >= N || ccz >= N) { ok = false; break; }
                    int id = vid[((ccx + 1) * C + (ccy + 1)) * C + (ccz + 1)];
                    if (id < 0) { ok = false; break; }
                    ids[k] = id;
                }
                if (!ok) continue;
                if (nq * 4 + 4 > qi.length) qi = Arrays.copyOf(qi, qi.length * 2);
                if (nq * 3 + 3 > qInfo.length) qInfo = Arrays.copyOf(qInfo, qInfo.length * 2);
                System.arraycopy(ids, 0, qi, nq * 4, 4);
                int ix = a0 ? x : qx, iy = a0 ? y : qy, iz = a0 ? z : qz;
                qInfo[nq * 3] = (a << 1) | (a0 ? 1 : 0); // a0 cheio: a normal aponta para +a
                qInfo[nq * 3 + 1] = p.get(ix, iy, iz) - 1;
                qInfo[nq * 3 + 2] = (ix * 64 + iy) * 64 + iz;
                nq++;
            }
        }
        // relaxa: puxa cada vértice para a média dos vizinhos, sem sair da célula (guarda as quinas fortes)
        int[] nviz = new int[nv];
        float[] soma = new float[nv * 3];
        for (int it = 0; it < 3; it++) {
            Arrays.fill(nviz, 0);
            Arrays.fill(soma, 0);
            for (int q = 0; q < nq; q++) for (int k = 0; k < 4; k++) {
                int u = qi[q * 4 + k], w = qi[q * 4 + (k + 1) % 4];
                for (int c = 0; c < 3; c++) { soma[u * 3 + c] += vp[w * 3 + c]; soma[w * 3 + c] += vp[u * 3 + c]; }
                nviz[u]++;
                nviz[w]++;
            }
            for (int v = 0; v < nv; v++) {
                if (nviz[v] == 0) continue;
                for (int c = 0; c < 3; c++) {
                    float alvo = soma[v * 3 + c] / nviz[v];
                    float novo = Mth.lerp(0.55f, vp[v * 3 + c], alvo);
                    vp[v * 3 + c] = Mth.clamp(novo, cel[v * 3 + c] + 0.02f, cel[v * 3 + c] + 0.98f);
                }
            }
        }
        // normais por vértice (média das faces em volta, pesada pela área)
        float[] vn = new float[nv * 3];
        float[] fn = new float[nq * 3];
        for (int q = 0; q < nq; q++) {
            int v0 = qi[q * 4], v1 = qi[q * 4 + 1], v2 = qi[q * 4 + 2], v3 = qi[q * 4 + 3];
            float ax = vp[v2 * 3] - vp[v0 * 3], ay = vp[v2 * 3 + 1] - vp[v0 * 3 + 1], az = vp[v2 * 3 + 2] - vp[v0 * 3 + 2];
            float bx = vp[v3 * 3] - vp[v1 * 3], by = vp[v3 * 3 + 1] - vp[v1 * 3 + 1], bz = vp[v3 * 3 + 2] - vp[v1 * 3 + 2];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            int eixo = qInfo[q * 3] >> 1;
            float sinal = (qInfo[q * 3] & 1) == 1 ? 1 : -1;
            float comp = eixo == 0 ? nx : eixo == 1 ? ny : nz;
            if (comp * sinal < 0) { nx = -nx; ny = -ny; nz = -nz; }
            fn[q * 3] = nx; fn[q * 3 + 1] = ny; fn[q * 3 + 2] = nz;
            for (int k = 0; k < 4; k++) {
                int v = qi[q * 4 + k];
                vn[v * 3] += nx; vn[v * 3 + 1] += ny; vn[v * 3 + 2] += nz;
            }
        }
        for (int v = 0; v < nv; v++) {
            float l = Mth.sqrt(vn[v * 3] * vn[v * 3] + vn[v * 3 + 1] * vn[v * 3 + 1] + vn[v * 3 + 2] * vn[v * 3 + 2]);
            if (l < 1e-6f) l = 1;
            vn[v * 3] /= l; vn[v * 3 + 1] /= l; vn[v * 3 + 2] /= l;
        }
        float[] q = new float[12], n = new float[12];
        for (int f = 0; f < nq; f++) {
            for (int k = 0; k < 4; k++) {
                int v = qi[f * 4 + k];
                System.arraycopy(vp, v * 3, q, k * 3, 3);
                System.arraycopy(vn, v * 3, n, k * 3, 3);
            }
            int c = qInfo[f * 3 + 2];
            int ix = c / 4096, iy = (c / 64) % 64, iz = c % 64;
            add(q, n, qInfo[f * 3 + 1], dist(ix, iy, iz) / maxD, 0);
        }
    }

    // ===================== Cor =====================

    /** A cor do anel com a saturação levantada (energia viva, não leitosa). */
    public static int vivo(int rgb) {
        float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
        if (hsb[1] < 0.12f) return rgb; // branco/cinza continua branco
        float s = Math.max(hsb[1], 0.82f), b = Math.max(hsb[2], 0.78f);
        return java.awt.Color.HSBtoRGB(hsb[0], s, b) & 0xFFFFFF;
    }

    public static int corTom(int cor, int tom) {
        int v = vivo(cor);
        return switch (tom) {
            case 1 -> EnergyShapes.clarear(v, 0.3f);
            case 2 -> escurecer(v, 0.42f);
            case 3 -> EnergyShapes.clarear(v, 0.72f);
            default -> v;
        };
    }

    static int escurecer(int rgb, float k) {
        int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
        return (r << 16) | (g << 8) | b;
    }

    // ===================== Desenho no mundo =====================

    /** Luz vinda de cima, um pouco da direita e da frente do olhar (no espaço da câmera). */
    private static final float LX = 0.32f, LY = 0.78f, LZ = 0.54f;

    /**
     * Desenha no mundo. k = tamanho do cubinho; centro: x e z no meio da grade, y na base.
     * mostra = até que "ordem" já apareceu (montar); some = a partir de que ordem já sumiu (desmontar).
     */
    public void desenhar(PoseStack ps, MultiBufferSource buf, int cor, float alfa, float k, float mostra, float some, float tempo) {
        // um tipo de cada vez: pedir outro tipo ao MultiBufferSource fecha o anterior (eles dividem o mesmo buffer)
        passe(ps, buf.getBuffer(LanternaRenderTypes.ENERGIA), null, cor, alfa, k, mostra, some, tempo, false);
        passe(ps, null, buf.getBuffer(LanternaRenderTypes.BRILHO), cor, alfa, k, mostra, some, tempo, true);
    }

    /** Cópia de luz (fantasma de movimento): só brilho somado, sem halo. */
    public void fantasma(PoseStack ps, MultiBufferSource buf, int cor, float alfa, float k, float tempo) {
        VertexConsumer b = buf.getBuffer(LanternaRenderTypes.BRILHO);
        passe(ps, b, b, cor, alfa, k, 2f, 2f, tempo, false);
    }

    /** corpo/brilho nulos = não desenha aquela parte nesta passada. */
    private void passe(PoseStack ps, VertexConsumer corpo, VertexConsumer brilho, int cor, float alfa, float k, float mostra, float some, float tempo, boolean halo) {
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        int gc = vivo(EnergyShapes.brilho(cor));
        float gr = ((gc >> 16) & 255) / 255f, gg = ((gc >> 8) & 255) / 255f, gb = (gc & 255) / 255f;
        int borda = EnergyShapes.clarear(gc, 0.35f);
        float br = ((borda >> 16) & 255) / 255f, bg = ((borda >> 8) & 255) / 255f, bb = (borda & 255) / 255f;
        boolean montando = mostra < 1.04f, desmontando = some < 1.04f;
        float hal = Math.max(0.03f, k * 0.9f); // espessura do halo, em blocos
        float[] vx = new float[4], vy = new float[4], vz = new float[4], nx = new float[4], ny = new float[4], nz = new float[4];
        float[] lx = new float[12];
        for (int f = 0; f < quads; f++) {
            float ordem = ordens[f];
            if (corpo == null && brilho == null) return;
            if (ordem > mostra || ordem > some) continue;
            float entrada = Mth.clamp((mostra - ordem) * 6f, 0, 1);
            float a = alfa * entrada;
            // faixa de luz na frente da montagem / da desmontagem
            float faixa = 0;
            if (montando) faixa = Math.max(faixa, Mth.clamp(1 - Math.abs(mostra - ordem) * 9f, 0, 1));
            if (desmontando) faixa = Math.max(faixa, Mth.clamp(1 - Math.abs(some - ordem) * 9f, 0, 1));
            int c = corTom(cor, tons[f]);
            float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
            float tr = 0.92f + 0.08f * Mth.sin(tempo * 0.25f + ordem * 9f);
            int o = f * 12;
            boolean frente = false;
            for (int i = 0; i < 4; i++) {
                float px = (pos[o + i * 3] - Projeto.MEIO) * k, py = pos[o + i * 3 + 1] * k, pz = (pos[o + i * 3 + 2] - Projeto.MEIO) * k;
                lx[i * 3] = px; lx[i * 3 + 1] = py; lx[i * 3 + 2] = pz;
                vx[i] = m.m00() * px + m.m10() * py + m.m20() * pz + m.m30();
                vy[i] = m.m01() * px + m.m11() * py + m.m21() * pz + m.m31();
                vz[i] = m.m02() * px + m.m12() * py + m.m22() * pz + m.m32();
                float ax = nor[o + i * 3], ay = nor[o + i * 3 + 1], az = nor[o + i * 3 + 2];
                float ex = nm.m00() * ax + nm.m10() * ay + nm.m20() * az;
                float ey = nm.m01() * ax + nm.m11() * ay + nm.m21() * az;
                float ez = nm.m02() * ax + nm.m12() * ay + nm.m22() * az;
                float l = Mth.sqrt(ex * ex + ey * ey + ez * ez);
                if (l < 1e-6f) l = 1;
                nx[i] = ex / l; ny[i] = ey / l; nz[i] = ez / l;
                float d = Mth.sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i]);
                if (d < 1e-5f) d = 1e-5f;
                if (-(nx[i] * vx[i] + ny[i] * vy[i] + nz[i] * vz[i]) / d > -0.08f) frente = true;
            }
            if (!frente) continue; // de costas para o olhar
            for (int i = 0; i < 4; i++) {
                float d = Math.max(1e-5f, Mth.sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i]));
                float ox = -vx[i] / d, oy = -vy[i] / d, oz = -vz[i] / d; // para o olho
                float olha = Mth.clamp(nx[i] * ox + ny[i] * oy + nz[i] * oz, 0, 1);
                float rim = 1 - olha;
                float rim2 = rim * rim;
                float luz = Math.max(0, nx[i] * LX + ny[i] * LY + nz[i] * LZ);
                float sombra = (0.5f + 0.5f * luz) * 0.9f;
                // reflexo (meio-vetor entre a luz e o olho)
                float hx = LX + ox, hy = LY + oy, hz = LZ + oz, hl = Math.max(1e-5f, Mth.sqrt(hx * hx + hy * hy + hz * hz));
                float spec = (float) Math.pow(Math.max(0, (nx[i] * hx + ny[i] * hy + nz[i] * hz) / hl), 28) * 0.55f;
                float px = lx[i * 3], py = lx[i * 3 + 1], pz = lx[i * 3 + 2];
                float u = px * 0.55f + pz * 0.32f + tempo * 0.011f, v = py * 0.55f - pz * 0.27f + px * 0.12f - tempo * 0.017f;
                if (corpo != null) corpo.vertex(m, px, py, pz).color(r * sombra, g * sombra, b * sombra, a * (0.74f + 0.2f * rim2) * tr).uv(u, v).endVertex();
            }
            if (brilho == null) continue;
            // brilho de borda + reflexo + faixa da montagem, somados por cima
            for (int i = 0; i < 4; i++) {
                float d = Math.max(1e-5f, Mth.sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i]));
                float ox = -vx[i] / d, oy = -vy[i] / d, oz = -vz[i] / d;
                float olha = Mth.clamp(nx[i] * ox + ny[i] * oy + nz[i] * oz, 0, 1);
                float rim = 1 - olha, rim3 = rim * rim * rim;
                float hx = LX + ox, hy = LY + oy, hz = LZ + oz, hl = Math.max(1e-5f, Mth.sqrt(hx * hx + hy * hy + hz * hz));
                float spec = (float) Math.pow(Math.max(0, (nx[i] * hx + ny[i] * hy + nz[i] * hz) / hl), 28) * 0.5f;
                float e = rim3 * 0.7f;
                float px = lx[i * 3], py = lx[i * 3 + 1], pz = lx[i * 3 + 2];
                brilho.vertex(m, px, py, pz)
                        .color(Math.min(1, gr * e + spec + faixa * 0.9f), Math.min(1, gg * e + spec + faixa * 0.9f), Math.min(1, gb * e + spec + faixa * 0.9f), a * tr)
                        .uv(px * 0.4f + tempo * 0.02f, py * 0.4f).endVertex();
            }
            // contorno nas bordas da forma (modo quadrado)
            if (bordas[f] != 0) {
                float[][] q = {{pos[o], pos[o + 1], pos[o + 2]}, {pos[o + 3], pos[o + 4], pos[o + 5]}, {pos[o + 6], pos[o + 7], pos[o + 8]}, {pos[o + 9], pos[o + 10], pos[o + 11]}};
                float[] mid = {(q[0][0] + q[2][0]) / 2, (q[0][1] + q[2][1]) / 2, (q[0][2] + q[2][2]) / 2};
                for (int i = 0; i < 4; i++) {
                    if ((bordas[f] & (1 << i)) == 0) continue;
                    float[] p0 = q[i], p1 = q[(i + 1) % 4];
                    float[] a0 = lerp(p0, mid, 0.18f), a1 = lerp(p1, mid, 0.18f);
                    for (float[] pp : new float[][]{p0, p1, a1, a0}) {
                        float px = (pp[0] - Projeto.MEIO) * k, py = pp[1] * k, pz = (pp[2] - Projeto.MEIO) * k;
                        brilho.vertex(m, px, py, pz).color(br, bg, bb, a * 0.55f * tr).uv(px * 0.5f, pz * 0.5f).endVertex();
                    }
                }
            }
            // halo: casca um pouco maior, só acesa nas beiradas da silhueta
            if (halo) {
                for (int i = 0; i < 4; i++) {
                    float d = Math.max(1e-5f, Mth.sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i]));
                    float olha = Mth.clamp(-(nx[i] * vx[i] + ny[i] * vy[i] + nz[i] * vz[i]) / d, 0, 1);
                    float rim = 1 - olha;
                    float h = rim * rim * rim * rim * 0.3f;
                    float ax = nor[o + i * 3], ay = nor[o + i * 3 + 1], az = nor[o + i * 3 + 2];
                    float px = lx[i * 3] + ax * hal, py = lx[i * 3 + 1] + ay * hal, pz = lx[i * 3 + 2] + az * hal;
                    brilho.vertex(m, px, py, pz).color(gr * h, gg * h, gb * h, a).uv(px * 0.3f, py * 0.3f - tempo * 0.01f).endVertex();
                }
            }
        }
    }

    // ===================== Desenho no editor =====================

    /**
     * Desenha opaco na vista do editor (BufferBuilder QUADS POSITION_COLOR). m = matriz da tela já com o modelo;
     * na tela o olho está em +Z e o "para cima" é -Y.
     */
    public void desenharGui(BufferBuilder bb, Matrix4f m, int cor, float mostra, boolean linhasCubo) {
        int gc = vivo(EnergyShapes.brilho(cor));
        float gr = ((gc >> 16) & 255) / 255f, gg = ((gc >> 8) & 255) / 255f, gbb = (gc & 255) / 255f;
        final float LXg = 0.3f, LYg = -0.72f, LZg = 0.62f;
        org.joml.Vector3f t = new org.joml.Vector3f();
        int linha = 0x38000000, bordaForma = 0xFF000000 | EnergyShapes.clarear(vivo(cor), 0.65f);
        for (int f = 0; f < quads; f++) {
            if (ordens[f] > mostra) continue;
            int c = corTom(cor, tons[f]);
            float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
            int o = f * 12;
            for (int i = 0; i < 4; i++) {
                m.transformDirection(nor[o + i * 3], nor[o + i * 3 + 1], nor[o + i * 3 + 2], t);
                t.normalize();
                float olha = Math.abs(t.z);
                float rim = 1 - olha;
                float luz = Math.max(0, t.x * LXg + t.y * LYg + t.z * LZg);
                float sombra = 0.42f + 0.58f * luz;
                float hx = LXg, hy = LYg, hz = LZg + 1, hl = Mth.sqrt(hx * hx + hy * hy + hz * hz);
                float spec = (float) Math.pow(Math.max(0, (t.x * hx + t.y * hy + t.z * hz) / hl), 30) * 0.45f;
                float e = rim * rim * rim * 0.7f;
                float rr = Math.min(1, r * sombra + gr * e + spec), g2 = Math.min(1, g * sombra + gg * e + spec), b2 = Math.min(1, b * sombra + gbb * e + spec);
                bb.vertex(m, pos[o + i * 3], pos[o + i * 3 + 1], pos[o + i * 3 + 2]).color(rr, g2, b2, 1f).endVertex();
            }
            if (!liso && linhasCubo) {
                float[][] q = {{pos[o], pos[o + 1], pos[o + 2]}, {pos[o + 3], pos[o + 4], pos[o + 5]}, {pos[o + 6], pos[o + 7], pos[o + 8]}, {pos[o + 9], pos[o + 10], pos[o + 11]}};
                float[] mid = {(q[0][0] + q[2][0]) / 2, (q[0][1] + q[2][1]) / 2, (q[0][2] + q[2][2]) / 2};
                float[] n = {nor[o] * 0.004f, nor[o + 1] * 0.004f, nor[o + 2] * 0.004f};
                for (int i = 0; i < 4; i++) {
                    boolean forma = (bordas[f] & (1 << i)) != 0;
                    float w = forma ? 0.2f : 0.08f;
                    int lc = forma ? bordaForma : linha;
                    float[] p0 = q[i], p1 = q[(i + 1) % 4];
                    float[] a0 = lerp(p0, mid, w), a1 = lerp(p1, mid, w);
                    int la = lc >>> 24, lr = (lc >> 16) & 255, lg = (lc >> 8) & 255, lb = lc & 255;
                    for (float[] pp : new float[][]{p0, p1, a1, a0})
                        bb.vertex(m, pp[0] + n[0], pp[1] + n[1], pp[2] + n[2]).color(lr, lg, lb, la).endVertex();
                }
            }
        }
    }

    private static float[] lerp(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }
}
