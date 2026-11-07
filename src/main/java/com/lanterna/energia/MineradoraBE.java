package com.lanterna.energia;

import com.lanterna.LanternaConfig;
import com.lanterna.guilda.Conquista;
import com.lanterna.guilda.Conquistas;
import com.lanterna.guilda.GuildaItems;
import com.lanterna.guilda.Guildas;
import com.lanterna.registry.ModItems;
import com.lanterna.space.Archetype;
import com.lanterna.space.SpaceBodies;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Mineradora Planetária: gasta FE (de qualquer mod) para extrair Essência de Poder do planeta conquistado, mais
 * minérios do tipo do planeta. Trabalha para a guilda dona do planeta (se o planeta for tomado, passa a trabalhar
 * para a nova dona). O nível vem das melhorias feitas no Forjador da guilda.
 * Com o planeta descarregado ela não roda; quando volta a carregar, recupera o tempo perdido (tempo real) usando a
 * energia que tinha guardada — assim não precisa deixar o chunk carregado no servidor.
 */
public class MineradoraBE extends MaquinaBE {
    public static final int CAPACIDADE = 4_000_000;
    private final Bateria energia = new Bateria(CAPACIDADE, 50_000, 0, this::setChanged);
    private final ItemStackHandler saida = new ItemStackHandler(9) {
        @Override
        protected void onContentsChanged(int s) {
            setChanged();
        }
    };
    /** Por fora só sai (funis e canos tiram a essência). */
    private final IItemHandler porFora = new IItemHandler() {
        public int getSlots() { return 9; }
        public @NotNull ItemStack getStackInSlot(int s) { return saida.getStackInSlot(s); }
        public @NotNull ItemStack insertItem(int s, @NotNull ItemStack st, boolean sim) { return st; }
        public @NotNull ItemStack extractItem(int s, int n, boolean sim) { return saida.extractItem(s, n, sim); }
        public int getSlotLimit(int s) { return 64; }
        public boolean isItemValid(int s, @NotNull ItemStack st) { return false; }
    };
    private final LazyOptional<IEnergyStorage> capEnergia = LazyOptional.of(() -> energia);
    private final LazyOptional<IItemHandler> capItem = LazyOptional.of(() -> porFora);

    /** Planeta ("" = modo de teste do criativo, trabalha em qualquer lugar no nível 1). */
    public String planeta = "";
    public boolean instalada;
    public long montadaEm;
    public int progresso;
    private long ultimoMs;
    /** Essências recuperadas na última volta do planeta (mostra no painel). */
    private int recuperadas;
    /** Estado para o desenho: 0 parada, 1 trabalhando, 2 sem energia, 3 cheia, 4 sem dono. */
    public int estado;
    /** Vida da estrutura (ataques); com 0 para até ser consertada. */
    public int integridade = 100;
    /** Cliente: tempo de animação (só anda trabalhando). */
    public float anim;
    public float animAnt;

    public MineradoraBE(BlockPos pos, BlockState s) {
        super(EnergiaRegistro.MINERADORA_BE.get(), pos, s);
    }

    public void instalar(String planeta, long tempo) {
        this.planeta = planeta;
        this.instalada = true;
        this.montadaEm = tempo;
        this.ultimoMs = System.currentTimeMillis();
        sincronizar();
    }

    @Nullable
    private Conquista conquista() {
        if (planeta.isEmpty() || level == null || level.getServer() == null) return null;
        return Guildas.get(level.getServer()).conquistas.get(planeta);
    }

    /** Nível das mineradoras deste planeta (melhorias do Forjador). */
    public int nivel() {
        Conquista c = conquista();
        return c == null ? 1 : Math.max(1, Math.min(4, c.minerador));
    }

    /** Ticks de trabalho por leva. */
    public int ciclo() {
        double f = Conquista.MIN_TEMPO[nivel() - 1];
        return Math.max(20, (int) (LanternaConfig.GUILDA_MIN_ESSENCIA.get() * 60 * 20 * f));
    }

    public int porLeva() {
        return Conquista.MIN_QTD[nivel() - 1];
    }

    private boolean temDono() {
        if (!instalada) return false;
        if (planeta.isEmpty()) return true;
        Conquista c = conquista();
        return c != null && !c.daTropa;
    }

    private boolean cheia() {
        for (int i = 0; i < saida.getSlots(); i++) if (saida.getStackInSlot(i).getCount() < 64) return false;
        return true;
    }

    @Override
    public void tickServidor() {
        if (level == null || !instalada) return;
        long agora = System.currentTimeMillis();
        if (ultimoMs > 0 && agora - ultimoMs > 10_000) recuperar(agora - ultimoMs);
        ultimoMs = agora;
        int novo;
        int fe = Producao.feMineradora();
        if (!temDono()) novo = 4;
        else if (integridade <= 0) novo = 5;
        else if (cheia()) novo = 3;
        else if (!energia.gastar(fe)) novo = 2;
        else {
            novo = 1;
            if (++progresso >= ciclo()) {
                progresso = 0;
                produzir(1);
                level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 1f, 0.7f);
            }
            if (level.getGameTime() % 6 == 0 && level instanceof ServerLevel sl) {
                Direction f = getBlockState().getValue(MaquinaBlock.FACING);
                BlockPos poco = MineradoraGeometria.poco(worldPosition, f);
                BlockState chao = level.getBlockState(poco.below());
                if (chao.isAir()) chao = Blocks.STONE.defaultBlockState();
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, chao), poco.getX() + 0.5, poco.getY() + 0.2, poco.getZ() + 0.5, 4, 0.5, 0.1, 0.5, 0.05);
            }
            if (level.getGameTime() % 40 == 0) level.playSound(null, worldPosition, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.35f, 0.6f);
            MineradoraAtaques.tick(this);
        }
        if (novo != estado) {
            estado = novo;
            aceso(estado == 1);
            sincronizar();
        } else if (level.getGameTime() % 100 == 0) sincronizar();
    }

    /** Trabalho feito com o planeta descarregado (limitado pela energia guardada e pelo teto de horas). */
    private void recuperar(long ms) {
        if (!temDono() || integridade <= 0) return;
        long ticks = Math.min(ms / 50, LanternaConfig.ENERGIA_OFFLINE_HORAS.get() * 72_000L);
        int fe = Producao.feMineradora();
        long trabalho = fe <= 0 ? ticks : Math.min(ticks, energia.getEnergyStored() / fe);
        if (trabalho <= 0) return;
        energia.gastar((int) (trabalho * fe));
        long total = progresso + trabalho;
        int levas = (int) (total / ciclo());
        progresso = (int) (total % ciclo());
        recuperadas = produzir(levas);
    }

    /** Põe n levas na saída; devolve quantas essências entraram. */
    private int produzir(int levas) {
        if (levas <= 0 || level == null) return 0;
        RandomSource r = level.random;
        int ess = 0;
        for (int i = 0; i < levas; i++) {
            ItemStack e = new ItemStack(GuildaItems.ESSENCIA.get(), porLeva());
            ItemStack sobra = ItemHandlerHelper.insertItem(saida, e, false);
            ess += e.getCount() - sobra.getCount();
            if (!sobra.isEmpty()) break;
            if (r.nextFloat() < 0.4f) ItemHandlerHelper.insertItem(saida, subproduto(r), false);
        }
        setChanged();
        return ess;
    }

    /** Minério do tipo do planeta (o que a broca traz junto). */
    private ItemStack subproduto(RandomSource r) {
        Archetype a = level == null ? null : Archetype.byDimension(level.dimension());
        if (r.nextFloat() < 0.15f) return new ItemStack(ModItems.FRAGMENTO_ESTELAR.get());
        Item it = a == null ? Items.RAW_IRON : switch (a) {
            case LUSH -> r.nextBoolean() ? Items.EMERALD : Items.RAW_COPPER;
            case DESERT -> Items.RAW_GOLD;
            case ICE -> Items.LAPIS_LAZULI;
            case VOLCANIC -> r.nextInt(6) == 0 ? Items.ANCIENT_DEBRIS : Items.QUARTZ;
            case OCEAN -> Items.PRISMARINE_CRYSTALS;
            case CRYSTAL -> Items.AMETHYST_SHARD;
            case FUNGAL -> Items.GLOWSTONE_DUST;
            case BARREN -> r.nextInt(5) == 0 ? Items.DIAMOND : Items.RAW_IRON;
        };
        return new ItemStack(it, 1 + r.nextInt(it == Items.DIAMOND || it == Items.ANCIENT_DEBRIS ? 1 : 3));
    }

    @Override
    public void tickCliente() {
        animAnt = anim;
        if (estado == 1) anim += 1f;
    }

    public float anim(float pt) {
        return animAnt + (anim - animAnt) * pt;
    }

    @Override
    public InteractionResult usar(Player p, InteractionHand hand) {
        if (!p.isShiftKeyDown() && p.getItemInHand(hand).isEmpty()) {
            boolean deu = false;
            for (int i = 0; i < saida.getSlots(); i++) {
                ItemStack s = saida.extractItem(i, 64, false);
                if (!s.isEmpty()) { entregar(p, s); deu = true; }
            }
            if (deu) {
                level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.7f, 1f);
                return InteractionResult.CONSUME;
            }
        }
        if (integridade < 100 && p.getItemInHand(hand).is(Items.IRON_INGOT)) {
            integridade = Math.min(100, integridade + 10);
            if (!p.getAbilities().instabuild) p.getItemInHand(hand).shrink(1);
            level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7f, 1.3f);
            avisar(p, Component.translatable("energia.lanterna.mineradora.consertou", integridade).withStyle(ChatFormatting.GOLD));
            sincronizar();
            return InteractionResult.CONSUME;
        }
        status(p);
        return InteractionResult.CONSUME;
    }

    private void status(Player p) {
        SpaceBodies.Body b = planeta.isEmpty() ? null : SpaceBodies.byId(planeta);
        Conquista c = conquista();
        Component onde = b != null ? b.displayName() : Component.translatable("energia.lanterna.mineradora.modo_teste");
        String dono = c == null ? "-" : Conquistas.nomeGuilda(c.guilda);
        p.sendSystemMessage(Component.translatable("energia.lanterna.mineradora.titulo", onde, dono).withStyle(ChatFormatting.GOLD));
        p.sendSystemMessage(Component.translatable("energia.lanterna.mineradora.estado." + estado).withStyle(estado == 1 ? ChatFormatting.GREEN : ChatFormatting.RED));
        p.sendSystemMessage(Component.translatable("energia.lanterna.mineradora.energia", fe(energia.getEnergyStored()), fe(CAPACIDADE), Producao.feMineradora()));
        p.sendSystemMessage(Component.translatable("energia.lanterna.mineradora.ritmo", nivel(), porLeva(), String.format("%.1f", ciclo() / 1200.0),
                progresso * 100 / ciclo()));
        p.sendSystemMessage(Component.translatable("energia.lanterna.mineradora.integridade", integridade));
        if (recuperadas > 0) p.sendSystemMessage(Component.translatable("energia.lanterna.mineradora.recuperou", recuperadas).withStyle(ChatFormatting.AQUA));
        p.sendSystemMessage(Component.translatable("energia.lanterna.mineradora.dica").withStyle(ChatFormatting.GRAY));
    }

    public void danificar(int d) {
        integridade = Math.max(0, integridade - d);
        sincronizar();
    }

    public int energia() {
        return energia.getEnergyStored();
    }

    public void darEnergia(int v) {
        energia.gerar(v);
    }

    public int essencias() {
        int n = 0;
        for (int i = 0; i < saida.getSlots(); i++) if (saida.getStackInSlot(i).is(GuildaItems.ESSENCIA.get())) n += saida.getStackInSlot(i).getCount();
        return n;
    }

    /** Teste: finge que o planeta ficou descarregado por ms. */
    public void fingirAusencia(long ms) {
        ultimoMs = System.currentTimeMillis() - ms;
    }

    @Override
    protected ItemStackHandler itens() {
        return saida;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(7, 10, 7);
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
        t.put("saida", saida.serializeNBT());
        t.putString("planeta", planeta);
        t.putBoolean("instalada", instalada);
        t.putLong("montadaEm", montadaEm);
        t.putInt("progresso", progresso);
        t.putLong("ultimoMs", ultimoMs);
        t.putInt("recuperadas", recuperadas);
        t.putInt("estado", estado);
        t.putInt("integridade", integridade);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        energia.definir(t.getInt("energia"));
        saida.deserializeNBT(t.getCompound("saida"));
        planeta = t.getString("planeta");
        instalada = t.getBoolean("instalada");
        montadaEm = t.getLong("montadaEm");
        progresso = t.getInt("progresso");
        ultimoMs = t.getLong("ultimoMs");
        recuperadas = t.getInt("recuperadas");
        estado = t.getInt("estado");
        integridade = t.contains("integridade") ? t.getInt("integridade") : 100;
    }
}
