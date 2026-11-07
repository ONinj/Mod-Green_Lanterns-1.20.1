package com.lanterna.energia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Onde ficam as partes da mineradora em volta do painel (compartilhado entre servidor e desenho). */
public final class MineradoraGeometria {
    /** O poço da broca fica 3 blocos atrás do painel. */
    public static BlockPos poco(BlockPos painel, Direction frente) {
        return painel.relative(frente.getOpposite(), 3);
    }

    private MineradoraGeometria() {}
}
