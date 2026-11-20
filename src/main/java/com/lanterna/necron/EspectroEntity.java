package com.lanterna.necron;

import com.lanterna.construct.ConstructEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * 0.57.0: o Espectro. Quem recusa a Morte tem que enfrentar a si mesmo: a mesma pele, o mesmo rosto, mas com o traje
 * negro e o Anel da Morte. Ele só luta com o próprio dono (ninguém mais o fere e ele não ataca mais ninguém), bate
 * forte de perto e solta o laser negro de longe. Vencido, a Morte desiste daquela alma por um tempo.
 */
public class EspectroEntity extends Monster {
    private static final EntityDataAccessor<Optional<UUID>> DONO = SynchedEntityData.defineId(EspectroEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private int longe, laser = 60;

    public EspectroEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 0;
    }

    public static AttributeSupplier.Builder atributos() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 120)
                .add(Attributes.MOVEMENT_SPEED, 0.33)
                .add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DONO, Optional.empty());
    }

    @Nullable
    public UUID dono() { return entityData.get(DONO).orElse(null); }

    public void setDono(Player p) {
        entityData.set(DONO, Optional.of(p.getUUID()));
        // o Anel da Morte na mão
        setItemSlot(EquipmentSlot.MAINHAND, com.lanterna.item.PowerRingItem.create(com.lanterna.corps.BuiltinCorps.BLACK.id(),
                com.lanterna.corps.BuiltinCorps.BLACK.color(), -1));
        setDropChance(EquipmentSlot.MAINHAND, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24f));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 5, false, false,
                e -> e.getUUID().equals(dono()) && !((Player) e).isCreative() && !e.isSpectator()));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // só o dono fere o próprio espectro
        if (!level().isClientSide && source.getEntity() instanceof LivingEntity quem && !quem.getUUID().equals(dono())
                && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount % 2 == 0) level().addParticle(ParticleTypes.SCULK_SOUL, getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0, 0.03, 0);
            if (tickCount % 3 == 0) level().addParticle(ParticleTypes.SMOKE, getRandomX(0.5), getY() + 0.1, getRandomZ(0.5), 0, 0.01, 0);
            return;
        }
        ServerPlayer d = dono() == null ? null : ((ServerLevel) level()).getServer().getPlayerList().getPlayer(dono());
        // sem o dono por perto por um tempo, ele volta para a sombra (e reaparece quando o dono voltar)
        if (d == null || d.level() != level() || d.distanceToSqr(this) > 90 * 90) {
            if (++longe > 200) discard();
            return;
        }
        longe = 0;
        LivingEntity alvo = getTarget();
        if (alvo != null && --laser <= 0 && distanceTo(alvo) > 5 && distanceTo(alvo) < 22 && hasLineOfSight(alvo)) {
            laser = 90 + random.nextInt(40);
            getLookControl().setLookAt(alvo, 60, 60);
            ConstructEntity.spawnBeam(this, com.lanterna.morte.Corrupcao.COR_PORTADOR, 3.5f, 30);
            level().playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.8f, 0.5f);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide && dono() != null) Tentacao.espectroVencido((ServerLevel) level(), this);
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (dono() != null) tag.putUUID("Dono", dono());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Dono")) entityData.set(DONO, Optional.of(tag.getUUID("Dono")));
    }
}
