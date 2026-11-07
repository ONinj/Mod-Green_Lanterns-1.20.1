package com.lanterna.construct.oficina;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.construct.Affinity;
import com.lanterna.construct.Constructs;
import com.lanterna.network.ModNetwork;
import com.lanterna.power.RingPowers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Os construtos desenhados de cada jogador (8 espaços), guardados no próprio jogador (passam pela morte e pelo
 * servidor). No preset, o construto desenhado aparece como "meu:<espaço>".
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MeusConstrutos {
    private static final String CHAVE = "lanterna_oficina";
    private static final Map<UUID, long[]> RECARGA = new HashMap<>();

    public static boolean eMeu(String id) {
        return id != null && id.startsWith("meu:");
    }

    public static int slot(String id) {
        try { return Integer.parseInt(id.substring(4)); } catch (Exception e) { return -1; }
    }

    private static CompoundTag raiz(Player p) {
        CompoundTag pers = p.getPersistentData();
        CompoundTag keep = pers.getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(Player.PERSISTED_NBT_TAG)) pers.put(Player.PERSISTED_NBT_TAG, keep);
        if (!keep.contains(CHAVE)) keep.put(CHAVE, new ListTag());
        return keep;
    }

    public static Projeto[] todos(Player p) {
        Projeto[] out = new Projeto[Projeto.SLOTS];
        ListTag l = raiz(p).getList(CHAVE, Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            int s = t.getInt("slot");
            if (s >= 0 && s < Projeto.SLOTS && t.contains("projeto")) out[s] = Projeto.ler(t.getCompound("projeto"));
        }
        return out;
    }

    private static void gravar(Player p, int slot, Projeto proj) {
        ListTag l = raiz(p).getList(CHAVE, Tag.TAG_COMPOUND);
        for (int i = l.size() - 1; i >= 0; i--) if (l.getCompound(i).getInt("slot") == slot) l.remove(i);
        if (proj != null && !proj.vazio()) {
            CompoundTag t = new CompoundTag();
            t.putInt("slot", slot);
            t.put("projeto", proj.salvar());
            l.add(t);
        }
        raiz(p).put(CHAVE, l);
    }

    public static void enviar(ServerPlayer p) {
        ModNetwork.sendTo(p, new Lista(todos(p)));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) enviar(p);
    }

    // ===================== Usar =====================

    public static void lancar(ServerPlayer p, int slot) {
        if (slot < 0 || slot >= Projeto.SLOTS) return;
        Projeto proj = todos(p)[slot];
        RingData ring = ModCapabilities.get(p).orElse(null);
        if (proj == null || proj.vazio() || ring == null || !ring.hasRing()) return;
        if (!ring.powerActive()) {
            p.displayClientMessage(Component.translatable("message.lanterna.power_needed"), true);
            return;
        }
        // arma, escudo e veículo: um de cada vez (usar de novo desfaz)
        var vab = p.getBoundingBox().inflate(64);
        for (ConstrutoProprio c : ConstrutoProprio.doDono(p.level(), p.getUUID(), vab)) {
            if (c.comp() == proj.comp && (proj.comp == Comportamento.ARMA || proj.comp == Comportamento.ESCUDO || proj.comp == Comportamento.VEICULO)) {
                c.acabar();
                return;
            }
        }
        long agora = p.level().getGameTime();
        long[] rec = RECARGA.computeIfAbsent(p.getUUID(), k -> new long[Projeto.SLOTS]);
        if (rec[slot] > agora) {
            p.displayClientMessage(Component.translatable("oficina.lanterna.recarga", (rec[slot] - agora + 19) / 20), true);
            return;
        }
        int custo = proj.custo();
        if (ring.charge() < custo && !p.isCreative()) {
            p.displayClientMessage(Component.translatable("message.lanterna.no_charge"), true);
            return;
        }
        float forca = Constructs.power(p, ring);
        ConstrutoProprio c = ConstrutoProprio.criar(p, proj, ring.color(), forca);
        switch (proj.comp) {
            case ARMA -> {
                Vec3 m = ConstrutoProprio.mao(p, 1f);
                c.setPos(m.x, m.y, m.z);
            }
            case ESCUDO -> c.setPos(p.getX(), p.getEyeY(), p.getZ());
            case PROJETIL -> c.lancar(p);
            case QUEDA -> c.preparaQueda(p);
            case VEICULO -> {
                c.setPos(p.getX(), p.getY(), p.getZ());
                c.setYRot(p.getYRot());
            }
            case ESTRUTURA -> c.colocar(p);
        }
        p.level().addFreshEntity(c);
        if (proj.comp == Comportamento.VEICULO) p.startRiding(c, true);
        if (!p.isCreative()) RingPowers.setCharge(p, ring, ring.charge() - com.lanterna.tropa.Nivel.custoComDesconto(p, custo));
        rec[slot] = agora + Math.round(proj.comp.recarga * com.lanterna.construct.fonte.ArvoreDaLuz.recarga(p));
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 1.6f);
        p.level().playSound(null, c.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1.2f);
        Affinity.add(p, 3);
        com.lanterna.mission.MissionManager.onConstruct(p, "meu");
    }

    /** O escudo desenhado segura o que vem da frente. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Vec3 de = e.getSource().getSourcePosition();
        if (de == null) return;
        for (ConstrutoProprio c : ConstrutoProprio.doDono(p.level(), p.getUUID(), p.getBoundingBox().inflate(6))) {
            if (c.comp() == Comportamento.ESCUDO && c.protegeDe(de)) {
                e.setAmount(e.getAmount() * 0.3f);
                p.level().playSound(null, p.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1f, 1.2f);
                return;
            }
        }
    }

    // ===================== Pacotes =====================

    /** Cliente → servidor: guardar (ou apagar, se vazio) um construto num espaço. */
    public record Salvar(int slot, Projeto projeto) {
        public static void encode(Salvar m, FriendlyByteBuf b) { b.writeVarInt(m.slot); m.projeto.escrever(b); }

        public static Salvar decode(FriendlyByteBuf b) { return new Salvar(b.readVarInt(), Projeto.ler(b)); }

        public static void handle(Salvar m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p == null || m.slot < 0 || m.slot >= Projeto.SLOTS) return;
                gravar(p, m.slot, m.projeto.vazio() ? null : m.projeto);
                enviar(p);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Cliente → servidor: golpe da arma desenhada (clique esquerdo). */
    public record Golpe() {
        public static void encode(Golpe m, FriendlyByteBuf b) {}

        public static Golpe decode(FriendlyByteBuf b) { return new Golpe(); }

        public static void handle(Golpe m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p == null) return;
                for (ConstrutoProprio c : ConstrutoProprio.doDono(p.level(), p.getUUID(), p.getBoundingBox().inflate(4)))
                    if (c.comp() == Comportamento.ARMA) c.golpear();
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Servidor → cliente: todos os construtos desenhados do jogador. */
    public record Lista(Projeto[] projetos) {
        public static void encode(Lista m, FriendlyByteBuf b) {
            for (Projeto p : m.projetos) {
                b.writeBoolean(p != null);
                if (p != null) p.escrever(b);
            }
        }

        public static Lista decode(FriendlyByteBuf b) {
            Projeto[] out = new Projeto[Projeto.SLOTS];
            for (int i = 0; i < Projeto.SLOTS; i++) if (b.readBoolean()) out[i] = Projeto.ler(b);
            return new Lista(out);
        }

        public static void handle(Lista m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.lanterna.client.oficina.OficinaCliente.receber(m.projetos)));
            ctx.get().setPacketHandled(true);
        }
    }

    private MeusConstrutos() {}
}
