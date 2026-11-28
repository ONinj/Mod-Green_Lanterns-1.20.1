package com.lanterna.fonte;

import com.lanterna.LanternaMod;
import com.lanterna.baterias.Tropa;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 0.60.3: os Desafios das Memórias. No Salão das Memórias de cada Fonte (oeste da plataforma de chegada) há um altar ao
 * lado do livro do Arquivo. Nele o portador escolhe um dos Lanternas lendários das histórias daquela Tropa e enfrenta o
 * Eco dele, que surge no meio do salão. Quatro por Tropa, do mais fraco ao lendário do livro.
 *
 * Vencer dá Essência da Fonte (os pontos da Árvore da Luz; com 8 nós o Coração da Emoção, o chefe, pode despertar):
 * a primeira vitória sobre cada um dá 8, 12, 18 e 25; depois, 3 por vitória (o Eco leva 10 minutos para voltar).
 * Morrer, sair da Fonte ou se afastar do salão encerra o desafio.
 *
 * Só o anel da própria Tropa da Fonte pode desafiar. Dados no NBT persistente do jogador ("lanterna_desafios").
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Desafios {
    /** Centro do Salão das Memórias e o altar. */
    public static final BlockPos SALAO = new BlockPos(-26, 100, 0), ALTAR = new BlockPos(-26, 101, -10);
    public static final int N = 4;
    static final int[] PRIMEIRA = {8, 12, 18, 25};
    static final int REPETIDA = 3, ESPERA = 20 * 60 * 10;
    static final float[] VIDA = {60, 110, 170, 260}, DANO = {1f, 1.4f, 1.9f, 2.5f};
    static final String TAG = "lanterna_eco";

    private record Ativo(UUID eco, String tropa, int idx, ServerBossEvent barra) {}

    private static final Map<UUID, Ativo> ATIVOS = new HashMap<>();

    static CompoundTag dados(ServerPlayer p) {
        CompoundTag t = p.getPersistentData();
        if (!t.contains("lanterna_desafios")) t.put("lanterna_desafios", new CompoundTag());
        return t.getCompound("lanterna_desafios");
    }

    public static boolean vencido(ServerPlayer p, String tropa, int i) { return dados(p).getBoolean(tropa + "_" + i); }

    static Tropa tropa(String nome) {
        for (Tropa t : Tropa.values()) if (t.nome.equals(nome)) return t;
        return null;
    }

    static Component nome(String tropa, int i) { return Component.translatable("desafio.lanterna." + tropa + "." + i + ".nome"); }

    // ===================== o altar =====================

    @SubscribeEvent
    public static void altar(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !e.getPos().equals(ALTAR)) return;
        String tropa = Fonte.fonteDe(p.level());
        if (tropa == null) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        abrir(p, tropa);
    }

    public static void abrir(ServerPlayer p, String tropa) {
        Tropa t = tropa(tropa);
        if (t == null) return;
        int ven = 0;
        long agora = p.serverLevel().getGameTime();
        int[] espera = new int[N];
        for (int i = 0; i < N; i++) {
            if (vencido(p, tropa, i)) ven |= 1 << i;
            espera[i] = (int) Math.max(0, (dados(p).getLong(tropa + "_t" + i) + ESPERA - agora) / 20);
        }
        ModNetwork.sendTo(p, new Abrir(tropa, t.cor, ven, espera, FonteDados.essencia(p)));
    }

    /** O jogador escolheu um Eco para enfrentar. */
    static void desafiar(ServerPlayer p, String tropa, int i) {
        Tropa t = tropa(tropa);
        if (t == null || i < 0 || i >= N || !tropa.equals(Fonte.fonteDe(p.level()))) return;
        if (ATIVOS.containsKey(p.getUUID())) { p.displayClientMessage(Component.translatable("desafio.lanterna.ja"), true); return; }
        var anel = com.lanterna.lore.Factions.playerCorps(p);
        if (anel != t.corps() && !p.isCreative()) { p.displayClientMessage(Component.translatable("desafio.lanterna.anel"), true); return; }
        long agora = p.serverLevel().getGameTime();
        long volta = dados(p).getLong(tropa + "_t" + i) + ESPERA;
        if (vencido(p, tropa, i) && agora < volta && !p.isCreative()) {
            p.displayClientMessage(Component.translatable("desafio.lanterna.espera", (volta - agora) / 1200 + 1), true);
            return;
        }
        ServerLevel l = p.serverLevel();
        LanternaEntity eco = LanternaEntity.lanterna(l, t.corps(), i >= 2);
        eco.moveTo(SALAO.getX() + 0.5, SALAO.getY() + 1, SALAO.getZ() + 5.5, 180, 0);
        eco.getPersistentData().putUUID(TAG, p.getUUID());
        eco.setCustomName(Component.translatable("desafio.lanterna.eco", nome(tropa, i)).withStyle(s -> s.withColor(t.cor)));
        eco.setCustomNameVisible(true);
        var vida = eco.getAttribute(Attributes.MAX_HEALTH);
        if (vida != null) vida.setBaseValue(VIDA[i]);
        eco.setHealth(VIDA[i]);
        var dano = eco.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dano != null) dano.setBaseValue(dano.getBaseValue() * DANO[i]);
        if (i == 3) eco.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 0, false, false));
        eco.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60 * 30, 0, false, false));
        eco.setTarget(p);
        l.addFreshEntity(eco);
        ServerBossEvent barra = new ServerBossEvent(eco.getCustomName(), cor(t), BossEvent.BossBarOverlay.NOTCHED_10);
        barra.addPlayer(p);
        ATIVOS.put(p.getUUID(), new Ativo(eco.getUUID(), tropa, i, barra));
        l.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, eco.getX(), eco.getY() + 1, eco.getZ(), 60, 0.5, 1, 0.5, 0.05);
        l.playSound(null, eco.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.5f, 0.6f);
        p.sendSystemMessage(Component.translatable("desafio.lanterna.historia", nome(tropa, i),
                Component.translatable("desafio.lanterna." + tropa + "." + i + ".fala")).withStyle(s -> s.withColor(t.cor)));
    }

    static BossEvent.BossBarColor cor(Tropa t) {
        return switch (t) {
            case VERDE -> BossEvent.BossBarColor.GREEN; case AMARELO -> BossEvent.BossBarColor.YELLOW; case VERMELHO -> BossEvent.BossBarColor.RED;
            case LARANJA -> BossEvent.BossBarColor.RED; case AZUL -> BossEvent.BossBarColor.BLUE; case INDIGO -> BossEvent.BossBarColor.PURPLE;
            case VIOLETA -> BossEvent.BossBarColor.PINK;
        };
    }

    /** O Eco só luta com quem o desafiou (o LanternaEntity pergunta aqui). */
    public static boolean ecoDe(Entity eco, Entity alvo) {
        return eco.getPersistentData().hasUUID(TAG) && eco.getPersistentData().getUUID(TAG).equals(alvo.getUUID());
    }

    public static boolean eco(Entity e) { return e.getPersistentData().hasUUID(TAG); }

    // ===================== vitória, derrota =====================

    @SubscribeEvent
    public static void morreu(LivingDeathEvent e) {
        if (e.getEntity().level().isClientSide) return;
        if (e.getEntity() instanceof ServerPlayer p && ATIVOS.containsKey(p.getUUID())) { encerrar(p, false); return; }
        if (!eco(e.getEntity())) return;
        UUID dono = e.getEntity().getPersistentData().getUUID(TAG);
        var srv = e.getEntity().getServer();
        ServerPlayer p = srv == null ? null : srv.getPlayerList().getPlayer(dono);
        if (p != null && ATIVOS.containsKey(dono)) encerrar(p, true);
    }

    static void encerrar(ServerPlayer p, boolean venceu) {
        Ativo a = ATIVOS.remove(p.getUUID());
        if (a == null) return;
        a.barra.removeAllPlayers();
        ServerLevel l = p.serverLevel();
        Entity eco = null;
        for (ServerLevel sl : p.server.getAllLevels()) { eco = sl.getEntity(a.eco); if (eco != null) break; }
        if (!venceu) {
            if (eco != null && eco.isAlive()) eco.discard();
            p.sendSystemMessage(Component.translatable("desafio.lanterna.perdeu", nome(a.tropa, a.idx)).withStyle(ChatFormatting.GRAY));
            return;
        }
        boolean primeira = !vencido(p, a.tropa, a.idx);
        int ganho = primeira ? PRIMEIRA[a.idx] : REPETIDA;
        dados(p).putBoolean(a.tropa + "_" + a.idx, true);
        dados(p).putLong(a.tropa + "_t" + a.idx, l.getGameTime());
        FonteDados.essencia(p, FonteDados.essencia(p) + ganho);
        Tropa t = tropa(a.tropa);
        l.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        l.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 1, 0.5, 0.3);
        p.sendSystemMessage(Component.translatable(primeira ? "desafio.lanterna.venceu" : "desafio.lanterna.venceu_de_novo", nome(a.tropa, a.idx), ganho,
                FonteDados.essencia(p)).withStyle(s -> s.withColor(t != null ? t.cor : 0xFFFFFF).withBold(primeira)));
        LanternaMod.LOGGER.info("Lanterna: {} venceu o Eco {} ({}) e ganhou {} de Essência", p.getGameProfile().getName(), a.idx, a.tropa, ganho);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ATIVOS.isEmpty()) return;
        var srv = e.getServer();
        for (UUID id : new java.util.ArrayList<>(ATIVOS.keySet())) {
            Ativo a = ATIVOS.get(id);
            ServerPlayer p = srv.getPlayerList().getPlayer(id);
            Entity eco = null;
            for (ServerLevel sl : srv.getAllLevels()) { eco = sl.getEntity(a.eco); if (eco != null) break; }
            if (p == null) { if (eco != null) eco.discard(); a.barra.removeAllPlayers(); ATIVOS.remove(id); continue; }
            boolean longe = !a.tropa.equals(Fonte.fonteDe(p.level())) || p.blockPosition().distSqr(SALAO) > 34 * 34;
            if (eco == null || !eco.isAlive() || longe) { encerrar(p, false); continue; }
            if (eco instanceof LanternaEntity le) {
                a.barra.setProgress(le.getHealth() / le.getMaxHealth());
                if (le.getTarget() != p) le.setTarget(p);
                // o Eco não sai do salão
                if (le.blockPosition().distSqr(SALAO) > 15 * 15) le.teleportTo(SALAO.getX() + 0.5, SALAO.getY() + 1, SALAO.getZ() + 0.5);
            }
        }
    }

    // ===================== pacotes =====================

    /** Servidor → cliente: abre a tela do altar. */
    public record Abrir(String tropa, int cor, int vencidos, int[] espera, int essencia) {
        public static void encode(Abrir m, FriendlyByteBuf b) {
            b.writeUtf(m.tropa); b.writeInt(m.cor); b.writeVarInt(m.vencidos); b.writeVarIntArray(m.espera); b.writeVarInt(m.essencia);
        }
        public static Abrir decode(FriendlyByteBuf b) { return new Abrir(b.readUtf(), b.readInt(), b.readVarInt(), b.readVarIntArray(), b.readVarInt()); }
        public static void handle(Abrir m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.lanterna.client.fonte.DesafiosScreen.abrir(m));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Cliente → servidor: desafiar o Eco "idx". */
    public record Desafiar(String tropa, int idx) {
        public static void encode(Desafiar m, FriendlyByteBuf b) { b.writeUtf(m.tropa); b.writeVarInt(m.idx); }
        public static Desafiar decode(FriendlyByteBuf b) { return new Desafiar(b.readUtf(), b.readVarInt()); }
        public static void handle(Desafiar m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) desafiar(p, m.tropa, m.idx);
            ctx.get().setPacketHandled(true);
        }
    }

    private Desafios() {}
}
