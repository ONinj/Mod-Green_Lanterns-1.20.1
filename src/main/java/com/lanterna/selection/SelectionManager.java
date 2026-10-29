package com.lanterna.selection;

import com.lanterna.LanternaConfig;
import com.lanterna.block.PowerBatteryBlock;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.CorpsDefinition;
import com.lanterna.corps.CorpsManager;
import com.lanterna.item.PowerBatteryItem;
import com.lanterna.item.PowerRingItem;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.SelectionCutscenePacket;
import com.lanterna.power.EquipmentHelper;
import com.lanterna.power.RingPowers;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A cerimônia de entrega do anel. Cada Tropa tem a sua:
 *
 *  VERDE    (vontade)    o anel risca o céu como um cometa e gira até o dedo
 *  VERMELHO (fúria)      colunas de sangue sobem do chão e se fecham em volta
 *  AMARELO  (medo)       a escuridão engole tudo; raios amarelos rasgam o escuro até você
 *  AZUL     (esperança)  uma aurora azul sobe do chão como um amanhecer, com sinos
 *  VIOLETA  (amor)       pétalas e corações caem em espiral em volta de você
 *  ÍNDIGO   (compaixão)  um feixe de luz desce do céu e ondas índigo se espalham no chão
 *  LARANJA  (avareza)    uma chuva de ouro gira em volta de você, cada vez mais rápida
 *  NEGRO    (morte)      almas sobem da terra e giram até virar um anel negro
 *  BRANCO   (vida)       uma coluna de luz branca explode, e tudo em volta floresce de luz
 *
 * Fluxo:  startSelection() -> tick() a cada tick -> deliver() no fim.
 */
public final class SelectionManager {

    /** Como o anel chegou, quando não foi pelo ritual da Tropa ("call" = conquistas, "dormant" = anel apagado). */
    private static final java.util.Map<java.util.UUID, String> REASON = new java.util.HashMap<>();

    public static boolean startSelection(ServerPlayer player, ResourceLocation corpsId, String reason) {
        boolean ok = startSelection(player, corpsId);
        if (ok) REASON.put(player.getUUID(), reason);
        return ok;
    }

    /** Começa a cerimônia. Devolve false se o jogador já tem anel ou já está sendo escolhido. */
    public static boolean startSelection(ServerPlayer player, ResourceLocation corpsId) {
        RingData data = ModCapabilities.get(player).orElse(null);
        if (data == null || data.hasRing() || data.isSelectionPending()) return false;

        CorpsDefinition def = CorpsManager.get(corpsId);
        int duration = LanternaConfig.SELECTION_ANIMATION_TICKS.get();
        data.startPending(def.id(), duration);

        // Efeito de tela no cliente
        ModNetwork.sendTo(player, new SelectionCutscenePacket(def.id().toString(), def.color(), duration));

        // Título grande no meio da tela
        String base = "selection." + def.id().getNamespace() + "." + def.id().getPath();
        player.connection.send(new ClientboundSetTitlesAnimationPacket(20, Math.max(10, duration - 30), 20));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.translatable(base + ".subtitle").withStyle(s -> s.withColor(def.color()).withItalic(true))));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable(base + ".title").withStyle(s -> s.withColor(def.color()).withBold(true))));

        startEffects(player, def.id().getPath(), duration);
        return true;
    }

    private static void startEffects(ServerPlayer player, String corps, int duration) {
        ServerLevel level = player.serverLevel();
        switch (corps) {
            case "red" -> {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, duration + 40, 0, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 2, false, false));
                sound(player, SoundEvents.WARDEN_HEARTBEAT, 2.0f, 0.6f);
                sound(player, SoundEvents.WITHER_SPAWN, 0.4f, 1.6f);
            }
            case "yellow" -> {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, duration + 20, 0, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 3, false, false));
                sound(player, SoundEvents.WARDEN_EMERGE, 1.0f, 1.3f);
                sound(player, SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.0f, 0.7f);
            }
            case "blue" -> {
                player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, duration / 2, 0, false, false));
                sound(player, SoundEvents.BELL_BLOCK, 1.5f, 1.2f);
                sound(player, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.2f);
            }
            case "violet" -> {
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, duration + 20, 0, false, false));
                sound(player, SoundEvents.ALLAY_AMBIENT_WITH_ITEM, 1.5f, 1.0f);
                sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 0.8f);
            }
            case "indigo" -> {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 1, false, false));
                sound(player, SoundEvents.CONDUIT_ACTIVATE, 1.5f, 0.9f);
            }
            case "orange" -> {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 2, false, false));
                sound(player, SoundEvents.PIGLIN_ADMIRING_ITEM, 1.5f, 0.8f);
                sound(player, SoundEvents.VILLAGER_TRADE, 1.0f, 0.7f);
            }
            case "black" -> {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 3, false, false));
                sound(player, SoundEvents.SOUL_ESCAPE, 2.0f, 0.5f);
                sound(player, SoundEvents.WITHER_AMBIENT, 0.8f, 0.5f);
            }
            case "white" -> {
                player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, duration / 3, 0, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, duration + 40, 0, false, false));
                sound(player, SoundEvents.BEACON_ACTIVATE, 2.0f, 0.6f);
                sound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0f, 0.6f);
            }
            default -> { // verde e Tropas de datapack
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, duration + 20, 0, false, false));
                sound(player, SoundEvents.BEACON_ACTIVATE, 1.5f, 0.8f);
            }
        }
    }

    /** Roda a cada tick enquanto a cerimônia está acontecendo. */
    public static void tick(ServerPlayer player, RingData data) {
        if (!data.isSelectionPending()) return;

        ResourceLocation corpsId = data.pendingCorps();
        if (corpsId == null) {
            data.clearPending();
            return;
        }
        CorpsDefinition def = CorpsManager.get(corpsId);

        int left = data.tickPending();
        int total = Math.max(1, data.pendingTotal());
        float progress = 1.0f - (float) left / total;   // 0 -> 1

        switch (def.id().getPath()) {
            case "green" -> animateGreen(player, def, progress, left);
            case "red" -> animateRed(player, def, progress, left);
            case "yellow" -> animateYellow(player, def, progress, left);
            case "blue" -> animateBlue(player, def, progress, left);
            case "violet" -> animateViolet(player, def, progress, left);
            case "indigo" -> animateIndigo(player, def, progress, left);
            case "orange" -> animateOrange(player, def, progress, left);
            case "black" -> animateBlack(player, def, progress, left);
            case "white" -> animateWhite(player, def, progress, left);
            default -> animateDefault(player, def, progress, left);
        }

        if (left <= 0) {
            deliver(player, data, def);
        }
    }

    // ============================== Animações ==============================

    /** Verde: cometa vindo do céu, depois espiral fechando até a mão. */
    private static void animateGreen(ServerPlayer player, CorpsDefinition def, float progress, int left) {
        ServerLevel level = player.serverLevel();
        Vector3f rgb = PowerBatteryBlock.rgb(def.color());
        Vec3 hand = player.position().add(0, 1.2, 0);

        if (progress < 0.6f) {
            float q = progress / 0.6f;
            float eased = q * q;
            double angle = (player.getUUID().getLeastSignificantBits() & 0xFFFF) / 65535.0 * Math.PI * 2;
            Vec3 start = hand.add(Math.cos(angle) * 18, 24, Math.sin(angle) * 18);
            Vec3 pos = start.lerp(hand.add(0, 1.0, 0), eased);
            float pq = Math.max(0, q - 0.03f);
            Vec3 prev = start.lerp(hand.add(0, 1.0, 0), pq * pq);

            for (int i = 0; i < 6; i++) {
                Vec3 p = prev.lerp(pos, i / 5.0);
                level.sendParticles(new DustParticleOptions(rgb, 2.0f), p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
            }
            level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1, 0.02, 0.02, 0.02, 0.01);
            if (left % 20 == 0) {
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0f, 1.5f);
            }
        } else {
            float q = (progress - 0.6f) / 0.4f;
            spiral(level, player, rgb, Mth.lerp(q, 1.8, 0.15), Mth.lerp(q, 2.2, 1.2), left * 0.55, 3, 1.4f);
            if (left % 6 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2, player.getZ(), 2, 0.3, 0.1, 0.3, 0.01);
            }
        }
    }

    /** Vermelho: colunas de sangue subindo do chão e se fechando. */
    private static void animateRed(ServerPlayer player, CorpsDefinition def, float progress, int left) {
        ServerLevel level = player.serverLevel();
        Vector3f rgb = PowerBatteryBlock.rgb(def.color());
        Vector3f dark = new Vector3f(0.35f, 0.0f, 0.0f);

        double radius = Mth.lerp(progress, 4.0, 0.4);
        double rise = (left * 0.12) % 2.4;
        for (int i = 0; i < 8; i++) {
            double a = i * (Math.PI * 2 / 8) + progress * Math.PI;
            double x = player.getX() + Math.cos(a) * radius;
            double z = player.getZ() + Math.sin(a) * radius;
            level.sendParticles(new DustParticleOptions(rgb, 1.6f), x, player.getY() + rise, z, 1, 0, 0, 0, 0);
            level.sendParticles(new DustParticleOptions(dark, 1.2f), x, player.getY() + 0.1, z, 1, 0.1, 0, 0.1, 0);
        }
        level.sendParticles(ParticleTypes.FALLING_LAVA, player.getX(), player.getY() + 3.0, player.getZ(), 2, 2.5, 0.5, 2.5, 0);
        if (left % 25 == 0) sound(player, SoundEvents.WARDEN_HEARTBEAT, 2.0f, 0.5f);
    }

    /** Amarelo: raios amarelos em zigue-zague saem da escuridão e convergem em você. */
    private static void animateYellow(ServerPlayer player, CorpsDefinition def, float progress, int left) {
        ServerLevel level = player.serverLevel();
        Vector3f rgb = PowerBatteryBlock.rgb(def.color());
        Vec3 center = player.position().add(0, 1.0, 0);

        // Almas de sculk subindo devagar em volta (o "escuro vivo")
        level.sendParticles(ParticleTypes.SCULK_SOUL, center.x, player.getY() + 0.2, center.z, 1, 3.0, 0.1, 3.0, 0.01);

        // Raios: de longe até você, mais curtos conforme o anel chega
        if (left % 4 == 0) {
            var rnd = player.getRandom();
            double a = rnd.nextDouble() * Math.PI * 2;
            double dist = Mth.lerp(progress, 10.0, 1.5);
            Vec3 from = center.add(Math.cos(a) * dist, rnd.nextDouble() * 3 - 1, Math.sin(a) * dist);
            Vec3 cur = from;
            int steps = 8;
            for (int i = 1; i <= steps; i++) {
                Vec3 next = from.lerp(center, (double) i / steps)
                        .add((rnd.nextDouble() - 0.5) * 0.6, (rnd.nextDouble() - 0.5) * 0.6, (rnd.nextDouble() - 0.5) * 0.6);
                for (int j = 0; j < 3; j++) {
                    Vec3 p = cur.lerp(next, j / 3.0);
                    level.sendParticles(new DustParticleOptions(rgb, 1.3f), p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
                cur = next;
            }
            if (left % 16 == 0) sound(player, SoundEvents.WARDEN_TENDRIL_CLICKS, 1.5f, 1.4f);
        }

        // "Olhos" amarelos piscando no escuro em volta
        if (left % 10 == 0) {
            var rnd = player.getRandom();
            double a = rnd.nextDouble() * Math.PI * 2;
            double x = center.x + Math.cos(a) * 5, z = center.z + Math.sin(a) * 5;
            double y = player.getY() + 1.6;
            double px = -Math.sin(a) * 0.15, pz = Math.cos(a) * 0.15;
            level.sendParticles(new DustParticleOptions(rgb, 1.0f), x + px, y, z + pz, 2, 0, 0, 0, 0);
            level.sendParticles(new DustParticleOptions(rgb, 1.0f), x - px, y, z - pz, 2, 0, 0, 0, 0);
        }
    }

    /** Azul: aurora subindo do chão, como um amanhecer, com sinos. */
    private static void animateBlue(ServerPlayer player, CorpsDefinition def, float progress, int left) {
        ServerLevel level = player.serverLevel();
        Vector3f rgb = PowerBatteryBlock.rgb(def.color());
        Vector3f pale = new Vector3f(0.7f, 0.85f, 1.0f);

        // Cortina de luz em um círculo grande, subindo em ondas
        double radius = 3.5;
        for (int i = 0; i < 6; i++) {
            double a = (left * 0.05) + i * (Math.PI * 2 / 6);
            double x = player.getX() + Math.cos(a) * radius;
            double z = player.getZ() + Math.sin(a) * radius;
            double h = (left * 0.08 + i) % 4.0;
            level.sendParticles(new DustParticleOptions(i % 2 == 0 ? rgb : pale, 1.5f), x, player.getY() + h, z, 1, 0.05, 0.05, 0.05, 0);
        }
        // Gotas de luz subindo (chuva ao contrário)
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.1, player.getZ(), 1, 2.5, 0, 2.5, 0.03);
        if (progress > 0.6f) {
            float q = (progress - 0.6f) / 0.4f;
            spiral(level, player, rgb, Mth.lerp(q, 2.0, 0.2), 1.2, left * 0.5, 2, 1.3f);
        }
        if (left % 30 == 0) sound(player, SoundEvents.BELL_RESONATE, 1.0f, 1.5f);
    }

    /** Violeta: pétalas e corações caindo em espiral. */
    private static void animateViolet(ServerPlayer player, CorpsDefinition def, float progress, int left) {
        ServerLevel level = player.serverLevel();
        Vector3f rgb = PowerBatteryBlock.rgb(def.color());
        Vector3f pink = new Vector3f(1.0f, 0.6f, 0.85f);

        level.sendParticles(ParticleTypes.CHERRY_LEAVES, player.getX(), player.getY() + 4.0, player.getZ(), 2, 2.5, 0.3, 2.5, 0);
        double radius = Mth.lerp(progress, 2.6, 0.3);
        double height = Mth.lerp(progress, 3.0, 1.2);
        for (int arm = 0; arm < 2; arm++) {
            double a = left * 0.35 + arm * Math.PI;
            level.sendParticles(new DustParticleOptions(arm == 0 ? rgb : pink, 1.4f),
                    player.getX() + Math.cos(a) * radius, player.getY() + height, player.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
        if (left % 8 == 0) {
            level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 2.2, player.getZ(), 1, 0.8, 0.3, 0.8, 0);
        }
        if (left % 24 == 0) sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 1.6f);
    }

    /** Índigo: feixe de luz descendo do céu, ondas índigo se espalhando pelo chão. */
    private static void animateIndigo(ServerPlayer player, CorpsDefinition def, float progress, int left) {
        ServerLevel level = player.serverLevel();
        Vector3f rgb = PowerBatteryBlock.rgb(def.color());

        // Feixe: desce do céu até a cabeça
        double top = player.getY() + Mth.lerp(Math.min(1f, progress * 2), 20.0, 2.0);
        for (double y = top; y < player.getY() + 22; y += 1.2) {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), y, player.getZ(), 1, 0.05, 0.2, 0.05, 0);
        }
        // Ondas no chão, uma a cada segundo
        double ring = ((left % 20) / 20.0) * 4.5;
        for (int i = 0; i < 16; i++) {
            double a = i * (Math.PI * 2 / 16);
            level.sendParticles(new DustParticleOptions(rgb, 1.2f),
                    player.getX() + Math.cos(a) * (4.6 - ring), player.getY() + 0.1, player.getZ() + Math.sin(a) * (4.6 - ring), 1, 0, 0, 0, 0);
        }
        if (left % 20 == 0) sound(player, SoundEvents.CONDUIT_AMBIENT, 1.5f, 1.2f);
    }

    /** Laranja: chuva de ouro girando cada vez mais rápido. */
    private static void animateOrange(ServerPlayer player, CorpsDefinition def, float progress, int left) {
        ServerLevel level = player.serverLevel();
        Vector3f rgb = PowerBatteryBlock.rgb(def.color());
        ItemParticleOption gold = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET));
        ItemParticleOption ingot = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GOLD_INGOT));

        double speed = Mth.lerp(progress, 0.15, 0.9);
        double radius = Mth.lerp(progress, 3.0, 0.6);
        for (int i = 0; i < 4; i++) {
            double a = left * speed + i * (Math.PI / 2);
            double h = 0.3 + ((left * 0.05 + i * 0.5) % 2.2);
            level.sendParticles(i % 2 == 0 ? gold : ingot,
                    player.getX() + Math.cos(a) * radius, player.getY() + h, player.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0);
            level.sendParticles(new DustParticleOptions(rgb, 1.3f),
                    player.getX() + Math.cos(a) * radius, player.getY() + h, player.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
        level.sendParticles(gold, player.getX(), player.getY() + 4.0, player.getZ(), 2, 2.0, 0.2, 2.0, 0.05);
        if (left % 6 == 0) sound(player, SoundEvents.ITEM_PICKUP, 0.6f, 0.5f + progress);
    }

    /** Negro: almas saindo da terra e girando até virar um anel negro. */
    private static void animateBlack(ServerPlayer player, CorpsDefinition def, float progress, int left) {
        ServerLevel level = player.serverLevel();
        Vector3f dark = new Vector3f(0.08f, 0.08f, 0.1f);
        Vector3f rgb = PowerBatteryBlock.rgb(def.color());

        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 0.1, player.getZ(), 2, 3.0, 0, 3.0, 0.02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.1, player.getZ(), 1, 2.0, 0, 2.0, 0.01);
        double radius = Mth.lerp(progress, 3.0, 0.3);
        for (int arm = 0; arm < 4; arm++) {
            double a = -left * 0.3 + arm * (Math.PI / 2);
            double h = Mth.lerp(progress, 0.2, 1.2);
            level.sendParticles(new DustParticleOptions(arm % 2 == 0 ? dark : rgb, 1.8f),
                    player.getX() + Math.cos(a) * radius, player.getY() + h, player.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
        if (left % 20 == 0) sound(player, SoundEvents.SOUL_ESCAPE, 2.0f, 0.4f);
    }

    /** Branco: coluna de luz branca e um clarão de vida. */
    private static void animateWhite(ServerPlayer player, CorpsDefinition def, float progress, int left) {
        ServerLevel level = player.serverLevel();
        Vector3f rgb = PowerBatteryBlock.rgb(def.color());

        // Coluna de luz até o céu, ficando mais larga
        double width = Mth.lerp(progress, 0.2, 1.2);
        for (double y = 0; y < 24; y += 1.0) {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + y, player.getZ(), 1, width * 0.3, 0.3, width * 0.3, 0);
        }
        // Raios saindo do corpo para fora
        for (int i = 0; i < 6; i++) {
            double a = left * 0.2 + i * (Math.PI / 3);
            double r = ((left * 0.15) % 4.0);
            level.sendParticles(new DustParticleOptions(rgb, 1.6f),
                    player.getX() + Math.cos(a) * r, player.getY() + 1.0, player.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        if (left % 5 == 0) {
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.6, 0.8, 0.6, 0.3);
        }
        if (left % 25 == 0) sound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0f, 1.8f);
    }

    /** Padrão para Tropas novas criadas por datapack. */
    private static void animateDefault(ServerPlayer player, CorpsDefinition def, float progress, int left) {
        spiral(player.serverLevel(), player, PowerBatteryBlock.rgb(def.color()),
                Mth.lerp(progress, 2.5, 0.2), 0.2 + progress * 1.2, left * 0.4, 2, 1.3f);
    }

    private static void spiral(ServerLevel level, ServerPlayer player, Vector3f rgb, double radius, double height,
                               double angle, int arms, float size) {
        for (int arm = 0; arm < arms; arm++) {
            double a = angle + arm * (Math.PI * 2 / arms);
            level.sendParticles(new DustParticleOptions(rgb, size),
                    player.getX() + Math.cos(a) * radius, player.getY() + height, player.getZ() + Math.sin(a) * radius,
                    1, 0, 0, 0, 0);
        }
    }

    // ============================== Entrega ==============================

    private static void deliver(ServerPlayer player, RingData data, CorpsDefinition def) {
        String reason = REASON.remove(player.getUUID());
        data.clearPending();
        data.resetRage();
        data.resetRituals();
        data.setEverChosen(true);
        data.setPowerActive(true);

        // O anel vai direto para o slot de anel, já ligado ao portador
        // O Anel Apagado desperta fraco: só 10% de carga
        int startCharge = "dormant".equals(reason) ? Math.max(1, def.maxCharge() / 10) : def.maxCharge();
        ItemStack ring = PowerRingItem.create(def.id(), def.color(), startCharge);
        PowerRingItem.setOwner(ring, player);
        EquipmentHelper.equipOrGive(player, RingData.RING_SLOT, ring);

        // A bateria da Tropa vem junto, no slot de bateria
        // (a Tropa da Vida não tem bateria: o anel branco não descarrega)
        ItemStack battery = PowerBatteryItem.create(def.id(), def.color());
        if (!battery.isEmpty()) EquipmentHelper.equipOrGive(player, RingData.BATTERY_SLOT, battery);

        ServerLevel level = player.serverLevel();
        Vector3f rgb = PowerBatteryBlock.rgb(def.color());
        level.sendParticles(new DustParticleOptions(rgb, 2.5f), player.getX(), player.getY() + 1.0, player.getZ(),
                120, 0.8, 1.0, 0.8, 0.15);
        level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.2, player.getZ(), 1, 0, 0, 0, 0);
        sound(player, SoundEvents.BEACON_POWER_SELECT, 2.0f, 1.0f);
        sound(player, SoundEvents.TOTEM_USE, 0.6f, 1.4f);

        switch (def.id().getPath()) {
            case "red" -> {
                sound(player, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0f, 0.6f);
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 60, 1));
            }
            case "yellow" -> {
                player.removeEffect(MobEffects.DARKNESS);
                sound(player, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.5f);
            }
            case "blue" -> player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 30, 1));
            case "violet" -> player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 60, 1));
            case "indigo" -> player.addEffect(new MobEffectInstance(MobEffects.HEAL, 1, 1));
            case "orange" -> player.addEffect(new MobEffectInstance(MobEffects.LUCK, 20 * 300, 1));
            case "black" -> player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60, 1));
            case "white" -> {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 30, 2));
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 10, 0));
            }
            default -> {}
        }

        String base = "selection." + def.id().getNamespace() + "." + def.id().getPath();
        if (reason != null) {
            player.sendSystemMessage(Component.translatable("selection.lanterna.reason." + reason,
                    Component.translatable(CorpsDefinition.nameKey(def.id()))).withStyle(s -> s.withColor(def.color())));
        } else {
            player.sendSystemMessage(Component.translatable(base + ".message").withStyle(s -> s.withColor(def.color())));
        }
        player.sendSystemMessage(Component.translatable("message.lanterna.tutorial"));
        RingPowers.sync(player, data);

        // A lore começa: missão inicial da Tropa, e o anel leva o novo Lanterna direto para o planeta dela
        com.lanterna.mission.MissionManager.startCorpsLine(player, def.id());
        com.lanterna.lore.PlanetLore home = com.lanterna.lore.PlanetLore.byCorps(def.id());
        if (home != null && home != com.lanterna.lore.PlanetLore.GENESE
                && com.lanterna.lore.PlanetLore.byDimension(player.level().dimension()) != home) {
            boolean green = def.id().equals(com.lanterna.corps.BuiltinCorps.GREEN.id());
            player.sendSystemMessage((green ? Component.translatable("message.lanterna.green_to_oa")
                    : Component.translatable("message.lanterna.ring_to_home", Component.translatable("planet.lanterna." + home.planet)))
                    .withStyle(s -> s.withColor(def.color()).withItalic(true)));
            ModNetwork.sendTo(player, new com.lanterna.network.lore.StartAutopilotPacket(home.planet, 60));
            RingPowers.setHoming(player, true);
        }
    }

    private static void sound(ServerPlayer player, SoundEvent event, float volume, float pitch) {
        player.level().playSound(null, player.blockPosition(), event, SoundSource.PLAYERS, volume, pitch);
    }

    private SelectionManager() {}
}
