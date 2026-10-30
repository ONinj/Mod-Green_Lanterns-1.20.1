package com.lanterna.tropa;

import com.lanterna.corps.BuiltinCorps;
import com.lanterna.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Itens das Tropas: armas dos piratas, chave da nave, peças de nave, contrato e as insígnias de posto. */
public final class TropaItems {
    public static final List<RegistryObject<Item>> ALL = new ArrayList<>();

    public static final RegistryObject<Item> BLASTER_PIRATA = reg("blaster_pirata",
            () -> new Blaster(new Item.Properties().stacksTo(1).durability(300), 5f, 16, 0xFF3020, false));
    public static final RegistryObject<Item> BLASTER_CAPITAO = reg("blaster_capitao",
            () -> new Blaster(new Item.Properties().stacksTo(1).durability(500).rarity(Rarity.UNCOMMON), 8f, 22, 0xFF6A10, true));
    public static final RegistryObject<Item> CHAVE_NAVE = reg("chave_nave",
            () -> new ChaveNave(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> FRAGMENTO_CASCO = reg("fragmento_casco", () -> new Desc(new Item.Properties()));
    public static final RegistryObject<Item> NUCLEO_PROPULSOR = reg("nucleo_propulsor", () -> new Desc(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> CONTRATO = reg("contrato", () -> new Desc(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> INSIGNIA_RECRUTA = reg("insignia_recruta", () -> new Insignia(new Item.Properties().stacksTo(1), 0));
    public static final RegistryObject<Item> INSIGNIA_PATRULHEIRO = reg("insignia_patrulheiro", () -> new Insignia(new Item.Properties().stacksTo(1), 1));
    public static final RegistryObject<Item> INSIGNIA_OFICIAL = reg("insignia_oficial", () -> new Insignia(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), 2));
    public static final RegistryObject<Item> INSIGNIA_VETERANO = reg("insignia_veterano", () -> new Insignia(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), 3));
    public static final RegistryObject<Item> INSIGNIA_GUARDA_HONRA = reg("insignia_guarda_honra", () -> new Insignia(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 4));

    private static RegistryObject<Item> reg(String name, java.util.function.Supplier<Item> s) {
        RegistryObject<Item> r = ModItems.ITEMS.register(name, s);
        ALL.add(r);
        return r;
    }

    public static Item insignia(int posto) {
        return switch (posto) {
            case 1 -> INSIGNIA_PATRULHEIRO.get();
            case 2 -> INSIGNIA_OFICIAL.get();
            case 3 -> INSIGNIA_VETERANO.get();
            case 4 -> INSIGNIA_GUARDA_HONRA.get();
            default -> INSIGNIA_RECRUTA.get();
        };
    }

    /** Cor da Tropa gravada no item ("Corps"), para tingir a chave e a insígnia da Guarda de Honra. */
    public static int corDaTropa(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("Corps")) {
            ResourceLocation id = ResourceLocation.tryParse(stack.getTag().getString("Corps"));
            if (id != null) return BuiltinCorps.colorOf(id);
        }
        return BuiltinCorps.GREEN.color();
    }

    public static void init() {}

    // ===================== Classes =====================

    public static class Desc extends Item {
        public Desc(Properties p) { super(p); }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
            tip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Pistola de plasma: botão direito dispara. */
    public static class Blaster extends Desc {
        private final float damage;
        private final int cooldown;
        private final int color;
        private final boolean explosive;

        public Blaster(Properties p, float damage, int cooldown, int color, boolean explosive) {
            super(p);
            this.damage = damage; this.cooldown = cooldown; this.color = color; this.explosive = explosive;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide) {
                Vec3 look = player.getLookAngle();
                Vec3 from = player.getEyePosition().add(look.scale(0.6)).add(0, -0.15, 0);
                TiroPlasma.shoot(level, player, from, look, 2.2f, damage, color, explosive);
                stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            }
            player.getCooldowns().addCooldown(this, cooldown);
            player.playSound(SoundEvents.BLAZE_SHOOT, 0.5f, 1.8f);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
    }

    /** Chave de uma nave: guarda o UUID da nave e a Tropa (cor da luz). Só quem tem a chave pilota. */
    public static class ChaveNave extends Item {
        public ChaveNave(Properties p) { super(p); }

        public static ItemStack forShip(java.util.UUID ship, String tipo, @Nullable String corps) {
            ItemStack s = new ItemStack(CHAVE_NAVE.get());
            s.getOrCreateTag().putUUID("Nave", ship);
            s.getTag().putString("Tipo", tipo);
            if (corps != null) s.getTag().putString("Corps", "lanterna:" + corps);
            return s;
        }

        @Nullable
        public static java.util.UUID ship(ItemStack s) {
            return s.hasTag() && s.getTag().hasUUID("Nave") ? s.getTag().getUUID("Nave") : null;
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
            if (stack.hasTag() && stack.getTag().contains("Tipo")) {
                tip.add(Component.translatable("entity.lanterna." + stack.getTag().getString("Tipo")).withStyle(ChatFormatting.GRAY));
            } else {
                tip.add(Component.translatable("item.lanterna.chave_nave.blank").withStyle(ChatFormatting.DARK_GRAY));
            }
            tip.add(Component.translatable("item.lanterna.chave_nave.desc").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static class Insignia extends Item {
        private final int posto;
        public Insignia(Properties p, int posto) { super(p); this.posto = posto; }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
            tip.add(Component.translatable("posto.lanterna." + posto).withStyle(ChatFormatting.GOLD));
            tip.add(Component.translatable("posto.lanterna." + posto + ".desc").withStyle(ChatFormatting.GRAY));
        }
    }

    private TropaItems() {}
}
