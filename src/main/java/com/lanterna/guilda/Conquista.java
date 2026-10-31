package com.lanterna.guilda;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Um planeta conquistado: de qual guilda, onde está a bandeira, quantas tropas defendem, o escudo de proteção,
 * a essência que o minerador juntou e a fila de treino do general. Os tempos são em tempo real (milissegundos),
 * então tudo anda mesmo com o servidor vazio.
 */
public final class Conquista {
    public final String planeta;
    public UUID guilda;
    public ResourceKey<Level> mundo;
    public BlockPos bandeira;
    public int defesa;
    /** Até quando o planeta não pode ser invadido. */
    public long escudoAte;
    /** Essência guardada no minerador e o momento em que ele contou a última. */
    public int essencia;
    public long ultimaEssencia;
    /** Treino: quantas faltam, para onde vão (defesa/ataque, em ordem) e quando sai a próxima. */
    public final java.util.ArrayDeque<Boolean> fila = new java.util.ArrayDeque<>(); // true = defesa
    public long proximaTropa;
    public long conquistado;
    /** Nível do Minerador (1 a 4). */
    public int minerador = 1;
    /** Planeta de uma Tropa NPC (sem jogadores): a defesa se recompõe sozinha. */
    public boolean daTropa;
    /** A base já foi construída no mundo? (os planetas das Tropas NPC só constroem quando alguém chega perto) */
    public boolean construida = true;
    public long ultimaRecomposicao;

    // ---- Minerador: tempo por coleta (fração do tempo base), essências por coleta, depósito, custo para subir ----
    public static final double[] MIN_TEMPO = {1.0, 0.66, 0.45, 0.3};
    public static final int[] MIN_QTD = {1, 2, 3, 4};
    public static final int[] MIN_DEPOSITO = {64, 128, 192, 256};

    public long passoMinerador(long base) {
        return Math.max(1000L, (long) (base * MIN_TEMPO[Math.max(0, Math.min(3, minerador - 1))]));
    }

    public int qtdMinerador() {
        return MIN_QTD[Math.max(0, Math.min(3, minerador - 1))];
    }

    public int depositoMinerador() {
        return MIN_DEPOSITO[Math.max(0, Math.min(3, minerador - 1))];
    }

    public Conquista(String planeta, UUID guilda, ResourceKey<Level> mundo, BlockPos bandeira) {
        this.planeta = planeta;
        this.guilda = guilda;
        this.mundo = mundo;
        this.bandeira = bandeira;
    }

    public boolean protegido(long agora) {
        return agora < escudoAte;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("planeta", planeta);
        t.putUUID("guilda", guilda);
        t.putString("mundo", mundo.location().toString());
        t.put("bandeira", NbtUtils.writeBlockPos(bandeira));
        t.putInt("defesa", defesa);
        t.putLong("escudo", escudoAte);
        t.putInt("essencia", essencia);
        t.putLong("ultimaEssencia", ultimaEssencia);
        StringBuilder f = new StringBuilder();
        for (boolean b : fila) f.append(b ? 'd' : 'a');
        t.putString("fila", f.toString());
        t.putLong("proxima", proximaTropa);
        t.putLong("conquistado", conquistado);
        t.putInt("minerador", minerador);
        t.putBoolean("daTropa", daTropa);
        t.putBoolean("construida", construida);
        t.putLong("recomposicao", ultimaRecomposicao);
        return t;
    }

    public static Conquista load(CompoundTag t) {
        Conquista c = new Conquista(t.getString("planeta"), t.getUUID("guilda"),
                ResourceKey.create(Registries.DIMENSION, new ResourceLocation(t.getString("mundo"))), NbtUtils.readBlockPos(t.getCompound("bandeira")));
        c.defesa = t.getInt("defesa");
        c.escudoAte = t.getLong("escudo");
        c.essencia = t.getInt("essencia");
        c.ultimaEssencia = t.getLong("ultimaEssencia");
        for (char ch : t.getString("fila").toCharArray()) c.fila.add(ch == 'd');
        c.proximaTropa = t.getLong("proxima");
        c.conquistado = t.getLong("conquistado");
        c.minerador = Math.max(1, t.getInt("minerador"));
        c.daTropa = t.getBoolean("daTropa");
        c.construida = !t.contains("construida") || t.getBoolean("construida");
        c.ultimaRecomposicao = t.getLong("recomposicao");
        return c;
    }
}
