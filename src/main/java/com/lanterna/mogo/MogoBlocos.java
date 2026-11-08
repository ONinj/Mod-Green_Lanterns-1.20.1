package com.lanterna.mogo;

import com.lanterna.registry.ModBlocks;
import com.lanterna.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/** Blocos do Mogo: a jade viva da faixa, os focos da infecção, a corrupção que se espalha e o núcleo das torres. */
public final class MogoBlocos {
    public static RegistryObject<Block> JADE, FOCO, CORRUPCAO, NUCLEO_TORRE;

    public static void init() {
        JADE = bloco("jade_mogo", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.EMERALD).strength(3f, 6f)
                .sound(SoundType.AMETHYST).lightLevel(s -> 12)));
        FOCO = bloco("foco_infeccao", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(1.2f, 3f)
                .sound(SoundType.SCULK).lightLevel(s -> 7)) {
            @Override
            public void playerWillDestroy(Level l, BlockPos pos, BlockState s, Player p) {
                super.playerWillDestroy(l, pos, s, p);
                if (l instanceof ServerLevel sl) MogoMissoes.focoQuebrado(sl, pos, p);
            }
        });
        CORRUPCAO = bloco("corrupcao_mogo", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.8f)
                .sound(SoundType.SCULK).lightLevel(s -> 3)));
        NUCLEO_TORRE = bloco("nucleo_torre_mogo", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.EMERALD).strength(-1f, 3600000f)
                .sound(SoundType.AMETHYST).lightLevel(s -> 15).noLootTable()));
    }

    private static RegistryObject<Block> bloco(String nome, java.util.function.Supplier<Block> b) {
        RegistryObject<Block> r = ModBlocks.BLOCKS.register(nome, b);
        ModItems.ITEMS.register(nome, () -> new BlockItem(r.get(), new Item.Properties()));
        return r;
    }

    private MogoBlocos() {}
}
