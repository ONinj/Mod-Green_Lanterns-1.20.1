package com.lanterna.lore.entity;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.lore.LoreWorldData;
import com.lanterna.mission.MissionManager;
import com.lanterna.power.RingPowers;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.List;

/** Criaturas da lore que não são povos: Ecos Cinzentos, Brotos e os alvos de treino. */
public final class LoreCreatures {
    static final RawAnimation IDLE = RawAnimation.begin().thenLoop("misc.idle");
    static final RawAnimation WALK = RawAnimation.begin().thenLoop("move.walk");
    static final RawAnimation STRIKE = RawAnimation.begin().thenPlay("attack.strike");

    /**
     * Eco Cinzento: criatura sem cor que vaza da rachadura do Nulo. Drena emoção: cada golpe
     * tira carga do anel e enfraquece. Aparecem mais a cada boss derrotado.
     */
    public static class Eco extends Monster implements GeoEntity {
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

        public Eco(EntityType<? extends Monster> type, Level level) {
            super(type, level);
            xpReward = 8;
        }

        public static AttributeSupplier.Builder attributes() {
            return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 26).add(Attributes.MOVEMENT_SPEED, 0.28)
                    .add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.FOLLOW_RANGE, 32);
        }

        @Override
        protected void registerGoals() {
            goalSelector.addGoal(0, new FloatGoal(this));
            goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false));
            goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
            goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10f));
            goalSelector.addGoal(8, new RandomLookAroundGoal(this));
            targetSelector.addGoal(1, new HurtByTargetGoal(this));
            targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
            targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, RaceEntity.class, 10, true, false,
                    e -> e instanceof RaceEntity r && r.race() != Race.GUARDIAO && r.role().isEmpty()));
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            triggerAnim("attack", "strike");
            boolean hit = super.doHurtTarget(target);
            if (hit && target instanceof LivingEntity le) {
                le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                if (target instanceof ServerPlayer sp) {
                    ModCapabilities.get(sp).ifPresent(d -> {
                        if (d.hasRing()) RingPowers.setCharge(sp, d, d.charge() - 25);
                    });
                }
                ((ServerLevel) level()).sendParticles(new DustParticleOptions(new Vector3f(0.5f, 0.5f, 0.52f), 1.4f),
                        target.getX(), target.getY() + 1, target.getZ(), 16, 0.3, 0.5, 0.3, 0);
            }
            return hit;
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (level().isClientSide && random.nextInt(3) == 0) {
                level().addParticle(new DustParticleOptions(new Vector3f(0.45f, 0.45f, 0.48f), 1.2f),
                        getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0, 0.01, 0);
            }
        }

        @Override
        protected boolean isSunBurnTick() {
            return false;
        }

        @Override
        protected SoundEvent getAmbientSound() {
            return SoundEvents.WARDEN_AMBIENT;
        }

        @Override
        public float getVoicePitch() {
            return 1.6f;
        }

        @Override
        protected SoundEvent getHurtSound(DamageSource source) {
            return SoundEvents.SCULK_SHRIEKER_BREAK;
        }

        @Override
        protected SoundEvent getDeathSound() {
            return SoundEvents.SOUL_ESCAPE;
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar c) {
            c.add(new AnimationController<>(this, "move", 4, s -> s.setAndContinue(s.isMoving() ? WALK : IDLE)));
            c.add(new AnimationController<>(this, "attack", 0, s -> PlayState.STOP).triggerableAnim("strike", STRIKE));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }

        /** Chance de nascer: cresce com os bosses derrotados no mundo. */
        public static boolean allowSpawn(ServerLevel level, net.minecraft.util.RandomSource random) {
            int bosses = LoreWorldData.get(level.getServer()).bossesDefeated();
            return random.nextFloat() < 0.25f + 0.1f * bosses;
        }
    }

    /**
     * Broto: fragmento do Coração Primordial. Segue o jogador e imita os pulos, foge do cinza,
     * dá presentes a quem está com a vida cheia e vive pouco (depois renasce em outro lugar).
     */
    public static class Broto extends PathfinderMob implements GeoEntity {
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        private int life = 6000 + 6000;
        private int giftCooldown = 1200;

        public Broto(EntityType<? extends PathfinderMob> type, Level level) {
            super(type, level);
            life = 6000 + random.nextInt(6000);
        }

        public static AttributeSupplier.Builder attributes() {
            return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 6).add(Attributes.MOVEMENT_SPEED, 0.32);
        }

        @Override
        protected void registerGoals() {
            goalSelector.addGoal(0, new FloatGoal(this));
            goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Eco.class, 10f, 1.2, 1.6));
            goalSelector.addGoal(1, new AvoidEntityGoal<>(this, com.lanterna.lore.entity.boss.LoreBoss.class, 16f, 1.2, 1.6));
            goalSelector.addGoal(3, new FollowPlayerGoal());
            goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
            goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8f));
        }

        @Override
        public void aiStep() {
            super.aiStep();
            if (level().isClientSide) {
                if (random.nextInt(4) == 0) level().addParticle(ParticleTypes.END_ROD, getRandomX(0.4), getRandomY(), getRandomZ(0.4), 0, 0.02, 0);
                return;
            }
            // Imita o jogador: pula quando ele pula
            Player p = level().getNearestPlayer(this, 6);
            if (p != null && !p.onGround() && p.getDeltaMovement().y > 0.2 && onGround()) jumpFromGround();
            if (--giftCooldown <= 0) {
                giftCooldown = 2400 + random.nextInt(2400);
                if (p != null && distanceTo(p) < 4 && p.getHealth() >= p.getMaxHealth()) giveGift(p);
            }
            if (--life <= 0) {
                ((ServerLevel) level()).sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.4, getZ(), 20, 0.3, 0.3, 0.3, 0.05);
                level().playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1f, 2f);
                discard();
            }
        }

        private void giveGift(Player p) {
            List<ItemStack> gifts = List.of(new ItemStack(Items.OAK_SAPLING), new ItemStack(Items.CHERRY_SAPLING),
                    new ItemStack(Items.TORCHFLOWER_SEEDS), new ItemStack(Items.PITCHER_POD), new ItemStack(Items.GLOW_BERRIES, 3),
                    new ItemStack(Items.LILY_OF_THE_VALLEY), new ItemStack(Items.SWEET_BERRIES, 4), new ItemStack(Items.MANGROVE_PROPAGULE));
            spawnAtLocation(gifts.get(random.nextInt(gifts.size())).copy());
            level().playSound(null, blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 1f, 1.6f);
        }

        @Override
        protected SoundEvent getAmbientSound() {
            return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
        }

        @Override
        public float getVoicePitch() {
            return 1.8f;
        }

        @Override
        public boolean removeWhenFarAway(double d) {
            return false;
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar c) {
            c.add(new AnimationController<>(this, "move", 4, s -> s.setAndContinue(s.isMoving() ? WALK : IDLE)));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }

        private class FollowPlayerGoal extends Goal {
            private Player target;

            FollowPlayerGoal() {
                setFlags(EnumSet.of(Flag.MOVE));
            }

            @Override
            public boolean canUse() {
                target = level().getNearestPlayer(Broto.this, 10);
                return target != null && !target.isSpectator() && distanceTo(target) > 2.5;
            }

            @Override
            public void tick() {
                getNavigation().moveTo(target, 1.1);
            }
        }
    }

    /**
     * Alvo de treino das bases: aguenta muitos golpes; cada vez que "quebra" (20 de dano) conta para a missão
     * de treino e se monta de novo.
     */
    public static class Dummy extends PathfinderMob implements GeoEntity {
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        private float damage;
        private int cooldown;

        public Dummy(EntityType<? extends PathfinderMob> type, Level level) {
            super(type, level);
            setPersistenceRequired();
        }

        public static AttributeSupplier.Builder attributes() {
            return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 100).add(Attributes.MOVEMENT_SPEED, 0)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
        }

        @Override
        protected void registerGoals() {}

        @Override
        public boolean hurt(DamageSource source, float amount) {
            if (level().isClientSide) return true;
            if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
            if (cooldown > 0) return false;
            Entity attacker = source.getEntity();
            if (!(attacker instanceof ServerPlayer sp)) return false;
            if (sp.isShiftKeyDown() && sp.isCreative()) {
                discard();
                return true;
            }
            damage += amount;
            level().playSound(null, blockPosition(), SoundEvents.ARMOR_STAND_HIT, SoundSource.NEUTRAL, 1f, 0.8f + random.nextFloat() * 0.4f);
            ((ServerLevel) level()).sendParticles(ParticleTypes.CRIT, getX(), getY() + 1.2, getZ(), 6, 0.3, 0.4, 0.3, 0.1);
            if (damage >= 20) {
                damage = 0;
                cooldown = 60;
                ((ServerLevel) level()).sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
                level().playSound(null, blockPosition(), SoundEvents.ARMOR_STAND_BREAK, SoundSource.NEUTRAL, 1.2f, 1f);
                MissionManager.onDummy(sp);
            }
            hurtTime = 10;
            return false;
        }

        @Override
        public void tick() {
            super.tick();
            if (cooldown > 0) cooldown--;
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }

        @Override
        public boolean isPushable() {
            return false;
        }

        @Override
        public boolean removeWhenFarAway(double d) {
            return false;
        }

        public boolean broken() {
            return cooldown > 0;
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar c) {
            c.add(new AnimationController<>(this, "idle", 0, s -> s.setAndContinue(IDLE)));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }
    }

    private LoreCreatures() {}
}
