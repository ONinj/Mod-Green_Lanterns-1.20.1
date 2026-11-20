package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import com.lanterna.network.ModNetwork;
import com.lanterna.vida.Emocoes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.*;
import java.util.function.Supplier;

/**
 * 0.55.1: os pedidos de ajuda e as quedas.
 *
 * Depois que o jogador defende a própria Tropa, Lanternas de outras Tropas vêm pedir socorro (uma cena no mundo: o
 * Lanterna desce do céu, pousa na frente do jogador e fala). Cada pedido abre a fenda sobre a Bateria Central daquela
 * Tropa e dá a missão de defendê-la (as mesmas três ondas). Entre um pedido e outro, uma das Tropas condenadas cai
 * (uma cena no espaço: a luz do planeta se apaga): a do Amor (Zamaron), a do Índigo (Nok) e a da Esperança (Odym),
 * menos a do próprio jogador. Depois disso, o Necron se volta para a Terra (próxima parte).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Pedidos {
    private static final String[] CONDENADAS = {"violet", "indigo", "blue"};
    private static final String[] ORDEM = {"red", "yellow", "orange", "green", "blue", "indigo", "violet"};
    private static final int MAX_PEDIDOS = 3, ESPERA = 200;
    /** Duração da cena do pedido e da queda (ticks). */
    public static final int DUR_PEDIDO = 300, DUR_QUEDA = 220;

    private static final class Cena {
        int t;
        boolean queda;
        String tropa;
        @Nullable UUID lanterna;
        Vec3 ondePousa;
    }

    private static final Map<UUID, Cena> CENAS = new HashMap<>();

    public static boolean emCena(net.minecraft.world.entity.player.Player p) { return CENAS.containsKey(p.getUUID()); }

    private static CompoundTag tag(ServerPlayer p) {
        CompoundTag t = SagaNecron.tag(p);
        if (!t.contains("pedidos")) t.put("pedidos", new CompoundTag());
        return t.getCompound("pedidos");
    }

    /** A sequência do jogador: "P:tropa" (pedido) e "Q:tropa" (queda), na ordem em que acontecem. */
    public static List<String> sequencia(String propria) {
        List<String> cond = new ArrayList<>(), outras = new ArrayList<>();
        for (String c : CONDENADAS) if (!c.equals(propria)) cond.add(c);
        for (String c : ORDEM) if (!c.equals(propria) && !Arrays.asList(CONDENADAS).contains(c) && outras.size() < MAX_PEDIDOS) outras.add(c);
        List<String> s = new ArrayList<>();
        for (int i = 0; i < Math.max(cond.size(), outras.size()); i++) {
            if (i < outras.size()) s.add("P:" + outras.get(i));
            if (i < cond.size()) s.add("Q:" + cond.get(i));
        }
        return s;
    }

    private static PlanetLore planeta(String tropa) {
        for (PlanetLore p : PlanetLore.values()) if (p.corps.id().getPath().equals(tropa)) return p;
        return PlanetLore.OA;
    }

    private static BuiltinCorps corps(String tropa) {
        for (BuiltinCorps b : BuiltinCorps.values()) if (b.id().getPath().equals(tropa)) return b;
        return BuiltinCorps.GREEN;
    }

    // ===================== avanço =====================

    /** Uma Bateria Central foi defendida com o jogador presente. */
    public static void defendeu(ServerPlayer p, PlanetLore planeta) {
        CompoundTag t = tag(p);
        t.putLong("proximo", p.server.overworld().getGameTime() + ESPERA);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        Cena c = CENAS.get(p.getUUID());
        if (c != null) { passoCena(p, c); return; }
        if (p.tickCount % 20 != 13) return;
        String propria = SagaNecron.tag(p).getString("tropa");
        if (!Emocoes.aspecto(propria)) return;
        LoreData d = MissionManager.data(p);
        if (d == null || !d.completed().contains("necron_4_" + propria)) return;
        CompoundTag t = tag(p);
        List<String> seq = sequencia(propria);
        int i = t.getInt("etapa");
        if (i >= seq.size()) {
            if (!t.getBoolean("fim")) {
                // Capítulo 6: antes de seguir, as ruínas de cada Tropa que caiu
                boolean falta = false;
                for (String c2 : CONDENADAS) {
                    if (c2.equals(propria) || !Queda.caida(p.server, planeta(c2))) continue;
                    if (d.completed().contains("necron_q_" + c2)) continue;
                    falta = true;
                    if (!d.isActive("necron_q_" + c2)) MissionManager.start(p, "necron_q_" + c2);
                }
                if (falta) return;
                t.putBoolean("fim", true);
                p.sendSystemMessage(Component.translatable("necron.lanterna.pedidos_fim").withStyle(ChatFormatting.DARK_PURPLE));
                MissionManager.start(p, "necron_5");
            }
            return;
        }
        String passo = seq.get(i);
        String tropa = passo.substring(2);
        long agora = p.server.overworld().getGameTime();
        if (!t.contains("proximo")) t.putLong("proximo", agora + ESPERA);
        if (agora < t.getLong("proximo")) return;
        // só começa uma cena com o jogador num lugar calmo (num planeta ou na Terra, sem outra cena)
        if (p.level().dimension().equals(com.lanterna.space.SpaceBodies.SPACE) || SagaNecron.assistindo(p) || Fuga.emFuga(p) || !p.isAlive()) return;
        if (passo.startsWith("Q:")) {
            if (Queda.caida(p.server, planeta(tropa))) {
                p.sendSystemMessage(Component.translatable("necron.lanterna.queda_ja", planeta(tropa).planetName()).withStyle(ChatFormatting.DARK_GRAY));
                ruinas(p, tropa);
                avancar(t, agora);
            } else comecarQueda(p, tropa);
            return;
        }
        // pedido: espera a defesa anterior (se houver uma em andamento)
        String esperando = t.getString("defendendo");
        if (!esperando.isEmpty() && !d.completed().contains("necron_4_" + esperando)) return;
        if (Defesa.defendida(p.server, planeta(tropa)) || Queda.caida(p.server, planeta(tropa))) {
            avancar(t, agora);
            return;
        }
        comecarPedido(p, tropa);
    }

    /** Capítulo 6: a Tropa caiu; a própria Tropa pede para buscar os feridos e o núcleo da bateria. */
    private static void ruinas(ServerPlayer p, String tropa) {
        LoreData d = MissionManager.data(p);
        if (d == null || d.completed().contains("necron_q_" + tropa) || d.isActive("necron_q_" + tropa)) return;
        MissionManager.start(p, "necron_q_" + tropa);
        p.sendSystemMessage(Component.translatable("necron.lanterna.ruina_chamado", Component.translatable("corps.lanterna." + tropa), planeta(tropa).planetName())
                .withStyle(s -> s.withColor(corps(SagaNecron.tag(p).getString("tropa")).color())));
    }

    private static void avancar(CompoundTag t, long agora) {
        t.putInt("etapa", t.getInt("etapa") + 1);
        t.putLong("proximo", agora + ESPERA);
    }

    // ===================== a cena do pedido (no mundo) =====================

    private static void comecarPedido(ServerPlayer p, String tropa) {
        ServerLevel l = p.serverLevel();
        BuiltinCorps b = corps(tropa);
        LanternaEntity a = LanternaEntity.lanterna(l, b, true);
        if (a == null) return;
        Vec3 frente = Vec3.directionFromRotation(0, p.getYRot()).multiply(1, 0, 1).normalize();
        Vec3 pousa = p.position().add(frente.scale(3.2));
        a.moveTo(pousa.x + frente.x * 6, pousa.y + 22, pousa.z + frente.z * 6, 0, 0);
        a.setNoAi(true);
        a.setNoGravity(true);
        a.setInvulnerable(true);
        a.addTag("lanterna_aliado_fuga");
        a.setCustomName(Component.translatable("necron.lanterna.aliado", Component.translatable("corps.lanterna." + tropa)).withStyle(s -> s.withColor(b.color())));
        a.setCustomNameVisible(true);
        l.addFreshEntity(a);
        Cena c = new Cena();
        c.tropa = tropa;
        c.lanterna = a.getUUID();
        c.ondePousa = pousa;
        CENAS.put(p.getUUID(), c);
        ModNetwork.sendTo(p, new Pacote(0, a.getId(), tropa, planeta(tropa).planet));
        l.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2f, 1.2f);
    }

    private static void comecarQueda(ServerPlayer p, String tropa) {
        Cena c = new Cena();
        c.queda = true;
        c.tropa = tropa;
        CENAS.put(p.getUUID(), c);
        ModNetwork.sendTo(p, new Pacote(1, -1, tropa, planeta(tropa).planet));
    }

    private static void passoCena(ServerPlayer p, Cena c) {
        c.t++;
        ServerLevel l = p.serverLevel();
        p.setDeltaMovement(Vec3.ZERO);
        if (c.queda) {
            if (c.t == DUR_QUEDA / 2) Queda.cair(p.server, planeta(c.tropa));
            if (c.t >= DUR_QUEDA) {
                CENAS.remove(p.getUUID());
                p.sendSystemMessage(Component.translatable("necron.lanterna.queda_msg", Component.translatable("corps.lanterna." + c.tropa),
                        planeta(c.tropa).planetName()).withStyle(ChatFormatting.DARK_PURPLE));
                avancar(tag(p), p.server.overworld().getGameTime());
                ruinas(p, c.tropa);
            }
            return;
        }
        Entity a = c.lanterna == null ? null : l.getEntity(c.lanterna);
        int cor = corps(c.tropa).color();
        DustParticleOptions luz = new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 1.6f);
        if (a != null) {
            if (c.t <= 50) {
                Vec3 pos = a.position().lerp(c.ondePousa, 0.12);
                a.moveTo(pos.x, pos.y, pos.z, yaw(pos, p.position()), 0);
                l.sendParticles(luz, pos.x, pos.y + 1, pos.z, 5, 0.3, 0.6, 0.3, 0);
            } else if (c.t < DUR_PEDIDO - 30) {
                a.setYRot(yaw(a.position(), p.position()));
                a.setYHeadRot(a.getYRot());
            } else {
                // vai embora voando
                a.moveTo(a.getX(), a.getY() + 1.2, a.getZ(), a.getYRot(), 0);
                l.sendParticles(luz, a.getX(), a.getY() + 1, a.getZ(), 6, 0.3, 0.6, 0.3, 0);
            }
        }
        if (c.t >= DUR_PEDIDO) {
            CENAS.remove(p.getUUID());
            if (a != null) a.discard();
            PlanetLore pl = planeta(c.tropa);
            Defesa.abrirFenda(l, corps(c.tropa));
            // 0.56.5: primeiro o socorro no planeta (prisioneiros, sobreviventes ou ladrões), depois a defesa
            String m = "necron_4_" + c.tropa, s0 = "necron_s_" + c.tropa;
            LoreData d = MissionManager.data(p);
            if (d != null) { d.completed().remove(m); d.completed().remove(s0); com.lanterna.fonte.FonteDados.chamado(p, "necron:defesa_" + c.tropa, 0); }
            MissionManager.start(p, s0);
            p.sendSystemMessage(Component.translatable("necron.lanterna.socorro_" + Socorro.tipo(c.tropa).name().toLowerCase())
                    .withStyle(s -> s.withColor(cor)));
            tag(p).putString("defendendo", c.tropa);
            avancar(tag(p), p.server.overworld().getGameTime());
            p.sendSystemMessage(Component.translatable("necron.lanterna.pedido_missao", Component.translatable("corps.lanterna." + c.tropa), pl.planetName())
                    .withStyle(s -> s.withColor(cor)));
        }
    }

    private static float yaw(Vec3 de, Vec3 para) {
        Vec3 d = para.subtract(de);
        return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void protegido(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && emCena(p) && !e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))
            e.setCanceled(true);
    }

    /** Teste: volta ao começo da sequência. */
    public static void zerar(ServerPlayer p) {
        CENAS.remove(p.getUUID());
        SagaNecron.tag(p).remove("pedidos");
    }

    // ===================== rede =====================

    /** Servidor → cliente: a cena do pedido (0) ou da queda (1). */
    public record Pacote(int tipo, int lanterna, String tropa, String planeta) {
        public static void encode(Pacote m, FriendlyByteBuf b) { b.writeVarInt(m.tipo); b.writeInt(m.lanterna); b.writeUtf(m.tropa, 32); b.writeUtf(m.planeta, 32); }
        public static Pacote decode(FriendlyByteBuf b) { return new Pacote(b.readVarInt(), b.readInt(), b.readUtf(32), b.readUtf(32)); }
        public static void handle(Pacote m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.lanterna.client.necron.CenaPedido.receber(m)));
            ctx.get().setPacketHandled(true);
        }
    }

    private Pedidos() {}
}
