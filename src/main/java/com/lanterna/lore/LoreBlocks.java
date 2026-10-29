package com.lanterna.lore;

import com.lanterna.lore.block.LoreBlockTypes;
import com.lanterna.lore.block.RitualBlocks;
import com.lanterna.registry.ModBlocks;
import com.lanterna.registry.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Blocos da lore: pedras e minérios dos planetas, blocos 3D especiais e os blocos das bases. */
public final class LoreBlocks {
    /** Na ordem da aba criativa. */
    public static final List<RegistryObject<Block>> ALL = new ArrayList<>();

    // ---- Pedras dos planetas ----
    public static final RegistryObject<Block> BASALTO_KHARUUN = stone("basalto_kharuun", MapColor.COLOR_BLACK, SoundType.BASALT, 1.5f);
    public static final RegistryObject<Block> ARENITO_AURUMVEX = stone("arenito_aurumvex", MapColor.GOLD, SoundType.STONE, 0.9f);
    public static final RegistryObject<Block> PEDRA_CREPUSCULAR = stone("pedra_crepuscular", MapColor.COLOR_GRAY, SoundType.DEEPSLATE, 1.6f);
    public static final RegistryObject<Block> ROCHA_POLIDA_VERIDIA = stone("rocha_polida_veridia", MapColor.COLOR_GREEN, SoundType.POLISHED_DEEPSLATE, 1.8f);
    public static final RegistryObject<Block> AREIA_ABISSAL_LUMERIS = reg("areia_abissal_lumeris", () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.6f).sound(SoundType.SAND)));
    public static final RegistryObject<Block> PEDRA_DO_TEMPLO_TALEUN = stone("pedra_do_templo_taleun", MapColor.COLOR_PURPLE, SoundType.STONE, 1.8f);
    public static final RegistryObject<Block> ROCHA_CRISTALINA_SELENAAR = stone("rocha_cristalina_selenaar", MapColor.COLOR_PINK, SoundType.CALCITE, 1.2f);
    public static final RegistryObject<Block> GELO_NEGRO_UMBRALL = reg("gelo_negro_umbrall", () -> new LoreBlockTypes.GeloNegro(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK).strength(1.2f).friction(0.98f).sound(SoundType.GLASS)));

    // ---- Minérios ----
    public static final RegistryObject<Block> IRA_BRUTA_MINERIO = ore("ira_bruta_minerio", MapColor.COLOR_RED, 7);
    public static final RegistryObject<Block> AMBAR_DA_POSSE_MINERIO = ore("ambar_da_posse_minerio", MapColor.GOLD, 0);
    public static final RegistryObject<Block> FRAGMENTO_DE_PAVOR_MINERIO = ore("fragmento_de_pavor_minerio", MapColor.COLOR_YELLOW, 3);
    public static final RegistryObject<Block> JADE_ESTELAR_MINERIO = ore("jade_estelar_minerio", MapColor.COLOR_GREEN, 4);
    public static final RegistryObject<Block> PEROLA_DO_AMANHA_MINERIO = ore("perola_do_amanha_minerio", MapColor.COLOR_LIGHT_BLUE, 5);
    public static final RegistryObject<Block> LAGRIMA_INDIGO_MINERIO = ore("lagrima_indigo_minerio", MapColor.COLOR_PURPLE, 4);
    public static final RegistryObject<Block> CRISTAL_DEVOTO_MINERIO = reg("cristal_devoto_minerio", () -> new LoreBlockTypes.CristalDevoto(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).requiresCorrectToolForDrops().strength(3.5f, 4f)
                    .sound(SoundType.AMETHYST).lightLevel(s -> s.getValue(LoreBlockTypes.CristalDevoto.ACESO) ? 10 : 0), null));
    public static final RegistryObject<Block> OBSIDIANA_DO_FIM_MINERIO = reg("obsidiana_do_fim_minerio", () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(25f, 1200f).sound(SoundType.STONE)));
    public static final RegistryObject<Block> BLOCO_OBSIDIANA_DO_FIM = reg("bloco_obsidiana_do_fim", () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(40f, 1200f).sound(SoundType.STONE)));

    // ---- Blocos 3D especiais ----
    public static final RegistryObject<Block> CONCHA_PEROLA = reg("concha_perola", () -> new LoreBlockTypes.Concha(BlockBehaviour.Properties.of()
            .mapColor(MapColor.SAND).requiresCorrectToolForDrops().strength(2.5f).sound(SoundType.BONE_BLOCK).noOcclusion().lightLevel(s -> 6)));
    public static final RegistryObject<Block> LAGRIMA_INDIGO_PENDENTE = reg("lagrima_indigo_pendente", () -> new LoreBlockTypes.Lagrima(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PURPLE).requiresCorrectToolForDrops().strength(2f).sound(SoundType.AMETHYST_CLUSTER).noOcclusion()
            .lightLevel(s -> 7).pushReaction(PushReaction.DESTROY)));
    public static final RegistryObject<Block> CRISTAL_DEVOTO_AGLOMERADO = reg("cristal_devoto_aglomerado", () -> new LoreBlockTypes.CristalDevoto(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).requiresCorrectToolForDrops().strength(2f).noOcclusion()
                    .sound(SoundType.AMETHYST_CLUSTER).lightLevel(s -> s.getValue(LoreBlockTypes.CristalDevoto.ACESO) ? 12 : 2),
            Block.box(1, 0, 1, 15, 15, 15)));

    // ---- Gênese ----
    public static final RegistryObject<Block> GRAMA_BRANCA = reg("grama_branca", () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.SNOW).strength(0.6f).sound(SoundType.GRASS).lightLevel(s -> 3)));
    public static final RegistryObject<Block> TERRA_BRANCA = reg("terra_branca", () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.QUARTZ).strength(0.5f).sound(SoundType.GRAVEL)));

    // ---- Bases dos planetas (indestrutíveis no modo sobrevivência) ----
    public static final RegistryObject<Block> FORJA = reg("forja_espectral", () -> new RitualBlocks.Forja(sacred(MapColor.METAL, 10)));
    public static final RegistryObject<Block> ALTAR_DESAFIO = reg("altar_desafio", () -> new RitualBlocks.AltarDesafio(sacred(MapColor.COLOR_BLACK, 8)));
    public static final RegistryObject<Block> ALTAR_VIDA = reg("altar_vida", () -> new RitualBlocks.AltarVida(sacred(MapColor.SNOW, 15)));
    public static final RegistryObject<Block> FAROL_PEROLA = reg("farol_perola", () -> new RitualBlocks.FarolPerola(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_BLUE).strength(-1f, 3600000f).noLootTable().sound(SoundType.GLASS)
            .lightLevel(s -> s.getValue(RitualBlocks.FarolPerola.ACESO) ? 15 : 0)));
    public static final RegistryObject<Block> RACHADURA = reg("rachadura_nulo", () -> new RitualBlocks.Rachadura(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GRAY).strength(-1f, 3600000f).noLootTable().noOcclusion().lightLevel(s -> 4).sound(SoundType.SCULK)));

    private static BlockBehaviour.Properties sacred(MapColor color, int light) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1f, 3600000f).noLootTable().noOcclusion()
                .lightLevel(s -> light).sound(SoundType.LODESTONE);
    }

    private static RegistryObject<Block> stone(String name, MapColor color, SoundType sound, float hardness) {
        return reg(name, () -> new Block(BlockBehaviour.Properties.of().mapColor(color).requiresCorrectToolForDrops()
                .strength(hardness, 6f).sound(sound)));
    }

    private static RegistryObject<Block> ore(String name, MapColor color, int light) {
        return reg(name, () -> new Block(BlockBehaviour.Properties.of().mapColor(color).requiresCorrectToolForDrops()
                .strength(4f, 4f).sound(SoundType.DEEPSLATE).lightLevel(s -> light)));
    }

    private static RegistryObject<Block> reg(String name, Supplier<Block> sup) {
        RegistryObject<Block> block = ModBlocks.BLOCKS.register(name, sup);
        boolean special = name.startsWith("forja") || name.startsWith("altar") || name.startsWith("rachadura");
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().rarity(special ? Rarity.EPIC : Rarity.COMMON)));
        ALL.add(block);
        return block;
    }

    /** Força o carregamento da classe (os registros acontecem nos campos estáticos). */
    public static void init() {}

    private LoreBlocks() {}
}
