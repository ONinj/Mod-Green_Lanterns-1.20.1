package com.lanterna.cidade;

import com.lanterna.LanternaMod;
import com.lanterna.lore.PlanetLore;
import com.lanterna.lore.entity.Race;
import com.lanterna.lore.entity.RaceEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.lanterna.cidade.Kit.b;

/**
 * A megacidade dos nativos de cada planeta das Tropas, a oeste da base dos Lanternas (centro em -480, 0): um disco de
 * 400 blocos com a arquitetura do povo do planeta (veja {@link CidadesPovos}).
 *
 * A primeira versão (0.25) ficava em -210, 0 e era bem menor; em mundos antigos ela é desmontada e os nativos dela
 * se mudam para a cidade nova.
 */
public final class CidadeNativa {
    public static BlockPos CENTRO = new BlockPos(-480, 0, 0);
    public static final int R = CidadesPovos.R;
    /** A cidade antiga (0.25). */
    public static final BlockPos ANTIGA = new BlockPos(-210, 0, 0);
    private static final int R_ANTIGA = 58;

    /** Constrói a cidade nova. Mede o chão em etapas (carregar 400x400 blocos de uma vez travaria o servidor). */
    public static void construir(ServerLevel level, PlanetLore planet) {
        int r = CidadesPovos.R;
        int[] medidas = {0, 0, 0};
        java.util.List<net.minecraft.world.level.ChunkPos> pedacos = new java.util.ArrayList<>();
        java.util.List<int[]> pontos = new java.util.ArrayList<>();
        for (int dx = -r; dx <= r; dx += 40) for (int dz = -r; dz <= r; dz += 40) {
            if (dx * dx + dz * dz > r * r) continue;
            int x = CENTRO.getX() + dx, z = CENTRO.getZ() + dz;
            pedacos.add(new net.minecraft.world.level.ChunkPos(x >> 4, z >> 4));
            pontos.add(new int[]{x, z});
        }
        Obra.carregar(level, pedacos);
        Obra.etapa(level, l -> { for (int[] q : pontos) { medidas[0] += l.getHeight(Heightmap.Types.OCEAN_FLOOR, q[0], q[1]); medidas[2] += l.getHeight(Heightmap.Types.WORLD_SURFACE, q[0], q[1]); medidas[1]++; } });
        Obra.etapa(level, l -> {
            int y = CidadesPovos.chaoDe(l, planet, medidas[0] / Math.max(1, medidas[1]), medidas[2] / Math.max(1, medidas[1]));
            LanternaMod.LOGGER.info("Lanterna: construindo a cidade do povo de {} (chão {})", planet.planet, y);
            CidadesPovos.construir(l, planet, CENTRO.atY(y));
        });
    }

    /** Mundos que têm a cidade antiga: desmonta a antiga, muda os nativos e constrói a nova. */
    public static void refazer(ServerLevel level, PlanetLore planet) {
        int y = Moradores.alturaCidade(level, ANTIGA);
        BlockPos c = ANTIGA.atY(y);
        for (int x0 = -R_ANTIGA - 1; x0 <= R_ANTIGA + 1; x0 += 8) {
            int f = x0;
            Obra.etapa(level, l -> {
                var m = new BlockPos.MutableBlockPos();
                for (int dx = f; dx <= Math.min(R_ANTIGA + 1, f + 7); dx++) for (int dz = -R_ANTIGA - 1; dz <= R_ANTIGA + 1; dz++) {
                    if (dx * dx + dz * dz > (R_ANTIGA + 1) * (R_ANTIGA + 1)) continue;
                    int x = c.getX() + dx, z = c.getZ() + dz;
                    l.getChunk(x >> 4, z >> 4);
                    int topo = l.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                    for (int yy = c.getY(); yy < topo; yy++) { m.set(x, yy, z); if (!l.getBlockState(m).isAir()) l.setBlock(m, b(Blocks.AIR), 2); }
                }
            });
        }
        construir(level, planet);
        Obra.etapa(level, l -> { // os nativos da cidade antiga se mudam
            Race race = Race.ofPlanet(planet.planet);
            if (race == null) return;
            BlockPos novo = CENTRO.atY(altura(l));
            List<BlockPos> casas = CidadesPovos.casas(novo);
            int i = 0;
            for (RaceEntity e : l.getEntitiesOfClass(RaceEntity.class, new AABB(c).inflate(R_ANTIGA + 20, 400, R_ANTIGA + 20), e -> e.race() == race && e.role().isEmpty())) {
                BlockPos casa = casas.get((i++ * 7) % casas.size());
                l.getChunkAt(casa);
                e.teleportTo(casa.getX() + 0.5, casa.getY(), casa.getZ() + 0.5);
                e.restrictTo(casa, 24);
            }
        });
    }

    /**
     * Altura do chão da cidade: o chão das avenidas (leste, oeste e diagonais) longe do monumento; vale a medida que
     * mais se repete.
     */
    public static int altura(ServerLevel level) {
        PlanetLore pl0 = PlanetLore.byDimension(level.dimension());
        if (Layout.novo() && pl0 != null && pl0 != PlanetLore.GENESE) return CidadeUnica.planta(level, pl0).y;
        Map<Integer, Integer> conta = new HashMap<>();
        for (int ang : new int[]{0, 45, 135, 180, 225, 315}) for (int d : new int[]{84, 92}) {
            double a = Math.toRadians(ang);
            int x = CENTRO.getX() + (int) Math.round(Math.cos(a) * d), z = CENTRO.getZ() + (int) Math.round(Math.sin(a) * d);
            level.getChunk(x >> 4, z >> 4);
            conta.merge(level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z), 1, Integer::sum);
        }
        return conta.entrySet().stream().max(Map.Entry.<Integer, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).orElse(64);
    }

    /**
     * Repõe os nativos da cidade. Só conta e só cria nas casas que estão carregadas agora (a cidade tem 400 blocos e
     * quem está longe não é visto: contar a cidade inteira fazia nascer gente a mais a cada visita). A meta é
     * proporcional às casas carregadas; quem sobra além de 30% da meta vai embora.
     */
    public static int nativos(ServerLevel l, BlockPos c, PlanetLore planet, int quantos) {
        Race race = Race.ofPlanet(planet.planet);
        if (race == null) return 0;
        List<BlockPos> casas;
        if (Layout.novo()) {
            // cidade única: as casas vêm da planta (com a altura do chão da cidade)
            CidadeUnica.Planta pl = CidadeUnica.planta(l, planet);
            casas = new java.util.ArrayList<>();
            for (BlockPos h : pl.casas) casas.add(h.atY(pl.y));
        } else {
            CidadesPovos.lembrar(l, planet, c);
            casas = new java.util.ArrayList<>(CidadesPovos.casas(c));
            casas.addAll(CidadesPovos.casasExtras(c));
        }
        List<BlockPos> carregadas = new java.util.ArrayList<>();
        for (BlockPos h : casas) if (l.isPositionEntityTicking(h)) carregadas.add(h);
        if (carregadas.isEmpty()) return 0;
        if (planet == PlanetLore.NOK) quantos = Math.round(quantos * 1.6f); // Nok: cidade dos Ves'Kai mais cheia
        int meta = (int) Math.ceil(quantos * (double) carregadas.size() / casas.size());
        AABB area = new AABB(c.getX() - R, l.getMinBuildHeight(), c.getZ() - R, c.getX() + R, l.getMaxBuildHeight(), c.getZ() + R);
        List<RaceEntity> tem = l.getEntitiesOfClass(RaceEntity.class, area, e -> e.isAlive() && e.race() == race && e.role().isEmpty());
        int teto = (int) Math.ceil(meta * 1.3) + 2;
        for (int i = teto; i < tem.size(); i++) tem.get(i).discard();
        int n = 0;
        for (int i = 0; tem.size() + n < meta && i < meta * 2; i++) {
            BlockPos casa = carregadas.get((i * 7) % carregadas.size());
            BlockPos at = casa.offset(i % 2 == 0 ? 1 : -1, planet == PlanetLore.NOK ? 3 : 0, i % 3 == 0 ? 1 : -1);
            if (com.lanterna.lore.world.PlanetSites.nativo(l, race, at) != null) n++;
        }
        return n;
    }

    private CidadeNativa() {}
}
