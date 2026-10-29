package com.lanterna.lore.entity;

import com.lanterna.armor.ModArmorItems;
import com.lanterna.lore.LoreItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Trocas de cada raça, seguindo a lore: cada povo troca do jeito da sua emoção.
 *
 *  Brakkari  só aceitam troféus de combate (ossos, cabeças de mobs)
 *  Ghallos   tudo é vendido, e caro; o preço sobe a cada compra
 *  Skrell'Vath pagam com segredos; aceitam coisas assustadoras
 *  Veltari   não usam moeda: Méritos da Tropa (ganhos em missões)
 *  Aelínos   peixes, plantas e cura; mais barato para quem tem o anel verde
 *  Ves'Kai   não trocam item por item: só por Gratidão (ganha curando quem sofre)
 *  Zaëlith   presentes e cristais
 *  O Coletor almas
 */
public final class RaceTrades {

    public static MerchantOffers of(Race race) {
        MerchantOffers o = new MerchantOffers();
        switch (race) {
            case BRAKKARI -> {
                t(o, new ItemStack(Items.BONE, 24), ItemStack.EMPTY, new ItemStack(LoreItems.IRA_BRUTA.get()));
                t(o, new ItemStack(Items.SKELETON_SKULL), ItemStack.EMPTY, new ItemStack(LoreItems.IRA_BRUTA.get(), 3));
                t(o, new ItemStack(Items.ZOMBIE_HEAD), ItemStack.EMPTY, new ItemStack(LoreItems.IRA_BRUTA.get(), 3));
                t(o, new ItemStack(Items.BONE, 12), ItemStack.EMPTY, new ItemStack(LoreItems.CARNE_DE_FERA.get(), 2));
                t(o, new ItemStack(LoreItems.IRA_BRUTA.get(), 8), new ItemStack(Items.NETHERITE_SCRAP, 2), new ItemStack(LoreItems.MARTELO_BRAKKARI.get()));
            }
            case GHALLOS -> {
                t(o, new ItemStack(Items.GOLD_INGOT, 6), ItemStack.EMPTY, new ItemStack(Items.EMERALD));
                t(o, new ItemStack(Items.DIAMOND), ItemStack.EMPTY, new ItemStack(Items.EMERALD, 3));
                t(o, new ItemStack(Items.EMERALD, 12), ItemStack.EMPTY, new ItemStack(LoreItems.AMBAR_DA_POSSE.get()));
                t(o, new ItemStack(Items.EMERALD, 8), ItemStack.EMPTY, new ItemStack(LoreItems.PERGAMINHO_SEGREDO.get()));
                t(o, new ItemStack(Items.EMERALD, 10), ItemStack.EMPTY, new ItemStack(Items.GOLDEN_APPLE));
                t(o, new ItemStack(Items.EMERALD, 6), ItemStack.EMPTY, new ItemStack(Items.ENDER_PEARL, 2));
                t(o, new ItemStack(Items.EMERALD, 20), ItemStack.EMPTY, new ItemStack(Items.NETHERITE_SCRAP));
            }
            case SKRELL_VATH -> {
                t(o, new ItemStack(Items.SPIDER_EYE, 4), ItemStack.EMPTY, new ItemStack(LoreItems.FRAGMENTO_DE_PAVOR.get()));
                t(o, new ItemStack(Items.GUNPOWDER, 8), ItemStack.EMPTY, new ItemStack(LoreItems.PERGAMINHO_SEGREDO.get()));
                t(o, new ItemStack(Items.SKELETON_SKULL), ItemStack.EMPTY, new ItemStack(LoreItems.FRAGMENTO_DE_PAVOR.get(), 3));
                t(o, new ItemStack(Items.CREEPER_HEAD), ItemStack.EMPTY, new ItemStack(LoreItems.PERGAMINHO_SEGREDO.get(), 3));
            }
            case VELTARI -> {
                Item m = LoreItems.MERITO.get();
                t(o, new ItemStack(m, 8), new ItemStack(Items.NETHERITE_INGOT), new ItemStack(LoreItems.ANEL_VAZIO.get()));
                t(o, new ItemStack(m, 4), ItemStack.EMPTY, new ItemStack(LoreItems.JADE_ESTELAR.get(), 4));
                t(o, new ItemStack(m, 2), ItemStack.EMPTY, new ItemStack(Items.EMERALD, 8));
                ItemStack helmet = new ItemStack(ModArmorItems.pieceFor("verde", EquipmentSlot.HEAD));
                if (!helmet.isEmpty()) {
                    t(o, new ItemStack(m, 6), ItemStack.EMPTY, helmet);
                    t(o, new ItemStack(m, 8), ItemStack.EMPTY, new ItemStack(ModArmorItems.pieceFor("verde", EquipmentSlot.CHEST)));
                    t(o, new ItemStack(m, 7), ItemStack.EMPTY, new ItemStack(ModArmorItems.pieceFor("verde", EquipmentSlot.LEGS)));
                    t(o, new ItemStack(m, 5), ItemStack.EMPTY, new ItemStack(ModArmorItems.pieceFor("verde", EquipmentSlot.FEET)));
                }
            }
            case AELINOS -> {
                t(o, new ItemStack(Items.COD, 12), new ItemStack(Items.SALMON, 6), new ItemStack(LoreItems.PEROLA_DO_AMANHA.get()));
                t(o, new ItemStack(Items.KELP, 16), ItemStack.EMPTY, new ItemStack(LoreItems.BENCAO_AELINOS.get()));
                t(o, new ItemStack(Items.GLISTERING_MELON_SLICE, 2), ItemStack.EMPTY, new ItemStack(Items.EMERALD, 2));
                t(o, new ItemStack(LoreItems.PEROLA_DO_AMANHA.get()), ItemStack.EMPTY, new ItemStack(LoreItems.BENCAO_AELINOS.get(), 3));
                t(o, new ItemStack(Items.TROPICAL_FISH, 4), ItemStack.EMPTY, new ItemStack(Items.HEART_OF_THE_SEA));
            }
            case VES_KAI -> {
                Item g = LoreItems.GRATIDAO.get();
                t(o, new ItemStack(g, 2), ItemStack.EMPTY, new ItemStack(LoreItems.LAGRIMA_INDIGO.get()));
                t(o, new ItemStack(g, 1), ItemStack.EMPTY, new ItemStack(LoreItems.ERVA_TALEUN.get(), 4));
                t(o, new ItemStack(g, 3), ItemStack.EMPTY, new ItemStack(Items.GOLDEN_APPLE));
                t(o, new ItemStack(g, 6), ItemStack.EMPTY, new ItemStack(LoreItems.COLAR_PROTECAO.get()));
            }
            case ZAELITH -> {
                t(o, new ItemStack(Items.AMETHYST_SHARD, 3), new ItemStack(Items.EMERALD, 2), new ItemStack(LoreItems.CRISTAL_DEVOTO.get()));
                t(o, new ItemStack(Items.DIAMOND), ItemStack.EMPTY, new ItemStack(LoreItems.CRISTAL_DEVOTO.get(), 2));
                t(o, new ItemStack(LoreItems.CRISTAL_DEVOTO.get()), new ItemStack(Items.EMERALD, 4), new ItemStack(LoreItems.COLAR_PROTECAO.get()));
            }
            case COLETOR -> {
                Item a = LoreItems.ALMA.get();
                t(o, new ItemStack(a, 4), ItemStack.EMPTY, new ItemStack(LoreItems.OBSIDIANA_DO_FIM.get()));
                t(o, new ItemStack(a, 12), ItemStack.EMPTY, new ItemStack(LoreItems.RELIQUIA.get()));
                t(o, new ItemStack(LoreItems.RELIQUIA.get()), new ItemStack(a, 8), new ItemStack(Items.TOTEM_OF_UNDYING));
            }
            default -> {}
        }
        return o;
    }

    private static void t(MerchantOffers o, ItemStack a, ItemStack b, ItemStack result) {
        if (result.isEmpty()) return;
        o.add(new MerchantOffer(a, b, result, 16, 4, 0.05f));
    }

    private RaceTrades() {}
}
