package com.lanterna.cidade;

import com.lanterna.corps.BuiltinCorps;
import com.lanterna.especies.Especie;
import com.lanterna.especies.EspeciesEntities;
import com.lanterna.especies.LanternaEntity;
import com.lanterna.lore.Factions;
import com.lanterna.lore.LoreItems;
import com.lanterna.lore.PlanetLore;
import com.lanterna.mission.LoreCapability;
import com.lanterna.mission.LoreData;
import com.lanterna.mission.MissionManager;
import com.lanterna.naves.NaveItem;
import com.lanterna.naves.NaveTipo;
import com.lanterna.space.SpaceBodies;
import com.lanterna.tropa.TropaItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Os NPCs com função na cidade e no palácio: vendedores (pagos em Méritos), quem dá missões, guardas, o Arauto.
 * Cada um tem a "função" gravada no próprio NPC; clicar abre a loja ou o painel de missões dele.
 */
public final class CidadeNpcs {
    public static final Set<String> LOJAS = Set.of("intendente", "cartografo", "reliquias", "armeiro", "boticario", "estaleiro");
    private static final Set<String> CIVIS = Set.of("intendente", "cartografo", "reliquias", "armeiro", "boticario", "estaleiro",
            "engenheiro", "medico", "arquivista", "cidadao_0", "cidadao_1", "cidadao_2");

    /** Nome com marcador: "$" vende, "!" tem missões. */
    public static Component nome(String funcao) {
        String base = funcao.startsWith("cidadao") ? "cidadao" : funcao;
        Component n = Component.translatable("cidade.lanterna.npc." + base);
        if (LOJAS.contains(funcao)) return Component.literal("$ ").withStyle(ChatFormatting.AQUA).append(n.copy().withStyle(ChatFormatting.WHITE));
        if (CidadeMissoes.daMissoes(funcao)) return Component.literal("! ").withStyle(ChatFormatting.GOLD).append(n.copy().withStyle(ChatFormatting.WHITE));
        return n;
    }

    /** Cria um NPC da cidade/palácio no lugar dado, preso num raio pequeno em volta. */
    public static LanternaEntity npc(ServerLevel level, BlockPos at, String funcao, BuiltinCorps corps, boolean oficial, float yaw, int raio) {
        LanternaEntity e = new LanternaEntity(EspeciesEntities.LANTERNA_NPC.get(), level);
        boolean civil = CIVIS.contains(funcao);
        Especie esp = civil ? Especie.paraPapel(Especie.Papel.MERCADOR, level.random) : Especie.paraTropa(corps, level.random);
        String roupa = civil ? (funcao.startsWith("cidadao") ? null : "roupa_mercador") : "roupa_tropa_" + corps.id().getPath();
        e.setup(esp, 1 + level.random.nextInt(3), civil ? Especie.Papel.MERCADOR : Especie.Papel.LANTERNA, civil ? null : corps,
                roupa, oficial, false);
        e.setFuncao(funcao);
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        e.restrictTo(at, Math.max(1, raio));
        e.setPersistenceRequired();
        e.setCustomName(nome(funcao));
        e.setCustomNameVisible(true);
        level.addFreshEntity(e);
        return e;
    }

    /** Planeta (de Tropa) onde o NPC está. */
    @Nullable
    public static PlanetLore planeta(LanternaEntity npc) {
        SpaceBodies.Body b = SpaceBodies.bodyAt(npc.level().dimension(), npc.getX(), npc.getZ());
        return b == null ? null : PlanetLore.byPlanet(b.id());
    }

    public static InteractionResult interagir(ServerPlayer p, LanternaEntity npc, String funcao) {
        PlanetLore pl = planeta(npc);
        String planet = pl != null ? pl.planet : "oa";
        // Conversar com o NPC conta para "fale com..."
        MissionManager.onTalk(p, funcao, planet);
        npc.triggerAnim("acao", "misc.talk");
        boolean palacio = funcao.startsWith("capitao_") || funcao.equals("honra");
        BuiltinCorps casa = pl != null ? pl.corps : null;
        if (!palacio && casa != null && Factions.enemies(casa, Factions.playerCorps(p)) && !funcao.equals("guarda") && !funcao.equals("preso")) {
            fala(p, npc, Component.translatable("cidade.lanterna.recusa", Component.translatable("corps.lanterna." + Factions.playerCorps(p).id().getPath())));
            return InteractionResult.CONSUME;
        }
        if (LOJAS.contains(funcao)) {
            abrirLoja(p, npc, funcao, planet);
            return InteractionResult.CONSUME;
        }
        switch (funcao) {
            case "contratos" -> com.lanterna.tropa.Contratos.abrirQuadro(p);
            case "arauto" -> Palacio.arauto(p, npc);
            case "guarda", "honra" -> fala(p, npc, Component.translatable("cidade.lanterna.fala." + funcao + "." + p.getRandom().nextInt(3)));
            case "preso" -> fala(p, npc, Component.translatable("cidade.lanterna.fala.preso." + p.getRandom().nextInt(3)));
            default -> CidadeMissoes.abrirPainel(p, npc, funcao, planet);
        }
        return InteractionResult.CONSUME;
    }

    public static void fala(ServerPlayer p, LanternaEntity npc, Component msg) {
        p.sendSystemMessage(Component.literal("<").append(npc.getDisplayName()).append("> ").append(msg).withStyle(s -> s.withColor(0xE8F0E8)));
    }

    // ===================== Lojas (Méritos) =====================

    /** Nível de reputação com a cidade: 0 desconhecido, 1 conhecido (50), 2 respeitado (150), 3 herói (300). */
    public static int nivelRep(int rep) {
        return rep >= 300 ? 3 : rep >= 150 ? 2 : rep >= 50 ? 1 : 0;
    }

    private static void abrirLoja(ServerPlayer p, LanternaEntity npc, String funcao, String planet) {
        LoreData d = LoreCapability.get(p).orElse(null);
        int rep = d == null ? 0 : d.rep("cidade_" + planet);
        int tier = nivelRep(rep);
        MerchantOffers ofs = npc.getOffers();
        ofs.clear();
        ofs.addAll(ofertas(funcao, tier, planet));
        if (ofs.isEmpty()) return;
        p.displayClientMessage(Component.translatable("cidade.lanterna.rep", Component.translatable("cidade.lanterna.rep." + tier), rep), true);
        npc.setTradingPlayer(p);
        npc.openTradingScreen(p, npc.getDisplayName(), tier + 1);
    }

    private static ItemStack m(int n) {
        return new ItemStack(LoreItems.MERITO.get(), n);
    }

    private static ItemStack it(Item i, int n) {
        return new ItemStack(i, n);
    }

    private static ItemStack pocao(Potion potion) {
        return PotionUtils.setPotion(new ItemStack(Items.POTION), potion);
    }

    private static MerchantOffer v(ItemStack custo, ItemStack sai) {
        return new MerchantOffer(custo, ItemStack.EMPTY, sai, 16, 2, 0f);
    }

    static MerchantOffers ofertas(String funcao, int tier, String planet) {
        MerchantOffers o = new MerchantOffers();
        PlanetLore pl = PlanetLore.byPlanet(planet);
        String cor = pl != null && pl.corps != BuiltinCorps.WHITE ? pl.corps.id().getPath() : "green";
        switch (funcao) {
            case "intendente" -> {
                o.add(v(m(1), it(Items.COOKED_BEEF, 8)));
                o.add(v(m(1), it(Items.BREAD, 10)));
                o.add(v(m(1), it(Items.TORCH, 24)));
                o.add(v(m(2), it(Items.GOLDEN_CARROT, 6)));
                o.add(v(it(Items.WHEAT, 20), m(1)));
                if (tier >= 1) o.add(v(m(2), it(Items.LANTERN, 8)));
                if (tier >= 2) o.add(v(m(4), it(Items.GOLDEN_APPLE, 2)));
                if (tier >= 3) o.add(v(m(12), it(Items.ENCHANTED_GOLDEN_APPLE, 1)));
            }
            case "armeiro" -> {
                o.add(v(m(6), it(TropaItems.BLASTER_PIRATA.get(), 1)));
                o.add(v(m(2), it(Items.SHIELD, 1)));
                o.add(v(m(1), it(Items.ARROW, 24)));
                o.add(v(it(TropaItems.FRAGMENTO_CASCO.get(), 4), m(1)));
                if (tier >= 1) { o.add(v(m(3), it(Items.IRON_CHESTPLATE, 1))); o.add(v(m(2), it(Items.IRON_HELMET, 1))); o.add(v(m(3), it(Items.IRON_LEGGINGS, 1))); }
                if (tier >= 2) { o.add(v(m(18), it(TropaItems.BLASTER_CAPITAO.get(), 1))); o.add(v(m(10), it(Items.DIAMOND_CHESTPLATE, 1))); }
                if (tier >= 3) o.add(v(m(16), it(Items.DIAMOND_SWORD, 1)));
            }
            case "reliquias" -> {
                Item[] gemas = {LoreItems.JADE_ESTELAR.get(), LoreItems.PEROLA_DO_AMANHA.get(), LoreItems.AMBAR_DA_POSSE.get(),
                        LoreItems.IRA_BRUTA.get(), LoreItems.FRAGMENTO_DE_PAVOR.get(), LoreItems.CRISTAL_DEVOTO.get(), LoreItems.LAGRIMA_INDIGO.get()};
                for (int i = 0; i < gemas.length; i++) if (i < 3 + tier * 2) o.add(v(m(3), it(gemas[i], 2)));
                o.add(v(it(LoreItems.RELIQUIA.get(), 1), m(4)));
                if (tier >= 1) o.add(v(m(2), it(Items.AMETHYST_SHARD, 8)));
                if (tier >= 3) o.add(v(m(25), it(LoreItems.ANEL_VAZIO.get(), 1)));
            }
            case "cartografo" -> {
                o.add(v(m(1), it(Items.MAP, 2)));
                o.add(v(m(1), it(Items.COMPASS, 1)));
                o.add(v(m(2), it(Items.SPYGLASS, 1)));
                o.add(v(it(Items.PAPER, 12), m(1)));
                if (tier >= 1) o.add(v(m(3), it(Items.RECOVERY_COMPASS, 1)));
                if (tier >= 2) o.add(v(m(4), it(Items.ENDER_PEARL, 4)));
            }
            case "boticario" -> {
                o.add(v(m(2), pocao(Potions.HEALING)));
                o.add(v(m(2), pocao(Potions.REGENERATION)));
                o.add(v(it(Items.NETHER_WART, 8), m(1)));
                if (tier >= 1) { o.add(v(m(3), pocao(Potions.FIRE_RESISTANCE))); o.add(v(m(2), pocao(Potions.SLOW_FALLING))); }
                if (tier >= 2) { o.add(v(m(4), pocao(Potions.STRENGTH))); o.add(v(m(4), pocao(Potions.LONG_SWIFTNESS))); }
                if (tier >= 3) o.add(v(m(6), pocao(Potions.STRONG_HEALING)));
            }
            case "estaleiro" -> {
                o.add(v(m(1), it(TropaItems.FRAGMENTO_CASCO.get(), 4)));
                o.add(v(m(4), it(TropaItems.NUCLEO_PROPULSOR.get(), 1)));
                if (tier >= 1) o.add(v(m(30), NaveItem.of(NaveTipo.NAVE_LANCEIRA, cor)));
                if (tier >= 2) o.add(v(m(60), NaveItem.of(NaveTipo.NAVE_PATRULHA, cor)));
                if (tier >= 3) o.add(v(m(90), NaveItem.of(NaveTipo.NAVE_CARGUEIRO, "1")));
            }
            default -> {}
        }
        return o;
    }

    private CidadeNpcs() {}
}
