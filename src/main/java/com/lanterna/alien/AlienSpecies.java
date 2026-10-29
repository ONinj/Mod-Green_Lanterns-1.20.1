package com.lanterna.alien;

import com.lanterna.space.Archetype;
import com.lanterna.space.SpaceBodies;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Espécie alienígena: uma por planeta. O id da espécie é o id do planeta (ex: "oa", "korugar",
 * ou "p3_m2" para um planeta gerado).
 *
 * A aparência provisória (cor da pele, tamanho da cabeça, olhos, antenas, braços extras, cauda)
 * sai daqui. Quando houver um modelo próprio da espécie (veja docs/COMO_TROCAR_MODELOS.md),
 * ele é usado no lugar.
 */
public record AlienSpecies(String id, Component name, int skin, float height, float headScale, boolean bigBrain,
                           int eyes, boolean antennae, boolean fourArms, boolean tail, Archetype archetype) {

    private static final Map<String, AlienSpecies> CACHE = new ConcurrentHashMap<>();

    public static AlienSpecies of(String id) {
        if (id == null || id.isEmpty()) id = "oa";
        return CACHE.computeIfAbsent(id, AlienSpecies::create);
    }

    private static AlienSpecies create(String id) {
        return generated(id);
    }

    /** Espécie de um planeta gerado: tudo sorteado a partir da semente do planeta. */
    private static AlienSpecies generated(String id) {
        SpaceBodies.Body body = SpaceBodies.byId(id);
        long seed = body != null && body.procedural() != null ? body.procedural().seed() : id.hashCode();
        Random r = new Random(seed ^ 0xA11E4L);
        Archetype type = body != null && body.procedural() != null ? body.procedural().type() : Archetype.LUSH;
        int skin = java.awt.Color.HSBtoRGB(r.nextFloat(), 0.35f + r.nextFloat() * 0.45f, 0.55f + r.nextFloat() * 0.4f) & 0xFFFFFF;
        float height = 0.7f + r.nextFloat() * 0.6f;
        float head = 0.85f + r.nextFloat() * 0.5f;
        Component name;
        if (body != null && body.procedural() != null) {
            name = Component.translatable("species.lanterna.generated", body.procedural().name());
        } else {
            name = Component.translatable("entity.lanterna.alien");
        }
        return new AlienSpecies(id, name, skin, height, head, head > 1.2f, 1 + r.nextInt(3),
                r.nextBoolean(), r.nextInt(4) == 0, r.nextInt(3) == 0, type);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    public boolean isGenerated() {
        return archetype != null;
    }
}
