package com.lanterna.space;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Perigos do espaço: sóis (queimam de perto e puxam muito mais forte que os planetas) e buracos negros (puxam tudo
 * para dentro; quem passa do horizonte não volta). Ficam nos setores vazios ({@link ProceduralPlanets#SECTOR}),
 * sempre os mesmos para a mesma semente do mundo, e ainda há o Sol grande e um buraco negro fixo (o Abismo).
 *
 * A física é igual no servidor e no cliente: o puxão de quem voa é calculado no cliente (o voo roda lá) e o
 * servidor cuida das queimaduras, das mortes e de puxar as naves e criaturas.
 */
public final class Perigos {
    public enum Tipo { SOL, BURACO }

    /**
     * @param raio   sol: o raio da estrela; buraco negro: o raio do horizonte (passou dele, morreu)
     * @param alcance até onde a gravidade chega
     */
    public record Perigo(String id, Tipo tipo, Vec3 centro, double raio, double alcance, int cor, String nome, double inclinacao) {
        /** Até onde queima (sol) ou rasga (buraco negro). */
        public double zonaDano() {
            return tipo == Tipo.SOL ? raio * 1.6 : raio * 3.0;
        }

        /** Raio do desenho inteiro (sol com a coroa, buraco com o disco). */
        public double raioVisual() {
            return tipo == Tipo.SOL ? raio * 1.3 : raio * 7.5;
        }

        public Component nomeExibido() {
            return Component.translatable(tipo == Tipo.SOL ? "perigo.lanterna.sol" : "perigo.lanterna.buraco", nome);
        }
    }

    /** O Sol grande do mapa (mesma posição e tamanho de {@link SpaceBodies#SUN}). */
    public static final Perigo SOL_CENTRAL = new Perigo("sol", Tipo.SOL, SpaceBodies.SUN.center(), SpaceBodies.SUN.radius(),
            SpaceBodies.SUN.radius() * 2.0, 0xFFE9A0, "Hélio", 0);
    /** O Abismo: um buraco negro entre Nok e Odym, perto o bastante para todo mundo conhecer o perigo. */
    public static final Perigo ABISMO = new Perigo("abismo", Tipo.BURACO, new Vec3(-6000, 150, 5600), 34, 34 * 40, 0xFFB060, "Abismo", 0.35);
    public static final List<Perigo> FIXOS = List.of(SOL_CENTRAL, ABISMO);

    private static final double CORE_RADIUS = 12000;
    private static final Map<Long, Optional<Perigo>> CACHE = new ConcurrentHashMap<>();
    private static long cacheSeed;

    private static final int[] CORES_SOL = {0xFFE070, 0xFFB050, 0xFF6A3A, 0xB8D4FF, 0xFFF4D8};

    @Nullable
    public static Perigo inSector(int sx, int sz) {
        long seed = ProceduralPlanets.seed();
        if (seed != cacheSeed) {
            CACHE.clear();
            cacheSeed = seed;
        }
        long key = ((long) sx << 32) ^ (sz & 0xFFFFFFFFL);
        return CACHE.computeIfAbsent(key, k -> Optional.ofNullable(generate(seed, sx, sz))).orElse(null);
    }

    @Nullable
    private static Perigo generate(long seed, int sx, int sz) {
        if (ProceduralPlanets.inSector(sx, sz) != null) return null;   // só nos setores sem planeta
        Random r = new Random(seed * 2862933555777941757L + sx * 7046029254386353131L + sz * 3202034522624059733L + 0x2545F491L);
        double cx = sx * (double) ProceduralPlanets.SECTOR + ProceduralPlanets.SECTOR / 2.0 + (r.nextDouble() - 0.5) * ProceduralPlanets.SECTOR * 0.5;
        double cz = sz * (double) ProceduralPlanets.SECTOR + ProceduralPlanets.SECTOR / 2.0 + (r.nextDouble() - 0.5) * ProceduralPlanets.SECTOR * 0.5;
        if (Math.hypot(cx, cz) < CORE_RADIUS) return null;
        Vec3 c = new Vec3(cx, -300 + r.nextDouble() * 700, cz);
        if (c.distanceTo(SOL_CENTRAL.centro()) < SOL_CENTRAL.alcance() + 3000) return null;
        if (com.lanterna.lendarios.PlanetasLendarios.perto(c, 9000)) return null;
        float k = r.nextFloat();
        String nome = PlanetNames.generate(r);
        String id = "h" + (sx < 0 ? "m" + -sx : sx) + "_" + (sz < 0 ? "m" + -sz : sz);
        if (k < 0.58f) {
            int tipo = r.nextInt(CORES_SOL.length);
            double raio = tipo == 2 ? 1200 + r.nextDouble() * 600    // gigante vermelha
                    : tipo == 3 ? 450 + r.nextDouble() * 250         // azul: pequena e furiosa
                    : 650 + r.nextDouble() * 550;
            return new Perigo(id, Tipo.SOL, c, raio, raio * 3.0, CORES_SOL[tipo], nome, 0);
        }
        if (k < 0.9f) {
            double h = 18 + r.nextDouble() * 24;
            return new Perigo(id, Tipo.BURACO, c, h, h * 40, r.nextBoolean() ? 0xFFB060 : 0xFFD8A0, nome, (r.nextDouble() - 0.5) * 1.2);
        }
        return null;
    }

    /** Perigos em volta da posição (raio em setores) + os fixos. */
    public static List<Perigo> near(Vec3 pos, int sectorRadius) {
        List<Perigo> list = new ArrayList<>(FIXOS);
        int sx = Math.floorDiv((int) Math.floor(pos.x), ProceduralPlanets.SECTOR);
        int sz = Math.floorDiv((int) Math.floor(pos.z), ProceduralPlanets.SECTOR);
        for (int dx = -sectorRadius; dx <= sectorRadius; dx++)
            for (int dz = -sectorRadius; dz <= sectorRadius; dz++) {
                Perigo p = inSector(sx + dx, sz + dz);
                if (p != null) list.add(p);
            }
        return list;
    }

    @Nullable
    public static Perigo byId(String id) {
        for (Perigo p : FIXOS) if (p.id().equals(id)) return p;
        if (!id.startsWith("h")) return null;
        try {
            String[] parts = id.substring(1).split("_");
            int sx = parts[0].startsWith("m") ? -Integer.parseInt(parts[0].substring(1)) : Integer.parseInt(parts[0]);
            int sz = parts[1].startsWith("m") ? -Integer.parseInt(parts[1].substring(1)) : Integer.parseInt(parts[1]);
            return inSector(sx, sz);
        } catch (Exception e) {
            return null;
        }
    }

    /** Aceleração (blocos/tick²) de um perigo nesta posição. */
    public static Vec3 puxao(Perigo p, Vec3 pos) {
        Vec3 to = p.centro().subtract(pos);
        double d = to.length();
        if (d >= p.alcance() || d < 0.5) return Vec3.ZERO;
        // some suave na borda do alcance
        double borda = Mth.clamp((p.alcance() - d) / (p.alcance() * 0.35), 0, 1);
        double a = p.tipo() == Tipo.SOL
                ? 0.32 * Math.pow(p.raio() / Math.max(d, p.raio()), 2)
                : 1.2 * Math.pow(p.raio() / Math.max(d, p.raio()), 1.6);
        return to.scale(Math.min(a * borda, 2.5) / d);
    }

    /** Soma dos puxões dos sóis e buracos negros perto. */
    public static Vec3 puxao(Vec3 pos) {
        Vec3 t = Vec3.ZERO;
        for (Perigo p : near(pos, 1)) t = t.add(puxao(p, pos));
        return t;
    }

    /** O perigo mais "forte" agindo nesta posição (para avisos), ou nenhum. */
    @Nullable
    public static Perigo dominante(Vec3 pos) {
        Perigo best = null;
        double bestA = 0.004;
        for (Perigo p : near(pos, 1)) {
            double a = puxao(p, pos).length();
            if (a > bestA) { bestA = a; best = p; }
        }
        return best;
    }

    /** Velocidade máxima da hiperpropulsão perto dos perigos (freia sozinho como perto dos planetas). */
    public static double limiteHiper(Vec3 pos, double limite, double minimo) {
        for (Perigo p : near(pos, 1)) {
            double fora = pos.distanceTo(p.centro()) - p.zonaDano();
            if (fora < p.alcance()) limite = Math.min(limite, Math.max(minimo, fora / 12.0));
        }
        return limite;
    }

    /** Se este ponto está perto demais de um sol ou buraco negro (com folga). */
    public static boolean perigoso(Vec3 pos, double folga) {
        for (Perigo p : near(pos, 1)) if (pos.distanceTo(p.centro()) < p.alcance() + folga) return true;
        return false;
    }

    private Perigos() {}
}
