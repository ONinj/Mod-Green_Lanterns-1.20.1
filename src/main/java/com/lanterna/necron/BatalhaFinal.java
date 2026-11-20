package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.lore.LoreWorldData;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
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
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
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
import org.joml.Vector3f;

import java.util.*;

/**
 * 0.56.0: A Última Luz. Na cratera, com a missão final e dois anéis, começa a batalha: uns 15 Lanternas das Tropas que
 * ainda resistem descem do céu para lutar junto, e o Necron finalmente pode ser ferido (por quem usa duas luzes e pelos
 * aliados). Ele luta em quatro fases, cada uma com o poder de uma Tropa que ele derrubou:
 *
 *   1  o Amor roubado      chuva de cristais violeta (círculos no chão avisam antes) e o abraço que puxa
 *   2  o Índigo roubado    some e reaparece atrás de alguém, com a foice; chama ecos (servos)
 *   3  a Esperança roubada um escudo azul-escuro que quase anula o dano; lanças de luz em linha
 *   4  a Morte             mãos negras saindo do chão, a onda de morte em volta dele e mais servos
 *
 * É difícil mas controlada: quem cai na batalha não morre (os Lanternas puxam para a borda da cratera, com vida),
 * e o Necron não recupera a vida. Vencendo, ele cai, e começa a purificação.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BatalhaFinal {
    public static final String ALIADO = "lanterna_aliado_final", DERROTADO = "saga:necron_derrotado";
    private static final int ALIADOS = 15;
    private static final int[] COR_FASE = {0xC050FF, 0x6A5CFF, 0x4AA0FF, 0x8A3CC8};
    private static final BossEvent.BossBarColor[] BARRA_FASE = {BossEvent.BossBarColor.PINK, BossEvent.BossBarColor.BLUE,
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarColor.PURPLE};

    private record Golpe(Vec3 pos, float raio, int cor, long quando, float dano, boolean empurra) {}

    private static boolean ativa;
    private static int fase, ticks, sozinho, imuneAte, escudoAte;
    private static final List<UUID> aliados = new ArrayList<>();
    private static final List<Golpe> golpes = new ArrayList<>();

    public static boolean ativa() { return ativa; }

    public static boolean derrotado(MinecraftServer s) {
        return LoreWorldData.get(s).siteBuilt(DERROTADO);
    }

    // ===================== começo =====================

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
        BlockPos c = Cratera.centro(s);
        if (c == null) return;
        ServerLevel l = s.overworld();
        if (!ativa) {
            if (s.getTickCount() % 20 == 3 && !derrotado(s)) tentarComecar(s, l, c);
            return;
        }
        passo(s, l, c);
    }

    @Nullable
    private static NecronEntity necron(ServerLevel l, BlockPos c) {
        return l.getEntitiesOfClass(NecronEntity.class, new AABB(c).inflate(80, 60, 80)).stream().findFirst().orElse(null);
    }

    private static List<ServerPlayer> lutando(ServerLevel l, BlockPos c) {
        List<ServerPlayer> r = new ArrayList<>();
        for (ServerPlayer p : l.players()) if (!p.isSpectator() && p.blockPosition().distSqr(c) < 70 * 70) r.add(p);
        return r;
    }

    private static void tentarComecar(MinecraftServer s, ServerLevel l, BlockPos c) {
        NecronEntity n = necron(l, c);
        if (n == null) return;
        for (ServerPlayer p : lutando(l, c)) {
            LoreData d = MissionManager.data(p);
            if (d == null || !d.active().containsKey("necron_6")) continue;
            if (!Cratera.doisAneis(p)) {
                if (p.tickCount % 200 < 20) p.displayClientMessage(Component.translatable("necron.lanterna.final_precisa_dois").withStyle(ChatFormatting.DARK_PURPLE), true);
                continue;
            }
            if (p.distanceTo(n) > 40) continue;
            comecar(s, l, c, n, p);
            return;
        }
    }

    private static void comecar(MinecraftServer s, ServerLevel l, BlockPos c, NecronEntity n, ServerPlayer quem) {
        ativa = true;
        fase = 0;
        ticks = 0;
        sozinho = 0;
        golpes.clear();
        var vida = n.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (vida != null) vida.setBaseValue(600);
        n.setHealth(n.getMaxHealth());
        // os Lanternas que restam descem do céu
        List<BuiltinCorps> tropas = new ArrayList<>();
        for (BuiltinCorps b : BuiltinCorps.values()) {
            PlanetLore pl = PlanetLore.byCorps(b.id());
            if (pl == null || b == BuiltinCorps.BLACK || b == BuiltinCorps.WHITE || Queda.caida(s, pl)) continue;
            tropas.add(b);
        }
        if (tropas.isEmpty()) tropas.add(BuiltinCorps.GREEN);
        for (int i = 0; i < ALIADOS; i++) {
            BuiltinCorps b = tropas.get(i % tropas.size());
            LanternaEntity a = LanternaEntity.lanterna(l, b, i < 3);
            if (a == null) continue;
            double ang = Math.PI * 2 * i / ALIADOS, r = 14 + (i % 3) * 4;
            double x = quem.getX() + Math.cos(ang) * r, z = quem.getZ() + Math.sin(ang) * r;
            a.moveTo(x, quem.getY() + 28 + (i % 4) * 3, z, (float) Math.toDegrees(ang) + 90, 0);
            a.addTag(ALIADO);
            a.setCustomName(Component.translatable("necron.lanterna.aliado_final", Component.translatable("corps.lanterna." + b.id().getPath()))
                    .withStyle(st -> st.withColor(b.color())));
            var hp = a.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (hp != null) { hp.setBaseValue(hp.getBaseValue() * 3); a.setHealth(a.getMaxHealth()); }
            a.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false));
            a.addEffect(new MobEffectInstance(MobEffects.REGENERATION, Integer.MAX_VALUE, 0, false, false));
            a.setPersistenceRequired();
            l.addFreshEntity(a);
            aliados.add(a.getUUID());
        }
        for (ServerPlayer p : lutando(l, c)) {
            titulo(p, "necron.lanterna.final_titulo", "necron.lanterna.final_sub", 0xFFFFFF);
            p.sendSystemMessage(Component.translatable("necron.lanterna.final_fala").withStyle(ChatFormatting.GREEN));
        }
        l.playSound(null, n.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3f, 0.5f);
        mudarFase(l, c, n, 1);
        LanternaMod.LOGGER.info("Lanterna: começou a batalha final contra o Necron");
    }

    private static void titulo(ServerPlayer q, String t, String sub, int cor) {
        q.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.translatable(t).withStyle(st -> st.withColor(cor))));
        q.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.translatable(sub).withStyle(ChatFormatting.GRAY)));
    }

    private static void mudarFase(ServerLevel l, BlockPos c, NecronEntity n, int nova) {
        fase = nova;
        imuneAte = ticks + 50;
        escudoAte = 0;
        int cor = COR_FASE[nova - 1];
        n.barra().setColor(BARRA_FASE[nova - 1]);
        for (ServerPlayer p : lutando(l, c)) titulo(p, "necron.lanterna.fase_" + nova, "necron.lanterna.fase_" + nova + "_sub", cor);
        l.playSound(null, n.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2f, 0.6f);
        DustParticleOptions d = dust(cor, 3f);
        for (int k = 0; k < 60; k++) {
            double a = Math.PI * 2 * k / 60;
            for (int h = 0; h < 4; h++) l.sendParticles(d, n.getX() + Math.cos(a) * (2 + h), n.getY() + 1 + h * 0.6, n.getZ() + Math.sin(a) * (2 + h), 1, 0, 0, 0, 0);
        }
        n.triggerAnim("golpe", "golpe");
    }

    private static DustParticleOptions dust(int cor, float tam) {
        return new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), tam);
    }

    // ===================== a luta =====================

    private static void passo(MinecraftServer s, ServerLevel l, BlockPos c) {
        ticks++;
        NecronEntity n = necron(l, c);
        List<ServerPlayer> ps = lutando(l, c);
        if (n == null) { encerrar(l, false); return; }
        if (ps.isEmpty()) {
            if (++sozinho > 20 * 30) { n.setHealth(n.getMaxHealth()); encerrar(l, false); }
            return;
        }
        sozinho = 0;
        // fase pela vida
        float frac = n.getHealth() / n.getMaxHealth();
        int devia = frac > 0.75f ? 1 : frac > 0.5f ? 2 : frac > 0.25f ? 3 : 4;
        if (devia > fase) mudarFase(l, c, n, devia);
        // aliados atacam os servos e o Necron
        if (ticks % 40 == 0) guiarAliados(l, c, n);
        // golpes avisados no chão
        resolverGolpes(l, ticks);
        if (ticks < imuneAte) return;
        ServerPlayer alvo = ps.get(l.random.nextInt(ps.size()));
        int cor = COR_FASE[fase - 1];
        switch (fase) {
            case 1 -> {
                if (ticks % 70 == 0) for (ServerPlayer p : ps) {
                    marcar(l, p.position(), 3f, cor, 30, 8, false);
                    for (int k = 0; k < 3; k++) marcar(l, p.position().add(l.random.nextGaussian() * 5, 0, l.random.nextGaussian() * 5), 2.5f, cor, 30 + k * 6, 7, false);
                }
                if (ticks % 140 == 70) abraco(l, n, ps, cor);
            }
            case 2 -> {
                if (ticks % 60 == 0) piscar(l, c, n, alvo, cor);
                if (ticks % 160 == 80) chamarEcos(l, n, 3);
            }
            case 3 -> {
                if (ticks % 200 == 10) { escudoAte = ticks + 80; l.playSound(null, n.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2f, 0.5f); }
                if (ticks < escudoAte && ticks % 3 == 0) cupula(l, n, cor);
                if (ticks % 50 == 0) lanca(l, n, alvo, cor);
            }
            default -> {
                if (ticks % 45 == 0) for (ServerPlayer p : ps) {
                    marcar(l, p.position(), 2.5f, 0x2A0A3A, 25, 11, false);
                    for (int k = 0; k < 2; k++) marcar(l, p.position().add(l.random.nextGaussian() * 6, 0, l.random.nextGaussian() * 6), 2.5f, 0x2A0A3A, 25, 10, false);
                }
                if (ticks % 180 == 90) marcar(l, n.position(), 12f, cor, 40, 14, true);
                if (ticks % 120 == 30) chamarEcos(l, n, 2);
            }
        }
        // a barra de vida da batalha
        n.barra().setProgress(frac);
    }

    /** Um golpe avisado: círculo no chão (na cor da fase) e, depois do tempo, o dano. */
    private static void marcar(ServerLevel l, Vec3 onde, float raio, int cor, int espera, float dano, boolean empurra) {
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(onde.x), (int) Math.floor(onde.z));
        golpes.add(new Golpe(new Vec3(onde.x, Math.min(onde.y, y + 0.1), onde.z), raio, cor, ticks + espera, dano, empurra));
    }

    private static void resolverGolpes(ServerLevel l, int agora) {
        for (Iterator<Golpe> it = golpes.iterator(); it.hasNext(); ) {
            Golpe g = it.next();
            if (agora < g.quando) {
                if (agora % 3 == 0) {
                    DustParticleOptions d = dust(g.cor, 1.6f);
                    int n = (int) (g.raio * 8);
                    for (int k = 0; k < n; k++) {
                        double a = Math.PI * 2 * k / n;
                        l.sendParticles(d, g.pos.x + Math.cos(a) * g.raio, g.pos.y + 0.15, g.pos.z + Math.sin(a) * g.raio, 1, 0, 0, 0, 0);
                    }
                }
                continue;
            }
            it.remove();
            DustParticleOptions d = dust(g.cor, 2.4f);
            l.sendParticles(d, g.pos.x, g.pos.y + 1, g.pos.z, (int) (g.raio * 14), g.raio * 0.5, 1, g.raio * 0.5, 0);
            l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, g.pos.x, g.pos.y + 0.3, g.pos.z, (int) (g.raio * 6), g.raio * 0.4, 0.3, g.raio * 0.4, 0.05);
            l.playSound(null, BlockPos.containing(g.pos), g.empurra ? SoundEvents.GENERIC_EXPLODE : SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.2f, 0.6f);
            for (ServerPlayer p : l.players()) {
                if (p.position().distanceTo(g.pos) > g.raio + 0.6 || p.isCreative() || p.isSpectator()) continue;
                p.hurt(p.damageSources().magic(), g.dano);
                if (g.empurra) {
                    Vec3 v = p.position().subtract(g.pos).normalize().scale(1.8);
                    p.push(v.x, 0.7, v.z);
                    p.hurtMarked = true;
                }
            }
        }
    }

    /** O abraço do Amor roubado: puxa todo mundo para perto dele. */
    private static void abraco(ServerLevel l, NecronEntity n, List<ServerPlayer> ps, int cor) {
        l.playSound(null, n.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2f, 0.5f);
        for (ServerPlayer p : ps) {
            if (p.distanceTo(n) > 22) continue;
            Vec3 v = n.position().subtract(p.position()).normalize().scale(1.3);
            p.push(v.x, 0.25, v.z);
            p.hurtMarked = true;
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            Vec3 d = n.position().add(0, 1.5, 0).subtract(p.position());
            for (int k = 0; k < 20; k++) {
                Vec3 q = p.position().add(0, 1, 0).add(d.scale(k / 20.0));
                l.sendParticles(dust(cor, 1.2f), q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
        }
    }

    /** O Índigo roubado: some e aparece atrás de alguém, com a foice já descendo. */
    private static void piscar(ServerLevel l, BlockPos c, NecronEntity n, ServerPlayer alvo, int cor) {
        Vec3 atras = alvo.position().subtract(Vec3.directionFromRotation(0, alvo.getYRot()).multiply(1, 0, 1).normalize().scale(3));
        Vec3 casa = Vec3.atBottomCenterOf(Cratera.lugarDoNecron(c));
        if (atras.distanceTo(casa) > 30) atras = casa.add(atras.subtract(casa).normalize().scale(30));
        l.sendParticles(dust(cor, 2.5f), n.getX(), n.getY() + 1.5, n.getZ(), 40, 0.6, 1.2, 0.6, 0);
        n.saltarPara(atras);
        l.sendParticles(dust(cor, 2.5f), atras.x, atras.y + 1.5, atras.z, 40, 0.6, 1.2, 0.6, 0);
        l.playSound(null, BlockPos.containing(atras), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5f, 0.5f);
        marcar(l, atras.add(alvo.position().subtract(atras).normalize().scale(2)), 4f, cor, 14, 10, true);
        n.triggerAnim("golpe", "golpe");
    }

    /** Ecos: servos que saem da sombra dele. */
    private static void chamarEcos(ServerLevel l, NecronEntity n, int quantos) {
        for (int i = 0; i < quantos; i++) {
            Mob m = Cratera.novoServo(l);
            if (m == null) continue;
            double a = l.random.nextDouble() * Math.PI * 2;
            m.moveTo(n.getX() + Math.cos(a) * 4, n.getY(), n.getZ() + Math.sin(a) * 4, 0, 0);
            m.addTag(Cratera.SERVO);
            l.addFreshEntity(m);
            l.sendParticles(ParticleTypes.SOUL, m.getX(), m.getY() + 0.5, m.getZ(), 15, 0.3, 0.6, 0.3, 0.02);
        }
    }

    /** A cúpula azul-escura da Esperança roubada (o dano quase não passa). */
    private static void cupula(ServerLevel l, NecronEntity n, int cor) {
        DustParticleOptions d = dust(cor, 1.8f);
        for (int k = 0; k < 18; k++) {
            double a = l.random.nextDouble() * Math.PI * 2, b = l.random.nextDouble() * Math.PI / 2;
            l.sendParticles(d, n.getX() + Math.cos(a) * Math.cos(b) * 3, n.getY() + 0.5 + Math.sin(b) * 3.5, n.getZ() + Math.sin(a) * Math.cos(b) * 3, 1, 0, 0, 0, 0);
        }
    }

    /** Lança de luz invertida: uma fileira de golpes do Necron até o alvo. */
    private static void lanca(ServerLevel l, NecronEntity n, ServerPlayer alvo, int cor) {
        Vec3 de = n.position(), dir = alvo.position().subtract(de).multiply(1, 0, 1);
        if (dir.lengthSqr() < 1) return;
        dir = dir.normalize();
        for (int k = 1; k <= 7; k++) marcar(l, de.add(dir.scale(k * 3.2)), 2f, cor, 18 + k * 2, 9, false);
    }

    private static void guiarAliados(ServerLevel l, BlockPos c, NecronEntity n) {
        List<Mob> servos = l.getEntitiesOfClass(Mob.class, new AABB(c).inflate(90, 60, 90), Cratera::servo);
        for (Iterator<UUID> it = aliados.iterator(); it.hasNext(); ) {
            Entity e = l.getEntity(it.next());
            if (!(e instanceof LanternaEntity a) || !a.isAlive()) { it.remove(); continue; }
            Mob alvo = n;
            double melhor = 30 * 30;
            for (Mob m : servos) { double d = m.distanceToSqr(a); if (d < melhor) { melhor = d; alvo = m; } }
            a.setTarget(alvo);
        }
    }

    // ===================== dano e quedas =====================

    /** No escudo da Esperança e no começo de cada fase o dano quase não passa. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void ferido(LivingHurtEvent e) {
        if (!ativa) return;
        if (e.getEntity() instanceof NecronEntity) {
            if (ticks < imuneAte) e.setAmount(0);
            else if (ticks < escudoAte) e.setAmount(e.getAmount() * 0.15f);
        }
        // os aliados não se ferem entre si nem com os golpes do jogador
        if (e.getEntity().getTags().contains(ALIADO) && (e.getSource().getEntity() instanceof Player || (e.getSource().getEntity() != null
                && e.getSource().getEntity().getTags().contains(ALIADO)))) e.setCanceled(true);
    }

    /** Quem cai na batalha não morre: os Lanternas puxam para a borda da cratera. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void caiu(LivingDeathEvent e) {
        if (!ativa || !(e.getEntity() instanceof ServerPlayer p)) return;
        BlockPos c = Cratera.centro(p.server);
        if (c == null || p.blockPosition().distSqr(c) > 80 * 80) return;
        e.setCanceled(true);
        p.setHealth(p.getMaxHealth() * 0.6f);
        p.removeAllEffects();
        Vec3 fora = p.position().subtract(Vec3.atCenterOf(c)).multiply(1, 0, 1);
        fora = fora.lengthSqr() < 1 ? new Vec3(1, 0, 0) : fora.normalize();
        int x = c.getX() + (int) (fora.x * (Cratera.BORDA + 6)), z = c.getZ() + (int) (fora.z * (Cratera.BORDA + 6));
        int y = p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        p.teleportTo(x + 0.5, y, z + 0.5);
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 1));
        p.displayClientMessage(Component.translatable("necron.lanterna.final_resgate").withStyle(ChatFormatting.GREEN), false);
    }

    // ===================== fim =====================

    /** O Necron caiu. */
    public static void vitoria(ServerLevel l, NecronEntity n) {
        if (!ativa) return;
        MinecraftServer s = l.getServer();
        BlockPos c = Cratera.centro(s);
        LoreWorldData.get(s).markSite(DERROTADO);
        for (int k = 0; k < 6; k++) l.sendParticles(dust(0xFFFFFF, 3f), n.getX(), n.getY() + 1.5 + k * 0.5, n.getZ(), 60, 1.5, 1.5, 1.5, 0);
        l.sendParticles(ParticleTypes.SOUL, n.getX(), n.getY() + 1.5, n.getZ(), 200, 2, 2, 2, 0.15);
        l.playSound(null, n.blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 3f, 0.5f);
        l.playSound(null, n.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 3f, 0.4f);
        // os servos se desfazem; os aliados comemoram (fogos nas cores das Tropas)
        if (c != null) for (Mob m : l.getEntitiesOfClass(Mob.class, new AABB(c).inflate(120, 80, 120), Cratera::servo)) {
            l.sendParticles(ParticleTypes.LARGE_SMOKE, m.getX(), m.getY() + 1, m.getZ(), 12, 0.3, 0.6, 0.3, 0.02);
            m.discard();
        }
        for (UUID u : aliados) {
            Entity a = l.getEntity(u);
            if (a instanceof LanternaEntity le) {
                le.setTarget(null);
                l.sendParticles(ParticleTypes.FIREWORK, a.getX(), a.getY() + 2, a.getZ(), 30, 0.5, 1, 0.5, 0.1);
            }
        }
        for (ServerPlayer p : c == null ? l.players() : lutando(l, c)) {
            titulo(p, "necron.lanterna.vitoria_titulo", "necron.lanterna.vitoria_sub", 0xFFFFFF);
            p.sendSystemMessage(Component.translatable("necron.lanterna.vitoria_fala").withStyle(ChatFormatting.GREEN));
            com.lanterna.fonte.FonteDados.chamado(p, "necron:vitoria", 1);
        }
        LanternaMod.LOGGER.info("Lanterna: o Necron foi derrotado");
        for (ServerPlayer q : s.getPlayerList().getPlayers()) SagaNecron.mandarMundo(q);
        for (ServerPlayer q : lutando(l, c == null ? n.blockPosition() : c)) MissionManager.start(q, "necron_7");
        encerrar(l, true);
    }

    private static void encerrar(ServerLevel l, boolean venceu) {
        ativa = false;
        golpes.clear();
        if (!venceu) {
            for (UUID u : aliados) { Entity a = l.getEntity(u); if (a != null) a.discard(); }
            aliados.clear();
        }
        // vencendo, os aliados ficam um pouco na cratera e depois vão embora (somem quando ninguém mais está perto)
    }

    /** Teste: volta tudo (o Necron reaparece). */
    public static void zerar(MinecraftServer s) {
        LoreWorldData.get(s).resetSite(DERROTADO);
        ativa = false;
        golpes.clear();
        aliados.clear();
    }

    private BatalhaFinal() {}
}
