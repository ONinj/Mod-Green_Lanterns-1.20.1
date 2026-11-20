package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.fonte.FonteDados;
import com.lanterna.lore.LoreItems;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.*;

/**
 * 0.56.6: Capítulo 6, As Quedas. Depois que uma Tropa cai (a cena da queda), o jogador vai até o planeta caído:
 *
 *  1. Os escombros: três Lanternas feridos ficaram soterrados em montes de pedra negra na terra morta (um feixe da cor
 *     da Tropa mostra cada um). Cavando o monte (sobra no máximo 1/5 das pedras), o Lanterna se levanta, agradece e voa
 *     para o que sobrou da bateria. Na metade da escavação, servos saem das sombras.
 *  2. O Carcereiro: soltos os três, aparece o Carcereiro do Necron junto à bateria quebrada (barra de chefe). Ele solta
 *     uma onda de almas em volta e chama servos. Derrotado, deixa cair o Núcleo da Bateria daquela Tropa.
 *  3. O núcleo: levá-lo até a Bateria Central da própria Tropa, onde fica guardado (a purificação vai precisar dele).
 *
 * Tudo é do mundo (os montes, o Carcereiro, o núcleo guardado): o que um jogador faz conta para todos.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Ruinas {
    public static final String CAIDO = "lanterna_caido_ruina", CARC = "lanterna_carcereiro", NUCLEO_TAG = "lanterna_tropa";
    private static final int VIDA_CARC = 450;

    /** Onde fica o monte i, na terra morta em volta da bateria caída. */
    public static BlockPos ponto(int i) {
        BlockPos h = PlanetLore.HUB;
        double a = Math.toRadians(20 + 120 * (i - 1));
        return new BlockPos(h.getX() + (int) (Math.cos(a) * 50), 0, h.getZ() + (int) (Math.sin(a) * 50));
    }

    private static String chave(PlanetLore p, String o) { return "saga:ruina:" + p.planet + ":" + o; }
    private static boolean feito(MinecraftServer s, String k) { return LoreWorldData.get(s).siteBuilt(k); }
    private static void marcar(MinecraftServer s, String k) { LoreWorldData.get(s).markSite(k); }

    private static BlockPos chao(ServerLevel l, BlockPos p) {
        return new BlockPos(p.getX(), l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()), p.getZ());
    }

    public static boolean nucleoGuardado(MinecraftServer s, String tropa) { return feito(s, "saga:nucleo:" + tropa); }

    // ===================== o monte de escombros =====================

    /** As pedras do monte (em volta de uma cova de 3x3 onde o Lanterna está deitado). */
    public static List<BlockPos> monte(BlockPos c) {
        List<BlockPos> l = new ArrayList<>();
        for (int y = 0; y <= 3; y++)
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++) {
                    double r = Math.sqrt(dx * dx + dz * dz);
                    if (r > 3.2 - y * 0.8) continue;
                    if (y == 0 && Math.abs(dx) <= 1 && Math.abs(dz) <= 1) continue;
                    l.add(c.offset(dx, y, dz));
                }
        return l;
    }

    private static BlockState pedra(BlockPos b) {
        int r = Math.floorMod(b.getX() * 31 + b.getY() * 17 + b.getZ() * 7, 11);
        return r < 4 ? Blocks.BLACKSTONE.defaultBlockState() : r < 7 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                : r < 9 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : r < 10 ? Blocks.BASALT.defaultBlockState()
                : Blocks.CRYING_OBSIDIAN.defaultBlockState();
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 3) return;
        if (p.tickCount % 20 == 3) entregar(p);
        PlanetLore pl = PlanetLore.byDimension(p.level().dimension());
        if (pl == null || !Queda.caida(p.server, pl) || !Queda.pronta(p.server, pl)) return;
        String t = pl.corps.id().getPath();
        LoreData d = MissionManager.data(p);
        if (d == null || !d.isActive("necron_q_" + t)) return;
        ServerLevel l = p.serverLevel();
        boolean todos = true;
        for (int i = 1; i <= 3; i++) if (!escombro(l, p, pl, i)) todos = false;
        if (todos) carcereiro(l, p, pl);
    }

    /** Cuida do monte i; true se o Lanterna dele já foi solto. */
    private static boolean escombro(ServerLevel l, ServerPlayer p, PlanetLore pl, int i) {
        MinecraftServer s = p.server;
        String t = pl.corps.id().getPath(), k = chave(pl, "monte:" + i);
        if (feito(s, k + ":livre")) {
            if (FonteDados.chamado(p, "necron:caido_" + t + "_" + i) < 1) FonteDados.chamado(p, "necron:caido_" + t + "_" + i, 1);
            return true;
        }
        BlockPos b = ponto(i);
        double dx = p.getX() - b.getX(), dz = p.getZ() - b.getZ(), dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 160 || !l.isLoaded(b) || !l.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(b.getX() >> 4, b.getZ() >> 4))) return false;
        if (!feito(s, k)) {
            BlockPos c = chao(l, b);
            for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) for (int y = 0; y <= 1; y++) l.setBlock(c.offset(ox, y, oz), Blocks.AIR.defaultBlockState(), 3);
            for (BlockPos q : monte(c)) l.setBlock(q, pedra(q), 3);
            l.setBlock(c.offset(2, 0, 2).above(2), Blocks.SOUL_FIRE.defaultBlockState(), 3);
            LanternaEntity f = LanternaEntity.lanterna(l, pl.corps, false);
            if (f != null) {
                f.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 90, 0);
                f.setNoAi(true);
                f.setInvulnerable(true);
                f.setPose(Pose.SLEEPING);
                f.addTag(CAIDO);
                f.addTag(CAIDO + ":" + i);
                f.setPersistenceRequired();
                f.setCustomName(Component.translatable("necron.lanterna.ferido", Component.translatable("corps.lanterna." + t)).withStyle(st -> st.withColor(pl.corps.color())));
                l.addFreshEntity(f);
            }
            marcar(s, k);
            marcar(s, k + ":y" + c.getY());
        }
        BlockPos c = centro(l, pl, i);
        List<BlockPos> m = monte(c);
        int resta = 0;
        for (BlockPos q : m) if (!l.getBlockState(q).isAir()) resta++;
        int tirado = (m.size() - resta) * 100 / Math.max(1, m.size() * 4 / 5);
        if (p.tickCount % 20 == 3) feixe(l, p, c, pl.corps.color());
        // o ferido fica deitado (a pose não fica salva no mundo)
        for (LanternaEntity f : l.getEntitiesOfClass(LanternaEntity.class, new AABB(c).inflate(4), x -> x.getTags().contains(CAIDO + ":" + i)))
            if (f.getPose() != Pose.SLEEPING) f.setPose(Pose.SLEEPING);
        if (dist < 9) p.displayClientMessage(Component.translatable("necron.lanterna.escombros", Math.min(100, tirado)).withStyle(ChatFormatting.GRAY), true);
        if (tirado >= 45 && !feito(s, k + ":eco")) {
            marcar(s, k + ":eco");
            for (int g = 0; g < 3; g++) {
                Mob sv = Cratera.novoServo(l);
                if (sv == null) continue;
                double a = Math.PI * 2 * g / 3 + 0.5;
                BlockPos sp = chao(l, c.offset((int) (Math.cos(a) * 7), 0, (int) (Math.sin(a) * 7)));
                sv.moveTo(sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, 0, 0);
                sv.addTag(Cratera.SERVO);
                sv.setPersistenceRequired();
                l.addFreshEntity(sv);
                l.sendParticles(ParticleTypes.SOUL, sv.getX(), sv.getY() + 0.5, sv.getZ(), 20, 0.4, 0.8, 0.4, 0.03);
            }
            p.sendSystemMessage(Component.translatable("necron.lanterna.escombros_eco").withStyle(ChatFormatting.DARK_PURPLE));
        }
        if (tirado >= 100) { libertar(l, p, pl, i, c); return true; }
        return false;
    }

    private static final Map<String, Integer> ALTURAS = new HashMap<>();

    public static BlockPos centro(ServerLevel l, PlanetLore pl, int i) {
        BlockPos b = ponto(i);
        String pre = chave(pl, "monte:" + i) + ":y";
        Integer c = ALTURAS.get(pre);
        if (c != null) return new BlockPos(b.getX(), c, b.getZ());
        // a altura salva quando o monte foi feito
        for (String k : LoreWorldData.get(l.getServer()).sites())
            if (k.startsWith(pre)) try {
                int y = Integer.parseInt(k.substring(pre.length()));
                ALTURAS.put(pre, y);
                return new BlockPos(b.getX(), y, b.getZ());
            } catch (NumberFormatException ignored) {}
        return chao(l, b);
    }

    private static void feixe(ServerLevel l, ServerPlayer p, BlockPos b, int cor) {
        DustParticleOptions d = new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 2.5f);
        for (int k = 0; k < 18; k++) l.sendParticles(p, d, true, b.getX() + 0.5, b.getY() + 4 + k * 2.5, b.getZ() + 0.5, 1, 0.1, 0.4, 0.1, 0);
    }

    private static final List<Object[]> VOOS = new ArrayList<>();

    private static void libertar(ServerLevel l, ServerPlayer p, PlanetLore pl, int i, BlockPos c) {
        String t = pl.corps.id().getPath();
        marcar(l.getServer(), chave(pl, "monte:" + i) + ":livre");
        for (BlockPos q : monte(c)) if (!l.getBlockState(q).isAir()) l.destroyBlock(q, false);
        for (LanternaEntity f : l.getEntitiesOfClass(LanternaEntity.class, new AABB(c).inflate(5), x -> x.getTags().contains(CAIDO + ":" + i))) {
            f.setPose(Pose.STANDING);
            f.setNoGravity(true);
            f.removeTag(CAIDO);
            l.sendParticles(ParticleTypes.END_ROD, f.getX(), f.getY() + 1, f.getZ(), 40, 0.5, 1, 0.5, 0.1);
            VOOS.add(new Object[]{l.dimension(), f.getUUID(), 0});
        }
        l.playSound(null, c, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.3f);
        for (ServerPlayer q : l.players()) {
            if (q != p && q.distanceToSqr(c.getX(), c.getY(), c.getZ()) > 120 * 120) continue;
            FonteDados.chamado(q, "necron:caido_" + t + "_" + i, 1);
            q.sendSystemMessage(Component.translatable("necron.lanterna.caido_fala_" + i).withStyle(st -> st.withColor(pl.corps.color())));
        }
    }

    // ===================== o Carcereiro =====================

    private static final Map<UUID, ServerBossEvent> BARRAS = new HashMap<>();

    private static void carcereiro(ServerLevel l, ServerPlayer p, PlanetLore pl) {
        MinecraftServer s = p.server;
        String t = pl.corps.id().getPath(), g = chave(pl, "carcereiro");
        if (feito(s, g + ":fim")) {
            if (FonteDados.chamado(p, "necron:guardiao_" + t) < 1) FonteDados.chamado(p, "necron:guardiao_" + t, 1);
            nucleoPerdido(l, p, pl);
            return;
        }
        BlockPos h = PlanetLore.HUB;
        double dx = p.getX() - h.getX(), dz = p.getZ() - h.getZ();
        if (dx * dx + dz * dz > 70 * 70 || p.tickCount % 40 != 3) return;
        if (!l.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(h.getX() >> 4, h.getZ() >> 4))) return;
        if (!l.getEntitiesOfClass(LanternaEntity.class, new AABB(h).inflate(160, 250, 160), x -> x.getTags().contains(CARC)).isEmpty()) return;
        LanternaEntity c = LanternaEntity.lanterna(l, BuiltinCorps.BLACK, true);
        if (c == null) return;
        BlockPos onde = chao(l, h.offset(14, 0, 0));
        c.moveTo(onde.getX() + 0.5, onde.getY(), onde.getZ() + 0.5, 90, 0);
        c.addTag(Cratera.SERVO);
        c.addTag(CARC);
        c.addTag(CARC + ":" + pl.planet);
        c.setPersistenceRequired();
        c.restrictTo(h, 40);
        c.setCustomName(nome(t));
        c.setCustomNameVisible(true);
        var hp = c.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(VIDA_CARC);
        var ar = c.getAttribute(Attributes.ARMOR);
        if (ar != null) ar.setBaseValue(10);
        var at = c.getAttribute(Attributes.ATTACK_DAMAGE);
        if (at != null) at.setBaseValue(10);
        c.setHealth(c.getMaxHealth());
        c.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        l.addFreshEntity(c);
        l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.getX(), c.getY() + 1, c.getZ(), 120, 0.6, 2.5, 0.6, 0.05);
        l.sendParticles(ParticleTypes.SCULK_SOUL, c.getX(), c.getY() + 1, c.getZ(), 40, 2, 1, 2, 0.02);
        l.playSound(null, c.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8f, 0.7f);
        if (!feito(s, g)) {
            marcar(s, g);
            for (ServerPlayer q : l.players())
                if (q.distanceToSqr(c) < 150 * 150) q.sendSystemMessage(Component.translatable("necron.lanterna.carcereiro_fala_1").withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    private static Component nome(String tropa) {
        return Component.translatable("necron.lanterna.carcereiro", Component.translatable("necron.lanterna.carcereiro." + tropa)).withStyle(ChatFormatting.DARK_PURPLE);
    }

    /** O Carcereiro luta: barra de chefe, onda de almas, chama servos, fica mais rápido na metade da vida. */
    @SubscribeEvent
    public static void tickServidor(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
        int tc = s.getTickCount();
        // os feridos soltos voam até a bateria
        for (Iterator<Object[]> it = VOOS.iterator(); it.hasNext(); ) {
            Object[] o = it.next();
            @SuppressWarnings("unchecked") ServerLevel l = s.getLevel((ResourceKey<Level>) o[0]);
            Entity f = l == null ? null : l.getEntity((UUID) o[1]);
            int n = (int) o[2] + 1;
            o[2] = n;
            if (f == null) { it.remove(); continue; }
            Vec3 alvo = Vec3.atCenterOf(PlanetLore.HUB).with(net.minecraft.core.Direction.Axis.Y, f.getY());
            Vec3 dir = alvo.subtract(f.position());
            Vec3 passo = dir.lengthSqr() > 1 ? dir.normalize().scale(0.5) : Vec3.ZERO;
            f.setPos(f.getX() + passo.x, f.getY() + (n < 30 ? 0.35 : 0.08), f.getZ() + passo.z);
            if (l != null && n % 2 == 0) l.sendParticles(ParticleTypes.END_ROD, f.getX(), f.getY() + 1, f.getZ(), 2, 0.3, 0.3, 0.3, 0.01);
            if (n >= 80) { f.discard(); it.remove(); }
        }
        if (tc % 10 != 0) return;
        for (PlanetLore pl : PlanetLore.values()) {
            if (!Queda.caida(s, pl) || !feito(s, chave(pl, "carcereiro")) || feito(s, chave(pl, "carcereiro") + ":fim")) continue;
            var corpo = com.lanterna.space.SpaceBodies.byId(pl.planet);
            ServerLevel l = corpo == null || corpo.dimension() == null ? null : s.getLevel(corpo.dimension());
            if (l == null || l.players().isEmpty()) continue;
            for (LanternaEntity c : l.getEntitiesOfClass(LanternaEntity.class, new AABB(PlanetLore.HUB).inflate(160, 250, 160), x -> x.getTags().contains(CARC))) lutar(l, c, pl, tc);
        }
    }

    private static void lutar(ServerLevel l, LanternaEntity c, PlanetLore pl, int tc) {
        ServerBossEvent barra = BARRAS.computeIfAbsent(c.getUUID(), u -> new ServerBossEvent(nome(pl.corps.id().getPath()), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10));
        barra.setProgress(c.getHealth() / c.getMaxHealth());
        for (ServerPlayer q : l.players()) {
            boolean perto = q.distanceToSqr(c) < 64 * 64;
            if (perto && !barra.getPlayers().contains(q)) barra.addPlayer(q);
            else if (!perto && barra.getPlayers().contains(q)) barra.removePlayer(q);
        }
        List<ServerPlayer> alvos = l.getEntitiesOfClass(ServerPlayer.class, c.getBoundingBox().inflate(24), q -> !q.isSpectator() && !q.isCreative());
        // metade da vida: fica mais rápido e fala
        if (c.getHealth() < c.getMaxHealth() / 2 && !c.getTags().contains("lanterna_carc_metade")) {
            c.addTag("lanterna_carc_metade");
            c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 1, false, false));
            for (ServerPlayer q : l.players()) if (q.distanceToSqr(c) < 80 * 80) q.sendSystemMessage(Component.translatable("necron.lanterna.carcereiro_fala_2").withStyle(ChatFormatting.DARK_PURPLE));
        }
        int ciclo = c.getTags().contains("lanterna_carc_metade") ? 70 : 100;
        // a onda de almas: aviso (as almas giram em volta) e, meio segundo depois, o golpe
        int fase = tc % ciclo;
        if (fase == ciclo - 20) {
            for (int k = 0; k < 24; k++) {
                double a = Math.PI * 2 * k / 24;
                l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.getX() + Math.cos(a) * 2, c.getY() + 0.3, c.getZ() + Math.sin(a) * 2, 1, 0, 0.2, 0, 0.01);
            }
            l.playSound(null, c.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.5f, 0.6f);
        }
        if (fase == 0) {
            for (int k = 0; k < 48; k++) {
                double a = Math.PI * 2 * k / 48;
                for (int r = 2; r <= 7; r += 2)
                    l.sendParticles(ParticleTypes.SOUL, c.getX() + Math.cos(a) * r, c.getY() + 0.4, c.getZ() + Math.sin(a) * r, 1, 0, 0.1, 0, 0.02);
            }
            l.playSound(null, c.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.9f, 0.8f);
            for (ServerPlayer q : alvos) {
                if (q.distanceTo(c) > 7) continue;
                q.hurt(l.damageSources().indirectMagic(c, c), 8);
                Vec3 v = q.position().subtract(c.position()).multiply(1, 0, 1).normalize().scale(1.4);
                q.setDeltaMovement(v.x, 0.55, v.z);
                q.hurtMarked = true;
            }
        }
        // chama servos (no máximo 5 em volta)
        if (tc % 240 == 120 && !alvos.isEmpty()
                && l.getEntitiesOfClass(Mob.class, c.getBoundingBox().inflate(30), x -> Cratera.servo(x) && !x.getTags().contains(CARC)).size() < 5) {
            for (int g = 0; g < 2; g++) {
                Mob sv = Cratera.novoServo(l);
                if (sv == null) continue;
                double a = l.random.nextDouble() * Math.PI * 2;
                BlockPos sp = chao(l, c.blockPosition().offset((int) (Math.cos(a) * 5), 0, (int) (Math.sin(a) * 5)));
                sv.moveTo(sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, 0, 0);
                sv.addTag(Cratera.SERVO);
                sv.setPersistenceRequired();
                l.addFreshEntity(sv);
                l.sendParticles(ParticleTypes.SOUL, sv.getX(), sv.getY() + 0.5, sv.getZ(), 20, 0.4, 0.8, 0.4, 0.03);
            }
        }
    }

    @SubscribeEvent
    public static void morreu(LivingDeathEvent e) {
        var c = e.getEntity();
        if (!c.getTags().contains(CARC) || !(c.level() instanceof ServerLevel l)) return;
        ServerBossEvent b = BARRAS.remove(c.getUUID());
        if (b != null) b.removeAllPlayers();
        PlanetLore pl = PlanetLore.byDimension(l.dimension());
        if (pl == null) return;
        String t = pl.corps.id().getPath();
        marcar(l.getServer(), chave(pl, "carcereiro") + ":fim");
        soltarNucleo(l, c.position(), t);
        l.sendParticles(ParticleTypes.SOUL, c.getX(), c.getY() + 1, c.getZ(), 80, 0.8, 1.5, 0.8, 0.08);
        l.playSound(null, c.blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 0.7f, 1.2f);
        for (ServerPlayer q : l.players()) {
            if (q.distanceToSqr(c) > 120 * 120) continue;
            FonteDados.chamado(q, "necron:guardiao_" + t, 1);
            q.sendSystemMessage(Component.translatable("necron.lanterna.carcereiro_fala_3").withStyle(ChatFormatting.DARK_PURPLE));
            q.sendSystemMessage(Component.translatable("necron.lanterna.nucleo_caiu", Component.translatable("necron.lanterna.tropa." + t)).withStyle(ChatFormatting.GOLD));
        }
    }

    // ===================== o núcleo =====================

    public static ItemStack nucleo(String tropa) {
        ItemStack s = new ItemStack(LoreItems.NUCLEO_BATERIA.get());
        s.getOrCreateTag().putString(NUCLEO_TAG, tropa);
        s.setHoverName(Component.translatable("item.lanterna.nucleo_bateria_de", Component.translatable("necron.lanterna.tropa." + tropa))
                .withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GOLD)));
        return s;
    }

    private static void soltarNucleo(ServerLevel l, Vec3 onde, String tropa) {
        ItemEntity it = new ItemEntity(l, onde.x, onde.y + 0.5, onde.z, nucleo(tropa));
        it.setGlowingTag(true);
        it.setUnlimitedLifetime();
        l.addFreshEntity(it);
    }

    private static boolean temNucleo(ServerPlayer p, String tropa) {
        for (ItemStack s : p.getInventory().items) if (s.is(LoreItems.NUCLEO_BATERIA.get()) && tropa.equals(s.getOrCreateTag().getString(NUCLEO_TAG))) return true;
        return false;
    }

    /** O núcleo sumiu (o jogador morreu longe, jogou fora...): enquanto não for guardado, ele volta para a bateria caída. */
    private static void nucleoPerdido(ServerLevel l, ServerPlayer p, PlanetLore pl) {
        String t = pl.corps.id().getPath();
        if (nucleoGuardado(p.server, t) || p.tickCount % 100 != 3 || temNucleo(p, t)) return;
        BlockPos h = PlanetLore.HUB;
        double dx = p.getX() - h.getX(), dz = p.getZ() - h.getZ();
        if (dx * dx + dz * dz > 30 * 30) return;
        if (!l.getEntitiesOfClass(ItemEntity.class, new AABB(h).inflate(80, 200, 80), x -> x.getItem().is(LoreItems.NUCLEO_BATERIA.get())).isEmpty()) return;
        BlockPos c = chao(l, h.offset(6, 0, 6));
        soltarNucleo(l, Vec3.atBottomCenterOf(c), t);
        l.sendParticles(ParticleTypes.END_ROD, c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5, 30, 0.3, 0.8, 0.3, 0.05);
    }

    /** Leva o núcleo à Bateria Central da própria Tropa (e dá o crédito de núcleos já guardados por outros). */
    private static void entregar(ServerPlayer p) {
        LoreData d = MissionManager.data(p);
        if (d == null) return;
        for (String t : Emocoes.ASPECTOS) {
            if (!d.isActive("necron_q_" + t)) continue;
            if (nucleoGuardado(p.server, t) && FonteDados.chamado(p, "necron:nucleo_" + t) < 1) FonteDados.chamado(p, "necron:nucleo_" + t, 1);
        }
        String propria = SagaNecron.tag(p).getString("tropa");
        PlanetLore aqui = PlanetLore.byDimension(p.level().dimension());
        if (aqui == null || !aqui.corps.id().getPath().equals(propria)) return;
        BlockPos h = PlanetLore.HUB;
        double dx = p.getX() - h.getX(), dz = p.getZ() - h.getZ();
        if (dx * dx + dz * dz > 28 * 28) return;
        for (ItemStack s : p.getInventory().items) {
            if (!s.is(LoreItems.NUCLEO_BATERIA.get())) continue;
            String t = s.getOrCreateTag().getString(NUCLEO_TAG);
            s.setCount(0);
            if (t.isEmpty()) continue;
            marcar(p.server, "saga:nucleo:" + t);
            FonteDados.chamado(p, "necron:nucleo_" + t, 1);
            p.sendSystemMessage(Component.translatable("necron.lanterna.nucleo_guardado", Component.translatable("necron.lanterna.tropa." + t)).withStyle(ChatFormatting.GOLD));
            ServerLevel l = p.serverLevel();
            l.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 60, 0.6, 1.5, 0.6, 0.1);
            l.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2f, 1.1f);
        }
    }

    /** Teste: zera as ruínas deste planeta. */
    public static void zerar(MinecraftServer s, PlanetLore p) {
        LoreWorldData w = LoreWorldData.get(s);
        for (int i = 1; i <= 3; i++) {
            for (String suf : new String[]{"", ":livre", ":eco"}) w.resetSite(chave(p, "monte:" + i) + suf);
            String pre = chave(p, "monte:" + i) + ":y";
            for (String k : new ArrayList<>(w.sites())) if (k.startsWith(pre)) w.resetSite(k);
        }
        ALTURAS.clear();
        w.resetSite(chave(p, "carcereiro"));
        w.resetSite(chave(p, "carcereiro") + ":fim");
        w.resetSite("saga:nucleo:" + p.corps.id().getPath());
    }

    private Ruinas() {}
}
