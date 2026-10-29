package com.lanterna.lore;

import com.lanterna.LanternaMod;
import com.lanterna.corps.BuiltinCorps;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * A lore de cada planeta das Tropas: emoção, raça, boss, minério, drop do boss e pedra do planeta.
 * Os planetas mantêm os nomes do mod (Oa, Ysmault...); as características vêm da lore do autor
 * (Veridia -> Oa, Kharuun -> Ysmault, Aurumvex -> Okaara, Nyxkor -> Korugar, Lumeris -> Odym,
 *  Tal'Eun -> Nok, Selenaar -> Zamaron, Umbrall -> Ryut). Gênese é o planeta escondido da Vida.
 *
 * Ordem = ordem sugerida de dificuldade na lore.
 */
public enum PlanetLore {
    ODYM("odym", BuiltinCorps.BLUE, "aelinos", "morrow", "perola_do_amanha", "lagrima_de_aurora", "areia_abissal_lumeris"),
    OA("oa", BuiltinCorps.GREEN, "veltari", "vigia_partido", "jade_estelar", "nucleo_de_vontade", "rocha_polida_veridia"),
    OKAARA("okaara", BuiltinCorps.ORANGE, "ghallos", "mammok", "ambar_da_posse", "ambar_primordial", "arenito_aurumvex"),
    YSMAULT("ysmault", BuiltinCorps.RED, "brakkari", "vorgath", "ira_bruta", "coracao_de_vorgath", "basalto_kharuun"),
    ZAMARON("zamaron", BuiltinCorps.VIOLET, "zaelith", "nyrielle", "cristal_devoto", "coracao_de_nyrielle", "rocha_cristalina_selenaar"),
    KORUGAR("korugar", BuiltinCorps.YELLOW, "skrell_vath", "thessara", "fragmento_de_pavor", "coroa_de_pavor", "pedra_crepuscular"),
    NOK("nok", BuiltinCorps.INDIGO, "ves_kai", "penitente", "lagrima_indigo", "coracao_do_penitente", "pedra_do_templo_taleun"),
    RYUT("ryut", BuiltinCorps.BLACK, "silente", "devorador_de_ecos", "obsidiana_do_fim", "nucleo_do_fim", "gelo_negro_umbrall"),
    GENESE("genese", BuiltinCorps.WHITE, "broto", "nulo", null, "fragmento_primordial", null);

    /** Centro da base de cada planeta (forja, líder, alvos de treino). */
    public static final BlockPos HUB = new BlockPos(0, 0, 0);
    /** Arena do boss (altar do desafio). */
    public static final BlockPos ARENA = new BlockPos(120, 0, 0);

    /** Onde fica o altar do boss neste planeta (em Gênese, a rachadura do Nulo ao lado do Altar da Vida). */
    public static BlockPos arena(PlanetLore p) {
        return p == GENESE ? new BlockPos(0, 0, -24) : ARENA;
    }

    public final String planet;
    public final BuiltinCorps corps;
    public final String race;
    public final String boss;
    @Nullable public final String ore;
    public final String bossDrop;
    @Nullable public final String stone;

    PlanetLore(String planet, BuiltinCorps corps, String race, String boss, @Nullable String ore, String bossDrop, @Nullable String stone) {
        this.planet = planet;
        this.corps = corps;
        this.race = race;
        this.boss = boss;
        this.ore = ore;
        this.bossDrop = bossDrop;
        this.stone = stone;
    }

    /** Os sete anéis que se fundem no Anel Branco (sem o Preto e sem o próprio Branco). */
    public boolean fusion() {
        return this != RYUT && this != GENESE;
    }

    public Item oreItem() {
        return ore == null ? Items.AIR : item(ore);
    }

    public Item dropItem() {
        return item(bossDrop);
    }

    public Block stoneBlock() {
        if (stone == null) return Blocks.CALCITE;
        Block b = ForgeRegistries.BLOCKS.getValue(LanternaMod.id(stone));
        return b == null ? Blocks.STONE : b;
    }

    public ResourceLocation corpsId() {
        return corps.id();
    }

    public int color() {
        return corps.color();
    }

    public Component planetName() {
        return Component.translatable("planet.lanterna." + planet);
    }

    public Component raceName() {
        return Component.translatable("race.lanterna." + race);
    }

    public Component bossName() {
        return Component.translatable("entity.lanterna." + boss);
    }

    private static Item item(String name) {
        Item i = ForgeRegistries.ITEMS.getValue(LanternaMod.id(name));
        return i == null ? Items.AIR : i;
    }

    @Nullable
    public static PlanetLore byPlanet(@Nullable String id) {
        if (id == null) return null;
        for (PlanetLore p : values()) if (p.planet.equals(id)) return p;
        return null;
    }

    @Nullable
    public static PlanetLore byCorps(@Nullable ResourceLocation corps) {
        if (corps == null) return null;
        for (PlanetLore p : values()) if (p.corps.id().equals(corps)) return p;
        return null;
    }

    @Nullable
    public static PlanetLore byBoss(String boss) {
        for (PlanetLore p : values()) if (p.boss.equals(boss)) return p;
        return null;
    }

    @Nullable
    public static PlanetLore byDimension(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {
        if (!dim.location().getNamespace().equals(LanternaMod.MODID)) return null;
        return byPlanet(dim.location().getPath());
    }
}
