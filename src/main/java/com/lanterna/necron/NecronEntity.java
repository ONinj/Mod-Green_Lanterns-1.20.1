package com.lanterna.necron;

import com.lanterna.LanternaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 0.54.2: o Necron na Terra, flutuando na frente da Bateria Central da Morte. Por enquanto ninguém o fere (a luz de um
 * anel some nele); quem chega perto é atacado com a foice e com raios da Morte. Mais adiante a saga traz a luta de
 * verdade (quatro fases).
 */
public class NecronEntity extends Monster implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("misc.idle");
    private static final RawAnimation GOLPE = RawAnimation.begin().thenPlay("attack.swing");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent barra = new ServerBossEvent(Component.translatable("necron.lanterna.nome_barra"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    @Nullable private BlockPos casa;
    private int recarga = 40;

    public NecronEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
        xpReward = 0;
    }

    public static AttributeSupplier.Builder atributos() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1000)
                .add(Attributes.ATTACK_DAMAGE, 14)
                .add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.ARMOR, 30);
    }

    public void casa(BlockPos p) { casa = p; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 40f, 1f));
    }

    // ===================== ninguém fere (por enquanto) =====================

    @Override
    public boolean isInvulnerableTo(DamageSource s) {
        if (BatalhaFinal.ativa()) return false;   // 0.56.0: na batalha final ele sangra
        return !s.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    public ServerBossEvent barra() { return barra; }

    /** Onde ele flutua durante a batalha (muda quando ele some e reaparece). */
    @Nullable private Vec3 posBatalha;

    public void saltarPara(Vec3 p) {
        posBatalha = new Vec3(p.x, p.y + 0.5, p.z);
        teleportTo(posBatalha.x, posBatalha.y, posBatalha.z);
    }

    @Override
    public void die(DamageSource s) {
        super.die(s);
        if (level() instanceof ServerLevel l) {
            BatalhaFinal.vitoria(l, this);
            // 0.58.0: a queda dele é uma cutscene (o modelo cai de joelhos e se desfaz); o corpo some na hora
            if (BatalhaFinal.derrotado(l.getServer())) discard();
        }
    }

    @Override public boolean isPushable() { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean causeFallDamage(float a, float b, DamageSource s) { return false; }

    // ===================== tick =====================

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel l)) {
            // a aura escura (do lado de quem vê)
            if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.SMOKE, getRandomX(0.8), getY() + random.nextDouble() * 3, getRandomZ(0.8), 0, 0.02, 0);
            if (random.nextInt(6) == 0) level().addParticle(ParticleTypes.SOUL, getRandomX(1.0), getY() + random.nextDouble() * 2.5, getRandomZ(1.0), 0, 0.03, 0);
            return;
        }
        // 0.56.0: a batalha final cuida dos ataques; ele flutua onde a batalha mandou
        var armadura = getAttribute(Attributes.ARMOR);
        if (armadura != null) armadura.setBaseValue(BatalhaFinal.ativa() ? 6 : 30);
        if (BatalhaFinal.ativa()) {
            Vec3 alvo = (posBatalha != null ? posBatalha : casa != null ? Vec3.atBottomCenterOf(casa) : position()).add(0, 0.25 * Math.sin(tickCount * 0.07), 0);
            setPos(position().lerp(alvo, 0.25));
            setDeltaMovement(Vec3.ZERO);
            barra.setProgress(getHealth() / getMaxHealth());
            for (ServerPlayer p : l.players()) {
                boolean perto = p.distanceToSqr(this) < 70 * 70 && !p.isSpectator();
                if (perto && !barra.getPlayers().contains(p)) barra.addPlayer(p);
                if (!perto && barra.getPlayers().contains(p)) barra.removePlayer(p);
            }
            Player olha = l.getNearestPlayer(this, 40);
            if (olha != null) getLookControl().setLookAt(olha, 30, 30);
            return;
        }
        posBatalha = null;
        // fica no lugar dele, flutuando
        if (casa != null) {
            Vec3 alvo = Vec3.atBottomCenterOf(casa).add(0, 0.25 * Math.sin(tickCount * 0.05), 0);
            if (position().distanceToSqr(alvo) > 0.04) setPos(position().lerp(alvo, 0.2));
            setDeltaMovement(Vec3.ZERO);
        }
        // a barra só para quem está perto
        for (ServerPlayer p : l.players()) {
            boolean perto = p.distanceToSqr(this) < 48 * 48 && !p.isSpectator();
            if (perto && !barra.getPlayers().contains(p)) barra.addPlayer(p);
            if (!perto && barra.getPlayers().contains(p)) barra.removePlayer(p);
        }
        Player alvo = l.getNearestPlayer(this, 30);
        if (alvo != null && (alvo.isCreative() || alvo.isSpectator())) alvo = null;
        if (alvo == null || SagaNecron.assistindo(alvo) || Fuga.emFuga(alvo) || Tentacao.corrompido(alvo)) return;
        getLookControl().setLookAt(alvo, 30, 30);
        if (--recarga > 0) return;
        double d = distanceTo(alvo);
        if (d < 5) golpe(l, alvo);
        else raio(l, alvo);
        recarga = 30 + random.nextInt(20);
    }

    /** A foice: golpe largo, joga longe. */
    private void golpe(ServerLevel l, Player alvo) {
        triggerAnim("golpe", "golpe");
        swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);   // 0.58.3: o golpe da foice (a animação)
        l.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.4f, 0.5f);
        l.playSound(null, blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 0.8f, 0.6f);
        l.sendParticles(ParticleTypes.SWEEP_ATTACK, alvo.getX(), alvo.getY() + 1, alvo.getZ(), 3, 0.6, 0.3, 0.6, 0);
        alvo.hurt(damageSources().mobAttack(this), Math.max(14, alvo.getMaxHealth() * 0.3f));
        Vec3 empurra = alvo.position().subtract(position()).normalize().scale(1.6);
        alvo.push(empurra.x, 0.5, empurra.z);
        alvo.hurtMarked = true;
    }

    /** O raio da Morte: uma linha de luz negra até o alvo; seca a vida (Wither). */
    private void raio(ServerLevel l, Player alvo) {
        swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        Vec3 de = position().add(0, 2.6, 0), ate = alvo.position().add(0, 1, 0);
        Vec3 dir = ate.subtract(de);
        int n = (int) (dir.length() * 3);
        DustParticleOptions negro = new DustParticleOptions(new Vector3f(0.18f, 0.02f, 0.28f), 1.6f);
        for (int i = 0; i < n; i++) {
            Vec3 p = de.add(dir.scale(i / (double) n));
            l.sendParticles(negro, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
            if (i % 4 == 0) l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        l.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1f, 1.4f);
        alvo.hurt(damageSources().indirectMagic(this, this), Math.max(7, alvo.getMaxHealth() * 0.16f));
        alvo.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1));
        alvo.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
    }

    // ===================== barra e dados =====================

    @Override
    public void stopSeenByPlayer(ServerPlayer p) {
        super.stopSeenByPlayer(p);
        barra.removePlayer(p);
    }

    @Override
    public void remove(RemovalReason r) {
        barra.removeAllPlayers();
        super.remove(r);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        if (casa != null) t.put("Casa", NbtUtils.writeBlockPos(casa));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        if (t.contains("Casa")) casa = NbtUtils.readBlockPos(t.getCompound("Casa"));
    }

    // ===================== GeckoLib =====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "corpo", 5, st -> st.setAndContinue(IDLE)));
        c.add(new AnimationController<>(this, "golpe", 0, st -> PlayState.STOP).triggerableAnim("golpe", GOLPE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    static { LanternaMod.LOGGER.debug("Necron carregado"); }
}
