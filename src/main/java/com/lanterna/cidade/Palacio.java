package com.lanterna.cidade;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.lore.LoreBlocks;
import com.lanterna.lore.LoreEntities;
import com.lanterna.lore.LoreItems;
import com.lanterna.lore.PlanetLore;
import com.lanterna.lore.entity.Race;
import com.lanterna.lore.entity.RaceEntity;
import com.lanterna.lore.world.CidadeTropa;
import com.lanterna.mission.LoreCapability;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import com.lanterna.naves.bloco.NaveBlocks;
import com.lanterna.power.RingPowers;
import com.lanterna.space.SpaceBodies;
import com.lanterna.tropa.Nivel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

import static com.lanterna.cidade.Kit.*;

/**
 * Palácio dos Guardiões (Oa): ilha flutuante acima das nuvens, 320 blocos ao norte da cidade, ligada a ela por uma
 * ponte de luz. Só entra quem foi chamado (nível 30 / missão "O Chamado de Oa").
 *
 *   Portão (sul) com as 7 cores das emoções, só o verde aceso  ·  Pátio das Estátuas com fontes
 *   Grande Salão redondo com os tronos do Conselho  ·  Câmara da Bateria Primordial (recarrega o anel)
 *   6 alas dos Capitães (3 a oeste, 3 a leste)  ·  Biblioteca do Livro de Oa  ·  Observatório  ·  Celas de Contenção
 *   Campo de Provas: ilha menor a oeste, onde acontecem as provações  ·  anéis de voo em volta (provação do Salto)
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Palacio {
    public static final int Y = 170;
    public static final BlockPos CENTRO = new BlockPos(0, Y, -470);
    public static final BlockPos SALAO = CENTRO.offset(0, 0, -6);
    public static final BlockPos BIBLIOTECA = CENTRO.offset(-24, 0, -34);
    public static final BlockPos CAMARA = CENTRO.offset(0, 0, -34);
    public static final BlockPos CAMPO = CENTRO.offset(-84, 0, 0);
    public static final BlockPos PORTAO = CENTRO.offset(0, 0, 54);
    public static final int R = 56;
    public static final List<BlockPos> ANEIS = List.of(CENTRO.offset(40, 22, 60), CENTRO.offset(75, 32, 0), CENTRO.offset(40, 44, -70),
            CENTRO.offset(-30, 36, -80), CENTRO.offset(-60, 26, 55));

    /** Sala de cada Capitão (técnica -> centro da ala). */
    public static BlockPos ala(String tec) {
        return switch (tec) {
            case "olhar" -> CENTRO.offset(-32, 0, -14);
            case "bastiao" -> CENTRO.offset(-32, 0, 4);
            case "onda" -> CENTRO.offset(-32, 0, 22);
            case "salto" -> CENTRO.offset(32, 0, -14);
            case "armadura" -> CENTRO.offset(32, 0, 4);
            default -> CENTRO.offset(32, 0, 22);
        };
    }

    // ===================== Construção =====================

    public static void construir(ServerLevel level) {
        Paleta pal = new Paleta(b(LoreBlocks.ROCHA_POLIDA_VERIDIA.get()), b(Blocks.QUARTZ_BRICKS), b(Blocks.LIME_CONCRETE), b(Blocks.LIME_STAINED_GLASS),
                b(Blocks.SEA_LANTERN), b(Blocks.WHITE_CONCRETE), b(Blocks.EMERALD_BLOCK), b(Blocks.VERDANT_FROGLIGHT), b(Blocks.QUARTZ_PILLAR),
                Blocks.BIRCH_PLANKS, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ_SLAB, Blocks.LIME_WOOL, Blocks.LIME_CARPET, Blocks.LIME_BED,
                Blocks.LIME_STAINED_GLASS_PANE);
        Kit k = new Kit(level, pal);
        for (int x0 = -R; x0 <= R; x0 += 8) { int f = x0; Obra.etapa(level, l -> ilha(k, CENTRO, R, f, Math.min(R, f + 7), 34)); }
        for (int x0 = -22; x0 <= 22; x0 += 8) { int f = x0; Obra.etapa(level, l -> ilha(k, CAMPO, 22, f, Math.min(22, f + 7), 18)); }
        Obra.etapa(level, l -> portaoEPatio(k));
        Obra.etapa(level, l -> salao(k));
        Obra.etapa(level, l -> camara(k));
        for (String t : CidadeMissoes.TECNICAS) Obra.etapa(level, l -> alaCapitao(k, t));
        Obra.etapa(level, l -> biblioteca(k));
        Obra.etapa(level, l -> observatorio(k));
        Obra.etapa(level, l -> celas(k));
        Obra.etapa(level, l -> campo(k));
        Obra.etapa(level, l -> aneis(k));
        for (int i = 0; i < 6; i++) { int f = i; Obra.etapa(level, l -> ponte(k, f)); }
        Obra.etapa(level, l -> moradores(l));
        Obra.etapa(level, l -> LanternaMod.LOGGER.info("Lanterna: Palácio dos Guardiões pronto"));
    }

    /** Ilha flutuante: chão redondo e a parte de baixo afinando como uma pedra pendurada (faixa de x0 a x1). */
    private static void ilha(Kit k, BlockPos c, int raio, int x0, int x1, int fundo) {
        BlockState rocha = b(LoreBlocks.ROCHA_POLIDA_VERIDIA.get()), pedra = b(Blocks.STONE), musgo = b(Blocks.MOSS_BLOCK);
        for (int dx = x0; dx <= x1; dx++) for (int dz = -raio; dz <= raio; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > raio + 0.5) continue;
            int x = c.getX() + dx, z = c.getZ() + dz;
            k.s(x, c.getY() - 1, z, d > raio - 1 ? k.p.faixa() : ((Math.floorMod(dx, 6) == 0 || Math.floorMod(dz, 6) == 0) ? k.p.parede() : rocha));
            double frac = d / raio;
            int prof = (int) (fundo * (1 - frac * frac)) + 2 + (int) (Math.abs(Math.sin(dx * 0.7) * Math.cos(dz * 0.5)) * 6);
            for (int y = 2; y <= prof; y++) k.s(x, c.getY() - y, z, y < 4 ? rocha : (y % 7 == 0 ? musgo : pedra));
            for (int y = 0; y < 50; y++) {
                BlockPos up = new BlockPos(x, c.getY() + y, z);
                if (!k.L.getBlockState(up).isAir()) k.s(up, b(Blocks.AIR));
            }
            if (d > raio - 1 && d <= raio + 0.5 && ((dx + dz) & 1) == 0) k.s(x, c.getY(), z, k.p.painel().defaultBlockState());
        }
    }

    private static void portaoEPatio(Kit k) {
        BlockPos g = PORTAO;
        for (int s : new int[]{-7, 7}) for (int h = 0; h < 17; h++) {
            k.s(g.offset(s, h, 0), h == 16 ? k.p.destaque() : k.p.pilar());
            k.s(g.offset(s + (s < 0 ? -1 : 1), h, 0), h % 4 == 3 ? k.p.faixa() : k.p.parede());
        }
        for (int dx = -8; dx <= 8; dx++) { k.s(g.offset(dx, 14, 0), k.p.faixa()); k.s(g.offset(dx, 16, 0), k.p.parede()); }
        // as 7 emoções: só o verde aceso
        Block[] cores = {Blocks.RED_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.VERDANT_FROGLIGHT,
                Blocks.BLUE_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.MAGENTA_CONCRETE};
        for (int i = 0; i < 7; i++) k.s(g.offset(-6 + i * 2, 15, 0), b(cores[i]));
        for (int i = 0; i < 6; i++) k.s(g.offset(-5 + i * 2, 15, 0), k.p.parede());
        // caminho do portão ao salão
        for (int dz = 10; dz <= 54; dz++) for (int dx = -3; dx <= 3; dx++)
            k.s(CENTRO.offset(dx, -1, dz), Math.abs(dx) == 3 ? k.p.faixa() : (dz % 4 == 0 && dx == 0 ? k.p.luz() : k.p.parede()));
        // estátuas dos Lanternas lendários
        for (int i = 0; i < 6; i++) {
            int x = (i % 2 == 0) ? -12 : 12;
            int z = 18 + (i / 2) * 11;
            BlockPos s = CENTRO.offset(x, 0, z);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) k.s(s.offset(dx, 0, dz), k.p.faixa());
            for (int h = 1; h <= 6; h++) k.s(s.above(h), h == 6 ? b(Blocks.PLAYER_HEAD) : (h >= 4 ? k.p.pilar() : k.p.parede()));
            k.s(s.offset(x < 0 ? 1 : -1, 4, 0), k.p.destaque());
            k.s(s.offset(x < 0 ? 1 : -1, 5, 0), k.p.nucleo());
        }
        // fontes
        for (int x : new int[]{-22, 22}) {
            BlockPos f = CENTRO.offset(x, 0, 30);
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 3.4) continue;
                k.s(f.offset(dx, -1, dz), d > 2.4 ? k.p.faixa() : k.p.luz());
                k.s(f.offset(dx, 0, dz), d > 2.4 ? lajeAlta(k.p.laje()) : b(Blocks.WATER));
            }
            for (int h = 0; h < 4; h++) k.s(f.above(h), h == 3 ? k.p.nucleo() : k.p.pilar());
        }
    }

    /** Grande Salão: redondo, colunas, tronos do Conselho ao norte, disco do julgamento no centro, cúpula de vidro. */
    private static void salao(Kit k) {
        BlockPos c = SALAO;
        int r = 16, h = 18;
        for (int dx = -r - 1; dx <= r + 1; dx++) for (int dz = -r - 1; dz <= r + 1; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > r + 0.5) continue;
            k.s(c.offset(dx, -1, dz), d < 3.5 ? (d < 1.5 ? k.p.nucleo() : k.p.destaque()) : (Math.floorMod((int) d, 3) == 0 ? k.p.faixa() : k.p.chao()));
            if (d > r - 0.5) for (int y = 0; y < h; y++) {
                double ang = Math.atan2(dz, dx);
                boolean janela = y >= 5 && y <= 12 && (Math.floorMod((int) Math.round(Math.toDegrees(ang)), 30) < 12);
                k.s(c.offset(dx, y, dz), y % 6 == 5 ? k.p.faixa() : janela ? k.p.vidro() : k.p.parede());
            }
        }
        // cúpula
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = 0; dy <= r; dy++) {
            double d = Math.sqrt(dx * dx + dz * dz + dy * dy);
            if (d > r + 0.5 || d < r - 0.5) continue;
            boolean nervura = dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz);
            k.s(c.offset(dx, h + dy, dz), nervura ? k.p.faixa() : k.p.vidro());
        }
        k.s(c.above(h + r + 1), k.p.nucleo());
        // colunas
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            BlockPos col = c.offset((int) Math.round(Math.cos(a) * 12), 0, (int) Math.round(Math.sin(a) * 12));
            if (col.getZ() - c.getZ() > 8 && Math.abs(col.getX() - c.getX()) < 6) continue;
            for (int y = 0; y < h; y++) k.s(col.above(y), y == h - 1 ? k.p.luz() : k.p.pilar());
        }
        // tronos (norte = -z)
        int[] angs = {-60, -30, 0, 30, 60};
        for (int a : angs) {
            double rad = Math.toRadians(a);
            BlockPos t = c.offset((int) Math.round(Math.sin(rad) * 10), 0, (int) Math.round(-Math.cos(rad) * 10));
            for (int y = 0; y < 3; y++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) k.s(t.offset(dx, y, dz), y == 2 ? k.p.faixa() : k.p.parede());
            k.s(t.above(3), degrau(Blocks.QUARTZ_STAIRS, Direction.SOUTH));
            for (int y = 3; y < 8; y++) k.s(t.offset(0, y, -1), y == 7 ? k.p.nucleo() : k.p.pilar());
            k.s(t.offset(-1, 3, -1), k.p.destaque());
            k.s(t.offset(1, 3, -1), k.p.destaque());
            k.s(t.offset(0, 2, 2), degrau(Blocks.QUARTZ_STAIRS, Direction.NORTH));
        }
        // portas: sul (pátio) e norte (câmara)
        for (int dx = -2; dx <= 2; dx++) for (int y = 0; y < 8; y++) { k.s(c.offset(dx, y, r), b(Blocks.AIR)); k.s(c.offset(dx, y, -r), b(Blocks.AIR)); }
        // estandartes
        for (int dx : new int[]{-5, 5}) k.estandarte(c.offset(dx, 0, 14), 7);
    }

    /** Câmara da Bateria Primordial: cúpula com a maior bateria do jogo sobre um poço de luz. */
    private static void camara(Kit k) {
        BlockPos c = CAMARA;
        int r = 11;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = 0; dy <= r + 6; dy++) {
            double dh = Math.sqrt(dx * dx + dz * dz);
            if (dh > r + 0.5) continue;
            if (dy == 0) k.s(c.offset(dx, -1, dz), dh > 8 ? k.p.faixa() : (dh > 6 ? k.p.luz() : k.p.chao()));
            double d = Math.sqrt(dx * dx + dz * dz + Math.max(0, dy - 6) * Math.max(0, dy - 6));
            if (d > r - 0.5 && d <= r + 0.5) k.s(c.offset(dx, dy, dz), dy < 6 ? (dy % 3 == 2 ? k.p.faixa() : k.p.parede()) : k.p.vidro());
        }
        for (int dx = -2; dx <= 2; dx++) for (int y = 0; y < 7; y++) k.s(c.offset(dx, y, r), b(Blocks.AIR));
        CidadeTropa.bateriaCentral(k.L, c, PlanetLore.OA);
    }

    private static void alaCapitao(Kit k, String tec) {
        BlockPos a = ala(tec);
        boolean oeste = a.getX() < CENTRO.getX();
        k.predio(a, 6, 6, 1, 7, oeste ? Direction.EAST : Direction.WEST);
        int lado = oeste ? -1 : 1; // parede do fundo
        switch (tec) {
            case "olhar" -> {
                for (int dz = -4; dz <= 4; dz++) for (int h = 0; h < 3; h++) k.s(a.offset(lado * 5, h, dz), h == 1 && dz % 2 == 0 ? b(Blocks.CHISELED_BOOKSHELF) : b(Blocks.BOOKSHELF));
                k.s(a.offset(0, 0, 0), virado(Blocks.LECTERN, oeste ? Direction.EAST : Direction.WEST));
                for (int dx = -2; dx <= 2; dx++) k.s(a.offset(dx, 6, 0), k.p.vidro());
                k.s(a.offset(lado * 3, 0, -4), b(Blocks.AMETHYST_CLUSTER));
            }
            case "bastiao" -> {
                for (int dz = -4; dz <= 4; dz++) for (int h = 0; h < 4; h++) k.s(a.offset(lado * 4, h, dz), (dz + h) % 2 == 0 ? k.p.vidro() : b(Blocks.IRON_BLOCK));
                for (int dz : new int[]{-3, 3}) k.s(a.offset(0, 0, dz), b(Blocks.IRON_BLOCK));
            }
            case "onda" -> {
                for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) k.s(a.offset(dx, -1, dz), (dx + dz) % 2 == 0 ? k.p.faixa() : b(Blocks.GREEN_WOOL));
                for (int dz = -4; dz <= 4; dz += 2) k.s(a.offset(lado * 5, 1, dz), b(Blocks.TARGET));
                k.s(a.offset(lado * 3, 0, 4), b(Blocks.ANVIL));
            }
            case "salto" -> {
                for (int i = 0; i < 20; i++) {
                    double an = Math.PI * 2 * i / 20;
                    k.s(a.offset(0, 3 + (int) Math.round(Math.sin(an) * 2.5), (int) Math.round(Math.cos(an) * 2.5)), i % 5 == 0 ? k.p.luz() : k.p.vidro());
                }
                k.s(a.offset(lado * 4, 0, 0), b(NaveBlocks.NAVE_HOLOMAPA.get()));
            }
            case "armadura" -> {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockState st = switch (Math.floorMod(dz, 4)) {
                        case 0 -> b(Blocks.SMITHING_TABLE);
                        case 1 -> b(Blocks.BLAST_FURNACE);
                        case 2 -> b(Blocks.ANVIL);
                        default -> b(Blocks.IRON_BLOCK);
                    };
                    k.s(a.offset(lado * 5, 0, dz), st);
                }
                k.s(a.offset(lado * 5, 1, 0), b(Blocks.LAVA_CAULDRON));
            }
            default -> {
                for (int dx = -2; dx <= 2; dx++) for (int dz = -1; dz <= 1; dz++) k.s(a.offset(dx, 0, dz), b(Blocks.CARTOGRAPHY_TABLE));
                Block[] las = {Blocks.LIME_WOOL, Blocks.YELLOW_WOOL, Blocks.RED_WOOL, Blocks.LIGHT_BLUE_WOOL, Blocks.ORANGE_WOOL, Blocks.PURPLE_WOOL, Blocks.MAGENTA_WOOL, Blocks.BLACK_WOOL};
                for (int i = 0; i < 8; i++) for (int h = 1; h <= 3; h++) k.s(a.offset(lado * 5, h, -4 + i), b(las[i]));
            }
        }
        k.estandarte(a.offset(-lado * 4, 0, -5), 4);
        k.estandarte(a.offset(-lado * 4, 0, 5), 4);
    }

    private static void biblioteca(Kit k) {
        BlockPos b0 = BIBLIOTECA;
        k.predio(b0, 9, 7, 1, 9, Direction.SOUTH);
        for (int ring = 3; ring <= 7; ring += 2) {
            for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring + 1; dz <= ring - 1; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring - 1 || Math.abs(dx) > 8 || Math.abs(dz) > 6) continue;
                if (dz == ring - 1 && Math.abs(dx) <= 1) continue; // passagem
                for (int h = 0; h < ring - 1; h++) k.s(b0.offset(dx, h, dz), b(Blocks.BOOKSHELF));
            }
        }
        k.s(b0, k.p.destaque());
        k.s(b0.above(), virado(Blocks.LECTERN, Direction.SOUTH));
        k.s(b0.above(5), k.p.nucleo());
        bau(k, b0.offset(-8, 0, -6), 1);
    }

    private static void observatorio(Kit k) {
        BlockPos o = CENTRO.offset(24, 0, -34);
        for (int y = 0; y < 30; y++) for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 5.4) continue;
            if (y == 0) k.s(o.offset(dx, -1, dz), k.p.chao());
            if (d > 4.4) k.s(o.offset(dx, y, dz), y >= 24 ? k.p.vidro() : (y % 6 == 5 ? k.p.faixa() : k.p.parede()));
        }
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) if (dx * dx + dz * dz <= 29) k.s(o.offset(dx, 30, dz), k.p.teto());
        for (int dx = -1; dx <= 1; dx++) for (int y = 0; y < 4; y++) k.s(o.offset(dx, y, 5), b(Blocks.AIR));
        k.s(o, b(NaveBlocks.NAVE_HOLOMAPA.get()));
        for (int y = 1; y < 22; y++) k.s(o.offset(0, y, -3), y % 5 == 0 ? k.p.luz() : b(Blocks.CHAIN));
        bau(k, o.offset(3, 0, -3), 2);
    }

    private static void celas(Kit k) {
        BlockPos c = CENTRO.offset(30, 0, 36);
        k.predio(c, 7, 5, 1, 6, Direction.WEST);
        for (int i = -1; i <= 1; i++) {
            int z = i * 3;
            for (int h = 0; h < 4; h++) { k.s(c.offset(2, h, z), h < 3 ? b(Blocks.IRON_BARS) : k.p.painel().defaultBlockState());
                k.s(c.offset(3, h, z - 1), k.p.parede()); k.s(c.offset(3, h, z + 1), k.p.parede()); }
        }
        bau(k, c.offset(-5, 0, 4), 3);
    }

    /** Baú escondido com uma relíquia (provação do Olhar do Anel). */
    private static void bau(Kit k, BlockPos p, int n) {
        k.s(p, b(Blocks.CHEST));
        if (k.L.getBlockEntity(p) instanceof ChestBlockEntity be) {
            be.setItem(13, new ItemStack(LoreItems.RELIQUIA.get()));
            be.setItem(4, new ItemStack(LoreItems.MERITO.get(), n));
        }
    }

    private static void campo(Kit k) {
        BlockPos c = CAMPO;
        for (int dx = -20; dx <= 20; dx++) for (int dz = -20; dz <= 20; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 20.5) continue;
            k.s(c.offset(dx, -1, dz), d > 19 ? k.p.faixa() : (Math.floorMod((int) d, 4) == 0 ? k.p.destaque() : k.p.chao()));
            if (d > 19 && d <= 20.5) { k.s(c.offset(dx, 0, dz), k.p.parede()); k.s(c.offset(dx, 1, dz), ((dx + dz) & 3) == 0 ? k.p.luz() : k.p.painel().defaultBlockState()); }
        }
        for (int dz = -2; dz <= 2; dz++) for (int y = 0; y < 3; y++) k.s(c.offset(20, y, dz), b(Blocks.AIR));
        // ponte curta até o palácio
        for (int x = c.getX() + 20; x <= CENTRO.getX() - R + 2; x++) for (int dz = -2; dz <= 2; dz++)
            k.s(x, Y - 1, c.getZ() + dz, Math.abs(dz) == 2 ? k.p.faixa() : k.p.vidro());
        for (int x = c.getX() + 20; x <= CENTRO.getX() - R + 2; x += 5) for (int dz : new int[]{-2, 2}) k.s(x, Y, c.getZ() + dz, k.p.luz());
    }

    private static void aneis(Kit k) {
        for (BlockPos r : ANEIS) {
            for (int i = 0; i < 32; i++) {
                double a = Math.PI * 2 * i / 32;
                k.s(r.offset((int) Math.round(Math.cos(a) * 5), (int) Math.round(Math.sin(a) * 5), 0), i % 4 == 0 ? k.p.nucleo() : k.p.vidro());
            }
        }
    }

    /** Ponte de luz: do portão norte da cidade de Oa até o portão do palácio (feita em 6 pedaços). */
    private static void ponte(Kit k, int parte) {
        int zIni = Cidade.CENTRO.getZ() - Cidade.R - 1, zFim = PORTAO.getZ() + 2;
        int yIni = altura(k.L, 0, zIni + 2);
        int total = zIni - zFim;
        int passo = (total + 5) / 6;
        for (int i = parte * passo; i <= Math.min(total, (parte + 1) * passo); i++) {
            int z = zIni - i;
            double t = (double) i / total;
            int y = (int) Math.round(yIni + (Y - yIni) * t) - 1;
            for (int dx = -2; dx <= 2; dx++) {
                k.s(dx, y, z, Math.abs(dx) == 2 ? k.p.faixa() : (i % 6 == 0 && dx == 0 ? k.p.luz() : k.p.vidro()));
                for (int h = 1; h <= 3; h++) if (Math.abs(dx) < 2) k.s(dx, y + h, z, b(Blocks.AIR));
            }
            if (i % 8 == 0) for (int s : new int[]{-2, 2}) { k.s(s, y + 1, z, k.p.painel().defaultBlockState()); k.s(s, y + 2, z, k.p.nucleo()); }
            else for (int s : new int[]{-2, 2}) k.s(s, y + 1, z, k.p.painel().defaultBlockState());
        }
    }

    // ===================== Moradores =====================

    private static RaceEntity guardiao(ServerLevel l, BlockPos at, String role) {
        RaceEntity e = LoreEntities.typeOf(Race.GUARDIAO).create(l);
        if (e == null) return null;
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        e.finalizeSpawn(l, l.getCurrentDifficultyAt(at), MobSpawnType.STRUCTURE, null, null);
        e.setRole(role, at);
        e.setPersistenceRequired();
        e.setCustomName(Component.translatable("cidade.lanterna.npc." + role));
        e.setCustomNameVisible(true);
        l.addFreshEntity(e);
        return e;
    }

    private static void moradores(ServerLevel l) {
        // Conselho: quatro tronos ocupados, o quinto (de Selun) fica vazio: ele está na biblioteca
        int[] angs = {-60, -30, 0, 30};
        String[] quem = {"conselho_dorvek", "conselho_miraeth", "conselho_varo", "conselho_ilyss"};
        for (int i = 0; i < 4; i++) {
            double rad = Math.toRadians(angs[i]);
            guardiao(l, SALAO.offset((int) Math.round(Math.sin(rad) * 10), 4, (int) Math.round(-Math.cos(rad) * 10)), quem[i]);
        }
        guardiao(l, BIBLIOTECA.offset(0, 0, 2), "conselho_selun");
        // Capitães
        for (String t : CidadeMissoes.TECNICAS) {
            BlockPos a = ala(t);
            LanternaEntity cap = CidadeNpcs.npc(l, a.offset(a.getX() < 0 ? -2 : 2, 0, 0), "capitao_" + t, BuiltinCorps.GREEN, true, a.getX() < 0 ? 270 : 90, 2);
            cap.setup(com.lanterna.especies.Especie.byId(especie(t)), 2, com.lanterna.especies.Especie.Papel.LANTERNA, BuiltinCorps.GREEN,
                    "roupa_tropa_green", true, false);
            cap.setCustomName(CidadeNpcs.nome("capitao_" + t));
        }
        // Guarda de Honra
        for (int[] g : new int[][]{{-4, 56}, {4, 56}, {-4, 12}, {4, 12}, {-4, -22}, {4, -22}, {-12, 4}, {12, 4}}) {
            CidadeNpcs.npc(l, CENTRO.offset(g[0], 0, g[1]), "honra", BuiltinCorps.GREEN, true, 0, 3);
        }
        // Presos
        for (int i = -1; i <= 1; i++) {
            LanternaEntity e = LanternaEntity.lanterna(l, BuiltinCorps.BLACK, false);
            e.moveTo(CENTRO.getX() + 30 + 4.5, Y, CENTRO.getZ() + 36 + i * 3 + 0.5, 270, 0);
            e.setNoAi(true);
            e.setInvulnerable(true);
            e.setPersistenceRequired();
            e.setFuncao("preso");
            l.addFreshEntity(e);
        }
    }

    /** Espécie de cada Capitão (os modelos que já existem). */
    public static String especie(String tec) {
        return switch (tec) {
            case "armadura" -> "brutor";
            case "salto" -> "lumiae";
            case "onda" -> "drevan";
            case "bastiao" -> "ilith";
            case "olhar" -> "aurith";
            default -> "qiri";
        };
    }

    // ===================== Acesso, Arauto e a Câmara =====================

    public static boolean permitido(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return true;
        LoreData d = LoreCapability.get(p).orElse(null);
        return d != null && (d.isActive("pal_chamado") || d.isDone("pal_chamado") || Nivel.nivel(d.xp()) >= 30);
    }

    private static boolean emOa(ServerPlayer p) {
        SpaceBodies.Body b = SpaceBodies.byId("oa");
        return b != null && p.level().dimension().equals(b.dimension());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 20 != 3 || !emOa(p)) return;
        double dx = p.getX() - CENTRO.getX(), dz = p.getZ() - CENTRO.getZ();
        boolean noPalacio = dx * dx + dz * dz < (R + 6) * (R + 6) && p.getY() > Y - 40 && p.getY() < Y + 80;
        if (noPalacio && !permitido(p)) {
            int z = Cidade.CENTRO.getZ() - Cidade.R - 4;
            ServerLevel l = p.serverLevel();
            int y = altura(l, 0, z);
            p.teleportTo(l, 0.5, y + 1, z + 0.5, 180, 0);
            p.displayClientMessage(Component.translatable("palacio.lanterna.barrado"), false);
            l.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1f, 0.6f);
            return;
        }
        // Câmara da Bateria Primordial: recarrega o anel
        if (p.blockPosition().distSqr(CAMARA) < 11 * 11) {
            var ring = ModCapabilities.get(p).orElse(null);
            if (ring != null && ring.hasRing() && ring.charge() < ring.maxCharge()) {
                RingPowers.setCharge(p, ring, Math.min(ring.maxCharge(), ring.charge() + Math.max(30, ring.maxCharge() / 20)));
                p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 6, 0.4, 0.6, 0.4, 0.02);
            }
        }
    }

    /** O Arauto (no QG da cidade de Oa) leva até o palácio quem foi chamado. */
    public static void arauto(ServerPlayer p, LanternaEntity npc) {
        if (!permitido(p)) {
            CidadeNpcs.fala(p, npc, Component.translatable("palacio.lanterna.arauto.nao"));
            return;
        }
        CidadeNpcs.fala(p, npc, Component.translatable("palacio.lanterna.arauto.sim"));
        ServerLevel l = p.serverLevel();
        l.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 1, 0.4, 0.05);
        p.teleportTo(l, PORTAO.getX() + 0.5, Y, PORTAO.getZ() + 3.5, 180, 0);
        l.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1.2f);
    }

    /** Conversa com um Guardião do Conselho. */
    public static void conselho(ServerPlayer p, RaceEntity npc) {
        String role = npc.role();
        MissionManager.onTalk(p, role, "oa");
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null) return;
        p.sendSystemMessage(Component.literal("<").append(npc.getDisplayName()).append("> ")
                .append(Component.translatable("palacio.lanterna.fala." + role)).withStyle(s -> s.withColor(0xBFF5CF)));
        if (role.equals("conselho_varo") || role.equals("conselho_miraeth")) {
            CidadeMissoes.enviarPainel(p, d, npc.getDisplayName(), List.of("pal_honra"));
        }
    }

    private Palacio() {}
}
