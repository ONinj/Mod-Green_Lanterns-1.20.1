package com.lanterna.energia;

import com.lanterna.baterias.Tropa;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/** O gerador guarda qual emoção ele queima (começa na Tropa do anel de quem colocou). */
public class GeradorEmocionalBlock extends MaquinaBlock {
    public static final EnumProperty<Tropa> EMOCAO = EnumProperty.create("emocao", Tropa.class);

    public GeradorEmocionalBlock(Properties p) {
        super(p, () -> EnergiaRegistro.GERADOR_BE.get());
    }

    @Override
    protected BlockState estadoPadrao() {
        return super.estadoPadrao().setValue(EMOCAO, Tropa.VERDE);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(EMOCAO);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        Tropa t = Tropa.VERDE;
        if (c.getPlayer() instanceof ServerPlayer sp) {
            Tropa a = com.lanterna.fonte.FonteChamado.tropaDoAnel(sp);
            if (a != null) t = a;
        }
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()).setValue(EMOCAO, t);
    }

    static Direction frente(BlockState s) {
        return s.getValue(FACING);
    }
}
