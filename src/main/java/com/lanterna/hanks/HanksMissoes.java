package com.lanterna.hanks;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.fonte.FonteDados;
import com.lanterna.lendarios.Lendario;
import com.lanterna.lore.Factions;
import com.lanterna.lore.LoreItems;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.Mission;
import com.lanterna.mission.MissionManager;
import com.lanterna.mission.Objective;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Missões da Tropa Amarela em Hanks (oferecidas sozinhas a quem tem o anel amarelo e nível {@link #NIVEL}):
 *  hanks_1  O Planeta-Máquina   achar Hanks no espaço (ele anda) e pousar
 *  hanks_2  Os Três Núcleos      ligar os três núcleos de energia do distrito (ficar 5 s junto de cada um)
 *  hanks_3  A Prova de Sinestro  vencer o duelo com o Sinestro na cidadela
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HanksMissoes {
    public static final String LINHA = "hanks";
    public static final int COR = 0xFFC830;
    public static final int NIVEL = 25;
    private static final String OFERECIDA = "lanterna_hanks_oferecida", NUCLEOS = "lanterna_hanks_nucleos";

    public static boolean eDoHanks(String id) {
        return id.startsWith("hanks_");
    }

    private static final Map<String, Mission> CACHE = new HashMap<>();

    @Nullable
    public static Mission mission(String id) {
        return CACHE.computeIfAbsent(id, HanksMissoes::criar);
    }

    @Nullable
    private static Mission criar(String id) {
        return switch (id) {
            case "hanks_1" -> m(id, "hanks:pouso", List.of(() -> new ItemStack(LoreItems.MERITO.get(), 2)), "hanks_2");
            case "hanks_2" -> m(id, "hanks:nucleos", List.of(() -> new ItemStack(LoreItems.MERITO.get(), 3),
                    () -> new ItemStack(LoreItems.FRAGMENTO_LENDARIO.get(), 2)), "hanks_3");
            case "hanks_3" -> m(id, "hanks:sinestro", List.of(() -> new ItemStack(LoreItems.MERITO.get(), 5),
                    () -> new ItemStack(LoreItems.FRAGMENTO_DE_PAVOR.get(), 6)), null);
            default -> null;
        };
    }

    private static Mission m(String id, String flag, List<java.util.function.Supplier<ItemStack>> itens, @Nullable String next) {
        return new Mission(id, LINHA, COR, List.of(Objective.of(Objective.Type.FONTE, flag, 1, "hanks", null)), () -> {
            List<ItemStack> out = new ArrayList<>();
            for (var s : itens) out.add(s.get());
            return out;
        }, 80, next, null);
    }

    static boolean amarelo(ServerPlayer p) {
        return Factions.playerCorps(p) == BuiltinCorps.YELLOW;
    }

    static boolean ativa(ServerPlayer p, String id) {
        LoreData d = MissionManager.data(p);
        return d != null && d.isActive(id);
    }

    static void cumprir(ServerPlayer p, String flag) {
        if (FonteDados.chamado(p, flag) >= 1) return;
        FonteDados.chamado(p, flag, 1);
    }

    /**
     * 0.52.1: a linha já foi oferecida? Antes a marca ficava num lugar que se perde (morte, trocar o anel...) e o convite
     * voltava, repetindo a missão. Agora a marca fica guardada de verdade, e quem já tem ou já fez qualquer missão da
     * linha nunca recebe o convite de novo.
     */
    static boolean jaOferecida(ServerPlayer p) {
        var raiz = p.getPersistentData();
        var pers = raiz.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        if (pers.getBoolean(OFERECIDA)) return true;
        LoreData d = MissionManager.data(p);
        boolean temLinha = raiz.getBoolean(OFERECIDA) || d != null && (d.active().keySet().stream().anyMatch(k -> k.startsWith("hanks_"))
                || d.completed().stream().anyMatch(k -> k.startsWith("hanks_")));
        if (temLinha) marcarOferecida(p);
        return temLinha;
    }

    static void marcarOferecida(ServerPlayer p) {
        var raiz = p.getPersistentData();
        if (!raiz.contains(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)) raiz.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, new net.minecraft.nbt.CompoundTag());
        raiz.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).putBoolean(OFERECIDA, true);
        raiz.putBoolean(OFERECIDA, true);
    }

    /** Teste/admin: marca as anteriores como feitas e começa a missão n. */
    public static void pular(ServerPlayer p, int n) {
        String[] flags = {"hanks:pouso", "hanks:nucleos", "hanks:sinestro"};
        marcarOferecida(p);
        LoreData d = MissionManager.data(p);
        for (int i = 1; i <= 3; i++) if (d != null && d.isActive("hanks_" + i)) MissionManager.abandon(p, "hanks_" + i);
        for (int i = 0; i < n - 1; i++) FonteDados.chamado(p, flags[i], 1);
        for (int i = n - 1; i < 3; i++) FonteDados.chamado(p, flags[i], 0);
        if (n >= 2) p.getPersistentData().putInt(NUCLEOS, n >= 3 ? 7 : 0);
        MissionManager.start(p, "hanks_" + n);
    }

    private static void titulo(ServerPlayer p, String chave, String sub) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(8, 60, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(chave).withStyle(s -> s.withColor(COR))));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(sub).withStyle(ChatFormatting.GOLD)));
    }

    /** O convite (Amarelos de nível 25+). */
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 200 == 61 && !jaOferecida(p) && amarelo(p) && com.lanterna.tropa.Nivel.nivel(p) >= NIVEL) {
            marcarOferecida(p);
            MissionManager.start(p, "hanks_1");
            titulo(p, "hanks.lanterna.convite.titulo", "hanks.lanterna.convite.sub");
            p.sendSystemMessage(Component.translatable("hanks.lanterna.convite").withStyle(s -> s.withColor(COR)));
        }
        if (p.tickCount % 5 == 0 && p.level().dimension().equals(HanksMundo.DIM) && ativa(p, "hanks_2")) nucleos(p);
        if (p.tickCount % 40 == 13 && p.level().dimension().equals(HanksMundo.DIM) && ativa(p, "hanks_2")) guiarNucleos(p);
    }

    /**
     * 0.49.5: a cidade de Hanks não acaba e é toda parecida: achar os núcleos era muito difícil. Agora o cliente recebe
     * onde estão os que faltam (o marcador da missão aponta o mais perto) e cada núcleo apagado solta uma coluna de luz
     * alta, visível de longe por cima dos prédios (só para quem está nessa missão).
     */
    private static void guiarNucleos(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        HanksMundo.Dados d = HanksMundo.dados(l);
        if (!d.pronto) return;
        int feitos = p.getPersistentData().getInt(NUCLEOS);
        com.lanterna.network.ModNetwork.sendTo(p, new NucleosSync(d.y, feitos));
        var luz = new DustParticleOptions(new Vector3f(1f, 0.82f, 0.25f), 2.2f);
        for (int i = 0; i < 3; i++) {
            if ((feitos & (1 << i)) != 0) continue;
            BlockPos c = HanksMundo.nucleo(d, i);
            for (int k = 0; k < 90; k += 2)
                l.sendParticles(p, luz, true, c.getX() + 0.5, c.getY() + 2 + k, c.getZ() + 0.5, 1, 0.15, 0.4, 0.15, 0);
            l.sendParticles(p, net.minecraft.core.particles.ParticleTypes.END_ROD, true, c.getX() + 0.5, c.getY() + 92, c.getZ() + 0.5, 6, 0.6, 0.6, 0.6, 0.02);
        }
    }

    /** Onde estão os núcleos (altura) e quais já foram ligados (bits 1, 2, 4). */
    public record NucleosSync(int y, int feitos) {
        public static void encode(NucleosSync m, net.minecraft.network.FriendlyByteBuf b) {
            b.writeVarInt(m.y);
            b.writeByte(m.feitos);
        }

        public static NucleosSync decode(net.minecraft.network.FriendlyByteBuf b) {
            return new NucleosSync(b.readVarInt(), b.readByte());
        }

        public static void handle(NucleosSync m, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.lanterna.client.mission.ClienteHanks.receber(m.y, m.feitos));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Chegou em Hanks. */
    public static void pousou(ServerPlayer p) {
        if (ativa(p, "hanks_1")) {
            cumprir(p, "hanks:pouso");
            titulo(p, "planet.lanterna.hanks", "hanks.lanterna.chegada");
        } else {
            p.displayClientMessage(Component.translatable(amarelo(p) ? "hanks.lanterna.de_volta" : "hanks.lanterna.visitante")
                    .withStyle(s -> s.withColor(COR)), true);
        }
    }

    // ===================== 2. Os três núcleos =====================

    private static final Map<UUID, int[]> CARGA = new HashMap<>();

    private static void nucleos(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        HanksMundo.Dados d = HanksMundo.dados(l);
        if (!d.pronto) return;
        int feitos = p.getPersistentData().getInt(NUCLEOS);
        int[] carga = CARGA.computeIfAbsent(p.getUUID(), k -> new int[3]);
        for (int i = 0; i < 3; i++) {
            if ((feitos & (1 << i)) != 0) continue;
            BlockPos c = HanksMundo.nucleo(d, i);
            if (p.blockPosition().distSqr(c) > 5 * 5) { carga[i] = 0; continue; }
            carga[i] += 5;
            var op = new DustParticleOptions(new Vector3f(1f, 0.8f, 0.2f), 1.4f);
            l.sendParticles(op, c.getX() + 0.5, c.getY() + 1.5, c.getZ() + 0.5, 8, 0.4, 1.2, 0.4, 0);
            p.displayClientMessage(Component.translatable("hanks.lanterna.nucleo.carregando", i + 1, Math.min(100, carga[i])).withStyle(s -> s.withColor(COR)), true);
            if (carga[i] >= 100) {
                feitos |= 1 << i;
                p.getPersistentData().putInt(NUCLEOS, feitos);
                HanksMundo.acender(l, c, true);
                l.playSound(null, c, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.5f, 0.8f);
                int n = Integer.bitCount(feitos);
                p.sendSystemMessage(Component.translatable("hanks.lanterna.nucleo.ligado", n).withStyle(s -> s.withColor(COR)));
                if (n >= 3) {
                    cumprir(p, "hanks:nucleos");
                    titulo(p, "hanks.lanterna.nucleos.titulo", "hanks.lanterna.nucleos.sub");
                }
            }
        }
    }

    // ===================== 3. A Prova de Sinestro =====================

    /** Venceu o duelo de um lendário (chamado por Lendarios). */
    public static void venceuDuelo(ServerPlayer p, Lendario l) {
        if (l == Lendario.SINESTRO && ativa(p, "hanks_3")) cumprir(p, "hanks:sinestro");
    }

    private HanksMissoes() {}
}
