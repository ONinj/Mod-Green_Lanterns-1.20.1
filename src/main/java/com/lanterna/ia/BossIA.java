package com.lanterna.ia;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A "cabeça" dos chefes (fase 3 da inteligência): uma camada por cima dos ataques de cada chefe, que observa a luta e
 * reage ao jeito de jogar de cada um.
 *
 *  - Ameaça: cada jogador tem uma ameaça (dano causado, que esfria com o tempo). A cada 2 s o chefe escolhe o alvo pela
 *    ameaça, por quem está mais perto e por quem está mais fraco: em grupo, ele troca de alvo (não fica preso no tanque).
 *  - Contra quem foge e ataca de longe: se o alvo fica longe por uns segundos, o chefe dá um salto de energia até ele.
 *  - Contra quem voa: se o alvo fica no alto, um raio puxa ele para baixo (com dano).
 *  - Esquiva: vê projéteis vindo na direção dele e desvia de lado (mais vezes nas fases 2 e 3).
 *  - Escudo: quem fica só defendendo tem o escudo quebrado no próximo golpe.
 *  - Fúria: luta longa demais (3 min) deixa o chefe mais rápido e mais forte.
 */
public final class BossIA {
    /** Teste automático: a última reação de algum chefe. */
    public static volatile String ultimo = "";
    private final Mob dono;
    private final int cor;
    private final Map<UUID, Float> ameaca = new HashMap<>();
    private int longe, alto, cdSalto, cdRaio, cdEsquiva, tempoLuta;
    private boolean furia;
    /** Escudo do alvo levantado há quantos ticks. */
    private int escudo;

    private final java.util.function.Predicate<Player> valido;

    public BossIA(Mob dono, int cor) {
        this(dono, cor, p -> true);
    }

    /** @param valido quem o chefe pode escolher como alvo (os lendários não atacam a própria Tropa) */
    public BossIA(Mob dono, int cor, java.util.function.Predicate<Player> valido) {
        this.dono = dono;
        this.cor = cor;
        this.valido = valido;
    }

    /** Quem bateu e quanto. */
    public void apanhou(Player p, float dano) {
        ameaca.merge(p.getUUID(), dano, Float::sum);
    }

    public boolean emFuria() {
        return furia;
    }

    /** Multiplicador do dano do chefe (fúria). */
    public float danoExtra() {
        return furia ? 1.25f : 1f;
    }

    /**
     * Roda a cada tick no servidor, antes dos ataques do chefe.
     * @param fase fase atual (1..3)
     * @param podeSaltar o chefe pode se teletransportar (os que ficam presos num lugar não)
     */
    public void tick(int fase, boolean podeSaltar) {
        if (!(dono.level() instanceof ServerLevel sl)) return;
        LivingEntity alvo = dono.getTarget();
        if (cdSalto > 0) cdSalto--;
        if (cdRaio > 0) cdRaio--;
        if (cdEsquiva > 0) cdEsquiva--;
        if (dono.tickCount % 20 == 0) ameaca.replaceAll((k, v) -> v * 0.93f);

        // ---- escolher o alvo ----
        if (dono.tickCount % 40 == 0 && alvo != null) {
            Player melhor = null;
            double nota = -1, notaAtual = -1;
            for (Player p : sl.getEntitiesOfClass(Player.class, dono.getBoundingBox().inflate(40), x -> !x.isSpectator() && !x.isCreative() && x.isAlive() && valido.test(x))) {
                double n = ameaca.getOrDefault(p.getUUID(), 0f) + 30.0 / (4 + p.distanceTo(dono)) * 4
                        + (1 - p.getHealth() / p.getMaxHealth()) * 12;
                if (p == alvo) notaAtual = n;
                if (n > nota) { nota = n; melhor = p; }
            }
            if (melhor != null && melhor != alvo && (notaAtual < 0 || nota > notaAtual * 1.25)) {
                dono.setTarget(melhor);
                alvo = melhor;
            }
        }
        if (alvo == null || !alvo.isAlive()) {
            tempoLuta = Math.max(0, tempoLuta - 2);
            return;
        }

        // ---- fúria ----
        tempoLuta++;
        if (!furia && tempoLuta > 20 * 180) {
            furia = true;
            var vel = dono.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            if (vel != null) vel.setBaseValue(vel.getBaseValue() * 1.15);
            sl.playSound(null, dono.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2f, 0.6f);
            for (Player p : sl.getEntitiesOfClass(Player.class, dono.getBoundingBox().inflate(48)))
                p.displayClientMessage(Component.translatable("boss.lanterna.ia.furia", dono.getDisplayName()).withStyle(s -> s.withColor(cor).withBold(true)), true);
        }

        double dist = dono.distanceTo(alvo);
        double altura = alvo.getY() - dono.getY();

        // ---- quem fica longe ----
        longe = dist > 14 ? longe + 1 : Math.max(0, longe - 2);
        if (podeSaltar && longe > 80 && cdSalto == 0 && dist < 48) {
            Vec3 dir = alvo.position().subtract(dono.position()).normalize();
            Vec3 destino = alvo.position().subtract(dir.scale(3 + dono.getBbWidth()));
            if (sl.noCollision(dono, dono.getBoundingBox().move(destino.subtract(dono.position())))) {
                rastro(sl, dono.position().add(0, dono.getBbHeight() / 2, 0), destino.add(0, dono.getBbHeight() / 2, 0));
                dono.teleportTo(destino.x, destino.y, destino.z);
                sl.playSound(null, dono.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.6f, 0.5f);
                cdSalto = fase >= 2 ? 160 : 240;
                ultimo = "salto";
                longe = 0;
            }
        }

        // ---- quem fica voando no alto ----
        boolean voando = altura > 6 && !alvo.onGround();
        alto = voando ? alto + 1 : Math.max(0, alto - 2);
        if (alto > 60 && cdRaio == 0 && dist < 40 && dono.hasLineOfSight(alvo)) {
            rastro(sl, dono.position().add(0, dono.getBbHeight() * 0.8, 0), alvo.position().add(0, 1, 0));
            float dano = (float) Math.max(4, dono.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 0.5);
            alvo.hurt(dono.damageSources().indirectMagic(dono, dono), dano);
            alvo.setDeltaMovement(alvo.getDeltaMovement().x * 0.3, -1.6, alvo.getDeltaMovement().z * 0.3);
            alvo.hurtMarked = true;
            sl.playSound(null, alvo.blockPosition(), SoundEvents.TRIDENT_THUNDER, SoundSource.HOSTILE, 1f, 1.6f);
            if (alvo instanceof Player p)
                p.displayClientMessage(Component.translatable("boss.lanterna.ia.puxou", dono.getDisplayName()).withStyle(s -> s.withColor(cor)), true);
            cdRaio = fase >= 2 ? 140 : 200;
            ultimo = "raio";
            alto = 0;
        }

        // ---- esquiva de projéteis ----
        if (cdEsquiva == 0 && dono.onGround()) {
            for (Projectile pr : sl.getEntitiesOfClass(Projectile.class, dono.getBoundingBox().inflate(7))) {
                if (pr.getOwner() == dono || pr.getDeltaMovement().lengthSqr() < 0.05) continue;
                Vec3 v = pr.getDeltaMovement().normalize();
                Vec3 ate = dono.position().add(0, dono.getBbHeight() / 2, 0).subtract(pr.position());
                if (v.dot(ate.normalize()) < 0.85) continue;
                float chance = fase >= 3 ? 0.55f : fase == 2 ? 0.4f : 0.25f;
                cdEsquiva = 50;
                if (dono.getRandom().nextFloat() > chance) break;
                Vec3 lado = new Vec3(-v.z, 0, v.x).normalize().scale(dono.getRandom().nextBoolean() ? 0.9 : -0.9);
                dono.setDeltaMovement(lado.x, 0.35, lado.z);
                dono.hurtMarked = true;
                sl.sendParticles(ParticleTypes.CLOUD, dono.getX(), dono.getY() + 0.2, dono.getZ(), 8, 0.4, 0.1, 0.4, 0.02);
                break;
            }
        }

        // ---- escudo ----
        if (alvo instanceof Player p && p.isBlocking()) {
            if (++escudo > 60 && dist < 5) {
                p.disableShield(true);
                sl.playSound(null, p.blockPosition(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1f, 0.8f);
                escudo = 0;
            }
        } else escudo = Math.max(0, escudo - 1);
    }

    private void rastro(ServerLevel sl, Vec3 de, Vec3 ate) {
        var op = new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 1.6f);
        Vec3 d = ate.subtract(de);
        int n = (int) Math.min(60, d.length() * 2);
        for (int i = 0; i <= n; i++) {
            Vec3 p = de.add(d.scale(i / (double) n));
            sl.sendParticles(op, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
        }
    }
}
