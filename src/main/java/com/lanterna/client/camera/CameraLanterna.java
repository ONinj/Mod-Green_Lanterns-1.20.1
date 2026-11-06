package com.lanterna.client.camera;

import com.lanterna.LanternaMod;
import com.lanterna.client.ClientRingData;
import com.lanterna.client.hud.Hud;
import com.lanterna.naves.NaveEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Terceira pessoa melhorada: a câmera fica por cima do ombro (não atrás da cabeça), um pouco mais perto, com o
 * movimento suavizado; afasta sozinha quando voa rápido e se encolhe quando bate em parede. A mira aparece onde o
 * olhar do personagem realmente acerta (é para lá que os poderes do anel vão).
 *
 *   F6           liga/desliga (ligada já entra na terceira pessoa)
 *   F7           troca o ombro
 *   Alt + roda   aproxima/afasta
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CameraLanterna {
    public static boolean ligada = true;
    /** 1 = ombro direito, -1 = esquerdo. */
    public static int ombro = 1;
    public static float distanciaBase = 3.2f;

    private static float lado = 0.75f, dist = 3.4f, alturaSuave = 0;
    private static long ultimo;
    /** Última câmera aplicada (para a mira). */
    public static boolean aplicada;

    static {
        carregar();
    }

    public static void alternar() {
        Minecraft mc = Minecraft.getInstance();
        ligada = !ligada;
        if (ligada && mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (mc.player != null) mc.player.displayClientMessage(Component.translatable(ligada ? "camera.lanterna.ligada" : "camera.lanterna.desligada"), true);
        salvar();
    }

    public static void trocarOmbro() {
        ombro = -ombro;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && ligada) mc.player.displayClientMessage(Component.translatable(ombro > 0 ? "camera.lanterna.ombro_direito" : "camera.lanterna.ombro_esquerdo"), true);
        salvar();
    }

    /** Vale para esta câmera? */
    public static boolean ativa(Entity e, boolean detached, boolean mirror) {
        Minecraft mc = Minecraft.getInstance();
        return ligada && detached && !mirror && e == mc.player && e instanceof LivingEntity le && !le.isSleeping()
                && !(e.getVehicle() instanceof NaveEntity) && !e.isSpectator();
    }

    /**
     * Calcula a nova posição da câmera. A câmera já está com a rotação do jogador; olho = posição do olho.
     */
    public static Vec3 posicionar(BlockGetter level, Entity e, Vec3 olho, Vector3f frente, Vector3f esquerda, Vector3f cima) {
        long agora = System.nanoTime();
        float dt = ultimo == 0 ? 0.016f : Mth.clamp((agora - ultimo) / 1e9f, 0.001f, 0.1f);
        ultimo = agora;
        float k = 1 - (float) Math.exp(-dt * 9);
        // afasta com a velocidade (voando rápido a câmera "fica para trás")
        Vec3 v = e.getDeltaMovement();
        float vel = (float) v.length();
        boolean voando = ClientRingData.flight || (e instanceof net.minecraft.world.entity.player.Player p && p.getAbilities().flying) || (e instanceof LivingEntity le2 && le2.isFallFlying());
        float alvoDist = distanciaBase + Math.min(3.5f, vel * (voando ? 2.6f : 1.2f));
        float alvoLado = ombro * (voando ? 0.85f : 1.05f);
        float alvoAlt = e.isCrouching() ? 0.05f : 0.3f;
        dist += (alvoDist - dist) * (alvoDist > dist ? k * 0.35f : k);
        lado += (alvoLado - lado) * k;
        alturaSuave += (alvoAlt - alturaSuave) * k;
        // ombro → para trás; cada trecho testa colisão
        Vec3 dir = new Vec3(-esquerda.x(), -esquerda.y(), -esquerda.z()); // direita
        Vec3 up = new Vec3(cima.x(), cima.y(), cima.z());
        Vec3 ombroPos = colisao(level, e, olho, olho.add(dir.scale(lado)).add(up.scale(alturaSuave)));
        Vec3 tras = new Vec3(-frente.x(), -frente.y(), -frente.z());
        return colisao(level, e, ombroPos, ombroPos.add(tras.scale(dist)));
    }

    private static Vec3 colisao(BlockGetter level, Entity e, Vec3 de, Vec3 ate) {
        Vec3 d = ate.subtract(de);
        double len = d.length();
        if (len < 1e-4) return ate;
        Vec3 n = d.scale(1 / len);
        double melhor = len;
        // quatro raios (cantos de uma caixinha) para a câmera não atravessar quinas
        for (int i = 0; i < 4; i++) {
            double ox = (i & 1) == 0 ? -0.1 : 0.1, oz = (i & 2) == 0 ? -0.1 : 0.1;
            Vec3 a = de.add(ox, (i == 0 || i == 3) ? 0.08 : -0.08, oz);
            Vec3 b = a.add(d);
            HitResult h = level.clip(new ClipContext(a, b, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, e));
            if (h.getType() != HitResult.Type.MISS) melhor = Math.min(melhor, h.getLocation().distanceTo(a) - 0.12);
        }
        return de.add(n.scale(Math.max(0, melhor)));
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent ev) {
        Minecraft mc = Minecraft.getInstance();
        if (!ligada || mc.screen != null || mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK || !Screen.hasAltDown()) return;
        distanciaBase = Mth.clamp(distanciaBase - (float) ev.getScrollDelta() * 0.4f, 1.6f, 9f);
        ev.setCanceled(true);
        salvar();
    }

    // ===================== Mira =====================

    public static final IGuiOverlay MIRA = (gui, g, pt, w, h) -> mira(g, w, h, pt);

    private static void mira(GuiGraphics g, int w, int h, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (!aplicada || mc.player == null || mc.options.hideGui || mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK || mc.screen != null) return;
        var cam = mc.gameRenderer.getMainCamera();
        Entity p = mc.player;
        Vec3 olho = p.getEyePosition(pt);
        Vec3 olhar = p.getViewVector(pt);
        HitResult hit = mc.level.clip(new ClipContext(olho, olho.add(olhar.scale(96)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        Vec3 alvo = hit.getType() == HitResult.Type.MISS ? olho.add(olhar.scale(96)) : hit.getLocation();
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) alvo = mc.hitResult.getLocation();
        Vec3 rel = alvo.subtract(cam.getPosition());
        Vector3f f = cam.getLookVector(), l = cam.getLeftVector(), u = cam.getUpVector();
        double z = rel.x * f.x() + rel.y * f.y() + rel.z * f.z();
        if (z < 0.1) return;
        double x = rel.x * l.x() + rel.y * l.y() + rel.z * l.z();
        double y = rel.x * u.x() + rel.y * u.y() + rel.z * u.z();
        double fov = mc.options.fov().get() * Mth.lerp(pt, 1f, mc.player.getFieldOfViewModifier());
        double esc = (h / 2.0) / Math.tan(Math.toRadians(fov / 2));
        int cx = (int) Math.round(w / 2.0 - x / z * esc), cy = (int) Math.round(h / 2.0 - y / z * esc);
        int cor = ClientRingData.hasRing ? ClientRingData.color : 0xFFFFFF;
        boolean ent = mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY;
        int c = ent ? 0xFFFF5050 : 0xF0FFFFFF, sombra = 0xA0000000;
        int centro = Hud.argb(ent ? 0xFF5050 : cor, 255);
        // retícula: ponto + quatro tracinhos
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, sombra);
        g.fill(cx, cy, cx + 1, cy + 1, centro);
        int a = ent ? 3 : 4;
        for (int[] s : new int[][]{{-a - 4, 0, -a, 1}, {a + 1, 0, a + 5, 1}, {0, -a - 4, 1, -a}, {0, a + 1, 1, a + 5}}) {
            g.fill(cx + s[0] - 1, cy + s[1] - 1, cx + s[2] + 1, cy + s[3] + 1, sombra);
            g.fill(cx + s[0], cy + s[1], cx + s[2], cy + s[3], c);
        }
    }

    // ===================== Preferências =====================

    private static Path arquivo() {
        return FMLPaths.CONFIGDIR.get().resolve("lanterna-camera.txt");
    }

    private static void carregar() {
        try {
            Path f = arquivo();
            if (!Files.exists(f)) return;
            for (String linha : Files.readAllLines(f)) {
                String[] kv = linha.split("=", 2);
                if (kv.length < 2) continue;
                switch (kv[0].trim()) {
                    case "ligada" -> ligada = Boolean.parseBoolean(kv[1].trim());
                    case "ombro" -> ombro = Integer.parseInt(kv[1].trim()) < 0 ? -1 : 1;
                    case "distancia" -> distanciaBase = Mth.clamp(Float.parseFloat(kv[1].trim()), 1.6f, 9f);
                    default -> {}
                }
            }
        } catch (Exception ignored) {}
    }

    private static void salvar() {
        try {
            Files.writeString(arquivo(), "ligada=" + ligada + "\nombro=" + ombro + "\ndistancia=" + distanciaBase + "\n");
        } catch (Exception ignored) {}
    }

    private CameraLanterna() {}
}
