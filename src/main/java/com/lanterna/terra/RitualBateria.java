package com.lanterna.terra;

import com.lanterna.LanternaMod;
import com.lanterna.block.ChiseledBatteryBlock;
import com.lanterna.fonte.FonteDados;
import com.lanterna.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O Ritual da Bateria: enche a Bateria Cinzelada de energia pura, em três etapas.
 *
 *  1. O altar. De noite, a céu aberto, a bateria sobre um bloco de ametista, com quatro velas acesas nos cantos
 *     (dois blocos para cada diagonal, na mesma altura da bateria). Clique nela com um Fragmento Estelar.
 *  2. A carga (1 minuto). Uma coluna de luz desce do céu; a luz atrai os Contaminados, em três ondas. Fique por perto
 *     (até 24 blocos). Se amanhecer, se você morrer ou se afastar demais, a carga se perde (e o fragmento também).
 *  3. A vontade. A bateria pulsa: agache do lado dela (até 3 blocos) por 5 segundos, sem sair.
 *
 * Pronta (carga 3), ela brilha e pode acordar o Anel Apagado.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RitualBateria {
    /** Duração da carga (ticks). */
    static final int CARGA = 1200;
    /** Tempo para a etapa da vontade (ticks). */
    static final int VONTADE_MAX = 1200, VONTADE = 100;
    private static final int[] ONDAS = {120, 520, 920};

    private static final class Ritual {
        final UUID jogador;
        final ServerLevel level;
        final BlockPos pos;
        int t, longe, vontade, onda;
        final List<UUID> inimigos = new ArrayList<>();

        Ritual(UUID jogador, ServerLevel level, BlockPos pos) {
            this.jogador = jogador;
            this.level = level;
            this.pos = pos;
        }
    }

    private static final Map<BlockPos, Ritual> ATIVOS = new HashMap<>();
    /** Para o teste automático: aceita o altar sem a noite. */
    public static boolean ignorarNoite;

    public static boolean emAndamento(BlockPos pos) {
        return ATIVOS.containsKey(pos);
    }

    /** As 4 velas dos cantos (diagonais a 2 blocos). */
    static BlockPos[] cantos(BlockPos b) {
        return new BlockPos[]{b.offset(2, 0, 2), b.offset(-2, 0, 2), b.offset(2, 0, -2), b.offset(-2, 0, -2)};
    }

    /** O que falta no altar (chave de mensagem) ou null se está tudo certo. */
    static String falta(ServerLevel l, BlockPos b) {
        if (!l.getBlockState(b.below()).is(Blocks.AMETHYST_BLOCK)) return "message.lanterna.ritual.ametista";
        for (BlockPos c : cantos(b)) {
            BlockState v = l.getBlockState(c);
            if (!(v.getBlock() instanceof CandleBlock) || !v.getValue(CandleBlock.LIT)) return "message.lanterna.ritual.velas";
        }
        if (!l.canSeeSky(b.above())) return "message.lanterna.ritual.ceu";
        if (!ignorarNoite && !l.isNight()) return "message.lanterna.ritual.noite";
        return null;
    }

    public static void iniciar(ServerPlayer p, BlockPos pos, ItemStack fragmento) {
        ServerLevel l = p.serverLevel();
        if (ATIVOS.containsKey(pos)) return;
        for (Ritual r : ATIVOS.values()) if (r.jogador.equals(p.getUUID())) {
            p.displayClientMessage(Component.translatable("message.lanterna.ritual.um_por_vez"), true);
            return;
        }
        String f = falta(l, pos);
        if (f != null) {
            p.displayClientMessage(Component.translatable(f), false);
            l.playSound(null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1f, 0.7f);
            return;
        }
        if (!p.getAbilities().instabuild) fragmento.shrink(1);
        ATIVOS.put(pos.immutable(), new Ritual(p.getUUID(), l, pos.immutable()));
        carga(l, pos, 1);
        l.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2f, 0.6f);
        l.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2f, 0.8f);
        p.sendSystemMessage(Component.translatable("message.lanterna.ritual.comecou").withStyle(s -> s.withColor(0xFFE6A0)));
    }

    private static void carga(ServerLevel l, BlockPos pos, int c) {
        BlockState st = l.getBlockState(pos);
        if (st.is(ModBlocks.CHISELED_BATTERY.get())) l.setBlockAndUpdate(pos, st.setValue(ChiseledBatteryBlock.CARGA, c));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ATIVOS.isEmpty()) return;
        MinecraftServer server = e.getServer();
        ATIVOS.values().removeIf(r -> tick(server, r));
    }

    /** true = o ritual acabou (deu certo ou falhou). */
    private static boolean tick(MinecraftServer server, Ritual r) {
        ServerLevel l = r.level;
        ServerPlayer p = server.getPlayerList().getPlayer(r.jogador);
        if (!l.getBlockState(r.pos).is(ModBlocks.CHISELED_BATTERY.get())) return falhar(r, p, "message.lanterna.ritual.quebrou");
        if (p == null || p.isDeadOrDying() || p.level() != l) return falhar(r, p, "message.lanterna.ritual.caiu");
        if (!ignorarNoite && !l.isNight()) return falhar(r, p, "message.lanterna.ritual.amanheceu");
        double d = p.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(r.pos));
        if (d > 24) {
            if (++r.longe % 20 == 1) p.displayClientMessage(Component.translatable("message.lanterna.ritual.volte", 10 - r.longe / 20), true);
            if (r.longe > 200) return falhar(r, p, "message.lanterna.ritual.longe");
        } else r.longe = 0;
        r.t++;
        efeitos(l, r);
        if (r.t <= CARGA) {
            if (r.onda < ONDAS.length && r.t == ONDAS[r.onda]) ondas(l, r, p, r.onda++);
            if (r.t == CARGA / 2) carga(l, r.pos, 2);
            if (r.t % 20 == 0) {
                int s = (CARGA - r.t) / 20;
                p.displayClientMessage(Component.translatable("message.lanterna.ritual.carga", 100 * r.t / CARGA, s), true);
            }
            if (r.t == CARGA) {
                l.playSound(null, r.pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 2f, 1.2f);
                p.sendSystemMessage(Component.translatable("message.lanterna.ritual.vontade").withStyle(s -> s.withColor(0xFFE6A0)));
            }
            return false;
        }
        // Etapa 3: a vontade (agachado do lado da bateria)
        if (p.isShiftKeyDown() && d <= 3.2) r.vontade++;
        else r.vontade = Math.max(0, r.vontade - 2);
        if (r.t % 5 == 0) p.displayClientMessage(Component.translatable("message.lanterna.ritual.firme", 100 * r.vontade / VONTADE), true);
        if (r.vontade >= VONTADE) {
            concluir(l, r, p);
            return true;
        }
        if (r.t > CARGA + VONTADE_MAX) return falhar(r, p, "message.lanterna.ritual.fraco");
        return false;
    }

    private static void ondas(ServerLevel l, Ritual r, ServerPlayer p, int n) {
        int quantos = 3 + n;
        var rng = l.getRandom();
        for (int i = 0; i < quantos; i++) {
            double a = rng.nextDouble() * Math.PI * 2, raio = 10 + rng.nextDouble() * 5;
            int x = r.pos.getX() + (int) Math.round(Math.cos(a) * raio), z = r.pos.getZ() + (int) Math.round(Math.sin(a) * raio);
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            EntityType<? extends Mob> tipo = n == 2 && i == 0 ? EntityType.ZOMBIE : switch (rng.nextInt(4)) {
                case 0 -> EntityType.SKELETON;
                case 1 -> EntityType.SPIDER;
                default -> EntityType.ZOMBIE;
            };
            Mob m = tipo.create(l);
            if (m == null) continue;
            m.moveTo(x + 0.5, y, z + 0.5, rng.nextFloat() * 360, 0);
            m.finalizeSpawn(l, l.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null);
            Meteoros.contaminar(m, n == 2 && i == 0);
            m.setTarget(p);
            l.addFreshEntity(m);
            l.sendParticles(ParticleTypes.LARGE_SMOKE, x + 0.5, y + 1, z + 0.5, 12, 0.3, 0.6, 0.3, 0.02);
            r.inimigos.add(m.getUUID());
        }
        l.playSound(null, r.pos, SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.HOSTILE, 1.4f, 0.6f);
        p.displayClientMessage(Component.translatable("message.lanterna.ritual.onda", n + 1, 3), false);
    }

    private static void efeitos(ServerLevel l, Ritual r) {
        var c = net.minecraft.world.phys.Vec3.atCenterOf(r.pos);
        float forca = Math.min(1f, r.t / (float) CARGA);
        if (r.t % 2 == 0) {
            // coluna de luz descendo do céu
            for (int k = 0; k < 4; k++) {
                double y = c.y + 0.5 + l.getRandom().nextDouble() * 40;
                l.sendParticles(ParticleTypes.END_ROD, c.x, y, c.z, 1, 0.08, 0, 0.08, 0);
            }
        }
        if (r.t % 4 == 0) {
            // as velas mandam fios de luz para a bateria
            for (BlockPos v : cantos(r.pos)) {
                var a = net.minecraft.world.phys.Vec3.atCenterOf(v).add(0, 0.2, 0);
                for (int k = 0; k <= 6; k++) {
                    var q = a.lerp(c.add(0, 0.3, 0), k / 6.0);
                    l.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.9f, 0.55f), 0.7f), q.x, q.y, q.z, 1, 0, 0, 0, 0);
                }
            }
        }
        if (r.t % 40 == 0) l.playSound(null, r.pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.2f + forca, 0.6f + forca * 0.6f);
        if (r.t > CARGA && r.t % 10 == 0) {
            l.sendParticles(ParticleTypes.FLASH, c.x, c.y + 0.4, c.z, 1, 0, 0, 0, 0);
            l.playSound(null, r.pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 1.4f);
        }
    }

    private static void concluir(ServerLevel l, Ritual r, ServerPlayer p) {
        carga(l, r.pos, 3);
        LightningBolt raio = EntityType.LIGHTNING_BOLT.create(l);
        if (raio != null) {
            raio.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(r.pos));
            raio.setVisualOnly(true);
            l.addFreshEntity(raio);
        }
        var c = net.minecraft.world.phys.Vec3.atCenterOf(r.pos);
        l.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 80, 0.4, 0.6, 0.4, 0.25);
        l.playSound(null, r.pos, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 1.2f, 1.1f);
        FonteDados.chamado(p, "terra:ritual", 1);
        p.sendSystemMessage(Component.translatable("message.lanterna.ritual.pronta").withStyle(s -> s.withColor(0xFFE6A0)));
        LanternaMod.LOGGER.info("Lanterna: {} carregou uma Bateria Cinzelada em {}", p.getGameProfile().getName(), r.pos);
    }

    private static boolean falhar(Ritual r, ServerPlayer p, String motivo) {
        carga(r.level, r.pos, 0);
        r.level.playSound(null, r.pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2f, 0.6f);
        for (BlockPos v : cantos(r.pos)) {
            BlockState st = r.level.getBlockState(v);
            if (st.getBlock() instanceof CandleBlock) r.level.setBlockAndUpdate(v, st.setValue(CandleBlock.LIT, false));
        }
        if (p != null) p.sendSystemMessage(Component.translatable(motivo).withStyle(s -> s.withColor(0xFF8080)));
        return true;
    }

    private RitualBateria() {}
}
