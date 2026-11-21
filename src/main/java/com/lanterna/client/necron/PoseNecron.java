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
 * 0.58.3: as animações do Necron, feitas por código e usadas por todos que desenham o modelo: o chefe na cratera, a
 * Forma do Necron do Arauto e as cutscenes. Cada pose é uma lista de rotações por osso; elas se misturam pelo peso:
 *
 *   parado    respira, a mandíbula treme, farrapos e capa balançam devagar
 *   andando   desliza inclinado para a frente; farrapos e saia escorrem para trás; o braço livre balança
 *   voando    a pose do voo (o corpo vai deitado; quem desenha inclina o modelo inteiro)
 *   golpe     a foice: levanta por cima do ombro (preparo), desce em diagonal com o tronco girando (o corte) e volta
 *   ajoelhar  vencido: cabeça baixa, braços soltos, a foice escorregando
 *
 * (rotX positivo leva um membro que pende para baixo na direção da frente.)
 */
public final class PoseNecron {
    public record Estado(float t, float andar, float voo, float golpe, float ajoelhar, boolean cinema) {
        public Estado(float t, float andar, float voo, float golpe, float ajoelhar) { this(t, andar, voo, golpe, ajoelhar, false); }
    }

    /** Duração do golpe da foice (ticks). */
    public static final float DUR_GOLPE = 14f;
    /** Só para o teste: força o ângulo da foice no voo. */
    public static float debugFoice = Float.NaN;

    private static final Map<Entity, Float> INICIO_GOLPE = new WeakHashMap<>();

    /** Quanto do golpe da foice já passou (0..1), ou 0 sem golpe. Começa quando a entidade balança o braço. */
    public static float golpe(Entity e, float pt) {
        float anim = e instanceof LivingEntity le ? le.getAttackAnim(pt) : 0;
        float agora = e.tickCount + pt;
        Float inicio = INICIO_GOLPE.get(e);
        if (inicio == null) {
            if (anim <= 0) return 0;
            inicio = agora - anim * 6;
            INICIO_GOLPE.put(e, inicio);
        }
        float k = (agora - inicio) / DUR_GOLPE;
        if (k >= 1 || k < 0) {
            if (anim <= 0 || k < 0) INICIO_GOLPE.remove(e);
            return 0;
        }
        return k;
    }

    /** O quanto a entidade está se movendo (0..1), pelo deslocamento desde o último tick. */
    public static float andar(Entity e, float pt) {
        double dx = e.getX() - e.xo, dz = e.getZ() - e.zo;
        return Mth.clamp((float) Math.sqrt(dx * dx + dz * dz) * 5f, 0, 1);
    }

    private static float suave(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }

    private static void por(Map<String, float[]> m, String o, float x, float y, float z) { m.put(o, new float[]{x, y, z}); }

    private static void somar(Map<String, float[]> m, String o, float x, float y, float z, float k) {
        float[] v = m.computeIfAbsent(o, q -> new float[3]);
        v[0] += x * k; v[1] += y * k; v[2] += z * k;
    }

    /** Mistura para o alvo (k = 0 fica como está; 1 vira o alvo). */
    private static void ir(Map<String, float[]> m, String o, float x, float y, float z, float k) {
        float[] v = m.computeIfAbsent(o, q -> new float[3]);
        v[0] = Mth.lerp(k, v[0], x); v[1] = Mth.lerp(k, v[1], y); v[2] = Mth.lerp(k, v[2], z);
    }

    private static Map<String, float[]> parado(float t) {
        Map<String, float[]> m = new HashMap<>();
        for (int i = 1; i <= 12; i++) por(m, "farrapo_" + i, 0.18f * Mth.sin(t * 0.12f + i * 1.3f), 0, 0.1f * Mth.sin(t * 0.09f + i));
        por(m, "saia", 0.05f * Mth.sin(t * 0.07f), 0, 0);
        por(m, "corpo", 0.03f * Mth.sin(t * 0.06f), 0, 0);
        por(m, "cabeca", 0.05f * Mth.sin(t * 0.05f), 0.08f * Mth.sin(t * 0.03f), 0);
        por(m, "mandibula", 0.12f + 0.12f * Math.max(0, Mth.sin(t * 0.21f)), 0, 0);
        por(m, "capuz", 0.04f * Mth.sin(t * 0.06f), 0, 0);
        por(m, "braco_d", -0.35f + 0.04f * Mth.sin(t * 0.05f), 0, 0.12f);
        por(m, "braco_e", 0.1f + 0.05f * Mth.sin(t * 0.05f + 1), 0, -0.18f);
        por(m, "foice", 0.3f, 0, 0);
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

    public static void aplicar(Function<String, Optional<? extends CoreGeoBone>> ossos, Estado e) {
        float t = e.t();
        Map<String, float[]> m = parado(t);
        // ---- voando ----
        float voo = Mth.clamp(e.voo(), 0, 1);
        if (voo > 0.001f) for (var en : voando(t, e.cinema()).entrySet()) ir(m, en.getKey(), en.getValue()[0], en.getValue()[1], en.getValue()[2], voo);
        // ---- andando (deslizando) ----
        float an = Mth.clamp(e.andar(), 0, 1) * (1 - voo);
        if (an > 0.001f) {
            float passo = t * 0.32f;
            somar(m, "corpo", -0.14f, 0, 0.04f * Mth.sin(passo), an);
            somar(m, "cabeca", 0.12f, 0, 0, an);
            somar(m, "saia", -0.22f + 0.05f * Mth.sin(passo * 2), 0, 0, an);
            for (int i = 1; i <= 12; i++) somar(m, "farrapo_" + i, -0.35f + 0.22f * Mth.sin(passo * 2 - i * 0.8f), 0, 0, an);
            somar(m, "capuz", -0.12f, 0, 0, an);
            somar(m, "braco_e", 0.35f * Mth.sin(passo), 0, -0.05f, an);
            somar(m, "braco_d", -0.15f + 0.15f * Mth.sin(passo + Mth.PI), 0, 0, an);
        }
        // ---- o golpe da foice ----
        float g = e.golpe();
        if (g > 0.001f) {
            float prep = suave(g / 0.32f);                                      // levanta por cima do ombro
            float corte = suave((g - 0.32f) / 0.26f);                           // desce em diagonal
            float volta = suave((g - 0.62f) / 0.38f);                           // volta à pose
            float peso = 1 - volta;
            float bx = Mth.lerp(corte, 2.9f, 0.55f), bz = Mth.lerp(corte, 0.65f, -0.45f), by = Mth.lerp(corte, 0.35f, -0.55f);
            float fx = Mth.lerp(corte, -0.9f, 0.85f);
            float tronco = Mth.lerp(corte, 0.38f, -0.42f), inclina = Mth.lerp(corte, -0.12f, 0.22f);
            ir(m, "braco_d", bx, by, bz, prep * peso);
            ir(m, "foice", fx, 0, Mth.lerp(corte, 0.2f, -0.25f), prep * peso);
            somar(m, "corpo", inclina, tronco, 0, prep * peso);
            somar(m, "cabeca", -0.1f * (1 - corte), -tronco * 0.5f, 0, prep * peso);
            ir(m, "braco_e", Mth.lerp(corte, 0.9f, -0.3f), 0, Mth.lerp(corte, -0.5f, -0.2f), prep * peso);
            ir(m, "mandibula", 0.45f, 0, 0, prep * peso);                     // abre a boca no golpe
            for (int i = 1; i <= 12; i++) somar(m, "farrapo_" + i, 0, 0, 0.35f * (corte - 0.5f) * (i % 2 == 0 ? 1 : -1), prep * peso);
            somar(m, "saia", 0, -tronco * 0.4f, 0, prep * peso);
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
        for (var en : m.entrySet()) {
            float[] v = en.getValue();
            ossos.apply(en.getKey()).ifPresent(b -> {
                b.setRotX(b.getInitialSnapshot().getRotX() + v[0]);
                b.setRotY(b.getInitialSnapshot().getRotY() + v[1]);
                b.setRotZ(b.getInitialSnapshot().getRotZ() + v[2]);
            });
        }
    }

    private PoseNecron() {}
}
