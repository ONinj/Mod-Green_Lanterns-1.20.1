package com.lanterna.mogo;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Ferida: a infecção que cresceu dentro do Mogo. Flutua no meio da câmara do núcleo, atira estilhaços e chama Ecos
 * Cinzentos. Na metade da vida se fecha num casulo que nada atravessa: aí o Mogo pede para ser chamado.
 */
public class Ferida extends Monster {
    private final ServerBossEvent barra = new ServerBossEvent(Component.translatable("entity.lanterna.ferida_mogo"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
    private int tiro, eco;
    public boolean casulo;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> CASULO =
            net.minecraft.network.syncher.SynchedEntityData.defineId(Ferida.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(CASULO, false);
    }

    /** No cliente: se está fechada no casulo. */
    public boolean emCasulo() {
        return entityData.get(CASULO);
    }
    public int casuloDesde = -1;

    public Ferida(EntityType<? extends Ferida> t, Level l) {
        super(t, l);
        setNoGravity(true);
        setPersistenceRequired();
        xpReward = 200;
    }

    public static AttributeSupplier.Builder atributos() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 420).add(Attributes.ARMOR, 8)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.FOLLOW_RANGE, 48);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(getDeltaMovement().multiply(0, 0.5, 0).add(0, Math.sin(tickCount * 0.05) * 0.01, 0));
        if (level().isClientSide) return;
        barra.setProgress(getHealth() / getMaxHealth());
        if (!casulo && getHealth() <= getMaxHealth() / 2) {
            casulo = true;
            entityData.set(CASULO, true);
            casuloDesde = tickCount;
            level().playSound(null, blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 3f, 0.7f);
            for (ServerPlayer p : ((ServerLevel) level()).getPlayers(p -> p.distanceTo(this) < 48)) MogoMissoes.casuloFechou(p, this);
        }
        if (casulo) {
            // sem o Chamado o Mogo atira sozinho depois de um tempo (ninguém fica preso aqui)
            if (tickCount - casuloDesde == 20 * 35) {
                Player alguem = level().getNearestPlayer(this, 48);
                if (alguem instanceof ServerPlayer sp) MogoMissoes.chamadoInterno(sp, this);
            }
            return;
        }
        Player alvo = level().getNearestPlayer(this, 40);
        if (alvo == null || alvo.isCreative() || alvo.isSpectator()) return;
        getLookControl().setLookAt(alvo);
        if (++tiro >= 30) {
            tiro = 0;
            for (int i = 0; i < 3; i++) {
                Vec3 de = position().add(0, 2.2, 0);
                Vec3 dir = alvo.getEyePosition().subtract(de).normalize().add((random.nextDouble() - 0.5) * 0.15, (random.nextDouble() - 0.5) * 0.1, (random.nextDouble() - 0.5) * 0.15).normalize();
                level().addFreshEntity(RochaMogo.criar(level(), RochaMogo.ESTILHACO, 0.5f, de.add(dir.scale(2.2)), dir.scale(0.9), 5));
            }
            level().playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2f, 0.5f);
        }
        if (++eco >= 20 * 14) {
            eco = 0;
            for (int i = 0; i < 2; i++) {
                Mob m = com.lanterna.lore.LoreEntities.ECO_CINZENTO.get().create(level());
                if (m == null) continue;
                m.moveTo(getX() + (random.nextDouble() - 0.5) * 10, getY() - 3, getZ() + (random.nextDouble() - 0.5) * 10, random.nextFloat() * 360, 0);
                m.setTarget(alvo);
                level().addFreshEntity(m);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource s, float a) {
        // no casulo só o raio do Mogo entra
        if (casulo && !s.is(ChamadoMogo.DANO)) {
            if (s.getEntity() instanceof Player p) p.displayClientMessage(Component.translatable("mogo.lanterna.nucleo.casulo"), true);
            return false;
        }
        return super.hurt(s, a);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer p) {
        super.startSeenByPlayer(p);
        barra.addPlayer(p);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer p) {
        super.stopSeenByPlayer(p);
        barra.removePlayer(p);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SCULK_SHRIEKER_SHRIEK;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource s) {
        return SoundEvents.SCULK_BLOCK_BREAK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WARDEN_DEATH;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    protected void registerGoals() {}
}
