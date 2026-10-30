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
        return c;
    }
}
