package com.lanterna.guilda;

import com.lanterna.LanternaConfig;
import com.lanterna.LanternaMod;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.space.SpaceBodies;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.UUID;

/** Fincar a bandeira, perder/tomar planetas e proteger o território. */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Conquistas {
    public static final String TAG_GUILDA = "lanterna_guilda";
    public static final String TAG_BASE = "lanterna_guilda_base";

    /** Planeta (gerado) onde o bloco está, ou null. */
    @Nullable
    public static SpaceBodies.Body planetaGerado(Level level, BlockPos pos) {
        SpaceBodies.Body b = SpaceBodies.bodyAt(level.dimension(), pos.getX(), pos.getZ());
        return b != null && b.procedural() != null ? b : null;
    }

    /** Tenta conquistar o planeta com a bandeira em pos. Devolve o erro (ou null se deu certo). */
    @Nullable
    public static Component fincar(ServerPlayer p, ServerLevel level, BlockPos pos) {
        Guildas gs = Guildas.get(p.server);
        Guilda g = gs.daJogador(p.getUUID());
        if (g == null) return Component.translatable("guilda.lanterna.erro.sem_guilda");
        SpaceBodies.Body b = planetaGerado(level, pos);
        if (b == null) return Component.translatable("guilda.lanterna.erro.so_gerados");
        Conquista c = gs.conquistas.get(b.id());
        if (c != null) {
            Guilda dono = gs.guildas.get(c.guilda);
            return Component.translatable("guilda.lanterna.erro.ja_tem_dono", dono == null ? "?" : dono.nome);
        }
        if (gs.planetasDe(g.id).size() >= LanternaConfig.GUILDA_MAX_PLANETAS.get())
            return Component.translatable("guilda.lanterna.erro.max_planetas", LanternaConfig.GUILDA_MAX_PLANETAS.get());
        c = new Conquista(b.id(), g.id, level.dimension(), pos.immutable());
        long agora = System.currentTimeMillis();
        c.conquistado = agora;
        c.ultimaEssencia = agora;
        c.escudoAte = agora + (long) (LanternaConfig.GUILDA_ESCUDO_CONQUISTA.get() * 3_600_000L);
        gs.conquistas.put(b.id(), c);
        gs.mudou();
        BaseGuilda.construir(level, pos, g, b.id());
        anunciar(p.server, Component.translatable("guilda.lanterna.conquistou", g.nome, b.displayName()).withStyle(s -> s.withColor(g.cor()).withBold(true)));
        return null;
    }

    /** O planeta passa para outra guilda (invasão vencida). */
    public static void transferir(MinecraftServer server, Conquista c, Guilda nova) {
        Guildas gs = Guildas.get(server);
        Guilda velha = gs.guildas.get(c.guilda);
        ServerLevel level = server.getLevel(c.mundo);
        c.guilda = nova.id;
        c.defesa = 0;
        c.fila.clear();
        c.essencia = 0;
        c.ultimaEssencia = System.currentTimeMillis();
        c.conquistado = System.currentTimeMillis();
        c.escudoAte = System.currentTimeMillis() + (long) (LanternaConfig.GUILDA_ESCUDO_INVASAO.get() * 3_600_000L);
        gs.mudou();
        if (level != null) {
            level.setBlock(c.bandeira, GuildaItems.BANDEIRA.get().defaultBlockState(), 3);
            removerNpcs(level, c);
            BaseGuilda.npcs(level, c.bandeira, nova, c.planeta);
        }
        SpaceBodies.Body b = SpaceBodies.byId(c.planeta);
        anunciar(server, Component.translatable("guilda.lanterna.tomou", nova.nome, b == null ? c.planeta : b.displayName(),
                velha == null ? "?" : velha.nome).withStyle(s -> s.withColor(nova.cor()).withBold(true)));
    }

    /** A guilda larga o planeta (abandonar ou desfazer a guilda): some a bandeira e os NPCs. */
    public static void largar(MinecraftServer server, Conquista c) {
        Guildas gs = Guildas.get(server);
        gs.conquistas.remove(c.planeta);
        gs.mudou();
        ServerLevel level = server.getLevel(c.mundo);
        if (level != null) {
            level.getChunk(c.bandeira.getX() >> 4, c.bandeira.getZ() >> 4);
            if (level.getBlockState(c.bandeira).is(GuildaItems.BANDEIRA.get())) level.removeBlock(c.bandeira, false);
            removerNpcs(level, c);
        }
    }

    public static void removerNpcs(ServerLevel level, Conquista c) {
        level.getChunk(c.bandeira.getX() >> 4, c.bandeira.getZ() >> 4);
        for (Entity e : new ArrayList<>(level.getEntitiesOfClass(LanternaEntity.class, new net.minecraft.world.phys.AABB(c.bandeira).inflate(48),
                x -> c.planeta.equals(x.getPersistentData().getString(TAG_BASE))))) e.discard();
    }

    public static void anunciar(MinecraftServer server, Component msg) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(msg);
    }

    public static void avisarGuilda(MinecraftServer server, UUID guilda, Component msg) {
        Guildas gs = Guildas.get(server);
        Guilda g = gs.guildas.get(guilda);
        if (g == null) return;
        for (UUID u : g.membros.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) p.sendSystemMessage(msg);
        }
    }

    /** Conquista cujo território contém a posição (no mesmo mundo). */
    @Nullable
    public static Conquista territorio(Level level, BlockPos pos) {
        Guildas gs = Guildas.get();
        if (gs == null || gs.conquistas.isEmpty()) return null;
        SpaceBodies.Body b = planetaGerado(level, pos);
        if (b == null) return null;
        Conquista c = gs.conquistas.get(b.id());
        if (c == null || !c.mundo.equals(level.dimension())) return null;
        int r = LanternaConfig.GUILDA_RAIO.get();
        return c.bandeira.distSqr(pos.atY(c.bandeira.getY())) <= (double) r * r ? c : null;
    }

    /** Pode mexer em blocos aqui? (membros sempre; invasores durante a invasão) */
    public static boolean podeMexer(Player p, Conquista c) {
        if (p.isCreative() && p.hasPermissions(2)) return true;
        if (Guildas.membro(c.guilda, p)) return true;
        Invasao inv = Invasao.ativa(c.planeta);
        return inv != null && inv.participa(p);
    }

    // ===================== Proteção do território =====================

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) return;
        Player p = event.getPlayer();
        if (event.getState().is(GuildaItems.BANDEIRA.get())) {
            Conquista c = Guildas.get().conquistas.values().stream().filter(x -> x.bandeira.equals(event.getPos()) && x.mundo.equals(level.dimension())).findFirst().orElse(null);
            Invasao inv = c == null ? null : Invasao.ativa(c.planeta);
            if (c == null) return; // bandeira órfã: pode tirar
            if (inv == null || !inv.participa(p) || !event.getState().getValue(BandeiraBlock.LIBERADA)) {
                event.setCanceled(true);
                return;
            }
            event.setCanceled(true); // não dropa: a invasão decide
            inv.bandeiraCaiu((ServerPlayer) p);
            return;
        }
        Conquista c = territorio(level, event.getPos());
        if (c != null && !podeMexer(p, c)) {
            event.setCanceled(true);
            p.displayClientMessage(Component.translatable("guilda.lanterna.territorio", nomeGuilda(c.guilda)).withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player p) || !(event.getLevel() instanceof Level level) || level.isClientSide) return;
        if (event.getPlacedBlock().is(GuildaItems.BANDEIRA.get())) return; // a própria bandeira decide em setPlacedBy
        Conquista c = territorio(level, event.getPos());
        if (c != null && !podeMexer(p, c)) {
            event.setCanceled(true);
            p.displayClientMessage(Component.translatable("guilda.lanterna.territorio", nomeGuilda(c.guilda)).withStyle(ChatFormatting.RED), true);
        }
    }

    public static String nomeGuilda(UUID id) {
        Guildas gs = Guildas.get();
        Guilda g = gs == null ? null : gs.guildas.get(id);
        return g == null ? "?" : g.nome;
    }

    private Conquistas() {}
}
