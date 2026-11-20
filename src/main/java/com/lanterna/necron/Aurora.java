package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.fonte.FonteDados;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import com.lanterna.terra.TerraSite;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.*;

/**
 * 0.56.2 — Capítulo 2, A Queda, na Vila Aurora:
 *
 *  necron_a1  Pânico em Aurora      falar com o Sgt. Rui (o que caiu do céu, os moradores presos nos sítios)
 *  necron_a2  Cercados              quatro moradores presos fora da vila, cada um cercado por servos. Com um anel só não dá
 *                                   para ferir os servos: é preciso chegar até o morador, segurar o clique nele (envolver em
 *                                   luz por 3 segundos, apanhando) e ele voa para a vila numa bolha
 *  necron_a3  A Doutora             falar com a Dra. Lívia (ela mediu a queda)
 *  necron_a4  O Rastro do Cometa    três marcas de cristal negro entre a vila e a cratera: ficar perto de cada uma e deixar
 *                                   o anel ler (4 segundos); a última revela a cratera
 * Depois disso vem "A Queda do Necron" (chegar à cratera).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Aurora {
    public static final String REFEM = "lanterna_refem", CERCO = "lanterna_cerco";
    private static final String[] NOMES = {"Seu Ari", "Dona Neide", "Lia", "Beto"};
    private static final int RESGATE = 60, LEITURA = 80, RAIO_MARCA = 7;

    private static final class Resgate { UUID morador; int t; int voo = -1; Vec3 de; }
    private static final Map<UUID, Resgate> RESGATES = new HashMap<>();
    /** As bolhas voando para a vila (o jogador já pode começar o próximo resgate). */
    private static final List<Object[]> VOOS = new ArrayList<>();
    private static final Map<UUID, int[]> LEITURAS = new HashMap<>();

    // ===================== lugares =====================

    @Nullable
    public static BlockPos centro() { return TerraSite.centro; }

    /** Onde o morador i (1..4) está preso: fora da vila, nas quatro diagonais. */
    @Nullable
    public static BlockPos refem(int i) {
        BlockPos c = centro();
        if (c == null) return null;
        double a = Math.toRadians(45 + 90 * (i - 1));
        return new BlockPos(c.getX() + (int) (Math.cos(a) * 72), 0, c.getZ() + (int) (Math.sin(a) * 72));
    }

    /** A marca i (1..3) do rastro: no caminho entre a vila e a cratera. */
    @Nullable
    public static BlockPos marca(int i) {
        BlockPos c = centro(), k = MissoesNecron.centro;
        if (c == null || k == null) return null;
        double f = i == 1 ? 0.3 : i == 2 ? 0.55 : 0.8;
        // um pouco de lado, para o caminho não ser uma linha reta
        double dx = k.getX() - c.getX(), dz = k.getZ() - c.getZ(), len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        double lado = (i == 2 ? -1 : 1) * 40;
        return new BlockPos((int) (c.getX() + dx * f - dz / len * lado), 0, (int) (c.getZ() + dz * f + dx / len * lado));
    }

    private static BlockPos noChao(ServerLevel l, BlockPos p) {
        l.getChunk(p.getX() >> 4, p.getZ() >> 4);
        return new BlockPos(p.getX(), l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()), p.getZ());
    }

    // ===================== tick =====================

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.level().dimension() != Level.OVERWORLD) return;
        LoreData d = MissionManager.data(p);
        if (d == null) return;
        if (d.isActive("necron_a2")) cerco(p);
        else if (p.tickCount % 40 == 0) limparCerco(p);
        if (d.isActive("necron_a4")) rastro(p);
    }

    // ===================== Cercados =====================

    private static void cerco(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        BlockPos c = centro();
        if (c == null) return;
        // monta o cerco quando o jogador chega perto da vila (uma vez; volta se sumir)
        if (p.tickCount % 40 == 5 && p.blockPosition().distSqr(c) < 260 * 260) {
            for (int i = 1; i <= 4; i++) {
                if (FonteDados.chamado(p, "necron:resgate_" + i) >= 1) continue;
                // só cria (ou recria) quando aquele pedaço do mundo está carregado com as criaturas dele: senão o morador
                // que já existe ainda não apareceu na busca e nasceria um repetido
                BlockPos r0 = refem(i);
                if (!l.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(r0.getX() >> 4, r0.getZ() >> 4))) continue;
                if (morador(l, p, i) != null) continue;
                BlockPos onde = noChao(l, r0);
                Villager v = EntityType.VILLAGER.create(l);
                if (v == null) continue;
                v.moveTo(onde.getX() + 0.5, onde.getY(), onde.getZ() + 0.5, l.random.nextFloat() * 360, 0);
                v.setNoAi(true);
                v.setInvulnerable(true);
                v.setPersistenceRequired();
                v.setCustomName(Component.literal(NOMES[i - 1]).withStyle(ChatFormatting.YELLOW));
                v.setCustomNameVisible(true);
                v.addTag(REFEM);
                v.addTag(REFEM + ":" + p.getUUID() + ":" + i);
                l.addFreshEntity(v);
                // três servos em volta
                for (int k = 0; k < 3; k++) {
                    Mob m = Cratera.novoServo(l);
                    if (m == null) continue;
                    double a = Math.PI * 2 * k / 3;
                    BlockPos sp = noChao(l, onde.offset((int) (Math.cos(a) * 5), 0, (int) (Math.sin(a) * 5)));
                    m.moveTo(sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, 0, 0);
                    m.addTag(Cratera.SERVO);
                    m.addTag(CERCO);
                    m.setPersistenceRequired();
                    l.addFreshEntity(m);
                }
            }
        }
        // a luz que mostra onde estão os presos (vista de longe)
        if (p.tickCount % 15 == 0) {
            for (int i = 1; i <= 4; i++) {
                Villager v = morador(l, p, i);
                if (v == null || FonteDados.chamado(p, "necron:resgate_" + i) >= 1) continue;
                for (int k = 0; k < 14; k++) l.sendParticles(p, ParticleTypes.END_ROD, true, v.getX(), v.getY() + 2 + k * 1.5, v.getZ(), 1, 0.05, 0.2, 0.05, 0);
            }
        }
        voos(l, p);
        // o resgate em andamento
        Resgate r = RESGATES.get(p.getUUID());
        if (r == null) return;
        Entity ent = l.getEntity(r.morador);
        if (!(ent instanceof Villager v)) { RESGATES.remove(p.getUUID()); return; }
        int cor = com.lanterna.capability.ModCapabilities.get(p).map(x -> x.color()).orElse(0x2BFF4A);
        DustParticleOptions luz = new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 1.4f);
        if (r.voo < 0) {
            if (p.distanceTo(v) > 4.5) {
                RESGATES.remove(p.getUUID());
                p.displayClientMessage(Component.translatable("necron.lanterna.resgate_soltou").withStyle(ChatFormatting.RED), true);
                return;
            }
            r.t++;
            for (int k = 0; k < 6; k++) {
                double a = l.random.nextDouble() * Math.PI * 2, b = l.random.nextDouble() * Math.PI;
                l.sendParticles(luz, v.getX() + Math.cos(a) * Math.sin(b) * 1.1, v.getY() + 1 + Math.cos(b) * 1.1, v.getZ() + Math.sin(a) * Math.sin(b) * 1.1, 1, 0, 0, 0, 0);
            }
            int pct = r.t * 100 / RESGATE;
            if (r.t % 5 == 0) p.displayClientMessage(Component.translatable("necron.lanterna.resgate_progresso", pct).withStyle(s -> s.withColor(cor)), true);
            if (r.t >= RESGATE) {
                r.voo = 0;
                r.de = v.position();
                l.playSound(null, v.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.5f);
                v.setNoGravity(true);
                RESGATES.remove(p.getUUID());
                VOOS.add(new Object[]{p.getUUID(), r});
            }
        }
    }

    /** As bolhas voando: levam o morador até a vila e contam o resgate. */
    private static void voos(ServerLevel l, ServerPlayer p) {
        BlockPos c = centro();
        int cor = com.lanterna.capability.ModCapabilities.get(p).map(x -> x.color()).orElse(0x2BFF4A);
        DustParticleOptions luz = new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 1.4f);
        for (Iterator<Object[]> it = VOOS.iterator(); it.hasNext(); ) {
            Object[] o = it.next();
            if (!p.getUUID().equals(o[0])) continue;
            Resgate r = (Resgate) o[1];
            Entity ent = l.getEntity(r.morador);
            if (!(ent instanceof Villager v) || c == null) { it.remove(); continue; }
            voar(l, p, v, r, c, luz, it);
        }
    }

    private static void voar(ServerLevel l, ServerPlayer p, Villager v, Resgate r, BlockPos c, DustParticleOptions luz, Iterator<Object[]> it) {
        // a bolha voa para a vila
        r.voo++;
        double k = Math.min(1, r.voo / 70.0);
        Vec3 alvo = Vec3.atBottomCenterOf(c).add(0, 1, 0);
        Vec3 pos = r.de.lerp(alvo, k * k).add(0, Math.sin(k * Math.PI) * 25, 0);
        v.moveTo(pos.x, pos.y, pos.z, v.getYRot(), 0);
        l.sendParticles(luz, pos.x, pos.y + 1, pos.z, 12, 0.6, 0.6, 0.6, 0);
        if (r.voo >= 70) {
            int i = indice(v);
            it.remove();
            v.discard();
            if (i > 0) FonteDados.chamado(p, "necron:resgate_" + i, 1);
            int feitos = 0;
            for (int j = 1; j <= 4; j++) if (FonteDados.chamado(p, "necron:resgate_" + j) >= 1) feitos++;
            p.sendSystemMessage(Component.translatable("necron.lanterna.resgate_ok", NOMES[Math.max(0, i - 1)], feitos).withStyle(ChatFormatting.GREEN));
            l.playSound(null, BlockPos.containing(alvo), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.4f);
        }
    }

    @Nullable
    private static Villager morador(ServerLevel l, ServerPlayer p, int i) {
        BlockPos onde = refem(i);
        if (onde == null) return null;
        String tag = REFEM + ":" + p.getUUID() + ":" + i;
        List<Villager> vs = l.getEntitiesOfClass(Villager.class, new AABB(onde).inflate(40, 300, 40), v -> v.getTags().contains(tag));
        return vs.isEmpty() ? null : vs.get(0);
    }

    private static int indice(Villager v) {
        for (String t : v.getTags()) if (t.startsWith(REFEM + ":")) return Integer.parseInt(t.substring(t.lastIndexOf(':') + 1));
        return 0;
    }

    /** Segurar o clique no morador preso: começa a envolver em luz. */
    @SubscribeEvent
    public static void clicou(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getTarget() instanceof Villager v) || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!v.getTags().contains(REFEM + ":" + p.getUUID() + ":" + indice(v))) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (RESGATES.containsKey(p.getUUID())) return;
        var r = com.lanterna.capability.ModCapabilities.get(p).orElse(null);
        if (r == null || !r.hasRing()) return;
        Resgate g = new Resgate();
        g.morador = v.getUUID();
        RESGATES.put(p.getUUID(), g);
        p.displayClientMessage(Component.translatable("necron.lanterna.resgate_comeca").withStyle(ChatFormatting.GREEN), true);
    }

    /** O cerco some depois que todos foram salvos (ou a missão acabou). */
    private static void limparCerco(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel l)) return;
        BlockPos c = centro();
        if (c == null || p.blockPosition().distSqr(c) > 260 * 260) return;
        AABB area = new AABB(c).inflate(140, 120, 140);
        for (Mob m : l.getEntitiesOfClass(Mob.class, area, m -> m.getTags().contains(CERCO))) m.discard();
        for (Villager v : l.getEntitiesOfClass(Villager.class, area, v -> v.getTags().contains(REFEM + ":" + p.getUUID() + ":" + indice(v)))) v.discard();
    }

    // ===================== O Rastro do Cometa =====================

    private static void rastro(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        int[] lido = LEITURAS.computeIfAbsent(p.getUUID(), k -> new int[4]);
        for (int i = 1; i <= 3; i++) {
            if (FonteDados.chamado(p, "necron:rastro_" + i) >= 1) continue;
            BlockPos m = marca(i);
            if (m == null) return;
            double dx = p.getX() - m.getX(), dz = p.getZ() - m.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            // a marca só existe depois de lida a anterior; o feixe mostra onde ela está
            boolean anteriorLida = i == 1 || FonteDados.chamado(p, "necron:rastro_" + (i - 1)) >= 1;
            if (!anteriorLida) return;
            if (dist < 140 && p.tickCount % 20 == 0) construirMarca(l, m);
            if (p.tickCount % 15 == 0 && dist < 500) {
                BlockPos ch = noChao(l, m);
                for (int k = 0; k < 16; k++) l.sendParticles(p, new DustParticleOptions(new Vector3f(0.5f, 0.15f, 0.75f), 2.5f), true,
                        ch.getX() + 0.5, ch.getY() + 2 + k * 2, ch.getZ() + 0.5, 1, 0.1, 0.3, 0.1, 0);
            }
            if (dist < RAIO_MARCA) {
                lido[i]++;
                if (lido[i] % 10 == 0) p.displayClientMessage(Component.translatable("necron.lanterna.rastro_lendo", lido[i] * 100 / LEITURA).withStyle(ChatFormatting.LIGHT_PURPLE), true);
                if (lido[i] >= LEITURA) {
                    FonteDados.chamado(p, "necron:rastro_" + i, 1);
                    p.sendSystemMessage(Component.translatable("necron.lanterna.rastro_" + i).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                    l.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.6f);
                }
            } else lido[i] = Math.max(0, lido[i] - 1);
            return;   // só uma marca por vez
        }
    }

    /** A marca: um buraco raso de pedra negra com estilhaços de obsidiana chorona espetados (uma vez). */
    private static void construirMarca(ServerLevel l, BlockPos m) {
        BlockPos ch = noChao(l, m);
        if (l.getBlockState(ch.below()).is(Blocks.CRYING_OBSIDIAN) || l.getBlockState(ch).is(Blocks.CRYING_OBSIDIAN)) return;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 5) continue;
                BlockPos b = noChao(l, ch.offset(dx, 0, dz)).below();
                l.setBlock(b, (dx + dz) % 2 == 0 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.BASALT.defaultBlockState(), 3);
            }
        l.setBlock(ch.below(), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
        l.setBlock(ch, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
        l.setBlock(ch.above(), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
        l.setBlock(ch.offset(1, 0, 1), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
        l.setBlock(ch.offset(-1, 0, 0), Blocks.SOUL_FIRE.defaultBlockState(), 3);
    }

    /** Teste: zera o capítulo. */
    public static void zerar(ServerPlayer p) {
        RESGATES.remove(p.getUUID());
        VOOS.removeIf(o -> p.getUUID().equals(o[0]));
        LEITURAS.remove(p.getUUID());
        for (int i = 1; i <= 4; i++) FonteDados.chamado(p, "necron:resgate_" + i, 0);
        for (int i = 1; i <= 3; i++) FonteDados.chamado(p, "necron:rastro_" + i, 0);
    }

    private Aurora() {}
}
