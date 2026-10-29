package com.lanterna.registry;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.item.PowerBatteryItem;
import com.lanterna.item.PowerRingItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LanternaMod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.lanterna"))
                    .icon(() -> PowerRingItem.create(BuiltinCorps.GREEN.id(), BuiltinCorps.GREEN.color(), -1))
                    .displayItems((params, output) -> {
                        for (BuiltinCorps corps : BuiltinCorps.values()) {
                            output.accept(PowerRingItem.create(corps.id(), corps.color(), -1));
                        }
                        // Baterias (blocos), o Cajado da Compaixão (Índigo); a Tropa da Vida não tem bateria
                        for (BuiltinCorps corps : BuiltinCorps.values()) {
                            ItemStack battery = PowerBatteryItem.create(corps.id(), corps.color());
                            if (!battery.isEmpty()) output.accept(battery);
                        }
                        // Uniformes 3D das 9 Tropas (máscara, peitoral, calça e botas)
                        for (var piece : com.lanterna.armor.ModArmorItems.all()) output.accept(piece);
                        // Lore: minérios, drops dos bosses, moedas, presentes, blocos das bases e ovos
                        for (var item : com.lanterna.lore.LoreItems.ALL) output.accept(item.get());
                        for (var block : com.lanterna.lore.LoreBlocks.ALL) output.accept(block.get());
                        for (var egg : com.lanterna.lore.LoreEntities.EGGS) output.accept(egg.get());
                        output.accept(com.lanterna.item.DormantRingItem.create());
                        output.accept(ModItems.CHISELED_BATTERY.get());
                        output.accept(ModItems.ALIEN_ALLOY.get());
                        output.accept(ModItems.ALIEN_SPAWN_EGG.get());
                    })
                    .build());

    private ModCreativeTabs() {}
}
