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
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.*;

/**
 * 0.56.5 — Capítulo 5, Os Pedidos: antes de cada defesa de outra Tropa, uma missão no planeta dela (necron_s_<tropa>):
 *
 *  PRISIONEIROS  três Lanternas da Tropa presos em jaulas da Morte, cada uma com 3 servos de guarda. Derrotados os
 *                guardas, quebrar a trava (a obsidiana chorona no alto da jaula) solta o Lanterna, que voa para a Bateria.
 *  SOBREVIVENTES três civis presos em postos fora da cidade, cercados. Clicar e ficar perto 3 segundos: a bolha de luz
 *                leva o civil voando para a cidade.
 *  LADRÕES       quatro Ladrões da Luz (servos mais fortes) fugiram com pedaços da Bateria Central. Derrotar cada um
 *                (ele larga um Fragmento da Bateria Central) e juntar os 4 fragmentos.
 *
 * Qual missão cada Tropa pede está em {@link #tipo}. As jaulas, os postos e os ladrões são do mundo (do planeta).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Socorro {
    public enum Tipo { PRISIONEIROS, SOBREVIVENTES, LADROES }

    public static final String PRESO = "lanterna_preso_jaula", GUARDA = "lanterna_guarda_jaula", CIVIL = "lanterna_civil_posto",
            CERCO = "lanterna_cerco_posto", LADRAO = "lanterna_ladrao_luz";
    public static final int FRAGMENTOS = 4;
    private static final String[] CIVIS = {"Orin", "Tessa", "Kalu"};

    public static Tipo tipo(String tropa) {
        return switch (tropa) {
            case "yellow", "indigo" -> Tipo.LADROES;
            case "orange", "blue" -> Tipo.SOBREVIVENTES;
            default -> Tipo.PRISIONEIROS;
        };
    }

    /** Onde fica o ponto i (jaula, posto ou esconderijo do ladrão) em volta da cidade. */
    public static BlockPos ponto(int i) {
        BlockPos h = PlanetLore.HUB;
        double a = Math.toRadians(70 + 95 * (i - 1));
        return new BlockPos(h.getX() + (int) (Math.cos(a) * 95), 0, h.getZ() + (int) (Math.sin(a) * 95));
    }

    private static BlockPos chao(ServerLevel l, BlockPos p) {
        return new BlockPos(p.getX(), l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()), p.getZ());
    }

    private static String chave(PlanetLore p, String o, int i) { return "saga:socorro:" + p.planet + ":" + o + ":" + i; }

    private static boolean feito(ServerLevel l, String k) { return LoreWorldData.get(l.getServer()).siteBuilt(k); }
    private static void marcar(ServerLevel l, String k) { LoreWorldData.get(l.getServer()).markSite(k); }

    private static BuiltinCorps corps(PlanetLore p) { return p.corps; }

    // ===================== tick =====================

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        PlanetLore aqui = PlanetLore.byDimension(p.level().dimension());
        if (aqui == null) return;
        String t = aqui.corps.id().getPath();
        ServerLevel l = p.serverLevel();
        if (tipo(t) == Tipo.SOBREVIVENTES) resgates(l, p, aqui);
        LoreData d = MissionManager.data(p);
        if (d == null || !d.isActive("necron_s_" + t)) return;
        switch (tipo(t)) {
            case PRISIONEIROS -> { if (p.tickCount % 10 == 1) jaulas(l, p, aqui); }
            case SOBREVIVENTES -> postos(l, p, aqui);
            case LADROES -> { if (p.tickCount % 20 == 1) ladroes(l, p, aqui); }
        }
    }

    private static void feixe(ServerLevel l, ServerPlayer p, BlockPos b, int cor) {
        DustParticleOptions d = new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 2.5f);
        for (int k = 0; k < 18; k++) l.sendParticles(p, d, true, b.getX() + 0.5, b.getY() + 3 + k * 2.5, b.getZ() + 0.5, 1, 0.1, 0.4, 0.1, 0);
    }

    // ===================== as jaulas =====================

    @Nullable
    private static BlockPos trava(ServerLevel l, int i) {
        BlockPos b = ponto(i);
        if (!l.isLoaded(b)) return null;
        return chao(l, b.offset(3, 0, 3)).atY(l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, b.getX() + 3, b.getZ() + 3) + 4).offset(-3, 0, -3);
    }

    private static void jaulas(ServerLevel l, ServerPlayer p, PlanetLore pl) {
        String t = pl.corps.id().getPath();
        for (int i = 1; i <= 3; i++) {
            String k = chave(pl, "jaula", i);
            if (feito(l, k + ":solto")) { if (FonteDados.chamado(p, "necron:preso_" + t + "_" + i) < 1) FonteDados.chamado(p, "necron:preso_" + t + "_" + i, 1); continue; }
            BlockPos b = ponto(i);
            double dx = p.getX() - b.getX(), dz = p.getZ() - b.getZ(), dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 200) continue;
            BlockPos tr = trava(l, i);
            if (tr == null) continue;
            if (p.tickCount % 20 == 1) feixe(l, p, tr, pl.corps.color());
            if (!feito(l, k)) {
                construirJaula(l, tr);
                marcar(l, k);
            }
            if (dist < 70 && !feito(l, k + ":guardas") && l.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(tr.getX() >> 4, tr.getZ() >> 4))) {
                // o Lanterna preso e os guardas (uma vez)
                LanternaEntity preso = LanternaEntity.lanterna(l, pl.corps, false);
                if (preso != null) {
                    preso.moveTo(tr.getX() + 0.5, tr.getY() - 3, tr.getZ() + 0.5, l.random.nextFloat() * 360, 0);
                    preso.setNoAi(true);
                    preso.setInvulnerable(true);
                    preso.addTag(PRESO);
                    preso.addTag(PRESO + ":" + i);
                    preso.setPersistenceRequired();
                    preso.setCustomName(Component.translatable("necron.lanterna.preso", Component.translatable("corps.lanterna." + t)).withStyle(s -> s.withColor(pl.corps.color())));
                    l.addFreshEntity(preso);
                }
                for (int g = 0; g < 3; g++) {
                    Mob m = Cratera.novoServo(l);
                    if (m == null) continue;
                    double a = Math.PI * 2 * g / 3;
                    BlockPos s = chao(l, tr.offset((int) (Math.cos(a) * 6), 0, (int) (Math.sin(a) * 6)));
                    m.moveTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, 0, 0);
                    m.addTag(Cratera.SERVO);
                    m.addTag(GUARDA + ":" + pl.planet + ":" + i);
                    m.setPersistenceRequired();
                    l.addFreshEntity(m);
                }
                marcar(l, k + ":guardas");
            }
        }
    }

    /** Uma jaula 3x3 de barras de ferro e pedra negra, com a trava de obsidiana chorona no alto. */
    private static void construirJaula(ServerLevel l, BlockPos tr) {
        BlockPos base = tr.below(4);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                l.setBlock(base.offset(dx, 0, dz), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
                for (int y = 1; y <= 3; y++) {
                    boolean borda = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    boolean canto = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                    l.setBlock(base.offset(dx, y, dz), canto ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
                            : borda ? Blocks.IRON_BARS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                }
                l.setBlock(base.offset(dx, 4, dz), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
            }
        l.setBlock(tr, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
        l.setBlock(tr.above(), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
    }

    @SubscribeEvent
    public static void quebrar(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel l)) return;
        PlanetLore pl = PlanetLore.byDimension(l.dimension());
        if (pl == null || tipo(pl.corps.id().getPath()) != Tipo.PRISIONEIROS) return;
        for (int i = 1; i <= 3; i++) {
            String k = chave(pl, "jaula", i);
            if (!feito(l, k) || feito(l, k + ":solto")) continue;
            BlockPos tr = trava(l, i);
            if (tr == null) continue;
            BlockPos base = tr.below(4);
            BlockPos q = e.getPos();
            boolean daJaula = Math.abs(q.getX() - base.getX()) <= 2 && Math.abs(q.getZ() - base.getZ()) <= 2 && q.getY() >= base.getY() && q.getY() <= tr.getY();
            if (!daJaula) continue;
            if (!q.equals(tr)) { e.setCanceled(true); p.displayClientMessage(Component.translatable("necron.lanterna.jaula_trava").withStyle(ChatFormatting.DARK_PURPLE), true); return; }
            String gtag = GUARDA + ":" + pl.planet + ":" + i;
            int vivos = l.getEntitiesOfClass(Mob.class, new AABB(tr).inflate(60), m -> m.getTags().contains(gtag)).size();
            if (vivos > 0) {
                e.setCanceled(true);
                p.displayClientMessage(Component.translatable("necron.lanterna.ancora_protegida", vivos).withStyle(ChatFormatting.DARK_PURPLE), true);
                return;
            }
            soltar(l, p, pl, i, tr);
            return;
        }
    }

    private static void soltar(ServerLevel l, ServerPlayer p, PlanetLore pl, int i, BlockPos tr) {
        marcar(l, chave(pl, "jaula", i) + ":solto");
        BlockPos base = tr.below(4);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int y = 1; y <= 4; y++) l.setBlock(base.offset(dx, y, dz), Blocks.AIR.defaultBlockState(), 3);
        l.setBlock(tr.above(), Blocks.AIR.defaultBlockState(), 3);
        String t = pl.corps.id().getPath();
        for (LanternaEntity preso : l.getEntitiesOfClass(LanternaEntity.class, new AABB(tr).inflate(10), x -> x.getTags().contains(PRESO + ":" + i))) {
            preso.removeTag(PRESO);
            preso.setNoAi(false);
            preso.setInvulnerable(false);
            l.sendParticles(ParticleTypes.END_ROD, preso.getX(), preso.getY() + 1, preso.getZ(), 40, 0.5, 1, 0.5, 0.1);
        }
        l.playSound(null, tr, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.5f, 0.6f);
        l.playSound(null, tr, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 1.4f);
        for (ServerPlayer q : l.players()) {
            if (q.distanceToSqr(tr.getX(), tr.getY(), tr.getZ()) > 120 * 120 && q != p) continue;
            FonteDados.chamado(q, "necron:preso_" + t + "_" + i, 1);
            q.sendSystemMessage(Component.translatable("necron.lanterna.preso_fala").withStyle(s -> s.withColor(pl.corps.color())));
        }
    }

    // ===================== os postos (sobreviventes) =====================

    private static final class Bolha { UUID civil; int t; int voo = -1; Vec3 de; }
    private static final Map<UUID, Bolha> BOLHAS = new HashMap<>();
    private static final List<Object[]> VOANDO = new ArrayList<>();

    private static void postos(ServerLevel l, ServerPlayer p, PlanetLore pl) {
        String t = pl.corps.id().getPath();
        if (p.tickCount % 20 == 2) for (int i = 1; i <= 3; i++) {
            String k = chave(pl, "posto", i);
            if (feito(l, k + ":salvo")) { if (FonteDados.chamado(p, "necron:sobrev_" + t + "_" + i) < 1) FonteDados.chamado(p, "necron:sobrev_" + t + "_" + i, 1); continue; }
            BlockPos b = ponto(i);
            double dx = p.getX() - b.getX(), dz = p.getZ() - b.getZ();
            if (dx * dx + dz * dz > 200 * 200 || !l.isLoaded(b)) continue;
            BlockPos ch = chao(l, b);
            feixe(l, p, ch, 0xFFE070);
            if (feito(l, k) || !l.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(b.getX() >> 4, b.getZ() >> 4))) continue;
            // um morador do planeta (os aldeões comuns são recolhidos pelas cidades dos planetas)
            LanternaEntity v = LanternaEntity.mercador(l);
            if (v == null) continue;
            v.moveTo(ch.getX() + 0.5, ch.getY(), ch.getZ() + 0.5, 0, 0);
            v.setNoAi(true);
            v.setInvulnerable(true);
            v.setPersistenceRequired();
            v.setCustomName(Component.literal(CIVIS[i - 1]).withStyle(ChatFormatting.YELLOW));
            v.setCustomNameVisible(true);
            v.addTag(CIVIL);
            v.addTag(CIVIL + ":" + pl.planet + ":" + i);
            l.addFreshEntity(v);
            for (int g = 0; g < 3; g++) {
                Mob m = Cratera.novoServo(l);
                if (m == null) continue;
                double a = Math.PI * 2 * g / 3;
                BlockPos s = chao(l, ch.offset((int) (Math.cos(a) * 5), 0, (int) (Math.sin(a) * 5)));
                m.moveTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, 0, 0);
                m.addTag(Cratera.SERVO);
                m.addTag(CERCO);
                m.setPersistenceRequired();
                l.addFreshEntity(m);
            }
            marcar(l, k);
        }
    }

    /**
     * 0.57.2: o resgate (a bolha de luz e o voo até a cidade) roda sozinho, fora da condição da missão, e é tolerante:
     * conta a distância na horizontal (até 6 blocos, com até 4 de altura), para quem paira com o anel perto do
     * morador; e basta ficar ao lado dele um instante para a luz começar (o clique continua valendo).
     */
    private static void resgates(ServerLevel l, ServerPlayer p, PlanetLore pl) {
        String t = pl.corps.id().getPath();
        if (p.tickCount % 10 == 4 && !BOLHAS.containsKey(p.getUUID()) && !p.isSpectator()) {
            Mob perto = l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(2.5, 3, 2.5),
                    m -> m.getTags().contains(CIVIL) && !m.isNoGravity()).stream().findFirst().orElse(null);
            if (perto != null) comecar(p, perto);
        }
        // envolvendo em luz
        Bolha bo = BOLHAS.get(p.getUUID());
        if (bo != null) {
            Entity ent = l.getEntity(bo.civil);
            if (!(ent instanceof Mob v)) { BOLHAS.remove(p.getUUID()); }
            else if (longe(p, v)) {
                BOLHAS.remove(p.getUUID());
                p.displayClientMessage(Component.translatable("necron.lanterna.resgate_soltou").withStyle(ChatFormatting.RED), true);
            } else {
                bo.t++;
                if (bo.t % 5 == 0) p.displayClientMessage(Component.translatable("necron.lanterna.resgate_progresso", bo.t * 100 / 60).withStyle(ChatFormatting.GREEN), true);
                l.sendParticles(ParticleTypes.END_ROD, v.getX(), v.getY() + 1, v.getZ(), 3, 0.6, 0.6, 0.6, 0.01);
                if (bo.t >= 60) {
                    BOLHAS.remove(p.getUUID());
                    bo.de = v.position();
                    bo.voo = 0;
                    v.setNoGravity(true);
                    VOANDO.add(new Object[]{p.getUUID(), bo, pl});
                    l.playSound(null, v.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.5f);
                }
            }
        }
        for (Iterator<Object[]> it = VOANDO.iterator(); it.hasNext(); ) {
            Object[] o = it.next();
            if (!p.getUUID().equals(o[0])) continue;
            Bolha b = (Bolha) o[1];
            Entity ent = l.getEntity(b.civil);
            if (!(ent instanceof Mob v)) { it.remove(); continue; }
            b.voo++;
            double k = Math.min(1, b.voo / 70.0);
            Vec3 alvo = Vec3.atBottomCenterOf(chao(l, PlanetLore.HUB.offset(4, 0, 8))).add(0, 1, 0);
            Vec3 pos = b.de.lerp(alvo, k * k).add(0, Math.sin(k * Math.PI) * 25, 0);
            v.moveTo(pos.x, pos.y, pos.z, v.getYRot(), 0);
            l.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1, pos.z, 4, 0.5, 0.5, 0.5, 0.01);
            if (b.voo >= 70) {
                it.remove();
                int i = 0;
                for (String tg : v.getTags()) if (tg.startsWith(CIVIL + ":")) i = Integer.parseInt(tg.substring(tg.lastIndexOf(':') + 1));
                v.discard();
                if (i > 0) {
                    marcar(l, chave(pl, "posto", i) + ":salvo");
                    FonteDados.chamado(p, "necron:sobrev_" + t + "_" + i, 1);
                    p.sendSystemMessage(Component.translatable("necron.lanterna.sobrev_ok", CIVIS[i - 1]).withStyle(ChatFormatting.GREEN));
                    // o cerco daquele posto se desfaz
                    for (Mob m : l.getEntitiesOfClass(Mob.class, new AABB(ponto(i)).inflate(30, 200, 30), m -> m.getTags().contains(CERCO))) m.discard();
                }
            }
        }
    }

    private static boolean longe(ServerPlayer p, Mob v) {
        double dx = p.getX() - v.getX(), dz = p.getZ() - v.getZ();
        return dx * dx + dz * dz > 6 * 6 || Math.abs(p.getY() - v.getY()) > 4;
    }

    private static void comecar(ServerPlayer p, Mob v) {
        Bolha b = new Bolha();
        b.civil = v.getUUID();
        BOLHAS.put(p.getUUID(), b);
        p.displayClientMessage(Component.translatable("necron.lanterna.resgate_comeca").withStyle(ChatFormatting.GREEN), true);
    }

    @SubscribeEvent
    public static void clicou(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getTarget() instanceof Mob v) || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!v.getTags().contains(CIVIL) || v.isNoGravity()) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (BOLHAS.containsKey(p.getUUID())) return;
        comecar(p, v);
    }

    // ===================== os ladrões =====================

    private static void ladroes(ServerLevel l, ServerPlayer p, PlanetLore pl) {
        int tem = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(LoreItems.FRAGMENTO_BATERIA.get())) tem += s.getCount();
        if (tem >= FRAGMENTOS) return;
        int vivos = 0;
        List<LanternaEntity> lista = l.getEntitiesOfClass(LanternaEntity.class, p.getBoundingBox().inflate(300, 200, 300), x -> x.getTags().contains(LADRAO));
        vivos = lista.size();
        // os ladrões aparecem nos esconderijos (e voltam enquanto faltar fragmento a recuperar, um de cada vez)
        long caidos = l.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(300, 200, 300), x -> x.getItem().is(LoreItems.FRAGMENTO_BATERIA.get())).size();
        int faltam = FRAGMENTOS - tem - (int) caidos - vivos;
        for (int i = 1; i <= FRAGMENTOS && faltam > 0; i++) {
            BlockPos b = i <= 3 ? ponto(i) : ponto(1).offset(30, 0, -40);
            double dx = p.getX() - b.getX(), dz = p.getZ() - b.getZ();
            if (dx * dx + dz * dz > 180 * 180 || !l.isLoaded(b) || !l.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(b.getX() >> 4, b.getZ() >> 4))) continue;
            LanternaEntity la = LanternaEntity.lanterna(l, BuiltinCorps.BLACK, true);
            if (la == null) continue;
            BlockPos ch = chao(l, b);
            la.moveTo(ch.getX() + 0.5, ch.getY(), ch.getZ() + 0.5, 0, 0);
            la.addTag(Cratera.SERVO);
            la.addTag(LADRAO);
            la.setPersistenceRequired();
            la.setCustomName(Component.translatable("necron.lanterna.ladrao").withStyle(ChatFormatting.DARK_PURPLE));
            var hp = la.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (hp != null) { hp.setBaseValue(hp.getBaseValue() * 2); la.setHealth(la.getMaxHealth()); }
            la.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
            l.addFreshEntity(la);
            faltam--;
        }
        // o brilho de cada ladrão mostra onde ele está
        for (LanternaEntity la : lista) feixe(l, p, la.blockPosition(), 0x9A50E0);
    }

    @SubscribeEvent
    public static void ladraoCaiu(LivingDeathEvent e) {
        if (!e.getEntity().getTags().contains(LADRAO) || !(e.getEntity().level() instanceof ServerLevel l)) return;
        var en = e.getEntity();
        ItemEntity it = new ItemEntity(l, en.getX(), en.getY() + 0.5, en.getZ(), new ItemStack(LoreItems.FRAGMENTO_BATERIA.get()));
        it.setGlowingTag(true);
        it.setUnlimitedLifetime();
        l.addFreshEntity(it);
        l.sendParticles(ParticleTypes.END_ROD, en.getX(), en.getY() + 1, en.getZ(), 30, 0.4, 0.6, 0.4, 0.08);
        l.playSound(null, en.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.2f, 1.2f);
    }

    /** Teste: zera o socorro deste planeta. */
    public static void zerar(net.minecraft.server.MinecraftServer s, PlanetLore p) {
        for (String o : new String[]{"jaula", "posto"}) for (int i = 1; i <= 3; i++) for (String suf : new String[]{"", ":guardas", ":solto", ":salvo"})
            LoreWorldData.get(s).resetSite(chave(p, o, i) + suf);
    }

    private Socorro() {}
}
