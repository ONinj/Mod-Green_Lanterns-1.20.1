package com.lanterna.lore.ritual;

import com.lanterna.LanternaMod;
import com.lanterna.block.PowerBatteryBlock;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.CorpsDefinition;
import com.lanterna.corps.CorpsManager;
import com.lanterna.item.BatteryItems;
import com.lanterna.item.PowerRingItem;
import com.lanterna.lore.LoreItems;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import com.lanterna.power.EquipmentHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Forja do Espectro (uma em cada planeta): forja o anel da Tropa do planeta.
 *
 * Precisa de: Anel Vazio + o drop do boss do planeta + 4 do minério do planeta, e o ritual da emoção
 * (10 segundos parado perto da forja, cumprindo a condição o tempo todo):
 *
 *   Odym     (esperança)  céu aberto, de noite ou na chuva, com a vida cheia
 *   Oa       (vontade)    ter vencido o Vigia Partido; ficar firme (sem se mexer)
 *   Okaara   (avareza)    pagar 16 lingotes de ouro de tributo, sem ninguém por perto para dividir
 *   Ysmault  (fúria)      só forja em quem sangra: vida pela metade ou menos
 *   Zamaron  (amor)       outro jogador ou um bicho domado perto de você
 *   Korugar  (medo)       dez segundos dentro da escuridão, sem fugir
 *   Nok      (compaixão)  não ter matado nenhum animal pacífico nos últimos 3 dias do jogo
 *   Ryut     (morte)      à beira da morte: 2 corações ou menos
 *
 * Com o drop do boss da SUA Tropa na mão, a forja melhora a sua bateria (anel com 50% a mais de carga).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeRituals {
    public static final int RITUAL_TICKS = 200;
    private static final Map<UUID, Ritual> ACTIVE = new HashMap<>();

    private record Ritual(PlanetLore planet, BlockPos forge, Vec3 start, int[] ticks) {}

    public static void use(ServerPlayer player, BlockPos pos) {
        PlanetLore planet = PlanetLore.byDimension(player.level().dimension());
        if (planet == null || planet == PlanetLore.GENESE) {
            player.displayClientMessage(Component.translatable("message.lanterna.forge_wrong_place"), true);
            return;
        }
        if (ACTIVE.containsKey(player.getUUID())) return;

        // Melhoria da bateria com o drop do boss da própria Tropa
        RingData ring = ModCapabilities.get(player).orElse(null);
        ItemStack held = player.getMainHandItem();
        if (ring != null && ring.hasRing()) {
            PlanetLore own = PlanetLore.byCorps(ring.corps());
            ItemStack battery = EquipmentHelper.battery(player);
            if (own != null && held.is(own.dropItem()) && BatteryItems.isBattery(battery)) {
                if (battery.getOrCreateTag().getBoolean("Upgraded")) {
                    player.displayClientMessage(Component.translatable("message.lanterna.battery_already_upgraded"), true);
                    return;
                }
                if (!player.isCreative()) held.shrink(1);
                battery.getOrCreateTag().putBoolean("Upgraded", true);
                burst(player.serverLevel(), pos, own.color());
                player.sendSystemMessage(Component.translatable("message.lanterna.battery_upgraded").withStyle(s -> s.withColor(own.color()).withBold(true)));
                return;
            }
        }

        if (planet == PlanetLore.RYUT && !player.isCreative() && !com.lanterna.morte.Corrupcao.podeForjar(player)) {
            player.sendSystemMessage(Component.translatable("morte.lanterna.forja_recusa").withStyle(s -> s.withColor(planet.color()).withItalic(true)));
            return;
        }
        int req = com.lanterna.tropa.Nivel.nivelParaForjar(planet);
        if (!player.isCreative() && com.lanterna.tropa.Nivel.nivel(player) < req) {
            player.displayClientMessage(Component.translatable("nivel.lanterna.precisa_forja", req), true);
            return;
        }
        if (MissionManager.hasRing(player, planet.corps.id().getPath())) {
            player.displayClientMessage(Component.translatable("message.lanterna.forge_has_ring", planet.planetName()), true);
            return;
        }
        boolean creative = player.isCreative();
        int empty = MissionManager.countItem(player, LoreItems.ANEL_VAZIO.get());
        int drops = MissionManager.countItem(player, planet.dropItem());
        int ores = MissionManager.countItem(player, planet.oreItem());
        int gold = MissionManager.countItem(player, Items.GOLD_INGOT);
        if (!creative && (empty < 1 || drops < 1 || ores < 4 || (planet == PlanetLore.OKAARA && gold < 16))) {
            player.sendSystemMessage(Component.translatable("message.lanterna.forge_needs", planet.dropItem().getDescription(),
                    planet.oreItem().getDescription()).withStyle(s -> s.withColor(planet.color())));
            player.sendSystemMessage(Component.translatable("ritual.lanterna." + planet.planet + ".rule").withStyle(s -> s.withItalic(true)));
            return;
        }
        Component why = check(player, planet);
        if (why != null) {
            player.sendSystemMessage(why.copy().withStyle(s -> s.withColor(planet.color())));
            return;
        }
        ACTIVE.put(player.getUUID(), new Ritual(planet, pos, player.position(), new int[]{0}));
        player.displayClientMessage(Component.translatable("ritual.lanterna.started").withStyle(s -> s.withColor(planet.color())), true);
        player.level().playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5f, 0.7f);
    }

    /** Condição do ritual (null = ok). */
    private static Component check(ServerPlayer p, PlanetLore planet) {
        ServerLevel level = p.serverLevel();
        boolean ok = switch (planet) {
            case ODYM -> {
                boolean sky = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, p.blockPosition()).getY() <= p.getBlockY() + 1;
                yield sky && (level.isRaining() || level.isNight()) && p.getHealth() >= p.getMaxHealth() - 0.5f;
            }
            case OA -> MissionManager.data(p) != null && MissionManager.data(p).bossKills().contains("vigia_partido");
            case OKAARA -> level.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(32), o -> o != p && !o.isSpectator()).isEmpty();
            case YSMAULT -> p.getHealth() <= p.getMaxHealth() * 0.5f;
            case ZAMARON -> !level.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(8), o -> o != p && !o.isSpectator()).isEmpty()
                    || !level.getEntitiesOfClass(TamableAnimal.class, p.getBoundingBox().inflate(8), t -> t.isTame() && p.getUUID().equals(t.getOwnerUUID())).isEmpty();
            case KORUGAR -> true;
            case NOK -> {
                LoreData d = MissionManager.data(p);
                yield d != null && level.getGameTime() - d.lastPassiveKill() > 72000;
            }
            case RYUT -> p.getHealth() <= 4.0f;
            default -> true;
        };
        return ok || p.isCreative() ? null : Component.translatable("ritual.lanterna." + planet.planet + ".rule");
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Ritual r = ACTIVE.get(player.getUUID());
        if (r == null) return;
        ServerLevel level = player.serverLevel();
        int t = ++r.ticks()[0];
        boolean moved = player.position().distanceTo(r.start()) > 2.5 || !level.dimension().equals(player.level().dimension());
        Component why = check(player, r.planet());
        if (moved || why != null || player.isDeadOrDying()) {
            ACTIVE.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("ritual.lanterna.failed")
                    .withStyle(s -> s.withColor(0xFF6060)), true);
            if (why != null) player.sendSystemMessage(why);
            return;
        }
        if (r.planet() == PlanetLore.KORUGAR) player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
        // Espiral de luz em volta, apertando
        int c = r.planet().color();
        float k = t / (float) RITUAL_TICKS;
        double radius = 2.5 * (1 - k) + 0.3;
        for (int arm = 0; arm < 3; arm++) {
            double a = t * 0.25 + arm * Math.PI * 2 / 3;
            level.sendParticles(dust(c, 1.4f), player.getX() + Math.cos(a) * radius, player.getY() + 0.2 + k * 1.8,
                    player.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
        if (t % 20 == 0) {
            player.displayClientMessage(Component.translatable("ritual.lanterna.progress", (RITUAL_TICKS - t) / 20)
                    .withStyle(s -> s.withColor(c)), true);
            level.playSound(null, r.forge(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1f, 0.6f + k);
        }
        if (t >= RITUAL_TICKS) {
            ACTIVE.remove(player.getUUID());
            finish(player, r);
        }
    }

    private static void finish(ServerPlayer player, Ritual r) {
        PlanetLore planet = r.planet();
        if (!player.isCreative()) {
            MissionManager.removeItems(player, LoreItems.ANEL_VAZIO.get(), 1);
            MissionManager.removeItems(player, planet.dropItem(), 1);
            MissionManager.removeItems(player, planet.oreItem(), 4);
            if (planet == PlanetLore.OKAARA) MissionManager.removeItems(player, Items.GOLD_INGOT, 16);
        }
        CorpsDefinition def = CorpsManager.get(planet.corps.id());
        ItemStack ring = PowerRingItem.create(def.id(), def.color(), def.maxCharge());
        PowerRingItem.setOwner(ring, player);
        give(player, ring);
        ItemStack battery = BatteryItems.create(def.id(), def.color());
        if (!battery.isEmpty()) give(player, battery);
        burst(player.serverLevel(), r.forge(), planet.color());
        player.sendSystemMessage(Component.translatable("ritual.lanterna.forged", Component.translatable(CorpsDefinition.fullNameKey(def.id())))
                .withStyle(s -> s.withColor(planet.color()).withBold(true)));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                Component.translatable(CorpsDefinition.nameKey(def.id())).withStyle(s -> s.withColor(planet.color()).withBold(true))));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                Component.translatable("ritual.lanterna.forged_sub")));
        LoreData d = MissionManager.data(player);
        if (d != null) MissionManager.evaluate(player, d);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    static void burst(ServerLevel level, BlockPos pos, int color) {
        level.sendParticles(dust(color, 2.5f), pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 150, 1, 1.5, 1, 0.2);
        level.sendParticles(ParticleTypes.FLASH, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 60, 0.3, 3, 0.3, 0.1);
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 2f, 1f);
        level.playSound(null, pos, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 0.8f, 1.2f);
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1f, 0.6f);
    }

    static DustParticleOptions dust(int c, float size) {
        return new DustParticleOptions(new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f), size);
    }

    private ForgeRituals() {}
}
