package com.lanterna.client.combate;

import com.lanterna.LanternaMod;
import com.lanterna.client.ClientRingData;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.lore.ConstructCastPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 0.50: defesa no botão direito. Com o anel ligado e a mão vazia, segurar o botão direito levanta o escudo do anel
 * (o mesmo que antes ficava na roda e no Alt+4); soltar, ele some. Olhando para uma porta, baú, alavanca e afins, o
 * botão direito continua abrindo/usando o bloco normalmente.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DefesaCliente {
    private static boolean defendendo;
    /** 0.50.1: quando a defesa pode voltar (1,5 s depois de soltar). */
    private static long liberaEm;

    public static boolean defendendo() {
        return defendendo;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void clique(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isUseItem() || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!podeDefender()) return;
        e.setCanceled(true);
        e.setSwingHand(false);
        if (!defendendo && System.currentTimeMillis() < liberaEm) {
            var mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("defesa.lanterna.recarga",
                    String.format(java.util.Locale.ROOT, "%.1f", (liberaEm - System.currentTimeMillis()) / 1000.0)), true);
            return;
        }
        if (!defendendo) {
            defendendo = true;
            ModNetwork.sendToServer(new ConstructCastPacket("defesa", true));
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !defendendo) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.keyUse.isDown() || mc.screen != null || !ClientRingData.active || !mc.player.getMainHandItem().isEmpty()) {
            defendendo = false;
            liberaEm = System.currentTimeMillis() + com.lanterna.construct.Constructs.DEFESA_RECARGA * 50L;
            if (mc.player != null) ModNetwork.sendToServer(new ConstructCastPacket("defesa", false));
        }
    }

    private static boolean podeDefender() {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || mc.screen != null || !ClientRingData.hasRing || !ClientRingData.active) return false;
        if (!p.getMainHandItem().isEmpty() || p.isSpectator() || p.getVehicle() != null) return false;
        // bloco que se usa com a mão (porta, baú, alavanca...): deixa o jogo usar
        if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK && usavel(bh.getBlockPos())) return false;
        // NPC ou outro ser na mira que se usa com o botão direito (conversar, negociar): também deixa
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY
                && !(((net.minecraft.world.phys.EntityHitResult) mc.hitResult).getEntity() instanceof net.minecraft.world.entity.monster.Enemy)
                && !(((net.minecraft.world.phys.EntityHitResult) mc.hitResult).getEntity() instanceof net.minecraft.world.entity.player.Player)
                && !(((net.minecraft.world.phys.EntityHitResult) mc.hitResult).getEntity() instanceof com.lanterna.especies.LanternaEntity))
            return false;
        return true;
    }

    private static boolean usavel(BlockPos pos) {
        var l = Minecraft.getInstance().level;
        if (l == null) return false;
        BlockState s = l.getBlockState(pos);
        Block b = s.getBlock();
        return s.getMenuProvider(l, pos) != null || l.getBlockEntity(pos) != null || b instanceof DoorBlock || b instanceof TrapDoorBlock
                || b instanceof FenceGateBlock || b instanceof ButtonBlock || b instanceof LeverBlock || b instanceof BedBlock
                || b instanceof CraftingTableBlock || b instanceof AnvilBlock || b instanceof BellBlock || b instanceof NoteBlock
                || b instanceof RepeaterBlock || b instanceof ComparatorBlock || b instanceof BaseEntityBlock
                || net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(b).getNamespace().equals(LanternaMod.MODID);
    }

    private DefesaCliente() {}
}
