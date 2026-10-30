package com.lanterna.naves;

import com.lanterna.LanternaMod;
import com.lanterna.naves.bloco.NaveBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraft.core.Vec3i;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Interior das naves: cada nave ganha uma sala de blocos (a estrutura interior_<tipo>.nbt) na dimensão
 * "interior_naves". Entrar: Shift + clique na nave. Sair: clicar na porta externa da sala (a da parede de fora).
 *
 * Posição de cada sala: x = número da nave * 64, z = região da cor * 4096 (assim o cliente sabe de que cor
 * pintar as faixas e luzes só pela posição).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NaveInterior {

    public static final ResourceKey<Level> DIM = ResourceKey.create(Registries.DIMENSION, LanternaMod.id("interior_naves"));
    public static final int REGION = 4096;
    public static final int SLOT = 64;
    public static final int Y = 64;
    private static final String RETURN = "lanterna_interior_volta";

    /** Região de cor: 0..7 = Tropas (ordem de BuiltinCorps), 8 = piratas, 9 = cargueiros. */
    public static int region(String pintura, NaveTipo tipo) {
        var corps = com.lanterna.corps.BuiltinCorps.values();
        for (int i = 0; i < 8; i++) if (corps[i].id().getPath().equals(pintura)) return i;
        return tipo == NaveTipo.NAVE_CARGUEIRO ? 9 : 8;
    }

    /** Cor das faixas numa posição (cliente e servidor). */
    public static int corDaRegiao(int z) {
        int r = Math.floorDiv(z, REGION);
        if (r >= 0 && r < 8) return com.lanterna.corps.BuiltinCorps.values()[r].color();
        if (r == 8) return 0xFF3020;
        if (r == 9) return 0xFFB040;
        return 0x46E66E;
    }

    public static void entrar(ServerPlayer player, NaveEntity nave) {
        String struct = nave.tipo().interior();
        ServerLevel dim = player.server.getLevel(DIM);
        if (struct == null || dim == null) return;
        Dados dados = Dados.get(player.server);
        Sala sala = dados.sala(nave, struct);
        if (!sala.built) {
            if (!colar(dim, sala, struct)) {
                player.displayClientMessage(Component.literal("Interior não encontrado: " + struct), true);
                return;
            }
            sala.built = true;
            sala.semEscotilha = true;
            sala.portasOk = true;
            dados.setDirty();
        } else if (!sala.semEscotilha || !sala.portasOk) {
            // salas feitas antes: tira o alçapão do chão e arruma as portas lado a lado
            tirarEscotilhas(dim, sala.origem, sala.tamanho);
            arrumarPortas(dim, sala.origem, sala.tamanho);
            sala.semEscotilha = true;
            sala.portasOk = true;
            dados.setDirty();
        }
        CompoundTag back = new CompoundTag();
        back.putUUID("Nave", nave.getUUID());
        back.putString("Dim", nave.level().dimension().location().toString());
        Vec3 out = nave.getDismountLocationForPassenger(player);
        back.putDouble("X", out.x);
        back.putDouble("Y", out.y);
        back.putDouble("Z", out.z);
        player.getPersistentData().put(RETURN, back);
        if (nave.dono() != null && !nave.dono().equals(sala.dono)) { sala.dono = nave.dono(); dados.setDirty(); }
        BlockPos in = sala.dentro;
        player.teleportTo(dim, in.getX() + 0.5, in.getY(), in.getZ() + 0.5, sala.olhar, 0);
        player.fallDistance = 0;
        dim.playSound(null, in, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.8f, 1f);
        // A sala às vezes chega ao cliente antes dos blocos colados: reenvia os chunks dela logo depois
        CHEGANDO.put(player.getUUID(), new long[]{player.server.getTickCount() + 5, sala.origem.asLong(), sala.tamanho.asLong(), 0});
    }

    private static final Map<UUID, long[]> CHEGANDO = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || CHEGANDO.isEmpty()) return;
        MinecraftServer server = event.getServer();
        int now = server.getTickCount();
        CHEGANDO.entrySet().removeIf(e -> {
            long[] v = e.getValue();
            if (now < v[0]) return false;
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null || !p.level().dimension().equals(DIM)) return true;
            ServerLevel dim = p.serverLevel();
            BlockPos o = BlockPos.of(v[1]), t = BlockPos.of(v[2]);
            for (int cx = o.getX() >> 4; cx <= (o.getX() + Math.max(1, t.getX())) >> 4; cx++) {
                for (int cz = o.getZ() >> 4; cz <= (o.getZ() + Math.max(1, t.getZ())) >> 4; cz++) {
                    var chunk = dim.getChunk(cx, cz);
                    p.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(chunk, dim.getLightEngine(), null, null));
                }
            }
            // segura o jogador no lugar enquanto o cliente monta a sala
            p.fallDistance = 0;
            p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
            if (v[3] == 0) { v[3] = 1; v[0] = now + 20; return false; }
            return true;
        });
    }

    /** Cola a estrutura e acha a porta externa (uma nave_porta de baixo na borda da sala). */
    private static boolean colar(ServerLevel dim, Sala sala, String struct) {
        StructureTemplate tpl = dim.getStructureManager().get(LanternaMod.id(struct)).orElse(null);
        if (tpl == null) return false;
        BlockPos origin = sala.origem;
        StructurePlaceSettings settings = new StructurePlaceSettings();
        tpl.placeInWorld(dim, origin, origin, settings, dim.random, 2);
        Vec3i size = tpl.getSize();
        tirarEscotilhas(dim, origin, new BlockPos(size.getX(), size.getY(), size.getZ()));
        arrumarPortas(dim, origin, new BlockPos(size.getX(), size.getY(), size.getZ()));
        BlockPos door = null;
        for (StructureTemplate.StructureBlockInfo info : tpl.filterBlocks(BlockPos.ZERO, settings, NaveBlocks.NAVE_PORTA.get())) {
            BlockPos p = info.pos();
            if (info.state().getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
            if (p.getX() == 0 || p.getZ() == 0 || p.getX() == size.getX() - 1 || p.getZ() == size.getZ() - 1) {
                door = p;
                break;
            }
        }
        if (door == null) door = new BlockPos(size.getX() / 2, 1, 0);
        Direction in = door.getX() == 0 ? Direction.EAST : door.getX() == size.getX() - 1 ? Direction.WEST
                : door.getZ() == 0 ? Direction.SOUTH : Direction.NORTH;
        sala.porta = origin.offset(door);
        sala.dentro = sala.porta.relative(in);
        sala.olhar = in.toYRot();
        sala.tamanho = new BlockPos(size.getX(), size.getY(), size.getZ());
        return true;
    }

    /** O alçapão do chão levava para fora da sala (no vazio): vira piso. A entrada e saída é só pela porta. */
    /**
     * Portas lado a lado (rampa do cargueiro, hangar da Capitânia) vinham todas com a dobradiça do mesmo lado
     * e batiam umas nas outras: vira pares de portas duplas (esquerda/direita alternando).
     */
    private static void arrumarPortas(ServerLevel dim, BlockPos o, BlockPos t) {
        java.util.Set<BlockPos> feitas = new java.util.HashSet<>();
        for (BlockPos p : BlockPos.betweenClosed(o, o.offset(Math.max(0, t.getX() - 1), Math.max(0, t.getY() - 1), Math.max(0, t.getZ() - 1)))) {
            BlockState st = dim.getBlockState(p);
            if (!st.is(NaveBlocks.NAVE_PORTA.get()) || st.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER || feitas.contains(p)) continue;
            Direction f = st.getValue(DoorBlock.FACING);
            Direction lado = f.getClockWise();
            // começo da fileira
            BlockPos ini = p.immutable();
            while (dim.getBlockState(ini.relative(lado.getOpposite())).is(NaveBlocks.NAVE_PORTA.get())) ini = ini.relative(lado.getOpposite());
            int i = 0;
            for (BlockPos q = ini; dim.getBlockState(q).is(NaveBlocks.NAVE_PORTA.get()); q = q.relative(lado), i++) {
                BlockState qs = dim.getBlockState(q);
                if (qs.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) break;
                var hinge = i % 2 == 0 ? net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT
                        : net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT;
                dim.setBlock(q, qs.setValue(DoorBlock.HINGE, hinge), 2);
                BlockState up = dim.getBlockState(q.above());
                if (up.is(NaveBlocks.NAVE_PORTA.get())) dim.setBlock(q.above(), up.setValue(DoorBlock.HINGE, hinge), 2);
                feitas.add(q.immutable());
            }
        }
    }

    private static void tirarEscotilhas(ServerLevel dim, BlockPos o, BlockPos t) {
        for (BlockPos p : BlockPos.betweenClosed(o, o.offset(Math.max(0, t.getX() - 1), Math.max(0, t.getY() - 1), Math.max(0, t.getZ() - 1)))) {
            if (dim.getBlockState(p).is(NaveBlocks.NAVE_ESCOTILHA.get())) dim.setBlock(p, NaveBlocks.NAVE_PISO.get().defaultBlockState(), 2);
        }
    }

    /** Clicar na porta externa da sala leva de volta para fora da nave. */
    @SubscribeEvent
    public static void onUseDoor(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.level().dimension().equals(DIM)) return;
        BlockState state = player.level().getBlockState(event.getPos());
        // Painel da nave: contratos da sua Tropa
        if (state.is(NaveBlocks.NAVE_CONSOLE.get())) {
            event.setCanceled(true);
            if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) com.lanterna.tropa.Contratos.abrirQuadro(player);
            return;
        }
        if (!state.is(NaveBlocks.NAVE_PORTA.get())) return;
        BlockPos lower = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? event.getPos().below() : event.getPos();
        Sala sala = Dados.get(player.server).porPorta(lower);
        if (sala == null) sala = Dados.get(player.server).portaDaBorda(lower);
        if (sala == null) return;
        event.setCanceled(true);
        if (sala.destino != null) {
            long falta = Math.max(0, sala.chegada - player.server.overworld().getGameTime()) / 20;
            player.displayClientMessage(Component.translatable("nave.lanterna.auto.em_viagem", falta), true);
            return;
        }
        sair(player);
    }

    // ===================== Piloto automático pelo holomapa =====================

    /** UUID da nave da sala onde o jogador está (se for dele), ou null. */
    @Nullable
    private static Map.Entry<UUID, Sala> salaDoJogador(ServerPlayer player) {
        if (!player.level().dimension().equals(DIM)) return null;
        BlockPos pos = player.blockPosition();
        for (Map.Entry<UUID, Sala> e : Dados.get(player.server).salas.entrySet()) {
            Sala s = e.getValue();
            BlockPos o = s.origem, t = s.tamanho;
            if (!s.built || pos.getX() < o.getX() - 1 || pos.getX() > o.getX() + t.getX() || pos.getZ() < o.getZ() - 1
                    || pos.getZ() > o.getZ() + t.getZ()) continue;
            return e;
        }
        return null;
    }

    /** Posição "no espaço" de um lugar (para calcular a distância da viagem). */
    private static Vec3 noEspaco(ResourceKey<Level> dim, double x, double y, double z) {
        if (dim.equals(com.lanterna.space.SpaceBodies.SPACE)) return new Vec3(x, y, z);
        com.lanterna.space.SpaceBodies.Body b = com.lanterna.space.SpaceBodies.bodyAt(dim, x, z);
        if (b == null && dim.equals(Level.OVERWORLD)) b = com.lanterna.space.SpaceBodies.EARTH;
        return b != null ? b.center() : Vec3.ZERO;
    }

    public static void iniciarViagem(ServerPlayer player, String destId) {
        if (!salaPropria(player)) {
            player.displayClientMessage(Component.translatable("contrato.lanterna.nave_alheia"), true);
            return;
        }
        var entry = salaDoJogador(player);
        com.lanterna.space.SpaceBodies.Body body = com.lanterna.space.SpaceBodies.byId(destId);
        if (entry == null || body == null || !body.landable() || body.dimension() == null) return;
        Sala sala = entry.getValue();
        if (sala.destino != null) {
            player.displayClientMessage(Component.translatable("nave.lanterna.auto.ja_viajando"), true);
            return;
        }
        CompoundTag back = player.getPersistentData().getCompound(RETURN);
        if (!back.contains("Dim") || !back.hasUUID("Nave") || !back.getUUID("Nave").equals(entry.getKey())) {
            player.displayClientMessage(Component.translatable("nave.lanterna.auto.sem_nave"), true);
            return;
        }
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(back.getString("Dim")));
        if (dimKey.equals(body.dimension()) && com.lanterna.space.SpaceBodies.bodyAt(dimKey, back.getDouble("X"), back.getDouble("Z")) == body) {
            player.displayClientMessage(Component.translatable("nave.lanterna.auto.ja_aqui", body.displayName()), true);
            return;
        }
        sala.destino = destId;
        sala.fase = 0;
        sala.inicio = player.server.overworld().getGameTime();
        sala.origemDim = back.getString("Dim");
        sala.ox = back.getDouble("X"); sala.oy = back.getDouble("Y"); sala.oz = back.getDouble("Z");
        Dados.get(player.server).setDirty();
        ServerLevel src = player.server.getLevel(dimKey);
        if (src != null) src.setChunkForced(((int) Math.floor(sala.ox)) >> 4, ((int) Math.floor(sala.oz)) >> 4, true);
        player.displayClientMessage(Component.translatable("nave.lanterna.auto.preparando", body.displayName()), true);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 0.7f);
    }

    @SubscribeEvent
    public static void onViagemTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 5 != 0) return;
        Dados dados = Dados.get(server);
        long now = server.overworld().getGameTime();
        for (Map.Entry<UUID, Sala> e : dados.salas.entrySet()) {
            Sala sala = e.getValue();
            if (sala.destino == null) continue;
            com.lanterna.space.SpaceBodies.Body body = com.lanterna.space.SpaceBodies.byId(sala.destino);
            if (body == null || body.dimension() == null) { sala.destino = null; dados.setDirty(); continue; }
            if (sala.fase == 0) buscarNave(server, dados, e.getKey(), sala, body, now);
            else if (now >= sala.chegada) chegar(server, dados, e.getKey(), sala, body);
            else if (server.getTickCount() % 100 == 0) avisar(server, e.getKey(), Component.translatable("nave.lanterna.auto.viajando",
                    body.displayName(), (sala.chegada - now) / 20), false);
        }
    }

    /** Fase 0: carrega o lugar onde a nave ficou, guarda ela "em viagem" e calcula quanto tempo leva. */
    private static void buscarNave(MinecraftServer server, Dados dados, UUID id, Sala sala, com.lanterna.space.SpaceBodies.Body body, long now) {
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(sala.origemDim));
        ServerLevel src = server.getLevel(dimKey);
        int cx = ((int) Math.floor(sala.ox)) >> 4, cz = ((int) Math.floor(sala.oz)) >> 4;
        if (src != null && src.getEntity(id) instanceof NaveEntity n) {
            n.ejectPassengers();
            CompoundTag tag = new CompoundTag();
            if (!n.save(tag)) return;
            sala.naveNbt = tag;
            Vec3 de = noEspaco(dimKey, n.getX(), n.getY(), n.getZ());
            n.discard();
            src.setChunkForced(cx, cz, false);
            double dist = de.distanceTo(body.center());
            long segundos = (long) Mth.clamp(8 + dist / 1200.0, 8, 120);
            sala.chegada = now + segundos * 20;
            sala.fase = 1;
            dados.setDirty();
            avisar(server, id, Component.translatable("nave.lanterna.auto.partiu", body.displayName(), segundos), true);
            return;
        }
        if (now - sala.inicio > 20 * 15 || src == null) {
            if (src != null) src.setChunkForced(cx, cz, false);
            sala.destino = null;
            dados.setDirty();
            avisar(server, id, Component.translatable("nave.lanterna.auto.sem_nave"), true);
        }
    }

    /** Chegou: a nave aparece pousada no destino e a porta da sala passa a sair lá. */
    private static void chegar(MinecraftServer server, Dados dados, UUID id, Sala sala, com.lanterna.space.SpaceBodies.Body body) {
        ServerLevel dest = server.getLevel(body.dimension());
        if (dest == null || sala.naveNbt == null) { sala.destino = null; dados.setDirty(); return; }
        Vec3 lp;
        if (body == com.lanterna.space.SpaceBodies.EARTH) {
            BlockPos sp = dest.getSharedSpawnPos();
            lp = new Vec3(sp.getX() + 24, 0, sp.getZ() + 24);
        } else {
            Vec3 de = noEspaco(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(sala.origemDim)), sala.ox, sala.oy, sala.oz);
            lp = com.lanterna.space.SpaceTravel.landingPoint(body, de);
        }
        dest.getChunk(((int) Math.floor(lp.x)) >> 4, ((int) Math.floor(lp.z)) >> 4);
        int y = dest.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(lp.x), (int) Math.floor(lp.z));
        if (y <= dest.getMinBuildHeight() + 1) y = 100;
        final double fy = y + 0.5;
        net.minecraft.world.entity.Entity ent = net.minecraft.world.entity.EntityType.loadEntityRecursive(sala.naveNbt, dest, en -> {
            en.moveTo(lp.x, fy, lp.z, en.getYRot(), 0);
            return en;
        });
        if (ent == null) { sala.destino = null; dados.setDirty(); return; }
        ent.setDeltaMovement(Vec3.ZERO);
        dest.addFreshEntity(ent);
        dest.playSound(null, ent.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 1.5f, 0.6f);
        // A volta de quem está na sala passa a ser o destino
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            CompoundTag back = p.getPersistentData().getCompound(RETURN);
            if (!back.hasUUID("Nave") || !back.getUUID("Nave").equals(id)) continue;
            Vec3 out = ent instanceof NaveEntity n ? n.getDismountLocationForPassenger(p) : ent.position().add(3, 0.5, 0);
            back.putString("Dim", dest.dimension().location().toString());
            back.putDouble("X", out.x);
            back.putDouble("Y", out.y);
            back.putDouble("Z", out.z);
            p.getPersistentData().put(RETURN, back);
        }
        sala.destino = null;
        sala.naveNbt = null;
        dados.setDirty();
        avisar(server, id, Component.translatable("nave.lanterna.auto.chegou", body.displayName()), true);
    }

    /** Mensagem para quem está dentro desta nave. */
    private static void avisar(MinecraftServer server, UUID nave, Component msg, boolean som) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.level().dimension().equals(DIM)) continue;
            CompoundTag back = p.getPersistentData().getCompound(RETURN);
            if (!back.hasUUID("Nave") || !back.getUUID("Nave").equals(nave)) continue;
            p.displayClientMessage(msg, !som);
            if (som) p.playNotifySound(SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8f, 1.2f);
        }
    }

    /** A nave está em viagem (o holomapa mostra isso)? */
    public static boolean emViagem(ServerPlayer p) {
        var e = salaDoJogador(p);
        return e != null && e.getValue().destino != null;
    }

    /** O jogador está numa sala de nave que é dele (dono, chave da nave, ou nave sem dono)? */
    public static boolean salaPropria(ServerPlayer player) {
        if (!player.level().dimension().equals(DIM)) return false;
        Dados dados = Dados.get(player.server);
        BlockPos pos = player.blockPosition();
        for (Map.Entry<UUID, Sala> e : dados.salas.entrySet()) {
            Sala s = e.getValue();
            if (!s.built) continue;
            BlockPos o = s.origem, t = s.tamanho;
            if (pos.getX() < o.getX() - 1 || pos.getX() > o.getX() + t.getX() || pos.getZ() < o.getZ() - 1 || pos.getZ() > o.getZ() + t.getZ()
                    || pos.getY() < o.getY() - 2 || pos.getY() > o.getY() + t.getY() + 1) continue;
            if (player.isCreative() || s.dono == null || s.dono.equals(player.getUUID())) return true;
            for (var st : player.getInventory().items) {
                if (st.getItem() instanceof com.lanterna.tropa.TropaItems.ChaveNave && e.getKey().equals(com.lanterna.tropa.TropaItems.ChaveNave.ship(st))) return true;
            }
            return e.getKey().equals(com.lanterna.tropa.TropaItems.ChaveNave.ship(player.getOffhandItem()));
        }
        return false;
    }

    public static void sair(ServerPlayer player) {
        CompoundTag back = player.getPersistentData().getCompound(RETURN);
        MinecraftServer server = player.server;
        ResourceKey<Level> dimKey = back.contains("Dim")
                ? ResourceKey.create(Registries.DIMENSION, new ResourceLocation(back.getString("Dim"))) : Level.OVERWORLD;
        ServerLevel level = server.getLevel(dimKey);
        if (level == null) level = server.overworld();
        Vec3 out = new Vec3(back.getDouble("X"), back.getDouble("Y"), back.getDouble("Z"));
        if (back.hasUUID("Nave") && level.getEntity(back.getUUID("Nave")) instanceof NaveEntity nave) {
            out = nave.getDismountLocationForPassenger(player);
            nave.abrirPorta(true);
        } else if (!back.contains("X")) {
            BlockPos s = level.getSharedSpawnPos();
            out = new Vec3(s.getX(), s.getY() + 1, s.getZ());
        }
        player.teleportTo(level, out.x, out.y, out.z, player.getYRot(), 0);
        player.fallDistance = 0;
    }

    // ===================== Dados salvos =====================

    static final class Sala {
        int indice;
        BlockPos origem = BlockPos.ZERO, porta = BlockPos.ZERO, dentro = BlockPos.ZERO, tamanho = BlockPos.ZERO;
        float olhar;
        boolean built, semEscotilha, portasOk;
        /** Piloto automático: destino, quando chega, fase (0 = buscando a nave, 1 = viajando) e a nave guardada. */
        @Nullable String destino;
        long chegada, inicio;
        int fase;
        @Nullable CompoundTag naveNbt;
        @Nullable String origemDim;
        double ox, oy, oz;
        @Nullable UUID dono;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("I", indice);
            t.putLong("O", origem.asLong());
            t.putLong("P", porta.asLong());
            t.putLong("D", dentro.asLong());
            t.putLong("T", tamanho.asLong());
            t.putFloat("Y", olhar);
            t.putBoolean("B", built);
            if (dono != null) t.putUUID("Dono", dono);
            t.putBoolean("SemEscotilha", semEscotilha);
            t.putBoolean("PortasOk", portasOk);
            if (destino != null) {
                t.putString("Destino", destino);
                t.putLong("Chegada", chegada);
                t.putLong("Inicio", inicio);
                t.putInt("Fase", fase);
                if (naveNbt != null) t.put("NaveNbt", naveNbt);
                if (origemDim != null) t.putString("OrigemDim", origemDim);
                t.putDouble("OX", ox); t.putDouble("OY", oy); t.putDouble("OZ", oz);
            }
            return t;
        }

        static Sala load(CompoundTag t) {
            Sala s = new Sala();
            s.indice = t.getInt("I");
            s.origem = BlockPos.of(t.getLong("O"));
            s.porta = BlockPos.of(t.getLong("P"));
            s.dentro = BlockPos.of(t.getLong("D"));
            s.tamanho = BlockPos.of(t.getLong("T"));
            s.olhar = t.getFloat("Y");
            s.built = t.getBoolean("B");
            if (t.hasUUID("Dono")) s.dono = t.getUUID("Dono");
            s.semEscotilha = t.getBoolean("SemEscotilha");
            s.portasOk = t.getBoolean("PortasOk");
            if (t.contains("Destino")) {
                s.destino = t.getString("Destino");
                s.chegada = t.getLong("Chegada");
                s.inicio = t.getLong("Inicio");
                s.fase = t.getInt("Fase");
                if (t.contains("NaveNbt")) s.naveNbt = t.getCompound("NaveNbt");
                if (t.contains("OrigemDim")) s.origemDim = t.getString("OrigemDim");
                s.ox = t.getDouble("OX"); s.oy = t.getDouble("OY"); s.oz = t.getDouble("OZ");
            }
            return s;
        }
    }

    static final class Dados extends SavedData {
        private final Map<UUID, Sala> salas = new HashMap<>();
        private final int[] next = new int[10];

        static Dados get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Dados::load, Dados::new, "lanterna_interiores");
        }

        Sala sala(NaveEntity nave, String struct) {
            return salas.computeIfAbsent(nave.getUUID(), id -> {
                int r = region(nave.getPintura(), nave.tipo());
                Sala s = new Sala();
                s.indice = next[r]++;
                s.origem = new BlockPos(s.indice * SLOT, Y, r * REGION);
                setDirty();
                return s;
            });
        }

        /** Qualquer porta na parede de fora da sala também sai (cargueiro tem rampa com 4 portas, a Capitânia um hangar). */
        @Nullable
        Sala portaDaBorda(BlockPos p) {
            for (Sala s : salas.values()) {
                if (!s.built) continue;
                BlockPos o = s.origem, t = s.tamanho;
                int x = p.getX() - o.getX(), z = p.getZ() - o.getZ(), y = p.getY() - o.getY();
                if (x < 0 || z < 0 || y < 0 || x >= t.getX() || z >= t.getZ() || y >= t.getY()) continue;
                if (x == 0 || z == 0 || x == t.getX() - 1 || z == t.getZ() - 1) return s;
            }
            return null;
        }

        @Nullable
        Sala porPorta(BlockPos lower) {
            for (Sala s : salas.values()) if (s.built && s.porta.equals(lower)) return s;
            return null;
        }

        static Dados load(CompoundTag tag) {
            Dados d = new Dados();
            CompoundTag m = tag.getCompound("Salas");
            for (String k : m.getAllKeys()) d.salas.put(UUID.fromString(k), Sala.load(m.getCompound(k)));
            int[] n = tag.getIntArray("Next");
            System.arraycopy(n, 0, d.next, 0, Math.min(n.length, d.next.length));
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            CompoundTag m = new CompoundTag();
            salas.forEach((k, v) -> m.put(k.toString(), v.save()));
            tag.put("Salas", m);
            tag.putIntArray("Next", next);
            return tag;
        }
    }

    private NaveInterior() {}
}
