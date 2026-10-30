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
    public enum Kind { BEAM, HAMMER, FIST, BOLT, SHIELD, PICKAXE, GRAPPLE, BLADE, HAND }

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
            case FIST, BOLT -> {
                if (!level().isClientSide) moveProjectile(owner);
                else setPos(getX() + getDeltaMovement().x, getY() + getDeltaMovement().y, getZ() + getDeltaMovement().z);
            }
            default -> {}
        }
        if (level().isClientSide) return;

        if (owner == null || !owner.isAlive()) {
            if (kind() != Kind.FIST && kind() != Kind.BOLT && kind() != Kind.HAMMER) {
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
            default -> {}
        }
        if (tickCount >= life()) discard();
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
        setEnd(hit != null ? hit.position().add(0, hit.getBbHeight() * 0.6, 0) : stop);
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
        float size = (kind() == Kind.FIST ? 0.7f : 0.3f) * scale();
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
