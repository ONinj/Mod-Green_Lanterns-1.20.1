package com.lanterna.naves;

import com.lanterna.tropa.TiroPlasma;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.entity.PartEntity;

/** Uma das caixas de colisão da nave (col_N). Sólida: dá para pisar, bater e atirar nela. */
public class NavePart extends PartEntity<NaveEntity> implements TiroPlasma.NavePartLike {
    private EntityDimensions size = EntityDimensions.fixed(0.1f, 0.1f);
    /** Meia-largura real em x e z (a caixa não precisa ser quadrada). */
    private double hx = 0.05, hz = 0.05;
    final int index;

    public NavePart(NaveEntity parent, int index) {
        super(parent);
        this.index = index;
        refreshDimensions();
    }

    /** Caixa retangular (x por z), mais justa que a quadrada do Minecraft. */
    void resize(double wx, double wz, float h) {
        hx = wx / 2; hz = wz / 2;
        resize((float) Math.max(wx, wz), h);
    }

    @Override
    protected net.minecraft.world.phys.AABB makeBoundingBox() {
        if (size == null) return super.makeBoundingBox(); // chamado pelo construtor do Entity, antes dos campos existirem
        return new net.minecraft.world.phys.AABB(getX() - hx, getY(), getZ() - hz, getX() + hx, getY() + size.height, getZ() + hz);
    }

    void resize(float w, float h) {
        if (Math.abs(size.width - w) > 0.05f || Math.abs(size.height - h) > 0.05f) {
            size = EntityDimensions.fixed(w, h);
            refreshDimensions();
        }
    }

    @Override public EntityDimensions getDimensions(Pose pose) { return size; }
    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public Entity naveOwner() { return getParent(); }

    @Override
    public boolean isPickable() {
        return size.width > 0.2f;
    }

    /** Destroços não são sólidos: dá para entrar no meio deles (clicar ainda saqueia). */
    @Override
    public boolean canBeCollidedWith() {
        return size.width > 0.2f && !getParent().tipo().destrocos();
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return other != getParent() && !(other instanceof NavePart p && p.getParent() == getParent()) && super.canCollideWith(other);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !isInvulnerableTo(source) && getParent().hurt(source, amount);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return getParent().interact(player, hand);
    }

    @Override
    public ItemStack getPickResult() {
        return getParent().getPickResult();
    }

    @Override
    public boolean is(Entity other) {
        return this == other || getParent() == other;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        throw new UnsupportedOperationException();
    }
}
