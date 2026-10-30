package com.lanterna.especies;

import com.lanterna.lore.LoreItems;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;

/** Mercadores do espaço: compram sucata de nave e vendem peças, minérios dos planetas e mapas. */
public final class MercadorTrades {

    public static MerchantOffers ofertas(RandomSource r) {
        List<MerchantOffer> all = new ArrayList<>();
        all.add(t(new ItemStack(com.lanterna.tropa.TropaItems.FRAGMENTO_CASCO.get(), 4), new ItemStack(Items.EMERALD, 3)));
        all.add(t(new ItemStack(Items.EMERALD, 10), new ItemStack(com.lanterna.tropa.TropaItems.NUCLEO_PROPULSOR.get())));
        all.add(t(new ItemStack(Items.EMERALD, 6), new ItemStack(com.lanterna.tropa.TropaItems.FRAGMENTO_CASCO.get(), 3)));
        all.add(t(new ItemStack(Items.EMERALD, 18), new ItemStack(com.lanterna.tropa.TropaItems.BLASTER_PIRATA.get())));
        all.add(t(new ItemStack(LoreItems.MERITO.get(), 2), new ItemStack(Items.EMERALD, 5)));
        all.add(t(new ItemStack(Items.EMERALD, 8), new ItemStack(LoreItems.JADE_ESTELAR.get(), 2)));
        all.add(t(new ItemStack(Items.EMERALD, 8), new ItemStack(LoreItems.PEROLA_DO_AMANHA.get(), 2)));
        all.add(t(new ItemStack(Items.EMERALD, 8), new ItemStack(LoreItems.AMBAR_DA_POSSE.get(), 2)));
        all.add(t(new ItemStack(Items.EMERALD, 8), new ItemStack(LoreItems.IRA_BRUTA.get(), 2)));
        all.add(t(new ItemStack(Items.EMERALD, 12), new ItemStack(LoreItems.FRAGMENTO_DE_PAVOR.get(), 2)));
        all.add(t(new ItemStack(Items.EMERALD, 12), new ItemStack(LoreItems.CRISTAL_DEVOTO.get(), 2)));
        all.add(t(new ItemStack(Items.EMERALD, 12), new ItemStack(LoreItems.LAGRIMA_INDIGO.get(), 2)));
        all.add(t(new ItemStack(Items.EMERALD, 4), new ItemStack(Items.COOKED_BEEF, 8)));
        all.add(t(new ItemStack(Items.EMERALD, 20), new ItemStack(LoreItems.ANEL_VAZIO.get())));
        MerchantOffers out = new MerchantOffers();
        for (int i = 0; i < 6 && !all.isEmpty(); i++) out.add(all.remove(r.nextInt(all.size())));
        return out;
    }

    private static MerchantOffer t(ItemStack cost, ItemStack result) {
        return new MerchantOffer(cost, ItemStack.EMPTY, result, 8, 4, 0.05f);
    }

    private MercadorTrades() {}
}
