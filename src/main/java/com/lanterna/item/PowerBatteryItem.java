package com.lanterna.item;

import com.lanterna.corps.BuiltinCorps;
import com.lanterna.corps.CorpsDefinition;
import com.lanterna.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Item da bateria. A Tropa vai em BlockEntityTag, então ao colocar o bloco
 * o Minecraft já passa a Tropa para a bateria sozinho.
 */
public class PowerBatteryItem extends BlockItem {

    public PowerBatteryItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** A bateria da Tropa (o cajado para a Índigo; vazio para a Branca). */
    public static ItemStack create(ResourceLocation corps, int color) {
        return BatteryItems.create(corps, color);
    }

    /** O bloco-bateria com a Tropa gravada. */
    public static ItemStack createBlock(ResourceLocation corps, int color) {
        ItemStack stack = new ItemStack(ModItems.POWER_BATTERY.get());
        CompoundTag beTag = new CompoundTag();
        beTag.putString("Corps", corps.toString());
        beTag.putInt("Color", color);
        stack.getOrCreateTag().put("BlockEntityTag", beTag);
        return stack;
    }

    public static ResourceLocation getCorps(ItemStack stack) {
        CompoundTag be = stack.getTagElement("BlockEntityTag");
        if (be != null && be.contains("Corps")) {
            ResourceLocation id = ResourceLocation.tryParse(be.getString("Corps"));
            if (id != null) return id;
        }
        return BuiltinCorps.GREEN.id();
    }

    public static int getColor(ItemStack stack) {
        CompoundTag be = stack.getTagElement("BlockEntityTag");
        if (be != null && be.contains("Color")) return be.getInt("Color");
        return BuiltinCorps.colorOf(getCorps(stack));
    }

    /** Cada Tropa tem a sua bateria com nome próprio (Lanterna de Vigia, Braseiro, Relicário...). */
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("block.lanterna.power_battery." + getCorps(stack).getPath());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int color = getColor(stack);
        tooltip.add(Component.translatable(CorpsDefinition.fullNameKey(getCorps(stack)))
                .withStyle(s -> s.withColor(color)));
        tooltip.add(Component.translatable("tooltip.lanterna.battery_use").withStyle(ChatFormatting.DARK_GRAY));
        int jade = com.lanterna.mogo.JadeViva.nivel(stack);
        if (jade > 0) tooltip.add(Component.translatable("mogo.lanterna.jade.nivel." + jade).withStyle(ChatFormatting.GREEN));
    }

    /** Bateria verde com o Selo do Mogo (Jade III): botão direito num alvo chama o Mogo. */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player p, net.minecraft.world.InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        // no núcleo do Mogo, com a Ferida no casulo, qualquer bateria verde faz o primeiro Chamado
        if (p instanceof net.minecraft.server.level.ServerPlayer sp0 && com.lanterna.mogo.JadeViva.aceita(s)
                && com.lanterna.mogo.MogoMissoes.tentarChamadoNucleo(sp0))
            return net.minecraft.world.InteractionResultHolder.success(s);
        if (com.lanterna.mogo.JadeViva.nivel(s) >= com.lanterna.mogo.JadeViva.MAX && com.lanterna.mogo.JadeViva.aceita(s)) {
            if (p instanceof net.minecraft.server.level.ServerPlayer sp) com.lanterna.mogo.ChamadoMogo.usar(sp, s);
            return net.minecraft.world.InteractionResultHolder.sidedSuccess(s, level.isClientSide);
        }
        return super.use(level, p, hand);
    }

    /** Com o Selo, mirar num bloco também chama (o alvo é quem estiver na mira); agachado coloca a bateria. */
    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext c) {
        ItemStack s = c.getItemInHand();
        if (c.getPlayer() != null && !c.getPlayer().isShiftKeyDown()
                && com.lanterna.mogo.JadeViva.nivel(s) >= com.lanterna.mogo.JadeViva.MAX && com.lanterna.mogo.JadeViva.aceita(s)) {
            if (c.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp) com.lanterna.mogo.ChamadoMogo.usar(sp, s);
            return net.minecraft.world.InteractionResult.sidedSuccess(c.getLevel().isClientSide);
        }
        return super.useOn(c);
    }
}
