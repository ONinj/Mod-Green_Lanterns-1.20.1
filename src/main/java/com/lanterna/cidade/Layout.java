package com.lanterna.cidade;

import com.lanterna.LanternaMod;
import com.lanterna.lore.LoreWorldData;
import com.lanterna.lore.PlanetLore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Qual é o desenho dos planetas das Tropas neste mundo.
 *
 *  - Mundos criados a partir da 0.48.0: uma cidade só por planeta ({@link CidadeUnica}), gerada junto com o terreno:
 *    a cidade do povo no centro (0, 0), o quartel da Tropa com a Bateria Central ao sul (0, 130), a arena fora da
 *    muralha a leste (250, 0) e, em Oa, o Palácio dos Guardiões no centro, no chão.
 *  - Mundos antigos: tudo como antes (base, arena, cidade da Tropa, palácio flutuante e cidade do povo separados,
 *    construídos quando o jogador chega).
 *
 * Os pontos conhecidos (PlanetLore.HUB, PlanetLore.ARENA, Cidade.CENTRO, CidadeNativa.CENTRO, Palacio.CENTRO) mudam
 * conforme o desenho. Objetivos de missão escritos antes disso são traduzidos ({@link #traduzir}).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Layout {
    public static final BlockPos HUB_ANTIGO = new BlockPos(0, 0, 0), ARENA_ANTIGA = new BlockPos(120, 0, 0),
            CIDADE_ANTIGA = new BlockPos(0, 0, -150), NATIVA_ANTIGA = new BlockPos(-480, 0, 0), PALACIO_ANTIGO = new BlockPos(0, 170, -470);
    public static final BlockPos CIDADE_NOVA = new BlockPos(0, 0, 130), ARENA_NOVA = new BlockPos(250, 0, 0), NATIVA_NOVA = BlockPos.ZERO;

    private static volatile boolean novo;
    /** Já foi decidido (objetivos criados antes disso usam os pontos antigos). */
    public static volatile boolean aplicado;

    public static boolean novo() {
        return novo;
    }

    @SubscribeEvent
    public static void fim(net.minecraftforge.event.server.ServerStoppedEvent e) {
        CidadeUnica.esquecer();
        com.lanterna.terra.AuroraGeracao.esquecer();
        aplicar(false, null, null);
        aplicado = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void inicio(ServerStartingEvent e) {
        CidadeUnica.esquecer();
        LoreWorldData data = LoreWorldData.get(e.getServer());
        int v = data.layout();
        if (v == 0) {
            boolean fresco = e.getServer().getWorldData().overworldData().getGameTime() < 20;
            v = fresco || Boolean.getBoolean("lanterna.cidadeUnica") ? 2 : 1;
            data.setLayout(v);
            LanternaMod.LOGGER.info("Lanterna: desenho dos planetas deste mundo: {}", v == 2 ? "cidade única (0.48)" : "antigo");
        }
        aplicar(v == 2, PlanetLore.OA, e.getServer().getLevel(com.lanterna.space.SpaceBodies.byId("oa").dimension()));
        if (v == 2) com.lanterna.terra.TerraSite.escolherNaCriacao(e.getServer());
    }

    /** Aplica o desenho (no servidor; no cliente vem pelo pacote). */
    public static void aplicar(boolean n, @Nullable PlanetLore oa, @Nullable ServerLevel oaLevel) {
        novo = n;
        aplicado = true;
        com.lanterna.terra.TerraSite.desenho(n);
        if (n) {
            PlanetLore.HUB = CIDADE_NOVA;
            PlanetLore.ARENA = ARENA_NOVA;
            Cidade.CENTRO = CIDADE_NOVA;
            CidadeNativa.CENTRO = NATIVA_NOVA;
            int y = oaLevel != null ? CidadeUnica.planta(oaLevel, PlanetLore.OA).y + CidadeUnica.PODIO : palacioY;
            palacioY = y;
            Palacio.posicionar(new BlockPos(0, y, 0), true);
        } else {
            PlanetLore.HUB = HUB_ANTIGO;
            PlanetLore.ARENA = ARENA_ANTIGA;
            Cidade.CENTRO = CIDADE_ANTIGA;
            CidadeNativa.CENTRO = NATIVA_ANTIGA;
            Palacio.posicionar(PALACIO_ANTIGO, false);
        }
    }

    private static volatile int palacioY = 80;

    /**
     * Ponto escrito com o desenho antigo, no desenho deste mundo. Só muda alguma coisa nos planetas das Tropas de
     * mundos novos.
     */
    public static BlockPos traduzir(BlockPos p, @Nullable String planeta) {
        if (!novo || planeta == null || planeta.equals("genese")) return p;
        PlanetLore pl = PlanetLore.byPlanet(planeta);
        if (pl == null || pl == PlanetLore.GENESE) return p;
        if (perto(p, HUB_ANTIGO, 70)) return mover(p, HUB_ANTIGO, CIDADE_NOVA);
        if (perto(p, ARENA_ANTIGA, 45)) return mover(p, ARENA_ANTIGA, ARENA_NOVA);
        if (perto(p, CIDADE_ANTIGA, 95)) return mover(p, CIDADE_ANTIGA, CIDADE_NOVA);
        if (perto(p, NATIVA_ANTIGA, 240)) return mover(p, NATIVA_ANTIGA, NATIVA_NOVA);
        if (pl == PlanetLore.OA && perto(p, PALACIO_ANTIGO, 160)) {
            BlockPos q = mover(p, PALACIO_ANTIGO, Palacio.CENTRO);
            return p.getY() >= 100 ? new BlockPos(q.getX(), Palacio.Y + (p.getY() - PALACIO_ANTIGO.getY()), q.getZ()) : q;
        }
        return p;
    }

    private static boolean perto(BlockPos p, BlockPos c, int r) {
        long dx = p.getX() - c.getX(), dz = p.getZ() - c.getZ();
        return dx * dx + dz * dz <= (long) r * r;
    }

    private static BlockPos mover(BlockPos p, BlockPos de, BlockPos para) {
        return new BlockPos(p.getX() - de.getX() + para.getX(), p.getY(), p.getZ() - de.getZ() + para.getZ());
    }

    // ===================== cliente =====================

    @SubscribeEvent
    public static void entrou(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) com.lanterna.network.ModNetwork.sendTo(p, new Pacote(novo, palacioY));
    }

    public record Pacote(boolean novo, int palacioY) {
        public static void encode(Pacote m, FriendlyByteBuf b) { b.writeBoolean(m.novo); b.writeVarInt(m.palacioY); }

        public static Pacote decode(FriendlyByteBuf b) { return new Pacote(b.readBoolean(), b.readVarInt()); }

        public static void handle(Pacote m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                Layout.palacioY = m.palacioY;
                // no jogo de um jogador só, o servidor é o mesmo processo: já está aplicado
                if (net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() == null) aplicar(m.novo, null, null);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    private Layout() {}
}
