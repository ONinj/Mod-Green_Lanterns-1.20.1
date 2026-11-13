package com.lanterna.lendarios;

import com.lanterna.space.SpaceBodies;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Os planetas dos Lanternas lendários no mapa do espaço (fixos, a {@link #DIST} blocos do centro) e onde fica o chão
 * de cada um: uma região do mundo do tipo do planeta, a 24 milhões de blocos (longe das regiões dos planetas gerados).
 */
public final class PlanetasLendarios {
    public static final double DIST = 50000;
    public static final int REGIAO_BASE = 24_000_000, REGIAO_PASSO = 200_000;
    /** Até onde (em x/z) o mundo do tipo conta como o planeta lendário. */
    public static final int MEIA_REGIAO = 20_000;
    public static final double RAIO = 260;

    /** Corpos celestes (sem o Hanks, que tem mundo próprio e anda). */
    public static final List<SpaceBodies.Body> CORPOS = criar();

    private static List<SpaceBodies.Body> criar() {
        List<SpaceBodies.Body> l = new ArrayList<>();
        for (Lendario e : Lendario.values()) {
            if (e.tipo == null) continue;
            double a = Math.toRadians(e.angulo);
            Vec3 c = new Vec3(Math.cos(a) * DIST, -100 + (e.ordinal() * 97 % 400), Math.sin(a) * DIST);
            int rx = regiaoX(e), rz = REGIAO_BASE;
            var proc = new SpaceBodies.Procedural(e.planeta, e.tipo, 0x1E4DA0L + e.ordinal(), rx, rz);
            l.add(new SpaceBodies.Body(e.corpoId(), c, RAIO, e.corps.color(), true, e.tipo.dimension, e.tipo.breathable, proc));
        }
        return List.copyOf(l);
    }

    public static int regiaoX(Lendario e) {
        return REGIAO_BASE + e.ordinal() * REGIAO_PASSO;
    }

    /** O planeta lendário onde fica este ponto de um mundo de tipo (ou nenhum). */
    @Nullable
    public static SpaceBodies.Body noMundo(ResourceKey<Level> dim, double x, double z) {
        if (x < REGIAO_BASE - MEIA_REGIAO) return null;
        for (SpaceBodies.Body b : CORPOS) {
            if (!dim.equals(b.dimension()) || b.procedural() == null) continue;
            if (Math.abs(x - b.procedural().regionX()) < MEIA_REGIAO && Math.abs(z - b.procedural().regionZ()) < MEIA_REGIAO) return b;
        }
        return null;
    }

    @Nullable
    public static Lendario lendarioEm(ResourceKey<Level> dim, double x, double z) {
        SpaceBodies.Body b = noMundo(dim, x, z);
        return b == null ? null : Lendario.doCorpo(b.id());
    }

    public static boolean eLendario(SpaceBodies.Body b) {
        return b.id().startsWith("lend_");
    }

    /** Pontos que os planetas gerados, os sóis e os buracos negros devem deixar livres. */
    public static boolean perto(Vec3 p, double folga) {
        for (SpaceBodies.Body b : CORPOS) if (b.center().distanceTo(p) < folga) return true;
        return false;
    }

    private PlanetasLendarios() {}
}
