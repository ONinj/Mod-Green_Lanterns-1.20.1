package com.lanterna.energia;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Conversor de Luz: queima Cristais Emocionais (60 mil FE) ou Núcleos Emocionais (300 mil FE, do caminho do Mekanism)
 * e solta a energia (FE) para tudo que estiver encostado: mineradora, cabos e máquinas de qualquer mod.
 */
public class ConversorBE extends MaquinaBE {
    public static final int CAPACIDADE = 2_000_000;
    private final Bateria energia = new Bateria(CAPACIDADE, 0, 20_000, this::setChanged);
    private final ItemStackHandler slot = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int s, @NotNull ItemStack st) {
            return valor(st) > 0;
        }

        @Override
        protected void onContentsChanged(int s) {
            setChanged();
        }
    };
    private final LazyOptional<IEnergyStorage> capEnergia = LazyOptional.of(() -> energia);
    private final LazyOptional<ItemStackHandler> capItem = LazyOptional.of(() -> slot);
    private int queimando;

    public ConversorBE(BlockPos pos, BlockState s) {
        super(EnergiaRegistro.CONVERSOR_BE.get(), pos, s);
    }

    public static int valor(ItemStack s) {
        if (s.is(EnergiaRegistro.CRISTAL.get())) return Producao.FE_CRISTAL;
        if (s.is(EnergiaRegistro.NUCLEO.get())) return Producao.FE_NUCLEO;
        return 0;
    }

    @Override
    public void tickServidor() {
        if (level == null) return;
        if (queimando <= 0 && energia.getEnergyStored() < CAPACIDADE) {
            int v = valor(slot.getStackInSlot(0));
            if (v > 0) {
                queimando = v;
                slot.extractItem(0, 1, false);
                level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.4f, 1.6f);
            }
        }
        boolean ativo = queimando > 0 && energia.getEnergyStored() < CAPACIDADE;
        if (ativo) {
            int q = Math.min(queimando, Producao.FE_POR_TICK_CONVERSOR);
            queimando -= energia.gerar(q) == 0 ? 0 : q;
            if (level.getGameTime() % 10 == 0 && level instanceof ServerLevel sl)
                sl.sendParticles(new DustParticleOptions(new org.joml.Vector3f(0.35f, 1f, 0.55f), 0.9f), worldPosition.getX() + 0.5,
                        worldPosition.getY() + 1.05, worldPosition.getZ() + 0.5, 2, 0.2, 0.05, 0.2, 0);
        }
        if (level.getGameTime() % 20 == 0) aceso(ativo);
        empurrarEnergia(energia, 20_000);
    }

    @Override
    public InteractionResult usar(Player p, InteractionHand hand) {
        ItemStack mao = p.getItemInHand(hand);
        if (valor(mao) > 0) {
            colocar(p, hand, slot, 0);
            return InteractionResult.CONSUME;
        }
        avisar(p, Component.translatable("energia.lanterna.conversor.status", fe(energia.getEnergyStored()), fe(CAPACIDADE),
                slot.getStackInSlot(0).getCount(), fe(queimando)).withStyle(ChatFormatting.GREEN));
        return InteractionResult.CONSUME;
    }

    @Override
    protected ItemStackHandler itens() {
        return slot;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) return capEnergia.cast();
        if (cap == ForgeCapabilities.ITEM_HANDLER) return capItem.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        capEnergia.invalidate();
        capItem.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putInt("energia", energia.getEnergyStored());
        t.put("slot", slot.serializeNBT());
        t.putInt("queimando", queimando);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        energia.definir(t.getInt("energia"));
        slot.deserializeNBT(t.getCompound("slot"));
        queimando = t.getInt("queimando");
    }

    public int energia() {
        return energia.getEnergyStored();
    }
}
