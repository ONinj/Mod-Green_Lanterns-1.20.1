package com.lanterna.terra;

import com.lanterna.LanternaMod;
import com.lanterna.cidade.Desenho;
import com.lanterna.cidade.Kit;
import com.lanterna.cidade.Layout;
import com.lanterna.cidade.Mobilia;
import com.lanterna.cidade.Mundo;
import com.lanterna.cidade.Obra;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * A Cidade Aurora (mundos da 0.48+), gerada junto com o terreno da Terra, no lugar escolhido quando o mundo é criado.
 *
 *   praça central com a fonte e o Monumento da Estrela Cadente · avenida leste-oeste e rua norte-sul · anel de ruas
 *   prefeitura, mercado, escola, clínica, padaria · 18 casas mobiliadas (térreas e sobrados) com quintal
 *   o Observatório no fim da avenida leste, com a Base Vigília embaixo e o terraço do Ritual · hortas a leste
 *   cerca com 4 portões
 */
public final class AuroraGeracao extends Feature<NoneFeatureConfiguration> {
    public static final int R = 72;
    static final int ANEL = 36;

    public AuroraGeracao(Codec<NoneFeatureConfiguration> c) {
        super(c);
    }

    record Peca(String id, int x0, int z0, int x1, int z1, Consumer<Kit> obra) {
        boolean toca(int cx, int cz) {
            return (cx << 4) + 15 >= x0 && (cx << 4) <= x1 && (cz << 4) + 15 >= z0 && (cz << 4) <= z1;
        }
    }

    private static volatile List<Peca> pecas;
    private static volatile BlockPos pecasDe;
    private static final Map<String, CompletableFuture<Desenho>> DESENHOS = new ConcurrentHashMap<>();

    public static void esquecer() {
        pecas = null;
        pecasDe = null;
        DESENHOS.clear();
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!Layout.novo()) return false;
        WorldGenLevel l = ctx.level();
        if (!l.getLevel().dimension().equals(Level.OVERWORLD)) return false;
        BlockPos c = TerraSite.centro;
        if (c == null) return false;
        int cx = ctx.origin().getX() >> 4, cz = ctx.origin().getZ() >> 4;
        int mx = Math.max(Math.abs((cx << 4) + 8 - c.getX()) - 8, 0), mz = Math.max(Math.abs((cz << 4) + 8 - c.getZ()) - 8, 0);
        if (mx * mx + mz * mz > (R + 10) * (R + 10)) return false;
        try {
            List<Peca> ps = pecas(c);
            Mundo g = new Mundo.Geracao(l, cx, cz, ctx.random());
            terreno(new Kit(g, TerraSite.paleta()), c, cx, cz);
            for (Peca p : ps) {
                if (!p.toca(cx, cz)) continue;
                Desenho d = desenho(l.getLevel(), c, p);
                if (d != null && d.toca(cx, cz)) d.copiar(l, cx, cz, ctx.random());
            }
        } catch (Exception e) {
            LanternaMod.LOGGER.error("Lanterna: falha gerando a Cidade Aurora", e);
        }
        return true;
    }

    /**
     * Mundos em que o gerador não põe as decorações (mundo plano, por exemplo): a mesma Cidade Aurora é construída aos
     * poucos, com o servidor ligado (como era a Vila Aurora).
     */
    public static void construirAgora(ServerLevel l, BlockPos c, Runnable fim) {
        List<net.minecraft.world.level.ChunkPos> pedacos = new ArrayList<>();
        int r = (R + 12) >> 4;
        for (int x = -r - 1; x <= r + 1; x++) for (int z = -r - 1; z <= r + 1; z++) pedacos.add(new net.minecraft.world.level.ChunkPos((c.getX() >> 4) + x, (c.getZ() >> 4) + z));
        com.lanterna.cidade.Obra.carregar(l, pedacos);
        Kit k = new Kit(new Mundo.Servidor(l), TerraSite.paleta());
        for (var p : pedacos) com.lanterna.cidade.Obra.etapa(l, x -> terreno(k, c, p.x, p.z));
        for (Peca p : pecas(c)) com.lanterna.cidade.Obra.etapa(l, x -> {
            try { p.obra().accept(k); } catch (Exception e) { LanternaMod.LOGGER.error("Lanterna: falha construindo {} da Cidade Aurora", p.id(), e); }
        });
        com.lanterna.cidade.Obra.etapa(l, x -> fim.run());
    }

    private static Desenho desenho(ServerLevel l, BlockPos c, Peca p) {
        CompletableFuture<Desenho> nova = new CompletableFuture<>();
        CompletableFuture<Desenho> f = DESENHOS.putIfAbsent(p.id(), nova);
        if (f != null) return f.join();
        try {
            Desenho d = new Desenho();
            Kit k = new Kit(new Mundo.Virtual(l, d, (x, z) -> chao(l, c, x, z)), TerraSite.paleta());
            Obra.imediato(l, () -> p.obra().accept(k));
            nova.complete(d);
            return d;
        } catch (Throwable t) {
            LanternaMod.LOGGER.error("Lanterna: falha desenhando {} da Cidade Aurora", p.id(), t);
            nova.complete(null);
            return null;
        }
    }

    static int chao(ServerLevel l, BlockPos c, int x, int z) {
        double dx = x - c.getX(), dz = z - c.getZ();
        if (dx * dx + dz * dz <= R * R) return c.getY();
        return Mundo.alturaGerador(l, x, z);
    }

    /** Depois da vegetação: tira as árvores naturais que nasceram dentro da cidade (as dos quintais ficam: folhas presas). */
    public static final class Limpeza extends Feature<NoneFeatureConfiguration> {
        public Limpeza(Codec<NoneFeatureConfiguration> c) {
            super(c);
        }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
            if (!Layout.novo()) return false;
            WorldGenLevel l = ctx.level();
            BlockPos c = TerraSite.centro;
            if (c == null || !l.getLevel().dimension().equals(Level.OVERWORLD)) return false;
            int cx = ctx.origin().getX() >> 4, cz = ctx.origin().getZ() >> 4;
            int mx = Math.max(Math.abs((cx << 4) + 8 - c.getX()) - 8, 0), mz = Math.max(Math.abs((cz << 4) + 8 - c.getZ()) - 8, 0);
            if (mx * mx + mz * mz > R * R) return false;
            var m = new BlockPos.MutableBlockPos();
            for (int x = cx << 4; x < (cx << 4) + 16; x++) for (int z = cz << 4; z < (cz << 4) + 16; z++) {
                double dx = x - c.getX(), dz = z - c.getZ();
                if (dx * dx + dz * dz > R * R) continue;
                boolean arvore = false;
                for (int y = c.getY() + 30; y >= c.getY(); y--) {
                    BlockState s = l.getBlockState(m.set(x, y, z));
                    if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT)) { l.setBlock(m, Blocks.AIR.defaultBlockState(), 2); arvore = true; }
                    else if (arvore && s.is(net.minecraft.tags.BlockTags.LOGS)) l.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
                    else if (s.is(Blocks.TALL_GRASS) || s.is(Blocks.VINE)) l.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
                }
            }
            return true;
        }
    }

    // ===================== terreno =====================

    static boolean rua(int dx, int dz) {
        if (Math.abs(dz) <= 2 || Math.abs(dx) <= 2) return true;
        boolean noAnel = (Math.abs(Math.abs(dx) - ANEL) <= 1 && Math.abs(dz) <= ANEL) || (Math.abs(Math.abs(dz) - ANEL) <= 1 && Math.abs(dx) <= ANEL);
        return noAnel;
    }

    static void terreno(Kit k, BlockPos c, int cx, int cz) {
        BlockState ar = Kit.b(Blocks.AIR), grama = Kit.b(Blocks.GRASS_BLOCK), terra = Kit.b(Blocks.DIRT), pedra = Kit.b(Blocks.STONE);
        var m = new BlockPos.MutableBlockPos();
        for (int x = cx << 4; x < (cx << 4) + 16; x++) for (int z = cz << 4; z < (cz << 4) + 16; z++) {
            int dx = x - c.getX(), dz = z - c.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > R + 10) continue;
            int h = k.altura(x, z);
            if (d > R) {
                // rampa até o terreno de fora
                double t = (d - R) / 10.0;
                int alvo = (int) Math.round(c.getY() + (h - c.getY()) * t);
                for (int y = alvo; y < Math.max(h, alvo) + 6; y++) k.s(m.set(x, y, z), ar);
                for (int y = Math.min(h, alvo) - 3; y < alvo; y++) k.s(m.set(x, y, z), y == alvo - 1 ? grama : terra);
                continue;
            }
            for (int y = c.getY(); y <= Math.max(h, c.getY()) + 12; y++) k.s(m.set(x, y, z), ar);
            for (int y = Math.min(h, c.getY()) - 4; y < c.getY() - 1; y++) k.s(m.set(x, y, z), y > c.getY() - 4 ? terra : pedra);
            BlockState piso;
            if (d <= 13) piso = Kit.b(((int) d) % 4 == 0 ? Blocks.POLISHED_ANDESITE : ((dx + dz) & 1) == 0 ? Blocks.STONE_BRICKS : Blocks.CRACKED_STONE_BRICKS);
            else if (rua(dx, dz)) {
                boolean meio = Math.abs(dz) <= 2 ? Math.abs(dz) <= 1 : Math.abs(dx) <= 2 ? Math.abs(dx) <= 1 : true;
                piso = Kit.b(meio ? Blocks.STONE_BRICKS : Blocks.POLISHED_ANDESITE);
                if (Math.abs(dz) <= 2 && Math.abs(dx) > 13 && Math.abs(dz) == 0 && Math.floorMod(dx, 6) == 0) piso = Kit.b(Blocks.CHISELED_STONE_BRICKS);
            } else if (d > R - 2) piso = Kit.b(Blocks.COARSE_DIRT);
            else piso = grama;
            k.s(m.set(x, c.getY() - 1, z), piso);
        }
    }

    // ===================== a planta =====================

    static synchronized List<Peca> pecas(BlockPos c) {
        if (pecas != null && c.equals(pecasDe)) return pecas;
        List<Peca> ps = new ArrayList<>();
        add(ps, "praca", c, 16, k -> praca(k, c));
        add(ps, "postes", c, R, k -> postes(k, c));
        add(ps, "cerca", c, R + 2, k -> cerca(k, c));
        add(ps, "prefeitura", c.offset(0, 0, -24), 14, k -> prefeitura(k, c.offset(0, 0, -24)));
        add(ps, "mercado", c.offset(0, 0, 24), 14, k -> mercado(k, c.offset(0, 0, 24)));
        add(ps, "escola", c.offset(-52, 0, -24), 12, k -> escola(k, c.offset(-52, 0, -24)));
        add(ps, "clinica", c.offset(-52, 0, 24), 12, k -> clinica(k, c.offset(-52, 0, 24)));
        add(ps, "padaria", c.offset(-24, 0, 52), 10, k -> padaria(k, c.offset(-24, 0, 52)));
        // observatório, Base Vigília e o terraço do Ritual (as mesmas construções da Vila Aurora)
        add(ps, "observatorio", c.offset(TerraSite.OBS), 14, k -> TerraSite.observatorio(k, c));
        add(ps, "base", c.offset(TerraSite.LAB), 14, k -> TerraSite.base(k, c));
        add(ps, "altar", c.offset(TerraSite.ALTAR), 8, k -> TerraSite.altar(k, c));
        add(ps, "posto", c.offset(TerraSite.OBS).offset(-10, 0, 10), 8, k -> posto(k, c.offset(TerraSite.OBS).offset(-10, 0, 10)));
        add(ps, "hortas", c.offset(46, 0, -36), 14, k -> horta(k, c.offset(46, 0, -36)));
        add(ps, "hortas2", c.offset(46, 0, 36), 14, k -> horta(k, c.offset(46, 0, 36)));
        // casas: as do anel de dentro são térreas; as de fora, sobrados
        int[][] dentro = {{-20, -20, 1}, {20, -20, 1}, {-20, 20, 0}, {20, 20, 0}, {-24, -8, 1}, {-24, 8, 0}, {24, -8, 1}, {24, 8, 0}};
        int[][] fora = {{-30, -52, 1}, {-8, -52, 1}, {14, -52, 1}, {34, -52, 1}, {0, 52, 0}, {22, 52, 0}, {44, 54, 0}, {-52, -52, 1}, {-52, 52, 0}, {-54, 0, 3}};
        int i = 0;
        for (int[] h : dentro) {
            BlockPos o = c.offset(h[0], 0, h[1]);
            Direction porta = dir(h[2]);
            int n = i++;
            add(ps, "casa" + n, o, 10, k -> { casa(k, o, porta); Mobilia.mobiliar(k, o, 6, null, o.asLong()); arvore(k, o.relative(porta.getOpposite(), 7).relative(porta.getClockWise(), 4)); });
        }
        for (int[] h : fora) {
            BlockPos o = c.offset(h[0], 0, h[1]);
            Direction porta = dir(h[2]);
            int n = i++;
            add(ps, "casa" + n, o, 12, k -> { sobrado(k, o, porta); Mobilia.mobiliar(k, o, 7, null, o.asLong()); arvore(k, o.relative(porta.getOpposite(), 8).relative(porta.getClockWise(), 5)); });
        }
        pecas = ps;
        pecasDe = c;
        return ps;
    }

    private static Direction dir(int i) {
        return switch (i) { case 0 -> Direction.NORTH; case 1 -> Direction.SOUTH; case 2 -> Direction.WEST; default -> Direction.EAST; };
    }

    private static void add(List<Peca> ps, String id, BlockPos c, int r, Consumer<Kit> o) {
        ps.add(new Peca(id, c.getX() - r, c.getZ() - r, c.getX() + r, c.getZ() + r, o));
    }

    /** As casas onde os moradores da história ficam (relativas ao centro). */
    public static BlockPos casaRel(int i) {
        int[][] t = {{-20, -20}, {20, -20}, {-20, 20}, {20, 20}, {-24, -8}};
        return new BlockPos(t[i][0], 0, t[i][1]);
    }

    // ===================== construções =====================

    static void praca(Kit k, BlockPos c) {
        // fonte larga
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 4.5) continue;
            k.s(c.offset(dx, -1, dz), Kit.b(Blocks.STONE_BRICKS));
            k.s(c.offset(dx, 0, dz), d > 3.5 ? Kit.b(Blocks.STONE_BRICK_WALL) : Kit.b(Blocks.WATER));
        }
        // Monumento da Estrela Cadente: coluna com a "estrela" (rocha escura e cristais) e um rastro curvo
        for (int y = 0; y < 6; y++) k.s(c.above(y), Kit.b(y == 5 ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS));
        BlockPos e = c.above(7);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= 2) k.s(e.offset(dx, dy, dz), Kit.b(dx == 0 && dy == 0 && dz == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.BLACKSTONE));
        k.s(e.above(2), Kit.b(Blocks.AMETHYST_CLUSTER));
        for (int i = 1; i <= 6; i++) k.s(e.offset(-i, i / 2 + 1, i), Kit.b(i < 3 ? Blocks.AMETHYST_BLOCK : Blocks.WHITE_STAINED_GLASS));
        k.s(c.above(6), Kit.b(Blocks.SEA_LANTERN));
        // bancos, canteiros com árvores e postes em volta
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12 + Math.PI / 12;
            int x = (int) Math.round(Math.cos(a) * 9), z = (int) Math.round(Math.sin(a) * 9);
            BlockPos p = c.offset(x, 0, z);
            if (Math.abs(x) <= 2 || Math.abs(z) <= 2) continue;
            if (i % 3 == 0) arvore(k, p);
            else k.s(p, Kit.degrau(Blocks.SPRUCE_STAIRS, Direction.fromYRot(Math.toDegrees(Math.atan2(-x, z)))));
        }
        for (int[] q : new int[][]{{12, 4}, {-12, 4}, {12, -4}, {-12, -4}, {4, 12}, {-4, 12}, {4, -12}, {-4, -12}}) TerraSite.poste(k, c.offset(q[0], 0, q[1]));
    }

    static void postes(Kit k, BlockPos c) {
        for (int i = 16; i <= R - 4; i += 8) for (int s : new int[]{-4, 4}) {
            TerraSite.poste(k, c.offset(i, 0, s));
            TerraSite.poste(k, c.offset(-i, 0, s));
            TerraSite.poste(k, c.offset(s, 0, i));
            TerraSite.poste(k, c.offset(s, 0, -i));
        }
    }

    static void cerca(Kit k, BlockPos c) {
        int n = (int) Math.ceil(2 * Math.PI * R * 2);
        BlockPos ult = null;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            BlockPos p = c.offset((int) Math.round(Math.cos(a) * (R - 1)), 0, (int) Math.round(Math.sin(a) * (R - 1)));
            if (p.equals(ult)) continue;
            ult = p;
            int dx = p.getX() - c.getX(), dz = p.getZ() - c.getZ();
            if (Math.abs(dx) <= 4 || Math.abs(dz) <= 4) continue;
            k.s(p, Kit.b(Blocks.SPRUCE_FENCE));
            if (i % 24 == 0) { k.s(p, Kit.b(Blocks.STRIPPED_SPRUCE_LOG)); k.s(p.above(), Kit.b(Blocks.LANTERN)); }
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos g = c.relative(d, R - 1);
            Direction lado = d.getClockWise();
            for (int s : new int[]{-4, 4}) {
                for (int y = 0; y < 5; y++) k.s(g.relative(lado, s).above(y), Kit.b(Blocks.STRIPPED_SPRUCE_LOG));
                k.s(g.relative(lado, s).above(5), Kit.b(Blocks.LANTERN));
            }
            for (int s = -4; s <= 4; s++) k.s(g.relative(lado, s).above(4), Kit.b(Blocks.SPRUCE_PLANKS));
            k.s(g.above(3), Kit.b(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
        }
    }

    static void casa(Kit k, BlockPos o, Direction porta) {
        k.predio(o, 4, 3, 1, 4, porta);
        telhado(k, o, 4, 3, 4);
        jardim(k, o, porta, 4);
    }

    static void sobrado(Kit k, BlockPos o, Direction porta) {
        boolean x = porta.getAxis() == Direction.Axis.Z;
        int hw = x ? 5 : 4, hd = x ? 4 : 5;
        k.predio(o, hw, hd, 2, 4, porta);
        // escada para o andar de cima, no canto do fundo
        Direction fundo = porta.getOpposite(), lado = porta.getClockWise();
        BlockPos ini = o.relative(fundo, (x ? hd : hw) - 2).relative(lado, (x ? hw : hd) - 4);
        k.escada(ini, lado, 4);
        telhado(k, o, hw, hd, 8);
        jardim(k, o, porta, x ? hd : hw);
    }

    /** Telhado de duas águas com escadas de madeira escura. */
    static void telhado(Kit k, BlockPos o, int hw, int hd, int altura) {
        boolean longoX = hw >= hd;
        int meio = longoX ? hd : hw;
        for (int i = 0; i <= meio + 1; i++) {
            int y = altura + 1 + i;
            for (int s = -(longoX ? hw : hd) - 1; s <= (longoX ? hw : hd) + 1; s++) {
                for (int lado : new int[]{-1, 1}) {
                    int off = (meio + 1 - i) * lado;
                    BlockPos p = longoX ? o.offset(s, y, off) : o.offset(off, y, s);
                    Direction olha = longoX ? (lado < 0 ? Direction.SOUTH : Direction.NORTH) : (lado < 0 ? Direction.EAST : Direction.WEST);
                    k.s(p, i == meio + 1 ? Kit.b(Blocks.DARK_OAK_SLAB) : Kit.degrau(Blocks.DARK_OAK_STAIRS, olha));
                }
            }
            // oitões
            for (int lado : new int[]{-1, 1}) for (int w = -(meio - i); w <= meio - i; w++) {
                BlockPos p = longoX ? o.offset(lado * hw, y - 1, w) : o.offset(w, y - 1, lado * hd);
                if (i > 0) k.s(p, Kit.b(Blocks.SPRUCE_PLANKS));
            }
        }
    }

    /** Cerquinha, flores e caminho até a porta. */
    static void jardim(Kit k, BlockPos o, Direction porta, int alcance) {
        Direction lado = porta.getClockWise();
        BlockPos frente = o.relative(porta, alcance + 1);
        for (int s = -5; s <= 5; s++) {
            BlockPos p = frente.relative(porta, 2).relative(lado, s);
            if (Math.abs(s) <= 1) continue;
            k.s(p, Kit.b(Blocks.OAK_FENCE));
            if (Math.abs(s) >= 3) k.s(frente.relative(porta, 1).relative(lado, s), Kit.b(s % 2 == 0 ? Blocks.ROSE_BUSH : Blocks.PEONY));
        }
        for (int i = 0; i <= 3; i++) k.s(frente.relative(porta, i).below(), Kit.b(Blocks.DIRT_PATH));
    }

    static void arvore(Kit k, BlockPos p) {
        if (!k.get(p).isAir() && !k.get(p).canBeReplaced()) return;
        for (int y = 0; y < 5; y++) k.s(p.above(y), Kit.b(Blocks.OAK_LOG));
        BlockState folha = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 3; dy <= 6; dy++) {
            int r = dy >= 5 ? 1 : 2;
            if (Math.abs(dx) > r || Math.abs(dz) > r || (Math.abs(dx) == 2 && Math.abs(dz) == 2)) continue;
            BlockPos q = p.offset(dx, dy, dz);
            if (k.get(q).isAir()) k.s(q, folha);
        }
    }

    static void prefeitura(Kit k, BlockPos o) {
        k.predio(o, 9, 6, 2, 5, Direction.SOUTH);
        // torre do relógio no meio
        for (int y = 10; y < 20; y++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            boolean borda = Math.abs(dx) == 2 || Math.abs(dz) == 2;
            k.s(o.offset(dx, y, dz), borda ? (y == 15 && Math.abs(dx) < 2 ? Kit.b(Blocks.WHITE_CONCRETE) : Kit.b(Blocks.STONE_BRICKS)) : Kit.b(Blocks.AIR));
        }
        k.s(o.offset(0, 15, 2), Kit.b(Blocks.BLACK_CONCRETE));
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) k.s(o.offset(dx, 20, dz), Kit.b(Blocks.DARK_OAK_SLAB));
        k.s(o.offset(0, 21, 0), Kit.b(Blocks.LIGHTNING_ROD));
        k.s(o.offset(0, 19, 0), Kit.b(Blocks.BELL));
        // salão: mesa do conselho, bandeiras e estantes
        for (int dx = -4; dx <= 4; dx++) k.mesa(o.offset(dx, 0, -2));
        for (int dx = -7; dx <= 7; dx += 2) k.s(o.offset(dx, 0, -5), Kit.b(Blocks.BOOKSHELF));
        k.s(o.offset(0, 0, -5), Kit.virado(Blocks.LECTERN, Direction.SOUTH));
        k.s(o.offset(-8, 0, 4), Kit.b(Blocks.CHEST));
        k.m.bau(o.offset(-8, 0, 4), Mobilia.tabela(null, "casa"));
        k.s(o.offset(-3, 2, 6), Blocks.WHITE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH));
        k.s(o.offset(3, 2, 6), Blocks.WHITE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH));
    }

    static void mercado(Kit k, BlockPos o) {
        // barracas com toldos coloridos
        Block[] toldos = {Blocks.RED_WOOL, Blocks.YELLOW_WOOL, Blocks.LIME_WOOL, Blocks.LIGHT_BLUE_WOOL, Blocks.ORANGE_WOOL, Blocks.MAGENTA_WOOL};
        for (int i = 0; i < 6; i++) {
            BlockPos b = o.offset(-10 + (i % 3) * 10, 0, (i / 3) * 8 - 4);
            for (int[] q : new int[][]{{-2, -1}, {2, -1}, {-2, 1}, {2, 1}}) for (int y = 0; y < 3; y++) k.s(b.offset(q[0], y, q[1]), Kit.b(Blocks.SPRUCE_FENCE));
            for (int dx = -3; dx <= 3; dx++) for (int dz = -2; dz <= 2; dz++) k.s(b.offset(dx, 3, dz), Kit.b(toldos[i]));
            for (int dx = -1; dx <= 1; dx++) k.s(b.offset(dx, 0, -1), Kit.b(Blocks.BARREL));
            k.m.bau(b.offset(0, 0, -1), Mobilia.tabela(null, "barril"));
            k.s(b.offset(-1, 1, -1), Kit.b(i % 2 == 0 ? Blocks.MELON : Blocks.PUMPKIN));
            k.s(b.offset(1, 1, -1), Kit.b(Blocks.HAY_BLOCK));
        }
    }

    static void escola(Kit k, BlockPos o) {
        k.predio(o, 7, 5, 1, 5, Direction.EAST);
        telhado(k, o, 7, 5, 5);
        for (int dx = -4; dx <= 2; dx += 3) for (int dz = -3; dz <= 3; dz += 2) {
            k.s(o.offset(dx, 0, dz), Kit.b(Blocks.SPRUCE_FENCE));
            k.s(o.offset(dx, 1, dz), Kit.b(Blocks.SPRUCE_PRESSURE_PLATE));
            k.s(o.offset(dx - 1, 0, dz), Kit.degrau(Blocks.SPRUCE_STAIRS, Direction.WEST));
        }
        for (int dz = -3; dz <= 3; dz++) k.s(o.offset(-6, 1, dz), Kit.b(Blocks.BLACK_CONCRETE));
        k.s(o.offset(-5, 0, 0), Kit.virado(Blocks.LECTERN, Direction.EAST));
        for (int dz = -4; dz <= 4; dz += 2) k.s(o.offset(5, 0, dz), Kit.b(Blocks.BOOKSHELF));
    }

    static void clinica(Kit k, BlockPos o) {
        k.predio(o, 7, 5, 1, 5, Direction.EAST);
        telhado(k, o, 7, 5, 5);
        for (int dz = -3; dz <= 3; dz += 3) k.cama(Blocks.WHITE_BED, o.offset(-5, 0, dz), Direction.WEST);
        k.s(o.offset(3, 0, -4), Kit.b(Blocks.BREWING_STAND));
        k.s(o.offset(4, 0, -4), Kit.b(Blocks.CAULDRON));
        k.s(o.offset(5, 0, -4), Kit.b(Blocks.CHEST));
        k.m.bau(o.offset(5, 0, -4), Mobilia.tabela(null, "clinica"));
        k.s(o.offset(7, 3, 0), Kit.b(Blocks.RED_CONCRETE));
        k.s(o.offset(7, 3, 1), Kit.b(Blocks.WHITE_CONCRETE));
        k.s(o.offset(7, 3, -1), Kit.b(Blocks.WHITE_CONCRETE));
    }

    static void padaria(Kit k, BlockPos o) {
        k.predio(o, 5, 4, 1, 4, Direction.NORTH);
        telhado(k, o, 5, 4, 4);
        for (int dx = -3; dx <= 3; dx += 2) k.s(o.offset(dx, 0, 3), Kit.virado(Blocks.SMOKER, Direction.NORTH));
        for (int dx = -3; dx <= 3; dx++) k.s(o.offset(dx, 0, 0), Kit.b(Blocks.SMOOTH_STONE_SLAB));
        k.s(o.offset(0, 1, 0), Kit.b(Blocks.CAKE));
        k.s(o.offset(-4, 0, 3), Kit.b(Blocks.BARREL));
        k.m.bau(o.offset(-4, 0, 3), Mobilia.tabela(null, "padaria"));
    }

    static void posto(Kit k, BlockPos o) {
        k.predio(o, 3, 3, 1, 4, Direction.WEST);
        k.s(o.offset(2, 0, -2), Kit.b(Blocks.CHEST));
        k.m.bau(o.offset(2, 0, -2), Mobilia.tabela(null, "posto"));
        k.s(o.offset(2, 0, 2), Kit.b(Blocks.CRAFTING_TABLE));
        k.s(o.offset(0, 4, 0), Kit.b(Blocks.REDSTONE_LAMP).setValue(RedstoneLampBlock.LIT, true));
    }

    static void horta(Kit k, BlockPos o) {
        for (int dx = -9; dx <= 9; dx++) for (int dz = -7; dz <= 7; dz++) {
            boolean borda = Math.abs(dx) == 9 || Math.abs(dz) == 7;
            BlockPos p = o.offset(dx, -1, dz);
            if (borda) { k.s(p.above(), Kit.b(Blocks.OAK_FENCE)); continue; }
            if (dz % 4 == 0) { k.s(p, Kit.b(Blocks.WATER)); continue; }
            k.s(p, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            Block planta = Math.abs(dx) < 4 ? Blocks.WHEAT : dx > 0 ? Blocks.CARROTS : Blocks.POTATOES;
            k.s(p.above(), planta.defaultBlockState().setValue(CropBlock.AGE, (dx * 3 + dz) & 7));
        }
        for (int s : new int[]{-1, 0, 1}) k.s(o.offset(s, 0, -7), Kit.b(Blocks.AIR));
        k.s(o.offset(-10, 0, 0), Kit.b(Blocks.COMPOSTER));
    }
}
