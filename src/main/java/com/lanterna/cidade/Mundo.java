package com.lanterna.cidade;

import com.lanterna.LanternaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import org.jetbrains.annotations.Nullable;

/**
 * Onde as construções escrevem. Duas formas:
 *  - {@link Servidor}: direto no mundo (o jeito antigo, bloco a bloco no servidor);
 *  - {@link Virtual}: numa memória ({@link Desenho}), que depois é copiada pedaço por pedaço (chunk) durante a geração
 *    do mundo, nas threads de geração — o servidor não trava.
 */
public interface Mundo {
    void set(BlockPos p, BlockState s);

    BlockState get(BlockPos p);

    /** O mundo de verdade por trás (para criar entidades, ler moldes...). */
    ServerLevel nivel();

    default ResourceKey<Level> dimensao() {
        return nivel().dimension();
    }

    /** Altura do chão (primeiro bloco livre acima do terreno). */
    int altura(int x, int z);

    /** Uma entidade nova (as naves do hangar). */
    void entidade(Entity e);

    /** Baú (ou barril) já colocado em p ganha a tabela de itens. */
    void bau(BlockPos p, ResourceLocation tabela);

    /** Molde (.nbt) com o canto em "canto". */
    void molde(ResourceLocation id, BlockPos canto);

    // =====================================================================================================

    final class Servidor implements Mundo {
        private final ServerLevel l;

        public Servidor(ServerLevel l) { this.l = l; }

        @Override public void set(BlockPos p, BlockState s) { l.setBlock(p, s, 2); }
        @Override public BlockState get(BlockPos p) { return l.getBlockState(p); }
        @Override public ServerLevel nivel() { return l; }
        @Override public int altura(int x, int z) { return Kit.altura(l, x, z); }
        @Override public void entidade(Entity e) { l.addFreshEntity(e); }

        @Override
        public void bau(BlockPos p, ResourceLocation tabela) {
            if (l.getBlockEntity(p) instanceof RandomizableContainerBlockEntity be) be.setLootTable(tabela, l.random.nextLong());
        }

        @Override
        public void molde(ResourceLocation id, BlockPos canto) {
            l.getStructureManager().get(id).ifPresentOrElse(
                    t -> t.placeInWorld(l, canto, canto, new StructurePlaceSettings(), l.random, 2),
                    () -> LanternaMod.LOGGER.warn("Lanterna: molde {} não encontrado", id));
        }
    }

    /**
     * Desenha numa memória. A altura do terreno vem do gerador do mundo (a mesma conta que ele faz ao gerar), então dá
     * para saber onde fica o chão sem o mundo existir ainda. Ler um bloco não desenhado devolve pedra embaixo do chão e
     * ar em cima.
     */
    final class Virtual implements Mundo {
        private final ServerLevel l;
        private final Desenho d;
        private final java.util.function.IntBinaryOperator chao;

        public Virtual(ServerLevel l, Desenho d, java.util.function.IntBinaryOperator chao) {
            this.l = l; this.d = d; this.chao = chao;
        }

        @Override public void set(BlockPos p, BlockState s) { d.set(p, s); }

        @Override
        public BlockState get(BlockPos p) {
            BlockState s = d.get(p);
            if (s != null) return s;
            return p.getY() < chao.applyAsInt(p.getX(), p.getZ()) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }

        @Override public ServerLevel nivel() { return l; }

        @Override
        public int altura(int x, int z) {
            // o chão desenhado (o mais alto bloco desenhado na coluna) vence o terreno
            int t = chao.applyAsInt(x, z);
            Integer topo = d.topo(x, z);
            return topo != null ? Math.max(t, topo + 1) : t;
        }

        @Override
        public void entidade(Entity e) {
            CompoundTag t = new CompoundTag();
            if (e.save(t)) d.entidade(t, e.blockPosition());
            e.discard();
        }

        @Override public void bau(BlockPos p, ResourceLocation tabela) { d.bau(p, tabela); }
        @Override public void molde(ResourceLocation id, BlockPos canto) { d.molde(id, canto); }
    }

    /** Altura do terreno segundo o gerador (sem o mundo existir). */
    static int alturaGerador(ServerLevel l, int x, int z) {
        var src = l.getChunkSource();
        return src.getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, l, src.randomState());
    }

    /** Teste: um baú já com tabela. */
    @Nullable
    static ResourceLocation tabela(String planeta, String tipo) {
        return LanternaMod.id("chests/" + planeta + "/" + tipo);
    }

    /** Escreve direto no pedaço do mundo que está sendo gerado (só dentro dele). */
    final class Geracao implements Mundo {
        private final net.minecraft.world.level.WorldGenLevel l;
        private final int cx, cz;
        private final net.minecraft.util.RandomSource rnd;

        public Geracao(net.minecraft.world.level.WorldGenLevel l, int cx, int cz, net.minecraft.util.RandomSource rnd) {
            this.l = l; this.cx = cx; this.cz = cz; this.rnd = rnd;
        }

        private boolean dentro(BlockPos p) {
            return (p.getX() >> 4) == cx && (p.getZ() >> 4) == cz && p.getY() >= l.getMinBuildHeight() && p.getY() < l.getMaxBuildHeight();
        }

        @Override public void set(BlockPos p, BlockState s) { if (dentro(p)) l.setBlock(p, s, 2); }
        @Override public BlockState get(BlockPos p) { return l.getBlockState(p); }
        @Override public ServerLevel nivel() { return l.getLevel(); }
        @Override public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimensao() { return l.getLevel().dimension(); }
        @Override public int altura(int x, int z) { return l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x, z); }
        @Override public void entidade(Entity e) { if (dentro(e.blockPosition())) l.addFreshEntity(e); }
        @Override public void bau(BlockPos p, ResourceLocation t) { if (dentro(p)) RandomizableContainerBlockEntity.setLootTable(l, rnd, p, t); }

        @Override
        public void molde(ResourceLocation id, BlockPos canto) {
            net.minecraft.world.level.levelgen.structure.BoundingBox caixa = new net.minecraft.world.level.levelgen.structure.BoundingBox(cx << 4, l.getMinBuildHeight(), cz << 4, (cx << 4) + 15, l.getMaxBuildHeight() - 1, (cz << 4) + 15);
            l.getLevel().getStructureManager().get(id).ifPresent(t -> t.placeInWorld(l, canto, canto, new StructurePlaceSettings().setBoundingBox(caixa), rnd, 2));
        }
    }

}
