package com.lanterna.mogo;

import com.lanterna.LanternaMod;
import com.lanterna.capability.ModCapabilities;
import com.lanterna.capability.RingData;
import com.lanterna.corps.BuiltinCorps;
import com.lanterna.fonte.FonteDados;
import com.lanterna.lore.LoreItems;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.Mission;
import com.lanterna.mission.MissionManager;
import com.lanterna.mission.Objective;
import com.lanterna.mission.Objective.Type;
import com.lanterna.space.SpaceBodies;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A linha do Mogo (só Lanternas Verdes; o convite chega no nível 18). Pouca conversa, muita ação:
 *  mogo_1  A Chegada pelo Cinturão   atravessar a tempestade de destroços em volta do Mogo e pousar
 *  mogo_2  A Corrida da Ferida       destruir os 8 focos da infecção na faixa de jade antes do tempo acabar
 *  mogo_3  O Chão que se Move        atravessar o cânion pela ponte que desaba (o Mogo segura o voo)
 *  mogo_4  A Prova do Mogo           o planeta luta com você: quebre os 4 olhos de cristal (ele não deixa morrer)
 *  mogo_5  O Cerco                   defender as torres da faixa de 3 levas da Tropa Amarela
 *  mogo_6  O Mergulho no Núcleo      descer pela fenda e vencer a Ferida; o Chamado do Mogo acaba com ela
 * Recompensas: Jade Viva na 2, 4 e 6 (as três completam o Selo) e o Vínculo (Segunda Chance) na 6.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MogoMissoes {
    public static final String LINHA = "mogo";
    public static final int COR = 0x3CFF7A;
    public static final int NIVEL = 18;
    private static final String OFERECIDA = "lanterna_mogo_oferecida";

    public static boolean eDoMogo(String id) {
        return id.startsWith("mogo_");
    }

    private static final Map<String, Mission> CACHE = new HashMap<>();

    @Nullable
    public static Mission mission(String id) {
        return CACHE.computeIfAbsent(id, MogoMissoes::criar);
    }

    @Nullable
    private static Mission criar(String id) {
        return switch (id) {
            case "mogo_1" -> m(id, "mogo:cinturao", List.of(() -> new ItemStack(LoreItems.MERITO.get(), 2)), "mogo_2");
            case "mogo_2" -> m(id, "mogo:corrida", List.of(() -> new ItemStack(JadeViva.ITEM.get()), () -> new ItemStack(LoreItems.MERITO.get(), 2)), "mogo_3");
            case "mogo_3" -> m(id, "mogo:ponte", List.of(() -> new ItemStack(LoreItems.MERITO.get(), 3)), "mogo_4");
            case "mogo_4" -> m(id, "mogo:prova", List.of(() -> new ItemStack(JadeViva.ITEM.get()), () -> new ItemStack(LoreItems.MERITO.get(), 3)), "mogo_5");
            case "mogo_5" -> m(id, "mogo:cerco", List.of(() -> new ItemStack(LoreItems.MERITO.get(), 4)), "mogo_6");
            case "mogo_6" -> m(id, "mogo:nucleo", List.of(() -> new ItemStack(JadeViva.ITEM.get()), () -> new ItemStack(LoreItems.MERITO.get(), 5)), null);
            default -> null;
        };
    }

    private static Mission m(String id, String flag, List<java.util.function.Supplier<ItemStack>> itens, @Nullable String next) {
        return new Mission(id, LINHA, COR, List.of(Objective.of(Type.FONTE, flag, 1, "mogo", null)), () -> {
            List<ItemStack> out = new ArrayList<>();
            for (var s : itens) out.add(s.get());
            return out;
        }, 80, next, null);
    }

    /** Teste/admin: marca as anteriores como feitas e começa a missão n. */
    public static void pular(ServerPlayer p, int n) {
        String[] flags = {"mogo:cinturao", "mogo:corrida", "mogo:ponte", "mogo:prova", "mogo:cerco", "mogo:nucleo"};
        p.getPersistentData().putBoolean(OFERECIDA, true);
        LoreData d = MissionManager.data(p);
        for (int i = 1; i <= 6; i++) if (d != null && d.isActive("mogo_" + i)) MissionManager.abandon(p, "mogo_" + i);
        for (int i = 0; i < n - 1; i++) FonteDados.chamado(p, flags[i], 1);
        for (int i = n - 1; i < 6; i++) FonteDados.chamado(p, flags[i], 0);
        MissionManager.start(p, "mogo_" + n);
    }

    // ===================== Voz do Mogo =====================

    /** O Mogo fala pela terra: título na tela e a fala no chat. */
    public static void falar(ServerPlayer p, String chave, boolean titulo) {
        Component fala = Component.translatable("mogo.lanterna.voz." + chave);
        p.sendSystemMessage(Component.translatable("mogo.lanterna.voz", fala).withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));
        if (titulo) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
            p.connection.send(new ClientboundSetSubtitleTextPacket(fala.copy().withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC)));
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.AMBIENT, 1.4f, 0.5f);
    }

    private static void titulo(ServerPlayer p, String chave, int cor) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(chave).withStyle(s -> s.withColor(cor))));
    }

    static boolean verde(ServerPlayer p) {
        RingData r = ModCapabilities.get(p).orElse(null);
        return r != null && r.hasRing() && BuiltinCorps.GREEN.id().equals(r.corps());
    }

    static boolean ativa(ServerPlayer p, String id) {
        LoreData d = MissionManager.data(p);
        return d != null && d.isActive(id);
    }

    static void cumprir(ServerPlayer p, String flag) {
        if (FonteDados.chamado(p, flag) >= 1) return;
        FonteDados.chamado(p, flag, 1);
    }

    static boolean noMogo(Entity e) {
        return e.level().dimension().equals(MogoMundo.DIM);
    }

    // ===================== Convite e acesso =====================

    /** Quem pode pousar no Mogo: os inimigos dos Verdes não. */
    public static boolean podePousar(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return true;
        RingData r = ModCapabilities.get(p).orElse(null);
        if (r == null || !r.hasRing()) return true;
        var c = r.corps();
        return !(BuiltinCorps.YELLOW.id().equals(c) || BuiltinCorps.RED.id().equals(c) || BuiltinCorps.ORANGE.id().equals(c) || BuiltinCorps.BLACK.id().equals(c));
    }

    /** Empurra para longe do Mogo (no espaço). */
    public static void repelir(ServerPlayer p, @Nullable Entity veiculo) {
        SpaceBodies.Body mogo = SpaceBodies.byId("mogo");
        if (mogo == null) return;
        Entity alvo = veiculo != null ? veiculo : p;
        Vec3 d = alvo.position().subtract(mogo.center());
        if (d.lengthSqr() < 1e-3) d = new Vec3(1, 0, 0);
        d = d.normalize();
        Vec3 fora = mogo.center().add(d.scale(mogo.radius() + 70));
        alvo.teleportTo(fora.x, fora.y, fora.z);
        alvo.setDeltaMovement(d.scale(2.5));
        alvo.hurtMarked = true;
        if (p.tickCount % 40 == 0 || veiculo == null) {
            falar(p, "repele", true);
            p.level().playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.AMBIENT, 1.2f, 0.4f);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        // convite
        if (p.tickCount % 200 == 31 && !p.getPersistentData().getBoolean(OFERECIDA) && verde(p) && com.lanterna.tropa.Nivel.nivel(p) >= NIVEL) {
            p.getPersistentData().putBoolean(OFERECIDA, true);
            MissionManager.start(p, "mogo_1");
            titulo(p, "mogo.lanterna.convite.titulo", COR);
            p.sendSystemMessage(Component.translatable("mogo.lanterna.convite").withStyle(ChatFormatting.GREEN));
        }
        // inimigo dentro do Mogo (por comando, portal...): para fora
        if (noMogo(p) && p.tickCount % 20 == 3 && !podePousar(p)) {
            ServerLevel space = p.server.getLevel(SpaceBodies.SPACE);
            SpaceBodies.Body mogo = SpaceBodies.byId("mogo");
            if (space != null && mogo != null) {
                Vec3 fora = mogo.center().add(mogo.radius() + 70, 0, 0);
                p.teleportTo(space, fora.x, fora.y, fora.z, -90, 0);
                falar(p, "repele", true);
            }
            return;
        }
        if (noMogo(p) && p.tickCount % 40 == 5 && !MogoMundo.dados(p.serverLevel()).pronto)
            p.displayClientMessage(Component.translatable("mogo.lanterna.montando").withStyle(ChatFormatting.GREEN), true);
        if (p.tickCount % 2 == 0) {
            Cinturao.tick(p);
            if (noMogo(p)) {
                Corrida.tick(p);
                Ponte.tick(p);
                Prova.tick(p);
                Cerco.tick(p);
                Nucleo.tick(p);
            }
        }
    }

    /** Chegou no Mogo. */
    public static void pousou(ServerPlayer p) {
        if (!podePousar(p)) return;
        RingData r = ModCapabilities.get(p).orElse(null);
        if (ativa(p, "mogo_1")) {
            cumprir(p, "mogo:cinturao");
            falar(p, "chegada", true);
        } else if (r != null && r.hasRing() && !verde(p)) falar(p, "visitante", true);
        else if (r == null || !r.hasRing()) falar(p, "humano", true);
        else falar(p, "de_volta", false);
    }

    // ===================== 1. O cinturão de destroços =====================

    static final class Cinturao {
        static void tick(ServerPlayer p) {
            if (!p.level().dimension().equals(SpaceBodies.SPACE) || !ativa(p, "mogo_1")) return;
            SpaceBodies.Body mogo = SpaceBodies.byId("mogo");
            if (mogo == null) return;
            Entity quem = p.getVehicle() != null ? p.getVehicle() : p;
            double d = quem.position().distanceTo(mogo.center());
            if (d > mogo.radius() + 260 || d < mogo.radius()) return;
            if (p.tickCount % 200 == 0) p.displayClientMessage(Component.translatable("mogo.lanterna.cinturao.aviso").withStyle(ChatFormatting.GOLD), true);
            if (p.tickCount % 10 != 0) return;
            ServerLevel l = p.serverLevel();
            // a rocha vem de frente (entre o jogador e o Mogo), mirando onde ele vai estar
            Vec3 paraMogo = mogo.center().subtract(quem.position()).normalize();
            Vec3 vel = quem.getDeltaMovement();
            Vec3 lado = paraMogo.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 de = quem.position().add(paraMogo.scale(55 + l.random.nextInt(25)))
                    .add(lado.scale((l.random.nextDouble() - 0.5) * 50)).add(0, (l.random.nextDouble() - 0.5) * 30, 0);
            Vec3 alvo = quem.position().add(vel.scale(25));
            Vec3 dir = alvo.subtract(de).normalize();
            float tam = 1.2f + l.random.nextFloat() * 2.4f;
            l.addFreshEntity(RochaMogo.criar(l, RochaMogo.CINTURAO, tam, de, dir.scale(1.6 + l.random.nextDouble()), 4 + tam * 2));
        }
    }

    // ===================== 2. A corrida da Ferida =====================

    static final class Corrida {
        record Estado(List<BlockPos> focos, long fim, List<BlockPos> corrompidos) {}

        static final Map<UUID, Estado> ESTADOS = new HashMap<>();
        static final Map<UUID, Long> ESPERA = new HashMap<>();
        static final int TEMPO = 20 * 130;

        static void tick(ServerPlayer p) {
            if (!ativa(p, "mogo_2") || FonteDados.chamado(p, "mogo:corrida") >= 1) return;
            ServerLevel l = p.serverLevel();
            MogoMundo.Dados d = MogoMundo.dados(l);
            if (!d.pronto) return;
            Estado e = ESTADOS.get(p.getUUID());
            if (e == null) {
                if (p.blockPosition().distSqr(d.pouso) > 48 * 48) return;
                if (l.getGameTime() < ESPERA.getOrDefault(p.getUUID(), 0L)) return;
                comecar(p, l);
                return;
            }
            long falta = e.fim - l.getGameTime();
            int restam = (int) e.focos.stream().filter(f -> l.getBlockState(f).is(MogoBlocos.FOCO.get())).count();
            if (restam == 0) {
                limpar(l, e);
                ESTADOS.remove(p.getUUID());
                cumprir(p, "mogo:corrida");
                falar(p, "corrida_ok", true);
                return;
            }
            if (falta <= 0) {
                limpar(l, e);
                ESTADOS.remove(p.getUUID());
                ESPERA.put(p.getUUID(), l.getGameTime() + 200);
                falar(p, "corrida_falhou", true);
                return;
            }
            if (p.tickCount % 20 == 0) {
                p.displayClientMessage(Component.translatable("mogo.lanterna.corrida.hud", 8 - restam, 8, falta / 1200, (falta / 20) % 60)
                        .withStyle(falta < 400 ? ChatFormatting.RED : ChatFormatting.YELLOW), true);
                // a infecção se espalha em volta de cada foco
                for (BlockPos f : e.focos) {
                    if (!l.getBlockState(f).is(MogoBlocos.FOCO.get())) continue;
                    for (int k = 0; k < 3; k++) {
                        int r = 1 + (int) ((TEMPO - falta) / 300);
                        int x = f.getX() + l.random.nextInt(r * 2 + 1) - r, z = f.getZ() + l.random.nextInt(r * 2 + 1) - r;
                        BlockPos chao = new BlockPos(x, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                        BlockState s = l.getBlockState(chao);
                        if (s.is(MogoBlocos.JADE.get()) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.GRASS_BLOCK)) {
                            l.setBlock(chao, MogoBlocos.CORRUPCAO.get().defaultBlockState(), 3);
                            e.corrompidos.add(chao);
                        }
                    }
                }
            }
        }

        static void comecar(ServerPlayer p, ServerLevel l) {
            List<BlockPos> focos = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                int x = 40 + i * 38, z = l.random.nextInt(13) - 6;
                l.getChunk(x >> 4, z >> 4);
                int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos f = new BlockPos(x, y, z);
                l.setBlock(f, MogoBlocos.FOCO.get().defaultBlockState(), 3);
                l.setBlock(f.above(), MogoBlocos.FOCO.get().defaultBlockState(), 3);
                focos.add(f);
                focos.add(f.above());
            }
            ESTADOS.put(p.getUUID(), new Estado(focos, l.getGameTime() + TEMPO, new ArrayList<>()));
            falar(p, "corrida", true);
        }

        static void limpar(ServerLevel l, Estado e) {
            for (BlockPos f : e.focos) if (l.getBlockState(f).is(MogoBlocos.FOCO.get())) l.setBlock(f, Blocks.AIR.defaultBlockState(), 3);
            for (BlockPos c : e.corrompidos) if (l.getBlockState(c).is(MogoBlocos.CORRUPCAO.get())) l.setBlock(c, MogoBlocos.JADE.get().defaultBlockState(), 3);
        }
    }

    /** Chamado quando alguém quebra um foco. */
    static void focoQuebrado(ServerLevel l, BlockPos pos, net.minecraft.world.entity.player.Player p) {
        l.playSound(null, pos, SoundEvents.SCULK_SHRIEKER_BREAK, SoundSource.BLOCKS, 1.5f, 1.4f);
        if (p instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("mogo.lanterna.corrida.foco").withStyle(ChatFormatting.GREEN), true);
        // o par (foco de cima ou de baixo) some junto
        for (BlockPos q : new BlockPos[]{pos.above(), pos.below()}) if (l.getBlockState(q).is(MogoBlocos.FOCO.get())) l.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
    }

    // ===================== 3. O chão que se move =====================

    static final class Ponte {
        record Estado(long inicio, Map<Integer, Long> caidas) {}

        static final Map<UUID, Estado> ESTADOS = new HashMap<>();
        static final float VEL = 3.0f / 20f; // blocos por tick em que a ponte vai caindo

        static void tick(ServerPlayer p) {
            if (!ativa(p, "mogo_3") || FonteDados.chamado(p, "mogo:ponte") >= 1) {
                ESTADOS.remove(p.getUUID());
                return;
            }
            ServerLevel l = p.serverLevel();
            MogoMundo.Dados d = MogoMundo.dados(l);
            if (!d.pronto) return;
            Estado e = ESTADOS.get(p.getUUID());
            if (e == null) {
                if (p.blockPosition().distSqr(d.ponteIni) > 3 * 3) {
                    if (p.tickCount % 100 == 0 && p.blockPosition().distSqr(d.ponteIni) < 40 * 40)
                        p.displayClientMessage(Component.translatable("mogo.lanterna.ponte.dica").withStyle(ChatFormatting.GRAY), true);
                    return;
                }
                restaurar(l, d);
                ESTADOS.put(p.getUUID(), new Estado(l.getGameTime() + 40, new HashMap<>()));
                falar(p, "ponte", true);
                return;
            }
            // o Mogo segura o voo aqui
            semVoo(p);
            long t = l.getGameTime() - e.inicio;
            // a ponte cai de trás para frente
            if (t > 0) {
                int ate = d.ponteZ0 + (int) (t * VEL);
                for (int z = d.ponteZ0; z <= Math.min(ate, d.ponteZ1); z++) derrubar(l, d, z);
                // e pedaços à frente afundam e voltam (o chão que se move)
                if (t % 8 == 0) {
                    int z = Math.min(d.ponteZ1, ate + 4 + l.random.nextInt(18));
                    if (z < d.ponteZ1 - 2 && !e.caidas.containsKey(z)) {
                        e.caidas.put(z, l.getGameTime() + 34);
                        aviso(l, d, z);
                    }
                }
                e.caidas.entrySet().removeIf(en -> {
                    long quando = en.getValue();
                    if (l.getGameTime() == quando - 20) derrubar(l, d, en.getKey());
                    if (l.getGameTime() >= quando + 30) {
                        if (en.getKey() > d.ponteZ0 + (int) (t * VEL)) recolocar(l, d, en.getKey());
                        return true;
                    }
                    return false;
                });
            }
            if (p.getY() < d.ponteY - 5) {
                ESTADOS.remove(p.getUUID());
                p.teleportTo(d.ponteIni.getX() + 0.5, d.ponteIni.getY(), d.ponteIni.getZ() + 0.5);
                p.fallDistance = 0;
                restaurar(l, d);
                falar(p, "ponte_caiu", false);
                return;
            }
            if (p.getZ() >= d.ponteFim.getZ() - 2 && Math.abs(p.getY() - d.ponteY) < 4) {
                ESTADOS.remove(p.getUUID());
                restaurar(l, d);
                cumprir(p, "mogo:ponte");
                falar(p, "ponte_ok", true);
            }
        }

        static void semVoo(ServerPlayer p) {
            ModCapabilities.get(p).ifPresent(r -> {
                if (r.flightEnabled()) {
                    r.setFlightEnabled(false);
                    p.displayClientMessage(Component.translatable("mogo.lanterna.ponte.sem_voo").withStyle(ChatFormatting.GREEN), true);
                }
            });
            if (p.getAbilities().flying || p.getAbilities().mayfly) {
                p.getAbilities().flying = false;
                p.getAbilities().mayfly = false;
                p.onUpdateAbilities();
            }
        }

        static void aviso(ServerLevel l, MogoMundo.Dados d, int z) {
            for (int x = -13; x <= -11; x++) {
                BlockPos b = new BlockPos(x, d.ponteY - 1, z);
                if (!l.getBlockState(b).isAir()) l.setBlock(b, MogoBlocos.CORRUPCAO.get().defaultBlockState(), 3);
            }
            l.playSound(null, new BlockPos(-12, d.ponteY, z), SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.BLOCKS, 1.5f, 0.6f);
        }

        static void derrubar(ServerLevel l, MogoMundo.Dados d, int z) {
            for (int x = -13; x <= -11; x++) {
                BlockPos b = new BlockPos(x, d.ponteY - 1, z);
                BlockState s = l.getBlockState(b);
                if (s.isAir()) continue;
                FallingBlockEntity.fall(l, b, s).disableDrop();
            }
        }

        static void recolocar(ServerLevel l, MogoMundo.Dados d, int z) {
            for (int x = -13; x <= -11; x++) l.setBlock(new BlockPos(x, d.ponteY - 1, z), (z % 9 == 0 ? MogoBlocos.JADE.get() : Blocks.MOSSY_STONE_BRICKS).defaultBlockState(), 3);
        }

        static void restaurar(ServerLevel l, MogoMundo.Dados d) {
            for (int z = d.ponteZ0; z <= d.ponteZ1; z++) recolocar(l, d, z);
            // limpa o que caiu no fundo do cânion
            for (int x = -19; x <= -5; x++) for (int z = d.ponteZ0; z <= d.ponteZ1; z++) for (int y = d.ponteY - 26; y < d.ponteY - 12; y++) {
                BlockPos b = new BlockPos(x, y, z);
                if (!l.getBlockState(b).isAir()) l.setBlock(b, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    // ===================== 4. A prova do Mogo =====================

    static final class Prova {
        record Estado(List<UUID> olhos, List<UUID> golens, ServerBossEvent barra, long inicio) {}

        static final Map<UUID, Estado> ESTADOS = new HashMap<>();

        static void tick(ServerPlayer p) {
            Estado e = ESTADOS.get(p.getUUID());
            if (!ativa(p, "mogo_4") || FonteDados.chamado(p, "mogo:prova") >= 1) {
                if (e != null) acabar(p.serverLevel(), p, e);
                return;
            }
            ServerLevel l = p.serverLevel();
            MogoMundo.Dados d = MogoMundo.dados(l);
            if (!d.pronto) return;
            double dist = Math.sqrt(p.blockPosition().distSqr(d.arena));
            if (e == null) {
                if (dist > 20) return;
                comecar(p, l, d);
                return;
            }
            if (dist > 60) {
                acabar(l, p, e);
                return;
            }
            int vivos = 0;
            for (UUID u : e.olhos) if (l.getEntity(u) instanceof OlhoMogo o && o.isAlive()) vivos++;
            e.barra.setProgress(vivos / 4f);
            if (vivos == 0) {
                acabar(l, p, e);
                cumprir(p, "mogo:prova");
                falar(p, "prova_ok", true);
                return;
            }
            long t = l.getGameTime() - e.inicio;
            int ritmo = 70 - (4 - vivos) * 12; // quanto menos olhos, mais rápido ele ataca
            if (t % ritmo == 0) atacar(p, l, d, e, (int) ((t / ritmo) % 3), 4 - vivos);
        }

        static void comecar(ServerPlayer p, ServerLevel l, MogoMundo.Dados d) {
            List<UUID> olhos = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + Math.PI / 4;
                OlhoMogo o = MogoEntidades.OLHO.get().create(l);
                if (o == null) continue;
                o.moveTo(d.arena.getX() + 0.5 + Math.cos(a) * 17, d.arena.getY() + 2.5, d.arena.getZ() + 0.5 + Math.sin(a) * 17, 0, 0);
                l.addFreshEntity(o);
                olhos.add(o.getUUID());
            }
            ServerBossEvent barra = new ServerBossEvent(Component.translatable("mogo.lanterna.prova.barra"), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_6);
            barra.addPlayer(p);
            ESTADOS.put(p.getUUID(), new Estado(olhos, new ArrayList<>(), barra, l.getGameTime()));
            falar(p, "prova", true);
            l.playSound(null, d.arena, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 3f, 0.6f);
        }

        static void atacar(ServerPlayer p, ServerLevel l, MogoMundo.Dados d, Estado e, int qual, int raiva) {
            switch (qual) {
                case 0 -> { // pedras arremessadas da borda
                    for (int i = 0; i < 2 + raiva; i++) {
                        double a = l.random.nextDouble() * Math.PI * 2;
                        Vec3 de = new Vec3(d.arena.getX() + Math.cos(a) * 24, d.arena.getY() + 2, d.arena.getZ() + Math.sin(a) * 24);
                        Vec3 alvo = p.position().add(p.getDeltaMovement().scale(12));
                        Vec3 delta = alvo.subtract(de);
                        double tempo = 24;
                        Vec3 vel = new Vec3(delta.x / tempo, delta.y / tempo + 0.045 * tempo / 2, delta.z / tempo);
                        l.addFreshEntity(RochaMogo.criar(l, RochaMogo.PEDRA, 1.4f, de, vel, 7));
                    }
                    l.playSound(null, d.arena, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 2f, 0.5f);
                }
                case 1 -> { // raízes: uma linha de terra que rompe o chão até o jogador
                    double a = l.random.nextDouble() * Math.PI * 2;
                    Vec3 de = new Vec3(d.arena.getX() + Math.cos(a) * 22, d.arena.getY(), d.arena.getZ() + Math.sin(a) * 22);
                    Vec3 dir = p.position().subtract(de).multiply(1, 0, 1).normalize();
                    Raizes.ATIVAS.add(new Raizes.Linha(l, de, dir, l.getGameTime(), d.arena.getY()));
                }
                default -> { // guardiões de terra
                    e.golens.removeIf(u -> !(l.getEntity(u) instanceof IronGolem g && g.isAlive()));
                    if (e.golens.size() >= 2 + raiva / 2) return;
                    IronGolem g = EntityType.IRON_GOLEM.create(l);
                    if (g == null) return;
                    double a = l.random.nextDouble() * Math.PI * 2;
                    g.moveTo(d.arena.getX() + Math.cos(a) * 15, d.arena.getY(), d.arena.getZ() + Math.sin(a) * 15, 0, 0);
                    g.setCustomName(Component.translatable("mogo.lanterna.prova.golem"));
                    g.setPersistentAngerTarget(p.getUUID());
                    g.setRemainingPersistentAngerTime(20 * 120);
                    g.setTarget(p);
                    l.addFreshEntity(g);
                    e.golens.add(g.getUUID());
                    l.playSound(null, g.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.HOSTILE, 2f, 0.5f);
                }
            }
        }

        static void acabar(ServerLevel l, ServerPlayer p, Estado e) {
            ESTADOS.remove(p.getUUID());
            e.barra.removeAllPlayers();
            for (UUID u : e.olhos) if (l.getEntity(u) instanceof OlhoMogo o) o.discard();
            for (UUID u : e.golens) if (l.getEntity(u) instanceof IronGolem g) g.discard();
        }

        static boolean emProva(ServerPlayer p) {
            return ESTADOS.containsKey(p.getUUID());
        }
    }

    /** As linhas de raízes da Prova: avançam um bloco por tick, rompem o chão e jogam para cima quem estiver ali. */
    @Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Raizes {
        record Linha(ServerLevel l, Vec3 de, Vec3 dir, long inicio, int y) {}

        static final List<Linha> ATIVAS = new ArrayList<>();
        static final List<Object[]> TIRAR = new ArrayList<>();

        @SubscribeEvent
        public static void tick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            ATIVAS.removeIf(lin -> {
                long t = lin.l.getGameTime() - lin.inicio;
                if (t > 46) return true;
                Vec3 p = lin.de.add(lin.dir.scale(t));
                BlockPos b = new BlockPos((int) Math.floor(p.x), lin.y, (int) Math.floor(p.z));
                for (int k = 0; k < 3; k++) {
                    BlockPos q = b.above(k);
                    if (lin.l.getBlockState(q).isAir()) {
                        lin.l.setBlock(q, (k == 2 ? Blocks.ROOTED_DIRT : Blocks.MOSSY_COBBLESTONE).defaultBlockState(), 3);
                        TIRAR.add(new Object[]{lin.l, q, lin.l.getGameTime() + 40});
                    }
                }
                lin.l.playSound(null, b, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1f, 0.6f);
                for (LivingEntity v : lin.l.getEntitiesOfClass(LivingEntity.class, new AABB(b).inflate(0.8, 2, 0.8),
                        v -> v instanceof ServerPlayer)) {
                    v.hurt(lin.l.damageSources().magic(), 6);
                    v.push(0, 1.0, 0);
                    v.hurtMarked = true;
                }
                return false;
            });
            TIRAR.removeIf(o -> {
                ServerLevel l = (ServerLevel) o[0];
                if (l.getGameTime() < (long) o[2]) return false;
                l.setBlock((BlockPos) o[1], Blocks.AIR.defaultBlockState(), 3);
                return true;
            });
        }
    }

    /** Na Prova o Mogo não deixa ninguém morrer: levanta o jogador na borda da arena. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void quaseMorreu(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !Prova.emProva(p)) return;
        e.setCanceled(true);
        p.setHealth(p.getMaxHealth() * 0.6f);
        p.removeAllEffects();
        MogoMundo.Dados d = MogoMundo.dados(p.serverLevel());
        p.teleportTo(d.arena.getX() + 20.5, d.arena.getY() + 1, d.arena.getZ() + 0.5);
        falar(p, "prova_levanta", true);
    }

    // ===================== 5. O cerco =====================

    static final class Cerco {
        record Estado(int[] vida, int[] onda, List<UUID> inimigos, ServerBossEvent barra, long[] proxima) {}

        static final Map<UUID, Estado> ESTADOS = new HashMap<>();
        static final Map<UUID, Long> ESPERA = new HashMap<>();
        static final int ONDAS = 3, VIDA = 100;

        static void tick(ServerPlayer p) {
            Estado e = ESTADOS.get(p.getUUID());
            if (!ativa(p, "mogo_5") || FonteDados.chamado(p, "mogo:cerco") >= 1) {
                if (e != null) acabar(p.serverLevel(), p, e, true);
                return;
            }
            ServerLevel l = p.serverLevel();
            MogoMundo.Dados d = MogoMundo.dados(l);
            if (!d.pronto) return;
            BlockPos meio = d.torres[1];
            if (e == null) {
                if (meio == null || p.blockPosition().distSqr(meio) > 50 * 50 || l.getGameTime() < ESPERA.getOrDefault(p.getUUID(), 0L)) return;
                ServerBossEvent barra = new ServerBossEvent(Component.translatable("mogo.lanterna.cerco.barra", 1, ONDAS), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);
                barra.addPlayer(p);
                e = new Estado(new int[]{VIDA, VIDA, VIDA}, new int[]{0}, new ArrayList<>(), barra, new long[]{l.getGameTime() + 60});
                ESTADOS.put(p.getUUID(), e);
                falar(p, "cerco", true);
                return;
            }
            if (p.blockPosition().distSqr(meio) > 140 * 140) {
                acabar(l, p, e, true);
                return;
            }
            e.inimigos.removeIf(u -> !(l.getEntity(u) instanceof LivingEntity le && le.isAlive()));
            // os inimigos atacam as torres quando chegam perto
            if (p.tickCount % 20 == 0) {
                for (UUID u : e.inimigos) {
                    if (!(l.getEntity(u) instanceof Mob m)) continue;
                    int mais = -1;
                    double md = Double.MAX_VALUE;
                    for (int i = 0; i < 3; i++) {
                        if (e.vida[i] <= 0) continue;
                        double dd = m.distanceToSqr(Vec3.atCenterOf(d.torres[i]));
                        if (dd < md) { md = dd; mais = i; }
                    }
                    if (mais < 0) break;
                    if (md < 9 * 9) {
                        e.vida[mais] = Math.max(0, e.vida[mais] - 3);
                        l.playSound(null, d.torres[mais], SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.HOSTILE, 1.5f, 0.6f);
                        if (e.vida[mais] == 0) {
                            l.setBlock(d.torres[mais], MogoBlocos.CORRUPCAO.get().defaultBlockState(), 3);
                            l.playSound(null, d.torres[mais], SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2f, 0.8f);
                            falar(p, "cerco_torre", false);
                        }
                    } else if (m.getTarget() == null || m.getTarget() == p && m.distanceToSqr(p) > 20 * 20) {
                        m.getNavigation().moveTo(d.torres[mais].getX() + 0.5, d.torres[mais].getY() - 8, d.torres[mais].getZ() + 0.5, 1.2);
                    }
                }
            }
            int total = e.vida[0] + e.vida[1] + e.vida[2];
            e.barra.setProgress(total / (3f * VIDA));
            if (total <= 0) {
                acabar(l, p, e, false);
                ESPERA.put(p.getUUID(), l.getGameTime() + 20 * 15);
                falar(p, "cerco_falhou", true);
                return;
            }
            if (e.inimigos.isEmpty() && l.getGameTime() >= e.proxima[0]) {
                if (e.onda[0] >= ONDAS) {
                    acabar(l, p, e, true);
                    cumprir(p, "mogo:cerco");
                    falar(p, "cerco_ok", true);
                    return;
                }
                e.onda[0]++;
                e.barra.setName(Component.translatable("mogo.lanterna.cerco.barra", e.onda[0], ONDAS));
                onda(p, l, d, e);
                e.proxima[0] = l.getGameTime() + 20 * 6;
            }
        }

        static void onda(ServerPlayer p, ServerLevel l, MogoMundo.Dados d, Estado e) {
            int n = 3 + e.onda[0] * 2;
            titulo(p, "mogo.lanterna.cerco.onda", 0xFFD040);
            for (int i = 0; i < n; i++) {
                var y = com.lanterna.especies.LanternaEntity.lanterna(l, BuiltinCorps.YELLOW, i == 0 && e.onda[0] == ONDAS);
                BlockPos t = d.torres[l.random.nextInt(3)];
                double a = l.random.nextDouble() * Math.PI * 2;
                y.moveTo(t.getX() + Math.cos(a) * 40, t.getY() + 6, t.getZ() + Math.sin(a) * 40, 0, 0);
                y.setTarget(p);
                y.setPersistenceRequired();
                l.addFreshEntity(y);
                e.inimigos.add(y.getUUID());
            }
            if (e.onda[0] >= 2) {
                var nave = com.lanterna.tropa.Encontros.naveInimiga(p, BuiltinCorps.YELLOW);
                if (nave != null) e.inimigos.add(nave.getUUID());
            }
            l.playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2f, 1.1f);
        }

        static void acabar(ServerLevel l, ServerPlayer p, Estado e, boolean restaurar) {
            ESTADOS.remove(p.getUUID());
            e.barra.removeAllPlayers();
            for (UUID u : e.inimigos) {
                Entity x = l.getEntity(u);
                if (x != null) x.discard();
            }
            MogoMundo.Dados d = MogoMundo.dados(l);
            for (BlockPos t : d.torres) if (t != null) l.setBlock(t, MogoBlocos.NUCLEO_TORRE.get().defaultBlockState(), 3);
        }
    }

    // ===================== 6. O mergulho no núcleo =====================

    static final class Nucleo {
        static final Map<UUID, UUID> FERIDAS = new HashMap<>();

        static void tick(ServerPlayer p) {
            if (!ativa(p, "mogo_6") || FonteDados.chamado(p, "mogo:nucleo") >= 1) return;
            ServerLevel l = p.serverLevel();
            MogoMundo.Dados d = MogoMundo.dados(l);
            if (!d.pronto) return;
            double hx = p.getX() - (d.fenda.getX() + 0.5), hz = p.getZ() - (d.fenda.getZ() + 0.5);
            boolean naFenda = hx * hx + hz * hz < 5 * 5 && p.getY() < d.fenda.getY() - 2 && p.getY() > d.camara.getY() + 10;
            if (naFenda) {
                // o Mogo segura a queda: um mergulho longo e controlado
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false));
                if (p.tickCount % 60 == 0) p.displayClientMessage(Component.translatable("mogo.lanterna.nucleo.descendo").withStyle(ChatFormatting.GREEN), true);
            }
            boolean naCamara = p.blockPosition().distSqr(d.camara) < 16 * 16;
            if (!naCamara) return;
            UUID id = FERIDAS.get(p.getUUID());
            Entity f = id == null ? null : l.getEntity(id);
            if (f == null || !f.isAlive()) {
                if (f == null) {
                    Ferida nova = MogoEntidades.FERIDA.get().create(l);
                    if (nova == null) return;
                    nova.moveTo(d.camara.getX() + 0.5, d.camara.getY() + 4, d.camara.getZ() + 0.5, 0, 0);
                    l.addFreshEntity(nova);
                    FERIDAS.put(p.getUUID(), nova.getUUID());
                    falar(p, "nucleo", true);
                }
            }
        }

        static boolean podeChamar(ServerPlayer p) {
            UUID id = FERIDAS.get(p.getUUID());
            return id != null && p.serverLevel().getEntity(id) instanceof Ferida f && f.casulo && f.isAlive();
        }
    }

    /** A Ferida se fechou no casulo: hora do Chamado. */
    static void casuloFechou(ServerPlayer p, Ferida f) {
        falar(p, "nucleo_chame", true);
    }

    /** O Chamado de dentro do Mogo (bateria verde na mão, ou o próprio Mogo depois de um tempo). */
    static void chamadoInterno(ServerPlayer p, Ferida f) {
        ChamadoMogo.interno(p, f);
    }

    /** Bateria na mão durante o casulo: o primeiro Chamado (mesmo sem o Selo). */
    public static boolean tentarChamadoNucleo(ServerPlayer p) {
        if (!Nucleo.podeChamar(p)) return false;
        UUID id = Nucleo.FERIDAS.get(p.getUUID());
        if (p.serverLevel().getEntity(id) instanceof Ferida f) {
            f.casuloDesde = Integer.MIN_VALUE / 2; // não atira de novo sozinho
            chamadoInterno(p, f);
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void morreu(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof Ferida f) || !(f.level() instanceof ServerLevel l)) return;
        for (var en : new ArrayList<>(Nucleo.FERIDAS.entrySet())) {
            if (!en.getValue().equals(f.getUUID())) continue;
            Nucleo.FERIDAS.remove(en.getKey());
            ServerPlayer p = l.getServer().getPlayerList().getPlayer(en.getKey());
            if (p == null) continue;
            cumprir(p, "mogo:nucleo");
            FonteDados.chamado(p, "mogo:vinculo", 1);
            falar(p, "nucleo_ok", true);
            p.sendSystemMessage(Component.translatable("mogo.lanterna.vinculo").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            MogoMundo.Dados d = MogoMundo.dados(l);
            p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, 6, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0, false, false));
            l.getServer().execute(() -> p.teleportTo(l, d.pouso.getX() + 0.5, d.pouso.getY() + 1, d.pouso.getZ() + 3.5, 180, 0));
        }
    }

    private MogoMissoes() {}
}
