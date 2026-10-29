package com.lanterna.lore.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Item simples da lore: mostra a descrição ("item.lanterna.<nome>.desc") e pode ter brilho e um uso.
 */
public class LoreItem extends Item {
    public interface Use {
        /** Devolve true se usou (gasta 1 item, a não ser que o uso diga outra coisa). */
        boolean use(Level level, Player player, ItemStack stack);
    }

    private final boolean foil;
    @Nullable private final Use use;
    private final int cooldown;
    private final boolean consume;

    public LoreItem(Properties props, boolean foil) {
        this(props, foil, null, 0, false);
    }

    public LoreItem(Properties props, boolean foil, @Nullable Use use, int cooldown, boolean consume) {
        super(props);
        this.foil = foil;
        this.use = use;
        this.cooldown = cooldown;
        this.consume = consume;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return foil || super.isFoil(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (use == null) return super.use(level, player, hand);
        if (!level.isClientSide && use.use(level, player, stack)) {
            if (consume && !player.getAbilities().instabuild) stack.shrink(1);
            if (cooldown > 0) player.getCooldowns().addCooldown(this, cooldown);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String key = getDescriptionId() + ".desc";
        if (level != null && level.isClientSide && I18n.exists(key)) {
            for (String line : I18n.get(key).split("\n")) {
                tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
