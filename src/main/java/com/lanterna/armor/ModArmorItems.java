package com.lanterna.armor;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registra máscara, peitoral, calça e botas 3D de cada Tropa.
 *  Na classe principal do mod: ModArmorItems.register(modEventBus); */
public class ModArmorItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ModArmorMaterials.MODID);

    public static final RegistryObject<Item> TROPA_VERDE_HELMET = ITEMS.register("tropa_verde_helmet",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_VERDE, ArmorItem.Type.HELMET, new Item.Properties(), "verde"));
    public static final RegistryObject<Item> TROPA_VERDE_CHESTPLATE = ITEMS.register("tropa_verde_chestplate",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_VERDE, ArmorItem.Type.CHESTPLATE, new Item.Properties(), "verde"));
    public static final RegistryObject<Item> TROPA_VERDE_LEGGINGS = ITEMS.register("tropa_verde_leggings",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_VERDE, ArmorItem.Type.LEGGINGS, new Item.Properties(), "verde"));
    public static final RegistryObject<Item> TROPA_VERDE_BOOTS = ITEMS.register("tropa_verde_boots",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_VERDE, ArmorItem.Type.BOOTS, new Item.Properties(), "verde"));
    public static final RegistryObject<Item> TROPA_VERMELHO_HELMET = ITEMS.register("tropa_vermelho_helmet",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_VERMELHO, ArmorItem.Type.HELMET, new Item.Properties(), "vermelho"));
    public static final RegistryObject<Item> TROPA_VERMELHO_CHESTPLATE = ITEMS.register("tropa_vermelho_chestplate",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_VERMELHO, ArmorItem.Type.CHESTPLATE, new Item.Properties(), "vermelho"));
    public static final RegistryObject<Item> TROPA_VERMELHO_LEGGINGS = ITEMS.register("tropa_vermelho_leggings",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_VERMELHO, ArmorItem.Type.LEGGINGS, new Item.Properties(), "vermelho"));
    public static final RegistryObject<Item> TROPA_VERMELHO_BOOTS = ITEMS.register("tropa_vermelho_boots",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_VERMELHO, ArmorItem.Type.BOOTS, new Item.Properties(), "vermelho"));
    public static final RegistryObject<Item> TROPA_LARANJA_HELMET = ITEMS.register("tropa_laranja_helmet",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_LARANJA, ArmorItem.Type.HELMET, new Item.Properties(), "laranja"));
    public static final RegistryObject<Item> TROPA_LARANJA_CHESTPLATE = ITEMS.register("tropa_laranja_chestplate",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_LARANJA, ArmorItem.Type.CHESTPLATE, new Item.Properties(), "laranja"));
    public static final RegistryObject<Item> TROPA_LARANJA_LEGGINGS = ITEMS.register("tropa_laranja_leggings",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_LARANJA, ArmorItem.Type.LEGGINGS, new Item.Properties(), "laranja"));
    public static final RegistryObject<Item> TROPA_LARANJA_BOOTS = ITEMS.register("tropa_laranja_boots",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_LARANJA, ArmorItem.Type.BOOTS, new Item.Properties(), "laranja"));
    public static final RegistryObject<Item> TROPA_AMARELO_HELMET = ITEMS.register("tropa_amarelo_helmet",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_AMARELO, ArmorItem.Type.HELMET, new Item.Properties(), "amarelo"));
    public static final RegistryObject<Item> TROPA_AMARELO_CHESTPLATE = ITEMS.register("tropa_amarelo_chestplate",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_AMARELO, ArmorItem.Type.CHESTPLATE, new Item.Properties(), "amarelo"));
    public static final RegistryObject<Item> TROPA_AMARELO_LEGGINGS = ITEMS.register("tropa_amarelo_leggings",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_AMARELO, ArmorItem.Type.LEGGINGS, new Item.Properties(), "amarelo"));
    public static final RegistryObject<Item> TROPA_AMARELO_BOOTS = ITEMS.register("tropa_amarelo_boots",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_AMARELO, ArmorItem.Type.BOOTS, new Item.Properties(), "amarelo"));
    public static final RegistryObject<Item> TROPA_AZUL_HELMET = ITEMS.register("tropa_azul_helmet",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_AZUL, ArmorItem.Type.HELMET, new Item.Properties(), "azul"));
    public static final RegistryObject<Item> TROPA_AZUL_CHESTPLATE = ITEMS.register("tropa_azul_chestplate",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_AZUL, ArmorItem.Type.CHESTPLATE, new Item.Properties(), "azul"));
    public static final RegistryObject<Item> TROPA_AZUL_LEGGINGS = ITEMS.register("tropa_azul_leggings",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_AZUL, ArmorItem.Type.LEGGINGS, new Item.Properties(), "azul"));
    public static final RegistryObject<Item> TROPA_AZUL_BOOTS = ITEMS.register("tropa_azul_boots",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_AZUL, ArmorItem.Type.BOOTS, new Item.Properties(), "azul"));
    public static final RegistryObject<Item> TROPA_INDIGO_HELMET = ITEMS.register("tropa_indigo_helmet",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_INDIGO, ArmorItem.Type.HELMET, new Item.Properties(), "indigo"));
    public static final RegistryObject<Item> TROPA_INDIGO_CHESTPLATE = ITEMS.register("tropa_indigo_chestplate",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_INDIGO, ArmorItem.Type.CHESTPLATE, new Item.Properties(), "indigo"));
    public static final RegistryObject<Item> TROPA_INDIGO_LEGGINGS = ITEMS.register("tropa_indigo_leggings",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_INDIGO, ArmorItem.Type.LEGGINGS, new Item.Properties(), "indigo"));
    public static final RegistryObject<Item> TROPA_INDIGO_BOOTS = ITEMS.register("tropa_indigo_boots",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_INDIGO, ArmorItem.Type.BOOTS, new Item.Properties(), "indigo"));
    public static final RegistryObject<Item> TROPA_VIOLETA_HELMET = ITEMS.register("tropa_violeta_helmet",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_VIOLETA, ArmorItem.Type.HELMET, new Item.Properties(), "violeta"));
    public static final RegistryObject<Item> TROPA_VIOLETA_CHESTPLATE = ITEMS.register("tropa_violeta_chestplate",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_VIOLETA, ArmorItem.Type.CHESTPLATE, new Item.Properties(), "violeta"));
    public static final RegistryObject<Item> TROPA_VIOLETA_LEGGINGS = ITEMS.register("tropa_violeta_leggings",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_VIOLETA, ArmorItem.Type.LEGGINGS, new Item.Properties(), "violeta"));
    public static final RegistryObject<Item> TROPA_VIOLETA_BOOTS = ITEMS.register("tropa_violeta_boots",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_VIOLETA, ArmorItem.Type.BOOTS, new Item.Properties(), "violeta"));
    public static final RegistryObject<Item> TROPA_PRETO_HELMET = ITEMS.register("tropa_preto_helmet",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_PRETO, ArmorItem.Type.HELMET, new Item.Properties(), "preto"));
    public static final RegistryObject<Item> TROPA_PRETO_CHESTPLATE = ITEMS.register("tropa_preto_chestplate",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_PRETO, ArmorItem.Type.CHESTPLATE, new Item.Properties(), "preto"));
    public static final RegistryObject<Item> TROPA_PRETO_LEGGINGS = ITEMS.register("tropa_preto_leggings",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_PRETO, ArmorItem.Type.LEGGINGS, new Item.Properties(), "preto"));
    public static final RegistryObject<Item> TROPA_PRETO_BOOTS = ITEMS.register("tropa_preto_boots",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_PRETO, ArmorItem.Type.BOOTS, new Item.Properties(), "preto"));
    public static final RegistryObject<Item> TROPA_BRANCO_HELMET = ITEMS.register("tropa_branco_helmet",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_BRANCO, ArmorItem.Type.HELMET, new Item.Properties(), "branco"));
    public static final RegistryObject<Item> TROPA_BRANCO_CHESTPLATE = ITEMS.register("tropa_branco_chestplate",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_BRANCO, ArmorItem.Type.CHESTPLATE, new Item.Properties(), "branco"));
    public static final RegistryObject<Item> TROPA_BRANCO_LEGGINGS = ITEMS.register("tropa_branco_leggings",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_BRANCO, ArmorItem.Type.LEGGINGS, new Item.Properties(), "branco"));
    public static final RegistryObject<Item> TROPA_BRANCO_BOOTS = ITEMS.register("tropa_branco_boots",
            () -> new UniformeTropaItem(ModArmorMaterials.TROPA_BRANCO, ArmorItem.Type.BOOTS, new Item.Properties(), "branco"));

    public static void register(IEventBus bus) { ITEMS.register(bus); }

    /** Nome da Tropa nos arquivos do uniforme ("verde", "vermelho"...) a partir do id da Tropa do anel. */
    public static String tropaOf(net.minecraft.resources.ResourceLocation corps) {
        return switch (corps.getPath()) {
            case "green" -> "verde";
            case "red" -> "vermelho";
            case "orange" -> "laranja";
            case "yellow" -> "amarelo";
            case "blue" -> "azul";
            case "indigo" -> "indigo";
            case "violet" -> "violeta";
            case "black" -> "preto";
            case "white" -> "branco";
            default -> null;
        };
    }

    /** A peça do uniforme de uma Tropa para um encaixe do corpo (ou null). */
    public static Item pieceFor(String tropa, net.minecraft.world.entity.EquipmentSlot slot) {
        String part = switch (slot) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            case FEET -> "boots";
            default -> null;
        };
        if (tropa == null || part == null) return null;
        return ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(ModArmorMaterials.MODID,
                "tropa_" + tropa + "_" + part));
    }

    /** Todas as peças, na ordem das Tropas (para a aba criativa). */
    public static java.util.List<Item> all() {
        java.util.List<Item> list = new java.util.ArrayList<>();
        for (RegistryObject<Item> item : ITEMS.getEntries()) list.add(item.get());
        return list;
    }
}
