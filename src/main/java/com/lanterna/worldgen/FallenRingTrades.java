package com.lanterna.worldgen;

import com.lanterna.LanternaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** O cartógrafo (nível Aprendiz em diante) vende o mapa até a cratera do Anel Caído mais próxima. */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FallenRingTrades {

    public static final TagKey<Structure> MAP_TAG = TagKey.create(Registries.STRUCTURE, LanternaMod.id("on_anel_caido_maps"));

    @SubscribeEvent
    public static void onTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.CARTOGRAPHER) return;
        event.getTrades().get(2).add((trader, random) -> {
            if (!(trader.level() instanceof ServerLevel level)) return null;
            BlockPos pos = level.findNearestMapStructure(MAP_TAG, trader.blockPosition(), 100, true);
            if (pos == null) return null;
            ItemStack map = MapItem.create(level, pos.getX(), pos.getZ(), (byte) 2, true, true);
            MapItem.renderBiomePreviewMap(level, map);
            MapItemSavedData.addTargetDecoration(map, pos, "+", MapDecoration.Type.RED_X);
            map.setHoverName(Component.translatable("filled_map.lanterna.anel_caido"));
            return new MerchantOffer(new ItemStack(Items.EMERALD, 14), new ItemStack(Items.COMPASS), map, 2, 10, 0.2f);
        });
    }

    private FallenRingTrades() {}
}
