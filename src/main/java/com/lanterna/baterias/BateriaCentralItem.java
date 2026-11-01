package com.lanterna.baterias;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * Item de construção (só para criativo/admin): clique no chão e a Bateria inteira é colocada,
 * com a porta virada para o norte (-Z). Usa a estrutura data/<modid>/structures/bateria_central_<tropa>.nbt.
 */
public class BateriaCentralItem extends Item {
    private final Tropa tropa;
    public BateriaCentralItem(Properties p, Tropa t) { super(p); this.tropa = t; }

    /** posição do núcleo dentro da estrutura (x, y, z) — veja o LEIA-ME */
    public static int[] origem(Tropa t) {
        return switch (t) {
            case VERDE -> new int[]{7, 0, 9};
            case AMARELO -> new int[]{7, 0, 9};
            case VERMELHO -> new int[]{8, 0, 9};
            case LARANJA -> new int[]{7, 0, 9};
            case AZUL -> new int[]{7, 0, 9};
            case INDIGO -> new int[]{7, 0, 9};
            case VIOLETA -> new int[]{9, 0, 10};
        };
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        if (!(c.getLevel() instanceof ServerLevel lvl)) return InteractionResult.SUCCESS;
        if (c.getPlayer() != null && !c.getPlayer().getAbilities().instabuild) {
            c.getPlayer().displayClientMessage(Component.translatable("msg.lanterna.bateria.criativo"), true); return InteractionResult.FAIL;
        }
        int[] o = origem(tropa);
        BlockPos nucleo = c.getClickedPos();                 // o núcleo fica no lugar do bloco clicado (nível do chão)
        BlockPos canto = nucleo.offset(-o[0], -o[1], -o[2]);
        return lvl.getStructureManager().get(new ResourceLocation(ModBaterias.MODID, "bateria_central_" + tropa.nome)).map(t -> {
            t.placeInWorld(lvl, canto, canto, new StructurePlaceSettings(), lvl.random, 2);
            return InteractionResult.CONSUME;
        }).orElse(InteractionResult.FAIL);
    }
}
