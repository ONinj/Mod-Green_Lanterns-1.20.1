package com.lanterna.energia;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Refinaria Emocional (o refino sem outros mods): 1000 mB de Emoção Bruta + 1 redstone = 1 Cristal Emocional.
 * Recebe o fluido do gerador ao lado (ou por canos); funis põem redstone e tiram os cristais.
 */
public class RefinariaBE extends MaquinaBE {
    public static final int TANQUE = 8000;
    private final FluidTank tanque = new FluidTank(TANQUE, f -> f.getFluid() == EnergiaRegistro.EMOCAO.get()) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    /** 0 = redstone, 1 = cristais prontos. */
    private final ItemStackHandler slots = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int s, @NotNull ItemStack st) {
            return s == 0 ? st.is(Items.REDSTONE) : st.is(EnergiaRegistro.CRISTAL.get());
        }

        @Override
        protected void onContentsChanged(int s) {
            setChanged();
        }
    };
    /** Por fora: põe só redstone, tira só cristal. */
    private final IItemHandler porFora = new IItemHandler() {
        public int getSlots() { return 2; }
        public @NotNull ItemStack getStackInSlot(int s) { return slots.getStackInSlot(s); }
        public @NotNull ItemStack insertItem(int s, @NotNull ItemStack st, boolean sim) { return s == 0 ? slots.insertItem(0, st, sim) : st; }
        public @NotNull ItemStack extractItem(int s, int n, boolean sim) { return s == 1 ? slots.extractItem(1, n, sim) : ItemStack.EMPTY; }
        public int getSlotLimit(int s) { return 64; }
        public boolean isItemValid(int s, @NotNull ItemStack st) { return s == 0 && st.is(Items.REDSTONE); }
    };
    private final LazyOptional<IFluidHandler> capFluido = LazyOptional.of(() -> tanque);
    private final LazyOptional<IItemHandler> capItem = LazyOptional.of(() -> porFora);
    public int progresso;

    public RefinariaBE(BlockPos pos, BlockState s) {
        super(EnergiaRegistro.REFINARIA_BE.get(), pos, s);
    }

    private boolean pode() {
        ItemStack out = slots.getStackInSlot(1);
        return tanque.getFluidAmount() >= Producao.MB_POR_CRISTAL && slots.getStackInSlot(0).is(Items.REDSTONE) && out.getCount() < 64;
    }

    @Override
    public void tickServidor() {
        if (level == null) return;
        if (!pode()) {
            if (progresso != 0) progresso = 0;
            if (level.getGameTime() % 20 == 0) aceso(false);
            return;
        }
        aceso(true);
        if (++progresso >= Producao.TICKS_REFINO) {
            progresso = 0;
            tanque.drain(Producao.MB_POR_CRISTAL, IFluidHandler.FluidAction.EXECUTE);
            slots.extractItem(0, 1, false);
            slots.insertItem(1, new ItemStack(EnergiaRegistro.CRISTAL.get()), false);
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.BLOCKS, 0.8f, 1.3f);
        }
        if (level.getGameTime() % 8 == 0 && level instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.WITCH, worldPosition.getX() + 0.5, worldPosition.getY() + 1.05, worldPosition.getZ() + 0.5, 1, 0.15, 0, 0.15, 0);
        setChanged();
    }

    @Override
    public InteractionResult usar(Player p, InteractionHand hand) {
        ItemStack mao = p.getItemInHand(hand);
        if (mao.is(Items.REDSTONE)) {
            colocar(p, hand, slots, 0);
            return InteractionResult.CONSUME;
        }
        if (mao.isEmpty() && !slots.getStackInSlot(1).isEmpty()) {
            entregar(p, slots.extractItem(1, 64, false));
            level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 1.2f);
            return InteractionResult.CONSUME;
        }
        avisar(p, Component.translatable("energia.lanterna.refinaria.status", tanque.getFluidAmount(), TANQUE,
                slots.getStackInSlot(0).getCount(), slots.getStackInSlot(1).getCount(), progresso * 100 / Producao.TICKS_REFINO).withStyle(ChatFormatting.LIGHT_PURPLE));
        return InteractionResult.CONSUME;
    }

    @Override
    protected ItemStackHandler itens() {
        return slots;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) return capFluido.cast();
        if (cap == ForgeCapabilities.ITEM_HANDLER) return capItem.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        capFluido.invalidate();
        capItem.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.put("tanque", tanque.writeToNBT(new CompoundTag()));
        t.put("slots", slots.serializeNBT());
        t.putInt("progresso", progresso);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        tanque.readFromNBT(t.getCompound("tanque"));
        slots.deserializeNBT(t.getCompound("slots"));
        progresso = t.getInt("progresso");
    }

    public int quantidade() {
        return tanque.getFluidAmount();
    }

    public void encher(int mb) {
        tanque.fill(new FluidStack(EnergiaRegistro.EMOCAO.get(), mb), IFluidHandler.FluidAction.EXECUTE);
    }
}
