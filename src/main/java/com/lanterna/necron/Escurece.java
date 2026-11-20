package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.fonte.FonteDados;
import com.lanterna.lore.LoreWorldData;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.*;

/**
 * 0.56.7: Capítulo 7, A Terra Escurece. Uma missão por etapa (cada uma terminada, a escuridão avança na Terra):
 *
 *  necron_5   Fendas sobre a Terra: três fendas se abrem no céu, perto das vilas em volta de Aurora. Cada uma despeja
 *             servos; derrotados 6, ela se fecha. (etapa 1)
 *  necron_5b  falar com o Sgt. Rui: o Necron mandou um exército para Aurora.
 *  necron_5c  O Cerco de Aurora: três ondas de servos marcham para a praça; cada um que chega perto enfraquece o
 *             escudo de luz da vila. Na última onda vem um Arauto. Se o escudo cair, o cerco recomeça. (etapa 2)
 *  necron_5d  falar com a Dra. Lívia: um pulso de vida embaixo da terra morta.
 *  necron_5e  O Pulso da Vida: três lugares onde a vida resiste (flores no meio da terra morta); o anel escuta cada
 *             pulso. O último mostra a entrada do Santuário, uma caverna funda onde o Embrião da Vida dorme.
 *  necron_5f  Proteger o Embrião: os servos do Necron cavam até o Santuário; três ondas. (etapa 3 → A Última Luz)
 *
 * As fendas, o cerco, o Santuário e o Embrião são do mundo: valem para todos os jogadores.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Escurece {
    public static final String FENDA = "lanterna_fenda_terra", CERCO = "lanterna_cerco_escuro";
    public static final int SERVOS_FENDA = 6;
    private static final int[][] FENDAS = {{260, 0}, {-190, 190}, {-40, -270}};
    private static final int[][] PULSOS = {{-110, 50}, {-190, 100}, {-255, 140}};
    private static final int[] SANTUARIO = {-320, 170};
    private static final int FUNDO = 22, LEITURA = 80;

    // ===================== lugares =====================

    @Nullable
    private static BlockPos aurora() { return com.lanterna.terra.TerraSite.centro; }

    @Nullable
    public static BlockPos rel(int dx, int dz) {
        BlockPos a = aurora();
        return a == null ? null : new BlockPos(a.getX() + dx, 0, a.getZ() + dz);
    }

    @Nullable public static BlockPos fenda(int i) { return rel(FENDAS[i - 1][0], FENDAS[i - 1][1]); }
    @Nullable public static BlockPos pulso(int i) { return rel(PULSOS[i - 1][0], PULSOS[i - 1][1]); }
    @Nullable public static BlockPos santuario() { return rel(SANTUARIO[0], SANTUARIO[1]); }

    private static int chao(ServerLevel l, int x, int z) {
        l.getChunk(x >> 4, z >> 4);
        return l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private static boolean feito(MinecraftServer s, String k) { return LoreWorldData.get(s).siteBuilt(k); }
    private static void marcar(MinecraftServer s, String k) { LoreWorldData.get(s).markSite(k); }

    /** Altura salva de um lugar construído (o chão muda depois: a terra morta, os blocos quebrados...). */
    private static int altura(MinecraftServer s, String k, int padrao) {
        Integer c = ALTURAS.get(k);
        if (c != null) return c;
        String pre = k + ":y";
        for (String id : LoreWorldData.get(s).sites())
            if (id.startsWith(pre)) try {
                int v = Integer.parseInt(id.substring(pre.length()));
                ALTURAS.put(k, v);
                return v;
            } catch (NumberFormatException ignored) {}
        return padrao;
    }

    /** As alturas já achadas (a lista de marcas do mundo é grande; não vale varrer a cada tick). */
    private static final Map<String, Integer> ALTURAS = new HashMap<>();

    /** Centro da câmara do Santuário (onde o Embrião flutua), se já foi cavada. */
    @Nullable
    public static BlockPos camara(MinecraftServer s) {
        BlockPos b = santuario();
        if (b == null || !feito(s, "saga:santuario")) return null;
        return new BlockPos(b.getX(), altura(s, "saga:santuario", 40), b.getZ());
    }

    @Nullable
    public static BlockPos fendaNoCeu(ServerLevel l, int i) {
        BlockPos f = fenda(i);
        if (f == null) return null;
        return new BlockPos(f.getX(), altura(l.getServer(), "saga:terra_fenda:" + i, chao(l, f.getX(), f.getZ()) + 16), f.getZ());
    }

    public static boolean fendaFechada(MinecraftServer s, int i) { return feito(s, "saga:terra_fenda:" + i + ":fechada"); }

    private static int mortos(MinecraftServer s, int i) {
        int n = 0;
        while (n < SERVOS_FENDA && feito(s, "saga:terra_fenda:" + i + ":m" + (n + 1))) n++;
        return n;
    }

    /** As fendas da Terra e o Embrião, para o cliente desenhar ("earth1:x:y:z;...;embriao:x:y:z"). */
    public static String paraCliente(ServerPlayer p) {
        StringBuilder sb = new StringBuilder();
        LoreData d = MissionManager.data(p);
        ServerLevel o = p.server.overworld();
        if (d != null && d.isActive("necron_5"))
            for (int i = 1; i <= 3; i++) {
                if (fendaFechada(p.server, i) || !feito(p.server, "saga:terra_fenda:" + i)) continue;
                BlockPos f = fendaNoCeu(o, i);
                if (f != null) sb.append(sb.length() == 0 ? "" : ";").append("earth").append(i).append(":").append(f.getX()).append(":").append(f.getY()).append(":").append(f.getZ());
            }
        BlockPos c = camara(p.server);
        if (c != null && !feito(p.server, "saga:embriao_partiu"))
            sb.append(sb.length() == 0 ? "" : ";").append("embriao:").append(c.getX()).append(":").append(c.getY()).append(":").append(c.getZ());
        if (feito(p.server, LinhaCorrompida.PRESO)) sb.append(sb.length() == 0 ? "" : ";").append("embriaopreso:0:0:0");
        return sb.toString();
    }

    // ===================== tick do jogador =====================

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 7) return;
        LoreData d = MissionManager.data(p);
        if (d == null) return;
        if (p.tickCount % 20 == 7) etapas(p, d);
        if (p.level().dimension() != Level.OVERWORLD || aurora() == null) return;
        ServerLevel l = p.serverLevel();
        if (d.isActive("necron_5")) for (int i = 1; i <= 3; i++) fenda(l, p, i);
        if (d.isActive("necron_5c")) cerco(l, p, "aurora");
        if (d.isActive("necron_5e")) { for (int i = 1; i <= 3; i++) pulso(l, p, i); santuario(l, p); }
        if (d.isActive("necron_5f")) cerco(l, p, "embriao");
    }

    /** Terminada uma etapa, a Terra escurece mais um pouco. */
    private static void etapas(ServerPlayer p, LoreData d) {
        var t = SagaNecron.tag(p);
        int feita = t.getInt("escurece");
        int nova = d.completed().contains("necron_5f") ? 3 : d.completed().contains("necron_5c") ? 2 : d.completed().contains("necron_5") ? 1 : 0;
        if (nova <= feita) return;
        t.putInt("escurece", nova);
        TerraEscurece.avancar(p, nova);
    }

    // ===================== as fendas sobre a Terra =====================

    private static void fenda(ServerLevel l, ServerPlayer p, int i) {
        MinecraftServer s = p.server;
        String k = "saga:terra_fenda:" + i;
        if (fendaFechada(s, i)) { if (FonteDados.chamado(p, "necron:fenda_terra_" + i) < 1) FonteDados.chamado(p, "necron:fenda_terra_" + i, 1); return; }
        BlockPos f0 = fenda(i);
        if (f0 == null) return;
        double dx = p.getX() - f0.getX(), dz = p.getZ() - f0.getZ();
        if (!feito(s, k)) {
            int y = chao(l, f0.getX(), f0.getZ()) + 16;
            marcar(s, k);
            marcar(s, k + ":y" + y);
            for (ServerPlayer q : s.getPlayerList().getPlayers()) SagaNecron.mandarMundo(q);
        }
        if (dx * dx + dz * dz > 80 * 80) return;
        BlockPos f = fendaNoCeu(l, i);
        if (f == null || p.tickCount % 60 != 7) return;
        int mortos = mortos(s, i);
        String tag = FENDA + ":" + i;
        int vivos = l.getEntitiesOfClass(Mob.class, new AABB(f).inflate(70, 60, 70), m -> m.getTags().contains(tag)).size();
        if (vivos >= 4 || vivos + mortos >= SERVOS_FENDA) return;
        Mob m = Cratera.novoServo(l);
        if (m == null) return;
        double a = l.random.nextDouble() * Math.PI * 2;
        m.moveTo(f.getX() + Math.cos(a) * 3 + 0.5, f.getY() - 2, f.getZ() + Math.sin(a) * 3 + 0.5, 0, 0);
        m.addTag(Cratera.SERVO);
        m.addTag(FENDA);
        m.addTag(tag);
        m.setPersistenceRequired();
        m.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false));
        l.addFreshEntity(m);
        m.setTarget(p);
        l.sendParticles(ParticleTypes.SOUL, m.getX(), m.getY() + 0.5, m.getZ(), 20, 0.4, 0.8, 0.4, 0.03);
        if (mortos == 0 && vivos == 0 && !feito(s, k + ":aviso")) {
            marcar(s, k + ":aviso");
            p.sendSystemMessage(Component.translatable("necron.lanterna.fenda_terra_aviso").withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    // ===================== o cerco (Aurora e o Embrião) =====================

    private static final class Cerco {
        final String id;
        final BlockPos centro;
        int onda, forca = 100, pausa = 60, nascer, vazio;
        final ServerBossEvent barra;
        Cerco(String id, BlockPos centro) {
            this.id = id;
            this.centro = centro;
            barra = new ServerBossEvent(Component.empty(), id.equals("aurora") ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
        }
    }

    private static final Map<String, Cerco> CERCOS = new HashMap<>();
    private static final int ONDAS = 3;

    private static boolean cercoVencido(MinecraftServer s, String id) { return feito(s, "saga:cerco:" + id); }

    @Nullable
    private static BlockPos centroCerco(ServerLevel l, String id) {
        if (id.equals("aurora")) {
            BlockPos a = aurora();
            return a == null ? null : new BlockPos(a.getX(), chao(l, a.getX(), a.getZ()), a.getZ());
        }
        BlockPos c = camara(l.getServer());
        return c == null ? null : c.below(3);
    }

    private static void cerco(ServerLevel l, ServerPlayer p, String id) {
        String chave = id.equals("aurora") ? "necron:aurora_cerco" : "necron:embriao_salvo";
        if (cercoVencido(p.server, id)) { if (FonteDados.chamado(p, chave) < 1) FonteDados.chamado(p, chave, 1); return; }
        if (CERCOS.containsKey(id)) return;
        BlockPos c = centroCerco(l, id);
        if (c == null || p.distanceToSqr(Vec3.atCenterOf(c)) > (id.equals("aurora") ? 60 * 60 : 20 * 20)) return;
        CERCOS.put(id, new Cerco(id, c));
        for (ServerPlayer q : l.players())
            if (q.distanceToSqr(Vec3.atCenterOf(c)) < 120 * 120)
                q.sendSystemMessage(Component.translatable("necron.lanterna.cerco_" + id + "_comeca").withStyle(id.equals("aurora") ? ChatFormatting.GOLD : ChatFormatting.GREEN));
    }

    @SubscribeEvent
    public static void tickServidor(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || CERCOS.isEmpty()) return;
        ServerLevel l = e.getServer().overworld();
        for (Iterator<Cerco> it = CERCOS.values().iterator(); it.hasNext(); ) {
            Cerco c = it.next();
            if (!passo(l, c)) { c.barra.removeAllPlayers(); it.remove(); }
        }
    }

    private static int ondaTamanho(Cerco c) { return (c.id.equals("aurora") ? 5 : 4) + 2 * c.onda; }

    /** Um tick do cerco; false quando ele acaba (vencido ou sem ninguém por perto). */
    private static boolean passo(ServerLevel l, Cerco c) {
        Vec3 meio = Vec3.atCenterOf(c.centro);
        double raio = c.id.equals("aurora") ? 90 : 40;
        List<ServerPlayer> perto = new ArrayList<>();
        for (ServerPlayer q : l.players()) if (!q.isSpectator() && q.distanceToSqr(meio) < raio * raio) perto.add(q);
        String tag = CERCO + ":" + c.id;
        List<Mob> servos = l.getEntitiesOfClass(Mob.class, new AABB(c.centro).inflate(100, 80, 100), m -> m.getTags().contains(tag));
        // ninguém por perto há 30 s: o cerco se desfaz (volta do começo quando alguém voltar)
        if (perto.isEmpty()) {
            if (++c.vazio > 600) { servos.forEach(Mob::discard); return false; }
        } else c.vazio = 0;
        // a barra
        c.barra.setName(Component.translatable("necron.lanterna.cerco_" + c.id + "_barra", Math.max(1, Math.min(ONDAS, c.onda)), ONDAS));
        c.barra.setProgress(c.forca / 100f);
        for (ServerPlayer q : l.players()) {
            boolean dentro = perto.contains(q);
            if (dentro && !c.barra.getPlayers().contains(q)) c.barra.addPlayer(q);
            else if (!dentro && c.barra.getPlayers().contains(q)) c.barra.removePlayer(q);
        }
        if (c.pausa > 0) {
            if (--c.pausa == 0) {
                c.onda++;
                c.nascer = ondaTamanho(c);
                for (ServerPlayer q : perto)
                    q.displayClientMessage(Component.translatable("necron.lanterna.cerco_onda", c.onda, ONDAS).withStyle(ChatFormatting.DARK_PURPLE), true);
                l.playSound(null, c.centro, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2f, 0.7f);
            }
            return true;
        }
        int tc = l.getServer().getTickCount();
        // nascem aos poucos, em volta (em Aurora, na borda da vila; no Santuário, saindo das paredes)
        if (c.nascer > 0 && tc % 15 == 0) {
            boolean arauto = c.onda == ONDAS && c.nascer == 1;
            Mob m = arauto ? LanternaEntity.lanterna(l, BuiltinCorps.BLACK, true) : Cratera.novoServo(l);
            if (m != null) {
                double a = l.random.nextDouble() * Math.PI * 2;
                BlockPos onde;
                if (c.id.equals("aurora")) {
                    int x = c.centro.getX() + (int) (Math.cos(a) * 38), z = c.centro.getZ() + (int) (Math.sin(a) * 38);
                    onde = new BlockPos(x, chao(l, x, z), z);
                } else onde = c.centro.offset((int) Math.round(Math.cos(a) * 6.5), 1, (int) Math.round(Math.sin(a) * 6.5)); // saindo da parede
                m.moveTo(onde.getX() + 0.5, onde.getY(), onde.getZ() + 0.5, 0, 0);
                m.addTag(Cratera.SERVO);
                m.addTag(CERCO);
                m.addTag(tag);
                m.setPersistenceRequired();
                if (arauto) {
                    m.setCustomName(Component.translatable("necron.lanterna.arauto").withStyle(ChatFormatting.DARK_PURPLE));
                    var hp = m.getAttribute(Attributes.MAX_HEALTH);
                    if (hp != null) { hp.setBaseValue(160); m.setHealth(160); }
                    m.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
                }
                l.addFreshEntity(m);
                l.sendParticles(ParticleTypes.SOUL, m.getX(), m.getY() + 0.5, m.getZ(), 20, 0.4, 0.8, 0.4, 0.03);
                if (!c.id.equals("aurora")) l.sendParticles(ParticleTypes.SMOKE, m.getX(), m.getY() + 1, m.getZ(), 30, 0.5, 0.8, 0.5, 0.02);
                if (arauto) for (ServerPlayer q : perto)
                    q.sendSystemMessage(Component.translatable("necron.lanterna.cerco_" + c.id + "_arauto").withStyle(ChatFormatting.DARK_PURPLE));
            }
            c.nascer--;
        }
        // marcham para o centro; quem chega suga o escudo
        if (tc % 20 == 0) {
            double alcance = c.id.equals("aurora") ? 6 : 2.8;
            for (Mob m : servos) {
                double d2 = m.distanceToSqr(meio);
                if (d2 < alcance * alcance) {
                    c.forca = Math.max(0, c.forca - 3);
                    l.sendParticles(ParticleTypes.SCULK_SOUL, m.getX(), m.getY() + 1, m.getZ(), 4, 0.3, 0.3, 0.3, 0.02);
                } else if (m.getTarget() == null || m.getTarget().distanceToSqr(m) > 12 * 12) {
                    m.setTarget(null);
                    m.getNavigation().moveTo(meio.x, c.centro.getY(), meio.z, 1.1);
                }
            }
            if (tc % 40 == 0) escudo(l, c);
        }
        // o escudo caiu: o cerco recomeça
        if (c.forca <= 0) {
            servos.forEach(Mob::discard);
            for (ServerPlayer q : perto) q.sendSystemMessage(Component.translatable("necron.lanterna.cerco_" + c.id + "_caiu").withStyle(ChatFormatting.RED));
            l.playSound(null, c.centro, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2f, 0.6f);
            c.onda = 0;
            c.forca = 100;
            c.pausa = 200;
            c.nascer = 0;
            return true;
        }
        // onda vencida
        if (c.nascer == 0 && servos.isEmpty()) {
            if (c.onda >= ONDAS) {
                vencer(l, c, perto);
                return false;
            }
            c.pausa = 120;
            c.forca = Math.min(100, c.forca + 15);
            for (ServerPlayer q : perto) q.displayClientMessage(Component.translatable("necron.lanterna.cerco_pausa").withStyle(ChatFormatting.GREEN), true);
        }
        return true;
    }

    /** O escudo de luz em volta do centro (mais fraco, mais apagado). */
    private static void escudo(ServerLevel l, Cerco c) {
        float k = c.forca / 100f;
        Vector3f cor = c.id.equals("aurora") ? new Vector3f(1f, 0.85f, 0.4f) : new Vector3f(0.6f, 1f, 0.55f);
        DustParticleOptions d = new DustParticleOptions(cor, 1.6f);
        int n = (int) (10 + 30 * k);
        double r = c.id.equals("aurora") ? 7 : 4;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            l.sendParticles(d, c.centro.getX() + 0.5 + Math.cos(a) * r, c.centro.getY() + 0.5 + l.random.nextDouble() * 3, c.centro.getZ() + 0.5 + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    private static void vencer(ServerLevel l, Cerco c, List<ServerPlayer> perto) {
        marcar(l.getServer(), "saga:cerco:" + c.id);
        String chave = c.id.equals("aurora") ? "necron:aurora_cerco" : "necron:embriao_salvo";
        l.playSound(null, c.centro, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        l.sendParticles(ParticleTypes.END_ROD, c.centro.getX() + 0.5, c.centro.getY() + 2, c.centro.getZ() + 0.5, 120, 3, 2, 3, 0.1);
        for (ServerPlayer q : l.players()) {
            if (!perto.contains(q) && q.distanceToSqr(Vec3.atCenterOf(c.centro)) > 150 * 150) continue;
            FonteDados.chamado(q, chave, 1);
            q.sendSystemMessage(Component.translatable("necron.lanterna.cerco_" + c.id + "_fim").withStyle(c.id.equals("aurora") ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        }
    }

    // ===================== o pulso da vida =====================

    private static final Map<UUID, int[]> ESCUTA = new HashMap<>();

    private static void pulso(ServerLevel l, ServerPlayer p, int i) {
        String chave = "necron:pulso_" + i;
        if (FonteDados.chamado(p, chave) >= 1) return;
        BlockPos b0 = pulso(i);
        if (b0 == null) return;
        double dx = p.getX() - b0.getX(), dz = p.getZ() - b0.getZ(), dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 150) return;
        String k = "saga:pulso:" + i;
        if (!feito(l.getServer(), k)) {
            int y = chao(l, b0.getX(), b0.getZ());
            canteiro(l, new BlockPos(b0.getX(), y, b0.getZ()));
            marcar(l.getServer(), k);
            marcar(l.getServer(), k + ":y" + y);
        }
        BlockPos b = new BlockPos(b0.getX(), altura(l.getServer(), k, chao(l, b0.getX(), b0.getZ())), b0.getZ());
        // a coluna de luz verde-dourada, vista de longe
        if (p.tickCount % 20 == 7) {
            DustParticleOptions d = new DustParticleOptions(new Vector3f(0.7f, 1f, 0.5f), 2.5f);
            for (int n = 0; n < 16; n++) l.sendParticles(p, d, true, b.getX() + 0.5, b.getY() + 2 + n * 2.5, b.getZ() + 0.5, 1, 0.1, 0.3, 0.1, 0);
        }
        l.sendParticles(ParticleTypes.HAPPY_VILLAGER, b.getX() + 0.5, b.getY() + 1, b.getZ() + 0.5, 3, 1.5, 0.5, 1.5, 0);
        int[] e = ESCUTA.computeIfAbsent(p.getUUID(), u -> new int[4]);
        if (dist > 5 || Math.abs(p.getY() - b.getY()) > 6) { e[i] = 0; return; }
        e[i] += 10;
        p.displayClientMessage(Component.translatable("necron.lanterna.pulso_escuta", Math.min(100, e[i] * 100 / LEITURA)).withStyle(ChatFormatting.GREEN), true);
        if (e[i] < LEITURA) return;
        e[i] = 0;
        FonteDados.chamado(p, chave, 1);
        p.sendSystemMessage(Component.translatable("necron.lanterna.pulso_" + i).withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));
        l.playSound(null, b, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 0.8f);
        l.sendParticles(ParticleTypes.END_ROD, b.getX() + 0.5, b.getY() + 1, b.getZ() + 0.5, 50, 1, 1, 1, 0.08);
    }

    /** Um canteiro de vida no meio da escuridão: grama, flores e uma azaleia florida. */
    private static void canteiro(ServerLevel l, BlockPos c) {
        BlockState[] flores = {Blocks.DANDELION.defaultBlockState(), Blocks.POPPY.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState(),
                Blocks.CORNFLOWER.defaultBlockState(), Blocks.LILY_OF_THE_VALLEY.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState()};
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 10) continue;
                int y = chao(l, c.getX() + dx, c.getZ() + dz);
                BlockPos g = new BlockPos(c.getX() + dx, y - 1, c.getZ() + dz);
                if (l.getBlockState(g).isAir() || !l.getFluidState(g).isEmpty()) continue;
                l.setBlock(g, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                int r = Math.floorMod(dx * 7 + dz * 13, 9);
                if (dx == 0 && dz == 0) l.setBlock(g.above(), Blocks.FLOWERING_AZALEA.defaultBlockState(), 3);
                else if (r < 6) l.setBlock(g.above(), flores[r], 3);
                else if (r < 8) l.setBlock(g.above(), Blocks.GRASS.defaultBlockState(), 3);
            }
    }

    // ===================== o Santuário =====================

    private static void santuario(ServerLevel l, ServerPlayer p) {
        MinecraftServer s = p.server;
        BlockPos b = santuario();
        if (b == null) return;
        double dx = p.getX() - b.getX(), dz = p.getZ() - b.getZ();
        if (dx * dx + dz * dz > 140 * 140) return;
        construirSantuario(l);
        BlockPos c = camara(s);
        if (c == null) return;
        // a entrada brilha (todos os pulsos ouvidos)
        boolean ouviu = FonteDados.chamado(p, "necron:pulso_3") >= 1;
        if (ouviu && p.tickCount % 20 == 7) {
            DustParticleOptions d = new DustParticleOptions(new Vector3f(0.7f, 1f, 0.5f), 3f);
            int topo = chao(l, b.getX(), b.getZ());
            for (int n = 0; n < 20; n++) l.sendParticles(p, d, true, b.getX() + 0.5, topo + 2 + n * 2.5, b.getZ() + 0.5, 1, 0.1, 0.3, 0.1, 0);
        }
        if (FonteDados.chamado(p, "necron:santuario") < 1 && p.distanceToSqr(Vec3.atCenterOf(c)) < 9 * 9) {
            FonteDados.chamado(p, "necron:santuario", 1);
            p.sendSystemMessage(Component.translatable("necron.lanterna.santuario_achou").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));
            l.playSound(null, c, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 2f, 0.7f);
        }
    }

    /** Cava o Santuário, se ainda não existe (a linha corrompida também pode chegar até ele). */
    public static void construirSantuario(ServerLevel l) {
        MinecraftServer s = l.getServer();
        BlockPos b = santuario();
        if (b == null || feito(s, "saga:santuario")) return;
        int topo = chao(l, b.getX(), b.getZ());
        int cy = Math.max(l.getMinBuildHeight() + 12, topo - FUNDO);
        cavar(l, new BlockPos(b.getX(), cy, b.getZ()), topo);
        marcar(s, "saga:santuario");
        marcar(s, "saga:santuario:y" + cy);
        for (ServerPlayer q : s.getPlayerList().getPlayers()) SagaNecron.mandarMundo(q);
    }

    /** A câmara (uma bolha de 8 de raio, musgo, raízes, líquen brilhante) e o poço até a superfície. */
    private static void cavar(ServerLevel l, BlockPos c, int topo) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -9; dx <= 9; dx++)
            for (int dz = -9; dz <= 9; dz++)
                for (int dy = -7; dy <= 7; dy++) {
                    double r = Math.sqrt(dx * dx + dz * dz + (dy * 1.3) * (dy * 1.3));
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    int h = Math.floorMod(dx * 31 + dy * 17 + dz * 11, 13);
                    if (r < 7.6) {
                        if (dy <= -5 && r > 0) l.setBlock(m, dy == -5 ? (h < 3 ? Blocks.MOSS_CARPET.defaultBlockState() : Blocks.AIR.defaultBlockState()) : Blocks.MOSS_BLOCK.defaultBlockState(), 2);
                        else l.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
                    } else if (r < 9) {
                        BlockState s = h < 5 ? Blocks.MOSS_BLOCK.defaultBlockState() : h < 8 ? Blocks.ROOTED_DIRT.defaultBlockState()
                                : h < 9 ? Blocks.SHROOMLIGHT.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState();
                        l.setBlock(m, s, 2);
                    }
                }
        // raízes penduradas e líquen brilhante no teto
        for (int dx = -6; dx <= 6; dx++)
            for (int dz = -6; dz <= 6; dz++) {
                int h = Math.floorMod(dx * 13 + dz * 29, 7);
                for (int dy = 7; dy >= 0; dy--) {
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (!l.getBlockState(m).isAir()) {
                        BlockPos baixo = m.below();
                        if (l.getBlockState(baixo).isAir()) l.setBlock(baixo, h < 2 ? Blocks.HANGING_ROOTS.defaultBlockState() : h < 3 ? Blocks.SPORE_BLOSSOM.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                        break;
                    }
                }
            }
        // o pedestal de musgo com azaleias em volta (o Embrião flutua em cima)
        BlockPos chao = c.below(5);
        l.setBlock(chao, Blocks.MOSS_BLOCK.defaultBlockState(), 2);
        l.setBlock(chao.above(), Blocks.MOSS_BLOCK.defaultBlockState(), 2);
        for (int[] o : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) l.setBlock(chao.offset(o[0], 1, o[1]), Blocks.FLOWERING_AZALEA.defaultBlockState(), 2);
        // o poço até a superfície (3x3, com trepadeiras), saindo no meio de um canteiro
        for (int y = c.getY() + 5; y <= topo + 2; y++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) l.setBlock(m.set(c.getX() + 8 + dx, y, c.getZ() + dz), Blocks.AIR.defaultBlockState(), 2);
        for (int y = c.getY() + 3; y <= topo; y++) {
            m.set(c.getX() + 10, y, c.getZ());
            if (!l.getBlockState(m).isAir()) l.setBlock(m.set(c.getX() + 9, y, c.getZ()), Blocks.CAVE_VINES.defaultBlockState(), 2);
        }
        canteiro(l, new BlockPos(c.getX() + 8, topo, c.getZ() + 4));
    }

    // ===================== servos que morrem =====================

    @SubscribeEvent
    public static void morreu(LivingDeathEvent e) {
        var m = e.getEntity();
        if (!m.getTags().contains(FENDA) || !(m.level() instanceof ServerLevel l)) return;
        for (String tag : m.getTags()) {
            if (!tag.startsWith(FENDA + ":")) continue;
            int i = Integer.parseInt(tag.substring(FENDA.length() + 1));
            MinecraftServer s = l.getServer();
            if (fendaFechada(s, i)) return;
            int n = mortos(s, i) + 1;
            marcar(s, "saga:terra_fenda:" + i + ":m" + n);
            BlockPos f = fendaNoCeu(l, i);
            for (ServerPlayer q : l.players()) {
                if (f == null || q.distanceToSqr(Vec3.atCenterOf(f)) > 150 * 150) continue;
                q.displayClientMessage(Component.translatable("necron.lanterna.fenda_terra_conta", n, SERVOS_FENDA).withStyle(ChatFormatting.LIGHT_PURPLE), true);
                if (n >= SERVOS_FENDA) {
                    FonteDados.chamado(q, "necron:fenda_terra_" + i, 1);
                    q.sendSystemMessage(Component.translatable("necron.lanterna.fenda_terra_fechou").withStyle(ChatFormatting.GREEN));
                }
            }
            if (n >= SERVOS_FENDA) {
                marcar(s, "saga:terra_fenda:" + i + ":fechada");
                if (f != null) {
                    l.playSound(null, f, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 3f, 0.6f);
                    l.sendParticles(ParticleTypes.REVERSE_PORTAL, f.getX() + 0.5, f.getY(), f.getZ() + 0.5, 200, 3, 3, 3, 0.2);
                }
                for (ServerPlayer q : s.getPlayerList().getPlayers()) SagaNecron.mandarMundo(q);
            }
            return;
        }
    }

    /** Teste: zera o capítulo (as marcas do mundo; o que foi construído fica). */
    public static void zerar(MinecraftServer s) {
        LoreWorldData w = LoreWorldData.get(s);
        for (String k : new ArrayList<>(w.sites()))
            if (k.startsWith("saga:terra_fenda:") || k.startsWith("saga:cerco:") || k.startsWith("saga:pulso:")) w.resetSite(k);
        for (Cerco c : CERCOS.values()) c.barra.removeAllPlayers();
        CERCOS.clear();
        ALTURAS.clear();
    }

    public static void zerar(ServerPlayer p) {
        for (int i = 1; i <= 3; i++) { FonteDados.chamado(p, "necron:fenda_terra_" + i, 0); FonteDados.chamado(p, "necron:pulso_" + i, 0); }
        for (String k : new String[]{"necron:aurora_cerco", "necron:embriao_salvo", "necron:santuario", "necron:terra", "necron:vitoria"}) FonteDados.chamado(p, k, 0);
        SagaNecron.tag(p).putInt("escurece", 0);
        ESCUTA.remove(p.getUUID());
    }

    private Escurece() {}
}
