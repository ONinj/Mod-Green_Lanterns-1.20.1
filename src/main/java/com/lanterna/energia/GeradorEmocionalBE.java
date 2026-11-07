package com.lanterna.energia;

import com.lanterna.baterias.Tropa;
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
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Gerador Emocional: transforma a emoção do lugar em Emoção Bruta (fluido). Cada Tropa queima uma emoção diferente:
 *  verde (vontade)     trabalho de jogador por perto: quebrar e colocar blocos, mais um pouco sempre;
 *  amarelo (medo)      monstros por perto, vivos;
 *  vermelho (raiva)    criaturas morrendo por perto;
 *  laranja (avareza)   come ouro, esmeralda e diamante do compartimento;
 *  azul (esperança)    pouco, mas nunca para;
 *  índigo (compaixão)  queima Lágrimas Índigo do compartimento;
 *  violeta (amor)      jogadores juntos por perto e animais nascendo.
 * Agachar + mão vazia troca a emoção. Empurra o fluido para as máquinas ao lado (Refinaria ou canos de outros mods).
 */
public class GeradorEmocionalBE extends MaquinaBE {
    public static final int TANQUE = 16_000;
    static final Set<GeradorEmocionalBE> ATIVOS = Collections.newSetFromMap(new WeakHashMap<>());

    private final FluidTank tanque = new FluidTank(TANQUE, f -> f.getFluid() == EnergiaRegistro.EMOCAO.get()) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final ItemStackHandler slot = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int s, @NotNull ItemStack st) {
            return valorCombustivel(getBlockState().getValue(GeradorEmocionalBlock.EMOCAO), st) > 0;
        }

        @Override
        protected void onContentsChanged(int s) {
            setChanged();
        }
    };
    /** Saída: só tira (ninguém enche o gerador por fora). */
    private final IFluidHandler saida = new IFluidHandler() {
        public int getTanks() { return 1; }
        public @NotNull FluidStack getFluidInTank(int t) { return tanque.getFluid(); }
        public int getTankCapacity(int t) { return TANQUE; }
        public boolean isFluidValid(int t, @NotNull FluidStack s) { return false; }
        public int fill(FluidStack s, FluidAction a) { return 0; }
        public @NotNull FluidStack drain(FluidStack s, FluidAction a) { return tanque.drain(s, a); }
        public @NotNull FluidStack drain(int m, FluidAction a) { return tanque.drain(m, a); }
    };
    private final LazyOptional<IFluidHandler> capFluido = LazyOptional.of(() -> saida);
    private final LazyOptional<ItemStackHandler> capItem = LazyOptional.of(() -> slot);

    /** mB acumulados (fração) e mB de combustível ainda queimando. */
    private float acumulado;
    private int queimando;
    /** Últimos mB/s (para o painel) e por quanto tempo fica aceso. */
    public int ritmo;
    private int luz;

    public GeradorEmocionalBE(BlockPos pos, BlockState s) {
        super(EnergiaRegistro.GERADOR_BE.get(), pos, s);
    }

    public Tropa emocao() {
        return getBlockState().getValue(GeradorEmocionalBlock.EMOCAO);
    }

    public int quantidade() {
        return tanque.getFluidAmount();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) ATIVOS.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        ATIVOS.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        ATIVOS.remove(this);
    }

    /** Um acontecimento perto de algum gerador (chamado pelos eventos). */
    static void aconteceu(Level level, BlockPos onde, Tropa emocao, float mb, double raio) {
        if (level.isClientSide) return;
        for (GeradorEmocionalBE g : ATIVOS) {
            if (g.isRemoved() || g.level != level || g.emocao() != emocao) continue;
            if (g.worldPosition.distSqr(onde) > raio * raio) continue;
            g.acumulado += mb * Producao.mult();
        }
    }

    /** mB que um item rende no gerador desta emoção (0 = não serve). */
    static int valorCombustivel(Tropa t, ItemStack s) {
        if (t == Tropa.INDIGO && s.is(com.lanterna.lore.LoreItems.LAGRIMA_INDIGO.get())) return 600;
        if (t == Tropa.LARANJA) {
            if (s.is(Items.GOLD_INGOT)) return 150;
            if (s.is(Items.EMERALD)) return 300;
            if (s.is(Items.DIAMOND)) return 1200;
            if (s.is(Items.GOLD_BLOCK)) return 1350;
        }
        return 0;
    }

    @Override
    public void tickServidor() {
        if (level == null) return;
        long t = level.getGameTime();
        if ((t + worldPosition.asLong()) % 20 == 0) segundo();
        if (t % 10 == 3) empurrar();
        if (luz > 0 && --luz == 0) aceso(false);
    }

    private void segundo() {
        Tropa e = emocao();
        float antes = acumulado;
        AABB area = new AABB(worldPosition).inflate(10);
        switch (e) {
            case VERDE -> acumulado += 1f;
            case AZUL -> acumulado += 4f;
            case AMARELO -> {
                int n = Math.min(8, level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, area, m -> m instanceof Enemy && m.isAlive()).size());
                acumulado += 3f * n;
            }
            case VIOLETA -> {
                int n = level.getEntitiesOfClass(Player.class, area, p -> !p.isSpectator()).size();
                acumulado += n >= 2 ? 3f * n : 1f * n;
            }
            case INDIGO, LARANJA -> {
                if (queimando <= 0) {
                    ItemStack s = slot.getStackInSlot(0);
                    int v = valorCombustivel(e, s);
                    if (v > 0) {
                        queimando = v;
                        slot.extractItem(0, 1, false);
                    }
                }
                if (queimando > 0) {
                    int q = Math.min(queimando, 25);
                    queimando -= q;
                    acumulado += q;
                }
            }
            default -> {}
        }
        if (e == Tropa.VERDE || e == Tropa.AZUL || e == Tropa.VIOLETA || e == Tropa.AMARELO) acumulado += (acumulado - antes) * (Producao.mult() - 1);
        int inteiro = (int) acumulado;
        ritmo = inteiro;
        if (inteiro > 0) {
            int foi = tanque.fill(new FluidStack(EnergiaRegistro.EMOCAO.get(), inteiro), IFluidHandler.FluidAction.EXECUTE);
            acumulado -= inteiro;
            if (foi > 0) {
                luz = 30;
                aceso(true);
                if (level instanceof ServerLevel sl && level.random.nextInt(2) == 0) {
                    org.joml.Vector3f c = e.corVetor();
                    sl.sendParticles(new DustParticleOptions(c, 1.1f), worldPosition.getX() + 0.5, worldPosition.getY() + 1.15, worldPosition.getZ() + 0.5,
                            3, 0.2, 0.1, 0.2, 0);
                }
            }
        }
        if (acumulado > 2000) acumulado = 2000;
    }

    /** Manda a Emoção Bruta para os vizinhos que aceitam (até 500 mB a cada meio segundo). */
    private void empurrar() {
        if (tanque.isEmpty()) return;
        for (Direction d : Direction.values()) {
            if (tanque.isEmpty()) return;
            BlockEntity be = level.getBlockEntity(worldPosition.relative(d));
            if (be == null || be instanceof GeradorEmocionalBE) continue;
            IFluidHandler alvo = be.getCapability(ForgeCapabilities.FLUID_HANDLER, d.getOpposite()).orElse(null);
            if (alvo == null) continue;
            FluidStack tenta = tanque.drain(500, IFluidHandler.FluidAction.SIMULATE);
            int foi = alvo.fill(tenta, IFluidHandler.FluidAction.EXECUTE);
            if (foi > 0) tanque.drain(foi, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    @Override
    public InteractionResult usar(Player p, InteractionHand hand) {
        ItemStack mao = p.getItemInHand(hand);
        Tropa e = emocao();
        if (mao.isEmpty() && p.isShiftKeyDown()) {
            Tropa nova = Tropa.values()[(e.ordinal() + 1) % Tropa.values().length];
            ItemStack sobra = slot.extractItem(0, 64, false);
            if (!sobra.isEmpty()) entregar(p, sobra);
            level.setBlock(worldPosition, getBlockState().setValue(GeradorEmocionalBlock.EMOCAO, nova), 3);
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1f, 0.8f + nova.ordinal() * 0.08f);
            avisar(p, Component.translatable("energia.lanterna.gerador.trocou", Component.translatable("energia.lanterna.emocao." + nova.nome))
                    .withStyle(s -> s.withColor(nova.cor)));
            return InteractionResult.CONSUME;
        }
        if (!mao.isEmpty() && valorCombustivel(e, mao) > 0) {
            if (colocar(p, hand, slot, 0)) level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 1.1f);
            return InteractionResult.CONSUME;
        }
        avisar(p, Component.translatable("energia.lanterna.gerador.status", Component.translatable("energia.lanterna.emocao." + e.nome),
                tanque.getFluidAmount(), TANQUE, ritmo).withStyle(s -> s.withColor(e.cor)));
        p.sendSystemMessage(Component.translatable("energia.lanterna.gerador.dica." + e.nome).withStyle(ChatFormatting.GRAY));
        return InteractionResult.CONSUME;
    }

    @Override
    protected ItemStackHandler itens() {
        return slot;
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
        t.put("slot", slot.serializeNBT());
        t.putFloat("acumulado", acumulado);
        t.putInt("queimando", queimando);
        t.putInt("ritmo", ritmo);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        tanque.readFromNBT(t.getCompound("tanque"));
        slot.deserializeNBT(t.getCompound("slot"));
        acumulado = t.getFloat("acumulado");
        queimando = t.getInt("queimando");
        ritmo = t.getInt("ritmo");
    }
}
