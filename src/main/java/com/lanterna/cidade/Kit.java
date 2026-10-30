package com.lanterna.cidade;

import com.lanterna.naves.bloco.NaveBlocks;
import com.lanterna.naves.bloco.QuadroContratos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Ferramentas de construção (blocos, prédios ocos com andares, escadas e móveis). */
public final class Kit {
    public final ServerLevel L;
    public final Paleta p;

    public Kit(ServerLevel level, Paleta paleta) {
        this.L = level;
        this.p = paleta;
    }

    /** Altura do chão (carrega o pedaço do mundo antes, senão o mapa de alturas vem vazio). */
    public static int altura(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    public static BlockState b(Block x) {
        return x.defaultBlockState();
    }

    public void s(BlockPos pos, BlockState st) {
        L.setBlock(pos, st, 2);
    }

    public void s(int x, int y, int z, BlockState st) {
        L.setBlock(new BlockPos(x, y, z), st, 2);
    }

    public void box(BlockPos a, BlockPos c, BlockState st) {
        for (BlockPos q : BlockPos.betweenClosed(a, c)) L.setBlock(q, st, 2);
    }

    public void ar(BlockPos a, BlockPos c) {
        box(a, c, Blocks.AIR.defaultBlockState());
    }

    public static BlockState degrau(Block x, Direction f) {
        return x.defaultBlockState().setValue(StairBlock.FACING, f);
    }

    public static BlockState degrauInv(Block x, Direction f) {
        return x.defaultBlockState().setValue(StairBlock.FACING, f).setValue(StairBlock.HALF, Half.TOP);
    }

    public static BlockState lajeAlta(Block x) {
        return x.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    public static BlockState virado(Block x, Direction f) {
        return x.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, f);
    }

    /** Cama (ou beliche) de 2 blocos: pés em pos, cabeceira para "cabeca". */
    public void cama(Block bed, BlockPos pes, Direction cabeca) {
        BlockState st = bed.defaultBlockState().setValue(BedBlock.FACING, cabeca);
        s(pes, st.setValue(BedBlock.PART, BedPart.FOOT));
        s(pes.relative(cabeca), st.setValue(BedBlock.PART, BedPart.HEAD));
    }

    /** Quadro de contratos 2x2 numa parede, olhando para "frente". */
    public void quadro(BlockPos baixoDir, Direction frente) {
        BlockState q = NaveBlocks.QUADRO_CONTRATOS.get().defaultBlockState().setValue(QuadroContratos.FACING, frente);
        Direction esq = frente.getClockWise();
        s(baixoDir, q.setValue(QuadroContratos.PARTE, QuadroContratos.Parte.BAIXO_DIR));
        s(baixoDir.relative(esq), q.setValue(QuadroContratos.PARTE, QuadroContratos.Parte.BAIXO_ESQ));
        s(baixoDir.above(), q.setValue(QuadroContratos.PARTE, QuadroContratos.Parte.CIMA_DIR));
        s(baixoDir.above().relative(esq), q.setValue(QuadroContratos.PARTE, QuadroContratos.Parte.CIMA_ESQ));
    }

    /**
     * Prédio oco retangular (centro c no chão), com "andares" de altura h cada, laje entre andares com luzes,
     * faixa de janelas em cada andar, cantos de pilar e telhado com borda. A porta (3 de largura) fica no lado "porta".
     */
    public void predio(BlockPos c, int hw, int hd, int andares, int h, Direction porta) {
        int topo = andares * h;
        for (int dx = -hw; dx <= hw; dx++) for (int dz = -hd; dz <= hd; dz++) {
            boolean borda = Math.abs(dx) == hw || Math.abs(dz) == hd;
            boolean canto = Math.abs(dx) == hw && Math.abs(dz) == hd;
            s(c.offset(dx, -1, dz), borda ? p.faixa() : b(p.madeira()));
            for (int k = 0; k < topo; k++) {
                int nk = k % h;
                BlockState st;
                if (!borda) {
                    st = (nk == 0 && k > 0) ? ((Math.floorMod(dx, 4) == 0 && Math.floorMod(dz, 4) == 0) ? p.luz() : b(p.madeira())) : b(Blocks.AIR);
                } else if (canto || (Math.abs(dx) == hw ? Math.floorMod(dz, 6) == 0 : Math.floorMod(dx, 6) == 0)) st = p.pilar();
                else if (nk == 0 && k > 0) st = p.faixa();
                else if (nk >= 2 && nk <= 3 && (Math.abs(dx) == hw ? Math.floorMod(dz, 3) != 0 : Math.floorMod(dx, 3) != 0)) st = p.vidro();
                else st = p.parede();
                s(c.offset(dx, k, dz), st);
            }
            s(c.offset(dx, topo, dz), borda ? p.faixa() : ((Math.floorMod(dx, 4) == 2 && Math.floorMod(dz, 4) == 2) ? p.luz() : p.teto()));
            if (borda) s(c.offset(dx, topo + 1, dz), (dx + dz) % 2 == 0 ? p.parede() : b(Blocks.AIR));
        }
        // luz no teto do térreo também
        for (int dx = -hw + 2; dx <= hw - 2; dx += 4) for (int dz = -hd + 2; dz <= hd - 2; dz += 4) {
            if (andares == 1) s(c.offset(dx, topo, dz), p.luz());
        }
        int alcance = porta.getAxis() == Direction.Axis.X ? hw : hd;
        BlockPos pp = c.relative(porta, alcance);
        Direction lado = porta.getClockWise();
        for (int w = -1; w <= 1; w++) for (int k = 0; k < 3; k++) s(pp.relative(lado, w).above(k), b(Blocks.AIR));
        for (int w = -2; w <= 2; w++) s(pp.relative(lado, w).above(3), p.faixa());
        s(pp.relative(lado, -2).relative(porta).above(2), p.luz());
        s(pp.relative(lado, 2).relative(porta).above(2), p.luz());
        // degrau na frente
        for (int w = -1; w <= 1; w++) s(pp.relative(porta).relative(lado, w).below(), p.faixa());
    }

    /** Escada de um andar ao outro: sobe "h" degraus na direção "sobe", começando em pos (chão do andar). */
    public void escada(BlockPos inicio, Direction sobe, int h) {
        for (int i = 0; i < h; i++) {
            BlockPos q = inicio.relative(sobe, i).above(i);
            s(q, degrau(p.escada(), sobe));
            for (int k = 1; k <= 3; k++) s(q.above(k), b(Blocks.AIR)); // espaço da cabeça (abre a laje de cima)
            if (i > 0) s(q.below(), p.parede());
        }
    }

    /** Mesa com cadeiras dos dois lados (eixo x). */
    public void mesa(BlockPos c) {
        s(c, b(p.madeira() == Blocks.BIRCH_PLANKS ? Blocks.BIRCH_FENCE : Blocks.DARK_OAK_FENCE));
        s(c.above(), b(Blocks.SPRUCE_PRESSURE_PLATE));
        s(c.west(), degrau(Blocks.SPRUCE_STAIRS, Direction.WEST));
        s(c.east(), degrau(Blocks.SPRUCE_STAIRS, Direction.EAST));
    }

    /** Poste de luz. */
    public void poste(BlockPos base, int alt) {
        for (int k = 0; k < alt; k++) s(base.above(k), p.pilar());
        s(base.above(alt), p.luz());
    }

    /** Estandarte da Tropa (coluna de lã com destaque em cima). */
    public void estandarte(BlockPos base, int alt) {
        for (int k = 0; k < alt; k++) s(base.above(k), b(p.la()));
        s(base.above(alt), p.destaque());
    }
}
