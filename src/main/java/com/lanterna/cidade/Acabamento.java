package com.lanterna.cidade;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Acabamento das construções, para parecerem construídas à mão e não "coladas" por um programa:
 *  - vidros finos, grades, cercas, muros e escadas: o jogo recalcula as ligações com os vizinhos quando o pedaço do
 *    mundo termina de nascer (vidro emenda no vidro e na parede, cerca emenda na cerca, escada faz a quina);
 *  - lampiões: pendurados quando há teto em cima, de pé quando há chão embaixo; soltos no ar ganham uma corrente até o
 *    teto ou um poste até o chão;
 *  - tochas de pé sem chão viram tochas de parede presas na parede do lado.
 */
public final class Acabamento {

    /** Lampiões e tochas: precisam de algo que os segure. */
    public static boolean pendurado(BlockState s) {
        Block b = s.getBlock();
        return b instanceof LanternBlock || (b instanceof TorchBlock && !(b instanceof WallTorchBlock));
    }

    /** Blocos cuja forma depende dos vizinhos: o jogo precisa recalcular depois que tudo em volta existe. */
    public static boolean conectavel(BlockState s) {
        Block b = s.getBlock();
        return b instanceof CrossCollisionBlock || b instanceof WallBlock || b instanceof StairBlock || b instanceof FenceGateBlock
                || b instanceof ChainBlock || b instanceof RedStoneWireBlock;
    }

    private static boolean firme(BlockState s) {
        return !s.isAir() && s.canOcclude();
    }

    /** Pode segurar um lampião (em cima ou embaixo): bloco cheio, cerca, muro, corrente, grade, viga. */
    private static boolean segura(BlockState s) {
        Block b = s.getBlock();
        return firme(s) || b instanceof CrossCollisionBlock || b instanceof WallBlock || b instanceof ChainBlock
                || b instanceof SlabBlock || b instanceof StairBlock || b instanceof TrapDoorBlock;
    }

    /**
     * Arruma lampiões e tochas que ficaram soltos. get/set leem e escrevem no mesmo lugar onde a construção foi feita
     * (o desenho na memória ou o mundo).
     */
    public static void prender(BlockPos p, Function<BlockPos, BlockState> get, BiConsumer<BlockPos, BlockState> set) {
        BlockState s = get.apply(p);
        Block b = s.getBlock();
        if (b instanceof LanternBlock) {
            BlockState baixo = get.apply(p.below()), cima = get.apply(p.above());
            boolean pendurado = s.getValue(LanternBlock.HANGING);
            if (pendurado && segura(cima)) return;
            if (!pendurado && segura(baixo)) return;
            if (segura(cima)) { set.accept(p, s.setValue(LanternBlock.HANGING, true)); return; }
            if (segura(baixo)) { set.accept(p, s.setValue(LanternBlock.HANGING, false)); return; }
            // solto: corrente até um teto perto, ou poste até o chão perto
            for (int i = 2; i <= 6; i++) {
                if (segura(get.apply(p.above(i)))) {
                    for (int j = 1; j < i; j++) set.accept(p.above(j), Blocks.CHAIN.defaultBlockState());
                    set.accept(p, s.setValue(LanternBlock.HANGING, true));
                    return;
                }
                if (!get.apply(p.above(i)).isAir()) break;
            }
            for (int i = 2; i <= 4; i++) {
                if (segura(get.apply(p.below(i)))) {
                    for (int j = 1; j < i; j++) set.accept(p.below(j), Blocks.DARK_OAK_FENCE.defaultBlockState());
                    set.accept(p, s.setValue(LanternBlock.HANGING, false));
                    return;
                }
                if (!get.apply(p.below(i)).isAir()) break;
            }
            return;
        }
        if (b instanceof TorchBlock && !(b instanceof WallTorchBlock) && !(b instanceof RedstoneTorchBlock)) {
            if (segura(get.apply(p.below()))) return;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (firme(get.apply(p.relative(d)))) {
                    Block parede = b == Blocks.SOUL_TORCH ? Blocks.SOUL_WALL_TORCH : Blocks.WALL_TORCH;
                    set.accept(p, parede.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, d.getOpposite()));
                    return;
                }
            }
        }
    }

    /**
     * Depois de todas as peças copiadas num pedaço do mundo: confere de novo os lampiões e tochas dele com o mundo de
     * verdade (uma peça vizinha pode ter apagado o apoio que outra desenhou).
     */
    public static void prenderNoPedaco(net.minecraft.world.level.WorldGenLevel l, int cx, int cz, int y0, int y1) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        java.util.List<BlockPos> ps = new java.util.ArrayList<>();
        for (int x = cx << 4; x < (cx << 4) + 16; x++) for (int z = cz << 4; z < (cz << 4) + 16; z++)
            for (int y = Math.max(y0, l.getMinBuildHeight()); y <= Math.min(y1, l.getMaxBuildHeight() - 1); y++)
                if (pendurado(l.getBlockState(m.set(x, y, z)))) ps.add(m.immutable());
        for (BlockPos p : ps) prender(p, l::getBlockState, (q, st) -> {
            if ((q.getX() >> 4) == cx && (q.getZ() >> 4) == cz) l.setBlock(q, st, 2);
        });
    }

    private Acabamento() {}
}
