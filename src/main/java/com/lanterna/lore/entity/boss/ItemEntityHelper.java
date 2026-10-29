package com.lanterna.lore.entity.boss;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Deixa cair um item que brilha e não some (drops importantes dos bosses). */
public final class ItemEntityHelper {

    public static void dropGlowing(Entity from, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity item = new ItemEntity(from.level(), from.getX(), from.getY() + 1.0, from.getZ(), stack);
        item.setGlowingTag(true);
        item.setUnlimitedLifetime();
        item.setInvulnerable(true);
        item.setDeltaMovement((from.level().random.nextDouble() - 0.5) * 0.2, 0.35, (from.level().random.nextDouble() - 0.5) * 0.2);
        from.level().addFreshEntity(item);
    }

    private ItemEntityHelper() {}
}
