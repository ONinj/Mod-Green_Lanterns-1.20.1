package com.lanterna.client.mission;

import com.lanterna.hanks.HanksMundo;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** O que o cliente sabe dos núcleos de Hanks (para o marcador da missão "Os Três Núcleos"). */
public final class ClienteHanks {
    private static int y, feitos;
    private static long quando = -1;

    public static void receber(int altura, int ligados) {
        y = altura;
        feitos = ligados;
        quando = System.currentTimeMillis();
    }

    public static int ligados() {
        return Integer.bitCount(feitos & 7);
    }

    /** O núcleo apagado mais perto (índice, ou -1). Só vale em Hanks e com notícia recente do servidor. */
    public static int maisPerto(Vec3 de) {
        var mc = Minecraft.getInstance();
        if (quando < 0 || System.currentTimeMillis() - quando > 15000 || mc.level == null || !mc.level.dimension().equals(HanksMundo.DIM)) return -1;
        int melhor = -1;
        double md = Double.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            if ((feitos & (1 << i)) != 0) continue;
            double d = ponto(i).distanceToSqr(de);
            if (d < md) { md = d; melhor = i; }
        }
        return melhor;
    }

    public static Vec3 ponto(int i) {
        var n = HanksMundo.NUCLEOS_XZ[i];
        return new Vec3(n.getX() + 0.5, y + 1.5, n.getZ() + 0.5);
    }

    private ClienteHanks() {}
}
