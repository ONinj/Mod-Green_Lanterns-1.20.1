package com.lanterna.client.devtest;

import com.lanterna.LanternaMod;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.OpenLanternMenuPacket;
import com.lanterna.network.ToggleFlightPacket;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import com.lanterna.client.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * SÓ PARA TESTE (não vai no .jar): roda um roteiro dentro do jogo e tira prints.
 * Ativado com -Dlanterna.autotest=true.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DevAutoTest {
    private static final boolean ENABLED = Boolean.getBoolean("lanterna.autotest");
    private static final List<Step> STEPS = new ArrayList<>();
    private static int tick;
    private static int stepIndex;
    private static int waitUntil;

    private record Step(int delay, Runnable action) {}

    // Câmera lateral de teste: um suporte de armadura invisível acompanhando o jogador
    private static net.minecraft.world.entity.decoration.ArmorStand sideCam;
    private static double camSide = 90, camDist = 4.5, camUp = 0.6;

    private static void sideCamera(boolean on, double side, double dist, double up) {
        Minecraft mc = Minecraft.getInstance();
        if (!on) { mc.setCameraEntity(mc.player); sideCam = null; return; }
        camSide = side; camDist = dist; camUp = up;
        sideCam = new net.minecraft.world.entity.decoration.ArmorStand(net.minecraft.world.entity.EntityType.ARMOR_STAND, mc.level);
        sideCam.setInvisible(true);
        updateSideCam();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(sideCam);
    }

    private static void updateSideCam() {
        Minecraft mc = Minecraft.getInstance();
        if (sideCam == null || mc.player == null) return;
        double yaw = Math.toRadians(mc.player.getYRot() + camSide);
        double ox = -Math.sin(yaw) * camDist, oz = Math.cos(yaw) * camDist;
        double oy = camUp - sideCam.getEyeHeight() + 0.9;
        // Posição atual e anterior seguem o jogador (para a câmera não "ficar para trás" em alta velocidade)
        sideCam.setPos(mc.player.getX() + ox, mc.player.getY() + oy, mc.player.getZ() + oz);
        sideCam.xo = sideCam.xOld = mc.player.xo + ox;
        sideCam.yo = sideCam.yOld = mc.player.yo + oy;
        sideCam.zo = sideCam.zOld = mc.player.zo + oz;
        double dy = 0.9 - (oy + sideCam.getEyeHeight());
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(ox * ox + oz * oz)));
        float look = (float) Math.toDegrees(Math.atan2(ox, -oz));
        sideCam.setYRot(look); sideCam.yRotO = look; sideCam.setYHeadRot(look);
        sideCam.setXRot(pitch); sideCam.xRotO = pitch;
    }

    private static void at(int delay, Runnable action) { STEPS.add(new Step(delay, action)); }

    private static void cmd(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.connection.sendCommand(command);
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "lanterna_" + name + ".png", mc.getMainRenderTarget(), msg -> {});
        LanternaMod.LOGGER.info("AUTOTEST shot {} dim={} pos={} screen={}", name,
                mc.player != null ? mc.player.level().dimension().location() : null,
                mc.player != null ? mc.player.position() : null, mc.screen);
        if (mc.player != null) LanternaMod.LOGGER.info("AUTOTEST state fwd={} up={} flight={} active={} auto={} hyper={} speed={} chunk={}",
                mc.player.input.forwardImpulse, mc.options.keyUp.isDown(), com.lanterna.client.ClientRingData.flight,
                com.lanterna.client.ClientRingData.active, com.lanterna.client.flight.Autopilot.active(),
                com.lanterna.client.flight.ClientFlight.hyperOn(), com.lanterna.client.flight.ClientFlight.localSpeed(),
                mc.level.hasChunkAt(mc.player.getBlockX(), mc.player.getBlockZ()));
    }

    private static void look(float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.setYRot(yaw); mc.player.setXRot(pitch);
        mc.player.yRotO = yaw; mc.player.xRotO = pitch;
        mc.player.setYHeadRot(yaw); mc.player.setYBodyRot(yaw);
    }

    private static void hold(KeyMapping key, boolean down) {
        KeyMapping.set(key.getKey(), down);
    }

    private static void camera(CameraType type) {
        Minecraft.getInstance().options.setCameraType(type);
    }

    private static java.util.function.BooleanSupplier waitCond;
    private static int waitLimit;

    /** Espera uma condição (no máximo maxTicks). */
    private static void waitFor(java.util.function.BooleanSupplier cond, int maxTicks) {
        STEPS.add(new Step(1, () -> { waitCond = cond; waitLimit = tick + maxTicks; }));
    }

    private static final java.util.concurrent.atomic.AtomicInteger PENDING = new java.util.concurrent.atomic.AtomicInteger();

    /** Roda no servidor; os próximos passos esperam ele terminar (o servidor pode atrasar). */
    private static void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> fn) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        PENDING.incrementAndGet();
        server.execute(() -> {
            try { fn.accept(server.getPlayerList().getPlayers().get(0)); }
            finally { PENDING.decrementAndGet(); }
        });
    }

    private static void logState(String tag) {
        server(sp -> com.lanterna.mission.LoreCapability.get(sp).ifPresent(d ->
                LanternaMod.LOGGER.info("AUTOTEST lore {} active={} done={} rep={} aff={}", tag,
                        d.active().keySet(), d.completed(), d.reps(), d.affinity())));
    }

    private static <T extends net.minecraft.world.entity.Entity> T nearest(Class<T> cls, net.minecraft.server.level.ServerPlayer sp, double r,
                                                                          java.util.function.Predicate<T> filter) {
        T best = null;
        double bd = 1e18;
        for (T e : sp.level().getEntitiesOfClass(cls, sp.getBoundingBox().inflate(r), filter)) {
            double d = e.distanceToSqr(sp);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    private static void construct(String id, boolean start) {
        ModNetwork.sendToServer(new com.lanterna.network.lore.ConstructCastPacket(id, start));
    }

    private static final String SCENE = System.getProperty("lanterna.autotest.scene", "full");

    /** Cena curta: cratera do Anel Caído, anel apagado + bateria e o Chamado por conquistas. */
    private static void sceneCaido() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(60, () -> {
            mc.options.hideGui = false;
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd("gamemode creative"); cmd("time set 6000"); cmd("weather clear");
            cmd("gamerule doMobSpawning false"); cmd("kill @e[type=!player]");
            cmd("lanterna resetar"); cmd("clear @s");
            cmd("execute in minecraft:overworld run tp @s 400.5 -40 400.5");
        });
        waitFor(() -> mc.level.hasChunkAt(400, 400) && mc.player.blockPosition().getY() < -45, 400);
        // Mundo de teste é superplano (raso demais): monta um chão alto e constrói a cratera direto
        at(20, () -> server(sp -> {
            var src = sp.createCommandSourceStack().withSuppressedOutput().withPermission(4);
            sp.server.getCommands().performPrefixedCommand(src, "fill 380 -63 380 420 -46 420 minecraft:stone");
            sp.server.getCommands().performPrefixedCommand(src, "fill 380 -45 380 420 -45 420 minecraft:grass_block");
            sp.server.getCommands().performPrefixedCommand(src, "fill 380 -44 380 420 -32 420 minecraft:air");
            sp.server.getCommands().performPrefixedCommand(src, "fill 380 -31 380 420 -15 420 minecraft:air");
            var piece = new com.lanterna.worldgen.FallenRingPiece(new BlockPos(400, -45, 400), 0);
            piece.postProcess(sp.serverLevel(), sp.serverLevel().structureManager(), sp.serverLevel().getChunkSource().getGenerator(),
                    sp.getRandom(), piece.getBoundingBox(), new net.minecraft.world.level.ChunkPos(25, 25), new BlockPos(400, -45, 400));
            StringBuilder col = new StringBuilder();
            for (int y = -50; y <= -40; y++) col.append(y).append('=').append(sp.level().getBlockState(new BlockPos(403, y, 400)).getBlock()).append(' ');
            LanternaMod.LOGGER.info("AUTOTEST crater col {} top={}", col, sp.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, 403, 400));
        }));
        at(40, () -> { cmd("gamemode spectator"); cmd("tp @s 400.5 -33 419.5 180 38"); });
        at(160, () -> shot("caido1_crater"));
        at(5, () -> cmd("tp @s 400.5 -18 400.5 0 90"));
        at(100, () -> shot("caido2_top"));
        at(5, () -> cmd("gamemode creative"));
        at(5, () -> server(sp -> {
            BlockPos chest = null;
            for (BlockPos p : BlockPos.betweenClosed(396, -55, 396, 404, -40, 404))
                if (sp.level().getBlockState(p).is(net.minecraft.world.level.block.Blocks.CHEST)) { chest = p.immutable(); break; }
            LanternaMod.LOGGER.info("AUTOTEST chest={}", chest);
            if (chest != null && sp.level().getBlockEntity(chest) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity be) {
                be.unpackLootTable(sp);
                for (int i = 0; i < be.getContainerSize(); i++) {
                    var st = be.getItem(i);
                    if (!st.isEmpty()) LanternaMod.LOGGER.info("AUTOTEST chest item {} {}", st, st.getTag());
                    if (st.getItem() instanceof com.lanterna.item.DormantRingItem) { sp.getInventory().add(st.copy()); be.setItem(i, net.minecraft.world.item.ItemStack.EMPTY); }
                }
                net.minecraft.core.Direction f = be.getBlockState().getValue(net.minecraft.world.level.block.ChestBlock.FACING);
                BlockPos eye = chest.relative(f, 3);
                sp.teleportTo(eye.getX() + 0.5, eye.getY(), eye.getZ() + 0.5);
                sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, net.minecraft.world.phys.Vec3.atCenterOf(chest));
            }
            sp.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        }));
        at(5, () -> {});
        at(30, () -> shot("caido3_meteor"));
        at(5, () -> {
            var st = mc.player.getInventory().items.stream().filter(x -> x.getItem() instanceof com.lanterna.item.DormantRingItem).findFirst().orElse(null);
            if (st != null) for (var line : st.getTooltipLines(mc.player, net.minecraft.world.item.TooltipFlag.NORMAL))
                LanternaMod.LOGGER.info("AUTOTEST tooltip {}", line.getString());
        });
        // A Bateria Cinzelada: o anel encosta, a bateria quebra e o anel escolhe uma cor
        at(5, () -> server(sp -> {
            var ringStack = sp.getInventory().items.stream().filter(x -> x.getItem() instanceof com.lanterna.item.DormantRingItem).findFirst().orElse(null);
            if (ringStack == null) { LanternaMod.LOGGER.warn("AUTOTEST sem anel apagado"); return; }
            sp.teleportTo(400.5, -44, 418.5);
            sp.setYRot(180);
            BlockPos at = new BlockPos(400, -44, 416);
            sp.level().setBlock(at, com.lanterna.registry.ModBlocks.CHISELED_BATTERY.get().defaultBlockState(), 3);
            sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ringStack.copy()); ringStack.setCount(0);
        }));
        at(40, () -> shot("caido4_battery"));
        at(5, () -> server(sp -> {
            BlockPos at = new BlockPos(400, -44, 416);
            var res = sp.level().getBlockState(at).use(sp.level(), sp, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at), net.minecraft.core.Direction.UP, at, false));
            LanternaMod.LOGGER.info("AUTOTEST chiseled use={} batteryLeft={} pending={}", res, sp.level().getBlockState(at),
                    com.lanterna.capability.ModCapabilities.get(sp).map(d -> d.isSelectionPending()).orElse(false));
        }));
        at(30, () -> shot("caido5_energize"));
        at(150, () -> { shot("caido6_chosen"); server(sp -> {
            var d = com.lanterna.capability.ModCapabilities.get(sp).orElse(null);
            LanternaMod.LOGGER.info("AUTOTEST after awaken hasRing={} corps={} charge={}/{}", d.hasRing(), d.corps(), d.charge(), d.maxCharge());
            com.lanterna.mission.LoreCapability.get(sp).ifPresent(l -> LanternaMod.LOGGER.info("AUTOTEST lore active={}", l.active().keySet()));
        }); });
        // O anel leva o novo Lanterna ao planeta da Tropa
        at(10, () -> LanternaMod.LOGGER.info("AUTOTEST autopilot={}", com.lanterna.client.flight.Autopilot.active()));
        waitFor(() -> { String d = mc.player.level().dimension().location().getPath(); return !d.equals("overworld") && !d.equals("space"); }, 7000);
        waitFor(() -> !com.lanterna.client.flight.Autopilot.active(), 1500);
        at(60, () -> { shot("home1_arrived"); LanternaMod.LOGGER.info("AUTOTEST home={} charge={}", mc.player.level().dimension().location(),
                com.lanterna.client.ClientRingData.charge); });
        // Guardião Emissário na base
        at(5, () -> server(sp -> sp.teleportTo(0.5, sp.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0) + 1, 0.5)));
        at(80, () -> server(sp -> {
            var g = nearest(com.lanterna.lore.entity.RaceEntity.class, sp, 60, r -> r.race() == com.lanterna.lore.entity.Race.GUARDIAO);
            LanternaMod.LOGGER.info("AUTOTEST guardian={}", g);
            if (g != null) {
                sp.teleportTo(g.getX(), g.getY(), g.getZ() - 4);
                sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, g.position().add(0, 1.5, 0));
            }
        }));
        at(40, () -> shot("home2_guardian"));
        at(5, () -> server(sp -> {
            var g = nearest(com.lanterna.lore.entity.RaceEntity.class, sp, 20, r -> r.race() == com.lanterna.lore.entity.Race.GUARDIAO);
            if (g != null) com.lanterna.lore.entity.NpcDialogs.open(sp, g);
        }));
        waitFor(() -> mc.screen != null, 200);
        at(20, () -> shot("home3_dialog"));
        at(5, () -> { mc.setScreen(null); server(sp -> com.lanterna.mission.LoreCapability.get(sp).ifPresent(l ->
                LanternaMod.LOGGER.info("AUTOTEST after guardian active={} done={}", l.active().keySet(), l.completed()))); });
        // Inimizade: um morador de uma Tropa inimiga ataca; soco com afinidade
        at(5, () -> server(sp -> {
            var mine = com.lanterna.lore.Factions.playerCorps(sp);
            for (var r : com.lanterna.lore.entity.Race.values()) {
                if (r == com.lanterna.lore.entity.Race.GUARDIAO) continue;
                LanternaMod.LOGGER.info("AUTOTEST faction {} ({}) hates {} = {}", r, com.lanterna.lore.Factions.raceCorps(r), mine,
                        com.lanterna.lore.Factions.raceHatesPlayer(r, sp));
            }
            var z = net.minecraft.world.entity.EntityType.IRON_GOLEM.create(sp.serverLevel());
            z.moveTo(sp.getX() + 2, sp.getY(), sp.getZ()); z.setNoAi(true); z.addTag("punch"); sp.serverLevel().addFreshEntity(z);
        }));
        at(30, () -> server(sp -> {
            var z = nearest(net.minecraft.world.entity.animal.IronGolem.class, sp, 8, e -> e.getTags().contains("punch"));
            if (z == null) return;
            float before = z.getHealth(); sp.attack(z);
            var rd = com.lanterna.capability.ModCapabilities.get(sp).orElse(null);
            LanternaMod.LOGGER.info("AUTOTEST punch lvl0={} ring={} active={} charge={} hand={}", before - z.getHealth(), rd.hasRing(), rd.powerActive(), rd.charge(), sp.getMainHandItem());
            com.lanterna.construct.Affinity.add(sp, 30000);
        }));
        at(30, () -> server(sp -> {
            var z = nearest(net.minecraft.world.entity.animal.IronGolem.class, sp, 8, e -> e.getTags().contains("punch"));
            if (z == null) return;
            z.invulnerableTime = 0; float before = z.getHealth(); sp.attack(z);
            LanternaMod.LOGGER.info("AUTOTEST punch lvl5={}", before - z.getHealth());
            z.discard();
            sp.setHealth(10); sp.getFoodData().setFoodLevel(10);
        }));
        at(200, () -> server(sp -> LanternaMod.LOGGER.info("AUTOTEST passives health={} food={}", sp.getHealth(), sp.getFoodData().getFoodLevel())));
        at(5, () -> mc.setScreen(com.lanterna.client.mission.JournalScreen.ringTab()));
        at(20, () -> shot("home4_journal"));
        at(5, () -> mc.setScreen(null));
        at(10, () -> cmd("execute in minecraft:overworld run tp @s 410.5 -40 410.5"));
        at(60, () -> {});
        // O Chamado por conquistas
        at(40, () -> { cmd("lanterna resetar"); cmd("advancement revoke @s everything"); });
        at(20, () -> cmd("advancement grant @s only minecraft:story/mine_diamond"));
        at(20, () -> cmd("advancement grant @s only minecraft:story/enter_the_nether"));
        at(20, () -> shot("call1_sign"));
        at(5, () -> cmd("advancement grant @s only minecraft:story/enchant_item"));
        at(40, () -> shot("call2_ceremony"));
        at(160, () -> server(sp -> {
            LanternaMod.LOGGER.info("AUTOTEST after call hasRing={} corps={}",
                    com.lanterna.capability.ModCapabilities.get(sp).map(d -> d.hasRing()).orElse(false),
                    com.lanterna.capability.ModCapabilities.get(sp).map(d -> String.valueOf(d.corps())).orElse("?"));
            LanternaMod.LOGGER.info("AUTOTEST recipe green={} known={}",
                    sp.server.getRecipeManager().byKey(LanternaMod.id("bateria_green")).isPresent(),
                    sp.getRecipeBook().contains(LanternaMod.id("bateria_green")));
        }));
        at(40, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    /** Cena curta: o que acontece com o anel e a bateria quando o portador morre. */
    private static void sceneMorte() {
        Minecraft mc = Minecraft.getInstance();
        java.util.function.Consumer<String> report = tag -> server(sp -> {
            var d = com.lanterna.capability.ModCapabilities.get(sp).orElse(null);
            var ring = com.lanterna.power.EquipmentHelper.ring(sp);
            var items = sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, sp.getBoundingBox().inflate(300));
            LanternaMod.LOGGER.info("AUTOTEST morte {} ring={} charge={}/{} battery={} drops={}", tag, !ring.isEmpty(),
                    com.lanterna.item.PowerRingItem.getStoredCharge(ring), d.maxCharge(), com.lanterna.power.EquipmentHelper.battery(sp),
                    items.stream().map(i -> i.getItem().getItem() + "@" + i.blockPosition().toShortString()).toList());
        });
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(60, () -> {
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd("gamemode survival"); cmd("gamerule keepInventory false"); cmd("gamerule doImmediateRespawn true");
            cmd("kill @e[type=item]"); cmd("lanterna resetar"); cmd("clear @s");
            cmd("execute in minecraft:overworld run tp @s 400.5 -44 400.5");
        });
        at(40, () -> cmd("lanterna escolher lanterna:red"));
        at(200, () -> report.accept("escolhido"));
        // 80% de carga: o anel volta, a bateria fica
        at(10, () -> server(sp -> {
            var d = com.lanterna.capability.ModCapabilities.get(sp).orElse(null);
            com.lanterna.power.RingPowers.setCharge(sp, d, d.maxCharge() * 8 / 10);
            sp.kill();
        }));
        at(60, () -> { if (mc.player.isDeadOrDying()) mc.player.respawn(); mc.setScreen(null); });
        at(40, () -> report.accept("morte80"));
        // 30%: o anel cai junto com a bateria
        at(10, () -> server(sp -> {
            var d = com.lanterna.capability.ModCapabilities.get(sp).orElse(null);
            com.lanterna.power.RingPowers.setCharge(sp, d, d.maxCharge() * 3 / 10);
            sp.kill();
        }));
        at(60, () -> { if (mc.player.isDeadOrDying()) mc.player.respawn(); mc.setScreen(null); });
        at(40, () -> report.accept("morte30"));
        at(20, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    /** Cena: espécies, roupas, naves (pilotar, atirar, interior), blocos, nível, contratos e encontros. */
    private static void sceneNaves() {
        Minecraft mc = Minecraft.getInstance();
        java.util.function.Consumer<String> cmdServer = c -> server(sp -> sp.server.getCommands().performPrefixedCommand(
                sp.createCommandSourceStack().withSuppressedOutput().withPermission(4), c));
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> {
            mc.options.hideGui = false;
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd("gamemode creative"); cmd("time set 6000"); cmd("weather clear"); cmd("gamerule doMobSpawning false");
            cmd("lanterna resetar"); cmd("clear @s");
            cmd("execute in minecraft:overworld run tp @s 600.5 -40 600.5");
        });
        waitFor(() -> mc.level.hasChunkAt(600, 600) && mc.player.blockPosition().getY() < -30, 400);
        at(20, () -> {
            cmdServer.accept("fill 560 -61 560 660 -61 660 minecraft:smooth_stone");
            cmdServer.accept("fill 560 -60 560 660 -40 600 minecraft:air");
            cmdServer.accept("fill 560 -60 600 660 -40 660 minecraft:air");
            cmdServer.accept("kill @e[type=!player]");
        });
        // ---- 18 espécies com o uniforme verde, e corrompidas ----
        at(20, () -> server(sp -> {
            var all = com.lanterna.especies.Especie.values();
            for (int i = 0; i < all.length; i++) {
                for (int row = 0; row < 2; row++) {
                    var e = new com.lanterna.especies.LanternaEntity(com.lanterna.especies.EspeciesEntities.LANTERNA_NPC.get(), sp.level());
                    e.setup(all[i], 1 + (i % 3), com.lanterna.especies.Especie.Papel.LANTERNA, com.lanterna.corps.BuiltinCorps.GREEN,
                            row == 0 ? "roupa_tropa_green" : "roupa_tropa_black", i % 5 == 0, row == 1);
                    e.moveTo(574 + i * 3 + 0.5, -60, 580 + row * 5 + 0.5, 180, 0);
                    e.setNoAi(true);
                    sp.level().addFreshEntity(e);
                }
            }
        }));
        at(40, () -> cmd("tp @s 600.5 -56 570 0 10"));
        at(60, () -> shot("n1_especies"));
        at(5, () -> cmd("tp @s 585.5 -57 574 0 12"));
        at(40, () -> shot("n2_especies_perto"));
        // Piratas, capitão, mercador, sem roupa, peles
        at(5, () -> server(sp -> {
            String[][] list = {{"drevan", "pirata"}, {"brutor", "capitao"}, {"qiri", "mercador"}, {"ilith", "nua"}, {"lumiae", "nua"}, {"aurith", "nua"}};
            for (int i = 0; i < list.length; i++) {
                var e = new com.lanterna.especies.LanternaEntity(com.lanterna.especies.EspeciesEntities.LANTERNA_NPC.get(), sp.level());
                var esp = com.lanterna.especies.Especie.byId(list[i][0]);
                switch (list[i][1]) {
                    case "pirata" -> e.setup(esp, 1, com.lanterna.especies.Especie.Papel.PIRATA, null, "roupa_pirata", false, false);
                    case "capitao" -> e.setup(esp, 2, com.lanterna.especies.Especie.Papel.CAPITAO, null, "roupa_capitao", false, false);
                    case "mercador" -> e.setup(esp, 3, com.lanterna.especies.Especie.Papel.MERCADOR, null, "roupa_mercador", false, false);
                    default -> e.setup(esp, 1 + i % 3, com.lanterna.especies.Especie.Papel.LANTERNA, com.lanterna.corps.BuiltinCorps.BLUE, null, false, false);
                }
                e.moveTo(578 + i * 3 + 0.5, -60, 596.5, 180, 0);
                e.setNoAi(true);
                sp.level().addFreshEntity(e);
                if (i < 3) e.triggerAnim("acao", i == 0 ? "attack.beam" : i == 1 ? "misc.salute" : "misc.talk");
            }
        }));
        at(5, () -> cmd("tp @s 585.5 -57 590 0 12"));
        at(40, () -> shot("n3_papeis"));
        // ---- Naves no chão ----
        at(5, () -> server(sp -> {
            String[][] ships = {{"nave_lanceira", "green"}, {"nave_patrulha", "yellow"}, {"nave_pirata", "1"}, {"nave_cargueiro", "2"},
                    {"nave_pirata_capitania", "1"}, {"destrocos_2", ""}};
            int[][] at = {{575, 615}, {592, 615}, {612, 615}, {635, 615}, {600, 645}, {645, 640}};
            for (int i = 0; i < ships.length; i++) {
                var n = com.lanterna.naves.NaveEntity.create(sp.level(), com.lanterna.naves.NaveTipo.byId(ships[i][0]), ships[i][1]);
                n.moveTo(at[i][0] + 0.5, -60, at[i][1] + 0.5, 30, 0);
                sp.level().addFreshEntity(n);
            }
        }));
        at(60, () -> cmd("tp @s 600.5 -40 595 0 30"));
        at(60, () -> shot("n4_naves"));
        at(5, () -> cmd("tp @s 600.5 -45 620 0 25"));
        at(50, () -> shot("n5_capitania"));
        // ---- Blocos de interior ----
        at(5, () -> server(sp -> {
            int x = 570;
            for (var b : com.lanterna.naves.bloco.NaveBlocks.ALL) {
                if (b == com.lanterna.naves.bloco.NaveBlocks.QUADRO_CONTRATOS || b == com.lanterna.naves.bloco.NaveBlocks.NAVE_PORTA
                        || b == com.lanterna.naves.bloco.NaveBlocks.NAVE_BELICHE) continue;
                sp.level().setBlock(new BlockPos(x, -60, 562), b.get().defaultBlockState(), 3);
                x += 2;
            }
            var porta = com.lanterna.naves.bloco.NaveBlocks.NAVE_PORTA.get().defaultBlockState();
            sp.level().setBlock(new BlockPos(x, -60, 562), porta, 3);
            sp.level().setBlock(new BlockPos(x, -59, 562), porta.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
        }));
        at(5, () -> cmd("tp @s 585.5 -58 568 180 15"));
        at(40, () -> shot("n6_blocos"));
        // ---- Pilotar a Lanceira ----
        at(5, () -> server(sp -> {
            var n = nearest(com.lanterna.naves.NaveEntity.class, sp, 80, x -> x.getTipoNave().equals("nave_lanceira"));
            LanternaMod.LOGGER.info("AUTOTEST lanceira={}", n);
            if (n != null) { sp.teleportTo(n.getX(), n.getY() + 2, n.getZ()); sp.startRiding(n, true); }
        }));
        at(30, () -> { camera(CameraType.THIRD_PERSON_BACK); LanternaMod.LOGGER.info("AUTOTEST riding={}", mc.player.getVehicle()); shot("n7_cabine"); });
        at(5, () -> com.lanterna.naves.NaveEntity.CLIENT_INPUT = n -> new com.lanterna.naves.NaveEntity.Input(1f, 0, true, false, false));
        at(50, () -> { shot("n8_voando"); LanternaMod.LOGGER.info("AUTOTEST voo pos={}", mc.player.getVehicle() != null ? mc.player.getVehicle().position() : null); });
        at(5, () -> com.lanterna.client.naves.ClientNaves.fire());
        at(4, () -> shot("n9_tiro"));
        at(5, () -> { com.lanterna.client.naves.ClientNaves.init(); });
        at(40, () -> server(sp -> LanternaMod.LOGGER.info("AUTOTEST nave server pos={} pousada={}", sp.getVehicle() != null ? sp.getVehicle().position() : null,
                sp.getVehicle() instanceof com.lanterna.naves.NaveEntity n ? n.pousada() : null)));
        // ---- Interior ----
        at(5, () -> server(sp -> {
            if (sp.getVehicle() instanceof com.lanterna.naves.NaveEntity n) { sp.stopRiding(); com.lanterna.naves.NaveInterior.entrar(sp, n); }
        }));
        at(60, () -> { camera(CameraType.FIRST_PERSON); LanternaMod.LOGGER.info("AUTOTEST interior dim={} pos={}", mc.player.level().dimension().location(), mc.player.position()); shot("n10_interior"); });
        at(5, () -> look(mc.player.getYRot() + 180, 10));
        at(20, () -> shot("n11_interior_b"));
        at(5, () -> server(sp -> com.lanterna.naves.NaveInterior.sair(sp)));
        at(40, () -> LanternaMod.LOGGER.info("AUTOTEST saiu dim={} pos={}", mc.player.level().dimension().location(), mc.player.position()));
        // ---- Nível: XP e HUD ----
        at(5, () -> { cmd("lanterna escolher lanterna:green"); });
        waitFor(() -> com.lanterna.client.ClientRingData.hasRing, 400);
        at(40, () -> { cmd("gamemode survival"); server(sp -> com.lanterna.tropa.Nivel.addXp(sp, 3200, net.minecraft.network.chat.Component.literal("teste"))); });
        at(10, () -> shot("n12_nivel"));
        at(30, () -> server(sp -> com.lanterna.mission.LoreCapability.get(sp).ifPresent(d ->
                LanternaMod.LOGGER.info("AUTOTEST xp={} nivel={} hp={} active={}", d.xp(), com.lanterna.tropa.Nivel.nivel(d.xp()), sp.getMaxHealth(), d.active().keySet()))));
        // ---- Base de Oa: Lanternas e quadro de contratos ----
        waitFor(() -> mc.player.level().dimension().location().getPath().equals("oa") || !com.lanterna.client.flight.Autopilot.active(), 3000);
        waitFor(() -> !com.lanterna.client.flight.Autopilot.active(), 1500);
        at(10, () -> { cmd("gamemode creative"); cmd("execute in lanterna:oa run tp @s 0.5 120 -4.5"); });
        at(80, () -> server(sp -> {
            int y = sp.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 3, -8);
            sp.teleportTo(3.5, y, -6.5);
            sp.setYRot(180);
        }));
        at(80, () -> { look(180, 0); shot("n13_quadro"); });
        at(5, () -> server(sp -> com.lanterna.tropa.Contratos.abrirQuadro(sp, true)));
        waitFor(() -> mc.screen != null, 200);
        at(20, () -> shot("n14_quadro_tela"));
        at(5, () -> { mc.setScreen(null); server(sp -> {
            var ids = com.lanterna.tropa.Contratos.ofertas("oa", com.lanterna.tropa.Contratos.dia(sp));
            com.lanterna.tropa.Contratos.aceitar(sp, ids.get(0));
            com.lanterna.tropa.Contratos.aceitar(sp, ids.get(1));
            com.lanterna.mission.LoreCapability.get(sp).ifPresent(d -> LanternaMod.LOGGER.info("AUTOTEST contratos active={}", d.active().keySet()));
            var lanternas = sp.level().getEntitiesOfClass(com.lanterna.especies.LanternaEntity.class, sp.getBoundingBox().inflate(40));
            LanternaMod.LOGGER.info("AUTOTEST lanternas na base={}", lanternas.size());
        }); });
        at(5, () -> cmd("tp @s 0.5 ~ 8.5 180 10"));
        at(40, () -> shot("n15_base_lanternas"));
        // ---- Encontro no espaço ----
        at(5, () -> cmd("execute in lanterna:space run tp @s 0 -200 0"));
        waitFor(() -> mc.player.level().dimension().location().getPath().equals("space"), 400);
        at(40, () -> server(sp -> com.lanterna.tropa.Encontros.forcar(sp, "pirata")));
        at(5, () -> server(sp -> com.lanterna.tropa.Encontros.forcar(sp, "patrulha")));
        at(160, () -> { shot("n16_encontro"); server(sp -> {
            for (var n : sp.level().getEntitiesOfClass(com.lanterna.naves.NaveEntity.class, sp.getBoundingBox().inflate(250)))
                LanternaMod.LOGGER.info("AUTOTEST encontro {} ia={} dist={} hp={} piloto={}", n.getTipoNave(), n.ia(), (int) n.distanceTo(sp), n.hp(), n.getControllingPassenger());
        }); });
        at(5, () -> server(sp -> {
            var n = nearest(com.lanterna.naves.NaveEntity.class, sp, 250, x -> x.ia().equals("pirata"));
            if (n != null) { sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, n.position()); }
        }));
        at(30, () -> shot("n17_pirata"));
        at(20, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    /** Fotos das naves em 3/4, uma por vez (espectador, sem cair). */
    private static void sceneNavesFoto() {
        Minecraft mc = Minecraft.getInstance();
        java.util.function.Consumer<String> cmdServer = c -> server(sp -> sp.server.getCommands().performPrefixedCommand(
                sp.createCommandSourceStack().withSuppressedOutput().withPermission(4), c));
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.hideGui = true; cmd("gamemode spectator"); cmd("time set 6000");
            cmd("execute in minecraft:overworld run tp @s 800.5 -50 800.5"); });
        waitFor(() -> mc.level.hasChunkAt(800, 800), 400);
        at(20, () -> { cmdServer.accept("kill @e[type=!player]"); cmdServer.accept("fill 760 -61 760 840 -61 840 minecraft:smooth_stone"); });
        String[][] ships = {{"nave_lanceira", "green", "16"}, {"nave_pirata", "1", "26"}, {"nave_patrulha", "violet", "24"},
                {"nave_cargueiro", "1", "34"}, {"nave_pirata_capitania", "2", "60"}, {"destrocos_2", "", "24"}};
        for (String[] sh : ships) {
            at(10, () -> server(sp -> {
                for (var o : sp.level().getEntitiesOfClass(com.lanterna.naves.NaveEntity.class, sp.getBoundingBox().inflate(200))) o.discard();
                var n = com.lanterna.naves.NaveEntity.create(sp.level(), com.lanterna.naves.NaveTipo.byId(sh[0]), sh[1]);
                n.moveTo(800.5, -60, 800.5, 0, 0);
                sp.level().addFreshEntity(n);
            }));
            double d = Double.parseDouble(sh[2]);
            // nave olha para +z (yaw 0); câmera na frente-esquerda, olhando para ela
            at(10, () -> cmd(String.format(java.util.Locale.ROOT, "tp @s %.1f %.1f %.1f %.1f %.1f", 800.5 + d * 0.7, -60 + d * 0.45, 800.5 + d * 0.7, 135.0, 22.0)));
            at(60, () -> shot("f_" + sh[0] + "_frente"));
            at(5, () -> cmd(String.format(java.util.Locale.ROOT, "tp @s %.1f %.1f %.1f %.1f %.1f", 800.5 - d * 0.7, -60 + d * 0.45, 800.5 - d * 0.7, -45.0, 22.0)));
            at(40, () -> shot("f_" + sh[0] + "_tras"));
        }
        // Espécies de frente
        at(10, () -> server(sp -> {
            for (var o : sp.level().getEntitiesOfClass(com.lanterna.naves.NaveEntity.class, sp.getBoundingBox().inflate(200))) o.discard();
            var all = com.lanterna.especies.Especie.values();
            for (int i = 0; i < all.length; i++) {
                var e = new com.lanterna.especies.LanternaEntity(com.lanterna.especies.EspeciesEntities.LANTERNA_NPC.get(), sp.level());
                var c = all[i].tropa != null ? all[i].tropa : com.lanterna.corps.BuiltinCorps.GREEN;
                e.setup(all[i], 1, com.lanterna.especies.Especie.Papel.LANTERNA, c, "roupa_tropa_" + c.id().getPath(), i % 4 == 0, false);
                e.moveTo(786 + (i % 9) * 3 + 0.5, -60, 800 + (i / 9) * 4 + 0.5, 180, 0);
                e.setYBodyRot(180); e.setYHeadRot(180);
                e.setNoAi(true);
                sp.level().addFreshEntity(e);
            }
        }));
        at(10, () -> cmd("tp @s 798.5 -56.5 815 180 12"));
        at(60, () -> shot("f_especies_frente"));
        at(20, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    /** Encontros no espaço. */
    private static void sceneEncontro() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd("gamemode survival"); cmd("effect give @s resistance 99999 4 true"); cmd("lanterna resetar"); });
        at(20, () -> cmd("lanterna escolher lanterna:green"));
        waitFor(() -> com.lanterna.client.ClientRingData.hasRing, 400);
        at(10, () -> { cmd("execute in lanterna:space run tp @s 300 -200 300"); });
        waitFor(() -> mc.player.level().dimension().location().getPath().equals("space"), 600);
        at(60, () -> { com.lanterna.client.flight.Autopilot.clear(); cmd("tp @s 300 -200 300"); });
        at(60, () -> server(sp -> { com.lanterna.tropa.Encontros.forcar(sp, "pirata"); com.lanterna.tropa.Encontros.forcar(sp, "patrulha");
            com.lanterna.tropa.Encontros.forcar(sp, "cargueiro"); }));
        for (int k = 0; k < 4; k++) {
            int kk = k;
            at(80, () -> server(sp -> {
                for (var n : sp.level().getEntitiesOfClass(com.lanterna.naves.NaveEntity.class, sp.getBoundingBox().inflate(250)))
                    LanternaMod.LOGGER.info("AUTOTEST encontro{} {} ia={} dist={} hp={} piloto={}", kk, n.getTipoNave(), n.ia(), (int) n.distanceTo(sp), n.hp(),
                            n.getControllingPassenger() != null ? n.getControllingPassenger().getType().toShortString() : null);
                LanternaMod.LOGGER.info("AUTOTEST jogador hp={} tiros={}", sp.getHealth(),
                        sp.level().getEntitiesOfClass(com.lanterna.tropa.TiroPlasma.class, sp.getBoundingBox().inflate(80)).size());
            }));
            at(2, () -> server(sp -> {
                var n = nearest(com.lanterna.naves.NaveEntity.class, sp, 250, x -> !x.ia().isEmpty());
                if (n != null) sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, n.position());
            }));
            at(4, () -> shot("e_" + kk));
        }
        at(20, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    /** Teste de verdade: põe a nave pelo item, Shift+clique entra, clica na porta para sair, monta e pilota virando. */
    private static void scenePilotar() {
        Minecraft mc = Minecraft.getInstance();
        java.util.function.Consumer<String> cmdServer = c -> server(sp -> sp.server.getCommands().performPrefixedCommand(
                sp.createCommandSourceStack().withSuppressedOutput().withPermission(4), c));
        Runnable info = () -> LanternaMod.LOGGER.info("AUTOTEST hit={} dim={} veiculo={} pos={}",
                mc.hitResult == null ? null : (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult eh ? "entity:" + eh.getEntity().getClass().getSimpleName() + "#" + eh.getEntity().getId() : mc.hitResult.getType()),
                mc.level.dimension().location(), mc.player.getVehicle(), mc.player.position());
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> {
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd("gamemode survival"); cmd("time set 6000"); cmd("weather clear"); cmd("gamerule doMobSpawning false");
            cmd("effect give @s resistance 99999 4 true");
            cmd("lanterna resetar"); cmd("clear @s");
            cmd("execute in minecraft:overworld run tp @s 900.5 -40 900.5");
        });
        waitFor(() -> mc.level.dimension().location().getPath().equals("overworld") && mc.level.hasChunkAt(900, 900) && mc.player.blockPosition().getY() < -30, 400);
        at(20, () -> {
            cmdServer.accept("fill 860 -61 860 960 -61 960 minecraft:smooth_stone");
            cmdServer.accept("fill 860 -60 860 960 -40 960 minecraft:air");
            cmdServer.accept("kill @e[type=!player]");
        });
        at(20, () -> { cmd("tp @s 900.5 -60 900.5 0 40"); cmd("give @s lanterna:nave_lanceira"); });
        at(30, () -> { mc.player.getInventory().selected = 0; info.run(); KeyMapping.click(mc.options.keyUse.getKey()); });
        at(20, () -> server(sp -> {
            var n = nearest(com.lanterna.naves.NaveEntity.class, sp, 30, x -> true);
            LanternaMod.LOGGER.info("AUTOTEST nave colocada={} yaw={}", n == null ? null : n.position(), n == null ? 0 : n.getYRot());
        }));
        // Afasta e olha para a nave
        at(5, () -> server(sp -> {
            var n = nearest(com.lanterna.naves.NaveEntity.class, sp, 30, x -> true);
            if (n != null) { sp.teleportTo(sp.serverLevel(), n.getX() + 3.2, n.getY(), n.getZ(), 90f, 20f); }
        }));
        at(20, () -> { info.run(); mc.options.keyShift.setDown(true); });
        at(10, () -> { info.run(); KeyMapping.click(mc.options.keyUse.getKey()); });
        at(5, () -> mc.options.keyShift.setDown(false));
        waitFor(() -> mc.level.dimension().location().getPath().equals("interior_naves"), 200);
        at(2, () -> { info.run(); var bp = mc.player.blockPosition().below();
            LanternaMod.LOGGER.info("AUTOTEST cliente chao={} chunk={}", mc.level.getBlockState(bp), mc.level.hasChunkAt(bp));
            server(sp -> LanternaMod.LOGGER.info("AUTOTEST servidor pos={} chao={}", sp.position(), sp.level().getBlockState(sp.blockPosition().below()))); });
        at(40, () -> { info.run(); shot("p1_interior"); var bp = mc.player.blockPosition().below();
            LanternaMod.LOGGER.info("AUTOTEST cliente chao={} chunk={}", mc.level.getBlockState(bp), mc.level.hasChunkAt(bp)); });
        at(60, () -> { info.run(); shot("p1b_interior"); });
        // Vira para a porta externa e clica nela
        at(5, () -> { mc.player.setYRot(mc.player.getYRot() + 180); mc.player.setXRot(10); });
        at(10, () -> { info.run(); KeyMapping.click(mc.options.keyUse.getKey()); });
        waitFor(() -> mc.level.dimension().location().getPath().equals("overworld") && mc.screen == null, 300);
        at(40, () -> { info.run(); shot("p2_saiu");
            for (var e : mc.level.entitiesForRendering()) if (e instanceof com.lanterna.naves.NaveEntity n)
                LanternaMod.LOGGER.info("AUTOTEST volta cliente nave #{} {} pos={}", n.getId(), n.getUUID(), n.position());
            server(sp -> { for (var n : sp.serverLevel().getEntitiesOfClass(com.lanterna.naves.NaveEntity.class, sp.getBoundingBox().inflate(300)))
                LanternaMod.LOGGER.info("AUTOTEST volta servidor nave #{} {} pos={}", n.getId(), n.getUUID(), n.position()); }); });
        // Monta e pilota
        at(5, () -> server(sp -> {
            var n = nearest(com.lanterna.naves.NaveEntity.class, sp, 30, x -> true);
            if (n != null) sp.teleportTo(sp.serverLevel(), n.getX() + 3.2, n.getY(), n.getZ(), 90f, 20f);
        }));
        at(15, () -> { info.run(); KeyMapping.click(mc.options.keyUse.getKey()); });
        at(20, () -> { info.run(); mc.options.keyJump.setDown(true); });
        at(30, () -> { mc.options.keyJump.setDown(false); mc.options.keyUp.setDown(true); });
        for (int k = 0; k < 6; k++) {
            int kk = k;
            at(15, () -> mc.player.setYRot(mc.player.getYRot() + 30));
            at(10, () -> {
                var v = mc.player.getVehicle();
                LanternaMod.LOGGER.info("AUTOTEST curva{} jogadorYaw={} naveYawCliente={} pos={}", kk, mc.player.getYRot(), v == null ? null : v.getYRot(), v == null ? null : v.position());
                server(sp -> { var sv = sp.getVehicle(); LanternaMod.LOGGER.info("AUTOTEST curva{} naveYawServidor={}", kk, sv == null ? null : sv.getYRot()); });
            });
        }
        at(5, () -> { mc.options.keyUp.setDown(false); mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); });
        at(30, () -> shot("p3_pilotando_3a_pessoa"));
        at(5, () -> mc.player.setYRot(mc.player.getYRot() + 90));
        at(30, () -> shot("p4_virou_90"));
        at(5, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); server(sp -> {
            var v = sp.getVehicle(); sp.stopRiding();
            if (v != null) { v.setYRot(45); sp.teleportTo(sp.serverLevel(), v.getX(), v.getY() + 6, v.getZ() - 16, 0f, 15f); } }); });
        at(40, () -> shot("p5_de_fora_yaw45"));
        at(5, () -> {
            for (var e : mc.level.entitiesForRendering()) if (e instanceof com.lanterna.naves.NaveEntity n)
                LanternaMod.LOGGER.info("AUTOTEST cliente nave #{} {} pos={} removida={}", n.getId(), n.getUUID(), n.position(), n.isRemoved());
            server(sp -> { for (var n : sp.serverLevel().getEntitiesOfClass(com.lanterna.naves.NaveEntity.class, sp.getBoundingBox().inflate(300)))
                LanternaMod.LOGGER.info("AUTOTEST servidor nave #{} {} pos={}", n.getId(), n.getUUID(), n.position()); });
        });
        at(20, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    /** Quadro de contratos só na Tropa/nave própria, e a busca do mapa estelar. */
    private static void sceneQuadro() {
        Minecraft mc = Minecraft.getInstance();
        Runnable tela = () -> LanternaMod.LOGGER.info("AUTOTEST tela={} dim={}", mc.screen == null ? null : mc.screen.getClass().getSimpleName(), mc.level.dimension().location());
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { cmd("gamemode survival"); cmd("effect give @s resistance 99999 4 true"); cmd("lanterna resetar"); cmd("clear @s");
            cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5"); });
        at(40, () -> server(sp -> { com.lanterna.capability.ModCapabilities.get(sp).ifPresent(r -> {});
            sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna escolher lanterna:green"); }));
        waitFor(() -> com.lanterna.client.ClientRingData.hasRing, 400);
        at(20, () -> { com.lanterna.client.flight.Autopilot.clear(); cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5"); });
        // 1) Terra: não abre
        at(40, () -> server(sp -> com.lanterna.tropa.Contratos.abrirQuadro(sp)));
        at(10, tela);
        // 2) Planeta de outra Tropa (Korugar): não abre
        at(5, () -> cmd("execute in lanterna:korugar run tp @s 0 120 0"));
        waitFor(() -> mc.level.dimension().location().getPath().equals("korugar"), 400);
        at(40, () -> server(sp -> com.lanterna.tropa.Contratos.abrirQuadro(sp)));
        at(10, tela);
        // 3) Oa: abre
        at(5, () -> cmd("execute in lanterna:oa run tp @s 0 120 0"));
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa"), 400);
        at(40, () -> server(sp -> com.lanterna.tropa.Contratos.abrirQuadro(sp)));
        at(10, tela);
        at(10, () -> shot("q1_oa"));
        at(5, () -> mc.setScreen(null));
        // 4) Painel da própria nave
        at(5, () -> cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5"));
        waitFor(() -> mc.level.dimension().location().getPath().equals("overworld") && mc.screen == null, 400);
        at(20, () -> server(sp -> {
            var n = com.lanterna.naves.NaveEntity.create(sp.level(), com.lanterna.naves.NaveTipo.NAVE_LANCEIRA, "green");
            n.moveTo(sp.getX() + 6, sp.getY(), sp.getZ(), 0, 0); n.setDono(sp.getUUID()); sp.level().addFreshEntity(n);
            com.lanterna.naves.NaveInterior.entrar(sp, n); }));
        waitFor(() -> mc.level.dimension().location().getPath().equals("interior_naves") && mc.screen == null, 400);
        at(40, () -> server(sp -> com.lanterna.tropa.Contratos.abrirQuadro(sp)));
        at(10, tela);
        at(5, () -> shot("q2_nave"));
        at(5, () -> mc.setScreen(null));
        // 5) Nave de outro dono
        at(5, () -> server(sp -> { var d = sp.server.getPlayerList(); com.lanterna.naves.NaveInterior.sair(sp); }));
        waitFor(() -> mc.level.dimension().location().getPath().equals("overworld") && mc.screen == null, 400);
        at(20, () -> server(sp -> {
            var n = com.lanterna.naves.NaveEntity.create(sp.level(), com.lanterna.naves.NaveTipo.NAVE_PATRULHA, "blue");
            n.moveTo(sp.getX() - 10, sp.getY(), sp.getZ(), 0, 0); n.setDono(java.util.UUID.randomUUID()); sp.level().addFreshEntity(n);
            com.lanterna.naves.NaveInterior.entrar(sp, n); }));
        waitFor(() -> mc.level.dimension().location().getPath().equals("interior_naves") && mc.screen == null, 400);
        at(40, () -> server(sp -> com.lanterna.tropa.Contratos.abrirQuadro(sp)));
        at(10, tela);
        // 6) Mapa estelar com busca
        at(5, () -> mc.setScreen(new com.lanterna.client.screen.StarMapScreen()));
        at(20, () -> shot("q3_mapa"));
        for (String q : new String[]{"gelado", "verde", "korug"}) {
            at(5, () -> { try { var f = com.lanterna.client.screen.StarMapScreen.class.getDeclaredField("search"); f.setAccessible(true);
                ((net.minecraft.client.gui.components.EditBox) f.get(mc.screen)).setValue(q); } catch (Exception e) { LanternaMod.LOGGER.error("x", e); } });
            at(20, () -> shot("q4_busca_" + q));
        }
        at(20, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    /** Blocos do interior funcionando, sem alçapão, e destroços sem parede invisível. */
    private static final java.util.Map<String, net.minecraft.core.BlockPos> BLOCOS = new java.util.concurrent.ConcurrentHashMap<>();
    private static void usar(String nome) {
        Minecraft mc = Minecraft.getInstance();
        var pos = BLOCOS.get(nome);
        if (pos == null) { LanternaMod.LOGGER.info("AUTOTEST bloco {} nao achado", nome); return; }
        mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, net.minecraft.world.phys.Vec3.atCenterOf(pos));
        mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
    }
    private static void sceneBlocos() {
        Minecraft mc = Minecraft.getInstance();
        Runnable tela = () -> LanternaMod.LOGGER.info("AUTOTEST tela={} veiculo={} carga={}", mc.screen == null ? null : mc.screen.getClass().getSimpleName(),
                mc.player.getVehicle() == null ? null : mc.player.getVehicle().getClass().getSimpleName(), com.lanterna.client.ClientRingData.charge);
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd("gamemode survival"); cmd("effect give @s resistance 99999 4 true"); cmd("lanterna resetar"); cmd("clear @s");
            cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5"); });
        at(40, () -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna escolher lanterna:green")));
        waitFor(() -> com.lanterna.client.ClientRingData.hasRing, 400);
        for (int k = 0; k < 8; k++) at(20, () -> { com.lanterna.client.flight.Autopilot.clear(); cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5"); });
        at(10, () -> server(sp -> { for (var n : sp.serverLevel().getEntitiesOfClass(com.lanterna.naves.NaveEntity.class, sp.getBoundingBox().inflate(200))) n.discard(); }));
        waitFor(() -> mc.level.dimension().location().getPath().equals("overworld"), 400);
        // ---- destroço: dá para entrar no meio ----
        at(30, () -> server(sp -> {
            sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "fill 870 -61 870 930 -61 930 minecraft:smooth_stone");
            var n = com.lanterna.naves.NaveEntity.create(sp.level(), com.lanterna.naves.NaveTipo.DESTROCOS_2, "");
            n.moveTo(900.5, -60, 912.5, 30, 0); sp.level().addFreshEntity(n);
            com.lanterna.capability.ModCapabilities.get(sp).ifPresent(r -> { if (r.flightEnabled()) com.lanterna.power.RingPowers.toggleFlight(sp); });
            sp.teleportTo(sp.serverLevel(), 900.5, -60, 896.5, 0f, 10f); }));
        at(20, () -> { mc.player.setXRot(10); mc.options.keyUp.setDown(true); });
        at(60, () -> { mc.options.keyUp.setDown(false); LanternaMod.LOGGER.info("AUTOTEST destroco jogador z={} (centro 912.5)", mc.player.getZ()); });
        at(10, () -> shot("b0_destroco"));
        // ---- interior ----
        at(5, () -> server(sp -> {
            var n = com.lanterna.naves.NaveEntity.create(sp.level(), com.lanterna.naves.NaveTipo.NAVE_LANCEIRA, "green");
            n.moveTo(880.5, -60, 880.5, 0, 0); n.setDono(sp.getUUID()); sp.level().addFreshEntity(n);
            com.lanterna.naves.NaveInterior.entrar(sp, n); }));
        waitFor(() -> mc.level.dimension().location().getPath().equals("interior_naves") && mc.screen == null, 400);
        at(40, () -> server(sp -> {
            BLOCOS.clear(); int esc = 0;
            for (var p : net.minecraft.core.BlockPos.betweenClosed(sp.blockPosition().offset(-12, -3, -12), sp.blockPosition().offset(12, 6, 12))) {
                var b = sp.level().getBlockState(p).getBlock();
                String id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(b).getPath();
                if (id.equals("nave_escotilha")) esc++;
                if (id.startsWith("nave_") && !BLOCOS.containsKey(id)) BLOCOS.put(id, p.immutable());
            }
            LanternaMod.LOGGER.info("AUTOTEST escotilhas={} blocos={}", esc, BLOCOS.keySet());
        }));
        at(5, () -> shot("b1_sala"));
        at(5, () -> { cmd("lanterna carga 10"); });
        at(20, () -> { tela.run(); usar("nave_recarga"); });
        at(20, tela);
        at(5, () -> usar("nave_recarga"));
        at(10, () -> usar("nave_cadeira"));
        at(20, () -> { tela.run(); shot("b2_sentado"); });
        at(5, () -> { mc.options.keyShift.setDown(true); });
        at(10, () -> { mc.options.keyShift.setDown(false); tela.run(); });
        at(10, () -> usar("nave_armario"));
        at(20, () -> { tela.run(); shot("b3_armario"); });
        at(5, () -> mc.player.closeContainer());
        at(10, () -> usar("nave_luz"));
        at(20, () -> LanternaMod.LOGGER.info("AUTOTEST luz={}", mc.level.getBlockState(BLOCOS.get("nave_luz"))));
        at(5, () -> usar("nave_holomapa"));
        at(20, () -> { tela.run(); shot("b4_holomapa"); });
        at(5, () -> mc.setScreen(null));
        at(5, () -> server(sp -> sp.server.overworld().setDayTime(18000)));
        at(10, () -> usar("nave_beliche"));
        at(20, () -> server(sp -> LanternaMod.LOGGER.info("AUTOTEST tempo={} renasce={} {}", sp.server.overworld().getDayTime() % 24000, sp.getRespawnDimension().location(), sp.getRespawnPosition())));
        at(5, () -> usar("nave_console"));
        at(20, tela);
        at(20, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    /** Voo do anel (normal e hiperpropulsão, rastro) e da nave (curva com inclinação). */
    private static void sceneVoo() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd("gamemode survival"); cmd("effect give @s resistance 99999 4 true"); cmd("lanterna resetar"); cmd("clear @s"); cmd("time set 6000");
            cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5"); });
        at(40, () -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna escolher lanterna:green")));
        waitFor(() -> com.lanterna.client.ClientRingData.hasRing, 400);
        for (int k = 0; k < 8; k++) at(20, () -> { com.lanterna.client.flight.Autopilot.clear(); cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5 0 0"); });
        at(10, () -> server(sp -> { for (var n : sp.serverLevel().getEntitiesOfClass(com.lanterna.naves.NaveEntity.class, sp.getBoundingBox().inflate(300))) n.discard(); }));
        // ---- Nave: curva para a direita e subida ----
        at(10, () -> server(sp -> {
            var n = com.lanterna.naves.NaveEntity.create(sp.level(), com.lanterna.naves.NaveTipo.NAVE_LANCEIRA, "green");
            n.moveTo(900.5, -55, 905.5, 0, 0); n.setDono(sp.getUUID()); sp.level().addFreshEntity(n);
            sp.startRiding(n, true); }));
        at(20, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); mc.player.setXRot(0); mc.options.keyUp.setDown(true); });
        at(40, () -> {});
        for (int k = 0; k < 12; k++) at(2, () -> mc.player.setYRot(mc.player.getYRot() + 6));
        at(1, () -> { var v = mc.player.getVehicle(); if (v instanceof com.lanterna.naves.NaveEntity n)
            LanternaMod.LOGGER.info("AUTOTEST nave curva roll={} pitch={} yaw={}", n.visRoll(1), n.visPitch(1), n.getYRot()); shot("v1_nave_curva_direita"); });
        at(30, () -> mc.options.keyJump.setDown(true));
        at(14, () -> { var v = mc.player.getVehicle(); if (v instanceof com.lanterna.naves.NaveEntity n)
            LanternaMod.LOGGER.info("AUTOTEST nave subindo roll={} pitch={}", n.visRoll(1), n.visPitch(1)); shot("v2_nave_subindo"); });
        at(5, () -> { mc.options.keyJump.setDown(false); mc.options.keyUp.setDown(false); });
        at(20, () -> server(sp -> { var v = sp.getVehicle(); sp.stopRiding(); if (v != null) v.discard(); }));
        // ---- Anel: voo normal com curva ----
        at(20, () -> server(sp -> com.lanterna.capability.ModCapabilities.get(sp).ifPresent(r -> { if (!r.flightEnabled()) com.lanterna.power.RingPowers.toggleFlight(sp); })));
        at(10, () -> { mc.player.setXRot(-5); mc.options.keyUp.setDown(true); mc.options.keySprint.setDown(true); });
        at(50, () -> {});
        for (int k = 0; k < 10; k++) at(2, () -> mc.player.setYRot(mc.player.getYRot() - 5));
        at(1, () -> shot("v3_anel_curva"));
        at(10, () -> { mc.options.keyUp.setDown(false); mc.options.keySprint.setDown(false); });
        // ---- Espaço: hiperpropulsão e rastro ----
        at(20, () -> cmd("execute in lanterna:space run tp @s 300 -200 300 0 0"));
        waitFor(() -> mc.level.dimension().location().getPath().equals("space") && mc.screen == null, 600);
        at(40, () -> { com.lanterna.client.flight.Autopilot.clear(); server(sp -> com.lanterna.capability.ModCapabilities.get(sp).ifPresent(r -> { if (!r.flightEnabled()) com.lanterna.power.RingPowers.toggleFlight(sp); })); });
        at(10, () -> { mc.player.setXRot(0); mc.player.setYRot(90); mc.options.keyUp.setDown(true); mc.options.keySprint.setDown(true); });
        at(40, () -> shot("v4_espaco_rapido"));
        at(120, () -> { LanternaMod.LOGGER.info("AUTOTEST hiper vel={}", mc.player.getDeltaMovement().length()); shot("v5_hiper_tras"); });
        at(2, () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        at(3, () -> shot("v6_hiper_frente"));
        at(10, () -> { mc.player.setYRot(mc.player.getYRot() + 40); });
        at(10, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); });
        at(6, () -> shot("v7_hiper_curva"));
        at(5, () -> { mc.options.keyUp.setDown(false); mc.options.keySprint.setDown(false); });
        at(20, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    /** Contrato de piratas: reforços quando faltam alvos, completar, e desistir destruindo o local. */
    private static final String[] CT = new String[2];
    private static void sceneContrato() {
        Minecraft mc = Minecraft.getInstance();
        java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> estado = sp -> {
            var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null);
            String id = CT[0];
            var m = com.lanterna.tropa.Contratos.mission(id);
            int vivos = sp.serverLevel().getEntitiesOfClass(com.lanterna.especies.LanternaEntity.class, sp.getBoundingBox().inflate(200),
                    e -> e.getPersistentData().getString("lanterna_ct").startsWith(id + ":")).size();
            StringBuilder sb = new StringBuilder();
            int tot = 0;
            for (var e : sp.serverLevel().getAllEntities()) if (e != null && e.getPersistentData().getString("lanterna_ct").startsWith(id + ":")) {
                tot++; if (sb.length() < 300) sb.append(e.getType().toShortString()).append('@').append(e.blockPosition().toShortString()).append(e.isAlive() ? "" : "(morto)").append(' ');
            }
            LanternaMod.LOGGER.info("AUTOTEST contrato {} ativo={} feito={} progresso={}/{} perto={} total={} jogador={} {}", id, d.isActive(id), d.isDone(id),
                    d.isActive(id) ? d.progress(id, 0) : -1, m.objectives().get(0).count(), vivos, tot, sp.blockPosition().toShortString(), sb);
        };
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd("gamemode creative"); cmd("lanterna resetar"); cmd("clear @s"); cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5"); });
        at(40, () -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna escolher lanterna:green")));
        waitFor(() -> com.lanterna.client.ClientRingData.hasRing, 400);
        for (int k = 0; k < 6; k++) at(20, () -> { com.lanterna.client.flight.Autopilot.clear(); cmd("execute in lanterna:oa run tp @s 0 120 0"); });
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa") && mc.screen == null, 600);
        at(20, () -> server(sp -> {
            long hoje = com.lanterna.tropa.Contratos.dia(sp);
            outer:
            for (long dd = hoje + 50 + (System.currentTimeMillis() / 1000) % 500; dd < hoje + 1000; dd++) for (String id : com.lanterna.tropa.Contratos.ofertas("oa", dd)) {
                var m = com.lanterna.tropa.Contratos.mission(id);
                if (m != null && m.objectives().get(0).target().equals("papel:pirata")) {
                    sp.server.overworld().setDayTime(dd * 24000 + 6000);
                    CT[0] = id; break outer;
                }
            }
            for (String id : com.lanterna.tropa.Contratos.ofertas("oa", com.lanterna.tropa.Contratos.dia(sp))) if (!id.equals(CT[0])) { CT[1] = id; break; }
            com.lanterna.tropa.Contratos.aceitar(sp, CT[0]);
            var pos = com.lanterna.tropa.Contratos.mission(CT[0]).objectives().get(0).pos();
            LanternaMod.LOGGER.info("AUTOTEST aceitou {} em {}", CT[0], pos);
            sp.teleportTo(sp.serverLevel(), pos.getX() + 20, 120, pos.getZ(), 90f, 30f);
        }));
        at(10, () -> mc.setScreen(null));
        for (int k = 0; k < 6; k++) at(20, () -> server(estado));
        // some com 3 piratas (como se tivessem caído num buraco): sem crédito
        at(5, () -> server(sp -> { int n = 0;
            for (var e : sp.serverLevel().getEntitiesOfClass(com.lanterna.especies.LanternaEntity.class, sp.getBoundingBox().inflate(200),
                    e -> e.getPersistentData().getString("lanterna_ct").startsWith(CT[0] + ":"))) if (n++ < 3) e.discard(); }));
        at(5, () -> server(estado));
        for (int k = 0; k < 8; k++) at(100, () -> server(estado));   // depois dos reforços
        at(5, () -> shot("c1_reforcos"));
        // mata todos os marcados como o jogador
        at(5, () -> server(sp -> {
            for (var e : sp.serverLevel().getEntitiesOfClass(com.lanterna.especies.LanternaEntity.class, sp.getBoundingBox().inflate(200),
                    e -> e.getPersistentData().getString("lanterna_ct").startsWith(CT[0] + ":"))) e.hurt(sp.damageSources().playerAttack(sp), 1000);
        }));
        at(20, () -> server(estado));
        // ---- desistir de outro contrato ----
        at(10, () -> server(sp -> {
            com.lanterna.tropa.Contratos.aceitar(sp, CT[1]);
            LanternaMod.LOGGER.info("AUTOTEST aceitou2 {} ofertas={}", CT[1], com.lanterna.tropa.Contratos.ofertas(sp, "oa", com.lanterna.tropa.Contratos.dia(sp)));
            var pos = com.lanterna.tropa.Contratos.mission(CT[1]).objectives().get(0).pos();
            if (pos != null) sp.teleportTo(sp.serverLevel(), pos.getX() + 20, 120, pos.getZ(), 90f, 30f);
        }));
        at(120, () -> server(sp -> LanternaMod.LOGGER.info("AUTOTEST antes de desistir marcados={}", sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, sp.getBoundingBox().inflate(200),
                e -> e.getPersistentData().getString("lanterna_ct").startsWith(CT[1] + ":")).size())));
        at(5, () -> server(sp -> com.lanterna.tropa.Contratos.desistir(sp, CT[1])));
        at(10, () -> server(sp -> LanternaMod.LOGGER.info("AUTOTEST depois de desistir marcados={} ofertas={}", sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, sp.getBoundingBox().inflate(200),
                e -> e.getPersistentData().getString("lanterna_ct").startsWith(CT[1] + ":")).size(), com.lanterna.tropa.Contratos.ofertas(sp, "oa", com.lanterna.tropa.Contratos.dia(sp)))));
        at(10, () -> shot("c2_quadro_novo"));
        at(20, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    /** 0.14: laser contínuo, trava de mira, piloto automático da nave, portas do cargueiro, missões novas. */
    private static void sceneNovidades() {
        Minecraft mc = Minecraft.getInstance();
        java.util.function.Consumer<String> cs = c -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), c));
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd("gamemode survival"); cmd("effect give @s resistance 99999 4 true"); cmd("lanterna resetar"); cmd("clear @s"); cmd("time set 6000");
            cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5"); });
        at(40, () -> cs.accept("lanterna escolher lanterna:green"));
        waitFor(() -> com.lanterna.client.ClientRingData.hasRing, 400);
        for (int k = 0; k < 8; k++) at(20, () -> { com.lanterna.client.flight.Autopilot.clear(); cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5 0 0"); });
        at(10, () -> server(sp -> { for (var n : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, sp.getBoundingBox().inflate(300), e -> !(e instanceof net.minecraft.world.entity.player.Player))) n.discard(); }));
        at(5, () -> cs.accept("fill 870 -61 870 930 -61 930 minecraft:smooth_stone"));
        if (SCENE.equals("auto")) { sceneNovidadesAuto(); return; }
        // ---- Trava de mira ----
        at(20, () -> server(sp -> {
            var e = com.lanterna.especies.LanternaEntity.pirata(sp.serverLevel(), false);
            e.moveTo(906.5, -60, 912.5, 0, 0); e.setNoAi(true); sp.serverLevel().addFreshEntity(e); }));
        at(20, () -> { mc.player.setYRot(-20); mc.player.setXRot(0); KeyMapping.click(KeyBindings.TRAVAR_MIRA.getKey()); });
        at(5, () -> { LanternaMod.LOGGER.info("AUTOTEST mira alvo={}", com.lanterna.client.combat.MiraTravada.alvo()); mc.player.setYRot(mc.player.getYRot() + 70); });
        at(30, () -> { var a = com.lanterna.client.combat.MiraTravada.alvo();
            double want = a == null ? 0 : Math.toDegrees(Math.atan2(-(a.getX() - mc.player.getX()), a.getZ() - mc.player.getZ()));
            LanternaMod.LOGGER.info("AUTOTEST mira yaw={} esperado~{}", net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot()), want); shot("n1_mira"); });
        // ---- Laser contínuo (segurando X com repetição de tecla) ----
        at(5, () -> { cs.accept("lanterna afinidade 24000"); });
        at(20, () -> { KeyBindings.CONSTRUCT_USE.setDown(true); KeyMapping.click(KeyBindings.CONSTRUCT_USE.getKey()); });
        for (int k = 0; k < 12; k++) {
            int kk = k;
            at(5, () -> { KeyMapping.click(KeyBindings.CONSTRUCT_USE.getKey()); // repetição do teclado
                if (kk % 3 == 0) server(sp -> { var l = sp.serverLevel().getEntitiesOfClass(com.lanterna.construct.ConstructEntity.class, sp.getBoundingBox().inflate(10));
                    LanternaMod.LOGGER.info("AUTOTEST laser t={} feixes={} idade={}", kk, l.size(), l.isEmpty() ? -1 : l.get(0).age()); }); });
        }
        at(1, () -> shot("n2_laser"));
        at(2, () -> { KeyBindings.CONSTRUCT_USE.setDown(false); KeyMapping.click(KeyBindings.TRAVAR_MIRA.getKey()); });
        // ---- Missões por posto ----
        at(10, () -> server(sp -> { com.lanterna.tropa.Nivel.addXp(sp, com.lanterna.tropa.Nivel.total(21), null);
            var of = com.lanterna.tropa.Contratos.ofertas(sp, "oa", com.lanterna.tropa.Contratos.dia(sp));
            LanternaMod.LOGGER.info("AUTOTEST ofertas nivel={} {}", com.lanterna.tropa.Nivel.nivel(sp), of);
            for (String id : of) { var m = com.lanterna.tropa.Contratos.mission(id);
                LanternaMod.LOGGER.info("AUTOTEST oferta {} -> {} objetivos={}", id, com.lanterna.tropa.Contratos.titulo(id).getString(), m == null ? null : m.objectives().size()); }
            com.lanterna.tropa.Contratos.aceitar(sp, of.get(4)); }));
        // ---- Cargueiro: todas as portas da borda saem ----
        at(20, () -> server(sp -> {
            var n = com.lanterna.naves.NaveEntity.create(sp.level(), com.lanterna.naves.NaveTipo.NAVE_CARGUEIRO, "1");
            n.moveTo(890.5, -60, 890.5, 0, 0); n.setDono(sp.getUUID()); sp.level().addFreshEntity(n);
            com.lanterna.naves.NaveInterior.entrar(sp, n); }));
        waitFor(() -> mc.level.dimension().location().getPath().equals("interior_naves") && mc.screen == null, 400);
        at(30, () -> server(sp -> {
            BLOCOS.clear();
            int i = 0;
            for (var p : net.minecraft.core.BlockPos.betweenClosed(sp.blockPosition().offset(-24, -3, -24), sp.blockPosition().offset(24, 6, 24))) {
                var st = sp.level().getBlockState(p);
                if (st.is(com.lanterna.naves.bloco.NaveBlocks.NAVE_PORTA.get()) && st.getValue(net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)
                    { BLOCOS.put("porta" + (i++), p.immutable()); LanternaMod.LOGGER.info("AUTOTEST porta {} hinge={}", p, st.getValue(net.minecraft.world.level.block.DoorBlock.HINGE)); }
            }
        }));
        at(5, () -> shot("n3_cargueiro"));
        for (int k = 0; k < 7; k++) {
            String nome = "porta" + k;
            at(10, () -> { if (BLOCOS.containsKey(nome)) { server(sp -> { var p = BLOCOS.get(nome); sp.teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5); }); } });
            at(10, () -> { if (BLOCOS.containsKey(nome)) usar(nome); });
            at(30, () -> { LanternaMod.LOGGER.info("AUTOTEST {} -> dim={}", nome, mc.level.dimension().location());
                if (!mc.level.dimension().location().getPath().equals("interior_naves")) server(sp -> {
                    var n = sp.serverLevel().getEntitiesOfClass(com.lanterna.naves.NaveEntity.class, sp.getBoundingBox().inflate(40)).stream().findFirst().orElse(null);
                    if (n != null) com.lanterna.naves.NaveInterior.entrar(sp, n); }); });
            at(30, () -> {});
        }
        // ---- Piloto automático: da Terra até Oa ----
        at(20, () -> server(sp -> { if (!sp.level().dimension().equals(com.lanterna.naves.NaveInterior.DIM)) {
            var n = sp.serverLevel().getEntitiesOfClass(com.lanterna.naves.NaveEntity.class, sp.getBoundingBox().inflate(40)).stream().findFirst().orElse(null);
            if (n != null) com.lanterna.naves.NaveInterior.entrar(sp, n); } }));
        waitFor(() -> mc.level.dimension().location().getPath().equals("interior_naves") && mc.screen == null, 400);
        at(20, () -> server(sp -> com.lanterna.naves.NaveInterior.iniciarViagem(sp, "oa")));
        for (int k = 0; k < 12; k++) at(40, () -> server(sp -> LanternaMod.LOGGER.info("AUTOTEST viagem emViagem={} volta={}", com.lanterna.naves.NaveInterior.emViagem(sp),
                sp.getPersistentData().getCompound("lanterna_interior_volta").getString("Dim"))));
        waitFor(() -> false, 1);
        at(20, () -> server(com.lanterna.naves.NaveInterior::sair));
        waitFor(() -> !mc.level.dimension().location().getPath().equals("interior_naves") && mc.screen == null, 600);
        at(40, () -> { LanternaMod.LOGGER.info("AUTOTEST saiu em {} pos={}", mc.level.dimension().location(), mc.player.position());
            server(sp -> LanternaMod.LOGGER.info("AUTOTEST nave perto={}", sp.serverLevel().getEntitiesOfClass(com.lanterna.naves.NaveEntity.class, sp.getBoundingBox().inflate(30)).size()));
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); });
        at(20, () -> shot("n4_chegou"));
        at(20, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    private static void sceneNovidadesAuto() {
        Minecraft mc = Minecraft.getInstance();
        at(20, () -> server(sp -> {
            var n = com.lanterna.naves.NaveEntity.create(sp.level(), com.lanterna.naves.NaveTipo.NAVE_CARGUEIRO, "1");
            n.moveTo(890.5, -60, 890.5, 0, 0); n.setDono(sp.getUUID()); sp.level().addFreshEntity(n);
            com.lanterna.naves.NaveInterior.entrar(sp, n); }));
        waitFor(() -> mc.level.dimension().location().getPath().equals("interior_naves") && mc.screen == null, 400);
        at(30, () -> mc.setScreen(new com.lanterna.client.screen.StarMapScreen()));
        at(20, () -> { try { var f = com.lanterna.client.screen.StarMapScreen.class.getDeclaredField("selected"); f.setAccessible(true); f.set(mc.screen, "oa");
            var g = com.lanterna.client.screen.StarMapScreen.class.getDeclaredMethod("go"); g.setAccessible(true); shot("n5_holomapa"); g.invoke(mc.screen); } catch (Exception e) { LanternaMod.LOGGER.error("x", e); } });
        for (int k = 0; k < 14; k++) at(40, () -> server(sp -> LanternaMod.LOGGER.info("AUTOTEST viagem emViagem={} volta={}", com.lanterna.naves.NaveInterior.emViagem(sp),
                sp.getPersistentData().getCompound("lanterna_interior_volta").getString("Dim"))));
        at(20, () -> server(com.lanterna.naves.NaveInterior::sair));
        waitFor(() -> !mc.level.dimension().location().getPath().equals("interior_naves") && mc.screen == null, 600);
        at(60, () -> { LanternaMod.LOGGER.info("AUTOTEST saiu em {} pos={}", mc.level.dimension().location(), mc.player.position());
            server(sp -> LanternaMod.LOGGER.info("AUTOTEST nave perto={}", sp.serverLevel().getEntitiesOfClass(com.lanterna.naves.NaveEntity.class, sp.getBoundingBox().inflate(30)).size()));
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); });
        at(20, () -> shot("n4_chegou"));
        at(20, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    /** As cidades das Tropas, vistas do alto. */
    private static void sceneCidades() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.hideGui = false;
            cmd("gamemode spectator"); cmd("time set 6000"); });
        for (String pl : new String[]{"oa", "korugar", "ysmault", "odym", "okaara", "nok", "zamaron", "ryut"}) {
            at(10, () -> server(sp -> com.lanterna.lore.LoreWorldData.get(sp.server).resetSite(pl + ":cidade")));
            at(10, () -> cmd("gamemode survival"));
            at(10, () -> cmd("execute in lanterna:" + pl + " run tp @s 0 200 -150"));
            waitFor(() -> mc.level.dimension().location().getPath().equals(pl) && mc.screen == null, 600);
            at(60, () -> {});
            at(40, () -> cmd("gamemode spectator"));
            at(5, () -> server(sp -> { int y = sp.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, -150);
                sp.teleportTo(sp.serverLevel(), 0.5, y + 34, -150 + 52, 180f, 32f); }));
            at(80, () -> shot("cid_" + pl));
            at(5, () -> server(sp -> { int y = sp.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, -150);
                sp.teleportTo(sp.serverLevel(), 0.5, y + 6, -150 + 20, 180f, -8f); }));
            at(40, () -> shot("cid_" + pl + "_bateria"));
        }
        at(20, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    /** Protótipos (pilotos) dos prédios da cidade da Tropa, para as fotos de conceito. */
    private static void scenePiloto() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.hideGui = false;
            cmd("gamemode spectator"); cmd("time set 6000"); cmd("weather clear"); cmd("gamerule doDaylightCycle false");
            cmd("execute in minecraft:overworld run tp @s 1100 -40 1000"); });
        waitFor(() -> mc.level.dimension().location().getPath().equals("overworld") && mc.screen == null, 400);
        at(60, () -> server(sp -> { for (int cx = 975 >> 4; cx <= 1215 >> 4; cx++) for (int cz = 975 >> 4; cz <= 1030 >> 4; cz++) sp.serverLevel().getChunk(cx, cz);
            for (var e : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new net.minecraft.world.phys.AABB(960, -70, 960, 1240, 0, 1050), e -> !(e instanceof net.minecraft.world.entity.player.Player))) e.discard();
            PilotoCidade.construir(sp.serverLevel()); }));
        Object[][] fotos = {
                {"p_mercado_rua", 1016.5, -55.0, 1000.5, 90f, 14f},
                {"p_mercado_loja", 1000.5, -58.3, 1006.8, 0f, 12f},
                {"p_qg_briefing", 1060.5, -55.0, 1005.5, 180f, 22f},
                {"p_hangar", 1120.5, -52.0, 1024.5, 180f, 14f},
                {"p_casa", 1180.5, -56.5, 1009.5, 180f, 16f},
                {"p_mercado_alto", 1016.5, -46.0, 986.5, 48f, 32f}};
        for (Object[] f : fotos) {
            at(10, () -> server(sp -> sp.teleportTo(sp.serverLevel(), (double) f[1], (double) f[2], (double) f[3], (float) f[4], (float) f[5])));
            at(50, () -> shot((String) f[0]));
        }
        at(20, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    /** Cidade completa (0.15): construção e fotos por dentro e por fora. */
    private static void sceneCidade2() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.hideGui = false;
            cmd("gamemode creative"); cmd("time set 6000"); cmd("gamerule doDaylightCycle false"); cmd("weather clear"); });
        for (String pl : new String[]{"oa", "korugar"}) {
            at(10, () -> server(sp -> { var w = com.lanterna.lore.LoreWorldData.get(sp.server); w.resetSite(pl + ":cidade2"); w.resetSite(pl + ":palacio");
                w.markSite(pl + ":palacio"); }));
            at(10, () -> cmd("execute in lanterna:" + pl + " run tp @s 0 200 -150"));
            waitFor(() -> mc.level.dimension().location().getPath().equals(pl) && mc.screen == null, 600);
            at(60, () -> {});
            waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 2400);
            at(40, () -> cmd("gamemode spectator"));
            int[] y = new int[1];
            at(5, () -> server(sp -> { y[0] = sp.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 30, -150); }));
            Object[][] fotos = {
                    {"alto", 0.5, 70.0, -150 + 95.0, 180f, 35f},
                    {"praca", 0.5, 8.0, -150 + 22.0, 180f, 12f},
                    {"qg_terreo", 0.5, 2.5, -150 - 30.5, 180f, 8f},
                    {"qg_briefing", 0.5, 9.5, -150 - 31.0, 180f, 22f},
                    {"qg_comandante", 6.5, 15.0, -150 - 32.0, 150f, 18f},
                    {"mercado", 58.5, 6.0, -150 + 0.5, 90f, 14f},
                    {"loja", 27.5, 1.5, -150 + 10.0, 0f, 10f},
                    {"hangar", 0.5, 8.0, -150 + 66.0, 180f, 10f},
                    {"arquivo", 36.5, 3.5, -150 - 30.5, 180f, 12f},
                    {"celas", -36.5, 2.5, -150 - 30.5, 180f, 10f},
                    {"enfermaria", 36.5, 2.5, -150 + 30.5, 0f, 10f},
                    {"academia", -38.5, 12.0, -150 + 36.0, 180f, 30f},
                    {"casa", -34.5, 2.0, -150 + 41.0, 180f, 15f}};
            for (Object[] f : fotos) {
                at(5, () -> server(sp -> sp.teleportTo(sp.serverLevel(), (double) f[1], (double) f[2] + y[0], (double) f[3], (float) f[4], (float) f[5])));
                at(40, () -> shot("c2_" + pl + "_" + f[0]));
            }
            at(5, () -> cmd("gamemode creative"));
        }
        at(20, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    /** Palácio dos Guardiões: barreira, Conselho, provação, técnica e loja da cidade. */
    private static Entity achar(String funcao, net.minecraft.server.level.ServerPlayer sp) {
        for (Entity e : sp.serverLevel().getAllEntities()) {
            if (e instanceof com.lanterna.especies.LanternaEntity l && l.funcao().equals(funcao)) return e;
            if (e instanceof com.lanterna.lore.entity.RaceEntity r && r.role().equals(funcao)) return e;
        }
        return null;
    }
    private static void clicarNpc(String funcao) {
        Minecraft mc = Minecraft.getInstance();
        server(sp -> {
            Entity e = achar(funcao, sp);
            if (e == null) { LanternaMod.LOGGER.info("AUTOTEST npc {} nao achado", funcao); return; }
            sp.teleportTo(sp.serverLevel(), e.getX() + 1.5, e.getY(), e.getZ() + 1.5, 135f, 10f);
            int id = e.getId();
            mc.execute(() -> mc.tell(() -> {}));
            sp.server.execute(() -> {});
            LanternaMod.LOGGER.info("AUTOTEST npc {} em {}", funcao, e.blockPosition());
            CLICAR.set(id);
        });
    }
    private static final java.util.concurrent.atomic.AtomicInteger CLICAR = new java.util.concurrent.atomic.AtomicInteger(-1);
    private static final java.util.concurrent.atomic.AtomicBoolean TEM_VARO = new java.util.concurrent.atomic.AtomicBoolean();
    private static void sceneTrono() {
        Minecraft mc = Minecraft.getInstance();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ServerTickEvent ev) -> {
            if (ev.phase == net.minecraftforge.event.TickEvent.Phase.END && ev.getServer().getTickCount() % 40 == 0 && !TEM_VARO.get()) {
                var lvl = ev.getServer().getLevel(com.lanterna.space.SpaceBodies.byId("oa").dimension());
                if (lvl != null) for (Entity e : lvl.getAllEntities()) if (e instanceof com.lanterna.lore.entity.RaceEntity r && r.role().equals("conselho_varo")) { TEM_VARO.set(true); break; }
            }
        });
        Runnable clicar = () -> { int id = CLICAR.getAndSet(-1); var e = id >= 0 ? mc.level.getEntity(id) : null;
            if (e != null) mc.gameMode.interact(mc.player, e, net.minecraft.world.InteractionHand.MAIN_HAND);
            LanternaMod.LOGGER.info("AUTOTEST clicou {} tela={}", e, mc.screen == null ? null : mc.screen.getClass().getSimpleName()); };
        Runnable estado = () -> server(sp -> { var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null);
            LanternaMod.LOGGER.info("AUTOTEST estado nivel={} ativas={} feitas={} tecnicas={} slots={},{} pos={} dim={}", com.lanterna.tropa.Nivel.nivel(d.xp()),
                d.active().keySet(), d.completed().stream().filter(x -> x.startsWith("pal_") || x.startsWith("prov_") || x.startsWith("cid_")).toList(),
                d.tecnicas(), d.slotTecnica(0), d.slotTecnica(1), sp.blockPosition(), sp.level().dimension().location()); });
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd("gamemode survival"); cmd("effect give @s resistance 99999 4 true"); cmd("time set 6000"); cmd("lanterna resetar"); cmd("clear @s");
            cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5"); });
        at(40, () -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna escolher lanterna:green")));
        waitFor(() -> com.lanterna.client.ClientRingData.hasRing, 400);
        for (int k = 0; k < 6; k++) at(20, () -> { com.lanterna.client.flight.Autopilot.clear(); cmd("execute in lanterna:oa run tp @s 0 200 -150"); });
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa") && mc.screen == null, 600);
        at(60, () -> {});
        waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 2400);
        // ---- loja e painel na cidade ----
        at(40, () -> server(sp -> com.lanterna.capability.ModCapabilities.get(sp).ifPresent(r -> { if (r.flightEnabled()) com.lanterna.power.RingPowers.toggleFlight(sp); })));
        at(5, () -> clicarNpc("intendente"));
        at(30, clicar);
        at(20, () -> shot("t1_loja"));
        at(5, () -> mc.setScreen(null));
        at(5, () -> clicarNpc("comandante"));
        at(30, clicar);
        at(20, () -> shot("t2_painel_comandante"));
        at(5, () -> { com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.tropa.ContratoPacket("cid_oa_comandante_1", true)); mc.setScreen(null); });
        at(20, estado);
        // ---- barreira (nível baixo) ----
        at(10, () -> server(sp -> { var w = com.lanterna.lore.LoreWorldData.get(sp.server); w.resetSite("oa:palacio"); }));
        at(10, () -> cmd("tp @s 0 200 -360"));
        at(80, () -> {});
        waitFor(() -> TEM_VARO.get(), 3000);
        at(60, () -> cmd("tp @s 0.5 171 -440"));
        at(60, estado);
        // ---- chamado ----
        at(5, () -> server(sp -> { var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null);
            com.lanterna.tropa.Nivel.addXp(sp, com.lanterna.tropa.Nivel.total(36), null);
            for (String b : new String[]{"vorgath", "mammok", "thessara", "morrow", "penitente"}) d.addBossKill(b); }));
        at(260, estado);
        at(5, () -> cmd("tp @s 0.5 171 -436"));
        at(60, () -> shot("t3_portao"));
        at(5, () -> clicarNpc("conselho_varo"));
        at(30, clicar);
        at(20, () -> { mc.setScreen(null); shot("t4_salao"); });
        at(5, () -> clicarNpc("conselho_selun"));
        at(30, clicar);
        at(5, () -> { mc.setScreen(null); clicarNpc("capitao_comando"); });
        at(30, clicar);
        at(10, () -> mc.setScreen(null));
        at(10, estado);
        // ---- provação da Onda ----
        at(5, () -> clicarNpc("capitao_onda"));
        at(30, clicar);
        at(20, () -> shot("t5_capitao"));
        at(5, () -> { com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.tropa.ContratoPacket("prov_onda", true)); mc.setScreen(null); });
        at(20, () -> server(sp -> sp.teleportTo(sp.serverLevel(), com.lanterna.cidade.Palacio.CAMPO.getX() + 10.5, com.lanterna.cidade.Palacio.Y, com.lanterna.cidade.Palacio.CAMPO.getZ() + 0.5, 90f, 10f)));
        at(80, () -> server(sp -> LanternaMod.LOGGER.info("AUTOTEST piratas no campo={}", sp.serverLevel().getEntitiesOfClass(com.lanterna.especies.LanternaEntity.class, sp.getBoundingBox().inflate(40), e -> e.isPirata()).size())));
        at(5, () -> shot("t6_campo"));
        at(5, () -> server(sp -> { for (var e : sp.serverLevel().getEntitiesOfClass(com.lanterna.especies.LanternaEntity.class, sp.getBoundingBox().inflate(40), e -> e.isPirata()))
            e.hurt(sp.damageSources().playerAttack(sp), 1000); }));
        at(40, estado);
        // ---- usar técnica ----
        at(5, () -> server(sp -> { var e = com.lanterna.especies.LanternaEntity.pirata(sp.serverLevel(), false); e.moveTo(sp.getX() + 3, sp.getY(), sp.getZ(), 0, 0); sp.serverLevel().addFreshEntity(e);
            var e2 = com.lanterna.especies.LanternaEntity.pirata(sp.serverLevel(), false); e2.moveTo(sp.getX() - 3, sp.getY(), sp.getZ() + 2, 0, 0); sp.serverLevel().addFreshEntity(e2); }));
        at(20, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); KeyMapping.click(KeyBindings.TECNICA[0].getKey()); });
        at(3, () -> shot("t7_onda"));
        at(20, () -> server(sp -> { var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null); com.lanterna.cidade.Tecnicas.desbloquear(sp, d, "bastiao"); }));
        at(60, () -> KeyMapping.click(KeyBindings.TECNICA[1].getKey()));
        at(10, () -> shot("t8_bastiao"));
        at(10, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); mc.setScreen(new com.lanterna.client.cidade.ClientTecnicas.Tela()); });
        at(20, () -> shot("t9_tela"));
        at(5, () -> mc.setScreen(null));
        // ---- fotos do palácio ----
        Object[][] fotos = {{"pal_alto", 0.5, 230.0, -330.0, 180f, 30f}, {"pal_patio", 0.5, 176.0, -414.0, 180f, 8f}, {"pal_salao_dentro", 0.5, 174.0, -464.0, 180f, 5f},
                {"pal_camara", 0.5, 173.0, -496.0, 180f, -5f}, {"pal_biblioteca", -24.5, 173.0, -498.0, 180f, 15f}, {"pal_ala", -27.5, 173.0, -484.0, 90f, 12f},
                {"pal_ponte", 0.5, 150.0, -280.0, 180f, 5f}};
        at(5, () -> cmd("gamemode spectator"));
        for (Object[] f : fotos) {
            at(5, () -> server(sp -> sp.teleportTo(sp.serverLevel(), (double) f[1], (double) f[2], (double) f[3], (float) f[4], (float) f[5])));
            at(70, () -> shot((String) f[0]));
        }
        at(20, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    private static void sceneBarreira() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { cmd("gamemode survival"); cmd("lanterna resetar"); cmd("execute in lanterna:oa run tp @s 0.5 175 -440"); });
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa") && mc.screen == null, 600);
        for (int k = 0; k < 4; k++) at(30, () -> server(sp -> LanternaMod.LOGGER.info("AUTOTEST barreira pos={} permitido={} criativo={} dim={} oa={}", sp.blockPosition(),
                com.lanterna.cidade.Palacio.permitido(sp), sp.isCreative(), sp.level().dimension().location(), com.lanterna.space.SpaceBodies.byId("oa").dimension())));
        at(20, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    private static void sceneFotos15() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.hideGui = true;
            cmd("gamemode creative"); cmd("time set 6000"); });
        // sabotador da 1ª missão do Comandante (aceita no teste anterior)
        at(10, () -> cmd("execute in lanterna:oa run tp @s 0.5 80 -110"));
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa") && mc.screen == null, 600);
        at(200, () -> server(sp -> LanternaMod.LOGGER.info("AUTOTEST sabotador={}", sp.serverLevel().getEntitiesOfClass(com.lanterna.especies.LanternaEntity.class,
                sp.getBoundingBox().inflate(120), e -> "sabotador".equals(e.getPersistentData().getString("lanterna_alvo"))).size())));
        at(5, () -> cmd("gamemode spectator"));
        Object[][] fotos = {
                {"oa", "f_cidade_oa", 70.5, 140.0, -60.0, 135f, 30f},
                {"oa", "f_palacio", 70.5, 215.0, -400.0, 135f, 22f},
                {"oa", "f_palacio_portao", 0.5, 178.0, -395.0, 180f, 4f},
                {"oa", "f_ponte", 30.5, 120.0, -230.0, 150f, 12f},
                {"korugar", "f_cidade_korugar", 70.5, 140.0, -60.0, 135f, 30f}};
        for (Object[] f : fotos) {
            at(5, () -> cmd("execute in lanterna:" + f[0] + " run tp @s " + f[2] + " " + f[3] + " " + f[4] + " " + f[5] + " " + f[6]));
            at(240, () -> shot((String) f[1]));
        }
        at(20, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    /** Auditoria: toda missão de "derrotar N" precisa ser possível de completar. */
    private static final java.util.List<String> AUD_LOG = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static void sceneAuditoria() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd("gamemode survival"); cmd("effect give @s resistance 99999 4 true"); cmd("effect give @s regeneration 99999 4 true"); cmd("time set 6000");
            cmd("lanterna resetar"); cmd("clear @s"); cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5"); });
        at(40, () -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna escolher lanterna:green")));
        waitFor(() -> com.lanterna.client.ClientRingData.hasRing, 400);
        for (int k = 0; k < 6; k++) at(20, () -> { com.lanterna.client.flight.Autopilot.clear(); cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5"); });
        at(20, () -> server(sp -> {
            com.lanterna.capability.ModCapabilities.get(sp).ifPresent(r -> { if (r.flightEnabled()) com.lanterna.power.RingPowers.toggleFlight(sp); });
            var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null);
            for (String id : new java.util.ArrayList<>(d.active().keySet())) d.abandon(id);
            com.lanterna.tropa.Nivel.addXp(sp, com.lanterna.tropa.Nivel.total(46), null);
            for (String id : new java.util.ArrayList<>(d.active().keySet())) d.abandon(id);
            Auditoria.iniciar(sp);
        }));
        waitFor(() -> Auditoria.FIM.get(), 20 * 60 * 30);
        at(20, () -> { for (String l : AUD_LOG) LanternaMod.LOGGER.info("AUTOTEST AUDIT {}", l); LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    static final class Auditoria {
        static final java.util.concurrent.atomic.AtomicBoolean FIM = new java.util.concurrent.atomic.AtomicBoolean();
        record Caso(String id, String planeta, @org.jetbrains.annotations.Nullable net.minecraft.core.BlockPos onde) {}
        static final java.util.List<Caso> CASOS = new java.util.ArrayList<>();
        static int atual = -1, ciclos, maxVivos, mortos;
        static net.minecraft.server.level.ServerPlayer P;

        static String contrato(net.minecraft.server.level.ServerPlayer sp, String tipo) {
            long hoje = com.lanterna.tropa.Contratos.dia(sp);
            for (long dd = hoje + 2000; dd < hoje + 2400; dd++) for (String id : com.lanterna.tropa.Contratos.ofertas("korugar", dd)) {
                var m = com.lanterna.tropa.Contratos.mission(id);
                if (m == null) continue;
                String t = m.objectives().get(0).target();
                if (tipo.equals("piratas") && t.equals("papel:pirata")) return id;
                if (tipo.equals("ecos") && t.equals("lanterna:eco_cinzento")) return id;
                if (tipo.equals("invasores") && t.equals("papel:inimigo")) return id;
                if (tipo.equals("capitao") && t.equals("papel:capitao")) return id;
            }
            return null;
        }

        static void iniciar(net.minecraft.server.level.ServerPlayer sp) {
            P = sp;
            for (String t : new String[]{"piratas", "ecos", "invasores", "capitao"}) { String id = contrato(sp, t); if (id != null) CASOS.add(new Caso(id, null, null)); }
            for (String id : new String[]{"cid_korugar_comandante_1", "cid_korugar_comandante_2", "cid_korugar_medico_1", "cid_korugar_carcereiro_1",
                    "cid_korugar_instrutor_2", "cid_korugar_instrutor_3", "cid_korugar_carcereiro_3"}) CASOS.add(new Caso(id, null, null));
            for (String id : new String[]{"prov_bastiao", "prov_onda", "prov_armadura", "prov_comando"}) CASOS.add(new Caso(id, null, null));
            CASOS.add(new Caso("prova_green", "odym", new net.minecraft.core.BlockPos(260, 0, 260)));
            CASOS.add(new Caso("patrulha", "oa", new net.minecraft.core.BlockPos(300, 0, 300)));
            CASOS.add(new Caso("inicio_green_3", "oa", com.lanterna.lore.PlanetLore.HUB.offset(4, 0, 12)));
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(Auditoria::tick);
            proximo();
        }

        static void proximo() {
            atual++;
            ciclos = 0; maxVivos = 0; mortos = 0;
            if (atual >= CASOS.size()) { FIM.set(true); return; }
            Caso c = CASOS.get(atual);
            var d = com.lanterna.mission.LoreCapability.get(P).orElse(null);
            for (String id : new java.util.ArrayList<>(d.active().keySet())) d.abandon(id);
            d.completed().remove(c.id);
            com.lanterna.mission.MissionManager.start(P, c.id);
            var m = com.lanterna.mission.Missions.get(c.id);
            // vai até o lugar do primeiro objetivo de derrotar (ou o lugar do caso)
            String planeta = c.planeta;
            net.minecraft.core.BlockPos onde = c.onde;
            for (var o : m.objectives()) if ((o.type() == com.lanterna.mission.Objective.Type.KILL || o.type() == com.lanterna.mission.Objective.Type.DUMMY) && o.pos() != null) { planeta = o.planet(); onde = o.pos(); break; }
            var body = com.lanterna.space.SpaceBodies.byId(planeta);
            var lvl = P.server.getLevel(body.dimension());
            int x = onde.getX() + 8, z = onde.getZ() + 8;
            lvl.getChunk(x >> 4, z >> 4);
            int y = onde.getY() >= 150 ? onde.getY() : lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            P.teleportTo(lvl, x + 0.5, y + 1, z + 0.5, 0, 0);
            AUD_LOG.add("INICIO " + c.id + " em " + planeta + " " + new net.minecraft.core.BlockPos(x, y, z).toShortString());
        }

        static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent ev) {
            if (ev.phase != net.minecraftforge.event.TickEvent.Phase.END || FIM.get() || atual < 0 || atual >= CASOS.size()) return;
            if (ev.getServer().getTickCount() % 40 != 0) return;
            Caso c = CASOS.get(atual);
            var d = com.lanterna.mission.LoreCapability.get(P).orElse(null);
            var m = com.lanterna.mission.Missions.get(c.id);
            ciclos++;
            var lvl = P.serverLevel();
            // derrota o que conta para a missão
            boolean tudo = true;
            StringBuilder prog = new StringBuilder();
            for (int i = 0; i < m.objectives().size(); i++) {
                var o = m.objectives().get(i);
                int pr = d.isActive(c.id) ? d.progress(c.id, i) : (d.isDone(c.id) ? o.count() : -1);
                if (o.type() != com.lanterna.mission.Objective.Type.KILL && o.type() != com.lanterna.mission.Objective.Type.DUMMY) continue;
                prog.append(o.target()).append(' ').append(pr).append('/').append(o.count()).append(' ');
                if (pr < o.count()) tudo = false; else continue;
                var vivos = lvl.getEntities((Entity) null, P.getBoundingBox().inflate(130, 90, 130), e -> o.type() == com.lanterna.mission.Objective.Type.DUMMY
                        ? e.getType() == com.lanterna.lore.LoreEntities.ALVO_TREINO.get() && e.isAlive() : com.lanterna.mission.AlvosGarantidos.conta(e, o.target()));
                maxVivos = Math.max(maxVivos, vivos.size());
                for (Entity e : vivos) { e.hurt(P.damageSources().playerAttack(P), 5000); mortos++; }
            }
            if (tudo || d.isDone(c.id)) {
                AUD_LOG.add("OK    " + c.id + " em " + (ciclos * 2) + " s | " + prog + "| vivos de uma vez=" + maxVivos + " derrotados=" + mortos);
                proximo();
            } else if (ciclos > 75) {
                AUD_LOG.add("FALHA " + c.id + " | " + prog + "| vivos de uma vez=" + maxVivos + " derrotados=" + mortos);
                proximo();
            }
        }
    }

    private static void sceneMiraeth() {
        Minecraft mc = Minecraft.getInstance();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ServerTickEvent ev) -> {
            if (ev.phase == net.minecraftforge.event.TickEvent.Phase.END && ev.getServer().getTickCount() % 40 == 0 && !TEM_VARO.get()) {
                var lvl = ev.getServer().getLevel(com.lanterna.space.SpaceBodies.byId("oa").dimension());
                if (lvl != null) for (Entity e : lvl.getAllEntities()) if (e instanceof com.lanterna.lore.entity.RaceEntity r && r.role().equals("conselho_varo")) { TEM_VARO.set(true); break; }
            }
        });
        Runnable clicar = () -> { int id = CLICAR.getAndSet(-1); var e = id >= 0 ? mc.level.getEntity(id) : null;
            if (e != null) mc.gameMode.interact(mc.player, e, net.minecraft.world.InteractionHand.MAIN_HAND);
            LanternaMod.LOGGER.info("AUTOTEST clicou {} tela={}", e, mc.screen == null ? null : mc.screen.getClass().getSimpleName()); };
        Runnable estado = () -> server(sp -> { var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null);
            LanternaMod.LOGGER.info("AUTOTEST estado nivel={} ativas={} feitas={} pos={}", com.lanterna.tropa.Nivel.nivel(d.xp()),
                d.active().keySet(), d.completed().stream().filter(x -> x.startsWith("pal_") || x.startsWith("prov_")).toList(), sp.blockPosition());
            for (String r : new String[]{"conselho_dorvek", "conselho_miraeth", "conselho_varo", "conselho_ilyss", "conselho_selun"}) {
                var e = achar(r, sp); LanternaMod.LOGGER.info("AUTOTEST guardiao {} {}", r, e == null ? "SUMIU" : e.blockPosition() + " " + e.getClass().getSimpleName()); } });
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd("gamemode survival"); cmd("effect give @s resistance 99999 4 true"); cmd("time set 6000"); cmd("lanterna resetar"); cmd("clear @s");
            cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5"); });
        at(40, () -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna escolher lanterna:green")));
        waitFor(() -> com.lanterna.client.ClientRingData.hasRing, 400);
        for (int k = 0; k < 6; k++) at(20, () -> { com.lanterna.client.flight.Autopilot.clear(); cmd("execute in lanterna:oa run tp @s 0 200 -360"); });
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa") && mc.screen == null, 600);
        at(40, () -> server(sp -> com.lanterna.capability.ModCapabilities.get(sp).ifPresent(r -> { if (r.flightEnabled()) com.lanterna.power.RingPowers.toggleFlight(sp); })));
        at(10, () -> server(sp -> { var w = com.lanterna.lore.LoreWorldData.get(sp.server); w.resetSite("oa:palacio"); }));
        at(80, () -> {});
        waitFor(() -> TEM_VARO.get(), 3000);
        waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 2400);
        at(5, () -> server(sp -> { var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null);
            com.lanterna.tropa.Nivel.addXp(sp, com.lanterna.tropa.Nivel.total(41), null);
            for (String b : new String[]{"vorgath", "mammok", "thessara", "morrow", "penitente"}) d.addBossKill(b); }));
        at(260, () -> cmd("tp @s 0.5 171 -440"));
        at(100, estado);
        at(5, () -> clicarNpc("conselho_varo"));
        at(30, clicar);
        at(10, () -> mc.setScreen(null));
        at(5, () -> clicarNpc("conselho_selun"));
        at(30, clicar);
        at(5, () -> { mc.setScreen(null); clicarNpc("capitao_comando"); });
        at(30, clicar);
        at(10, () -> mc.setScreen(null));
        at(5, () -> server(sp -> { var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null);
            com.lanterna.cidade.Tecnicas.desbloquear(sp, d, "onda"); com.lanterna.cidade.Tecnicas.desbloquear(sp, d, "bastiao");
            d.completed().add("prov_onda"); d.completed().add("prov_bastiao"); }));
        at(10, estado);
        // jeito real: sobe os degraus do trono da Miraeth e olha para ela
        at(5, () -> server(sp -> { var t = com.lanterna.cidade.Palacio.SALAO.offset(-5, 0, -9);
            sp.teleportTo(sp.serverLevel(), t.getX() + 0.5, t.getY(), t.getZ() + 6.5, 180f, -10f); }));
        at(40, () -> shot("m0_degraus"));
        at(5, () -> server(sp -> { Entity e = achar("conselho_miraeth", sp); var t = com.lanterna.cidade.Palacio.SALAO.offset(-5, 0, -9);
            double x = t.getX() + 0.5, y = t.getY() + 3, z = t.getZ() + 2.5;
            double dx = e.getX() - x, dz = e.getZ() - z, dy = e.getEyeY() - (y + 1.62);
            sp.teleportTo(sp.serverLevel(), x, y, z, (float) Math.toDegrees(Math.atan2(-dx, dz)), (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))); }));
        at(40, () -> { LanternaMod.LOGGER.info("AUTOTEST mira {} {} y={}", mc.hitResult == null ? null : mc.hitResult.getType(), mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult eh ? eh.getEntity() : "", mc.player.getY());
            shot("m1_trono");
            if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult eh) mc.gameMode.interact(mc.player, eh.getEntity(), net.minecraft.world.InteractionHand.MAIN_HAND); });
        at(30, () -> { LanternaMod.LOGGER.info("AUTOTEST tela {}", mc.screen == null ? null : mc.screen.getClass().getSimpleName()); shot("m2_painel"); });
        at(5, () -> { com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.tropa.ContratoPacket("pal_honra", true)); mc.setScreen(null); });
        at(30, estado);
        at(20, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    /** Equilíbrio PvP: mede o dano real de cada golpe entre jogadores de vários níveis, e de monstros. */
    private static void scenePvp() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { cmd("gamemode survival"); cmd("effect clear @s"); cmd("difficulty normal"); cmd("time set 6000"); cmd("lanterna resetar"); cmd("clear @s");
            cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5"); });
        at(160, () -> server(sp -> {
            var lvl = sp.serverLevel();
            LanternaMod.LOGGER.info("AUTOTEST modo {} dificuldade {}", sp.gameMode.getGameModeForPlayer(), lvl.getDifficulty());
            var rival = net.minecraftforge.common.util.FakePlayerFactory.get(lvl, new com.mojang.authlib.GameProfile(java.util.UUID.fromString("11111111-2222-3333-4444-555555555555"), "Rival"));
            rival.moveTo(sp.getX() + 2, sp.getY(), sp.getZ());
            var zumbi = net.minecraft.world.entity.EntityType.ZOMBIE.create(lvl);
            int[][] pares = {{1, 1}, {10, 10}, {25, 25}, {50, 50}, {50, 10}, {10, 50}, {50, 30}};
            for (int[] par : pares) {
                int na = par[0], nv = par[1];
                int afA = na >= 50 ? 5 : na >= 25 ? 3 : na >= 10 ? 1 : 0;
                float forca = 1f + 0.01f * na, pow = (1f + 0.2f * afA) * forca;
                com.lanterna.mission.LoreCapability.get(rival).ifPresent(d -> d.setXp(com.lanterna.tropa.Nivel.total(na)));
                com.lanterna.mission.LoreCapability.get(sp).ifPresent(d -> d.setXp(com.lanterna.tropa.Nivel.total(nv)));
                com.lanterna.tropa.Nivel.aplicar(sp);
                float hp = sp.getMaxHealth();
                double oldHp = 20 + (nv / 5) * 2;
                StringBuilder sb = new StringBuilder(String.format("PVP atacante nv%d x alvo nv%d | vida %.0f armadura %.1f dureza %.1f |", na, nv, hp,
                        sp.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR), sp.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS)));
                Object[][] golpes = {{"soco", 1f + (1.5f + afA) * forca}, {"punho", 10f * pow}, {"martelo", 18f * pow}, {"onda", 12f * forca}, {"raio", 3f * pow}, {"espada", 7f}};
                for (Object[] g : golpes) {
                    float bruto = (float) g[1];
                    sp.setHealth(hp); sp.invulnerableTime = 0;
                    String nome = (String) g[0];
                    rival.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, nome.equals("espada") ? new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD) : net.minecraft.world.item.ItemStack.EMPTY);
                    if (nome.equals("soco") || nome.equals("espada")) sp.hurt(sp.damageSources().playerAttack(rival), bruto);
                    else com.lanterna.combate.Equilibrio.ferirComPoder(sp, sp.damageSources().playerAttack(rival), bruto);
                    float tirou = hp - sp.getHealth();
                    sb.append(String.format(" %s: antes %.0f/%.0f (%s) agora %.1f (%d golpes) |", g[0], bruto, oldHp, bruto >= oldHp ? "HIT KILL" : (int) Math.ceil(oldHp / bruto) + " golpes",
                            tirou, tirou <= 0 ? 0 : (int) Math.ceil(hp / tirou)));
                }
                AUD_LOG.add(sb.toString());
            }
            for (int nv : new int[]{1, 25, 50}) {
                com.lanterna.mission.LoreCapability.get(sp).ifPresent(d -> d.setXp(com.lanterna.tropa.Nivel.total(nv)));
                com.lanterna.tropa.Nivel.aplicar(sp);
                float hp = sp.getMaxHealth();
                StringBuilder sb = new StringBuilder(String.format("MOB contra nv%d vida %.0f |", nv, hp));
                for (float dmg : new float[]{3, 7, 16}) {
                    sp.setHealth(hp); sp.invulnerableTime = 0;
                    sp.hurt(sp.damageSources().mobAttack(zumbi), dmg);
                    float tirou = hp - sp.getHealth();
                    sb.append(String.format(" golpe %.0f tirou %.1f (%d golpes) |", dmg, tirou, (int) Math.ceil(hp / tirou)));
                }
                AUD_LOG.add(sb.toString());
            }
            sp.setHealth(sp.getMaxHealth());
        }));
        at(40, () -> { for (String l : AUD_LOG) LanternaMod.LOGGER.info("AUTOTEST {}", l); LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    /** Guildas: criar, símbolo, bandeira (base pronta), minerador, general, escudo/HUD no espaço e invasão completa. */
    private static final java.util.List<String> GLOG = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final String[] G_PLANETAS = new String[2];
    private static void glog(String s) { GLOG.add(s); LanternaMod.LOGGER.info("AUTOTEST G {}", s); }
    private static void sceneGuilda() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd("gamemode survival"); cmd("effect give @s resistance 99999 4 true"); cmd("effect give @s regeneration 99999 4 true"); cmd("difficulty normal");
            cmd("time set 6000"); cmd("lanterna resetar"); cmd("clear @s"); cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5"); });
        at(40, () -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna escolher lanterna:green")));
        waitFor(() -> com.lanterna.client.ClientRingData.hasRing, 400);
        for (int k = 0; k < 6; k++) at(20, () -> { com.lanterna.client.flight.Autopilot.clear(); cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5"); });
        at(20, () -> server(sp -> {
            com.lanterna.capability.ModCapabilities.get(sp).ifPresent(r -> { if (r.flightEnabled()) com.lanterna.power.RingPowers.toggleFlight(sp); });
            var gs = com.lanterna.guilda.Guildas.get(sp.server);
            for (var g : new java.util.ArrayList<>(gs.guildas.values())) { for (var c : gs.planetasDe(g.id)) com.lanterna.guilda.Conquistas.largar(sp.server, c); gs.desfazer(g); }
            // dois planetas gerados
            int n = 0;
            for (int r = 2; r < 6 && n < 2; r++) for (int sx = -r; sx <= r && n < 2; sx++) {
                var b = com.lanterna.space.ProceduralPlanets.inSector(sx, r);
                if (b != null && sp.server.getLevel(b.dimension()) != null) G_PLANETAS[n++] = b.id();
            }
            glog("planetas " + G_PLANETAS[0] + " " + G_PLANETAS[1]);
        }));
        // ---- criar a guilda (abre o editor de símbolo) ----
        at(10, () -> cmd("guilda criar Zezinho do Grau"));
        at(30, () -> { glog("tela " + (mc.screen == null ? null : mc.screen.getClass().getSimpleName())); shot("g1_editor_vazio"); });
        at(5, () -> {
            // desenha um "Z" com raio (símbolo da guilda) e salva pelo próprio pacote do editor
            byte[] px = new byte[256];
            for (int x = 2; x <= 13; x++) { px[2 * 16 + x] = 7; px[3 * 16 + x] = 7; px[12 * 16 + x] = 7; px[13 * 16 + x] = 7; }
            for (int i = 0; i < 9; i++) { int x = 12 - i, y = 4 + i; px[y * 16 + x] = 1; px[y * 16 + x + 1] = 1; if (x - 1 >= 0) px[y * 16 + x - 1] = 4; }
            for (int y = 0; y < 16; y++) { px[y * 16] = 9; px[y * 16 + 15] = 9; }
            if (mc.screen instanceof com.lanterna.client.guilda.SimboloScreen) {
                try { var f = com.lanterna.client.guilda.SimboloScreen.class.getDeclaredField("px"); f.setAccessible(true); System.arraycopy(px, 0, (byte[]) f.get(mc.screen), 0, 256); } catch (Exception e) { throw new RuntimeException(e); }
            }
        });
        at(10, () -> shot("g2_editor_desenhado"));
        at(5, () -> { try { var f = com.lanterna.client.guilda.SimboloScreen.class.getDeclaredField("px"); f.setAccessible(true);
            com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.guilda.GuildaAcaoPacket("", "simbolo", ((byte[]) f.get(mc.screen)).clone())); } catch (Exception e) { throw new RuntimeException(e); }
            mc.setScreen(null); });
        // ---- fincar a bandeira no planeta A ----
        at(20, () -> server(sp -> {
            var b = com.lanterna.space.SpaceBodies.byId(G_PLANETAS[0]);
            var lvl = sp.server.getLevel(b.dimension());
            int x = b.procedural().regionX() + 40, z = b.procedural().regionZ() + 40;
            lvl.getChunk(x >> 4, z >> 4);
            int y = lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            sp.teleportTo(lvl, x + 0.5, y + 1, z + 4.5, 180, 20);
        }));
        at(60, () -> server(sp -> {
            var lvl = sp.serverLevel();
            BlockPos pos = sp.blockPosition().offset(0, 0, -4);
            pos = pos.atY(lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()));
            var st = com.lanterna.guilda.GuildaItems.BANDEIRA.get().defaultBlockState();
            lvl.setBlock(pos, st, 3);
            st.getBlock().setPlacedBy(lvl, pos, st, sp, new net.minecraft.world.item.ItemStack(com.lanterna.guilda.GuildaItems.BANDEIRA_ITEM.get()));
            var gs = com.lanterna.guilda.Guildas.get(sp.server);
            glog("bandeira em " + pos.toShortString() + " conquista=" + (gs.conquistas.get(G_PLANETAS[0]) != null));
        }));
        waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 2000);
        at(40, () -> { cmd("gamemode spectator"); server(sp -> { var c = com.lanterna.guilda.Guildas.get(sp.server).conquistas.get(G_PLANETAS[0]);
            sp.teleportTo(sp.serverLevel(), c.bandeira.getX() + 0.5, c.bandeira.getY() + 34, c.bandeira.getZ() + 38.5, 180, 38); }); });
        at(80, () -> shot("g3_base_alto"));
        at(5, () -> server(sp -> { var c = com.lanterna.guilda.Guildas.get(sp.server).conquistas.get(G_PLANETAS[0]);
            sp.teleportTo(sp.serverLevel(), c.bandeira.getX() + 30.5, c.bandeira.getY() + 18, c.bandeira.getZ() - 30.5, 45, 25); }));
        at(60, () -> shot("g3b_base_lado"));
        at(5, () -> cmd("gamemode survival"));
        at(5, () -> server(sp -> { var c = com.lanterna.guilda.Guildas.get(sp.server).conquistas.get(G_PLANETAS[0]);
            sp.teleportTo(sp.serverLevel(), c.bandeira.getX() + 6.5, c.bandeira.getY() + 2, c.bandeira.getZ() + 7.5, 140, -12); }));
        at(60, () -> shot("g4_bandeira"));
        // ---- minerador e general ----
        at(5, () -> server(sp -> { var c = com.lanterna.guilda.Guildas.get(sp.server).conquistas.get(G_PLANETAS[0]); c.essencia = 20; }));
        at(5, () -> clicarNpc("guilda_minerador"));
        at(30, () -> { int id = CLICAR.getAndSet(-1); var e = mc.level.getEntity(id); if (e != null) mc.gameMode.interact(mc.player, e, net.minecraft.world.InteractionHand.MAIN_HAND); });
        at(30, () -> { glog("tela minerador " + (mc.screen == null ? null : mc.screen.getClass().getSimpleName())); shot("g5_minerador"); });
        at(5, () -> com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.guilda.GuildaAcaoPacket(G_PLANETAS[0], "forjar")));
        at(10, () -> com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.guilda.GuildaAcaoPacket(G_PLANETAS[0], "forjar")));
        at(10, () -> com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.guilda.GuildaAcaoPacket(G_PLANETAS[0], "coletar")));
        at(10, () -> server(sp -> { var c = com.lanterna.guilda.Guildas.get(sp.server).conquistas.get(G_PLANETAS[0]);
            glog("depois de forjar: estoque=" + c.essencia + " aneis=" + count(sp, com.lanterna.guilda.GuildaItems.ANEL_RECRUTA.get()) + " essencia_inv=" + count(sp, com.lanterna.guilda.GuildaItems.ESSENCIA.get()));
            sp.getInventory().add(new net.minecraft.world.item.ItemStack(com.lanterna.guilda.GuildaItems.ANEL_RECRUTA.get(), 4)); }));
        at(5, () -> { mc.setScreen(null); clicarNpc("guilda_general"); });
        at(30, () -> { int id = CLICAR.getAndSet(-1); var e = mc.level.getEntity(id); if (e != null) mc.gameMode.interact(mc.player, e, net.minecraft.world.InteractionHand.MAIN_HAND); });
        at(10, () -> com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.guilda.GuildaAcaoPacket(G_PLANETAS[0], "treinar_defesa")));
        at(5, () -> com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.guilda.GuildaAcaoPacket(G_PLANETAS[0], "treinar_defesa")));
        at(5, () -> com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.guilda.GuildaAcaoPacket(G_PLANETAS[0], "treinar_defesa")));
        at(5, () -> com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.guilda.GuildaAcaoPacket(G_PLANETAS[0], "treinar_ataque")));
        at(5, () -> com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.guilda.GuildaAcaoPacket(G_PLANETAS[0], "treinar_ataque")));
        at(20, () -> { glog("tela general " + (mc.screen == null ? null : mc.screen.getClass().getSimpleName())); shot("g6_general"); });
        at(5, () -> server(sp -> { var c = com.lanterna.guilda.Guildas.get(sp.server).conquistas.get(G_PLANETAS[0]); glog("fila=" + c.fila.size()); c.proximaTropa = 0; }));
        at(60, () -> server(sp -> { var gs = com.lanterna.guilda.Guildas.get(sp.server); var c = gs.conquistas.get(G_PLANETAS[0]);
            glog("treinadas: defesa=" + c.defesa + " ataque=" + gs.guildas.get(c.guilda).ataque + " fila=" + c.fila.size()); }));
        at(5, () -> mc.setScreen(null));
        // ---- espaço: escudo, símbolo e HUD ----
        at(5, () -> server(sp -> {
            var b = com.lanterna.space.SpaceBodies.byId(G_PLANETAS[0]);
            var sl = sp.server.getLevel(com.lanterna.space.SpaceBodies.SPACE);
            net.minecraft.world.phys.Vec3 at = b.center().add(0, b.radius() * 0.3, b.radius() * 2.6);
            sp.teleportTo(sl, at.x, at.y, at.z, 180, 6);
        }));
        at(100, () -> server(sp -> { var b = com.lanterna.space.SpaceBodies.byId(G_PLANETAS[0]);
            var rel = b.center().subtract(sp.getEyePosition());
            sp.connection.teleport(sp.getX(), sp.getY(), sp.getZ(), (float) Math.toDegrees(Math.atan2(-rel.x, rel.z)), (float) -Math.toDegrees(Math.atan2(rel.y, Math.hypot(rel.x, rel.z)))); }));
        at(40, () -> shot("g7_espaco_seu"));
        // ---- guilda rival dona do planeta B ----
        at(5, () -> server(sp -> {
            var gs = com.lanterna.guilda.Guildas.get(sp.server);
            var b = com.lanterna.space.SpaceBodies.byId(G_PLANETAS[1]);
            var lvl = sp.server.getLevel(b.dimension());
            var rival = net.minecraftforge.common.util.FakePlayerFactory.get(lvl, new com.mojang.authlib.GameProfile(java.util.UUID.fromString("11111111-2222-3333-4444-555555555555"), "Lorde_Atrocitus"));
            var g = gs.criar(rival, "Lordes de Ysmault", "red");
            for (int i = 0; i < 256; i++) { int x = i % 16, y = i / 16; double d = Math.hypot(x - 7.5, y - 7.5); g.simbolo[i] = (byte) (d < 7 && d > 5 ? 5 : (Math.abs(x - 7.5) < 1.5 && y > 3 && y < 12) ? 4 : 0); }
            int x = b.procedural().regionX() + 40, z = b.procedural().regionZ() + 40;
            lvl.getChunk(x >> 4, z >> 4);
            int y = lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            lvl.setBlock(pos, com.lanterna.guilda.GuildaItems.BANDEIRA.get().defaultBlockState(), 3);
            var erro = com.lanterna.guilda.Conquistas.fincar(rival, lvl, pos);
            var c = gs.conquistas.get(G_PLANETAS[1]);
            glog("rival fincou erro=" + (erro == null ? null : erro.getString()) + " c=" + (c != null));
            c.defesa = 5;
            c.escudoAte = System.currentTimeMillis() + 3_600_000L;
            gs.guildas.get(gs.daJogador(sp.getUUID()).id).ataque = 4;
            gs.mudou();
            var bb = com.lanterna.space.SpaceBodies.byId(G_PLANETAS[1]);
            var sl = sp.server.getLevel(com.lanterna.space.SpaceBodies.SPACE);
            net.minecraft.world.phys.Vec3 at = bb.center().add(bb.radius() * 1.4, bb.radius() * 0.4, bb.radius() * 1.9);
            sp.teleportTo(sl, at.x, at.y, at.z, 0, 0);
        }));
        waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 2000);
        at(60, () -> server(sp -> { var b = com.lanterna.space.SpaceBodies.byId(G_PLANETAS[1]);
            var rel = b.center().subtract(sp.getEyePosition());
            sp.connection.teleport(sp.getX(), sp.getY(), sp.getZ(), (float) Math.toDegrees(Math.atan2(-rel.x, rel.z)), (float) -Math.toDegrees(Math.atan2(rel.y, Math.hypot(rel.x, rel.z)))); }));
        at(40, () -> shot("g8_espaco_rival_escudo"));
        at(5, () -> server(sp -> { var gs = com.lanterna.guilda.Guildas.get(sp.server); gs.conquistas.get(G_PLANETAS[1]).escudoAte = 0; gs.mudou(); }));
        at(30, () -> shot("g9_espaco_rival_vulneravel"));
        // ---- pousar no território rival: proteção e aviso de invasão ----
        at(5, () -> server(sp -> { var c = com.lanterna.guilda.Guildas.get(sp.server).conquistas.get(G_PLANETAS[1]);
            var lvl = sp.server.getLevel(c.mundo);
            sp.teleportTo(lvl, c.bandeira.getX() + 0.5, c.bandeira.getY(), c.bandeira.getZ() + 14.5, 180, 10); }));
        at(40, () -> server(sp -> { var c = com.lanterna.guilda.Guildas.get(sp.server).conquistas.get(G_PLANETAS[1]);
            BlockPos q = c.bandeira.offset(3, -1, 3);
            var antes = sp.serverLevel().getBlockState(q);
            sp.gameMode.destroyBlock(q);
            glog("quebrar bloco no territorio rival: protegido=" + (sp.serverLevel().getBlockState(q) == antes)); }));
        at(100, () -> shot("g10_aviso"));
        waitFor(() -> com.lanterna.guilda.Invasao.ativa(G_PLANETAS[1]) != null, 600);
        at(60, () -> { glog("invasao ativa=" + (com.lanterna.guilda.Invasao.ativa(G_PLANETAS[1]) != null)); shot("g11_invasao"); });
        at(5, () -> server(sp -> {
            var defs = sp.serverLevel().getEntitiesOfClass(com.lanterna.especies.LanternaEntity.class, sp.getBoundingBox().inflate(80),
                    e -> "defesa".equals(e.getPersistentData().getString(com.lanterna.guilda.Invasao.TAG_PAPEL)));
            var atk = sp.serverLevel().getEntitiesOfClass(com.lanterna.especies.LanternaEntity.class, sp.getBoundingBox().inflate(80),
                    e -> "ataque".equals(e.getPersistentData().getString(com.lanterna.guilda.Invasao.TAG_PAPEL)));
            glog("defensores=" + defs.size() + " cor=" + (defs.isEmpty() ? null : defs.get(0).tropa()) + " tropasAtaque=" + atk.size() + " cor=" + (atk.isEmpty() ? null : atk.get(0).tropa())
                    + " alvoDefensor=" + (defs.isEmpty() || defs.get(0).getTarget() == null ? null : defs.get(0).getTarget().getName().getString()));
            // a bandeira ainda não quebra
            var c = com.lanterna.guilda.Guildas.get(sp.server).conquistas.get(G_PLANETAS[1]);
            sp.gameMode.destroyBlock(c.bandeira);
            glog("bandeira antes da defesa cair: ainda la=" + sp.serverLevel().getBlockState(c.bandeira).is(com.lanterna.guilda.GuildaItems.BANDEIRA.get()));
        }));
        at(100, () -> shot("g12_batalha"));
        at(5, () -> server(sp -> { glog("jogador em " + sp.blockPosition().toShortString() + " " + sp.level().dimension().location() + " " + com.lanterna.guilda.Invasao.ativa(G_PLANETAS[1]).debug());
            for (var u : new java.util.ArrayList<>(com.lanterna.guilda.Invasao.ativa(G_PLANETAS[1]).defensoresIds())) {
            if (!(sp.serverLevel().getEntity(u) instanceof com.lanterna.especies.LanternaEntity e)) continue;
            e.invulnerableTime = 0; boolean ok = e.hurt(sp.damageSources().playerAttack(sp), 5000);
            glog("ferir defensor ok=" + ok + " vivo=" + e.isAlive() + " vida=" + e.getHealth() + " pos=" + e.blockPosition().toShortString()); } }));
        at(30, () -> server(sp -> { var inv = com.lanterna.guilda.Invasao.ativa(G_PLANETAS[1]); glog("inv defensores restantes=" + (inv == null ? -1 : inv.debug())); }));
        at(60, () -> server(sp -> { var c = com.lanterna.guilda.Guildas.get(sp.server).conquistas.get(G_PLANETAS[1]);
            glog("liberada=" + sp.serverLevel().getBlockState(c.bandeira).getValue(com.lanterna.guilda.BandeiraBlock.LIBERADA));
            sp.teleportTo(sp.serverLevel(), c.bandeira.getX() + 3.5, c.bandeira.getY(), c.bandeira.getZ() + 4.5, 145, 15); }));
        at(40, () -> shot("g13_bandeira_liberada"));
        at(5, () -> server(sp -> { var c = com.lanterna.guilda.Guildas.get(sp.server).conquistas.get(G_PLANETAS[1]);
            sp.gameMode.destroyBlock(c.bandeira);
            var gs = com.lanterna.guilda.Guildas.get(sp.server);
            glog("depois de derrubar: dono=" + gs.guildas.get(gs.conquistas.get(G_PLANETAS[1]).guilda).nome + " invasao=" + (com.lanterna.guilda.Invasao.ativa(G_PLANETAS[1]) != null)
                    + " ataqueDeVolta=" + gs.daJogador(sp.getUUID()).ataque); }));
        at(5, () -> server(sp -> { var c = com.lanterna.guilda.Guildas.get(sp.server).conquistas.get(G_PLANETAS[1]);
            sp.teleportTo(sp.serverLevel(), c.bandeira.getX() + 7.5, c.bandeira.getY() + 3, c.bandeira.getZ() + 9.5, 145, -8); }));
        at(80, () -> shot("g14_tomado"));
        // ---- defesa vence (invasores somem do território) ----
        at(5, () -> server(sp -> {
            var gs = com.lanterna.guilda.Guildas.get(sp.server);
            var c = gs.conquistas.get(G_PLANETAS[1]);
            c.defesa = 3;
            var rivalG = gs.porNome("Lordes de Ysmault");
            rivalG.ataque = 2;
            var rival = net.minecraftforge.common.util.FakePlayerFactory.get(sp.serverLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.fromString("11111111-2222-3333-4444-555555555555"), "Lorde_Atrocitus"));
            rival.moveTo(c.bandeira.getX() + 20, c.bandeira.getY(), c.bandeira.getZ());
            com.lanterna.guilda.Invasao.comecar(rival, c, rivalG);
            glog("contra-ataque comecou=" + (com.lanterna.guilda.Invasao.ativa(G_PLANETAS[1]) != null));
        }));
        at(60, () -> shot("g15_contra_ataque"));
        at(5, () -> server(sp -> { for (var e : sp.serverLevel().getEntitiesOfClass(com.lanterna.especies.LanternaEntity.class, sp.getBoundingBox().inflate(120),
                e -> "ataque".equals(e.getPersistentData().getString(com.lanterna.guilda.Invasao.TAG_PAPEL)))) e.hurt(sp.damageSources().playerAttack(sp), 5000); }));
        waitFor(() -> com.lanterna.guilda.Invasao.ativa(G_PLANETAS[1]) == null, 1200);
        at(20, () -> server(sp -> { var gs = com.lanterna.guilda.Guildas.get(sp.server); var c = gs.conquistas.get(G_PLANETAS[1]);
            glog("defesa venceu: dono=" + gs.guildas.get(c.guilda).nome + " defesa=" + c.defesa + " escudo_h=" + (c.escudoAte - System.currentTimeMillis()) / 3_600_000.0); }));
        at(20, () -> { for (String l : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", l); LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }
    private static int count(net.minecraft.server.level.ServerPlayer sp, net.minecraft.world.item.Item it) {
        int n = 0; for (var s : sp.getInventory().items) if (s.is(it)) n += s.getCount(); return n;
    }

    static {
        if (ENABLED && SCENE.equals("guilda")) sceneGuilda();
        else if (ENABLED && SCENE.equals("pvp")) scenePvp();
        else if (ENABLED && SCENE.equals("miraeth")) sceneMiraeth();
        else if (ENABLED && SCENE.equals("auditoria")) sceneAuditoria();
        else if (ENABLED && SCENE.equals("fotos15")) sceneFotos15();
        else if (ENABLED && SCENE.equals("barreira")) sceneBarreira();
        else if (ENABLED && SCENE.equals("trono")) sceneTrono();
        else if (ENABLED && SCENE.equals("cidade2")) sceneCidade2();
        else if (ENABLED && SCENE.equals("piloto")) scenePiloto();
        else if (ENABLED && (SCENE.equals("novidades") || SCENE.equals("auto"))) sceneNovidades();
        else if (ENABLED && SCENE.equals("cidades")) sceneCidades();
        else if (ENABLED && SCENE.equals("contrato")) sceneContrato();
        else if (ENABLED && SCENE.equals("voo")) sceneVoo();
        else if (ENABLED && SCENE.equals("blocos")) sceneBlocos();
        else if (ENABLED && SCENE.equals("quadro")) sceneQuadro();
        else if (ENABLED && SCENE.equals("pilotar")) scenePilotar();
        else if (ENABLED && SCENE.equals("encontro")) sceneEncontro();
        else if (ENABLED && SCENE.equals("navesfoto")) sceneNavesFoto();
        else if (ENABLED && SCENE.equals("naves")) sceneNaves();
        else if (ENABLED && SCENE.equals("morte")) sceneMorte();
        else if (ENABLED && SCENE.equals("caido")) sceneCaido();
        else if (ENABLED) {
            Minecraft mc = Minecraft.getInstance();
            at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
            at(60, () -> {
                mc.options.hideGui = false;
                mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
                cmd("gamemode survival"); cmd("time set 6000"); cmd("weather clear");
                cmd("effect give @s resistance 99999 4 true"); cmd("effect give @s saturation 99999 1 true");
                cmd("gamerule doMobSpawning false"); cmd("kill @e[type=!player]");
                cmd("lanterna resetar"); cmd("clear @s");
                cmd("execute in minecraft:overworld run tp @s 0.5 150 0.5"); cmd("effect give @s slow_falling 30 0 true");
                look(0, 0);
            });
            at(15, () -> { cmd("fill -8 149 -8 8 149 8 minecraft:smooth_stone"); cmd("fill -8 150 -8 8 160 8 minecraft:air"); cmd("tp @s 0.5 150 0.5"); });
            // ---- O anel verde escolhe: cerimônia e piloto automático até Oa ----
            at(20, () -> cmd("lanterna escolher lanterna:green"));
            at(60, () -> shot("g1_ceremony"));
            waitFor(() -> com.lanterna.client.flight.Autopilot.active(), 600);
            at(20, () -> { logState("after_choose"); shot("g2_autopilot_start"); });
            at(60, () -> shot("g3_autopilot_climb"));
            waitFor(() -> mc.player.level().dimension().location().getPath().equals("space"), 1200);
            at(30, () -> shot("g4_space_hud"));
            at(60, () -> shot("g5_space_hyper"));
            waitFor(() -> mc.player.level().dimension().location().getPath().equals("oa"), 2400);
            at(40, () -> shot("g6_landing_oa"));
            waitFor(() -> !com.lanterna.client.flight.Autopilot.active(), 1200);
            at(60, () -> { logState("arrived"); shot("g7_arrived"); });
            // Mundo de teste reaproveitado: reconstrói a cidadela (e os NPCs) para começar do zero
            at(5, () -> server(sp -> {
                sp.serverLevel().getEntitiesOfClass(com.lanterna.lore.entity.RaceEntity.class, sp.getBoundingBox().inflate(200)).forEach(e -> e.discard());
                sp.serverLevel().getEntitiesOfClass(com.lanterna.lore.entity.LoreCreatures.Dummy.class, sp.getBoundingBox().inflate(200)).forEach(e -> e.discard());
                com.lanterna.lore.world.PlanetSites.rebuild(sp.serverLevel(), com.lanterna.lore.PlanetLore.OA);
            }));
            at(60, () -> {});
            // ---- Cidadela de Oa ----
            at(10, () -> server(sp -> {
                int y = sp.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, -14);
                sp.teleportTo(0.5, y + 1, -16.5);
            }));
            at(30, () -> { look(0, -10); shot("c1_citadel"); });
            at(10, () -> { cmd("tp @s 0.5 ~ 2.5 180 0"); });
            at(30, () -> shot("c2_guardians"));
            // Falar com o Instrutor
            at(10, () -> server(sp -> {
                var npc = nearest(com.lanterna.lore.entity.RaceEntity.class, sp, 40, r -> r.role().equals("instrutor"));
                LanternaMod.LOGGER.info("AUTOTEST instrutor={}", npc);
                if (npc != null) { sp.teleportTo(npc.getX(), npc.getY(), npc.getZ() - 3); com.lanterna.lore.entity.NpcDialogs.open(sp, npc); }
            }));
            waitFor(() -> mc.screen != null, 200);
            at(20, () -> shot("d1_dialog_instrutor"));
            at(5, () -> { mc.setScreen(null); logState("talked"); });
            // ---- Construtos ----
            at(10, () -> { cmd("lanterna carga 1000"); com.lanterna.client.construct.ConstructWheelScreen.pinned = true; mc.setScreen(new com.lanterna.client.construct.ConstructWheelScreen()); });
            at(10, () -> shot("k1_wheel"));
            at(2, () -> { com.lanterna.client.construct.ConstructWheelScreen.pinned = false; mc.setScreen(null); });
            at(5, () -> server(sp -> {
                var d = nearest(com.lanterna.lore.entity.LoreCreatures.Dummy.class, sp, 60, x -> true);
                LanternaMod.LOGGER.info("AUTOTEST dummy={}", d);
                if (d != null) { sp.teleportTo(d.getX() - 8, d.getY(), d.getZ()); sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, d.position().add(0, 1.2, 0)); }
            }));
            at(10, () -> construct("parede", true));
            at(15, () -> { camera(CameraType.THIRD_PERSON_BACK); shot("k2_wall"); });
            at(5, () -> { cmd("fill ~-6 ~-1 ~-6 ~6 ~6 ~6 air replace lanterna:construct_block"); camera(CameraType.FIRST_PERSON); });
            at(5, () -> server(sp -> {
                var d = nearest(com.lanterna.lore.entity.LoreCreatures.Dummy.class, sp, 60, x -> true);
                if (d != null) sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, d.position().add(0, 1.2, 0));
            }));
            at(5, () -> construct("laser", true));
            at(20, () -> { camera(CameraType.THIRD_PERSON_BACK); shot("k3_laser"); });
            at(60, () -> construct("laser", false));
            at(10, () -> { camera(CameraType.FIRST_PERSON); construct("punho", true); });
            at(4, () -> shot("k4_fist"));
            at(30, () -> server(sp -> Affinity5(sp)));
            at(10, () -> construct("martelo", true));
            at(6, () -> { camera(CameraType.THIRD_PERSON_BACK); shot("k5_hammer_a"); });
            at(6, () -> shot("k6_hammer_b"));
            at(20, () -> construct("escudo", true));
            at(10, () -> shot("k7_shield"));
            at(10, () -> construct("lamina", true));
            at(10, () -> shot("k8_blades"));
            at(40, () -> { construct("rajada", true); });
            at(3, () -> shot("k9_bolts"));
            at(20, () -> construct("mao", true));
            at(6, () -> shot("k10_hand"));
            at(30, () -> { look(mc.player.getYRot(), 20); construct("ponte", true); });
            at(10, () -> shot("k11_bridge"));
            at(10, () -> { camera(CameraType.FIRST_PERSON); logState("constructs"); shot("k12_hud"); });
            at(20, () -> { mc.setScreen(new com.lanterna.client.mission.JournalScreen()); });
            at(10, () -> shot("j1_journal"));
            at(2, () -> mc.setScreen(null));
            // Guardiões
            at(5, () -> server(sp -> {
                var npc = nearest(com.lanterna.lore.entity.RaceEntity.class, sp, 60, r -> r.role().equals("guardiao"));
                if (npc != null) { sp.teleportTo(npc.getX(), npc.getY() - 0.5, npc.getZ() - 4); com.lanterna.lore.entity.NpcDialogs.open(sp, npc); }
            }));
            waitFor(() -> mc.screen != null, 200);
            at(20, () -> shot("d2_dialog_guardiao"));
            at(5, () -> { mc.setScreen(null); logState("guardians"); });
            // ---- Arena de Oa: Altar do Desafio e o Vigia Partido ----
            at(10, () -> server(sp -> {
                sp.teleportTo(120.5, sp.getY() + 40, -14.5);
            }));
            at(60, () -> server(sp -> {
                int y = sp.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 120, -14);
                sp.teleportTo(120.5, y, -16.5);
                sp.getInventory().add(new net.minecraft.world.item.ItemStack(com.lanterna.lore.LoreItems.JADE_ESTELAR.get(), 4));
                for (var p : net.minecraft.core.BlockPos.betweenClosed(100, y - 6, -20, 140, y + 6, 20)) {
                    if (sp.level().getBlockState(p).is(com.lanterna.lore.LoreBlocks.ALTAR_DESAFIO.get())) {
                        LanternaMod.LOGGER.info("AUTOTEST altar at {}", p);
                        com.lanterna.lore.entity.boss.BossAltar.use(sp, p.immutable());
                        break;
                    }
                }
            }));
            at(40, () -> { look(0, -15); shot("x1_vigia"); });
            at(40, () -> shot("x2_vigia_fight"));
            // ---- Todos os bosses e raças lado a lado (planeta plano de teste) ----
            at(10, () -> cmd("execute in minecraft:overworld run tp @s 0.5 150 -20"));
            at(20, () -> cmd("kill @e[type=!player]"));
            at(20, () -> {
                cmd("fill -40 149 -30 40 149 40 minecraft:smooth_stone"); cmd("fill -40 150 -30 40 175 40 minecraft:air");
                String[] bosses = {"vorgath", "mammok", "thessara", "vigia_partido", "morrow", "penitente", "nyrielle", "devorador_de_ecos", "nulo"};
                for (int i = 0; i < bosses.length; i++) cmd("summon lanterna:" + bosses[i] + " " + (-32 + i * 8) + " 150 20 {NoAI:1b,Rotation:[180f,0f]}");
                String[] races = {"brakkari", "ghallos", "skrell_vath", "veltari", "aelinos", "ves_kai", "zaelith", "silente", "coletor", "guardiao", "eco_cinzento", "broto", "alvo_treino"};
                for (int i = 0; i < races.length; i++) cmd("summon lanterna:" + races[i] + " " + (-18 + i * 3) + " 150 0 {NoAI:1b,Rotation:[180f,0f],Role:\"x\"}");
            });
            at(40, () -> { cmd("tp @s 0.5 152 -14 0 5"); });
            at(20, () -> shot("e1_races"));
            at(5, () -> cmd("tp @s -16 154 -8 0 5"));
            at(20, () -> shot("e2_races_left"));
            at(5, () -> cmd("tp @s 14 154 -8 0 5"));
            at(20, () -> shot("e3_races_right"));
            at(5, () -> cmd("tp @s -20 160 -6 0 12"));
            at(20, () -> shot("e4_bosses_left"));
            at(5, () -> cmd("tp @s 12 160 -6 0 12"));
            at(20, () -> shot("e5_bosses_right"));
            at(60, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
        }
    }

    private static void Affinity5(net.minecraft.server.level.ServerPlayer sp) {
        com.lanterna.construct.Affinity.add(sp, 30000);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        // Mundo superplano de teste: aceita o aviso de "configurações experimentais" sozinho
        if (mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen confirm) {
            for (var child : confirm.children()) {
                if (child instanceof net.minecraft.client.gui.components.Button button
                        && button.getMessage().getString().equals("Proceed")) {
                    button.onPress();
                    return;
                }
            }
        }
        if (mc.player == null || mc.level == null) return;
        tick++;
        updateSideCam();
        if (waitCond != null) {
            if (waitCond.getAsBoolean() || tick > waitLimit) {
                if (tick > waitLimit) LanternaMod.LOGGER.warn("AUTOTEST wait timeout at step {}", stepIndex);
                waitCond = null;
            } else return;
        }
        if (PENDING.get() > 0) return;
        if (stepIndex >= STEPS.size()) return;
        if (waitUntil == 0) waitUntil = tick + STEPS.get(stepIndex).delay();
        if (tick >= waitUntil) {
            try {
                STEPS.get(stepIndex).action().run();
            } catch (Exception e) {
                LanternaMod.LOGGER.error("AUTOTEST step {} failed", stepIndex, e);
            }
            stepIndex++;
            waitUntil = 0;
        }
    }

    private DevAutoTest() {}
}
