package com.lanterna.space;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.lore.Factions;
import com.lanterna.naves.NaveEntity;
import com.lanterna.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Visão de longe no espaço. O Minecraft só manda para o cliente quem está dentro da distância de visão (no espaço,
 * 4 chunks), então quem passa voando a 1.800 blocos/s some na hora. Aqui o servidor manda, 5 vezes por segundo, onde
 * estão os outros jogadores do espaço (até {@link #ALCANCE} blocos), sem carregar chunk nenhum; o cliente desenha cada
 * um conforme a distância: o jogador de verdade, o boneco com a skin, e de longe uma luz na cor da Tropa com rastro.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VisaoLonge {
    public static final double ALCANCE = 9000;
    private static final int MAX = 24;
    private static final Map<UUID, Vec3> ANTES = new HashMap<>();

    /** Um jogador visto de longe. nave = tipo da nave que ele pilota ("" = voando sozinho). */
    public record Visto(UUID uuid, String nome, double x, double y, double z, float vx, float vy, float vz, float yaw,
                        int cor, String nave, String pintura) {}

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 4 != 0) return;
        ServerLevel space = e.getServer().getLevel(SpaceBodies.SPACE);
        if (space == null) return;
        List<ServerPlayer> todos = space.players();
        if (todos.isEmpty()) { ANTES.clear(); return; }
        List<Visto> vistos = new ArrayList<>();
        for (ServerPlayer p : todos) {
            if (p.isSpectator()) continue;
            var quem = p.getVehicle() != null ? p.getVehicle() : p;
            Vec3 pos = quem.position();
            Vec3 antes = ANTES.put(p.getUUID(), pos);
            Vec3 v = antes == null || antes.distanceTo(pos) > 3000 ? Vec3.ZERO : pos.subtract(antes).scale(0.25);
            BuiltinCorps c = Factions.playerCorps(p);
            String nave = "", pintura = "";
            if (quem instanceof NaveEntity n) {
                nave = n.getTipoNave();
                pintura = n.getPintura();
            }
            vistos.add(new Visto(p.getUUID(), p.getGameProfile().getName(), pos.x, pos.y, pos.z, (float) v.x, (float) v.y, (float) v.z,
                    quem.getYRot(), c != null ? c.color() : 0xC8D8FF, nave, pintura));
        }
        ANTES.keySet().removeIf(id -> todos.stream().noneMatch(p -> p.getUUID().equals(id)));
        for (ServerPlayer p : todos) {
            Vec3 aqui = p.position();
            List<Visto> meus = new ArrayList<>();
            for (Visto v : vistos) {
                if (v.uuid().equals(p.getUUID())) continue;
                double d = Math.sqrt((v.x() - aqui.x) * (v.x() - aqui.x) + (v.y() - aqui.y) * (v.y() - aqui.y) + (v.z() - aqui.z) * (v.z() - aqui.z));
                if (d <= ALCANCE) meus.add(v);
            }
            if (meus.size() > MAX) {
                meus.sort(java.util.Comparator.comparingDouble(v -> new Vec3(v.x(), v.y(), v.z()).distanceToSqr(aqui)));
                meus = new ArrayList<>(meus.subList(0, MAX));
            }
            ModNetwork.sendTo(p, new Pacote(meus));
        }
    }

    public record Pacote(List<Visto> vistos) {
        public static void encode(Pacote m, FriendlyByteBuf b) {
            b.writeVarInt(m.vistos.size());
            for (Visto v : m.vistos) {
                b.writeUUID(v.uuid()); b.writeUtf(v.nome(), 32);
                b.writeDouble(v.x()); b.writeDouble(v.y()); b.writeDouble(v.z());
                b.writeFloat(v.vx()); b.writeFloat(v.vy()); b.writeFloat(v.vz()); b.writeFloat(v.yaw());
                b.writeInt(v.cor()); b.writeUtf(v.nave(), 64); b.writeUtf(v.pintura(), 64);
            }
        }

        public static Pacote decode(FriendlyByteBuf b) {
            int n = Math.min(b.readVarInt(), 64);
            List<Visto> l = new ArrayList<>();
            for (int i = 0; i < n; i++) l.add(new Visto(b.readUUID(), b.readUtf(32), b.readDouble(), b.readDouble(), b.readDouble(),
                    b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readInt(), b.readUtf(64), b.readUtf(64)));
            return new Pacote(l);
        }

        public static void handle(Pacote m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.lanterna.client.space.VisaoLongeCliente.receber(m.vistos)));
            ctx.get().setPacketHandled(true);
        }
    }

    private VisaoLonge() {}
}
