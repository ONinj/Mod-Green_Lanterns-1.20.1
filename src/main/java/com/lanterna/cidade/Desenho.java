package com.lanterna.cidade;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Uma construção desenhada na memória, separada por pedaço do mundo (chunk), pronta para ser copiada na geração.
 */
public final class Desenho {
    private final Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<BlockState>> porChunk = new Long2ObjectOpenHashMap<>();
    private final Long2IntOpenHashMap topo = new Long2IntOpenHashMap();
    private final List<Object[]> baus = new ArrayList<>();
    private final List<Object[]> moldes = new ArrayList<>();
    private final List<Object[]> entidades = new ArrayList<>();
    private int blocos;

    public Desenho() {
        topo.defaultReturnValue(Integer.MIN_VALUE);
    }

    public void set(BlockPos p, BlockState s) {
        long ck = ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4);
        Long2ObjectOpenHashMap<BlockState> m = porChunk.get(ck);
        if (m == null) { m = new Long2ObjectOpenHashMap<>(); porChunk.put(ck, m); }
        if (m.put(p.asLong(), s) == null) blocos++;
        long col = ChunkPos.asLong(p.getX(), p.getZ());
        if (!s.isAir() && p.getY() > topo.get(col)) topo.put(col, p.getY());
    }

    @Nullable
    public BlockState get(BlockPos p) {
        Long2ObjectOpenHashMap<BlockState> m = porChunk.get(ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4));
        return m == null ? null : m.get(p.asLong());
    }

    @Nullable
    public Integer topo(int x, int z) {
        int t = topo.get(ChunkPos.asLong(x, z));
        return t == Integer.MIN_VALUE ? null : t;
    }

    void bau(BlockPos p, ResourceLocation tabela) { baus.add(new Object[]{p.immutable(), tabela}); }
    void molde(ResourceLocation id, BlockPos canto) { moldes.add(new Object[]{id, canto.immutable()}); }
    void entidade(CompoundTag t, BlockPos p) { entidades.add(new Object[]{t, p.immutable()}); }

    public int blocos() {
        return blocos;
    }

    /** Toca este pedaço do mundo? */
    public boolean toca(int cx, int cz) {
        if (porChunk.containsKey(ChunkPos.asLong(cx, cz))) return true;
        for (Object[] m : moldes) {
            BlockPos c = (BlockPos) m[1];
            if (Math.abs((c.getX() >> 4) - cx) <= 3 && Math.abs((c.getZ() >> 4) - cz) <= 3) return true;
        }
        return false;
    }

    /** Copia a parte deste pedaço do mundo para a geração (só dentro dele). */
    public void copiar(WorldGenLevel level, int cx, int cz, RandomSource rnd) {
        Long2ObjectOpenHashMap<BlockState> m = porChunk.get(ChunkPos.asLong(cx, cz));
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        if (m != null) for (var e : m.long2ObjectEntrySet()) {
            q.set(BlockPos.getX(e.getLongKey()), BlockPos.getY(e.getLongKey()), BlockPos.getZ(e.getLongKey()));
            if (q.getY() < level.getMinBuildHeight() || q.getY() >= level.getMaxBuildHeight()) continue;
            level.setBlock(q, e.getValue(), 2);
        }
        BoundingBox caixa = new BoundingBox(cx << 4, level.getMinBuildHeight(), cz << 4, (cx << 4) + 15, level.getMaxBuildHeight() - 1, (cz << 4) + 15);
        for (Object[] mo : moldes) {
            ResourceLocation id = (ResourceLocation) mo[0];
            BlockPos canto = (BlockPos) mo[1];
            level.getLevel().getStructureManager().get(id).ifPresent(t ->
                    t.placeInWorld(level, canto, canto, new StructurePlaceSettings().setBoundingBox(caixa), rnd, 2));
        }
        for (Object[] b : baus) {
            BlockPos p = (BlockPos) b[0];
            if ((p.getX() >> 4) == cx && (p.getZ() >> 4) == cz)
                RandomizableContainerBlockEntity.setLootTable(level, rnd, p, (ResourceLocation) b[1]);
        }
        for (Object[] en : entidades) {
            BlockPos p = (BlockPos) en[1];
            if ((p.getX() >> 4) != cx || (p.getZ() >> 4) != cz) continue;
            var e = EntityType.loadEntityRecursive((CompoundTag) en[0], level.getLevel(), x -> x);
            if (e != null) {
                e.setUUID(net.minecraft.util.Mth.createInsecureUUID(rnd));
                level.addFreshEntity(e);
            }
        }
    }
}
