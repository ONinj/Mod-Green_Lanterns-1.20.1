package seu.pacote.trajes;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registra os 27 trajes (9 Tropas x 3: "a", "b" e "energia") x 4 peças = 108 itens.
 * Nomes: traje_<tropa>_<variante>_helmet / _chestplate / _leggings / _boots
 * Na classe principal do mod:  ModTrajes.register(modEventBus);
 */
public class ModTrajes {
    public static final String MODID = "seu_modid";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final String[] TROPAS = {"verde", "amarelo", "vermelho", "laranja", "azul", "indigo", "violeta", "preto", "branco"};
    public static final String[] VARIANTES = {"a", "b", "energia"};
    /** chave "traje_<tropa>_<variante>_<peca>" -> item */
    public static final Map<String, RegistryObject<Item>> TODOS = new LinkedHashMap<>();

    static {
        for (String tropa : TROPAS) {
            TrajeMaterial mat = TrajeMaterial.valueOf(tropa.toUpperCase());
            for (String v : VARIANTES) {
                registrar(tropa, v, mat, ArmorItem.Type.HELMET, "helmet");
                registrar(tropa, v, mat, ArmorItem.Type.CHESTPLATE, "chestplate");
                registrar(tropa, v, mat, ArmorItem.Type.LEGGINGS, "leggings");
                registrar(tropa, v, mat, ArmorItem.Type.BOOTS, "boots");
            }
        }
    }

    private static void registrar(String tropa, String v, TrajeMaterial mat, ArmorItem.Type tipo, String peca) {
        String nome = "traje_" + tropa + "_" + v + "_" + peca;
        TODOS.put(nome, ITEMS.register(nome, () -> new TrajeTropaItem(mat, tipo, new Item.Properties().stacksTo(1), tropa, v)));
    }

    public static void register(IEventBus bus) { ITEMS.register(bus); }
}
