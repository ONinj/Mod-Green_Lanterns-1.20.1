package com.lanterna.naves;

import com.lanterna.lore.Factions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Pilotos das naves dos encontros no espaço:
 *  pirata    persegue o jogador mais perto, circula e atira; foge quando está quase destruída
 *  inimiga   patrulha de uma Tropa inimiga: igual ao pirata, mas só com Lanternas inimigos
 *  patrulha  patrulha da Tropa do jogador: acompanha um pouco e ataca piratas por perto
 *  cargueiro mercador: chega perto, para e espera; foge se for atacado
 *  rota      passa reto (só para o espaço parecer movimentado)
 */
final class NaveAI {
    private final NaveEntity n;
    private int fleeTicks;

    NaveAI(NaveEntity n) {
        this.n = n;
    }

    void tick(NaveTipo t, boolean espaco) {
        String mode = n.ia();
        if (mode.isEmpty()) return;
        Entity target = target(mode);
        double max = t.speed * (espaco ? 1.6 : 1.0);
        if (fleeTicks > 0) {
            fleeTicks--;
            Vec3 dir = Vec3.directionFromRotation(0, n.getYRot());
            seek(n.position().add(dir.scale(60)).add(0, 10, 0), max * 1.4);
            if (fleeTicks == 0) n.despawn();
            return;
        }
        switch (mode) {
            case "pirata", "inimiga" -> {
                if (t != NaveTipo.NAVE_CARGUEIRO && n.hp() < t.maxHp * 0.22f && n.rng().nextInt(40) == 0) {
                    fleeTicks = 200;
                    return;
                }
                if (target == null) { cruise(max * 0.5); return; }
                orbit(target, t == NaveTipo.NAVE_PIRATA_CAPITANIA ? 34 : 20, max);
                aimAndFire(target, 56);
            }
            case "patrulha" -> {
                if (target instanceof Player p0 && n.distanceTo(p0) < 32) com.lanterna.tropa.Encontros.saudar(n, p0);
                Entity pirate = nearestHostileShip();
                if (pirate != null) {
                    orbit(pirate, 18, max);
                    aimAndFire(pirate, 56);
                } else if (target != null) {
                    orbit(target, 16, max * 0.8);
                } else cruise(max * 0.5);
            }
            case "cargueiro" -> {
                if (target != null && n.distanceTo(target) > 18) seek(target.position().add(0, 4, 0), max);
                else {
                    brake();
                    if (target != null) com.lanterna.tropa.Encontros.atracou(n);
                }
            }
            default -> cruise(max * 0.7);
        }
    }

    void onAttacked(LivingEntity attacker) {
        String mode = n.ia();
        if (mode.equals("cargueiro") || mode.equals("rota")) {
            fleeTicks = 160;
        } else if (mode.equals("patrulha") && attacker instanceof Player p) {
            n.setIa("inimiga");
            n.setAiTarget(p.getUUID());
        } else if (!mode.isEmpty() && attacker != null) {
            n.setAiTarget(attacker.getUUID());
        }
    }

    @Nullable
    private Entity target(String mode) {
        ServerLevel sl = (ServerLevel) n.level();
        Entity t = n.aiTarget() != null ? sl.getEntity(n.aiTarget()) : null;
        if (t != null && t.isAlive() && t.distanceTo(n) < 160) return t.getVehicle() != null ? t.getVehicle() : t;
        Player best = null;
        double bd = 120 * 120;
        for (Player p : sl.players()) {
            if (p.isSpectator() || p.isCreative()) continue;
            if (mode.equals("inimiga") && !Factions.enemies(corps(), Factions.playerCorps(p))) continue;
            double d = p.distanceToSqr(n);
            if (d < bd) { bd = d; best = p; }
        }
        if (best != null) {
            n.setAiTarget(best.getUUID());
            return best.getVehicle() != null ? best.getVehicle() : best;
        }
        return null;
    }

    @Nullable
    private com.lanterna.corps.BuiltinCorps corps() {
        for (com.lanterna.corps.BuiltinCorps c : com.lanterna.corps.BuiltinCorps.values())
            if (c.id().getPath().equals(n.getPintura())) return c;
        return null;
    }

    @Nullable
    private Entity nearestHostileShip() {
        for (NaveEntity o : n.level().getEntitiesOfClass(NaveEntity.class, n.getBoundingBox().inflate(90),
                o -> o != n && (o.ia().equals("pirata") || o.ia().equals("inimiga")))) return o;
        return null;
    }

    private void orbit(Entity target, double radius, double max) {
        Vec3 c = target.position().add(0, 5, 0);
        Vec3 away = n.position().subtract(c);
        Vec3 flat = new Vec3(away.x, 0, away.z);
        if (flat.lengthSqr() < 1) flat = new Vec3(1, 0, 0);
        flat = flat.normalize();
        Vec3 tangent = new Vec3(-flat.z, 0, flat.x);
        Vec3 want = c.add(flat.scale(radius)).add(tangent.scale(radius * 0.6));
        seek(want, max);
        face(target.position());
    }

    private void aimAndFire(Entity target, double range) {
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 dir = to.subtract(n.position().add(0, 2, 0));
        float yawTo = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        n.setAnguloTorre(Mth.wrapDegrees(yawTo - n.getYRot()));
        if (dir.length() < range && n.rng().nextInt(3) == 0) {
            Entity pilot = n.getControllingPassenger();
            n.atirar(dir, pilot != null ? pilot : n);
        }
    }

    private void cruise(double speed) {
        Vec3 dir = Vec3.directionFromRotation(0, n.getYRot());
        if (n.rng().nextInt(120) == 0) n.setYRot(n.getYRot() + (n.rng().nextFloat() - 0.5f) * 40);
        seek(n.position().add(dir.scale(20)), speed);
    }

    private void brake() {
        Vec3 v = n.getDeltaMovement().scale(0.85);
        n.setDeltaMovement(v);
        n.move(MoverType.SELF, v);
    }

    private void seek(Vec3 want, double max) {
        Vec3 d = want.subtract(n.position());
        double len = d.length();
        if (len < 0.01) { brake(); return; }
        Vec3 v = n.getDeltaMovement().scale(0.92).add(d.scale(1.0 / len).scale(Math.min(max * 0.1, len * 0.02)));
        if (v.length() > max) v = v.normalize().scale(max);
        // Fora do espaço, não desce abaixo do terreno
        if (!n.level().dimension().equals(com.lanterna.space.SpaceBodies.SPACE)) {
            int ground = n.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, n.getBlockX(), n.getBlockZ());
            if (n.getY() < ground + 6 && v.y < 0.1) v = new Vec3(v.x, 0.15, v.z);
        }
        n.setDeltaMovement(v);
        n.move(MoverType.SELF, v);
        if (v.horizontalDistanceSqr() > 0.01) {
            float yaw = (float) Math.toDegrees(Math.atan2(-v.x, v.z));
            n.setYRot(n.getYRot() + Mth.clamp(Mth.wrapDegrees(yaw - n.getYRot()), -4f, 4f));
        }
    }

    private void face(Vec3 p) {
        // A nave olha para onde vai; as torres miram no alvo (aimAndFire)
    }
}
