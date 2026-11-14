package com.lanterna.client.ia;

import com.lanterna.LanternaMod;
import com.lanterna.client.ClientRingData;
import com.lanterna.client.flight.Autopilot;
import com.lanterna.client.flight.Destinations;
import com.lanterna.client.mission.ClientLoreData;
import com.lanterna.ia.Intencao;
import com.lanterna.ia.Texto;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.Mission;
import com.lanterna.mission.Objective;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.ToggleFlightPacket;
import com.lanterna.network.TogglePowerPacket;
import com.lanterna.space.SpaceBodies;
import com.lanterna.tropa.Nivel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A "IA" do anel (fase 3): o jogador conversa com o anel pelo chat ou pela voz, em português ou inglês.
 *
 * Pelo chat: comece a mensagem com "anel," / "ring," (ou "@anel", "@ring"). A mensagem não vai para os outros
 * jogadores: só você e o anel. Pela voz: segure a tecla "Falar com o anel" (padrão: Ç / ;) e fale.
 *
 * Tudo roda aqui no cliente, sem serviço pago: o texto passa por {@link Intencao} (palavras-chave + semelhança) e o
 * anel responde no jeito da Tropa dele. Pedidos como "me leve para Zamaron" ligam o piloto automático do anel.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AnelIA {
    private static final String[] PREFIXOS = {"@anel", "@ring", "anel,", "anel:", "ring,", "ring:", "anel ", "ring "};
    private static final Random RND = new Random();
    /** Sem vírgula ("anel me leve para Oa"), só pedidos claros contam: "anel verde é o melhor" continua no chat. */
    private static final java.util.Set<Intencao> PEDIDOS = java.util.EnumSet.of(Intencao.VIAJAR, Intencao.PARAR, Intencao.CARGA,
            Intencao.NIVEL, Intencao.MISSAO, Intencao.ONDE, Intencao.DISTANCIA, Intencao.PERIGO, Intencao.LIGAR, Intencao.DESLIGAR,
            Intencao.VOAR, Intencao.POUSAR, Intencao.JURAMENTO, Intencao.QUEM, Intencao.AJUDA, Intencao.DUVIDA);
    /** Último destino falado (para "e quanto falta?"). */
    @Nullable private static String ultimoDestino;

    /** Teste automático: última resposta do anel (texto puro). */
    public static String ultimaResposta = "";

    @SubscribeEvent
    public static void onChat(ClientChatEvent e) {
        String msg = e.getMessage();
        if (msg == null || msg.startsWith("/")) return;
        String low = msg.toLowerCase(java.util.Locale.ROOT).trim();
        for (String p : PREFIXOS) {
            if (!low.startsWith(p)) continue;
            String resto = msg.trim().substring(p.length()).trim();
            // "anel" no começo de uma frase comum ("anel verde é demais") só conta se for um pedido que o anel entende
            if (p.endsWith(" ") && (!ClientRingData.hasRing || !PEDIDOS.contains(Intencao.ler(com.lanterna.ia.Vocabulario.consertar(Texto.limpo(resto))).intencao()))) return;
            e.setCanceled(true);
            Minecraft.getInstance().gui.getChat().addRecentChat(msg);
            ouvir(resto, false);
            return;
        }
    }

    /** O jogador disse/escreveu isto para o anel. */
    public static void ouvir(String texto, boolean falado) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        p.displayClientMessage(Component.literal(falado ? "🎙 " : "› ").append(Component.literal(texto)).withStyle(s -> s.withColor(0xB8C4CC).withItalic(true)), false);
        if (!ClientRingData.hasRing) {
            responder(Component.translatable("anel.ia.sem_anel"), false);
            return;
        }
        // nomes do mod ouvidos errado ("noque" -> nok) e sinônimos ("meu planeta" -> casa)
        String limpo = com.lanterna.ia.Vocabulario.consertar(Texto.limpo(texto));
        Intencao.Lida lida = Intencao.ler(limpo);
        Component r;
        try {
            r = responder(lida, limpo, p);
        } catch (Exception ex) {
            LanternaMod.LOGGER.warn("Lanterna: o anel não entendeu '{}'", texto, ex);
            r = persona("nada");
        }
        responder(r, true);
    }

    // ===================== respostas =====================

    private static final java.util.Set<Intencao> PODE_SER_DUVIDA = java.util.EnumSet.of(Intencao.PERIGO, Intencao.CARGA, Intencao.NIVEL,
            Intencao.VOAR, Intencao.POUSAR, Intencao.LIGAR, Intencao.DESLIGAR, Intencao.QUEM, Intencao.JURAMENTO, Intencao.TROCA,
            Intencao.PARAR, Intencao.ONDE, Intencao.DISTANCIA);

    private static Component responder(Intencao.Lida lida, String limpo, LocalPlayer p) {
        if (PODE_SER_DUVIDA.contains(lida.intencao())) {
            Component d = Duvidas.perguntaSobreOMod(limpo);
            if (d == null) d = Duvidas.topicoForte(limpo); // "bateria cinzelada" sozinho não é pedir a carga
            if (d != null) return d;
        }
        return switch (lida.intencao()) {
            case VIAJAR -> viajar(lida.resto(), p);
            case PARAR -> {
                if (!Autopilot.active()) yield Component.translatable("anel.ia.parar_nada");
                Autopilot.stop(null);
                yield Component.translatable("anel.ia.parar");
            }
            case CARGA -> {
                int pct = Math.round(100f * ClientRingData.charge / Math.max(1, ClientRingData.maxCharge));
                yield Component.translatable(pct < 20 ? "anel.ia.carga_baixa" : "anel.ia.carga", pct);
            }
            case NIVEL -> {
                int xp = ClientLoreData.DATA.xp();
                int nv = Nivel.nivel(xp);
                yield nv >= Nivel.MAX ? Component.translatable("anel.ia.nivel_max", nv)
                        : Component.translatable("anel.ia.nivel", nv, Math.max(0, Nivel.total(nv + 1) - xp));
            }
            case MISSAO -> {
                // "me ajuda com a missão do juramento": a missão pelo nome; "o que fazer na fonte": o assunto
                String assunto = Duvidas.assunto(limpo);
                if (!assunto.isBlank()) {
                    com.lanterna.mission.Mission m = Duvidas.missaoPeloNome(assunto);
                    if (m != null) yield Duvidas.guia(m, p);
                    Component d = Duvidas.perguntaSobreOMod(limpo);
                    if (d == null) d = Duvidas.topicoForte(limpo);
                    if (d != null) yield d;
                }
                yield missao();
            }
            case ONDE -> onde(p);
            case DISTANCIA -> {
                Destinations.Destination d = destino(lida.resto().isBlank() && ultimoDestino != null ? ultimoDestino : lida.resto(), p);
                if (d == null) yield Component.translatable("anel.ia.destino_desconhecido", lida.resto());
                yield Component.translatable("anel.ia.distancia", d.name(), distancia(p, d), tempo(p, d));
            }
            case PERIGO -> perigo(p);
            case LIGAR -> {
                if (ClientRingData.active) yield Component.translatable("anel.ia.ja_ligado");
                ModNetwork.sendToServer(new TogglePowerPacket());
                yield Component.translatable("anel.ia.ligar");
            }
            case DESLIGAR -> {
                if (!ClientRingData.active) yield Component.translatable("anel.ia.ja_desligado");
                if (Autopilot.active()) Autopilot.stop(null);
                ModNetwork.sendToServer(new TogglePowerPacket());
                yield Component.translatable("anel.ia.desligar");
            }
            case VOAR -> {
                if (!ClientRingData.active) yield Component.translatable("anel.ia.precisa_ligar");
                if (!ClientRingData.flight) ModNetwork.sendToServer(new ToggleFlightPacket());
                yield Component.translatable("anel.ia.voar");
            }
            case POUSAR -> {
                if (Autopilot.active()) Autopilot.stop(null);
                if (ClientRingData.flight) ModNetwork.sendToServer(new ToggleFlightPacket());
                yield Component.translatable("anel.ia.pousar");
            }
            case JURAMENTO -> juramento();
            case QUEM -> persona("quem");
            case AJUDA -> Component.translatable("anel.ia.ajuda");
            case DUVIDA -> {
                Component d = Duvidas.responder(limpo, p);
                if (d != null) yield d;
                Duvidas.semResposta(limpo);
                yield Component.translatable("anel.ia.duvida.nao_sei");
            }
            case OI -> persona("oi");
            case COMO_VAI -> Component.translatable("anel.ia.como_vai", Math.round(100f * ClientRingData.charge / Math.max(1, ClientRingData.maxCharge)));
            case PIADA -> Component.translatable("anel.ia.piada." + (1 + RND.nextInt(5)));
            case OBRIGADO -> Component.translatable("anel.ia.obrigado." + (1 + RND.nextInt(3)));
            case TCHAU -> Component.translatable("anel.ia.tchau");
            case ELOGIO -> persona("elogio");
            case INSULTO -> persona("insulto");
            case TROCA -> Component.translatable("anel.ia.troca");
            case SOBRE -> {
                // um planeta com história própria fica com a história; o resto, os assuntos do mod
                Destinations.Destination dest = destino(lida.resto(), p);
                if (dest == null || !I18n.exists("mission.lanterna.main_" + dest.id() + "_1.desc")) {
                    Component d = Duvidas.responder(limpo, p);
                    if (d != null) yield d;
                }
                yield sobre(lida.resto(), p);
            }
            case NADA -> {
                // "zamaron" sozinho: entende como pedido de viagem
                Destinations.Destination d = destino(limpo, p);
                if (d != null && Texto.palavras(limpo).size() <= 3) yield viajar(limpo, p);
                // qualquer outra frase: talvez seja uma dúvida escrita de outro jeito ("qual a receita...", "jade viva?")
                Component duv = Duvidas.responder(limpo, p);
                if (duv != null) yield duv;
                Duvidas.semResposta(limpo);
                yield persona("nada");
            }
        };
    }

    private static Component viajar(String alvo, LocalPlayer p) {
        Destinations.Destination d = destino(alvo, p);
        if (d == null) return Component.translatable("anel.ia.destino_desconhecido", alvo);
        ultimoDestino = d.id();
        if (Autopilot.active() && Autopilot.target() != null && Autopilot.target().id().equals(d.id()))
            return Component.translatable("anel.ia.ja_indo", d.name());
        if (Autopilot.active()) Autopilot.stop(null);
        Component erro = Autopilot.start(d);
        if (erro != null) return Component.translatable("anel.ia.viajar_erro", d.name(), erro);
        return Component.translatable("anel.ia.viajar." + (1 + RND.nextInt(3)), d.name(), distancia(p, d), tempo(p, d));
    }

    /** Acha o destino pelo nome (com erros de digitação), "casa", "Terra"... */
    @Nullable
    public static Destinations.Destination destino(String texto, LocalPlayer p) {
        String t = Texto.limpo(texto);
        if (t.isEmpty()) return null;
        // apelidos
        if (Texto.tem(t, "casa", "minha casa", "minha tropa", "bateria central", "meu planeta", "home", "my corps", "home planet",
                "central battery")) {
            PlanetLore pl = PlanetLore.byCorps(ClientRingData.corps);
            if (pl != null) return Destinations.byId(pl.planet);
        }
        if (Texto.tem(t, "terra", "earth", "mundo normal", "overworld")) return Destinations.byId("earth");
        List<Destinations.Destination> todos = new ArrayList<>(Destinations.all(6));
        for (SpaceBodies.Body b : SpaceBodies.TODOS) {
            Destinations.Destination d = Destinations.byId(b.id());
            if (d != null && todos.stream().noneMatch(x -> x.id().equals(d.id()))) {
                if (SpaceBodies.isHidden(b.id()) && !ClientLoreData.geneseRevealed()) continue;
                todos.add(d);
            }
        }
        Destinations.Destination melhor = null;
        double nota = 0.72;
        for (Destinations.Destination d : todos) {
            double s = Math.max(Texto.semelhanca(t, Texto.limpo(d.name().getString())),
                    Texto.semelhanca(t, Texto.limpo(d.id().replace("lend_", "").replace('_', ' '))));
            if (s > nota) { nota = s; melhor = d; }
        }
        return melhor;
    }

    private static String distancia(LocalPlayer p, Destinations.Destination d) {
        double m = Destinations.spacePosition(p).distanceTo(d.center()) - d.radius();
        return m >= 1000 ? String.format(java.util.Locale.ROOT, "%.1fk", m / 1000.0) : String.valueOf(Math.max(0, (int) m));
    }

    /** Tempo estimado em hiperpropulsão (~1.800 blocos/s, mais subida e descida). */
    private static String tempo(LocalPlayer p, Destinations.Destination d) {
        double m = Destinations.spacePosition(p).distanceTo(d.center());
        int s = (int) Math.round(m / 1500.0) + (p.level().dimension().equals(SpaceBodies.SPACE) ? 8 : 25);
        return s >= 60 ? (s / 60) + "min " + (s % 60) + "s" : s + "s";
    }

    private static Component missao() {
        Mission m = ClientLoreData.tracked();
        if (m == null) return Component.translatable("anel.ia.sem_missao");
        int[] prog = ClientLoreData.DATA.active().get(m.id());
        for (int i = 0; i < m.objectives().size(); i++) {
            Objective o = m.objectives().get(i);
            int feito = prog != null && i < prog.length ? prog[i] : 0;
            if (feito < o.count()) return Component.translatable("anel.ia.missao", m.title(), o.label(feito), feito, o.count());
        }
        return Component.translatable("anel.ia.missao_pronta", m.title());
    }

    private static Component onde(LocalPlayer p) {
        String dim = p.level().dimension().location().getPath();
        if (p.level().dimension().equals(SpaceBodies.SPACE)) {
            Destinations.Destination perto = null;
            double best = Double.MAX_VALUE;
            for (Destinations.Destination d : Destinations.all()) {
                double m = p.position().distanceTo(d.center());
                if (m < best) { best = m; perto = d; }
            }
            return perto == null ? Component.translatable("anel.ia.onde_espaco_vazio")
                    : Component.translatable("anel.ia.onde_espaco", perto.name(), distancia(p, perto));
        }
        SpaceBodies.Body b = SpaceBodies.bodyAt(p.level().dimension(), p.getX(), p.getZ());
        Component lugar = b != null ? b.displayName() : Component.literal(dim);
        return Component.translatable("anel.ia.onde", lugar, (int) p.getX(), (int) p.getY(), (int) p.getZ());
    }

    private static Component perigo(LocalPlayer p) {
        int n = 0;
        Entity perto = null;
        double best = Double.MAX_VALUE;
        for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(48))) {
            if (!(e instanceof Enemy) || !e.isAlive()) continue;
            n++;
            double d = e.distanceTo(p);
            if (d < best) { best = d; perto = e; }
        }
        if (n == 0) return Component.translatable("anel.ia.sem_perigo");
        Vec3 v = perto.position().subtract(p.position());
        String dir = direcao(p, v);
        return Component.translatable("anel.ia.perigo", n, perto.getDisplayName(), (int) best, Component.translatable("anel.ia.dir." + dir));
    }

    /** frente / atrás / esquerda / direita / acima / abaixo, em relação para onde o jogador olha. */
    private static String direcao(LocalPlayer p, Vec3 v) {
        if (Math.abs(v.y) > Math.hypot(v.x, v.z) * 1.4) return v.y > 0 ? "acima" : "abaixo";
        double ang = Math.toDegrees(Math.atan2(-v.x, v.z)) - p.getYRot();
        ang = ((ang % 360) + 540) % 360 - 180;
        if (Math.abs(ang) < 45) return "frente";
        if (Math.abs(ang) > 135) return "tras";
        return ang > 0 ? "direita" : "esquerda";
    }

    private static Component juramento() {
        String k = "oath.lanterna." + ClientRingData.corps.getPath();
        if (!I18n.exists(k)) return persona("quem");
        return Component.literal(I18n.get(k).replace("\n", " "));
    }

    private static Component sobre(String assunto, LocalPlayer p) {
        Destinations.Destination d = destino(assunto, p);
        if (d != null) {
            String k = "mission.lanterna.main_" + d.id() + "_1.desc";
            if (I18n.exists(k)) return Component.translatable("anel.ia.sobre", d.name(), Component.translatable(k));
            return Component.translatable("anel.ia.sobre_curto", d.name(), distancia(p, d));
        }
        String t = Texto.limpo(assunto);
        for (String tema : new String[]{"guardioes", "guardians", "mogo", "fonte", "source", "bateria", "battery", "anel", "ring", "tropa", "corps"}) {
            if (Texto.tem(t, tema)) return Component.translatable("anel.ia.tema." + tema);
        }
        return Component.translatable("anel.ia.sobre_desconhecido", assunto);
    }

    /** Fala no jeito da Tropa do anel (cai na versão geral se a Tropa não tiver a sua). */
    private static Component persona(String chave) {
        String k = "anel.ia." + chave + "." + ClientRingData.corps.getPath();
        return Component.translatable(I18n.exists(k) ? k : "anel.ia." + chave);
    }

    /** Mostra (e fala, se a voz do anel estiver ligada) a resposta. */
    public static void responder(Component r, boolean som) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        String nome = "anel.ia.nome." + ClientRingData.corps.getPath();
        Component quem = Component.translatable(I18n.exists(nome) ? nome : "anel.ia.nome");
        int cor = ClientRingData.color;
        p.displayClientMessage(Component.literal("◈ ").append(quem).append(": ").withStyle(s -> s.withColor(cor).withBold(true))
                .append(r.copy().withStyle(s -> s.withColor(0xE8F0F4).withBold(false))), false);
        if (som) p.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.5f, 1.6f);
        ultimaResposta = r.getString();
        VozAnel.falar(ultimaResposta);
    }

    private AnelIA() {}
}
