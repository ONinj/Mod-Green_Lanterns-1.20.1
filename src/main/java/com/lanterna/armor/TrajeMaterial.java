package com.lanterna.armor;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/** Material dos trajes "segunda pele" (um por Tropa; Branco e Preto mais fortes). */
public enum TrajeMaterial implements ArmorMaterial {
    VERDE("verde", 3, 8, 6, 3, 1.5f), AMARELO("amarelo", 3, 8, 6, 3, 1.5f), VERMELHO("vermelho", 3, 8, 6, 3, 2.0f),
    LARANJA("laranja", 3, 7, 6, 3, 1.0f), AZUL("azul", 3, 7, 6, 3, 1.0f), INDIGO("indigo", 2, 7, 5, 2, 1.0f),
    VIOLETA("violeta", 3, 7, 6, 3, 1.0f), PRETO("preto", 3, 8, 6, 3, 2.5f), BRANCO("branco", 4, 9, 7, 4, 3.0f);

    private final String nome;
    private final int[] protecao;
    private final float dureza;

    TrajeMaterial(String nome, int capacete, int peito, int calca, int bota, float dureza) {
        this.nome = nome;
        this.protecao = new int[]{bota, calca, peito, capacete};
        this.dureza = dureza;
    }

    private static int idx(ArmorItem.Type t) {
        return switch (t) { case BOOTS -> 0; case LEGGINGS -> 1; case CHESTPLATE -> 2; case HELMET -> 3; };
    }

    @Override public int getDurabilityForType(ArmorItem.Type t) { return new int[]{13, 15, 16, 11}[idx(t)] * 33; }
    @Override public int getDefenseForType(ArmorItem.Type t) { return protecao[idx(t)]; }
    @Override public int getEnchantmentValue() { return 15; }
    @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_LEATHER; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    @Override public String getName() { return ModArmorMaterials.MODID + ":traje_" + nome; }
    @Override public float getToughness() { return dureza; }
    @Override public float getKnockbackResistance() { return 0.0f; }
}
