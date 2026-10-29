package com.lanterna.lore.entity.boss;

import com.lanterna.lore.LoreEntities;
import com.lanterna.lore.LoreWorldData;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.MissionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Base dos bosses da lore: barra de boss, duas (ou três) fases, ataque corpo a corpo,
 * golpes especiais com tempo de recarga, coleira na arena e recompensas.
 *
 * Um boss pode nascer como "eco" (o Devorador de Ecos e o Nulo revivem bosses já derrotados):
 * versão preta, menor e mais fraca, sem recompensas.
 */
public abstract class LoreBoss extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(LoreBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ECHO = SynchedEntityData.defineId(LoreBoss.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(LoreBoss.class, EntityDataSerializers.INT);
    private static final UUID ECHO_HEALTH = UUID.fromString("0f3a8c2e-5d4b-4e1a-9b7c-2c1d0e9f8a71");

    protected static final RawAnimation IDLE = RawAnimation.begin().thenLoop("misc.idle");
    protected static final RawAnimation WALK = RawAnimation.begin().thenLoop("move.walk");
    protected static final RawAnimation PHASE2 = RawAnimation.begin().thenLoop("misc.phase2");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    protected final ServerBossEvent bar;
    @Nullable protected BlockPos home;
    protected final Set<UUID> fighters = new HashSet<>();
    protected int specialCooldown = 60;
    protected int meleeCooldown;
    private int alone;

    protected LoreBoss(EntityType<? extends Monster> type, Level level, BossEvent.BossBarColor color) {
        super(type, level);
        bar = new ServerBossEvent(getDisplayName(), color, BossEvent.BossBarOverlay.NOTCHED_10);
        bar.setDarkenScreen(true);
        xpReward = 300;
        setPersistenceRequired();
    }

    /** Id do boss (= nome da entidade, ex: "vorgath"). */
    public String bossId() {
        var key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(getType());
        return key == null ? "boss" : key.getPath();
    }

    public static AttributeSupplier.Builder attributes(double health, double damage, double speed) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, health)
                .add(Attributes.ATTACK_DAMAGE, damage)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.ARMOR, 8);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE, 1);
        entityData.define(ECHO, false);
        entityData.define(STATE, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 32f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // ===================== Estado =====================

    public int phase() {
        return entityData.get(PHASE);
    }

    protected void setPhase(int p) {
        entityData.set(PHASE, p);
    }

    public boolean isEcho() {
        return entityData.get(ECHO);
    }

    public int state() {
        return entityData.get(STATE);
    }

    protected void setState(int s) {
        entityData.set(STATE, s);
    }

    public void setHome(BlockPos pos) {
        home = pos;
    }

    /** Transforma em eco (versão preta e fraca, revivida pelo Devorador ou pelo Nulo). */
    public void makeEcho() {
        entityData.set(ECHO, true);
        AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.addPermanentModifier(new AttributeModifier(ECHO_HEALTH, "eco", -0.75, AttributeModifier.Operation.MULTIPLY_TOTAL));
            setHealth(getMaxHealth());
        }
        AttributeInstance dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.addPermanentModifier(new AttributeModifier(ECHO_HEALTH, "eco", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL));
        setCustomName(Component.translatable("entity.lanterna.echo_of", getType().getDescription()));
        xpReward = 10;
        refreshDimensions();
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return super.getDimensions(pose).scale(isEcho() ? 0.6f : 1f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ECHO.equals(key)) refreshDimensions();
    }

    // ===================== Tick =====================

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        bar.setProgress(getHealth() / getMaxHealth());
        if (meleeCooldown > 0) meleeCooldown--;
        if (specialCooldown > 0) specialCooldown--;

        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(48))) {
            if (!p.isSpectator() && !p.isCreative()) fighters.add(p.getUUID());
        }
        // Coleira: o boss não sai da arena
        if (home != null && distanceToSqr(Vec3.atCenterOf(home)) > 45 * 45) {
            teleportTo(home.getX() + 0.5, home.getY() + 1, home.getZ() + 0.5);
        }
        // Ninguém por perto há muito tempo: a luta acaba (dá para chamar de novo no altar)
        boolean anyone = level().getNearestPlayer(this, 80) != null;
        alone = anyone ? 0 : alone + 1;
        if (alone > 1200) {
            discard();
            return;
        }
        int p = phase();
        if (p == 1 && getHealth() <= getMaxHealth() * phase2At()) {
            setPhase(2);
            onPhaseChange(2);
        } else if (p == 2 && hasPhase3() && getHealth() <= getMaxHealth() * 0.33f) {
            setPhase(3);
            onPhaseChange(3);
        }
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) {
            getLookControl().setLookAt(target, 30, 30);
            bossTick(target);
        } else {
            idleTick();
        }
    }

    protected float phase2At() {
        return 0.5f;
    }

    protected boolean hasPhase3() {
        return false;
    }

    /** Muda de fase: cada boss faz a sua mudança. */
    protected void onPhaseChange(int phase) {
        level().playSound(null, blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.2f, 0.7f);
        ((ServerLevel) level()).sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
        for (Player p : playersNear(48)) {
            p.displayClientMessage(Component.translatable("boss.lanterna." + bossId() + ".phase" + phase)
                    .withStyle(s -> s.withColor(barRgb()).withBold(true)), true);
        }
    }

    /** Ataques do boss com um alvo. */
    protected abstract void bossTick(LivingEntity target);

    protected void idleTick() {}

    /** Golpe corpo a corpo com alcance do tamanho do boss. */
    protected boolean tryMelee(LivingEntity target, double extraReach, int cooldown) {
        if (meleeCooldown > 0) return false;
        double reach = getBbWidth() * 0.8 + target.getBbWidth() + extraReach;
        if (distanceToSqr(target) > reach * reach) return false;
        meleeCooldown = cooldown;
        triggerAnim("attack", "strike");
        return doHurtTarget(target);
    }

    protected void chase(LivingEntity target, double speed) {
        if (tickCount % 10 == 0) getNavigation().moveTo(target, speed);
    }

    protected List<Player> playersNear(double radius) {
        return level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(radius), p -> !p.isSpectator() && !p.isCreative());
    }

    /** Dano em área ao redor de um ponto (não acerta o próprio boss nem outros bosses). */
    protected void areaDamage(Vec3 center, double radius, float damage, double knockback) {
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(center, center).inflate(radius))) {
            if (e == this || e instanceof LoreBoss) continue;
            double d = e.position().distanceTo(center);
            if (d > radius) continue;
            e.hurt(damageSources().mobAttack(this), damage * (float) (1.0 - d / (radius * 1.5)));
            Vec3 push = e.position().subtract(center).normalize().scale(knockback);
            e.push(push.x, 0.4 * knockback, push.z);
            e.hurtMarked = true;
        }
    }

    // ===================== Dano =====================

    /** Multiplicador de dano recebido (0 = imune). Cada boss tem as suas regras. */
    protected float damageTaken(DamageSource source, float amount) {
        return amount;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (source.getEntity() instanceof LoreBoss) return false;
        float real = damageTaken(source, amount);
        if (real <= 0) return false;
        if (source.getEntity() instanceof Player p) fighters.add(p.getUUID());
        return super.hurt(source, real);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    // ===================== Morte e recompensas =====================

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level().isClientSide || isEcho()) return;
        ServerLevel sl = (ServerLevel) level();
        onDefeated();
        if (!countsAsDefeat()) return;
        LoreWorldData.get(sl.getServer()).bossDefeated(bossId());
        Component name = getType().getDescription();
        for (UUID id : fighters) {
            if (sl.getPlayerByUUID(id) instanceof ServerPlayer sp && sp.distanceTo(this) < 96) {
                MissionManager.onBoss(sp, bossId());
                sp.sendSystemMessage(Component.translatable("boss.lanterna.defeated", name).withStyle(s -> s.withColor(barRgb()).withBold(true)));
            }
        }
        sl.playSound(null, blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.HOSTILE, 2f, 0.8f);
    }

    /** Um boss que se divide (Vigia Partido) só conta como derrotado quando o último cai. */
    protected boolean countsAsDefeat() {
        return true;
    }

    protected void onDefeated() {}

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (isEcho() || !countsAsDefeat()) return;
        PlanetLore p = PlanetLore.byBoss(bossId());
        if (p != null) {
            // Dois drops: um para forjar (ou melhorar a bateria) e outro para o Altar da Vida
            ItemEntityHelper.dropGlowing(this, new ItemStack(p.dropItem(), 2));
        }
        for (ItemStack extra : extraLoot()) ItemEntityHelper.dropGlowing(this, extra);
    }

    protected List<ItemStack> extraLoot() {
        return List.of();
    }

    // ===================== Barra de boss =====================

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bar.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        bar.setName(getDisplayName());
    }

    protected int barRgb() {
        PlanetLore p = PlanetLore.byBoss(bossId());
        return p != null ? p.color() : 0xFFFFFF;
    }

    // ===================== NBT =====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Phase", phase());
        tag.putBoolean("Echo", isEcho());
        tag.putInt("State", state());
        if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setPhase(Math.max(1, tag.getInt("Phase")));
        if (tag.getBoolean("Echo")) entityData.set(ECHO, true);
        setState(tag.getInt("State"));
        if (tag.contains("Home")) home = NbtUtils.readBlockPos(tag.getCompound("Home"));
        if (hasCustomName()) bar.setName(getDisplayName());
    }

    // ===================== Animações =====================

    /** Animações extras que este boss dispara (além de "strike"). */
    protected String[] extraAnims() {
        return new String[0];
    }

    protected RawAnimation moveAnim(boolean moving) {
        if (phase() >= 2 && usesPhase2Loop()) return moving ? WALK : PHASE2;
        return moving ? WALK : IDLE;
    }

    protected boolean usesPhase2Loop() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 5, state -> state.setAndContinue(moveAnim(state.isMoving()))));
        AnimationController<LoreBoss> attack = new AnimationController<>(this, "attack", 0, state -> PlayState.STOP);
        attack.triggerableAnim("strike", RawAnimation.begin().thenPlay("attack.strike"));
        for (String a : extraAnims()) attack.triggerableAnim(a, RawAnimation.begin().thenPlay(animName(a)));
        controllers.add(attack);
    }

    private static String animName(String trigger) {
        return switch (trigger) {
            case "stomp", "bite" -> "attack." + trigger;
            default -> "misc." + trigger;
        };
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ===================== Ecos =====================

    /** Faz nascer o eco de um boss (preto, menor, fraco). */
    @Nullable
    public static LoreBoss spawnEcho(ServerLevel level, String bossId, Vec3 pos, @Nullable LivingEntity target) {
        EntityType<?> type = LoreEntities.bossType(bossId);
        if (type == null) return null;
        Entity e = type.create(level);
        if (!(e instanceof LoreBoss boss)) return null;
        boss.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360, 0);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(pos)), MobSpawnType.MOB_SUMMONED, null, null);
        boss.makeEcho();
        boss.setHome(BlockPos.containing(pos));
        if (target != null) boss.setTarget(target);
        level.addFreshEntity(boss);
        level.sendParticles(ParticleTypes.SCULK_SOUL, pos.x, pos.y + 1, pos.z, 40, 1, 1, 1, 0.05);
        return boss;
    }
}
