package com.lanterna.alien;

import com.lanterna.registry.ModItems;
import com.lanterna.space.Archetype;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Random;

/**
 * Trocas de cada espécie, ligadas ao planeta dela. Cada planeta das Tropas tem o seu material raro
 * (o "coração" daquele planeta); os planetas gerados trocam liga alienígena e coisas do tipo deles.
 * Base para as futuras receitas de baterias e anéis.
 */
public final class AlienTrades {

    public static MerchantOffers forSpecies(AlienSpecies species) {
        MerchantOffers o = new MerchantOffers();
        switch (species.id()) {
            case "oa" -> {
                buy(o, Items.IRON_INGOT, 12, 1);
                sell(o, 6, ModItems.OA_CRYSTAL.get(), 1);
                swap(o, ModItems.OA_CRYSTAL.get(), 1, Items.EMERALD, 3);
                trade(o, new ItemStack(ModItems.OA_CRYSTAL.get()), new ItemStack(Items.LAPIS_LAZULI, 8), new ItemStack(Items.EXPERIENCE_BOTTLE, 4));
            }
            case "korugar" -> {
                buy(o, Items.GOLD_INGOT, 8, 1);
                sell(o, 7, ModItems.KORUGAR_AMBER.get(), 1);
                buy(o, Items.BONE, 16, 1);
                swap(o, ModItems.KORUGAR_AMBER.get(), 1, Items.EMERALD, 3);
            }
            case "ysmault" -> {
                buy(o, Items.ROTTEN_FLESH, 24, 1);
                trade(o, new ItemStack(Items.EMERALD, 4), new ItemStack(Items.REDSTONE, 8), new ItemStack(ModItems.YSMAULT_BLOODSTONE.get()));
                swap(o, ModItems.YSMAULT_BLOODSTONE.get(), 1, Items.BLAZE_POWDER, 4);
            }
            case "odym" -> {
                buy(o, Items.SNOWBALL, 32, 1);
                sell(o, 6, ModItems.ODYM_FROST.get(), 1);
                swap(o, ModItems.ODYM_FROST.get(), 1, Items.BLUE_ICE, 4);
            }
            case "zamaron" -> {
                buy(o, Items.POPPY, 12, 1);
                trade(o, new ItemStack(Items.EMERALD, 5), new ItemStack(Items.PINK_PETALS, 4), new ItemStack(ModItems.ZAMARON_HEARTSTONE.get()));
                swap(o, ModItems.ZAMARON_HEARTSTONE.get(), 1, Items.GOLDEN_APPLE, 1);
            }
            case "nok" -> {
                buy(o, Items.LAPIS_LAZULI, 10, 1);
                sell(o, 7, ModItems.NOK_INDIGO.get(), 1);
                swap(o, ModItems.NOK_INDIGO.get(), 1, Items.AMETHYST_SHARD, 6);
            }
            case "okaara" -> {
                swap(o, Items.GOLD_BLOCK, 1, Items.EMERALD, 3);
                sell(o, 9, ModItems.OKAARA_GOLD.get(), 1);
                swap(o, ModItems.OKAARA_GOLD.get(), 1, Items.GOLD_BLOCK, 2);
            }
            case "ryut" -> {
                buy(o, Items.BONE, 20, 1);
                trade(o, new ItemStack(Items.EMERALD, 6), new ItemStack(Items.COAL, 16), new ItemStack(ModItems.RYUT_OBSIDIAN.get()));
                swap(o, ModItems.RYUT_OBSIDIAN.get(), 1, Items.CRYING_OBSIDIAN, 4);
            }
            default -> generated(o, species);
        }
        return o;
    }

    /** Planetas gerados: liga alienígena + duas trocas típicas do tipo do planeta. */
    private static void generated(MerchantOffers o, AlienSpecies species) {
        Random r = new Random(species.id().hashCode() * 31L + 7);
        sell(o, 4 + r.nextInt(3), ModItems.ALIEN_ALLOY.get(), 1);
        swap(o, ModItems.ALIEN_ALLOY.get(), 1, Items.EMERALD, 2);
        Archetype type = species.archetype() != null ? species.archetype() : Archetype.LUSH;
        Item[][] pool = switch (type) {
            case LUSH -> new Item[][]{{Items.WHEAT}, {Items.OAK_LOG}, {Items.APPLE}};
            case DESERT -> new Item[][]{{Items.CACTUS}, {Items.SANDSTONE}, {Items.DEAD_BUSH}};
            case ICE -> new Item[][]{{Items.SNOW_BLOCK}, {Items.PACKED_ICE}, {Items.COD}};
            case VOLCANIC -> new Item[][]{{Items.MAGMA_CREAM}, {Items.BASALT}, {Items.BLAZE_ROD}};
            case OCEAN -> new Item[][]{{Items.KELP}, {Items.PRISMARINE_SHARD}, {Items.TROPICAL_FISH}};
            case CRYSTAL -> new Item[][]{{Items.AMETHYST_SHARD}, {Items.QUARTZ}, {Items.CALCITE}};
            case FUNGAL -> new Item[][]{{Items.RED_MUSHROOM}, {Items.BROWN_MUSHROOM}, {Items.MYCELIUM}};
            case BARREN -> new Item[][]{{Items.IRON_NUGGET}, {Items.FLINT}, {Items.TUFF}};
        };
        Item wanted = pool[r.nextInt(pool.length)][0];
        buy(o, wanted, 10 + r.nextInt(14), 1);
        Item offered = pool[r.nextInt(pool.length)][0];
        sell(o, 1 + r.nextInt(2), offered, 4 + r.nextInt(8));
    }

    // ---- atalhos ----

    /** O alienígena compra: N itens -> esmeraldas. */
    private static void buy(MerchantOffers o, Item item, int count, int emeralds) {
        trade(o, new ItemStack(item, count), ItemStack.EMPTY, new ItemStack(Items.EMERALD, emeralds));
    }

    /** O alienígena vende: esmeraldas -> item. */
    private static void sell(MerchantOffers o, int emeralds, Item item, int count) {
        trade(o, new ItemStack(Items.EMERALD, emeralds), ItemStack.EMPTY, new ItemStack(item, count));
    }

    private static void swap(MerchantOffers o, Item in, int inCount, Item out, int outCount) {
        trade(o, new ItemStack(in, inCount), ItemStack.EMPTY, new ItemStack(out, outCount));
    }

    private static void trade(MerchantOffers o, ItemStack a, ItemStack b, ItemStack result) {
        o.add(new MerchantOffer(a, b, result, 12, 5, 0.05f));
    }

    private AlienTrades() {}
}
