package com.lanterna.tropa;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Tiro de plasma (blasters dos piratas e canhões das naves). Voa reto, sem gravidade; o rastro é de partículas. */
public class TiroPlasma extends ThrowableProjectile {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(TiroPlasma.class, EntityDataSerializers.INT);
    private float damage = 5f;
    private boolean explosive;

    public TiroPlasma(EntityType<? extends TiroPlasma> type, Level level) {
        super(type, level);
    }

    /** Dispara de {@code from} na direção {@code dir}. */
    public static TiroPlasma shoot(Level level, Entity owner, Vec3 from, Vec3 dir, float speed, float damage, int color, boolean explosive) {
        TiroPlasma t = new TiroPlasma(TropaEntities.TIRO_PLASMA.get(), level);
        t.setOwner(owner);
        t.setPos(from.x, from.y, from.z);
        t.damage = damage;
        t.explosive = explosive;
        t.entityData.set(COLOR, color);
        t.shoot(dir.x, dir.y, dir.z, speed, 0.6f);
        level.addFreshEntity(t);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.6f, 1.6f);
        return t;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(COLOR, 0xFF3020);
    }

    @Override
    protected float getGravity() {
        return 0f;
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > 80) discard();
        if (level().isClientSide) {
            int c = entityData.get(COLOR);
            Vector3f rgb = new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
            Vec3 v = getDeltaMovement();
            for (int i = 0; i < 3; i++) {
                double k = i / 3.0;
                level().addParticle(new DustParticleOptions(rgb, 1.2f), getX() - v.x * k, getY() - v.y * k, getZ() - v.z * k, 0, 0, 0);
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        Entity target = hit.getEntity();
        Entity owner = getOwner();
        if (target == owner || (owner != null && target.isPassengerOfSameVehicle(owner))) return;
        if (owner != null && target instanceof NavePartLike part && part.naveOwner() == owner.getVehicle()) return;
        target.hurt(damageSources().indirectMagic(this, owner instanceof LivingEntity l ? l : null), damage);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            if (result.getType() == HitResult.Type.ENTITY && ((EntityHitResult) result).getEntity() == getOwner()) return;
            if (explosive) level().explode(this, getX(), getY(), getZ(), 1.6f, Level.ExplosionInteraction.NONE);
            ((net.minecraft.server.level.ServerLevel) level()).sendParticles(ParticleTypes.FLASH, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            discard();
        }
    }

    /** Peça de uma nave (para não acertar a própria nave). */
    public interface NavePartLike {
        Entity naveOwner();
    }
}
