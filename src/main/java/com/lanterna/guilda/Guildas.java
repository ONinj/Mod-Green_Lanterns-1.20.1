package com.lanterna.guilda;

import com.lanterna.LanternaConfig;
import com.lanterna.LanternaMod;
import com.lanterna.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Todas as guildas e planetas conquistados do servidor (salvo com o mundo). Também roda o minerador (essência) e o
 * general (treino) de cada planeta, em tempo real, e manda o resumo para todos os jogadores (escudos, HUD).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Guildas extends SavedData {
    private static final String NAME = "lanterna_guildas";
    public static final int ESSENCIA_MAX = 64;

    public final Map<UUID, Guilda> guildas = new LinkedHashMap<>();
    public final Map<String, Conquista> conquistas = new LinkedHashMap<>();
    /** Convites pendentes: jogador -> guilda (não salva). */
    public final Map<UUID, UUID> convites = new HashMap<>();
    /** Planetas das Tropas NPC que já foram tomados alguma vez (não voltam a ser da Tropa). */
    public final Set<String> liberados = new LinkedHashSet<>();
    private static boolean sincronizar;

    public static Guildas get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Guildas::load, Guildas::new, NAME);
    }

    @Nullable
    public static Guildas get() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s == null ? null : get(s);
    }

    // ===================== Consultas =====================

    @Nullable
    public Guilda daJogador(UUID jogador) {
        for (Guilda g : guildas.values()) if (g.membros.containsKey(jogador)) return g;
        return null;
    }

    @Nullable
    public Guilda porNome(String nome) {
        for (Guilda g : guildas.values()) if (g.nome.equalsIgnoreCase(nome)) return g;
        return null;
    }

    public List<Conquista> planetasDe(UUID guilda) {
        List<Conquista> l = new ArrayList<>();
        for (Conquista c : conquistas.values()) if (c.guilda.equals(guilda)) l.add(c);
        return l;
    }

    public static boolean mesmaGuilda(Player a, Player b) {
        Guildas gs = get();
        if (gs == null) return false;
        Guilda g = gs.daJogador(a.getUUID());
        return g != null && g.membros.containsKey(b.getUUID());
    }

    public static boolean membro(@Nullable UUID guilda, Player p) {
        Guildas gs = get();
        if (gs == null || guilda == null) return false;
        Guilda g = gs.guildas.get(guilda);
        return g != null && g.membros.containsKey(p.getUUID());
    }

    // ===================== Mudanças =====================

    public void mudou() {
        setDirty();
        sincronizar = true;
    }

    public Guilda criar(ServerPlayer dono, String nome, String tropa) {
        Guilda g = new Guilda(UUID.randomUUID(), nome, dono.getUUID(), dono.getGameProfile().getName(), tropa);
        g.criada = System.currentTimeMillis();
        guildas.put(g.id, g);
        mudou();
        return g;
    }

    public void desfazer(Guilda g) {
        guildas.remove(g.id);
        conquistas.values().removeIf(c -> c.guilda.equals(g.id));
        mudou();
    }

    // ===================== Minerador e general (tempo real) =====================

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 == 7) {
            Guildas gs = get(server);
            if (gs.produzir(System.currentTimeMillis())) gs.mudou();
        }
        if (server.getTickCount() % 40 == 17) TropasNpc.verificar(server);
        if (sincronizar) {
            sincronizar = false;
            Guildas gs = get(server);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) ModNetwork.sendTo(p, GuildasSyncPacket.de(gs));
        }
    }

    /** Minerador junta essência; general solta as tropas treinadas. */
    boolean produzir(long agora) {
        boolean mudou = false;
        long base = LanternaConfig.GUILDA_MIN_ESSENCIA.get() * 60_000L;
        for (Conquista c : conquistas.values()) {
            if (c.daTropa) {
                if (TropasNpc.recompor(c, agora)) mudou = true;
                continue;
            }
            long passo = c.passoMinerador(base);
            int max = c.depositoMinerador();
            if (c.ultimaEssencia == 0) c.ultimaEssencia = agora;
            while (c.essencia < max && agora - c.ultimaEssencia >= passo) {
                c.essencia = Math.min(max, c.essencia + c.qtdMinerador());
                c.ultimaEssencia += passo;
                mudou = true;
            }
            if (c.essencia >= max) c.ultimaEssencia = agora;
            Guilda g = guildas.get(c.guilda);
            while (!c.fila.isEmpty() && agora >= c.proximaTropa && g != null) {
                boolean defesa = c.fila.poll();
                if (defesa) c.defesa = Math.min(LanternaConfig.GUILDA_MAX_DEFESA.get(), c.defesa + 1);
                else g.ataque = Math.min(LanternaConfig.GUILDA_MAX_ATAQUE.get(), g.ataque + 1);
                c.proximaTropa = c.proximaTropa + LanternaConfig.GUILDA_SEG_TREINO.get() * 1000L; // offline: as atrasadas saem todas
                mudou = true;
            }
        }
        return mudou;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Guildas gs = get(p.server);
            Guilda g = gs.daJogador(p.getUUID());
            // guarda o nome atual (o jogador pode ter trocado de nome)
            if (g != null && !p.getGameProfile().getName().equals(g.membros.get(p.getUUID()))) {
                g.membros.put(p.getUUID(), p.getGameProfile().getName());
                gs.setDirty();
            }
            ModNetwork.sendTo(p, GuildasSyncPacket.de(gs));
        }
    }

    // ===================== Salvar =====================

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag gl = new ListTag();
        for (Guilda g : guildas.values()) gl.add(g.save());
        tag.put("guildas", gl);
        ListTag cl = new ListTag();
        for (Conquista c : conquistas.values()) cl.add(c.save());
        tag.put("conquistas", cl);
        ListTag lib = new ListTag();
        for (String x : liberados) lib.add(net.minecraft.nbt.StringTag.valueOf(x));
        tag.put("liberados", lib);
        return tag;
    }

    public static Guildas load(CompoundTag tag) {
        Guildas gs = new Guildas();
        for (Tag t : tag.getList("guildas", Tag.TAG_COMPOUND)) {
            Guilda g = Guilda.load((CompoundTag) t);
            gs.guildas.put(g.id, g);
        }
        for (Tag t : tag.getList("conquistas", Tag.TAG_COMPOUND)) {
            Conquista c = Conquista.load((CompoundTag) t);
            if (gs.guildas.containsKey(c.guilda)) gs.conquistas.put(c.planeta, c);
        }
        for (Tag t : tag.getList("liberados", Tag.TAG_STRING)) gs.liberados.add(t.getAsString());
        return gs;
    }
}
