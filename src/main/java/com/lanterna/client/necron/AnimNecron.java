package com.lanterna.client.necron;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lanterna.LanternaMod;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 0.59.3: lê as animações do Necron em quadros-chave (assets/lanterna/animations/entity/necron.animation.json, o
 * formato do Blockbench) e as amostra em qualquer instante, com curva suave (Catmull-Rom) entre os quadros. Quem mistura
 * as animações conforme o estado é o PoseNecron. Gerado por ferramentas/anim_necron.py; dá para abrir e ajustar no
 * Blockbench junto com geo/entity/necron.geo.json.
 *
 * Rotação: o arquivo guarda graus como o Blockbench (X e Y invertidos em relação aos ossos do GeckoLib); aqui já sai
 * em radianos na convenção dos ossos. Posição: em pixels (1/16 de bloco).
 */
public final class AnimNecron {
    private record Chave(float t, float x, float y, float z) {}
    private record Trilha(Chave[] rot, Chave[] pos) {}
    private record Anim(float dur, boolean laco, Map<String, Trilha> ossos) {}

    private static Map<String, Anim> ANIMS;

    private static float num(JsonElement e) {
        if (e == null || e.isJsonNull()) return 0;
        try { return e.getAsFloat(); } catch (Exception ex) {
            try { return Float.parseFloat(e.getAsString().trim()); } catch (Exception ex2) { return 0; }
        }
    }

    private static float[] vetor(JsonElement e) {
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            e = o.has("post") ? o.get("post") : o.has("vector") ? o.get("vector") : o.get("pre");
        }
        if (e != null && e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            return new float[]{num(a.get(0)), num(a.get(1)), num(a.get(2))};
        }
        float v = num(e);
        return new float[]{v, v, v};
    }

    private static Chave[] chaves(JsonElement e, boolean rotacao) {
        if (e == null) return null;
        List<Chave> cs = new ArrayList<>();
        if (e.isJsonObject()) {
            for (var en : e.getAsJsonObject().entrySet()) {
                float[] v = vetor(en.getValue());
                cs.add(chave(Float.parseFloat(en.getKey()), v, rotacao));
            }
        } else cs.add(chave(0, vetor(e), rotacao));
        cs.sort((a, b) -> Float.compare(a.t, b.t));
        return cs.toArray(Chave[]::new);
    }

    private static Chave chave(float t, float[] v, boolean rotacao) {
        if (!rotacao) return new Chave(t, v[0], v[1], v[2]);
        float g = Mth.DEG_TO_RAD;
        return new Chave(t, -v[0] * g, -v[1] * g, v[2] * g);
    }

    private static synchronized void carregar() {
        if (ANIMS != null) return;
        ANIMS = new HashMap<>();
        try (var r = Minecraft.getInstance().getResourceManager().getResource(LanternaMod.id("animations/entity/necron.animation.json")).orElseThrow().openAsReader()) {
            JsonObject todas = JsonParser.parseReader(r).getAsJsonObject().getAsJsonObject("animations");
            for (var en : todas.entrySet()) {
                JsonObject a = en.getValue().getAsJsonObject();
                Map<String, Trilha> ossos = new HashMap<>();
                if (a.has("bones")) for (var b : a.getAsJsonObject("bones").entrySet()) {
                    JsonObject o = b.getValue().getAsJsonObject();
                    ossos.put(b.getKey(), new Trilha(chaves(o.get("rotation"), true), chaves(o.get("position"), false)));
                }
                boolean laco = a.has("loop") && a.get("loop").isJsonPrimitive() && a.get("loop").getAsJsonPrimitive().isBoolean() && a.get("loop").getAsBoolean();
                ANIMS.put(en.getKey(), new Anim(a.has("animation_length") ? num(a.get("animation_length")) : 1, laco, ossos));
            }
        } catch (Exception ex) {
            LanternaMod.LOGGER.error("Lanterna: não consegui ler as animações do Necron", ex);
        }
    }

    /** Recarrega na próxima vez (F3+T). */
    public static void esquecer() { ANIMS = null; }

    private static float cr(float p0, float p1, float p2, float p3, float u) {
        float u2 = u * u, u3 = u2 * u;
        return 0.5f * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
    }

    private static float[] valor(Chave[] k, float t) {
        if (k.length == 1 || t <= k[0].t) return new float[]{k[0].x, k[0].y, k[0].z};
        if (t >= k[k.length - 1].t) { Chave c = k[k.length - 1]; return new float[]{c.x, c.y, c.z}; }
        int i = 0;
        while (i < k.length - 2 && t >= k[i + 1].t) i++;
        Chave a = k[Math.max(0, i - 1)], b = k[i], c = k[i + 1], d = k[Math.min(k.length - 1, i + 2)];
        float u = (t - b.t) / Math.max(1e-4f, c.t - b.t);
        return new float[]{cr(a.x, b.x, c.x, d.x, u), cr(a.y, b.y, c.y, d.y, u), cr(a.z, b.z, c.z, d.z, u)};
    }

    /** Duração (s) da animação. */
    public static float duracao(String nome) {
        carregar();
        Anim a = ANIMS.get("animation.necron." + nome);
        return a == null ? 1 : a.dur;
    }

    /**
     * Mistura a animação "nome" no instante "segundos" nas poses (rotação em radianos e posição em pixels), puxando
     * cada osso que ela anima com o peso dado (1 = só ela).
     */
    public static void amostrar(String nome, float segundos, Map<String, float[]> rot, Map<String, float[]> pos, float peso) {
        if (peso <= 0.0001f) return;
        carregar();
        Anim a = ANIMS.get("animation.necron." + nome);
        if (a == null) return;
        float t = a.laco ? ((segundos % a.dur) + a.dur) % a.dur : Mth.clamp(segundos, 0, a.dur);
        for (var en : a.ossos.entrySet()) {
            Trilha tr = en.getValue();
            if (tr.rot != null) puxar(rot, en.getKey(), valor(tr.rot, t), peso);
            if (tr.pos != null) puxar(pos, en.getKey(), valor(tr.pos, t), peso);
        }
    }

    private static void puxar(Map<String, float[]> m, String osso, float[] v, float peso) {
        float[] o = m.computeIfAbsent(osso, q -> new float[3]);
        for (int i = 0; i < 3; i++) o[i] = Mth.lerp(peso, o[i], v[i]);
    }

    private AnimNecron() {}
}
