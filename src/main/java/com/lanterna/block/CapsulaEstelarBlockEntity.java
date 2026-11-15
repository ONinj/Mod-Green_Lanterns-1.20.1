package com.lanterna.block;

import com.lanterna.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** O conteúdo da Cápsula Estelar, um por jogador (sorteado da tabela de saque na primeira vez que ele abre). */
public class CapsulaEstelarBlockEntity extends BlockEntity {
    public static final ResourceLocation PADRAO = com.lanterna.LanternaMod.id("chests/anel_caido");
    private ResourceLocation saque = PADRAO;
    private final Map<UUID, NonNullList<ItemStack>> conteudo = new HashMap<>();

    public CapsulaEstelarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAPSULA_ESTELAR.get(), pos, state);
    }

    public void setSaque(ResourceLocation tabela) {
        saque = tabela;
        setChanged();
    }

    /** Abre a parte deste jogador (sorteia na primeira vez). */
    public void abrir(ServerPlayer p) {
        NonNullList<ItemStack> itens = conteudo.get(p.getUUID());
        if (itens == null) {
            itens = NonNullList.withSize(27, ItemStack.EMPTY);
            SimpleContainer tmp = new SimpleContainer(27);
            var tabela = p.server.getLootData().getLootTable(saque);
            LootParams params = new LootParams.Builder((ServerLevel) level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                    .withParameter(LootContextParams.THIS_ENTITY, p).withLuck(p.getLuck()).create(LootContextParamSets.CHEST);
            tabela.fill(tmp, params, worldPosition.asLong() ^ p.getUUID().getLeastSignificantBits());
            for (int i = 0; i < 27; i++) itens.set(i, tmp.getItem(i));
            conteudo.put(p.getUUID(), itens);
            setChanged();
        }
        NonNullList<ItemStack> lista = itens;
        boolean[] pronto = {false};
        SimpleContainer c = new SimpleContainer(27) {
            @Override
            public boolean stillValid(Player pl) {
                return !isRemoved() && pl.distanceToSqr(Vec3.atCenterOf(worldPosition)) < 64;
            }

            @Override
            public void setChanged() {
                super.setChanged();
                if (!pronto[0]) return; // ainda enchendo a tela: não salva por cima
                for (int i = 0; i < 27; i++) lista.set(i, getItem(i));
                CapsulaEstelarBlockEntity.this.setChanged();
            }

            @Override
            public void stopOpen(Player pl) {
                super.stopOpen(pl);
                setChanged();
                level.playSound(null, worldPosition, SoundEvents.ENDER_CHEST_CLOSE, SoundSource.BLOCKS, 0.6f, 1.2f);
            }
        };
        for (int i = 0; i < 27; i++) c.setItem(i, lista.get(i));
        pronto[0] = true;
        level.playSound(null, worldPosition, SoundEvents.ENDER_CHEST_OPEN, SoundSource.BLOCKS, 0.6f, 1.2f);
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> ChestMenu.threeRows(id, inv, c), Component.translatable("block.lanterna.capsula_estelar")));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Saque", saque.toString());
        CompoundTag todos = new CompoundTag();
        for (var e : conteudo.entrySet()) todos.put(e.getKey().toString(), ContainerHelper.saveAllItems(new CompoundTag(), e.getValue()));
        tag.put("PorJogador", todos);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Saque")) saque = ResourceLocation.tryParse(tag.getString("Saque"));
        if (saque == null) saque = PADRAO;
        conteudo.clear();
        CompoundTag todos = tag.getCompound("PorJogador");
        for (String k : todos.getAllKeys()) {
            try {
                NonNullList<ItemStack> l = NonNullList.withSize(27, ItemStack.EMPTY);
                ContainerHelper.loadAllItems(todos.getCompound(k), l);
                conteudo.put(UUID.fromString(k), l);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
