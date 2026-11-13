package com.lanterna.cidade;

import com.lanterna.LanternaMod;
import com.lanterna.baterias.Tropa;
import com.lanterna.lore.LoreBlocks;
import com.lanterna.lore.PlanetLore;
import com.lanterna.lore.world.Arquitetura;
import com.lanterna.lore.world.CidadeTropa;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static com.lanterna.cidade.CidadesPovos.*;
import static com.lanterna.cidade.Kit.b;

/**
 * A cidade única de cada planeta das Tropas (mundos da 0.48+), gerada junto com o terreno.
 *
 *   centro (0, 0)        a cidade do povo: monumento, 8 avenidas, anéis de rua, torres, prédios e casas mobiliadas
 *                        (em Oa, o Palácio dos Guardiões no chão, no lugar do monumento)
 *   quartel (0, 130)     a cidade da Tropa: muralha, QG, academia, mercado, enfermaria, hangar e a Bateria Central no
 *                        meio, com o Guardião e o mestre do planeta em volta — é onde quem chega do espaço pousa
 *   arena (250, 0)       fora da muralha, a leste, ligada pela avenida
 *
 * Como funciona: a cidade é dividida em peças (uma casa, uma torre, um prédio do quartel...). Cada peça é desenhada
 * uma vez na memória ({@link Desenho}); quando o jogo gera um pedaço do mundo (chunk) dentro da cidade, o recurso de
 * geração ({@link Recurso}) aplaina o chão daquele pedaço e copia para ele a parte de cada peça que cai ali. Isso roda
 * nas threads de geração do Minecraft, igual às vilas: não existe mais a hora da construção que trava o servidor.
 */
public final class CidadeUnica {
    /** Altura do pódio do Palácio de Oa acima do chão da cidade. */
    public static final int PODIO = 5;
    static final BlockPos QUARTEL = Layout.CIDADE_NOVA;
    static final BlockPos ARENA = Layout.ARENA_NOVA;
    static final int ARENA_R = 27;
    /** Até onde a cidade mexe no mundo (a arena é a parte mais longe). */
    static final int ALCANCE = 290;

    /** Uma peça: retângulo (x0, z0, x1, z1) que ela pode ocupar e a obra que a desenha. */
    record Peca(String id, int x0, int z0, int x1, int z1, Consumer<Kit> obra) {
        boolean toca(int cx, int cz) {
            return (cx << 4) + 15 >= x0 && (cx << 4) <= x1 && (cz << 4) + 15 >= z0 && (cz << 4) <= z1;
        }
    }

    /** A planta da cidade de um planeta. */
    public static final class Planta {
        public final PlanetLore planeta;
        public final int y;
        final BlockPos c;
        final Estilo e;
        final Paleta pal;
        final List<Peca> pecas = new ArrayList<>();
        /** Onde ficam as casas (os nativos moram nelas). */
        public final List<BlockPos> casas = new ArrayList<>();
        final String chave;

        Planta(PlanetLore p, int y, String chave) {
            this.planeta = p;
            this.y = y;
            this.c = new BlockPos(0, y, 0);
            this.e = CidadesPovos.estilo(p);
            this.pal = Paleta.of(p);
            this.chave = chave;
        }
    }

    private static final Map<String, Planta> PLANTAS = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<Desenho>> DESENHOS = new ConcurrentHashMap<>();
    private static final Map<String, Set<Long>> FALTAM = new ConcurrentHashMap<>();

    public static void esquecer() {
        PLANTAS.clear();
        DESENHOS.clear();
        FALTAM.clear();
    }

    // ===================== Planta =====================

    public static Planta planta(ServerLevel l, PlanetLore p) {
        String chave = l.dimension().location() + "|" + l.getSeed();
        return PLANTAS.computeIfAbsent(chave, k -> montar(l, p, k));
    }

    static boolean noQuartel(int x, int z) {
        return Math.abs(x - QUARTEL.getX()) <= 70 && z >= QUARTEL.getZ() - 72 && z <= QUARTEL.getZ() + 72;
    }

    static boolean naArena(int x, int z) {
        double dx = x - ARENA.getX(), dz = z - ARENA.getZ();
        return dx * dx + dz * dz <= (ARENA_R + 0.5) * (ARENA_R + 0.5);
    }

    static boolean naEstrada(int x, int z) {
        return x >= R - 6 && x <= ARENA.getX() - ARENA_R + 2 && Math.abs(z) <= 4;
    }

    /** Lotes que a cidade do povo não usa (quartel, palácio e campo de provas de Oa). */
    static boolean reservado(PlanetLore p, int x, int z, int folga) {
        if (Math.abs(x - QUARTEL.getX()) <= 70 + folga && z >= QUARTEL.getZ() - 72 - folga && z <= QUARTEL.getZ() + 72 + folga) return true;
        if (p == PlanetLore.OA) {
            if (x * x + z * z <= (Palacio.R + 10 + folga) * (Palacio.R + 10 + folga)) return true;
            double dx = x + 84, dz = z;
            if (dx * dx + dz * dz <= (30 + folga) * (30 + folga)) return true;
        }
        return false;
    }

    private static Planta montar(ServerLevel l, PlanetLore p, String chave) {
        // chão da cidade pela média do terreno (a mesma conta da cidade antiga, mas com o gerador)
        var src = l.getChunkSource();
        long fundo = 0, sup = 0;
        int n = 0;
        for (int dx = -R; dx <= R; dx += 40) for (int dz = -R; dz <= R; dz += 40) {
            if (dx * dx + dz * dz > R * R) continue;
            fundo += src.getGenerator().getBaseHeight(dx, dz, Heightmap.Types.OCEAN_FLOOR_WG, l, src.randomState());
            sup += src.getGenerator().getBaseHeight(dx, dz, Heightmap.Types.WORLD_SURFACE_WG, l, src.randomState());
            n++;
        }
        int y = CidadesPovos.chaoDe(l, p, (int) (fundo / n), (int) (sup / n));
        Planta pl = new Planta(p, y, chave);
        BlockPos c = pl.c;
        Estilo e = pl.e;

        // ---- o centro: monumento do povo (Oa: o Palácio) ----
        if (p == PlanetLore.OA) palacio(pl);
        else pl.pecas.add(new Peca("marco", -78, -78, 78, 78, k -> e.marco(k.L, k, c)));

        // ---- torres, prédios e casas do povo ----
        for (int i = 0; i < 8; i++) {
            BlockPos t = torre(c, i);
            if (reservado(p, t.getX(), t.getZ(), 26)) continue;
            int f = i;
            pl.pecas.add(peca("torre" + i, t, 32, k -> e.torre(k, t, f, c)));
        }
        List<BlockPos> pr = predios(c);
        for (int i = 0; i < pr.size(); i++) {
            BlockPos q = pr.get(i);
            if (reservado(p, q.getX(), q.getZ(), 16)) continue;
            int f = i;
            pl.pecas.add(peca("predio" + i, q, 24, k -> e.predio(k, q, paraOCentro(c, q), f)));
        }
        List<BlockPos> cs = new ArrayList<>(casas(c));
        // anel novo de casas na borda (a cidade ficou maior)
        for (int i = 0; i < 62; i++) {
            BlockPos q = noCirculo(c, 205, 360.0 * (i + 0.5) / 62 + 2);
            if (!pertoDaAvenida(c, q, 10)) cs.add(q);
        }
        cs.removeIf(q -> reservado(p, q.getX(), q.getZ(), 9));
        cs.addAll(lotesExtras(pl, cs, pr));
        for (int i = 0; i < cs.size(); i++) {
            BlockPos q = cs.get(i);
            int f = i;
            pl.casas.add(q);
            Direction frente = paraOCentro(c, q);
            pl.pecas.add(peca("casa" + i, q, 15, k -> {
                e.casa(k, q, frente, f);
                Mobilia.mobiliar(k, q, 9, p, q.asLong() ^ 0x5EEDL);
            }));
        }
        // ---- postes ----
        for (int a = 0; a < 8; a++) {
            int fa = a;
            double ang = Math.toRadians(45 * a);
            int ex = (int) Math.round(Math.cos(ang) * R), ez = (int) Math.round(Math.sin(ang) * R);
            pl.pecas.add(new Peca("postes" + a, Math.min(0, ex) - 10, Math.min(0, ez) - 10, Math.max(0, ex) + 10, Math.max(0, ez) + 10, k -> {
                int idx = fa * 100;
                for (int s = PRACA + 8; s < R - 10; s += 14) for (int lado : new int[]{-1, 1}) {
                    double px = Math.cos(ang) * s - Math.sin(ang) * 6 * lado, pz = Math.sin(ang) * s + Math.cos(ang) * 6 * lado;
                    BlockPos q = c.offset((int) Math.round(px), 0, (int) Math.round(pz));
                    if (!reservado(p, q.getX(), q.getZ(), 2)) e.poste(k, q, idx++);
                }
            }));
        }
        int[] aneis = {ANEIS[0], ANEIS[1], ANEIS[2], 196};
        for (int r : aneis) {
            pl.pecas.add(new Peca("postes_anel" + r, -r - 10, -r - 10, r + 10, r + 10, k -> {
                int idx = r * 7;
                for (int j = 0; j < r / 6; j++) {
                    BlockPos q = noCirculo(c, r + 4, 360.0 * j / (r / 6) + 3);
                    if (!pertoDaAvenida(c, q, 7) && !reservado(p, q.getX(), q.getZ(), 3)) e.poste(k, q, idx++);
                }
            }));
        }
        // ---- muralha e portões ----
        int nMuro = (int) Math.ceil(2 * Math.PI * R * 2);
        for (int s = 0; s < 32; s++) {
            int f = s;
            double a0 = 360.0 * f / 32, a1 = 360.0 * (f + 1) / 32;
            int[] caixa = caixaArco(R, a0, a1);
            pl.pecas.add(new Peca("muro" + s, caixa[0] - 8, caixa[1] - 8, caixa[2] + 8, caixa[3] + 8, k -> {
                for (int j = f * nMuro / 32; j < (f + 1) * nMuro / 32; j++) {
                    double ang = 360.0 * j / nMuro;
                    for (double rr = R - 4; rr <= R; rr += 0.5) {
                        BlockPos q = noCirculo(c, rr, ang);
                        if (pertoDaAvenida(c, q, 6)) continue;
                        e.muro(k, q, j, rr >= R - 0.6);
                    }
                }
            }));
        }
        for (int i = 0; i < 8; i++) {
            double ang = 45 * i;
            BlockPos g = noCirculo(c, R - 2, ang);
            pl.pecas.add(peca("portao" + i, g, 22, k -> e.portao(k, g, radial(ang))));
        }
        // ---- o quartel da Tropa ----
        quartel(pl);
        // ---- a arena ----
        BlockPos ac = ARENA.atY(y);
        pl.pecas.add(peca("arena", ac, ARENA_R + 6, k -> arena(k, ac, p)));
        LanternaMod.LOGGER.info("Lanterna: planta da cidade de {}: chão {}, {} peças, {} casas", p.planet, y, pl.pecas.size(), pl.casas.size());
        return pl;
    }

    private static Peca peca(String id, BlockPos c, int r, Consumer<Kit> obra) {
        return new Peca(id, c.getX() - r, c.getZ() - r, c.getX() + r, c.getZ() + r, obra);
    }

    private static int[] caixaArco(double r, double a0, double a1) {
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (double a = a0; a <= a1 + 0.01; a += (a1 - a0) / 8) {
            int x = (int) Math.round(Math.cos(Math.toRadians(a)) * r), z = (int) Math.round(Math.sin(Math.toRadians(a)) * r);
            x0 = Math.min(x0, x); z0 = Math.min(z0, z); x1 = Math.max(x1, x); z1 = Math.max(z1, z);
        }
        return new int[]{x0, z0, x1, z1};
    }

    /** Lotes vazios no meio dos bairros (o "adensamento" da 0.28, agora na planta). */
    private static List<BlockPos> lotesExtras(Planta pl, List<BlockPos> casas, List<BlockPos> predios) {
        List<BlockPos> cand = new ArrayList<>();
        for (int x = -R; x <= R; x += 8) for (int z = -R; z <= R; z += 8) {
            double d = dist(x, z);
            if (d < PRACA + 12 || d > R - 14 || tipo(x, z) != 0 || distAvenida(x, z) < 11) continue;
            boolean ok = true;
            for (int r : ANEIS) if (Math.abs(d - r) < 10) ok = false;
            if (Math.abs(d - 196) < 8) ok = false;
            if (!ok || reservado(pl.planeta, x, z, 10)) continue;
            BlockPos q = new BlockPos(x, pl.y, z);
            if (pertoDeTorre(pl.c, q, 30)) continue;
            cand.add(q);
        }
        cand.sort(Comparator.comparingLong(q -> net.minecraft.util.Mth.getSeed(q.getX(), 0, q.getZ())));
        List<BlockPos> novas = new ArrayList<>();
        for (BlockPos q : cand) {
            if (novas.size() >= CASAS_NOVAS) break;
            boolean ok = true;
            for (BlockPos o : casas) if (o.distSqr(q) < 16 * 16) { ok = false; break; }
            if (ok) for (BlockPos o : predios) if (o.distSqr(q) < 22 * 22) { ok = false; break; }
            if (ok) for (BlockPos o : novas) if (o.distSqr(q) < 16 * 16) { ok = false; break; }
            if (ok) novas.add(q);
        }
        return novas;
    }

    // ===================== Quartel da Tropa =====================

    private static void quartel(Planta pl) {
        BlockPos q = QUARTEL.atY(pl.y);
        PlanetLore p = pl.planeta;
        int x0 = q.getX() - 72, z0 = q.getZ() - 72, x1 = q.getX() + 72, z1 = q.getZ() + 72;
        List<Consumer<Kit>> obras = new ArrayList<>();
        obras.add(k -> { Cidade.avenidas(k, q); Cidade.praca(k, q); });
        obras.add(k -> {
            Tropa t = Tropa.doPlaneta(p);
            if (t != null) {
                // a Bateria Central de verdade (o molde do pacote das Baterias), com o portal para a Fonte
                int[] o = com.lanterna.baterias.BateriaCentralItem.origem(t);
                BlockPos nucleo = q.below(), canto = nucleo.offset(-o[0], -o[1], -o[2]);
                for (int dx = -9; dx <= 9; dx++) for (int dz = -9; dz <= 9; dz++) for (int h = 0; h < 4; h++) k.s(q.offset(dx, h, dz), b(Blocks.AIR));
                k.m.molde(new ResourceLocation(LanternaMod.MODID, "bateria_central_" + t.nome), canto);
            } else CidadeTropa.bateriaCentral(k.m, q, p);
        });
        obras.add(k -> Cidade.muralha(k, q));
        obras.add(k -> Cidade.qgCasca(k, q));
        obras.add(k -> Cidade.qgInterior(k, q, p));
        obras.add(k -> Cidade.arquivo(k, q));
        obras.add(k -> Cidade.celas(k, q));
        obras.add(k -> Cidade.academia(k, q));
        obras.add(k -> Cidade.mercado(k, q));
        obras.add(k -> Cidade.enfermaria(k, q));
        obras.add(k -> Cidade.hangar(k, q));
        int[][] casas = {{-48, 34, 0}, {-34, 34, 0}, {-20, 34, 0}, {-48, 50, 1}, {-34, 50, 1}, {-20, 50, 1}};
        for (int[] cs : casas) obras.add(k -> {
            BlockPos h = q.offset(cs[0], 0, cs[1]);
            Cidade.casa(k, h, cs[2] == 0 ? Direction.SOUTH : Direction.NORTH);
            Mobilia.mobiliar(k, h, 7, p, h.asLong() ^ 0x7A11L);
        });
        obras.add(k -> Cidade.aneis(k, q));
        for (int i = 0; i < obras.size(); i++) {
            Consumer<Kit> o = obras.get(i);
            pl.pecas.add(new Peca("quartel" + i, x0, z0, x1, z1, k -> {
                Kit kq = new Kit(k.m, pl.pal);
                o.accept(kq);
            }));
        }
    }

    // ===================== Palácio de Oa =====================

    private static void palacio(Planta pl) {
        int y = pl.y + PODIO;
        BlockPos c = new BlockPos(0, y, 0);
        Palacio.posicionar(c, true);
        int r = Palacio.R + 34;
        List<Consumer<Kit>> obras = new ArrayList<>();
        obras.add(k -> podio(k, c, Palacio.R + 2, pl.y));
        obras.add(k -> podio(k, Palacio.CAMPO, 24, pl.y));
        obras.add(Palacio::portaoEPatio);
        obras.add(Palacio::salao);
        obras.add(Palacio::camara);
        for (String t : CidadeMissoes.TECNICAS) obras.add(k -> Palacio.alaCapitao(k, t));
        obras.add(Palacio::biblioteca);
        obras.add(Palacio::observatorio);
        obras.add(Palacio::celas);
        obras.add(Palacio::campo);
        obras.add(Palacio::aneis);
        obras.add(k -> torreDaLuz(k, Palacio.SALAO));
        Paleta pal = Palacio.paleta();
        for (int i = 0; i < obras.size(); i++) {
            Consumer<Kit> o = obras.get(i);
            pl.pecas.add(new Peca("palacio" + i, -r - 60, -r, r, r + 10, k -> o.accept(new Kit(k.m, pal))));
        }
    }

    /** Pódio de pedra (no lugar da ilha flutuante): degraus em volta e escadaria larga ao sul. */
    static void podio(Kit k, BlockPos c, int raio, int chao) {
        BlockState rocha = b(LoreBlocks.ROCHA_POLIDA_VERIDIA.get());
        int altura = c.getY() - chao;
        for (int dx = -raio - altura; dx <= raio + altura; dx++) for (int dz = -raio - altura; dz <= raio + altura + 10; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            int x = c.getX() + dx, z = c.getZ() + dz;
            // degraus em volta: cada degrau desce 1 a cada bloco de distância além do raio
            int topo = d <= raio + 0.5 ? altura : altura - (int) Math.ceil(d - raio - 0.5);
            boolean escadaria = Math.abs(dx) <= 5 && dz > raio - 1 && dz <= raio + altura + 10;
            if (escadaria) topo = Math.max(topo, altura - (int) Math.floor((dz - raio + 1) / 2.0));
            if (topo <= 0) continue;
            for (int h = 0; h < topo; h++) {
                BlockState s = h == topo - 1 && d > raio + 0.5 ? k.p.faixa() : (h % 3 == 1 ? k.p.parede() : rocha);
                k.s(x, chao + h, z, s);
            }
            if (d <= raio + 0.5) k.s(x, c.getY() - 1, z, d > raio - 1 ? k.p.faixa()
                    : ((Math.floorMod(dx, 6) == 0 || Math.floorMod(dz, 6) == 0) ? k.p.parede() : rocha));
        }
        // luzes nos degraus
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            int x = c.getX() + (int) Math.round(Math.cos(a) * (raio + 1)), z = c.getZ() + (int) Math.round(Math.sin(a) * (raio + 1));
            k.s(x, c.getY() - 1, z, k.p.luz());
        }
    }

    /** A Torre da Luz (o monumento de Oa) agora sobe do meio do Grande Salão do Palácio. */
    static void torreDaLuz(Kit k, BlockPos salao) {
        BlockPos base = salao.above(16);
        for (int h = 0; h < 90; h++) {
            double r = h < 10 ? 5 : Math.max(1.4, 5 - (h - 10) * 0.045);
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) for (int dz = -ri; dz <= ri; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r) continue;
                BlockPos q = base.offset(dx, h, dz);
                if (d > r - 1.1) k.s(q, h % 12 == 11 ? k.p.destaque() : (((dx + dz + h) & 3) == 0 ? k.p.vidro() : k.p.parede()));
                else k.s(q, h % 12 == 11 ? k.p.luz() : b(Blocks.AIR));
            }
        }
        for (int h = 90; h < 100; h++) k.s(base.offset(0, h, 0), h % 2 == 0 ? k.p.luz() : b(Blocks.END_ROD));
    }

    // ===================== Arena =====================

    /** A arena do desafio (a mesma arquitetura da 0.47), fora da muralha, a leste. */
    static void arena(Kit k, BlockPos c, PlanetLore planet) {
        Mundo l = k.m;
        Arquitetura.Estilo e = Arquitetura.estilo(planet.corps);
        int y = c.getY();
        Arquitetura.piso(l, c, y, 19, e);
        BlockPos altar = c;
        if (planet == PlanetLore.ODYM) {
            for (int dx = -13; dx <= 13; dx++) for (int dz = -13; dz <= 13; dz++) {
                if (dx * dx + dz * dz > 13 * 13) continue;
                for (int h = 1; h <= 7; h++) k.s(c.offset(dx, -h, dz), b(Blocks.WATER));
                k.s(c.offset(dx, -8, dz), b(LoreBlocks.AREIA_ABISSAL_LUMERIS.get()));
            }
            altar = c.offset(0, 0, 15);
        } else {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 2) k.s(c.offset(dx, 0, dz), Arquitetura.laje(e, false));
            for (int[] d : new int[][]{{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) Arquitetura.obelisco(l, c.offset(d[0], 0, d[1]), 4, e);
            for (int[] d : new int[][]{{9, 0}, {0, 9}, {0, -9}}) Arquitetura.braseiro(l, c.offset(d[0], 0, d[1]), e);
        }
        BlockPos[] topos = new BlockPos[8];
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            int bx = c.getX() + (int) Math.round(Math.cos(a) * 17), bz = c.getZ() + (int) Math.round(Math.sin(a) * 17);
            int topo = Arquitetura.coluna(l, new BlockPos(bx, y, bz), 9, e);
            topos[i] = new BlockPos(bx, topo, bz);
            Direction dentro = Arquitetura.rumo(c.getX() - bx, c.getZ() - bz);
            Arquitetura.estandarte(l, new BlockPos(bx, y + 6, bz).relative(dentro), dentro, e.cor());
        }
        for (int i = 0; i < 8; i++) Arquitetura.arco(l, topos[i], topos[(i + 1) % 8], 3, e);
        Arquitetura.arquibancada(l, c, y, 21, 3, e);
        k.s(altar, b(LoreBlocks.ALTAR_DESAFIO.get()));
    }

    // ===================== Geração =====================

    /** O recurso de geração: roda em cada pedaço do mundo dos planetas das Tropas. */
    public static final class Recurso extends Feature<NoneFeatureConfiguration> {
        public Recurso(Codec<NoneFeatureConfiguration> codec) {
            super(codec);
        }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
            if (!Layout.novo()) return false;
            WorldGenLevel level = ctx.level();
            ServerLevel sl = level.getLevel();
            PlanetLore p = PlanetLore.byDimension(sl.dimension());
            if (p == null || p == PlanetLore.GENESE) return false;
            int cx = ctx.origin().getX() >> 4, cz = ctx.origin().getZ() >> 4;
            int mx = Math.max(Math.abs((cx << 4) + 8) - 8, 0), mz = Math.max(Math.abs((cz << 4) + 8) - 8, 0);
            if (mx * mx + mz * mz > ALCANCE * ALCANCE) return false;
            try {
                gerar(level, planta(sl, p), cx, cz, ctx.random());
            } catch (Exception ex) {
                LanternaMod.LOGGER.error("Lanterna: falha gerando a cidade em {} {}", cx, cz, ex);
            }
            return true;
        }
    }

    /**
     * Limpeza depois da vegetação: as árvores do planeta que nasceram nos gramados da cidade (folhas naturais e o
     * tronco delas) saem. Folhas da cidade (presas) e madeira dos prédios ficam.
     */
    public static final class Limpeza extends Feature<NoneFeatureConfiguration> {
        public Limpeza(Codec<NoneFeatureConfiguration> codec) {
            super(codec);
        }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
            if (!Layout.novo()) return false;
            WorldGenLevel level = ctx.level();
            PlanetLore p = PlanetLore.byDimension(level.getLevel().dimension());
            if (p == null || p == PlanetLore.GENESE) return false;
            int cx = ctx.origin().getX() >> 4, cz = ctx.origin().getZ() >> 4;
            Planta pl = planta(level.getLevel(), p);
            var m = new BlockPos.MutableBlockPos();
            BlockState ar = b(Blocks.AIR);
            for (int x = cx << 4; x < (cx << 4) + 16; x++) for (int z = cz << 4; z < (cz << 4) + 16; z++) {
                if (!(dist(x, z) <= R + 1 || naArena(x, z) || naEstrada(x, z))) continue;
                boolean arvore = false;
                for (int y = pl.y + 40; y >= pl.y; y--) {
                    m.set(x, y, z);
                    BlockState s = level.getBlockState(m);
                    if (s.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock && !s.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)) {
                        level.setBlock(m, ar, 2);
                        arvore = true;
                    } else if (arvore && s.is(net.minecraft.tags.BlockTags.LOGS)) level.setBlock(m, ar, 2);
                    else if (s.is(Blocks.VINE) || s.is(Blocks.BEE_NEST)) level.setBlock(m, ar, 2);
                }
            }
            return true;
        }
    }

    static void gerar(WorldGenLevel level, Planta pl, int cx, int cz, RandomSource rnd) {
        Mundo.Geracao g = new Mundo.Geracao(level, cx, cz, rnd);
        terreno(new Kit(g, pl.pal), pl, cx, cz);
        for (Peca p : pl.pecas) {
            if (!p.toca(cx, cz)) continue;
            Desenho d = desenho(level.getLevel(), pl, p);
            if (d == null || !d.toca(cx, cz)) continue;
            d.copiar(level, cx, cz, rnd);
            Set<Long> falta = FALTAM.get(pl.chave + "|" + p.id());
            if (falta != null) {
                falta.remove(net.minecraft.world.level.ChunkPos.asLong(cx, cz));
                if (falta.isEmpty()) { DESENHOS.remove(pl.chave + "|" + p.id()); FALTAM.remove(pl.chave + "|" + p.id()); }
            }
        }
    }

    /** O desenho da peça (feito uma vez e guardado até todos os pedaços dela serem gerados). */
    static Desenho desenho(ServerLevel l, Planta pl, Peca p) {
        String chave = pl.chave + "|" + p.id();
        CompletableFuture<Desenho> nova = new CompletableFuture<>();
        CompletableFuture<Desenho> f = DESENHOS.putIfAbsent(chave, nova);
        if (f != null) return f.join();
        try {
            Desenho d = new Desenho();
            Mundo.Virtual v = new Mundo.Virtual(l, d, (x, z) -> chao(l, pl, x, z));
            Kit k = new Kit(v, pl.pal);
            Obra.imediato(l, () -> p.obra().accept(k));
            Set<Long> pedacos = ConcurrentHashMap.newKeySet();
            for (int x = p.x0() >> 4; x <= p.x1() >> 4; x++) for (int z = p.z0() >> 4; z <= p.z1() >> 4; z++)
                if (d.toca(x, z)) pedacos.add(net.minecraft.world.level.ChunkPos.asLong(x, z));
            FALTAM.put(chave, pedacos);
            nova.complete(d);
            return d;
        } catch (Throwable t) {
            LanternaMod.LOGGER.error("Lanterna: falha desenhando {}", p.id(), t);
            nova.complete(null);
            return null;
        }
    }

    /** O chão onde a cidade está (dentro dela, plano; fora, o do terreno). */
    static int chao(ServerLevel l, Planta pl, int x, int z) {
        if (x * x + z * z <= (R + 1) * (R + 1) || naArena(x, z) || naEstrada(x, z)) return pl.y;
        return Mundo.alturaGerador(l, x, z);
    }

    /** Aplaina o pedaço: limpa em cima do chão da cidade, põe o piso (ruas, praças, bairros) e enche embaixo. */
    static void terreno(Kit k, Planta pl, int cx, int cz) {
        Estilo e = pl.e;
        int y = pl.y;
        BlockState ar = b(Blocks.AIR), parede = k.p.parede();
        var m = new BlockPos.MutableBlockPos();
        for (int x = cx << 4; x < (cx << 4) + 16; x++) for (int z = cz << 4; z < (cz << 4) + 16; z++) {
            double d = dist(x, z);
            boolean disco = d <= R + 1, arena = naArena(x, z), estrada = naEstrada(x, z), quartel = noQuartel(x, z);
            int topo = k.altura(x, z);
            if (disco || arena || estrada) {
                for (int h = y; h < Math.max(topo, y + 1) + 2; h++) { m.set(x, h, z); if (!k.get(m).isAir()) k.s(m, ar); }
                BlockState s;
                boolean aterro = e.aterro() || quartel || arena || estrada;
                if (quartel) s = k.p.chao();
                else if (arena || estrada) s = e.piso(x, z, d, 0, 1);
                else s = e.piso(x, z, d, Math.atan2(z, x), d > R - 5 ? 1 : tipo(x, z));
                if (s == null && aterro) s = k.p.chao();
                if (s != null) {
                    k.s(x, y - 1, z, s);
                    if (aterro) for (int i = 2; i < 60; i++) {
                        m.set(x, y - i, z);
                        BlockState q = k.get(m);
                        if (!q.isAir() && !q.liquid() && !q.canBeReplaced()) break;
                        k.s(m, parede);
                    }
                    int t = tipo(x, z);
                    if (!quartel && !arena && !estrada && t == 0 && d > PRACA + 2 && d < R - 6 && !reservado(pl.planeta, x, z, 0))
                        e.enfeite(k, new BlockPos(x, y, z), x, z, t);
                }
            } else if (d <= R + 12 && e.aterro()) {
                for (int i = -16; i <= 40; i++) {
                    m.set(x, y + i, z);
                    BlockState s = k.get(m);
                    if (!s.getFluidState().isEmpty()) k.s(m, parede);
                    else if (i < 0 && (s.isAir() || s.canBeReplaced())) k.s(m, parede);
                }
                if (d <= R + 5) k.s(x, y - 1, z, k.p.chao());
            }
        }
    }

    /** Teste: desenha a casa i sozinha e diz quantos blocos e móveis saíram. */
    public static String testeCasa(ServerLevel l, PlanetLore p, int i) {
        Planta pl = planta(l, p);
        Peca pc = null;
        for (Peca x : pl.pecas) if (x.id().equals("casa" + i)) pc = x;
        if (pc == null) return "sem casa " + i;
        Desenho d = new Desenho();
        Kit k = new Kit(new Mundo.Virtual(l, d, (x, z) -> chao(l, pl, x, z)), pl.pal);
        Mobilia.DEBUG = true;
        final Peca alvo = pc;
        Obra.imediato(l, () -> alvo.obra().accept(k));
        BlockPos h = pl.casas.get(i).atY(pl.y);
        int baus = 0, outros = 0;
        for (BlockPos q : BlockPos.betweenClosed(h.offset(-10, -1, -10), h.offset(10, 14, 10))) {
            BlockState s = d.get(q);
            if (s == null) continue;
            if (s.is(Blocks.CHEST) || s.is(Blocks.BARREL)) baus++;
            if (s.is(Blocks.CRAFTING_TABLE) || s.is(Blocks.FURNACE) || s.is(Blocks.SMOKER)) outros++;
        }
        return "casa " + i + " blocos=" + d.blocos() + " baus=" + baus + " bancadas=" + outros;
    }

    private static Peca pc0(Peca p) { return p; }

    private CidadeUnica() {}
}
