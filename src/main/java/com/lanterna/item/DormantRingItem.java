package com.lanterna.item;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.registry.ModItems;
import com.lanterna.selection.SelectionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Anel Apagado: um anel sem cor e sem nenhuma energia, achado no fundo da cratera do Anel Caído.
 * Ninguém sabe de qual Tropa ele é, nem ele mesmo. Clique com ele numa Bateria Cinzelada:
 * a bateria se parte, o anel desperta, escolhe uma Tropa ao acaso e chega com só 10% de carga.
 */
public class DormantRingItem extends Item {

    /** Tropas que o anel pode escolher (o Branco só nasce da fusão). */
    public static final BuiltinCorps[] FALLEN = {
            BuiltinCorps.GREEN, BuiltinCorps.YELLOW, BuiltinCorps.RED, BuiltinCorps.BLUE,
            BuiltinCorps.VIOLET, BuiltinCorps.INDIGO, BuiltinCorps.ORANGE, BuiltinCorps.BLACK};

    public DormantRingItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create() {
        return new ItemStack(ModItems.DORMANT_RING.get());
    }

    public static BuiltinCorps random(RandomSource random) {
        return FALLEN[random.nextInt(FALLEN.length)];
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.lanterna.dormant_need_battery"), true);
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    /** O anel encosta na Bateria Cinzelada: a bateria quebra e o anel escolhe uma Tropa. */
    public static boolean awaken(ServerPlayer player, ItemStack ring, BlockPos batteryPos) {
        RingData data = ModCapabilities.get(player).orElse(null);
        if (data == null) return false;
        if (data.hasRing() || data.isSelectionPending()) {
            player.displayClientMessage(Component.translatable("message.lanterna.dormant_has_ring"), true);
            return false;
        }
        ServerLevel level = player.serverLevel();
        BuiltinCorps corps = random(player.getRandom());
        if (!SelectionManager.startSelection(player, corps.id(), "dormant")) return false;
        ring.shrink(1);

        // A bateria se parte e a energia pura corre até o anel
        var state = level.getBlockState(batteryPos);
        level.destroyBlock(batteryPos, false);
        Vec3 from = Vec3.atCenterOf(batteryPos);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), from.x, from.y, from.z, 60, 0.3, 0.4, 0.3, 0.15);
        Vec3 to = player.position().add(0, 1.1, 0);
        for (int i = 0; i <= 30; i++) {
            Vec3 p = from.lerp(to, i / 30.0);
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
        }
        level.sendParticles(ParticleTypes.FLASH, to.x, to.y, to.z, 1, 0, 0, 0, 0);
        level.playSound(null, batteryPos, SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS, 0.8f, 1.4f);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 1.3f);
        player.sendSystemMessage(Component.translatable("message.lanterna.dormant_awake").withStyle(ChatFormatting.WHITE));
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.lanterna.dormant_line1").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.lanterna.dormant_line2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.lanterna.dormant_line3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
