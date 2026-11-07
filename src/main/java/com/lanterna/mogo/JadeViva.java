package com.lanterna.mogo;

import com.lanterna.corps.BuiltinCorps;
import com.lanterna.item.BatteryItems;
import com.lanterna.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Jade Viva: o material vivo do Mogo. Encaixa na bateria dos Lanternas Verdes (clique com a Jade em cima da bateria
 * no inventário). Cada Jade sobe um nível:
 *  I    +25% de carga no anel;
 *  II   +50% de carga e a bateria vai recarregando o anel devagar sozinha;
 *  III  +75% de carga e o Selo do Mogo: com a bateria na mão, botão direito num alvo chama o Mogo.
 */
public class JadeViva extends Item {
    public static final String TAG = "JadeMogo";
    public static final int MAX = 3;

    public static RegistryObject<Item> ITEM;

    public static void init() {
        ITEM = ModItems.ITEMS.register("jade_viva", () -> new JadeViva(new Item.Properties().rarity(Rarity.EPIC).stacksTo(16)));
    }

    public JadeViva(Properties p) {
        super(p);
    }

    public static int nivel(ItemStack bateria) {
        return bateria.hasTag() ? Math.min(MAX, bateria.getTag().getInt(TAG)) : 0;
    }

    public static void definir(ItemStack bateria, int n) {
        bateria.getOrCreateTag().putInt(TAG, Math.max(0, Math.min(MAX, n)));
    }

    /** Só as baterias verdes aceitam a Jade. */
    public static boolean aceita(ItemStack s) {
        return BatteryItems.isBattery(s) && BatteryItems.corpsOf(s).equals(BuiltinCorps.GREEN.id());
    }

    /** Multiplicador da carga máxima do anel. */
    public static float bonusCarga(int nivel) {
        return 1f + 0.25f * nivel;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack jade, Slot slot, ClickAction acao, Player p) {
        if (acao != ClickAction.PRIMARY) return false;
        ItemStack alvo = slot.getItem();
        if (!aceita(alvo)) return false;
        int n = nivel(alvo);
        if (n >= MAX) {
            if (p.level().isClientSide) p.displayClientMessage(Component.translatable("mogo.lanterna.jade.cheia").withStyle(ChatFormatting.GREEN), true);
            return true;
        }
        definir(alvo, n + 1);
        jade.shrink(1);
        slot.setChanged();
        p.playSound(n + 1 == MAX ? SoundEvents.BEACON_ACTIVATE : SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
        if (!p.level().isClientSide)
            p.displayClientMessage(Component.translatable("mogo.lanterna.jade.subiu." + (n + 1)).withStyle(ChatFormatting.GREEN), false);
        return true;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack jade, ItemStack outro, Slot slot, ClickAction acao, Player p, SlotAccess cursor) {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack s) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.translatable("item.lanterna.jade_viva.desc").withStyle(ChatFormatting.GRAY));
    }
}
