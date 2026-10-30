package com.lanterna.naves.bloco;

import com.lanterna.registry.ModBlocks;
import com.lanterna.registry.ModItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Os 14 blocos de interior das naves e o quadro de contratos. */
public final class NaveBlocks {
    public static final List<RegistryObject<Block>> ALL = new ArrayList<>();
    public static final List<RegistryObject<Item>> ITEMS = new ArrayList<>();
    /** Blocos com faixa/luz tingida (tintindex 0). */
    public static final List<RegistryObject<Block>> TINGIDOS = new ArrayList<>();

    /** Metal das naves: porta e escotilha abrem com a mão. */
    public static final BlockSetType METAL_NAVE = BlockSetType.register(new BlockSetType("lanterna_nave", true, SoundType.METAL,
            SoundEvents.IRON_DOOR_CLOSE, SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.IRON_TRAPDOOR_OPEN,
            SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
            SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON));

    private static BlockBehaviour.Properties metal(int light) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4f, 9f).sound(SoundType.METAL)
                .requiresCorrectToolForDrops().lightLevel(s -> light);
    }

    public static final RegistryObject<Block> NAVE_PLACA = reg("nave_placa", () -> new Block(metal(0)), false);
    public static final RegistryObject<Block> NAVE_PLACA_FAIXA = reg("nave_placa_faixa", () -> new Block(metal(7)), true);
    public static final RegistryObject<Block> NAVE_PISO = reg("nave_piso", () -> new Block(metal(0)), true);
    public static final RegistryObject<Block> NAVE_PLACA_AVARIADA = reg("nave_placa_avariada", () -> new Block(metal(2)), false);
    public static final RegistryObject<Block> NAVE_JANELA = reg("nave_janela",
            () -> new net.minecraft.world.level.block.GlassBlock(metal(0).noOcclusion().isViewBlocking((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false)), false);
    public static final RegistryObject<Block> NAVE_PORTA = ModBlocks.BLOCKS.register("nave_porta",
            () -> new net.minecraft.world.level.block.DoorBlock(metal(0).noOcclusion(), METAL_NAVE));
    public static final RegistryObject<Block> NAVE_CONSOLE = reg("nave_console", () -> new Horizontal(metal(6).noOcclusion()), true);
    public static final RegistryObject<Block> NAVE_CADEIRA = reg("nave_cadeira", () -> new Horizontal(metal(0).noOcclusion()), true);
    public static final RegistryObject<Block> NAVE_HOLOMAPA = reg("nave_holomapa", () -> new Simples(metal(10).noOcclusion()), true);
    public static final RegistryObject<Block> NAVE_RECARGA = reg("nave_recarga", () -> new Horizontal(metal(6).noOcclusion()), true);
    public static final RegistryObject<Block> NAVE_BELICHE = ModBlocks.BLOCKS.register("nave_beliche",
            () -> new Beliche(metal(0).noOcclusion().strength(2f)));
    public static final RegistryObject<Block> NAVE_ARMARIO = reg("nave_armario", () -> new Armario(metal(0).noOcclusion()), true);
    public static final RegistryObject<Block> NAVE_LUZ = reg("nave_luz", () -> new Luz(metal(15).noOcclusion()), true);
    public static final RegistryObject<Block> NAVE_ESCOTILHA = reg("nave_escotilha",
            () -> new net.minecraft.world.level.block.TrapDoorBlock(metal(0).noOcclusion(), METAL_NAVE), true);
    public static final RegistryObject<Block> QUADRO_CONTRATOS = reg("quadro_contratos", () -> new QuadroContratos(metal(4).noOcclusion()), false);

    static {
        ALL.add(NAVE_PORTA);
        ALL.add(NAVE_BELICHE);
        TINGIDOS.add(NAVE_PORTA);
        TINGIDOS.add(NAVE_BELICHE);
        ITEMS.add(ModItems.ITEMS.register("nave_porta", () -> new DoubleHighBlockItem(NAVE_PORTA.get(), new Item.Properties())));
        ITEMS.add(ModItems.ITEMS.register("nave_beliche", () -> new BedItem(NAVE_BELICHE.get(), new Item.Properties().stacksTo(1))));
    }

    private static RegistryObject<Block> reg(String name, Supplier<Block> s, boolean tinted) {
        RegistryObject<Block> b = ModBlocks.BLOCKS.register(name, s);
        ALL.add(b);
        if (tinted) TINGIDOS.add(b);
        ITEMS.add(ModItems.ITEMS.register(name, () -> new BlockItem(b.get(), new Item.Properties())));
        return b;
    }

    public static void init() {}

    private NaveBlocks() {}
}
