package com.lanterna.selection;

import com.lanterna.LanternaConfig;
import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.item.DormantRingItem;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * O Chamado: os anéis prestam atenção em quem se destaca na Terra.
 * Cada conquista da lista (config "conquistas_chamado") é um sinal. Ao juntar os sinais necessários,
 * um anel aleatório (nunca o Branco) cruza o espaço e escolhe o jogador.
 * Os sinais ficam no NBT persistente do jogador (sobrevivem à morte).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CallingEvents {

    private static final String KEY = "lanterna_chamado";

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String id = event.getAdvancement().getId().toString();
        if (!LanternaConfig.CALL_ADVANCEMENTS.get().contains(id)) return;
        int required = LanternaConfig.CALL_REQUIRED.get();
        if (required <= 0) return;
        RingData data = ModCapabilities.get(player).orElse(null);
        if (data == null || data.hasRing() || data.isSelectionPending()) return;

        ListTag signs = signs(player);
        for (Tag t : signs) if (t.getAsString().equals(id)) return;
        signs.add(StringTag.valueOf(id));

        int count = signs.size();
        if (count < required) {
            player.sendSystemMessage(Component.translatable("message.lanterna.call_sign", count, required)
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.6f + count * 0.15f);
            return;
        }
        BuiltinCorps corps = DormantRingItem.random(player.getRandom());
        player.sendSystemMessage(Component.translatable("message.lanterna.call_answered")
                .withStyle(s -> s.withColor(corps.color()).withItalic(true)));
        if (SelectionManager.startSelection(player, corps.id(), "call")) signs.clear();
    }

    /** Sinais já recebidos (para o diário e para o comando de teste). */
    public static int count(Player player) {
        return signs(player).size();
    }

    public static void reset(Player player) {
        persisted(player).remove(KEY);
    }

    private static ListTag signs(Player player) {
        CompoundTag root = persisted(player);
        if (!root.contains(KEY, Tag.TAG_LIST)) root.put(KEY, new ListTag());
        return root.getList(KEY, Tag.TAG_STRING);
    }

    private static CompoundTag persisted(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private CallingEvents() {}
}
