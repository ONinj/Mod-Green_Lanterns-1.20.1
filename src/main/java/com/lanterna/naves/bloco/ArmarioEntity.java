package com.lanterna.naves.bloco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Guarda 27 itens; a porta do armário abre enquanto alguém mexe nele. */
public class ArmarioEntity extends RandomizableContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    private int abertos;

    public ArmarioEntity(BlockPos pos, BlockState state) {
        super(NaveBlocks.ARMARIO_BE.get(), pos, state);
    }

    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> list) { items = list; }
    @Override public int getContainerSize() { return 27; }
    @Override protected Component getDefaultName() { return Component.translatable("block.lanterna.nave_armario"); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inv) { return ChestMenu.threeRows(id, inv, this); }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) ContainerHelper.loadAllItems(tag, items);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, items);
    }

    @Override
    public void startOpen(Player p) {
        if (p.isSpectator() || level == null) return;
        if (abertos++ == 0) porta(true);
    }

    @Override
    public void stopOpen(Player p) {
        if (p.isSpectator() || level == null) return;
        if (--abertos <= 0) { abertos = 0; porta(false); }
    }

    private void porta(boolean open) {
        BlockState s = getBlockState();
        if (!(s.getBlock() instanceof Armario)) return;
        level.setBlock(worldPosition, s.setValue(Armario.OPEN, open), 3);
        level.playSound(null, worldPosition, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.7f, 1.2f);
    }
}
