package com.lanterna.terra;

import com.lanterna.LanternaMod;
import com.lanterna.cidade.Kit;
import com.lanterna.cidade.Obra;
import com.lanterna.cidade.Paleta;
import com.lanterna.lore.LoreEntities;
import com.lanterna.lore.entity.Race;
import com.lanterna.lore.entity.RaceEntity;
import com.lanterna.network.ModNetwork;
import com.lanterna.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Vila Aurora e a Base Vigília: onde a história do mod começa, na própria Terra.
 *
 * Uma vila pequena, a uns 300 blocos do ponto de nascimento, com um observatório na ponta leste. Embaixo do
 * observatório fica a Base Vigília, um laboratório escondido que rastreia o que cai do céu. No terraço do
 * observatório há um altar de ensaio do Ritual da Bateria.
 *
 * Tudo é construído uma vez por mundo (na primeira vez que alguém entra), em etapas, sem travar o servidor.
 * Posições relativas ao centro da praça (chão = y do centro).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TerraSite {
    static final int R = 32;
    /** Observatório (centro da torre). */
    static BlockPos OBS = new BlockPos(22, 0, 0);
    /** Altar de ensaio, no terraço ao lado do observatório. */
    static BlockPos ALTAR = new BlockPos(22, 0, -11);
    /** Base Vigília (centro do laboratório), embaixo do observatório. */
    static final int FUNDO = -14;
    static BlockPos LAB = new BlockPos(22, FUNDO, 9);

    /** Cidade Aurora (mundos da 0.48+): o observatório fica no fim da avenida leste. */
    public static void desenho(boolean novo) {
        int x = novo ? 58 : 22;
        OBS = new BlockPos(x, 0, 0);
        ALTAR = new BlockPos(x, 0, -11);
        LAB = new BlockPos(x, FUNDO, 9);
    }

    /** Centro da vila neste mundo (servidor) ou o que o servidor mandou (cliente). Null = ainda não existe. */
    @Nullable
    public static volatile BlockPos centro;

    // ===================== Dados salvos =====================

    public static class Dados extends SavedData {
        @Nullable BlockPos c;
        boolean pronto;

        static Dados load(CompoundTag t) {
            Dados d = new Dados();
            if (t.contains("centro")) d.c = NbtUtils.readBlockPos(t.getCompound("centro"));
            d.pronto = t.getBoolean("pronto");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            if (c != null) t.put("centro", NbtUtils.writeBlockPos(c));
            t.putBoolean("pronto", pronto);
            return t;
        }

        static Dados get(MinecraftServer s) {
            return s.overworld().getDataStorage().computeIfAbsent(Dados::load, Dados::new, "lanterna_terra");
        }
    }

    public static boolean pronta(MinecraftServer s) {
        return Dados.get(s).pronto;
    }

    /** Ponto do mundo a partir de um ponto relativo à vila. */
    @Nullable
    public static BlockPos em(BlockPos rel) {
        BlockPos c = centro;
        return c == null ? null : c.offset(rel);
    }

    // ===================== Escolher o lugar =====================

    @Nullable
    static BlockPos escolher(ServerLevel l) {
        BlockPos spawn = l.getSharedSpawnPos();
        var gen = l.getChunkSource().getGenerator();
        var rs = l.getChunkSource().randomState();
        BlockPos melhor = null;
        double nota = Double.MAX_VALUE;
        for (int raio = com.lanterna.cidade.Layout.novo() ? 330 : 260; raio <= 560 && (melhor == null || nota > 6); raio += 65) {
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8 + raio * 0.01;
                int x = spawn.getX() + (int) (Math.cos(a) * raio), z = spawn.getZ() + (int) (Math.sin(a) * raio);
                if (l.getBiome(new BlockPos(x, 64, z)).is(BiomeTags.IS_OCEAN) || l.getBiome(new BlockPos(x, 64, z)).is(BiomeTags.IS_RIVER)) continue;
                int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
                boolean agua = false;
                for (int dx = -R; dx <= R; dx += 8) for (int dz = -R; dz <= R; dz += 8) {
                    int sup = gen.getBaseHeight(x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG, l, rs);
                    int fundo = gen.getBaseHeight(x + dx, z + dz, Heightmap.Types.OCEAN_FLOOR_WG, l, rs);
                    if (sup > fundo + 1) agua = true;
                    min = Math.min(min, fundo);
                    max = Math.max(max, fundo);
                }
                // água ou chão abaixo do mar ainda serve (a cidade aterra), mas vale bem menos que um campo seco
                double n = (max - min) + Math.abs(raio - 300) * 0.01 + (agua ? 25 : 0) + Math.max(0, l.getSeaLevel() - min) * 2;
                if (n < nota) {
                    nota = n;
                    melhor = new BlockPos(x, (min + max) / 2, z);
                }
            }
        }
        if (melhor == null) melhor = spawn.offset(300, 0, 0);
        return melhor;
    }

    /**
     * Mundos da 0.48: o lugar da Cidade Aurora é escolhido quando o servidor liga, antes de a região existir, e a cidade
     * nasce com o terreno ({@link AuroraGeracao}).
     */
    public static void escolherNaCriacao(MinecraftServer s) {
        Dados d = Dados.get(s);
        ServerLevel ow = s.overworld();
        // só o gerador normal põe decorações (num mundo plano a cidade é construída ao entrar, como antes)
        if (d.c == null && !(ow.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator)) return;
        if (d.c == null) {
            BlockPos esc = escolher(ow);
            int y = Math.max(com.lanterna.cidade.Mundo.alturaGerador(ow, esc.getX(), esc.getZ()), ow.getMinBuildHeight() + 24);
            d.c = new BlockPos(esc.getX(), y, esc.getZ());
            d.pronto = true;
            d.setDirty();
            LanternaMod.LOGGER.info("Lanterna: a Cidade Aurora vai nascer em {}", d.c);
        }
        centro = d.c;
    }

    // ===================== Entrar no mundo =====================

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        MinecraftServer s = p.server;
        Dados d = Dados.get(s);
        if (d.c == null) {
            ServerLevel ow = s.overworld();
            BlockPos esc = escolher(ow);
            // a Base Vigília fica 15 blocos abaixo da praça: o chão da vila nunca fica perto do fundo do mundo
            int y = Math.max(Kit.altura(ow, esc.getX(), esc.getZ()), ow.getMinBuildHeight() + 24);
            d.c = new BlockPos(esc.getX(), y, esc.getZ());
            d.setDirty();
            LanternaMod.LOGGER.info("Lanterna: Vila Aurora será construída em {}", d.c);
            Runnable fim = () -> {
                d.pronto = true;
                d.setDirty();
            };
            if (com.lanterna.cidade.Layout.novo()) AuroraGeracao.construirAgora(ow, d.c, fim);
            else construir(ow, d.c, fim);
        }
        centro = d.c;
        ModNetwork.sendTo(p, new Sync(d.c));
        TerraHistoria.aoEntrar(p);
    }

    // ===================== Construção =====================

    static Paleta paleta() {
        return new Paleta(Kit.b(Blocks.STONE_BRICKS), Kit.b(Blocks.STONE_BRICKS), Kit.b(Blocks.SPRUCE_PLANKS), Kit.b(Blocks.GLASS_PANE),
                Kit.b(Blocks.LANTERN), Kit.b(Blocks.SPRUCE_PLANKS), Kit.b(Blocks.WHITE_CONCRETE), Kit.b(Blocks.SEA_LANTERN), Kit.b(Blocks.STRIPPED_SPRUCE_LOG),
                Blocks.SPRUCE_PLANKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB,
                Blocks.WHITE_WOOL, Blocks.GRAY_CARPET, Blocks.WHITE_BED, Blocks.GLASS_PANE);
    }

    public static void construir(ServerLevel level, BlockPos c, Runnable fim) {
        Kit k = new Kit(level, paleta());
        List<ChunkPos> pedacos = new ArrayList<>();
        for (int x = -R - 8; x <= R + 8; x += 16) for (int z = -R - 8; z <= R + 8; z += 16) pedacos.add(new ChunkPos(c.offset(x, 0, z)));
        Obra.carregar(level, pedacos);
        for (int x0 = -R - 4; x0 <= R + 4; x0 += 8) {
            int f = x0;
            Obra.etapa(level, l -> terreno(k, c, f, Math.min(R + 4, f + 7)));
        }
        Obra.etapa(level, l -> ruas(k, c));
        Obra.etapa(level, l -> praca(k, c));
        int[][] casas = {{-20, -13, 0}, {-7, -13, 0}, {6, -14, 0}, {-20, 13, 1}, {-7, 13, 1}, {6, 14, 1}};
        for (int[] cs : casas) Obra.etapa(level, l -> casa(k, c.offset(cs[0], 0, cs[1]), cs[2] == 0 ? Direction.SOUTH : Direction.NORTH));
        Obra.etapa(level, l -> observatorio(k, c));
        Obra.etapa(level, l -> base(k, c));
        Obra.etapa(level, l -> altar(k, c));
        Obra.etapa(level, l -> pessoas(l, c));
        Obra.etapa(level, l -> {
            fim.run();
            LanternaMod.LOGGER.info("Lanterna: Vila Aurora pronta em {}", c);
        });
    }

    private static void terreno(Kit k, BlockPos c, int x0, int x1) {
        for (int dx = x0; dx <= x1; dx++) for (int dz = -R - 4; dz <= R + 4; dz++) {
            int ax = Math.abs(dx), az = Math.abs(dz);
            boolean borda = ax > R || az > R;
            int x = c.getX() + dx, z = c.getZ() + dz;
            int h = Kit.altura(k.L, x, z);
            if (borda) {
                // rampa suave até o terreno de fora
                int dist = Math.max(ax, az) - R;
                int alvo = (int) Math.round(c.getY() + (h - c.getY()) * (dist / 5.0));
                for (int y = alvo; y < Math.max(h, alvo) + 12; y++) k.s(x, y, z, Kit.b(Blocks.AIR));
                for (int y = Math.min(h, alvo) - 3; y < alvo; y++) k.s(x, y, z, Kit.b(y == alvo - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT));
                continue;
            }
            for (int y = c.getY(); y <= Math.max(h, c.getY()) + 20; y++) k.s(x, y, z, Kit.b(Blocks.AIR));
            for (int y = Math.min(h, c.getY()) - 4; y < c.getY(); y++) k.s(x, y, z, Kit.b(y == c.getY() - 1 ? Blocks.GRASS_BLOCK : y > c.getY() - 4 ? Blocks.DIRT : Blocks.STONE));
        }
    }

    private static void ruas(Kit k, BlockPos c) {
        for (int i = -R; i <= R; i++) for (int w = -1; w <= 1; w++) {
            k.s(c.offset(i, -1, w), Kit.b(Blocks.DIRT_PATH));
            k.s(c.offset(w, -1, i), Kit.b(Blocks.DIRT_PATH));
        }
        for (int i = -R + 2; i <= R - 2; i += 8) {
            for (int s : new int[]{-3, 3}) {
                if (Math.abs(i) < 6) continue;
                poste(k, c.offset(i, 0, s));
                poste(k, c.offset(s, 0, i));
            }
        }
    }

    static void poste(Kit k, BlockPos b) {
        k.s(b, Kit.b(Blocks.SPRUCE_FENCE));
        k.s(b.above(), Kit.b(Blocks.SPRUCE_FENCE));
        k.s(b.above(2), Kit.b(Blocks.SPRUCE_FENCE));
        k.s(b.above(3), Kit.b(Blocks.LANTERN));
    }

    private static void praca(Kit k, BlockPos c) {
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
            boolean anel = Math.abs(dx) == 5 || Math.abs(dz) == 5;
            k.s(c.offset(dx, -1, dz), Kit.b(anel ? Blocks.POLISHED_ANDESITE : ((dx + dz) % 2 == 0 ? Blocks.STONE_BRICKS : Blocks.CRACKED_STONE_BRICKS)));
        }
        // fonte
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            boolean borda = Math.abs(dx) == 2 || Math.abs(dz) == 2;
            k.s(c.offset(dx, -1, dz), Kit.b(Blocks.STONE_BRICKS));
            k.s(c.offset(dx, 0, dz), borda ? Kit.b(Blocks.STONE_BRICK_WALL) : Kit.b(Blocks.WATER));
        }
        k.s(c.offset(0, 0, 0), Kit.b(Blocks.CHISELED_STONE_BRICKS));
        k.s(c.offset(0, 1, 0), Kit.b(Blocks.CHISELED_STONE_BRICKS));
        k.s(c.offset(0, 2, 0), Kit.b(Blocks.WATER));
        for (int[] b : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
            k.s(c.offset(b[0], 0, b[1]), Kit.degrau(Blocks.SPRUCE_STAIRS, b[0] < 0 ? Direction.EAST : Direction.WEST));
        }
    }

    private static void casa(Kit k, BlockPos o, Direction porta) {
        k.predio(o, 4, 3, 1, 4, porta);
        int f = porta == Direction.SOUTH ? 1 : -1;
        k.cama(Blocks.RED_BED, o.offset(-3, 0, -1 * f), porta.getOpposite());
        k.s(o.offset(-3, 0, 1 * f), Kit.b(Blocks.CHEST));
        k.s(o.offset(3, 0, -2 * f), Kit.virado(Blocks.FURNACE, Direction.WEST));
        k.s(o.offset(2, 0, -2 * f), Kit.b(Blocks.CRAFTING_TABLE));
        k.s(o.offset(3, 0, -1 * f), Kit.b(Blocks.BARREL));
        k.s(o.offset(3, 0, 1 * f), Kit.b(Blocks.BOOKSHELF));
        k.s(o.offset(0, 0, -1 * f), Kit.b(Blocks.SPRUCE_FENCE));
        k.s(o.offset(0, 1, -1 * f), Kit.b(Blocks.SPRUCE_PRESSURE_PLATE));
        k.s(o.offset(-1, 0, 1 * f), Kit.b(Blocks.POTTED_POPPY));
        BlockPos fr = o.relative(porta, 5);
        for (int dx : new int[]{-3, 3}) {
            k.s(fr.offset(dx, 0, 0), Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        }
    }

    /** Torre redonda branca com cúpula de vidro e o telescópio apontado para o céu. */
    static void observatorio(Kit k, BlockPos c) {
        BlockPos o = c.offset(OBS);
        int raio = 6, alt = 9;
        for (int dx = -raio; dx <= raio; dx++) for (int dz = -raio; dz <= raio; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > raio + 0.5) continue;
            k.s(o.offset(dx, -1, dz), Kit.b(Blocks.POLISHED_DIORITE));
            boolean parede = d > raio - 0.5;
            for (int y = 0; y < alt; y++) {
                BlockState st;
                if (!parede) st = Kit.b(Blocks.AIR);
                else if (y == 0 || y == alt - 1) st = Kit.b(Blocks.LIGHT_GRAY_CONCRETE);
                else if (y >= 2 && y <= 4 && (Math.abs(dx) <= 1 || Math.abs(dz) <= 1)) st = Kit.b(Blocks.GLASS);
                else st = Kit.b(Blocks.WHITE_CONCRETE);
                k.s(o.offset(dx, y, dz), st);
            }
            // piso do segundo andar (com o buraco da escada)
            if (!parede && !(dx == 0 && dz == 0)) k.s(o.offset(dx, 4, dz), Kit.b(Blocks.SPRUCE_PLANKS));
        }
        // cúpula
        for (int dx = -raio; dx <= raio; dx++) for (int dz = -raio; dz <= raio; dz++) for (int dy = 0; dy <= raio; dy++) {
            double d = Math.sqrt(dx * dx + dz * dz + dy * dy);
            if (d > raio + 0.5 || d < raio - 0.6) continue;
            boolean fenda = dx >= 0 && Math.abs(dz) <= 1 && dy >= 2;
            k.s(o.offset(dx, alt + dy, dz), fenda ? Kit.b(Blocks.AIR) : Kit.b(dy % 2 == 0 ? Blocks.WHITE_CONCRETE : Blocks.LIGHT_GRAY_STAINED_GLASS));
        }
        // telescópio: um tubo inclinado saindo pela fenda
        for (int i = 0; i < 7; i++) {
            BlockPos t = o.offset(i - 1, alt + 1 + i * 2 / 3, 0);
            k.s(t, Kit.b(i == 6 ? Blocks.TINTED_GLASS : Blocks.IRON_BLOCK));
        }
        k.s(o.offset(-2, alt, 0), Kit.b(Blocks.IRON_BLOCK));
        k.s(o.offset(-2, alt - 1, 0), Kit.b(Blocks.SPRUCE_PLANKS));
        // porta (oeste, para a praça)
        for (int y = 0; y < 3; y++) for (int w = -1; w <= 1; w++) k.s(o.offset(-raio, y, w), Kit.b(Blocks.AIR));
        k.s(o.offset(-raio - 1, 2, -2), Kit.b(Blocks.LANTERN));
        k.s(o.offset(-raio - 1, 2, 2), Kit.b(Blocks.LANTERN));
        // térreo: mesas com mapas do céu, estantes, um terminal
        for (int dz = -3; dz <= 3; dz++) k.s(o.offset(4, 0, dz), Kit.b(dz % 2 == 0 ? Blocks.BOOKSHELF : Blocks.CARTOGRAPHY_TABLE));
        k.s(o.offset(2, 0, -4), Kit.virado(Blocks.LECTERN, Direction.SOUTH));
        k.s(o.offset(2, 0, 4), Kit.virado(Blocks.LECTERN, Direction.NORTH));
        k.s(o.offset(-2, 0, -4), Blocks.REDSTONE_LAMP.defaultBlockState().setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
        k.s(o.offset(-2, 1, -4), Kit.b(Blocks.BLACK_STAINED_GLASS_PANE));
        // escada de mão no centro: sobe para o andar de cima e desce para a Base Vigília (alçapão no chão)
        for (int y = FUNDO; y <= 4; y++) {
            k.s(o.offset(0, y, 1), Kit.b(Blocks.POLISHED_DEEPSLATE));
            k.s(o.offset(0, y, 0), y == -1 ? Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP).setValue(TrapDoorBlock.FACING, Direction.NORTH)
                    : Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
            if (y < -1) {
                for (int[] p : new int[][]{{1, 0}, {-1, 0}, {0, -1}}) k.s(o.offset(p[0], y, p[1]), Kit.b(Blocks.POLISHED_DEEPSLATE));
            }
        }
        k.s(o.offset(0, -1, 0), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
    }

    /** Base Vigília: laboratório escondido embaixo do observatório. */
    static void base(Kit k, BlockPos c) {
        BlockPos o = c.offset(OBS);
        int y0 = FUNDO;
        // corredor da escada até o laboratório (para o sul)
        caixa(k, o.offset(-2, y0 - 1, -1), o.offset(2, y0 + 3, 4), Blocks.POLISHED_DEEPSLATE);
        vazio(k, o.offset(-1, y0, -0), o.offset(1, y0 + 2, 3));
        k.s(o.offset(0, y0, 0), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        // laboratório
        BlockPos lab = c.offset(LAB);
        caixa(k, lab.offset(-9, -1, -5), lab.offset(9, 4, 6), Blocks.POLISHED_DEEPSLATE);
        vazio(k, lab.offset(-8, 0, -4), lab.offset(8, 3, 5));
        for (int dx = -8; dx <= 8; dx++) for (int dz = -4; dz <= 5; dz++) {
            k.s(lab.offset(dx, -1, dz), Kit.b((dx + dz) % 2 == 0 ? Blocks.GRAY_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE));
            if (dx % 4 == 0 && dz % 3 == 0) k.s(lab.offset(dx, 4, dz), Kit.b(Blocks.SEA_LANTERN));
        }
        vazio(k, lab.offset(-1, 0, -6), lab.offset(1, 2, -5));
        // painéis: parede leste com telas
        for (int dz = -3; dz <= 4; dz++) {
            k.s(lab.offset(8, 0, dz), Kit.b(Blocks.POLISHED_BLACKSTONE));
            k.s(lab.offset(8, 1, dz), Kit.b(dz % 2 == 0 ? Blocks.BLACK_STAINED_GLASS : Blocks.LIGHT_BLUE_STAINED_GLASS));
            k.s(lab.offset(8, 2, dz), Kit.b(Blocks.BLACK_STAINED_GLASS));
            k.s(lab.offset(7, 0, dz), Kit.degrau(Blocks.POLISHED_BLACKSTONE_STAIRS, Direction.EAST));
        }
        // bancadas no meio
        for (int dx = -4; dx <= 2; dx += 3) {
            k.s(lab.offset(dx, 0, 0), Kit.b(Blocks.WHITE_CONCRETE));
            k.s(lab.offset(dx, 0, 1), Kit.b(Blocks.WHITE_CONCRETE));
            k.s(lab.offset(dx, 1, 0), Kit.b(Blocks.BREWING_STAND));
            k.s(lab.offset(dx, 1, 1), Kit.b(Blocks.AMETHYST_CLUSTER));
        }
        // a amostra: uma cápsula de vidro com um pedaço de estrela (só enfeite)
        BlockPos cap = lab.offset(-6, 0, 3);
        for (int y = 0; y < 3; y++) for (int[] p : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) k.s(cap.offset(p[0], y, p[1]), Kit.b(Blocks.GLASS));
        k.s(cap.below(), Kit.b(Blocks.SEA_LANTERN));
        k.s(cap, Kit.b(Blocks.CRYING_OBSIDIAN));
        k.s(cap.above(), Kit.b(Blocks.AIR));
        k.s(cap.above(2), Kit.b(Blocks.IRON_BLOCK));
        // dormitório e arquivo
        for (int dz = -3; dz <= 3; dz += 2) k.s(lab.offset(-8, 0, dz), Kit.b(Blocks.BOOKSHELF));
        for (int dz = -3; dz <= 3; dz += 2) k.s(lab.offset(-8, 1, dz), Kit.b(Blocks.BOOKSHELF));
        k.cama(Blocks.LIGHT_GRAY_BED, lab.offset(-5, 0, -4), Direction.SOUTH);
        k.cama(Blocks.LIGHT_GRAY_BED, lab.offset(-3, 0, -4), Direction.SOUTH);
        k.s(lab.offset(4, 0, 5), Kit.b(Blocks.CHEST));
        k.s(lab.offset(5, 0, 5), Kit.b(Blocks.CRAFTING_TABLE));
    }

    /** Altar de ensaio do Ritual da Bateria (terraço a céu aberto ao lado do observatório). */
    static void altar(Kit k, BlockPos c) {
        BlockPos a = c.offset(ALTAR);
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            boolean borda = Math.abs(dx) == 4 || Math.abs(dz) == 4;
            k.s(a.offset(dx, -1, dz), Kit.b(borda ? Blocks.POLISHED_DIORITE : Blocks.SMOOTH_STONE));
            k.s(a.offset(dx, 0, dz), borda && !(dx == 0 && dz == 4) ? Kit.b(Blocks.DIORITE_WALL) : Kit.b(Blocks.AIR));
        }
        k.s(a.below(), Kit.b(Blocks.AMETHYST_BLOCK));
        for (BlockPos v : RitualBateria.cantos(a)) {
            k.s(v.below(), Kit.b(Blocks.CHISELED_STONE_BRICKS));
            k.s(v, Blocks.WHITE_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
        }
    }

    private static void caixa(Kit k, BlockPos a, BlockPos b, Block bl) {
        k.box(a, b, Kit.b(bl));
    }

    private static void vazio(Kit k, BlockPos a, BlockPos b) {
        k.ar(a, b);
    }

    // ===================== Moradores =====================

    /** titulo: "dra", "sgt", "dona", "seu" ou "" (traduzido: "Dra." vira "Dr.", "Dona" vira "Ms." etc.). */
    private record Posto(String papel, BlockPos rel, String titulo, String nome) {
        String antigo() {
            return switch (titulo) {
                case "dra" -> "Dra. " + nome;
                case "sgt" -> "Sgt. " + nome;
                case "dona" -> "Dona " + nome;
                case "seu" -> "Seu " + nome;
                default -> nome;
            };
        }

        Component exibido() {
            return titulo.isEmpty() ? Component.literal(nome) : Component.translatable("terra.lanterna.titulo." + titulo, nome);
        }
    }

    private static final String TAG_POSTO = "lanterna_posto";

    private static List<Posto> postos() {
        if (com.lanterna.cidade.Layout.novo()) return List.of(
                new Posto("cientista", LAB.offset(2, 0, -2), "dra", "Lívia Andrade"),
                new Posto("assistente", LAB.offset(-4, 0, 2), "", "Téo Brandão"),
                new Posto("seguranca", OBS.offset(-8, 0, 2), "sgt", "Rui Tavares"),
                new Posto("morador", new BlockPos(-20, 0, -13), "dona", "Cida"),
                new Posto("morador", new BlockPos(20, 0, -13), "seu", "Jonas"),
                new Posto("morador", new BlockPos(20, 0, 13), "", "Marina"),
                new Posto("morador", new BlockPos(-20, 0, 13), "", "Caio"),
                new Posto("morador", new BlockPos(-6, 0, 6), "", "Bia"),
                new Posto("morador", new BlockPos(0, 0, 18), "", "Rafa"),
                new Posto("morador", new BlockPos(-46, 0, -18), "", "Prof. Helena"),
                new Posto("morador", new BlockPos(-46, 0, 18), "", "Enf. Davi"),
                new Posto("morador", new BlockPos(-24, 0, 45), "", "Seu Tonho"),
                new Posto("morador", new BlockPos(0, 0, -16), "", "Prefeita Lúcia"));
        return POSTOS;
    }

    private static final List<Posto> POSTOS = List.of(
            new Posto("cientista", LAB.offset(2, 0, -2), "dra", "Lívia Andrade"),
            new Posto("assistente", LAB.offset(-4, 0, 2), "", "Téo Brandão"),
            new Posto("seguranca", OBS.offset(-8, 0, 2), "sgt", "Rui Tavares"),
            new Posto("morador", new BlockPos(-20, 0, -7), "dona", "Cida"),
            new Posto("morador", new BlockPos(-7, 0, -7), "seu", "Jonas"),
            new Posto("morador", new BlockPos(6, 0, 8), "", "Marina"),
            new Posto("morador", new BlockPos(-20, 0, 7), "", "Caio"),
            new Posto("morador", new BlockPos(-4, 0, 4), "", "Bia"));

    static void pessoas(ServerLevel l, BlockPos c) {
        for (Posto p : postos()) garantir(l, c, p);
    }

    private static void garantir(ServerLevel l, BlockPos c, Posto p) {
        BlockPos at = c.offset(p.rel());
        if (!l.hasChunkAt(at)) return;
        var lista = l.getEntitiesOfClass(RaceEntity.class, new AABB(at).inflate(48),
                e -> e.race() == Race.TERRANO && e.role().equals(p.papel()) && (p.nome().equals(e.getPersistentData().getString(TAG_POSTO))
                        || e.hasCustomName() && e.getCustomName().getString().equals(p.antigo())));
        if (!lista.isEmpty()) {
            // mundos antigos: o nome era salvo em português ("Dra. ..."); passa a ser traduzido
            for (RaceEntity v : lista) if (!p.nome().equals(v.getPersistentData().getString(TAG_POSTO))) {
                v.getPersistentData().putString(TAG_POSTO, p.nome());
                v.setCustomName(p.exibido());
            }
            return;
        }
        RaceEntity e = LoreEntities.typeOf(Race.TERRANO).create(l);
        if (e == null) return;
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, l.getRandom().nextFloat() * 360, 0);
        e.finalizeSpawn(l, l.getCurrentDifficultyAt(at), MobSpawnType.STRUCTURE, null, null);
        e.setRole(p.papel(), at);
        e.setCustomName(p.exibido());
        e.getPersistentData().putString(TAG_POSTO, p.nome());
        e.setCustomNameVisible(false);
        l.addFreshEntity(e);
    }

    /** De vez em quando, se alguém está por perto: quem sumiu (morreu) volta. */
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
        ServerLevel ow = s.overworld();
        if (ow.getGameTime() % 1200 != 37) return;
        Dados d = Dados.get(s);
        if (!d.pronto || d.c == null) return;
        boolean perto = false;
        for (ServerPlayer p : s.getPlayerList().getPlayers())
            if (p.level() == ow && p.blockPosition().distSqr(d.c) < 80 * 80) perto = true;
        if (perto) pessoas(ow, d.c);
    }

    // ===================== Pacote =====================

    public record Sync(@Nullable BlockPos c) {
        public static void encode(Sync m, FriendlyByteBuf b) {
            b.writeBoolean(m.c != null);
            if (m.c != null) b.writeBlockPos(m.c);
        }

        public static Sync decode(FriendlyByteBuf b) {
            return new Sync(b.readBoolean() ? b.readBlockPos() : null);
        }

        public static void handle(Sync m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> centro = m.c));
            ctx.get().setPacketHandled(true);
        }
    }

    private TerraSite() {}
}
