package com.lanterna.client.devtest;

import com.lanterna.LanternaMod;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.OpenLanternMenuPacket;
import com.lanterna.network.ToggleFlightPacket;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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

    static {
        if (ENABLED && SCENE.equals("quadro")) sceneQuadro();
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
