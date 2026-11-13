package com.lanterna.cidade;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.lore.LoreWorldData;
import com.lanterna.lore.PlanetLore;
import com.lanterna.lore.entity.RaceEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vigia dos moradores fixos das cidades e do Palácio dos Guardiões: os NPCs de missão (Comandante, Instrutor,
 * Capitães, Anciões do Conselho...) só nasciam uma vez, quando o lugar era construído. Se um deles morresse, caísse
 * ou sumisse, a missão ficava sem ninguém para conversar. Agora, quando um jogador está perto, a lista de moradores
 * é conferida e quem faltar nasce de novo no lugar dele.
 *
 * A lista vem das mesmas funções que constroem os lugares, rodadas em "modo censo" (só anotam, não criam nada).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Moradores {
    /** Um morador esperado: tipo "npc" (LanternaEntity da cidade) ou "guardiao" (Guardião do Conselho). */
    public record Morador(String tipo, BlockPos pos, String funcao, BuiltinCorps corps, boolean oficial, float yaw, int raio) {}

    @Nullable private static List<Morador> censo;

    /** Em modo censo, quem cria NPCs só anota (e não cria). */
    public static boolean censo() {
        return censo != null;
    }

    static void anotar(Morador m) {
        if (censo != null) censo.add(m);
    }

    private static final Map<String, Long> ULTIMA = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 100 != 37) return;
        ServerLevel level = p.serverLevel();
        PlanetLore planet = PlanetLore.byDimension(level.dimension());
        if (planet == null || planet == PlanetLore.GENESE) return;
        LoreWorldData data = LoreWorldData.get(level.getServer());
        // cidade única: nada é construído com o servidor ligado, então uma obra em outro lugar (a Cidade Aurora num
        // mundo plano, por exemplo) não segura os moradores
        if (!Layout.novo() && Obra.ocupada()) return;
        if (Layout.novo()) {
            // cidade única (0.48): tudo já nasce com o mundo; aqui só entram (e voltam) os moradores
            if (planet == PlanetLore.OA && perto(p, Palacio.CENTRO, 110)) conferir(level, "oa:palacio", () -> Palacio.censo(level));
            if (perto(p, PlanetLore.HUB, 80)) {
                String chave = level.dimension().location() + "|hub";
                if (level.getGameTime() - ULTIMA.getOrDefault(chave, -100000L) >= 600) {
                    ULTIMA.put(chave, level.getGameTime());
                    int n = com.lanterna.lore.world.PlanetSites.vigiarBase(level, planet);
                    if (n > 0) LanternaMod.LOGGER.info("Lanterna: {} NPC(s) da Bateria Central voltaram em {}", n, planet.planet);
                }
            }
            if (perto(p, CidadeNativa.CENTRO, 250)) {
                String chave = level.dimension().location() + "|nativa";
                if (level.getGameTime() - ULTIMA.getOrDefault(chave, -100000L) >= 600) {
                    ULTIMA.put(chave, level.getGameTime());
                    int n = CidadeNativa.nativos(level, CidadeNativa.CENTRO.atY(CidadeNativa.altura(level)), planet, 105);
                    if (n > 0) LanternaMod.LOGGER.info("Lanterna: {} nativo(s) chegaram à cidade de {}", n, planet.planet);
                }
            }
            if (perto(p, Cidade.CENTRO, 110)) conferir(level, planet.planet + ":cidade2", () -> Cidade.censo(level, planet));
            return;
        }
        if (planet == PlanetLore.OA && data.siteBuilt("oa:palacio") && perto(p, Palacio.CENTRO, 110)) {
            conferir(level, "oa:palacio", () -> Palacio.censo(level));
        }
        if (data.siteBuilt(planet.planet + ":hub") && perto(p, PlanetLore.HUB, 80)) {
            String chave = level.dimension().location() + "|hub";
            if (level.getGameTime() - ULTIMA.getOrDefault(chave, -100000L) >= 600) {
                ULTIMA.put(chave, level.getGameTime());
                int n = com.lanterna.lore.world.PlanetSites.vigiarBase(level, planet);
                if (n > 0) LanternaMod.LOGGER.info("Lanterna: {} NPC(s) da base voltaram em {}", n, planet.planet);
            }
        }
        if (data.siteBuilt(planet.planet + ":nativa") && data.siteBuilt(planet.planet + ":megacidade") && !Obra.ocupada() && perto(p, CidadeNativa.CENTRO, 240)) {
            String chave = level.dimension().location() + "|nativa";
            if (level.getGameTime() - ULTIMA.getOrDefault(chave, -100000L) >= 600) {
                ULTIMA.put(chave, level.getGameTime());
                int n = CidadeNativa.nativos(level, CidadeNativa.CENTRO.atY(CidadeNativa.altura(level)), planet, 75);
                if (n > 0) LanternaMod.LOGGER.info("Lanterna: {} nativo(s) voltaram à cidade de {}", n, planet.planet);
            }
        }
        if (data.siteBuilt(planet.planet + ":cidade2") && perto(p, Cidade.CENTRO, 110)) {
            conferir(level, planet.planet + ":cidade2", () -> Cidade.censo(level, planet));
        }
    }

    private static boolean perto(ServerPlayer p, BlockPos c, int r) {
        double dx = p.getX() - c.getX(), dz = p.getZ() - c.getZ();
        return dx * dx + dz * dz < r * r;
    }

    private static void conferir(ServerLevel level, String lugar, Runnable gerarLista) {
        long agora = level.getGameTime();
        String chave = level.dimension().location() + "|" + lugar;
        if (agora - ULTIMA.getOrDefault(chave, -100000L) < 600) return; // no máximo a cada 30 s
        ULTIMA.put(chave, agora);
        List<Morador> lista = new ArrayList<>();
        censo = lista;
        try {
            gerarLista.run();
        } catch (Exception e) {
            LanternaMod.LOGGER.warn("Lanterna: falha no censo de {}", lugar, e);
            return;
        } finally {
            censo = null;
        }
        // Conta por função na área inteira (os moradores andam pela cidade): só nasce quem falta de verdade
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Morador m : lista) {
            minX = Math.min(minX, m.pos().getX()); maxX = Math.max(maxX, m.pos().getX());
            minZ = Math.min(minZ, m.pos().getZ()); maxZ = Math.max(maxZ, m.pos().getZ());
            minY = Math.min(minY, m.pos().getY()); maxY = Math.max(maxY, m.pos().getY());
        }
        AABB area = new AABB(minX - 40, level.getMinBuildHeight(), minZ - 40, maxX + 40, level.getMaxBuildHeight(), maxZ + 40);
        Map<String, Integer> esperados = new HashMap<>();
        for (Morador m : lista) esperados.merge(m.funcao(), 1, Integer::sum);
        Map<String, List<Entity>> achados = new HashMap<>();
        for (Entity e : level.getEntities((Entity) null, area, Entity::isAlive)) {
            String f = funcao(e);
            if (esperados.containsKey(f)) achados.computeIfAbsent(f, k -> new ArrayList<>()).add(e);
        }
        // Repetidos (sobrando além do esperado) vão embora
        Map<String, Integer> existem = new HashMap<>();
        for (var en : achados.entrySet()) {
            List<Entity> l = en.getValue();
            l.sort(java.util.Comparator.comparingInt(Entity::getId));
            int max = esperados.get(en.getKey());
            for (int i = max; i < l.size(); i++) l.get(i).discard();
            existem.put(en.getKey(), Math.min(max, l.size()));
        }
        // Quem se afastou demais do posto (a Capitã que sai andando pelo Palácio) volta para o lugar dele
        Map<String, List<Morador>> postos = new HashMap<>();
        for (Morador m : lista) postos.computeIfAbsent(m.funcao(), k -> new ArrayList<>()).add(m);
        for (var en : achados.entrySet()) {
            List<Morador> ps = postos.get(en.getKey());
            List<Entity> l = en.getValue();
            for (int i = 0; i < Math.min(ps.size(), l.size()); i++) {
                Entity e = l.get(i);
                if (!e.isAlive()) continue;
                Morador m = ps.get(i);
                double max = m.raio() + 5;
                if (e.distanceToSqr(m.pos().getX() + 0.5, m.pos().getY(), m.pos().getZ() + 0.5) > max * max) {
                    e.teleportTo(m.pos().getX() + 0.5, m.pos().getY(), m.pos().getZ() + 0.5);
                    e.setYRot(m.yaw());
                    if (e instanceof net.minecraft.world.entity.PathfinderMob pm) pm.restrictTo(m.pos(), Math.max(1, m.raio()));
                }
            }
        }
        int voltaram = 0;
        for (Morador m : lista) {
            if (!level.isLoaded(m.pos())) continue;
            int tem = existem.getOrDefault(m.funcao(), 0);
            if (tem > 0) { existem.put(m.funcao(), tem - 1); continue; }
            criar(level, m);
            LanternaMod.LOGGER.info("Lanterna: morador {} voltou em {} ({})", m.funcao(), m.pos(), lugar);
            voltaram++;
        }
        if (voltaram > 0) LanternaMod.LOGGER.info("Lanterna: {} morador(es) voltaram em {}", voltaram, lugar);
    }

    private static String funcao(Entity e) {
        if (e instanceof LanternaEntity l) return l.funcao();
        if (e instanceof RaceEntity r) return r.role();
        return "";
    }

    private static void criar(ServerLevel level, Morador m) {
        if (m.tipo().equals("guardiao")) {
            Palacio.guardiao(level, m.pos(), m.funcao());
            return;
        }
        LanternaEntity e = CidadeNpcs.npc(level, m.pos(), m.funcao(), m.corps(), m.oficial(), m.yaw(), m.raio());
        if (e != null && m.funcao().startsWith("capitao_")) Palacio.vestirCapitao(e, m.funcao().substring(8));
    }

    /** Altura do chão da cidade já construída (a avenida em volta da praça é plana). */
    public static int alturaCidade(ServerLevel level, BlockPos c) {
        Map<Integer, Integer> conta = new HashMap<>();
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            int x = c.getX() + (int) Math.round(Math.cos(a) * 20), z = c.getZ() + (int) Math.round(Math.sin(a) * 20);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            conta.merge(y, 1, Integer::sum);
        }
        return conta.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(c.getY());
    }

    private Moradores() {}
}
