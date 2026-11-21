package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.fonte.FonteDados;
import com.lanterna.lore.LoreWorldData;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import com.lanterna.vida.Emocoes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.*;

/**
 * 0.58.0: o meio da linha corrompida (entre Apagar as Luzes e O Coração da Vida).
 *
 *  necron_c2b  A Caçada: quatro Lanternas fugiram para a Terra com notícias do Arauto. O anel negro os fareja (uma
 *              coluna cinza-roxa mostra cada um); cada fugitivo anda com dois companheiros.
 *  necron_c2c  Aurora Apagada: o Sgt. Rui enfrenta o Arauto; os quatro Guardas de Aurora (golens) defendem a vila; e o
 *              escudo de luz da Dra. Lívia, na praça, tem que se apagar (15 s junto dele).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CorrompidaMeio {
    public static final String FUGITIVO = "lanterna_fugitivo", COMPANHEIRO = "lanterna_fugitivo_comp", GUARDA = "lanterna_guarda_aurora";
    private static final int[][] CACA = {{300, 80}, {-250, -200}, {120, -320}, {-330, 230}};
    public static final int APAGAR_AURORA = 300;
    private static final Map<UUID, int[]> PROGRESSO = new HashMap<>();

    private static boolean feito(MinecraftServer s, String k) { return LoreWorldData.get(s).siteBuilt(k); }
    private static void marcar(MinecraftServer s, String k) { LoreWorldData.get(s).markSite(k); }

    @Nullable public static BlockPos fuga(int i) { return Escurece.rel(CACA[i - 1][0], CACA[i - 1][1]); }

    /** As Tropas que ainda têm Lanternas para mandar (as de pé; se nenhuma, qualquer uma). */
    private static List<BuiltinCorps> tropas(MinecraftServer s) {
        List<BuiltinCorps> r = new ArrayList<>();
        for (String t : Emocoes.ASPECTOS) {
            PlanetLore pl = null;
            for (PlanetLore x : PlanetLore.values()) if (x.corps.id().getPath().equals(t)) pl = x;
            for (BuiltinCorps b : BuiltinCorps.values()) if (b.id().getPath().equals(t) && (pl == null || !Queda.caida(s, pl))) r.add(b);
        }
        if (r.isEmpty()) for (String t : Emocoes.ASPECTOS) for (BuiltinCorps b : BuiltinCorps.values()) if (b.id().getPath().equals(t)) r.add(b);
        return r;
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 6 || !Tentacao.corrompido(p)) return;
        if (p.level().dimension() != Level.OVERWORLD || Escurece.rel(0, 0) == null) return;
        LoreData d = MissionManager.data(p);
        if (d == null) return;
        if (d.isActive("necron_c2b")) for (int i = 1; i <= 4; i++) caca(p, i);
        if (d.isActive("necron_c2c")) aurora(p);
    }

    // ===================== a caçada =====================

    private static void caca(ServerPlayer p, int i) {
        MinecraftServer s = p.server;
        if (FonteDados.chamado(p, "necron:caca_" + i) >= 1) return;
        if (feito(s, "saga:caca:" + i + ":fim")) { FonteDados.chamado(p, "necron:caca_" + i, 1); return; }
        BlockPos b0 = fuga(i);
        if (b0 == null) return;
        ServerLevel l = p.serverLevel();
        double dx = p.getX() - b0.getX(), dz = p.getZ() - b0.getZ(), dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 400) return;
        String tag = FUGITIVO + ":" + i;
        LanternaEntity lider = l.getEntitiesOfClass(LanternaEntity.class, new AABB(b0).inflate(160, 200, 160), x -> x.getTags().contains(tag)).stream().findFirst().orElse(null);
        // o anel negro fareja: uma coluna cinza-roxa sobre o fugitivo (ou sobre o esconderijo)
        if (p.tickCount % 20 == 6) {
            Vec3 alvo = lider != null ? lider.position() : Vec3.atBottomCenterOf(b0).add(0, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, b0.getX(), b0.getZ()) - b0.getY(), 0);
            DustParticleOptions dd = new DustParticleOptions(new Vector3f(0.45f, 0.35f, 0.55f), 2.5f);
            for (int k = 0; k < 16; k++) l.sendParticles(p, dd, true, alvo.x, alvo.y + 3 + k * 2.5, alvo.z, 1, 0.1, 0.4, 0.1, 0);
        }
        if (lider != null || dist > 70) return;
        if (!l.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(b0.getX() >> 4, b0.getZ() >> 4))) return;
        List<BuiltinCorps> tr = tropas(s);
        BuiltinCorps bc = tr.get((i * 3) % tr.size());
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, b0.getX(), b0.getZ());
        LanternaEntity f = LanternaEntity.lanterna(l, bc, true);
        if (f == null) return;
        f.moveTo(b0.getX() + 0.5, y, b0.getZ() + 0.5, 0, 0);
        f.addTag(FUGITIVO);
        f.addTag(tag);
        f.setPersistenceRequired();
        f.setCustomName(Component.translatable("necron.lanterna.fugitivo", Component.translatable("necron.lanterna.tropa." + bc.id().getPath())).withStyle(st -> st.withColor(bc.color())));
        f.setCustomNameVisible(true);
        var hp = f.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) { hp.setBaseValue(110); f.setHealth(110); }
        l.addFreshEntity(f);
        for (int k = 0; k < 2; k++) {
            LanternaEntity c = LanternaEntity.lanterna(l, bc, false);
            if (c == null) continue;
            c.moveTo(b0.getX() + 2.5 - k * 5, y + 1, b0.getZ() + 1.5, 0, 0);
            c.addTag(COMPANHEIRO);
            c.setPersistenceRequired();
            l.addFreshEntity(c);
        }
        if (!feito(s, "saga:caca:" + i)) {
            marcar(s, "saga:caca:" + i);
            p.sendSystemMessage(Component.translatable("necron.lanterna.caca_achou_" + i).withStyle(st -> st.withColor(bc.color())));
        }
    }

    @SubscribeEvent
    public static void morreu(LivingDeathEvent e) {
        var x = e.getEntity();
        if (!(x.level() instanceof ServerLevel l)) return;
        if (x.getTags().contains(FUGITIVO)) {
            for (String tag : x.getTags()) {
                if (!tag.startsWith(FUGITIVO + ":")) continue;
                int i = Integer.parseInt(tag.substring(FUGITIVO.length() + 1));
                marcar(l.getServer(), "saga:caca:" + i + ":fim");
                for (ServerPlayer q : l.players())
                    if (Tentacao.corrompido(q) && q.distanceToSqr(x) < 150 * 150) {
                        FonteDados.chamado(q, "necron:caca_" + i, 1);
                        q.sendSystemMessage(Component.translatable("necron.lanterna.caca_fim").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
                    }
            }
        }
        if (x.getTags().contains(GUARDA)) l.sendParticles(ParticleTypes.SMOKE, x.getX(), x.getY() + 1, x.getZ(), 20, 0.4, 0.8, 0.4, 0.02);
    }

    // ===================== Aurora apagada =====================

    private static void aurora(ServerPlayer p) {
        MinecraftServer s = p.server;
        BlockPos a = Escurece.rel(0, 0);
        ServerLevel l = p.serverLevel();
        int ya = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, a.getX(), a.getZ());
        Vec3 centro = new Vec3(a.getX() + 0.5, ya, a.getZ() + 0.5);
        // na horizontal (o ponto mais alto do centro pode ser uma estrutura da vila, não o chão da praça)
        double dx0 = p.getX() - centro.x, dz0 = p.getZ() - centro.z;
        double dist = Math.sqrt(dx0 * dx0 + dz0 * dz0) + Math.max(0, Math.abs(p.getY() - centro.y) - 12);
        if (feito(s, "saga:aurora_apagada")) {
            if (FonteDados.chamado(p, "necron:aurora_golens") < 1) FonteDados.chamado(p, "necron:aurora_golens", 1);
            if (FonteDados.chamado(p, "necron:aurora_apagada") < 1) FonteDados.chamado(p, "necron:aurora_apagada", 1);
            return;
        }
        if (dist > 60) { PROGRESSO.remove(p.getUUID()); return; }
        // o escudo da Dra. Lívia: um anel de luz dourada na praça
        if (p.tickCount % 20 == 6) {
            DustParticleOptions dd = new DustParticleOptions(new Vector3f(1f, 0.85f, 0.4f), 1.6f);
            for (int k = 0; k < 36; k++) {
                double ang = Math.PI * 2 * k / 36;
                l.sendParticles(dd, centro.x + Math.cos(ang) * 7, centro.y + 0.5 + l.random.nextDouble() * 3, centro.z + Math.sin(ang) * 7, 1, 0, 0, 0, 0);
            }
        }
        List<IronGolem> golens = l.getEntitiesOfClass(IronGolem.class, new AABB(BlockPos.containing(centro)).inflate(80, 40, 80), g -> g.getTags().contains(GUARDA));
        if (!feito(s, "saga:aurora_guardas")) {
            if (dist > 35) return;
            marcar(s, "saga:aurora_guardas");
            for (int i = 0; i < 4; i++) {
                IronGolem g = EntityType.IRON_GOLEM.create(l);
                if (g == null) continue;
                double ang = Math.PI * 2 * i / 4 + 0.4;
                int x = (int) (centro.x + Math.cos(ang) * 10), z = (int) (centro.z + Math.sin(ang) * 10);
                g.moveTo(x + 0.5, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5, 0, 0);
                g.addTag(GUARDA);
                g.setPersistenceRequired();
                g.setCustomName(Component.translatable("necron.lanterna.guarda_aurora").withStyle(ChatFormatting.GOLD));
                var hp = g.getAttribute(Attributes.MAX_HEALTH);
                if (hp != null) { hp.setBaseValue(140); g.setHealth(140); }
                l.addFreshEntity(g);
                g.setPersistentAngerTarget(p.getUUID());
                g.startPersistentAngerTimer();
                g.setTarget(p);
            }
            p.sendSystemMessage(Component.translatable("necron.lanterna.aurora_guardas").withStyle(ChatFormatting.GOLD));
            return;
        }
        // os guardas seguem bravos com o Arauto
        for (IronGolem g : golens) if (g.getTarget() == null && !p.isCreative()) { g.setPersistentAngerTarget(p.getUUID()); g.startPersistentAngerTimer(); g.setTarget(p); }
        if (!golens.isEmpty()) {
            p.displayClientMessage(Component.translatable("necron.lanterna.aurora_restam", golens.size()).withStyle(ChatFormatting.GOLD), true);
            return;
        }
        if (FonteDados.chamado(p, "necron:aurora_golens") < 1) {
            FonteDados.chamado(p, "necron:aurora_golens", 1);
            p.sendSystemMessage(Component.translatable("necron.lanterna.aurora_escudo").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        }
        int[] pr = PROGRESSO.computeIfAbsent(p.getUUID(), u -> new int[1]);
        if (dist > 8) { pr[0] = 0; return; }
        pr[0] += 10;
        l.sendParticles(ParticleTypes.SCULK_SOUL, centro.x, centro.y + 1, centro.z, 6, 2, 1, 2, 0.01);
        p.displayClientMessage(Component.translatable("necron.lanterna.aurora_apagando", Math.min(100, pr[0] * 100 / APAGAR_AURORA)).withStyle(ChatFormatting.DARK_PURPLE), true);
        if (pr[0] < APAGAR_AURORA) return;
        PROGRESSO.remove(p.getUUID());
        marcar(s, "saga:aurora_apagada");
        FonteDados.chamado(p, "necron:aurora_apagada", 1);
        l.playSound(null, BlockPos.containing(centro), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 3f, 0.5f);
        p.sendSystemMessage(Component.translatable("necron.lanterna.aurora_apagou").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }

    /** Teste: volta o meio da linha corrompida. */
    public static void zerar(MinecraftServer s) {
        LoreWorldData w = LoreWorldData.get(s);
        for (String k : new ArrayList<>(w.sites()))
            if (k.startsWith("saga:caca:") || k.equals("saga:aurora_apagada") || k.equals("saga:aurora_guardas")) w.resetSite(k);
    }

    public static void zerar(ServerPlayer p) {
        for (int i = 1; i <= 4; i++) FonteDados.chamado(p, "necron:caca_" + i, 0);
        FonteDados.chamado(p, "necron:aurora_golens", 0);
        FonteDados.chamado(p, "necron:aurora_apagada", 0);
        PROGRESSO.remove(p.getUUID());
    }

    private CorrompidaMeio() {}
}
