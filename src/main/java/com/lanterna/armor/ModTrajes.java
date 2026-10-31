package com.lanterna.armor;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trajes "segunda pele": 9 Tropas x 2 variações (a, b) x 4 peças = 72 itens.
 * Nomes: traje_<tropa>_<variante>_helmet / _chestplate / _leggings / _boots (iguais aos arquivos do pacote).
 * Além de itens vestíveis, são opções do uniforme do anel (tela de Personalização).
 */
public final class ModTrajes {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ModArmorMaterials.MODID);

    public static final String[] TROPAS = {"verde", "amarelo", "vermelho", "laranja", "azul", "indigo", "violeta", "preto", "branco"};
    public static final String[] VARIANTES = {"a", "b"};
    /** "traje_<tropa>_<variante>_<peca>" -> item */
    public static final Map<String, RegistryObject<Item>> TODOS = new LinkedHashMap<>();

    static {
        for (String tropa : TROPAS) {
            TrajeMaterial mat = TrajeMaterial.valueOf(tropa.toUpperCase(java.util.Locale.ROOT));
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

    /** A peça do traje para um encaixe do corpo (ou null). */
    @Nullable
    public static Item peca(String tropa, String variante, EquipmentSlot slot) {
        String part = switch (slot) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            case FEET -> "boots";
            default -> null;
        };
        RegistryObject<Item> r = part == null ? null : TODOS.get("traje_" + tropa + "_" + variante + "_" + part);
        return r == null ? null : r.get();
    }

    public static void register(IEventBus bus) { ITEMS.register(bus); }

    private ModTrajes() {}
}
