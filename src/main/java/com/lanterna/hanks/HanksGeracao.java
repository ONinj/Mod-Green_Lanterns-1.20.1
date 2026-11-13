package com.lanterna.hanks;

import com.lanterna.cidade.Layout;
import com.lanterna.lendarios.ArenaLendaria;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Hanks, o planeta-máquina (mundos da 0.48+), gerado junto com o terreno.
 *
 * Superfície: placas de metal em degraus (uma grade de 12 blocos com alturas diferentes), blocos-máquina altos,
 * trincheiras com canos, respiros, antenas e os Poços de Energia: crateras de lava de onde sobe uma coluna de luz amarela
 * (a energia do planeta). Perto do pouso, dos três núcleos e da cidadela do Sinestro o chão fica plano.
 *
 * Por baixo: o Núcleo. Uma câmara enorme no meio do planeta (como uma estação de guerra oca), onde vivem os Lanternas
 * Amarelos: no centro a esfera de energia presa por vigas ao chão e ao teto, anel de avenida, prédios de pedra escura
 * com vidro amarelo, pilares até a cúpula e o chão tomado de sculk vivo (as paredes respiram). Um poço com escada,
 * a sul do pouso, liga a superfície ao Núcleo.
 *
 * Tudo é calculado coluna por coluna (cada pedaço do mundo só mexe nele mesmo), nas threads de geração.
 */
public final class HanksGeracao extends Feature<NoneFeatureConfiguration> {
    /** Primeiro bloco de ar da superfície (a mesma altura do Hanks antigo). */
    public static final int SUP = -10;
    /** Centro e tamanho da câmara do Núcleo. */
    public static final int NUCLEO_Y = -110, NUCLEO_RX = 95, NUCLEO_RY = 58;
    /** Chão da cidade do Núcleo. */
    public static final int CHAO = -140;
    /** O poço que liga a superfície ao Núcleo. */
    public static final BlockPos POCO = new BlockPos(0, 0, 40);
    private static final int CELULA = 12;

    public HanksGeracao(Codec<NoneFeatureConfiguration> c) {
        super(c);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!Layout.novo()) return false;
        WorldGenLevel l = ctx.level();
        if (!l.getLevel().dimension().equals(HanksMundo.DIM)) return false;
        int cx = ctx.origin().getX() >> 4, cz = ctx.origin().getZ() >> 4;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = cx << 4; x < (cx << 4) + 16; x++) for (int z = cz << 4; z < (cz << 4) + 16; z++) {
            superficie(l, m, x, z);
            nucleo(l, m, x, z);
            poco(l, m, x, z);
        }
        return true;
    }

    // ===================== ajudantes =====================

    private static long h(long a, long b) {
        long x = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ 0x48414E4B53L;
        x ^= x >>> 31; x *= 0xBF58476D1CE4E5B9L; x ^= x >>> 29;
        return x;
    }

    private static void s(WorldGenLevel l, BlockPos.MutableBlockPos m, int x, int y, int z, BlockState b) {
        if (y < l.getMinBuildHeight() || y >= l.getMaxBuildHeight()) return;
        l.setBlock(m.set(x, y, z), b, 2);
    }

    private static BlockState b(Block x) {
        return x.defaultBlockState();
    }

    private static double d(double x, double z) {
        return Math.sqrt(x * x + z * z);
    }

    /** Áreas que precisam ficar planas (pouso, núcleos, cidadela e o caminho até ela, a boca do poço). */
    static boolean plano(int x, int z) {
        if (d(x, z) < 16) return true;
        for (BlockPos n : HanksMundo.NUCLEOS_XZ) if (d(x - n.getX(), z - n.getZ()) < 11) return true;
        if (d(x, z - HanksMundo.CIDADELA_Z) < ArenaLendaria.RAIO + 8) return true;
        if (Math.abs(x) <= 4 && z >= HanksMundo.CIDADELA_Z && z <= 0) return true;
        if (Math.abs(x - POCO.getX()) <= 6 && Math.abs(z - POCO.getZ()) <= 6) return true;
        return false;
    }

    // ===================== superfície =====================

    /** Tipo da célula: 0 placa, 1 placa elevada, 2 bloco-máquina, 3 trincheira, 4 poço de energia. */
    private static int tipo(int gx, int gz) {
        long r = h(gx, gz);
        int v = (int) ((r >>> 8) & 99);
        if (v < 34) return 0;
        if (v < 64) return 1;
        if (v < 76) return 2;
        if (v < 90) return 3;
        return 4;
    }

    private static boolean celulaPlana(int gx, int gz) {
        for (int dx = 0; dx < CELULA; dx += 3) for (int dz = 0; dz < CELULA; dz += 3) if (plano(gx * CELULA + dx, gz * CELULA + dz)) return true;
        return plano(gx * CELULA + CELULA - 1, gz * CELULA + CELULA - 1);
    }

    private static void superficie(WorldGenLevel l, BlockPos.MutableBlockPos m, int x, int z) {
        int gx = Math.floorDiv(x, CELULA), gz = Math.floorDiv(z, CELULA);
        int lx = Math.floorMod(x, CELULA), lz = Math.floorMod(z, CELULA);
        boolean borda = lx == 0 || lz == 0;
        long r = h(gx, gz);
        BlockState placa = b(Blocks.POLISHED_DEEPSLATE), tile = b(Blocks.DEEPSLATE_TILES), escura = b(Blocks.POLISHED_BLACKSTONE_BRICKS),
                luz = b(Blocks.OCHRE_FROGLIGHT), ar = b(Blocks.AIR), cobre = b(Blocks.WAXED_OXIDIZED_CUT_COPPER), ferro = b(Blocks.IRON_BLOCK);
        if (celulaPlana(gx, gz)) {
            // chão plano com circuito (o mesmo desenho do Hanks antigo)
            if (borda) s(l, m, x, SUP - 1, z, ((r >>> lx + lz) & 3) != 0 ? luz : escura);
            return;
        }
        int t = tipo(gx, gz);
        switch (t) {
            case 0 -> {
                s(l, m, x, SUP - 1, z, borda ? (((r >>> 3) & 1) == 0 ? luz : escura) : (((lx * 7 + lz * 3 + r) & 7) == 0 ? escura : placa));
                if (lx == 6 && lz == 6 && ((r >>> 11) & 3) == 0) { s(l, m, x, SUP, z, b(Blocks.LIGHTNING_ROD)); }
            }
            case 1 -> {
                int alt = 2 + (int) ((r >>> 13) % 5);
                for (int y = 0; y < alt; y++) {
                    boolean lado = lx == 0 || lz == 0 || lx == CELULA - 1 || lz == CELULA - 1;
                    BlockState bb = lado && y == alt - 1 ? (((r >>> 5) & 1) == 0 ? luz : escura) : lado && y % 2 == 0 && (lx + lz) % 3 == 1 ? b(Blocks.IRON_BARS) : tile;
                    s(l, m, x, SUP + y, z, bb);
                }
                s(l, m, x, SUP + alt - 1, z, (lx == 0 || lz == 0) ? escura : placa);
                if (lx == 3 && lz == 3) s(l, m, x, SUP + alt, z, b(Blocks.POLISHED_BLACKSTONE_BUTTON).setValue(ButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
            }
            case 2 -> {
                // bloco-máquina: torre larga com cantos de cobre, faixas de luz e respiros
                int alt = 8 + (int) ((r >>> 17) % 16);
                boolean dentro = lx >= 1 && lx <= CELULA - 2 && lz >= 1 && lz <= CELULA - 2;
                if (!dentro) { s(l, m, x, SUP - 1, z, escura); return; }
                boolean canto = (lx == 1 || lx == CELULA - 2) && (lz == 1 || lz == CELULA - 2);
                boolean parede = lx == 1 || lx == CELULA - 2 || lz == 1 || lz == CELULA - 2;
                for (int y = 0; y < alt; y++) {
                    BlockState bb;
                    if (canto) bb = cobre;
                    else if (parede) bb = y % 5 == 4 ? luz : (y % 5 == 2 && (lx + lz) % 2 == 0 ? b(Blocks.YELLOW_STAINED_GLASS) : tile);
                    else bb = y == alt - 1 ? escura : ar;
                    s(l, m, x, SUP + y, z, bb);
                }
                if (lx == 6 && lz == 6) {
                    s(l, m, x, SUP + alt, z, ferro);
                    s(l, m, x, SUP + alt + 1, z, b(Blocks.LIGHTNING_ROD));
                }
                if (lx == 4 && lz == 7) { s(l, m, x, SUP + alt, z, b(Blocks.CAMPFIRE).setValue(CampfireBlock.SIGNAL_FIRE, true)); }
            }
            case 3 -> {
                // trincheira com canos e lava no fundo
                int fundo = 5;
                boolean parede = lx == 0 || lz == 0 || lx == CELULA - 1 || lz == CELULA - 1;
                if (parede) { s(l, m, x, SUP - 1, z, escura); return; }
                for (int y = 1; y <= fundo; y++) s(l, m, x, SUP - y, z, ar);
                s(l, m, x, SUP - fundo - 1, z, (lx == 6) ? b(Blocks.MAGMA_BLOCK) : tile);
                if (lx == 6) s(l, m, x, SUP - fundo, z, b(Blocks.LAVA));
                if (lz == 3 || lz == 8) s(l, m, x, SUP - 3, z, (lx % 4 == 0) ? ferro : cobre);
                if (lx == 3 && lz % 4 == 1) s(l, m, x, SUP - 2, z, b(Blocks.CHAIN));
            }
            default -> {
                // Poço de Energia: cratera de lava, borda de magma e ouro, e a coluna de luz subindo
                double dd = d(lx - 5.5, lz - 5.5);
                if (dd > 5.6) { s(l, m, x, SUP - 1, z, escura); return; }
                if (dd > 4.6) {
                    s(l, m, x, SUP - 1, z, b(Blocks.GILDED_BLACKSTONE));
                    s(l, m, x, SUP, z, ((lx + lz) & 1) == 0 ? b(Blocks.POLISHED_BLACKSTONE_BRICK_WALL) : ar);
                    return;
                }
                int prof = 10;
                for (int y = 1; y <= prof; y++) s(l, m, x, SUP - y, z, y >= prof - 2 ? b(Blocks.LAVA) : ar);
                s(l, m, x, SUP - prof - 1, z, b(Blocks.MAGMA_BLOCK));
                if (dd > 3.6) for (int y = 1; y < prof - 2; y++) s(l, m, x, SUP - y, z, y % 3 == 0 ? luz : b(Blocks.MAGMA_BLOCK));
                if (dd < 1.2) for (int y = -prof + 3; y < 26; y++) s(l, m, x, SUP + y, z, y % 3 == 0 ? luz : b(Blocks.YELLOW_STAINED_GLASS));
                if (dd < 0.8) s(l, m, x, SUP + 26, z, b(Blocks.END_ROD));
            }
        }
    }

    // ===================== o Núcleo =====================

    private static void nucleo(WorldGenLevel l, BlockPos.MutableBlockPos m, int x, int z) {
        double dh = d(x, z);
        if (dh >= NUCLEO_RX + 3) return;
        double k = 1 - (dh * dh) / ((double) NUCLEO_RX * NUCLEO_RX);
        int alto = k > 0 ? (int) Math.round(NUCLEO_Y + NUCLEO_RY * Math.sqrt(k)) : NUCLEO_Y;
        BlockState ar = b(Blocks.AIR), tile = b(Blocks.DEEPSLATE_TILES), luz = b(Blocks.OCHRE_FROGLIGHT), escura = b(Blocks.POLISHED_BLACKSTONE_BRICKS),
                sculk = b(Blocks.SCULK), veio = b(Blocks.REINFORCED_DEEPSLATE), vidro = b(Blocks.YELLOW_STAINED_GLASS), ouro = b(Blocks.GOLD_BLOCK);
        long r = h(x, z * 31L);
        if (k <= 0) {
            // casca da câmara (do lado de fora fica pedra)
            return;
        }
        // abre a câmara do chão até a cúpula
        for (int y = CHAO; y < alto; y++) s(l, m, x, y, z, ar);
        // cúpula: pedra escura com veios de luz e sculk (a parede "viva")
        s(l, m, x, alto, z, ((r >>> 4) & 15) == 0 ? luz : ((r >>> 9) & 3) == 0 ? sculk : tile);
        if (((r >>> 13) & 63) == 0 && alto - CHAO > 30) {
            int n = 3 + (int) ((r >>> 20) % 10);
            for (int i = 1; i <= n; i++) s(l, m, x, alto - i, z, b(Blocks.CHAIN));
            s(l, m, x, alto - n - 1, z, b(Blocks.SOUL_LANTERN).setValue(LanternBlock.HANGING, true));
        }
        // chão da cidade
        double ang = Math.atan2(z, x);
        boolean avenida = dh >= 19 && dh <= 25;
        boolean raio = dh > 25 && Math.abs(Math.sin(ang * 4)) * dh < 2.5;
        BlockState chao;
        if (dh < 19) chao = ((int) dh % 4 == 0) ? luz : escura;
        else if (avenida) chao = Math.abs(dh - 22) < 0.6 ? luz : escura;
        else if (raio) chao = Math.abs(Math.sin(ang * 4)) * dh < 0.5 ? luz : escura;
        else chao = ((r >>> 7) & 3) != 0 ? sculk : (((r >>> 11) & 7) == 0 ? veio : tile);
        s(l, m, x, CHAO - 1, z, chao);
        if (!avenida && !raio && dh >= 25 && ((r >>> 23) & 31) == 0) s(l, m, x, CHAO, z, b(Blocks.SCULK_SENSOR));
        else if (!avenida && !raio && dh >= 25 && ((r >>> 23) & 15) == 1) s(l, m, x, CHAO, z, b(Blocks.SCULK_VEIN));
        // a esfera de energia no centro e as vigas
        double esfera = Math.sqrt(x * x + z * z);
        int ey = NUCLEO_Y + 5;
        for (int y = CHAO; y < alto; y++) {
            double de = Math.sqrt(esfera * esfera + (y - ey) * (y - ey));
            if (de <= 12) s(l, m, x, y, z, de > 11 ? ((((x + y + z) & 1) == 0) ? vidro : luz) : de > 9 ? b(Blocks.SHROOMLIGHT) : luz);
            else if (esfera <= 1.6 && (y < ey - 12 || y > ey + 12)) s(l, m, x, y, z, (y & 3) == 0 ? ouro : vidro);
        }
        // pilares até a cúpula
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12 + Math.PI / 12;
            double px = Math.cos(a) * 62, pz = Math.sin(a) * 62;
            if (Math.abs(x - px) <= 1.6 && Math.abs(z - pz) <= 1.6) {
                for (int y = CHAO; y < alto; y++) s(l, m, x, y, z, (y - CHAO) % 12 == 11 ? ouro : (y - CHAO) % 12 == 5 ? luz : escura);
            }
        }
        // prédios dos Lanternas Amarelos (dois anéis de 16 setores)
        predio(l, m, x, z, dh, ang, r);
    }

    private static void predio(WorldGenLevel l, BlockPos.MutableBlockPos m, int x, int z, double dh, double ang, long rr) {
        int[][] aneis = {{30, 44}, {48, 70}};
        for (int a = 0; a < aneis.length; a++) {
            int r0 = aneis[a][0], r1 = aneis[a][1];
            if (dh < r0 || dh > r1) continue;
            int setores = a == 0 ? 12 : 16;
            double passo = Math.PI * 2 / setores;
            double rel = ang + Math.PI;
            int s = (int) Math.floor(rel / passo);
            double dentroSetor = rel - s * passo;
            double tang = dentroSetor * dh, larg = passo * dh;
            // rua entre os prédios
            if (tang < 3.5 || tang > larg - 3.5) return;
            long r = h(s * 13L + a, 77);
            int alt = 9 + (int) ((r >>> 5) % (a == 0 ? 18 : 26));
            boolean pareda = dh - r0 < 1 || r1 - dh < 1 || tang < 4.5 || tang > larg - 4.5;
            BlockState parede = b(Blocks.DEEPSLATE_BRICKS), janela = b(Blocks.YELLOW_STAINED_GLASS), luz = b(Blocks.OCHRE_FROGLIGHT),
                    teto = b(Blocks.POLISHED_BLACKSTONE_BRICKS), ar = b(Blocks.AIR), piso = b(Blocks.POLISHED_DEEPSLATE);
            boolean porta = dh - r0 < 1 && Math.abs(tang - larg / 2) < 1.6;
            for (int y = 0; y <= alt; y++) {
                int yy = CHAO + y;
                BlockState bb;
                if (y == alt) bb = teto;
                else if (pareda) {
                    if (porta && y < 3) bb = ar;
                    else if (y % 6 == 5) bb = luz;
                    else if (y % 6 >= 2 && y % 6 <= 3 && ((int) tang + (int) dh) % 3 != 0) bb = janela;
                    else bb = parede;
                } else bb = (y % 6 == 0 && y > 0) ? piso : ar;
                s(l, m, x, yy, z, bb);
            }
            // sculk subindo pela base das paredes (a cidade é viva)
            if (pareda && ((rr >>> 3) & 3) == 0) s(l, m, x, CHAO, z, b(Blocks.SCULK));
            if (!pareda && Math.abs(tang - larg / 2) < 0.6 && Math.abs(dh - (r0 + r1) / 2.0) < 0.6) s(l, m, x, CHAO + 1, z, b(Blocks.SCULK_CATALYST));
            return;
        }
    }

    // ===================== o poço =====================

    private static void poco(WorldGenLevel l, BlockPos.MutableBlockPos m, int x, int z) {
        int dx = x - POCO.getX(), dz = z - POCO.getZ();
        if (Math.abs(dx) > 3 || Math.abs(dz) > 3) return;
        boolean parede = Math.abs(dx) == 3 || Math.abs(dz) == 3;
        double dh = d(x, z);
        double k = 1 - (dh * dh) / ((double) NUCLEO_RX * NUCLEO_RX);
        int teto = (int) Math.round(NUCLEO_Y + NUCLEO_RY * Math.sqrt(Math.max(0, k)));
        for (int y = CHAO; y < SUP + 4; y++) {
            BlockState bb;
            if (parede) {
                boolean naCamara = y < teto;
                if (y >= SUP) bb = y == SUP + 3 ? b(Blocks.OCHRE_FROGLIGHT) : (Math.abs(dx) == 3 && Math.abs(dz) == 3 ? b(Blocks.WAXED_OXIDIZED_CUT_COPPER) : b(Blocks.AIR));
                else if (naCamara && dz == 3 && Math.abs(dx) <= 1 && y < CHAO + 3) bb = b(Blocks.AIR); // porta no Núcleo
                else bb = (y & 7) == 0 ? b(Blocks.OCHRE_FROGLIGHT) : naCamara && (y & 3) == 2 ? b(Blocks.YELLOW_STAINED_GLASS) : b(Blocks.POLISHED_BLACKSTONE_BRICKS);
            } else if (dz == -2 && dx == 0 && y < SUP) {
                bb = b(Blocks.LADDER).setValue(LadderBlock.FACING, Direction.SOUTH);
            } else bb = y == CHAO - 1 ? b(Blocks.POLISHED_BLACKSTONE) : b(Blocks.AIR);
            if (y >= SUP && !parede) continue;
            s(l, m, x, y, z, bb);
        }
    }
}
