package com.lanterna.client.mogo;

import com.lanterna.LanternaMod;
import com.lanterna.client.escolha.Geo;
import com.lanterna.client.render.LanternaRenderTypes;
import com.lanterna.mogo.ChamadoMogo;
import com.lanterna.mogo.ChamadoPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A cena do Chamado do Mogo: a jaula de barras cai sobre o alvo, o Mogo (um planeta inteiro, com a faixa de jade
 * acesa) vem chegando no céu, carrega e dispara um raio gigante. Quem chamou vê com câmera de cinema; todos por perto
 * veem o planeta e o raio, e o chão treme.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClienteChamado {
    private static final List<ChamadoPacket> ATIVOS = new ArrayList<>();
    private static final int JADE = 0x3CFF7A;

    public static void comecar(ChamadoPacket p) {
        ATIVOS.add(p);
    }

    private static float t(ChamadoPacket p, float pt) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 999 : mc.level.getGameTime() - p.inicio() + pt;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (Minecraft.getInstance().level == null) ATIVOS.clear();
        else ATIVOS.removeIf(p -> t(p, 0) > ChamadoMogo.FIM + 10);
    }

    /** Posição do Mogo no céu: vem de longe e para em mogo(). */
    private static Vec3 posMogo(ChamadoPacket p, float t) {
        Vec3 c = new Vec3(p.x(), p.y(), p.z()), m = new Vec3(p.mx(), p.my(), p.mz());
        float q = Geo.suaveX(Math.min(1, t / ChamadoMogo.CHEGA));
        return c.add(m.subtract(c).scale(Mth.lerp(q, 2.6f, 1f)));
    }

    // ===================================== desenho =====================================

    @SubscribeEvent
    public static void desenhar(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ATIVOS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        float pt = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        PoseStack ps = e.getPoseStack();
        for (ChamadoPacket p : ATIVOS) {
            float t = t(p, pt);
            if (t < 0 || t > ChamadoMogo.FIM + 8) continue;
            Vec3 c = new Vec3(p.x(), p.y(), p.z());
            ps.pushPose();
            ps.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
            Geo.buf = buf;
            Geo.ps = ps;
            Geo.cam = new Vector3f((float) (cam.x - c.x), (float) (cam.y - c.y), (float) (cam.z - c.z));
            Geo.tempo = mc.level.getGameTime() + pt;
            Vec3 m = posMogo(p, t).subtract(c);
            float escala = Mth.lerp(Geo.suaveX(Math.min(1, t / ChamadoMogo.CHEGA)), 0.45f, 1f);
            float some = 1 - Mth.clamp((t - ChamadoMogo.FIM + 4) / 12f, 0, 1);
            planeta(new Vector3f((float) m.x, (float) m.y, (float) m.z), 62 * escala, t, some);
            Entity alvo = mc.level.getEntity(p.alvo());
            jaula(alvo, t);
            raio(new Vector3f((float) m.x, (float) m.y, (float) m.z), 62 * escala, t);
            ps.popPose();
        }
        buf.endBatch();
    }

    // ---------------- o planeta ----------------

    private static float[][] malha; // por vértice: x,y,z (esfera unitária), cor
    private static final int LAT = 36, LON = 64;

    private static float ruido(float x, float y, float z) {
        return Mth.sin(x * 3.1f + 1.3f) * Mth.sin(y * 2.7f + 0.4f) * Mth.sin(z * 3.3f + 2.1f)
                + 0.5f * Mth.sin(x * 7.3f + z * 5.1f) * Mth.sin(y * 6.7f + 1.7f)
                + 0.25f * Mth.sin(x * 13.1f - y * 11.3f + z * 9.7f);
    }

    private static void montar() {
        malha = new float[(LAT + 1) * (LON + 1)][];
        for (int i = 0; i <= LAT; i++) for (int j = 0; j <= LON; j++) {
            float la = -Mth.HALF_PI + Mth.PI * i / LAT, lo = Mth.TWO_PI * j / LON;
            float x = Mth.cos(la) * Mth.cos(lo), y = Mth.sin(la), z = Mth.cos(la) * Mth.sin(lo);
            float n = ruido(x, y, z);
            int cor;
            if (Math.abs(y) > 0.88f) cor = 0xE4F2EA;                       // gelo nos polos
            else if (Math.abs(y) < 0.11f) cor = 0x2AB050;                 // a faixa (floresta de jade)
            else if (n > 0.35f) cor = Geo.mistura(0x5C8A30, 0x9A8458, Mth.clamp((n - 0.35f) * 2, 0, 1)); // serras
            else if (n > -0.05f) cor = Geo.mistura(0x2E8A34, 0x52B044, Mth.clamp(n * 3 + 0.2f, 0, 1));  // florestas
            else cor = Geo.mistura(0x1A5A78, 0x2A88A0, Mth.clamp(n + 0.9f, 0, 1));                        // mares
            malha[i * (LON + 1) + j] = new float[]{x, y, z, cor};
        }
    }

    private static void planeta(Vector3f centro, float raio, float t, float alfa) {
        if (malha == null) montar();
        if (alfa <= 0.01f) return;
        PoseStack ps = Geo.ps;
        ps.pushPose();
        ps.translate(centro.x, centro.y, centro.z);
        Vector3f paraCam = new Vector3f(Geo.cam).sub(centro).normalize();
        // o eixo do planeta fica de lado para quem olha: a faixa de jade atravessa o disco, inclinada
        Vector3f lado = new Vector3f(paraCam).cross(0, 0, 1);
        if (lado.lengthSquared() < 1e-4f) lado.set(1, 0, 0);
        lado.normalize();
        Vector3f eixo = new Vector3f(paraCam).cross(lado).normalize().lerp(lado, 0.35f).normalize();
        Quaternionf rot = new Quaternionf().rotationTo(0, 1, 0, eixo.x, eixo.y, eixo.z).rotateY(Geo.tempo * 0.002f);
        ps.mulPose(rot);
        Matrix4f m = ps.last().pose();
        // luz vindo meio de frente (do lado de quem olha), para ver o relevo
        Vector3f luz = new Vector3f(paraCam).add(new Vector3f(lado).mul(0.6f)).add(0, 0.4f, 0).normalize();
        VertexConsumer vc = Geo.corpo();
        for (int i = 0; i < LAT; i++) for (int j = 0; j < LON; j++) {
            float[][] q = {malha[i * (LON + 1) + j], malha[i * (LON + 1) + j + 1], malha[(i + 1) * (LON + 1) + j + 1], malha[(i + 1) * (LON + 1) + j]};
            Vector3f n = rot.transform(new Vector3f(q[0][0] + q[2][0], q[0][1] + q[2][1], q[0][2] + q[2][2]).normalize());
            if (n.dot(paraCam) < -0.05f) continue; // lado de trás
            for (float[] v : q) {
                Vector3f nw = rot.transform(new Vector3f(v[0], v[1], v[2]));
                float sh = 0.32f + 0.95f * Math.max(0, nw.dot(luz));
                int c = (int) v[3];
                Geo.v(vc, m, v[0] * raio, v[1] * raio, v[2] * raio, Geo.r(c) * sh, Geo.g(c) * sh, Geo.b(c) * sh, alfa, v[0] * 3 + v[2], v[1] * 3);
            }
        }
        // a faixa de jade, acesa (mais forte quando carrega o raio)
        float carga = Mth.clamp((t - ChamadoMogo.CHEGA) / (ChamadoMogo.CARGA - ChamadoMogo.CHEGA), 0, 1);
        float forca = (0.55f + 0.45f * carga) * alfa * (0.9f + 0.1f * Mth.sin(Geo.tempo * 0.4f));
        VertexConsumer lz = Geo.luz();
        int seg = 96;
        for (int k = 0; k < seg; k++) {
            float a0 = Mth.TWO_PI * k / seg, a1 = Mth.TWO_PI * (k + 1) / seg;
            for (float[] f : new float[][]{{-0.11f, 0f}, {0f, 0.11f}}) {
                float y0 = f[0], y1 = f[1], r0 = Mth.sqrt(1 - y0 * y0) * raio * 1.006f, r1 = Mth.sqrt(1 - y1 * y1) * raio * 1.006f;
                float al0 = y0 == 0 ? forca : 0, al1 = y1 == 0 ? forca : 0;
                Geo.v(lz, m, Mth.cos(a0) * r0, y0 * raio, Mth.sin(a0) * r0, JADE, al0, 0.5f, 0.5f);
                Geo.v(lz, m, Mth.cos(a1) * r0, y0 * raio, Mth.sin(a1) * r0, JADE, al0, 0.5f, 0.5f);
                Geo.v(lz, m, Mth.cos(a1) * r1, y1 * raio, Mth.sin(a1) * r1, JADE, al1, 0.5f, 0.5f);
                Geo.v(lz, m, Mth.cos(a0) * r1, y1 * raio, Mth.sin(a0) * r1, JADE, al1, 0.5f, 0.5f);
            }
        }
        ps.popPose();
        // atmosfera: halo esverdeado em volta do disco
        Geo.bola(centro.x, centro.y, centro.z, raio * 1.35f, 0x5AFFB0, 0.35f * alfa);
        // o brilho que junta no ponto de onde sai o raio
        if (carga > 0) {
            Vector3f d = new Vector3f(centro).negate().normalize();
            Vector3f ponto = new Vector3f(centro).add(new Vector3f(d).mul(raio));
            Geo.bola(ponto.x, ponto.y, ponto.z, raio * (0.2f + 0.5f * carga), JADE, carga * alfa);
            Geo.bola(ponto.x, ponto.y, ponto.z, raio * 0.18f * carga, 0xFFFFFF, carga * alfa);
        }
    }

    // ---------------- a jaula ----------------

    private static void jaula(Entity alvo, float t) {
        if (t > ChamadoMogo.ACERTA + 4) return;
        float larg = 1.4f, alt = 2.4f;
        if (alvo != null) {
            AABB b = alvo.getBoundingBox();
            larg = (float) Math.max(b.getXsize(), b.getZsize()) + 0.8f;
            alt = (float) b.getYsize() + 0.6f;
        }
        float cai = Geo.suaveX(Math.min(1, t / 6f));
        float y0 = (1 - cai) * 6f, h = larg / 2;
        float a = t > ChamadoMogo.ACERTA ? 1 - (t - ChamadoMogo.ACERTA) / 4f : 1;
        float pisca = t > ChamadoMogo.CARGA ? 0.7f + 0.3f * Mth.sin(Geo.tempo * 2f) : 1;
        int n = Math.max(4, Math.round(larg / 0.3f));
        float e = 0.045f;
        for (int lado = 0; lado < 4; lado++) for (int k = 0; k <= n; k++) {
            float u = -h + larg * k / n;
            float x = lado < 2 ? u : (lado == 2 ? -h : h), z = lado < 2 ? (lado == 0 ? -h : h) : u;
            barra(x - e, y0, z - e, x + e, y0 + alt, z + e, a * pisca);
        }
        for (float y : new float[]{y0, y0 + alt / 2, y0 + alt}) {
            barra(-h - e, y - e, -h - e, h + e, y + e, -h + e, a);
            barra(-h - e, y - e, h - e, h + e, y + e, h + e, a);
            barra(-h - e, y - e, -h - e, -h + e, y + e, h + e, a);
            barra(h - e, y - e, -h - e, h + e, y + e, h + e, a);
        }
        // tampa em grade
        for (int k = 0; k <= n; k++) {
            float u = -h + larg * k / n;
            barra(u - e, y0 + alt - e, -h, u + e, y0 + alt + e, h, a);
        }
        Geo.anelChao(0, larg * 0.9f, 0.03f, JADE, 0.35f * a, 0, true);
    }

    /** Barra de construto: caixa translúcida sombreada + brilho. */
    private static void barra(float x0, float y0, float z0, float x1, float y1, float z1, float a) {
        if (a <= 0.01f) return;
        Matrix4f m = Geo.ps.last().pose();
        VertexConsumer vc = Geo.corpo();
        float[][][] faces = {
                {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}}, {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},
                {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}}, {{x1, y0, z0}, {x1, y0, z1}, {x1, y1, z1}, {x1, y1, z0}},
                {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}}, {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}};
        float[] sh = {0.8f, 0.8f, 0.65f, 0.65f, 1f, 0.5f};
        for (int f = 0; f < 6; f++) for (float[] p : faces[f])
            Geo.v(vc, m, p[0], p[1], p[2], Geo.r(JADE) * sh[f] + 0.15f, Geo.g(JADE) * sh[f] + 0.1f, Geo.b(JADE) * sh[f] + 0.12f, 0.85f * a, p[0] + p[2], p[1]);
        float g = 0.06f;
        VertexConsumer lz = Geo.luz();
        for (int f = 0; f < 4; f++) for (float[] p : faces[f]) {
            float ox = f == 2 ? -g : f == 3 ? g : 0, oz = f == 0 ? -g : f == 1 ? g : 0;
            Geo.v(lz, m, p[0] + ox, p[1], p[2] + oz, JADE, 0.35f * a, 0.5f, 0.5f);
        }
    }

    // ---------------- o raio ----------------

    private static void raio(Vector3f mogo, float raioMogo, float t) {
        Vector3f dir = new Vector3f(mogo).normalize();
        float dist = mogo.length() - raioMogo * 0.98f;
        PoseStack ps = Geo.ps;
        ps.pushPose();
        ps.mulPose(new Quaternionf().rotationTo(0, 1, 0, dir.x, dir.y, dir.z));
        if (t >= ChamadoMogo.CHEGA && t < ChamadoMogo.CARGA) {
            // mira: um fio fino tremendo, cada vez mais forte
            float k = (t - ChamadoMogo.CHEGA) / (ChamadoMogo.CARGA - ChamadoMogo.CHEGA);
            float fl = 0.6f + 0.4f * Mth.sin(Geo.tempo * 3f);
            Geo.tubo(0.08f + 0.12f * k, 0, dist, JADE, 0.7f * k * fl, 0.4f * k * fl, 0, true);
        }
        if (t >= ChamadoMogo.CARGA) {
            float vida = t - ChamadoMogo.CARGA;
            float abre = Geo.suaveX(Math.min(1, vida / 4f));
            float fecha = 1 - Mth.clamp((t - (ChamadoMogo.FIM - 10)) / 10f, 0, 1);
            float f = abre * fecha;
            if (f > 0.01f) {
                float pulso = 1 + 0.08f * Mth.sin(Geo.tempo * 2.2f);
                Geo.tubo(4.2f * f * pulso, -2, dist, JADE, 0.22f * f, 0.12f * f, 0.05f, true);
                Geo.tubo(2.0f * f * pulso, -2, dist, JADE, 0.6f * f, 0.45f * f, 0.08f, true);
                Geo.tubo(0.75f * f, -2, dist, 0xFFFFFF, 0.95f * f, 0.8f * f, 0.03f, true);
                // anéis de energia descendo pelo raio
                for (int k = 0; k < 6; k++) {
                    float y = dist - ((Geo.tempo * 6f + k * dist / 6f) % dist);
                    Geo.anelChao(2.2f * f, 3.4f * f, y, JADE, 0.6f * f, 0, true);
                }
            }
        }
        ps.popPose();
        // impacto
        if (t >= ChamadoMogo.ACERTA - 1) {
            float d = t - ChamadoMogo.ACERTA;
            float cl = Math.max(0, 1 - Math.abs(d) / 6f);
            Geo.bola(0, 1, 0, 14 * cl, JADE, cl);
            Geo.bola(0, 1, 0, 6 * cl, 0xFFFFFF, cl);
            float o = Mth.clamp(d / 22f, 0, 1);
            if (o > 0 && o < 1) {
                Geo.onda(o * 26, 2.5f, 0.3f, JADE, (1 - o));
                Geo.onda(o * 18, 1.2f, 0.35f, 0xFFFFFF, (1 - o) * 0.6f);
            }
            float brasa = 1 - Mth.clamp((t - ChamadoMogo.ACERTA) / 40f, 0, 1);
            Geo.anelChao(0, 3.6f, 0.05f, 0xFF8A30, 0.4f * brasa, 0, true);
        }
    }

    // ===================================== câmera e tremor =====================================

    /** Câmera de quem chamou: {x,y,z,yaw,pitch} ou null. */
    public static double[] camera(Entity ent, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (ent != mc.player || ATIVOS.isEmpty()) return null;
        for (ChamadoPacket p : ATIVOS) {
            if (p.dono() != ent.getId()) continue;
            float t = t(p, pt);
            if (t < 0 || t >= ChamadoMogo.FIM - 4) continue;
            Vec3 pe = ent.getPosition(pt);
            Vec3 c = new Vec3(p.x(), p.y(), p.z());
            Vec3 m = posMogo(p, t);
            Vec3 h = new Vec3(c.x - pe.x, 0, c.z - pe.z);
            if (h.lengthSqr() < 1e-3) h = ent.getLookAngle().multiply(1, 0, 1);
            h = h.normalize();
            Vec3 lado = new Vec3(-h.z, 0, h.x);
            // atrás e um pouco acima de quem chamou
            Vec3 pos = pe.add(h.scale(-4.2)).add(0, 2.4, 0).add(lado.scale(1.4));
            // olha para o Mogo, depois desce junto com o raio até o alvo
            float desce = Geo.suaveX(Mth.clamp((t - ChamadoMogo.CARGA + 6) / 14f, 0, 1));
            Vec3 olhoMogo = m, olhoAlvo = c.add(0, 1, 0);
            Vec3 alvo = olhoMogo.add(olhoAlvo.subtract(olhoMogo).scale(Mth.lerp(desce, 0.0, 0.92)));
            double dx = alvo.x - pos.x, dy = alvo.y - pos.y, dz = alvo.z - pos.z;
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
            return new double[]{pos.x, pos.y, pos.z, yaw, pitch};
        }
        return null;
    }

    public static boolean emCena() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && camera(mc.player, 0) != null;
    }

    @SubscribeEvent
    public static void tremor(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (ATIVOS.isEmpty() || mc.player == null) return;
        float forca = 0;
        for (ChamadoPacket p : ATIVOS) {
            float t = t(p, (float) e.getPartialTick());
            double d = mc.player.position().distanceTo(new Vec3(p.x(), p.y(), p.z()));
            float perto = (float) Mth.clamp(1 - d / 90, 0, 1);
            if (t > ChamadoMogo.CARGA && t < ChamadoMogo.FIM) {
                float k = t < ChamadoMogo.ACERTA + 10 ? 3.5f : 1.2f * (1 - (t - ChamadoMogo.ACERTA - 10) / 20f);
                forca = Math.max(forca, k * perto);
            } else if (t > ChamadoMogo.CHEGA - 15 && t <= ChamadoMogo.CARGA) forca = Math.max(forca, 0.4f * perto);
        }
        if (forca <= 0) return;
        var r = mc.player.getRandom();
        e.setYaw(e.getYaw() + (r.nextFloat() - 0.5f) * forca);
        e.setPitch(e.getPitch() + (r.nextFloat() - 0.5f) * forca);
        e.setRoll(e.getRoll() + (r.nextFloat() - 0.5f) * forca * 0.6f);
    }

    /** Céu esverdeado enquanto o Mogo está lá. */
    @SubscribeEvent
    public static void corNeblina(ViewportEvent.ComputeFogColor e) {
        Minecraft mc = Minecraft.getInstance();
        if (ATIVOS.isEmpty() || mc.player == null) return;
        for (ChamadoPacket p : ATIVOS) {
            float t = t(p, (float) e.getPartialTick());
            if (t < 0 || t > ChamadoMogo.FIM) continue;
            if (mc.player.position().distanceTo(new Vec3(p.x(), p.y(), p.z())) > 200) continue;
            float k = Mth.clamp(t / 30f, 0, 1) * (1 - Mth.clamp((t - ChamadoMogo.FIM + 15) / 15f, 0, 1)) * 0.45f;
            e.setRed(Mth.lerp(k, e.getRed(), 0.25f));
            e.setGreen(Mth.lerp(k, e.getGreen(), 0.6f));
            e.setBlue(Mth.lerp(k, e.getBlue(), 0.35f));
            return;
        }
    }

    @SubscribeEvent
    public static void semMao(RenderHandEvent e) {
        if (emCena()) e.setCanceled(true);
    }

    private ClienteChamado() {}
}
