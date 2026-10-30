package com.lanterna.mission;

import com.lanterna.corps.CorpsDefinition;
import com.lanterna.LanternaMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/** Nomes legíveis dos alvos dos objetivos. */
public final class MissionText {

    public static Component targetName(Objective o) {
        String t = o.target();
        return switch (o.type()) {
            case GOTO -> Component.translatable("planet.lanterna." + t);
            case TALK -> {
                if (t.startsWith("resgate:")) yield Component.translatable("alvo.lanterna.resgate");
                String[] parts = t.split(":");
                yield Component.translatable("npc.lanterna.role." + parts[0],
                        Component.translatable("planet.lanterna." + (parts.length > 1 ? parts[1] : "oa")));
            }
            case KILL -> {
                if (t.startsWith("papel:")) yield Component.translatable("alvo.lanterna." + t.substring(6));
                ResourceLocation id = ResourceLocation.tryParse(t);
                EntityType<?> type = id == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(id);
                yield type != null ? type.getDescription() : Component.literal(t);
            }
            case COLLECT -> {
                ResourceLocation id = ResourceLocation.tryParse(t);
                Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
                yield item != null ? item.getDescription() : Component.literal(t);
            }
            case CONSTRUCT -> Component.translatable("construct.lanterna." + t);
            case DUMMY -> Component.translatable("entity.lanterna.alvo_treino");
            case BOSS -> Component.translatable("entity.lanterna." + t);
            case RING -> Component.translatable(CorpsDefinition.nameKey(LanternaMod.id(t)));
            case REP -> Component.translatable("race.lanterna." + t);
            case FUSION, AFFINITY, LEVEL, EMOCAO, DESCOBRIR -> Component.empty();
            case SETOR -> com.lanterna.tropa.Contratos.nomeSetor(o.pos());
            case PONTO -> Component.translatable("objective.lanterna.ponto.alvo");
        };
    }

    private MissionText() {}
}
