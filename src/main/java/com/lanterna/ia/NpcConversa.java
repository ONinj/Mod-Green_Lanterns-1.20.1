package com.lanterna.ia;

import com.lanterna.LanternaMod;
import com.lanterna.alien.AlienEntity;
import com.lanterna.especies.Especie;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.lore.entity.Race;
import com.lanterna.lore.entity.RaceEntity;
import com.lanterna.mission.LoreCapability;
import com.lanterna.space.SpaceBodies;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Conversa com os NPCs pelo chat (fase 3 da inteligência). Fale normalmente no chat perto de um morador, de um
 * Lanterna NPC ou de um alienígena, olhando para ele: ele responde (para quem está por perto), no jeito do povo dele.
 *
 *  - Entende cumprimento, quem é você, sobre um planeta, onde fica um lugar, trocas, missões, elogio, ofensa, tchau...
 *    (em português e em inglês, com erros de digitação).
 *  - Lembra de você: conta quantas vezes já conversaram e cumprimenta pelo nome na volta.
 *  - Elogio dá um pouco de respeito com o povo (uma vez a cada 10 min); ofensa tira. Um Brakkari ofendido parte para a
 *    briga; um pirata ofendido também.
 *
 * Roda no servidor (funciona em multiplayer): a mensagem continua indo para o chat normal.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NpcConversa {
    private static final String MEMORIA = "lanterna_ia_conhece", HUMOR = "lanterna_ia_elogio";

    private record Resposta(long quando, ServerLevel level, UUID npc, UUID jogador, Component texto) {}

    private static final List<Resposta> FILA = new ArrayList<>();
    /** Teste automático: a última fala de um NPC. */
    public static volatile String ultimaFala = "";

    @SubscribeEvent
    public static void chat(ServerChatEvent e) {
        ServerPlayer p = e.getPlayer();
        String msg = e.getRawText();
        if (msg == null || msg.isBlank() || msg.startsWith("/")) return;
        Mob npc = alvo(p);
        if (npc == null) return;
        String limpo = Texto.limpo(msg);
        Intencao.Lida lida = Intencao.ler(limpo);
        Component r = responder(p, npc, lida, limpo);
        if (r == null) return;
        synchronized (FILA) {
            FILA.add(new Resposta(p.serverLevel().getGameTime() + 14 + p.getRandom().nextInt(14), p.serverLevel(), npc.getUUID(), p.getUUID(), r));
        }
    }

    /** O NPC com quem o jogador está falando: o que ele olha, a até 6 blocos (ou o único bem perto). */
    @Nullable
    public static Mob alvo(ServerPlayer p) {
        Vec3 olho = p.getEyePosition(), olhar = p.getLookAngle();
        Mob melhor = null;
        double nota = 0;
        for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(6), NpcConversa::fala)) {
            Vec3 ate = m.getEyePosition().subtract(olho);
            double d = ate.length();
            double alinhado = ate.normalize().dot(olhar);
            double n = alinhado * 2 - d / 6;
            if (d < 2.5) n += 0.8;
            if (alinhado > 0.55 && n > nota) { nota = n; melhor = m; }
        }
        return melhor;
    }

    private static boolean fala(Mob m) {
        if (!m.isAlive()) return false;
        if (m instanceof RaceEntity) return true;
        return m instanceof LanternaEntity || m instanceof AlienEntity;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || FILA.isEmpty()) return;
        MinecraftServer server = e.getServer();
        List<Resposta> prontas = new ArrayList<>();
        synchronized (FILA) {
            for (Iterator<Resposta> it = FILA.iterator(); it.hasNext(); ) {
                Resposta r = it.next();
                if (r.level.getGameTime() < r.quando) continue;
                it.remove();
                prontas.add(r);
            }
        }
        for (Resposta r : prontas) {
            if (!(r.level.getEntity(r.npc) instanceof Mob npc) || !npc.isAlive()) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(r.jogador);
            if (p != null) npc.getLookControl().setLookAt(p, 30, 30);
            Component linha = Component.literal("<").append(npc.getDisplayName()).append("> ").append(r.texto)
                    .withStyle(s -> s.withColor(0xE6D9B8));
            for (ServerPlayer o : r.level.players()) if (o.distanceToSqr(npc) < 20 * 20) o.sendSystemMessage(linha);
            ultimaFala = linha.getString();
            if (npc instanceof AbstractVillager v) v.playSound(SoundEvents.VILLAGER_AMBIENT, 0.6f, 0.7f + npc.getRandom().nextFloat() * 0.6f);
        }
    }

    // ===================== respostas =====================

    private static String povo(Mob npc) {
        if (npc instanceof RaceEntity r) return r.race().id;
        if (npc instanceof LanternaEntity l) return switch (l.papel()) {
            case PIRATA, CAPITAO -> "pirata";
            case MERCADOR -> "mercador";
            default -> "lanterna";
        };
        return "alien";
    }

    @Nullable
    private static Component responder(ServerPlayer p, Mob npc, Intencao.Lida lida, String limpo) {
        String povo = povo(npc);
        int vezes = lembrar(npc, p);
        SpaceBodies.Body aqui = SpaceBodies.bodyAt(npc.level().dimension(), npc.getX(), npc.getZ());
        Component planeta = aqui != null ? aqui.displayName() : Component.translatable("npc.ia.lugar_nenhum");
        return switch (lida.intencao()) {
            case OI -> vezes > 1 ? fala(povo, "oi_de_novo", p.getDisplayName(), vezes) : fala(povo, "oi", p.getDisplayName());
            case QUEM -> fala(povo, "quem", npc.getDisplayName(), planeta);
            case COMO_VAI -> fala(povo, "como_vai");
            case TCHAU -> fala(povo, "tchau", p.getDisplayName());
            case OBRIGADO -> fala(povo, "obrigado");
            case PIADA -> fala(povo, "piada");
            case ONDE -> fala(povo, "onde", planeta);
            case MISSAO -> fala(povo, "missao");
            case TROCA -> {
                if (npc instanceof RaceEntity r && r.race().trades() && !r.canTradeWith(p)) yield fala(povo, "troca_nao");
                yield fala(povo, "troca");
            }
            case SOBRE, VIAJAR, DISTANCIA -> sobre(p, povo, lida.resto().isBlank() ? limpo : lida.resto());
            case ELOGIO -> {
                long agora = npc.level().getGameTime();
                CompoundTag t = npc.getPersistentData().getCompound(HUMOR);
                String k = p.getUUID().toString();
                if (npc instanceof RaceEntity r && agora - t.getLong(k) > 20 * 600) {
                    LoreCapability.get(p).ifPresent(d -> d.addRep(r.race().id, 1));
                    t.putLong(k, agora);
                    npc.getPersistentData().put(HUMOR, t);
                    yield fala(povo, "elogio_respeito");
                }
                yield fala(povo, "elogio");
            }
            case INSULTO -> {
                if (npc instanceof RaceEntity r) {
                    LoreCapability.get(p).ifPresent(d -> d.addRep(r.race().id, -1));
                    if (r.race() == Race.BRAKKARI) r.angerAt(p, 20 * 30);
                }
                if (npc instanceof LanternaEntity l && (l.papel() == Especie.Papel.PIRATA || l.papel() == Especie.Papel.CAPITAO)) l.setTarget(p);
                npc.level().playSound(null, npc.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1f, 0.7f);
                yield fala(povo, "insulto");
            }
            case LIGAR, DESLIGAR, VOAR, POUSAR, PARAR, CARGA, NIVEL, PERIGO, JURAMENTO, AJUDA -> fala(povo, "ajuda");
            case NADA -> fala(povo, "nada");
        };
    }

    /** "Fale sobre Korugar", "onde fica Oa?" */
    private static Component sobre(ServerPlayer p, String povo, String assunto) {
        String t = Texto.limpo(assunto);
        SpaceBodies.Body achado = null;
        double nota = 0.72;
        for (SpaceBodies.Body b : SpaceBodies.TODOS) {
            if (!b.landable() || SpaceBodies.isHidden(b.id())) continue;
            double s = Math.max(Texto.semelhanca(t, b.id().replace("lend_", "")), Texto.semelhanca(t, Texto.limpo(b.displayName().getString())));
            if (s > nota) { nota = s; achado = b; }
        }
        if (achado == null) return fala(povo, "nao_sei");
        com.lanterna.lore.PlanetLore pl = com.lanterna.lore.PlanetLore.byPlanet(achado.id());
        if (pl != null) return fala(povo, "sobre_tropa", achado.displayName(), Component.translatable("corps.lanterna." + pl.corps.id().getPath()));
        return fala(povo, "sobre", achado.displayName());
    }

    /** Falas próprias de cada povo (as outras usam a geral "npc.ia.<o que>"). */
    private static final java.util.Set<String> DO_POVO = java.util.Set.of("aelinos.insulto","aelinos.oi","aelinos.quem","brakkari.elogio_respeito","brakkari.insulto","brakkari.nada","brakkari.oi","brakkari.piada","brakkari.quem","coletor.oi","coletor.quem","ghallos.nada","ghallos.oi","ghallos.quem","ghallos.troca","guardiao.oi","guardiao.piada","guardiao.quem","lanterna.missao","lanterna.oi","lanterna.quem","mercador.oi","mercador.troca","pirata.elogio","pirata.insulto","pirata.oi","pirata.quem","silente.como_vai","silente.nada","silente.oi","silente.quem","skrell_vath.nada","skrell_vath.oi","skrell_vath.quem","terrano.oi","terrano.quem","veltari.oi","veltari.quem","ves_kai.oi","ves_kai.quem","zaelith.elogio","zaelith.oi","zaelith.quem");

    private static Component fala(String povo, String chave, Object... args) {
        String k = povo + "." + chave;
        return Component.translatable(DO_POVO.contains(k) ? "npc.ia." + k : "npc.ia." + chave, args);
    }

    /** Conta mais uma conversa com este jogador e devolve quantas já foram. */
    private static int lembrar(Mob npc, ServerPlayer p) {
        CompoundTag mem = npc.getPersistentData().getCompound(MEMORIA);
        String k = p.getUUID().toString();
        int n = mem.getInt(k) + 1;
        mem.putInt(k, n);
        npc.getPersistentData().put(MEMORIA, mem);
        return n;
    }

    private NpcConversa() {}
}
