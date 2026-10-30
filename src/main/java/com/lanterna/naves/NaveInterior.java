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
        if (sala == null) return;
        event.setCanceled(true);
        sair(player);
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
        boolean built;
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
