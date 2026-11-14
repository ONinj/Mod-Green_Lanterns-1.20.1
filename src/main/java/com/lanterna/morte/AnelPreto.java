package com.lanterna.morte;

import com.lanterna.capability.ModCapabilities;
import com.lanterna.combate.Supremos;
import com.lanterna.item.PowerRingItem;
import com.lanterna.lore.LoreWorldData;
import com.lanterna.power.EquipmentHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * O Anel Preto é um só (0.49.3). Ele é forjado uma única vez por mundo/servidor, em Ryut, e quem o carrega é o
 * Arauto da Morte: o mais forte de todos, que quase nada fere. Os outros "Lanternas Negros" são Portadores Negros:
 * jogadores corrompidos pelo Arauto, que continuam com os poderes da própria Tropa, tingidos de preto, e só ganham
 * dois dons menores da Morte.
 *
 * O dono fica gravado no mundo ("anel_preto:<uuid>:<nome>" nos sites do LoreWorldData).
 */
public final class AnelPreto {
    private static final String PREFIXO = "anel_preto:";

    /** O jogador carrega o Anel Preto de verdade (não um anel corrompido)? */
    public static boolean arauto(Player p) {
        var r = ModCapabilities.get(p).orElse(null);
        if (r == null || !r.hasRing() || !Supremos.preto(r.corps())) return false;
        return PowerRingItem.getCorrupcao(EquipmentHelper.ring(p)) == 0;
    }

    /** É um Portador Negro (corrompido pelo Arauto)? */
    public static boolean portador(Player p) {
        ItemStack anel = EquipmentHelper.ring(p);
        return !anel.isEmpty() && PowerRingItem.getCorrupcao(anel) == 2;
    }

    private static String registro(MinecraftServer s) {
        for (String id : LoreWorldData.get(s).sites()) if (id.startsWith(PREFIXO)) return id;
        return null;
    }

    @Nullable
    public static UUID dono(MinecraftServer s) {
        String r = registro(s);
        if (r == null) return null;
        try {
            return UUID.fromString(r.substring(PREFIXO.length(), r.indexOf(':', PREFIXO.length())));
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static String nomeDono(MinecraftServer s) {
        String r = registro(s);
        return r == null ? null : r.substring(r.indexOf(':', PREFIXO.length()) + 1);
    }

    /** Pode forjar? Só se ninguém forjou ainda, ou se foi ele mesmo. */
    public static boolean livre(ServerPlayer p) {
        UUID d = dono(p.server);
        return d == null || d.equals(p.getUUID());
    }

    public static void registrar(ServerPlayer p) {
        var w = LoreWorldData.get(p.server);
        String r = registro(p.server);
        if (r != null) w.resetSite(r);
        w.markSite(PREFIXO + p.getUUID() + ":" + p.getGameProfile().getName());
    }

    public static void liberar(MinecraftServer s) {
        String r = registro(s);
        if (r != null) LoreWorldData.get(s).resetSite(r);
    }

    private AnelPreto() {}
}
