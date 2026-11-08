package com.lanterna.mogo;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** Um "olho" de cristal do Mogo na Prova: parado no ar, é preciso quebrar os quatro. */
public class OlhoMogo extends Mob {
    public OlhoMogo(EntityType<? extends OlhoMogo> t, Level l) {
        super(t, l);
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder atributos() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 60).add(Attributes.ARMOR, 6).add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(0, 0, 0);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource s) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AMETHYST_CLUSTER_BREAK;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }
}
