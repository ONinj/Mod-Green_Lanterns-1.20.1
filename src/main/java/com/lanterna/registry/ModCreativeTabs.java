package com.lanterna.registry;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.item.PowerBatteryItem;
import com.lanterna.item.PowerRingItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
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
                        for (BuiltinCorps corps : BuiltinCorps.values()) {
                            output.accept(PowerBatteryItem.create(corps.id(), corps.color()));
                        }
                    })
                    .build());

    private ModCreativeTabs() {}
}
