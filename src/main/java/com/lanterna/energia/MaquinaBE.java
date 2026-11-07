package com.lanterna.energia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.ItemStackHandler;

/** Base das máquinas: sincroniza com o cliente, solta os itens quando quebra, ajuda a empurrar energia. */
public abstract class MaquinaBE extends BlockEntity {
    protected MaquinaBE(BlockEntityType<?> t, BlockPos pos, BlockState s) {
        super(t, pos, s);
    }

    public abstract void tickServidor();

    public void tickCliente() {}

    public abstract InteractionResult usar(Player p, InteractionHand hand);

    /** Itens guardados (para soltar ao quebrar). */
    protected ItemStackHandler itens() {
        return null;
    }

    public void soltarItens() {
        ItemStackHandler h = itens();
        if (h == null || level == null) return;
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (!s.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, s.copy());
        }
    }

    protected void aceso(boolean v) {
        BlockState s = getBlockState();
        if (s.hasProperty(MaquinaBlock.ACESO) && s.getValue(MaquinaBlock.ACESO) != v && level != null)
            level.setBlock(worldPosition, s.setValue(MaquinaBlock.ACESO, v), 3);
    }

    /** Manda o estado novo para quem está perto (renderização). */
    protected void sincronizar() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    /** Empurra energia para os vizinhos que aceitam FE. Devolve quanto saiu. */
    protected int empurrarEnergia(IEnergyStorage de, int max) {
        if (level == null || de.getEnergyStored() <= 0) return 0;
        int total = 0;
        for (Direction d : Direction.values()) {
            if (total >= max) break;
            BlockEntity be = level.getBlockEntity(worldPosition.relative(d));
            if (be == null || be instanceof ConversorBE) continue;
            IEnergyStorage alvo = be.getCapability(ForgeCapabilities.ENERGY, d.getOpposite()).orElse(null);
            if (alvo == null || !alvo.canReceive()) continue;
            int pode = de.extractEnergy(max - total, true);
            int foi = alvo.receiveEnergy(pode, false);
            de.extractEnergy(foi, false);
            total += foi;
        }
        return total;
    }

    protected static void avisar(Player p, Component c) {
        p.displayClientMessage(c, true);
    }

    /** Coloca a pilha da mão no slot (se couber); devolve se entrou algo. */
    protected static boolean colocar(Player p, InteractionHand hand, ItemStackHandler h, int slot) {
        ItemStack mao = p.getItemInHand(hand);
        if (mao.isEmpty()) return false;
        ItemStack sobra = h.insertItem(slot, mao.copy(), false);
        if (sobra.getCount() == mao.getCount()) return false;
        if (!p.getAbilities().instabuild) p.setItemInHand(hand, sobra);
        return true;
    }

    protected static void entregar(Player p, ItemStack s) {
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = super.getUpdateTag();
        saveAdditional(t);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public static String fe(long v) {
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000.0);
        if (v >= 1000) return String.format("%.1fk", v / 1000.0);
        return Long.toString(v);
    }
}
