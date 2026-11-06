package com.lanterna.construct.fonte;

import com.lanterna.LanternaMod;
import com.lanterna.combate.Equilibrio;
import com.lanterna.construct.ConstructBlock;
import com.lanterna.construct.ConstructEntity;
import com.lanterna.construct.ConstructEntity.Kind;
import com.lanterna.construct.ConstructType;
import com.lanterna.construct.Constructs;
import com.lanterna.construct.ModEfeitos;
import com.lanterna.construct.fonte.DefsFonte.Comp;
import com.lanterna.construct.fonte.DefsFonte.Def;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O motor dos 70 construtos da Árvore da Luz. Cada construto é um comportamento (projétil, salva, chuva, raio, pulso,
 * área, armadilha, invocação, órbita, barreira, reforço, movimento ou corrente) com os números de {@link DefsFonte}
 * e uma forma 3D própria (formas.json), desenhada pelo cliente.
 *
 * Papéis da entidade FORMA: 0 = corpo do construto, 1 = emissor invisível (salva e chuva), 2 = projétil.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConstrutosFonte {

    // ===================== Usar =====================

    public static boolean perform(ServerPlayer p, ConstructType type, float pow, int cor) {
        Def d = DefsFonte.get(type.id);
        if (d == null) return false;
        ServerLevel l = p.serverLevel();
        float dur = ArvoreDaLuz.duracao(p);
        Vec3 look = p.getViewVector(1f);
        boolean ok = switch (d.comp()) {
            case PROJETIL -> { projetil(p, d, cor, pow, p.getEyePosition().add(look.scale(0.9)).add(0, -0.25, 0), look.scale(d.vel())); yield true; }
            case SALVA -> {
                ConstructEntity e = corpo(p, d, cor, pow, d.qtd() * 2 + 2, 1);
                e.setPos(p.getX(), p.getY(), p.getZ());
                l.addFreshEntity(e);
                yield true;
            }
            case CHUVA -> {
                Vec3 alvo = chao(p, 32);
                ConstructEntity e = corpo(p, d, cor, pow, d.qtd() * 3 + 10, 1);
                e.setPos(alvo.x, alvo.y, alvo.z);
                e.setEnd(alvo);
                l.addFreshEntity(e);
                yield true;
            }
            case RAIO -> raio(p, d, cor, pow);
            case PULSO -> pulso(p, d, cor, pow);
            case AREA, ARMADILHA -> {
                Vec3 alvo = chao(p, 20);
                ConstructEntity e = corpo(p, d, cor, pow, Math.round(d.dur() * dur), 0);
                e.setPos(alvo.x, alvo.y, alvo.z);
                e.setEnd(alvo);
                l.addFreshEntity(e);
                yield true;
            }
            case INVOCACAO -> {
                ConstructEntity e = corpo(p, d, cor, pow, Math.round(d.dur() * dur), 0);
                Vec3 lado = p.position().add(look.cross(new Vec3(0, 1, 0)).normalize().scale(1.6)).add(look.scale(1.2));
                e.setPos(lado.x, d.tem("voa") ? p.getY() + 1.6 : p.getY(), lado.z);
                e.setYRot(p.getYRot());
                l.addFreshEntity(e);
                l.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + 1, e.getZ(), 20, 0.4, 0.8, 0.4, 0.05);
                yield true;
            }
            case ORBITA -> {
                ConstructEntity e = corpo(p, d, cor, pow, Math.round(d.dur() * dur), 0);
                e.setPos(p.getX(), p.getY(), p.getZ());
                l.addFreshEntity(e);
                yield true;
            }
            case BARREIRA -> barreira(p, d, cor, Math.round(d.dur() * dur));
            case BUFF -> buff(p, d, cor, pow, Math.round(d.dur() * dur));
            case MOVIMENTO -> movimento(p, d, cor, pow);
            case CORRENTE -> corrente(p, d, cor, pow, Math.round(d.dur() * dur));
        };
        if (ok) l.playSound(null, p.blockPosition(), som(d.som()), SoundSource.PLAYERS, 1f, 1f);
        return ok;
    }

    /**
     * Um chefe (ou qualquer criatura) usa um construto da Árvore contra um alvo: o mesmo comportamento e a mesma forma
     * dos jogadores, apontado para o alvo em vez da mira.
     */
    public static void usar(LivingEntity dono, String id, LivingEntity alvo, float dano, int cor) {
        Def d = DefsFonte.get(id);
        if (d == null) return;
        ServerLevel l = (ServerLevel) dono.level();
        Vec3 olho = dono.getEyePosition(), mira = alvo.position().add(0, alvo.getBbHeight() * 0.5, 0);
        Vec3 dir = mira.subtract(olho).normalize();
        float pow = dano / Math.max(1f, d.dano());
        switch (d.comp()) {
            case PROJETIL -> projetil(dono, d, cor, pow, olho.add(dir.scale(1.5)), dir.scale(d.vel()));
            case SALVA -> { for (int i = 0; i < Math.min(8, d.qtd()); i++) projetil(dono, d, cor, pow, olho.add(dir.scale(1.5)),
                    dir.add((l.random.nextDouble() - 0.5) * 0.25, (l.random.nextDouble() - 0.5) * 0.15, (l.random.nextDouble() - 0.5) * 0.25).normalize().scale(d.vel())); }
            case CHUVA, AREA, ARMADILHA -> {
                ConstructEntity e = corpo(dono, d, cor, pow, d.comp() == Comp.CHUVA ? d.qtd() * 3 + 10 : d.dur(), d.comp() == Comp.CHUVA ? 1 : 0);
                e.setPos(alvo.getX(), alvo.getY(), alvo.getZ());
                e.setEnd(alvo.position());
                l.addFreshEntity(e);
            }
            case RAIO -> {
                Vec3 fim = olho.add(dir.scale(d.alcance()));
                for (LivingEntity x : l.getEntitiesOfClass(LivingEntity.class, new AABB(olho, fim).inflate(1.2), x -> ConstructEntity.inimigo(dono, x)))
                    if (com.lanterna.combate.Mira.clip(x, olho, fim, 0.5).isPresent()) acertar(dono, d, x, dano, cor, dir);
                ConstructEntity e = corpo(dono, d, cor, pow, 12, 0);
                e.setPos(olho.x, olho.y, olho.z);
                e.setEnd(fim);
                l.addFreshEntity(e);
            }
            case PULSO -> {
                for (LivingEntity x : l.getEntitiesOfClass(LivingEntity.class, dono.getBoundingBox().inflate(d.raio() + 2, 3, d.raio() + 2), x -> ConstructEntity.inimigo(dono, x)))
                    if (x.distanceTo(dono) <= d.raio() + 2) acertar(dono, d, x, dano, cor, x.position().subtract(dono.position()));
                ConstructEntity e = corpo(dono, d, cor, pow, 16, 0);
                e.setPos(dono.getX(), dono.getY() + 0.1, dono.getZ());
                e.setYRot(dono.getYRot());
                l.addFreshEntity(e);
            }
            case INVOCACAO, ORBITA -> {
                ConstructEntity e = corpo(dono, d, cor, pow, d.dur(), 0);
                e.setPos(dono.getX() + (l.random.nextDouble() - 0.5) * 6, dono.getY(), dono.getZ() + (l.random.nextDouble() - 0.5) * 6);
                e.setTarget(alvo);
                l.addFreshEntity(e);
            }
            case CORRENTE -> {
                ConstructEntity e = corpo(dono, d, cor, pow, d.dur(), 0);
                e.setTarget(alvo);
                e.setPos(alvo.getX(), alvo.getY(), alvo.getZ());
                l.addFreshEntity(e);
                acertar(dono, d, alvo, dano, cor, Vec3.ZERO);
            }
            default -> {}
        }
        l.playSound(null, dono.blockPosition(), som(d.som()), SoundSource.HOSTILE, 1.4f, 0.8f);
    }

    private static ConstructEntity corpo(LivingEntity dono, Def d, int cor, float pow, int vida, int papel) {
        ConstructEntity e = ConstructEntity.create(dono, Kind.FORMA, cor, vida, d.dano() * pow, d.tem("grande") ? 1.6f : 1f);
        e.setForma(d.id());
        e.setPapel(papel);
        return e;
    }

    private static ConstructEntity projetil(LivingEntity dono, Def d, int cor, float pow, Vec3 de, Vec3 vel) {
        ConstructEntity e = corpo(dono, d, cor, pow, 70, 2);
        e.setPos(de.x, de.y, de.z);
        e.setVelocidade(vel);
        orientar(e, vel);
        dono.level().addFreshEntity(e);
        return e;
    }

    private static void orientar(Entity e, Vec3 v) {
        if (v.lengthSqr() < 1e-6) return;
        Vec3 n = v.normalize();
        e.setYRot((float) Math.toDegrees(Math.atan2(-n.x, n.z)));
        e.setXRot((float) -Math.toDegrees(Math.asin(Mth.clamp(n.y, -1, 1))));
    }

    // ===================== Tick (servidor) =====================

    public static void tick(ConstructEntity e, @Nullable LivingEntity dono) {
        Def d = DefsFonte.get(e.forma());
        if (d == null) { e.discard(); return; }
        ServerLevel l = (ServerLevel) e.level();
        if (e.papel() == 2) { voar(e, dono, d, l); return; }
        if (dono == null || !dono.isAlive()) { if (d.comp() != Comp.ARMADILHA && d.comp() != Comp.AREA) { e.discard(); return; } }
        int t = e.age();
        switch (d.comp()) {
            case SALVA -> {
                if (dono != null && t % 2 == 0 && e.passo < d.qtd()) {
                    e.passo++;
                    Vec3 look = dono.getViewVector(1f);
                    Vec3 dir = look.add((l.random.nextDouble() - 0.5) * 0.12, (l.random.nextDouble() - 0.5) * 0.08, (l.random.nextDouble() - 0.5) * 0.12);
                    projetil(dono, d, e.color(), 1f, dono.getEyePosition().add(look.scale(0.9)).add(0, -0.25, 0), dir.normalize().scale(d.vel())).setDano(e.dano());
                }
            }
            case CHUVA -> {
                if (t % 3 == 0 && e.passo < d.qtd() && dono != null) {
                    e.passo++;
                    Vec3 c = e.end();
                    double a = l.random.nextDouble() * Math.PI * 2, r = Math.sqrt(l.random.nextDouble()) * d.raio();
                    Vec3 de = c.add(Math.cos(a) * r + 3, 14 + l.random.nextInt(4), Math.sin(a) * r);
                    Vec3 para = c.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                    projetil(dono, d, e.color(), 1f, de, para.subtract(de).normalize().scale(1.3)).setDano(e.dano());
                }
            }
            case AREA -> { if (t % 10 == 0) area(e, dono, d, l); }
            case ARMADILHA -> armadilha(e, dono, d, l);
            case INVOCACAO -> invocacao(e, dono, d, l);
            case ORBITA -> { if (dono != null) { e.setPos(dono.getX(), dono.getY(), dono.getZ()); if (t % 5 == 0) orbita(e, dono, d, l); } }
            case BUFF -> { if (dono != null) { e.setPos(dono.getX(), dono.getY(), dono.getZ()); buffTick(e, dono, d, l); } }
            case CORRENTE -> correnteTick(e, dono, d, l);
            case MOVIMENTO -> movTick(e, dono, d, l);
            default -> {}
        }
    }

    /** Fim de vida: armadilhas que não dispararam somem, invocações soltam um clarão. */
    public static void fim(ConstructEntity e, @Nullable LivingEntity dono) {
        Def d = DefsFonte.get(e.forma());
        if (d == null) return;
        if (d.comp() == Comp.INVOCACAO)
            ((ServerLevel) e.level()).sendParticles(pó(e.color(), 1.6f), e.getX(), e.getY() + 1, e.getZ(), 30, 0.5, 0.8, 0.5, 0.02);
    }

    // ---- projéteis ----

    private static void voar(ConstructEntity e, @Nullable LivingEntity dono, Def d, ServerLevel l) {
        Vec3 v = e.velocidade();
        if (d.tem("teleguiado") && e.age() > 4) {
            LivingEntity alvo = maisPerto(l, dono, e.position(), 16, x -> true);
            if (alvo != null) {
                Vec3 quer = alvo.position().add(0, alvo.getBbHeight() * 0.5, 0).subtract(e.position()).normalize().scale(v.length());
                v = v.lerp(quer, 0.18).normalize().scale(v.length());
                e.setVelocidade(v);
                orientar(e, v);
            }
        }
        Vec3 de = e.position(), para = de.add(v);
        BlockHitResult bloco = l.clip(new ClipContext(de, para, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
        Vec3 fim = bloco.getType() == HitResult.Type.MISS ? para : bloco.getLocation();
        double tam = 0.45 * e.scale();
        for (LivingEntity x : l.getEntitiesOfClass(LivingEntity.class, new AABB(de, fim).inflate(tam + 0.5), x -> ConstructEntity.inimigo(dono, x))) {
            if (e.acertados().contains(x.getId()) || com.lanterna.combate.Mira.clip(x, de, fim, tam).isEmpty()) continue;
            e.acertados().add(x.getId());
            acertar(dono, d, x, e.dano(), e.color(), v);
            if (!d.tem("perfura")) { impacto(e, dono, d, l, x.position().add(0, x.getBbHeight() * 0.5, 0)); return; }
        }
        if (bloco.getType() != HitResult.Type.MISS) { impacto(e, dono, d, l, fim); return; }
        e.setPos(para.x, para.y, para.z);
        e.setDeltaMovement(v);
        if (e.age() % 2 == 0) l.sendParticles(pó(e.color(), 1.1f), e.getX(), e.getY(), e.getZ(), 2, 0.1, 0.1, 0.1, 0);
    }

    private static void impacto(ConstructEntity e, @Nullable LivingEntity dono, Def d, ServerLevel l, Vec3 em) {
        float raio0 = d.raio() > 0 && (d.tem("explode") || d.comp() == Comp.CHUVA) ? d.raio() : 0;
        final float raio = d.comp() == Comp.CHUVA ? Math.max(2.2f, raio0 * 0.35f) : raio0;
        if (raio > 0) {
            l.sendParticles(ParticleTypes.EXPLOSION, em.x, em.y, em.z, d.tem("explode") ? 2 : 0, raio * 0.3, 0.2, raio * 0.3, 0);
            for (LivingEntity x : l.getEntitiesOfClass(LivingEntity.class, new AABB(em, em).inflate(raio), x -> x.position().distanceTo(em) <= raio + x.getBbWidth())) {
                if (ConstructEntity.inimigo(dono, x)) { if (!e.acertados().contains(x.getId())) acertar(dono, d, x, e.dano() * 0.8f, e.color(), x.position().subtract(em)); }
                else if (d.tem("cura_aliado")) curar(x, d.cura());
            }
            if (d.tem("explode")) l.playSound(null, em.x, em.y, em.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7f, 1.4f);
        }
        l.sendParticles(pó(e.color(), 1.6f), em.x, em.y, em.z, 18, 0.3, 0.3, 0.3, 0.08);
        e.discard();
    }

    // ---- golpes ----

    /** Acerta um inimigo com os efeitos do construto. */
    static void acertar(@Nullable LivingEntity dono, Def d, LivingEntity x, float dano, int cor, Vec3 empurrao) {
        float mult = d.tem("anti_morto") && (x.isInvertedHealAndHarm() || x instanceof com.lanterna.especies.LanternaEntity le && le.tropa() == com.lanterna.corps.BuiltinCorps.BLACK) ? 2f : 1f;
        boolean feriu = dano > 0 && Equilibrio.ferirComPoder(x, fonte(x, dono), dano * mult);
        x.invulnerableTime = 0;
        for (DefsFonte.Ef ef : d.inimigos()) efeito(x, ef, dono);
        if (d.tem("fogo")) x.setSecondsOnFire(5);
        if (d.tem("encanto")) x.addEffect(new MobEffectInstance(ModEfeitos.ENCANTO.get(), 120, 0, false, false, true));
        if (d.tem("pavor")) x.addEffect(new MobEffectInstance(ModEfeitos.PAVOR.get(), 120, 0, false, false, true));
        if (d.tem("acalma") && x instanceof Mob m) m.setTarget(null);
        if (d.tem("silencio") && x instanceof Player) x.addEffect(new MobEffectInstance(ModEfeitos.PAVOR.get(), 100, 0, false, false, true));
        if (d.tem("roubo") && feriu && dono != null) dono.heal(dano * 0.35f);
        if (d.tem("rouba_carga") && x instanceof ServerPlayer vp && dono instanceof ServerPlayer op) roubarCarga(vp, op, 60);
        if (d.tem("empurra") && empurrao.lengthSqr() > 1e-4) { Vec3 n = empurrao.normalize(); x.push(n.x * 1.2, 0.4, n.z * 1.2); x.hurtMarked = true; }
        if (d.tem("arremessa")) { x.push(0, 0.9, 0); x.hurtMarked = true; }
        if (d.tem("puxa") && dono != null) { Vec3 n = dono.position().subtract(x.position()).normalize(); x.push(n.x * 0.9, 0.25, n.z * 0.9); x.hurtMarked = true; }
        x.level().getServer();
        ((ServerLevel) x.level()).sendParticles(pó(cor, 1.4f), x.getX(), x.getY() + x.getBbHeight() / 2, x.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
    }

    static void curar(LivingEntity x, float quanto) {
        if (quanto <= 0) return;
        x.heal(quanto);
        ((ServerLevel) x.level()).sendParticles(ParticleTypes.HEART, x.getX(), x.getY() + x.getBbHeight(), x.getZ(), 2, 0.3, 0.2, 0.3, 0);
    }

    private static void roubarCarga(ServerPlayer vp, ServerPlayer op, int quanto) {
        var rv = com.lanterna.capability.ModCapabilities.get(vp).orElse(null);
        var ro = com.lanterna.capability.ModCapabilities.get(op).orElse(null);
        if (rv == null || ro == null || !rv.hasRing() || !ro.hasRing()) return;
        int n = Math.min(quanto, rv.charge());
        com.lanterna.power.RingPowers.setCharge(vp, rv, rv.charge() - n);
        com.lanterna.power.RingPowers.setCharge(op, ro, Math.min(ro.maxCharge(), ro.charge() + n));
    }

    private static void efeito(LivingEntity x, DefsFonte.Ef ef, @Nullable LivingEntity dono) {
        MobEffect m = switch (ef.efeito()) {
            case "slow" -> MobEffects.MOVEMENT_SLOWDOWN; case "weak" -> MobEffects.WEAKNESS; case "poison" -> MobEffects.POISON;
            case "wither" -> MobEffects.WITHER; case "blind" -> MobEffects.BLINDNESS; case "dark" -> MobEffects.DARKNESS;
            case "nausea" -> MobEffects.CONFUSION; case "levit" -> MobEffects.LEVITATION; case "glow" -> MobEffects.GLOWING;
            case "regen" -> MobEffects.REGENERATION; case "res" -> MobEffects.DAMAGE_RESISTANCE; case "str" -> MobEffects.DAMAGE_BOOST;
            case "speed" -> MobEffects.MOVEMENT_SPEED; case "absorb" -> MobEffects.ABSORPTION; case "jump" -> MobEffects.JUMP;
            case "slowfall" -> MobEffects.SLOW_FALLING; case "fireres" -> MobEffects.FIRE_RESISTANCE; case "luck" -> MobEffects.LUCK;
            case "fear" -> ModEfeitos.PAVOR.get(); case "charm" -> ModEfeitos.ENCANTO.get();
            case "root" -> null;
            default -> null;
        };
        if (ef.efeito().equals("root")) {
            x.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ef.ticks(), 6, false, false, true));
            x.setDeltaMovement(0, Math.min(0, x.getDeltaMovement().y), 0);
            x.hurtMarked = true;
            return;
        }
        if (m != null) x.addEffect(new MobEffectInstance(m, ef.ticks(), ef.nivel(), false, true, true));
    }

    // ---- raio ----

    private static boolean raio(ServerPlayer p, Def d, int cor, float pow) {
        ServerLevel l = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getViewVector(1f), far = eye.add(look.scale(d.alcance()));
        BlockHitResult b = l.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 fim = b.getType() == HitResult.Type.MISS ? far : b.getLocation();
        for (LivingEntity x : l.getEntitiesOfClass(LivingEntity.class, new AABB(eye, fim).inflate(1.2), x -> ConstructEntity.inimigo(p, x)))
            if (com.lanterna.combate.Mira.clip(x, eye, fim, 0.4).isPresent()) acertar(p, d, x, d.dano() * pow, cor, look);
        ConstructEntity e = corpo(p, d, cor, pow, 12, 0);
        e.setPos(eye.x, eye.y - 0.3, eye.z);
        e.setEnd(fim);
        l.addFreshEntity(e);
        for (int i = 0; i < 12; i++) { Vec3 q = eye.lerp(fim, i / 12.0); l.sendParticles(pó(cor, 1.2f), q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0); }
        return true;
    }

    // ---- pulso ----

    private static boolean pulso(ServerPlayer p, Def d, int cor, float pow) {
        ServerLevel l = p.serverLevel();
        Vec3 look = p.getViewVector(1f);
        double r = d.raio();
        for (LivingEntity x : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r, 3, r), x -> x.distanceTo(p) <= r)) {
            boolean inimigo = ConstructEntity.inimigo(p, x);
            if (d.tem("cone")) {
                Vec3 rel = x.position().subtract(p.position());
                if (rel.lengthSqr() > 0.01 && rel.normalize().dot(new Vec3(look.x, 0, look.z).normalize()) < 0.45) continue;
            }
            if (inimigo && !d.tem("so_aliados")) acertar(p, d, x, d.dano() * pow, cor, x.position().subtract(p.position()));
            else if (!inimigo || x == p) {
                if (d.tem("cura_aliado")) curar(x, d.cura());
                for (DefsFonte.Ef ef : d.aliados()) efeito(x, ef, p);
                if (d.tem("purifica")) for (var ef : new ArrayList<>(x.getActiveEffects())) if (ef.getEffect().getCategory() == MobEffectCategory.HARMFUL) x.removeEffect(ef.getEffect());
            }
        }
        if (d.tem("cura_aliado")) { curar(p, d.cura()); for (DefsFonte.Ef ef : d.aliados()) efeito(p, ef, p); }
        if (d.tem("itens")) for (ItemEntity it : l.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(r))) it.setPos(p.getX(), p.getY() + 0.5, p.getZ());
        ConstructEntity e = corpo(p, d, cor, pow, 16, 0);
        e.setPos(p.getX(), p.getY() + 0.1, p.getZ());
        e.setYRot(p.getYRot());
        l.addFreshEntity(e);
        return true;
    }

    // ---- área e armadilha ----

    private static void area(ConstructEntity e, @Nullable LivingEntity dono, Def d, ServerLevel l) {
        Vec3 c = e.end();
        double r = d.raio();
        for (LivingEntity x : l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r, 3, r), x -> x.position().distanceTo(c) <= r + 0.5)) {
            if (ConstructEntity.inimigo(dono, x)) {
                acertar(dono, d, x, e.dano(), e.color(), d.tem("empurra") ? x.position().subtract(c) : Vec3.ZERO);
                if (d.tem("puxa")) { Vec3 n = c.subtract(x.position()); if (n.lengthSqr() > 0.3) { n = n.normalize(); x.push(n.x * 0.35, 0.05, n.z * 0.35); x.hurtMarked = true; } }
            } else if (d.tem("cura_aliado")) curar(x, d.cura());
        }
        if (e.age() % 20 == 0) l.sendParticles(pó(e.color(), 1.3f), c.x, c.y + 0.2, c.z, 20, r * 0.5, 0.2, r * 0.5, 0.02);
    }

    private static void armadilha(ConstructEntity e, @Nullable LivingEntity dono, Def d, ServerLevel l) {
        Vec3 c = e.end();
        if (e.age() < 20) return;
        LivingEntity pisou = null;
        for (LivingEntity x : l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(d.raio() * 0.6, 2, d.raio() * 0.6), x -> ConstructEntity.inimigo(dono, x))) { pisou = x; break; }
        if (pisou == null) return;
        for (LivingEntity x : l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(d.raio(), 2.5, d.raio()), x -> ConstructEntity.inimigo(dono, x)))
            acertar(dono, d, x, e.dano(), e.color(), x.position().subtract(c));
        if (d.tem("explode")) l.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 3, 0.8, 0.3, 0.8, 0);
        l.sendParticles(pó(e.color(), 1.8f), c.x, c.y + 0.5, c.z, 50, d.raio() * 0.4, 0.4, d.raio() * 0.4, 0.1);
        l.playSound(null, c.x, c.y, c.z, d.tem("explode") ? SoundEvents.GENERIC_EXPLODE : SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 1f, 1.1f);
        e.discard();
    }

    // ---- invocação ----

    private static void invocacao(ConstructEntity e, @Nullable LivingEntity dono, Def d, ServerLevel l) {
        if (dono == null) return;
        int t = e.age();
        if (d.tem("cura_dono") && t % 40 == 20) curar(dono, d.cura());
        if (d.tem("torre")) {
            if (t % 20 == 10) {
                LivingEntity alvo = maisPerto(l, dono, e.position(), d.raio(), x -> true);
                if (alvo != null) {
                    acertar(dono, d, alvo, e.dano(), e.color(), Vec3.ZERO);
                    e.setTarget(alvo);
                    e.setEnd(alvo.position().add(0, alvo.getBbHeight() * 0.5, 0));
                    l.playSound(null, e.blockPosition(), SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 0.4f, 1.6f);
                }
            }
            return;
        }
        LivingEntity alvo = e.target() instanceof LivingEntity le && le.isAlive() && le.distanceTo(dono) < 32 ? le : null;
        if (!(dono instanceof Player) && alvo == null) {
            Player pl = l.getNearestPlayer(e, 24);
            if (pl != null && !pl.isCreative() && !pl.isSpectator()) { alvo = pl; e.setTarget(pl); }
        }
        if (alvo == null || t % 20 == 0) {
            LivingEntity novo = maisPerto(l, dono, dono.position(), 16, x -> true);
            if (dono.getLastHurtByMob() != null && dono.getLastHurtByMob().isAlive() && dono.getLastHurtByMob().distanceTo(dono) < 20) novo = dono.getLastHurtByMob();
            if (novo != null) { alvo = novo; e.setTarget(novo); }
        }
        boolean voa = d.tem("voa");
        Vec3 quer;
        if (alvo != null) {
            Vec3 a = alvo.position();
            quer = d.tem("atira") ? a.add(new Vec3(e.getX() - a.x, 0, e.getZ() - a.z).normalize().scale(8)).add(0, 5, 0) : a;
        } else {
            Vec3 tras = dono.getViewVector(1f).multiply(1, 0, 1).normalize().scale(-2.2);
            quer = dono.position().add(tras).add(dono.getViewVector(1f).cross(new Vec3(0, 1, 0)).normalize().scale(1.2));
            if (voa) quer = quer.add(0, 1.8, 0);
        }
        Vec3 pos = e.position(), dif = quer.subtract(pos);
        double vel = d.vel() * (alvo == null && dif.length() > 10 ? 2.2 : 1);
        Vec3 passo = dif.length() > vel ? dif.normalize().scale(vel) : dif;
        Vec3 nova = pos.add(passo);
        if (!voa) {
            int x = Mth.floor(nova.x), z = Mth.floor(nova.z);
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            double alvoY = Math.abs(y - pos.y) < 4 ? y : pos.y + Math.signum(y - pos.y) * 0.5;
            nova = new Vec3(nova.x, Mth.lerp(0.5, pos.y, alvoY), nova.z);
        }
        e.setPos(nova.x, nova.y, nova.z);
        if (passo.horizontalDistanceSqr() > 1e-4) e.setYRot((float) Math.toDegrees(Math.atan2(-passo.x, passo.z)));
        if (alvo != null) {
            double dist = alvo.distanceTo(e);
            if (d.tem("atira")) {
                if (t % 12 == 0 && dist < 18) {
                    Vec3 de = e.position().add(0, 0.4, 0), dir = alvo.position().add(0, alvo.getBbHeight() * 0.5, 0).subtract(de).normalize();
                    Def tiro = DefsFonte.get("g_metralhadora");
                    if (tiro != null) projetil(dono, tiro, e.color(), 1f, de, dir.scale(2.2)).setDano(e.dano());
                }
            } else if (dist < 2.2 * e.scale() + alvo.getBbWidth() && t % 14 == 0) {
                acertar(dono, d, alvo, e.dano(), e.color(), alvo.position().subtract(e.position()));
                e.passo = t;   // o cliente anima o golpe
                l.playSound(null, e.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.3f);
            }
        }
    }

    // ---- órbita ----

    private static void orbita(ConstructEntity e, LivingEntity dono, Def d, ServerLevel l) {
        double r = d.raio();
        for (LivingEntity x : l.getEntitiesOfClass(LivingEntity.class, dono.getBoundingBox().inflate(r + 1, 1.5, r + 1), x -> ConstructEntity.inimigo(dono, x))) {
            double dist = x.distanceTo(dono);
            if (dist > r + 1.2 || dist < r * 0.4) continue;
            acertar(dono, d, x, e.dano(), e.color(), x.position().subtract(dono.position()));
        }
    }

    // ---- barreira ----

    private static boolean barreira(ServerPlayer p, Def d, int cor, int vida) {
        ServerLevel l = p.serverLevel();
        int n = 0;
        if (d.tem("caixa_alvo")) {
            LivingEntity alvo = Constructs.lookedEntity(p, 24);
            if (alvo == null) { p.displayClientMessage(Component.translatable("message.lanterna.construct_no_target"), true); return false; }
            AABB b = alvo.getBoundingBox();
            for (int x = Mth.floor(b.minX) - 1; x <= Mth.floor(b.maxX) + 1; x++) for (int y = Mth.floor(b.minY) - 1; y <= Mth.floor(b.maxY) + 1; y++)
                for (int z = Mth.floor(b.minZ) - 1; z <= Mth.floor(b.maxZ) + 1; z++) {
                    boolean casca = x == Mth.floor(b.minX) - 1 || x == Mth.floor(b.maxX) + 1 || y == Mth.floor(b.minY) - 1 || y == Mth.floor(b.maxY) + 1
                            || z == Mth.floor(b.minZ) - 1 || z == Mth.floor(b.maxZ) + 1;
                    if (casca && ConstructBlock.place(l, new BlockPos(x, y, z), cor, vida)) n++;
                }
            alvo.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, vida, 5));
            return n > 0;
        }
        BlockPos c = p.blockPosition();
        int r = Math.round(d.raio());
        for (int dx = -r - 1; dx <= r + 1; dx++) for (int dz = -r - 1; dz <= r + 1; dz++) for (int dy = 0; dy <= r + 1; dy++) {
            double dist = Math.sqrt(dx * dx + dz * dz + (d.tem("cupula") ? dy * dy : 0));
            boolean pode = d.tem("cupula") ? Math.abs(dist - r) < 0.6 : (Math.abs(Math.sqrt(dx * dx + dz * dz) - r) < 0.6 && dy < 3);
            if (pode && ConstructBlock.place(l, c.offset(dx, dy, dz), cor, vida)) n++;
        }
        ConstructEntity e = corpo(p, d, cor, 1f, 30, 0);
        e.setPos(p.getX(), p.getY(), p.getZ());
        l.addFreshEntity(e);
        return n > 0;
    }

    // ---- reforço ----

    private static final Map<UUID, Long> ANTIMORTE = new HashMap<>();
    private static final Map<UUID, Long> ESPELHO = new HashMap<>();
    /** alvo → (dono, até): o dano que o alvo causar volta para ele. */
    private static final Map<UUID, Long> ELO = new HashMap<>();

    private static boolean buff(ServerPlayer p, Def d, int cor, float pow, int vida) {
        ServerLevel l = p.serverLevel();
        List<LivingEntity> alvos = new ArrayList<>();
        alvos.add(p);
        if (d.tem("par")) {
            Player par = null;
            double best = d.raio() * d.raio();
            for (Player o : l.players()) if (o != p && o.distanceToSqr(p) < best && !ConstructEntity.inimigo(p, o)) { best = o.distanceToSqr(p); par = o; }
            if (par != null) alvos.add(par);
        } else if (!d.tem("so_eu") && d.raio() > 0) {
            for (LivingEntity x : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(d.raio()), x -> x != p && !ConstructEntity.inimigo(p, x) && x instanceof Player)) alvos.add(x);
        }
        long ate = l.getGameTime() + vida;
        for (LivingEntity a : alvos) {
            for (DefsFonte.Ef ef : d.aliados()) a.addEffect(new MobEffectInstance(efeitoDe(ef.efeito()), Math.round(ef.ticks() * ArvoreDaLuz.duracao(p)), ef.nivel(), false, true, true));
            if (d.tem("antimorte")) ANTIMORTE.put(a.getUUID(), ate);
            if (d.tem("espelho")) ESPELHO.put(a.getUUID(), ate);
        }
        ConstructEntity e = corpo(p, d, cor, pow, vida, 0);
        e.setPos(p.getX(), p.getY(), p.getZ());
        l.addFreshEntity(e);
        return true;
    }

    private static MobEffect efeitoDe(String n) {
        return switch (n) {
            case "regen" -> MobEffects.REGENERATION; case "res" -> MobEffects.DAMAGE_RESISTANCE; case "str" -> MobEffects.DAMAGE_BOOST;
            case "speed" -> MobEffects.MOVEMENT_SPEED; case "absorb" -> MobEffects.ABSORPTION; case "jump" -> MobEffects.JUMP;
            case "slowfall" -> MobEffects.SLOW_FALLING; case "luck" -> MobEffects.LUCK; default -> MobEffects.FIRE_RESISTANCE;
        };
    }

    private static void buffTick(ConstructEntity e, LivingEntity dono, Def d, ServerLevel l) {
        int t = e.age();
        if (d.tem("aura") && t % 20 == 0)
            for (LivingEntity x : l.getEntitiesOfClass(LivingEntity.class, dono.getBoundingBox().inflate(d.raio()), x -> ConstructEntity.inimigo(dono, x))) acertar(dono, d, x, 0, e.color(), Vec3.ZERO);
        if (d.tem("custo_vida") && t % 40 == 0 && dono.getHealth() > 4) dono.hurt(dono.damageSources().magic(), 1f);
        if (d.tem("itens") && t % 10 == 0) for (ItemEntity it : l.getEntitiesOfClass(ItemEntity.class, dono.getBoundingBox().inflate(8))) {
            Vec3 n = dono.position().subtract(it.position()).normalize().scale(0.35);
            it.setDeltaMovement(n.x, n.y + 0.05, n.z);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent ev) {
        LivingEntity vitima = ev.getEntity();
        long agora = vitima.level().getGameTime();
        Entity atk = ev.getSource().getEntity();
        // Espelho: quem me fere sente a mesma dor
        Long esp = ESPELHO.get(vitima.getUUID());
        if (esp != null && esp > agora && atk instanceof LivingEntity la && la != vitima && !ev.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            la.hurt(vitima.damageSources().thorns(vitima), ev.getAmount() * 0.6f);
        }
        // Elo de Dor: o dano que o alvo marcado causar volta para ele
        if (atk instanceof LivingEntity la) {
            Long elo = ELO.get(la.getUUID());
            if (elo != null && elo > agora && la != vitima) la.hurt(la.damageSources().magic(), ev.getAmount());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent ev) {
        LivingEntity v = ev.getEntity();
        Long ate = ANTIMORTE.get(v.getUUID());
        if (ate == null || ate < v.level().getGameTime()) return;
        ANTIMORTE.remove(v.getUUID());
        ev.setCanceled(true);
        v.setHealth(v.getMaxHealth() * 0.5f);
        v.removeAllEffects();
        v.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
        v.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1));
        ServerLevel l = (ServerLevel) v.level();
        l.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, v.getX(), v.getY() + 1, v.getZ(), 60, 0.6, 1, 0.6, 0.4);
        l.playSound(null, v.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1f, 1.2f);
    }

    // ---- movimento ----

    private static final Map<UUID, ConstructEntity> SALTOS = new HashMap<>();

    private static boolean movimento(ServerPlayer p, Def d, int cor, float pow) {
        ServerLevel l = p.serverLevel();
        Vec3 look = p.getViewVector(1f);
        if (d.tem("troca")) {
            LivingEntity alvo = Constructs.lookedEntity(p, d.alcance());
            if (alvo == null) { p.displayClientMessage(Component.translatable("message.lanterna.construct_no_target"), true); return false; }
            Vec3 a = p.position(), b = alvo.position();
            p.teleportTo(b.x, b.y, b.z);
            alvo.teleportTo(a.x, a.y, a.z);
            acertar(p, d, alvo, d.dano() * pow, cor, Vec3.ZERO);
            for (Vec3 q : new Vec3[]{a, b}) l.sendParticles(ParticleTypes.REVERSE_PORTAL, q.x, q.y + 1, q.z, 40, 0.4, 0.8, 0.4, 0.2);
            ConstructEntity e = corpo(p, d, cor, pow, 16, 0);
            e.setPos(b.x, b.y + 1, b.z);
            l.addFreshEntity(e);
            return true;
        }
        Vec3 v = d.tem("salto") ? new Vec3(look.x * d.vel(), 1.1, look.z * d.vel()) : look.scale(d.vel());
        p.setDeltaMovement(v);
        p.hurtMarked = true;
        p.fallDistance = 0;
        ConstructEntity e = corpo(p, d, cor, pow, d.tem("salto") ? 100 : 12, 0);
        e.setPos(p.getX(), p.getY(), p.getZ());
        e.setVelocidade(v);
        l.addFreshEntity(e);
        return true;
    }

    private static void movTick(ConstructEntity e, @Nullable LivingEntity dono, Def d, ServerLevel l) {
        if (dono == null) return;
        e.setPos(dono.getX(), dono.getY(), dono.getZ());
        dono.fallDistance = 0;
        if (d.tem("investida")) {
            if (e.age() < 10) { dono.setDeltaMovement(e.velocidade()); dono.hurtMarked = true; }
            for (LivingEntity x : l.getEntitiesOfClass(LivingEntity.class, dono.getBoundingBox().inflate(d.raio()), x -> ConstructEntity.inimigo(dono, x)))
                if (e.acertados().add(x.getId())) acertar(dono, d, x, e.dano(), e.color(), e.velocidade());
        } else if (d.tem("salto") && e.age() > 6 && dono.onGround()) {
            for (LivingEntity x : l.getEntitiesOfClass(LivingEntity.class, dono.getBoundingBox().inflate(d.raio(), 1.5, d.raio()), x -> ConstructEntity.inimigo(dono, x)))
                acertar(dono, d, x, e.dano(), e.color(), x.position().subtract(dono.position()));
            l.sendParticles(ParticleTypes.EXPLOSION, dono.getX(), dono.getY(), dono.getZ(), 2, 1, 0.1, 1, 0);
            l.sendParticles(pó(e.color(), 1.8f), dono.getX(), dono.getY() + 0.2, dono.getZ(), 60, d.raio() * 0.5, 0.2, d.raio() * 0.5, 0.15);
            l.playSound(null, dono.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.2f);
            e.discard();
        }
    }

    // ---- corrente ----

    private static boolean corrente(ServerPlayer p, Def d, int cor, float pow, int vida) {
        ServerLevel l = p.serverLevel();
        LivingEntity primeiro = Constructs.lookedEntity(p, d.alcance());
        if (primeiro == null || !ConstructEntity.inimigo(p, primeiro)) { p.displayClientMessage(Component.translatable("message.lanterna.construct_no_target"), true); return false; }
        List<LivingEntity> alvos = new ArrayList<>();
        alvos.add(primeiro);
        if (d.qtd() > 1)
            for (LivingEntity x : l.getEntitiesOfClass(LivingEntity.class, primeiro.getBoundingBox().inflate(d.raio()), x -> x != primeiro && ConstructEntity.inimigo(p, x))) {
                if (alvos.size() >= d.qtd()) break;
                alvos.add(x);
            }
        for (LivingEntity a : alvos) {
            ConstructEntity e = corpo(p, d, cor, pow, vida, 0);
            e.setTarget(a);
            e.setPos(a.getX(), a.getY(), a.getZ());
            l.addFreshEntity(e);
            acertar(p, d, a, d.dano() * pow, cor, Vec3.ZERO);
            if (d.tem("espelho_alvo")) ELO.put(a.getUUID(), l.getGameTime() + vida);
        }
        return true;
    }

    private static void correnteTick(ConstructEntity e, @Nullable LivingEntity dono, Def d, ServerLevel l) {
        if (!(e.target() instanceof LivingEntity t) || !t.isAlive() || dono == null) { e.discard(); return; }
        e.setPos(t.getX(), t.getY(), t.getZ());
        if (d.tem("puxa")) {
            Vec3 n = dono.position().subtract(t.position());
            if (n.length() > 2) { n = n.normalize().scale(0.55); t.setDeltaMovement(n.x, Math.max(0.05, n.y), n.z); t.hurtMarked = true; t.fallDistance = 0; }
        }
        if (e.age() % 20 == 10) {
            for (DefsFonte.Ef ef : d.inimigos()) efeito(t, ef, dono);
            if (d.tem("roubo") && Equilibrio.ferirComPoder(t, fonte(t, dono), e.dano() * 0.5f)) dono.heal(e.dano() * 0.3f);
            if (d.tem("fogo")) t.setSecondsOnFire(3);
            if (d.tem("rouba_carga") && t instanceof ServerPlayer vp && dono instanceof ServerPlayer op) roubarCarga(vp, op, 25);
        }
    }

    // ===================== Ajudantes =====================

    @Nullable
    private static LivingEntity maisPerto(ServerLevel l, @Nullable LivingEntity dono, Vec3 de, double r, java.util.function.Predicate<LivingEntity> f) {
        LivingEntity best = null;
        double bd = r * r;
        for (LivingEntity x : l.getEntitiesOfClass(LivingEntity.class, new AABB(de, de).inflate(r), x -> ConstructEntity.inimigo(dono, x) && f.test(x))) {
            if (x instanceof Player pl && dono instanceof Player && !(x instanceof ServerPlayer sp && sp.gameMode.isSurvival())) continue;
            // invocações só caçam o que é hostil (ou quem atacou o dono)
            if (!(x instanceof net.minecraft.world.entity.monster.Enemy) && !(x instanceof com.lanterna.especies.LanternaEntity) && x != (dono != null ? dono.getLastHurtByMob() : null)
                    && !(x instanceof Player)) continue;
            double d2 = x.distanceToSqr(de);
            if (d2 < bd) { bd = d2; best = x; }
        }
        return best;
    }

    /** O chão onde o jogador olha (ou o alvo). */
    private static Vec3 chao(ServerPlayer p, double alcance) {
        LivingEntity alvo = Constructs.lookedEntity(p, alcance);
        if (alvo != null) return alvo.position();
        Vec3 eye = p.getEyePosition(), far = eye.add(p.getViewVector(1f).scale(alcance));
        BlockHitResult b = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 spot = b.getType() == HitResult.Type.MISS ? far : b.getLocation();
        BlockPos q = BlockPos.containing(spot);
        for (int i = 0; i < 16 && p.level().getBlockState(q.below()).isAir(); i++) q = q.below();
        return new Vec3(spot.x, q.getY(), spot.z);
    }

    private static DamageSource fonte(LivingEntity alvo, @Nullable LivingEntity dono) {
        if (dono instanceof Player pl) return alvo.damageSources().playerAttack(pl);
        if (dono != null) return alvo.damageSources().mobAttack(dono);
        return alvo.damageSources().magic();
    }

    static DustParticleOptions pó(int c, float s) {
        return new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f), s);
    }

    private static SoundEvent som(String nome) {
        try {
            var f = SoundEvents.class.getField(nome);
            Object v = f.get(null);
            if (v instanceof SoundEvent s) return s;
            if (v instanceof net.minecraft.core.Holder<?> h && h.value() instanceof SoundEvent s) return s;
        } catch (Exception ignored) {}
        SoundEvent s = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(nome.toLowerCase().replace('_', '.')));
        return s != null ? s : SoundEvents.AMETHYST_BLOCK_RESONATE;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent ev) {
        UUID u = ev.getEntity().getUUID();
        ANTIMORTE.remove(u); ESPELHO.remove(u); SALTOS.remove(u);
    }

    @SubscribeEvent
    public static void limpar(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ev.getServer().getTickCount() % 1200 != 0) return;
        long agora = ev.getServer().overworld().getGameTime();
        ANTIMORTE.values().removeIf(t -> t < agora);
        ESPELHO.values().removeIf(t -> t < agora);
        ELO.values().removeIf(t -> t < agora);
    }

    private ConstrutosFonte() {}
}
