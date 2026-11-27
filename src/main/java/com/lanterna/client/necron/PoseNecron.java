package com.lanterna.client.necron;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * As animações do Necron, feitas por código e usadas por todos que desenham o modelo: o chefe na cratera, a Forma do
 * Necron do Arauto e as cutscenes. Cada pose é uma lista de rotações por osso; elas se misturam pelo peso.
 *
 * 0.58.4 (mais vida):
 *   · transições suaves: andar, voo, curva e queda mudam aos poucos (nada "pula" de uma pose para outra);
 *   · parado: respiração no peito e nos ombros, a cabeça inclina de vez em quando, e a cada ~9 s ele gira a foice
 *     na mão e a apoia de novo;
 *   · andando: deslizar com balanço de quadril, os farrapos em onda atrasada (atrás do corpo);
 *   · curva: o tronco e a cabeça viram antes, a túnica e os farrapos ficam para trás e chicoteiam;
 *   · caindo: braços abrem para cima e a túnica sobe;
 *   · golpe em sequência: o 1º desce em diagonal por cima do ombro; o 2º é um corte horizontal de volta (revés);
 *     com antecipação, impacto que passa do ponto e volta, e os farrapos chicoteando no corte;
 *   · conjurar (os dons da Morte): a mão esquerda se ergue aberta, a cabeça recua, a foice firma ao lado;
 *   · surgir (a transformação): de curvado, ele se ergue abrindo os braços.
 *
 * (rotX positivo leva um membro que pende para baixo na direção da frente.)
 */
public final class PoseNecron {
    public record Estado(float t, float andar, float voo, float golpe, float ajoelhar, boolean cinema,
                         int lado, float virar, float cair, float conjurar, float surge) {
        public Estado(float t, float andar, float voo, float golpe, float ajoelhar, boolean cinema) {
            this(t, andar, voo, golpe, ajoelhar, cinema, 0, 0, 0, -1, 1);
        }
        public Estado(float t, float andar, float voo, float golpe, float ajoelhar) { this(t, andar, voo, golpe, ajoelhar, false); }
    }

    /** Duração do golpe da foice (ticks). */
    public static final float DUR_GOLPE = 16f;
    /** Só para o teste: força o ângulo da foice no voo. */
    public static float debugFoice = Float.NaN;

    // ===================== estado por entidade =====================

    private static final class Mem {
        float inicioGolpe = Float.NaN;
        int golpes;
        float andar, voo, virar, cair, t = Float.NaN;
        /** 0.59.1: há quanto tempo está parado (a alimentação conta a partir daqui). */
        float quietoHa;
    }

    private static final Map<Entity, Mem> MEM = new WeakHashMap<>();

    private static Mem mem(Entity e) { return MEM.computeIfAbsent(e, k -> new Mem()); }

    /** Quanto do golpe da foice já passou (0..1), ou 0 sem golpe. Começa quando a entidade balança o braço. */
    public static float golpe(Entity e, float pt) {
        Mem m = mem(e);
        float anim = e instanceof LivingEntity le ? le.getAttackAnim(pt) : 0;
        float agora = e.tickCount + pt;
        if (Float.isNaN(m.inicioGolpe)) {
            if (anim <= 0) return 0;
            m.inicioGolpe = agora;   // a antecipação inteira, desde o começo
            m.golpes++;
        }
        float k = (agora - m.inicioGolpe) / DUR_GOLPE;
        if (k >= 1 || k < 0) {
            if (anim <= 0 || k < 0) m.inicioGolpe = Float.NaN;
            return 0;
        }
        return k;
    }

    /** Qual dos dois golpes da sequência (0 = diagonal, 1 = revés). */
    public static int lado(Entity e) { return (mem(e).golpes + 1) % 2; }

    /** O quanto a entidade está se movendo (0..1), pelo deslocamento desde o último tick. */
    public static float andar(Entity e, float pt) {
        double dx = e.getX() - e.xo, dz = e.getZ() - e.zo;
        return Mth.clamp((float) Math.sqrt(dx * dx + dz * dz) * 5f, 0, 1);
    }

    /**
     * O estado completo de uma entidade, com as mudanças suavizadas (o mesmo para o chefe e para o Arauto).
     * vooAlvo: o peso do voo que quem desenha calculou (o jogador usa o voo do anel; o chefe, a velocidade).
     */
    public static Estado estado(Entity e, float pt, float vooAlvo, float conjurar, float surge) {
        Mem m = mem(e);
        float t = e.tickCount + pt;
        float dt = Float.isNaN(m.t) ? 1 : Mth.clamp(t - m.t, 0, 4);
        m.t = t;
        float k = 1 - (float) Math.exp(-dt * 0.22);
        float andarAlvo = andar(e, pt);
        float virarAlvo = 0;
        if (e instanceof LivingEntity le) virarAlvo = Mth.clamp(Mth.wrapDegrees(le.yBodyRot - le.yBodyRotO) / 12f, -1, 1);
        float cairAlvo = e.onGround() || vooAlvo > 0.3f ? 0 : Mth.clamp((float) -(e.getY() - e.yo) * 2.5f, 0, 1);
        m.andar = Mth.lerp(k, m.andar, andarAlvo);
        m.voo = Mth.lerp(k * 0.8f, m.voo, vooAlvo);
        m.virar = Mth.lerp(k * 0.7f, m.virar, virarAlvo);
        m.cair = Mth.lerp(k, m.cair, cairAlvo);
        float gp = golpe(e, pt);
        boolean parado = andarAlvo < 0.05f && vooAlvo < 0.05f && gp <= 0.001f && conjurar < 0 && surge >= 0.999f && cairAlvo < 0.05f;
        m.quietoHa = parado ? m.quietoHa + dt : 0;
        ultimo = e;
        return new Estado(t, m.andar, m.voo, gp, 0, false, lado(e), m.virar, m.cair, conjurar, surge);
    }

    // ===================== utilidades =====================

    private static float suave(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }

    /** Sai rápido e passa um pouco do ponto antes de assentar (o impacto do golpe). */
    private static float passaDoPonto(float x) {
        x = Mth.clamp(x, 0, 1);
        float c = 1.9f;
        return 1 + (c + 1) * (float) Math.pow(x - 1, 3) + c * (float) Math.pow(x - 1, 2);
    }

    private static void por(Map<String, float[]> m, String o, float x, float y, float z) { m.put(o, new float[]{x, y, z}); }

    private static void somar(Map<String, float[]> m, String o, float x, float y, float z, float k) {
        float[] v = m.computeIfAbsent(o, q -> new float[3]);
        v[0] += x * k; v[1] += y * k; v[2] += z * k;
    }

    private static void ir(Map<String, float[]> m, String o, float x, float y, float z, float k) {
        float[] v = m.computeIfAbsent(o, q -> new float[3]);
        v[0] = Mth.lerp(k, v[0], x); v[1] = Mth.lerp(k, v[1], y); v[2] = Mth.lerp(k, v[2], z);
    }

    // ===================== poses (0.59.3: quadros-chave em AnimNecron) =====================

    // ===================== 0.59.1: alimentar-se de uma caveira =====================

    /** Parado, a cada CICLO ticks ele pega uma caveira e se alimenta dela durante DUR_ALIM ticks. */
    public static final int CICLO = 320, DUR_ALIM = 92;
    /** As cutscenes (Atores sem estado forçado) desligam a animação. */
    public static boolean alimentar = true;
    /** Último peso da animação de cada entidade (0 = não está parado o bastante para se alimentar). */
    private static final Map<Entity, Float> QUIETO = new WeakHashMap<>();
    private static Entity ultimo;

    /** Em que ponto da alimentação esta entidade está (0..1) ou -1: conta desde que ela parou de se mexer. */
    public static float alimento(Entity e) {
        Mem m = MEM.get(e);
        return m == null ? -1 : alimento(m.quietoHa);
    }

    /** Em que ponto da alimentação a entidade está (0..1) ou -1. */
    public static float alimento(float t) {
        float f = t % CICLO;
        return f >= CICLO - DUR_ALIM ? (f - (CICLO - DUR_ALIM)) / DUR_ALIM : -1;
    }

    /** O peso da alimentação desta entidade no último quadro desenhado. */
    public static float quieto(Entity e) { return QUIETO.getOrDefault(e, 0f); }

    /** Tamanho da caveira na mão: surge, murcha enquanto é drenada e se desfaz. */
    static float escalaCaveira(float g) {
        if (g < 0.1f || g > 0.86f) return 0;
        if (g < 0.18f) return suave((g - 0.1f) / 0.08f);
        if (g < 0.35f) return 1;
        if (g < 0.8f) return 1 - 0.3f * (g - 0.35f) / 0.45f;
        return 0.7f * (1 - (g - 0.8f) / 0.06f);
    }

    public static void aplicar(Function<String, Optional<? extends CoreGeoBone>> ossos, Estado e) {
        float t = e.t(), seg = t / 20f;
        Map<String, float[]> m = new HashMap<>(), pos = new HashMap<>();
        // ---- parado: respira, a foice como bengala ----
        AnimNecron.amostrar("parado", seg, m, pos, 1);
        // ---- voando ----
        float voo = Mth.clamp(e.voo(), 0, 1);
        AnimNecron.amostrar("voar", seg, m, pos, voo);
        if (voo > 0.001f && e.cinema()) {
            // na cutscene do espaço o modelo é girado de outro jeito: a foice vai para trás, ao longo do corpo
            float braco = m.getOrDefault("braco_d", new float[3])[0] + m.getOrDefault("antebraco_d", new float[3])[0] + m.getOrDefault("mao_d", new float[3])[0];
            ir(m, "foice", 2.95f - braco, 0, 0.05f, voo);
        }
        // ---- andando (passos de verdade, com coxa, canela e pé) ----
        float an = Mth.clamp(e.andar(), 0, 1) * (1 - voo);
        AnimNecron.amostrar("andar", seg, m, pos, an);
        // ---- curva: o tronco vira antes, o pano fica para trás ----
        float v = Mth.clamp(e.virar(), -1, 1);
        if (Math.abs(v) > 0.01f) {
            somar(m, "cabeca", 0, 0.35f * v, -0.06f * v, 1);
            somar(m, "corpo", 0, 0.12f * v, -0.08f * v, 1);
            somar(m, "saia", 0, -0.25f * v, 0.06f * v, 1);
            for (int i = 1; i <= 12; i++) somar(m, "farrapo_" + i, 0, 0, -0.4f * v, 1);
            somar(m, "capuz", 0, -0.1f * v, 0, 1);
        }
        // ---- caindo ----
        float cai = Mth.clamp(e.cair(), 0, 1) * (1 - voo);
        AnimNecron.amostrar("cair", seg, m, pos, cai);
        // ---- conjurar (os dons da Morte) ----
        float cj = e.conjurar();
        if (cj >= 0) AnimNecron.amostrar("conjurar", cj * AnimNecron.duracao("conjurar"), m, pos, 1);
        // ---- o golpe da foice (sequência de dois) ----
        float g = e.golpe();
        if (g > 0.001f) {
            String qual = e.lado() == 0 ? "golpe1" : "golpe2";
            AnimNecron.amostrar(qual, g * AnimNecron.duracao(qual), m, pos, suave(g / 0.06f) * (1 - suave((g - 0.92f) / 0.08f)));
        }
        // ---- surgir (a transformação) ----
        float s = Mth.clamp(e.surge(), 0, 1);
        if (s < 0.999f) AnimNecron.amostrar("surgir", s * AnimNecron.duracao("surgir"), m, pos, 1 - suave((s - 0.92f) / 0.08f));
        // ---- de joelhos ----
        float k = Mth.clamp(e.ajoelhar(), 0, 1);
        AnimNecron.amostrar("ajoelhar", seg, m, pos, k);
        // ---- parado há um tempo: se alimenta de uma caveira ----
        float quieto = (1 - voo) * (1 - an) * (g > 0.001f ? 0 : 1) * (cj >= 0 ? 0 : 1) * (s < 0.999f ? 0 : 1) * (1 - k)
                * (e.cinema() ? 0 : 1) * (alimentar ? 1 : 0);
        float al = ultimo != null ? alimento(mem(ultimo).quietoHa) : -1;
        if (al >= 0 && quieto > 0.01f) AnimNecron.amostrar("alimentar", al * AnimNecron.duracao("alimentar"), m, pos, quieto);
        float cav = al >= 0 ? escalaCaveira(al) * quieto : 0;
        ossos.apply("caveira").ifPresent(b -> {
            b.setHidden(cav < 0.02f);
            b.setScaleX(Math.max(cav, 0.001f)); b.setScaleY(Math.max(cav, 0.001f)); b.setScaleZ(Math.max(cav, 0.001f));
        });
        if (ultimo != null) { QUIETO.put(ultimo, quieto); ultimo = null; }
        for (var en : m.entrySet()) {
            float[] r = en.getValue();
            ossos.apply(en.getKey()).ifPresent(b -> {
                b.setRotX(b.getInitialSnapshot().getRotX() + r[0]);
                b.setRotY(b.getInitialSnapshot().getRotY() + r[1]);
                b.setRotZ(b.getInitialSnapshot().getRotZ() + r[2]);
            });
        }
        for (var en : pos.entrySet()) {
            float[] p = en.getValue();
            ossos.apply(en.getKey()).ifPresent(b -> {
                b.setPosX(b.getInitialSnapshot().getOffsetX() + p[0]);
                b.setPosY(b.getInitialSnapshot().getOffsetY() + p[1]);
                b.setPosZ(b.getInitialSnapshot().getOffsetZ() + p[2]);
            });
        }
    }

    private PoseNecron() {}
}
