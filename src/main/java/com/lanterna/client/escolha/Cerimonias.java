package com.lanterna.client.escolha;

import com.lanterna.LanternaMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Cerimônias de escolha do anel, desenhadas no mundo (todo mundo por perto vê). Para o escolhido ainda tem:
 * câmera de cinema (dá a volta, aproxima e termina no close da mão recebendo o anel), névoa e cor do céu do ritual,
 * tremor nas batidas e raios, e o braço direito se erguendo para o anel encaixar no dedo.
 * O cenário de cada Tropa está em {@link Cenas}; as formas em {@link Geo}.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Cerimonias {
    record Cer(String estilo, String corps, int cor, long inicio, int dur) {}

    private static final Map<Integer, Cer> ATIVAS = new HashMap<>();
    static final float PAIRA = 0.86f, DEDO = 0.92f;

    public static void comecar(int entidade, String corps, int cor, int dur) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int i = corps.indexOf(':');
        ATIVAS.put(entidade, new Cer(i >= 0 ? corps.substring(i + 1) : corps, corps, cor & 0xFFFFFF, mc.level.getGameTime(), Math.max(20, dur)));
    }

    private static float t(Cer c, float pt) {
        Minecraft mc = Minecraft.getInstance();
        return (mc.level.getGameTime() - c.inicio + pt) / c.dur;
    }

    private static Cer daLocal() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : ATIVAS.get(mc.player.getId());
    }

    /** A câmera de cinema está valendo para o jogador local? */
    public static boolean emCena() {
        Cer c = daLocal();
        return c != null && t(c, 0) < 1;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ATIVAS.clear();
            return;
        }
        ATIVAS.entrySet().removeIf(en -> t(en.getValue(), 0) > 1.08f);
    }

    // ---------------- geometria do corpo ----------------

    static Vector3f frente(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vector3f(-Mth.sin(r), 0, Mth.cos(r));
    }

    static Vector3f direita(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vector3f(-Mth.cos(r), 0, -Mth.sin(r));
    }

    /** Mão direita erguida (relativa aos pés). */
    static Vector3f mao(float yaw) {
        return new Vector3f(direita(yaw)).mul(0.38f).add(new Vector3f(frente(yaw)).mul(0.34f)).add(0, 2.0f, 0);
    }

    static Vector3f pairar(float yaw) {
        return new Vector3f(frente(yaw)).mul(1.25f).add(0, 1.65f, 0);
    }

    /** Onde o anel está (relativo aos pés) e de que tamanho: {x, y, z, tamanho} ou null. */
    static float[] anel(Cer c, float t, float yaw, int semente, float tempo) {
        Vector3f p = pairar(yaw), m = mao(yaw);
        Vector3f pos;
        float tam;
        if (t < Cenas.CHEGA) {
            pos = Cenas.anel(c.estilo, t, p, semente, tempo);
            if (pos == null) return null;
            tam = c.estilo.equals("green") && t < 0.5f ? 0.34f : 0.18f;
        } else if (t < PAIRA) {
            Vector3f de = Cenas.anel(c.estilo, Cenas.CHEGA, p, semente, tempo);
            if (de == null) de = new Vector3f(p);
            float q = Cenas.suave((t - Cenas.CHEGA) / 0.06f);
            pos = de.lerp(new Vector3f(p).add(0, Mth.sin(tempo * 0.12f) * 0.05f, 0), q);
            tam = 0.18f;
        } else if (t < DEDO) {
            float q = Cenas.suave((t - PAIRA) / (DEDO - PAIRA));
            pos = new Vector3f(p).lerp(m, q);
            tam = Mth.lerp(q, 0.18f, 0.07f);
        } else {
            pos = m;
            tam = 0.07f;
        }
        return new float[]{pos.x, pos.y, pos.z, tam};
    }

    // ---------------- desenho ----------------

    @SubscribeEvent
    public static void desenhar(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ATIVAS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        PoseStack ps = e.getPoseStack();
        for (Map.Entry<Integer, Cer> en : ATIVAS.entrySet()) {
            Entity ent = mc.level.getEntity(en.getKey());
            if (!(ent instanceof Player p)) continue;
            Cer c = en.getValue();
            float t = t(c, pt);
            if (t < 0 || t > 1.06f) continue;
            Vec3 pos = p.getPosition(pt);
            if (pos.distanceToSqr(cam) > 160 * 160) continue;
            float ticks = t * c.dur;
            float tempo = mc.level.getGameTime() + pt;
            float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
            int semente = p.getId() * 31 + (int) c.inicio;
            ps.pushPose();
            ps.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
            Geo.buf = buf;
            Geo.ps = ps;
            Geo.cam = new Vector3f((float) (cam.x - pos.x), (float) (cam.y - pos.y), (float) (cam.z - pos.z));
            Geo.tempo = tempo;
            float[] a = anel(c, Math.min(t, 1), yaw, semente, tempo);
            Vector3f anelPos = a == null ? null : new Vector3f(a[0], a[1], a[2]);
            Cenas.ambiente(c.estilo, Math.min(t, 1), c.cor, semente, tempo, ticks, pairar(yaw), anelPos);
            if (a != null && t <= 1.02f) {
                int ca = Cenas.corAnel(c.estilo, c.cor);
                boolean noDedo = t >= DEDO;
                // o anel encara a câmera (balançando um pouco), para ver o desenho dele
                float paraCam = (float) Math.toDegrees(Math.atan2(Geo.cam.x - a[0], Geo.cam.z - a[2]));
                float giro = noDedo ? -yaw : paraCam + 28 * Mth.sin(tempo * 0.045f);
                float incl = noDedo ? 90 : 10 * Mth.sin(tempo * 0.05f);
                Geo.anelModelo(c.corps, a[0], a[1], a[2], a[3], ca, 1f, giro, incl);
                if (c.estilo.equals("black")) Geo.bola(a[0], a[1], a[2], a[3] * 4, 0x7A40B0, 0.4f);
            }
            fim(c, t, yaw);
            ps.popPose();
        }
        buf.endBatch();
    }

    /** O fim, igual para todas: clarão na mão, onda no chão e a luz subindo pelo corpo. */
    private static void fim(Cer c, float t, float yaw) {
        int cor = c.estilo.equals("black") ? 0x7A50B0 : c.cor;
        Vector3f m = mao(yaw);
        float cl = Cenas.faixa(t, DEDO - 0.01f, DEDO + 0.015f) * (1 - Cenas.faixa(t, DEDO + 0.015f, 0.99f));
        if (cl > 0) {
            Geo.bola(m.x, m.y, m.z, 4.5f * cl, cor, cl);
            Geo.bola(m.x, m.y, m.z, 1.6f * cl, 0xFFFFFF, cl);
            Geo.estrela(m.x, m.y, m.z, 3.5f * cl, cor, cl, 0.3f);
        }
        float o = Cenas.faixa(t, DEDO, 1.04f);
        if (o > 0 && o < 1) {
            Geo.onda(o * 10, 0.9f, 0.05f, cor, (1 - o) * 0.9f);
            Geo.onda(o * 7, 0.4f, 0.05f, 0xFFFFFF, (1 - o) * 0.5f);
        }
        float s = Cenas.faixa(t, DEDO + 0.01f, 1.03f);
        if (s > 0 && s < 1) {
            float y = s * 2.2f;
            Geo.tubo(0.5f, Math.max(0, y - 0.35f), y + 0.05f, cor, 0, 0.9f * (1 - s * 0.5f), 0.04f, true);
            Geo.anelChao(0.35f, 0.75f, y, cor, 0.8f * (1 - s * 0.5f), 0, true);
        }
        if (t >= DEDO && t < 1.03f) Geo.bola(m.x, m.y, m.z, 0.45f, cor, 0.7f * (1 - Cenas.faixa(t, 0.98f, 1.03f)));
    }

    // ---------------- braços ----------------

    public static void bracos(HumanoidModel<?> mo, LivingEntity e, float pt) {
        if (ATIVAS.isEmpty() || Minecraft.getInstance().level == null) return;
        Cer c = ATIVAS.get(e.getId());
        if (c == null) return;
        float t = t(c, pt);
        if (t < 0 || t > 1.04f) return;
        float abre = Cenas.suave(t / 0.25f) * (1 - Cenas.faixa(t, 1.0f, 1.04f));
        float ergue = Cenas.suave(Cenas.faixa(t, Cenas.CHEGA + 0.04f, PAIRA + 0.02f)) * (1 - Cenas.faixa(t, 1.0f, 1.04f));
        // braços se abrindo para receber; depois o direito sobe para o anel
        mo.leftArm.xRot = Mth.lerp(abre, mo.leftArm.xRot, -0.25f);
        mo.leftArm.zRot = Mth.lerp(abre, mo.leftArm.zRot, -0.45f);
        mo.rightArm.xRot = Mth.lerp(abre, mo.rightArm.xRot, -0.25f);
        mo.rightArm.zRot = Mth.lerp(abre, mo.rightArm.zRot, 0.45f);
        if (ergue > 0) {
            mo.rightArm.xRot = Mth.lerp(ergue, mo.rightArm.xRot, -2.75f);
            mo.rightArm.yRot = Mth.lerp(ergue, mo.rightArm.yRot, -0.15f);
            mo.rightArm.zRot = Mth.lerp(ergue, mo.rightArm.zRot, -0.05f);
            mo.head.xRot = Mth.lerp(ergue, mo.head.xRot, -0.55f);
            mo.hat.copyFrom(mo.head);
        }
        if (mo instanceof PlayerModel<?> pm) {
            pm.leftSleeve.copyFrom(mo.leftArm);
            pm.rightSleeve.copyFrom(mo.rightArm);
        }
    }

    // ---------------- câmera de cinema (só o escolhido) ----------------

    /**
     * Posição e rotação da câmera: {x, y, z, yaw, pitch}, ou null se não está em cena. vanilla = onde a câmera
     * estaria (para voltar suave no fim).
     */
    public static double[] camera(Entity ent, float pt, Vec3 vanilla, float vYaw, float vPitch) {
        Minecraft mc = Minecraft.getInstance();
        if (ent != mc.player || mc.level == null) return null;
        Cer c = ATIVAS.get(ent.getId());
        if (c == null) return null;
        float t = t(c, pt);
        if (t < 0 || t >= 1.0f) return null;
        Player p = (Player) ent;
        Vec3 pe = p.getPosition(pt);
        float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        float tempo = mc.level.getGameTime() + pt;
        Vector3f fr = frente(yaw), di = direita(yaw);
        // A: dá a volta (de trás-lado para a frente), descendo e aproximando
        float q = Cenas.suave(Math.min(1, t / 0.76f));
        float base = (float) Math.atan2(fr.z, fr.x);
        float ang = base + Mth.lerp(q, 2.7f, 0.55f);
        float dist = Mth.lerp(q, 7.5f, 4.2f), alt = Mth.lerp(q, 4.2f, 1.9f);
        Vector3f posA = new Vector3f(Mth.cos(ang) * dist, alt, Mth.sin(ang) * dist);
        Vector3f alvoA = new Vector3f(0, 1.35f, 0);
        int semente = p.getId() * 31 + (int) c.inicio;
        float[] a = anel(c, Math.min(t, 1), yaw, semente, tempo);
        if (a != null && t < Cenas.CHEGA) {
            Vector3f an = new Vector3f(a[0], a[1], a[2]);
            float olhar = an.length() > 30 ? 0.25f : 0.4f;
            alvoA.lerp(an, olhar);
        }
        // B: close da mão recebendo o anel
        Vector3f m = mao(yaw);
        Vector3f posB = new Vector3f(fr).mul(2.0f).add(new Vector3f(di).mul(1.05f)).add(0, 1.85f, 0);
        Vector3f alvoB = new Vector3f(m).lerp(pairar(yaw), 0.35f);
        float b = Cenas.suave(Cenas.faixa(t, 0.74f, 0.82f));
        Vector3f pos = new Vector3f(posA).lerp(posB, b), alvo = new Vector3f(alvoA).lerp(alvoB, b);
        // respiração da câmera
        pos.add(Mth.sin(tempo * 0.031f) * 0.05f, Mth.sin(tempo * 0.043f) * 0.04f, 0);
        double x = pe.x + pos.x, y = pe.y + pos.y, z = pe.z + pos.z;
        double dx = pe.x + alvo.x - x, dy = pe.y + alvo.y - y, dz = pe.z + alvo.z - z;
        float yawC = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float pitchC = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        // C: volta para a câmera normal
        // fim: corta seco para a câmera normal (passar pelo meio atravessava a cabeça)
        if (t >= 1.0f) return null;
        float volta = 0;
        if (volta > 0) {
            x = Mth.lerp(volta, x, vanilla.x);
            y = Mth.lerp(volta, y, vanilla.y);
            z = Mth.lerp(volta, z, vanilla.z);
            yawC = yawC + Mth.wrapDegrees(vYaw - yawC) * volta;
            pitchC = Mth.lerp(volta, pitchC, vPitch);
        }
        return new double[]{x, y, z, yawC, pitchC};
    }

    @SubscribeEvent
    public static void tremor(ViewportEvent.ComputeCameraAngles e) {
        Cer c = daLocal();
        if (c == null) return;
        float t = t(c, (float) e.getPartialTick());
        if (t < 0 || t > 1.03f) return;
        float forca = 0;
        float ticks = t * c.dur;
        if (c.estilo.equals("red")) forca = 0.5f * t;
        if (c.estilo.equals("yellow") && (ticks % 11) < 3) forca = 1.2f;
        float imp = Cenas.faixa(t, DEDO, DEDO + 0.04f);
        if (imp > 0 && imp < 1) forca += 2.2f * (1 - imp);
        if (forca <= 0) return;
        var r = Minecraft.getInstance().player.getRandom();
        e.setYaw(e.getYaw() + (r.nextFloat() - 0.5f) * forca);
        e.setPitch(e.getPitch() + (r.nextFloat() - 0.5f) * forca);
        e.setRoll(e.getRoll() + (r.nextFloat() - 0.5f) * forca * 0.5f);
    }

    /** Clima do ritual: escuro no medo e na morte, avermelhado na fúria, claro na vida. */
    private static float clima(Cer c, float t) {
        return Cenas.faixa(t, 0, 0.15f) * (1 - Cenas.faixa(t, 0.9f, 1.02f));
    }

    @SubscribeEvent
    public static void neblina(ViewportEvent.RenderFog e) {
        Cer c = daLocal();
        if (c == null) return;
        float t = t(c, (float) e.getPartialTick());
        if (t < 0 || t > 1.02f) return;
        float k = clima(c, t);
        float longe = switch (c.estilo) {
            case "yellow" -> 9f;
            case "black" -> 15f;
            case "red" -> 26f;
            case "white" -> 30f;
            default -> -1;
        };
        if (longe < 0 || k <= 0) return;
        e.setFarPlaneDistance(Mth.lerp(k, e.getFarPlaneDistance(), longe));
        e.setNearPlaneDistance(Mth.lerp(k, e.getNearPlaneDistance(), longe * 0.05f));
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void corNeblina(ViewportEvent.ComputeFogColor e) {
        Cer c = daLocal();
        if (c == null) return;
        float t = t(c, (float) e.getPartialTick());
        if (t < 0 || t > 1.02f) return;
        float k = clima(c, t);
        float[] alvo = switch (c.estilo) {
            case "yellow" -> new float[]{0.03f, 0.025f, 0.0f};
            case "black" -> new float[]{0.06f, 0.02f, 0.09f};
            case "red" -> new float[]{0.35f, 0.03f, 0.02f};
            case "white" -> new float[]{1f, 1f, 1f};
            case "blue" -> new float[]{0.2f, 0.3f, 0.55f};
            case "violet" -> new float[]{0.75f, 0.45f, 0.8f};
            case "indigo" -> new float[]{0.3f, 0.25f, 0.6f};
            case "orange" -> new float[]{0.85f, 0.55f, 0.2f};
            default -> null;
        };
        if (alvo == null) return;
        float f = c.estilo.equals("yellow") || c.estilo.equals("black") ? k : k * 0.6f;
        e.setRed(Mth.lerp(f, e.getRed(), alvo[0]));
        e.setGreen(Mth.lerp(f, e.getGreen(), alvo[1]));
        e.setBlue(Mth.lerp(f, e.getBlue(), alvo[2]));
    }

    @SubscribeEvent
    public static void semMao(RenderHandEvent e) {
        if (emCena()) e.setCanceled(true);
    }

    private Cerimonias() {}
}
