package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.fonte.FonteDados;
import com.lanterna.lore.LoreEntities;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.LoreCapability;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import com.lanterna.network.ModNetwork;
import com.lanterna.power.EquipmentHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.function.Supplier;

/**
 * 0.57.0: a Tentação (onde as duas linhas da saga se separam).
 *
 * Durante a guerra contra o Necron, quem morre para a Morte (o Necron, os servos dele, um Lanterna Negro ou o próprio
 * espectro) às vezes ouve a voz dela. Na batalha final, quem cai também pode ouvir. Ao voltar, aparece a pergunta:
 *
 *   SIM: o jogador se corrompe. O anel fica preto (Portador Negro) e começa a linha corrompida: o Necron vence
 *        (ver {@link LinhaCorrompida}).
 *   NÃO: a Morte prende os anéis onde ele caiu (ou na cratera, se ele caiu fora da Terra) e deixa a sombra dele
 *        guardando. O jogador volta até lá sem anel; ao pegar os anéis de volta, o Espectro se levanta: ele mesmo,
 *        com o traje negro e o Anel da Morte. Vencendo, a Morte o deixa em paz por um tempo (3 mortes).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Tentacao {
    public static final double CHANCE_MORTE = 0.35, CHANCE_BATALHA = 0.2;
    public static final int IMUNE = 3;

    private static CompoundTag tag(Player p) { return SagaNecron.tag(p); }

    /** Escolheu a Morte (linha corrompida). */
    public static boolean corrompido(Player p) { return tag(p).getBoolean("corrompido"); }

    private static boolean emGuerra(ServerPlayer p) {
        return SagaNecron.dividido(p) && !BatalhaFinal.derrotado(p.server) && !LinhaCorrompida.venceu(p.server) && !corrompido(p);
    }

    /** Quem mata em nome da Morte. */
    private static boolean daMorte(@Nullable Entity e) {
        if (e == null) return false;
        if (e instanceof NecronEntity || e instanceof EspectroEntity || Cratera.servo(e)) return true;
        if (e instanceof com.lanterna.especies.LanternaEntity le && le.tropa() == com.lanterna.corps.BuiltinCorps.BLACK) return true;
        return e instanceof ServerPlayer k && ModCapabilities.get(k).map(r -> r.hasRing() && com.lanterna.combate.Supremos.preto(r.corps())).orElse(false);
    }

    /** Tenta a alma: com a chance, a pergunta vai aparecer (agora ou ao renascer). */
    private static boolean tentar(ServerPlayer p, double chance) {
        if (!emGuerra(p) || tag(p).getBoolean("tentado") || tag(p).contains("aneis_presos")) return false;
        int imune = tag(p).getInt("tentacao_imune");
        if (imune > 0) { tag(p).putInt("tentacao_imune", imune - 1); return false; }
        if (p.getRandom().nextDouble() >= chance) return false;
        tag(p).putBoolean("tentado", true);
        LanternaMod.LOGGER.info("Lanterna: a Morte tenta {}", p.getGameProfile().getName());
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void morreu(LivingDeathEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer p)) return;
        if (!daMorte(e.getSource().getEntity())) return;
        if (tentar(p, CHANCE_MORTE)) {
            // onde ele caiu (para os anéis, se ele recusar)
            guardarLugar(p);
        }
    }

    /** Na batalha final ninguém morre; mas a Morte ainda pode falar com quem cai. */
    public static void caiuNaBatalha(ServerPlayer p, BlockPos ondeCaiu) {
        if (tentar(p, CHANCE_BATALHA)) {
            guardarLugar(p);
            // os anéis ficam onde ele caiu (não na borda, para onde os Lanternas o puxaram)
            tag(p).putInt("presos_x", ondeCaiu.getX());
            tag(p).putInt("presos_y", ondeCaiu.getY());
            tag(p).putInt("presos_z", ondeCaiu.getZ());
            perguntar(p);
        }
    }

    private static void guardarLugar(ServerPlayer p) {
        CompoundTag t = tag(p);
        BlockPos b = p.blockPosition();
        String dim = p.level().dimension().location().toString();
        if (p.level().dimension() != Level.OVERWORLD) {
            // fora da Terra os anéis vão para a borda da cratera (sem anel não dá para voltar ao espaço)
            BlockPos c = Cratera.centro(p.server);
            if (c != null) {
                int x = c.getX() + Cratera.BORDA + 12, z = c.getZ();
                b = new BlockPos(x, p.server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            } else b = p.server.overworld().getSharedSpawnPos();
            dim = Level.OVERWORLD.location().toString();
        }
        t.putInt("presos_x", b.getX());
        t.putInt("presos_y", b.getY());
        t.putInt("presos_z", b.getZ());
        t.putString("presos_dim", dim);
    }

    @SubscribeEvent
    public static void renasceu(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && tag(p).getBoolean("tentado")) perguntar(p);
    }

    @SubscribeEvent
    public static void entrou(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && tag(p).getBoolean("tentado")) perguntar(p);
    }

    private static void perguntar(ServerPlayer p) {
        p.level().playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5f, 0.6f);
        ModNetwork.sendTo(p, new Pergunta(SagaNecron.tag(p).getString("tropa")));
    }

    // ===================== a escolha =====================

    public static void escolheu(ServerPlayer p, boolean sim) {
        if (!tag(p).getBoolean("tentado")) return;
        tag(p).remove("tentado");
        if (sim) aceitar(p);
        else recusar(p);
    }

    /** SIM: o anel fica preto e começa a linha corrompida. */
    private static void aceitar(ServerPlayer p) {
        LoreData d = LoreCapability.get(p).orElse(null);
        if (d == null) return;
        tag(p).putBoolean("corrompido", true);
        com.lanterna.morte.Corrupcao.converter(p, d);
        // a linha do herói acaba aqui para este jogador
        for (String k : new java.util.ArrayList<>(d.active().keySet()))
            if (k.startsWith("necron_") && !k.startsWith("necron_c")) MissionManager.abandon(p, k);
        p.sendSystemMessage(Component.translatable("necron.lanterna.tentacao_sim_1").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.translatable("necron.lanterna.tentacao_sim_titulo").withStyle(ChatFormatting.DARK_PURPLE)));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.translatable("necron.lanterna.tentacao_sim_sub").withStyle(ChatFormatting.GRAY)));
        MissionManager.start(p, "necron_c1");
        LanternaMod.LOGGER.info("Lanterna: {} aceitou a Morte (linha corrompida)", p.getGameProfile().getName());
    }

    /** NÃO: a Morte prende os anéis onde ele caiu, e a sombra dele fica guardando. */
    private static void recusar(ServerPlayer p) {
        RingData r = ModCapabilities.get(p).orElse(null);
        ListTag lista = new ListTag();
        ItemStack anel = EquipmentHelper.take(p, RingData.RING_SLOT);
        if (!anel.isEmpty()) { CompoundTag c = anel.save(new CompoundTag()); c.putString("slot", "anel"); lista.add(c); }
        if (r != null) {
            ItemStack seg = r.segundoSlot().getStackInSlot(0);
            if (!seg.isEmpty()) {
                r.segundoSlot().setStackInSlot(0, ItemStack.EMPTY);
                CompoundTag c = seg.save(new CompoundTag());
                c.putString("slot", "segundo");
                lista.add(c);
            }
        }
        tag(p).put("aneis_presos", lista);
        tag(p).remove("presos_longe");
        p.sendSystemMessage(Component.translatable("necron.lanterna.tentacao_nao_1").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        p.sendSystemMessage(Component.translatable("necron.lanterna.tentacao_nao_2").withStyle(ChatFormatting.DARK_PURPLE));
        p.level().playSound(null, p.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.5f, 0.6f);
        LoreData d = MissionManager.data(p);
        if (d != null) { d.completed().remove("necron_espectro"); MissionManager.start(p, "necron_espectro"); }
        FonteDados.chamado(p, "necron:espectro", 0);
        LanternaMod.LOGGER.info("Lanterna: {} recusou a Morte; os anéis ficaram presos", p.getGameProfile().getName());
    }

    // ===================== os anéis presos e o Espectro =====================

    @Nullable
    public static BlockPos lugar(Player p) {
        CompoundTag t = tag(p);
        return t.contains("presos_x") ? new BlockPos(t.getInt("presos_x"), t.getInt("presos_y"), t.getInt("presos_z")) : null;
    }

    private static boolean noLugar(ServerPlayer p) {
        String dim = tag(p).getString("presos_dim");
        return p.level().dimension().location().toString().equals(dim.isEmpty() ? "minecraft:overworld" : dim);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 1) return;
        LoreData d = MissionManager.data(p);
        if (d == null || !d.isActive("necron_espectro")) return;
        BlockPos b = lugar(p);
        if (b == null || !noLugar(p)) return;
        ServerLevel l = p.serverLevel();
        double dist = Math.sqrt(p.distanceToSqr(Vec3.atBottomCenterOf(b)));
        boolean presos = tag(p).contains("aneis_presos");
        if (presos) {
            // os anéis flutuando, presos numa corrente de almas
            if (dist < 200 && p.tickCount % 20 == 1) {
                DustParticleOptions d1 = new DustParticleOptions(new Vector3f(0.55f, 0.3f, 0.75f), 2.5f);
                for (int k = 0; k < 18; k++) l.sendParticles(p, d1, true, b.getX() + 0.5, b.getY() + 2 + k * 2.5, b.getZ() + 0.5, 1, 0.1, 0.4, 0.1, 0);
                l.sendParticles(p, ParticleTypes.SOUL, true, b.getX() + 0.5, b.getY() + 1.5, b.getZ() + 0.5, 8, 0.4, 0.4, 0.4, 0.02);
            }
            // só depois de ir embora e voltar (quem recusa a Morte no mesmo lugar onde caiu não pega os anéis na hora)
            if (dist > 12) tag(p).putBoolean("presos_longe", true);
            if (dist < 4 && tag(p).getBoolean("presos_longe")) devolver(l, p, b);
            return;
        }
        // os anéis já voltaram: o Espectro (se sumiu porque o jogador se afastou, volta quando ele chega perto)
        if (dist < 40 && p.tickCount % 40 == 1 && !p.isCreative()
                && l.getEntitiesOfClass(EspectroEntity.class, new AABB(b).inflate(100), x -> p.getUUID().equals(x.dono())).isEmpty())
            levantar(l, p, b);
    }

    private static void devolver(ServerLevel l, ServerPlayer p, BlockPos b) {
        ListTag lista = tag(p).getList("aneis_presos", 10);
        RingData r = ModCapabilities.get(p).orElse(null);
        for (int i = 0; i < lista.size(); i++) {
            CompoundTag c = lista.getCompound(i);
            ItemStack s = ItemStack.of(c);
            if (s.isEmpty()) continue;
            if ("segundo".equals(c.getString("slot")) && r != null && r.segundoSlot().getStackInSlot(0).isEmpty()) r.segundoSlot().setStackInSlot(0, s);
            else EquipmentHelper.equipOrGive(p, RingData.RING_SLOT, s);
        }
        tag(p).remove("aneis_presos");
        l.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.8, 0.4, 0.1);
        l.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2f, 1.2f);
        p.sendSystemMessage(Component.translatable("necron.lanterna.espectro_aneis").withStyle(ChatFormatting.GREEN));
        levantar(l, p, b);
    }

    private static void levantar(ServerLevel l, ServerPlayer p, BlockPos b) {
        EspectroEntity s = LoreEntities.ESPECTRO.get().create(l);
        if (s == null) return;
        Vec3 dir = p.position().subtract(Vec3.atBottomCenterOf(b)).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 0.5 ? new Vec3(1, 0, 0) : dir.normalize();
        double x = b.getX() + 0.5 - dir.x * 6, z = b.getZ() + 0.5 - dir.z * 6;
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        s.moveTo(x, y, z, 0, 0);
        s.setDono(p);
        s.setCustomName(Component.translatable("necron.lanterna.espectro_nome", p.getName()).withStyle(ChatFormatting.DARK_GRAY));
        var hp = s.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        int nv = com.lanterna.tropa.Nivel.nivel(p);
        if (hp != null) hp.setBaseValue(Math.min(400, 80 + nv * 1.2));
        var at = s.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (at != null) at.setBaseValue(Math.min(16, 5 + nv * 0.05));
        s.setHealth(s.getMaxHealth());
        l.addFreshEntity(s);
        s.setTarget(p);
        l.sendParticles(ParticleTypes.SCULK_SOUL, s.getX(), s.getY() + 1, s.getZ(), 80, 0.5, 1.2, 0.5, 0.05);
        l.playSound(null, s.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6f, 1.4f);
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.translatable("necron.lanterna.espectro_titulo").withStyle(ChatFormatting.DARK_GRAY)));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.translatable("necron.lanterna.espectro_sub").withStyle(ChatFormatting.GRAY)));
        p.sendSystemMessage(Component.translatable("necron.lanterna.espectro_fala").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /** O Espectro caiu: a Morte desiste daquela alma por um tempo. */
    public static void espectroVencido(ServerLevel l, EspectroEntity s) {
        ServerPlayer p = l.getServer().getPlayerList().getPlayer(s.dono());
        l.sendParticles(ParticleTypes.END_ROD, s.getX(), s.getY() + 1, s.getZ(), 60, 0.5, 1, 0.5, 0.1);
        l.playSound(null, s.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.5f, 1.3f);
        if (p == null) return;
        FonteDados.chamado(p, "necron:espectro", 1);
        tag(p).putInt("tentacao_imune", IMUNE);
        p.sendSystemMessage(Component.translatable("necron.lanterna.espectro_vencido").withStyle(ChatFormatting.GREEN));
    }

    // ===================== os servos não atacam quem serve o Necron =====================

    @SubscribeEvent
    public static void alvo(LivingChangeTargetEvent e) {
        if (e.getNewTarget() instanceof Player p && corrompido(p) && Cratera.servo(e.getEntity())) e.setCanceled(true);
    }

    /** Teste: volta tudo da tentação. */
    public static void zerar(ServerPlayer p) {
        CompoundTag t = tag(p);
        for (String k : new String[]{"tentado", "corrompido", "aneis_presos", "presos_longe", "presos_x", "presos_y", "presos_z", "presos_dim", "tentacao_imune"}) t.remove(k);
        FonteDados.chamado(p, "necron:espectro", 0);
    }

    /** Teste: força a pergunta agora. */
    public static void forcar(ServerPlayer p) {
        tag(p).putBoolean("tentado", true);
        guardarLugar(p);
        perguntar(p);
    }

    // ===================== rede =====================

    /** Servidor → cliente: a pergunta da Morte. */
    public record Pergunta(String tropa) {
        public static void encode(Pergunta m, FriendlyByteBuf b) { b.writeUtf(m.tropa, 32); }
        public static Pergunta decode(FriendlyByteBuf b) { return new Pergunta(b.readUtf(32)); }
        public static void handle(Pergunta m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.lanterna.client.necron.TentacaoScreen.abrir(m.tropa)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Cliente → servidor: a resposta. */
    public record Resposta(boolean sim) {
        public static void encode(Resposta m, FriendlyByteBuf b) { b.writeBoolean(m.sim); }
        public static Resposta decode(FriendlyByteBuf b) { return new Resposta(b.readBoolean()); }
        public static void handle(Resposta m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) escolheu(p, m.sim);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    private Tentacao() {}
}
