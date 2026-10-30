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

    /** Aba "Naves": naves (todas as pinturas), blocos de interior, itens das Tropas e o ovo das espécies. */
    public static final RegistryObject<CreativeModeTab> NAVES = TABS.register("naves",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.lanterna.naves"))
                    .withTabsBefore(MAIN.getId())
                    .icon(() -> com.lanterna.naves.NaveItem.of(com.lanterna.naves.NaveTipo.NAVE_LANCEIRA, "green"))
                    .displayItems((params, output) -> {
                        for (var t : com.lanterna.naves.NaveTipo.values()) {
                            if (t.destrocos()) continue;
                            if (t.pinturas.equals("tropa")) {
                                for (BuiltinCorps c : BuiltinCorps.values()) {
                                    if (c != BuiltinCorps.WHITE) output.accept(com.lanterna.naves.NaveItem.of(t, c.id().getPath()));
                                }
                            } else {
                                output.accept(com.lanterna.naves.NaveItem.of(t, "1"));
                                output.accept(com.lanterna.naves.NaveItem.of(t, "2"));
                            }
                        }
                        output.accept(com.lanterna.naves.NaveItem.of(com.lanterna.naves.NaveTipo.DESTROCOS_1, ""));
                        for (var b : com.lanterna.naves.bloco.NaveBlocks.ITEMS) output.accept(b.get());
                        for (var i : com.lanterna.tropa.TropaItems.ALL) output.accept(i.get());
                        output.accept(com.lanterna.guilda.GuildaItems.BANDEIRA_ITEM.get());
                        output.accept(com.lanterna.guilda.GuildaItems.ESSENCIA.get());
                        output.accept(com.lanterna.guilda.GuildaItems.ANEL_RECRUTA.get());
                        output.accept(com.lanterna.especies.EspeciesEntities.OVO.get());
                    })
                    .build());

    private ModCreativeTabs() {}
}
