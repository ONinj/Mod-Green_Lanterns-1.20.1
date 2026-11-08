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

    /** 0.18: minerador nível 4, planetas de Tropa NPC + inimizade, corrupção da Morte, rota da linha principal, supremos. */
    private static final String[] V18_PLANETA = new String[1];
    private static void sceneV18() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd("gamemode survival"); cmd("effect give @s resistance 99999 4 true"); cmd("difficulty normal");
            cmd("time set 6000"); cmd("lanterna resetar"); cmd("clear @s"); cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5"); });
        at(40, () -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna escolher lanterna:green")));
        waitFor(() -> com.lanterna.client.ClientRingData.hasRing, 400);
        for (int k = 0; k < 6; k++) at(20, () -> { com.lanterna.client.flight.Autopilot.clear(); cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5"); });
        at(20, () -> server(sp -> {
            com.lanterna.capability.ModCapabilities.get(sp).ifPresent(r -> { if (r.flightEnabled()) com.lanterna.power.RingPowers.toggleFlight(sp); });
            var gs = com.lanterna.guilda.Guildas.get(sp.server);
            for (var g : new java.util.ArrayList<>(gs.guildas.values())) { for (var c : gs.planetasDe(g.id)) com.lanterna.guilda.Conquistas.largar(sp.server, c); gs.guildas.remove(g.id); }
            gs.liberados.clear(); gs.conquistas.clear(); gs.mudou();
            var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null);
            for (String id : new java.util.ArrayList<>(d.active().keySet())) d.abandon(id);
            d.setMorte(0); d.tropasHostis().clear();
            // ---- dormente nunca dá Preto ----
            boolean temPreto = java.util.Arrays.asList(com.lanterna.item.DormantRingItem.FALLEN).contains(com.lanterna.corps.BuiltinCorps.BLACK);
            glog("anel dormente pode dar Preto? " + temPreto);
            // ---- rota da linha principal ----
            try {
                var rn = com.lanterna.mission.MissionManager.class.getDeclaredMethod("routeNext", net.minecraft.server.level.ServerPlayer.class, com.lanterna.mission.LoreData.class, com.lanterna.mission.Mission.class);
                rn.setAccessible(true);
                StringBuilder a = new StringBuilder("rota odym (outro):"), b = new StringBuilder(" rota oa (propria):");
                String id = "main_odym_1";
                for (int i = 0; i < 8 && id != null && id.startsWith("main_odym"); i++) { a.append(' ').append(id); id = (String) rn.invoke(null, sp, d, com.lanterna.mission.Missions.get(id)); }
                a.append(" -> ").append(id);
                id = "main_oa_1";
                for (int i = 0; i < 8 && id != null && id.startsWith("main_oa"); i++) { b.append(' ').append(id); id = (String) rn.invoke(null, sp, d, com.lanterna.mission.Missions.get(id)); }
                b.append(" -> ").append(id);
                glog(a.toString() + b);
                var m7 = com.lanterna.mission.Missions.get("main_zamaron_7");
                glog("provacao zamaron: " + m7.objectives().get(0).count() + " | forja zamaron nivel " + com.lanterna.tropa.Nivel.nivelParaForjar(com.lanterna.lore.PlanetLore.ZAMARON)
                        + " | genese " + com.lanterna.tropa.Nivel.nivelParaForjar(com.lanterna.lore.PlanetLore.GENESE) + " | rastro odym: " + com.lanterna.mission.Missions.get("main_odym_6").objectives());
                glog("proximo planeta nivel 1 (verde): " + com.lanterna.mission.MissionManager.nextPlanetLine(d, com.lanterna.corps.BuiltinCorps.GREEN.id()));
            } catch (Exception e) { glog("rota ERRO " + e); }
            // ---- supremos ----
            glog("vida antes (verde) " + sp.getMaxHealth() + " armadura " + sp.getArmorValue());
        }));
        at(5, () -> server(sp -> { var velho = com.lanterna.power.EquipmentHelper.take(sp, com.lanterna.capability.RingData.RING_SLOT);
            sp.getPersistentData().put("v18_anel", velho.save(new net.minecraft.nbt.CompoundTag()));
            com.lanterna.power.EquipmentHelper.equip(sp, com.lanterna.capability.RingData.RING_SLOT,
                com.lanterna.item.PowerRingItem.create(com.lanterna.corps.BuiltinCorps.BLACK.id(), com.lanterna.corps.BuiltinCorps.BLACK.color(), -1)); }));
        at(60, () -> server(sp -> { var r = com.lanterna.capability.ModCapabilities.get(sp).orElse(null);
            glog("anel agora " + r.corps() + " vida " + sp.getMaxHealth() + " armadura " + sp.getArmorValue() + " dano x" + com.lanterna.combate.Supremos.dano(r.corps(), sp)
                    + " voo x" + com.lanterna.combate.Supremos.voo(r.corps())); }));
        at(5, () -> server(sp -> { com.lanterna.power.EquipmentHelper.take(sp, com.lanterna.capability.RingData.RING_SLOT);
            com.lanterna.power.EquipmentHelper.equip(sp, com.lanterna.capability.RingData.RING_SLOT, net.minecraft.world.item.ItemStack.of(sp.getPersistentData().getCompound("v18_anel"))); }));
        at(60, () -> server(sp -> glog("voltou verde: vida " + sp.getMaxHealth() + " armadura " + sp.getArmorValue())));
        // ---- corrupção da Morte ----
        at(5, () -> server(sp -> {
            com.lanterna.LanternaConfig.MORTE_CHANCE.set(1.0);
            com.lanterna.tropa.Nivel.addXp(sp, com.lanterna.tropa.Nivel.total(20), null);
            glog("forja de Ryut antes: pode=" + com.lanterna.morte.Corrupcao.podeForjar(sp));
        }));
        at(5, () -> cmd("effect clear @s"));
        at(10, () -> server(sp -> sp.hurt(sp.damageSources().fellOutOfWorld(), 10000)));
        at(40, () -> { mc.player.respawn(); mc.setScreen(null); });
        at(60, () -> server(sp -> { var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null);
            com.lanterna.LanternaConfig.MORTE_CHANCE.set(0.004);
            glog("depois de morrer: morte=" + d.morte() + " ativas=" + d.active().keySet() + " colheAlmas=" + com.lanterna.morte.Corrupcao.colheAlmas(sp));
            for (int i = 0; i < 6; i++) { var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(sp.serverLevel());
            z.moveTo(sp.getX() + 2, sp.getY(), sp.getZ() + i * 0.3, 0, 0); sp.serverLevel().addFreshEntity(z);
            z.hurt(sp.damageSources().playerAttack(sp), 1000); }
        }));
        at(20, () -> server(sp -> { var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null);
            var almas = sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, sp.getBoundingBox().inflate(8), e -> e.getItem().is(com.lanterna.lore.LoreItems.ALMA.get()));
            glog("almas no chao=" + almas.stream().mapToInt(e -> e.getItem().getCount()).sum() + " de 6 zumbis; progresso morte_2? " + d.isActive("morte_2") + " morte_1 prog=" + java.util.Arrays.toString(d.active().get("morte_1"))); }));
        at(20, () -> shot("v1_corrompido"));
        // ---- guilda + minerador nível 4 ----
        at(10, () -> { cmd("effect give @s resistance 99999 4 true"); cmd("guilda criar Zezinho do Grau"); });
        at(20, () -> mc.setScreen(null));
        at(5, () -> server(sp -> {
            var gs = com.lanterna.guilda.Guildas.get(sp.server);
            int n = 0;
            String livre = null, tropa = null;
            for (int r = 2; r < 12 && (livre == null || tropa == null); r++) for (int sx = -r; sx <= r; sx++) for (int sz : new int[]{-r, r}) {
                var b = com.lanterna.space.ProceduralPlanets.inSector(sx, sz);
                if (b == null || sp.server.getLevel(b.dimension()) == null) continue;
                var dono = com.lanterna.guilda.TropasNpc.donoDe(b);
                if (dono == null && livre == null) livre = b.id();
                if (dono != null && dono != com.lanterna.corps.BuiltinCorps.GREEN && tropa == null) tropa = b.id();
            }
            G_PLANETAS[0] = livre; V18_PLANETA[0] = tropa;
            glog("planeta livre " + livre + " planeta de tropa " + tropa + " (" + com.lanterna.guilda.TropasNpc.donoDe(com.lanterna.space.SpaceBodies.byId(tropa)) + ")");
            var b = com.lanterna.space.SpaceBodies.byId(livre);
            var lvl = sp.server.getLevel(b.dimension());
            int x = b.procedural().regionX() + 40, z = b.procedural().regionZ() + 40;
            lvl.getChunk(x >> 4, z >> 4);
            int y = lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            sp.teleportTo(lvl, x + 0.5, y + 1, z + 4.5, 180, 20);
            BlockPos pos = new BlockPos(x, y, z);
            var st = com.lanterna.guilda.GuildaItems.BANDEIRA.get().defaultBlockState();
            lvl.setBlock(pos, st, 3);
            st.getBlock().setPlacedBy(lvl, pos, st, sp, new net.minecraft.world.item.ItemStack(com.lanterna.guilda.GuildaItems.BANDEIRA_ITEM.get()));
            var c = gs.conquistas.get(livre);
            c.essencia = 256;
            sp.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 64));
            sp.getInventory().add(new net.minecraft.world.item.ItemStack(com.lanterna.lore.LoreItems.MERITO.get(), 64));
            sp.getInventory().add(new net.minecraft.world.item.ItemStack(com.lanterna.lore.LoreItems.MERITO.get(), 64));
            sp.getInventory().add(new net.minecraft.world.item.ItemStack(com.lanterna.guilda.GuildaItems.ESSENCIA.get(), 64));
            sp.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT, 5));
        }));
        waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 2000);
        at(20, () -> clicarNpc("guilda_minerador"));
        at(30, () -> { int id = CLICAR.getAndSet(-1); var e = mc.level.getEntity(id); if (e != null) mc.gameMode.interact(mc.player, e, net.minecraft.world.InteractionHand.MAIN_HAND); });
        at(20, () -> shot("v2_minerador_1"));
        for (int k = 0; k < 3; k++) at(15, () -> com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.guilda.GuildaAcaoPacket(G_PLANETAS[0], "melhorar")));
        at(20, () -> shot("v3_minerador_4"));
        at(5, () -> server(sp -> { var gs = com.lanterna.guilda.Guildas.get(sp.server); var c = gs.conquistas.get(G_PLANETAS[0]);
            c.essencia = 0; long agora = System.currentTimeMillis(); c.ultimaEssencia = agora - 10 * 60_000L;
            try { var m = com.lanterna.guilda.Guildas.class.getDeclaredMethod("produzir", long.class); m.setAccessible(true); m.invoke(gs, agora); } catch (Exception e) { glog("ERRO " + e); }
            glog("minerador nivel=" + c.minerador + " em 10 min juntou " + c.essencia + " (nivel 1 juntaria 3) deposito=" + c.depositoMinerador()
                    + " diamantes restantes=" + count(sp, net.minecraft.world.item.Items.DIAMOND) + " netherita=" + count(sp, net.minecraft.world.item.Items.NETHERITE_INGOT)); }));
        at(5, () -> mc.setScreen(null));
        // ---- planeta de Tropa NPC ----
        at(5, () -> server(sp -> {
            var b = com.lanterna.space.SpaceBodies.byId(V18_PLANETA[0]);
            var sl = sp.server.getLevel(com.lanterna.space.SpaceBodies.SPACE);
            net.minecraft.world.phys.Vec3 at = b.center().add(b.radius() * 1.3, b.radius() * 0.5, b.radius() * 2.0);
            sp.teleportTo(sl, at.x, at.y, at.z, 0, 0);
        }));
        at(80, () -> server(sp -> { var b = com.lanterna.space.SpaceBodies.byId(V18_PLANETA[0]);
            var c = com.lanterna.guilda.Guildas.get(sp.server).conquistas.get(V18_PLANETA[0]);
            glog("registrado no espaco: " + (c != null) + (c == null ? "" : " daTropa=" + c.daTropa + " defesa=" + c.defesa + " construida=" + c.construida));
            var rel = b.center().subtract(sp.getEyePosition());
            sp.connection.teleport(sp.getX(), sp.getY(), sp.getZ(), (float) Math.toDegrees(Math.atan2(-rel.x, rel.z)), (float) -Math.toDegrees(Math.atan2(rel.y, Math.hypot(rel.x, rel.z)))); }));
        at(40, () -> shot("v4_planeta_tropa_espaco"));
        at(5, () -> server(sp -> { var c = com.lanterna.guilda.Guildas.get(sp.server).conquistas.get(V18_PLANETA[0]);
            var lvl = sp.server.getLevel(c.mundo);
            lvl.getChunk(c.bandeira.getX() >> 4, c.bandeira.getZ() >> 4);
            int y = lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.bandeira.getX() + 100, c.bandeira.getZ());
            sp.teleportTo(lvl, c.bandeira.getX() + 100.5, y + 2, c.bandeira.getZ() + 0.5, 90, 10); }));
        at(100, () -> {});
        waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 2000);
        at(60, () -> server(sp -> { var c = com.lanterna.guilda.Guildas.get(sp.server).conquistas.get(V18_PLANETA[0]);
            glog("base da tropa construida=" + c.construida + " bandeira=" + c.bandeira.toShortString() + " bloco=" + sp.serverLevel().getBlockState(c.bandeira).getBlock());
            cmd("gamemode spectator");
            sp.teleportTo(sp.serverLevel(), c.bandeira.getX() + 0.5, c.bandeira.getY() + 30, c.bandeira.getZ() + 36.5, 180, 38); }));
        at(80, () -> shot("v5_base_tropa"));
        at(5, () -> { cmd("gamemode survival"); server(sp -> { var c = com.lanterna.guilda.Guildas.get(sp.server).conquistas.get(V18_PLANETA[0]);
            sp.teleportTo(sp.serverLevel(), c.bandeira.getX() + 0.5, c.bandeira.getY() + 1, c.bandeira.getZ() + 16.5, 180, 10); }); });
        waitFor(() -> com.lanterna.guilda.Invasao.ativa(V18_PLANETA[0]) != null, 800);
        at(60, () -> server(sp -> { var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null);
            var c = com.lanterna.guilda.Guildas.get(sp.server).conquistas.get(V18_PLANETA[0]);
            var dono = com.lanterna.guilda.TropasNpc.donoDe(com.lanterna.space.SpaceBodies.byId(V18_PLANETA[0]));
            var e = com.lanterna.especies.LanternaEntity.lanterna(sp.serverLevel(), dono, false);
            e.moveTo(sp.getX() + 600, 200, sp.getZ());
            glog("invasao de planeta de Tropa ativa=" + (com.lanterna.guilda.Invasao.ativa(V18_PLANETA[0]) != null) + " tropasHostis=" + d.tropasHostis()
                    + " lanterna " + dono + " qualquer hostil a mim=" + e.hostilA(sp)); }));
        at(60, () -> shot("v6_invasao_tropa"));
        at(20, () -> { for (String l : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", l); LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    /** Construtos: os 9 das Tropas, o laser novo, polimento dos antigos e o Pavor travando construtos. */
    private static void anelDe(net.minecraft.server.level.ServerPlayer sp, com.lanterna.corps.BuiltinCorps c) {
        com.lanterna.power.EquipmentHelper.take(sp, com.lanterna.capability.RingData.RING_SLOT);
        com.lanterna.power.EquipmentHelper.equip(sp, com.lanterna.capability.RingData.RING_SLOT, com.lanterna.item.PowerRingItem.create(c.id(), c.color(), -1));
    }
    private static void alvos(net.minecraft.server.level.ServerPlayer sp, int n, double dist) {
        for (var e : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class, sp.getBoundingBox().inflate(40))) e.discard();
        for (int i = 0; i < n; i++) {
            var z = net.minecraft.world.entity.EntityType.HUSK.create(sp.serverLevel());
            z.moveTo(sp.getX() + (i - (n - 1) / 2.0) * 1.6, sp.getY(), sp.getZ() + dist + (i % 2) * 1.2, 180, 0);
            z.setNoAi(true); z.setPersistenceRequired(); z.setSilent(true);
            z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(400); z.setHealth(400);
            sp.serverLevel().addFreshEntity(z);
        }
    }
    private static final java.util.concurrent.atomic.AtomicInteger CAM = new java.util.concurrent.atomic.AtomicInteger(-1);
    private static double CAM_DX = 9, CAM_DZ = 3;
    /** Câmera parada de lado (um suporte de armadura invisível) para ver o construto inteiro. */
    private static void camLateral(boolean ligar) {
        Minecraft mc = Minecraft.getInstance();
        if (!ligar) { mc.setCameraEntity(mc.player); return; }
        server(sp -> {
            var lvl = sp.serverLevel();
            var st = lvl.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, sp.getBoundingBox().inflate(30), e -> e.getTags().contains("cam")).stream().findFirst().orElse(null);
            if (st == null) {
                st = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(lvl);
                st.addTag("cam"); st.setInvisible(true); st.setNoGravity(true); st.setInvulnerable(true);
                lvl.addFreshEntity(st);
            }
            double cx = sp.getX() + CAM_DX, cy = sp.getY() + 3.2, cz = sp.getZ() + CAM_DZ;
            double tx = sp.getX(), ty = sp.getY() + 1, tz = sp.getZ() + 4;
            float yaw = (float) Math.toDegrees(Math.atan2(-(tx - cx), tz - cz));
            float pitch = (float) -Math.toDegrees(Math.atan2(ty - (cy + 1.7), Math.hypot(tx - cx, tz - cz)));
            st.moveTo(cx, cy, cz, yaw, pitch);
            st.setYHeadRot(yaw);
            CAM.set(st.getId());
        });
    }
    private static void sceneConstrutos() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd("gamemode survival"); cmd("effect clear @s"); cmd("effect give @s resistance 99999 4 true"); cmd("difficulty normal"); cmd("gamerule doDaylightCycle false");
            cmd("time set 6000"); cmd("weather clear"); cmd("lanterna resetar"); cmd("clear @s"); cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5 0 5"); });
        at(40, () -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna escolher lanterna:green")));
        waitFor(() -> com.lanterna.client.ClientRingData.hasRing, 400);
        for (int k = 0; k < 6; k++) at(20, () -> { com.lanterna.client.flight.Autopilot.clear(); cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5 0 5"); });
        at(20, () -> server(sp -> {
            com.lanterna.capability.ModCapabilities.get(sp).ifPresent(r -> { if (r.flightEnabled()) com.lanterna.power.RingPowers.toggleFlight(sp); });
            sp.getPersistentData().putBoolean("lanterna_devtest", true);
        }));
        String[][] lista = {{"green", "ariete", "3"}, {"yellow", "pavor", "7"}, {"red", "napalm", "14"}, {"blue", "esperanca", "8"},
                {"violet", "cristal", "16"}, {"indigo", "teleporte", "3"}, {"orange", "avareza", "12"}, {"black", "ceifa", "26"}, {"white", "vida", "8"}};
        for (String[] l : lista) {
            at(5, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                server(sp -> { sp.teleportTo(sp.serverLevel(), 900.5, -60, 900.5, 0, 12);
                    anelDe(sp, java.util.Arrays.stream(com.lanterna.corps.BuiltinCorps.values()).filter(c -> c.id().getPath().equals(l[0])).findFirst().get());
                    alvos(sp, 5, 7); }); });
            at(2, () -> camLateral(true));
            at(30, () -> server(sp -> { com.lanterna.construct.Affinity.add(sp, 30000);
                var r = com.lanterna.capability.ModCapabilities.get(sp).orElse(null);
                if (!r.powerActive()) com.lanterna.power.RingPowers.togglePower(sp);
                com.lanterna.power.RingPowers.setCharge(sp, r, r.maxCharge()); }));
            at(10, () -> server(sp -> com.lanterna.construct.Constructs.cast(sp, l[1])));
            at(Integer.parseInt(l[2]), () -> shot("c_" + l[1]));
            at(1, () -> { var e = mc.level.getEntity(CAM.get()); if (e != null) mc.setCameraEntity(e); });
            at(3, () -> shot("c_" + l[1] + "_lado"));
            at(1, () -> camLateral(false));
            at(20, () -> server(sp -> {
                StringBuilder b = new StringBuilder();
                for (var z : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class, sp.getBoundingBox().inflate(40)))
                    b.append(String.format("%.0f", 400 - z.getHealth())).append(z.hasEffect(com.lanterna.construct.ModEfeitos.PAVOR.get()) ? "P" : "")
                            .append(z.hasEffect(com.lanterna.construct.ModEfeitos.ENCANTO.get()) ? "E" : "").append(z.isOnFire() ? "F" : "")
                            .append(z.hasEffect(net.minecraft.world.effect.MobEffects.WITHER) ? "W" : "").append(' ');
                glog(l[1] + " (" + l[0] + "): dano nos alvos [" + b.toString().trim() + "] jogador em " + sp.blockPosition().toShortString() + " vida " + sp.getHealth());
                com.lanterna.construct.Constructs.release(sp, null);
            }));
            at(60, () -> {});
        }
        // ---- laser novo ----
        at(5, () -> server(sp -> { sp.teleportTo(sp.serverLevel(), 900.5, -60, 900.5, 0, 6); anelDe(sp, com.lanterna.corps.BuiltinCorps.GREEN); alvos(sp, 1, 9); }));
        at(20, () -> server(sp -> { var r = com.lanterna.capability.ModCapabilities.get(sp).orElse(null); if (!r.powerActive()) com.lanterna.power.RingPowers.togglePower(sp);
            com.lanterna.power.RingPowers.setCharge(sp, r, r.maxCharge()); com.lanterna.construct.Constructs.cast(sp, "laser"); }));
        at(2, () -> camLateral(true));
        at(14, () -> shot("c_laser_3a"));
        at(1, () -> { var e = mc.level.getEntity(CAM.get()); if (e != null) mc.setCameraEntity(e); });
        at(3, () -> shot("c_laser_lado"));
        at(1, () -> camLateral(false));
        at(5, () -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        at(10, () -> shot("c_laser_1a"));
        at(5, () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        at(10, () -> shot("c_laser_frente"));
        at(5, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); server(sp -> com.lanterna.construct.Constructs.release(sp, null)); });
        // ---- polimento: punho, martelo, escudo, lâminas, gancho ----
        at(5, () -> server(sp -> com.lanterna.construct.Affinity.add(sp, 30000)));
        String[][] velhos = {{"punho", "2"}, {"martelo", "9"}, {"martelo", "13"}, {"escudo", "10"}, {"lamina", "10"}, {"rajada", "1"}, {"gancho", "1"}, {"mao", "6"}};
        for (String[] v : velhos) {
            at(2, () -> camLateral(true));
            at(20, () -> server(sp -> { sp.teleportTo(sp.serverLevel(), 900.5, -60, 900.5, 0, 12); alvos(sp, 3, 8);
                var r = com.lanterna.capability.ModCapabilities.get(sp).orElse(null); com.lanterna.power.RingPowers.setCharge(sp, r, r.maxCharge());
                com.lanterna.construct.Constructs.cast(sp, v[0]); }));
            at(Integer.parseInt(v[1]), () -> { var e = mc.level.getEntity(CAM.get()); if (e != null) mc.setCameraEntity(e); });
            at(1, () -> shot("c_" + v[0] + "_" + v[1] + "_lado"));
            at(1, () -> camLateral(false));
            at(60, () -> {});
        }
        // ---- Pavor trava construtos ----
        at(5, () -> server(sp -> { sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.lanterna.construct.ModEfeitos.PAVOR.get(), 200, 0));
            for (var z : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class, sp.getBoundingBox().inflate(40))) z.discard();
            var r = com.lanterna.capability.ModCapabilities.get(sp).orElse(null);
            int antes = r.charge();
            com.lanterna.construct.Constructs.cast(sp, "punho");
            glog("com Pavor: carga antes=" + antes + " depois=" + r.charge() + " (igual = travou)"); }));
        at(30, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); });
        at(20, () -> shot("c_pavor_tela"));
        at(20, () -> { for (String l : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", l); LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    private static void sceneFist() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { cmd("gamemode survival"); cmd("time set 6000"); cmd("lanterna resetar"); cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5 0 5"); });
        at(40, () -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna escolher lanterna:green")));
        waitFor(() -> com.lanterna.client.ClientRingData.hasRing, 400);
        for (int k = 0; k < 6; k++) at(20, () -> { com.lanterna.client.flight.Autopilot.clear(); cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5 0 5"); });
        at(10, () -> server(sp -> { com.lanterna.capability.ModCapabilities.get(sp).ifPresent(r -> { if (r.flightEnabled()) com.lanterna.power.RingPowers.toggleFlight(sp); }); }));
        at(10, () -> camLateral(true));
        at(5, () -> { var e = mc.level.getEntity(CAM.get()); if (e != null) mc.setCameraEntity(e); });
        at(5, () -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
        String[][] q = {{"punho", "2"}, {"rajada", "1"}, {"martelo", "8"}, {"martelo", "13"}, {"lamina", "8"}, {"escudo", "8"}};
        at(5, () -> server(sp -> { com.lanterna.construct.Affinity.add(sp, 30000); alvos(sp, 3, 10); }));
        for (String[] x : q) {
            at(30, () -> server(sp -> { var r = com.lanterna.capability.ModCapabilities.get(sp).orElse(null); com.lanterna.power.RingPowers.setCharge(sp, r, r.maxCharge());
                com.lanterna.construct.Constructs.cast(sp, x[0]); }));
            at(Integer.parseInt(x[1]), () -> shot("f_" + x[0] + x[1]));
        }
        at(20, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    /** Porta Estelar: linha dos Guardiões, tela de destinos e a viagem animada até Oa. */
    private static boolean hurtHook;
    private static void sceneReceber() {
        Minecraft mc = Minecraft.getInstance();
        if (!hurtHook) { hurtHook = true; net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.LivingHurtEvent e) -> {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) glog("DANO " + e.getSource().getMsgId() + " " + e.getAmount() + " dim=" + sp.level().dimension().location()
                    + " y=" + (int) sp.getY() + " auto=" + com.lanterna.client.flight.Autopilot.phase() + " hp=" + sp.getHealth()); }); }
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { cmd("gamemode survival"); cmd("difficulty normal"); cmd("lanterna resetar"); cmd("clear @s"); cmd("time set 6000"); cmd("weather clear");
            cmd("execute in minecraft:overworld run tp @s 300.5 120 300.5 0 0"); });
        at(60, () -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna escolher lanterna:green")));
        for (int i = 0; i < 90; i++) {
            final int k = i;
            at(40, () -> {
                var p = mc.player; if (p == null) return;
                glog("t=" + k * 2 + "s dim=" + p.level().dimension().location().getPath() + " y=" + (int) p.getY() + " hp=" + p.getHealth()
                    + " auto=" + com.lanterna.client.flight.Autopilot.phase() + " fly=" + com.lanterna.client.ClientRingData.flight + " on=" + com.lanterna.client.ClientRingData.active
                    + " carga=" + com.lanterna.client.ClientRingData.charge + " scr=" + (mc.screen == null ? "-" : mc.screen.getClass().getSimpleName()) + " cut=" + com.lanterna.client.ClientCutscene.active());
                if (k == 12 || k == 30) shot("receber_" + k);
            });
        }
        at(20, () -> shot("receber_fim"));
        at(10, () -> com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.network.OpenLanternMenuPacket()));
        at(30, () -> shot("tela_k"));
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }

    private static void sceneV22() {
        Minecraft mc = Minecraft.getInstance();
        if (!hurtHook) { hurtHook = true; net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.LivingHurtEvent e) -> {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) glog("DANO " + e.getSource().getMsgId() + " " + e.getAmount() + " dim=" + sp.level().dimension().location()
                    + " y=" + (int) sp.getY() + " auto=" + com.lanterna.client.flight.Autopilot.phase()); }); }
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd("gamemode survival"); cmd("difficulty normal"); cmd("lanterna resetar"); cmd("clear @s"); cmd("time set 6000"); cmd("weather clear");
            cmd("execute in minecraft:overworld run tp @s 300.5 -60 300.5 0 0"); });
        at(40, () -> cmd("fill 296 -44 296 304 -42 304 minecraft:stone"));
        at(20, () -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna escolher lanterna:green")));
        waitFor(() -> com.lanterna.client.flight.Autopilot.phase() == com.lanterna.client.flight.Autopilot.Phase.ASCEND, 900);
        at(1, () -> glog("comecou a subir em " + String.format("%.1f %.1f %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ())));
        at(30, () -> { glog("subindo y=" + (int) mc.player.getY()); shot("v22_subindo"); });
        at(60, () -> server(sp -> { int ar = 0; StringBuilder sb = new StringBuilder();
            for (int x = 296; x <= 304; x++) for (int y = -44; y <= -42; y++) for (int z = 296; z <= 304; z++)
                if (sp.serverLevel().getBlockState(new net.minecraft.core.BlockPos(x, y, z)).isAir()) { ar++; if (ar < 12) sb.append(x).append(",").append(y).append(",").append(z).append(" "); }
            glog("teto: blocos quebrados=" + ar + " [" + sb + "] y=" + (int) sp.getY()); }));
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa") && !com.lanterna.client.flight.Autopilot.active() && mc.screen == null, 3000);
        at(60, () -> { glog("chegou y=" + (int) mc.player.getY() + " hp=" + mc.player.getHealth() + "/" + mc.player.getMaxHealth()); shot("v22_missao"); });
        at(10, () -> com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.network.OpenLanternMenuPacket()));
        at(30, () -> shot("v22_tela_k"));
        at(5, () -> { mc.player.closeContainer(); com.lanterna.client.visual.PerfilScreen.abrir(com.lanterna.client.visual.PerfilScreen.Aba.ESTATISTICAS); });
        at(40, () -> shot("v22_estatisticas"));
        at(5, () -> com.lanterna.client.visual.PerfilScreen.abrir(com.lanterna.client.visual.PerfilScreen.Aba.VISUAL));
        at(5, () -> ((com.lanterna.client.visual.PerfilScreen) mc.screen).escolher("a", 3));
        at(30, () -> shot("v22_visual_a"));
        at(5, () -> mc.setScreen(null));
        at(5, () -> { camera(CameraType.THIRD_PERSON_FRONT); mc.options.hideGui = true; });
        at(30, () -> shot("v22_traje_a"));
        for (String v : new String[]{"b", "padrao"}) {
            at(5, () -> { var sc = new com.lanterna.client.visual.PerfilScreen(com.lanterna.client.visual.PerfilScreen.Aba.VISUAL); mc.setScreen(sc); sc.escolher(v, 0); mc.setScreen(null); });
            at(30, () -> shot("v22_traje_" + v));
        }
        at(5, () -> server(sp -> glog("visual servidor=" + com.lanterna.visual.Visual.traje(sp) + " mascara=" + com.lanterna.visual.Visual.mascara(sp))));
        at(5, () -> { camera(CameraType.FIRST_PERSON); mc.options.hideGui = false; });
        // ---- voo de combate ----
        at(5, () -> { look(0, 0); if (!com.lanterna.client.ClientRingData.flight) com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.network.ToggleFlightPacket()); });
        at(20, () -> { cmd("tp @s ~ ~25 ~"); });
        at(20, () -> hold(mc.options.keyUp, true));
        at(10, () -> glog("W 10t vel=" + String.format("%.2f", com.lanterna.client.flight.ClientFlight.localSpeed() * 20) + " b/s"));
        at(30, () -> glog("W 40t vel=" + String.format("%.2f", com.lanterna.client.flight.ClientFlight.localSpeed() * 20) + " b/s"));
        at(1, () -> hold(mc.options.keyUp, false));
        at(10, () -> glog("solto 10t vel=" + String.format("%.2f", com.lanterna.client.flight.ClientFlight.localSpeed() * 20) + " b/s"));
        at(5, () -> { hold(mc.options.keyLeft, true); });
        at(15, () -> { glog("A 15t vel=" + String.format("%.2f", com.lanterna.client.flight.ClientFlight.localSpeed() * 20) + " b/s"); hold(mc.options.keyLeft, false); });
        at(5, () -> { hold(mc.options.keyUp, true); hold(mc.options.keySprint, true); });
        at(60, () -> glog("Ctrl+W 60t vel=" + String.format("%.2f", com.lanterna.client.flight.ClientFlight.localSpeed() * 20) + " b/s"));
        at(1, () -> { hold(mc.options.keyUp, false); hold(mc.options.keySprint, false); });
        at(40, () -> glog("parado vel=" + String.format("%.2f", com.lanterna.client.flight.ClientFlight.localSpeed() * 20) + " b/s"));
        // ---- nave pirata x laser e punho ----
        int[] nave = new int[1];
        at(5, () -> server(sp -> {
            var n = com.lanterna.naves.NaveEntity.create(sp.serverLevel(), com.lanterna.naves.NaveTipo.NAVE_PIRATA, "1");
            n.moveTo(sp.getX(), sp.getY() - 1, sp.getZ() + 22, 0, 0);
            sp.serverLevel().addFreshEntity(n); nave[0] = n.getId();
            var r = com.lanterna.capability.ModCapabilities.get(sp).orElse(null); com.lanterna.power.RingPowers.setCharge(sp, r, r.maxCharge());
            com.lanterna.construct.Affinity.add(sp, 30000);
            glog("nave hp=" + n.hp());
        }));
        Runnable mira = () -> { var e = mc.level.getEntity(nave[0]); if (e == null) return;
            var d = e.position().add(0, 2.5, 0).subtract(mc.player.getEyePosition());
            look((float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)))); };
        at(30, () -> { mira.run(); server(sp -> { var e = sp.serverLevel().getEntity(nave[0]); glog("nave y=" + (e == null ? "?" : (int) e.getY()) + " jogador y=" + (int) sp.getY()); }); });
        at(5, () -> { mira.run(); server(sp -> com.lanterna.construct.Constructs.cast(sp, "laser")); });
        for (int i = 0; i < 16; i++) at(5, mira);
        at(1, () -> shot("v22_laser_nave"));
        at(5, () -> server(sp -> { com.lanterna.construct.Constructs.release(sp, null);
            var e = sp.serverLevel().getEntity(nave[0]); glog("depois do laser hp=" + (e instanceof com.lanterna.naves.NaveEntity n ? n.hp() : -1)); }));
        at(20, () -> { mira.run(); server(sp -> { var r = com.lanterna.capability.ModCapabilities.get(sp).orElse(null); com.lanterna.power.RingPowers.setCharge(sp, r, r.maxCharge());
            com.lanterna.construct.Constructs.cast(sp, "punho"); }); });
        at(30, () -> server(sp -> { var e = sp.serverLevel().getEntity(nave[0]); glog("depois do punho hp=" + (e instanceof com.lanterna.naves.NaveEntity n ? n.hp() + " vivo=" + n.isAlive() : "sumiu")); }));
        // ---- galeria dos trajes em suportes de armadura ----
        at(10, () -> server(sp -> { com.lanterna.capability.ModCapabilities.get(sp).ifPresent(r -> { if (r.flightEnabled()) com.lanterna.power.RingPowers.toggleFlight(sp); }); }));
        at(10, () -> { cmd("execute in minecraft:overworld run tp @s 320.5 -60 330.5 0 8"); cmd("time set 6000"); });
        waitFor(() -> mc.level.dimension().location().getPath().equals("overworld") && mc.screen == null, 600);
        at(20, () -> cmd("fill 310 -60 326 331 -55 345 minecraft:air"));
        for (String v : new String[]{"a", "b"}) {
            at(30, () -> server(sp -> {
                for (var e : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, sp.getBoundingBox().inflate(30))) e.discard();
                String[] tr = com.lanterna.armor.ModTrajes.TROPAS;
                for (int i = 0; i < tr.length; i++) {
                    var st = new net.minecraft.world.entity.decoration.ArmorStand(sp.serverLevel(), 320.5 + (i - 4) * 1.6, -60, 337.5);
                    st.setYRot(180); st.setYBodyRot(180); st.setYHeadRot(180);
                    for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                            net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET})
                        st.setItemSlot(slot, new net.minecraft.world.item.ItemStack(com.lanterna.armor.ModTrajes.peca(tr[i], v, slot)));
                    st.setNoGravity(true);
                    sp.serverLevel().addFreshEntity(st);
                }
                sp.teleportTo(320.5, -60, 330.0); sp.setYRot(0); sp.setXRot(6);
            }));
            at(5, () -> { look(0, 6); mc.options.hideGui = true; });
            at(40, () -> shot("v22_galeria_" + v));
        }
        at(5, () -> mc.options.hideGui = false);
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }

    private static void sceneTeto() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { cmd("gamemode survival"); cmd("lanterna resetar"); cmd("execute in minecraft:overworld run tp @s 520.5 -60 520.5 0 0"); });
        at(30, () -> { cmd("fill 510 -59 510 530 -50 530 minecraft:air"); });
        at(10, () -> { cmd("fill 516 -55 516 524 -53 524 minecraft:stone"); });
        at(10, () -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna anel green")));
        at(20, () -> com.lanterna.client.flight.Autopilot.start(com.lanterna.client.flight.Destinations.byId("oa")));
        for (int i = 0; i < 30; i++) at(5, () -> {
            var p = mc.player;
            int ar = 0; for (int x = 516; x <= 524; x++) for (int y = -55; y <= -53; y++) for (int z = 516; z <= 524; z++)
                if (mc.level.getBlockState(new net.minecraft.core.BlockPos(x, y, z)).isAir()) ar++;
            glog("y=" + String.format("%.2f", p.getY()) + " ar=" + ar + " vcol=" + p.verticalCollision + " fase=" + com.lanterna.client.flight.Autopilot.phase() + " voo=" + com.lanterna.client.ClientRingData.flight + " hp=" + p.getHealth());
        });
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }

    private static void sceneTeto2() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { cmd("gamemode survival"); cmd("lanterna resetar"); cmd("execute in minecraft:overworld run tp @s 620.5 -60 620.5 0 0"); });
        at(30, () -> { cmd("fill 610 -59 610 630 -40 630 minecraft:air"); });
        at(10, () -> { cmd("fill 616 -50 616 624 -48 624 minecraft:stone"); });
        at(10, () -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna escolher lanterna:green")));
        for (int i = 0; i < 40; i++) at(5, () -> {
            var p = mc.player;
            int ar = 0; for (int x = 616; x <= 624; x++) for (int y = -50; y <= -48; y++) for (int z = 616; z <= 624; z++)
                if (mc.level.getBlockState(new net.minecraft.core.BlockPos(x, y, z)).isAir()) ar++;
            glog("y=" + String.format("%.2f", p.getY()) + " ar=" + ar + " vcol=" + p.verticalCollision + " fase=" + com.lanterna.client.flight.Autopilot.phase() + " voo=" + com.lanterna.client.ClientRingData.flight + " hp=" + p.getHealth());
        });
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }

    private static void sceneCabecas() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { cmd("gamemode creative"); cmd("time set 6000"); cmd("weather clear"); cmd("execute in minecraft:overworld run tp @s 720.5 -60 720.5 0 0"); });
        at(30, () -> cmd("fill 705 -60 715 735 -50 735 minecraft:air"));
        for (String v : new String[]{"padrao", "a", "b"}) {
            at(30, () -> server(sp -> {
                for (var e : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, sp.getBoundingBox().inflate(30))) e.discard();
                String[] tr = com.lanterna.armor.ModTrajes.TROPAS;
                for (int i = 0; i < tr.length; i++) {
                    var st = new net.minecraft.world.entity.decoration.ArmorStand(sp.serverLevel(), 720.5 + (i - 4) * 1.05, -60, 723.2);
                    st.setYRot(180); st.setYBodyRot(180); st.setYHeadRot(180);
                    for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST}) {
                        var item = v.equals("padrao") ? com.lanterna.armor.ModArmorItems.pieceFor(tr[i], slot) : com.lanterna.armor.ModTrajes.peca(tr[i], v, slot);
                        st.setItemSlot(slot, new net.minecraft.world.item.ItemStack(item));
                    }
                    st.setNoGravity(true); st.setInvisible(false);
                    sp.serverLevel().addFreshEntity(st);
                }
                sp.teleportTo(720.5, -59.3, 719.6); sp.setYRot(0); sp.setXRot(14);
            }));
            at(5, () -> { look(0, 14); mc.options.hideGui = true; });
            at(40, () -> shot("cab_" + v));
        }
        at(10, () -> { mc.options.hideGui = false; LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    private static void sceneV23() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd("gamemode creative"); cmd("time set 6000"); cmd("weather clear"); cmd("lanterna resetar"); });
        at(10, () -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna anel green")));
        at(10, () -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna nivel 35")));
        // ---- Palácio: moradores ----
        at(10, () -> cmd("execute in lanterna:oa run tp @s 0 172 -440"));
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa") && mc.screen == null, 900);
        at(20, () -> server(sp -> { var w = com.lanterna.lore.LoreWorldData.get(sp.server); glog("palacio construido=" + w.siteBuilt("oa:palacio") + " cidade=" + w.siteBuilt("oa:cidade2")); }));
        waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 6000);
        at(200, () -> server(sp -> { int n = 0; for (var e : sp.serverLevel().getEntitiesOfClass(com.lanterna.especies.LanternaEntity.class, sp.getBoundingBox().inflate(120)))
                if (e.funcao().startsWith("capitao_")) n++; glog("capitaes perto=" + n); }));
        at(10, () -> server(sp -> { int n = 0; for (var e : sp.serverLevel().getEntitiesOfClass(com.lanterna.especies.LanternaEntity.class, sp.getBoundingBox().inflate(120)))
                if (e.funcao().equals("capitao_comando")) { e.discard(); n++; } glog("capita qiri removida=" + n); }));
        at(800, () -> server(sp -> { int n = 0; for (var e : sp.serverLevel().getEntitiesOfClass(com.lanterna.especies.LanternaEntity.class, sp.getBoundingBox().inflate(120)))
                if (e.funcao().equals("capitao_comando")) { n++; glog("qiri de volta em " + e.blockPosition()); } glog("qiri vivas=" + n); }));
        // ---- Cidade ----
        at(10, () -> cmd("execute in lanterna:oa run tp @s 0 120 -150"));
        at(700, () -> server(sp -> { glog("altura estimada da cidade=" + com.lanterna.cidade.Moradores.alturaCidade(sp.serverLevel(), com.lanterna.cidade.Cidade.CENTRO));
            for (var e : sp.serverLevel().getEntitiesOfClass(com.lanterna.especies.LanternaEntity.class, sp.getBoundingBox().inflate(100, 80, 100)))
                if (!e.funcao().isEmpty()) glog("  npc " + e.funcao() + " em " + e.blockPosition()); }));
        // ---- uniforme sem a segunda camada da skin ----
        at(10, () -> { cmd("gamemode survival"); cmd("execute in lanterna:oa run tp @s 30 120 -150"); });
        at(60, () -> { mc.options.hideGui = false; shot("v23_hud"); });
        at(10, () -> server(sp -> { com.lanterna.capability.ModCapabilities.get(sp).ifPresent(r -> { if (r.flightEnabled()) com.lanterna.power.RingPowers.toggleFlight(sp); }); }));
        at(80, () -> { camera(CameraType.THIRD_PERSON_FRONT); mc.options.hideGui = true; look(0, 0); });
        for (String v : new String[]{"padrao", "a", "b"}) {
            at(5, () -> { var sc = new com.lanterna.client.visual.PerfilScreen(com.lanterna.client.visual.PerfilScreen.Aba.VISUAL); mc.setScreen(sc); sc.escolher(v, 0); mc.setScreen(null); });
            at(40, () -> shot("v23_rosto_" + v));
        }
        at(5, () -> { camera(CameraType.FIRST_PERSON); mc.options.hideGui = false; });
        // ---- hiperpropulsão no planeta ----
        at(5, () -> { cmd("execute in lanterna:oa run tp @s 0 160 -150"); look(0, 0); });
        at(20, () -> { if (!com.lanterna.client.ClientRingData.flight) com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.network.ToggleFlightPacket()); });
        at(40, () -> { look(0, 0); hold(mc.options.keyUp, true); hold(mc.options.keySprint, true); glog("voo=" + com.lanterna.client.ClientRingData.flight + " tela=" + mc.screen); });
        for (int i = 1; i <= 8; i++) { int k = i; at(20, () -> glog("Ctrl+W " + k + "s vel=" + String.format("%.1f", com.lanterna.client.flight.ClientFlight.localSpeed() * 20) + " hiper=" + com.lanterna.client.flight.ClientFlight.hyperOn())); }
        at(1, () -> { camera(CameraType.THIRD_PERSON_BACK); });
        at(3, () -> shot("v23_hiper"));
        at(1, () -> camera(CameraType.FIRST_PERSON));
        at(1, () -> { hold(mc.options.keyUp, false); hold(mc.options.keySprint, false); });
        at(30, () -> glog("soltou vel=" + String.format("%.1f", com.lanterna.client.flight.ClientFlight.localSpeed() * 20) + " hiper=" + com.lanterna.client.flight.ClientFlight.hyperOn()));
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }

    private static void sceneV24b() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { cmd("gamemode creative"); cmd("execute in lanterna:oa run tp @s 0 120 20"); });
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa") && mc.screen == null, 900);
        Runnable conta = () -> server(sp -> { int toran = 0, guard = 0;
            for (var e : sp.serverLevel().getEntitiesOfClass(com.lanterna.lore.entity.RaceEntity.class, new net.minecraft.world.phys.AABB(-60, -64, -60, 60, 320, 60))) {
                if (e.role().equals("instrutor")) { toran++; glog("  instrutor em " + e.blockPosition() + " vivo=" + e.isAlive()); } if (e.role().equals("guardiao")) guard++; }
            glog("base de Oa: instrutores=" + toran + " guardioes=" + guard + " hub construido=" + com.lanterna.lore.LoreWorldData.get(sp.server).siteBuilt("oa:hub")); });
        at(60, conta);
        at(300, conta);
        at(700, conta);
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }

    private static void sceneV24c() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { cmd("gamemode survival"); cmd("time set 6000"); cmd("execute in minecraft:overworld run tp @s 320.5 -60 330.5 0 0"); });
        for (String t : new String[]{"white", "yellow", "green"}) for (String v : new String[]{"padrao", "a"}) {
            at(10, () -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna anel " + t)));
            at(5, () -> cmd("clear @s"));
            at(10, () -> { var sc = new com.lanterna.client.visual.PerfilScreen(com.lanterna.client.visual.PerfilScreen.Aba.VISUAL); mc.setScreen(sc); sc.escolher(v, 0); mc.setScreen(null);
                camera(CameraType.FIRST_PERSON); mc.options.hideGui = false; mc.player.getInventory().selected = 8; look(0, 10); });
            at(40, () -> shot("v24c_" + t + "_" + v));
        }
        at(5, () -> com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.network.TogglePowerPacket()));
        at(40, () -> com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.network.TogglePowerPacket()));
        at(11, () -> shot("v24c_ligando"));
        at(10, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    private static void sceneAgua() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { cmd("gamemode spectator"); cmd("execute in lanterna:oa run tp @s 0 190 -440"); });
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa") && mc.screen == null, 900);
        Object[][] zonas = {{"palacio", 0, -470, 90, 140, 200}, {"cidade", 0, -150, 75, 30, 110}};
        for (Object[] z : zonas) {
            at(10, () -> cmd("execute in lanterna:oa run tp @s " + z[1] + " " + ((int) z[5] + 20) + " " + z[2]));
            at(300, () -> server(sp -> {
                var l = sp.serverLevel(); int cx = (int) z[1], cz = (int) z[2], r = (int) z[3];
                java.util.Map<String, Integer> grupos = new java.util.TreeMap<>();
                var m = new net.minecraft.core.BlockPos.MutableBlockPos();
                for (int x = cx - r; x <= cx + r; x++) for (int zz = cz - r; zz <= cz + r; zz++) for (int y = (int) z[4]; y <= (int) z[5]; y++) {
                    m.set(x, y, zz); var f = l.getFluidState(m);
                    if (!f.isEmpty() && !f.isSource()) grupos.merge((x >> 3 << 3) + "," + (y >> 3 << 3) + "," + (zz >> 3 << 3), 1, Integer::sum);
                }
                glog(z[0] + ": agua corrente em " + grupos);
                if (z[0].equals("palacio")) for (int y = 167; y <= 171; y++) { StringBuilder sb = new StringBuilder("y=" + y + ": ");
                    for (int x = 16; x <= 28; x++) { var st = l.getBlockState(new net.minecraft.core.BlockPos(x, y, -440)); var f = st.getFluidState();
                        String n = st.isAir() ? "." : !f.isEmpty() ? (f.isSource() ? "W" : "w") : st.getBlock().getDescriptionId().replace("block.minecraft.", "").replace("block.lanterna.", "");
                        sb.append(n.length() > 6 ? n.substring(0, 6) : n).append(" "); } glog(sb.toString()); }
                if (z[0].equals("cidade")) for (int y = 50; y <= 58; y++) { StringBuilder sb = new StringBuilder("y=" + y + ": ");
                    for (int zz = -100; zz <= -88; zz++) { var st = l.getBlockState(new net.minecraft.core.BlockPos(10, y, zz)); var f = st.getFluidState();
                        String n = st.isAir() ? "." : !f.isEmpty() ? (f.isSource() ? "W" : "w") : st.getBlock().getDescriptionId().replace("block.minecraft.", "").replace("block.lanterna.", "");
                        sb.append(n.length() > 6 ? n.substring(0, 6) : n).append(" "); } glog(sb.toString()); }
            }));
        }
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }





    private static void sceneArvore() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.renderDistance().set(8);
            cmd("gamerule doDaylightCycle false"); cmd("weather clear"); cmd("gamemode creative"); cmd("gamerule doMobSpawning false"); });
        at(10, () -> cmd("execute in lanterna:oa run tp @s -300 120 0.5"));
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa") && mc.screen == null, 900);
        at(100, () -> cmd("time set 6000"));
        waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 60000);
        String[] tropas = System.getProperty("lanterna.planetas", "green,yellow,red,orange,blue,indigo,violet").split(",");
        for (String tr : tropas) {
            at(20, () -> { cmd("lanterna anel " + tr); });
            at(20, () -> { cmd("lanterna fonte arvore tudo"); cmd("lanterna fonte selo todas"); cmd("lanterna cheio"); cmd("lanterna afinidade 100000"); });
            at(30, () -> server(sp -> { int h = com.lanterna.cidade.CidadeNativa.altura(sp.serverLevel()); sp.teleportTo(sp.serverLevel(), -300.5, h, 0.5, 90f, 10f); }));
            // a roda com os construtos novos
            at(40, () -> { com.lanterna.client.construct.ConstructWheelScreen.pinned = true; mc.setScreen(new com.lanterna.client.construct.ConstructWheelScreen()); });
            at(30, () -> shot("roda_" + tr));
            at(5, () -> { com.lanterna.client.construct.ConstructWheelScreen.pinned = false; mc.setScreen(new com.lanterna.client.fonte.ArvoreScreen()); });
            at(30, () -> shot("arvore_" + tr));
            at(5, () -> mc.setScreen(null));
            for (var t : com.lanterna.construct.ConstructType.values()) {
                if (!t.arvore || !t.corps.equals(tr)) continue;
                at(10, () -> server(sp -> {
                    var l = sp.serverLevel();
                    for (var e : l.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, sp.getBoundingBox().inflate(40), x -> !(x instanceof net.minecraft.world.entity.player.Player))) e.discard();
                    for (var e : l.getEntitiesOfClass(com.lanterna.construct.ConstructEntity.class, sp.getBoundingBox().inflate(60))) e.discard();
                    int h = com.lanterna.cidade.CidadeNativa.altura(l);
                    for (var q : net.minecraft.core.BlockPos.betweenClosed(-318, h - 1, -10, -290, h + 8, 10))
                        if (l.getBlockState(q).is(com.lanterna.construct.ConstructRegistry.CONSTRUCT_BLOCK.get())) l.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                    sp.teleportTo(l, -300.5, h, 0.5, 90f, 9f);
                    com.lanterna.construct.Constructs.zerarRecargas(sp.getUUID());
                    for (int i = 0; i < 4; i++) {
                        var z = net.minecraft.world.entity.EntityType.HUSK.create(l);
                        z.moveTo(-305.5 - (i % 2) * 2, h, -1.5 + i * 1.3, 270f, 0);
                        z.setNoAi(true);
                        z.setPersistenceRequired();
                        l.addFreshEntity(z);
                    }
                    var r = com.lanterna.capability.ModCapabilities.get(sp).orElse(null);
                    if (r != null) com.lanterna.power.RingPowers.setCharge(sp, r, r.maxCharge());
                }));
                at(10, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); server(sp -> com.lanterna.construct.Constructs.cast(sp, t.id)); });
                // depois de lançar, vira de lado: a câmera mostra o Lanterna e o alvo ao mesmo tempo
                at(2, () -> { mc.player.setYRot(140f); mc.player.setXRot(18f); });
                at(t.id.endsWith("meteoros") || t.id.endsWith("chuva") || t.id.endsWith("aurora") || t.id.endsWith("coracoes") || t.id.endsWith("lagrimas") ? 26 : 9, () -> shot("cons_" + t.id));
                at(30, () -> server(sp -> {
                    float perdido = 0; int n = 0;
                    for (var e : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Husk.class, sp.getBoundingBox().inflate(30))) { perdido += e.getMaxHealth() - e.getHealth(); n++; }
                    int cons = sp.serverLevel().getEntitiesOfClass(com.lanterna.construct.ConstructEntity.class, sp.getBoundingBox().inflate(60)).size();
                    glog(t.id + ": dano=" + Math.round(perdido) + " mortos=" + (4 - n) + " construtos=" + cons + " vida=" + Math.round(sp.getHealth()));
                }));
            }
            at(5, () -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        }
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }

    private static void sceneChefes() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.renderDistance().set(10);
            cmd("gamerule doDaylightCycle false"); cmd("gamemode creative"); cmd("lanterna anel green"); cmd("lanterna afinidade 100000"); });
        // o evento da Bateria em Oa
        at(10, () -> cmd("execute in lanterna:oa run tp @s -388 120 10"));
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa") && mc.screen == null, 900);
        waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 60000);
        at(20, () -> server(sp -> { int h = com.lanterna.cidade.CidadeNativa.altura(sp.serverLevel()); sp.teleportTo(sp.serverLevel(), -387.5, h + 6, 2.5, 180f, 20f); }));
        at(20, () -> { cmd("time set 6000"); cmd("lanterna fonte evento"); });
        at(80, () -> shot("evento_onda"));
        at(5, () -> server(sp -> glog("evento ativo=" + com.lanterna.fonte.BateriaEnfraquece.ativo() + " recarga=" + com.lanterna.fonte.BateriaEnfraquece.recarga(sp))));
        for (int i = 0; i < 6; i++) at(60, () -> { cmd("kill @e[type=lanterna:lanterna_npc,distance=..80]"); });
        at(60, () -> server(sp -> glog("evento depois ativo=" + com.lanterna.fonte.BateriaEnfraquece.ativo() + " recarga=" + com.lanterna.fonte.BateriaEnfraquece.recarga(sp) + " essencia=" + com.lanterna.fonte.FonteDados.essencia(sp))));
        String[] tropas = System.getProperty("lanterna.planetas", "green,yellow,red,orange,blue,indigo,violet").split(",");
        for (String tr : tropas) {
            at(20, () -> cmd("lanterna anel " + tr));
            at(20, () -> { cmd("lanterna fonte selo todas"); cmd("lanterna fonte arvore tudo"); cmd("lanterna fonte chefe"); });
            at(20, () -> cmd("lanterna fonte ir"));
            waitFor(() -> mc.level.dimension().location().getPath().startsWith("fonte_") && mc.screen == null, 900);
            at(60, () -> {});
            waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 60000);
            at(40, () -> server(sp -> sp.teleportTo(sp.serverLevel(), 0.5, 112, -14.5, 180f, 35f)));
            at(40, () -> shot("salas_" + tr));
            at(10, () -> server(sp -> sp.teleportTo(sp.serverLevel(), 0.5, 101, com.lanterna.fonte.FonteMundo.ILHA.getZ() - 8 + 0.5, 0f, 10f)));
            at(40, () -> server(sp -> {
                var pos = com.lanterna.fonte.FonteMundo.ILHA.above();
                var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
                net.minecraftforge.common.ForgeHooks.onRightClickBlock(sp, net.minecraft.world.InteractionHand.MAIN_HAND, pos, hit);
            }));
            at(10, () -> { cmd("gamemode survival"); cmd("effect give @s resistance 600 4 true"); cmd("effect give @s regeneration 600 4 true"); });
            at(20, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); });
            at(60, () -> shot("chefe_" + tr));
            at(140, () -> server(sp -> {
                var bs = sp.serverLevel().getEntitiesOfClass(com.lanterna.lore.entity.boss.ChefeFonte.class, sp.getBoundingBox().inflate(80));
                for (var b : bs) { b.setHealth(b.getMaxHealth() * 0.45f); glog(tr + " chefe=" + b.bossId() + " vida=" + Math.round(b.getHealth()) + "/" + Math.round(b.getMaxHealth())); }
                if (bs.isEmpty()) glog(tr + " chefe=NENHUM");
            }));
            at(100, () -> shot("chefe2_" + tr));
            at(10, () -> server(sp -> {
                for (var b : sp.serverLevel().getEntitiesOfClass(com.lanterna.lore.entity.boss.ChefeFonte.class, sp.getBoundingBox().inflate(80)))
                    b.hurt(sp.damageSources().playerAttack(sp), 100000f);
            }));
            at(40, () -> server(sp -> glog(tr + " coracao=" + com.lanterna.lore.entity.boss.ChefeFonte.temCoracao(sp) + " essencia=" + com.lanterna.fonte.FonteDados.essencia(sp) + " vida=" + Math.round(sp.getHealth()))));
            at(10, () -> { cmd("gamemode creative"); mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); cmd("kill @e[type=!player,distance=..120,type=!lanterna:alvo_treino]"); });
        }
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }

    private static void sceneForca() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.renderDistance().set(8);
            cmd("gamerule doDaylightCycle false"); cmd("gamemode creative"); cmd("lanterna anel green"); });
        at(20, () -> cmd("lanterna maximo"));
        at(20, () -> server(sp -> { var r = com.lanterna.capability.ModCapabilities.get(sp).orElse(null); var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null);
            glog("maximo: nivel_xp=" + (d == null ? -1 : d.xp()) + " afinidade=" + com.lanterna.construct.Affinity.points(com.lanterna.power.EquipmentHelper.ring(sp))
                + " carga=" + (r == null ? -1 : r.charge()) + "/" + (r == null ? -1 : r.maxCharge()) + " sobrecarga=" + com.lanterna.fonte.FonteDados.sobrecarga(sp)
                + " essencia=" + com.lanterna.fonte.FonteDados.essencia(sp) + " nos=" + com.lanterna.fonte.FonteDados.arvore(sp).size() + " coracao=" + com.lanterna.lore.entity.boss.ChefeFonte.temCoracao(sp)); }));
        at(10, () -> cmd("lanterna fonte chefe"));
        at(10, () -> cmd("lanterna fonte ir ilha"));
        waitFor(() -> mc.level.dimension().location().getPath().startsWith("fonte_") && mc.screen == null, 900);
        waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 60000);
        at(40, () -> server(sp -> {
            var pos = com.lanterna.fonte.FonteMundo.ILHA.above();
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
            net.minecraftforge.common.ForgeHooks.onRightClickBlock(sp, net.minecraft.world.InteractionHand.MAIN_HAND, pos, hit);
        }));
        // sem armadura, sem resistência: mede cada golpe e devolve a vida
        at(10, () -> { cmd("gamemode survival"); cmd("effect clear @s"); cmd("clear @s"); mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); });
        float[] ultima = {-1};
        int[] mortes = {0};
        for (int i = 0; i < 160; i++) {
            final int k = i;
            if (i == 80) at(1, () -> server(sp -> { for (var b : sp.serverLevel().getEntitiesOfClass(com.lanterna.lore.entity.boss.ChefeFonte.class, sp.getBoundingBox().inflate(80))) b.setHealth(b.getMaxHealth() * 0.45f); glog("--- fase 2 ---"); }));
            at(10, () -> server(sp -> {
                if (sp.isDeadOrDying()) { mortes[0]++; glog("MORREU t=" + k); return; }
                float h = sp.getHealth();
                if (ultima[0] > 0 && h < ultima[0] - 0.5f) { var src = sp.getLastDamageSource(); glog("t=" + (k / 2) + "s golpe=" + Math.round((ultima[0] - h) * 10) / 10f + " de " + Math.round(sp.getMaxHealth()) + " por=" + (src == null ? "?" : src.getMsgId() + "/" + (src.getDirectEntity() == null ? "-" : src.getDirectEntity().getType().getDescriptionId()))); }
                sp.setHealth(sp.getMaxHealth());
                ultima[0] = sp.getMaxHealth();
                var bs = sp.serverLevel().getEntitiesOfClass(com.lanterna.lore.entity.boss.ChefeFonte.class, sp.getBoundingBox().inflate(80));
                if (!bs.isEmpty() && sp.distanceTo(bs.get(0)) > 10) sp.teleportTo(bs.get(0).getX() + 6, bs.get(0).getY(), bs.get(0).getZ());
            }));
            if (i == 40 || i == 120) at(1, () -> shot("forca_" + k));
        }
        at(10, () -> { glog("mortes=" + mortes[0]); for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }

    private static final int LENTO = Integer.getInteger("lanterna.lento", 8);
    private static boolean gravando;

    private static void relogio(int fator) {
        com.lanterna.Lento.FATOR = fator;
        try {
            var f = Minecraft.class.getDeclaredField("timer"); f.setAccessible(true);
            Object timer = f.get(Minecraft.getInstance());
            var ms = timer.getClass().getDeclaredField("msPerTick"); ms.setAccessible(true);
            ms.setFloat(timer, 50f * fator);
        } catch (Exception e) { LanternaMod.LOGGER.error("lento", e); }
    }

    /** Começa um take: câmera lenta ligada (o vídeo depois é acelerado de volta). */
    private static void take(String nome) {
        if (gravando) corteAgora();
        relogio(LENTO);
        gravando = true;
        LanternaMod.LOGGER.info("TAKE {} {} fps={}", nome, System.currentTimeMillis(), Minecraft.getInstance().getFps());
    }

    private static void corteAgora() {
        LanternaMod.LOGGER.info("CORTE {}", System.currentTimeMillis());
        gravando = false;
        relogio(1);
    }

    private static void corte() { at(1, () -> { if (gravando) corteAgora(); }); }

    /** Caminho de câmera suave: o jogador (a câmera) vai de a até b em "ticks", com aceleração e freio. a/b = {x, y, z, yaw, pitch}. */
    private static void trajeto(int ticks, java.util.function.Supplier<double[]> fa, java.util.function.Supplier<double[]> fb) {
        Minecraft mc = Minecraft.getInstance();
        for (int k = 0; k <= ticks; k++) {
            final double t0 = (double) k / ticks, t = t0 * t0 * (3 - 2 * t0);
            at(1, () -> {
                double[] a = fa.get(), b = fb.get();
                var p = mc.player;
                p.getAbilities().flying = true;
                p.setDeltaMovement(0, 0, 0);
                p.setPos(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t);
                p.setYRot((float) (a[3] + (b[3] - a[3]) * t));
                p.setXRot((float) (a[4] + (b[4] - a[4]) * t));
                p.setYHeadRot(p.getYRot());
                p.yBodyRot = p.getYRot();
            });
        }
    }

    private static double[] v(double x, double y, double z, double yaw, double pitch) { return new double[]{x, y, z, yaw, pitch}; }

    /** Cenas para o trailer: cada "TAKE" marca no log o início de um trecho bom para o vídeo (em câmera lenta). */
    private static void sceneTrailer() {
        Minecraft mc = Minecraft.getInstance();
        Runnable voar = () -> server(sp -> com.lanterna.capability.ModCapabilities.get(sp).ifPresent(r -> { if (!r.flightEnabled()) com.lanterna.power.RingPowers.toggleFlight(sp); }));
        Runnable parar = () -> { mc.options.keyUp.setDown(false); mc.options.keySprint.setDown(false); mc.options.keyJump.setDown(false); };
        Runnable tempo = () -> { cmd("execute in minecraft:overworld run weather clear 1000000"); cmd("time set 1000"); };
        int[] H = {60};
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.renderDistance().set(12);
            mc.options.hideGui = true; mc.options.bobView().set(false);
            cmd("gamerule doDaylightCycle false"); cmd("gamerule doWeatherCycle false"); cmd("gamemode creative"); cmd("gamerule doMobSpawning false");
            cmd("lanterna anel green"); cmd("execute in minecraft:overworld run weather clear 1000000"); });
        at(20, () -> { cmd("lanterna maximo"); cmd("effect give @s resistance 99999 4 true"); });
        at(10, () -> cmd("execute in lanterna:oa run tp @s -480 200 -150 0 20"));
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa") && mc.screen == null, 900);
        waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 60000);
        at(10, tempo);
        at(5, () -> server(sp -> H[0] = com.lanterna.cidade.CidadeNativa.altura(sp.serverLevel())));
        // pré-carrega o caminho do voo
        at(5, () -> cmd("tp @s -480 170 -330 0 25"));
        at(200, () -> {});
        // ---- 1. voo sobre a megacidade de Oa ----
        at(5, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); voar.run(); take("voo_cidade"); });
        at(1, () -> {});
        trajeto(220, () -> v(-480.5, H[0] + 110, -330.5, 0, 22), () -> v(-470.5, H[0] + 45, -90.5, 12, 14));
        corte();
        // ---- 2. a Bateria Central (vista de baixo, recuando) ----
        at(10, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); cmd("tp @s -388 " + (H[0] + 4) + " 14 0 -14"); });
        at(60, () -> take("bateria"));
        at(1, () -> {});
        trajeto(150, () -> v(-387.5, H[0] + 4, 16.5, 0, -16), () -> v(-396.5, H[0] + 12, 0.5, -12, -12));
        corte();
        // ---- 3. construtos novos contra piratas ----
        String[] cons = {"g_canhao", "g_meteoros", "r_chuva", "b_aurora", "v_jardim", "o_vortice", "y_morcegos", "i_raizes"};
        String[] anel = {"green", "green", "red", "blue", "violet", "orange", "yellow", "indigo"};
        for (int i = 0; i < cons.length; i++) {
            final String id = cons[i], an = anel[i];
            at(10, () -> cmd("lanterna anel " + an));
            at(10, () -> server(sp -> {
                var l = sp.serverLevel();
                for (var e : l.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, sp.getBoundingBox().inflate(50), x -> !(x instanceof net.minecraft.world.entity.player.Player))) e.discard();
                for (var e : l.getEntitiesOfClass(com.lanterna.construct.ConstructEntity.class, sp.getBoundingBox().inflate(60))) e.discard();
                int h = H[0];
                for (var q : net.minecraft.core.BlockPos.betweenClosed(-325, h - 1, -12, -290, h + 10, 12))
                    if (l.getBlockState(q).is(com.lanterna.construct.ConstructRegistry.CONSTRUCT_BLOCK.get())) l.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                sp.teleportTo(l, -300.5, h, 0.5, 90f, 10f);
                com.lanterna.construct.Constructs.zerarRecargas(sp.getUUID());
                for (int j = 0; j < 5; j++) {
                    var z = com.lanterna.especies.LanternaEntity.pirata(l, j == 0);
                    z.moveTo(-311.5 - (j % 2) * 2.5, h, -3.5 + j * 1.8, 270f, 0);
                    z.setNoAi(true);
                    var hp = z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                    if (hp != null) hp.setBaseValue(400);
                    z.setHealth(400);
                    l.addFreshEntity(z);
                }
                var r = com.lanterna.capability.ModCapabilities.get(sp).orElse(null);
                if (r != null) { if (!r.powerActive()) com.lanterna.power.RingPowers.togglePower(sp); com.lanterna.power.RingPowers.setCharge(sp, r, r.maxCharge()); }
            }));
            at(15, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); mc.player.setYRot(90f); mc.player.setXRot(10f); });
            at(2, () -> { take("cons_" + id); server(sp -> com.lanterna.construct.Constructs.cast(sp, id)); });
            at(3, () -> { mc.player.setYRot(130f); mc.player.setXRot(16f); });
            at(id.endsWith("meteoros") || id.endsWith("chuva") || id.endsWith("aurora") ? 70 : 50, () -> {});
            corte();
        }
        // ---- 4. a roda e a Árvore da Luz ----
        at(5, () -> cmd("lanterna anel green"));
        at(15, () -> { take("roda"); com.lanterna.client.construct.ConstructWheelScreen.pinned = true; mc.setScreen(new com.lanterna.client.construct.ConstructWheelScreen()); });
        at(50, () -> { take("arvore"); com.lanterna.client.construct.ConstructWheelScreen.pinned = false; mc.setScreen(new com.lanterna.client.fonte.ArvoreScreen()); });
        at(50, () -> mc.setScreen(null));
        corte();
        // ---- 5. a Fonte: aura e voo até a Ilha do Coração ----
        at(5, () -> cmd("lanterna fonte ir"));
        waitFor(() -> mc.level.dimension().location().getPath().startsWith("fonte_") && mc.screen == null, 900);
        waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 60000);
        at(60, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); voar.run(); });
        at(5, () -> {});
        trajeto(1, () -> v(0.5, 102, 6.5, 180, 8), () -> v(0.5, 102, 6.5, 180, 8));
        at(20, () -> take("fonte_aura"));
        at(1, () -> {});
        trajeto(80, () -> v(0.5, 102, 6.5, 180, 8), () -> v(0.5, 104.5, 6.5, 215, 4));
        corte();
        at(10, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); take("fonte_voo"); });
        at(1, () -> {});
        trajeto(160, () -> v(0.5, 118, -45.5, 0, 24), () -> v(0.5, 110, 95.5, 0, 10));
        corte();
        // ---- 6. os chefes ----
        String[] tr = {"green", "red", "violet", "orange"};
        for (String t : tr) {
            at(5, () -> { cmd("lanterna anel " + t); cmd("lanterna fonte chefe"); });
            at(10, () -> cmd("lanterna fonte ir ilha"));
            waitFor(() -> mc.level.dimension().location().getPath().startsWith("fonte_") && mc.screen == null, 900);
            waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 60000);
            at(40, () -> server(sp -> {
                for (var b : sp.serverLevel().getEntitiesOfClass(com.lanterna.lore.entity.boss.ChefeFonte.class, sp.getBoundingBox().inflate(120))) b.discard();
                var pos = com.lanterna.fonte.FonteMundo.ILHA.above();
                var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
                net.minecraftforge.common.ForgeHooks.onRightClickBlock(sp, net.minecraft.world.InteractionHand.MAIN_HAND, pos, hit);
                var il = com.lanterna.fonte.FonteMundo.ILHA;
                sp.teleportTo(sp.serverLevel(), il.getX() - 7.5, il.getY() + 1, il.getZ() - 2.5, -35f, -6f);
            }));
            at(10, () -> { cmd("gamemode survival"); mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); });
            at(30, () -> take("chefe_" + t));
            // o Lanterna ataca com construtos enquanto o chefe responde (câmera olhando para o chefe)
            for (int k = 0; k < 6; k++) {
                final int kk = k;
                at(20, () -> server(sp -> {
                    var bs = sp.serverLevel().getEntitiesOfClass(com.lanterna.lore.entity.boss.ChefeFonte.class, sp.getBoundingBox().inflate(80));
                    if (bs.isEmpty()) return;
                    var b = bs.get(0);
                    var d = b.position().add(0, b.getBbHeight() * 0.5, 0).subtract(sp.getEyePosition());
                    float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
                    float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
                    sp.teleportTo(sp.serverLevel(), sp.getX(), sp.getY(), sp.getZ(), yaw, pitch);
                    if (kk % 2 == 0) {
                        com.lanterna.construct.Constructs.zerarRecargas(sp.getUUID());
                        for (var ct : com.lanterna.construct.ConstructType.values()) if (ct.arvore && ct.corps.equals(t) && (ct.id.endsWith("canhao") || ct.id.endsWith("lanca") || ct.id.endsWith("vomito") || ct.id.endsWith("punho") || ct.id.endsWith("moedas") || ct.id.endsWith("flecha"))) {
                            com.lanterna.construct.Constructs.cast(sp, ct.id);
                            break;
                        }
                    }
                    b.setHealth(Math.max(1, b.getHealth() - b.getMaxHealth() * 0.09f));
                }));
            }
            at(1, () -> { take("chefe_morre_" + t); server(sp -> { for (var b : sp.serverLevel().getEntitiesOfClass(com.lanterna.lore.entity.boss.ChefeFonte.class, sp.getBoundingBox().inflate(80))) b.hurt(sp.damageSources().playerAttack(sp), 100000f); }); });
            at(50, () -> cmd("gamemode creative"));
            corte();
        }
        // ---- 7. viagem no espaço ----
        at(10, () -> { cmd("lanterna anel green"); cmd("execute in lanterna:space run tp @s 300 -200 300 90 0"); });
        waitFor(() -> mc.level.dimension().location().getPath().equals("space") && mc.screen == null, 600);
        at(80, () -> { com.lanterna.client.flight.Autopilot.clear(); voar.run(); });
        at(10, () -> { mc.player.setXRot(0); mc.player.setYRot(90); mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); mc.options.keyUp.setDown(true); mc.options.keySprint.setDown(true); });
        at(60, () -> take("espaco"));
        for (int k = 0; k < 60; k++) at(3, () -> mc.player.setYRot(mc.player.getYRot() + (float) Math.sin(mc.player.tickCount * 0.03) * 0.8f));
        at(1, parar);
        corte();
        // ---- 8. evento: piratas atacam a Bateria ----
        at(10, () -> cmd("execute in lanterna:oa run tp @s -388 120 10"));
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa") && mc.screen == null, 900);
        at(20, tempo);
        at(20, () -> { cmd("gamemode creative"); cmd("lanterna fonte evento"); });
        at(60, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); take("evento"); });
        at(1, () -> {});
        trajeto(140, () -> v(-372.5, H[0] + 10, 6.5, 50, 22), () -> v(-404.5, H[0] + 12, 6.5, -45, 26));
        corte();
        // ---- 9. final: a câmera sobe e revela a megacidade ----
        at(5, () -> { cmd("tp @s -440 " + (H[0] + 40) + " 120 180 20"); });
        at(100, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); take("final"); });
        at(1, () -> {});
        trajeto(180, () -> v(-440.5, H[0] + 40, 130.5, 165, 15), () -> v(-470.5, H[0] + 150, 260.5, 180, 32));
        corte();
        at(20, () -> { LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    private static float k0() { return 0f; }

    /** Combate contra um Lanterna NPC inimigo: o jogador mira no que vê (posição do cliente) e segura o laser. */
    private static void sceneCombate() {
        Minecraft mc = Minecraft.getInstance();
        int[] npcId = {-1};
        int[] cont = {0, 0};
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.renderDistance().set(8);
            cmd("gamerule doDaylightCycle false"); cmd("execute in minecraft:overworld run weather clear 100000"); cmd("lanterna anel green"); });
        at(20, () -> { cmd("lanterna maximo"); });
        at(10, () -> cmd("execute in lanterna:oa run tp @s 40 140 60"));
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa") && mc.screen == null, 900);
        at(60, () -> server(sp -> {
            int h = sp.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 40, 60);
            sp.teleportTo(sp.serverLevel(), 40.5, h, 60.5, 0, 0);
        }));
        at(10, () -> { cmd("gamemode survival"); cmd("effect give @s resistance 9999 4 true"); cmd("effect give @s regeneration 9999 4 true"); });
        String[] fases = {"sem_comp", "com_comp"};
        for (String fase : fases) {
            at(10, () -> server(sp -> {
                com.lanterna.combate.Mira.desligada = fase.equals("sem_comp");
                for (var e : sp.serverLevel().getEntitiesOfClass(com.lanterna.especies.LanternaEntity.class, sp.getBoundingBox().inflate(80))) e.discard();
                var e = new com.lanterna.especies.LanternaEntity(com.lanterna.especies.EspeciesEntities.LANTERNA_NPC.get(), sp.serverLevel());
                e.setup(com.lanterna.especies.Especie.paraTropa(com.lanterna.corps.BuiltinCorps.YELLOW, sp.getRandom()), 1, com.lanterna.especies.Especie.Papel.LANTERNA,
                        com.lanterna.corps.BuiltinCorps.YELLOW, "roupa_tropa_yellow", false, false);
                e.moveTo(sp.getX() + 10, sp.getY() + 3, sp.getZ() + 2, 0, 0);
                var hp = e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                hp.setBaseValue(100000); e.setHealth(100000);
                sp.serverLevel().addFreshEntity(e);
                e.setTarget(sp);
                npcId[0] = e.getId(); cont[0] = 0; cont[1] = 0;
            }));
            at(60, () -> {});
            // 12 s segurando o laser, mirando onde o cliente mostra o NPC
            at(1, () -> construct("laser", true));
            for (int k = 0; k < 240; k++) {
                at(1, () -> {
                    Entity n = mc.level.getEntity(npcId[0]);
                    if (n != null) mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, n.getPosition(mc.getFrameTime()).add(0, n.getBbHeight() * 0.5, 0));
                    server(sp -> {
                        Entity sn = sp.serverLevel().getEntity(npcId[0]);
                        if (sn instanceof net.minecraft.world.entity.LivingEntity le) { cont[1]++; if (le.hurtTime == 10) cont[0]++; if (cont[1] % 40 == 0) glog(fase + " t=" + cont[1] + " dist=" + Math.round(le.distanceTo(sp)) + " vel=" + Math.round(le.getDeltaMovement().length() * 200) / 10.0 + " vida=" + Math.round(le.getHealth())); } else if (cont[1] >= 0) { glog(fase + " NPC sumiu"); cont[1] = -1000; }
                        com.lanterna.capability.ModCapabilities.get(sp).ifPresent(r -> com.lanterna.power.RingPowers.setCharge(sp, r, r.maxCharge()));
                    });
                });
                if (k == 120) at(0, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); shot("combate_" + fase); });
                if (k == 125) at(0, () -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
            }
            at(1, () -> construct("laser", false));
            at(5, () -> server(sp -> {
                Entity sn = sp.serverLevel().getEntity(npcId[0]);
                glog(fase + ": acertos " + cont[0] + " de " + (cont[1] / 4) + " pulsos do laser (" + Math.round(400.0 * cont[0] / Math.max(1, cont[1])) + "%)"
                        + (sn != null ? " dist=" + Math.round(sn.distanceTo(sp)) : ""));
            }));
        }
        // regras de nascimento dos Ecos
        at(10, () -> server(sp -> {
            var l = sp.serverLevel();
            java.util.function.BiFunction<Integer, Integer, String> t = (x, z) -> {
                int y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                int ok = 0;
                for (int i = 0; i < 200; i++) if (com.lanterna.lore.world.ZonasDeNascimento.ecoPodeNascer(l, new net.minecraft.core.BlockPos(x, y, z), net.minecraft.world.entity.MobSpawnType.NATURAL, l.random)) ok++;
                return ok + "/200 chao=" + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(l.getBlockState(new net.minecraft.core.BlockPos(x, y - 1, z)).getBlock()).getPath();
            };
            glog("eco megacidade(-480,30): " + t.apply(-480, 30));
            glog("eco megacidade(-400,-120): " + t.apply(-400, -120));
            glog("eco cidadeTropa(10,-150): " + t.apply(10, -150));
            glog("eco base(5,5): " + t.apply(5, 5));
            glog("eco campo(40,60): " + t.apply(40, 60));
            glog("eco campo(200,200): " + t.apply(200, 200));
            glog("eco campo(-150,250): " + t.apply(-150, 250));
        }));
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }

    /** Bairro novo das megacidades e moradores a mais. */
    private static void sceneDensa() {
        Minecraft mc = Minecraft.getInstance();
        String[] pls = System.getProperty("lanterna.planetas", "oa").split(",");
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.renderDistance().set(16); mc.options.hideGui = true;
            cmd("gamemode creative"); cmd("execute in minecraft:overworld run weather clear 100000"); cmd("gamerule doDaylightCycle false"); cmd("time set 1000"); });
        for (String pl : pls) {
            at(5, () -> server(sp -> { if (System.getProperty("lanterna.refazer") != null) com.lanterna.lore.LoreWorldData.get(sp.server).resetSite(pl + ":megacidade_densa"); }));
            at(10, () -> cmd("execute in lanterna:" + pl + " run tp @s -480 260 -60 0 60"));
            waitFor(() -> mc.level.dimension().location().getPath().equals(pl) && mc.screen == null, 900);
            at(200, () -> {});
            waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 60000);
            at(100, () -> {});
            waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 60000);
            at(60, () -> server(sp -> {
                var l = sp.serverLevel();
                var c = com.lanterna.cidade.CidadeNativa.CENTRO;
                int n = l.getEntitiesOfClass(com.lanterna.lore.entity.RaceEntity.class, new net.minecraft.world.phys.AABB(c).inflate(210, 400, 210)).size();
                glog(pl + " nativos=" + n + " densa=" + com.lanterna.lore.LoreWorldData.get(sp.server).siteBuilt(pl + ":megacidade_densa"));
                sp.teleportTo(l, -480.5, com.lanterna.cidade.CidadeNativa.altura(l) + 150, -40.5, 0f, 70f);
            }));
            at(80, () -> shot("densa_cima_" + pl));
            at(5, () -> server(sp -> { int h = com.lanterna.cidade.CidadeNativa.altura(sp.serverLevel()); sp.teleportTo(sp.serverLevel(), -480.5 + 86, h + 14, 30.5, 120f, 25f); }));
            at(80, () -> shot("densa_rua_" + pl));
            at(5, () -> cmd("execute in lanterna:" + pl + " run tp @s 0 140 -150 0 60"));
            at(200, () -> server(sp -> {
                int n = sp.serverLevel().getEntitiesOfClass(com.lanterna.especies.LanternaEntity.class, new net.minecraft.world.phys.AABB(com.lanterna.cidade.Cidade.CENTRO).inflate(80, 300, 80)).size();
                glog(pl + " npcs cidade da Tropa=" + n);
            }));
            at(700, () -> server(sp -> {
                int n = sp.serverLevel().getEntitiesOfClass(com.lanterna.especies.LanternaEntity.class, new net.minecraft.world.phys.AABB(com.lanterna.cidade.Cidade.CENTRO).inflate(80, 300, 80)).size();
                glog(pl + " npcs cidade da Tropa (depois do censo)=" + n);
            }));
        }
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }

    /** Naves: queda ao pousar, dano dos anéis nas naves de Tropa, sair depois de renascer, chave que chama a nave. */

    private static void sceneV30() {
        Minecraft mc = Minecraft.getInstance();
        String miss = com.lanterna.juramento.Juramento.missao(com.lanterna.baterias.Tropa.VERDE);
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.renderDistance().set(10);
            cmd("gamerule doDaylightCycle false"); cmd("execute in minecraft:overworld run weather clear 100000"); cmd("gamemode creative"); cmd("lanterna anel green"); });
        at(10, () -> cmd("execute in lanterna:oa run tp @s -388 120 10"));
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa") && mc.screen == null, 900);
        at(100, () -> cmd("time set 6000"));
        waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 60000);
        // ---- 1. juramento falado ----
        at(20, () -> server(sp -> {
            var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null);
            glog("missao juramento ativa=" + d.isActive(miss) + " ativas=" + d.active().keySet());
            if (!d.isActive(miss)) com.lanterna.mission.MissionManager.start(sp, miss);
            glog("depois de iniciar: ativa=" + d.isActive(miss) + " anel ok=" + com.lanterna.fonte.FonteChamado.tropaDoAnel(sp));
            int h = com.lanterna.cidade.CidadeNativa.altura(sp.serverLevel());
            sp.teleportTo(sp.serverLevel(), -387.5, h, 28.5, 0f, 10f);
        }));
        at(30, () -> { cmd("gamemode survival"); cmd("effect give @s resistance 900 4 true"); });
        at(20, () -> server(sp -> glog("usar bateria=" + com.lanterna.fonte.FonteChamado.usarBateria(sp, new net.minecraft.core.BlockPos(-388, 60, 38)))));
        at(30, () -> glog("tela juramento aberta=" + (mc.screen instanceof com.lanterna.client.juramento.JuramentoScreen)));
        at(5, () -> shot("v30_juramento_inicio"));
        at(5, () -> com.lanterna.client.juramento.JuramentoScreen.autoFalar = true);
        at(120, () -> shot("v30_juramento_meio"));
        waitFor(() -> !(mc.screen instanceof com.lanterna.client.juramento.JuramentoScreen), 4000);
        at(20, () -> { com.lanterna.client.juramento.JuramentoScreen.autoFalar = false; server(sp -> {
            var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null);
            glog("juramento: chamado verde:anel=" + com.lanterna.fonte.FonteDados.chamado(sp, "verde:anel") + " missao ainda ativa=" + d.isActive(miss) + " ativas=" + d.active().keySet());
        }); });
        // ---- 1b. sem falar nada não passa ----
        at(20, () -> server(sp -> { com.lanterna.fonte.FonteDados.chamado(sp, "verde:anel", 0); com.lanterna.juramento.Juramento.abrir(sp, com.lanterna.juramento.Juramento.ANEL, com.lanterna.baterias.Tropa.VERDE, new net.minecraft.core.BlockPos(-388, 60, 38)); }));
        at(200, () -> glog("silencio: tela ainda aberta=" + (mc.screen instanceof com.lanterna.client.juramento.JuramentoScreen)));
        at(5, () -> mc.setScreen(null));
        at(20, () -> server(sp -> { glog("silencio: chamado=" + com.lanterna.fonte.FonteDados.chamado(sp, "verde:anel")); com.lanterna.fonte.FonteDados.chamado(sp, "verde:anel", 1); }));
        // ---- 2. câmera por cima do ombro ----
        at(10, () -> { com.lanterna.client.camera.CameraLanterna.ligada = true; mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            server(sp -> { int h = com.lanterna.cidade.CidadeNativa.altura(sp.serverLevel()); sp.teleportTo(sp.serverLevel(), -387.5, h, 22.5, 0f, -5f); }); });
        at(40, () -> { glog("camera aplicada=" + com.lanterna.client.camera.CameraLanterna.aplicada + " pos=" + mc.gameRenderer.getMainCamera().getPosition() + " player=" + mc.player.getEyePosition()); shot("v30_camera_ombro"); });
        at(10, () -> com.lanterna.client.camera.CameraLanterna.trocarOmbro());
        at(30, () -> shot("v30_camera_ombro_esq"));
        at(10, () -> { com.lanterna.client.camera.CameraLanterna.trocarOmbro(); com.lanterna.client.camera.CameraLanterna.ligada = false; });
        at(20, () -> shot("v30_camera_vanilla"));
        at(10, () -> com.lanterna.client.camera.CameraLanterna.ligada = true);
        // ---- 3. ligação + holograma ----
        at(10, () -> {
            java.util.UUID fake = java.util.UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");
            ClientLigacaoTeste.estado(3, mc.player.getUUID(), mc.player.getGameProfile().getName(), fake, "Kilowog_");
        });
        at(40, () -> shot("v30_ligacao_terceira"));
        at(10, () -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        at(30, () -> shot("v30_ligacao_primeira"));
        at(10, () -> ClientLigacaoTeste.estado(2, java.util.UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6"), "Kilowog_", null, null));
        at(30, () -> shot("v30_ligacao_recebendo"));
        at(10, () -> { ClientLigacaoTeste.estado(0, null, null, null, null); mc.setScreen(new com.lanterna.client.voz.ComunicadorScreen()); });
        at(20, () -> shot("v30_comunicador"));
        at(5, () -> mc.setScreen(null));
        at(10, () -> server(sp -> glog("svc presente=" + com.lanterna.voz.Ligacoes.svc() + " voicechat carregado=" + net.minecraftforge.fml.ModList.get().isLoaded("voicechat"))));
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }

    /** Monta um estado de ligação falso no cliente (o teste só tem um jogador). */
    private static final class ClientLigacaoTeste {
        static void estado(int modo, java.util.UUID a, String na, java.util.UUID b, String nb) {
            var C = com.lanterna.client.voz.ClientLigacao.class;
            com.lanterna.client.voz.ClientLigacao.modo = modo;
            com.lanterna.client.voz.ClientLigacao.segundos = 74;
            com.lanterna.client.voz.ClientLigacao.recebidoEm = System.currentTimeMillis();
            var l = new java.util.ArrayList<com.lanterna.client.voz.ClientLigacao.Pessoa>();
            if (a != null) l.add(new com.lanterna.client.voz.ClientLigacao.Pessoa(a, na, false));
            if (b != null) l.add(new com.lanterna.client.voz.ClientLigacao.Pessoa(b, nb, true));
            com.lanterna.client.voz.ClientLigacao.membros = l;
            com.lanterna.client.voz.ClientLigacao.tocando = java.util.List.of();
        }
    }


    private static void sceneInclina() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.renderDistance().set(8);
            cmd("gamerule doDaylightCycle false"); cmd("execute in minecraft:overworld run weather clear 100000"); cmd("lanterna anel green"); cmd("gamemode creative"); });
        at(10, () -> cmd("execute in lanterna:oa run tp @s 60 140 60"));
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa") && mc.screen == null, 900);
        at(60, () -> server(sp -> {
            var l = sp.serverLevel();
            for (var n : l.getEntitiesOfClass(com.lanterna.naves.NaveEntity.class, sp.getBoundingBox().inflate(120))) n.discard();
            int h = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 60, 60);
            var n = com.lanterna.naves.NaveEntity.create(l, com.lanterna.naves.NaveTipo.NAVE_LANCEIRA, "green");
            n.moveTo(60.5, h + 40, 60.5, 0, 0);
            n.setDono(sp.getUUID());
            l.addFreshEntity(n);
            sp.teleportTo(l, 60.5, h + 41, 60.5, 0, 0);
            sp.startRiding(n, true);
        }));
        at(40, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); mc.options.keyUp.setDown(true); });
        // curva longa para a direita
        for (int i = 0; i < 50; i++) at(1, () -> { mc.player.setYRot(mc.player.getYRot() + 5f); });
        at(1, () -> { var n = (com.lanterna.naves.NaveEntity) mc.player.getVehicle();
            glog("roll=" + n.visRoll(1) + " pitch=" + n.visPitch(1) + " nave=" + n.position() + " jogador=" + mc.player.position()); shot("inclina_tras"); });
        at(1, () -> mc.getEntityRenderDispatcher().setRenderHitBoxes(true));
        at(1, () -> { com.lanterna.naves.client.NaveRenderer.OCULTA = true; });
        for (int i = 0; i < 20; i++) at(1, () -> { mc.player.setYRot(mc.player.getYRot() + 5f); });
        at(1, () -> { glog("roll sem casco=" + ((com.lanterna.naves.NaveEntity) mc.player.getVehicle()).visRoll(1)); shot("inclina_tras_sem_casco"); });
        for (int i = 0; i < 20; i++) at(1, () -> { mc.player.setYRot(mc.player.getYRot() + 5f); });
        at(1, () -> { com.lanterna.naves.client.NaveRenderer.OCULTA = false; mc.getEntityRenderDispatcher().setRenderHitBoxes(false); });
        for (int i = 0; i < 10; i++) at(1, () -> { mc.player.setYRot(mc.player.getYRot() + 5f); });
        at(1, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); });
        for (int i = 0; i < 6; i++) at(1, () -> { mc.player.setYRot(mc.player.getYRot() + 5f); });
        at(1, () -> shot("inclina_frente"));
        for (int i = 0; i < 6; i++) at(1, () -> { mc.player.setYRot(mc.player.getYRot() + 5f); });
        at(1, () -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        for (int i = 0; i < 6; i++) at(1, () -> { mc.player.setYRot(mc.player.getYRot() + 5f); });
        at(1, () -> { var n = (com.lanterna.naves.NaveEntity) mc.player.getVehicle(); glog("roll 1p=" + n.visRoll(1)); shot("inclina_primeira"); });
        at(1, () -> mc.options.keyUp.setDown(false));
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }


    private static void sceneInclina2() {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.entity.decoration.ArmorStand[] cam = {null};
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.renderDistance().set(8); mc.options.hideGui = true;
            cmd("gamerule doDaylightCycle false"); cmd("execute in minecraft:overworld run weather clear 100000"); cmd("lanterna anel green"); cmd("gamemode creative"); });
        at(10, () -> cmd("execute in lanterna:oa run tp @s 60 140 60"));
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa") && mc.screen == null, 900);
        String[] tipos = {"NAVE_LANCEIRA", "NAVE_PATRULHA"};
        for (String tp : tipos) {
            at(40, () -> server(sp -> {
                var l = sp.serverLevel();
                sp.stopRiding();
                for (var n : l.getEntitiesOfClass(com.lanterna.naves.NaveEntity.class, sp.getBoundingBox().inflate(200))) n.discard();
                var n = com.lanterna.naves.NaveEntity.create(l, com.lanterna.naves.NaveTipo.valueOf(tp), "green");
                n.moveTo(60.5, 150, 60.5, 0, 0);
                n.setDono(sp.getUUID());
                l.addFreshEntity(n);
                sp.teleportTo(l, 60.5, 151, 60.5, 0, 0);
                sp.startRiding(n, true);
            }));
            at(30, () -> { mc.player.setXRot(0); mc.options.keyUp.setDown(true); mc.options.keyJump.setDown(true); });
            at(30, () -> {
                var n = mc.player.getVehicle();
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            });
            at(12, () -> {});
            at(1, () -> { var n = (com.lanterna.naves.NaveEntity) mc.player.getVehicle(); glog(tp + " subindo pitch=" + n.visPitch(1)); shot("inclina_sobe_" + tp.toLowerCase()); });
            at(1, () -> com.lanterna.naves.client.NaveRenderer.OCULTA = true);
            at(6, () -> shot("inclina_sobe_oculta_" + tp.toLowerCase()));
            at(1, () -> com.lanterna.naves.client.NaveRenderer.OCULTA = false);
            at(1, () -> { mc.options.keyJump.setDown(false); com.lanterna.client.KeyBindings.NAVE_DESCER.setDown(true); });
            at(40, () -> {});
            at(1, () -> { var n = (com.lanterna.naves.NaveEntity) mc.player.getVehicle(); glog(tp + " descendo pitch=" + n.visPitch(1)); shot("inclina_desce_" + tp.toLowerCase()); });
            at(1, () -> { com.lanterna.client.KeyBindings.NAVE_DESCER.setDown(false); mc.options.keyUp.setDown(false);  });
        }
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }


    private static void sceneCajado() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.renderDistance().set(8); mc.options.hideGui = true;
            cmd("gamerule doDaylightCycle false"); cmd("time set 6000"); cmd("execute in minecraft:overworld run weather clear 100000"); cmd("gamemode creative"); cmd("lanterna anel indigo"); });
        at(10, () -> cmd("execute in minecraft:overworld run tp @s 0 200 0"));
        at(60, () -> server(sp -> {
            var l = sp.serverLevel();
            for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) l.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, 199, z), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
            com.lanterna.power.EquipmentHelper.take(sp, com.lanterna.capability.RingData.BATTERY_SLOT);
            com.lanterna.power.EquipmentHelper.equip(sp, com.lanterna.capability.RingData.BATTERY_SLOT, new net.minecraft.world.item.ItemStack(com.lanterna.registry.ModItems.COMPASSION_STAFF.get()));
            sp.getInventory().clearContent();
            sp.teleportTo(l, 0.5, 200, 0.5, 0, 0);
            glog("bateria=" + com.lanterna.power.EquipmentHelper.battery(sp));
        }));
        at(20, () -> { com.lanterna.client.camera.CameraLanterna.ligada = false; mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); });
        at(30, () -> shot("cajado_costas"));
        at(5, () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        at(20, () -> shot("cajado_costas_frente"));
        // segurando
        at(5, () -> server(sp -> { sp.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(com.lanterna.registry.ModItems.COMPASSION_STAFF.get())); sp.getInventory().selected = 0; }));
        at(5, () -> mc.player.getInventory().selected = 0);
        at(25, () -> shot("cajado_mao_frente"));
        at(5, () -> { mc.player.setYRot(mc.player.getYRot() + 70); });
        at(20, () -> shot("cajado_mao_lado"));
        at(5, () -> { mc.player.setYRot(mc.player.getYRot() - 70); server(sp -> sp.getInventory().clearContent()); });
        // recarga em terceira pessoa
        for (String modo : new String[]{"first", "front"}) {
            at(20, () -> { mc.options.hideGui = modo.equals("front"); mc.options.setCameraType(modo.equals("front") ? net.minecraft.client.CameraType.THIRD_PERSON_FRONT : net.minecraft.client.CameraType.FIRST_PERSON);
                server(sp -> com.lanterna.capability.ModCapabilities.get(sp).ifPresent(d -> { d.setLastPocketRecharge(-100000L); d.setCharge(d.maxCharge() / 4); })); });
            at(10, () -> com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.network.PocketRechargePacket()));
            at(8, () -> shot("cajado_recarga_" + modo + "_a"));
            at(22, () -> shot("cajado_recarga_" + modo + "_b"));
            at(14, () -> shot("cajado_recarga_" + modo + "_c"));
            at(50, () -> server(sp -> com.lanterna.capability.ModCapabilities.get(sp).ifPresent(d -> glog(modo + " carga=" + d.charge() + "/" + d.maxCharge()))));
        }
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }


    private static void sceneFavores() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); cmd("gamemode creative"); cmd("lanterna anel indigo"); });
        at(10, () -> cmd("execute in lanterna:nok run tp @s 0 120 0"));
        waitFor(() -> mc.level.dimension().location().getPath().equals("nok") && mc.screen == null, 900);
        at(200, () -> server(sp -> {
            var l = sp.serverLevel();
            var vs = l.getEntitiesOfClass(com.lanterna.lore.entity.RaceEntity.class, sp.getBoundingBox().inflate(400), r -> r.race() == com.lanterna.lore.entity.Race.VES_KAI);
            glog("ves_kai perto=" + vs.size());
            var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null);
            int ofertas = 0;
            for (var v : vs) {
                String id = com.lanterna.lore.entity.FavoresVesKai.oferta(sp, v, d);
                if (id == null) continue;
                ofertas++;
                var m = com.lanterna.mission.Missions.get(id);
                glog("favor " + id + " titulo=" + m.title().getString() + " | " + m.description().getString());
                com.lanterna.mission.MissionManager.start(sp, id);
            }
            glog("ofertas=" + ofertas + " ativos=" + d.active().keySet());
            int antes = sp.getInventory().countItem(com.lanterna.lore.LoreItems.GRATIDAO.get());
            for (String id : new java.util.ArrayList<>(d.active().keySet())) {
                if (!id.startsWith("vk_")) continue;
                var o = com.lanterna.mission.Missions.get(id).objectives().get(0);
                if (o.type() == com.lanterna.mission.Objective.Type.COLLECT)
                    sp.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(o.target())), o.count()));
            }
            glog("moedas antes=" + antes);
        }));
        at(60, () -> server(sp -> {
            var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null);
            glog("depois: ativos=" + d.active().keySet() + " moedas=" + sp.getInventory().countItem(com.lanterna.lore.LoreItems.GRATIDAO.get()));
            var o = com.lanterna.lore.entity.RaceTrades.class;
        }));
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }


    private static volatile boolean FLAG_T;

    private static void sceneTerra() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.renderDistance().set(10);
            cmd("gamerule doDaylightCycle false"); cmd("execute in minecraft:overworld run weather clear 100000"); cmd("gamemode creative"); cmd("time set 6000"); });
        at(20, () -> server(sp -> glog("centro=" + com.lanterna.terra.TerraSite.centro + " pronta=" + com.lanterna.terra.TerraSite.pronta(sp.server))));
        at(10, () -> server(sp -> { var c = com.lanterna.terra.TerraSite.centro; sp.teleportTo(sp.server.overworld(), c.getX() - 26.5, c.getY() + 18, c.getZ() + 0.5, -90, 20); }));
        waitFor(() -> mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD), 600);
        waitFor(() -> { server(sp -> FLAG_T = com.lanterna.terra.TerraSite.pronta(sp.server)); return FLAG_T; }, 20000);
        at(200, () -> { mc.options.hideGui = true; shot("terra_vila"); });
        at(5, () -> server(sp -> {
            var c = com.lanterna.terra.TerraSite.centro;
            var l = sp.serverLevel();
            var npcs = l.getEntitiesOfClass(com.lanterna.lore.entity.RaceEntity.class, new net.minecraft.world.phys.AABB(c).inflate(60));
            StringBuilder b = new StringBuilder();
            for (var n : npcs) b.append(n.role()).append("/").append(n.getCustomName() == null ? "?" : n.getCustomName().getString()).append(" ");
            glog("npcs=" + npcs.size() + " " + b);
            sp.teleportTo(l, c.getX() + 8.5, c.getY() + 1, c.getZ() + 3.5, -100, 0);
        }));
        at(40, () -> shot("terra_observatorio"));
        // missões
        at(5, () -> server(sp -> {
            var d = com.lanterna.mission.MissionManager.data(sp);
            for (String k : new java.util.ArrayList<>(d.active().keySet())) if (k.startsWith("terra_")) d.active().remove(k);
            d.completed().removeIf(k -> k.startsWith("terra_"));
            for (String f : new String[]{"terra:anel", "terra:ritual", "terra:despertou"}) com.lanterna.fonte.FonteDados.chamado(sp, f, 0);
            com.lanterna.mission.MissionManager.start(sp, "terra_1");
            var rui = sp.serverLevel().getEntitiesOfClass(com.lanterna.lore.entity.RaceEntity.class, sp.getBoundingBox().inflate(80), e -> e.role().equals("seguranca"));
            if (!rui.isEmpty()) com.lanterna.lore.entity.NpcDialogs.open(sp, rui.get(0));
        }));
        at(30, () -> { mc.options.hideGui = false; shot("terra_dialogo_rui"); });
        at(5, () -> { mc.setScreen(null); server(sp -> glog("apos rui ativos=" + com.lanterna.mission.MissionManager.data(sp).active().keySet())); });
        at(5, () -> server(sp -> { var c = com.lanterna.terra.TerraSite.centro; sp.teleportTo(sp.serverLevel(), c.getX() + 20.5, c.getY() - 14, c.getZ() + 9.5, 120, 10); }));
        at(40, () -> { mc.options.hideGui = true; shot("terra_base"); });
        at(5, () -> server(sp -> {
            var liv = sp.serverLevel().getEntitiesOfClass(com.lanterna.lore.entity.RaceEntity.class, sp.getBoundingBox().inflate(40), e -> e.role().equals("cientista"));
            glog("livia perto=" + liv.size());
            if (!liv.isEmpty()) com.lanterna.lore.entity.NpcDialogs.open(sp, liv.get(0));
        }));
        at(30, () -> { mc.options.hideGui = false; shot("terra_dialogo_livia"); });
        at(5, () -> { mc.setScreen(null); server(sp -> glog("apos livia ativos=" + com.lanterna.mission.MissionManager.data(sp).active().keySet())); });
        // meteoro
        at(5, () -> server(sp -> { var c = com.lanterna.terra.TerraSite.centro; sp.teleportTo(sp.serverLevel(), c.getX() - 60.5, c.getY() + 30, c.getZ() + 0.5, -90, 15); }));
        at(5, () -> cmd("time set 18000"));
        at(60, () -> server(sp -> glog("meteoro lancado=" + com.lanterna.terra.Meteoros.lancar(sp))));
        at(30, () -> { mc.options.hideGui = true; shot("terra_meteoro_caindo"); });
        at(40, () -> server(sp -> {
            int n = 0; net.minecraft.core.BlockPos achou = null;
            for (var b : net.minecraft.core.BlockPos.betweenClosed(sp.blockPosition().offset(-100, -60, -100), sp.blockPosition().offset(100, 20, 100)))
                if (sp.serverLevel().getBlockState(b).is(com.lanterna.registry.ModBlocks.ROCHA_ESTELAR.get())) { n++; achou = b.immutable(); }
            glog("rochas=" + n + " em " + achou + " contaminados=" + sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, sp.getBoundingBox().inflate(120), m -> m.getPersistentData().getBoolean("lanterna_contaminado")).size());
            if (achou != null) sp.teleportTo(sp.serverLevel(), achou.getX() + 6.5, achou.getY() + 4, achou.getZ() + 6.5, 135, 30);
        }));
        at(40, () -> shot("terra_cratera"));
        // ritual no altar de ensaio
        at(5, () -> server(sp -> {
            var c = com.lanterna.terra.TerraSite.centro;
            var l = sp.serverLevel();
            for (var m : l.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, sp.getBoundingBox().inflate(150), m -> m.getPersistentData().getBoolean("lanterna_contaminado"))) m.discard();
            var a = c.offset(22, 0, -11);
            l.setBlockAndUpdate(a, com.lanterna.registry.ModBlocks.CHISELED_BATTERY.get().defaultBlockState());
            sp.teleportTo(l, a.getX() + 0.5, a.getY(), a.getZ() + 2.5, 180, 20);
            cmd("gamemode survival"); cmd("effect give @s resistance 600 4 true"); cmd("effect give @s regeneration 600 3 true");
        }));
        at(20, () -> server(sp -> {
            var a = com.lanterna.terra.TerraSite.centro.offset(22, 0, -11);
            var frag = new net.minecraft.world.item.ItemStack(com.lanterna.registry.ModItems.FRAGMENTO_ESTELAR.get(), 2);
            com.lanterna.terra.RitualBateria.iniciar(sp, a, frag);
            glog("ritual em andamento=" + com.lanterna.terra.RitualBateria.emAndamento(a) + " frag=" + frag.getCount());
        }));
        at(200, () -> { mc.options.hideGui = false; shot("terra_ritual"); });
        at(1, () -> FLAG_T = false);
        waitFor(() -> false, 1050);
        at(1, () -> server(sp -> glog("ritual 1050 ticks: carga=" + sp.serverLevel().getBlockState(com.lanterna.terra.TerraSite.centro.offset(22, 0, -11)).getValue(com.lanterna.block.ChiseledBatteryBlock.CARGA)
                + " andamento=" + com.lanterna.terra.RitualBateria.emAndamento(com.lanterna.terra.TerraSite.centro.offset(22, 0, -11)))));
        waitFor(() -> false, 200);
        at(5, () -> mc.options.keyShift.setDown(true));
        at(130, () -> { mc.options.keyShift.setDown(false); shot("terra_ritual_fim"); server(sp -> {
            var a = com.lanterna.terra.TerraSite.centro.offset(22, 0, -11);
            glog("ritual fim: carga=" + sp.serverLevel().getBlockState(a).getValue(com.lanterna.block.ChiseledBatteryBlock.CARGA) + " flag=" + com.lanterna.fonte.FonteDados.chamado(sp, "terra:ritual") + " vivo=" + sp.isAlive());
        }); });
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }


    private static void sceneAura() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.hideGui = true;
            cmd("gamerule doDaylightCycle false"); cmd("time set 6000"); cmd("execute in minecraft:overworld run weather clear 100000"); cmd("gamemode creative"); cmd("lanterna anel green"); });
        at(10, () -> cmd("execute in minecraft:overworld run tp @s 0 200 0"));
        at(60, () -> server(sp -> {
            var l = sp.serverLevel();
            for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
                l.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, 199, z), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
                for (int y = 200; y < 206; y++) l.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
            com.lanterna.capability.ModCapabilities.get(sp).ifPresent(d -> { if (!d.powerActive()) com.lanterna.power.RingPowers.togglePower(sp); });
            for (var n : l.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, sp.getBoundingBox().inflate(60), e -> !(e instanceof net.minecraft.world.entity.player.Player))) n.discard();
            for (int x = -30; x <= 30; x++) for (int z = -30; z <= 30; z++) for (int y = 200; y < 230; y++)
                if (!l.getBlockState(new net.minecraft.core.BlockPos(x, y, z)).isAir()) l.setBlock(new net.minecraft.core.BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            sp.teleportTo(l, 0.5, 200, 0.5, 0, 0);
        }));
        at(20, () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        at(20, () -> shot("aura_1"));
        at(2, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); server(sp -> sp.teleportTo(sp.serverLevel(), 0.5, 200, 0.5, 180, 10)); });
        at(10, () -> shot("aura_costas"));
        at(2, () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        at(4, () -> shot("aura_2"));
        at(4, () -> shot("aura_3"));
        at(2, () -> server(sp -> sp.teleportTo(sp.serverLevel(), 0.5, 200, 0.5, 40, 0)));
        at(6, () -> shot("aura_lado"));
        at(2, () -> cmd("time set 18000"));
        at(30, () -> shot("aura_noite"));
        at(5, () -> shot("aura_noite2"));
    }

    private static void sceneMogo() {
        Minecraft mc = Minecraft.getInstance();
        int[] alvoId = {-1};
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.renderDistance().set(12); mc.options.hideGui = true;
            cmd("gamerule doDaylightCycle false"); cmd("gamerule doMobSpawning false"); cmd("time set 6000"); cmd("execute in minecraft:overworld run weather clear 100000");
            cmd("gamemode creative"); cmd("lanterna anel green"); });
        at(10, () -> cmd("execute in minecraft:overworld run tp @s 0 200 0"));
        at(60, () -> server(sp -> {
            var l = sp.serverLevel();
            for (int x = -40; x <= 40; x++) for (int z = -40; z <= 40; z++) {
                for (int y = 190; y < 200; y++) l.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y, z), y == 199 ? net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState() : net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                for (int y = 200; y < 214; y++) l.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
            for (var z : l.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, sp.getBoundingBox().inflate(80))) z.discard();
            com.lanterna.capability.ModCapabilities.get(sp).ifPresent(d -> { if (!d.powerActive()) com.lanterna.power.RingPowers.togglePower(sp); });
            sp.teleportTo(l, 0.5, 200, 0.5, 0, 0);
        }));
        // aura de fumaça (de frente)
        at(30, () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        at(30, () -> server(sp -> sp.teleportTo(sp.serverLevel(), 0.5, 200, 0.5, 0, 0)));
        at(30, () -> shot("mogo_aura_dia"));
        at(2, () -> server(sp -> sp.teleportTo(sp.serverLevel(), 0.5, 200, 0.5, 30, 0)));
        at(6, () -> shot("mogo_aura_lado"));
        at(2, () -> cmd("time set 18000"));
        at(30, () -> shot("mogo_aura_noite"));
        at(2, () -> { cmd("time set 6000"); mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); });
        // a bateria com o Selo e o alvo
        at(10, () -> server(sp -> {
            var l = sp.serverLevel();
            var bat = com.lanterna.item.PowerBatteryItem.create(com.lanterna.corps.BuiltinCorps.GREEN.id(), com.lanterna.corps.BuiltinCorps.GREEN.color());
            com.lanterna.mogo.JadeViva.definir(bat, 3);
            sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bat);
            var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(l);
            z.moveTo(0.5, 200, 22.5, 180, 0);
            z.setNoAi(true);
            z.setPersistenceRequired();
            l.addFreshEntity(z);
            alvoId[0] = z.getId();
            sp.teleportTo(l, 0.5, 200, 0.5, 0, 1);
            com.lanterna.capability.ModCapabilities.get(sp).ifPresent(d -> com.lanterna.power.RingPowers.setCharge(sp, d, d.maxCharge()));
            glog("bateria jade=" + com.lanterna.mogo.JadeViva.nivel(sp.getMainHandItem()) + " carga=" + com.lanterna.capability.ModCapabilities.get(sp).map(d -> d.charge() + "/" + d.maxCharge()).orElse("?"));
        }));
        at(10, () -> server(sp -> {
            sp.getPersistentData().remove(com.lanterna.mogo.ChamadoMogo.TAG_ULTIMO);
            boolean ok = com.lanterna.mogo.ChamadoMogo.usar(sp, sp.getMainHandItem());
            glog("chamado usar=" + ok + " restante=" + com.lanterna.mogo.ChamadoMogo.restante(sp) + " carga=" + com.lanterna.capability.ModCapabilities.get(sp).map(d -> d.charge() + "/" + d.maxCharge()).orElse("?"));
        }));
        at(4, () -> shot("mogo_jaula"));
        at(26, () -> shot("mogo_chega"));
        at(30, () -> shot("mogo_carga"));
        at(22, () -> shot("mogo_raio"));
        at(4, () -> shot("mogo_impacto"));
        at(10, () -> shot("mogo_raio2"));
        at(30, () -> server(sp -> {
            var e = sp.serverLevel().getEntity(alvoId[0]);
            glog("alvo vivo=" + (e != null && e.isAlive()) + " bloco no poco=" + sp.serverLevel().getBlockState(new net.minecraft.core.BlockPos(0, 195, 22)));
            boolean ok = com.lanterna.mogo.ChamadoMogo.usar(sp, sp.getMainHandItem());
            glog("segundo uso restante=" + com.lanterna.mogo.ChamadoMogo.restante(sp));
        }));
        at(5, () -> cmd("lanterna mogo resetar"));
        at(5, () -> server(sp -> glog("depois do reset restante=" + com.lanterna.mogo.ChamadoMogo.restante(sp))));
        at(5, () -> server(sp -> sp.teleportTo(sp.serverLevel(), 8.5, 203, 12.5, 150, 35)));
        at(20, () -> shot("mogo_cratera"));
        at(5, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); });
    }


    private static com.lanterna.mogo.MogoMundo.Dados md(net.minecraft.server.level.ServerPlayer sp) {
        return com.lanterna.mogo.MogoMundo.dados(sp.server.getLevel(com.lanterna.mogo.MogoMundo.DIM));
    }

    private static void tpm(net.minecraft.server.level.ServerPlayer sp, double x, double y, double z, float yaw, float pitch) {
        sp.teleportTo(sp.server.getLevel(com.lanterna.mogo.MogoMundo.DIM), x, y, z, yaw, pitch);
    }

    private static String flag(net.minecraft.server.level.ServerPlayer sp, String f) {
        return f + "=" + com.lanterna.fonte.FonteDados.chamado(sp, f);
    }

    private static void sceneMogoPlaneta() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.renderDistance().set(10); mc.options.hideGui = false;
            cmd("gamerule doDaylightCycle false"); cmd("gamerule doMobSpawning false"); cmd("time set 6000");
            cmd("gamemode creative"); cmd("lanterna anel green"); });
        // 1. cinturão
        at(20, () -> cmd("lanterna mogo missao 1"));
        at(10, () -> server(sp -> {
            var mogo = com.lanterna.space.SpaceBodies.byId("mogo");
            var space = sp.server.getLevel(com.lanterna.space.SpaceBodies.SPACE);
            var p = mogo.center().add(mogo.radius() + 120, 0, 0);
            sp.teleportTo(space, p.x, p.y, p.z, 90, 0);
            com.lanterna.capability.ModCapabilities.get(sp).ifPresent(d -> { if (!d.powerActive()) com.lanterna.power.RingPowers.togglePower(sp); });
        }));
        at(120, () -> shot("mp_cinturao"));
        at(2, () -> server(sp -> glog("rochas=" + sp.serverLevel().getEntitiesOfClass(com.lanterna.mogo.RochaMogo.class, sp.getBoundingBox().inflate(120)).size())));
        // pousar
        at(10, () -> cmd("lanterna mogo ir"));
        at(400, () -> server(sp -> { var d = md(sp); glog("pronto=" + d.pronto + " pouso=" + d.pouso + " dim=" + sp.level().dimension().location() + " " + flag(sp, "mogo:cinturao")); }));
        at(10, () -> server(sp -> { var d = md(sp); tpm(sp, d.pouso.getX() + 14.5, d.pouso.getY() + 6, d.pouso.getZ() + 14.5, 135, 15); }));
        at(40, () -> shot("mp_pouso"));
        // 2. corrida
        at(5, () -> cmd("lanterna mogo missao 2"));
        at(5, () -> server(sp -> { var d = md(sp); tpm(sp, d.pouso.getX() + 0.5, d.pouso.getY() + 1, d.pouso.getZ() + 3.5, -90, 0); }));
        at(10, () -> server(sp -> tpm(sp, 60, sp.getY() + 14, 14, 60, 30)));
        at(60, () -> shot("mp_corrida"));
        at(5, () -> server(sp -> {
            var l = sp.server.getLevel(com.lanterna.mogo.MogoMundo.DIM);
            int n = 0;
            for (int x = 30; x < 340; x++) for (int z = -8; z <= 8; z++) for (int y = 40; y < 200; y++) {
                var b = new net.minecraft.core.BlockPos(x, y, z);
                if (l.getBlockState(b).is(com.lanterna.mogo.MogoBlocos.FOCO.get())) { l.setBlock(b, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3); n++; }
            }
            glog("focos quebrados=" + n);
        }));
        at(20, () -> server(sp -> glog(flag(sp, "mogo:corrida"))));
        // 3. ponte
        at(5, () -> cmd("lanterna mogo missao 3"));
        at(5, () -> server(sp -> { var d = md(sp); tpm(sp, d.ponteIni.getX() + 0.5, d.ponteIni.getY(), d.ponteIni.getZ() + 0.5, 0, 10); }));
        at(70, () -> server(sp -> { var d = md(sp); tpm(sp, d.ponteIni.getX() + 6.5, d.ponteY + 5, d.ponteZ0 + 30, 160, 25); }));
        at(20, () -> shot("mp_ponte"));
        at(5, () -> server(sp -> { var d = md(sp); tpm(sp, d.ponteFim.getX() + 0.5, d.ponteY, d.ponteFim.getZ() + 0.5, 0, 0); }));
        at(10, () -> server(sp -> glog(flag(sp, "mogo:ponte") + " mayfly=" + sp.getAbilities().mayfly)));
        at(2, () -> cmd("gamemode creative"));
        // 4. prova
        at(5, () -> cmd("lanterna mogo missao 4"));
        at(5, () -> server(sp -> { var d = md(sp); tpm(sp, d.arena.getX() + 0.5, d.arena.getY() + 1, d.arena.getZ() + 0.5, 0, 0); }));
        at(10, () -> cmd("gamemode survival"));
        at(60, () -> server(sp -> { var d = md(sp); tpm(sp, d.arena.getX() + 0.5, d.arena.getY() + 1, d.arena.getZ() + 12.5, 180, 10); }));
        at(40, () -> shot("mp_prova"));
        at(5, () -> server(sp -> { sp.hurt(sp.damageSources().generic(), 500); glog("prova nao morre vivo=" + sp.isAlive() + " hp=" + sp.getHealth()); }));
        at(5, () -> server(sp -> {
            int n = 0;
            for (var o : sp.serverLevel().getEntitiesOfClass(com.lanterna.mogo.OlhoMogo.class, sp.getBoundingBox().inflate(60))) { o.kill(); n++; }
            glog("olhos=" + n);
        }));
        at(10, () -> server(sp -> glog(flag(sp, "mogo:prova"))));
        at(2, () -> cmd("gamemode creative"));
        // 5. cerco
        at(5, () -> cmd("lanterna mogo missao 5"));
        at(5, () -> server(sp -> { var d = md(sp); tpm(sp, d.torres[1].getX() + 12.5, d.torres[1].getY() + 4, d.torres[1].getZ() + 12.5, 135, 20); }));
        at(100, () -> shot("mp_cerco"));
        for (int i = 0; i < 4; i++) at(160, () -> server(sp -> {
            int n = 0;
            for (var e : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, sp.getBoundingBox().inflate(150),
                    e -> e instanceof com.lanterna.especies.LanternaEntity)) { e.kill(); n++; }
            for (var e : sp.serverLevel().getEntitiesOfClass(com.lanterna.naves.NaveEntity.class, sp.getBoundingBox().inflate(150))) { e.discard(); n++; }
            glog("cerco mortos=" + n + " " + flag(sp, "mogo:cerco"));
        }));
        // 6. núcleo
        at(5, () -> cmd("lanterna mogo missao 6"));
        at(5, () -> server(sp -> { var d = md(sp); tpm(sp, d.fenda.getX() + 0.5, d.fenda.getY() - 20, d.fenda.getZ() + 0.5, 0, 60); }));
        at(30, () -> shot("mp_fenda"));
        at(5, () -> server(sp -> { var d = md(sp); tpm(sp, d.camara.getX() + 9.5, d.camara.getY() + 1, d.camara.getZ() + 0.5, 90, -10); }));
        at(40, () -> shot("mp_ferida"));
        at(5, () -> server(sp -> {
            var f = sp.serverLevel().getEntitiesOfClass(com.lanterna.mogo.Ferida.class, sp.getBoundingBox().inflate(40));
            glog("feridas=" + f.size());
            for (var x : f) x.hurt(sp.damageSources().playerAttack(sp), 300);
        }));
        at(20, () -> server(sp -> {
            var bat = com.lanterna.item.PowerBatteryItem.create(com.lanterna.corps.BuiltinCorps.GREEN.id(), com.lanterna.corps.BuiltinCorps.GREEN.color());
            sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bat);
            var f = sp.serverLevel().getEntitiesOfClass(com.lanterna.mogo.Ferida.class, sp.getBoundingBox().inflate(40));
            glog("casulo=" + (f.isEmpty() ? "?" : f.get(0).casulo) + " chamado=" + com.lanterna.mogo.MogoMissoes.tentarChamadoNucleo(sp));
        }));
        at(30, () -> shot("mp_chamado1"));
        at(52, () -> shot("mp_chamado2"));
        at(40, () -> server(sp -> glog(flag(sp, "mogo:nucleo") + " " + flag(sp, "mogo:vinculo") + " feridas=" + sp.serverLevel().getEntitiesOfClass(com.lanterna.mogo.Ferida.class, sp.getBoundingBox().inflate(80)).size())));
        // Segunda Chance
        at(20, () -> cmd("execute in minecraft:overworld run tp @s 0 120 0"));
        at(60, () -> { cmd("gamemode survival"); cmd("lanterna mogo segunda"); });
        at(10, () -> server(sp -> { sp.hurt(sp.damageSources().generic(), 500); glog("segunda vivo=" + sp.isAlive()); }));
        at(70, () -> shot("mp_segunda1"));
        at(30, () -> shot("mp_segunda2"));
        at(40, () -> server(sp -> glog("segunda dim=" + sp.level().dimension().location() + " restante=" + com.lanterna.mogo.SegundaChance.restante(sp))));
        at(60, () -> shot("mp_segunda_chegou"));
        // inimigo não entra
        at(10, () -> { cmd("gamemode creative"); cmd("lanterna anel yellow"); });
        at(10, () -> cmd("gamemode survival"));
        at(30, () -> server(sp -> glog("amarelo dim=" + sp.level().dimension().location())));
        at(5, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); });
    }


    private static void sceneNaves3() {
        Minecraft mc = Minecraft.getInstance();
        int[] nave = {-1};
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.renderDistance().set(8);
            cmd("gamerule doDaylightCycle false"); cmd("gamerule doMobSpawning false"); cmd("time set 6000"); cmd("execute in minecraft:overworld run weather clear 100000");
            cmd("gamemode survival"); cmd("lanterna anel green"); });
        at(10, () -> cmd("execute in minecraft:overworld run tp @s 0 200 0"));
        at(60, () -> server(sp -> {
            var l = sp.serverLevel();
            for (int x = -40; x <= 40; x++) for (int z = -40; z <= 40; z++) {
                for (int y = 190; y < 200; y++) l.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y, z), y == 199 ? net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState() : net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                for (int y = 200; y < 224; y++) l.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
            // um degrau para testar terreno irregular
            for (int x = 2; x <= 6; x++) for (int z = 4; z <= 8; z++) l.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, 200, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            sp.teleportTo(l, 0.5, 200, 0.5, 0, 0);
        }));
        for (String tipo : new String[]{"NAVE_LANCEIRA", "NAVE_PATRULHA", "NAVE_PIRATA"}) for (float yaw : new float[]{0f, 45f}) {
            at(20, () -> server(sp -> {
                var l = sp.serverLevel();
                for (var n : l.getEntitiesOfClass(com.lanterna.naves.NaveEntity.class, sp.getBoundingBox().inflate(80))) n.discard();
                sp.teleportTo(l, 0.5, 200, 0.5, yaw, 30);
                var t = com.lanterna.naves.NaveTipo.valueOf(tipo);
                var stack = com.lanterna.naves.NaveItem.of(t, "green");
                sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
                var hit = new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(0.5, 200, 2.5), net.minecraft.core.Direction.UP, new net.minecraft.core.BlockPos(0, 199, 2), false);
                var r = stack.useOn(new net.minecraft.world.item.context.UseOnContext(sp, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
                var ns = l.getEntitiesOfClass(com.lanterna.naves.NaveEntity.class, sp.getBoundingBox().inflate(80));
                if (ns.isEmpty()) { glog(tipo + " yaw" + yaw + " colocar=" + r + " (nenhuma)"); return; }
                var n = ns.get(0);
                nave[0] = n.getId();
                glog(tipo + " yaw" + yaw + " colocar=" + r + " nave=" + String.format("%.1f %.1f %.1f", n.getX(), n.getY(), n.getZ()) + " jogadorPreso=" + n.bateNaNave(sp.getBoundingBox()));
            }));
            at(10, () -> { mc.getEntityRenderDispatcher().setRenderHitBoxes(true); mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); });
            at(10, () -> server(sp -> {
                var n = (com.lanterna.naves.NaveEntity) sp.serverLevel().getEntity(nave[0]);
                double a = Math.toRadians(yaw + 150);
                double r = n.alcance() + 9;
                tpm2(sp, n.getX() - Math.sin(a) * r, n.getY() + 6, n.getZ() + Math.cos(a) * r, n);
            }));
            at(15, () -> shot("n3_" + tipo.toLowerCase() + "_" + (int) yaw));
            // entrar e sair no chão
            at(5, () -> server(sp -> {
                var n = sp.serverLevel().getEntity(nave[0]);
                sp.startRiding(n, true);
            }));
            at(10, () -> server(sp -> {
                var n = (com.lanterna.naves.NaveEntity) sp.serverLevel().getEntity(nave[0]);
                sp.stopRiding();
                glog(tipo + " yaw" + yaw + " saiu chao em " + String.format("%.1f %.1f %.1f", sp.getX(), sp.getY(), sp.getZ()) + " naveY=" + String.format("%.1f", n.getY())
                        + " preso=" + n.bateNaNave(sp.getBoundingBox()) + " blocos=" + !sp.serverLevel().noCollision(sp));
            }));
            // entrar, subir 6 blocos (pairando) e sair
            at(5, () -> server(sp -> {
                var n = sp.serverLevel().getEntity(nave[0]);
                sp.startRiding(n, true);
            }));
            at(10, () -> server(sp -> {
                var n = (com.lanterna.naves.NaveEntity) sp.serverLevel().getEntity(nave[0]);
                n.setPos(n.getX(), n.getY() + 8, n.getZ());
                double ny = n.getY();
                sp.stopRiding();
                glog(tipo + " yaw" + yaw + " saiu no ar em " + String.format("%.1f %.1f %.1f", sp.getX(), sp.getY(), sp.getZ()) + " naveY=" + String.format("%.1f", ny)
                        + " embaixo=" + (sp.getY() < ny - 0.5 && Math.abs(sp.getX() - n.getX()) < n.alcance() && Math.abs(sp.getZ() - n.getZ()) < n.alcance()) + " preso=" + n.bateNaNave(sp.getBoundingBox()));
            }));
        }
        // Odym: acha uma ilha pelo gerador (sem gerar o mapa todo) e testa os Ecos subindo do mar
        int[] ilhaXZ = {0, 0, 0};
        at(20, () -> server(sp -> {
            var odym = sp.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, com.lanterna.LanternaMod.id("odym")));
            var gen = odym.getChunkSource().getGenerator();
            var rs = odym.getChunkSource().randomState();
            int mar = gen.getSeaLevel();
            outer:
            for (int r = 200; r < 3000; r += 40) for (int k = 0; k < 64; k++) {
                double a = k * Math.PI * 2 / 64;
                int x = (int) (Math.cos(a) * r), z = (int) (Math.sin(a) * r);
                int h = gen.getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, odym, rs);
                if (h > mar + 4) { ilhaXZ[0] = x; ilhaXZ[1] = z; ilhaXZ[2] = h; break outer; }
            }
            glog("odym mar=" + mar + " ilha em " + ilhaXZ[0] + "," + ilhaXZ[1] + " h=" + ilhaXZ[2]);
            int sub = 0, ok = 0, agua = 0;
            for (int i = 0; i < 3000; i++) {
                int x = ilhaXZ[0] + odym.random.nextInt(161) - 80, z = ilhaXZ[1] + odym.random.nextInt(161) - 80;
                odym.getChunk(x >> 4, z >> 4);
                var p = new net.minecraft.core.BlockPos(x, odym.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, x, z), z);
                boolean w = !odym.getFluidState(p).isEmpty();
                boolean pode = com.lanterna.lore.world.ZonasDeNascimento.ecoPodeNascer(odym, p, net.minecraft.world.entity.MobSpawnType.NATURAL, odym.random);
                if (w) agua++;
                if (pode && w) sub++;
                if (pode && !w) ok++;
            }
            glog("odym perto da ilha: agua=" + agua + " podeNaAgua=" + sub + " podeNaIlha=" + ok);
            sp.teleportTo(odym, ilhaXZ[0] + 0.5, ilhaXZ[2] + 2, ilhaXZ[1] + 0.5, 0, 20);
        }));
        at(60, () -> { cmd("gamemode survival"); cmd("difficulty normal"); });
        at(20, () -> server(sp -> {
            int total = 0;
            for (int i = 0; i < 40; i++) total += com.lanterna.lore.world.EcosOdym.tentar(sp);
            var ecos = sp.serverLevel().getEntitiesOfClass(com.lanterna.lore.entity.LoreCreatures.Eco.class, sp.getBoundingBox().inflate(60));
            StringBuilder sb = new StringBuilder();
            for (var e : ecos) sb.append(String.format(" [%.0f %.0f %.0f agua=%s chao=%s]", e.getX(), e.getY(), e.getZ(), e.isInWater(),
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(sp.serverLevel().getBlockState(e.blockPosition().below()).getBlock()).getPath()));
            glog("ecos odym nasceram=" + total + " perto=" + ecos.size() + sb);
            if (!ecos.isEmpty()) { var e = ecos.get(0); tpm3(sp, e); }
        }));
        at(30, () -> shot("n3_odym_eco"));
        at(5, () -> cmd("gamemode creative"));
        at(5, () -> { cmd("lanterna laranja"); cmd("lanterna laranja varios"); });
        at(5, () -> server(sp -> glog("laranja unico=" + com.lanterna.selection.OrangeClaim.get(sp.server).unico() + " taken=" + com.lanterna.selection.OrangeClaim.get(sp.server).isTaken())));
        at(5, () -> cmd("lanterna laranja unico"));
        at(5, () -> server(sp -> glog("laranja unico=" + com.lanterna.selection.OrangeClaim.get(sp.server).unico())));
        at(5, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); });
    }

    private static void tpm3(net.minecraft.server.level.ServerPlayer sp, net.minecraft.world.entity.Entity alvo) {
        sp.teleportTo(sp.serverLevel(), alvo.getX() + 6, alvo.getY() + 3, alvo.getZ() + 6, 135, 20);
    }

    private static void tpm2(net.minecraft.server.level.ServerPlayer sp, double x, double y, double z, net.minecraft.world.entity.Entity alvo) {
        double dx = alvo.getX() - x, dy = alvo.getY() + 1 - y, dz = alvo.getZ() - z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        sp.teleportTo(sp.serverLevel(), x, y, z, yaw, pitch);
    }

    private static void sceneEscolha() {
        Minecraft mc = Minecraft.getInstance();
        String only = System.getProperty("lanterna.tropas", "green,red,yellow,blue,violet,indigo,orange,black,white");
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.renderDistance().set(8); mc.options.hideGui = true;
            cmd("gamerule doDaylightCycle false"); cmd("gamerule doMobSpawning false"); cmd("time set 6000"); cmd("execute in minecraft:overworld run weather clear 100000"); cmd("gamemode creative"); });
        at(10, () -> cmd("execute in minecraft:overworld run tp @s 0 200 0"));
        at(60, () -> server(sp -> {
            var l = sp.serverLevel();
            for (int x = -30; x <= 30; x++) for (int z = -30; z <= 30; z++) {
                l.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, 199, z), (x * 7 + z * 13) % 11 == 0 ? net.minecraft.world.level.block.Blocks.MOSS_BLOCK.defaultBlockState() : net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
                for (int y = 200; y < 210; y++) l.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
            for (var z : l.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, sp.getBoundingBox().inflate(80))) z.discard();
            com.lanterna.power.RingPowers.removeRing(sp);
            sp.teleportTo(l, 0.5, 200, 0.5, 0, 0);
        }));
        for (String tropa : only.split(",")) {
            at(10, () -> server(sp -> {
                com.lanterna.capability.ModCapabilities.get(sp).ifPresent(d -> { d.clearPending(); });
                com.lanterna.power.RingPowers.removeRing(sp);
                sp.teleportTo(sp.serverLevel(), 0.5, 200, 0.5, 0, 0);
                sp.removeAllEffects();
                boolean ok = com.lanterna.selection.SelectionManager.startSelection(sp, new net.minecraft.resources.ResourceLocation("lanterna", tropa));
                glog("escolha " + tropa + " ok=" + ok);
            }));
            at(50, () -> { glog(tropa + " shot1 yaw=" + mc.player.getYRot() + " body=" + mc.player.yBodyRot + " head=" + mc.player.yHeadRot + " cam=" + mc.gameRenderer.getMainCamera().getYRot()); shot("escolha_" + tropa + "_1"); });
            at(60, () -> { glog(tropa + " shot2 yaw=" + mc.player.getYRot() + " body=" + mc.player.yBodyRot + " head=" + mc.player.yHeadRot + " cam=" + mc.gameRenderer.getMainCamera().getYRot()); shot("escolha_" + tropa + "_2"); });
            at(46, () -> { glog(tropa + " shot3 yaw=" + mc.player.getYRot() + " body=" + mc.player.yBodyRot + " head=" + mc.player.yHeadRot + " cam=" + mc.gameRenderer.getMainCamera().getYRot()); shot("escolha_" + tropa + "_3"); });
            at(22, () -> { glog(tropa + " shot4 yaw=" + mc.player.getYRot() + " body=" + mc.player.yBodyRot + " head=" + mc.player.yHeadRot + " cam=" + mc.gameRenderer.getMainCamera().getYRot()); shot("escolha_" + tropa + "_4"); });
            at(14, () -> { glog(tropa + " shot5 yaw=" + mc.player.getYRot() + " body=" + mc.player.yBodyRot + " head=" + mc.player.yHeadRot + " cam=" + mc.gameRenderer.getMainCamera().getYRot()); shot("escolha_" + tropa + "_5"); });
            at(2, () -> server(sp -> com.lanterna.capability.ModCapabilities.get(sp).ifPresent(d -> d.clearPending())));
            at(20, () -> {});
        }
        at(5, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); });
    }

    private static void sceneEnergia() {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.core.BlockPos MIN = new net.minecraft.core.BlockPos(0, 200, 10), CONV = MIN.east(), GER = new net.minecraft.core.BlockPos(-7, 200, 3), REF = GER.east();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.renderDistance().set(8);
            cmd("gamerule doDaylightCycle false"); cmd("gamerule doMobSpawning false"); cmd("time set 6000"); cmd("execute in minecraft:overworld run weather clear 100000"); cmd("gamemode creative"); });
        at(10, () -> cmd("execute in minecraft:overworld run tp @s 0 200 0"));
        at(60, () -> server(sp -> {
            var l = sp.serverLevel();
            for (int x = -24; x <= 24; x++) for (int z = -20; z <= 34; z++) {
                l.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, 199, z), (x + z) % 7 == 0 ? net.minecraft.world.level.block.Blocks.COARSE_DIRT.defaultBlockState() : net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
                for (int y = 200; y < 214; y++) l.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
            for (var z : l.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, sp.getBoundingBox().inflate(80))) z.discard();
            var E = com.lanterna.energia.EnergiaRegistro.class;
            l.setBlockAndUpdate(MIN, com.lanterna.energia.EnergiaRegistro.MINERADORA.get().defaultBlockState().setValue(com.lanterna.energia.MaquinaBlock.FACING, net.minecraft.core.Direction.NORTH));
            l.setBlockAndUpdate(CONV, com.lanterna.energia.EnergiaRegistro.CONVERSOR.get().defaultBlockState().setValue(com.lanterna.energia.MaquinaBlock.FACING, net.minecraft.core.Direction.NORTH));
            l.setBlockAndUpdate(GER, com.lanterna.energia.EnergiaRegistro.GERADOR.get().defaultBlockState().setValue(com.lanterna.energia.GeradorEmocionalBlock.EMOCAO, com.lanterna.baterias.Tropa.VERMELHO));
            l.setBlockAndUpdate(REF, com.lanterna.energia.EnergiaRegistro.REFINARIA.get().defaultBlockState());
            if (l.getBlockEntity(MIN) instanceof com.lanterna.energia.MineradoraBE m) m.instalar("", l.getGameTime());
            if (l.getBlockEntity(CONV) instanceof com.lanterna.energia.ConversorBE c)
                c.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).ifPresent(h -> h.insertItem(0, new net.minecraft.world.item.ItemStack(com.lanterna.energia.EnergiaRegistro.NUCLEO.get(), 8), false));
            if (l.getBlockEntity(REF) instanceof com.lanterna.energia.RefinariaBE r)
                r.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).ifPresent(h -> h.insertItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE, 16), false));
            sp.teleportTo(l, -3.5, 203.0, -3.5, -20, 12);
        }));
        at(2, () -> mc.options.hideGui = true);
        at(26, () -> shot("energia_montando"));
        at(100, () -> server(sp -> {
            var l = sp.serverLevel();
            if (l.getBlockEntity(MIN) instanceof com.lanterna.energia.MineradoraBE m) glog("mineradora estado=" + m.estado + " energia=" + m.energia() + " progresso=" + m.progresso + " ciclo=" + m.ciclo());
            if (l.getBlockEntity(CONV) instanceof com.lanterna.energia.ConversorBE c) glog("conversor energia=" + c.energia());
        }));
        at(30, () -> shot("energia_mineradora"));
        at(37, () -> shot("energia_mineradora2"));
        at(2, () -> server(sp -> sp.teleportTo(sp.serverLevel(), -6.5, 204.5, 0.5, -122, 18)));
        at(20, () -> shot("energia_lateral"));
        at(2, () -> server(sp -> sp.teleportTo(sp.serverLevel(), 7.5, 202.5, 4.5, 40, 10)));
        at(20, () -> shot("energia_braco"));
        at(2, () -> server(sp -> sp.teleportTo(sp.serverLevel(), -8.5, 201.6, -1.5, -15, 25)));
        // gerador de raiva: criaturas morrendo perto
        at(5, () -> server(sp -> {
            var l = sp.serverLevel();
            for (int i = 0; i < 4; i++) {
                var z = net.minecraft.world.entity.EntityType.PIG.create(l);
                z.moveTo(GER.getX() + 2.5, 200, GER.getZ() + 0.5 + i, 0, 0);
                l.addFreshEntity(z);
                z.kill();
            }
        }));
        at(45, () -> server(sp -> {
            var l = sp.serverLevel();
            if (l.getBlockEntity(GER) instanceof com.lanterna.energia.GeradorEmocionalBE g) glog("gerador raiva mb=" + g.quantidade() + " ritmo=" + g.ritmo);
            if (l.getBlockEntity(REF) instanceof com.lanterna.energia.RefinariaBE r) { glog("refinaria recebeu=" + r.quantidade()); r.encher(2000); }
        }));
        at(15, () -> shot("energia_gerador"));
        at(340, () -> server(sp -> {
            var l = sp.serverLevel();
            if (l.getBlockEntity(REF) instanceof com.lanterna.energia.RefinariaBE r)
                r.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).ifPresent(h -> glog("refinaria cristais=" + h.getStackInSlot(1).getCount() + " mb=" + r.quantidade()));
            if (l.getBlockEntity(MIN) instanceof com.lanterna.energia.MineradoraBE m) { glog("antes offline essencias=" + m.essencias() + " energia=" + m.energia()); m.fingirAusencia(3_600_000L); }
        }));
        at(5, () -> server(sp -> {
            var l = sp.serverLevel();
            if (l.getBlockEntity(MIN) instanceof com.lanterna.energia.MineradoraBE m) glog("depois de 1h offline essencias=" + m.essencias() + " energia=" + m.energia());
        }));
        // ataque (à noite, para os zumbis não queimarem)
        at(5, () -> { cmd("time set 18000"); server(sp -> com.lanterna.energia.MineradoraAtaques.ataque(sp.serverLevel(), MIN)); });
        at(400, () -> server(sp -> {
            var l = sp.serverLevel();
            long n = l.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(MIN).inflate(30), e -> e.getPersistentData().contains(com.lanterna.energia.MineradoraAtaques.TAG)).size();
            if (l.getBlockEntity(MIN) instanceof com.lanterna.energia.MineradoraBE m) glog("ataque: atacantes=" + n + " integridade=" + m.integridade + " estado=" + m.estado);
            sp.teleportTo(l, -4.5, 203.5, -5.5, -16, 14);
        }));
        at(20, () -> shot("energia_ataque"));
        at(5, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); });
    }

    private static void sceneOficina() {
        Minecraft mc = Minecraft.getInstance();
        String[] mods = {"espada", "escudo", "flecha", "martelo", "carro", "muralha"};
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.renderDistance().set(8);
            cmd("gamerule doDaylightCycle false"); cmd("time set 6000"); cmd("execute in minecraft:overworld run weather clear 100000"); cmd("gamemode creative"); cmd("lanterna anel green"); });
        at(10, () -> cmd("execute in minecraft:overworld run tp @s 0 200 0"));
        at(60, () -> server(sp -> {
            var l = sp.serverLevel();
            for (int x = -20; x <= 20; x++) for (int z = -20; z <= 30; z++) l.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, 199, z), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
            sp.teleportTo(l, 0.5, 200, 0.5, 0, 0);
            com.lanterna.capability.ModCapabilities.get(sp).ifPresent(d -> { if (!d.powerActive()) com.lanterna.power.RingPowers.togglePower(sp); });
            for (var z : l.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, sp.getBoundingBox().inflate(60))) z.discard();
        }));
        for (int i = 0; i < mods.length; i++) {
            int s = i;
            at(2, () -> { var p = com.lanterna.construct.oficina.Modelos.TODOS.get(mods[s]).get(); p.nome = mods[s];
                com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.construct.oficina.MeusConstrutos.Salvar(s, p)); });
        }
        // editor
        at(20, () -> { var t = new com.lanterna.client.oficina.OficinaScreen(); mc.setScreen(t); });
        at(20, () -> shot("oficina_editor_espada"));
        at(2, () -> { if (mc.screen instanceof com.lanterna.client.oficina.OficinaScreen o) o.carregar(com.lanterna.construct.oficina.Modelos.TODOS.get("carro").get()); });
        at(10, () -> shot("oficina_editor_carro"));
        at(2, () -> { if (mc.screen instanceof com.lanterna.client.oficina.OficinaScreen o) { o.carregar(com.lanterna.construct.oficina.Modelos.TODOS.get("martelo").get()); o.testar(true); } });
        at(12, () -> shot("oficina_editor_teste"));
        at(2, () -> { if (mc.screen instanceof com.lanterna.client.oficina.OficinaScreen o) { o.testar(false); var p = com.lanterna.construct.oficina.Modelos.TODOS.get("espada").get(); p.liso = false; o.carregar(p); } });
        at(10, () -> shot("oficina_editor_quadrado"));
        at(2, () -> mc.setScreen(null));
        at(10, () -> glog("meus=" + java.util.Arrays.stream(com.lanterna.client.oficina.OficinaCliente.MEUS).map(p -> p == null ? "-" : p.nome + "/" + p.comp + "/" + p.quantos()).toList()));
        // no mundo
        at(5, () -> { com.lanterna.client.camera.CameraLanterna.ligada = true; mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); mc.options.hideGui = true; });
        at(5, () -> server(sp -> com.lanterna.construct.oficina.MeusConstrutos.lancar(sp, 0)));
        at(14, () -> shot("oficina_arma"));
        at(2, () -> com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.construct.oficina.MeusConstrutos.Golpe()));
        at(3, () -> shot("oficina_arma_golpe"));
        at(14, () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
        at(4, () -> com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.construct.oficina.MeusConstrutos.Golpe()));
        at(3, () -> shot("oficina_arma_golpe_costas"));
        at(14, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); server(sp -> com.lanterna.construct.oficina.MeusConstrutos.lancar(sp, 0)); });
        at(15, () -> server(sp -> com.lanterna.construct.oficina.MeusConstrutos.lancar(sp, 1)));
        at(4, () -> shot("oficina_escudo_montando"));
        at(10, () -> shot("oficina_escudo"));
        at(5, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); server(sp -> { com.lanterna.construct.oficina.MeusConstrutos.lancar(sp, 1); }); });
        at(10, () -> { mc.player.setXRot(2); server(sp -> { sp.setXRot(2); com.lanterna.construct.oficina.MeusConstrutos.lancar(sp, 2); }); });
        at(3, () -> shot("oficina_flecha"));
        at(20, () -> { mc.player.setXRot(8); server(sp -> { sp.setXRot(8); com.lanterna.construct.oficina.MeusConstrutos.lancar(sp, 3); }); });
        at(22, () -> shot("oficina_martelo_alto"));
        at(7, () -> shot("oficina_martelo_queda"));
        at(3, () -> shot("oficina_martelo_onda"));
        at(30, () -> { mc.player.setXRot(10); server(sp -> { sp.setXRot(10); com.lanterna.construct.oficina.MeusConstrutos.lancar(sp, 5); }); });
        at(20, () -> shot("oficina_muralha"));
        at(20, () -> server(sp -> com.lanterna.construct.oficina.MeusConstrutos.lancar(sp, 4)));
        at(5, () -> mc.options.keyUp.setDown(true));
        at(20, () -> { mc.options.keyUp.setDown(false); mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); });
        at(5, () -> shot("oficina_carro"));
        at(5, () -> glog("montado=" + (mc.player.getVehicle() != null ? mc.player.getVehicle().getClass().getSimpleName() : "nao") + " pos=" + mc.player.position()));
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }

    private static void sceneNaves2() {
        Minecraft mc = Minecraft.getInstance();
        java.util.UUID[] minha = {null};
        int[] alvoId = {-1};
        float[] hp0 = {0};
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.renderDistance().set(8);
            cmd("gamerule doDaylightCycle false"); cmd("gamerule keepInventory false"); cmd("execute in minecraft:overworld run weather clear 100000"); cmd("lanterna anel green"); });
        at(20, () -> cmd("lanterna maximo"));
        at(10, () -> cmd("execute in lanterna:oa run tp @s 60 140 60"));
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa") && mc.screen == null, 900);
        // ---- 1. pousar descendo devagar de ~50 blocos ----
        at(60, () -> server(sp -> {
            var l = sp.serverLevel();
            for (var n : l.getEntitiesOfClass(com.lanterna.naves.NaveEntity.class, sp.getBoundingBox().inflate(120))) n.discard();
            int h = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 60, 60);
            var n = com.lanterna.naves.NaveEntity.create(l, com.lanterna.naves.NaveTipo.NAVE_LANCEIRA, "green");
            n.moveTo(60.5, h + 50, 60.5, 0, 0);
            n.setDono(sp.getUUID());
            l.addFreshEntity(n);
            minha[0] = n.getUUID();
            sp.getInventory().add(com.lanterna.tropa.TropaItems.ChaveNave.forShip(n.getUUID(), "nave_lanceira", "green"));
            sp.teleportTo(l, 60.5, h + 51, 60.5, 0, 0);
            sp.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            sp.removeAllEffects();
            sp.setHealth(sp.getMaxHealth());
            sp.startRiding(n, true);
            hp0[0] = sp.getHealth();
        }));
        at(20, () -> com.lanterna.client.KeyBindings.NAVE_DESCER.setDown(true));
        at(400, () -> com.lanterna.client.KeyBindings.NAVE_DESCER.setDown(false));
        at(40, () -> server(sp -> glog("queda: vida antes=" + hp0[0] + " depois=" + sp.getHealth() + " montado=" + (sp.getVehicle() != null) + " y=" + Math.round(sp.getY())
                + " ultimo dano=" + (sp.getLastDamageSource() == null ? "nenhum" : sp.getLastDamageSource().getMsgId()) + " comida=" + sp.getFoodData().getFoodLevel())));
        at(5, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); shot("nave_pousada"); });
        at(10, () -> { server(sp -> sp.stopRiding()); mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); });
        // ---- 2. nave de Tropa contra os poderes do anel ----
        String[] poderes = {"laser", "martelo", "g_canhao", "g_meteoros"};
        for (String pd : poderes) {
            at(10, () -> server(sp -> {
                var l = sp.serverLevel();
                for (var n : l.getEntitiesOfClass(com.lanterna.naves.NaveEntity.class, sp.getBoundingBox().inflate(60), x -> !x.getUUID().equals(minha[0]))) n.discard();
                var n = com.lanterna.naves.NaveEntity.create(l, com.lanterna.naves.NaveTipo.NAVE_PATRULHA, "yellow");
                sp.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                sp.getAbilities().flying = true; sp.onUpdateAbilities();
                sp.teleportTo(l, 60.5, 110, 20.5, 0, 0);
                n.moveTo(60.5, 108, 38.5, 0, 0);
                n.setIa("patrulha");
                l.addFreshEntity(n);
                alvoId[0] = n.getId();
                hp0[0] = n.hp();
                sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, n.position().add(0, 1.5, 0));
                com.lanterna.construct.Constructs.zerarRecargas(sp.getUUID());
            }));
            at(10, () -> { if (pd.equals("laser")) construct("laser", true); else server(sp -> { var n = sp.serverLevel().getEntity(alvoId[0]); if (n != null) sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, n.position().add(0, 1.5, 0)); com.lanterna.construct.Constructs.cast(sp, pd); }); });
            for (int k = 0; k < 70; k++) at(1, () -> { var n = mc.level.getEntity(alvoId[0]); if (n != null) mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, n.getPosition(mc.getFrameTime()).add(0, 1.5, 0)); });
            at(1, () -> { if (pd.equals("laser")) construct("laser", false); });
            at(5, () -> server(sp -> {
                var e = sp.serverLevel().getEntity(alvoId[0]);
                glog("nave de Tropa x " + pd + ": vida " + hp0[0] + " -> " + (e instanceof com.lanterna.naves.NaveEntity n ? n.hp() : "destruída"));
            }));
        }
        // ---- 3. morrer com a chave, renascer dentro da nave e sair ----
        at(10, () -> server(sp -> {
            var l = sp.serverLevel();
            if (!(l.getEntity(minha[0]) instanceof com.lanterna.naves.NaveEntity n)) { glog("minha nave sumiu"); return; }
            com.lanterna.naves.NaveInterior.entrar(sp, n);
        }));
        at(60, () -> server(sp -> {
            sp.setRespawnPosition(sp.level().dimension(), sp.blockPosition(), sp.getYRot(), true, false);
            glog("dentro da nave: " + sp.level().dimension().location() + " chaves=" + sp.getInventory().countItem(com.lanterna.tropa.TropaItems.CHAVE_NAVE.get()));
            sp.kill();
        }));
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(80, () -> server(sp -> {
            glog("renasceu em: " + sp.level().dimension().location() + " chaves=" + sp.getInventory().countItem(com.lanterna.tropa.TropaItems.CHAVE_NAVE.get()));
            com.lanterna.naves.NaveInterior.sair(sp, minha[0]);
        }));
        at(60, () -> server(sp -> {
            var n = sp.serverLevel().getEntity(minha[0]);
            glog("saiu em: " + sp.level().dimension().location() + " perto da nave=" + (n != null ? Math.round(n.distanceTo(sp)) + " blocos" : "NAO"));
        }));
        // ---- 4. a chave chama a nave para outro planeta ----
        at(10, () -> cmd("execute in lanterna:korugar run tp @s 30 150 30"));
        waitFor(() -> mc.level.dimension().location().getPath().equals("korugar") && mc.screen == null, 900);
        at(80, () -> server(sp -> {
            int h = sp.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 30, 30);
            for (var old : sp.serverLevel().getEntitiesOfClass(com.lanterna.naves.NaveEntity.class, new net.minecraft.world.phys.AABB(30, 0, 30, 31, 300, 31).inflate(150))) old.discard();
            sp.teleportTo(sp.serverLevel(), 30.5, h, 30.5, 0, -10);
            sp.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            net.minecraft.world.item.ItemStack chave = net.minecraft.world.item.ItemStack.EMPTY;
            for (net.minecraft.world.item.ItemStack st : sp.getInventory().items) if (st.getItem() instanceof com.lanterna.tropa.TropaItems.ChaveNave) chave = st;
            glog("chamar: " + com.lanterna.naves.ChamadoNave.chamar(sp, chave));
        }));
        at(5, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); mc.options.hideGui = true; mc.player.setYRot(70); mc.player.setXRot(-25); });
        at(15, () -> shot("chamado_1"));
        at(20, () -> shot("chamado_2"));
        at(20, () -> { shot("chamado_3"); });
        at(20, () -> { shot("chamado_4"); mc.player.setXRot(-10); });
        at(30, () -> { shot("chamado_5"); mc.player.setXRot(5); });
        at(80, () -> shot("chamado_6"));
        at(40, () -> { mc.options.hideGui = false; });
        at(10, () -> server(sp -> {
            var n = sp.serverLevel().getEntity(minha[0]);
            glog("nave chamada: " + (n != null ? "em " + sp.level().dimension().location() + " a " + Math.round(n.distanceTo(sp)) + " blocos, pousada=" + ((com.lanterna.naves.NaveEntity) n).pousada() + " chegando=" + ((com.lanterna.naves.NaveEntity) n).chegando() + " y=" + Math.round(n.getY()) + " chao=" + sp.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, n.getBlockX(), n.getBlockZ()) : "NAO chegou"));
        }));
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }

    private static void sceneFonte() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.renderDistance().set(10);
            cmd("gamerule doDaylightCycle false"); cmd("weather clear"); cmd("gamemode creative"); cmd("lanterna fonte zerar"); });
        at(10, () -> cmd("execute in lanterna:oa run tp @s -388 120 10"));
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa") && mc.screen == null, 900);
        at(100, () -> cmd("time set 6000"));
        waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 60000);
        at(20, () -> server(sp -> {
            var r = com.lanterna.capability.ModCapabilities.get(sp).orElse(null);
            glog("anel=" + (r != null && r.hasRing() ? r.corps() : "nenhum") + " tropa=" + com.lanterna.fonte.FonteChamado.tropaDoAnel(sp));
            int h = com.lanterna.cidade.CidadeNativa.altura(sp.serverLevel());
            sp.teleportTo(sp.serverLevel(), -387.5, h, 25.5, 0f, 0f);
        }));
        at(20, () -> { cmd("gamemode survival"); cmd("effect give @s resistance 600 4 true"); cmd("lanterna fonte prova"); });
        at(20, () -> server(sp -> glog("selo antes=" + com.lanterna.fonte.FonteDados.temSelo(sp, "verde") + " usar=" + com.lanterna.fonte.FonteChamado.usarBateria(sp, new net.minecraft.core.BlockPos(-388, 60, 38)))));
        at(60, () -> shot("fonte_prova"));
        for (int i = 0; i < 4; i++) {
            at(80, () -> { cmd("kill @e[type=lanterna:eco_cinzento,distance=..60]"); cmd("kill @e[type=lanterna:lanterna_npc,distance=..60]"); });
            at(60, () -> server(sp -> glog("prova ativa=" + com.lanterna.fonte.ProvaFonte.ativa(sp) + " chamado=" + com.lanterna.fonte.FonteDados.chamado(sp, "verde:prova"))));
        }
        at(20, () -> server(sp -> { var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null);
            glog("missao 3 ativa=" + d.isActive("fonte_verde_3")); com.lanterna.fonte.FonteChamado.usarBateria(sp, new net.minecraft.core.BlockPos(-388, 60, 38)); }));
        at(320, () -> server(sp -> glog("selo depois=" + com.lanterna.fonte.FonteDados.temSelo(sp, "verde") + " essencia=" + com.lanterna.fonte.FonteDados.essencia(sp))));
        // atravessa o campo
        at(20, () -> server(sp -> { int h = com.lanterna.cidade.CidadeNativa.altura(sp.serverLevel()); sp.teleportTo(sp.serverLevel(), -387.5, h, 38.5, 0f, 0f); }));
        at(120, () -> server(sp -> glog("dim=" + sp.level().dimension().location())));
        at(20, () -> cmd("lanterna fonte banho 10"));
        at(60, () -> server(sp -> { var r = com.lanterna.capability.ModCapabilities.get(sp).orElse(null);
            glog("sobrecarga=" + com.lanterna.fonte.FonteDados.sobrecarga(sp) + " carga=" + (r == null ? -1 : r.charge()) + "/" + (r == null ? -1 : r.maxCharge())); }));
        at(20, () -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); });
        at(60, () -> shot("fonte_aura"));
        at(20, () -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        at(30, () -> shot("fonte_hud"));
        at(10, () -> cmd("lanterna fonte pressao 119"));
        at(20, () -> server(sp -> com.lanterna.fonte.FonteDados.pressao(sp, com.lanterna.fonte.Fonte.PRESSAO_MAX - 30)));
        at(80, () -> server(sp -> glog("depois da pressao: dim=" + sp.level().dimension().location() + " bloqueado=" + com.lanterna.fonte.FonteDados.bloqueado(sp)
                + " sobrecarga=" + com.lanterna.fonte.FonteDados.sobrecarga(sp))));
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }

    private static void sceneBateria() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.hideGui = true;
            mc.options.renderDistance().set(10); cmd("gamerule doDaylightCycle false"); cmd("weather clear"); });
        String only = System.getProperty("lanterna.planetas", "oa");
        for (String pl : only.split(",")) {
            at(10, () -> server(sp -> { var w = com.lanterna.lore.LoreWorldData.get(sp.server); if (System.getProperty("lanterna.refazer") != null) w.resetSite(pl + ":megacidade"); }));
            at(10, () -> { cmd("gamemode spectator"); cmd("execute in lanterna:" + pl + " run tp @s -388 200 -20"); });
            waitFor(() -> mc.level.dimension().location().getPath().equals(pl) && mc.screen == null, 900);
            at(10, () -> cmd("time set 6000"));
            for (int rep = 0; rep < 3; rep++) {
                at(100, () -> cmd("execute in lanterna:" + pl + " run tp @s -388 200 -20"));
                waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 60000);
            }
            // a praça e a Bateria vistas do norte
            at(20, () -> server(sp -> { int h = com.lanterna.cidade.CidadeNativa.altura(sp.serverLevel());
                sp.teleportTo(sp.serverLevel(), -387.5, h + 8, 38 - 30.0, 0f, 8f); }));
            at(200, () -> shot("bat_" + pl + "_frente"));
            at(20, () -> server(sp -> { int h = com.lanterna.cidade.CidadeNativa.altura(sp.serverLevel());
                sp.teleportTo(sp.serverLevel(), -387.5 + 24, h + 20, 38 - 24.0, 45f, 22f); }));
            at(120, () -> shot("bat_" + pl + "_lado"));
            // atravessa o campo (sobrevivência, com a tag da Bateria)
            at(20, () -> server(sp -> {
                int h = com.lanterna.cidade.CidadeNativa.altura(sp.serverLevel());
                var t = com.lanterna.baterias.Tropa.doPlaneta(com.lanterna.lore.PlanetLore.byPlanet(pl));
                sp.addTag("bateria_" + t.nome);
                sp.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                var l = sp.serverLevel(); int portais = 0;
                for (var q : net.minecraft.core.BlockPos.betweenClosed(-394, h - 2, 32, -382, h + 8, 44)) if (l.getBlockState(q).is(com.lanterna.baterias.ModBaterias.PORTAL.get())) portais++;
                glog(pl + ": blocos de portal na camara=" + portais + " nucleo=" + l.getBlockState(new net.minecraft.core.BlockPos(-388, h - 1, 38)).getBlock());
                sp.teleportTo(l, -387.5, h, 38.5, 180f, 0f);
            }));
            at(120, () -> server(sp -> glog(pl + ": depois do campo -> " + sp.level().dimension().location() + " em " + sp.blockPosition())));
            at(60, () -> shot("bat_" + pl + "_fonte"));
            at(10, () -> server(sp -> sp.teleportTo(sp.serverLevel(), 0.5, 101, 0.5, 0f, 0f)));
            at(140, () -> server(sp -> glog(pl + ": volta -> " + sp.level().dimension().location() + " em " + sp.blockPosition())));
            at(40, () -> shot("bat_" + pl + "_volta"));
        }
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }

    private static void scenePovos() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.hideGui = true;
            mc.options.renderDistance().set(16); cmd("gamemode spectator"); cmd("gamerule doDaylightCycle false"); cmd("weather clear"); });
        String only = System.getProperty("lanterna.planetas", "oa,korugar,ysmault,odym,okaara,nok,zamaron,ryut");
        for (String pl : only.split(",")) {
            at(10, () -> server(sp -> { var w = com.lanterna.lore.LoreWorldData.get(sp.server); if (System.getProperty("lanterna.refazer") != null) w.resetSite(pl + ":megacidade"); }));
            at(10, () -> cmd("execute in lanterna:" + pl + " run tp @s -480 200 -300"));
            waitFor(() -> mc.level.dimension().location().getPath().equals(pl) && mc.screen == null, 900);
            at(10, () -> cmd("time set 6000"));
            for (int rep = 0; rep < 3; rep++) {
                at(100, () -> cmd("execute in lanterna:" + pl + " run tp @s -480 200 -300"));
                waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 60000);
            }
            at(200, () -> server(sp -> { int nat = 0;
                for (var e : sp.serverLevel().getEntitiesOfClass(com.lanterna.lore.entity.RaceEntity.class, new net.minecraft.world.phys.AABB(-700, -64, -220, -260, 320, 220))) if (e.role().isEmpty()) nat++;
                glog(pl + ": chao=" + com.lanterna.cidade.CidadeNativa.altura(sp.serverLevel()) + " nativos=" + nat);
                var l = sp.serverLevel(); int h = com.lanterna.cidade.CidadeNativa.altura(l);
                for (int[] q : new int[][]{{0, 0}, {20, 0}, {44, 0}, {60, 0}, {100, 0}, {160, 0}, {190, 0}, {0, 100}, {-100, 0}, {0, -150}}) {
                    int x = -480 + q[0], z = q[1]; l.getChunk(x >> 4, z >> 4);
                    StringBuilder sb = new StringBuilder();
                    for (int dy = -1; dy <= 8; dy += 3) sb.append(dy).append('=').append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(l.getBlockState(new net.minecraft.core.BlockPos(x, h + dy, z)).getBlock()).getPath()).append(' ');
                    glog("  " + q[0] + "," + q[1] + " topo=" + l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z) + " " + sb);
                } }));
            Object[][] fotos = {{"alto", -480.5, 130.0, -175.0, 0f, 32f}, {"rua", -320.0, 3.0, 0.5, 90f, -14f}, {"bairro", -480 + 172 * 0.866, 9.0, 172 * 0.5, 150f, 2f},
                    {"lado", -480 - 150.0, 95.0, 150.0, -135f, 20f}};
            for (Object[] f : fotos) {
                at(10, () -> server(sp -> {
                    int h = com.lanterna.cidade.CidadeNativa.altura(sp.serverLevel());
                    sp.teleportTo(sp.serverLevel(), ((Number) f[1]).doubleValue(), h + (double) f[2], ((Number) f[3]).doubleValue(), (float) f[4], (float) f[5]);
                }));
                at(f[0].equals("alto") || f[0].equals("lado") ? 500 : 200, () -> shot("povo_" + pl + "_" + f[0]));
            }
        }
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }

    private static void sceneNativa() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); mc.options.hideGui = true;
            cmd("gamemode spectator"); cmd("time set 6000"); cmd("gamerule doDaylightCycle false"); cmd("weather clear"); });
        // palácio: fontes refeitas e a Capitã no lugar
        at(10, () -> cmd("execute in lanterna:oa run tp @s 0 190 -440"));
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa") && mc.screen == null, 900);
        at(100, () -> {});
        waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 3000);
        at(700, () -> server(sp -> { for (var e : sp.serverLevel().getEntitiesOfClass(com.lanterna.especies.LanternaEntity.class, sp.getBoundingBox().inflate(150)))
                if (e.funcao().equals("capitao_comando")) glog("Qiri em " + e.blockPosition() + " (posto 34,170,-448)"); }));
        // cidade: bordas fechadas
        at(10, () -> cmd("execute in lanterna:oa run tp @s 0 120 -150"));
        at(100, () -> {});
        waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 3000);
        // cidade dos nativos (refaz)
        at(5, () -> server(sp -> { var w = com.lanterna.lore.LoreWorldData.get(sp.server); w.resetSite("oa:nativa"); w.resetSite("oa:cidade2_agua"); }));
        at(10, () -> cmd("execute in lanterna:oa run tp @s 0 120 -150"));
        at(100, () -> {});
        waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 3000);
        at(10, () -> cmd("execute in lanterna:oa run tp @s -210 160 -60"));
        at(100, () -> {});
        waitFor(() -> !com.lanterna.cidade.Obra.ocupada(), 12000);
        at(100, () -> server(sp -> {
            var l = sp.serverLevel(); java.util.Map<String, Integer> g = new java.util.TreeMap<>();
            var m = new net.minecraft.core.BlockPos.MutableBlockPos();
            for (int[] z : new int[][]{{0, -470, 90, 150, 200}, {0, -150, 75, 30, 110}}) for (int x = z[0] - z[2]; x <= z[0] + z[2]; x++) for (int zz = z[1] - z[2]; zz <= z[1] + z[2]; zz++) for (int y = z[3]; y <= z[4]; y++) {
                m.set(x, y, zz); var f = l.getFluidState(m); if (!f.isEmpty() && !f.isSource()) g.merge(z[1] == -470 ? "palacio" : "cidade", 1, Integer::sum); }
            glog("agua corrente depois do conserto: " + g);
            int h = com.lanterna.cidade.CidadeNativa.altura(l); int nat = 0;
            for (var e : l.getEntitiesOfClass(com.lanterna.lore.entity.RaceEntity.class, new net.minecraft.world.phys.AABB(-270, -64, -60, -150, 320, 60))) if (e.role().isEmpty()) nat++;
            glog("cidade nativa: chao=" + h + " nativos=" + nat);
        }));
        at(700, () -> server(sp -> { int nat = 0;
            for (var e : sp.serverLevel().getEntitiesOfClass(com.lanterna.lore.entity.RaceEntity.class, new net.minecraft.world.phys.AABB(-270, -64, -60, -150, 320, 60))) if (e.role().isEmpty()) nat++;
            glog("nativos depois do vigia=" + nat); }));
        Object[][] fotos = {{"nat_alto", -210.5, 90.0, -75.0, 0f, 30f}, {"nat_lado", -150.0, 70.0, 60.5, 135f, 22f}, {"nat_rua", -210.5, 0.0, 45.0, 180f, -12f},
                {"nat_torre", -195.0, 0.0, 8.0, 120f, -40f}, {"nat_casulo", -210.5, 0.0, 0.0, 0f, 0f}, {"nat_casa", -210.5, 0.0, 0.0, 0f, 0f}};
        for (Object[] f : fotos) {
            at(10, () -> server(sp -> {
                int h = com.lanterna.cidade.CidadeNativa.altura(sp.serverLevel());
                double y = (double) f[2];
                double x = (double) f[1], z = (double) f[3];
                if (f[0].equals("nat_rua") || f[0].equals("nat_torre")) y += h + 3;
                if (f[0].equals("nat_casulo")) { double a = Math.toRadians(22.5); x = -210 + Math.cos(a) * 34 - 2; z = Math.sin(a) * 34 + 0.5; y = h + 44 - 1; }
                if (f[0].equals("nat_casa")) { double a = Math.toRadians(11.25 + 22.5 * 3); x = -210 + Math.cos(a) * 48 + 0.5; z = Math.sin(a) * 48 + 0.5; y = h + 1; }
                sp.teleportTo(sp.serverLevel(), x, y, z, (float) f[4], (float) f[5]);
            }));
            at(60, () -> shot((String) f[0]));
        }
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }

    private static void sceneV24() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd("gamemode survival"); cmd("time set 6000"); cmd("weather clear"); cmd("lanterna resetar"); cmd("execute in minecraft:overworld run tp @s 320.5 -60 330.5 0 0"); });
        at(30, () -> cmd("fill 310 -60 320 331 -50 345 minecraft:air"));
        String[] tropas = {"white", "green", "yellow", "black"};
        for (String t : tropas) {
            at(10, () -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna anel " + t)));
            at(10, () -> server(sp -> { com.lanterna.capability.ModCapabilities.get(sp).ifPresent(r -> { if (r.flightEnabled()) com.lanterna.power.RingPowers.toggleFlight(sp); if (!r.powerActive()) com.lanterna.power.RingPowers.togglePower(sp); }); }));
            for (String v : new String[]{"padrao", "a", "b"}) {
                at(5, () -> { var sc = new com.lanterna.client.visual.PerfilScreen(com.lanterna.client.visual.PerfilScreen.Aba.VISUAL); mc.setScreen(sc); sc.escolher(v, 0); mc.setScreen(null);
                    camera(CameraType.THIRD_PERSON_FRONT); mc.options.hideGui = true; look(0, 10); });
                at(40, () -> shot("v24_" + t + "_" + v));
            }
        }
        // ---- primeira pessoa: braço com o uniforme e a transformação ----
        at(5, () -> { camera(CameraType.FIRST_PERSON); mc.options.hideGui = false; var sc = new com.lanterna.client.visual.PerfilScreen(com.lanterna.client.visual.PerfilScreen.Aba.VISUAL); mc.setScreen(sc); sc.escolher("padrao", 0); mc.setScreen(null); });
        at(5, () -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna anel green")));
        at(10, () -> { mc.player.getInventory().selected = 8; look(0, 10); });
        at(30, () -> shot("v24_braco"));
        at(5, () -> com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.network.TogglePowerPacket()));
        at(40, () -> com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.network.TogglePowerPacket()));
        at(9, () -> shot("v24_ligando"));
        at(30, () -> shot("v24_ligado"));
        // ---- laser em terceira pessoa ----
        at(5, () -> { camera(CameraType.THIRD_PERSON_BACK); look(30, 0); });
        at(5, () -> server(sp -> com.lanterna.construct.Constructs.cast(sp, "laser")));
        at(15, () -> shot("v24_laser_f5"));
        at(5, () -> { camera(CameraType.THIRD_PERSON_FRONT); });
        at(5, () -> shot("v24_laser_frente"));
        at(5, () -> { server(sp -> com.lanterna.construct.Constructs.release(sp, null)); camera(CameraType.FIRST_PERSON); });
        // ---- base de Oa: sem Mestres Toran repetidos ----
        at(5, () -> cmd("execute in lanterna:oa run tp @s 0 120 20"));
        waitFor(() -> mc.level.dimension().location().getPath().equals("oa") && mc.screen == null, 900);
        Runnable conta = () -> server(sp -> { int toran = 0, guard = 0;
            for (var e : sp.serverLevel().getEntitiesOfClass(com.lanterna.lore.entity.RaceEntity.class, new net.minecraft.world.phys.AABB(-60, -64, -60, 60, 320, 60))) {
                if (e.role().equals("instrutor")) toran++; if (e.role().equals("guardiao")) guard++; }
            glog("base de Oa: instrutores=" + toran + " guardioes=" + guard); });
        at(60, conta);
        at(700, conta);
        at(700, conta);
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }

    private static void opc(String c) {
        server(sp -> {
            int r = sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), c);
            glog("/" + c + " -> " + r);
        });
    }

    private static void sceneComandos() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { cmd("gamemode creative"); cmd("lanterna resetar"); cmd("clear @s"); cmd("time set 6000");
            cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5 0 5"); });
        at(40, () -> opc("lanterna ajuda"));
        at(10, () -> opc("lanterna anel white"));
        at(20, () -> server(sp -> glog("anel=" + com.lanterna.power.EquipmentHelper.ring(sp).getHoverName().getString())));
        at(10, () -> opc("lanterna anel black"));
        at(20, () -> server(sp -> glog("anel=" + com.lanterna.power.EquipmentHelper.ring(sp).getHoverName().getString()
                + " morte=" + com.lanterna.mission.LoreCapability.get(sp).orElse(null).morte())));
        at(10, () -> opc("lanterna anel green"));
        at(10, () -> opc("lanterna nivel 37"));
        at(10, () -> server(sp -> glog("nivel=" + com.lanterna.tropa.Nivel.nivel(com.lanterna.mission.LoreCapability.get(sp).orElse(null).xp()))));
        at(10, () -> opc("lanterna nivel 5"));
        at(10, () -> server(sp -> glog("nivel=" + com.lanterna.tropa.Nivel.nivel(com.lanterna.mission.LoreCapability.get(sp).orElse(null).xp()))));
        at(10, () -> opc("lanterna afinidade_nivel 4"));
        at(10, () -> server(sp -> glog("afin=" + com.lanterna.construct.Affinity.points(com.lanterna.power.EquipmentHelper.ring(sp)))));
        at(10, () -> opc("lanterna tecnica todas"));
        at(10, () -> opc("lanterna poder portal"));
        at(10, () -> opc("lanterna poder honra"));
        at(10, () -> server(sp -> { var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null);
            glog("portal=" + d.portal() + " honra=" + d.honra() + " slots=" + com.lanterna.cidade.Tecnicas.slots(d)); }));
        at(10, () -> opc("lanterna morte corromper"));
        at(10, () -> server(sp -> { var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null); glog("morte=" + d.morte() + " ativas=" + d.active().keySet()); }));
        at(10, () -> opc("lanterna morte limpar"));
        at(10, () -> opc("lanterna missao_iniciar ryut_1"));
        at(10, () -> opc("lanterna missao_concluir ryut_1"));
        at(10, () -> server(sp -> { var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null); glog("ryut_1 feita=" + d.completed().contains("ryut_1") + " ativas=" + d.active().keySet()); }));
        at(10, () -> opc("lanterna missao_concluir portal_3"));
        at(10, () -> opc("lanterna missao_limpar"));
        at(10, () -> opc("lanterna visitar todos"));
        at(10, () -> opc("lanterna tropa_hostil yellow"));
        at(10, () -> opc("lanterna tropa_hostil limpar"));
        at(10, () -> opc("lanterna invocar pirata 2"));
        at(10, () -> opc("lanterna invocar oficial_yellow"));
        at(10, () -> opc("lanterna invocar lanterna_red"));
        at(10, () -> opc("lanterna invocar eco"));
        at(10, () -> opc("lanterna invocar mercador"));
        at(10, () -> opc("lanterna invocar alvo_treino"));
        at(10, () -> server(sp -> { for (String b : com.lanterna.lore.LoreEntities.bosses().keySet()) {
            int r = sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna invocar boss_" + b);
            glog("boss_" + b + " -> " + r); } }));
        at(10, () -> opc("lanterna nave nave_lanceira yellow"));
        at(10, () -> opc("lanterna kit tudo"));
        at(10, () -> opc("lanterna poder tudo"));
        at(10, () -> opc("lanterna cheio"));
        at(10, () -> opc("lanterna morte dominar"));
        at(10, () -> opc("lanterna invocar nada"));
        at(40, () -> shot("comandos"));
        at(10, () -> { for (String g : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", g); mc.stop(); });
    }

    private static void scenePortal() {
        Minecraft mc = Minecraft.getInstance();
        at(40, () -> { if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); } });
        at(40, () -> { mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            cmd("gamemode survival"); cmd("effect clear @s"); cmd("time set 6000"); cmd("weather clear"); cmd("lanterna resetar"); cmd("clear @s");
            cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5 0 5"); });
        at(40, () -> server(sp -> sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lanterna escolher lanterna:green")));
        waitFor(() -> com.lanterna.client.ClientRingData.hasRing, 400);
        for (int k = 0; k < 6; k++) at(20, () -> { com.lanterna.client.flight.Autopilot.clear(); cmd("execute in minecraft:overworld run tp @s 900.5 -60 900.5 0 5"); });
        at(20, () -> server(sp -> {
            com.lanterna.capability.ModCapabilities.get(sp).ifPresent(r -> { if (r.flightEnabled()) com.lanterna.power.RingPowers.toggleFlight(sp); });
            var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null);
            for (String id : new java.util.ArrayList<>(d.active().keySet())) d.abandon(id);
            d.setPortal(false); d.completed().removeIf(x -> x.startsWith("portal_"));
            glog("antes: pode abrir? " + d.portal());
            com.lanterna.tropa.Nivel.addXp(sp, com.lanterna.tropa.Nivel.total(33), null);
            com.lanterna.construct.Affinity.add(sp, 7000);
        }));
        at(120, () -> server(sp -> { var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null); glog("linha liberada: portal_1 ativa=" + d.isActive("portal_1"));
            com.lanterna.mission.MissionManager.onTalk(sp, "guardiao", "oa"); }));
        at(20, () -> server(sp -> { var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null);
            glog("portal_2 ativa=" + d.isActive("portal_2") + " objetivos=" + com.lanterna.mission.Missions.get("portal_2").objectives().size());
            for (int i = 0; i < 3; i++) d.setProgress("portal_2", i, 1);
            for (String pl : new String[]{"odym", "korugar", "zamaron", "oa", "nok"}) d.visitar(pl);
            com.lanterna.mission.MissionManager.evaluate(sp, d); }));
        at(20, () -> server(sp -> { var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null);
            glog("portal_3 ativa=" + d.isActive("portal_3")); com.lanterna.mission.MissionManager.onTalk(sp, "guardiao", "oa"); }));
        at(20, () -> server(sp -> { var d = com.lanterna.mission.LoreCapability.get(sp).orElse(null); glog("aprendeu=" + d.portal());
            var r = com.lanterna.capability.ModCapabilities.get(sp).orElse(null); if (!r.powerActive()) com.lanterna.power.RingPowers.togglePower(sp);
            com.lanterna.power.RingPowers.setCharge(sp, r, r.maxCharge()); }));
        at(20, () -> com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.portal.PortalPackets.Pedido("")));
        at(20, () -> { glog("tela=" + (mc.screen == null ? null : mc.screen.getClass().getSimpleName())); shot("p1_tela"); });
        at(5, () -> { if (mc.screen instanceof com.lanterna.client.portal.PortalScreen ps) ps.escolher("oa"); });
        at(5, () -> shot("p2_escolhido"));
        at(5, () -> { mc.setScreen(null); com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.portal.PortalPackets.Pedido("oa")); });
        int[] quando = {5, 11, 9, 8, 6, 3};
        String[] nomes = {"p3_sobe", "p4_porta_nasce", "p5_porta_aberta", "p6_voando", "p7_dentro", "p8_clarao"};
        for (int i = 0; i < quando.length; i++) { int k = i; at(quando[i], () -> shot(nomes[k])); }
        waitFor(() -> !(mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen) && mc.level.dimension().location().getPath().equals("oa"), 400);
        at(3, () -> shot("p9_chega"));
        at(5, () -> shot("p10_saindo"));
        at(7, () -> shot("p11_fora"));
        at(30, () -> { shot("p12_fim"); server(sp -> glog("chegou em " + sp.level().dimension().location() + " " + sp.blockPosition().toShortString()
                + " camera=" + mc.options.getCameraType() + " carga=" + com.lanterna.capability.ModCapabilities.get(sp).map(r -> r.charge()).orElse(-1))); });
        // a mesma viagem vista de lado (outro destino), com a câmera parada
        at(20, () -> server(sp -> { sp.teleportTo(sp.server.overworld(), 900.5, -60, 900.5, 0, 5);
            com.lanterna.mission.LoreCapability.get(sp).ifPresent(d -> d.setPortalCd(0)); }));
        at(40, () -> { CAM_DX = 7; CAM_DZ = 10; camLateral(true); });
        at(10, () -> com.lanterna.network.ModNetwork.sendToServer(new com.lanterna.portal.PortalPackets.Pedido("nok")));
        at(1, () -> { var e = mc.level.getEntity(CAM.get()); if (e != null) mc.setCameraEntity(e); });
        at(8, () -> shot("pl1_sobe"));
        at(14, () -> shot("pl2_porta"));
        at(14, () -> shot("pl3_voando"));
        at(5, () -> camLateral(false));
        at(60, () -> { for (String l : GLOG) LanternaMod.LOGGER.info("AUTOTEST RES {}", l); LanternaMod.LOGGER.info("AUTOTEST done"); mc.stop(); });
    }

    static {
        if (ENABLED && SCENE.equals("portal")) scenePortal();
        else if (ENABLED && SCENE.equals("comandos")) sceneComandos();
        else if (ENABLED && SCENE.equals("receber")) sceneReceber();
        else if (ENABLED && SCENE.equals("v22")) sceneV22();
        else if (ENABLED && SCENE.equals("teto")) sceneTeto();
        else if (ENABLED && SCENE.equals("v24")) sceneV24();
        else if (ENABLED && SCENE.equals("nativa")) sceneNativa();
        else if (ENABLED && SCENE.equals("povos")) scenePovos();
        else if (ENABLED && SCENE.equals("bateria")) sceneBateria();
        else if (ENABLED && SCENE.equals("fonte")) sceneFonte();
        else if (ENABLED && SCENE.equals("arvore")) sceneArvore();
        else if (ENABLED && SCENE.equals("chefes")) sceneChefes();
        else if (ENABLED && SCENE.equals("forca")) sceneForca();
        else if (ENABLED && SCENE.equals("trailer")) sceneTrailer();
        else if (ENABLED && SCENE.equals("combate")) sceneCombate();
        else if (ENABLED && SCENE.equals("densa")) sceneDensa();
        else if (ENABLED && SCENE.equals("naves2")) sceneNaves2();
        else if (ENABLED && SCENE.equals("v30")) sceneV30();
        else if (ENABLED && SCENE.equals("inclina")) sceneInclina();
        else if (ENABLED && SCENE.equals("inclina2")) sceneInclina2();
        else if (ENABLED && SCENE.equals("cajado")) sceneCajado();
        else if (ENABLED && SCENE.equals("favores")) sceneFavores();
        else if (ENABLED && SCENE.equals("terra")) sceneTerra();
        else if (ENABLED && SCENE.equals("oficina")) sceneOficina();
        else if (ENABLED && SCENE.equals("energia")) sceneEnergia();
        else if (ENABLED && SCENE.equals("escolha")) sceneEscolha();
        else if (ENABLED && SCENE.equals("mogo")) sceneMogo();
        else if (ENABLED && SCENE.equals("aura")) sceneAura();
        else if (ENABLED && SCENE.equals("mogoplaneta")) sceneMogoPlaneta();
        else if (ENABLED && SCENE.equals("naves3")) sceneNaves3();
        else if (ENABLED && SCENE.equals("agua")) sceneAgua();
        else if (ENABLED && SCENE.equals("v24c")) sceneV24c();
        else if (ENABLED && SCENE.equals("v24b")) sceneV24b();
        else if (ENABLED && SCENE.equals("v23")) sceneV23();
        else if (ENABLED && SCENE.equals("cabecas")) sceneCabecas();
        else if (ENABLED && SCENE.equals("teto2")) sceneTeto2();
        else if (ENABLED && SCENE.equals("fist")) sceneFist();
        else if (ENABLED && SCENE.equals("construtos")) sceneConstrutos();
        else if (ENABLED && SCENE.equals("v18")) sceneV18();
        else if (ENABLED && SCENE.equals("guilda")) sceneGuilda();
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
