package com.lanterna.power;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.visual.Visual;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A aura que ilumina: com o anel ligado, a aura ligada e a opção "iluminar", o jogador carrega uma luz de tocha
 * (nível 14) que anda com ele — um bloco de luz invisível na altura da cabeça, trocado de lugar quando ele se move.
 * A cor da luz (vermelha para a Fúria, verde para a Vontade...) é desenhada no cliente por cima do chão e das paredes
 * que essa luz alcança (ClienteLuzAura). Funciona em servidor: todos veem a luz e o tom.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LuzAura {
    public static final int NIVEL = 14;
    private static final String TAG = "lanterna_luz_aura";

    private record Luz(ResourceKey<Level> dim, BlockPos pos, boolean agua) {}

    private static final Map<UUID, Luz> LUZES = new HashMap<>();

    public static boolean ativa(ServerPlayer p) {
        if (p.isSpectator() || !p.isAlive()) return false;
        var r = ModCapabilities.get(p).orElse(null);
        return r != null && r.hasRing() && r.powerActive() && Visual.iluminar(p);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 2 != 0) return;
        Luz atual = LUZES.get(p.getUUID());
        if (!ativa(p)) {
            if (atual != null) apagar(p);
            return;
        }
        ServerLevel l = p.serverLevel();
        BlockPos novo = alvo(p, l);
        if (atual != null && atual.dim == l.dimension() && atual.pos.equals(novo)) return;
        if (atual != null) apagar(p);
        if (novo == null) return;
        boolean agua = l.getFluidState(novo).is(Fluids.WATER);
        BlockState luz = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, NIVEL)
                .setValue(BlockStateProperties.WATERLOGGED, agua);
        l.setBlock(novo, luz, 3);
        Luz nova = new Luz(l.dimension(), novo, agua);
        LUZES.put(p.getUUID(), nova);
        CompoundTag t = new CompoundTag();
        t.putString("dim", l.dimension().location().toString());
        t.put("pos", NbtUtils.writeBlockPos(novo));
        t.putBoolean("agua", agua);
        p.getPersistentData().put(TAG, t);
    }

    /** Onde pôr a luz: na cabeça, senão nos pés; só em ar ou água parada (nunca troca um bloco do mundo). */
    private static BlockPos alvo(ServerPlayer p, ServerLevel l) {
        BlockPos cabeca = BlockPos.containing(p.getEyePosition());
        for (BlockPos b : new BlockPos[]{cabeca, p.blockPosition(), cabeca.above()}) {
            if (b.getY() < l.getMinBuildHeight() || b.getY() >= l.getMaxBuildHeight()) continue;
            BlockState s = l.getBlockState(b);
            if (s.is(Blocks.LIGHT) && !minha(p, b)) continue; // a luz de outro jogador
            if (s.isAir() || s.is(Blocks.LIGHT) || s.is(Blocks.WATER) && l.getFluidState(b).isSource()) return b;
        }
        return null;
    }

    private static boolean minha(ServerPlayer p, BlockPos b) {
        Luz x = LUZES.get(p.getUUID());
        return x != null && x.pos.equals(b);
    }

    /** Tira a luz do jogador do mundo (devolvendo a água, se era água). */
    public static void apagar(ServerPlayer p) {
        Luz x = LUZES.remove(p.getUUID());
        p.getPersistentData().remove(TAG);
        if (x == null) return;
        ServerLevel l = p.server.getLevel(x.dim);
        if (l == null) return;
        remover(l, x.pos, x.agua);
    }

    private static void remover(ServerLevel l, BlockPos pos, boolean agua) {
        if (!l.isLoaded(pos) || !l.getBlockState(pos).is(Blocks.LIGHT)) return;
        // outra luz de aura ocupando o mesmo bloco? deixa
        for (Luz o : LUZES.values()) if (o.dim == l.dimension() && o.pos.equals(pos)) return;
        l.setBlock(pos, agua ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
    }

    @SubscribeEvent
    public static void saiu(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) apagar(p);
    }

    @SubscribeEvent
    public static void trocouDeMundo(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) apagar(p);
    }

    @SubscribeEvent
    public static void renasceu(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) apagar(p);
    }

    /** Se o jogo fechou de repente, a última luz ficou no mundo: tira ao entrar de novo. */
    @SubscribeEvent
    public static void entrou(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !p.getPersistentData().contains(TAG)) return;
        CompoundTag t = p.getPersistentData().getCompound(TAG);
        p.getPersistentData().remove(TAG);
        ResourceLocation id = ResourceLocation.tryParse(t.getString("dim"));
        if (id == null) return;
        ServerLevel l = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (l != null) remover(l, NbtUtils.readBlockPos(t.getCompound("pos")), t.getBoolean("agua"));
    }

    @SubscribeEvent
    public static void parando(ServerStoppingEvent e) {
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) apagar(p);
        LUZES.clear();
    }

    private LuzAura() {}
}
