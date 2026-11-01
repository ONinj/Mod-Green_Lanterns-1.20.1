package com.lanterna.cidade;

import com.lanterna.LanternaMod;
import com.lanterna.lore.PlanetLore;
import com.lanterna.lore.entity.Race;
import com.lanterna.lore.entity.RaceEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import static com.lanterna.cidade.Kit.b;

/**
 * Cidade dos nativos de cada planeta das Tropas (Veltari em Oa, Brakkari em Ysmault, Aelínos em Odym...), a oeste da
 * base dos Lanternas. Bem maior que o Quartel: um disco de 117 blocos com arquitetura de cidade do futuro, feita
 * de luz e vidro, nas cores do planeta:
 *
 *   Torre da Luz      agulha de vidro no centro (~80 blocos) com núcleo de luz e uma coroa a 60 blocos
 *   8 torres-cálice   hastes finas com raízes de apoio e um casulo de vidro no alto (38 a 56 blocos)
 *   pontes aéreas     de cada casulo até a Torre da Luz, em alturas diferentes
 *   4 portais         arcos enormes nas avenidas norte, sul, leste e oeste
 *   16 casas-cúpula   em volta, onde os nativos moram (cama, luz, janelas)
 *   praça             linhas de luz saindo do centro, anéis no chão, postes
 *
 * Os nativos (12) nascem nas casas; se faltarem, o vigia de moradores repõe.
 */
public final class CidadeNativa {
    public static final BlockPos CENTRO = new BlockPos(-210, 0, 0);
    public static final int R = 58;
    private static final int ALTO = 86;

    public static void construir(ServerLevel level, PlanetLore planet) {
        Paleta pal = Paleta.of(planet);
        Kit k = new Kit(level, pal);
        int y = chao(level);
        BlockPos c = CENTRO.atY(y);
        for (int x0 = -R - 12; x0 <= R + 12; x0 += 8) { int f = x0; Obra.etapa(level, l -> terreno(k, c, f, Math.min(R + 12, f + 7))); }
        Obra.etapa(level, l -> { praca(k, c); avenidas(k, c); });
        Obra.etapa(level, l -> borda(k, c));
        for (int h0 = 0; h0 <= ALTO; h0 += 12) { int f = h0; Obra.etapa(level, l -> torreDaLuz(k, c, f, Math.min(ALTO, f + 11))); }
        for (int i = 0; i < 8; i++) { int f = i; Obra.etapa(level, l -> torreCalice(k, c, f)); }
        for (int i = 0; i < 8; i++) { int f = i; Obra.etapa(level, l -> ponte(k, c, f)); }
        for (Direction d : Direction.Plane.HORIZONTAL) Obra.etapa(level, l -> portal(k, c, d));
        for (int i = 0; i < 16; i++) { int f = i; Obra.etapa(level, l -> casa(k, c, f)); }
        Obra.etapa(level, l -> jardins(k, c));
        Obra.etapa(level, l -> nativos(l, c, planet, 12));
        Obra.etapa(level, l -> LanternaMod.LOGGER.info("Lanterna: cidade dos nativos pronta em {}", planet.planet));
    }

    private static int chao(ServerLevel level) {
        int sum = 0, n = 0;
        for (int dx = -R; dx <= R; dx += 16) for (int dz = -R; dz <= R; dz += 16) {
            if (dx * dx + dz * dz > R * R) continue;
            level.getChunk((CENTRO.getX() + dx) >> 4, (CENTRO.getZ() + dz) >> 4);
            sum += level.getHeight(Heightmap.Types.WORLD_SURFACE, CENTRO.getX() + dx, CENTRO.getZ() + dz);
            n++;
        }
        int y = sum / Math.max(1, n);
        return Math.max(level.getMinBuildHeight() + 12, Math.min(y, level.getMaxBuildHeight() - ALTO - 10));
    }

    /** Altura do chão de uma cidade já construída (a praça em volta da Torre é plana). */
    public static int altura(ServerLevel level) {
        return Moradores.alturaCidade(level, CENTRO);
    }

    private static double dist(int dx, int dz) {
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ===================== Terreno =====================

    /** Aplaina o disco, enche por baixo, limpa o céu e fecha a borda contra água e lava de fora. */
    private static void terreno(Kit k, BlockPos c, int x0, int x1) {
        BlockState ar = b(Blocks.AIR);
        var m = new BlockPos.MutableBlockPos();
        for (int dx = x0; dx <= x1; dx++) for (int dz = -R - 12; dz <= R + 12; dz++) {
            double d = dist(dx, dz);
            if (d > R + 12) continue;
            int x = c.getX() + dx, z = c.getZ() + dz;
            boolean dentro = d <= R + 1;
            if (dentro) {
                k.s(x, c.getY() - 1, z, k.p.chao());
                for (int i = 2; i < 34; i++) {
                    m.set(x, c.getY() - i, z);
                    BlockState s = k.L.getBlockState(m);
                    if (!s.isAir() && !s.liquid() && !s.canBeReplaced()) break;
                    k.s(m, k.p.parede());
                }
                for (int i = 0; i < ALTO; i++) {
                    m.set(x, c.getY() + i, z);
                    if (!k.L.getBlockState(m).isAir()) k.s(m, ar);
                }
            } else {
                // anel de fora: terreno firme até o nível da praça e nada de líquido escorrendo para dentro
                // faixa larga (12 blocos): água de fora não atravessa (ela só corre 7 blocos num chão plano)
                for (int i = -14; i <= 40; i++) {
                    m.set(x, c.getY() + i, z);
                    BlockState s = k.L.getBlockState(m);
                    if (!s.getFluidState().isEmpty()) k.s(m, k.p.parede());
                    else if (i < 0 && (s.isAir() || s.canBeReplaced())) k.s(m, k.p.parede());
                }
                if (d <= R + 5) k.s(x, c.getY() - 1, z, k.p.chao());
            }
        }
    }

    // ===================== Chão =====================

    private static void praca(Kit k, BlockPos c) {
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            double d = dist(dx, dz);
            if (d > R) continue;
            double a = Math.atan2(dz, dx);
            // 12 raios de luz saindo da Torre
            double setor = Math.PI / 6;
            double desvio = Math.abs(Math.IEEEremainder(a, setor)) * d;
            BlockState s;
            if (d > R - 1.2) s = k.p.faixa();
            else if (d > 9 && desvio < 0.55) s = (Math.round(d) % 3 == 0) ? k.p.luz() : k.p.faixa();
            else if (Math.abs(d - 18) < 0.6 || Math.abs(d - 40) < 0.6) s = k.p.faixa();
            else if (Math.abs(d - 19.2) < 0.5 || Math.abs(d - 41.2) < 0.5) s = k.p.destaque();
            else s = ((dx * 7 + dz * 13) & 7) == 0 ? k.p.parede() : k.p.chao();
            k.s(c.offset(dx, -1, dz), s);
        }
    }

    private static void avenidas(Kit k, BlockPos c) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Direction lado = d.getClockWise();
            for (int i = 10; i <= R; i++) {
                BlockPos m = c.relative(d, i).below();
                for (int w = -3; w <= 3; w++) {
                    BlockState s = Math.abs(w) == 3 ? k.p.faixa() : w == 0 ? (i % 3 == 0 ? k.p.luz() : k.p.destaque()) : k.p.parede();
                    k.s(m.relative(lado, w), s);
                }
            }
        }
    }

    /** Mureta baixa de vidro com luz, aberta nas quatro avenidas. */
    private static void borda(Kit k, BlockPos c) {
        for (int i = 0; i < 720; i++) {
            double a = i * Math.PI * 2 / 720;
            int dx = (int) Math.round(Math.cos(a) * (R - 0.5)), dz = (int) Math.round(Math.sin(a) * (R - 0.5));
            if (Math.abs(dx) <= 4 || Math.abs(dz) <= 4) continue; // portões
            k.s(c.offset(dx, 0, dz), k.p.parede());
            k.s(c.offset(dx, 1, dz), (i % 24 == 0) ? k.p.luz() : k.p.vidro());
        }
    }

    // ===================== Torre da Luz =====================

    /** Raio da agulha central em cada altura (afina até a ponta). */
    private static double raioTorre(int h) {
        if (h < 0) return 0;
        return Math.max(1.5, 8.0 * Math.pow(Math.max(0, 1 - h / 92.0), 0.85));
    }

    private static void torreDaLuz(Kit k, BlockPos c, int h0, int h1) {
        for (int h = h0; h <= h1; h++) {
            double r = raioTorre(h);
            int ri = (int) Math.ceil(r) + 1;
            for (int dx = -ri; dx <= ri; dx++) for (int dz = -ri; dz <= ri; dz++) {
                double d = dist(dx, dz);
                BlockPos p = c.offset(dx, h, dz);
                if (d <= 0.5 && h < ALTO - 6) { k.s(p, k.p.nucleo()); continue; } // núcleo de luz por dentro
                if (d > r || d < r - 1.05) continue;
                double a = Math.atan2(dz, dx);
                boolean nervura = Math.abs(Math.IEEEremainder(a, Math.PI / 4)) * d < 0.6;
                boolean porta = h < 4 && (Math.abs(dx) <= 1 || Math.abs(dz) <= 1);
                if (porta) { k.s(p, b(Blocks.AIR)); continue; }
                if (nervura) k.s(p, k.p.pilar());
                else if (h % 9 == 0) k.s(p, k.p.luz());
                else k.s(p, k.p.vidro());
            }
            // coroa: um disco largo a 60 blocos, com borda de luz
            if (h == 60 || h == 61) {
                for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) {
                    double d = dist(dx, dz);
                    if (d > 12 || d < raioTorre(h)) continue;
                    BlockPos p = c.offset(dx, h, dz);
                    if (h == 60) k.s(p, d > 11 ? k.p.luz() : d > 9.5 ? k.p.faixa() : k.p.destaque());
                    else if (d > 11) k.s(p, k.p.vidro());
                }
            }
            // anéis flutuantes em volta da agulha
            if (h == 30 || h == 46) {
                double rr = raioTorre(h) + 4;
                for (int i = 0; i < 96; i++) {
                    double a = i * Math.PI * 2 / 96;
                    k.s(c.offset((int) Math.round(Math.cos(a) * rr), h, (int) Math.round(Math.sin(a) * rr)), i % 8 == 0 ? k.p.luz() : k.p.faixa());
                }
            }
            if (h == h1 && h1 == ALTO) {
                for (int t = 0; t < 6; t++) k.s(c.offset(0, ALTO + t, 0), t == 5 ? k.p.nucleo() : b(Blocks.END_ROD));
            }
        }
    }

    // ===================== Torres-cálice =====================

    private static final int[] ALTURAS = {44, 52, 38, 56, 42, 50, 40, 54};

    private static BlockPos centroTorre(BlockPos c, int i) {
        double a = Math.toRadians(22.5 + 45 * i);
        return c.offset((int) Math.round(Math.cos(a) * 34), 0, (int) Math.round(Math.sin(a) * 34));
    }

    private static void torreCalice(Kit k, BlockPos c, int i) {
        BlockPos t = centroTorre(c, i);
        int H = ALTURAS[i];
        // raízes: quatro apoios curvos que nascem no chão e sobem até a haste
        for (int r = 0; r < 4; r++) {
            double a = Math.toRadians(45 + 90 * r + i * 11);
            for (int h = 0; h <= 10; h++) {
                double dist = 5.5 * Math.pow(1 - h / 10.0, 1.6);
                BlockPos p = t.offset((int) Math.round(Math.cos(a) * dist), h, (int) Math.round(Math.sin(a) * dist));
                k.s(p, k.p.pilar());
                if (h % 5 == 0) k.s(p.above(), k.p.luz());
            }
        }
        // haste
        for (int h = 0; h < H - 4; h++) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 1) continue;
                boolean meio = dx == 0 && dz == 0;
                k.s(t.offset(dx, h, dz), meio ? (h % 6 == 3 ? k.p.luz() : k.p.pilar()) : (h % 6 == 3 ? k.p.luz() : k.p.parede()));
            }
        }
        // casulo de vidro no alto (elipsoide), com piso e faixa de luz no meio
        int rx = 7, ry = 5;
        for (int dx = -rx; dx <= rx; dx++) for (int dz = -rx; dz <= rx; dz++) for (int dy = -ry; dy <= ry; dy++) {
            double e = (dx * dx + dz * dz) / (double) (rx * rx) + dy * dy / (double) (ry * ry);
            double ei = (dx * dx + dz * dz) / (double) ((rx - 1) * (rx - 1)) + dy * dy / (double) ((ry - 1) * (ry - 1));
            BlockPos p = t.offset(dx, H + dy, dz);
            if (e <= 1.0 && ei > 1.0) {
                k.s(p, dy == 0 ? k.p.luz() : dy == -ry + 1 || dy == -ry ? k.p.faixa() : k.p.vidro());
            } else if (e <= 1.0 && dy == -2) {
                k.s(p, k.p.chao()); // piso
            } else if (e <= 1.0) {
                k.s(p, b(Blocks.AIR));
            }
        }
        // agulha e luz no topo
        for (int h = 1; h <= 7; h++) k.s(t.offset(0, H + ry + h - 1, 0), h == 7 ? k.p.nucleo() : h > 4 ? b(Blocks.END_ROD) : k.p.pilar());
    }

    // ===================== Pontes aéreas =====================

    private static void ponte(Kit k, BlockPos c, int i) {
        BlockPos t = centroTorre(c, i);
        int h = ALTURAS[i] - 2; // altura do piso do casulo
        double dx = c.getX() - t.getX(), dz = c.getZ() - t.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        double ux = dx / len, uz = dz / len, px = -uz, pz = ux;
        double ini = 6.5, fim = len - raioTorre(h) + 0.5;
        for (double s = ini; s <= fim; s += 0.5) {
            for (double w = -1.5; w <= 1.5; w += 0.5) {
                int x = (int) Math.round(t.getX() + ux * s + px * w), z = (int) Math.round(t.getZ() + uz * s + pz * w);
                BlockPos p = new BlockPos(x, c.getY() + h, z);
                boolean lateral = Math.abs(w) > 1.2;
                k.s(p, lateral ? k.p.faixa() : ((int) s % 4 == 0 ? k.p.luz() : k.p.destaque()));
                k.s(p.above(), lateral ? k.p.painel().defaultBlockState() : b(Blocks.AIR));
                k.s(p.above(2), b(Blocks.AIR));
                k.s(p.above(3), b(Blocks.AIR));
            }
        }
    }

    // ===================== Portais =====================

    private static void portal(Kit k, BlockPos c, Direction d) {
        BlockPos base = c.relative(d, R - 4);
        Direction lado = d.getClockWise();
        int raio = 11;
        for (int i = 0; i <= 180; i++) {
            double a = Math.toRadians(i);
            for (double rr = raio - 1; rr <= raio + 0.6; rr += 0.5) {
                int w = (int) Math.round(Math.cos(a) * rr), h = (int) Math.round(Math.sin(a) * rr * 1.5);
                BlockPos p = base.relative(lado, w).above(h);
                k.s(p, rr > raio ? k.p.faixa() : (i % 15 == 0 ? k.p.luz() : k.p.pilar()));
                k.s(p.relative(d), k.p.vidro());
            }
        }
        k.s(base.above((int) Math.round(raio * 1.5) + 2), k.p.nucleo());
    }

    // ===================== Casas-cúpula =====================

    private static BlockPos centroCasa(BlockPos c, int i) {
        double a = Math.toRadians(11.25 + 22.5 * i);
        return c.offset((int) Math.round(Math.cos(a) * 48), 0, (int) Math.round(Math.sin(a) * 48));
    }

    private static void casa(Kit k, BlockPos c, int i) {
        BlockPos h = centroCasa(c, i);
        double r = 6.2;
        int vx = Integer.signum(c.getX() - h.getX()), vz = Integer.signum(c.getZ() - h.getZ());
        Direction frente = Math.abs(c.getX() - h.getX()) > Math.abs(c.getZ() - h.getZ())
                ? (vx > 0 ? Direction.EAST : Direction.WEST) : (vz > 0 ? Direction.SOUTH : Direction.NORTH);
        for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) for (int dy = 0; dy <= 6; dy++) {
            double d = Math.sqrt(dx * dx + dz * dz + dy * dy * 1.3);
            BlockPos p = h.offset(dx, dy, dz);
            if (d <= r && d > r - 1.1) {
                boolean janela = (dy == 2 || dy == 3) && ((dx + dz) & 1) == 0;
                k.s(p, dy == 0 ? k.p.faixa() : janela ? k.p.vidro() : dy >= 5 ? k.p.teto() : dy == 4 ? k.p.luz() : k.p.parede());
            } else if (d <= r - 1.1) {
                k.s(p, b(Blocks.AIR));
            }
        }
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) if (dx * dx + dz * dz <= 36) k.s(h.offset(dx, -1, dz), k.p.chao());
        // porta virada para a Torre da Luz
        for (int s = 4; s <= 7; s++) for (int y = 0; y <= 2; y++) {
            k.s(h.relative(frente, s).above(y), b(Blocks.AIR));
            if (y == 0) k.s(h.relative(frente, s).below(), k.p.faixa());
        }
        k.s(h.relative(frente, 7).above(3), k.p.luz());
        // dentro: cama, tapete e luz no teto
        Direction costas = frente.getOpposite();
        k.cama(k.p.cama(), h.relative(costas, 1), costas);
        k.s(h.relative(frente, 1), b(k.p.tapete()));
        k.s(h.above(5), k.p.luz());
        k.s(h.relative(costas, 3).relative(frente.getClockWise(), 2), k.p.painel().defaultBlockState());
    }

    private static void jardins(Kit k, BlockPos c) {
        // agulhas finas de luz na borda: o horizonte da cidade
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(22.5 * i + 11.25);
            BlockPos p = c.offset((int) Math.round(Math.cos(a) * (R - 3)), 0, (int) Math.round(Math.sin(a) * (R - 3)));
            int alt = 18 + (i * 7) % 13;
            for (int h = 0; h < alt; h++) k.s(p.above(h), h == alt - 1 ? k.p.nucleo() : h % 5 == 4 ? k.p.luz() : k.p.pilar());
            for (int h = 0; h < 3; h++) k.s(p.above(alt + h), b(Blocks.END_ROD));
        }
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(22.5 * i);
            BlockPos p = c.offset((int) Math.round(Math.cos(a) * 25), 0, (int) Math.round(Math.sin(a) * 25));
            if (Math.abs(p.getX() - c.getX()) <= 4 || Math.abs(p.getZ() - c.getZ()) <= 4) continue;
            k.poste(p, 4);
        }
    }

    // ===================== Nativos =====================

    /** Faz nascer nativos até chegar a {@code quantos} dentro da cidade. Devolve quantos nasceram. */
    public static int nativos(ServerLevel l, BlockPos c, PlanetLore planet, int quantos) {
        Race race = Race.ofPlanet(planet.planet);
        if (race == null) return 0;
        AABB area = new AABB(c.getX() - R, l.getMinBuildHeight(), c.getZ() - R, c.getX() + R, l.getMaxBuildHeight(), c.getZ() + R);
        int tem = l.getEntitiesOfClass(RaceEntity.class, area, e -> e.isAlive() && e.race() == race && e.role().isEmpty()).size();
        int n = 0;
        for (int i = 0; tem + n < quantos && i < 32; i++) {
            BlockPos casa = centroCasa(c, (i * 5) % 16);
            BlockPos at = casa.offset(i % 2 == 0 ? 1 : -1, 0, i % 3 == 0 ? 1 : -1);
            l.getChunkAt(at); // carrega a casa (o jogador pode estar do outro lado da cidade)
            if (com.lanterna.lore.world.PlanetSites.nativo(l, race, at) != null) n++;
        }
        return n;
    }

    private CidadeNativa() {}
}
