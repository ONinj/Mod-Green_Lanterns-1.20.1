package seu.pacote.baterias;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/**
 * Bateria Central de cada Tropa: um modelo gigante (GeckoLib) desenhado pelo bloco "núcleo",
 * cercado por blocos invisíveis de colisão ("casca") e com um campo de portal na câmara de baixo.
 * Na classe principal:  ModBaterias.register(modEventBus);
 */
public class ModBaterias {
    public static final String MODID = "seu_modid";
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);

    private static BlockBehaviour.Properties inquebravel() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(-1.0f, 3600000.0f).noLootTable().pushReaction(PushReaction.BLOCK);
    }

    public static final RegistryObject<Block> NUCLEO = BLOCKS.register("bateria_nucleo",
            () -> new BateriaNucleoBlock(inquebravel().noOcclusion().lightLevel(s -> 15)));
    public static final RegistryObject<Block> CASCA = BLOCKS.register("bateria_casca",
            () -> new BateriaCascaBlock(inquebravel().noOcclusion().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)));
    public static final RegistryObject<Block> PORTAL = BLOCKS.register("bateria_portal",
            () -> new BateriaPortalBlock(inquebravel().noCollission().noOcclusion().lightLevel(s -> 12)));

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<BateriaNucleoBlockEntity>> NUCLEO_BE = BLOCK_ENTITIES.register("bateria_nucleo",
            () -> BlockEntityType.Builder.of(BateriaNucleoBlockEntity::new, NUCLEO.get()).build(null));

    /** item que constrói a Bateria inteira (uma por Tropa, 7 no total): bateria_central_<tropa> */
    public static final Map<Tropa, RegistryObject<Item>> ITENS = new EnumMap<>(Tropa.class);
    static {
        for (Tropa t : Tropa.values())
            ITENS.put(t, ITEMS.register("bateria_central_" + t.nome, () -> new BateriaCentralItem(new Item.Properties().stacksTo(1), t)));
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus);
    }
}
