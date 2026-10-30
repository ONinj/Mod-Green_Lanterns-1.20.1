package com.lanterna.client.combat;

import com.lanterna.client.ClientRingData;
import com.lanterna.client.KeyBindings;
import com.lanterna.client.mission.Waypoints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.jetbrains.annotations.Nullable;

/**
 * Trava de mira: aperte a tecla (Tab) e a câmera fica presa no inimigo mais perto do centro da tela.
 * A câmera segue o alvo suavemente (dá para brigar com o mouse), aparece uma mira em volta dele com nome e vida.
 * Solta ao apertar de novo, quando o alvo morre, fica longe demais ou some de vista por um tempo.
 */
public final class MiraTravada {
    private static final double ALCANCE = 48, SOLTA = 64;
    @Nullable private static Entity alvo;
    private static int semVer;
    private static long ultimoFrame;

    @Nullable
    public static Entity alvo() {
        return alvo;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { alvo = null; return; }
        while (KeyBindings.TRAVAR_MIRA.consumeClick()) {
            if (alvo != null) soltar(true);
            else escolher(mc);
        }
        if (alvo == null) return;
        if (!alvo.isAlive() || alvo.isRemoved() || alvo.level() != mc.level || alvo.distanceTo(mc.player) > SOLTA) {
            soltar(false);
            return;
        }
        if (!mc.player.hasLineOfSight(alvo)) {
            if (++semVer > 60) soltar(false);
        } else semVer = 0;
    }

    private static void escolher(Minecraft mc) {
        Player p = mc.player;
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1f);
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == p || !valido(e, p) || e.distanceTo(p) > ALCANCE) continue;
            Vec3 to = centro(e, 1f).subtract(eye);
            double d = to.length();
            double cos = to.dot(look) / Math.max(1e-4, d);
            if (cos < Math.cos(Math.toRadians(35))) continue;
            if (!p.hasLineOfSight(e) && !(e instanceof com.lanterna.naves.NaveEntity)) continue;
            double score = (1 - cos) * 60 + d * 0.15;
            if (score < bestScore) { bestScore = score; best = e; }
        }
        if (best == null) {
            p.displayClientMessage(Component.translatable("mira.lanterna.sem_alvo"), true);
            return;
        }
        alvo = best;
        semVer = 0;
        p.playSound(SoundEvents.NOTE_BLOCK_BIT.value(), 0.6f, 1.8f);
    }

    private static boolean valido(Entity e, Player p) {
        if (e.isSpectator() || e.isInvisible() || !e.isAlive()) return false;
        if (e == p.getVehicle() || p.hasPassenger(e) || e.isPassengerOfSameVehicle(p)) return false;
        if (e instanceof com.lanterna.naves.NaveEntity n) return !n.ia().isEmpty() || n.getControllingPassenger() instanceof Player;
        if (e instanceof net.minecraft.world.entity.TamableAnimal t && p.getUUID().equals(t.getOwnerUUID())) return false;
        return e instanceof Mob || e instanceof Player;
    }

    private static void soltar(boolean som) {
        alvo = null;
        Minecraft mc = Minecraft.getInstance();
        if (som && mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_BIT.value(), 0.5f, 1.2f);
    }

    private static Vec3 centro(Entity e, float pt) {
        return e.getPosition(pt).add(0, e.getBbHeight() * 0.6, 0);
    }

    /** A cada quadro: a câmera vira suavemente para o alvo (como o mouse, sem tranco). */
    public static void frame(float pt) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float dt = ultimoFrame == 0 ? 0.016f : Math.min(0.1f, (now - ultimoFrame) / 1.0e9f);
        ultimoFrame = now;
        if (alvo == null || mc.player == null || mc.isPaused() || mc.screen != null) return;
        var p = mc.player;
        Vec3 to = centro(alvo, pt).subtract(p.getEyePosition(pt));
        double h = Math.sqrt(to.x * to.x + to.z * to.z);
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(to.y, h) * Mth.RAD_TO_DEG);
        float k = 1f - (float) Math.exp(-dt * 10.0);
        float dy = Mth.wrapDegrees(yaw - p.getYRot()) * k;
        float dp = (pitch - p.getXRot()) * k;
        p.setYRot(p.getYRot() + dy);
        p.yRotO += dy;
        p.setXRot(Mth.clamp(p.getXRot() + dp, -90, 90));
        p.xRotO += dp;
    }

    /** Mira em volta do alvo, nome e vida. */
    public static final IGuiOverlay HUD = (gui, g, pt, w, h) -> {
        Minecraft mc = Minecraft.getInstance();
        if (alvo == null || mc.options.hideGui || mc.player == null) return;
        float[] s = Waypoints.project(centro(alvo, pt), w, h);
        if (s == null || s[2] > 0) return;
        int x = Math.round(s[0]), y = Math.round(s[1]);
        double d = alvo.distanceTo(mc.player);
        int r = (int) Mth.clamp(14 + alvo.getBbHeight() * 70 / Math.max(2, d), 12, 70);
        int c = 0xFF000000 | (ClientRingData.hasRing ? ClientRingData.color : 0xFF4040);
        float spin = (mc.level.getGameTime() + pt) * 4f;
        cantos(g, x, y, r, c, spin);
        g.fill(x - 1, y - 1, x + 2, y + 2, c);
        Component nome = alvo.getDisplayName();
        g.drawString(mc.font, nome, x - mc.font.width(nome) / 2, y + r + 4, 0xFFFFFFFF, true);
        if (alvo instanceof LivingEntity le) {
            float vida = Mth.clamp(le.getHealth() / Math.max(1, le.getMaxHealth()), 0, 1);
            int bw = 40;
            g.fill(x - bw / 2 - 1, y + r + 14, x + bw / 2 + 1, y + r + 18, 0xAA000000);
            g.fill(x - bw / 2, y + r + 15, x - bw / 2 + Math.round(bw * vida), y + r + 17, vida > 0.3f ? 0xFF50E060 : 0xFFE04040);
        }
        String dist = Math.round(d) + " m";
        g.drawString(mc.font, dist, x - mc.font.width(dist) / 2, y - r - 11, 0xFFB8C0D0, true);
    };

    /** Quatro cantos girando devagar em volta do alvo. */
    private static void cantos(GuiGraphics g, int x, int y, int r, int c, float spin) {
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(spin + i * 90);
            int cx = x + (int) Math.round(Math.cos(a) * r), cy = y + (int) Math.round(Math.sin(a) * r);
            double t = a + Math.PI / 2;
            for (int k = -5; k <= 5; k++) {
                int px = cx + (int) Math.round(Math.cos(t) * k), py = cy + (int) Math.round(Math.sin(t) * k);
                g.fill(px, py, px + 2, py + 2, c);
            }
            for (int k = 1; k <= 4; k++) {
                int px = cx - (int) Math.round(Math.cos(a) * k), py = cy - (int) Math.round(Math.sin(a) * k);
                g.fill(px, py, px + 2, py + 2, c);
            }
        }
    }

    public static void clear() {
        alvo = null;
    }

    private MiraTravada() {}
}
