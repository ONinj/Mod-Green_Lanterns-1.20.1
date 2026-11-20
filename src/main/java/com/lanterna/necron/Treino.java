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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 0.56.3 — Capítulo 3, Duas Luzes (no planeta da Tropa do jogador, depois de forjar o segundo anel):
 *
 *  necron_3              Duas Luzes          forjar um segundo anel e usar os dois ao mesmo tempo (já existia)
 *  necron_3b_<tropa>     O Treino            o Mestre da Tropa ensina: 6 construtos, um de apoio do anel do punho e 4
 *                                            alvos de treino da cidade
 *  necron_3c_<tropa>     O Duelo             duelo de treino com o Mestre (ele se rende quase vencido; se o jogador
 *                                            "cair", o Mestre segura o golpe)
 *  necron_3d             A Primeira Fenda    uma fenda pequena se abre na Terra, perto da Vila Aurora: derrotar 10 servos
 *                                            com os dois anéis (a primeira vez que eles sangram)
 * Depois disso vem a defesa da Bateria Central da Tropa (necron_4_<tropa>).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Treino {
    public static final String MESTRE = "lanterna_mestre_duelo", PRIMEIRA = "lanterna_primeira_fenda";
    public static final int SERVOS_FENDA = 10;
    private static final Map<UUID, UUID> MESTRES = new HashMap<>();

    private static String tropa(ServerPlayer p) {
        String t = SagaNecron.tag(p).getString("tropa");
        return Emocoes.aspecto(t) ? t : "green";
    }

    private static BuiltinCorps corps(String t) {
        for (BuiltinCorps b : BuiltinCorps.values()) if (b.id().getPath().equals(t)) return b;
        return BuiltinCorps.GREEN;
    }

    @Nullable
    private static PlanetLore casa(String t) { return PlanetLore.byCorps(corps(t).id()); }

    private static void fala(ServerPlayer p, String chave, String tropa) {
        p.sendSystemMessage(Component.translatable(chave).withStyle(s -> s.withColor(corps(tropa).color())));
    }

    // ===================== construtos (o de apoio do anel do punho) =====================

    /** Chamado quando o jogador usa um construto: conta o de apoio vindo do segundo anel. */
    public static void construto(ServerPlayer p, com.lanterna.construct.ConstructType tipo) {
        var r = com.lanterna.capability.ModCapabilities.get(p).orElse(null);
        if (r == null || r.segundo().isEmpty() || !r.segundo().equals(tipo.corps)) return;
        LoreData d = MissionManager.data(p);
        if (d == null || !d.isActive("necron_3b_" + tropa(p))) return;
        if (FonteDados.chamado(p, "necron:apoio") < 1) {
            FonteDados.chamado(p, "necron:apoio", 1);
            fala(p, "necron.lanterna.mestre_apoio", tropa(p));
        }
    }

    // ===================== tick =====================

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 3) return;
        LoreData d = MissionManager.data(p);
        if (d == null) return;
        String t = tropa(p);
        // a cadeia: Duas Luzes → Treino → Duelo → Primeira Fenda (as próximas começam sozinhas)
        if (d.completed().contains("necron_3") && !d.isDone("necron_3b_" + t) && !d.isActive("necron_3b_" + t)) {
            MissionManager.start(p, "necron_3b_" + t);
            fala(p, "necron.lanterna.mestre_1", t);
        }
        if (d.isActive("necron_3c_" + t)) duelo(p, t);
        if (d.isActive("necron_3d")) primeiraFenda(p);
    }

    // ===================== o duelo =====================

    private static void duelo(ServerPlayer p, String t) {
        PlanetLore c = casa(t);
        if (c == null || PlanetLore.byDimension(p.level().dimension()) != c) return;
        ServerLevel l = p.serverLevel();
        double hx = p.getX() - PlanetLore.HUB.getX(), hz = p.getZ() - PlanetLore.HUB.getZ();
        if (hx * hx + hz * hz > 60 * 60) return;   // só na horizontal (o centro da cidade fica guardado na altura 0)
        UUID u = MESTRES.get(p.getUUID());
        Entity m = u == null ? null : l.getEntity(u);
        if (m != null && m.isAlive()) return;
        if (FonteDados.chamado(p, "necron:duelo") >= 1) return;
        LanternaEntity mestre = LanternaEntity.lanterna(l, corps(t), true);
        if (mestre == null) return;
        Vec3 frente = Vec3.directionFromRotation(0, p.getYRot()).multiply(1, 0, 1).normalize().scale(6);
        BlockPos onde = BlockPos.containing(p.position().add(frente));
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, onde.getX(), onde.getZ());
        mestre.moveTo(onde.getX() + 0.5, y, onde.getZ() + 0.5, p.getYRot() + 180, 0);
        mestre.addTag(MESTRE);
        mestre.addTag(MESTRE + ":" + p.getUUID());
        mestre.setCustomName(Component.translatable("necron.lanterna.mestre_nome", Component.translatable("corps.lanterna." + t))
                .withStyle(s -> s.withColor(corps(t).color())));
        mestre.setCustomNameVisible(true);
        var hp = mestre.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (hp != null) { hp.setBaseValue(Math.max(hp.getBaseValue() * 4, p.getMaxHealth() * 1.5)); mestre.setHealth(mestre.getMaxHealth()); }
        l.addFreshEntity(mestre);
        mestre.setTarget(p);
        MESTRES.put(p.getUUID(), mestre.getUUID());
        l.sendParticles(ParticleTypes.END_ROD, mestre.getX(), mestre.getY() + 1, mestre.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
        fala(p, "necron.lanterna.mestre_duelo", t);
    }

    @Nullable
    private static ServerPlayer donoDoMestre(Entity m) {
        if (!(m.level() instanceof ServerLevel l)) return null;
        for (String tag : m.getTags()) if (tag.startsWith(MESTRE + ":")) {
            var pl = l.getServer().getPlayerList().getPlayer(UUID.fromString(tag.substring(MESTRE.length() + 1)));
            if (pl != null) return pl;
        }
        return null;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void ferido(LivingHurtEvent e) {
        var alvo = e.getEntity();
        // o Mestre se rende quase vencido
        if (alvo.getTags().contains(MESTRE) && alvo instanceof LanternaEntity m) {
            if (m.getHealth() - e.getAmount() > m.getMaxHealth() * 0.15f) return;
            e.setAmount(0);
            ServerPlayer dono = donoDoMestre(m);
            if (dono != null && FonteDados.chamado(dono, "necron:duelo") < 1) {
                FonteDados.chamado(dono, "necron:duelo", 1);
                fala(dono, "necron.lanterna.mestre_rende", tropa(dono));
                dono.level().playSound(null, m.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1.2f);
            }
            m.setTarget(null);
            m.setNoAi(true);
            m.discard();
            return;
        }
        // o golpe do Mestre não mata: ele segura
        if (alvo instanceof ServerPlayer p && e.getSource().getEntity() != null && e.getSource().getEntity().getTags().contains(MESTRE)
                && p.getHealth() - e.getAmount() < 2) {
            e.setAmount(Math.max(0, p.getHealth() - 2));
            fala(p, "necron.lanterna.mestre_segura", tropa(p));
            Entity m = e.getSource().getEntity();
            if (m instanceof LanternaEntity le) { le.setTarget(null); le.discard(); }
            MESTRES.remove(p.getUUID());
        }
    }

    // ===================== a primeira fenda =====================

    /** Onde a fenda pequena se abre: perto da Vila Aurora (ou do spawn da Terra). */
    @Nullable
    public static BlockPos fendaPequena(ServerLevel l) {
        BlockPos c = com.lanterna.terra.TerraSite.centro != null ? com.lanterna.terra.TerraSite.centro : l.getSharedSpawnPos();
        int x = c.getX() + 170, z = c.getZ() + 60;
        l.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 12, z);
    }

    private static void primeiraFenda(ServerPlayer p) {
        if (p.level().dimension() != Level.OVERWORLD) return;
        ServerLevel l = p.serverLevel();
        BlockPos f = fendaPequena(l);
        if (f == null || p.blockPosition().distSqr(f) > 70 * 70) return;
        int mortos = FonteDados.chamado(p, "necron:primeira");
        if (mortos >= SERVOS_FENDA) return;
        AABB area = new AABB(f).inflate(50, 40, 50);
        String tag = PRIMEIRA + ":" + p.getUUID();
        int vivos = l.getEntitiesOfClass(Mob.class, area, m -> m.getTags().contains(tag)).size();
        if (vivos >= 4 || vivos + mortos >= SERVOS_FENDA) return;
        if (p.tickCount % 60 != 3) return;
        Mob m = Cratera.novoServo(l);
        if (m == null) return;
        double a = l.random.nextDouble() * Math.PI * 2;
        m.moveTo(f.getX() + Math.cos(a) * 3 + 0.5, f.getY() - 2, f.getZ() + Math.sin(a) * 3 + 0.5, 0, 0);
        m.addTag(Cratera.SERVO);
        m.addTag(PRIMEIRA);
        m.addTag(tag);
        m.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 160, 0, false, false));
        l.addFreshEntity(m);
        if (m instanceof LanternaEntity le) le.setTarget(p);
        if (mortos == 0 && vivos == 0) { fala(p, "necron.lanterna.mestre_fenda", tropa(p)); SagaNecron.mandarMundo(p); }
    }

    /** Servo da primeira fenda derrotado por quem a enfrenta (com as duas luzes). */
    @SubscribeEvent
    public static void morreu(LivingDeathEvent e) {
        var m = e.getEntity();
        if (!m.getTags().contains(PRIMEIRA)) return;
        for (String tag : m.getTags()) {
            if (!tag.startsWith(PRIMEIRA + ":")) continue;
            if (!(m.level() instanceof ServerLevel l)) return;
            ServerPlayer p = l.getServer().getPlayerList().getPlayer(UUID.fromString(tag.substring(PRIMEIRA.length() + 1)));
            if (p == null) return;
            int n = FonteDados.chamado(p, "necron:primeira") + 1;
            FonteDados.chamado(p, "necron:primeira", n);
            p.displayClientMessage(Component.translatable("necron.lanterna.primeira_conta", n, SERVOS_FENDA).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            if (n >= SERVOS_FENDA) {
                FonteDados.chamado(p, "necron:primeira_fechou", 1);
                fala(p, "necron.lanterna.mestre_fenda_fim", tropa(p));
                l.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 1f);
                for (ServerPlayer q : l.getServer().getPlayerList().getPlayers()) SagaNecron.mandarMundo(q);
            }
            return;
        }
    }

    /** A fenda pequena está aberta para este jogador (o cliente desenha o portal na Terra). */
    public static boolean fendaAberta(ServerPlayer p) {
        LoreData d = MissionManager.data(p);
        return d != null && d.isActive("necron_3d") && FonteDados.chamado(p, "necron:primeira") < SERVOS_FENDA;
    }

    /** Teste: zera o capítulo. */
    public static void zerar(ServerPlayer p) {
        MESTRES.remove(p.getUUID());
        for (String k : new String[]{"necron:apoio", "necron:duelo", "necron:primeira", "necron:primeira_fechou"}) FonteDados.chamado(p, k, 0);
    }

    private Treino() {}
}
