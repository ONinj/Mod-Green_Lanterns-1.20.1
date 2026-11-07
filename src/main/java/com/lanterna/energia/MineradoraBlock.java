package com.lanterna.energia;

import com.lanterna.guilda.Conquista;
import com.lanterna.guilda.Conquistas;
import com.lanterna.guilda.Guilda;
import com.lanterna.guilda.Guildas;
import com.lanterna.space.SpaceBodies;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Mineradora Planetária: só funciona num planeta que a sua guilda conquistou. O bloco é o painel de controle; a
 * torre de perfuração, o braço escavadeira e a esteira são montados em volta (só visual, não bloqueiam a passagem).
 */
public class MineradoraBlock extends MaquinaBlock {
    private static final VoxelShape FORMA = Block.box(0, 0, 0, 16, 14, 16);

    public MineradoraBlock(Properties p) {
        super(p, () -> EnergiaRegistro.MINERADORA_BE.get());
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState s, @Nullable LivingEntity quem, ItemStack item) {
        super.setPlacedBy(level, pos, s, quem, item);
        if (level.isClientSide || !(quem instanceof ServerPlayer p) || !(level.getBlockEntity(pos) instanceof MineradoraBE be)) return;
        Component erro = validar(p, level, pos);
        if (erro == null) {
            SpaceBodies.Body b = Conquistas.planetaGerado(level, pos);
            be.instalar(b == null ? "" : b.id(), level.getGameTime());
            p.displayClientMessage(Component.translatable("energia.lanterna.mineradora.instalada").withStyle(ChatFormatting.GREEN), false);
        } else if (p.isCreative()) {
            be.instalar("", level.getGameTime());
            p.displayClientMessage(Component.translatable("energia.lanterna.mineradora.teste").withStyle(ChatFormatting.YELLOW), false);
        } else {
            p.displayClientMessage(erro.copy().withStyle(ChatFormatting.RED), false);
            level.removeBlock(pos, false);
            if (!p.getInventory().add(new ItemStack(EnergiaRegistro.MINERADORA_ITEM.get()))) p.drop(new ItemStack(EnergiaRegistro.MINERADORA_ITEM.get()), false);
        }
    }

    /** null = pode instalar aqui. */
    @Nullable
    static Component validar(ServerPlayer p, Level level, BlockPos pos) {
        SpaceBodies.Body b = Conquistas.planetaGerado(level, pos);
        if (b == null) return Component.translatable("energia.lanterna.mineradora.erro.planeta");
        Guildas gs = Guildas.get(p.server);
        Conquista c = gs.conquistas.get(b.id());
        Guilda g = gs.daJogador(p.getUUID());
        if (c == null || g == null || !c.guilda.equals(g.id)) return Component.translatable("energia.lanterna.mineradora.erro.dono");
        return null;
    }
}
