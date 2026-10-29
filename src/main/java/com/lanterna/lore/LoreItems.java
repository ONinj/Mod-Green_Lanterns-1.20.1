package com.lanterna.lore;

import com.lanterna.lore.item.LoreItem;
import com.lanterna.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Itens da lore: minérios brutos, drops dos bosses, moedas das raças e presentes. */
public final class LoreItems {
    public static final List<RegistryObject<Item>> ALL = new ArrayList<>();

    // ---- Minérios brutos (um por planeta) ----
    public static final RegistryObject<Item> IRA_BRUTA = material("ira_bruta", Rarity.UNCOMMON);
    public static final RegistryObject<Item> AMBAR_DA_POSSE = material("ambar_da_posse", Rarity.UNCOMMON);
    public static final RegistryObject<Item> FRAGMENTO_DE_PAVOR = material("fragmento_de_pavor", Rarity.UNCOMMON);
    public static final RegistryObject<Item> JADE_ESTELAR = material("jade_estelar", Rarity.UNCOMMON);
    public static final RegistryObject<Item> PEROLA_DO_AMANHA = material("perola_do_amanha", Rarity.UNCOMMON);
    public static final RegistryObject<Item> LAGRIMA_INDIGO = material("lagrima_indigo", Rarity.UNCOMMON);
    public static final RegistryObject<Item> CRISTAL_DEVOTO = material("cristal_devoto", Rarity.UNCOMMON);
    public static final RegistryObject<Item> OBSIDIANA_DO_FIM = material("obsidiana_do_fim", Rarity.RARE);

    // ---- Drops dos bosses ----
    public static final RegistryObject<Item> CORACAO_DE_VORGATH = relic("coracao_de_vorgath");
    public static final RegistryObject<Item> AMBAR_PRIMORDIAL = relic("ambar_primordial");
    public static final RegistryObject<Item> COROA_DE_PAVOR = relic("coroa_de_pavor");
    public static final RegistryObject<Item> NUCLEO_DE_VONTADE = relic("nucleo_de_vontade");
    public static final RegistryObject<Item> LAGRIMA_DE_AURORA = relic("lagrima_de_aurora");
    public static final RegistryObject<Item> CORACAO_DO_PENITENTE = relic("coracao_do_penitente");
    public static final RegistryObject<Item> CORACAO_DE_NYRIELLE = relic("coracao_de_nyrielle");
    public static final RegistryObject<Item> NUCLEO_DO_FIM = relic("nucleo_do_fim");
    public static final RegistryObject<Item> FRAGMENTO_PRIMORDIAL = relic("fragmento_primordial");

    // ---- Fabricação dos anéis ----
    public static final RegistryObject<Item> ANEL_VAZIO = reg("anel_vazio",
            () -> new LoreItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant(), false));

    // ---- Moedas e trocas das raças ----
    public static final RegistryObject<Item> MERITO = material("merito", Rarity.UNCOMMON);
    public static final RegistryObject<Item> GRATIDAO = material("gratidao", Rarity.UNCOMMON);
    public static final RegistryObject<Item> ALMA = reg("alma", () -> new LoreItem(new Item.Properties().rarity(Rarity.UNCOMMON), true));
    public static final RegistryObject<Item> RELIQUIA = material("reliquia", Rarity.RARE);

    public static final RegistryObject<Item> CARNE_DE_FERA = reg("carne_de_fera", () -> new LoreItem(new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(10).saturationMod(1.0f).meat()
                    .effect(() -> new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 90, 1), 1.0f).build()), false));
    public static final RegistryObject<Item> ERVA_TALEUN = reg("erva_taleun", () -> new LoreItem(new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(2).saturationMod(0.4f).alwaysEat().fast()
                    .effect(() -> new MobEffectInstance(MobEffects.REGENERATION, 20 * 8, 1), 1.0f).build()), false));

    public static final RegistryObject<Item> BENCAO_AELINOS = reg("bencao_aelinos", () -> new LoreItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON),
            true, (level, player, stack) -> {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 30, 1));
                player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 20 * 180, 0));
                player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 20 * 180, 0));
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.6f);
                return true;
            }, 20, true));

    public static final RegistryObject<Item> COLAR_PROTECAO = reg("colar_protecao", () -> new LoreItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
            true, (level, player, stack) -> {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 120, 1));
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 120, 1));
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1.2f);
                return true;
            }, 20 * 300, false));

    /** Revela minérios num raio de 24 blocos (brilho nas pedras por alguns segundos). */
    public static final RegistryObject<Item> PERGAMINHO_SEGREDO = reg("pergaminho_segredo", () -> new LoreItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON),
            true, (level, player, stack) -> {
                if (!(level instanceof ServerLevel sl)) return false;
                int found = 0;
                BlockPos c = player.blockPosition();
                for (BlockPos p : BlockPos.betweenClosed(c.offset(-24, -16, -24), c.offset(24, 16, 24))) {
                    BlockState s = sl.getBlockState(p);
                    if (s.is(net.minecraftforge.common.Tags.Blocks.ORES) || s.getBlock().getDescriptionId().contains("minerio")) {
                        sl.sendParticles(player instanceof net.minecraft.server.level.ServerPlayer sp ? ParticleTypes.GLOW : ParticleTypes.GLOW,
                                p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0);
                        if (++found > 200) break;
                    }
                }
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 30, 0));
                player.displayClientMessage(Component.translatable("message.lanterna.secret_scroll", found), true);
                level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.7f);
                return true;
            }, 40, true));

    public static final RegistryObject<Item> MARTELO_BRAKKARI = reg("martelo_brakkari", () -> new SwordItem(Tiers.NETHERITE, 9, -3.3f,
            new Item.Properties().rarity(Rarity.RARE).fireResistant()));

    private static RegistryObject<Item> material(String name, Rarity rarity) {
        return reg(name, () -> new LoreItem(new Item.Properties().rarity(rarity), false));
    }

    private static RegistryObject<Item> relic(String name) {
        return reg(name, () -> new LoreItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant(), true));
    }

    private static RegistryObject<Item> reg(String name, Supplier<Item> sup) {
        RegistryObject<Item> item = ModItems.ITEMS.register(name, sup);
        ALL.add(item);
        return item;
    }

    public static void init() {}

    private LoreItems() {}
}
