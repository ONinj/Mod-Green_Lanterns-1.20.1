package com.lanterna.cidade;

import com.lanterna.LanternaMod;
import com.lanterna.lore.LoreBlocks;
import com.lanterna.lore.PlanetLore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.lanterna.cidade.Kit.b;

/**
 * As megacidades dos povos. Todas usam o mesmo traçado (um disco de 400 blocos), mas cada povo constrói com a sua
 * arquitetura e a sua cultura:
 *
 *   praça (raio 40)    o monumento do povo, de 125 a 200 blocos de altura
 *   8 avenidas largas  saindo do centro, com postes/barracas/braseiros do povo
 *   3 anéis de ruas    (raios 70, 130 e 176)
 *   8 marcos           no raio 100, entre as avenidas
 *   ~40 prédios        no raio 148 (salões, mercados, quartéis, templos)
 *   ~150 casas         em quatro bairros (raios 52, 115, 162 e 188)
 *   muralha e 8 portões na borda
 *
 *   Oa       · Veltari     · Cidade de Luz: Torre da Luz de 200 blocos, torres-cálice com pontes, casas-cúpula
 *   Korugar  · Skrell'Vath · A Colmeia do Medo: colmeia negra de 190 blocos, agulhas de espinhos, casulos
 *   Ysmault  · Brakkari    · A Fortaleza de Gorrak: coliseu gigante com 4 torres de chifres, quartéis, bunkers
 *   Odym     · Aelínos     · A Cidade das Marés: sobre a água, Farol de Pérola de 180 blocos, corais, conchas
 *   Okaara   · Ghallos     · O Mercado dos Cofres: zigurate do tesouro com obelisco de ouro, cofres, mercados
 *   Nok      · Ves'Kai     · O Santuário das Raízes: Árvore-Mãe de 150 blocos cobrindo a praça, templos
 *   Zamaron  · Zaëlith     · O Jardim de Cristal: Coração de Cristal de 100 blocos entre agulhas gêmeas
 *   Ryut     · Silentes    · A Necrópole: o Colosso de ossos e o Obelisco do Silêncio, mausoléus
 */
public final class CidadesPovos {
    public static final int R = 200;
    static final int PRACA = 40;
    static final int[] ANEIS = {70, 130, 176};
    public static final int NATIVOS = 40;

    // ===================== Traçado =====================

    /** Onde ficam as casas (os nativos nascem nelas). */
    public static List<BlockPos> casas(BlockPos c) {
        List<BlockPos> out = new ArrayList<>();
        int[][] aneis = {{52, 14}, {115, 34}, {162, 50}, {188, 58}};
        for (int[] a : aneis) {
            for (int i = 0; i < a[1]; i++) {
                double ang = 360.0 * (i + 0.5) / a[1] + (a[0] % 7);
                BlockPos p = noCirculo(c, a[0], ang);
                if (pertoDaAvenida(c, p, 10)) continue;
                if (a[0] == 115 && pertoDeTorre(c, p, 26)) continue;
                out.add(p);
            }
        }
        return out;
    }

    static List<BlockPos> predios(BlockPos c) {
        List<BlockPos> out = new ArrayList<>();
        int n = 44;
        for (int i = 0; i < n; i++) {
            BlockPos p = noCirculo(c, 148, 360.0 * (i + 0.5) / n);
            if (pertoDaAvenida(c, p, 13)) continue;
            out.add(p);
        }
        return out;
    }

    static BlockPos torre(BlockPos c, int i) {
        return noCirculo(c, 100, 22.5 + 45 * i);
    }

    private static boolean pertoDeTorre(BlockPos c, BlockPos p, double raio) {
        for (int i = 0; i < 8; i++) if (torre(c, i).distSqr(p) < raio * raio) return true;
        return false;
    }

    /** Distância do ponto até a avenida mais próxima (8 avenidas, de 45 em 45 graus). */
    static double distAvenida(int dx, int dz) {
        double d = dist(dx, dz), a = Math.atan2(dz, dx);
        return Math.abs(Math.sin(a - Math.round(a / (Math.PI / 4)) * (Math.PI / 4))) * d;
    }

    private static boolean pertoDaAvenida(BlockPos c, BlockPos p, double w) {
        return distAvenida(p.getX() - c.getX(), p.getZ() - c.getZ()) < w;
    }

    /** 0 bairro, 1 avenida, 2 praça, 3 anel de rua. */
    private static int tipo(int dx, int dz) {
        double d = dist(dx, dz);
        if (d <= PRACA) return 2;
        if (distAvenida(dx, dz) < 4.5) return 1;
        for (int r : ANEIS) if (Math.abs(d - r) < 3) return 3;
        return 0;
    }

    // ===================== O que cada povo constrói =====================

    interface Estilo {
        /** Chão; null = não mexe (Odym: a água fica). */
        BlockState piso(int dx, int dz, double d, double a, int tipo);
        /** Enfeites em cima do chão do bairro (flores, musgo...). */
        default void enfeite(Kit k, BlockPos p, int dx, int dz, int tipo) {}
        /** O monumento (enfileira as próprias etapas). */
        void marco(ServerLevel level, Kit k, BlockPos c);
        void torre(Kit k, BlockPos t, int i, BlockPos c);
        void predio(Kit k, BlockPos p, Direction frente, int i);
        void casa(Kit k, BlockPos h, Direction frente, int i);
        void portao(Kit k, BlockPos g, Direction d);
        /** Uma coluna da muralha; {@code fora} = a face externa. */
        void muro(Kit k, BlockPos p, int i, boolean fora);
        void poste(Kit k, BlockPos p, int i);
        default boolean aterro() { return true; }
        default int pisoAltura(ServerLevel l, int fundo, int superficie) { return fundo; }
    }

    private static Estilo estilo(PlanetLore p) {
        return switch (p) {
            case KORUGAR -> new Korugar();
            case YSMAULT -> new Ysmault();
            case ODYM -> new Odym();
            case OKAARA -> new Okaara();
            case NOK -> new Nok();
            case ZAMARON -> new Zamaron();
            case RYUT -> new Ryut();
            default -> new Oa();
        };
    }

    // ===================== Obra =====================

    /** O chão da cidade a partir da média do terreno. */
    public static int chaoDe(ServerLevel level, PlanetLore planet, int fundo, int superficie) {
        int y = estilo(planet).pisoAltura(level, fundo, superficie);
        return Math.max(level.getMinBuildHeight() + 20, Math.min(y, level.getMaxBuildHeight() - 225));
    }

    public static void construir(ServerLevel level, PlanetLore planet, BlockPos c) {
        Kit k = new Kit(level, Paleta.of(planet));
        Estilo e = estilo(planet);
        // terreno, um pedaço do mundo (chunk) por etapa, em lotes já carregados (o servidor não trava gerando o mundo)
        int cx0 = (c.getX() - R - 12) >> 4, cx1 = (c.getX() + R + 12) >> 4, cz0 = (c.getZ() - R - 12) >> 4, cz1 = (c.getZ() + R + 12) >> 4;
        List<net.minecraft.world.level.ChunkPos> lote = new ArrayList<>();
        List<Consumer<ServerLevel>> etapas = new ArrayList<>();
        for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) {
            int fx = cx, fz = cz;
            int mx = Math.max(Math.abs((fx << 4) + 8 - c.getX()) - 12, 0), mz = Math.max(Math.abs((fz << 4) + 8 - c.getZ()) - 12, 0);
            if (mx * mx + mz * mz > (R + 13) * (R + 13)) continue;
            lote.add(new net.minecraft.world.level.ChunkPos(fx, fz));
            etapas.add(l -> terreno(l, k, e, c, fx, fz));
            if (lote.size() == 48 || (cx == cx1 && cz == cz1)) {
                Obra.carregar(level, new ArrayList<>(lote));
                for (var et : etapas) Obra.etapa(level, et);
                lote.clear(); etapas.clear();
            }
        }
        if (!lote.isEmpty()) { Obra.carregar(level, new ArrayList<>(lote)); for (var et : etapas) Obra.etapa(level, et); }
        // muralha e portões
        int nMuro = (int) Math.ceil(2 * Math.PI * R * 2);
        for (int s = 0; s < 32; s++) {
            int f = s;
            Obra.etapa(level, l -> {
                for (int j = f * nMuro / 32; j < (f + 1) * nMuro / 32; j++) {
                    double ang = 360.0 * j / nMuro;
                    for (double rr = R - 4; rr <= R; rr += 0.5) {
                        BlockPos p = noCirculo(c, rr, ang);
                        if (pertoDaAvenida(c, p, 6)) continue;
                        e.muro(k, p, j, rr >= R - 0.6);
                    }
                }
            });
        }
        for (int i = 0; i < 8; i++) {
            double ang = 45 * i;
            Obra.etapa(level, l -> e.portao(k, noCirculo(c, R - 2, ang), radial(ang)));
        }
        // monumento, marcos, prédios, casas
        e.marco(level, k, c);
        com.lanterna.baterias.Tropa bateria = com.lanterna.baterias.Tropa.doPlaneta(planet);
        for (int i = 0; i < 8; i++) {
            int f = i;
            // a Bateria Central da Tropa do planeta fica no lugar do primeiro marco (a sudeste do centro, entre duas avenidas)
            if (i == 0 && bateria != null) Obra.etapa(level, l -> pracaDaBateria(l, k, e, c, torre(c, 0), bateria));
            else Obra.etapa(level, l -> e.torre(k, torre(c, f), f, c));
        }
        List<BlockPos> pr = predios(c);
        for (int i = 0; i < pr.size(); i++) { int f = i; BlockPos p = pr.get(i); Obra.etapa(level, l -> e.predio(k, p, paraOCentro(c, p), f)); }
        List<BlockPos> cs = casas(c);
        for (int i = 0; i < cs.size(); i++) { int f = i; BlockPos p = cs.get(i); Obra.etapa(level, l -> e.casa(k, p, paraOCentro(c, p), f)); }
        // postes nas avenidas e nos anéis
        Obra.etapa(level, l -> {
            int n = 0;
            for (int a = 0; a < 8; a++) for (int s = PRACA + 8; s < R - 10; s += 14) for (int lado : new int[]{-1, 1}) {
                double ang = Math.toRadians(45 * a), px = Math.cos(ang) * s - Math.sin(ang) * 6 * lado, pz = Math.sin(ang) * s + Math.cos(ang) * 6 * lado;
                e.poste(k, c.offset((int) Math.round(px), 0, (int) Math.round(pz)), n++);
            }
            for (int r : ANEIS) for (int j = 0; j < r / 6; j++) {
                BlockPos p = noCirculo(c, r + 4, 360.0 * j / (r / 6) + 3);
                if (!pertoDaAvenida(c, p, 7)) e.poste(k, p, n++);
            }
        });
        // drenagem final: água de lagos que escorreu para o chão aplainado durante a obra
        for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) {
            int fx = cx, fz = cz;
            int mx = Math.max(Math.abs((fx << 4) + 8 - c.getX()) - 12, 0), mz = Math.max(Math.abs((fz << 4) + 8 - c.getZ()) - 12, 0);
            if (mx * mx + mz * mz > (R + 2) * (R + 2)) continue;
            Obra.etapa(level, l -> drenar(l, e, c, fx, fz));
        }
        Obra.etapa(level, l -> CidadeNativa.nativos(l, c, planet, NATIVOS));
        Obra.etapa(level, l -> LanternaMod.LOGGER.info("Lanterna: cidade do povo de {} pronta", planet.planet));
    }

    /**
     * A praça da Bateria Central: um largo de 40 blocos no estilo do povo, com postes em volta, e a Bateria no meio
     * (estrutura do pacote das Baterias, com a câmara virada para o norte).
     */
    private static void pracaDaBateria(ServerLevel l, Kit k, Estilo e, BlockPos c, BlockPos p, com.lanterna.baterias.Tropa t) {
        int r = 20;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            double d = dist(dx, dz);
            if (d > r) continue;
            BlockPos q = p.offset(dx, 0, dz);
            for (int y = 0; y < 40; y++) if (!l.getBlockState(q.above(y)).isAir()) k.s(q.above(y), b(Blocks.AIR));
            BlockState s = e.piso(dx, dz, d, Math.atan2(dz, dx), 2);
            if (s != null) k.s(q.below(), d > r - 1.2 ? e.piso(dx, dz, d, 0, 3) : s);
        }
        if (e instanceof Odym o) { disco(k, p.below(), r, o.deck); o.estacas(k, p.below(), r); disco(k, p.below(), r - 2, e.piso(0, 0, 3, 0, 2)); }
        for (int i = 0; i < 12; i++) {
            BlockPos q = noCirculo(p, r - 2, i * 30 + 15);
            if (q.getZ() < p.getZ() - r + 6 && Math.abs(q.getX() - p.getX()) < 6) continue; // não bloqueia a entrada (norte)
            e.poste(k, q, i);
        }
        int[] o = com.lanterna.baterias.BateriaCentralItem.origem(t);
        BlockPos nucleo = p.below(), canto = nucleo.offset(-o[0], -o[1], -o[2]);
        l.getStructureManager().get(new net.minecraft.resources.ResourceLocation(LanternaMod.MODID, "bateria_central_" + t.nome)).ifPresentOrElse(
                tpl -> tpl.placeInWorld(l, canto, canto, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), l.random, 2),
                () -> LanternaMod.LOGGER.warn("Lanterna: estrutura da Bateria Central {} não encontrada", t.nome));
        LanternaMod.LOGGER.info("Lanterna: Bateria Central {} colocada em {}", t.nome, nucleo);
    }

    /** A direção de quem atravessa o portão (do centro para fora), arredondada para o eixo mais próximo. */
    private static Direction radial(double ang) {
        double a = Math.toRadians(ang);
        return Math.abs(Math.cos(a)) >= Math.abs(Math.sin(a)) - 1e-6 ? (Math.cos(a) > 0 ? Direction.EAST : Direction.WEST) : (Math.sin(a) > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    private static void drenar(ServerLevel l, Estilo e, BlockPos c, int cx, int cz) {
        var m = new BlockPos.MutableBlockPos();
        BlockState ar = b(Blocks.AIR);
        for (int x = cx << 4; x < (cx << 4) + 16; x++) for (int z = cz << 4; z < (cz << 4) + 16; z++) {
            int dx = x - c.getX(), dz = z - c.getZ();
            if (dx * dx + dz * dz > (R + 1) * (R + 1)) continue;
            for (int y = c.getY() - (e.aterro() ? 1 : 0); y < c.getY() + 12; y++) {
                m.set(x, y, z);
                BlockState s = l.getBlockState(m);
                if (s.liquid()) l.setBlock(m, ar, 2);
            }
        }
    }

    private static void terreno(ServerLevel l, Kit k, Estilo e, BlockPos c, int cx, int cz) {
        var m = new BlockPos.MutableBlockPos();
        BlockState ar = b(Blocks.AIR), parede = k.p.parede();
        for (int x = cx << 4; x < (cx << 4) + 16; x++) for (int z = cz << 4; z < (cz << 4) + 16; z++) {
            int dx = x - c.getX(), dz = z - c.getZ();
            double d = dist(dx, dz);
            if (d > R + 12) continue;
            int topo = l.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            if (d <= R + 1) {
                for (int y = c.getY(); y < topo; y++) { m.set(x, y, z); if (!l.getBlockState(m).isAir()) k.s(m, ar); }
                int t = tipo(dx, dz);
                BlockState s = e.piso(dx, dz, d, Math.atan2(dz, dx), d > R - 5 ? 1 : t);
                if (s != null) {
                    k.s(x, c.getY() - 1, z, s);
                    if (e.aterro()) for (int i = 2; i < 60; i++) {
                        m.set(x, c.getY() - i, z);
                        BlockState q = l.getBlockState(m);
                        if (!q.isAir() && !q.liquid() && !q.canBeReplaced()) break;
                        k.s(m, parede);
                    }
                    if (t == 0 && d > PRACA + 2 && d < R - 6) e.enfeite(k, new BlockPos(x, c.getY(), z), dx, dz, t);
                }
            } else if (e.aterro()) {
                for (int i = -16; i <= 40; i++) {
                    m.set(x, c.getY() + i, z);
                    BlockState s = l.getBlockState(m);
                    if (!s.getFluidState().isEmpty()) k.s(m, parede);
                    else if (i < 0 && (s.isAir() || s.canBeReplaced())) k.s(m, parede);
                }
                if (d <= R + 5) k.s(x, c.getY() - 1, z, k.p.chao());
            }
        }
    }

    // ===================== Formas =====================

    static double dist(double dx, double dz) {
        return Math.sqrt(dx * dx + dz * dz);
    }

    static BlockState eixo(Block b, Direction.Axis a) {
        return b.defaultBlockState().setValue(RotatedPillarBlock.AXIS, a);
    }

    static BlockState folha(Block b) {
        return b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    static BlockPos noCirculo(BlockPos c, double raio, double grausAng) {
        double a = Math.toRadians(grausAng);
        return c.offset((int) Math.round(Math.cos(a) * raio), 0, (int) Math.round(Math.sin(a) * raio));
    }

    /** Percorre um círculo com passo de meio bloco. */
    static void circulo(BlockPos c, double r, Consumer<BlockPos> f) {
        int n = Math.max(8, (int) Math.ceil(2 * Math.PI * r * 2));
        BlockPos ult = null;
        for (int i = 0; i < n; i++) {
            BlockPos p = noCirculo(c, r, 360.0 * i / n);
            if (!p.equals(ult)) f.accept(p);
            ult = p;
        }
    }

    static void cilindro(Kit k, BlockPos base, double r, int h, BlockState parede, BlockState dentro) {
        int ri = (int) Math.ceil(r);
        for (int y = 0; y < h; y++) for (int dx = -ri; dx <= ri; dx++) for (int dz = -ri; dz <= ri; dz++) {
            double d = dist(dx, dz);
            if (d > r) continue;
            BlockPos p = base.offset(dx, y, dz);
            if (d > r - 1.05) k.s(p, parede);
            else if (dentro != null) k.s(p, dentro);
        }
    }

    static void cone(Kit k, BlockPos base, double r0, int h, BlockState casca, BlockState faixa, int faixaCada) {
        for (int y = 0; y < h; y++) {
            double r = Math.max(0.6, r0 * (1 - y / (double) h));
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) for (int dz = -ri; dz <= ri; dz++) {
                double d = dist(dx, dz);
                if (d > r || d < r - 1.3) continue;
                k.s(base.offset(dx, y, dz), faixaCada > 0 && y % faixaCada == faixaCada - 1 ? faixa : casca);
            }
        }
    }

    static void cupula(Kit k, BlockPos c, double r, double achatar, BlockState casca, BlockState janela, int janelaY) {
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) for (int dz = -ri; dz <= ri; dz++) for (int dy = 0; dy <= ri; dy++) {
            double d = Math.sqrt(dx * dx + dz * dz + dy * dy * achatar);
            BlockPos p = c.offset(dx, dy, dz);
            if (d <= r && d > r - 1.1) k.s(p, janela != null && dy == janelaY && ((dx + dz) & 1) == 0 ? janela : casca);
            else if (d <= r - 1.1) k.s(p, b(Blocks.AIR));
        }
    }

    static void disco(Kit k, BlockPos c, double r, BlockState s) {
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) for (int dz = -ri; dz <= ri; dz++) if (dist(dx, dz) <= r) k.s(c.offset(dx, 0, dz), s);
    }

    static void caixa(Kit k, BlockPos c, int rx, int rz, int h, BlockState parede, BlockState teto) {
        for (int dx = -rx; dx <= rx; dx++) for (int dz = -rz; dz <= rz; dz++) for (int y = 0; y < h; y++) {
            boolean borda = Math.abs(dx) == rx || Math.abs(dz) == rz;
            k.s(c.offset(dx, y, dz), y == h - 1 ? teto : borda ? parede : b(Blocks.AIR));
        }
    }

    /** Arco de pé atravessando a direção {@code d} (3 blocos de espessura). */
    static void arco(Kit k, BlockPos base, Direction d, int raio, double esticar, BlockState s, BlockState topo) {
        Direction lado = d.getClockWise();
        int n = (int) Math.ceil(Math.PI * raio * 3);
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * i / n;
            for (double rr = raio - 1.5; rr <= raio + 0.5; rr += 0.5)
                for (int prof = -1; prof <= 1; prof++)
                    k.s(base.relative(d, prof).relative(lado, (int) Math.round(Math.cos(a) * rr)).above((int) Math.round(Math.sin(a) * rr * esticar)), s);
        }
        if (topo != null) k.s(base.above((int) Math.round(raio * esticar) + 1), topo);
    }

    static void vao(Kit k, BlockPos p, Direction d, int fundo) {
        vaoGrande(k, p, d, fundo, 1, 3);
    }

    static void vaoGrande(Kit k, BlockPos p, Direction d, int fundo, int largura, int altura) {
        Direction lado = d.getClockWise();
        for (int s = 0; s < fundo; s++) for (int w = -largura; w <= largura; w++) for (int y = 0; y < altura; y++) k.s(p.relative(d, s).relative(lado, w).above(y), b(Blocks.AIR));
    }

    static Direction paraOCentro(BlockPos c, BlockPos p) {
        int dx = c.getX() - p.getX(), dz = c.getZ() - p.getZ();
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    static void cama(Kit k, BlockPos centro, Direction frente, Block cama, BlockState luz, int alturaLuz) {
        Direction costas = frente.getOpposite();
        k.cama(cama, centro.relative(costas, 1), costas);
        if (luz != null) k.s(centro.above(alturaLuz), luz);
    }

    /** Enfileira uma função por fatias de altura (prédios muito altos). */
    static void emFatias(ServerLevel level, int h, int fatia, BiConsumer<Integer, Integer> f) {
        for (int y0 = 0; y0 < h; y0 += fatia) { int a = y0, z = Math.min(h, y0 + fatia); Obra.etapa(level, l -> f.accept(a, z)); }
    }

    // =====================================================================================================
    // OA · Veltari · Cidade de Luz
    // =====================================================================================================

    static final class Oa implements Estilo {
        final Paleta p = Paleta.of(PlanetLore.OA);

        public BlockState piso(int dx, int dz, double d, double a, int t) {
            if (t == 2) return Math.abs(Math.IEEEremainder(a, Math.PI / 6)) * d < 0.6 && d > 14 ? p.luz() : (((int) d) % 9 == 0 ? p.faixa() : p.chao());
            if (t == 1) return distAvenida(dx, dz) < 0.6 ? p.luz() : (distAvenida(dx, dz) > 3.6 ? p.faixa() : p.chao());
            if (t == 3) return p.faixa();
            return ((dx * 7 + dz * 13) & 15) == 0 ? p.destaque() : p.chao();
        }

        public void marco(ServerLevel level, Kit k, BlockPos c) {
            int H = 200;
            emFatias(level, H, 10, (y0, y1) -> {
                for (int y = y0; y < y1; y++) {
                    double r = 16 * Math.pow(1 - y / (double) (H + 6), 1.6) + 2;
                    int ri = (int) Math.ceil(r);
                    for (int dx = -ri; dx <= ri; dx++) for (int dz = -ri; dz <= ri; dz++) {
                        double d = dist(dx, dz);
                        if (d > r) continue;
                        BlockPos q = c.offset(dx, y, dz);
                        boolean porta = y < 12 && (Math.abs(dx) <= 3 || Math.abs(dz) <= 3) && d > r - 4;
                        if (porta) k.s(q, b(Blocks.AIR));
                        else if (d <= 1.5) k.s(q, p.nucleo());
                        else if (d > r - 1.2) k.s(q, y % 16 == 0 ? p.faixa() : Math.abs(Math.IEEEremainder(Math.atan2(dz, dx), Math.PI / 4)) * d < 0.7 ? p.luz() : p.vidro());
                        else k.s(q, b(Blocks.AIR));
                    }
                    if (y % 24 == 8 && y > 10) {
                        double rin = r - 1.2;
                        int ri2 = (int) rin;
                        for (int dx = -ri2; dx <= ri2; dx++) for (int dz = -ri2; dz <= ri2; dz++) { double d = dist(dx, dz); if (d <= rin && d > 1.5) k.s(c.offset(dx, y, dz), p.chao()); }
                    }
                }
            });
            Obra.etapa(level, l -> {
                for (int coroa : new int[]{60, 120, 168}) {
                    double r = 16 * Math.pow(1 - coroa / (double) (H + 6), 1.6) + 2;
                    double fora = r + 12 - coroa / 20.0;
                    for (double rr = r; rr < fora; rr += 0.7) circulo(c.above(coroa), rr, q -> k.s(q, p.faixa()));
                    circulo(c.above(coroa + 1), fora - 1, q -> k.s(q, p.luz()));
                }
                for (int y = H; y < H + 12; y++) k.s(c.above(y), y == H + 11 ? p.luz() : p.nucleo());
                for (int i = 0; i < 4; i++) { // raízes de apoio
                    double a = Math.toRadians(45 + 90 * i);
                    for (int s = 0; s < 30; s++) {
                        int y = (int) Math.round(34 - s * 1.15);
                        if (y < 0) break;
                        for (int g = 0; g < 2; g++) k.s(c.offset((int) Math.round(Math.cos(a) * (14 + s * 0.8)), y + g, (int) Math.round(Math.sin(a) * (14 + s * 0.8))), p.pilar());
                    }
                }
            });
        }

        public void torre(Kit k, BlockPos t, int i, BlockPos c) {
            int[] alt = {78, 92, 70, 96, 74, 88, 66, 84};
            int h = alt[i];
            for (int y = 0; y < h; y++) cilindro(k, t.above(y), y < 6 ? 4.5 - y * 0.4 : 2.3, 1, p.pilar(), p.pilar());
            for (int j = 0; j < 4; j++) {
                double a = Math.toRadians(45 + 90 * j);
                for (int s = 0; s < 14; s++) k.s(t.offset((int) Math.round(Math.cos(a) * (2 + s * 0.5)), 14 - s, (int) Math.round(Math.sin(a) * (2 + s * 0.5))), p.pilar());
            }
            BlockPos casulo = t.above(h);
            int ri = 9;
            for (int dx = -ri; dx <= ri; dx++) for (int dz = -ri; dz <= ri; dz++) for (int dy = -ri; dy <= ri; dy++) {
                double d = Math.sqrt(dx * dx + dz * dz + dy * dy * 1.6);
                BlockPos q = casulo.offset(dx, dy, dz);
                if (d <= 8.5 && d > 7.4) k.s(q, dy == 0 ? p.faixa() : p.vidro());
                else if (d <= 7.4) k.s(q, dy == -3 ? p.chao() : b(Blocks.AIR));
            }
            k.s(casulo.above(1), p.luz());
            // ponte até a Torre da Luz
            BlockPos a = casulo.below(3);
            double dx = c.getX() - a.getX(), dz = c.getZ() - a.getZ(), L = Math.sqrt(dx * dx + dz * dz);
            for (double s = 8; s < L - 14; s += 0.5) {
                double px = a.getX() + dx / L * s, pz = a.getZ() + dz / L * s;
                for (int w = -1; w <= 1; w++)
                    k.s(new BlockPos((int) Math.round(px - dz / L * w), a.getY(), (int) Math.round(pz + dx / L * w)), w == 0 && ((int) s) % 6 == 0 ? p.luz() : p.faixa());
            }
        }

        public void predio(Kit k, BlockPos q, Direction frente, int i) {
            int h = 26 + (i * 7) % 18;
            for (int y = 0; y < h; y++) {
                double r = 5.5 - (y > h - 8 ? (y - (h - 8)) * 0.6 : 0);
                int ri = (int) Math.ceil(r);
                for (int dx = -ri; dx <= ri; dx++) for (int dz = -ri; dz <= ri; dz++) {
                    double d = dist(dx, dz);
                    if (d > r) continue;
                    k.s(q.offset(dx, y, dz), d > r - 1.1 ? (y % 6 == 0 ? p.faixa() : p.vidro()) : (y % 6 == 0 && y > 0 ? p.chao() : b(Blocks.AIR)));
                }
            }
            k.s(q.above(h), p.luz());
            vao(k, q.relative(frente, 4), frente, 3);
            cama(k, q, frente, p.cama(), p.luz(), 5);
        }

        public void casa(Kit k, BlockPos h, Direction frente, int i) {
            cupula(k, h, 6.2, 1.1, p.parede(), p.vidro(), 3);
            vao(k, h.relative(frente, 4), frente, 3);
            cama(k, h, frente, p.cama(), p.luz(), 4);
        }

        public void portao(Kit k, BlockPos g, Direction d) {
            arco(k, g, d, 13, 1.8, p.faixa(), p.luz());
            arco(k, g, d, 11, 1.8, p.vidro(), null);
        }

        public void muro(Kit k, BlockPos q, int i, boolean fora) {
            for (int y = 0; y < 5; y++) k.s(q.above(y), y < 4 ? p.faixa() : fora && i % 8 == 0 ? p.luz() : b(Blocks.AIR));
        }

        public void poste(Kit k, BlockPos q, int i) {
            for (int y = 0; y < 6; y++) k.s(q.above(y), y == 5 ? p.luz() : p.pilar());
        }
    }

    // =====================================================================================================
    // KORUGAR · Skrell'Vath · A Colmeia do Medo
    // =====================================================================================================

    static final class Korugar implements Estilo {
        final BlockState negra = b(Blocks.POLISHED_BLACKSTONE_BRICKS), pedra = b(Blocks.BLACKSTONE), basalto = eixo(Blocks.POLISHED_BASALT, Direction.Axis.Y),
                ambar = b(Blocks.OCHRE_FROGLIGHT), vidro = b(Blocks.YELLOW_STAINED_GLASS), ouro = b(Blocks.GILDED_BLACKSTONE), muro = b(Blocks.POLISHED_BLACKSTONE_BRICK_WALL),
                polido = b(Blocks.POLISHED_BLACKSTONE);

        public BlockState piso(int dx, int dz, double d, double a, int t) {
            if (t == 2) return Math.abs(d % 8 - 4) < 0.6 ? ambar : ((dx + dz) & 1) == 0 ? polido : negra;
            if (t == 1) return distAvenida(dx, dz) < 0.7 && ((int) d) % 5 == 0 ? ambar : negra;
            if (t == 3) return polido;
            int hx = Math.floorMod(dx + (Math.floorMod(Math.floorDiv(dz, 4), 2) == 0 ? 0 : 3), 6), hz = Math.floorMod(dz, 4);
            return hx == 0 || hz == 0 ? polido : ((dx * 31 + dz * 17) & 31) == 0 ? ambar : pedra;
        }

        public void enfeite(Kit k, BlockPos p, int dx, int dz, int t) {
            if (((dx * 73 + dz * 41) & 127) == 0) { k.s(p, basalto); k.s(p.above(), muro); }
        }

        public void marco(ServerLevel level, Kit k, BlockPos c) {
            int H = 190;
            emFatias(level, H, 6, (y0, y1) -> {
                for (int y = y0; y < y1; y++) {
                    double r = Math.max(1.2, 38 * Math.pow(1 - y / (H + 4.0), 1.15));
                    int ri = (int) Math.ceil(r);
                    for (int dx = -ri; dx <= ri; dx++) for (int dz = -ri; dz <= ri; dz++) {
                        double d = dist(dx, dz);
                        if (d > r || d < r - 1.6) continue;
                        double ang = Math.toDegrees(Math.atan2(dz, dx));
                        boolean janela = y % 12 >= 5 && y % 12 <= 8 && Math.floorMod((int) Math.round(ang / 7.5) + (y / 12) * 2, 4) == 0;
                        boolean porta = y < 14 && (Math.abs(dx) <= 4 || Math.abs(dz) <= 4);
                        k.s(c.offset(dx, y, dz), porta ? b(Blocks.AIR) : janela ? vidro : y % 12 == 0 ? ouro : negra);
                    }
                    if (y % 30 == 15 && y < 160) { // andares do ninho
                        double rin = r - 1.6;
                        int ri2 = (int) rin;
                        for (int dx = -ri2; dx <= ri2; dx++) for (int dz = -ri2; dz <= ri2; dz++) {
                            double d = dist(dx, dz);
                            if (d <= rin && d > rin * 0.45) k.s(c.offset(dx, y, dz), ((int) d) % 6 == 0 ? ambar : polido);
                        }
                    }
                    if (y < 180) cilindro(k, c.above(y), 3.2, 1, y % 10 == 0 ? ambar : negra, ambar);
                }
            });
            Obra.etapa(level, l -> {
                for (int i = 0; i < 66; i++) { // espinhos em espiral
                    int y = Math.min(175, (int) (10 + i * 2.5));
                    double r = 38 * Math.pow(1 - y / 194.0, 1.15);
                    double a = Math.toRadians(i * 47);
                    int len = 6 + (i % 4) * 3;
                    for (int s = 0; s < len; s++) {
                        double rr = r + s;
                        k.s(c.offset((int) Math.round(Math.cos(a) * rr), y + s / 2, (int) Math.round(Math.sin(a) * rr)), s == len - 1 ? ambar : basalto);
                    }
                }
                for (int y = 188; y < 205; y++) k.s(c.above(y), y == 204 ? ambar : basalto);
            });
        }

        public void torre(Kit k, BlockPos t, int i, BlockPos c) {
            int h = 64 + (i * 11) % 28;
            cone(k, t, 10, h, negra, ambar, 12);
            for (int s = 0; s < 8; s++) {
                double a = Math.toRadians(s * 45 + i * 10);
                int y0 = 8 + s * (h / 10);
                double r = 10 * (1 - y0 / (double) h);
                for (int j = 0; j < 7; j++) k.s(t.offset((int) Math.round(Math.cos(a) * (r + j)), y0 + j / 2, (int) Math.round(Math.sin(a) * (r + j))), j == 6 ? ambar : basalto);
            }
            k.s(t.above(h), ambar);
            Direction f = paraOCentro(c, t);
            vaoGrande(k, t.relative(f, 10), f.getOpposite(), 4, 2, 5);
        }

        public void predio(Kit k, BlockPos p, Direction frente, int i) {
            int h = 18 + (i * 5) % 14;
            for (int y = 0; y < h; y++) for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
                double d = Math.max(Math.abs(dx) * 0.87 + Math.abs(dz) * 0.5, Math.abs(dz)); // hexágono
                if (d > 6.6) continue;
                BlockPos q = p.offset(dx, y, dz);
                if (d > 5.6) k.s(q, y % 6 == 3 && ((dx + dz) & 1) == 0 ? vidro : y % 6 == 0 ? ouro : negra);
                else k.s(q, y % 8 == 0 && y > 0 ? polido : b(Blocks.AIR));
            }
            cone(k, p.above(h), 6.5, 10, negra, ambar, 4);
            vao(k, p.relative(frente, 5), frente, 3);
            cama(k, p, frente, Blocks.YELLOW_BED, ambar, 5);
        }

        public void casa(Kit k, BlockPos h, Direction frente, int i) {
            cupula(k, h.below(2), 6.5, 0.7, pedra, vidro, 4);
            for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) if (dx * dx + dz * dz <= 25) k.s(h.offset(dx, -1, dz), negra);
            vao(k, h.relative(frente, 4), frente, 4);
            for (int s = 0; s < 3; s++) k.s(h.relative(frente, 5).above(3 + s).relative(frente.getClockWise(), s - 1), muro);
            cama(k, h, frente, Blocks.YELLOW_BED, ambar, 4);
        }

        public void portao(Kit k, BlockPos g, Direction d) {
            for (int s : new int[]{-1, 1}) for (int y = 0; y < 30; y++) {
                int w = (int) Math.round(8 + Math.sin(y / 30.0 * Math.PI) * 6) * s;
                for (int e = 0; e < 2; e++) for (int pr = -1; pr <= 1; pr++) k.s(g.relative(d, pr).relative(d.getClockWise(), w + e * s).above(y), y > 26 ? ambar : basalto);
            }
        }

        public void muro(Kit k, BlockPos p, int i, boolean fora) {
            for (int y = 0; y < 9; y++) k.s(p.above(y), negra);
            if (fora) k.s(p.above(9), i % 5 == 0 ? muro : b(Blocks.AIR));
            if (fora && i % 40 == 0) for (int y = 9; y < 16; y++) k.s(p.above(y), y == 15 ? ambar : basalto);
        }

        public void poste(Kit k, BlockPos p, int i) {
            for (int y = 0; y < 6; y++) k.s(p.above(y), y == 5 ? ambar : basalto);
            k.s(p.above(6), muro);
        }
    }

    // =====================================================================================================
    // YSMAULT · Brakkari · A Fortaleza de Gorrak
    // =====================================================================================================

    static final class Ysmault implements Estilo {
        final BlockState tijolo = b(Blocks.RED_NETHER_BRICKS), negro = b(Blocks.POLISHED_BLACKSTONE_BRICKS), magma = b(Blocks.MAGMA_BLOCK), fogo = b(Blocks.SHROOMLIGHT),
                areia = b(Blocks.RED_SAND), ossos = eixo(Blocks.BONE_BLOCK, Direction.Axis.Y), basalto = b(Blocks.SMOOTH_BASALT), nb = b(Blocks.NETHER_BRICKS);

        public BlockState piso(int dx, int dz, double d, double a, int t) {
            if (t == 2 || t == 1) return ((int) d) % 6 == 0 ? magma : nb;
            if (t == 3) return negro;
            double veio = Math.abs(Math.sin(a * 9 + d * 0.11)) * d;
            return veio < 1.1 ? magma : ((dx ^ dz) & 3) == 0 ? b(Blocks.BLACKSTONE) : basalto;
        }

        public void enfeite(Kit k, BlockPos p, int dx, int dz, int t) {
            if (((dx * 37 + dz * 91) & 255) == 0) { k.s(p, negro); k.s(p.above(), b(Blocks.CAMPFIRE)); }
        }

        public void marco(ServerLevel level, Kit k, BlockPos c) {
            // o Coliseu de Gorrak: arena de 60 blocos, 14 fileiras de arquibancada e muralha de 34
            Obra.etapa(level, l -> {
                disco(k, c.below(), 30, areia);
                for (int t = 0; t < 14; t++) {
                    double r = 31 + t * 1.6;
                    int tt = t;
                    for (double rr = r; rr < r + 1.6; rr += 0.5)
                        circulo(c, rr, p -> { for (int y = 0; y <= tt * 2; y++) k.s(p.above(y), y == tt * 2 ? b(Blocks.NETHER_BRICK_SLAB) : y == tt * 2 - 1 ? tijolo : negro); });
                }
            });
            emFatias(level, 34, 6, (y0, y1) -> {
                for (double rr = 53.5; rr <= 56; rr += 0.5) circulo(c, rr, p -> {
                    double a = Math.atan2(p.getZ() - c.getZ(), p.getX() - c.getX());
                    for (int y = y0; y < y1; y++) {
                        int fy = y % 11;
                        boolean janela = y > 10 && fy >= 3 && fy <= 8 && Math.abs(Math.IEEEremainder(a, Math.PI / 24)) * 55 < 1.5 + Math.sin((fy - 3) / 5.0 * Math.PI);
                        boolean ameia = y == 33 && (((int) Math.floor(a * 40)) & 1) == 0;
                        k.s(p.above(y), janela || ameia ? b(Blocks.AIR) : fy == 0 ? magma : tijolo);
                    }
                });
            });
            Obra.etapa(level, l -> {
                for (Direction d : Direction.Plane.HORIZONTAL) vaoGrande(k, c.relative(d, 30), d, 27, 4, 9);
                for (int i = 0; i < 12; i++) {
                    BlockPos p = noCirculo(c, 26, i * 30);
                    for (int y = 0; y < 4; y++) k.s(p.above(y), negro);
                    k.s(p.above(4), fogo); k.s(p.above(5), b(Blocks.CAMPFIRE));
                }
                BlockPos tr = c.relative(Direction.NORTH, 50).above(26); // trono do campeão do mês
                for (int dx = -3; dx <= 3; dx++) for (int y = 0; y < 8; y++) k.s(tr.offset(dx, y, -1), y >= 6 && Math.abs(dx) == 3 ? ossos : negro);
                for (int dx = -2; dx <= 2; dx++) k.s(tr.offset(dx, 0, 0), b(Blocks.RED_WOOL));
            });
            // 4 torres de chifres em volta do coliseu (110 blocos)
            for (int i = 0; i < 4; i++) {
                BlockPos t = noCirculo(c, 66, 45 + 90 * i);
                emFatias(level, 110, 20, (y0, y1) -> {
                    for (int y = y0; y < y1; y++) {
                        double r = 10 - y / 110.0 * 4;
                        int yy = y;
                        circulo(t.above(y), r, p -> k.s(p, yy % 12 == 0 ? magma : tijolo));
                        circulo(t.above(y), r - 0.8, p -> k.s(p, tijolo));
                    }
                });
                Obra.etapa(level, l -> {
                    disco(k, t.above(110), 7, negro);
                    circulo(t.above(111), 6.5, p -> k.s(p, ((p.getX() + p.getZ()) & 1) == 0 ? tijolo : b(Blocks.AIR)));
                    k.s(t.above(111), fogo);
                    vaoGrande(k, t.relative(paraOCentro(c, t), 9), paraOCentro(c, t).getOpposite(), 3, 2, 6);
                    for (int s : new int[]{-1, 1}) for (int j = 0; j < 34; j++) {
                        double tt = j / 34.0;
                        int out = (int) Math.round(5 + Math.sin(tt * Math.PI * 0.8) * 12);
                        int up = (int) Math.round(tt * 30 - (tt > 0.7 ? (tt - 0.7) * 30 : 0));
                        for (int w = 0; w < (j < 20 ? 2 : 1); w++) { k.s(t.relative(Direction.EAST, s * (out + w)).above(110 + up), ossos); k.s(t.relative(Direction.EAST, s * (out + w)).above(110 + up).north(), ossos); }
                    }
                });
            }
        }

        public void torre(Kit k, BlockPos t, int i, BlockPos c) {
            int h = 40 + (i % 3) * 8;
            cilindro(k, t, 7, h, tijolo, b(Blocks.AIR));
            for (int y = 0; y < h; y += 8) disco(k, t.above(y), 6, nb);
            circulo(t.above(h), 7, p -> k.s(p, ((p.getX() + p.getZ()) & 1) == 0 ? tijolo : b(Blocks.AIR)));
            k.s(t.above(h), fogo);
            for (int s : new int[]{-1, 1}) for (int j = 0; j < 12; j++) k.s(t.relative(Direction.EAST, s * (5 + j / 2)).above(h + j - (j > 8 ? (j - 8) * 2 : 0)), ossos);
            Direction f = paraOCentro(c, t);
            vaoGrande(k, t.relative(f, 7), f.getOpposite(), 2, 1, 4);
        }

        public void predio(Kit k, BlockPos p, Direction frente, int i) { // quartel
            boolean ns = frente.getAxis() == Direction.Axis.Z;
            int rx = ns ? 8 : 6, rz = ns ? 6 : 8, h = 12 + (i % 3) * 3;
            for (int dx = -rx; dx <= rx; dx++) for (int dz = -rz; dz <= rz; dz++) for (int y = 0; y < h; y++) {
                boolean borda = Math.abs(dx) == rx || Math.abs(dz) == rz;
                k.s(p.offset(dx, y, dz), borda ? (y == 6 ? magma : y == 3 && (dx + dz) % 4 == 0 ? b(Blocks.IRON_BARS) : tijolo) : (y == h - 1 || y == 6 ? nb : b(Blocks.AIR)));
            }
            for (int dx = -rx; dx <= rx; dx++) for (int dz = -rz; dz <= rz; dz++)
                if ((Math.abs(dx) == rx || Math.abs(dz) == rz) && ((dx + dz) & 1) == 0) k.s(p.offset(dx, h, dz), tijolo);
            int fr = ns ? rz : rx;
            vaoGrande(k, p.relative(frente, fr), frente, 1, 1, 4);
            k.s(p.relative(frente, fr + 1).above(5), b(Blocks.RED_WOOL));
            k.s(p.relative(frente, fr + 1).above(4), b(Blocks.RED_WOOL));
            cama(k, p.relative(frente.getClockWise(), 3), frente, Blocks.RED_BED, fogo, 5);
            cama(k, p.relative(frente.getCounterClockWise(), 3), frente, Blocks.RED_BED, null, 0);
        }

        public void casa(Kit k, BlockPos h, Direction frente, int i) {
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int y = 0; y < 5; y++) {
                boolean borda = Math.abs(dx) == 4 || Math.abs(dz) == 4 || y == 4;
                k.s(h.offset(dx, y, dz), borda ? (y == 2 && (dx + dz) % 3 == 0 ? magma : tijolo) : b(Blocks.AIR));
            }
            vao(k, h.relative(frente, 4), frente, 1);
            for (int s : new int[]{-1, 1}) for (int j = 0; j < 4; j++) k.s(h.relative(frente.getClockWise(), s * (4 + j / 2)).above(5 + j), ossos);
            cama(k, h, frente, Blocks.RED_BED, fogo, 3);
        }

        public void portao(Kit k, BlockPos g, Direction d) {
            Direction lado = d.getClockWise();
            for (int s : new int[]{-1, 1}) {
                BlockPos t = g.relative(lado, s * 11);
                cilindro(k, t, 5, 30, tijolo, nb);
                circulo(t.above(30), 5, p -> k.s(p, ((p.getX() + p.getZ()) & 1) == 0 ? tijolo : b(Blocks.AIR)));
                k.s(t.above(30), fogo);
            }
            for (int w = -7; w <= 7; w++) for (int y = 16; y < 22; y++) for (int pr = -2; pr <= 2; pr++) k.s(g.relative(lado, w).relative(d, pr).above(y), y == 16 ? negro : tijolo);
            for (int w = -6; w <= 6; w++) k.s(g.relative(lado, w).above(15), b(Blocks.IRON_BARS));
        }

        public void muro(Kit k, BlockPos p, int i, boolean fora) {
            for (int y = 0; y < 18; y++) k.s(p.above(y), y % 6 == 5 && fora ? magma : tijolo);
            if (fora && (i / 3) % 2 == 0) { k.s(p.above(18), tijolo); k.s(p.above(19), tijolo); }
        }

        public void poste(Kit k, BlockPos p, int i) {
            for (int y = 0; y < 3; y++) k.s(p.above(y), negro);
            k.s(p.above(3), fogo);
            k.s(p.above(4), b(Blocks.CAMPFIRE));
        }
    }

    // =====================================================================================================
    // ODYM · Aelínos · A Cidade das Marés (sobre a água)
    // =====================================================================================================

    static final class Odym implements Estilo {
        final BlockState prisma = b(Blocks.PRISMARINE_BRICKS), escuro = b(Blocks.DARK_PRISMARINE), luz = b(Blocks.SEA_LANTERN), estaca = b(Blocks.STRIPPED_WARPED_HYPHAE),
                concha = b(Blocks.WHITE_CONCRETE), azul = b(Blocks.LIGHT_BLUE_CONCRETE), vidro = b(Blocks.LIGHT_BLUE_STAINED_GLASS), deck = b(Blocks.WARPED_PLANKS);
        final BlockState[] corais = {b(Blocks.PINK_CONCRETE), b(Blocks.CYAN_CONCRETE), b(Blocks.LIGHT_BLUE_CONCRETE), b(Blocks.MAGENTA_CONCRETE)};

        public boolean aterro() { return false; }

        /** Os decks ficam 3 blocos acima da superfície da água (o mar de Odym é mais alto que o normal). */
        public int pisoAltura(ServerLevel l, int fundo, int superficie) { return Math.max(fundo + 2, superficie + 3); }

        public BlockState piso(int dx, int dz, double d, double a, int t) {
            if (t == 2) return ((int) d) % 5 == 0 ? azul : Math.abs(Math.IEEEremainder(a, Math.PI / 8)) * d < 0.7 ? luz : concha;
            if (t == 1 || t == 3) return deck;
            return null;
        }

        /** Palafitas: estacas do deck até o fundo. */
        void estacas(Kit k, BlockPos base, double r) {
            int ri = (int) r;
            for (int dx = -ri; dx <= ri; dx += 4) for (int dz = -ri; dz <= ri; dz += 4) {
                if (dx * dx + dz * dz > r * r) continue;
                for (int y = 1; y < 50; y++) {
                    BlockPos q = base.offset(dx, -y, dz);
                    var s = k.L.getBlockState(q);
                    if (!s.isAir() && k.L.getFluidState(q).isEmpty() && !s.canBeReplaced()) break;
                    k.s(q, estaca);
                }
            }
        }

        public void marco(ServerLevel level, Kit k, BlockPos c) {
            Obra.etapa(level, l -> {
                estacas(k, c.below(), PRACA);
                int ri = 28; // cúpula-concha enorme na base
                for (int dx = -ri; dx <= ri; dx++) for (int dz = -ri; dz <= ri; dz++) for (int dy = 0; dy <= ri; dy++) {
                    double d = Math.sqrt(dx * dx + dz * dz + dy * dy * 1.5);
                    BlockPos p = c.offset(dx, dy, dz);
                    if (d <= 27.5 && d > 26.3) k.s(p, Math.abs(Math.IEEEremainder(Math.atan2(dz, dx), Math.PI / 8)) * dist(dx, dz) < 1 ? azul : dy % 7 == 3 ? vidro : concha);
                    else if (d <= 26.3) k.s(p, b(Blocks.AIR));
                }
                for (Direction d : Direction.Plane.HORIZONTAL) vaoGrande(k, c.relative(d, 22), d, 8, 3, 7);
            });
            int H = 175;
            emFatias(level, H, 12, (y0, y1) -> {
                for (int y = y0; y < y1; y++) {
                    double r = 9 - y / (double) H * 3;
                    int yy = y;
                    circulo(c.above(y), r, p -> k.s(p, yy % 14 < 3 ? azul : prisma));
                    circulo(c.above(y), r - 0.8, p -> k.s(p, yy % 14 < 3 ? azul : prisma));
                    for (int f = 0; f < 3; f++) { // três fitas de onda subindo em hélice
                        double a = y * 0.09 + f * Math.PI * 2 / 3;
                        for (double rr = r; rr < r + 3; rr += 0.7) k.s(c.offset((int) Math.round(Math.cos(a) * rr), y, (int) Math.round(Math.sin(a) * rr)), f == 0 ? luz : escuro);
                    }
                }
            });
            Obra.etapa(level, l -> {
                disco(k, c.above(H - 1), 9, prisma);
                cupula(k, c.above(H), 9, 1.0, vidro, null, -1);
                for (int y = 0; y < 4; y++) k.s(c.above(H + y), luz);
                k.s(c.above(H + 4), LoreBlocks.FAROL_PEROLA.get().defaultBlockState());
                for (int y = H + 10; y < H + 20; y++) k.s(c.above(y), b(Blocks.END_ROD));
            });
        }

        public void torre(Kit k, BlockPos t, int i, BlockPos c) {
            disco(k, t.below(), 12, deck);
            estacas(k, t.below(), 12);
            BlockState cor = corais[i % 4];
            for (int ramo = 0; ramo < 9; ramo++) {
                double ang = Math.toRadians(ramo * 40 + i * 13);
                int h = 30 + ((i + ramo) * 7) % 26;
                for (int y = 0; y < h; y++) {
                    double curva = Math.sin(y / (double) h * Math.PI * 0.9) * (3 + ramo % 4) + (ramo == 0 ? 0 : 2);
                    BlockPos p = t.offset((int) Math.round(Math.cos(ang) * curva), y, (int) Math.round(Math.sin(ang) * curva));
                    k.s(p, y == h - 1 ? luz : cor);
                    k.s(p.east(), y % 3 == 0 ? cor : b(Blocks.AIR));
                    k.s(p.north(), cor);
                }
            }
        }

        public void predio(Kit k, BlockPos p, Direction frente, int i) {
            disco(k, p.below(), 8, deck);
            estacas(k, p.below(), 8);
            int h = 20 + (i * 7) % 14;
            for (int y = 0; y < h; y++) {
                double r = 6 - Math.abs(Math.sin(y / 5.0)) * 1.2; // conchas empilhadas
                int yy = y;
                circulo(p.above(y), r, q -> k.s(q, yy % 5 == 0 ? luz : yy % 5 == 2 ? vidro : prisma));
                if (y % 7 == 0 && y > 0) disco(k, p.above(y), r - 1, deck);
            }
            cupula(k, p.above(h), 5, 1.0, vidro, null, -1);
            vao(k, p.relative(frente, 5), frente, 2);
            cama(k, p, frente, Blocks.CYAN_BED, null, 0);
        }

        public void casa(Kit k, BlockPos h, Direction frente, int i) {
            disco(k, h.below(), 6.5, deck);
            estacas(k, h.below(), 6.5);
            for (int s = 1; s < 9; s++) for (int w = -1; w <= 1; w++) { // passarela até a rua
                BlockPos q = h.relative(frente, 6 + s).relative(frente.getClockWise(), w).below();
                if (k.L.getBlockState(q).isAir() || !k.L.getFluidState(q).isEmpty()) k.s(q, deck);
            }
            int ri = 6;
            for (int dx = -ri; dx <= ri; dx++) for (int dz = -ri; dz <= ri; dz++) for (int dy = 0; dy <= ri; dy++) {
                double d = Math.sqrt(dx * dx + dz * dz + dy * dy * 1.4);
                BlockPos p = h.offset(dx, dy, dz);
                if (d <= 5.8 && d > 4.7) k.s(p, Math.abs(Math.IEEEremainder(Math.atan2(dz, dx), Math.PI / 5)) < 0.12 ? azul : dy == 2 ? vidro : concha);
                else if (d <= 4.7) k.s(p, b(Blocks.AIR));
            }
            vao(k, h.relative(frente, 4), frente, 3);
            cama(k, h, frente, Blocks.LIGHT_BLUE_BED, luz, 4);
        }

        public void portao(Kit k, BlockPos g, Direction d) {
            estacas(k, g.below(), 6);
            arco(k, g, d, 11, 1.6, prisma, luz);
            arco(k, g, d, 9, 1.6, escuro, null);
        }

        public void muro(Kit k, BlockPos p, int i, boolean fora) {
            k.s(p.below(), deck);
            if (fora) { k.s(p, b(Blocks.PRISMARINE_WALL)); if (i % 12 == 0) k.s(p.above(), luz); }
            if (i % 8 == 0 && fora) for (int y = 2; y < 40; y++) { BlockPos q = p.below(y); var s = k.L.getBlockState(q); if (!s.isAir() && k.L.getFluidState(q).isEmpty()) break; k.s(q, estaca); }
        }

        public void poste(Kit k, BlockPos p, int i) {
            k.s(p.below(), deck);
            for (int y = 0; y < 4; y++) k.s(p.above(y), y == 3 ? luz : b(Blocks.PRISMARINE_WALL));
        }
    }

    // =====================================================================================================
    // OKAARA · Ghallos · O Mercado dos Cofres
    // =====================================================================================================

    static final class Okaara implements Estilo {
        final BlockState liso = b(Blocks.SMOOTH_SANDSTONE), cortado = b(Blocks.CUT_SANDSTONE), cinzel = b(Blocks.CHISELED_SANDSTONE), ouro = b(Blocks.GOLD_BLOCK),
                terracota = b(Blocks.ORANGE_TERRACOTTA), luz = b(Blocks.OCHRE_FROGLIGHT), ouroCru = b(Blocks.RAW_GOLD_BLOCK), muroA = b(Blocks.SANDSTONE_WALL);
        final Block[] toldos = {Blocks.ORANGE_WOOL, Blocks.YELLOW_WOOL, Blocks.RED_WOOL, Blocks.WHITE_WOOL};

        public BlockState piso(int dx, int dz, double d, double a, int t) {
            if (t == 2) return ((int) d) % 7 == 0 ? terracota : ((dx + dz) & 3) == 0 ? cinzel : cortado;
            if (t == 1) return distAvenida(dx, dz) > 3.6 ? terracota : cortado;
            if (t == 3) return terracota;
            return ((Math.floorDiv(dx, 3) + Math.floorDiv(dz, 3)) & 1) == 0 ? liso : b(Blocks.SANDSTONE);
        }

        public void marco(ServerLevel level, Kit k, BlockPos c) {
            for (int t = 0; t < 12; t++) {
                int tt = t, y0 = t * 7;
                Obra.etapa(level, l -> {
                    int r = 46 - tt * 3, alt = 7;
                    for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                        boolean borda = Math.abs(dx) == r || Math.abs(dz) == r;
                        boolean casca = Math.abs(dx) >= r - 1 || Math.abs(dz) >= r - 1;
                        for (int h = 0; h < alt; h++) {
                            if (h < alt - 1 && !casca) continue;
                            k.s(c.offset(dx, y0 + h, dz), borda ? (h == alt - 1 ? ouro : h == 2 ? terracota : liso) : (h == alt - 1 ? cortado : liso));
                        }
                    }
                    for (Direction d : Direction.Plane.HORIZONTAL) for (int w = -4; w <= 4; w++) for (int h = 0; h < alt; h++)
                        k.s(c.relative(d, r - h).relative(d.getClockWise(), w).above(y0 + h), Math.abs(w) == 4 ? cinzel : Kit.degrau(Blocks.SANDSTONE_STAIRS, d.getOpposite()));
                });
            }
            Obra.etapa(level, l -> {
                int top = 84;
                cupula(k, c.above(top), 11, 1.0, ouro, null, -1);
                for (Direction d : Direction.Plane.HORIZONTAL) vaoGrande(k, c.relative(d, 9).above(top), d, 3, 2, 5);
                k.s(c.above(top), ouroCru);
                for (int h = 0; h < 60; h++) { // o obelisco de ouro
                    int r = h < 50 ? 2 - h / 25 : 0;
                    for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) k.s(c.offset(dx, top + 11 + h, dz), h % 10 == 0 ? luz : ouro);
                }
                // salão do tesouro no térreo
                vaoGrande(k, c.relative(Direction.SOUTH, 46), Direction.NORTH, 40, 3, 6);
                for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) for (int h = 0; h < 7; h++)
                    k.s(c.offset(dx, h, dz), h == 0 && ((dx * 3 + dz) & 7) == 0 ? b(Blocks.CHEST) : b(Blocks.AIR));
                for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) k.s(c.offset(dx, -1, dz), ((dx + dz) & 1) == 0 ? ouroCru : ouro);
                k.s(c.above(6), luz);
            });
        }

        public void torre(Kit k, BlockPos t, int i, BlockPos c) {
            int h = 46 + (i * 7) % 18;
            cilindro(k, t, 7.5, h, liso, b(Blocks.AIR));
            for (int y = 4; y < h; y += 8) { circulo(t.above(y), 7.5, p -> k.s(p, ouro)); circulo(t.above(y + 1), 7.5, p -> k.s(p, terracota)); }
            cupula(k, t.above(h), 7.5, 0.7, ouro, null, -1);
            for (int y = 0; y < 8; y++) k.s(t.above(h + 9 + y), y == 7 ? luz : ouro);
            Direction f = paraOCentro(c, t);
            vaoGrande(k, t.relative(f, 7), f.getOpposite(), 2, 1, 4);
        }

        public void predio(Kit k, BlockPos p, Direction frente, int i) { // mercado coberto
            boolean ns = frente.getAxis() == Direction.Axis.Z;
            int rx = ns ? 8 : 6, rz = ns ? 6 : 8, h = 9;
            Direction lado = frente.getClockWise();
            for (int dx = -rx; dx <= rx; dx++) for (int dz = -rz; dz <= rz; dz++) for (int y = 0; y < h; y++) {
                boolean borda = Math.abs(dx) == rx || Math.abs(dz) == rz;
                boolean arcada = borda && y < 5 && ((dx + dz) & 3) != 0;
                k.s(p.offset(dx, y, dz), y == h - 1 ? cortado : borda ? (arcada ? b(Blocks.AIR) : y == 6 ? terracota : liso) : b(Blocks.AIR));
            }
            for (int dx = -rx; dx <= rx; dx++) for (int dz = -rz; dz <= rz; dz++) if (Math.abs(dx) == rx || Math.abs(dz) == rz) k.s(p.offset(dx, h, dz), muroA);
            for (int j = -2; j <= 2; j++) { // bancas de dentro
                BlockPos q = p.relative(lado, j * 3);
                k.s(q, b(Blocks.BARREL)); k.s(q.relative(frente), b(Blocks.CHEST));
                k.s(q.above(3), b(toldos[(i + j + 2) % 4]));
            }
            k.s(p.above(h - 2), luz);
            cupula(k, p.above(h), 3, 1, ouro, null, -1);
        }

        public void casa(Kit k, BlockPos h, Direction frente, int i) {
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int y = 0; y < 6; y++) {
                boolean borda = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                k.s(h.offset(dx, y, dz), y == 5 ? cortado : borda ? (y == 3 ? terracota : y == 2 && (dx + dz) % 3 == 0 ? b(Blocks.ORANGE_STAINED_GLASS) : liso) : b(Blocks.AIR));
            }
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) if (Math.abs(dx) == 4 || Math.abs(dz) == 4) k.s(h.offset(dx, 6, dz), muroA);
            vao(k, h.relative(frente, 4), frente, 1);
            Block t = toldos[i % 4];
            for (int w = -1; w <= 1; w++) k.s(h.relative(frente, 5).above(3).relative(frente.getClockWise(), w), b(t));
            cama(k, h, frente, Blocks.ORANGE_BED, luz, 4);
            k.s(h.relative(frente.getClockWise(), 2), b(Blocks.CHEST));
        }

        public void portao(Kit k, BlockPos g, Direction d) {
            arco(k, g, d, 12, 1.4, cortado, ouro);
            arco(k, g, d, 10, 1.4, liso, null);
            for (int s : new int[]{-1, 1}) for (int y = 0; y < 22; y++) k.s(g.relative(d.getClockWise(), s * 14).above(y), y == 21 ? luz : y % 5 == 0 ? ouro : cinzel);
        }

        public void muro(Kit k, BlockPos p, int i, boolean fora) {
            for (int y = 0; y < 10; y++) k.s(p.above(y), y == 7 ? terracota : liso);
            if (fora) k.s(p.above(10), i % 3 == 0 ? muroA : b(Blocks.AIR));
            if (fora && i % 60 == 0) { for (int y = 10; y < 16; y++) k.s(p.above(y), cortado); k.s(p.above(16), ouro); }
        }

        public void poste(Kit k, BlockPos p, int i) { // barraca do mercado
            k.s(p, b(Blocks.BARREL));
            k.s(p.above(), b(Blocks.OAK_FENCE)); k.s(p.above(2), b(Blocks.OAK_FENCE));
            Block t = toldos[i % 4];
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) k.s(p.offset(dx, 3, dz), b(t));
            if (i % 3 == 0) k.s(p.offset(1, 0, 0), b(Blocks.CHEST));
        }
    }

    // =====================================================================================================
    // NOK · Ves'Kai · O Santuário das Raízes
    // =====================================================================================================

    static final class Nok implements Estilo {
        final BlockState ladrilho = b(Blocks.DEEPSLATE_TILES), polido = b(Blocks.POLISHED_DEEPSLATE), cinzel = b(Blocks.CHISELED_DEEPSLATE), musgo = b(Blocks.MOSS_BLOCK),
                tronco = eixo(Blocks.MANGROVE_LOG, Direction.Axis.Y), madeira = b(Blocks.MANGROVE_WOOD),
                folhas = folha(Blocks.CHERRY_LEAVES), folhas2 = folha(Blocks.AZALEA_LEAVES), roxo = b(Blocks.PURPLE_WOOL), cristal = b(Blocks.PEARLESCENT_FROGLIGHT),
                ametista = b(Blocks.AMETHYST_BLOCK);

        BlockState templo() { return LoreBlocks.PEDRA_DO_TEMPLO_TALEUN.get().defaultBlockState(); }

        public BlockState piso(int dx, int dz, double d, double a, int t) {
            if (t == 2) return ((int) d) % 6 == 0 ? cinzel : musgo;
            if (t == 1 || t == 3) return ((dx + dz) & 3) == 0 ? cinzel : polido;
            double trilha = Math.abs(Math.sin(a * 5 + d * 0.05)) * d;
            return trilha < 1.4 ? ladrilho : b(Blocks.GRASS_BLOCK);
        }

        public void enfeite(Kit k, BlockPos p, int dx, int dz, int t) {
            int h = (dx * 53 + dz * 29) & 63;
            if (k.L.getBlockState(p.below()).is(Blocks.GRASS_BLOCK)) {
                if (h == 0) k.s(p, b(Blocks.FLOWERING_AZALEA));
                else if (h < 6) k.s(p, b(Blocks.GRASS));
                else if (h == 7) k.s(p, b(Blocks.MOSS_CARPET));
            }
        }

        public void marco(ServerLevel level, Kit k, BlockPos c) {
            int H = 110;
            emFatias(level, H, 10, (y0, y1) -> {
                for (int y = y0; y < y1; y++) {
                    double r = 13 - y / (double) H * 7;
                    int ri = (int) Math.ceil(r);
                    for (int dx = -ri; dx <= ri; dx++) for (int dz = -ri; dz <= ri; dz++) {
                        double d = dist(dx, dz);
                        if (d > r) continue;
                        boolean sulco = d > r - 1 && Math.abs(Math.IEEEremainder(Math.atan2(dz, dx) + y * 0.02, Math.PI / 7)) < 0.08;
                        k.s(c.offset(dx, y, dz), sulco ? madeira : (y < 10 && d < r - 3) ? b(Blocks.AIR) : tronco);
                    }
                }
            });
            Obra.etapa(level, l -> {
                for (Direction d : Direction.Plane.HORIZONTAL) vaoGrande(k, c.relative(d, 7), d, 7, 2, 6); // a câmara dentro da raiz
                k.s(c.above(4), cristal);
                for (int i = 0; i < 14; i++) { // raízes
                    double a = Math.toRadians(i * (360 / 14.0) + 5);
                    for (int s = 0; s < 40; s++) {
                        double dd = 11 + s;
                        int y = (int) Math.round(10 - s * 0.45);
                        BlockPos p = c.offset((int) Math.round(Math.cos(a) * dd), Math.max(-1, y), (int) Math.round(Math.sin(a) * dd));
                        int g = s < 15 ? 2 : 1;
                        for (int g1 = 0; g1 < g; g1++) for (int g2 = 0; g2 < g; g2++) k.s(p.offset(g1, 0, g2), madeira);
                        if (y > 0) k.s(p.below(), madeira);
                    }
                }
            });
            for (int i = 0; i < 10; i++) { // galhos
                int f = i;
                Obra.etapa(level, l -> {
                    double a = Math.toRadians(f * 36 + 7);
                    int y0 = 70 + (f % 4) * 10;
                    for (int s = 0; s < 44; s++) {
                        BlockPos p = c.offset((int) Math.round(Math.cos(a) * (4 + s)), y0 + (int) (s * 0.55), (int) Math.round(Math.sin(a) * (4 + s)));
                        k.s(p, madeira);
                        if (s < 25) { k.s(p.above(), madeira); k.s(p.east(), madeira); }
                    }
                });
            }
            for (int y0 = 92; y0 < 150; y0 += 3) {
                int f = y0;
                Obra.etapa(level, l -> {
                    for (int y = f; y < Math.min(150, f + 3); y++) for (int dx = -66; dx <= 66; dx++) for (int dz = -66; dz <= 66; dz++) {
                        double e = (dx * dx + dz * dz) / (64.0 * 64.0) + (y - 118) * (y - 118) / (30.0 * 30.0);
                        if (e > 1.0) continue;
                        double ruido = ((dx * 73 + dz * 37 + y * 11) & 15) / 60.0;
                        if (e <= 1.0 - ruido && e > 0.72) k.s(c.offset(dx, y, dz), ((dx * 13 + dz * 7 + y) & 63) == 0 ? cristal : (((dx + dz + y) & 7) == 0 ? folhas2 : folhas));
                    }
                });
            }
            Obra.etapa(level, l -> { // lágrimas de luz penduradas da copa
                for (int i = 0; i < 90; i++) {
                    double r = 16 + (i % 6) * 8;
                    // o fundo da copa (onde a casca começa) acima desse raio
                    int topo = (int) Math.round(118 - 30 * Math.sqrt(Math.max(0, 0.72 - (r * r) / (64.0 * 64.0))));
                    BlockPos p = noCirculo(c, r, i * 23).above(Math.max(92, topo));
                    int n = 4 + i % 9;
                    for (int y = 0; y < n; y++) k.s(p.below(y), b(Blocks.CHAIN));
                    k.s(p.below(n), cristal);
                }
            });
        }

        public void torre(Kit k, BlockPos t, int i, BlockPos c) {
            int y = 0;
            for (int nivel = 0; nivel < 6; nivel++) {
                int r = 11 - nivel * 2 + (nivel == 5 ? 1 : 0);
                for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int h = 0; h < 5; h++)
                    k.s(t.offset(dx, y + h, dz), (Math.abs(dx) == r || Math.abs(dz) == r) ? (h == 2 && (dx + dz) % 2 == 0 ? cinzel : templo()) : (h == 0 ? polido : b(Blocks.AIR)));
                y += 5;
            }
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int h = 0; h < 4; h++) k.s(t.offset(dx, y + h, dz), (Math.abs(dx) == 2 && Math.abs(dz) == 2) ? ametista : b(Blocks.AIR));
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) k.s(t.offset(dx, y + 4, dz), templo());
            k.s(t.above(y), cristal);
            Direction frente = paraOCentro(c, t);
            // escadaria pela frente até o santuário
            for (int s = 0; s < y; s++) for (int w = -2; w <= 2; w++)
                k.s(t.relative(frente, 12 - (s * 2) / 5).relative(frente.getClockWise(), w).above(s), Kit.degrau(Blocks.DEEPSLATE_TILE_STAIRS, frente.getOpposite()));
            vaoGrande(k, t.relative(frente, 11), frente.getOpposite(), 2, 1, 4);
        }

        public void predio(Kit k, BlockPos p, Direction frente, int i) { // casa longa sobre estacas
            boolean ns = frente.getAxis() == Direction.Axis.Z;
            int rx = ns ? 7 : 4, rz = ns ? 4 : 7;
            BlockPos h = p.above(4);
            for (int dx = -rx; dx <= rx; dx += rx) for (int dz = -rz; dz <= rz; dz += rz) for (int y = 0; y < 4; y++) k.s(p.offset(dx, y, dz), tronco);
            for (int dx = -rx - 1; dx <= rx + 1; dx++) for (int dz = -rz - 1; dz <= rz + 1; dz++) k.s(h.offset(dx, -1, dz), b(Blocks.MANGROVE_PLANKS));
            caixa(k, h, rx, rz, 4, b(Blocks.MANGROVE_PLANKS), b(Blocks.AIR));
            for (int t = 0; t < 6; t++) for (int dx = -rx - 2 + t; dx <= rx + 2 - t; dx++) for (int dz = -rz - 2 + t; dz <= rz + 2 - t; dz++)
                if (Math.abs(dx) == rx + 2 - t || Math.abs(dz) == rz + 2 - t || t == 5) k.s(h.offset(dx, 3 + t, dz), roxo);
            int fr = ns ? rz : rx;
            vao(k, h.relative(frente, fr), frente, 1);
            for (int s = 0; s < 5; s++) for (int w = -1; w <= 1; w++) k.s(p.relative(frente, fr + 1 + s).relative(frente.getClockWise(), w).above(3 - s), Kit.degrau(Blocks.MANGROVE_STAIRS, frente.getOpposite()));
            cama(k, h, frente, Blocks.PURPLE_BED, cristal, 3);
        }

        public void casa(Kit k, BlockPos h0, Direction frente, int i) {
            BlockPos h = h0.above(3);
            for (int dx : new int[]{-3, 3}) for (int dz : new int[]{-3, 3}) for (int y = 0; y < 3; y++) k.s(h0.offset(dx, y, dz), tronco);
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) k.s(h.offset(dx, -1, dz), b(Blocks.MANGROVE_PLANKS));
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int y = 0; y < 3; y++)
                k.s(h.offset(dx, y, dz), (Math.abs(dx) == 3 || Math.abs(dz) == 3) ? (y == 1 && (dx + dz) % 2 == 0 ? b(Blocks.PURPLE_STAINED_GLASS) : b(Blocks.MANGROVE_PLANKS)) : b(Blocks.AIR));
            for (int t = 0; t < 5; t++) for (int dx = -5 + t; dx <= 5 - t; dx++) for (int dz = -5 + t; dz <= 5 - t; dz++)
                if (Math.abs(dx) == 5 - t || Math.abs(dz) == 5 - t) k.s(h.offset(dx, 3 + t, dz), roxo);
            vao(k, h.relative(frente, 3), frente, 1);
            for (int s = 0; s < 4; s++) k.s(h0.relative(frente, 5 + s).above(2 - Math.min(2, s)), Kit.degrau(Blocks.MANGROVE_STAIRS, frente.getOpposite()));
            cama(k, h, frente, Blocks.PURPLE_BED, cristal, 3);
        }

        public void portao(Kit k, BlockPos g, Direction d) {
            arco(k, g, d, 11, 1.5, madeira, cristal);
            Direction lado = d.getClockWise();
            for (int w = -8; w <= 8; w += 4) {
                BlockPos p = g.relative(lado, w).above((int) (Math.sqrt(Math.max(0, 9.5 * 9.5 - w * w)) * 1.5) - 1);
                for (int y = 0; y < 3; y++) k.s(p.below(y), b(Blocks.CHAIN));
                k.s(p.below(3), cristal);
            }
        }

        public void muro(Kit k, BlockPos p, int i, boolean fora) {
            for (int y = 0; y < 4; y++) k.s(p.above(y), y == 0 ? musgo : folhas2);
            if (fora && i % 30 == 0) { for (int y = 0; y < 9; y++) k.s(p.above(y), tronco); k.s(p.above(9), cristal); }
        }

        public void poste(Kit k, BlockPos p, int i) {
            for (int y = 0; y < 5; y++) k.s(p.above(y), tronco);
            k.s(p.above(5), folhas);
            k.s(p.above(4).east(), b(Blocks.CHAIN)); k.s(p.above(3).east(), cristal);
        }
    }

    // =====================================================================================================
    // ZAMARON · Zaëlith · O Jardim de Cristal (tudo em pares)
    // =====================================================================================================

    static final class Zamaron implements Estilo {
        final BlockState calcita = b(Blocks.CALCITE), ametista = b(Blocks.AMETHYST_BLOCK), magenta = b(Blocks.MAGENTA_STAINED_GLASS), rosa = b(Blocks.PINK_STAINED_GLASS),
                luz = b(Blocks.PEARLESCENT_FROGLIGHT), purpura = b(Blocks.PURPUR_BLOCK), pilar = eixo(Blocks.PURPUR_PILLAR, Direction.Axis.Y), quartzo = b(Blocks.SMOOTH_QUARTZ);
        final Block[] flores = {Blocks.PINK_TULIP, Blocks.ALLIUM, Blocks.LILAC, Blocks.PEONY, Blocks.WHITE_TULIP, Blocks.ROSE_BUSH};

        public BlockState piso(int dx, int dz, double d, double a, int t) {
            if (t == 2) { double petala = Math.abs(Math.cos(a * 4)) * 10 + 22; return Math.abs(d - petala) < 0.8 ? ametista : ((dx + dz) & 1) == 0 ? calcita : quartzo; }
            if (t == 1) return distAvenida(dx, dz) < 0.7 ? rosa : ((dx + dz) & 1) == 0 ? calcita : quartzo;
            if (t == 3) return ametista;
            double petala = Math.abs(Math.cos(a * 16)) * 8;
            return ((int) (d + petala)) % 14 == 0 ? ametista : ((dx * 5 + dz * 3) & 7) < 6 ? b(Blocks.GRASS_BLOCK) : calcita;
        }

        public void enfeite(Kit k, BlockPos p, int dx, int dz, int t) {
            if (!k.L.getBlockState(p.below()).is(Blocks.GRASS_BLOCK) || ((dx * 7 + dz * 11) & 3) != 0) return;
            Block fl = flores[Math.floorMod(dx * 3 + dz, flores.length)];
            if (fl == Blocks.LILAC || fl == Blocks.PEONY || fl == Blocks.ROSE_BUSH) {
                k.s(p, fl.defaultBlockState()); k.s(p.above(), fl.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
            } else k.s(p, fl.defaultBlockState());
        }

        void agulha(Kit k, BlockPos t, int h, double r0) {
            for (int y = 0; y < h; y++) {
                double r = r0 - y / (double) h * (r0 - 1.2);
                int ri = (int) Math.ceil(r);
                for (int dx = -ri; dx <= ri; dx++) for (int dz = -ri; dz <= ri; dz++) {
                    double d = dist(dx, dz);
                    if (d > r) continue;
                    k.s(t.offset(dx, y, dz), d > r - 1 ? (y % 8 == 0 ? luz : (Math.abs(dx) == Math.abs(dz) ? pilar : magenta)) : purpura);
                }
            }
            for (int y = 0; y < 7; y++) k.s(t.above(h + y), y == 6 ? luz : ametista);
        }

        void ponte(Kit k, BlockPos a, BlockPos z, int y, int barriga) {
            int n = (int) (Math.sqrt(a.distSqr(z)) * 2);
            for (int s = 0; s <= n; s++) {
                double t = s / (double) n;
                double x = a.getX() + (z.getX() - a.getX()) * t, zz = a.getZ() + (z.getZ() - a.getZ()) * t;
                int yy = (int) Math.round(y + Math.sin(t * Math.PI) * barriga);
                k.s(new BlockPos((int) Math.round(x), a.getY() + yy, (int) Math.round(zz)), s % 8 == 0 ? luz : rosa);
                k.s(new BlockPos((int) Math.round(x), a.getY() + yy - 1, (int) Math.round(zz)), ametista);
            }
        }

        public void marco(ServerLevel level, Kit k, BlockPos c) {
            Obra.etapa(level, l -> { for (int t = 0; t < 8; t++) disco(k, c.above(t), 30 - t * 2.5, t % 2 == 0 ? purpura : calcita); });
            // o Coração de Cristal (≈100 x 100)
            for (int x0 = -54; x0 <= 54; x0 += 12) {
                int f = x0;
                Obra.etapa(level, l -> {
                    for (int x = f; x < Math.min(55, f + 12); x++) for (int y = -54; y <= 54; y++) {
                        double X = x / 46.0, Y = -y / 46.0 + 0.25;
                        double v = Math.pow(X * X + Y * Y - 1, 3) - X * X * Y * Y * Y;
                        if (v > 0) continue;
                        double X2 = X * 1.05, Y2 = Y * 1.05;
                        double borda = Math.pow(X2 * X2 + Y2 * Y2 - 1, 3) - X2 * X2 * Y2 * Y2 * Y2;
                        double esp = 4.5 * Math.sqrt(Math.min(1, -v * 4)) + 1;
                        for (int z = (int) -esp; z <= esp; z++) {
                            BlockState s = borda > 0 ? ametista : Math.abs(z) >= (int) esp ? magenta : (((x + y) & 7) == 0 ? luz : rosa);
                            k.s(c.offset(x, 66 - y, z), s);
                        }
                    }
                });
            }
            Obra.etapa(level, l -> agulha(k, c.offset(-50, 0, 0), 150, 7));
            Obra.etapa(level, l -> agulha(k, c.offset(50, 0, 0), 150, 7));
            Obra.etapa(level, l -> { ponte(k, c.offset(-50, 0, 0), c.offset(50, 0, 0), 132, 12); });
        }

        public void torre(Kit k, BlockPos t, int i, BlockPos c) {
            double ang = Math.atan2(t.getZ() - c.getZ(), t.getX() - c.getX());
            int dx = (int) Math.round(-Math.sin(ang) * 9), dz = (int) Math.round(Math.cos(ang) * 9);
            BlockPos a = t.offset(dx, 0, dz), z = t.offset(-dx, 0, -dz);
            int h = 60 + (i % 4) * 6;
            disco(k, t.below(), 15, calcita);
            agulha(k, a, h, 4.2);
            agulha(k, z, h, 4.2);
            ponte(k, a, z, h - 14, 6);
            ponte(k, a, z, h / 2, 4);
        }

        public void predio(Kit k, BlockPos p, Direction frente, int i) { // pavilhão octogonal
            for (int y = 0; y < 10; y++) for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
                double d = Math.max(Math.max(Math.abs(dx), Math.abs(dz)), (Math.abs(dx) + Math.abs(dz)) * 0.72);
                if (d > 7) continue;
                BlockPos q = p.offset(dx, y, dz);
                if (d > 6.2) k.s(q, (Math.abs(dx) == Math.abs(dz) || y == 0 || y == 9) ? ametista : y % 3 == 1 ? luz : magenta);
                else k.s(q, y == 9 ? calcita : b(Blocks.AIR));
            }
            cupula(k, p.above(10), 6, 1.0, rosa, null, -1);
            k.s(p.above(10), luz);
            vaoGrande(k, p.relative(frente, 6), frente, 2, 1, 4);
            cama(k, p.relative(frente.getClockWise(), 2), frente, Blocks.PINK_BED, null, 0);
            cama(k, p.relative(frente.getCounterClockWise(), 2), frente, Blocks.MAGENTA_BED, luz, 6);
        }

        public void casa(Kit k, BlockPos h, Direction frente, int i) { // duas cúpulas: o par
            Direction lado = frente.getClockWise();
            for (int s : new int[]{-1, 1}) {
                BlockPos q = h.relative(lado, s * 4);
                cupula(k, q, 4.5, 1.2, calcita, magenta, 2);
                vao(k, q.relative(frente, 3), frente, 2);
                k.s(q.above(3), luz);
            }
            k.cama(Blocks.MAGENTA_BED, h.relative(lado, -4).relative(frente.getOpposite()), frente.getOpposite());
            k.cama(Blocks.PINK_BED, h.relative(lado, 4).relative(frente.getOpposite()), frente.getOpposite());
            for (int y = 0; y < 5; y++) k.s(h.relative(frente, 3).above(y), y == 4 ? luz : ametista);
        }

        public void portao(Kit k, BlockPos g, Direction d) {
            Direction lado = d.getClockWise();
            agulha(k, g.relative(lado, 11), 34, 3);
            agulha(k, g.relative(lado, -11), 34, 3);
            arco(k, g, d, 9, 2.2, ametista, luz);
        }

        public void muro(Kit k, BlockPos p, int i, boolean fora) {
            for (int y = 0; y < 3; y++) k.s(p.above(y), calcita);
            if (fora) k.s(p.above(3), i % 10 == 0 ? luz : rosa);
        }

        public void poste(Kit k, BlockPos p, int i) {
            for (int y = 0; y < 5; y++) k.s(p.above(y), y == 4 ? luz : ametista);
            k.s(p.above(5), b(Blocks.AMETHYST_CLUSTER));
        }
    }

    // =====================================================================================================
    // RYUT · Silentes · A Necrópole Silenciosa
    // =====================================================================================================

    static final class Ryut implements Estilo {
        final BlockState negro = b(Blocks.POLISHED_BLACKSTONE), tijolo = b(Blocks.POLISHED_BLACKSTONE_BRICKS), osso = eixo(Blocks.BONE_BLOCK, Direction.Axis.Y),
                ossoX = eixo(Blocks.BONE_BLOCK, Direction.Axis.X), ossoZ = eixo(Blocks.BONE_BLOCK, Direction.Axis.Z), alma = b(Blocks.SOUL_LANTERN),
                fogo = b(Blocks.SOUL_CAMPFIRE), chorando = b(Blocks.CRYING_OBSIDIAN), cinzel = b(Blocks.CHISELED_POLISHED_BLACKSTONE), obsidiana = b(Blocks.OBSIDIAN);

        BlockState gelo() { return LoreBlocks.GELO_NEGRO_UMBRALL.get().defaultBlockState(); }

        public BlockState piso(int dx, int dz, double d, double a, int t) {
            if (t == 2) return ((int) d) % 8 == 0 ? chorando : ((dx + dz) & 1) == 0 ? negro : tijolo;
            if (t == 1) return distAvenida(dx, dz) > 3.6 ? osso : ((int) d) % 9 == 0 ? cinzel : negro;
            if (t == 3) return tijolo;
            return ((dx * 3 + dz * 5) & 15) == 0 ? b(Blocks.SOUL_SOIL) : gelo();
        }

        public void enfeite(Kit k, BlockPos p, int dx, int dz, int t) {
            int h = (dx * 61 + dz * 23) & 255;
            if (h == 0) k.s(p, fogo);
            else if (h == 1) { k.s(p, tijolo); k.s(p.above(), cinzel); } // lápide
        }

        public void marco(ServerLevel level, Kit k, BlockPos c) {
            int H = 170; // o Obelisco do Silêncio no centro
            emFatias(level, H, 15, (y0, y1) -> {
                for (int y = y0; y < y1; y++) {
                    int r = y < 6 ? 6 : y < 120 ? 4 : y < 150 ? 3 : 2;
                    for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                        boolean canto = Math.abs(dx) == r && Math.abs(dz) == r;
                        k.s(c.offset(dx, y, dz), y % 20 == 19 ? chorando : canto ? obsidiana : tijolo);
                    }
                }
            });
            Obra.etapa(level, l -> {
                k.s(c.above(H), cinzel); k.s(c.above(H + 1), fogo);
                for (Direction d : Direction.Plane.HORIZONTAL) for (int y = 0; y < 3; y++) k.s(c.relative(d, 8).above(y), y == 2 ? alma : tijolo);
            });
            // o Colosso: costelas e espinha de um gigante morto, de norte a sul
            for (int i = -9; i <= 9; i++) {
                int f = i;
                if (Math.abs(f) <= 1) continue;
                Obra.etapa(level, l -> {
                    BlockPos base = c.offset(0, 0, f * 9);
                    int raio = 46 - Math.abs(f) * 4;
                    int n = (int) (Math.PI * raio * 3);
                    for (int g = 0; g <= n; g++) {
                        double a = Math.PI * g / n;
                        double rr = raio + Math.sin(a * 2) * 3;
                        for (int esp = 0; esp < 2; esp++) for (int z = 0; z < 2; z++)
                            k.s(base.offset((int) Math.round(Math.cos(a) * (rr + esp)), (int) Math.round(Math.sin(a) * (rr + esp) * 1.25), z), osso);
                    }
                });
            }
            Obra.etapa(level, l -> {
                for (int z = -84; z <= 84; z++) {
                    int f = Math.round(z / 9f);
                    int raio = Math.max(10, 46 - Math.abs(f) * 4);
                    int y = (int) Math.round(raio * 1.25) + 2;
                    if (Math.abs(f) <= 1) y = (int) Math.round(42 * 1.25) + 2;
                    for (int w = -1; w <= 1; w++) for (int h = 0; h < 2; h++) k.s(c.offset(w, y + h, z), ossoZ);
                    if ((z & 3) == 0) for (int h = 2; h < 5; h++) k.s(c.offset(0, y + h, z), osso);
                }
                // o crânio no norte
                BlockPos cr = c.offset(0, 0, -104);
                cupula(k, cr, 20, 0.8, osso, null, -1);
                for (int s : new int[]{-8, 8}) for (int y = 9; y <= 14; y++) for (int w = -2; w <= 2; w++) { k.s(cr.offset(s + w, y, 17), chorando); k.s(cr.offset(s + w, y, 16), b(Blocks.AIR)); }
                vaoGrande(k, cr.offset(0, 0, 19), Direction.NORTH, 40, 4, 8);
            });
        }

        public void torre(Kit k, BlockPos t, int i, BlockPos c) {
            int[] ox = {0, 6, -6, 0}, oz = {0, 0, 0, 6};
            disco(k, t.below(), 11, tijolo);
            for (int o = 0; o < 4; o++) {
                int h = o == 0 ? 60 + (i * 7) % 16 : 30 + (o * 9 + i * 5) % 14;
                BlockPos b0 = t.offset(ox[o], 0, oz[o]);
                for (int y = 0; y < h; y++) {
                    int r = y < 3 ? 2 : 1;
                    for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) k.s(b0.offset(x, y, z), y % 10 == 9 ? chorando : tijolo);
                }
                k.s(b0.above(h), cinzel); k.s(b0.above(h + 1), fogo);
            }
            for (Direction d : Direction.Plane.HORIZONTAL) k.s(t.relative(d, 10), alma);
        }

        public void predio(Kit k, BlockPos p, Direction frente, int i) { // mausoléu grande
            boolean ns = frente.getAxis() == Direction.Axis.Z;
            int rx = ns ? 6 : 9, rz = ns ? 9 : 6, h = 10;
            for (int dx = -rx; dx <= rx; dx++) for (int dz = -rz; dz <= rz; dz++) for (int y = 0; y < h; y++) {
                boolean borda = Math.abs(dx) == rx || Math.abs(dz) == rz, canto = Math.abs(dx) == rx && Math.abs(dz) == rz;
                k.s(p.offset(dx, y, dz), canto ? osso : borda ? (y > 2 && y < 7 && ((dx + dz) & 3) == 0 ? b(Blocks.IRON_BARS) : tijolo) : b(Blocks.AIR));
            }
            int m = Math.min(rx, rz);
            for (int t = 0; t <= m + 1; t++) for (int dx = -rx - 1 + t; dx <= rx + 1 - t; dx++) for (int dz = -rz - 1 + t; dz <= rz + 1 - t; dz++)
                if (Math.abs(dx) == rx + 1 - t || Math.abs(dz) == rz + 1 - t || t == m + 1) k.s(p.offset(dx, h + t, dz), negro);
            int fr = ns ? rz : rx;
            vaoGrande(k, p.relative(frente, fr), frente, 1, 1, 4);
            k.s(p.relative(frente, fr + 1).above(4), alma);
            for (int j = -1; j <= 1; j++) k.s(p.relative(frente.getClockWise(), j * 3).relative(frente, -3), b(Blocks.ANVIL));
            cama(k, p, frente, Blocks.BLACK_BED, alma, 6);
        }

        public void casa(Kit k, BlockPos h, Direction frente, int i) {
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int y = 0; y < 5; y++) {
                boolean borda = Math.abs(dx) == 4 || Math.abs(dz) == 4, canto = Math.abs(dx) == 4 && Math.abs(dz) == 4;
                k.s(h.offset(dx, y, dz), canto ? osso : borda ? tijolo : b(Blocks.AIR));
            }
            for (int t = 0; t < 5; t++) for (int dx = -5 + t; dx <= 5 - t; dx++) for (int dz = -5 + t; dz <= 5 - t; dz++)
                if (Math.abs(dx) == 5 - t || Math.abs(dz) == 5 - t) k.s(h.offset(dx, 5 + t, dz), negro);
            vao(k, h.relative(frente, 4), frente, 1);
            k.s(h.relative(frente, 5).above(3), alma);
            cama(k, h, frente, Blocks.BLACK_BED, alma, 4);
            k.s(h.relative(frente.getClockWise(), 2), b(Blocks.ANVIL));
        }

        public void portao(Kit k, BlockPos g, Direction d) {
            arco(k, g, d, 12, 1.5, d.getAxis() == Direction.Axis.Z ? ossoX : ossoZ, fogo);
            Direction lado = d.getClockWise();
            for (int s : new int[]{-1, 1}) for (int y = 0; y < 24; y++) k.s(g.relative(lado, s * 15).above(y), y == 23 ? fogo : y % 8 == 7 ? chorando : tijolo);
        }

        public void muro(Kit k, BlockPos p, int i, boolean fora) {
            for (int y = 0; y < 12; y++) k.s(p.above(y), y % 6 == 5 ? cinzel : tijolo);
            if (fora) k.s(p.above(12), i % 14 == 0 ? alma : b(Blocks.POLISHED_BLACKSTONE_WALL));
        }

        public void poste(Kit k, BlockPos p, int i) {
            for (int y = 0; y < 4; y++) k.s(p.above(y), b(Blocks.POLISHED_BLACKSTONE_WALL));
            k.s(p.above(4), alma);
        }
    }

    private CidadesPovos() {}
}
