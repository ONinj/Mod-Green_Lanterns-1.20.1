package com.lanterna.necron;

import com.lanterna.LanternaMod;
import com.lanterna.fonte.FonteDados;
import com.lanterna.lore.LoreWorldData;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import com.lanterna.vida.Emocoes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * 0.56.4 — Capítulo 4, antes da Defesa da Tropa: As Âncoras da Morte.
 *
 * O Necron cravou três âncoras no planeta da Tropa do jogador (longe da cidade). Cada uma é um pilar de pedra negra com
 * um núcleo de obsidiana chorona, e manda um feixe roxo para o céu (para a fenda sobre a Bateria Central). Cinco servos
 * guardam cada âncora: enquanto algum estiver vivo, o núcleo não quebra. Derrotados os guardas, quebrar o núcleo destrói
 * a âncora. As três destruídas, a fenda enfraquece e a defesa começa (necron_4_<tropa>).
 *
 * As âncoras são do mundo: destruída por um jogador, conta para todos.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Ancoras {
    public static final String GUARDA = "lanterna_guarda_ancora";
    private static final int GUARDAS = 5;

    /** Onde fica a âncora i (1..3) do planeta (longe da cidade, em volta dela). */
    public static BlockPos posicao(int i) {
        BlockPos h = PlanetLore.HUB;
        double a = Math.toRadians(30 + 120 * (i - 1));
        return new BlockPos(h.getX() + (int) (Math.cos(a) * 150), 0, h.getZ() + (int) (Math.sin(a) * 150));
    }

    private static String chave(PlanetLore p, int i) { return "saga:ancora:" + p.planet + ":" + i; }

    public static boolean destruida(net.minecraft.server.MinecraftServer s, PlanetLore p, int i) {
        return LoreWorldData.get(s).siteBuilt(chave(p, i) + ":fim");
    }

    @Nullable
    private static BlockPos nucleo(ServerLevel l, int i) {
        BlockPos b = posicao(i);
        if (!l.isLoaded(b)) return null;
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, b.getX() + 4, b.getZ() + 4);
        return new BlockPos(b.getX(), y + 6, b.getZ());
    }

    private static String tropa(ServerPlayer p) {
        String t = SagaNecron.tag(p).getString("tropa");
        return Emocoes.aspecto(t) ? t : "green";
    }

    // ===================== tick =====================

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 7) return;
        LoreData d = MissionManager.data(p);
        if (d == null) return;
        String t = tropa(p);
        if (!d.isActive("necron_4a_" + t)) return;
        PlanetLore casa = PlanetLore.byCorps(LanternaMod.id(t));
        if (casa == null) return;
        // âncoras já destruídas por outra pessoa contam para este jogador também
        for (int i = 1; i <= 3; i++) if (destruida(p.server, casa, i) && FonteDados.chamado(p, "necron:ancora_" + i) < 1) FonteDados.chamado(p, "necron:ancora_" + i, 1);
        if (PlanetLore.byDimension(p.level().dimension()) != casa) return;
        ServerLevel l = p.serverLevel();
        for (int i = 1; i <= 3; i++) {
            if (destruida(p.server, casa, i)) continue;
            BlockPos b = posicao(i);
            double dx = p.getX() - b.getX(), dz = p.getZ() - b.getZ(), dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 220) continue;
            BlockPos n = nucleo(l, i);
            if (n == null) continue;
            if (!LoreWorldData.get(p.server).siteBuilt(chave(casa, i))) {
                construir(l, n);
                LoreWorldData.get(p.server).markSite(chave(casa, i));
            }
            // o feixe roxo até o céu (visto de longe)
            if (p.tickCount % 20 == 7) {
                DustParticleOptions roxo = new DustParticleOptions(new Vector3f(0.55f, 0.12f, 0.8f), 3f);
                for (int k = 0; k < 30; k++) l.sendParticles(p, roxo, true, n.getX() + 0.5, n.getY() + 2 + k * 3, n.getZ() + 0.5, 1, 0.15, 0.6, 0.15, 0);
            }
            // os guardas (nascem quando o jogador chega perto; não voltam depois de derrotados)
            if (dist < 70) guardas(l, p, casa, i, n);
        }
    }

    /**
     * A âncora: base larga de pedra negra, uma coluna de 6 blocos com veios de obsidiana chorona, o núcleo no alto
     * (obsidiana chorona com vidro preto em volta), quatro espinhos inclinados cravados no chão e correntes.
     */
    private static void construir(ServerLevel l, BlockPos n) {
        BlockPos base = n.below(6);
        var pedra = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 10) continue;
                l.setBlock(base.offset(dx, -1, dz), (dx + dz) % 3 == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : pedra, 3);
                for (int y = 0; y < 9; y++) l.setBlock(base.offset(dx, y, dz), Blocks.AIR.defaultBlockState(), 3);
            }
        for (int y = 0; y < 6; y++) l.setBlock(base.above(y), y % 2 == 1 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 3);
        l.setBlock(n, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
        l.setBlock(n.above(), Blocks.BLACK_STAINED_GLASS.defaultBlockState(), 3);
        for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) l.setBlock(n.relative(d), Blocks.BLACK_STAINED_GLASS.defaultBlockState(), 3);
        // quatro espinhos inclinados (diagonais), da base para fora e para cima
        int[][] diag = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        for (int[] c : diag) {
            for (int k = 1; k <= 4; k++) {
                BlockPos b = base.offset(c[0] * (k + 1) / 2 + c[0], k - 1, c[1] * (k + 1) / 2 + c[1]);
                l.setBlock(b, k < 4 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), 3);
            }
            l.setBlock(base.offset(c[0], 4, c[1]), Blocks.CHAIN.defaultBlockState(), 3);
        }
        int[][] cantos = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}};
        for (int[] c : cantos) l.setBlock(base.offset(c[0], 0, c[1]), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
    }

    private static void guardas(ServerLevel l, ServerPlayer p, PlanetLore casa, int i, BlockPos n) {
        String tag = GUARDA + ":" + casa.planet + ":" + i;
        String feito = chave(casa, i) + ":guardas";
        if (LoreWorldData.get(p.server).siteBuilt(feito)) return;
        if (!l.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(n.getX() >> 4, n.getZ() >> 4))) return;
        if (!l.getEntitiesOfClass(Mob.class, new AABB(n).inflate(40), m -> m.getTags().contains(tag)).isEmpty()) return;
        for (int k = 0; k < GUARDAS; k++) {
            Mob m = Cratera.novoServo(l);
            if (m == null) continue;
            double a = Math.PI * 2 * k / GUARDAS;
            int x = n.getX() + (int) (Math.cos(a) * 6), z = n.getZ() + (int) (Math.sin(a) * 6);
            m.moveTo(x + 0.5, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5, 0, 0);
            m.addTag(Cratera.SERVO);
            m.addTag(GUARDA);
            m.addTag(tag);
            m.setPersistenceRequired();
            l.addFreshEntity(m);
        }
        LoreWorldData.get(p.server).markSite(feito);
        p.sendSystemMessage(Component.translatable("necron.lanterna.ancora_guardas").withStyle(ChatFormatting.DARK_PURPLE));
    }

    // ===================== quebrar o núcleo =====================

    @SubscribeEvent
    public static void quebrar(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel l)) return;
        PlanetLore aqui = PlanetLore.byDimension(l.dimension());
        if (aqui == null) return;
        for (int i = 1; i <= 3; i++) {
            if (destruida(p.server, aqui, i) || !LoreWorldData.get(p.server).siteBuilt(chave(aqui, i))) continue;
            BlockPos n = nucleo(l, i);
            if (n == null || !(e.getPos().equals(n) || e.getPos().equals(n.above()) || e.getPos().equals(n.below()))) {
                // as outras peças da âncora também não quebram enquanto ela existe
                BlockPos b = posicao(i);
                if (n != null && Math.abs(e.getPos().getX() - b.getX()) <= 3 && Math.abs(e.getPos().getZ() - b.getZ()) <= 3
                        && e.getPos().getY() >= n.getY() - 7 && e.getPos().getY() <= n.getY() + 2) e.setCanceled(true);
                continue;
            }
            String tag = GUARDA + ":" + aqui.planet + ":" + i;
            int vivos = l.getEntitiesOfClass(Mob.class, new AABB(n).inflate(60), m -> m.getTags().contains(tag)).size();
            if (vivos > 0) {
                e.setCanceled(true);
                p.displayClientMessage(Component.translatable("necron.lanterna.ancora_protegida", vivos).withStyle(ChatFormatting.DARK_PURPLE), true);
                l.playSound(null, n, SoundEvents.SHIELD_BLOCK, SoundSource.BLOCKS, 1f, 0.5f);
                return;
            }
            destruir(l, p, aqui, i, n);
            return;
        }
    }

    private static void destruir(ServerLevel l, ServerPlayer p, PlanetLore casa, int i, BlockPos n) {
        LoreWorldData.get(p.server).markSite(chave(casa, i) + ":fim");
        BlockPos base = n.below(6);
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                for (int y = 0; y < 9; y++) {
                    BlockPos b = base.offset(dx, y, dz);
                    if (!l.getBlockState(b).isAir()) l.setBlock(b, y == 0 && l.random.nextBoolean() ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                }
        int cor = com.lanterna.capability.ModCapabilities.get(p).map(r -> r.color()).orElse(0xFFFFFF);
        DustParticleOptions luz = new DustParticleOptions(new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f), 3f);
        l.sendParticles(luz, n.getX() + 0.5, n.getY(), n.getZ() + 0.5, 150, 2, 2, 2, 0);
        l.sendParticles(ParticleTypes.SOUL, n.getX() + 0.5, n.getY(), n.getZ() + 0.5, 60, 1.5, 1.5, 1.5, 0.1);
        l.playSound(null, n, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.5f, 0.8f);
        l.playSound(null, n, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2f, 0.6f);
        int feitas = 0;
        for (int j = 1; j <= 3; j++) if (destruida(p.server, casa, j)) feitas++;
        for (ServerPlayer q : l.players()) {
            if (q.distanceToSqr(n.getX(), n.getY(), n.getZ()) > 120 * 120 && q != p) continue;
            FonteDados.chamado(q, "necron:ancora_" + i, 1);
            q.sendSystemMessage(Component.translatable("necron.lanterna.ancora_destruida", feitas).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (feitas >= 3) for (ServerPlayer q : l.players()) q.sendSystemMessage(Component.translatable("necron.lanterna.ancora_fala_fim")
                .withStyle(s -> s.withColor(casa.corps.color())));
    }

    /** Teste: as âncoras voltam (as peças são reconstruídas quando alguém chega perto). */
    public static void zerar(net.minecraft.server.MinecraftServer s, PlanetLore p) {
        for (int i = 1; i <= 3; i++) {
            LoreWorldData.get(s).resetSite(chave(p, i));
            LoreWorldData.get(s).resetSite(chave(p, i) + ":fim");
            LoreWorldData.get(s).resetSite(chave(p, i) + ":guardas");
        }
    }

    private Ancoras() {}
}
