package com.lanterna.client.construct;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lanterna.LanternaMod;
import com.lanterna.construct.ConstructEntity;
import com.lanterna.construct.fonte.DefsFonte;
import com.lanterna.construct.fonte.DefsFonte.Comp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Desenha os construtos da Árvore da Luz com as formas 3D de assets/lanterna/construtos/formas.json (caixas, tubos,
 * esferas, anéis e discos, com grupos que giram e pulsam), em energia texturizada da cor da Tropa. Onde a forma
 * aparece depende do comportamento: voando (projéteis), no chão (áreas), em volta do dono (órbitas e reforços),
 * andando (invocações) ou presa ao alvo (correntes).
 */
public final class FormaRender {
    private static Map<String, JsonArray> FORMAS;

    private static JsonArray forma(String id) {
        if (FORMAS == null) {
            FORMAS = new HashMap<>();
            try {
                var res = Minecraft.getInstance().getResourceManager().getResource(LanternaMod.id("construtos/formas.json"));
                if (res.isPresent()) try (var in = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                    JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
                    for (var en : o.entrySet()) FORMAS.put(en.getKey(), en.getValue().getAsJsonArray());
                }
            } catch (Exception ex) {
                LanternaMod.LOGGER.error("Lanterna: falha lendo as formas dos construtos", ex);
            }
        }
        return FORMAS.getOrDefault(id, new JsonArray());
    }

    /** Recarrega as formas (F3+T). */
    public static void recarregar() { FORMAS = null; }

    // ===================== Construto no mundo =====================

    public static void render(ConstructEntity e, PoseStack pose, VertexConsumer body, VertexConsumer glow, int cor, float age, float alpha,
                              Vec3 origin, LivingEntity owner, float pt) {
        DefsFonte.Def d = DefsFonte.get(e.forma());
        if (d == null) return;
        JsonArray f = forma(d.id());
        int gc = EnergyShapes.brilho(cor);
        float grow = EnergyShapes.entrada(age, 5f);
        float s = e.scale();
        if (e.papel() == 1) {
            // emissor: a chuva marca a área no chão
            if (d.comp() == Comp.CHUVA) {
                Vec3 at = e.end().subtract(origin);
                pose.pushPose();
                pose.translate(at.x, at.y + 0.05, at.z);
                EnergyShapes.anel(pose, glow, d.raio() * Math.min(1f, age / 6f), 0.25f, 0.05f, gc, 0.7f * alpha);
                EnergyShapes.disco(pose, body, d.raio(), cor, 0.12f * alpha, 0.25f * alpha);
                pose.popPose();
            }
            return;
        }
        if (e.papel() == 2) {
            Vec3 v = e.getDeltaMovement();
            for (int k = 2; k >= 0; k--) {
                pose.pushPose();
                pose.translate(-v.x * k * 0.4, -v.y * k * 0.4, -v.z * k * 0.4);
                pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
                pose.mulPose(Axis.XP.rotationDegrees(e.getXRot()));
                float g = s * grow * (d.comp() == Comp.CHUVA ? 0.8f : 0.75f) * (1 - k * 0.12f);
                pose.scale(g, g, g);
                desenhar(pose, body, glow, f, cor, age, alpha * (k == 0 ? 1f : 0.3f / k));
                pose.popPose();
            }
            return;
        }
        switch (d.comp()) {
            case RAIO -> {
                Vec3 a = owner != null ? CastAnim.mao(owner, pt).subtract(origin) : Vec3.ZERO;
                Vec3 b = e.end().subtract(origin);
                Vec3 dd = b.subtract(a);
                double len = dd.length();
                if (len < 0.1) return;
                float fade = Mth.clamp((e.life() - age) / 8f, 0, 1) * alpha;
                pose.pushPose();
                pose.translate(a.x, a.y, a.z);
                pose.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(Math.atan2(dd.x, dd.z))));
                pose.mulPose(Axis.XP.rotationDegrees((float) -Math.toDegrees(Math.asin(dd.y / len))));
                pose.scale(1f + 0.3f * (1 - fade), 1f + 0.3f * (1 - fade), (float) (len / 2.4));
                desenhar(pose, body, glow, f, cor, age, fade);
                pose.popPose();
                EnergyShapes.clarao(pose, glow, b, 0.6f, gc, fade, 6);
            }
            case PULSO -> {
                float k = Mth.clamp(age / 12f, 0, 1);
                float ease = 1 - (1 - k) * (1 - k);
                float fade = (1 - k) * alpha;
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
                float r = d.raio() * (0.35f + 0.65f * ease);
                pose.scale(r * 0.55f, r * 0.55f, r * 0.55f);
                desenhar(pose, body, glow, f, cor, age, fade);
                pose.popPose();
                EnergyShapes.anel(pose, glow, d.raio() * ease, 0.5f, 0.15f, gc, 0.8f * fade);
            }
            case AREA, ARMADILHA -> {
                float r = d.comp() == Comp.AREA ? d.raio() : d.raio() * 0.55f;
                float pulsa = d.comp() == Comp.ARMADILHA ? 1f + 0.06f * Mth.sin(age * 0.3f) : 1f;
                pose.pushPose();
                pose.scale(r * grow * pulsa, Math.max(1f, r * 0.6f) * grow, r * grow * pulsa);
                desenhar(pose, body, glow, f, cor, age, alpha * (d.comp() == Comp.ARMADILHA ? 0.75f : 1f));
                pose.popPose();
            }
            case INVOCACAO -> {
                pose.pushPose();
                float bob = d.tem("voa") ? 0.15f * Mth.sin(age * 0.15f) : 0.04f * Mth.abs(Mth.sin(age * 0.5f));
                pose.translate(0, bob, 0);
                pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yRotO, e.getYRot())));
                pose.scale(s * grow, s * grow, s * grow);
                desenhar(pose, body, glow, f, cor, age, alpha);
                pose.popPose();
                Entity t = e.target();
                if (d.tem("torre") && t != null) {
                    Vec3 at = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(origin);
                    EnergyShapes.tube(pose, glow, Vec3.ZERO, at, 0.05f, 0.05f, gc, 0.35f * alpha * (0.5f + 0.5f * Mth.sin(age * 0.6f)), 6);
                }
            }
            case ORBITA -> {
                int n = Math.max(1, d.qtd());
                float h = owner != null ? owner.getBbHeight() * 0.55f : 1f;
                for (int i = 0; i < n; i++) {
                    pose.pushPose();
                    pose.translate(0, h, 0);
                    pose.mulPose(Axis.YP.rotationDegrees(age * 9 + i * 360f / n));
                    pose.translate(0, 0, d.raio() * Math.min(1f, grow));
                    pose.mulPose(Axis.YP.rotationDegrees(90));
                    pose.scale(0.8f, 0.8f, 0.8f);
                    desenhar(pose, body, glow, f, cor, age, alpha);
                    pose.popPose();
                }
            }
            case BARREIRA -> {
                float k = Mth.clamp(age / 30f, 0, 1);
                pose.pushPose();
                pose.scale(d.raio() * (0.5f + 0.5f * k), d.raio() * (0.5f + 0.5f * k), d.raio() * (0.5f + 0.5f * k));
                desenhar(pose, body, glow, f, cor, age, alpha * (1 - k));
                pose.popPose();
            }
            case BUFF -> {
                if (owner == null) return;
                if (owner == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, owner.yBodyRotO, owner.yBodyRot)));
                float h = owner.getBbHeight() / 1.8f;
                String id = d.id();
                if (id.contains("coroa")) pose.translate(0, owner.getBbHeight() + 0.05, 0);
                else if (id.contains("mascara")) pose.translate(0, owner.getEyeHeight(), 0.27);
                else if (id.contains("asas")) pose.translate(0, -0.15, -0.25);
                pose.scale(h, h, h);
                desenhar(pose, body, glow, f, cor, age, alpha * 0.85f);
                pose.popPose();
            }
            case MOVIMENTO -> {
                Vec3 v = e.velocidade().lengthSqr() > 1e-4 ? e.velocidade() : e.getDeltaMovement();
                float fade = Mth.clamp((e.life() - age) / 6f, 0, 1) * alpha;
                pose.pushPose();
                if (v.lengthSqr() > 1e-4) {
                    Vec3 n = v.normalize();
                    pose.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(Math.atan2(n.x, n.z))));
                    pose.mulPose(Axis.XP.rotationDegrees((float) -Math.toDegrees(Math.asin(Mth.clamp(n.y, -1, 1)))));
                }
                pose.translate(0, 0.9, 0);
                desenhar(pose, body, glow, f, cor, age, fade);
                pose.popPose();
            }
            case CORRENTE -> {
                Entity t = e.target();
                if (t == null) return;
                Vec3 a = owner != null ? CastAnim.mao(owner, pt).subtract(origin) : Vec3.ZERO;
                Vec3 b = new Vec3(0, t.getBbHeight() * 0.55, 0);
                int elos = (int) Math.max(3, a.distanceTo(b) / 0.45);
                for (int i = 0; i < elos * Math.min(1f, age / 5f); i++) {
                    Vec3 p0 = a.lerp(b, i / (double) elos), p1 = a.lerp(b, (i + 0.7) / elos);
                    double sag = Math.sin(Math.PI * i / elos) * 0.4;
                    EnergyShapes.tube(pose, body, p0.add(0, -sag, 0), p1.add(0, -sag, 0), 0.06f, 0.06f, cor, 0.6f * alpha, 6);
                }
                pose.pushPose();
                pose.translate(0, 0, 0);
                float w = Math.max(0.6f, t.getBbWidth());
                pose.scale(w, Math.max(0.6f, t.getBbHeight() / 2f), w);
                desenhar(pose, body, glow, f, cor, age, alpha);
                pose.popPose();
            }
            default -> {
                pose.pushPose();
                pose.scale(grow, grow, grow);
                desenhar(pose, body, glow, f, cor, age, alpha);
                pose.popPose();
            }
        }
    }

    // ===================== Primitivas =====================

    public static void desenhar(PoseStack pose, VertexConsumer body, VertexConsumer glow, JsonArray prims, int cor, float age, float alpha) {
        for (JsonElement el : prims) primitiva(pose, body, glow, el.getAsJsonObject(), cor, age, alpha);
    }

    private static Vec3 v3(JsonObject o, String k) {
        JsonArray a = o.getAsJsonArray(k);
        return a == null ? Vec3.ZERO : new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    private static float f(JsonObject o, String k, float def) {
        return o.has(k) ? o.get(k).getAsFloat() : def;
    }

    private static void primitiva(PoseStack pose, VertexConsumer body, VertexConsumer glow, JsonObject p, int cor, float age, float alpha) {
        float k = f(p, "k", 1f);
        int c = k > 1.05f ? EnergyShapes.clarear(cor, Math.min(0.7f, (k - 1f) * 0.6f)) : cor;
        int gc = EnergyShapes.brilho(c);
        float a = alpha * Math.min(1.2f, k);
        switch (p.get("t").getAsString()) {
            case "caixa" -> {
                Vec3 lo = v3(p, "a"), hi = v3(p, "b");
                Vec3 ce = lo.add(hi).scale(0.5);
                pose.pushPose();
                pose.translate(ce.x, ce.y, ce.z);
                if (p.has("rot")) {
                    Vec3 r = v3(p, "rot");
                    pose.mulPose(Axis.YP.rotationDegrees((float) r.y));
                    pose.mulPose(Axis.XP.rotationDegrees((float) r.x));
                    pose.mulPose(Axis.ZP.rotationDegrees((float) r.z));
                }
                Vec3 h = hi.subtract(lo).scale(0.5);
                EnergyShapes.box(pose, body, glow, (float) -h.x, (float) -h.y, (float) -h.z, (float) h.x, (float) h.y, (float) h.z, c, a);
                pose.popPose();
            }
            case "tubo" -> {
                Vec3 x0 = v3(p, "a"), x1 = v3(p, "b");
                float r0 = f(p, "r0", 0.1f), r1 = f(p, "r1", r0);
                int n = (int) f(p, "n", 6);
                EnergyShapes.tube(pose, body, x0, x1, r0, r1, c, 0.6f * a, n);
                EnergyShapes.tube(pose, glow, x0, x1, r0 * 1.12f + 0.01f, r1 * 1.12f + 0.01f, gc, 0.14f * a, n);
                EnergyShapes.tube(pose, glow, x0, x1, r0 * 0.35f, r1 * 0.35f, EnergyShapes.clarear(gc, 0.5f), 0.35f * a, Math.max(4, n / 2));
            }
            case "esfera" -> {
                Vec3 ce = v3(p, "c");
                float r = f(p, "r", 0.5f);
                pose.pushPose();
                pose.translate(ce.x, ce.y, ce.z);
                EnergyShapes.sphere(pose, body, r, c, 0.4f * a, age);
                EnergyShapes.sphere(pose, glow, r * 1.06f, gc, 0.12f * a, age * 1.3f);
                if (k > 1.2f) EnergyShapes.clarao(pose, glow, Vec3.ZERO, r * 1.4f, gc, 0.6f * a, 0);
                pose.popPose();
            }
            case "anel" -> {
                Vec3 ce = v3(p, "c");
                pose.pushPose();
                pose.translate(ce.x, ce.y, ce.z);
                EnergyShapes.anel(pose, body, f(p, "r", 1f), f(p, "w", 0.12f), f(p, "h", 0.06f), c, 0.6f * a);
                EnergyShapes.anel(pose, glow, f(p, "r", 1f) + 0.02f, f(p, "w", 0.12f) * 0.5f, f(p, "h", 0.06f), gc, 0.5f * a);
                pose.popPose();
            }
            case "disco" -> {
                Vec3 ce = v3(p, "c");
                pose.pushPose();
                pose.translate(ce.x, ce.y, ce.z);
                EnergyShapes.disco(pose, body, f(p, "r", 1f), c, 0.3f * a, 0.12f * a);
                pose.popPose();
            }
            case "grupo" -> {
                Vec3 ce = v3(p, "c");
                pose.pushPose();
                pose.translate(ce.x, ce.y, ce.z);
                if (p.has("rot")) {
                    Vec3 r = v3(p, "rot");
                    pose.mulPose(Axis.YP.rotationDegrees((float) r.y));
                    pose.mulPose(Axis.XP.rotationDegrees((float) r.x));
                    pose.mulPose(Axis.ZP.rotationDegrees((float) r.z));
                }
                if (p.has("spin")) {
                    JsonArray sp = p.getAsJsonArray("spin");
                    Vector3f eixo = new Vector3f(sp.get(0).getAsFloat(), sp.get(1).getAsFloat(), sp.get(2).getAsFloat()).normalize();
                    pose.mulPose(new org.joml.Quaternionf().rotationAxis((float) Math.toRadians(age * sp.get(3).getAsFloat()), eixo));
                }
                if (p.has("pulsa")) { float sc = 1f + p.get("pulsa").getAsFloat() * Mth.sin(age * 0.35f); pose.scale(sc, sc, sc); }
                if (p.has("bob")) pose.translate(0, p.get("bob").getAsFloat() * Mth.sin(age * 0.2f), 0);
                pose.translate(-ce.x, -ce.y, -ce.z);
                desenhar(pose, body, glow, p.getAsJsonArray("partes"), cor, age, alpha * Math.min(1.2f, k));
                pose.popPose();
            }
            default -> {}
        }
    }

    private FormaRender() {}
}
