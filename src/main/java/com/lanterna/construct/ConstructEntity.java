package com.lanterna.construct;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A parte visível (e o golpe) de um construto: feixe do laser, martelo gigante, punho, rajada,
 * escudo-bolha, picareta, corrente do gancho, lâminas giratórias e a mão que puxa.
 * Pode pertencer a um jogador ou a um boss (o Vigia Partido copia os construtos do jogador).
 */
public class ConstructEntity extends Entity {
    public enum Kind { BEAM, HAMMER, FIST, BOLT, SHIELD, PICKAXE, GRAPPLE, BLADE, HAND, RAM, FEAR, NOVA, STREAM, CRYSTAL, TENDRIL, DEADHANDS, DOOR }

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(ConstructEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(ConstructEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(ConstructEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(ConstructEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> END = SynchedEntityData.defineId(ConstructEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(ConstructEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(ConstructEntity.class, EntityDataSerializers.INT);

    /** Dano (só no servidor). */
    float damage;
    private Vec3 velocity = Vec3.ZERO;
    private boolean grabbed;
    /** Quem o aríete já atravessou (acerta cada um uma vez só). */
    private final java.util.Set<Integer> atravessados = new java.util.HashSet<>();

    public ConstructEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, 0);
        entityData.define(COLOR, 0x2BFF4A);
        entityData.define(OWNER, -1);
        entityData.define(LIFE, 20);
        entityData.define(END, new Vector3f());
        entityData.define(SCALE, 1f);
        entityData.define(TARGET, -1);
    }

    // ===================== Criação =====================

    public static ConstructEntity create(LivingEntity owner, Kind kind, int color, int life, float damage, float scale) {
        ConstructEntity e = new ConstructEntity(ConstructRegistry.CONSTRUCT.get(), owner.level());
        e.entityData.set(KIND, kind.ordinal());
        e.entityData.set(COLOR, color);
        e.entityData.set(OWNER, owner.getId());
        e.entityData.set(LIFE, life);
        e.entityData.set(SCALE, scale);
        e.damage = damage;
        e.setPos(owner.getX(), owner.getY() + owner.getBbHeight() * 0.7, owner.getZ());
        e.setYRot(owner.getYRot());
        return e;
    }

    /** Feixe de laser saindo da mão do dono (dura enquanto o dono segurar / o tempo dado). */
    public static ConstructEntity spawnBeam(LivingEntity owner, int color, float damage, int life) {
        ConstructEntity e = create(owner, Kind.BEAM, color, life, damage, 1f);
        e.updateBeam(owner);
        owner.level().addFreshEntity(e);
        return e;
    }

    /** Martelo gigante que desce sobre o ponto e acerta tudo em volta. */
    public static ConstructEntity spawnHammer(LivingEntity owner, Vec3 spot, int color, float damage, float scale) {
        ConstructEntity e = create(owner, Kind.HAMMER, color, 24, damage, scale);
        e.setPos(spot.x, spot.y, spot.z);
        e.setEnd(spot);
        Vec3 d = spot.subtract(owner.position());
        e.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        owner.level().addFreshEntity(e);
        owner.level().playSound(null, spot.x, spot.y, spot.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 0.6f);
        return e;
    }

    /** Projétil (punho gigante ou rajada). */
    public static ConstructEntity spawnProjectile(LivingEntity owner, Kind kind, Vec3 dir, double speed, int color, float damage, float scale) {
        ConstructEntity e = create(owner, kind, color, 50, damage, scale);
        Vec3 start = owner.getEyePosition().add(dir.scale(0.8)).add(0, -0.25, 0);
        e.setPos(start.x, start.y, start.z);
        e.velocity = dir.normalize().scale(speed);
        e.setDeltaMovement(e.velocity);
        e.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        e.setXRot((float) -Math.toDegrees(Math.asin(Mth.clamp(dir.normalize().y, -1, 1))));
        owner.level().addFreshEntity(e);
        return e;
    }

    // ===================== Dados =====================

    public Kind kind() {
        Kind[] v = Kind.values();
        int i = entityData.get(KIND);
        return i >= 0 && i < v.length ? v[i] : Kind.BEAM;
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public void setLife(int life) {
        entityData.set(LIFE, life);
    }

    public float scale() {
        return entityData.get(SCALE);
    }

    public Vec3 end() {
        Vector3f v = entityData.get(END);
        return new Vec3(v.x, v.y, v.z);
    }

    public void setEnd(Vec3 v) {
        entityData.set(END, new Vector3f((float) v.x, (float) v.y, (float) v.z));
    }

    @Nullable
    public LivingEntity owner() {
        Entity e = level().getEntity(entityData.get(OWNER));
        return e instanceof LivingEntity le ? le : null;
    }

    @Nullable
    public Entity target() {
        int id = entityData.get(TARGET);
        return id < 0 ? null : level().getEntity(id);
    }

    public void setTarget(Entity e) {
        entityData.set(TARGET, e.getId());
    }

    public int age() {
        return tickCount;
    }

    // ===================== Tick =====================

    @Override
    public void tick() {
        super.tick();
        LivingEntity owner = owner();
        switch (kind()) {
            case BEAM -> {
                if (owner != null) updateBeam(owner);
            }
            case SHIELD, BLADE -> {
                // escudo grande (Bastião) fica parado onde foi criado
                if (owner != null && !(kind() == Kind.SHIELD && scale() >= 2.5f)) setPos(owner.getX(), owner.getY(), owner.getZ());
            }
            case FIST, BOLT, RAM -> {
                if (!level().isClientSide) moveProjectile(owner);
                else {
                    setPos(getX() + getDeltaMovement().x, getY() + getDeltaMovement().y, getZ() + getDeltaMovement().z);
                    rastro();
                }
            }
            case STREAM -> {
                if (owner != null) updateStream(owner);
            }
            case CRYSTAL, TENDRIL -> {
                Entity t = target();
                if (t != null && kind() == Kind.CRYSTAL) setPos(t.getX(), t.getY(), t.getZ());
                if (t != null && kind() == Kind.TENDRIL) setEnd(t.position().add(0, t.getBbHeight() * 0.55, 0));
            }
            default -> {}
        }
        if (level().isClientSide) {
            efeitosCliente();
            return;
        }

        if (owner == null || !owner.isAlive()) {
            if (kind() != Kind.FIST && kind() != Kind.BOLT && kind() != Kind.HAMMER && kind() != Kind.RAM && kind() != Kind.DEADHANDS && kind() != Kind.DOOR
                    && kind() != Kind.FEAR && kind() != Kind.NOVA) {
                discard();
                return;
            }
        }
        switch (kind()) {
            case BEAM -> {
                if (tickCount % 4 == 0 && owner != null) beamHit(owner);
            }
            case HAMMER -> {
                if (tickCount == 11) hammerImpact(owner);
            }
            case BLADE -> {
                if (tickCount % 8 == 0 && owner != null) bladeHit(owner);
            }
            case GRAPPLE -> {
                if (owner != null) grapplePull(owner);
            }
            case HAND -> {
                if (owner != null) handPull(owner);
            }
            case STREAM -> {
                if (tickCount % 4 == 0 && owner != null) streamHit(owner);
            }
            case CRYSTAL -> crystalHold(owner);
            case TENDRIL -> {
                if (owner != null && tickCount % 10 == 5) tendrilDrain(owner);
            }
            case DEADHANDS -> {
                if (tickCount % 10 == 0) deadHands(owner);
            }
            default -> {}
        }
        if (tickCount >= life()) {
            if (kind() == Kind.CRYSTAL && target() instanceof LivingEntity le) {
                le.addEffect(new net.minecraft.world.effect.MobEffectInstance(ModEfeitos.ENCANTO.get(), 200, 0, false, false, true));
                if (le instanceof Player) le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 200, 1));
                ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, le.getX(), le.getY() + le.getBbHeight(), le.getZ(), 6, 0.4, 0.3, 0.4, 0);
                level().playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.2f, 1.2f);
            }
            discard();
        }
    }

    // ===================== Construtos das Tropas =====================

    /** Feixe de napalm (Vermelho): cone que acompanha o olhar do dono. */
    public static ConstructEntity spawnStream(LivingEntity owner, int color, float damage, int life) {
        ConstructEntity e = create(owner, Kind.STREAM, color, life, damage, 1f);
        e.updateStream(owner);
        owner.level().addFreshEntity(e);
        return e;
    }

    void updateStream(LivingEntity owner) {
        Vec3 look = owner.getViewVector(1f);
        Vec3 mouth = owner.getEyePosition().add(look.scale(0.6)).add(0, -0.25, 0);
        setPos(mouth.x, mouth.y, mouth.z);
        Vec3 far = owner.getEyePosition().add(look.scale(8));
        BlockHitResult block = level().clip(new ClipContext(owner.getEyePosition(), far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        setEnd(block.getType() == HitResult.Type.MISS ? far : block.getLocation());
    }

    private void streamHit(LivingEntity owner) {
        Vec3 from = position(), to = end();
        Vec3 axis = to.subtract(from);
        double len = axis.length();
        if (len < 0.5) return;
        Vec3 dir = axis.scale(1 / len);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(2.5), x -> inimigo(owner, x))) {
            Vec3 rel = e.position().add(0, e.getBbHeight() / 2, 0).subtract(from);
            double t = rel.dot(dir);
            if (t < 0 || t > len + 1) continue;
            double raio = 0.6 + t * 0.28;
            if (rel.subtract(dir.scale(t)).length() > raio + e.getBbWidth() / 2) continue;
            com.lanterna.combate.Equilibrio.ferirComPoder(e, source(owner), damage);
            e.invulnerableTime = 0;
            e.setSecondsOnFire(4);
        }
        navesNaArea(owner, new AABB(to, to).inflate(1.8), damage);
        ServerLevel sl = (ServerLevel) level();
        sl.sendParticles(ParticleTypes.FLAME, to.x, to.y, to.z, 4, 0.4, 0.4, 0.4, 0.03);
        if (tickCount % 12 == 0) level().playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.7f, 0.7f);
    }

    /** Cristal do Amor (Violeta): prende o alvo; ao quebrar, deixa Encanto. */
    private void crystalHold(@Nullable LivingEntity owner) {
        if (!(target() instanceof LivingEntity t) || !t.isAlive()) {
            discard();
            return;
        }
        t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
        t.hurtMarked = true;
        if (t instanceof net.minecraft.world.entity.Mob m) m.setTarget(null);
        if (tickCount % 20 == 0 && owner != null) com.lanterna.combate.Equilibrio.ferirComPoder(t, source(owner), damage);
    }

    /** Garra da Avareza (Laranja): suga a vida (e a carga do anel) do alvo para o dono. */
    private void tendrilDrain(LivingEntity owner) {
        if (!(target() instanceof LivingEntity t) || !t.isAlive()) {
            discard();
            return;
        }
        if (com.lanterna.combate.Equilibrio.ferirComPoder(t, source(owner), damage)) owner.heal(damage * 0.5f);
        t.invulnerableTime = 0;
        if (t instanceof net.minecraft.server.level.ServerPlayer vp && owner instanceof net.minecraft.server.level.ServerPlayer op) {
            var rv = com.lanterna.capability.ModCapabilities.get(vp).orElse(null);
            var ro = com.lanterna.capability.ModCapabilities.get(op).orElse(null);
            if (rv != null && ro != null && rv.hasRing() && ro.hasRing()) {
                int roubo = Math.min(40, rv.charge());
                com.lanterna.power.RingPowers.setCharge(vp, rv, rv.charge() - roubo);
                com.lanterna.power.RingPowers.setCharge(op, ro, Math.min(ro.maxCharge(), ro.charge() + roubo));
            }
        }
        Vec3 p = t.position().add(0, t.getBbHeight() * 0.5, 0);
        ((ServerLevel) level()).sendParticles(dust(), p.x, p.y, p.z, 6, 0.2, 0.2, 0.2, 0);
    }

    /** Mãos dos Mortos (Preto): brotam do chão, prendem, secam e devolvem vida ao dono. */
    private void deadHands(@Nullable LivingEntity owner) {
        Vec3 c = end();
        double r = 4.0 * scale();
        ServerLevel sl = (ServerLevel) level();
        sl.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y + 0.3, c.z, 6, r * 0.5, 0.2, r * 0.5, 0.01);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r, 2, r), x -> inimigo(owner, x))) {
            if (e.position().distanceTo(c) > r + 0.5) continue;
            e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 30, 3));
            e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, 60, 1));
            if (owner != null && com.lanterna.combate.Equilibrio.ferirComPoder(e, source(owner), damage)) owner.heal(damage * 0.3f);
            e.invulnerableTime = 0;
        }
        if (tickCount % 30 == 0) level().playSound(null, c.x, c.y, c.z, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.5f, 0.6f);
    }

    /** Pode ferir este? (não acerta o dono, aliados da mesma guilda/Tropa nem bichos do dono) */
    public static boolean inimigo(@Nullable LivingEntity owner, LivingEntity e) {
        if (e == owner || !e.isAlive() || e.isSpectator()) return false;
        if (e instanceof net.minecraft.world.entity.decoration.ArmorStand) return false;
        if (owner instanceof Player op) {
            if (e instanceof Player p && (p.isCreative() || com.lanterna.guilda.Guildas.mesmaGuilda(op, p))) return false;
            if (e instanceof com.lanterna.especies.LanternaEntity l && l.aliadoDe(op)) return false;
            if (e instanceof net.minecraft.world.entity.TamableAnimal ta && op.getUUID().equals(ta.getOwnerUUID())) return false;
            if (e instanceof net.minecraft.world.entity.npc.AbstractVillager && !(e instanceof com.lanterna.especies.LanternaEntity)) return false;
        }
        return true;
    }

    // ---- Efeitos só de tela (rastros e faíscas) ----

    private void rastro() {
        int c = color();
        Vec3 v = getDeltaMovement();
        float s = kind() == Kind.BOLT ? 0.7f : 1.3f;
        for (int i = 0; i < (kind() == Kind.BOLT ? 1 : 3); i++) {
            double k = random.nextDouble();
            level().addParticle(new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f), s),
                    getX() - v.x * k + (random.nextDouble() - 0.5) * 0.4 * scale(), getY() - v.y * k + (random.nextDouble() - 0.5) * 0.4 * scale(),
                    getZ() - v.z * k + (random.nextDouble() - 0.5) * 0.4 * scale(), 0, 0, 0);
        }
    }

    private void efeitosCliente() {
        int c = color();
        DustParticleOptions d = new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f), 1.1f);
        switch (kind()) {
            case BEAM -> {
                // faíscas e brilho onde o laser bate
                Vec3 e = end();
                if (tickCount % 2 == 0) level().addParticle(d, e.x + (random.nextDouble() - 0.5) * 0.3, e.y + (random.nextDouble() - 0.5) * 0.3,
                        e.z + (random.nextDouble() - 0.5) * 0.3, 0, 0, 0);
                if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.ELECTRIC_SPARK, e.x, e.y, e.z,
                        (random.nextDouble() - 0.5) * 0.4, random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.4);
            }
            case STREAM -> {
                Vec3 a = position(), b = end();
                for (int i = 0; i < 3; i++) {
                    double t = random.nextDouble();
                    Vec3 p = a.lerp(b, t);
                    double r = 0.2 + t * 1.2;
                    level().addParticle(random.nextInt(3) == 0 ? ParticleTypes.FLAME : d, p.x + (random.nextDouble() - 0.5) * r,
                            p.y + (random.nextDouble() - 0.5) * r, p.z + (random.nextDouble() - 0.5) * r, 0, 0.02, 0);
                }
            }
            case DEADHANDS -> {
                Vec3 e = end();
                if (tickCount % 2 == 0) level().addParticle(ParticleTypes.SMOKE, e.x + (random.nextDouble() - 0.5) * 7 * scale(), e.y + 0.1,
                        e.z + (random.nextDouble() - 0.5) * 7 * scale(), 0, 0.03, 0);
            }
            case DOOR -> {
                // faíscas sendo puxadas para o meio da porta
                double yaw = Math.toRadians(getYRot());
                double sx = Math.cos(yaw), sz = Math.sin(yaw);
                for (int i = 0; i < 2; i++) {
                    double ox = (random.nextDouble() - 0.5) * 3.2, oy = random.nextDouble() * 3.4;
                    double px = getX() + sx * ox, pz = getZ() + sz * ox, py = getY() + oy;
                    level().addParticle(d, px, py, pz, (getX() - px) * 0.08, (getY() + 1.7 - py) * 0.08, (getZ() - pz) * 0.08);
                }
            }
            case CRYSTAL -> {
                if (tickCount % 3 == 0) level().addParticle(ParticleTypes.END_ROD, getX() + (random.nextDouble() - 0.5) * 1.6,
                        getY() + random.nextDouble() * 2.2, getZ() + (random.nextDouble() - 0.5) * 1.6, 0, 0.01, 0);
            }
            default -> {}
        }
    }

    // ---- Naves (piratas, cargueiros, capitânias): o anel também fura casco ----

    /** Energia do anel contra casco de nave rende mais (senão uma capitânia levaria minutos). */
    private static final float CONTRA_NAVE = 2.2f;

    private static boolean naveAlvo(@Nullable LivingEntity owner, com.lanterna.naves.NaveEntity n) {
        if (n.isRemoved() || n.tipo().destrocos()) return false;
        if (owner == null) return true;
        if (n.hasPassenger(owner) || owner.getVehicle() == n) return false;
        return !(owner instanceof Player p && p.getUUID().equals(n.dono()));
    }

    private static com.lanterna.naves.NaveEntity naveDe(Entity e) {
        return e instanceof com.lanterna.naves.NavePart p ? p.getParent() : e instanceof com.lanterna.naves.NaveEntity n ? n : null;
    }

    /** Primeira nave cortada pela linha: fere (se dmg > 0) e devolve o ponto atingido, ou null. */
    @Nullable
    Vec3 naveNaLinha(@Nullable LivingEntity owner, Vec3 from, Vec3 to, double inflate, float dmg) {
        Entity best = null;
        Vec3 bestP = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : level().getEntities((Entity) null, new AABB(from, to).inflate(inflate + 1), x -> naveDe(x) != null)) {
            if (!naveAlvo(owner, naveDe(e))) continue;
            var clip = e.getBoundingBox().inflate(inflate).clip(from, to);
            if (clip.isEmpty()) continue;
            double d = clip.get().distanceToSqr(from);
            if (d < bd) { bd = d; best = e; bestP = clip.get(); }
        }
        if (best != null && dmg > 0 && !level().isClientSide) {
            best.hurt(source(owner), dmg * CONTRA_NAVE);
            ServerLevel sl = (ServerLevel) level();
            sl.sendParticles(ParticleTypes.LAVA, bestP.x, bestP.y, bestP.z, 2, 0.3, 0.3, 0.3, 0);
            sl.sendParticles(dust(), bestP.x, bestP.y, bestP.z, 10, 0.4, 0.4, 0.4, 0.1);
        }
        return bestP;
    }

    /** Fere (uma vez cada) as naves que encostam na área. */
    void navesNaArea(@Nullable LivingEntity owner, AABB box, float dmg) {
        if (level().isClientSide || dmg <= 0) return;
        java.util.Set<com.lanterna.naves.NaveEntity> feridas = new java.util.HashSet<>();
        for (Entity e : level().getEntities((Entity) null, box, x -> naveDe(x) != null)) {
            var n = naveDe(e);
            if (naveAlvo(owner, n) && feridas.add(n)) n.hurt(source(owner), dmg * CONTRA_NAVE);
        }
    }

    // ---- Laser ----

    void updateBeam(LivingEntity owner) {
        Vec3 look = owner.getViewVector(1f);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 hand = owner.getEyePosition().add(right.scale(0.35)).add(0, -0.35, 0).add(look.scale(0.4));
        setPos(hand.x, hand.y, hand.z);
        Vec3 far = owner.getEyePosition().add(look.scale(48));
        BlockHitResult block = level().clip(new ClipContext(owner.getEyePosition(), far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
        LivingEntity hit = firstEntityOnLine(owner, owner.getEyePosition(), stop);
        Vec3 end = hit != null ? hit.position().add(0, hit.getBbHeight() * 0.6, 0) : stop;
        Vec3 nave = naveNaLinha(owner, owner.getEyePosition(), end, 0.2, 0);
        setEnd(nave != null ? nave : end);
    }

    private void beamHit(LivingEntity owner) {
        Vec3 stop = end();
        LivingEntity hit = firstEntityOnLine(owner, owner.getEyePosition(), stop.add(owner.getViewVector(1f).scale(0.5)));
        ServerLevel sl = (ServerLevel) level();
        sl.sendParticles(dust(), stop.x, stop.y, stop.z, 4, 0.1, 0.1, 0.1, 0);
        if (hit != null) {
            com.lanterna.combate.Equilibrio.ferirComPoder(hit, source(owner), damage);
            hit.invulnerableTime = 0;
            sl.sendParticles(ParticleTypes.CRIT, stop.x, stop.y, stop.z, 3, 0.2, 0.2, 0.2, 0.1);
        } else {
            naveNaLinha(owner, owner.getEyePosition(), stop.add(owner.getViewVector(1f).scale(0.6)), 0.3, damage);
        }
        if (tickCount % 12 == 0) level().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.8f, 2f);
    }

    @Nullable
    private LivingEntity firstEntityOnLine(LivingEntity owner, Vec3 from, Vec3 to) {
        AABB box = new AABB(from, to).inflate(1.0);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, e -> e != owner && e.isAlive() && !e.isSpectator())) {
            var clip = e.getBoundingBox().inflate(0.3).clip(from, to);
            if (clip.isPresent()) {
                double d = clip.get().distanceToSqr(from);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
        }
        return best;
    }

    // ---- Martelo ----

    private void hammerImpact(@Nullable LivingEntity owner) {
        Vec3 c = end();
        float radius = 3.5f * scale();
        ServerLevel sl = (ServerLevel) level();
        sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 3, radius * 0.4, 0.2, radius * 0.4, 0);
        sl.sendParticles(dust(), c.x, c.y + 0.3, c.z, 80, radius * 0.6, 0.3, radius * 0.6, 0.1);
        level().playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.5f, 0.5f);
        level().playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.3f);
        navesNaArea(owner, new AABB(c, c).inflate(radius, radius, radius), damage);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius, radius, radius))) {
            if (e == owner || !e.isAlive()) continue;
            double d = e.position().distanceTo(c);
            if (d > radius + e.getBbWidth()) continue;
            com.lanterna.combate.Equilibrio.ferirComPoder(e, source(owner), damage * (float) Math.max(0.4, 1.0 - d / (radius * 1.6)));
            Vec3 push = e.position().subtract(c).normalize();
            e.push(push.x * 1.2, 0.6, push.z * 1.2);
            e.hurtMarked = true;
        }
    }

    // ---- Punho e rajada ----

    private void moveProjectile(@Nullable LivingEntity owner) {
        Vec3 from = position();
        Vec3 to = from.add(velocity);
        BlockHitResult block = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        float size = (kind() == Kind.FIST ? 0.7f : kind() == Kind.RAM ? 1.3f : 0.3f) * scale();
        // Bateu numa nave antes do bloco: fere o casco e acaba aqui
        Vec3 naveHit = naveNaLinha(owner, from, stop, size * 0.6, 0);
        if (naveHit != null) {
            LivingEntity antes = null;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(from, naveHit).inflate(size + 0.5), x -> inimigo(owner, x))) {
                if (e.getBoundingBox().inflate(size).clip(from, naveHit).isPresent()) { antes = e; break; }
            }
            if (antes == null) {
                naveNaLinha(owner, from, stop, size * 0.6, damage * (kind() == Kind.RAM ? 1.5f : 1f));
                ServerLevel sln = (ServerLevel) level();
                sln.sendParticles(kind() == Kind.BOLT ? ParticleTypes.CRIT : ParticleTypes.EXPLOSION, naveHit.x, naveHit.y, naveHit.z, kind() == Kind.BOLT ? 6 : 2, 0.3, 0.3, 0.3, 0);
                level().playSound(null, naveHit.x, naveHit.y, naveHit.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, kind() == Kind.BOLT ? 0.4f : 1.0f, 1.4f);
                discard();
                return;
            }
        }
        if (kind() == Kind.RAM) {
            ServerLevel sl0 = (ServerLevel) level();
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(from, stop).inflate(size + 0.5), x -> inimigo(owner, x))) {
                if (atravessados.contains(e.getId()) || e.getBoundingBox().inflate(size).clip(from, stop).isEmpty()) continue;
                atravessados.add(e.getId());
                com.lanterna.combate.Equilibrio.ferirComPoder(e, source(owner), damage);
                Vec3 push = velocity.normalize().scale(2.6);
                e.push(push.x, 0.7, push.z);
                e.hurtMarked = true;
                sl0.sendParticles(dust(), e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 30, 0.5, 0.5, 0.5, 0.12);
                level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.3f, 0.5f);
            }
            if (block.getType() != HitResult.Type.MISS) {
                sl0.sendParticles(ParticleTypes.EXPLOSION, stop.x, stop.y, stop.z, 2, 0.3, 0.3, 0.3, 0);
                sl0.sendParticles(dust(), stop.x, stop.y, stop.z, 40, 0.8, 0.8, 0.8, 0.15);
                level().playSound(null, stop.x, stop.y, stop.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.4f);
                discard();
                return;
            }
            setPos(to.x, to.y, to.z);
            setDeltaMovement(velocity);
            return;
        }
        LivingEntity hit = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(from, stop).inflate(size + 0.5),
                e -> e != owner && e.isAlive() && !e.isSpectator())) {
            var clip = e.getBoundingBox().inflate(size).clip(from, stop);
            if (clip.isPresent() && clip.get().distanceToSqr(from) < best) {
                best = clip.get().distanceToSqr(from);
                hit = e;
            }
        }
        ServerLevel sl = (ServerLevel) level();
        if (hit != null) {
            com.lanterna.combate.Equilibrio.ferirComPoder(hit, source(owner), damage);
            Vec3 push = velocity.normalize().scale(kind() == Kind.FIST ? 1.8 : 0.4);
            hit.push(push.x, kind() == Kind.FIST ? 0.5 : 0.1, push.z);
            hit.hurtMarked = true;
            sl.sendParticles(dust(), hit.getX(), hit.getY() + hit.getBbHeight() / 2, hit.getZ(), 30, 0.4, 0.4, 0.4, 0.1);
            level().playSound(null, hit.getX(), hit.getY(), hit.getZ(),
                    kind() == Kind.FIST ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.2f, 0.8f);
            discard();
            return;
        }
        if (block.getType() != HitResult.Type.MISS) {
            sl.sendParticles(dust(), stop.x, stop.y, stop.z, 16, 0.2, 0.2, 0.2, 0.05);
            level().playSound(null, stop.x, stop.y, stop.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.8f, 1.2f);
            discard();
            return;
        }
        setPos(to.x, to.y, to.z);
        setDeltaMovement(velocity);
    }

    // ---- Lâminas ----

    private void bladeHit(LivingEntity owner) {
        double r = 3.2 * scale();
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(r, 1, r))) {
            if (e == owner || !e.isAlive() || e.distanceTo(owner) > r + 0.5) continue;
            if (e instanceof Player p && owner instanceof Player) continue;
            com.lanterna.combate.Equilibrio.ferirComPoder(e, source(owner), damage);
            e.invulnerableTime = 0;
        }
        navesNaArea(owner, owner.getBoundingBox().inflate(r, 1, r), damage);
        if (tickCount % 16 == 0) level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 1.4f);
    }

    // ---- Gancho ----

    private void grapplePull(LivingEntity owner) {
        Vec3 target = end();
        Vec3 d = target.subtract(owner.position().add(0, 1, 0));
        if (d.length() < 2.0 || tickCount > 40) {
            discard();
            return;
        }
        Vec3 v = d.normalize().scale(Math.min(1.6, 0.6 + tickCount * 0.08));
        owner.setDeltaMovement(v);
        owner.hurtMarked = true;
        owner.fallDistance = 0;
    }

    // ---- Mão ----

    private void handPull(LivingEntity owner) {
        Entity t = target();
        if (t == null || !t.isAlive()) {
            discard();
            return;
        }
        Vec3 front = owner.getEyePosition().add(owner.getViewVector(1f).scale(2.2)).add(0, -0.8, 0);
        if (!grabbed) {
            // A mão vai até o alvo
            Vec3 now = end();
            Vec3 goal = t.position().add(0, t.getBbHeight() * 0.5, 0);
            Vec3 step = goal.subtract(now);
            double len = step.length();
            setEnd(len < 1.5 ? goal : now.add(step.scale(1.5 / len)));
            if (len < 1.5) {
                grabbed = true;
                level().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1f, 0.6f);
            }
        } else {
            // ...e volta trazendo o alvo
            Vec3 d = front.subtract(t.position());
            if (d.length() < 1.2) {
                if (t instanceof LivingEntity le) com.lanterna.combate.Equilibrio.ferirComPoder(le, source(owner), damage);
                discard();
                return;
            }
            Vec3 v = d.normalize().scale(Math.min(1.2, d.length() * 0.35));
            t.setDeltaMovement(v);
            t.hurtMarked = true;
            t.fallDistance = 0;
            setEnd(t.position().add(0, t.getBbHeight() * 0.5, 0));
        }
    }

    // ===================== Ajudantes =====================

    private DamageSource source(@Nullable LivingEntity owner) {
        if (owner instanceof Player p) return damageSources().playerAttack(p);
        if (owner != null) return damageSources().mobAttack(owner);
        return damageSources().magic();
    }

    private DustParticleOptions dust() {
        int c = color();
        return new DustParticleOptions(new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f), 1.4f);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
