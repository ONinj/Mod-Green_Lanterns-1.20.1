package com.lanterna.client.vida;

import com.lanterna.LanternaMod;
import com.lanterna.client.ClientVisibleRings;
import com.lanterna.particle.GlowOrbOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * 0.53.1: as cenas do Anel da Vida, vistas por todos que estão perto (e, para quem passa por ela, com câmera de cinema):
 *
 *  - TREINO (trocar o aspecto treinado): uma espiral da nova cor sobe pelo corpo, um clarão, e o traje se refaz na nova
 *    cor como líquido escorrendo (a mesma frente acesa da transformação), fechando com uma onda no chão.
 *  - SEGUNDO ANEL (colocar um segundo anel): faíscas da cor dele vêm de todos os lados e se juntam na outra mão, o anel
 *    aparece num clarão, metade do traje se refaz, e as duas luzes sobem trançadas em volta do corpo.
 *
 * As cenas começam sozinhas quando o anel visível do jogador muda (traje do treino ou segundo anel), então funcionam
 * igual em servidor, para quem vê de fora.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CenaEmocoes {
    public enum Tipo { TREINO, SEGUNDO }

    private record Cena(Tipo tipo, int cor, int cor2, long inicio, float yaw) {}

    public static final int DUR_TREINO = 76, DUR_SEGUNDO = 100;
    /** Quando, na cena do segundo anel, o anel aparece no dedo. */
    public static final int APARECE = 44;

    private static final Map<Integer, Cena> CENAS = new HashMap<>();
    private static final Map<Integer, String> ULTIMO = new HashMap<>();

    private static long agora() {
        var mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    /** Começa uma cena (também usado pelos testes). */
    public static void comecar(Player p, Tipo tipo) {
        var v = ClientVisibleRings.of(p.getId());
        int cor = tipo == Tipo.TREINO && v.traje().isEmpty() ? com.lanterna.corps.BuiltinCorps.WHITE.color() : v.color();
        CENAS.put(p.getId(), new Cena(tipo, cor, v.segundoCor(), agora(), p.yBodyRot));
        // 0.59.1: quem passa pela cena sai de qualquer menu (o do anel, o inventário...) para ver a cena
        var mc = Minecraft.getInstance();
        if (p == mc.player && mc.screen != null) {
            if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.player.closeContainer();
            else mc.setScreen(null);
        }
        // treino: o traje antigo se desfaz no clarão do começo e o novo escorre pelo corpo durante a espiral
        if (tipo == Tipo.TREINO) com.lanterna.client.uniform.UniformTransition.refazer(p.getId());
    }

    // ===================== consultas =====================

    /** Na cena do segundo anel, antes do clarão, o segundo anel ainda não está no dedo. */
    public static boolean escondeSegundo(int id) {
        Cena c = CENAS.get(id);
        return c != null && c.tipo == Tipo.SEGUNDO && agora() - c.inicio < APARECE;
    }

    /** Tamanho do segundo anel aparecendo (0..1, com um pulo). */
    public static float escalaSegundo(int id, float pt) {
        Cena c = CENAS.get(id);
        if (c == null || c.tipo != Tipo.SEGUNDO) return 1f;
        float t = agora() - c.inicio + pt - APARECE;
        if (t < 0) return 0f;
        if (t > 10) return 1f;
        float k = t / 10f;   // cresce passando um pouco do tamanho e volta
        return k < 0.6f ? k / 0.6f * 1.35f : 1.35f - (k - 0.6f) / 0.4f * 0.35f;
    }

    /** O jogador deste cliente está numa cena (câmera de cinema, sem painéis). */
    public static boolean ativa() {
        var mc = Minecraft.getInstance();
        return mc.player != null && CENAS.containsKey(mc.player.getId());
    }

    // ===================== tick =====================

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { CENAS.clear(); ULTIMO.clear(); return; }
        for (Player p : mc.level.players()) {
            var v = ClientVisibleRings.of(p.getId());
            String chave = v.ring().isEmpty() ? null : v.traje() + "|" + v.segundo();
            String antes = ULTIMO.put(p.getId(), chave);
            // só quando o anel já estava no dedo (entrar no mundo ou colocar o anel não conta). 0.59.1: os dados do
            // segundo anel chegam um instante depois dos do primeiro ao entrar no mundo (ou quando outro jogador entra
            // na vista); nos primeiros 5 s da entidade no cliente nada dispara a cena
            if (p.tickCount < 100) continue;
            if (antes != null && chave != null && !antes.equals(chave)) {
                String[] a = antes.split("\\|", -1), n = chave.split("\\|", -1);
                if (!n[1].isEmpty() && !n[1].equals(a[1])) comecar(p, Tipo.SEGUNDO);
                else if (!n[0].equals(a[0])) comecar(p, Tipo.TREINO);
            }
        }
        long t0 = agora();
        CENAS.entrySet().removeIf(en -> {
            var ent = mc.level.getEntity(en.getKey());
            Cena c = en.getValue();
            int t = (int) (t0 - c.inicio);
            if (!(ent instanceof Player p) || t > (c.tipo == Tipo.TREINO ? DUR_TREINO : DUR_SEGUNDO)) return true;
            if (c.tipo == Tipo.TREINO) treino(p, c, t);
            else segundo(p, c, t);
            return false;
        });
    }

    private static void som(Player p, SoundEvent s, float vol, float tom) {
        p.level().playLocalSound(p.getX(), p.getY() + 1, p.getZ(), s, SoundSource.PLAYERS, vol, tom, false);
    }

    private static void orb(Player p, int cor, float tam, double x, double y, double z, double vx, double vy, double vz) {
        p.level().addParticle(GlowOrbOptions.of(cor, tam), x, y, z, vx, vy, vz);
    }

    private static int clarear(int c, float f) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return ((int) (r + (255 - r) * f) << 16) | ((int) (g + (255 - g) * f) << 8) | (int) (b + (255 - b) * f);
    }

    /** Onde fica a mão do segundo anel (a que não é a principal): erguida à frente do peito durante a cena. */
    private static Vec3 maoSegunda(Player p) {
        float yaw = (float) Math.toRadians(p.yBodyRot);
        double lado = p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1; // o segundo anel fica na outra mão
        // esquerda do corpo = +x quando olha para +z; frente = (-sen, cos)
        double sx = Math.cos(yaw) * lado, sz = Math.sin(yaw) * lado, fx = -Math.sin(yaw), fz = Math.cos(yaw);
        return new Vec3(p.getX() + sx * 0.2 + fx * 0.62, p.getY() + 1.3, p.getZ() + sz * 0.2 + fz * 0.62);
    }

    private static void onda(Player p, int cor, int n, double vel) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            orb(p, cor, 0.16f, p.getX(), p.getY() + 0.08, p.getZ(), Math.cos(a) * vel, 0.005, Math.sin(a) * vel);
        }
    }

    private static void treino(Player p, Cena c, int t) {
        int cor = c.cor, luz = clarear(cor, 0.45f);
        if (t == 0) {
            som(p, SoundEvents.BEACON_POWER_SELECT, 0.9f, 0.7f);
            som(p, SoundEvents.ILLUSIONER_CAST_SPELL, 0.8f, 1.3f);
            for (int i = 0; i < 60; i++) {
                double a = p.getRandom().nextDouble() * Math.PI * 2, b = (p.getRandom().nextDouble() - 0.5) * Math.PI;
                double v = 0.16 + p.getRandom().nextDouble() * 0.12;
                orb(p, i % 3 == 0 ? 0xFFFFFF : luz, 0.14f, p.getX(), p.getY() + 1.0, p.getZ(),
                        Math.cos(a) * Math.cos(b) * v, Math.sin(b) * v, Math.sin(a) * Math.cos(b) * v);
            }
        }
        if (t < 18) {
            // espiral dupla subindo e apertando em volta do corpo
            for (int k = 0; k < 2; k++) {
                double a = t * 0.55 + k * Math.PI, r = 1.1 - t * 0.035, y = p.getY() + t / 18.0 * 2.1;
                orb(p, k == 0 ? cor : luz, 0.13f, p.getX() + Math.cos(a) * r, y, p.getZ() + Math.sin(a) * r, 0, 0.01, 0);
                orb(p, cor, 0.07f, p.getX() + Math.cos(a + 0.4) * r, y - 0.15, p.getZ() + Math.sin(a + 0.4) * r, 0, -0.02, 0);
            }
            if (t % 6 == 0) som(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5f, 0.8f + t * 0.04f);
        }
        if (t == 18) {
            // o clarão: o traje se desfaz e começa a se refazer na nova cor
            som(p, SoundEvents.ARMOR_EQUIP_NETHERITE, 1f, 1.1f);
            som(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 0.6f);
            for (int i = 0; i < 40; i++) {
                double a = p.getRandom().nextDouble() * Math.PI * 2, b = (p.getRandom().nextDouble() - 0.5) * Math.PI;
                double v = 0.18 + p.getRandom().nextDouble() * 0.12;
                orb(p, i % 3 == 0 ? 0xFFFFFF : luz, 0.15f, p.getX(), p.getY() + 1.0, p.getZ(),
                        Math.cos(a) * Math.cos(b) * v, Math.sin(b) * v, Math.sin(a) * Math.cos(b) * v);
            }
        }
        if (t > 18 && t < 56 && t % 2 == 0) {
            // faíscas subindo junto da frente do traje novo
            double y = p.getY() + (t - 18) / 38.0 * 2.0;
            for (int i = 0; i < 3; i++) {
                double a = p.getRandom().nextDouble() * Math.PI * 2;
                orb(p, luz, 0.09f, p.getX() + Math.cos(a) * 0.45, y, p.getZ() + Math.sin(a) * 0.45, Math.cos(a) * 0.03, 0.02, Math.sin(a) * 0.03);
            }
        }
        if (t == 58) {
            som(p, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.5f);
            onda(p, cor, 48, 0.32);
            onda(p, luz, 32, 0.2);
        }
    }

    private static void segundo(Player p, Cena c, int t) {
        int c1 = c.cor, c2 = c.cor2 == 0 ? 0xFFFFFF : c.cor2, l2 = clarear(c2, 0.5f);
        Vec3 mao = maoSegunda(p);
        if (t == 0) { som(p, SoundEvents.BEACON_POWER_SELECT, 1f, 0.55f); som(p, SoundEvents.PORTAL_TRIGGER, 0.25f, 1.8f); }
        if (t < APARECE) {
            // faíscas da cor do segundo anel vindo de longe em espiral e se juntando na mão (cada vez mais)
            int n = 1 + t / 7;
            for (int i = 0; i < n; i++) {
                double a = p.getRandom().nextDouble() * Math.PI * 2, b = (p.getRandom().nextDouble() - 0.3) * Math.PI * 0.8;
                double r = 2.4 + p.getRandom().nextDouble() * 1.4;
                double dx = Math.cos(a) * Math.cos(b), dy = Math.sin(b), dz = Math.sin(a) * Math.cos(b);
                double x = mao.x + dx * r, y = mao.y + dy * r, z = mao.z + dz * r;
                double k = 0.1, gira = 0.06;   // puxada para a mão + um pouco de lado (espiral)
                orb(p, i % 2 == 0 ? c2 : l2, 0.09f + p.getRandom().nextFloat() * 0.05f, x, y, z,
                        (mao.x - x) * k - dz * gira, (mao.y - y) * k, (mao.z - z) * k + dx * gira);
            }
            // nos últimos instantes, um aro de luz se fecha em volta do dedo
            if (t > APARECE - 16) {
                double raio = 0.75 * (APARECE - t) / 16.0 + 0.04;
                float yaw = (float) Math.toRadians(p.yBodyRot);
                double fx = -Math.sin(yaw), fz = Math.cos(yaw), sx = Math.cos(yaw), sz = Math.sin(yaw);
                for (int i = 0; i < 14; i++) {
                    double a = i * Math.PI * 2 / 14 + t * 0.3;
                    // o aro fica de pé, de frente para o jogador (plano lado/altura)
                    orb(p, i % 3 == 0 ? 0xFFFFFF : c2, 0.07f, mao.x + sx * Math.cos(a) * raio + fx * 0.02, mao.y + Math.sin(a) * raio,
                            mao.z + sz * Math.cos(a) * raio + fz * 0.02, 0, 0, 0);
                }
            }
            // um núcleo pulsando na mão
            if (t % 2 == 0) orb(p, 0xFFFFFF, 0.06f + t * 0.004f, mao.x, mao.y, mao.z, 0, 0, 0);
            if (t % 8 == 0) som(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 0.6f + t * 0.025f);
        }
        if (t == APARECE) {
            // o anel aparece no dedo num clarão; metade do traje se refaz
            com.lanterna.client.uniform.UniformTransition.refazer(p.getId());
            som(p, SoundEvents.TOTEM_USE, 0.45f, 1.6f);
            som(p, SoundEvents.ARMOR_EQUIP_NETHERITE, 1f, 0.9f);
            som(p, SoundEvents.BEACON_ACTIVATE, 0.7f, 1.9f);
            for (int i = 0; i < 80; i++) {
                double a = p.getRandom().nextDouble() * Math.PI * 2, b = (p.getRandom().nextDouble() - 0.5) * Math.PI;
                double v = 0.12 + p.getRandom().nextDouble() * 0.2;
                orb(p, i % 4 == 0 ? 0xFFFFFF : c2, 0.13f, mao.x, mao.y, mao.z, Math.cos(a) * Math.cos(b) * v, Math.sin(b) * v, Math.sin(a) * Math.cos(b) * v);
            }
        }
        if (t > APARECE && t < 90) {
            // as duas luzes sobem trançadas em volta do corpo
            double s = (t - APARECE) / 46.0;
            for (int k = 0; k < 2; k++) {
                double a = t * 0.42 + k * Math.PI, r = 0.85 + 0.15 * Math.sin(t * 0.3);
                double y = p.getY() + 0.1 + (s * 2.2) % 2.2;
                orb(p, k == 0 ? c1 : c2, 0.12f, p.getX() + Math.cos(a) * r, y, p.getZ() + Math.sin(a) * r, 0, 0.015, 0);
            }
        }
        if (t == 90) {
            som(p, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.2f);
            onda(p, c1, 40, 0.3);
            onda(p, c2, 40, 0.24);
        }
    }

    // ===================== câmera e controles =====================

    /** Câmera de cinema para quem passa pela cena: {x, y, z, yaw, pitch} ou null. */
    public static double[] camera(net.minecraft.world.entity.Entity e, float pt) {
        var mc = Minecraft.getInstance();
        if (e != mc.player) return null;
        Cena c = CENAS.get(e.getId());
        if (c == null) return null;
        double t = agora() - c.inicio + pt;
        double dur = c.tipo == Tipo.TREINO ? DUR_TREINO : DUR_SEGUNDO;
        double s = Math.min(1, t / dur);
        double ease = s * s * (3 - 2 * s);
        double th, r, alt, cy;
        if (c.tipo == Tipo.TREINO) {
            // de frente, girando devagar e afastando um pouco
            th = Math.toRadians(-35 + 95 * ease);
            r = 2.6 + 0.9 * ease;
            alt = 0.35 + 0.4 * Math.sin(s * Math.PI);
            cy = 1.0;
        } else {
            // começa perto da outra mão e abre para o corpo inteiro
            double lado = mc.player.getMainArm() == HumanoidArm.RIGHT ? -1 : 1;
            th = Math.toRadians(lado * (75 - 95 * ease));
            r = 1.7 + 1.9 * ease;
            alt = 0.1 + 0.5 * ease;
            cy = 0.75 + 0.3 * ease;
        }
        // th = 0: de frente para o jogador
        double yaw = Math.toRadians(c.yaw) + th;
        double cx = Mth.lerp(pt, e.xo, e.getX()), cz = Mth.lerp(pt, e.zo, e.getZ()), ccy = Mth.lerp(pt, e.yo, e.getY()) + cy;
        double x = cx - Math.sin(yaw) * r, z = cz + Math.cos(yaw) * r, y = ccy + alt;
        double dx = cx - x, dy = ccy - y, dz = cz - z;
        return new double[]{x, y, z, Math.toDegrees(Math.atan2(-dx, dz)), Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))};
    }

    /** Durante a cena o jogador fica parado. */
    @SubscribeEvent
    public static void parado(MovementInputUpdateEvent e) {
        if (!ativa()) return;
        var in = e.getInput();
        in.forwardImpulse = 0; in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = in.jumping = in.shiftKeyDown = false;
    }

    /** 0.59.1: a câmera é de fora: a mão de primeira pessoa não aparece. */
    @SubscribeEvent
    public static void semMao(net.minecraftforge.client.event.RenderHandEvent e) {
        if (ativa()) e.setCanceled(true);
    }

    private static float suave(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }

    /**
     * 0.59.1: os braços na cena. Segundo anel: a mão sobe à frente do peito, treme enquanto a luz se junta, dá um tranco
     * para cima no clarão e fecha o punho, desce devagar com as luzes trançando e os dois braços se abrem na onda final.
     * Treino: os braços se abrem aos poucos, palmas para fora, e voltam no fim.
     */
    public static void bracos(net.minecraft.client.model.HumanoidModel<?> m, net.minecraft.world.entity.LivingEntity e) {
        if (!(e instanceof Player p)) return;
        Cena c = CENAS.get(p.getId());
        if (c == null) return;
        float t = agora() - c.inicio + Minecraft.getInstance().getFrameTime();
        boolean destro = p.getMainArm() == HumanoidArm.RIGHT;
        var outro = destro ? m.leftArm : m.rightArm;
        var principal = destro ? m.rightArm : m.leftArm;
        float lado = destro ? 1 : -1;   // yRot que leva o braço "outro" para o meio do corpo
        if (c.tipo == Tipo.SEGUNDO) {
            float sobe = suave(t / 12f) * (1 - suave((t - 78f) / 14f));
            float tranco = t < APARECE ? 0 : (float) Math.exp(-(t - APARECE) / 4.0) * Mth.sin((t - APARECE) * 0.9f);
            float desce = suave((t - APARECE - 8) / 26f);
            float treme = t < APARECE ? 0.035f * (t / APARECE) * Mth.sin(t * 2.7f) : 0;
            float x = Mth.lerp(desce, -1.5f, -1.05f) - 0.55f * tranco + treme;
            outro.xRot = Mth.lerp(sobe, outro.xRot, x);
            outro.yRot = Mth.lerp(sobe, outro.yRot, lado * Mth.lerp(desce, 0.3f, 0.45f));
            outro.zRot = Mth.lerp(sobe, outro.zRot, treme * lado);
            // o outro braço acompanha, meio erguido, como quem segura o próprio pulso
            float apoia = suave(t / 16f) * (1 - suave((t - APARECE + 4) / 8f));
            principal.xRot = Mth.lerp(apoia, principal.xRot, -1.2f);
            principal.yRot = Mth.lerp(apoia, principal.yRot, lado * 0.55f);
            // a onda final: os dois braços se abrem
            float abre = suave((t - 84f) / 6f) * (1 - suave((t - 94f) / 6f));
            m.leftArm.zRot = Mth.lerp(abre, m.leftArm.zRot, -0.55f);
            m.rightArm.zRot = Mth.lerp(abre, m.rightArm.zRot, 0.55f);
            m.head.xRot = Mth.lerp(suave(t / 10f) * (1 - desce), m.head.xRot, 0.35f);   // olhando a própria mão
        } else {
            float abre = suave(t / 20f) * (1 - suave((t - 60f) / 16f));
            float clarao = (float) Math.exp(-Math.abs(t - 30) / 5.0);
            m.leftArm.zRot = Mth.lerp(abre, m.leftArm.zRot, -0.7f - 0.25f * clarao);
            m.rightArm.zRot = Mth.lerp(abre, m.rightArm.zRot, 0.7f + 0.25f * clarao);
            m.leftArm.xRot = Mth.lerp(abre, m.leftArm.xRot, -0.25f);
            m.rightArm.xRot = Mth.lerp(abre, m.rightArm.xRot, -0.25f);
            m.head.xRot = Mth.lerp(abre, m.head.xRot, -0.3f * clarao);
        }
        if (m instanceof net.minecraft.client.model.PlayerModel<?> pm) {
            pm.leftSleeve.copyFrom(m.leftArm);
            pm.rightSleeve.copyFrom(m.rightArm);
            pm.hat.copyFrom(m.head);
        }
    }

    /** Sem painéis durante a cena (só os títulos). */
    @SubscribeEvent
    public static void hud(RenderGuiOverlayEvent.Pre e) {
        if (!ativa()) return;
        if (e.getOverlay() == net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.TITLE_TEXT.type()) return;
        e.setCanceled(true);
    }

    private CenaEmocoes() {}
}
