package com.lanterna.mogo;

import com.lanterna.LanternaMod;
import com.lanterna.network.ModNetwork;
import com.lanterna.space.SpaceBodies;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.function.Supplier;

/**
 * O Mogo não fica parado: ele anda pelo espaço, numa volta lenta (meia hora para dar a volta) em torno de um ponto.
 * Toda vez que alguém entra no espaço vindo da Terra ou de outro planeta (e não há mais ninguém lá), esse ponto muda:
 * o Mogo aparece num lugar diferente. Quem sai do próprio Mogo não faz ele mudar de lugar.
 * A posição é calculada igual no servidor e no cliente (o ponto é sincronizado; o movimento vem do relógio do jogo).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MogoOrbita {
    public static final Vec3 PADRAO = new Vec3(-26000, 350, -21000);
    public static final double RAIO_VOLTA = 420;
    public static final long VOLTA = 20L * 60 * 30;
    public static final double DIST_MIN = 25000, DIST_MAX = 45000;
    /** De quanto em quanto tempo o Mogo muda de lugar sozinho (se ninguém estiver perto dele). */
    private static final long MUDA_A_CADA = 20L * 60 * 20;

    private static Vec3 servidor = PADRAO, cliente = PADRAO;
    /** Relógio do cliente (atualizado pelo cliente a cada tick). */
    public static long tempoCliente;

    /** Onde o Mogo está agora (no lado em que o código está rodando). */
    public static Vec3 agora() {
        boolean cli = EffectiveSide.get().isClient();
        long t;
        if (cli) t = tempoCliente;
        else {
            MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
            t = s == null ? 0 : s.overworld().getGameTime();
        }
        return posicao(cli ? cliente : servidor, t);
    }

    public static Vec3 posicao(Vec3 c, long t) {
        double a = (t % VOLTA) / (double) VOLTA * Math.PI * 2;
        return c.add(Math.cos(a) * RAIO_VOLTA, Math.sin(a * 2) * 60, Math.sin(a) * RAIO_VOLTA);
    }

    // ------------------------------------------------------------------ servidor

    public static final class Dados extends SavedData {
        Vec3 c = PADRAO;

        static Dados ler(CompoundTag t) {
            Dados d = new Dados();
            if (t.contains("x")) d.c = new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"));
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            t.putDouble("x", c.x); t.putDouble("y", c.y); t.putDouble("z", c.z);
            return t;
        }
    }

    private static Dados dados(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(Dados::ler, Dados::new, "lanterna_mogo_orbita");
    }

    @SubscribeEvent
    public static void entrou(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        servidor = dados(p.server).c;
        ModNetwork.sendTo(p, new Pacote(servidor.x, servidor.y, servidor.z));
    }

    @SubscribeEvent
    public static void trocou(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !e.getTo().equals(SpaceBodies.SPACE)) return;
        if (e.getFrom().equals(MogoMundo.DIM)) return; // saiu do Mogo: ele continua onde estava
        var space = p.server.getLevel(SpaceBodies.SPACE);
        if (space != null && space.players().stream().anyMatch(o -> o != p)) return; // outro jogador já está lá
        mudar(p.server, p.getRandom());
    }

    /** A cada 20 minutos o Mogo vai para outro canto do espaço, a não ser que alguém esteja perto dele ou nele. */
    @SubscribeEvent
    public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
        long t = s.overworld().getGameTime();
        // de vez em quando, lembra todo mundo de onde ele está (por segurança)
        if (t % 600 == 0) {
            Vec3 c = dados(s).c;
            ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new Pacote(c.x, c.y, c.z));
        }
        if (t % MUDA_A_CADA != 0) return;
        var mogo = s.getLevel(MogoMundo.DIM);
        if (mogo != null && !mogo.players().isEmpty()) return;
        var space = s.getLevel(SpaceBodies.SPACE);
        Vec3 aqui = posicao(dados(s).c, t);
        if (space != null && space.players().stream().anyMatch(p -> p.position().distanceTo(aqui) < 4000)) return;
        mudar(s, s.overworld().getRandom());
    }

    /** Mundos antigos: o Mogo estava perto dos planetas; na primeira vez ele já vai para longe. */
    @SubscribeEvent
    public static void iniciou(net.minecraftforge.event.server.ServerStartedEvent e) {
        Dados d = dados(e.getServer());
        if (new Vec3(d.c.x, 0, d.c.z).length() < DIST_MIN * 0.8) mudar(e.getServer(), e.getServer().overworld().getRandom());
        servidor = d.c;
    }

    /** Escolhe um ponto novo para a volta do Mogo, longe dos outros planetas, e avisa todo mundo. */
    public static void mudar(MinecraftServer s, RandomSource r) {
        Dados d = dados(s);
        Vec3 novo = d.c;
        for (int tent = 0; tent < 60; tent++) {
            // bem longe de tudo: de 25 a 45 mil blocos do centro (uns 20 a 30 segundos em hiperpropulsão)
            double ang = r.nextDouble() * Math.PI * 2, dist = DIST_MIN + r.nextDouble() * (DIST_MAX - DIST_MIN);
            Vec3 c = new Vec3(Math.cos(ang) * dist, -150 + r.nextDouble() * 650, Math.sin(ang) * dist);
            boolean ok = true;
            for (SpaceBodies.Body b : SpaceBodies.TODOS) {
                if ("mogo".equals(b.id()) || "sun".equals(b.id())) continue;
                if (b.center().distanceTo(c) < b.radius() + 280 + RAIO_VOLTA + 350) { ok = false; break; }
            }
            if (ok) for (SpaceBodies.Body b : com.lanterna.space.ProceduralPlanets.near(c, 1))
                if (b.center().distanceTo(c) < b.radius() + 280 + RAIO_VOLTA + 350) { ok = false; break; }
            // longe dos sóis e buracos negros
            if (ok && com.lanterna.space.Perigos.perigoso(c, RAIO_VOLTA + 900)) ok = false;
            if (ok) { novo = c; break; }
        }
        d.c = novo;
        d.setDirty();
        servidor = novo;
        ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new Pacote(novo.x, novo.y, novo.z));
    }

    // ------------------------------------------------------------------ rede

    public record Pacote(double x, double y, double z) {
        public static void encode(Pacote m, FriendlyByteBuf b) { b.writeDouble(m.x); b.writeDouble(m.y); b.writeDouble(m.z); }
        public static Pacote decode(FriendlyByteBuf b) { return new Pacote(b.readDouble(), b.readDouble(), b.readDouble()); }
        public static void handle(Pacote m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> cliente = new Vec3(m.x, m.y, m.z)));
            ctx.get().setPacketHandled(true);
        }
    }

    private MogoOrbita() {}
}
