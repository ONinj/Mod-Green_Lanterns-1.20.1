package com.lanterna.client.escolha;

import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * O cenário de cada Tropa, ligado ao ritual que a chamou:
 *  verde    (dormir a noite toda: a vontade que não desiste)   o anel risca o céu como um cometa, dá voltas em você
 *                                                              e um círculo de luz se desenha no chão;
 *  vermelho (matar muitos em pouco tempo: a fúria)              o chão racha, um poço de plasma ferve, colunas de
 *                                                              fogo pulsam com o coração; o anel sobe do poço;
 *  amarelo  (encarar o Warden no escuro: o medo)                o mundo apaga, olhos amarelos abrem em volta e raios
 *                                                              rasgam o escuro; o anel desce num raio;
 *  azul     (curar um aldeão zumbi: a esperança)                uma aurora sobe no céu e ondas calmas no chão; o
 *                                                              anel desce devagar como uma estrela;
 *  violeta  (filhotes com o bicho domado: o amor)               cristais crescem em volta, pétalas caem em espiral e
 *                                                              um botão de cristal se abre com o anel dentro;
 *  índigo   (salvar o Golem quase morto: a compaixão)           um feixe desce do céu, ondas se espalham como água e
 *                                                              lágrimas sobem; o anel desce no feixe;
 *  laranja  (o tesouro sobre o ouro: a avareza)                 um redemoinho de moedas gira cada vez mais rápido e
 *                                                              se funde no anel;
 *  negro    (morrer sob o Wither: a morte)                      tentáculos de sombra saem da terra, almas giram e o
 *                                                              anel se forma das almas;
 *  branco   (a fusão dos sete anéis)                            sete anéis coloridos orbitam e se fundem num anel
 *                                                              branco; flores de luz se abrem no chão.
 * Todas terminam igual: o anel para na sua frente, encaixa no dedo da mão erguida, clarão, onda no chão e a luz
 * sobe pelo corpo (o uniforme).
 */
final class Cenas {
    /** Até aqui o anel é da cena; depois ele vem para a frente do peito e para o dedo (comum). */
    static final float CHEGA = 0.72f;

    static float suave(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    static float faixa(float t, float a, float b) {
        return Mth.clamp((t - a) / (b - a), 0, 1);
    }

    /** Hash simples e estável. */
    static float h(int i, int k) {
        int x = i * 374761393 + k * 668265263;
        x = (x ^ (x >>> 13)) * 1274126177;
        return ((x ^ (x >>> 16)) & 0xFFFF) / 65535f;
    }

    /** Onde o anel está antes de CHEGA (null = ainda não aparece). frente/direita: vetores do corpo. */
    static Vector3f anel(String estilo, float t, Vector3f pairar, int semente, float tempo) {
        switch (estilo) {
            case "green": {
                float ang = h(semente, 1) * Mth.TWO_PI;
                Vector3f s = new Vector3f(Mth.cos(ang) * 34, 30, Mth.sin(ang) * 34);
                if (t < 0.5f) {
                    float q = (float) Math.pow(t / 0.5f, 1.5);
                    Vector3f fim = new Vector3f(Mth.cos(ang) * 2.2f, 1.7f, Mth.sin(ang) * 2.2f);
                    Vector3f ctrl = new Vector3f(s).lerp(fim, 0.5f).add(0, 8, 0);
                    return bezier(s, ctrl, fim, q);
                }
                float q = (t - 0.5f) / (CHEGA - 0.5f);
                float alvo = (float) Math.atan2(pairar.z, pairar.x);
                float a0 = ang, a1 = alvo + Mth.TWO_PI * 2;
                float a = Mth.lerp(suave(q), a0, a1);
                float r = Mth.lerp(q, 2.2f, pairar.length() > 0 ? (float) Math.hypot(pairar.x, pairar.z) : 1.3f);
                return new Vector3f(Mth.cos(a) * r, 1.7f + Mth.sin(q * Mth.PI * 2) * 0.4f, Mth.sin(a) * r);
            }
            case "red": {
                if (t < 0.42f) return null;
                float q = suave(faixa(t, 0.42f, CHEGA));
                return new Vector3f(pairar.x, Mth.lerp(q, -0.6f, pairar.y), pairar.z);
            }
            case "yellow": {
                if (t < 0.45f) return null;
                float q = faixa(t, 0.45f, CHEGA);
                q = q < 0.6f ? q * 0.3f / 0.6f : 0.3f + suave((q - 0.6f) / 0.4f) * 0.7f; // desce devagar, depois o raio o traz
                return new Vector3f(pairar.x, Mth.lerp(q, 16f, pairar.y), pairar.z);
            }
            case "blue": {
                if (t < 0.2f) return null;
                float q = suave(faixa(t, 0.2f, CHEGA));
                float bal = Mth.sin(tempo * 0.06f) * 0.6f * (1 - q);
                return new Vector3f(pairar.x + bal, Mth.lerp(q, 22f, pairar.y), pairar.z + bal * 0.5f);
            }
            case "violet": {
                if (t < 0.5f) return null;
                return new Vector3f(pairar.x, pairar.y + Mth.sin(tempo * 0.1f) * 0.05f, pairar.z);
            }
            case "indigo": {
                if (t < 0.3f) return null;
                float q = suave(faixa(t, 0.3f, CHEGA));
                return new Vector3f(Mth.lerp(q, 0, pairar.x), Mth.lerp(q, 26f, pairar.y), Mth.lerp(q, 0, pairar.z));
            }
            case "orange": {
                if (t < 0.62f) return null;
                return new Vector3f(pairar);
            }
            case "black": {
                if (t < 0.5f) return null;
                return new Vector3f(pairar.x, pairar.y + Mth.sin(tempo * 0.07f) * 0.08f, pairar.z);
            }
            case "white": {
                if (t < 0.62f) return null;
                return new Vector3f(0, 1.6f + (t - 0.62f) * 0.8f, 0).lerp(pairar, suave(faixa(t, 0.64f, CHEGA)));
            }
            default: {
                if (t < 0.2f) return null;
                float q = suave(faixa(t, 0.2f, CHEGA));
                float a = q * Mth.TWO_PI * 2;
                float r = (1 - q) * 3;
                return new Vector3f(pairar.x + Mth.cos(a) * r, Mth.lerp(q, 14, pairar.y), pairar.z + Mth.sin(a) * r);
            }
        }
    }

    static Vector3f bezier(Vector3f a, Vector3f b, Vector3f c, float t) {
        Vector3f ab = new Vector3f(a).lerp(b, t), bc = new Vector3f(b).lerp(c, t);
        return ab.lerp(bc, t);
    }

    /** Cor do anel na cena (o negro é escuro de verdade). */
    static int corAnel(String estilo, int cor) {
        return switch (estilo) {
            case "black" -> 0x2A2036;
            case "white" -> 0xFFFFFF;
            default -> cor;
        };
    }

    // =====================================================================

    static void ambiente(String estilo, float t, int cor, int semente, float tempo, float ticks, Vector3f pairar, Vector3f anelPos) {
        switch (estilo) {
            case "green" -> verde(t, cor, semente, tempo, anelPos, pairar);
            case "red" -> vermelho(t, cor, semente, tempo, ticks);
            case "yellow" -> amarelo(t, cor, semente, tempo, ticks, anelPos);
            case "blue" -> azul(t, cor, tempo, anelPos);
            case "violet" -> violeta(t, cor, tempo, pairar);
            case "indigo" -> indigo(t, cor, tempo, anelPos);
            case "orange" -> laranja(t, cor, tempo, pairar);
            case "black" -> negro(t, cor, tempo, pairar);
            case "white" -> branco(t, tempo, pairar);
            default -> {
                float a = faixa(t, 0, 0.2f) * (1 - faixa(t, 0.85f, 1));
                Geo.anelChao(1.6f, 2.4f, 0.03f, cor, 0, 0.6f * a, true);
            }
        }
    }

    /** Rastro do anel: posições anteriores da própria trajetória. */
    static void rastro(String estilo, float t, Vector3f pairar, int semente, float tempo, int cor, float dt, int n, float largura) {
        List<Vector3f> pts = new ArrayList<>();
        for (int k = n; k >= 0; k--) {
            float tk = t - k * dt;
            if (tk < 0) continue;
            Vector3f p = anel(estilo, Math.min(tk, CHEGA), pairar, semente, tempo - k);
            if (p != null) pts.add(p);
        }
        if (pts.size() < 2) return;
        float[] w = new float[pts.size()], a = new float[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            float f = i / (float) (pts.size() - 1);
            w[i] = largura * f;
            a[i] = f * f;
        }
        Geo.fita(pts, w, cor, a);
        float[] w2 = new float[pts.size()];
        for (int i = 0; i < w.length; i++) w2[i] = w[i] * 3.5f;
        float[] a2 = new float[pts.size()];
        for (int i = 0; i < a.length; i++) a2[i] = a[i] * 0.3f;
        Geo.fita(pts, w2, cor, a2);
    }

    // ---------------- verde ----------------
    private static void verde(float t, int cor, int semente, float tempo, Vector3f anelPos, Vector3f pairar) {
        if (anelPos != null && t < CHEGA) rastro("green", t, pairar, semente, tempo, cor, 0.006f, 26, t < 0.5f ? 0.9f : 0.35f);
        // círculo de luz se desenhando no chão: dois aros e 7 raios, girando devagar
        float a = faixa(t, 0.45f, 0.62f) * (1 - faixa(t, 0.95f, 1));
        if (a > 0) {
            Geo.ps.pushPose();
            Geo.ps.mulPose(Axis.YP.rotationDegrees(tempo * 0.6f));
            float desenho = faixa(t, 0.45f, 0.65f);
            Geo.anelChao(2.75f, 2.95f, 0.03f, cor, 0.8f * a, 0.8f * a, true);
            Geo.anelChao(1.75f, 1.85f, 0.03f, cor, 0.6f * a, 0.6f * a, true);
            for (int i = 0; i < 7; i++) {
                float ang = i * Mth.TWO_PI / 7;
                float l = Mth.lerp(desenho, 1.8f, 2.85f);
                Geo.linhaChao(Mth.cos(ang) * 1.8f, Mth.sin(ang) * 1.8f, Mth.cos(ang) * l, Mth.sin(ang) * l, 0.06f, 0.035f, cor, 0.9f * a);
                float a2 = ang + Mth.PI / 7;
                Geo.linhaChao(Mth.cos(ang) * 2.85f, Mth.sin(ang) * 2.85f, Mth.cos(a2) * 1.8f, Mth.sin(a2) * 1.8f, 0.04f, 0.035f, cor, 0.5f * a);
            }
            Geo.anelChao(0, 2.95f, 0.025f, cor, 0.12f * a, 0.05f * a, true);
            Geo.ps.popPose();
        }
        // coluna de luz quando o anel está chegando
        float col = faixa(t, 0.68f, 0.78f) * (1 - faixa(t, 0.86f, 0.98f));
        if (col > 0) {
            Geo.tubo(1.2f + 0.3f * col, 0, 32, cor, 0.4f * col, 0, 0.04f, true);
            Geo.tubo(0.5f, 0, 32, 0xFFFFFF, 0.25f * col, 0, 0, true);
        }
    }

    // ---------------- vermelho ----------------
    private static float batida(float ticks) {
        // mesmo ritmo do som do servidor: período cai de 26 para 10 ticks
        float acum = 0, k = 0;
        while (acum < ticks && k < 200) {
            float per = Math.max(10, 26 - 16 * (acum / 200f));
            if (acum + per > ticks) return (ticks - acum) / per;
            acum += per;
            k++;
        }
        return 0;
    }

    private static void vermelho(float t, int cor, int semente, float tempo, float ticks) {
        float fim = 1 - faixa(t, 0.9f, 1);
        float b = batida(ticks);
        float pulso = (float) Math.pow(Math.max(0, 1 - b * 3.5f), 2);
        // rachaduras crescendo para fora
        float alc = 1.5f + 7f * suave(faixa(t, 0.02f, 0.5f));
        for (int i = 0; i < 11; i++) {
            float ang = h(semente, i) * Mth.TWO_PI;
            float x = 0, z = 0;
            int n = 9;
            for (int k = 0; k < n; k++) {
                float d0 = alc * k / n, d1 = alc * (k + 1) / n;
                float a1 = ang + (h(i, k) - 0.5f) * 0.7f;
                float nx = Mth.cos(a1) * d1, nz = Mth.sin(a1) * d1;
                Geo.linhaChao(x, z, nx, nz, 0.09f * (1 - k / (float) n), 0.03f, cor, (0.55f + 0.45f * pulso) * fim * (1 - k / (float) n * 0.5f));
                x = nx;
                z = nz;
            }
        }
        // poço de plasma
        float poco = Math.min(2.4f, t * 9) * fim;
        Geo.anelChao(0, poco, 0.02f, 0x3A0000, 0.9f * fim, 0.7f * fim, false);
        Geo.anelChao(0, poco, 0.03f, cor, 0.5f + 0.4f * pulso, 0.15f, true);
        Geo.onda(poco + 0.05f, 0.35f, 0.035f, 0xFF7A40, 0.8f * fim);
        // colunas de fogo pulsando com o coração
        float sobe = suave(faixa(t, 0.1f, 0.35f)) * fim;
        for (int i = 0; i < 6; i++) {
            float ang = i * Mth.TWO_PI / 6 + tempo * 0.01f;
            Geo.ps.pushPose();
            Geo.ps.translate(Mth.cos(ang) * 3.4f, 0, Mth.sin(ang) * 3.4f);
            float alt = (2.6f + 1.6f * pulso) * sobe;
            Geo.tubo(0.42f, 0, alt, cor, 0.75f * sobe, 0, 0.3f, true);
            Geo.tubo(0.18f, 0, alt * 0.8f, 0xFFD0A0, 0.6f * sobe, 0, 0.2f, true);
            Geo.ps.popPose();
            Geo.bola(Mth.cos(ang) * 3.4f, 0.4f, Mth.sin(ang) * 3.4f, 1.1f, cor, 0.5f * sobe);
        }
        // onda de cada batida
        Geo.onda(b * 9, 0.6f, 0.04f, cor, 0.7f * (1 - b) * sobe);
    }

    // ---------------- amarelo ----------------
    private static void amarelo(float t, int cor, int semente, float tempo, float ticks, Vector3f anelPos) {
        float fim = 1 - faixa(t, 0.9f, 1);
        // olhos abrindo no escuro, piscando
        for (int i = 0; i < 12; i++) {
            float abre = faixa(t, 0.05f + h(semente, i) * 0.35f, 0.25f + h(semente, i) * 0.35f);
            float pisca = Mth.sin(tempo * 0.07f + i * 3.1f) > 0.96f ? 0.1f : 1f;
            float ang = h(semente + 7, i) * Mth.TWO_PI;
            float r = 6.5f + h(i, 3) * 3f;
            float x = Mth.cos(ang) * r, z = Mth.sin(ang) * r, y = 1 + h(i, 5) * 4.5f;
            float larg = 0.28f + h(i, 9) * 0.25f;
            float sep = larg * 2.6f;
            float tx = -Mth.sin(ang) * sep / 2, tz = Mth.cos(ang) * sep / 2;
            Geo.olho(x + tx, y, z + tz, larg, abre * pisca, cor, 0.95f * fim);
            Geo.olho(x - tx, y, z - tz, larg, abre * pisca, cor, 0.95f * fim);
        }
        // raios caindo em volta, cada vez mais perto
        int periodo = 11;
        int n = (int) (ticks / periodo);
        for (int k = Math.max(0, n - 1); k <= n; k++) {
            float idade = ticks - k * periodo;
            if (idade < 0 || idade > 6) continue;
            float a = (1 - idade / 6f) * fim;
            float ang = h(semente, k + 50) * Mth.TWO_PI;
            float d = Mth.lerp(t, 7f, 2.2f);
            Vector3f chao = new Vector3f(Mth.cos(ang) * d, 0, Mth.sin(ang) * d);
            Vector3f ceu = new Vector3f(chao).add((h(k, 1) - 0.5f) * 6, 20, (h(k, 2) - 0.5f) * 6);
            Geo.raio(ceu, chao, semente * 31L + k, 1.6f, cor, a, 0.09f);
            Geo.bola(chao.x, 0.2f, chao.z, 2.2f, cor, 0.6f * a);
            Geo.onda(idade * 0.5f, 0.3f, 0.04f, cor, a);
        }
        // o raio que traz o anel
        if (anelPos != null) {
            float traz = faixa(t, 0.6f, 0.64f) * (1 - faixa(t, 0.68f, 0.74f));
            if (traz > 0) Geo.raio(new Vector3f(anelPos).add(0, 24, 0), anelPos, semente * 7L + (long) (tempo / 2), 1.0f, cor, traz, 0.14f);
            // estalos em volta do anel
            for (int i = 0; i < 3; i++) {
                float ang = tempo * 0.4f + i * 2.1f;
                Vector3f p = new Vector3f(anelPos).add(Mth.cos(ang) * 0.9f, Mth.sin(ang * 1.3f) * 0.5f, Mth.sin(ang) * 0.9f);
                Geo.raio(anelPos, p, (long) (tempo * 3) + i, 0.35f, cor, 0.7f * fim, 0.03f);
            }
        }
    }

    // ---------------- azul ----------------
    private static void azul(float t, int cor, float tempo, Vector3f anelPos) {
        float a = suave(faixa(t, 0, 0.3f)) * (1 - faixa(t, 0.9f, 1));
        // aurora: três cortinas em arco no céu
        for (int c = 0; c < 3; c++) {
            float raio = 11 + c * 2.5f, base = 9 + c * 1.5f, alt = 6 + c * 1.5f;
            int col = c == 1 ? 0x60FFD0 : cor;
            Geo.ps.pushPose();
            Geo.ps.mulPose(Axis.YP.rotationDegrees(c * 50 + tempo * 0.15f));
            var vc = Geo.corpo(); // mistura normal: aparece até no céu de dia
            var m = Geo.ps.last().pose();
            int seg = 40;
            for (int i = 0; i < seg; i++) {
                float u0 = -1.4f + 2.8f * i / seg, u1 = -1.4f + 2.8f * (i + 1) / seg;
                float w0 = Mth.sin(u0 * 4 + tempo * 0.05f + c) * 1.4f, w1 = Mth.sin(u1 * 4 + tempo * 0.05f + c) * 1.4f;
                float x0 = Mth.cos(u0) * (raio + w0), z0 = Mth.sin(u0) * (raio + w0), x1 = Mth.cos(u1) * (raio + w1), z1 = Mth.sin(u1) * (raio + w1);
                float borda = Mth.sin((i + 0.5f) / seg * Mth.PI);
                float s0 = 0.6f + 0.4f * Mth.sin(u0 * 9 + tempo * 0.1f), s1 = 0.6f + 0.4f * Mth.sin(u1 * 9 + tempo * 0.1f);
                Geo.v(vc, m, x0, base, z0, col, 0.55f * a * borda * s0, u0, 0);
                Geo.v(vc, m, x1, base, z1, col, 0.55f * a * borda * s1, u1, 0);
                Geo.v(vc, m, x1, base + alt, z1, 0xFFFFFF, 0, u1, 1);
                Geo.v(vc, m, x0, base + alt, z0, 0xFFFFFF, 0, u0, 1);
                Geo.v(vc, m, x0, base - 1.2f, z0, col, 0, u0, 0);
                Geo.v(vc, m, x1, base - 1.2f, z1, col, 0, u1, 0);
                Geo.v(vc, m, x1, base, z1, col, 0.55f * a * borda * s1, u1, 0);
                Geo.v(vc, m, x0, base, z0, col, 0.55f * a * borda * s0, u0, 0);
            }
            Geo.ps.popPose();
        }
        // ondas calmas no chão
        for (int k = 0; k < 3; k++) {
            float f = ((tempo * 0.012f) + k / 3f) % 1f;
            Geo.onda(1 + f * 6, 0.5f, 0.03f, cor, 0.45f * (1 - f) * a);
        }
        // a estrela que desce
        if (anelPos != null) {
            Geo.estrela(anelPos.x, anelPos.y, anelPos.z, 1.6f, cor, 0.9f * a, tempo * 0.01f);
            Geo.estrela(anelPos.x, anelPos.y, anelPos.z, 0.9f, 0xFFFFFF, 0.6f * a, tempo * 0.01f + 0.4f);
        }
    }

    // ---------------- violeta ----------------
    private static void violeta(float t, int cor, float tempo, Vector3f pairar) {
        float fim = 1 - faixa(t, 0.9f, 1);
        // cristais crescendo em volta, inclinados para fora
        for (int i = 0; i < 14; i++) {
            float ang = i * Mth.TWO_PI / 14 + h(i, 1) * 0.3f;
            float r = 2.6f + h(i, 2) * 1.2f;
            float cresce = suave(faixa(t, 0.05f + h(i, 3) * 0.3f, 0.3f + h(i, 3) * 0.3f));
            if (cresce <= 0) continue;
            Geo.ps.pushPose();
            Geo.ps.translate(Mth.cos(ang) * r, -0.1f, Mth.sin(ang) * r);
            Geo.ps.mulPose(Axis.YP.rotation(-ang));
            Geo.ps.mulPose(Axis.ZP.rotationDegrees(-(14 + h(i, 4) * 18)));
            Geo.cristal((0.8f + h(i, 5) * 1.6f) * cresce, 0.22f + h(i, 6) * 0.15f, Geo.mistura(cor, 0xFFB0F0, h(i, 7) * 0.5f), 0.7f * fim, 6, false);
            Geo.ps.popPose();
        }
        // pétalas caindo em espiral
        for (int i = 0; i < 40; i++) {
            float fase = (tempo * 0.004f + h(i, 1)) % 1f;
            float y = 7 - fase * 7;
            float ang = h(i, 2) * Mth.TWO_PI + fase * 6;
            float r = 1.2f + h(i, 3) * 3.5f;
            float a = Math.min(1, fase * 5) * Math.min(1, (1 - fase) * 5) * faixa(t, 0, 0.15f) * fim;
            Geo.ps.pushPose();
            Geo.ps.translate(Mth.cos(ang) * r, y, Mth.sin(ang) * r);
            Geo.ps.mulPose(Axis.YP.rotation(fase * 9 + i));
            Geo.ps.mulPose(Axis.XP.rotation(fase * 7 + i * 0.3f));
            Geo.petala(0.22f, 0.09f, i % 3 == 0 ? 0xFFC0E8 : cor, 0.85f * a);
            Geo.ps.popPose();
        }
        // botão de cristal que se abre com o anel dentro
        float aparece = suave(faixa(t, 0.25f, 0.45f)), abre = suave(faixa(t, 0.52f, 0.7f));
        float some = 1 - faixa(t, 0.78f, 0.9f);
        if (aparece > 0 && some > 0) {
            Geo.ps.pushPose();
            Geo.ps.translate(pairar.x, pairar.y - 0.55f, pairar.z);
            Geo.ps.scale(aparece, aparece, aparece);
            for (int i = 0; i < 6; i++) {
                Geo.ps.pushPose();
                Geo.ps.mulPose(Axis.YP.rotationDegrees(i * 60 + tempo * 0.3f));
                Geo.ps.translate(0, 0, 0.12f);
                Geo.ps.mulPose(Axis.XP.rotationDegrees(-(8 + 62 * abre)));
                Geo.petala(1.1f, 0.38f, Geo.mistura(cor, 0xFFFFFF, 0.15f), 0.75f * some);
                Geo.ps.popPose();
            }
            Geo.ps.popPose();
            Geo.bola(pairar.x, pairar.y, pairar.z, 1.4f, cor, 0.5f * aparece * some);
        }
    }

    // ---------------- índigo ----------------
    private static void indigo(float t, int cor, float tempo, Vector3f anelPos) {
        float fim = 1 - faixa(t, 0.88f, 1);
        float desce = suave(faixa(t, 0, 0.28f));
        float base = Mth.lerp(desce, 40, 0);
        if (desce > 0) {
            Geo.tubo(1.15f, base, 48, cor, 0.38f * fim, 0.05f * fim, 0.03f, true);
            Geo.tubo(0.4f, base, 48, 0xC8B8FF, 0.22f * fim, 0.04f * fim, 0, true);
            Geo.bola(0, base + 0.2f, 0, 2.2f, cor, 0.6f * desce * fim);
        }
        // ondas como na água, a partir de quando o feixe toca o chão
        if (t > 0.26f) {
            for (int k = 0; k < 4; k++) {
                float f = ((tempo * 0.018f) + k / 4f) % 1f;
                Geo.onda(0.8f + f * 7, 0.25f, 0.03f, cor, 0.6f * (1 - f) * fim);
                Geo.onda(0.8f + f * 7 - 0.4f, 0.1f, 0.03f, 0xFFFFFF, 0.25f * (1 - f) * fim);
            }
        }
        // lágrimas subindo
        for (int i = 0; i < 26; i++) {
            float fase = (tempo * 0.006f + h(i, 1)) % 1f;
            float ang = h(i, 2) * Mth.TWO_PI, r = 0.8f + h(i, 3) * 2.4f;
            float a = Math.min(1, fase * 4) * Math.min(1, (1 - fase) * 4) * faixa(t, 0.25f, 0.4f) * fim;
            if (a <= 0) continue;
            Geo.ps.pushPose();
            Geo.ps.translate(Mth.cos(ang) * r, fase * 5, Mth.sin(ang) * r);
            Geo.ps.mulPose(Axis.XP.rotationDegrees(180));
            Geo.cristal(0.22f, 0.06f, Geo.mistura(cor, 0xFFFFFF, 0.4f), 0.8f * a, 6, true);
            Geo.ps.popPose();
        }
    }

    // ---------------- laranja ----------------
    private static void laranja(float t, int cor, float tempo, Vector3f pairar) {
        float fim = 1 - faixa(t, 0.9f, 1);
        float junta = faixa(t, 0.56f, 0.64f);
        int ouro = 0xFFC830;
        for (int i = 0; i < 96; i++) {
            float aparece = faixa(t, h(i, 1) * 0.25f, h(i, 1) * 0.25f + 0.08f);
            if (aparece <= 0 || junta >= 1) continue;
            float velo = 0.04f + 0.3f * t;
            float ang = i * 2.39996f + tempo * velo * (0.8f + h(i, 2) * 0.4f);
            float r = Mth.lerp(t, 4.6f, 1.0f) * (0.7f + 0.3f * h(i, 3));
            float y = 0.2f + ((i % 12) * 0.28f + tempo * 0.02f * (0.5f + h(i, 4))) % 3.4f;
            Vector3f p = new Vector3f(Mth.cos(ang) * r, y, Mth.sin(ang) * r);
            if (junta > 0) p.lerp(pairar, suave(junta));
            Geo.ps.pushPose();
            Geo.ps.translate(p.x, p.y, p.z);
            Geo.ps.mulPose(Axis.YP.rotation(-ang));
            Geo.ps.mulPose(Axis.XP.rotation(tempo * 0.2f + i));
            float s = aparece * (1 - junta * 0.7f);
            Geo.ps.scale(s, s, s);
            Geo.moeda(0.13f, 0.035f, i % 4 == 0 ? cor : ouro, 1f);
            Geo.ps.popPose();
        }
        // línguas de fogo no chão
        float fogo = faixa(t, 0.1f, 0.3f) * fim;
        for (int i = 0; i < 8; i++) {
            float ang = i * Mth.TWO_PI / 8 + tempo * 0.02f;
            Geo.ps.pushPose();
            Geo.ps.translate(Mth.cos(ang) * 1.3f, 0, Mth.sin(ang) * 1.3f);
            Geo.tubo(0.22f, 0, 0.9f + 0.4f * Mth.sin(tempo * 0.3f + i), cor, 0.6f * fogo, 0, 0.35f, true);
            Geo.ps.popPose();
        }
        Geo.anelChao(0, 4.5f, 0.03f, ouro, 0.25f * fogo, 0, true);
        // o clarão de quando as moedas viram anel
        float clarao = faixa(t, 0.6f, 0.63f) * (1 - faixa(t, 0.63f, 0.7f));
        if (clarao > 0) Geo.bola(pairar.x, pairar.y, pairar.z, 3f, ouro, clarao);
    }

    // ---------------- negro ----------------
    private static void negro(float t, int cor, float tempo, Vector3f pairar) {
        float fim = 1 - faixa(t, 0.9f, 1);
        // tentáculos de sombra saindo da terra e curvando para você
        for (int i = 0; i < 9; i++) {
            float ang = i * Mth.TWO_PI / 9 + h(i, 1) * 0.4f;
            float cresce = suave(faixa(t, 0.03f + h(i, 2) * 0.2f, 0.4f + h(i, 2) * 0.2f));
            if (cresce <= 0) continue;
            List<Vector3f> pts = new ArrayList<>();
            int n = 14;
            float r0 = 4.2f + h(i, 3);
            for (int k = 0; k <= n; k++) {
                float f = k / (float) n * cresce;
                float r = Mth.lerp(f, r0, 1.3f);
                float a = ang + f * 1.2f + Mth.sin(tempo * 0.05f + i + f * 4) * 0.15f;
                pts.add(new Vector3f(Mth.cos(a) * r, f * 3.4f + Mth.sin(f * 6 + tempo * 0.08f) * 0.25f, Mth.sin(a) * r));
            }
            float[] w = new float[n + 1], al = new float[n + 1], w2 = new float[n + 1], al2 = new float[n + 1];
            for (int k = 0; k <= n; k++) {
                float f = k / (float) n;
                w[k] = 0.42f * (1 - f) + 0.04f;
                al[k] = 0.92f * fim;
                w2[k] = w[k] * 1.6f;
                al2[k] = 0.55f * fim * (1 - f * 0.4f);
            }
            Geo.fita(pts, w2, 0x9A60D8, al2);          // borda roxa (luz)
            Geo.fita(pts, w, 0x07060A, al, false);      // a sombra
        }
        Geo.anelChao(0, 5.5f, 0.025f, 0x050408, 0.75f * faixa(t, 0, 0.2f) * fim, 0, false);
        // almas girando e subindo, e se juntando no anel
        float junta = faixa(t, 0.45f, 0.62f);
        for (int i = 0; i < 16; i++) {
            float fase = (tempo * 0.005f + h(i, 1)) % 1f;
            float ang = h(i, 2) * Mth.TWO_PI + tempo * 0.03f + fase * 3;
            float r = 1.4f + h(i, 3) * 2.8f;
            Vector3f p = new Vector3f(Mth.cos(ang) * r, 0.3f + fase * 4, Mth.sin(ang) * r);
            if (junta > 0) p.lerp(pairar, suave(junta));
            float a = Math.min(1, fase * 4) * Math.min(1, (1 - fase) * 4) * (1 - faixa(t, 0.6f, 0.66f)) * faixa(t, 0.05f, 0.2f);
            if (a <= 0) continue;
            Vector3f atras = new Vector3f(p).add(-Mth.sin(ang) * -0.5f, -0.35f, Mth.cos(ang) * -0.5f);
            Geo.fita(List.of(atras, p), new float[]{0, 0.22f}, 0x9FD8FF, new float[]{0, 0.6f * a});
            Geo.bola(p.x, p.y, p.z, 0.32f, 0xB0E8FF, 0.7f * a);
        }
    }

    // ---------------- branco ----------------
    static final String[] SETE_ID = {"green", "yellow", "red", "orange", "blue", "indigo", "violet"};
    static final int[] SETE = {0x4CFF8A, 0xFFE040, 0xFF3A20, 0xFF9A30, 0x5ABAFF, 0x8A70FF, 0xFF6AD0};

    private static void branco(float t, float tempo, Vector3f pairar) {
        float fim = 1 - faixa(t, 0.9f, 1);
        float junta = suave(faixa(t, 0.25f, 0.62f));
        if (t < 0.64f) {
            for (int i = 0; i < 7; i++) {
                float r = Mth.lerp(junta, 2.3f + i * 0.12f, 0.05f);
                float ang = i * Mth.TWO_PI / 7 + tempo * (0.03f + 0.15f * junta);
                Vector3f p = new Vector3f(Mth.cos(ang) * r, 1.3f + Mth.sin(ang * 2 + i) * 0.5f * (1 - junta), Mth.sin(ang) * r);
                float a = faixa(t, i * 0.02f, i * 0.02f + 0.1f);
                Geo.anelModelo("lanterna:" + SETE_ID[i], p.x, p.y + junta * 0.3f, p.z, 0.16f, SETE[i], a, tempo * 4 + i * 50, 15 + i * 10);
                // rastro curto de cada anel
                List<Vector3f> pts = new ArrayList<>();
                for (int k = 6; k >= 0; k--) {
                    float ak = ang - k * 0.08f;
                    pts.add(new Vector3f(Mth.cos(ak) * r, p.y + junta * 0.3f, Mth.sin(ak) * r));
                }
                float[] w = {0, 0.05f, 0.08f, 0.1f, 0.12f, 0.14f, 0.16f}, al = {0, 0.1f, 0.2f, 0.3f, 0.45f, 0.6f, 0.7f};
                for (int k = 0; k < al.length; k++) al[k] *= a;
                Geo.fita(pts, w, SETE[i], al);
            }
        }
        // a fusão: clarão e coluna branca
        float coluna = faixa(t, 0.6f, 0.66f) * (1 - faixa(t, 0.82f, 0.95f));
        if (coluna > 0) {
            Geo.tubo(0.6f + 1.6f * coluna, 0, 44, 0xFFFFFF, 0.45f * coluna, 0, 0.02f, true);
            Geo.bola(0, 1.6f, 0, 4f * coluna, 0xFFFFFF, coluna);
        }
        // flores de luz se abrindo no chão
        float flor = faixa(t, 0.35f, 0.85f);
        for (int i = 0; i < 34; i++) {
            float quando = h(i, 1);
            float abre = suave(faixa(flor, quando * 0.8f, quando * 0.8f + 0.2f));
            if (abre <= 0) continue;
            float ang = h(i, 2) * Mth.TWO_PI, r = 1.5f + h(i, 3) * 6;
            int c = Geo.mistura(SETE[i % 7], 0xFFFFFF, 0.55f);
            Geo.ps.pushPose();
            Geo.ps.translate(Mth.cos(ang) * r, 0.05f, Mth.sin(ang) * r);
            for (int k = 0; k < 5; k++) {
                Geo.ps.pushPose();
                Geo.ps.mulPose(Axis.YP.rotationDegrees(k * 72 + i * 13));
                Geo.ps.mulPose(Axis.XP.rotationDegrees(90 - 55 * abre));
                Geo.petala(0.28f * abre, 0.11f, c, 0.85f * fim);
                Geo.ps.popPose();
            }
            Geo.ps.popPose();
            Geo.bola(Mth.cos(ang) * r, 0.15f, Mth.sin(ang) * r, 0.45f * abre, c, 0.5f * fim);
        }
    }

    private Cenas() {}
}
