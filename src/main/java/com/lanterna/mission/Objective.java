package com.lanterna.mission;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Um objetivo de missão.
 *
 * @param type     o que precisa ser feito
 * @param target   alvo (planeta, papel do NPC, id de entidade/item/construto, raça, Tropa...)
 * @param count    quantidade
 * @param planet   planeta onde fica o objetivo (para a seta do HUD), ou null
 * @param pos      ponto no planeta (base, arena), ou null para "o planeta todo"
 */
public record Objective(Type type, String target, int count, @Nullable String planet, @Nullable BlockPos pos) {

    public enum Type {
        /** Chegar ao planeta. */
        GOTO,
        /** Falar com um NPC (papel:planeta, ex: "lider:ysmault", "instrutor:oa", "guardiao:oa"). */
        TALK,
        /** Derrotar criaturas (id da entidade, ex: "lanterna:eco_cinzento"). */
        KILL,
        /** Ter itens no inventário (são entregues ao concluir). */
        COLLECT,
        /** Usar construtos (id do construto ou "any"). */
        CONSTRUCT,
        /** Destruir alvos de treino. */
        DUMMY,
        /** Derrotar o boss (id do boss). */
        BOSS,
        /** Ter o anel da Tropa (forjado ou não). */
        RING,
        /** Ter respeito/reputação com uma raça. */
        REP,
        /** Ter os sete anéis da fusão e os sete drops dos bosses. */
        FUSION,
        /** Afinidade com o anel (nível). */
        AFFINITY,
        /** Nível do Lanterna. */
        LEVEL,
        /** Pontos de emoção da Tropa (Prova da Emoção). */
        EMOCAO,
        /** Chegar a um ponto do espaço (patrulha de setor). planet = "space", pos = ponto. */
        SETOR,
        /** Pousar num planeta que ainda não visitou. */
        DESCOBRIR
    }

    public static Objective of(Type type, String target, int count, @Nullable String planet, @Nullable BlockPos pos) {
        return new Objective(type, target, count, planet, pos);
    }

    public Component label(int progress) {
        String key = "objective.lanterna." + type.name().toLowerCase() + (type == Type.REP ? "." + target : "");
        Component base = Component.translatable(key, MissionText.targetName(this), count);
        if (type == Type.LEVEL) return base;
        if (count > 1 || type == Type.REP || type == Type.COLLECT || type == Type.KILL || type == Type.DUMMY || type == Type.CONSTRUCT) {
            return base.copy().append(Component.literal("  " + Math.min(progress, count) + "/" + count));
        }
        return base;
    }
}
