package com.lanterna.baterias;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** Colisão invisível que dá corpo ao modelo (é o "hitbox" da Bateria). */
public class BateriaCascaBlock extends Block {
    public BateriaCascaBlock(Properties p) { super(p); }
    /** Clique na Bateria: Chamado da Fonte (provação e juramento). */
    @Override
    public net.minecraft.world.InteractionResult use(BlockState s, net.minecraft.world.level.Level l, BlockPos pos, net.minecraft.world.entity.player.Player p,
                                                     net.minecraft.world.InteractionHand h, net.minecraft.world.phys.BlockHitResult hit) {
        if (h != net.minecraft.world.InteractionHand.MAIN_HAND) return net.minecraft.world.InteractionResult.PASS;
        if (l.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        return p instanceof net.minecraft.server.level.ServerPlayer sp && com.lanterna.fonte.FonteChamado.usarBateria(sp, pos)
                ? net.minecraft.world.InteractionResult.CONSUME : net.minecraft.world.InteractionResult.PASS;
    }
    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }
    @Override public float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) { return 1.0f; }
    @Override public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) { return true; }
}
