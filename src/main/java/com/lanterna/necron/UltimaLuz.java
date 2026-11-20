package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.fonte.FonteDados;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import com.lanterna.vida.Emocoes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.*;

/**
 * 0.56.9: Capítulo 8, A Última Luz (reunir os aliados). Antes da batalha final, o jogador vai a cada Tropa que ainda
 * resiste e chama os Lanternas dela na Bateria Central (fica perto da bateria uns 5 segundos: a bateria responde com
 * uma coluna de luz e os Lanternas sobem para o céu, rumo à Terra). Três Tropas bastam para a missão; cada Tropa a mais
 * traz mais Lanternas para a batalha. Os sobreviventes das ruínas (Capítulo 6) também vêm, se o núcleo da Tropa deles
 * foi guardado.
 *
 * Na batalha (ver {@link BatalhaFinal}): 3 Lanternas por Tropa chamada e 2 por Tropa caída cujo núcleo foi salvo.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UltimaLuz {
    public static final int MINIMO = 3, CHAMADO = 100, POR_TROPA = 3, POR_RUINA = 2;
    private static final Map<UUID, int[]> PROGRESSO = new HashMap<>();
    private static final List<Object[]> SOBEM = new ArrayList<>();

    /** As Tropas que este jogador já chamou. */
    public static List<String> chamadas(ServerPlayer p) {
        List<String> r = new ArrayList<>();
        ListTag l = SagaNecron.tag(p).getList("ultima_luz", 8);
        for (int i = 0; i < l.size(); i++) r.add(l.getString(i));
        return r;
    }

    private static PlanetLore planeta(String tropa) {
        for (PlanetLore p : PlanetLore.values()) if (p.corps.id().getPath().equals(tropa)) return p;
        return null;
    }

    private static BuiltinCorps corps(String t) {
        for (BuiltinCorps b : BuiltinCorps.values()) if (b.id().getPath().equals(t)) return b;
        return BuiltinCorps.GREEN;
    }

    /** As Tropas (com repetição pelo número de Lanternas) que descem na batalha, para quem está lutando. */
    public static List<BuiltinCorps> exercito(MinecraftServer s, List<ServerPlayer> lutando) {
        Set<String> tropas = new LinkedHashSet<>();
        for (ServerPlayer p : lutando) tropas.addAll(chamadas(p));
        List<BuiltinCorps> r = new ArrayList<>();
        for (String t : tropas) for (int i = 0; i < POR_TROPA; i++) r.add(corps(t));
        for (String t : Emocoes.ASPECTOS) {
            PlanetLore pl = planeta(t);
            if (pl != null && Queda.caida(s, pl) && Ruinas.nucleoGuardado(s, t)) for (int i = 0; i < POR_RUINA; i++) r.add(corps(t));
        }
        return r;
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 5) return;
        LoreData d = MissionManager.data(p);
        if (d == null || !(d.isActive("necron_6a") || d.isActive("necron_6") && !BatalhaFinal.ativa())) return;
        PlanetLore pl = PlanetLore.byDimension(p.level().dimension());
        if (pl == null) return;
        String t = pl.corps.id().getPath();
        if (!Emocoes.aspecto(t) || Queda.caida(p.server, pl) || chamadas(p).contains(t)) { PROGRESSO.remove(p.getUUID()); return; }
        BlockPos h = PlanetLore.HUB;
        double dx = p.getX() - h.getX(), dz = p.getZ() - h.getZ(), dist = Math.sqrt(dx * dx + dz * dz);
        ServerLevel l = p.serverLevel();
        if (dist < 160 && p.tickCount % 20 == 5) {
            // uma coluna fraca da cor da Tropa sobre a bateria: "venha até aqui"
            int cor = pl.corps.color();
            DustParticleOptions dd = new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 3f);
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, h.getX() + 14, h.getZ());
            for (int k = 0; k < 20; k++) l.sendParticles(p, dd, true, h.getX() + 0.5, y + 4 + k * 3, h.getZ() + 0.5, 1, 0.3, 0.5, 0.3, 0);
        }
        if (dist > 20) { PROGRESSO.remove(p.getUUID()); return; }
        int[] pr = PROGRESSO.computeIfAbsent(p.getUUID(), u -> new int[1]);
        pr[0] += 10;
        p.displayClientMessage(Component.translatable("necron.lanterna.chamando", Component.translatable("necron.lanterna.tropa." + t), Math.min(100, pr[0] * 100 / CHAMADO))
                .withStyle(st -> st.withColor(pl.corps.color())), true);
        if (pr[0] < CHAMADO) return;
        PROGRESSO.remove(p.getUUID());
        chamar(p, l, pl, t);
    }

    private static void chamar(ServerPlayer p, ServerLevel l, PlanetLore pl, String t) {
        CompoundTag tag = SagaNecron.tag(p);
        ListTag lista = tag.getList("ultima_luz", 8);
        lista.add(StringTag.valueOf(t));
        tag.put("ultima_luz", lista);
        int n = lista.size();
        for (int i = 1; i <= Math.min(n, MINIMO); i++) FonteDados.chamado(p, "necron:aliados_" + i, 1);
        int cor = pl.corps.color();
        BlockPos h = PlanetLore.HUB;
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, h.getX() + 14, h.getZ());
        // a bateria responde: uma coluna grossa de luz e os Lanternas sobem para o céu
        DustParticleOptions dd = new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 4f);
        for (int k = 0; k < 60; k++) l.sendParticles(dd, h.getX() + 0.5, y + k * 2, h.getZ() + 0.5, 6, 1.2, 0.6, 1.2, 0);
        l.sendParticles(ParticleTypes.END_ROD, h.getX() + 0.5, y + 10, h.getZ() + 0.5, 120, 4, 8, 4, 0.1);
        l.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2f, 0.8f);
        l.playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 1.5f, 1.4f);
        for (int i = 0; i < 6; i++) {
            LanternaEntity a = LanternaEntity.lanterna(l, pl.corps, i == 0);
            if (a == null) continue;
            double ang = Math.PI * 2 * i / 6;
            a.moveTo(p.getX() + Math.cos(ang) * 6, p.getY() + 1, p.getZ() + Math.sin(ang) * 6, (float) Math.toDegrees(ang) + 90, 0);
            a.setNoAi(true);
            a.setNoGravity(true);
            a.setInvulnerable(true);
            l.addFreshEntity(a);
            SOBEM.add(new Object[]{l.dimension(), a.getUUID(), -i * 6});
        }
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                Component.translatable("necron.lanterna.chamado_titulo", Component.translatable("necron.lanterna.tropa." + t)).withStyle(st -> st.withColor(cor))));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                Component.translatable(n >= MINIMO ? "necron.lanterna.chamado_sub_pronto" : "necron.lanterna.chamado_sub", n, MINIMO).withStyle(ChatFormatting.GRAY)));
        p.sendSystemMessage(Component.translatable("necron.lanterna.recruta." + t).withStyle(st -> st.withColor(cor)));
        if (n == MINIMO) p.sendSystemMessage(Component.translatable("necron.lanterna.chamado_basta").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @SubscribeEvent
    public static void tickServidor(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || SOBEM.isEmpty()) return;
        MinecraftServer s = e.getServer();
        for (Iterator<Object[]> it = SOBEM.iterator(); it.hasNext(); ) {
            Object[] o = it.next();
            @SuppressWarnings("unchecked") ServerLevel l = s.getLevel((ResourceKey<Level>) o[0]);
            Entity a = l == null ? null : l.getEntity((UUID) o[1]);
            int t = (int) o[2] + 1;
            o[2] = t;
            if (a == null) { it.remove(); continue; }
            if (t <= 0) continue;
            double v = Math.min(1.6, 0.05 + t * 0.03);
            a.setPos(a.getX(), a.getY() + v, a.getZ());
            if (t % 2 == 0) l.sendParticles(ParticleTypes.END_ROD, a.getX(), a.getY(), a.getZ(), 2, 0.2, 0.4, 0.2, 0.01);
            if (t > 90) { a.discard(); it.remove(); }
        }
    }

    /** Teste: esquece as Tropas chamadas. */
    public static void zerar(ServerPlayer p) {
        SagaNecron.tag(p).remove("ultima_luz");
        for (int i = 1; i <= MINIMO; i++) FonteDados.chamado(p, "necron:aliados_" + i, 0);
        PROGRESSO.remove(p.getUUID());
    }

    private UltimaLuz() {}
}
