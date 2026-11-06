package com.lanterna.naves;

import com.lanterna.LanternaMod;
import com.lanterna.registry.ModSounds;
import com.lanterna.space.SpaceBodies;
import com.lanterna.tropa.TropaItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * A Chave da Nave (o chip de acesso):
 *  - não cai quando o jogador morre: volta para o inventário ao renascer;
 *  - com a chave na mão, clique direito chama a nave para perto do jogador, em qualquer planeta (ou no espaço).
 *    A nave sai de onde estava e entra na atmosfera em fogo, freia e pousa a uns 12 blocos de você.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChamadoNave {
    private static final String GUARDADAS = "lanterna_chaves_guardadas";
    /** Espera entre chamadas (ticks). */
    public static final int ESPERA = 20 * 45;

    private record Pedido(UUID nave, UUID jogador, long desde) {}

    private static final Map<UUID, Pedido> PEDIDOS = new HashMap<>();

    // ===================== A chave não cai na morte =====================

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDrops(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        ListTag guardadas = new ListTag();
        Iterator<ItemEntity> it = e.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack s = it.next().getItem();
            if (s.getItem() instanceof TropaItems.ChaveNave && TropaItems.ChaveNave.ship(s) != null) {
                guardadas.add(s.save(new CompoundTag()));
                it.remove();
            }
        }
        if (!guardadas.isEmpty()) p.getPersistentData().put(GUARDADAS, guardadas);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        if (!e.isWasDeath()) return;
        ListTag guardadas = e.getOriginal().getPersistentData().getList(GUARDADAS, Tag.TAG_COMPOUND);
        Player p = e.getEntity();
        for (int i = 0; i < guardadas.size(); i++) {
            ItemStack s = ItemStack.of(guardadas.getCompound(i));
            if (!p.getInventory().add(s)) p.drop(s, false);
        }
        p.getPersistentData().remove(GUARDADAS);
    }

    // ===================== Chamar a nave =====================

    /** Clique direito com a chave. Devolve true se a chamada começou. */
    public static boolean chamar(ServerPlayer p, ItemStack chave) {
        UUID id = TropaItems.ChaveNave.ship(chave);
        if (id == null) return false;
        if (p.level().dimension().equals(NaveInterior.DIM)) {
            p.displayClientMessage(Component.translatable("nave.lanterna.chamar.dentro"), true);
            return false;
        }
        if (PEDIDOS.containsKey(id)) {
            p.displayClientMessage(Component.translatable("nave.lanterna.chamar.vindo"), true);
            return false;
        }
        NaveInterior.Posicao pos = NaveInterior.posicao(p.server, id);
        if (pos == null) {
            p.displayClientMessage(Component.translatable("nave.lanterna.chamar.perdida"), true);
            return false;
        }
        if (pos.chave().equals(p.level().dimension()) && p.position().distanceToSqr(pos.x(), pos.y(), pos.z()) < 40 * 40) {
            p.displayClientMessage(Component.translatable("nave.lanterna.chamar.perto"), true);
            return false;
        }
        ServerLevel src = p.server.getLevel(pos.chave());
        if (src == null) return false;
        src.setChunkForced(((int) Math.floor(pos.x())) >> 4, ((int) Math.floor(pos.z())) >> 4, true);
        PEDIDOS.put(id, new Pedido(id, p.getUUID(), p.server.getTickCount()));
        p.playNotifySound(ModSounds.NAVE_CHAMADO.get(), SoundSource.PLAYERS, 1f, 1f);
        p.displayClientMessage(Component.translatable("nave.lanterna.chamar.ok"), true);
        return true;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || PEDIDOS.isEmpty()) return;
        MinecraftServer server = e.getServer();
        PEDIDOS.values().removeIf(ped -> processar(server, ped));
    }

    /** true = acabou (deu certo ou desistiu). A nave pode levar alguns ticks para carregar do disco. */
    private static boolean processar(MinecraftServer server, Pedido ped) {
        NaveInterior.Posicao pos = NaveInterior.posicao(server, ped.nave());
        ServerPlayer p = server.getPlayerList().getPlayer(ped.jogador());
        if (pos == null || p == null) return true;
        ServerLevel src = server.getLevel(pos.chave());
        if (src == null) return true;
        int cx = ((int) Math.floor(pos.x())) >> 4, cz = ((int) Math.floor(pos.z())) >> 4;
        Entity ent = src.getEntity(ped.nave());
        if (!(ent instanceof NaveEntity nave)) {
            if (server.getTickCount() - ped.desde() > 200) {
                src.setChunkForced(cx, cz, false);
                p.displayClientMessage(Component.translatable("nave.lanterna.chamar.perdida"), true);
                return true;
            }
            return false;
        }
        src.setChunkForced(cx, cz, false);
        if (!nave.getPassengers().isEmpty() && nave.getPassengers().stream().anyMatch(x -> x instanceof Player && x != p)) {
            p.displayClientMessage(Component.translatable("nave.lanterna.chamar.ocupada"), true);
            return true;
        }
        nave.ejectPassengers();
        CompoundTag tag = new CompoundTag();
        if (!nave.save(tag)) return true;
        nave.discard();
        // onde pousar: à direita do jogador, uns 12 blocos, em chão firme (no espaço, na mesma altura)
        ServerLevel dest = p.serverLevel();
        boolean espaco = dest.dimension().equals(SpaceBodies.SPACE);
        Vec3 olhar = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 lado = new Vec3(-olhar.z, 0, olhar.x);
        Vec3 em = p.position().add(lado.scale(12)).add(olhar.scale(4));
        if (!espaco) {
            dest.getChunk(((int) Math.floor(em.x)) >> 4, ((int) Math.floor(em.z)) >> 4);
            int y = dest.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(em.x), (int) Math.floor(em.z));
            if (y <= dest.getMinBuildHeight() + 1) y = (int) p.getY();
            em = new Vec3(em.x, y, em.z);
        }
        // entra pelo céu, de trás do jogador
        Vec3 de = espaco ? em.add(olhar.scale(-160)).add(0, 20, 0) : em.add(olhar.scale(-70)).add(0, Math.min(110, dest.getMaxBuildHeight() - 10 - em.y), 0);
        final Vec3 inicio = de;
        Entity nova = EntityType.loadEntityRecursive(tag, dest, en -> { en.moveTo(inicio.x, inicio.y, inicio.z, en.getYRot(), 0); return en; });
        if (!(nova instanceof NaveEntity n)) return true;
        dest.addFreshEntity(n);
        n.chamar(de, em);
        p.displayClientMessage(Component.translatable("nave.lanterna.chamar.chegando"), true);
        return true;
    }

    private ChamadoNave() {}
}
