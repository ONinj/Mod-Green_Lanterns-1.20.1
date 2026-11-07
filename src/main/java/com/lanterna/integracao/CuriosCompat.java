package com.lanterna.integracao;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Curios (opcional): o anel pode ficar no espaço "ring" e a bateria no "belt" do Curios. O slot próprio do mod
 * (aba Lanterna) continua valendo e tem prioridade. Tudo por reflexão: sem o Curios instalado nada disso roda.
 */
public final class CuriosCompat {
    private static Boolean ativo;
    private static Method getInv, findFirst, slotStack, slotContext, ctxId, ctxIndex, getStacksHandler, getStacks, setStack;

    public static boolean ativo() {
        if (ativo == null) {
            ativo = false;
            if (ModList.get() != null && ModList.get().isLoaded("curios")) {
                try {
                    Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                    getInv = api.getMethod("getCuriosInventory", LivingEntity.class);
                    Class<?> inv = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
                    findFirst = inv.getMethod("findFirstCurio", Predicate.class);
                    getStacksHandler = inv.getMethod("getStacksHandler", String.class);
                    Class<?> res = Class.forName("top.theillusivec4.curios.api.SlotResult");
                    slotStack = res.getMethod("stack");
                    slotContext = res.getMethod("slotContext");
                    Class<?> ctx = Class.forName("top.theillusivec4.curios.api.SlotContext");
                    ctxId = ctx.getMethod("identifier");
                    ctxIndex = ctx.getMethod("index");
                    getStacks = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler").getMethod("getStacks");
                    setStack = Class.forName("top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler").getMethod("setStackInSlot", int.class, ItemStack.class);
                    ativo = true;
                } catch (Throwable t) {
                    com.lanterna.LanternaMod.LOGGER.warn("Curios encontrado, mas a API mudou; integração desligada: {}", t.toString());
                }
            }
        }
        return ativo;
    }

    /** O resultado (SlotResult) do primeiro curio que bate, ou null. */
    private static Object achar(LivingEntity e, Predicate<ItemStack> filtro) {
        if (!ativo()) return null;
        try {
            Object lazy = getInv.invoke(null, e);
            Object inv = ((net.minecraftforge.common.util.LazyOptional<?>) lazy).orElse(null);
            if (inv == null) return null;
            Optional<?> r = (Optional<?>) findFirst.invoke(inv, filtro);
            return r.orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static ItemStack achar(LivingEntity e, Predicate<ItemStack> filtro, boolean tirar) {
        Object r = achar(e, filtro);
        if (r == null) return ItemStack.EMPTY;
        try {
            ItemStack s = (ItemStack) slotStack.invoke(r);
            if (tirar) {
                Object ctx = slotContext.invoke(r);
                Object lazy = getInv.invoke(null, e);
                Object inv = ((net.minecraftforge.common.util.LazyOptional<?>) lazy).orElse(null);
                Optional<?> h = (Optional<?>) getStacksHandler.invoke(inv, (String) ctxId.invoke(ctx));
                if (h.isPresent()) setStack.invoke(getStacks.invoke(h.get()), (int) ctxIndex.invoke(ctx), ItemStack.EMPTY);
            }
            return s;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    private CuriosCompat() {}
}
