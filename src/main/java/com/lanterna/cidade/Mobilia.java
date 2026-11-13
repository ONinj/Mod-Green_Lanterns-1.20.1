package com.lanterna.cidade;

import com.lanterna.LanternaMod;
import com.lanterna.lore.PlanetLore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.*;

/**
 * Mobília das casas (0.48): olha por dentro da casa já desenhada, acha o chão de cada andar, as paredes e a porta, e
 * põe móveis encostados nas paredes sem fechar a passagem: cama (se não tiver), baú e barril com itens do planeta,
 * bancada, fornalha, a oficina do povo, estante, vasos com as flores do lugar, tapete e luz.
 */
public final class Mobilia {

    /** O que cada povo tem em casa. */
    private record Jeito(Block oficina1, Block oficina2, Block flor, Block tapete, Block enfeite, boolean defumador) {}

    private static Jeito jeito(PlanetLore p) {
        if (p == null) return new Jeito(Blocks.LOOM, Blocks.LECTERN, Blocks.POTTED_RED_TULIP, Blocks.BROWN_CARPET, Blocks.BOOKSHELF, false);
        return switch (p) {
            case OA -> new Jeito(Blocks.LECTERN, Blocks.CARTOGRAPHY_TABLE, Blocks.POTTED_AZURE_BLUET, Blocks.LIME_CARPET, Blocks.BOOKSHELF, false);
            case KORUGAR -> new Jeito(Blocks.FLETCHING_TABLE, Blocks.GRINDSTONE, Blocks.POTTED_WITHER_ROSE, Blocks.BLACK_CARPET, Blocks.BLACK_CANDLE, false);
            case YSMAULT -> new Jeito(Blocks.SMITHING_TABLE, Blocks.ANVIL, Blocks.POTTED_CRIMSON_FUNGUS, Blocks.RED_CARPET, Blocks.SKELETON_SKULL, true);
            case ODYM -> new Jeito(Blocks.BREWING_STAND, Blocks.LOOM, Blocks.POTTED_BLUE_ORCHID, Blocks.LIGHT_BLUE_CARPET, Blocks.SEA_PICKLE, true);
            case OKAARA -> new Jeito(Blocks.CARTOGRAPHY_TABLE, Blocks.SMITHING_TABLE, Blocks.POTTED_ORANGE_TULIP, Blocks.ORANGE_CARPET, Blocks.RAW_GOLD_BLOCK, false);
            case NOK -> new Jeito(Blocks.COMPOSTER, Blocks.LECTERN, Blocks.POTTED_FERN, Blocks.PURPLE_CARPET, Blocks.PURPLE_CANDLE, true);
            case ZAMARON -> new Jeito(Blocks.LOOM, Blocks.BREWING_STAND, Blocks.POTTED_LILY_OF_THE_VALLEY, Blocks.MAGENTA_CARPET, Blocks.AMETHYST_CLUSTER, false);
            case RYUT -> new Jeito(Blocks.STONECUTTER, Blocks.LECTERN, Blocks.POTTED_WITHER_ROSE, Blocks.GRAY_CARPET, Blocks.SKELETON_SKULL, false);
            default -> new Jeito(Blocks.LOOM, Blocks.LECTERN, Blocks.POTTED_POPPY, Blocks.WHITE_CARPET, Blocks.BOOKSHELF, false);
        };
    }

    public static boolean DEBUG = Boolean.getBoolean("lanterna.debugMobilia");

    public static ResourceLocation tabela(PlanetLore p, String tipo) {
        return LanternaMod.id("chests/" + (p == null ? "terra" : p.planet) + "/" + tipo);
    }

    private static boolean solido(BlockState s) {
        return !s.isAir() && !s.canBeReplaced() && s.getFluidState().isEmpty();
    }

    /**
     * Mobilia a casa em volta de "h" (o chão do térreo). "raio" é até onde a casa vai para os lados.
     * Funciona com o desenho na memória (geração) ou no mundo.
     */
    public static void mobiliar(Kit k, BlockPos h, int raio, PlanetLore planeta, long semente) {
        Random rnd = new Random(semente);
        Jeito j = jeito(planeta);
        // acha os andares: células de ar com chão sólido, teto em cima e paredes nos 4 lados
        Map<Integer, List<BlockPos>> andares = new TreeMap<>();
        Set<Long> fechadas = new HashSet<>();
        boolean temCama = false, temLuz = false;
        for (int dy = -1; dy <= 16; dy++) for (int dx = -raio; dx <= raio; dx++) for (int dz = -raio; dz <= raio; dz++) {
            BlockPos p = h.offset(dx, dy, dz);
            BlockState s = k.get(p);
            if (s.getBlock() instanceof BedBlock) temCama = true;
            if (s.getLightEmission() > 9) temLuz = true;
            if (!s.isAir() || !k.get(p.above()).isAir() || !solido(k.get(p.below()))) continue;
            if (!fechada(k, p, raio + 2)) continue;
            fechadas.add(p.asLong());
            andares.computeIfAbsent(p.getY(), y -> new ArrayList<>()).add(p);
        }
        if (DEBUG) LanternaMod.LOGGER.info("Lanterna: mobília em {}: andares={} células={}", h, andares.keySet(),
                andares.values().stream().mapToInt(List::size).sum());
        int feitos = 0;
        for (var en : andares.entrySet()) {
            List<BlockPos> celulas = en.getValue();
            if (celulas.size() < 5 || feitos >= 2) continue;
            feitos++;
            // longe da porta: célula fechada vizinha de ar aberto (e as vizinhas dela) fica livre
            Set<Long> livres = new HashSet<>();
            Map<Long, Boolean> aberto = new HashMap<>();
            for (BlockPos p : celulas) for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos q = p.relative(d);
                if (k.get(q).isAir() && !fechadas.contains(q.asLong())
                        && aberto.computeIfAbsent(q.asLong(), x -> !fechada(k, q, raio + 2))) {
                    // céu aberto do lado: é a porta de verdade, deixa folga em volta; debaixo do teto é só o corredor
                    // que leva à porta (ele mesmo fica vazio, por não estar fechado), então dá para encostar nele
                    if (!coberta(k, q)) {
                        livres.add(p.asLong());
                        for (Direction d2 : Direction.Plane.HORIZONTAL) livres.add(p.relative(d2).asLong());
                    }
                }
            }
            List<BlockPos> parede = new ArrayList<>(), meio = new ArrayList<>();
            for (BlockPos p : celulas) {
                if (livres.contains(p.asLong())) continue;
                boolean encosta = false;
                for (Direction d : Direction.Plane.HORIZONTAL) if (solido(k.get(p.relative(d)))) encosta = true;
                (encosta ? parede : meio).add(p);
            }
            Collections.shuffle(parede, rnd);
            Set<Long> usadas = new HashSet<>();
            List<BlockState> moveis = new ArrayList<>();
            moveis.add(b(Blocks.CHEST));
            moveis.add(b(Blocks.CRAFTING_TABLE));
            moveis.add(b(j.defumador() ? Blocks.SMOKER : Blocks.FURNACE));
            moveis.add(b(Blocks.BARREL));
            moveis.add(b(j.oficina1()));
            moveis.add(b(j.flor()));
            moveis.add(b(j.oficina2()));
            moveis.add(b(Blocks.BOOKSHELF));
            moveis.add(b(j.enfeite()));
            moveis.add(b(j.flor()));
            int max = Math.min(moveis.size(), Math.max(3, celulas.size() / 5));
            // cama primeiro (se a casa não tem)
            if (!temCama && en.getKey().equals(andares.keySet().iterator().next())) {
                for (BlockPos p : parede) {
                    Direction fora = paraLonge(k, p);
                    if (fora == null) continue;
                    BlockPos pes = p.relative(fora);
                    if (!celulas.contains(pes) || livres.contains(pes.asLong())) continue;
                    Direction cabeca = fora.getOpposite();
                    k.s(pes, cama(planeta).setValue(BedBlock.FACING, cabeca).setValue(BedBlock.PART, BedPart.FOOT));
                    k.s(p, cama(planeta).setValue(BedBlock.FACING, cabeca).setValue(BedBlock.PART, BedPart.HEAD));
                    usadas.add(p.asLong()); usadas.add(pes.asLong());
                    temCama = true;
                    break;
                }
            }
            int n = 0;
            for (BlockPos p : parede) {
                if (n >= max) break;
                if (usadas.contains(p.asLong())) continue;
                boolean perto = false;
                for (Direction d : Direction.Plane.HORIZONTAL) if (usadas.contains(p.relative(d).asLong())) perto = n % 3 != 0;
                if (perto) continue;
                BlockState m = moveis.get(n);
                Direction frente = paraLonge(k, p);
                if (frente != null && m.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
                    m = m.setValue(BlockStateProperties.HORIZONTAL_FACING, m.getBlock() instanceof AnvilBlock ? frente.getClockWise() : frente);
                if (m.getBlock() instanceof CandleBlock) m = m.setValue(CandleBlock.CANDLES, 1 + rnd.nextInt(3)).setValue(CandleBlock.LIT, true);
                if (m.getBlock() instanceof SkullBlock && frente != null) m = m.setValue(SkullBlock.ROTATION, (frente.get2DDataValue() * 4) & 15);
                k.s(p, m);
                if (m.is(Blocks.CHEST)) k.m.bau(p, tabela(planeta, "casa"));
                if (m.is(Blocks.BARREL)) k.m.bau(p, tabela(planeta, "barril"));
                usadas.add(p.asLong());
                n++;
            }
            // mesa e tapete no meio
            if (!meio.isEmpty()) {
                BlockPos mesa = meio.get(rnd.nextInt(meio.size()));
                k.s(mesa, b(Blocks.SPRUCE_FENCE));
                k.s(mesa.above(), b(Blocks.SPRUCE_PRESSURE_PLATE));
                for (BlockPos p : meio) if (p != mesa && rnd.nextInt(3) > 0 && !usadas.contains(p.asLong())) k.s(p, b(j.tapete()));
            }
            // luz: lanterna pendurada no teto, se a casa está escura
            if (!temLuz) {
                for (BlockPos p : meio.isEmpty() ? celulas : meio) {
                    BlockPos t = p.above(2);
                    int sobe = 0;
                    while (sobe < 8 && k.get(t.above()).isAir()) { t = t.above(); sobe++; }
                    if (sobe < 8 && solido(k.get(t.above()))) {
                        k.s(t, b(Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
                        temLuz = true;
                        break;
                    }
                }
            }
        }
    }

    /** Para onde o móvel encostado na parede olha (o lado aberto). */
    private static Direction paraLonge(Kit k, BlockPos p) {
        for (Direction d : Direction.Plane.HORIZONTAL) if (solido(k.get(p.relative(d)))) return d.getOpposite();
        return null;
    }

    /** Ar com paredes nos 4 lados e teto em cima (até "max" blocos). */
    private static boolean fechada(Kit k, BlockPos p, int max) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            boolean bate = false;
            for (int i = 1; i <= max; i++) if (solido(k.get(p.relative(d, i)))) { bate = true; break; }
            if (!bate) return false;
        }
        for (int i = 2; i <= 16; i++) if (solido(k.get(p.above(i)))) return true;
        return false;
    }

    private static boolean coberta(Kit k, BlockPos p) {
        for (int i = 1; i <= 16; i++) if (solido(k.get(p.above(i)))) return true;
        return false;
    }

    private static BlockState cama(PlanetLore p) {
        if (p == null) return b(Blocks.BLUE_BED);
        return switch (p) {
            case OA -> b(Blocks.LIME_BED);
            case KORUGAR -> b(Blocks.YELLOW_BED);
            case YSMAULT -> b(Blocks.RED_BED);
            case ODYM -> b(Blocks.LIGHT_BLUE_BED);
            case OKAARA -> b(Blocks.ORANGE_BED);
            case NOK -> b(Blocks.PURPLE_BED);
            case ZAMARON -> b(Blocks.MAGENTA_BED);
            case RYUT -> b(Blocks.GRAY_BED);
            default -> b(Blocks.WHITE_BED);
        };
    }

    private static BlockState b(Block x) {
        return x.defaultBlockState();
    }

    private Mobilia() {}
}
