package com.lanterna.morte;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.lore.LoreItems;
import com.lanterna.lore.block.AltarPurificacao;
import com.lanterna.mission.LoreCapability;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * O Ritual da Purificação (0.51), o último passo da linha da Purificação.
 *
 * No Altar da Purificação, com 2 Pérolas do Amanhã, 2 Lágrimas Índigo e 1 Maçã Dourada: o anel sai do dedo e flutua
 * sobre o pedestal da cabeceira, o portador se deita na laje de runas, os quatro cristais e o anel lançam luz sobre ele,
 * a fumaça da Morte sai do corpo, a Morte resiste uma vez e, num clarão, o anel volta limpo.
 *
 * Tudo no servidor (partículas, sons, títulos, o anel flutuando como item de exibição), então funciona igual em
 * servidor e para quem estiver olhando.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RitualPurificacao {
    public static final String FLAG = "purificacao:ritual";
    private static final int DURACAO = 200;

    private record Ritual(BlockPos altar, Direction cabeceira, UUID anelExibido, int cor) {}

    private static final Map<UUID, Ritual> RITUAIS = new HashMap<>();
    private static final Map<UUID, Integer> TEMPO = new HashMap<>();

    public static boolean emRitual(ServerPlayer p) {
        return RITUAIS.containsKey(p.getUUID());
    }

    /** Clicou no altar. */
    public static void usar(ServerPlayer p, BlockPos altar, Direction cabeceira) {
        if (emRitual(p)) return;
        LoreData d = LoreCapability.get(p).orElse(null);
        RingData r = ModCapabilities.get(p).orElse(null);
        if (d == null || r == null || !r.hasRing()) {
            p.displayClientMessage(Component.translatable("purificacao.lanterna.sem_anel").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (d.morte() != 2) {
            p.displayClientMessage(Component.translatable("purificacao.lanterna.limpo").withStyle(s -> s.withColor(0xCFE8FF)), true);
            return;
        }
        if (!d.isActive("purif_6") && !p.isCreative()) {
            p.displayClientMessage(Component.translatable("purificacao.lanterna.cedo").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        for (Ritual o : RITUAIS.values()) if (o.altar.equals(altar)) {
            p.displayClientMessage(Component.translatable("purificacao.lanterna.ocupado").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        // o que o altar pede (simples: a linha já é longa)
        if (!p.isCreative()) {
            int perolas = conta(p, LoreItems.PEROLA_DO_AMANHA.get()), lagrimas = conta(p, LoreItems.LAGRIMA_INDIGO.get()), macas = conta(p, Items.GOLDEN_APPLE);
            if (perolas < 2 || lagrimas < 2 || macas < 1) {
                p.sendSystemMessage(Component.translatable("purificacao.lanterna.falta", Math.max(0, 2 - perolas), Math.max(0, 2 - lagrimas), Math.max(0, 1 - macas))
                        .withStyle(s -> s.withColor(0xCFE8FF)));
                return;
            }
            tirar(p, LoreItems.PEROLA_DO_AMANHA.get(), 2);
            tirar(p, LoreItems.LAGRIMA_INDIGO.get(), 2);
            tirar(p, Items.GOLDEN_APPLE, 1);
        }
        ServerLevel l = p.serverLevel();
        // o anel vai para o pedestal (o de verdade continua no dedo, desligado; o do pedestal é a imagem dele)
        if (r.powerActive()) com.lanterna.power.RingPowers.togglePower(p);
        ItemStack copia = com.lanterna.power.EquipmentHelper.ring(p).copy();
        double[] pd = AltarPurificacao.gira(0, -0.6, cabeceira);
        Display.ItemDisplay anel = EntityType.ITEM_DISPLAY.create(l);
        UUID id = null;
        if (anel != null) {
            CompoundTag t = new CompoundTag();
            t.put("item", copia.save(new CompoundTag()));
            t.putString("billboard", "vertical");
            t.putString("item_display", "ground");
            CompoundTag tr = new CompoundTag();
            tr.put("scale", floats(1.6f, 1.6f, 1.6f));
            tr.put("translation", floats(0, 0, 0));
            tr.put("left_rotation", floats4(0, 0, 0, 1));
            tr.put("right_rotation", floats4(0, 0, 0, 1));
            t.put("transformation", tr);
            t.putInt("glow_color_override", r.color());
            anel.load(t);
            anel.setGlowingTag(true);
            anel.moveTo(altar.getX() + 0.5 + pd[0], altar.getY() + 1.25, altar.getZ() + 0.5 + pd[1]);
            l.addFreshEntity(anel);
            id = anel.getUUID();
        }
        RITUAIS.put(p.getUUID(), new Ritual(altar.immutable(), cabeceira, id, r.color()));
        TEMPO.put(p.getUUID(), 0);
        l.playSound(null, altar, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.2f, 0.8f);
        l.playSound(null, altar, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5f, 1f);
        // a luz sai da mão até o pedestal
        Vec3 mao = p.position().add(0, 1.1, 0), ped = new Vec3(altar.getX() + 0.5 + pd[0], altar.getY() + 1.25, altar.getZ() + 0.5 + pd[1]);
        for (int i = 0; i <= 16; i++) {
            Vec3 q = mao.lerp(ped, i / 16.0);
            l.sendParticles(new DustParticleOptions(cor(r.color()), 1.2f), q.x, q.y, q.z, 2, 0.03, 0.03, 0.03, 0);
        }
        p.sendSystemMessage(Component.translatable("purificacao.lanterna.comeca").withStyle(s -> s.withColor(0xCFE8FF).withItalic(true)));
        LanternaMod.LOGGER.info("Lanterna: {} começou o Ritual da Purificação", p.getGameProfile().getName());
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        Ritual r = RITUAIS.get(p.getUUID());
        if (r == null) return;
        int t = TEMPO.merge(p.getUUID(), 1, Integer::sum);
        ServerLevel l = p.serverLevel();
        BlockPos a = r.altar;
        if (!(l.getBlockState(a).getBlock() instanceof AltarPurificacao) || !p.isAlive()) {
            terminar(p, r, false);
            return;
        }
        Vec3 corpo = new Vec3(a.getX() + 0.5, a.getY() + 0.85, a.getZ() + 0.5);
        double[] meio = AltarPurificacao.gira(0, 0.55, r.cabeceira);
        corpo = corpo.add(meio[0], 0, meio[1]);
        double[] pdo = AltarPurificacao.gira(0, -0.6, r.cabeceira);
        Vec3 ped = new Vec3(a.getX() + 0.5 + pdo[0], a.getY() + 1.25, a.getZ() + 0.5 + pdo[1]);
        Vector3f cor = cor(r.cor);

        // o anel do pedestal sobe e desce de leve
        if (r.anelExibido != null && l.getEntity(r.anelExibido) instanceof Display.ItemDisplay d)
            d.moveTo(ped.x, ped.y + 0.12 * Math.sin(t * 0.12) + Math.min(0.5, t * 0.004), ped.z);

        if (t == 12) {
            // deita na laje (o jogo pensa que é uma cama)
            p.startSleeping(a);
            l.playSound(null, a, SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 0.6f, 0.8f);
        }
        // a Morte não deixa dormir de verdade: o contador de sono volta a zero (não pula a noite)
        if (t == 95 && p.isSleeping()) { p.stopSleepInBed(true, false); p.startSleeping(a); }
        if (t > 12 && !p.isSleeping() && t < DURACAO - 8) p.startSleeping(a);

        if (t >= 20 && t < 160) {
            // os quatro cristais lançam fios de luz até o corpo
            if (t % 3 == 0) for (double[] c : new double[][]{{-0.75, -1.4}, {0.75, -1.4}, {-0.75, 1.3}, {0.75, 1.3}}) {
                double[] g = AltarPurificacao.gira(c[0] * 1.25, c[1] * 1.15, r.cabeceira);
                Vec3 cr = new Vec3(a.getX() + 0.5 + g[0], a.getY() + 0.95, a.getZ() + 0.5 + g[1]);
                double f = ((t / 3) % 10) / 10.0;
                Vec3 q = cr.lerp(corpo, f);
                l.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
            // espiral da cor da Tropa descendo sobre o corpo
            double ang = t * 0.35, raio = 0.9 - (t - 20) * 0.004;
            for (int k = 0; k < 2; k++) {
                double aa = ang + k * Math.PI;
                l.sendParticles(new DustParticleOptions(cor, 1.4f), corpo.x + Math.cos(aa) * raio, corpo.y + 1.4 - ((t - 20) % 40) * 0.03,
                        corpo.z + Math.sin(aa) * raio, 1, 0, 0, 0, 0);
            }
            // o anel no pedestal manda luz para o peito
            if (t % 2 == 0) {
                Vec3 q = ped.add(0, 0.2, 0).lerp(corpo, ((t / 2) % 8) / 8.0);
                l.sendParticles(new DustParticleOptions(cor, 1.0f), q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
            }
            // a corrupção saindo do corpo
            if (t % 4 == 0) {
                l.sendParticles(ParticleTypes.SMOKE, corpo.x, corpo.y + 0.2, corpo.z, 3, 0.35, 0.05, 0.6, 0.01);
                l.sendParticles(ParticleTypes.SCULK_SOUL, corpo.x, corpo.y + 0.3, corpo.z, 1, 0.3, 0.05, 0.5, 0.02);
            }
            if (t % 20 == 0) l.playSound(null, a, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2f, 0.7f + (t - 20) / 160f);
            if (t == 40) titulo(p, "purificacao.lanterna.t1", "purificacao.lanterna.s1", r.cor);
        }
        if (t == 100) {
            // a Morte resiste
            l.playSound(null, a, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.BLOCKS, 1f, 0.6f);
            l.sendParticles(ParticleTypes.SCULK_SOUL, corpo.x, corpo.y + 0.5, corpo.z, 40, 0.5, 0.4, 0.8, 0.06);
            l.sendParticles(ParticleTypes.LARGE_SMOKE, corpo.x, corpo.y + 0.5, corpo.z, 30, 0.5, 0.4, 0.8, 0.03);
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
            titulo(p, "purificacao.lanterna.t2", "purificacao.lanterna.s2", 0x8A8A90);
        }
        if (t == 160) {
            // o clarão
            l.playSound(null, a, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1f, 1.1f);
            l.playSound(null, a, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.4f, 1.3f);
            l.sendParticles(ParticleTypes.END_ROD, corpo.x, corpo.y + 0.6, corpo.z, 160, 0.4, 1.4, 0.4, 0.25);
            l.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, corpo.x, corpo.y + 0.8, corpo.z, 80, 0.5, 0.6, 0.8, 0.5);
            l.sendParticles(new DustParticleOptions(cor, 2f), corpo.x, corpo.y + 0.8, corpo.z, 80, 1, 1, 1, 0);
            p.removeEffect(MobEffects.DARKNESS);
            // conclui o último passo da linha (o fim da linha chama Corrupcao.purificar)
            com.lanterna.fonte.FonteDados.chamado(p, FLAG, 1);
            LoreData d = LoreCapability.get(p).orElse(null);
            if (d != null && d.isActive("purif_6")) MissionManager.forcarConclusao(p, "purif_6");
            else if (d != null && d.morte() == 2) Corrupcao.purificar(p, d);
            titulo(p, "purificacao.lanterna.t3", "purificacao.lanterna.s3", 0xCFE8FF);
        }
        if (t >= DURACAO) terminar(p, r, true);
    }

    private static void terminar(ServerPlayer p, Ritual r, boolean ok) {
        RITUAIS.remove(p.getUUID());
        TEMPO.remove(p.getUUID());
        if (p.isSleeping()) p.stopSleepInBed(true, false);
        ServerLevel l = p.serverLevel();
        if (r.anelExibido != null && l.getEntity(r.anelExibido) != null) {
            Vec3 de = l.getEntity(r.anelExibido).position();
            l.getEntity(r.anelExibido).discard();
            // o anel volta para a mão
            Vec3 mao = p.position().add(0, 1.1, 0);
            for (int i = 0; i <= 12; i++) {
                Vec3 q = de.lerp(mao, i / 12.0);
                l.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
            }
        }
        if (ok) {
            RingData rd = ModCapabilities.get(p).orElse(null);
            if (rd != null && rd.hasRing() && !rd.powerActive()) com.lanterna.power.RingPowers.togglePower(p);
        } else if (!p.isCreative()) {
            // interrompido antes do fim: devolve o que o altar pediu
            p.getInventory().add(new ItemStack(LoreItems.PEROLA_DO_AMANHA.get(), 2));
            p.getInventory().add(new ItemStack(LoreItems.LAGRIMA_INDIGO.get(), 2));
            p.getInventory().add(new ItemStack(Items.GOLDEN_APPLE));
            p.sendSystemMessage(Component.translatable("purificacao.lanterna.interrompido").withStyle(ChatFormatting.GRAY));
        }
    }

    @SubscribeEvent
    public static void dormir(SleepingTimeCheckEvent e) {
        // no altar, deita a qualquer hora
        if (e.getEntity() instanceof ServerPlayer p && emRitual(p)) e.setResult(Event.Result.ALLOW);
    }

    @SubscribeEvent
    public static void saiu(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && RITUAIS.containsKey(p.getUUID())) terminar(p, RITUAIS.get(p.getUUID()), false);
    }

    private static void titulo(ServerPlayer p, String t, String s, int cor) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 40, 15));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(s).withStyle(st -> st.withColor(0xBFC6D0).withItalic(true))));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(t).withStyle(st -> st.withColor(cor).withBold(true))));
    }

    private static int conta(ServerPlayer p, net.minecraft.world.item.Item it) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(it)) n += s.getCount();
        return n;
    }

    private static void tirar(ServerPlayer p, net.minecraft.world.item.Item it, int n) {
        for (ItemStack s : p.getInventory().items) {
            if (n <= 0) return;
            if (!s.is(it)) continue;
            int k = Math.min(n, s.getCount());
            s.shrink(k);
            n -= k;
        }
    }

    private static Vector3f cor(int rgb) {
        return new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
    }

    private static net.minecraft.nbt.ListTag floats(float... v) {
        net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
        for (float f : v) l.add(net.minecraft.nbt.FloatTag.valueOf(f));
        return l;
    }

    private static net.minecraft.nbt.ListTag floats4(float a, float b, float c, float d) {
        return floats(a, b, c, d);
    }

    private RitualPurificacao() {}
}
