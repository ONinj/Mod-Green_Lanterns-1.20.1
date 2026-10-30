package com.lanterna.guilda;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Servidor -> cliente: as guildas e os planetas conquistados (para o escudo, o símbolo e o HUD). */
public record GuildasSyncPacket(List<G> guildas, List<P> planetas) {
    public record G(UUID id, String nome, String dono, int cor, byte[] simbolo, int ataque, List<UUID> membros) {}

    /** escudoMs = quanto falta do escudo (0 = pode ser invadido); invadindo = invasão acontecendo agora. */
    public record P(String planeta, UUID guilda, int defesa, long escudoMs, boolean invadindo) {}

    public static GuildasSyncPacket de(Guildas gs) {
        List<G> g = new ArrayList<>();
        for (Guilda x : gs.guildas.values())
            g.add(new G(x.id, x.nome, x.donoNome(), x.cor(), x.simbolo, x.ataque, new ArrayList<>(x.membros.keySet())));
        List<P> p = new ArrayList<>();
        long agora = System.currentTimeMillis();
        for (Conquista c : gs.conquistas.values())
            p.add(new P(c.planeta, c.guilda, c.defesa, Math.max(0, c.escudoAte - agora), Invasao.ativa(c.planeta) != null));
        return new GuildasSyncPacket(g, p);
    }

    public static void encode(GuildasSyncPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.guildas.size());
        for (G g : m.guildas) {
            b.writeUUID(g.id);
            b.writeUtf(g.nome, 64);
            b.writeUtf(g.dono, 64);
            b.writeInt(g.cor);
            b.writeByteArray(g.simbolo);
            b.writeVarInt(g.ataque);
            b.writeVarInt(g.membros.size());
            for (UUID u : g.membros) b.writeUUID(u);
        }
        b.writeVarInt(m.planetas.size());
        for (P p : m.planetas) {
            b.writeUtf(p.planeta, 64);
            b.writeUUID(p.guilda);
            b.writeVarInt(p.defesa);
            b.writeVarLong(p.escudoMs);
            b.writeBoolean(p.invadindo);
        }
    }

    public static GuildasSyncPacket decode(FriendlyByteBuf b) {
        int n = b.readVarInt();
        List<G> g = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            UUID id = b.readUUID();
            String nome = b.readUtf(64), dono = b.readUtf(64);
            int cor = b.readInt();
            byte[] s = b.readByteArray(256);
            int at = b.readVarInt();
            int nm = b.readVarInt();
            List<UUID> mem = new ArrayList<>();
            for (int k = 0; k < nm; k++) mem.add(b.readUUID());
            g.add(new G(id, nome, dono, cor, s, at, mem));
        }
        int np = b.readVarInt();
        List<P> p = new ArrayList<>();
        for (int i = 0; i < np; i++) p.add(new P(b.readUtf(64), b.readUUID(), b.readVarInt(), b.readVarLong(), b.readBoolean()));
        return new GuildasSyncPacket(g, p);
    }

    public static void handle(GuildasSyncPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lanterna.client.guilda.ClientGuildas.receber(m)));
        ctx.get().setPacketHandled(true);
    }
}
