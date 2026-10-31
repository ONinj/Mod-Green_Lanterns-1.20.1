package com.lanterna.guilda;

import com.lanterna.LanternaConfig;
import com.lanterna.cidade.CidadeNpcs;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Minerador: junta Essência de Poder sozinho (tempo real) e forja Anéis de Recruta (que nenhum jogador consegue usar).
 * General: recebe os anéis e treina tropas — de defesa (ficam neste planeta) ou de ataque (vão com a guilda nas invasões).
 */
public final class GuildaNpcs {

    public static InteractionResult interagir(ServerPlayer p, LanternaEntity npc, String funcao) {
        String planeta = npc.getPersistentData().getString(Conquistas.TAG_BASE);
        Guildas gs = Guildas.get(p.server);
        Conquista c = gs.conquistas.get(planeta);
        npc.triggerAnim("acao", "misc.talk");
        if (c == null) {
            CidadeNpcs.fala(p, npc, Component.translatable("guilda.lanterna.fala.sem_dono"));
            return InteractionResult.CONSUME;
        }
        if (!Guildas.membro(c.guilda, p)) {
            CidadeNpcs.fala(p, npc, Component.translatable("guilda.lanterna.fala.recusa", Conquistas.nomeGuilda(c.guilda)));
            return InteractionResult.CONSUME;
        }
        painel(p, c, funcao.equals("guilda_general"));
        return InteractionResult.CONSUME;
    }

    static void painel(ServerPlayer p, Conquista c, boolean general) {
        Guildas gs = Guildas.get(p.server);
        Guilda g = gs.guildas.get(c.guilda);
        if (g == null) return;
        long agora = System.currentTimeMillis();
        List<Component> linhas = new ArrayList<>();
        List<String> acoes = new ArrayList<>();
        List<Component> rot = new ArrayList<>();
        List<Boolean> at = new ArrayList<>();
        int custo = LanternaConfig.GUILDA_ESSENCIA_ANEL.get();
        int aneis = conta(p, GuildaItems.ANEL_RECRUTA.get().getDefaultInstance());
        Component titulo;
        if (!general) {
            titulo = Component.translatable("cidade.lanterna.npc.guilda_minerador");
            long passo = c.passoMinerador(LanternaConfig.GUILDA_MIN_ESSENCIA.get() * 60_000L);
            linhas.add(Component.translatable("guilda.lanterna.min.nivel", c.minerador, c.qtdMinerador(), Invasao.tempo(passo)).withStyle(ChatFormatting.GOLD));
            linhas.add(Component.translatable("guilda.lanterna.min.guardada", c.essencia, c.depositoMinerador()));
            linhas.add(c.essencia >= c.depositoMinerador() ? Component.translatable("guilda.lanterna.min.cheio")
                    : Component.translatable("guilda.lanterna.min.proxima", Invasao.tempo(Math.max(0, c.ultimaEssencia + passo - agora))));
            int bolso = conta(p, GuildaItems.ESSENCIA.get().getDefaultInstance());
            linhas.add(Component.translatable("guilda.lanterna.min.custo", custo, bolso));
            Melhoria prox = melhoria(c.minerador);
            if (prox != null) linhas.add(Component.translatable("guilda.lanterna.min.melhoria", c.minerador + 1, prox.essencia, prox.diamantes, prox.meritos,
                    prox.netherite).withStyle(ChatFormatting.AQUA));
            else linhas.add(Component.translatable("guilda.lanterna.min.maximo").withStyle(ChatFormatting.AQUA));
            linhas.add(Component.translatable("guilda.lanterna.min.dica").withStyle(ChatFormatting.GRAY));
            acoes.add("coletar"); rot.add(Component.translatable("guilda.lanterna.min.coletar")); at.add(c.essencia > 0);
            acoes.add("forjar"); rot.add(Component.translatable("guilda.lanterna.min.forjar")); at.add(c.essencia + bolso >= custo);
            if (prox != null) {
                acoes.add("melhorar"); rot.add(Component.translatable("guilda.lanterna.min.melhorar", c.minerador + 1)); at.add(podeMelhorar(p, c, prox));
            }
        } else {
            titulo = Component.translatable("cidade.lanterna.npc.guilda_general");
            int maxD = LanternaConfig.GUILDA_MAX_DEFESA.get(), maxA = LanternaConfig.GUILDA_MAX_ATAQUE.get();
            long filaD = c.fila.stream().filter(b -> b).count(), filaA = c.fila.size() - filaD;
            linhas.add(Component.translatable("guilda.lanterna.gen.defesa", c.defesa, maxD));
            linhas.add(Component.translatable("guilda.lanterna.gen.ataque", g.ataque, maxA));
            linhas.add(c.fila.isEmpty() ? Component.translatable("guilda.lanterna.gen.sem_fila")
                    : Component.translatable("guilda.lanterna.gen.fila", c.fila.size(), Invasao.tempo(Math.max(0, c.proximaTropa - agora))));
            linhas.add(Component.translatable("guilda.lanterna.gen.aneis", aneis));
            linhas.add(Component.translatable("guilda.lanterna.gen.dica", LanternaConfig.GUILDA_SEG_TREINO.get()).withStyle(ChatFormatting.GRAY));
            acoes.add("treinar_defesa"); rot.add(Component.translatable("guilda.lanterna.gen.treinar_defesa")); at.add(aneis > 0 && c.defesa + filaD < maxD);
            acoes.add("treinar_ataque"); rot.add(Component.translatable("guilda.lanterna.gen.treinar_ataque")); at.add(aneis > 0 && g.ataque + filaA < maxA);
        }
        ModNetwork.sendTo(p, new GuildaPainelPacket(c.planeta, g.cor(), Component.Serializer.toJson(titulo),
                linhas.stream().map(Component.Serializer::toJson).toList(), acoes, rot.stream().map(Component.Serializer::toJson).toList(), at));
    }

    public static void acao(ServerPlayer p, String planeta, String acao) {
        Guildas gs = Guildas.get(p.server);
        Conquista c = gs.conquistas.get(planeta);
        if (c == null || !Guildas.membro(c.guilda, p) || !p.level().dimension().equals(c.mundo)
                || p.blockPosition().distSqr(c.bandeira) > 40 * 40) return;
        Guilda g = gs.guildas.get(c.guilda);
        int custo = LanternaConfig.GUILDA_ESSENCIA_ANEL.get();
        long agora = System.currentTimeMillis();
        switch (acao) {
            case "coletar" -> {
                if (c.essencia <= 0) return;
                dar(p, new ItemStack(GuildaItems.ESSENCIA.get(), c.essencia));
                c.essencia = 0;
                p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.2f);
            }
            case "forjar" -> {
                int bolso = conta(p, GuildaItems.ESSENCIA.get().getDefaultInstance());
                if (c.essencia + bolso < custo) return;
                int doEstoque = Math.min(c.essencia, custo);
                c.essencia -= doEstoque;
                tirar(p, GuildaItems.ESSENCIA.get().getDefaultInstance(), custo - doEstoque);
                dar(p, new ItemStack(GuildaItems.ANEL_RECRUTA.get()));
                p.level().playSound(null, p.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8f, 1.4f);
                p.displayClientMessage(Component.translatable("guilda.lanterna.min.forjou").withStyle(ChatFormatting.GREEN), true);
            }
            case "melhorar" -> {
                Melhoria m = melhoria(c.minerador);
                if (m == null || !podeMelhorar(p, c, m)) return;
                int doEstoque = Math.min(c.essencia, m.essencia);
                c.essencia -= doEstoque;
                tirar(p, GuildaItems.ESSENCIA.get().getDefaultInstance(), m.essencia - doEstoque);
                tirar(p, new ItemStack(net.minecraft.world.item.Items.DIAMOND), m.diamantes);
                tirar(p, new ItemStack(com.lanterna.lore.LoreItems.MERITO.get()), m.meritos);
                tirar(p, new ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT), m.netherite);
                c.minerador++;
                p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1.2f);
                Conquistas.avisarGuilda(p.server, c.guilda, Component.translatable("guilda.lanterna.min.subiu", c.minerador).withStyle(ChatFormatting.GOLD));
                gs.mudou();
                painel(p, c, false);
                return;
            }
            case "treinar_defesa", "treinar_ataque" -> {
                boolean defesa = acao.equals("treinar_defesa");
                long filaD = c.fila.stream().filter(b -> b).count(), filaA = c.fila.size() - filaD;
                if (defesa && c.defesa + filaD >= LanternaConfig.GUILDA_MAX_DEFESA.get()) return;
                if (!defesa && g.ataque + filaA >= LanternaConfig.GUILDA_MAX_ATAQUE.get()) return;
                if (!tirar(p, GuildaItems.ANEL_RECRUTA.get().getDefaultInstance(), 1)) return;
                if (c.fila.isEmpty()) c.proximaTropa = agora + LanternaConfig.GUILDA_SEG_TREINO.get() * 1000L;
                c.fila.add(defesa);
                p.level().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS, 1f, 1f);
                p.displayClientMessage(Component.translatable(defesa ? "guilda.lanterna.gen.treinando_defesa" : "guilda.lanterna.gen.treinando_ataque")
                        .withStyle(ChatFormatting.GREEN), true);
            }
            default -> { return; }
        }
        gs.mudou();
        painel(p, c, acao.startsWith("treinar"));
    }

    /** Custo para o Minerador subir do nível atual para o próximo (null = já no 4). */
    public record Melhoria(int essencia, int diamantes, int meritos, int netherite) {}

    @org.jetbrains.annotations.Nullable
    public static Melhoria melhoria(int nivelAtual) {
        return switch (nivelAtual) {
            case 1 -> new Melhoria(32, 8, 8, 0);
            case 2 -> new Melhoria(96, 16, 24, 1);
            case 3 -> new Melhoria(192, 32, 48, 4);
            default -> null;
        };
    }

    static boolean podeMelhorar(ServerPlayer p, Conquista c, Melhoria m) {
        if (p.isCreative()) return true;
        return c.essencia + conta(p, GuildaItems.ESSENCIA.get().getDefaultInstance()) >= m.essencia
                && conta(p, new ItemStack(net.minecraft.world.item.Items.DIAMOND)) >= m.diamantes
                && conta(p, new ItemStack(com.lanterna.lore.LoreItems.MERITO.get())) >= m.meritos
                && conta(p, new ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT)) >= m.netherite;
    }

    static int conta(ServerPlayer p, ItemStack tipo) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(tipo.getItem())) n += s.getCount();
        return n;
    }

    static boolean tirar(ServerPlayer p, ItemStack tipo, int n) {
        if (n <= 0) return true;
        if (conta(p, tipo) < n) return false;
        for (ItemStack s : p.getInventory().items) {
            if (n <= 0) break;
            if (!s.is(tipo.getItem())) continue;
            int k = Math.min(n, s.getCount());
            s.shrink(k);
            n -= k;
        }
        return true;
    }

    static void dar(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    private GuildaNpcs() {}
}
