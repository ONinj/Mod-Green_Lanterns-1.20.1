package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.MissionManager;
import com.lanterna.network.ModNetwork;
import com.lanterna.vida.Emocoes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 0.54.3: o Necron "mata" o portador. Na primeira vez que a vida cai demais perto dele, em vez de morrer começa a cena
 * da fuga: um Lanterna da Tropa do jogador chega do céu num clarão, ergue um escudo contra a foice, segura o jogador e
 * voa para longe, com os raios da Morte atrás. Tudo escurece; o jogador acorda no planeta da Tropa, e o Lanterna
 * explica o que está acontecendo (o Embrião da Vida na Terra, as Baterias Centrais atacadas, e que só dois anéis ao
 * mesmo tempo ferem os servos do Necron). Depois disso começa a missão do segundo anel.
 *
 * Na cratera o jogador fica protegido durante a cena. Os outros jogadores veem o Lanterna chegando e levando o colega.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Fuga {
    // marcos (ticks)
    public static final int CHEGA = 45, GOLPE = 58, SOBE = 66, PRETO = 140, CASA = 172, FALA = 200, NFALAS = 9, POR_FALA = 75;
    /** 0.55.0: depois da conversa, o alerta: a fenda da Morte se abre sobre a Bateria Central da Tropa. */
    public static final int ALERTA = FALA + NFALAS * POR_FALA + 10, POR_ALERTA = 70, NALERTAS = 3;
    public static final int FIM = ALERTA + NALERTAS * POR_ALERTA + 30;

    private static final class Estado {
        int t;
        BuiltinCorps tropa;
        @Nullable UUID aliado, necron;
        Vec3 inicio;
        Vec3 fugaDir;
    }

    private static final Map<UUID, Estado> ATIVAS = new HashMap<>();

    public static boolean emFuga(net.minecraft.world.entity.player.Player p) {
        return ATIVAS.containsKey(p.getUUID());
    }

    /** Já foi salvo uma vez (a fuga só acontece na primeira derrota). */
    public static boolean fugiu(net.minecraft.world.entity.player.Player p) {
        return SagaNecron.tag(p).getBoolean("fugiu");
    }

    // ===================== quando começa =====================

    /** Perto do Necron, a vida não cai abaixo de 25%: começa a fuga. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void ferido(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.isCanceled()) return;
        if (emFuga(p)) { e.setCanceled(true); return; }
        if (fugiu(p) || !SagaNecron.dividido(p)) return;
        BlockPos c = Cratera.centro(p.server);
        if (c == null || !Cratera.dentro(p.level(), c, p.getX(), p.getZ(), 0)) return;
        Entity fonte = e.getSource().getEntity();
        boolean daMorte = fonte instanceof NecronEntity || (fonte != null && Cratera.servo(fonte))
                || p.position().distanceTo(Vec3.atCenterOf(Cratera.lugarDoNecron(c))) < 24;
        if (!daMorte) return;
        float limite = p.getMaxHealth() * 0.25f;
        if (p.getHealth() - e.getAmount() > limite) return;
        e.setAmount(Math.max(0, p.getHealth() - limite));
        comecar(p);
    }

    /** Segurança: se mesmo assim ia morrer durante a fuga, não morre. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void morreria(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && emFuga(p)) {
            e.setCanceled(true);
            p.setHealth(Math.max(2, p.getMaxHealth() * 0.2f));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void protegido(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && emFuga(p) && !e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))
            e.setCanceled(true);
        // o Lanterna que vem salvar não morre
        if (e.getEntity().getTags().contains("lanterna_aliado_fuga")) e.setCanceled(true);
    }

    public static void comecar(ServerPlayer p) {
        if (emFuga(p)) return;
        ServerLevel l = p.serverLevel();
        Estado st = new Estado();
        String tr = SagaNecron.tag(p).getString("tropa");
        if (!Emocoes.aspecto(tr)) tr = SagaNecron.tropaDeOrigem(p);
        st.tropa = tropa(tr);
        st.inicio = p.position();
        BlockPos c = Cratera.centro(p.server);
        Vec3 fora = c == null ? new Vec3(1, 0, 0) : p.position().subtract(Vec3.atCenterOf(c)).multiply(1, 0, 1);
        st.fugaDir = fora.lengthSqr() < 1 ? new Vec3(1, 0, 0) : fora.normalize();
        NecronEntity n = l.getEntitiesOfClass(NecronEntity.class, p.getBoundingBox().inflate(60)).stream().findFirst().orElse(null);
        st.necron = n == null ? null : n.getUUID();
        // o Lanterna chega do céu, atrás do jogador
        LanternaEntity a = aliado(l, st.tropa);
        if (a != null) {
            Vec3 de = p.position().add(st.fugaDir.scale(30)).add(0, 40, 0);
            a.moveTo(de.x, de.y, de.z, 0, 0);
            l.addFreshEntity(a);
            st.aliado = a.getUUID();
        }
        ATIVAS.put(p.getUUID(), st);
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 80, 4, false, false));
        ModNetwork.sendTo(p, new Cena(0, a == null ? -1 : a.getId(), n == null ? -1 : n.getId(), st.tropa.id().getPath(), 0, 0, 0));
        LanternaMod.LOGGER.info("Lanterna: {} foi derrotado pelo Necron; um Lanterna {} vem buscá-lo", p.getGameProfile().getName(), st.tropa.id().getPath());
    }

    private static BuiltinCorps tropa(String path) {
        for (BuiltinCorps b : BuiltinCorps.values()) if (b.id().getPath().equals(path)) return b;
        return BuiltinCorps.GREEN;
    }

    @Nullable
    private static LanternaEntity aliado(ServerLevel l, BuiltinCorps tropa) {
        LanternaEntity a = LanternaEntity.lanterna(l, tropa, true);
        if (a == null) return null;
        a.setNoAi(true);
        a.setNoGravity(true);
        a.setInvulnerable(true);
        a.addTag("lanterna_aliado_fuga");
        a.setCustomName(Component.translatable("necron.lanterna.aliado", Component.translatable("corps.lanterna." + tropa.id().getPath()))
                .withStyle(s -> s.withColor(tropa.color())));
        a.setCustomNameVisible(true);
        a.setPersistenceRequired();
        return a;
    }

    // ===================== a cena (servidor) =====================

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        Estado st = ATIVAS.get(p.getUUID());
        if (st == null) return;
        st.t++;
        int t = st.t;
        ServerLevel l = p.serverLevel();
        Entity a = st.aliado == null ? null : l.getEntity(st.aliado);
        Entity n = st.necron == null ? null : l.getEntity(st.necron);
        int cor = st.tropa.color();
        DustParticleOptions luz = new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 1.8f);
        if (t < CASA) {
            p.fallDistance = 0;
            p.setDeltaMovement(Vec3.ZERO);
        }
        if (t <= CHEGA && a != null) {
            // descendo do céu, rápido
            Vec3 alvo = st.inicio.add(st.fugaDir.scale(-1.6)).add(0, 0.2, 0);
            Vec3 pos = a.position().lerp(alvo, Math.min(1, 0.06 + t / (double) CHEGA * 0.3));
            a.moveTo(pos.x, pos.y, pos.z, yaw(pos, st.inicio), 0);
            l.sendParticles(luz, pos.x, pos.y + 1, pos.z, 6, 0.3, 0.6, 0.3, 0);
            if (t == CHEGA) {
                l.playSound(null, a.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 1.3f);
                l.playSound(null, a.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.4f);
                l.sendParticles(luz, pos.x, pos.y + 1, pos.z, 120, 1.2, 1.2, 1.2, 0);
            }
        }
        if (t > CHEGA && t < SOBE && a != null) {
            // o escudo: uma cúpula de luz da Tropa entre o jogador e o Necron
            Vec3 c0 = p.position().add(0, 1, 0);
            Vec3 paraNecron = n == null ? st.fugaDir.scale(-1) : n.position().subtract(p.position()).multiply(1, 0, 1).normalize();
            for (int i = 0; i < 24; i++) {
                double ang = (i / 24.0 - 0.5) * Math.PI * 1.2, alt = (l.random.nextDouble() - 0.3) * 2.4;
                Vec3 lado = new Vec3(-paraNecron.z, 0, paraNecron.x);
                Vec3 q = c0.add(paraNecron.scale(2.2 * Math.cos(ang))).add(lado.scale(2.2 * Math.sin(ang))).add(0, alt, 0);
                l.sendParticles(luz, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
            if (t == GOLPE && n instanceof NecronEntity ne) {
                ne.triggerAnim("golpe", "golpe");
                l.playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2f, 0.5f);
                l.playSound(null, p.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.5f, 0.6f);
                l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c0.x + paraNecron.x * 2, c0.y, c0.z + paraNecron.z * 2, 60, 0.6, 0.8, 0.6, 0.15);
            }
        }
        if (t >= SOBE && t < CASA) {
            // segura o jogador e voa para longe e para cima
            double k = (t - SOBE) / (double) (PRETO - SOBE);
            Vec3 pos = st.inicio.add(st.fugaDir.scale(70 * k * k)).add(0, 85 * k, 0);
            if (t < PRETO + 10) {
                p.connection.teleport(pos.x, pos.y, pos.z, p.getYRot(), p.getXRot());
                if (a != null) a.moveTo(pos.x - st.fugaDir.x * 0.6, pos.y + 0.6, pos.z - st.fugaDir.z * 0.6, yaw(st.inicio, pos), 0);
                l.sendParticles(luz, pos.x, pos.y + 0.8, pos.z, 8, 0.4, 0.5, 0.4, 0);
            }
            // os raios da Morte atrás deles
            if (n != null && t % 12 == 0 && t < PRETO) {
                Vec3 de = n.position().add(0, 2.6, 0), ate = pos.add(st.fugaDir.scale(-3)).add(l.random.nextDouble() * 4 - 2, l.random.nextDouble() * 3, l.random.nextDouble() * 4 - 2);
                Vec3 d = ate.subtract(de);
                DustParticleOptions negro = new DustParticleOptions(new Vector3f(0.18f, 0.02f, 0.28f), 2f);
                for (int i = 0; i < 40; i++) {
                    Vec3 q = de.add(d.scale(i / 40.0));
                    l.sendParticles(negro, q.x, q.y, q.z, 1, 0.1, 0.1, 0.1, 0);
                }
                l.playSound(null, n.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.7f, 1.6f);
            }
        }
        if (t == CASA) irParaCasa(p, st, a);
        if (t > CASA && a != null) {
            // na conversa, o Lanterna olha para o jogador
            a.setYRot(yaw(a.position(), p.position()));
            a.setYHeadRot(a.getYRot());
        }
        if (t == ALERTA) {
            Defesa.abrirFenda(p.serverLevel(), st.tropa);
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6f, 0.5f);
        }
        if (t >= FIM) acabar(p, st);
    }

    private static float yaw(Vec3 de, Vec3 para) {
        Vec3 d = para.subtract(de);
        return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
    }

    /** Tudo escuro: o jogador acorda no planeta da Tropa, com o Lanterna do lado. */
    private static void irParaCasa(ServerPlayer p, Estado st, @Nullable Entity a) {
        if (a != null) a.discard();
        PlanetLore casa = PlanetLore.byCorps(st.tropa.id());
        var corpo = casa == null ? null : com.lanterna.space.SpaceBodies.byId(casa.planet);
        ServerLevel destino = corpo == null || corpo.dimension() == null ? null : p.server.getLevel(corpo.dimension());
        if (destino == null) destino = p.server.overworld();
        BlockPos hub = destino == p.server.overworld() ? destino.getSharedSpawnPos() : PlanetLore.HUB;
        int x = hub.getX() + 6, z = hub.getZ() + 18;
        destino.getChunk(x >> 4, z >> 4);
        int y = destino.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        p.teleportTo(destino, x + 0.5, y, z + 0.5, 180, 0);
        p.setHealth(p.getMaxHealth());
        p.removeAllEffects();
        LanternaEntity novo = aliado(destino, st.tropa);
        if (novo != null) {
            novo.moveTo(x + 0.5, y, z - 2.5, 0, 0);
            destino.addFreshEntity(novo);
            st.aliado = novo.getUUID();
            BlockPos f = Defesa.fenda(destino);
            ModNetwork.sendTo(p, new Cena(1, novo.getId(), -1, st.tropa.id().getPath(), f.getX(), f.getY(), f.getZ()));
        }
        MissionManager.data(p);
        com.lanterna.fonte.FonteDados.chamado(p, "necron:enfrentou", 1);
    }

    private static void acabar(ServerPlayer p, Estado st) {
        ATIVAS.remove(p.getUUID());
        SagaNecron.tag(p).putBoolean("fugiu", true);
        Entity a = st.aliado == null ? null : p.serverLevel().getEntity(st.aliado);
        if (a instanceof LanternaEntity le) {
            // o Lanterna fica por ali, como um NPC da Tropa
            le.setNoAi(false);
            le.setNoGravity(false);
            le.removeTag("lanterna_aliado_fuga");
        }
        MissionManager.start(p, "necron_3");
        p.sendSystemMessage(Component.translatable("necron.lanterna.fuga_fim").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Saiu no meio: acaba na hora (acorda em casa ao voltar). */
    @SubscribeEvent
    public static void saiu(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Estado st = ATIVAS.get(p.getUUID());
        if (st == null) return;
        if (st.t < CASA) irParaCasa(p, st, st.aliado == null ? null : p.serverLevel().getEntity(st.aliado));
        acabar(p, st);
    }

    /** Teste: zera (pode fugir de novo). */
    public static void zerar(ServerPlayer p) {
        ATIVAS.remove(p.getUUID());
        SagaNecron.tag(p).remove("fugiu");
        for (String k : new String[]{"necron:cratera", "necron:enfrentou", "necron:dois"}) com.lanterna.fonte.FonteDados.chamado(p, k, 0);
    }

    // ===================== rede =====================

    /** Servidor → cliente: começa a cena da fuga (fase 0) ou a conversa no planeta (fase 1). */
    public record Cena(int fase, int aliado, int necron, String tropa, int fx, int fy, int fz) {
        public static void encode(Cena m, FriendlyByteBuf b) {
            b.writeVarInt(m.fase); b.writeInt(m.aliado); b.writeInt(m.necron); b.writeUtf(m.tropa, 32);
            b.writeInt(m.fx); b.writeInt(m.fy); b.writeInt(m.fz);
        }
        public static Cena decode(FriendlyByteBuf b) { return new Cena(b.readVarInt(), b.readInt(), b.readInt(), b.readUtf(32), b.readInt(), b.readInt(), b.readInt()); }
        public static void handle(Cena m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.lanterna.client.necron.CenaFuga.receber(m)));
            ctx.get().setPacketHandled(true);
        }
    }

    private Fuga() {}
}
