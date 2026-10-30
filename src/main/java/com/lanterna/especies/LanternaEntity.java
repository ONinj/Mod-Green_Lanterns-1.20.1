package com.lanterna.especies;

import com.lanterna.construct.ConstructEntity;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.lore.Factions;
import com.lanterna.lore.entity.LoreCreatures;
import com.lanterna.space.SpaceBodies;
import com.lanterna.tropa.TiroPlasma;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Humanoide de qualquer uma das 18 espécies. O papel decide o que ele faz:
 *  LANTERNA  membro de uma Tropa: voa, atira o laser do anel, cria martelos; ajuda os Lanternas da mesma Tropa
 *            e briga com as Tropas inimigas e com os piratas
 *  PIRATA    ataca qualquer um com a pistola de plasma
 *  CAPITAO   o chefe pirata: mais vida, canhão de mão e tiros explosivos
 *  MERCADOR  faz trocas e foge de briga
 * Tudo (espécie, pele, roupa, patente, corrupção, papel e Tropa) é sincronizado e salvo.
 */
public class LanternaEntity extends AbstractVillager implements GeoEntity, TemEspecie {

    private static final EntityDataAccessor<String> ESPECIE = SynchedEntityData.defineId(LanternaEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> PELE = SynchedEntityData.defineId(LanternaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> ROUPA = SynchedEntityData.defineId(LanternaEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> OFICIAL = SynchedEntityData.defineId(LanternaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CORROMPIDO = SynchedEntityData.defineId(LanternaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> PAPEL = SynchedEntityData.defineId(LanternaEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> TROPA = SynchedEntityData.defineId(LanternaEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> VOANDO = SynchedEntityData.defineId(LanternaEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("misc.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("move.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("move.run");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("move.fly");
    private static final RawAnimation HOVER = RawAnimation.begin().thenLoop("move.hover");
    private static final RawAnimation EXTRA = RawAnimation.begin().thenLoop("misc.extra");
    public static final String[] ACOES = {"attack.punch", "attack.beam", "cast.construct", "cast.shield", "misc.salute",
            "misc.oath", "misc.talk", "misc.hurt", "misc.death"};

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Jogadores que atacaram este NPC (ele revida até esquecer). */
    private final Set<UUID> angry = new HashSet<>();
    private int shieldTicks;
    private boolean configured;

    public LanternaEntity(EntityType<? extends AbstractVillager> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return AbstractVillager.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.FLYING_SPEED, 0.6);
    }

    // ===================== Criação =====================

    /** Um Lanterna da Tropa (o Preto sai de qualquer espécie, corrompido). */
    public static LanternaEntity lanterna(ServerLevel level, BuiltinCorps corps, boolean oficial) {
        LanternaEntity e = new LanternaEntity(EspeciesEntities.LANTERNA_NPC.get(), level);
        boolean black = corps == BuiltinCorps.BLACK;
        Especie esp = black ? Especie.paraTropa(BuiltinCorps.values()[level.random.nextInt(7)], level.random)
                : Especie.paraTropa(corps, level.random);
        e.setup(esp, 1 + level.random.nextInt(3), Especie.Papel.LANTERNA, corps, "roupa_tropa_" + corps.id().getPath(), oficial, black);
        return e;
    }

    public static LanternaEntity pirata(ServerLevel level, boolean capitao) {
        LanternaEntity e = new LanternaEntity(EspeciesEntities.LANTERNA_NPC.get(), level);
        Especie esp = Especie.paraPapel(Especie.Papel.PIRATA, level.random);
        e.setup(esp, 1 + level.random.nextInt(3), capitao ? Especie.Papel.CAPITAO : Especie.Papel.PIRATA, null,
                capitao ? "roupa_capitao" : "roupa_pirata", false, false);
        return e;
    }

    public static LanternaEntity mercador(ServerLevel level) {
        LanternaEntity e = new LanternaEntity(EspeciesEntities.LANTERNA_NPC.get(), level);
        e.setup(Especie.paraPapel(Especie.Papel.MERCADOR, level.random), 1 + level.random.nextInt(3), Especie.Papel.MERCADOR,
                null, "roupa_mercador", false, false);
        return e;
    }

    /** Define tudo de uma vez e ajusta a vida e o dano ao papel. */
    public void setup(Especie esp, int pele, Especie.Papel papel, @Nullable BuiltinCorps corps, @Nullable String roupa,
                      boolean oficial, boolean corrompido) {
        entityData.set(ESPECIE, esp.id);
        entityData.set(PELE, Mth.clamp(pele, 1, 3));
        entityData.set(PAPEL, papel.id);
        entityData.set(TROPA, corps == null ? "" : corps.id().getPath());
        entityData.set(ROUPA, roupa == null ? "" : roupa);
        entityData.set(OFICIAL, oficial);
        entityData.set(CORROMPIDO, corrompido);
        configured = true;
        applyStats();
        refreshDimensions();
    }

    private void applyStats() {
        Especie esp = especie();
        double hp = esp.corpo.health;
        double dmg = 4;
        switch (papel()) {
            case PIRATA -> { hp *= 0.8; dmg = 3; }
            case CAPITAO -> { hp *= 2.6; dmg = 7; }
            case MERCADOR -> { hp *= 0.7; dmg = 1; }
            case LANTERNA -> { if (isOficial()) { hp *= 1.6; dmg = 6; } }
        }
        var maxHp = getAttribute(Attributes.MAX_HEALTH);
        if (maxHp != null) maxHp.setBaseValue(hp);
        var atk = getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(dmg);
        setHealth((float) hp);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data, tag);
        if (!configured) {
            // Ovo genérico: um Lanterna Verde de qualquer espécie
            RandomSource r = level.getRandom();
            BuiltinCorps c = BuiltinCorps.values()[r.nextInt(8)];
            Especie esp = Especie.paraTropa(c, r);
            setup(esp, 1 + r.nextInt(3), Especie.Papel.LANTERNA, c, "roupa_tropa_" + c.id().getPath(), r.nextInt(5) == 0,
                    c == BuiltinCorps.BLACK);
        }
        return result;
    }

    // ===================== Dados =====================

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ESPECIE, "ilith");
        entityData.define(PELE, 1);
        entityData.define(ROUPA, "");
        entityData.define(OFICIAL, false);
        entityData.define(CORROMPIDO, false);
        entityData.define(PAPEL, "lanterna");
        entityData.define(TROPA, "green");
        entityData.define(VOANDO, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ESPECIE.equals(key)) refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        Especie.Corpo c = especie().corpo;
        return EntityDimensions.scalable(c.width, c.height);
    }

    public Especie especie() {
        return Especie.byId(entityData.get(ESPECIE));
    }

    public Especie.Papel papel() {
        return Especie.Papel.byId(entityData.get(PAPEL));
    }

    @Nullable
    public BuiltinCorps tropa() {
        String t = entityData.get(TROPA);
        if (t.isEmpty()) return null;
        for (BuiltinCorps c : BuiltinCorps.values()) if (c.id().getPath().equals(t)) return c;
        return null;
    }

    public int corEnergia() {
        BuiltinCorps c = tropa();
        if (c != null) return c.color();
        return papel() == Especie.Papel.CAPITAO ? 0xFF6A10 : 0xFF3020;
    }

    public boolean voando() {
        return entityData.get(VOANDO);
    }

    public void setVoando(boolean v) {
        if (voando() != v) entityData.set(VOANDO, v);
        setNoGravity(v || emEspaco());
    }

    public boolean emEspaco() {
        return level().dimension().equals(SpaceBodies.SPACE);
    }

    public boolean isPirata() {
        return papel() == Especie.Papel.PIRATA || papel() == Especie.Papel.CAPITAO;
    }

    @Override public String getEspecie() { return entityData.get(ESPECIE); }
    @Override public String getTipoCorpo() { return especie().corpo.id; }
    @Override public int getVariantePele() { return entityData.get(PELE); }
    @Override public String getRoupa() { String r = entityData.get(ROUPA); return r.isEmpty() ? null : r; }
    @Override public boolean isOficial() { return entityData.get(OFICIAL); }
    @Override public boolean isCorrompido() { return entityData.get(CORROMPIDO); }

    public void setRoupa(@Nullable String r) { entityData.set(ROUPA, r == null ? "" : r); }
    public void setOficial(boolean v) { entityData.set(OFICIAL, v); }
    public void setCorrompido(boolean v) { entityData.set(CORROMPIDO, v); }
    public void setPele(int v) { entityData.set(PELE, Mth.clamp(v, 1, 3)); }

    @Override
    protected Component getTypeName() {
        BuiltinCorps c = tropa();
        Component esp = Component.translatable("especie.lanterna." + getEspecie());
        return switch (papel()) {
            case LANTERNA -> Component.translatable(isOficial() ? "npc.lanterna.lanterna_oficial" : "npc.lanterna.lanterna",
                    Component.translatable("corps.lanterna." + (c == null ? "green" : c.id().getPath())), esp);
            case PIRATA -> Component.translatable("npc.lanterna.pirata", esp);
            case CAPITAO -> Component.translatable("npc.lanterna.capitao", esp);
            case MERCADOR -> Component.translatable("npc.lanterna.mercador", esp);
        };
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Especie", getEspecie());
        tag.putInt("Pele", getVariantePele());
        tag.putString("Roupa", entityData.get(ROUPA));
        tag.putBoolean("Oficial", isOficial());
        tag.putBoolean("Corrompido", isCorrompido());
        tag.putString("Papel", entityData.get(PAPEL));
        tag.putString("Tropa", entityData.get(TROPA));
        tag.putBoolean("Voando", voando());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Especie")) {
            entityData.set(ESPECIE, tag.getString("Especie"));
            entityData.set(PELE, Math.max(1, tag.getInt("Pele")));
            entityData.set(ROUPA, tag.getString("Roupa"));
            entityData.set(OFICIAL, tag.getBoolean("Oficial"));
            entityData.set(CORROMPIDO, tag.getBoolean("Corrompido"));
            entityData.set(PAPEL, tag.getString("Papel"));
            entityData.set(TROPA, tag.getString("Tropa"));
            entityData.set(VOANDO, tag.getBoolean("Voando"));
            configured = true;
            refreshDimensions();
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        // Piratas e Lanternas dos encontros no espaço somem quando ninguém está perto (os das bases ficam)
        return getPersistentData().getBoolean("lanterna_encontro") && distance > 128 * 128;
    }

    // ===================== IA =====================

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
        goalSelector.addGoal(2, new CombateGoal());
        goalSelector.addGoal(3, new PanicGoal(this, 1.5) {
            @Override public boolean canUse() { return papel() == Especie.Papel.MERCADOR && super.canUse(); }
        });
        goalSelector.addGoal(4, new PatrulhaVooGoal());
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.9));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7) {
            @Override public boolean canUse() { return !voando() && !emEspaco() && super.canUse(); }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override public boolean canUse() { return papel() != Especie.Papel.MERCADOR && super.canUse(); }
        }.setAlertOthers(LanternaEntity.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, this::hostilA));
    }

    /** Quer brigar com este alvo? */
    public boolean hostilA(LivingEntity e) {
        if (e == this || !e.isAlive() || papel() == Especie.Papel.MERCADOR) return false;
        if (e instanceof Player p) {
            if (p.isCreative() || p.isSpectator()) return false;
            if (angry.contains(p.getUUID())) return true;
            if (isPirata()) return true;
            return Factions.enemies(tropa(), Factions.playerCorps(p));
        }
        if (e instanceof LanternaEntity o) {
            if (o.papel() == Especie.Papel.MERCADOR) return isPirata() && random.nextInt(3) == 0;
            if (isPirata()) return !o.isPirata();
            if (o.isPirata()) return true;
            return Factions.enemies(tropa(), o.tropa());
        }
        return papel() == Especie.Papel.LANTERNA && e instanceof LoreCreatures.Eco;
    }

    /** Aliado de um jogador (mesma Tropa)? */
    public boolean aliadoDe(Player p) {
        BuiltinCorps mine = Factions.playerCorps(p);
        return papel() == Especie.Papel.LANTERNA && mine != null && mine == tropa() && !angry.contains(p.getUUID());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (shieldTicks > 0) amount *= 0.35f;
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide) {
            if (source.getEntity() instanceof Player p && !p.isCreative()) angry.add(p.getUUID());
            if (random.nextInt(3) == 0) triggerAnim("acao", "misc.hurt");
            // Lanternas se protegem com o escudo-bolha quando apanham muito
            if (papel() == Especie.Papel.LANTERNA && shieldTicks <= -200 && getHealth() < getMaxHealth() * 0.5f) {
                shieldTicks = 80;
                triggerAnim("acao", "cast.shield");
                level().playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.8f, 1.6f);
            }
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) triggerAnim("acao", "misc.death");
        super.die(source);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        shieldTicks--;
        if (shieldTicks > 0 && tickCount % 4 == 0) {
            int c = corEnergia();
            ((ServerLevel) level()).sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                    new org.joml.Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f), 1.6f),
                    getX(), getY() + getBbHeight() / 2, getZ(), 6, getBbWidth(), getBbHeight() / 2, getBbWidth(), 0);
        }
        // Pousou: para de voar
        if (voando() && onGround() && getTarget() == null) setVoando(false);
        if (emEspaco() && !isNoGravity()) setNoGravity(true);
        if (tickCount % 600 == 0 && !angry.isEmpty() && getTarget() == null) angry.clear();
    }

    @Override
    public boolean causeFallDamage(float distance, float mult, DamageSource source) {
        return papel() == Especie.Papel.LANTERNA ? false : super.causeFallDamage(distance, mult, source);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        triggerAnim("acao", "attack.punch");
        return super.doHurtTarget(target);
    }

    // ---- Combate: voo e laser para os Lanternas, pistola para os piratas ----

    private class CombateGoal extends Goal {
        private int cooldown;
        private int hammerCooldown = 120;
        private int strafe = 1;

        CombateGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive() && papel() != Especie.Papel.MERCADOR && !isPassenger();
        }

        @Override
        public void stop() {
            if (papel() == Especie.Papel.LANTERNA && !emEspaco()) setVoando(false);
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;
            getLookControl().setLookAt(t, 30f, 30f);
            double dist = distanceTo(t);
            cooldown--;
            hammerCooldown--;
            if (papel() == Especie.Papel.LANTERNA) lanterna(t, dist);
            else pirata(t, dist);
        }

        private void lanterna(LivingEntity t, double dist) {
            boolean voar = dist > 4 || !t.onGround() || emEspaco();
            if (voar) {
                setVoando(true);
                getNavigation().stop();
                if (random.nextInt(80) == 0) strafe = -strafe;
                Vec3 away = position().subtract(t.position());
                away = new Vec3(away.x, 0, away.z);
                if (away.lengthSqr() < 0.01) away = new Vec3(1, 0, 0);
                away = away.normalize();
                Vec3 side = new Vec3(-away.z, 0, away.x).scale(strafe * 3);
                Vec3 want = t.position().add(away.scale(8)).add(side).add(0, 3, 0);
                Vec3 v = want.subtract(position());
                double speed = Math.min(0.45, v.length() * 0.08);
                setDeltaMovement(getDeltaMovement().scale(0.7).add(v.normalize().scale(speed)));
                setYRot((float) Math.toDegrees(Math.atan2(-(t.getX() - getX()), t.getZ() - getZ())));
                yBodyRot = getYRot();
            } else {
                setVoando(false);
                getNavigation().moveTo(t, 1.2);
                if (dist < 2.6 && cooldown <= 0) {
                    doHurtTarget(t);
                    cooldown = 20;
                }
                return;
            }
            if (cooldown <= 0 && hasLineOfSight(t)) {
                // Laser do anel
                aimAt(t);
                float dmg = isOficial() ? 2.4f : 1.5f;
                ConstructEntity.spawnBeam(LanternaEntity.this, corEnergia(), dmg, 24);
                triggerAnim("acao", "attack.beam");
                cooldown = 50 + random.nextInt(30);
            } else if (hammerCooldown <= 0 && (isOficial() || random.nextInt(3) == 0)) {
                ConstructEntity.spawnHammer(LanternaEntity.this, t.position(), corEnergia(), isOficial() ? 10f : 7f, 1.0f);
                triggerAnim("acao", "cast.construct");
                hammerCooldown = 220 + random.nextInt(120);
            }
        }

        private void pirata(LivingEntity t, double dist) {
            boolean capitao = papel() == Especie.Papel.CAPITAO;
            if (emEspaco()) {
                Vec3 v = t.position().add(0, 2, 0).subtract(position());
                if (v.length() > 9) setDeltaMovement(getDeltaMovement().scale(0.8).add(v.normalize().scale(0.12)));
                else setDeltaMovement(getDeltaMovement().scale(0.8));
            } else if (dist > 12 || !hasLineOfSight(t)) {
                getNavigation().moveTo(t, 1.1);
            } else if (dist < 5) {
                // Recua para atirar de longe
                Vec3 away = position().subtract(t.position()).normalize().scale(4);
                getNavigation().moveTo(getX() + away.x, getY(), getZ() + away.z, 1.2);
            } else {
                getNavigation().stop();
            }
            if (cooldown <= 0 && hasLineOfSight(t) && dist < 28) {
                Vec3 from = getEyePosition().add(getViewVector(1f).scale(0.6)).add(0, -0.2, 0);
                Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0);
                TiroPlasma.shoot(level(), LanternaEntity.this, from, to.subtract(from), 1.6f, capitao ? 7f : 4f, corEnergia(), capitao && random.nextInt(3) == 0);
                triggerAnim("acao", "attack.beam");
                cooldown = capitao ? 18 + random.nextInt(10) : 30 + random.nextInt(20);
            }
        }

        private void aimAt(LivingEntity t) {
            Vec3 d = t.position().add(0, t.getBbHeight() * 0.6, 0).subtract(getEyePosition());
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
            setYRot(yaw); yRotO = yaw; setYHeadRot(yaw); yBodyRot = yaw;
            setXRot(pitch); xRotO = pitch;
        }
    }

    /** Lanternas das bases às vezes levantam voo e dão uma volta, só para patrulhar. */
    private class PatrulhaVooGoal extends Goal {
        private int time;
        private Vec3 center;

        PatrulhaVooGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return papel() == Especie.Papel.LANTERNA && getTarget() == null && !emEspaco() && !isPassenger() && random.nextInt(900) == 0;
        }

        @Override
        public void start() {
            time = 160 + random.nextInt(160);
            center = position();
            setVoando(true);
        }

        @Override
        public boolean canContinueToUse() {
            return time > 0 && getTarget() == null;
        }

        @Override
        public void tick() {
            time--;
            double a = time * 0.04;
            Vec3 want = center.add(Math.cos(a) * 10, 9 + Math.sin(a * 0.5) * 2, Math.sin(a) * 10);
            if (time < 50) want = center.add(0, 0.5, 0);
            Vec3 v = want.subtract(position());
            setDeltaMovement(getDeltaMovement().scale(0.8).add(v.normalize().scale(Math.min(0.25, v.length() * 0.05))));
            Vec3 m = getDeltaMovement();
            if (m.horizontalDistanceSqr() > 1e-3) {
                setYRot((float) Math.toDegrees(Math.atan2(-m.x, m.z)));
                yBodyRot = getYRot();
            }
        }

        @Override
        public void stop() {
            if (!emEspaco()) setNoGravity(false);
        }
    }

    // ===================== Conversa e trocas =====================

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (getTarget() == player) return InteractionResult.PASS;
        if (papel() == Especie.Papel.MERCADOR) {
            if (getOffers().isEmpty()) return InteractionResult.CONSUME;
            setTradingPlayer(sp);
            openTradingScreen(sp, getDisplayName(), 1);
            return InteractionResult.CONSUME;
        }
        if (papel() == Especie.Papel.LANTERNA) {
            getLookControl().setLookAt(player);
            boolean ally = aliadoDe(player);
            triggerAnim("acao", ally ? "misc.salute" : "misc.talk");
            String key = ally ? "npc.lanterna.fala.aliado" : Factions.enemies(tropa(), Factions.playerCorps(player))
                    ? "npc.lanterna.fala.inimigo" : "npc.lanterna.fala.neutro";
            sp.sendSystemMessage(Component.literal("<").append(getDisplayName()).append("> ")
                    .append(Component.translatable(key + "." + random.nextInt(3))).withStyle(s -> s.withColor(corEnergia())));
            com.lanterna.tropa.TropaEvents.onTalkNpc(sp, this);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void updateTrades() {
        getOffers().addAll(MercadorTrades.ofertas(random));
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
        if (offer.shouldRewardExp()) {
            level().addFreshEntity(new net.minecraft.world.entity.ExperienceOrb(level(), getX(), getY() + 0.5, getZ(), 3 + random.nextInt(4)));
        }
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    // ===================== Animações =====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movimento", 4, state -> {
            if (isDeadOrDying()) return PlayState.STOP;
            if (voando()) return state.setAndContinue(getDeltaMovement().horizontalDistanceSqr() > 0.004 ? FLY : HOVER);
            if (state.isMoving()) return state.setAndContinue(getDeltaMovement().horizontalDistanceSqr() > 0.03 ? RUN : WALK);
            return state.setAndContinue(IDLE);
        }));
        AnimationController<LanternaEntity> acao = new AnimationController<>(this, "acao", 0, state -> PlayState.STOP);
        for (String a : ACOES) {
            acao.triggerableAnim(a, a.equals("misc.death")
                    ? RawAnimation.begin().then(a, software.bernie.geckolib.core.animation.Animation.LoopType.HOLD_ON_LAST_FRAME)
                    : RawAnimation.begin().then(a, software.bernie.geckolib.core.animation.Animation.LoopType.PLAY_ONCE));
        }
        controllers.add(acao);
        controllers.add(new AnimationController<>(this, "extra", 0, state -> state.setAndContinue(EXTRA)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
