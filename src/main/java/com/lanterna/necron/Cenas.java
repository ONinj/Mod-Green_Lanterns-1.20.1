package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

/**
 * 0.58.0: as cutscenes no mundo da saga do Necron (a queda do Necron, o Despertar do Embrião, o Juramento, a Noite
 * Eterna, o Espectro e a visão de Parallax). O servidor só avisa o cliente qual cena tocar e onde (os pontos de
 * referência); a câmera, as faixas de cinema, as legendas e os efeitos são do cliente
 * ({@link com.lanterna.client.necron.CenaMundo}). Durante a cena o jogador fica parado e não leva dano.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Cenas {
    /** Duração de cada cena (ticks): o cliente usa a mesma tabela. */
    public static int duracao(String id) {
        return switch (id) {
            case "queda" -> 230;
            case "despertar" -> 470;
            case "juramento" -> 225;
            case "noite" -> 420;
            case "parallax" -> 200;
            case "parallax_anel" -> 120;
            case "espectro" -> 110;
            default -> 100;
        };
    }

    private static final Map<UUID, Long> ATE = new HashMap<>();

    public static void mostrar(ServerPlayer p, String id, Vec3... ancoras) {
        int dur = duracao(id);
        ATE.put(p.getUUID(), p.server.overworld().getGameTime() + dur);
        ModNetwork.sendTo(p, new Pacote(id, List.of(ancoras)));
    }

    public static boolean emCena(Player p) {
        Long fim = ATE.get(p.getUUID());
        if (fim == null || p.level().getServer() == null) return false;
        if (p.level().getServer().overworld().getGameTime() > fim) { ATE.remove(p.getUUID()); return false; }
        return true;
    }

    @SubscribeEvent
    public static void semDano(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && emCena(p)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void parado(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || !emCena(p)) return;
        var v = p.getDeltaMovement();
        p.setDeltaMovement(0, Math.min(0, v.y) * 0.2, 0);
        p.fallDistance = 0;
    }

    /** Servidor → cliente: toque esta cena (com os pontos de referência). */
    public record Pacote(String id, List<Vec3> ancoras) {
        public static void encode(Pacote m, FriendlyByteBuf b) {
            b.writeUtf(m.id, 32);
            b.writeVarInt(m.ancoras.size());
            for (Vec3 v : m.ancoras) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
        }
        public static Pacote decode(FriendlyByteBuf b) {
            String id = b.readUtf(32);
            int n = b.readVarInt();
            List<Vec3> l = new ArrayList<>();
            for (int i = 0; i < n; i++) l.add(new Vec3(b.readDouble(), b.readDouble(), b.readDouble()));
            return new Pacote(id, l);
        }
        public static void handle(Pacote m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.lanterna.client.necron.CenaMundo.receber(m.id, m.ancoras)));
            ctx.get().setPacketHandled(true);
        }
    }

    private Cenas() {}
}
