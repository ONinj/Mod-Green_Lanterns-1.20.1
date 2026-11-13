package com.lanterna.fonte;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Onde os inimigos das provas e dos ataques à Bateria Central podem surgir: em volta dela, no chão, nunca em cima
 * da estrutura (lá em cima o jogador não consegue mirar neles direito).
 */
public final class LugarDeSurgir {
    /** Raio (blocos) em volta da Bateria onde nada surge. */
    private static final int LIVRE = 12;

    /**
     * Um lugar no chão a uma distância entre rMin e rMax do centro, fora da Bateria e mais ou menos na altura do
     * centro. Se nada servir, o lado do centro oposto à Bateria.
     */
    public static BlockPos achar(ServerLevel l, BlockPos centro, double rMin, double rMax, @Nullable BlockPos bateria) {
        int chaoCentro = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centro.getX(), centro.getZ());
        for (int t = 0; t < 40; t++) {
            double a = l.random.nextDouble() * Math.PI * 2, r = rMin + l.random.nextDouble() * (rMax - rMin);
            int x = (int) Math.round(centro.getX() + Math.cos(a) * r), z = (int) Math.round(centro.getZ() + Math.sin(a) * r);
            BlockPos p = new BlockPos(x, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (serve(l, p, chaoCentro, bateria)) return p;
        }
        // lado oposto à Bateria
        double dx = 0, dz = -1;
        if (bateria != null) {
            dx = centro.getX() - bateria.getX(); dz = centro.getZ() - bateria.getZ();
            double d = Math.max(1, Math.sqrt(dx * dx + dz * dz));
            dx /= d; dz /= d;
        }
        int x = (int) Math.round(centro.getX() + dx * rMin), z = (int) Math.round(centro.getZ() + dz * rMin);
        return new BlockPos(x, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** O lugar serve? Fora da Bateria, não em cima dela e perto da altura do chão do centro. */
    public static boolean serve(ServerLevel l, BlockPos p, int chaoCentro, @Nullable BlockPos bateria) {
        if (bateria != null) {
            double dx = p.getX() - bateria.getX(), dz = p.getZ() - bateria.getZ();
            if (dx * dx + dz * dz < LIVRE * LIVRE) return false;
        }
        if (Math.abs(p.getY() - chaoCentro) > 5) return false;
        var id = ForgeRegistries.BLOCKS.getKey(l.getBlockState(p.below()).getBlock());
        if (id != null && id.getNamespace().equals("lanterna") && id.getPath().contains("bateria")) return false;
        return l.getFluidState(p).isEmpty();
    }

    private LugarDeSurgir() {}
}
