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

    // ===================== poses =====================

    /** A haste parada (em relação ao corpo): um pouco inclinada, o topo levemente à frente. */
    static final float HASTE_PARADO = -0.06f;

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

    private static void alimentar(Map<String, float[]> m, float g, float q, float t) {
        float pega = suave(g / 0.15f);
        float ergue = suave((g - 0.15f) / 0.2f);
        float solta = suave((g - 0.84f) / 0.16f);
        float w = q * pega * (1 - solta);
        if (w < 0.001f) return;
        float suga = ergue * (1 - suave((g - 0.78f) / 0.06f));
        // a mão esquerda: primeiro se abre para o lado (a caveira surge nela), depois ergue a caveira diante do rosto
        float bx = Mth.lerp(ergue, 0.55f, 2.05f), by = Mth.lerp(ergue, 0.1f, CAV_Y), bz = Mth.lerp(ergue, -0.45f, 0.05f);
        float treme = 0.03f * suga * Mth.sin(t * 2.1f);
        ir(m, "braco_e", bx + treme, by, bz, w);
        // a caveira vira o rosto para ele
        ir(m, "caveira", CAV_X * ergue, Mth.PI * ergue, 0, w);   // vira o rosto da caveira para ele
        somar(m, "cabeca", 0.1f * ergue, -0.12f * ergue, 0, w);
        ir(m, "mandibula", 0.2f + 0.45f * suga + 0.08f * suga * Mth.sin(t * 1.7f), 0, 0, w);
        somar(m, "corpo", 0.08f * ergue, -0.1f * ergue, 0, w);
        somar(m, "capuz", 0.05f * ergue, 0, 0, w);
        for (int i = 1; i <= 12; i++) somar(m, "farrapo_" + i, 0, 0, 0.08f * suga * Mth.sin(t * 0.5f + i), w);
    }

    /** Ajustes da caveira erguida (giro do braço para o meio e giro da própria caveira). */
    static float CAV_Y = 0.12f, CAV_X = -1.6f;

    private static Map<String, float[]> parado(float t) {
        Map<String, float[]> m = new HashMap<>();
        float resp = Mth.sin(t * 0.055f);
        for (int i = 1; i <= 12; i++) por(m, "farrapo_" + i, 0.18f * Mth.sin(t * 0.12f + i * 1.3f), 0, 0.1f * Mth.sin(t * 0.09f + i));
        por(m, "saia", 0.05f * Mth.sin(t * 0.07f), 0, 0.02f * Mth.sin(t * 0.045f));
        por(m, "corpo", 0.035f * resp, 0, 0.015f * Mth.sin(t * 0.031f));
        // a cabeça: olha em volta devagar e, de vez em quando, inclina de lado
        float inclina = suave((Mth.sin(t * 0.017f) - 0.6f) * 2.5f);
        por(m, "cabeca", 0.05f * Mth.sin(t * 0.05f) - 0.03f * resp, 0.1f * Mth.sin(t * 0.023f), 0.22f * inclina);
        por(m, "mandibula", 0.1f + 0.1f * Math.max(0, Mth.sin(t * 0.21f)) + 0.06f * Math.max(0, Mth.sin(t * 0.73f)), 0, 0);
        por(m, "capuz", 0.04f * Mth.sin(t * 0.06f), 0, 0.03f * inclina);
        // 0.59.1: a foice como bengala: a mão à frente do corpo e a haste em pé, apoiada no chão à frente dele
        float bengala = 0.42f + 0.03f * resp;
        por(m, "braco_d", bengala, 0, 0.1f);
        por(m, "foice", HASTE_PARADO - bengala, 0, -0.1f);
        por(m, "braco_e", 0.1f + 0.05f * Mth.sin(t * 0.05f + 1), 0, -0.18f - 0.03f * resp);
        return m;
    }

    private static Map<String, float[]> voando(float t, boolean cinema) {
        Map<String, float[]> m = new HashMap<>();
        for (int i = 1; i <= 12; i++) {
            float onda = Mth.sin(t * 0.55f - i * 0.9f);
            por(m, "farrapo_" + i, -0.3f + 0.32f * onda, 0, 0.12f * Mth.sin(t * 0.4f + i * 2.1f));
        }
        por(m, "saia", -0.12f + 0.06f * Mth.sin(t * 0.5f), 0, 0.04f * Mth.sin(t * 0.37f));
        por(m, "cabeca", 0.95f + 0.04f * Mth.sin(t * 0.07f), 0.06f * Mth.sin(t * 0.05f), 0);
        por(m, "capuz", -0.32f + 0.07f * Mth.sin(t * 0.6f), 0, 0);
        por(m, "mandibula", 0.28f + 0.06f * Mth.sin(t * 0.3f), 0, 0);
        por(m, "braco_e", 2.85f + 0.05f * Mth.sin(t * 0.09f), 0, -0.08f);
        por(m, "braco_d", -0.22f + 0.04f * Mth.sin(t * 0.08f), 0, 0.1f);
        // na cutscene do espaço o modelo é girado de outro jeito; no voo do mundo a foice vai à frente, como uma lança
        por(m, "foice", !Float.isNaN(debugFoice) ? debugFoice : cinema ? 2.95f : 0.35f, 0, 0.05f);
        por(m, "corpo", 0, 0, 0.05f * Mth.sin(t * 0.04f));
        return m;
    }

    /*
     * Como a foice funciona: ela é filha do braço direito e gira em volta da mão. O ângulo que importa é o da HASTE no
     * mundo (em relação ao corpo): haste = braço + foice. Por isso cada golpe define o ângulo do braço (a) e o ângulo
     * da haste (h) e a foice recebe h - a. Na pose parada a = -0,35 e h ≈ 0 (haste em pé, lâmina em cima).
     * rotX positivo: o braço vai para a frente e para cima; a ponta de cima da haste vai para TRÁS.
     */
    private static void segurar(Map<String, float[]> m, float braco, float haste, float bracoY, float bracoZ, float hasteZ, float peso) {
        ir(m, "braco_d", braco, bracoY, bracoZ, peso);
        ir(m, "foice", haste - braco, 0, hasteZ, peso);
    }

    /** O golpe 1: a foice sobe por cima do ombro (lâmina atrás da cabeça) e desce na frente, de cima para baixo. */
    private static void golpeDiagonal(Map<String, float[]> m, float g) {
        float prep = suave(g / 0.34f);                                         // antecipação: sobe e recua
        float corte = passaDoPonto((g - 0.38f) / 0.2f);                        // o corte (passa do ponto)
        float volta = suave((g - 0.66f) / 0.34f);
        float peso = prep * (1 - volta);
        float c = Mth.clamp(corte, 0, 1.2f);
        // braço: de erguido (2,7) para estendido à frente e abaixo (0,8); haste: de inclinada para trás (0,75,
        // lâmina atrás da cabeça) para deitada à frente e para baixo (-1,9, a lâmina cravando no chão à frente)
        float braco = Mth.lerp(c, 2.7f, 0.8f), haste = Mth.lerp(c, 0.75f, -1.9f);
        segurar(m, braco, haste, Mth.lerp(c, 0.15f, -0.25f), Mth.lerp(c, 0.25f, -0.15f), 0, peso);
        // a mão esquerda ajuda a segurar a haste (sobe junto e desce junto)
        ir(m, "braco_e", Mth.lerp(c, 2.3f, 0.95f), Mth.lerp(c, -0.35f, -0.45f), Mth.lerp(c, -0.2f, 0.25f), peso);
        somar(m, "corpo", Mth.lerp(c, -0.18f, 0.3f), Mth.lerp(c, 0.25f, -0.2f), 0, peso);
        somar(m, "cabeca", Mth.lerp(c, -0.15f, 0.15f), 0, 0, peso);
        ir(m, "mandibula", 0.15f + 0.35f * Mth.clamp(c, 0, 1), 0, 0, peso);
        somar(m, "saia", Mth.lerp(c, -0.1f, 0.15f), 0, 0, peso);
        for (int i = 1; i <= 12; i++) somar(m, "farrapo_" + i, Mth.lerp(c, -0.2f, 0.3f), 0, 0.2f * (c - 0.5f) * (i % 2 == 0 ? 1 : -1), peso);
        somar(m, "capuz", Mth.lerp(c, -0.1f, 0.12f), 0, 0, peso);
    }

    /** O golpe 2: o revés, a foice deitada na altura da cintura varrendo de um lado para o outro (o tronco gira). */
    private static void golpeReves(Map<String, float[]> m, float g) {
        float prep = suave(g / 0.3f);
        float corte = passaDoPonto((g - 0.32f) / 0.24f);
        float volta = suave((g - 0.64f) / 0.36f);
        float peso = prep * (1 - volta);
        float c = Mth.clamp(corte, 0, 1.2f);
        // braço à frente na altura da cintura; haste deitada para a frente (lâmina na ponta, à frente)
        segurar(m, 1.25f, -1.45f, Mth.lerp(c, 0.55f, -0.6f), 0, 0, peso);
        ir(m, "braco_e", 1.15f, Mth.lerp(c, 0.45f, -0.7f), Mth.lerp(c, 0.15f, -0.1f), peso);
        // quem varre é o tronco: começa torcido para o lado da foice e gira inteiro para o outro
        somar(m, "corpo", 0.12f, Mth.lerp(c, -0.75f, 0.8f), 0, peso);
        somar(m, "cabeca", 0, Mth.lerp(c, 0.35f, -0.35f), 0, peso);
        ir(m, "mandibula", 0.4f, 0, 0, peso);
        somar(m, "saia", 0, Mth.lerp(c, 0.25f, -0.3f), 0, peso);
        for (int i = 1; i <= 12; i++) somar(m, "farrapo_" + i, 0.1f, 0, 0.45f * (0.5f - c), peso);
    }

    public static void aplicar(Function<String, Optional<? extends CoreGeoBone>> ossos, Estado e) {
        float t = e.t();
        Map<String, float[]> m = parado(t);
        // ---- voando ----
        float voo = Mth.clamp(e.voo(), 0, 1);
        if (voo > 0.001f) for (var en : voando(t, e.cinema()).entrySet()) ir(m, en.getKey(), en.getValue()[0], en.getValue()[1], en.getValue()[2], voo);
        // ---- deslizando ----
        float an = Mth.clamp(e.andar(), 0, 1) * (1 - voo);
        if (an > 0.001f) {
            float passo = t * 0.32f;
            somar(m, "corpo", -0.16f, 0.05f * Mth.sin(passo), 0.05f * Mth.sin(passo), an);
            somar(m, "cabeca", 0.13f, -0.04f * Mth.sin(passo), 0, an);
            somar(m, "saia", -0.24f + 0.06f * Mth.sin(passo * 2), -0.06f * Mth.sin(passo), 0, an);
            // os farrapos em onda atrasada: cada um um pouco depois do outro
            for (int i = 1; i <= 12; i++) somar(m, "farrapo_" + i, -0.38f + 0.24f * Mth.sin(passo * 2 - i * 0.8f), 0, 0.08f * Mth.sin(passo - i), an);
            somar(m, "capuz", -0.14f + 0.03f * Mth.sin(passo * 2), 0, 0, an);
            somar(m, "braco_e", 0.38f * Mth.sin(passo), 0, -0.06f, an);
            // a bengala vai à frente a cada passo e a haste continua quase em pé
            float b = 0.12f * Mth.sin(passo + Mth.PI);
            somar(m, "braco_d", b, 0, 0, an);
            somar(m, "foice", -b + 0.05f * Mth.sin(passo), 0, 0, an);
        }
        // ---- curva: o tronco vira antes, o pano fica para trás ----
        float v = Mth.clamp(e.virar(), -1, 1);
        if (Math.abs(v) > 0.01f) {
            somar(m, "cabeca", 0, 0.35f * v, -0.06f * v, 1);
            somar(m, "corpo", 0, 0.12f * v, -0.08f * v, 1);
            somar(m, "saia", 0, -0.25f * v, 0.06f * v, 1);
            for (int i = 1; i <= 12; i++) somar(m, "farrapo_" + i, 0, 0, -0.4f * v, 1);
            somar(m, "capuz", 0, -0.1f * v, 0, 1);
        }
        // ---- caindo: a túnica sobe, os braços abrem ----
        float cai = Mth.clamp(e.cair(), 0, 1) * (1 - voo);
        if (cai > 0.01f) {
            for (int i = 1; i <= 12; i++) somar(m, "farrapo_" + i, -0.6f + 0.15f * Mth.sin(t * 0.9f + i), 0, 0, cai);
            somar(m, "saia", -0.2f, 0, 0, cai);
            somar(m, "capuz", -0.3f, 0, 0, cai);
            ir(m, "braco_e", 0.4f, 0, -0.9f, cai);
            somar(m, "braco_d", 0, 0, 0.5f, cai);
        }
        // ---- conjurar (os dons da Morte): a mão esquerda se ergue aberta ----
        float cj = e.conjurar();
        if (cj >= 0) {
            float k = suave(cj / 0.25f) * (1 - suave((cj - 0.7f) / 0.3f));
            ir(m, "braco_e", 1.85f, 0.25f, -0.25f + 0.06f * Mth.sin(t * 1.3f), k);
            somar(m, "cabeca", -0.18f, 0.1f, 0, k);
            ir(m, "mandibula", 0.5f, 0, 0, k);
            somar(m, "corpo", -0.08f, -0.15f, 0, k);
            ir(m, "braco_d", -0.1f, 0, 0.25f, k);
            for (int i = 1; i <= 12; i++) somar(m, "farrapo_" + i, -0.2f, 0, 0.12f * Mth.sin(t * 0.8f + i), k);
        }
        // ---- o golpe da foice (sequência de dois) ----
        float g = e.golpe();
        if (g > 0.001f) {
            if (e.lado() == 0) golpeDiagonal(m, g);
            else golpeReves(m, g);
        }
        // ---- surgir (a transformação): curvado, ele se ergue abrindo os braços ----
        float s = Mth.clamp(e.surge(), 0, 1);
        if (s < 0.999f) {
            float baixo = 1 - suave(s / 0.6f);
            float abre = suave((s - 0.35f) / 0.45f) * (1 - suave((s - 0.8f) / 0.2f));
            somar(m, "corpo", 0.65f * baixo, 0, 0, 1);
            somar(m, "cabeca", 0.6f * baixo - 0.35f * abre, 0, 0, 1);
            ir(m, "braco_e", 0.4f, 0, -1.25f, abre);
            ir(m, "braco_d", 0.3f, 0, 1.0f, abre);
            ir(m, "mandibula", 0.55f, 0, 0, abre);
            for (int i = 1; i <= 12; i++) somar(m, "farrapo_" + i, -0.5f * abre, 0, 0.2f * Mth.sin(t + i) * abre, 1);
        }
        // ---- de joelhos ----
        float k = Mth.clamp(e.ajoelhar(), 0, 1);
        if (k > 0.001f) {
            ir(m, "cabeca", 0.55f + 0.05f * Mth.sin(t * 0.05f), 0, 0, k);
            ir(m, "mandibula", 0.3f, 0, 0, k);
            ir(m, "braco_d", 0.15f, 0, 0.32f, k);
            ir(m, "braco_e", 0.1f, 0, -0.43f, k);
            ir(m, "foice", 1.5f, 0, 0.4f, k);
        }
        // ---- 0.59.1: parado, de vez em quando ele se alimenta de uma caveira ----
        float quieto = (1 - voo) * (1 - an) * (g > 0.001f ? 0 : 1) * (cj >= 0 ? 0 : 1) * (s < 0.999f ? 0 : 1) * (1 - k)
                * (e.cinema() ? 0 : 1) * (alimentar ? 1 : 0);
        float al = ultimo != null ? alimento(mem(ultimo).quietoHa) : -1;
        if (al >= 0 && quieto > 0.01f) alimentar(m, al, quieto, t);
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
    }

    private PoseNecron() {}
}
