package com.lanterna.mogo;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Uma rocha voando: os destroços do cinturão do Mogo (no espaço, sem gravidade), as pedras que o Mogo arremessa na
 * Prova (com gravidade) e os estilhaços da Ferida (pequenos e rápidos). Acerta naves, jogadores e criaturas.
 */
public class RochaMogo extends Entity {
    private static final EntityDataAccessor<Float> TAM = SynchedEntityData.defineId(RochaMogo.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> TIPO = SynchedEntityData.defineId(RochaMogo.class, EntityDataSerializers.INT);
    /** 0 destroço do cinturão · 1 pedra da Prova · 2 estilhaço da Ferida. */
    public static final int CINTURAO = 0, PEDRA = 1, ESTILHACO = 2;
    private int vida = 260;
    private float dano = 6;
    public float giroX, giroY;

    public RochaMogo(EntityType<? extends RochaMogo> t, Level l) {
        super(t, l);
        noPhysics = true;
        giroX = random.nextFloat() * 360;
        giroY = random.nextFloat() * 360;
    }

    public static RochaMogo criar(Level l, int tipo, float tam, Vec3 pos, Vec3 vel, float dano) {
        RochaMogo r = new RochaMogo(MogoEntidades.ROCHA.get(), l);
        r.entityData.set(TIPO, tipo);
        r.entityData.set(TAM, tam);
        r.dano = dano;
        r.moveTo(pos.x, pos.y, pos.z);
        r.setDeltaMovement(vel);
        return r;
    }

    public float tam() {
        return entityData.get(TAM);
    }

    public int tipo() {
        return entityData.get(TIPO);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TAM, 1f);
        entityData.define(TIPO, 0);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        if (tipo() == PEDRA) v = v.add(0, -0.045, 0);
        setDeltaMovement(v);
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        giroX += 6;
        giroY += 4;
        if (level().isClientSide) return;
        if (--vida <= 0) {
            discard();
            return;
        }
        float t = tam() * 0.5f;
        AABB caixa = new AABB(getX() - t, getY() - t, getZ() - t, getX() + t, getY() + t, getZ() + t);
        // acerta naves (quem pilota sente) e criaturas
        for (Entity e : level().getEntities(this, caixa.inflate(0.4))) {
            if (e instanceof RochaMogo || e instanceof OlhoMogo || e instanceof Ferida) continue;
            if (e instanceof com.lanterna.naves.NaveEntity n) {
                n.hurt(damageSources().flyIntoWall(), dano * 2);
                quebrar();
                return;
            }
            if (e instanceof LivingEntity le && !e.isSpectator()) {
                le.hurt(damageSources().fallingBlock(this), dano);
                le.push(v.x * 0.6, 0.35, v.z * 0.6);
                quebrar();
                return;
            }
        }
        // bate no chão (pedras e estilhaços)
        if (tipo() != CINTURAO && !level().getBlockState(blockPosition()).isAir()) quebrar();
    }

    private void quebrar() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, tipo() == ESTILHACO ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState()),
                    getX(), getY(), getZ(), 20, tam() * 0.4, tam() * 0.4, tam() * 0.4, 0.1);
            sl.playSound(null, blockPosition(), tipo() == ESTILHACO ? SoundEvents.AMETHYST_BLOCK_BREAK : SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.4f, 0.7f);
        }
        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
