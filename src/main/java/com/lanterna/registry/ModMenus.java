package com.lanterna.registry;

import com.lanterna.LanternaMod;
import com.lanterna.menu.LanternMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, LanternaMod.MODID);

    public static final RegistryObject<MenuType<LanternMenu>> LANTERN = MENUS.register("lantern",
            () -> IForgeMenuType.create((id, inventory, buf) -> new LanternMenu(id, inventory)));

    private ModMenus() {}
}
