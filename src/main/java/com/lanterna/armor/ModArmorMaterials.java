package com.lanterna.armor;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

/** Materiais de armadura dos uniformes das Tropas (Forge 1.20.1).
 *  A textura é buscada em assets/lanterna/textures/models/armor/<nome>_layer_1.png e _layer_2.png */
public enum ModArmorMaterials implements ArmorMaterial {
    TROPA_VERDE("tropa_verde", 33, new int[]{3, 6, 8, 3}, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, 2.0F, 0.1F, () -> Ingredient.EMPTY),
    TROPA_VERMELHO("tropa_vermelho", 33, new int[]{3, 6, 8, 3}, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, 2.0F, 0.1F, () -> Ingredient.EMPTY),
    TROPA_LARANJA("tropa_laranja", 33, new int[]{3, 6, 8, 3}, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, 2.0F, 0.1F, () -> Ingredient.EMPTY),
    TROPA_AMARELO("tropa_amarelo", 33, new int[]{3, 6, 8, 3}, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, 2.0F, 0.1F, () -> Ingredient.EMPTY),
    TROPA_AZUL("tropa_azul", 33, new int[]{3, 6, 8, 3}, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, 2.0F, 0.1F, () -> Ingredient.EMPTY),
    TROPA_INDIGO("tropa_indigo", 33, new int[]{3, 6, 8, 3}, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, 2.0F, 0.1F, () -> Ingredient.EMPTY),
    TROPA_VIOLETA("tropa_violeta", 33, new int[]{3, 6, 8, 3}, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, 2.0F, 0.1F, () -> Ingredient.EMPTY),
    TROPA_PRETO("tropa_preto", 33, new int[]{3, 6, 8, 3}, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, 2.0F, 0.1F, () -> Ingredient.EMPTY),
    TROPA_BRANCO("tropa_branco", 33, new int[]{3, 6, 8, 3}, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, 2.0F, 0.1F, () -> Ingredient.EMPTY);

    public static final String MODID = "lanterna";
    private static final int[] BASE_DURABILITY = {11, 16, 15, 13}; // botas, calça, peitoral, capacete

    private final String name;
    private final int durabilityMultiplier;
    private final int[] protection; // botas, calça, peitoral, capacete
    private final int enchantability;
    private final SoundEvent equipSound;
    private final float toughness;
    private final float knockbackResistance;
    private final Supplier<Ingredient> repair;

    ModArmorMaterials(String name, int durabilityMultiplier, int[] protection, int enchantability, SoundEvent equipSound,
                      float toughness, float knockbackResistance, Supplier<Ingredient> repair) {
        this.name = name; this.durabilityMultiplier = durabilityMultiplier; this.protection = protection;
        this.enchantability = enchantability; this.equipSound = equipSound; this.toughness = toughness;
        this.knockbackResistance = knockbackResistance; this.repair = repair;
    }

    private static int slot(ArmorItem.Type type) {
        return switch (type) { case BOOTS -> 0; case LEGGINGS -> 1; case CHESTPLATE -> 2; case HELMET -> 3; };
    }

    @Override public int getDurabilityForType(ArmorItem.Type type) { return BASE_DURABILITY[slot(type)] * durabilityMultiplier; }
    @Override public int getDefenseForType(ArmorItem.Type type) { return protection[slot(type)]; }
    @Override public int getEnchantmentValue() { return enchantability; }
    @Override public SoundEvent getEquipSound() { return equipSound; }
    @Override public Ingredient getRepairIngredient() { return repair.get(); }
    @Override public String getName() { return MODID + ":" + name; }
    @Override public float getToughness() { return toughness; }
    @Override public float getKnockbackResistance() { return knockbackResistance; }
}
