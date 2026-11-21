package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.lore.LoreItems;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 0.58.0: os epílogos e as lembranças da saga do Necron.
 *
 *  Herói (necron_10, O Dia Seguinte): Aurora comemora com fogos nas cores das sete Tropas; o Sgt. Rui e a Dra. Lívia
 *  falam com o portador; no fim, a Medalha da Última Luz.
 *  Corrompido: ao fim da Noite Eterna, o Fragmento da Noite Eterna (o que sobrou da luz da Vida no anel negro).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Epilogo {
    private static final int[] CORES = {0xE53935, 0xFF8F00, 0xFDD835, 0x43A047, 0x1E88E5, 0x5E35B1, 0xD81B60};

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 11) return;
        LoreData d = MissionManager.data(p);
        if (d == null) return;
        CompoundTag t = SagaNecron.tag(p);
        // a medalha do herói (uma vez)
        if (d.completed().contains("necron_10") && !t.getBoolean("medalha")) {
            t.putBoolean("medalha", true);
            dar(p, new ItemStack(LoreItems.MEDALHA_ULTIMA_LUZ.get()), "necron.lanterna.medalha_ganhou", ChatFormatting.GOLD);
        }
        // a lembrança do Arauto (uma vez)
        if (d.completed().contains("necron_c4") && !t.getBoolean("fragmento_noite")) {
            t.putBoolean("fragmento_noite", true);
            dar(p, new ItemStack(LoreItems.FRAGMENTO_NOITE.get()), "necron.lanterna.fragmento_noite_ganhou", ChatFormatting.DARK_PURPLE);
        }
        // Aurora comemora
        if (d.isActive("necron_10") && p.level().dimension() == Level.OVERWORLD && p.tickCount % 40 == 11) fogos(p);
    }

    private static void dar(ServerPlayer p, ItemStack s, String msg, ChatFormatting cor) {
        if (!p.getInventory().add(s)) p.drop(s, false);
        p.sendSystemMessage(Component.translatable(msg).withStyle(cor));
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
    }

    private static void fogos(ServerPlayer p) {
        BlockPos a = Escurece.rel(0, 0);
        if (a == null) return;
        ServerLevel l = p.serverLevel();
        double dx = p.getX() - a.getX(), dz = p.getZ() - a.getZ();
        if (dx * dx + dz * dz > 80 * 80) return;
        for (int i = 0; i < 2; i++) {
            int x = a.getX() + l.random.nextInt(31) - 15, z = a.getZ() + l.random.nextInt(31) - 15;
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            ItemStack f = new ItemStack(Items.FIREWORK_ROCKET);
            CompoundTag fw = f.getOrCreateTagElement("Fireworks");
            fw.putByte("Flight", (byte) (1 + l.random.nextInt(2)));
            ListTag ex = new ListTag();
            CompoundTag e = new CompoundTag();
            e.putByte("Type", (byte) l.random.nextInt(5));
            e.putBoolean("Trail", true);
            e.putBoolean("Flicker", l.random.nextBoolean());
            e.put("Colors", new IntArrayTag(new int[]{CORES[l.random.nextInt(CORES.length)], CORES[l.random.nextInt(CORES.length)]}));
            ex.add(e);
            fw.put("Explosions", ex);
            l.addFreshEntity(new FireworkRocketEntity(l, x + 0.5, y + 1, z + 0.5, f));
        }
    }

    /** Teste: esquece as lembranças. */
    public static void zerar(ServerPlayer p) {
        SagaNecron.tag(p).remove("medalha");
        SagaNecron.tag(p).remove("fragmento_noite");
    }

    private Epilogo() {}
}
