package com.lanterna.especies;

import com.lanterna.corps.BuiltinCorps;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** As 18 espécies humanoides (pacote Tropas: Espécies e Naves). Tipo de corpo e Tropa mais comum de cada uma. */
public enum Especie {
    KORRVAAL("korrvaal", Corpo.GRANDE, BuiltinCorps.GREEN, null),
    ILITH("ilith", Corpo.PADRAO, BuiltinCorps.GREEN, null),
    PYXI("pyxi", Corpo.PEQUENO, BuiltinCorps.GREEN, null),
    VHORR("vhorr", Corpo.PADRAO, BuiltinCorps.YELLOW, null),
    NYTHARI("nythari", Corpo.GRANDE, BuiltinCorps.YELLOW, null),
    GORRHAK("gorrhak", Corpo.GRANDE, BuiltinCorps.RED, null),
    SKARN("skarn", Corpo.PADRAO, BuiltinCorps.RED, null),
    LUMIAE("lumiae", Corpo.PADRAO, BuiltinCorps.BLUE, null),
    OSSENVAR("ossenvar", Corpo.GRANDE, BuiltinCorps.BLUE, null),
    SELVYN("selvyn", Corpo.PADRAO, BuiltinCorps.VIOLET, null),
    AURITH("aurith", Corpo.PEQUENO, BuiltinCorps.VIOLET, null),
    TARU("taru", Corpo.PADRAO, BuiltinCorps.INDIGO, null),
    MHENN("mhenn", Corpo.PEQUENO, BuiltinCorps.INDIGO, null),
    GRUBBIX("grubbix", Corpo.PEQUENO, BuiltinCorps.ORANGE, null),
    VELKOR("velkor", Corpo.GRANDE, BuiltinCorps.ORANGE, null),
    DREVAN("drevan", Corpo.PADRAO, null, Papel.PIRATA),
    BRUTOR("brutor", Corpo.GRANDE, null, Papel.PIRATA),
    QIRI("qiri", Corpo.PEQUENO, null, Papel.MERCADOR);

    public enum Corpo {
        PADRAO("padrao", 0.6f, 2.0f, 30), GRANDE("grande", 1.1f, 2.6f, 44), PEQUENO("pequeno", 0.5f, 1.3f, 22);
        public final String id;
        public final float width, height;
        public final int health;
        Corpo(String id, float w, float h, int health) { this.id = id; this.width = w; this.height = h; this.health = health; }
    }

    public final String id;
    public final Corpo corpo;
    /** Tropa em que a espécie é mais comum (null para piratas e mercadores). */
    @Nullable public final BuiltinCorps tropa;
    /** Papel mais comum fora das Tropas (piratas, mercadores). */
    @Nullable public final Papel papel;

    Especie(String id, Corpo corpo, @Nullable BuiltinCorps tropa, @Nullable Papel papel) {
        this.id = id; this.corpo = corpo; this.tropa = tropa; this.papel = papel;
    }

    public static Especie byId(String id) {
        for (Especie e : values()) if (e.id.equals(id)) return e;
        return ILITH;
    }

    /** Uma espécie para um Lanterna da Tropa (70% das próprias, 30% qualquer uma das Tropas). O Preto vem de todas. */
    public static Especie paraTropa(BuiltinCorps corps, RandomSource r) {
        List<Especie> own = new ArrayList<>(), any = new ArrayList<>();
        for (Especie e : values()) {
            if (e.tropa == null) continue;
            any.add(e);
            if (e.tropa == corps) own.add(e);
        }
        if (!own.isEmpty() && r.nextFloat() < 0.7f) return own.get(r.nextInt(own.size()));
        return any.get(r.nextInt(any.size()));
    }

    public static Especie paraPapel(Papel papel, RandomSource r) {
        List<Especie> own = new ArrayList<>();
        for (Especie e : values()) if (e.papel == papel) own.add(e);
        if (!own.isEmpty() && r.nextFloat() < 0.65f) return own.get(r.nextInt(own.size()));
        return values()[r.nextInt(values().length)];
    }

    public enum Papel {
        LANTERNA("lanterna"), PIRATA("pirata"), CAPITAO("capitao"), MERCADOR("mercador");
        public final String id;
        Papel(String id) { this.id = id; }
        public static Papel byId(String id) {
            for (Papel p : values()) if (p.id.equals(id)) return p;
            return LANTERNA;
        }
    }
}
