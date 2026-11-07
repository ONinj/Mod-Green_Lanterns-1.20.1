package com.lanterna.integracao.jei;

import com.lanterna.LanternaMod;
import com.lanterna.energia.EnergiaRegistro;
import com.lanterna.energia.Producao;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * JEI (opcional): mostra a Refinaria Emocional e o Conversor de Luz como receitas, e uma página de explicação para
 * cada máquina e item novo (com e sem Mekanism). Esta classe só é carregada pelo próprio JEI.
 */
@JeiPlugin
public class LanternaJei implements IModPlugin {
    static final RecipeType<Refino> REFINO = RecipeType.create(LanternaMod.MODID, "refino_emocional", Refino.class);
    static final RecipeType<Queima> QUEIMA = RecipeType.create(LanternaMod.MODID, "conversor_luz", Queima.class);

    public record Refino(int mb) {}

    public record Queima(ItemStack combustivel, int fe) {}

    @Override
    public ResourceLocation getPluginUid() {
        return LanternaMod.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration r) {
        IGuiHelper g = r.getJeiHelpers().getGuiHelper();
        r.addRecipeCategories(new CatRefino(g), new CatQueima(g));
    }

    @Override
    public void registerRecipes(IRecipeRegistration r) {
        r.addRecipes(REFINO, List.of(new Refino(Producao.MB_POR_CRISTAL)));
        r.addRecipes(QUEIMA, List.of(new Queima(new ItemStack(EnergiaRegistro.CRISTAL.get()), Producao.FE_CRISTAL),
                new Queima(new ItemStack(EnergiaRegistro.NUCLEO.get()), Producao.FE_NUCLEO)));
        boolean mek = ModList.get().isLoaded("mekanism");
        info(r, EnergiaRegistro.GERADOR_ITEM.get().getDefaultInstance(), "jei.lanterna.gerador");
        info(r, EnergiaRegistro.REFINARIA_ITEM.get().getDefaultInstance(), "jei.lanterna.refinaria");
        info(r, EnergiaRegistro.CONVERSOR_ITEM.get().getDefaultInstance(), "jei.lanterna.conversor");
        info(r, EnergiaRegistro.MINERADORA_ITEM.get().getDefaultInstance(), "jei.lanterna.mineradora");
        info(r, EnergiaRegistro.NUCLEO.get().getDefaultInstance(), mek ? "jei.lanterna.mekanism" : "jei.lanterna.mekanism_ausente");
        info(r, EnergiaRegistro.CRISTAL_BRUTO.get().getDefaultInstance(), mek ? "jei.lanterna.mekanism" : "jei.lanterna.mekanism_ausente");
        info(r, EnergiaRegistro.CRISTAL_PURO.get().getDefaultInstance(), mek ? "jei.lanterna.mekanism" : "jei.lanterna.mekanism_ausente");
        info(r, com.lanterna.guilda.GuildaItems.ESSENCIA.get().getDefaultInstance(), "jei.lanterna.essencia");
        info(r, new ItemStack(com.lanterna.registry.ModItems.FRAGMENTO_ESTELAR.get()), "jei.lanterna.fragmento");
    }

    private static void info(IRecipeRegistration r, ItemStack s, String chave) {
        r.addIngredientInfo(s, VanillaTypes.ITEM_STACK, Component.translatable(chave));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration r) {
        r.addRecipeCatalyst(new ItemStack(EnergiaRegistro.REFINARIA_ITEM.get()), REFINO);
        r.addRecipeCatalyst(new ItemStack(EnergiaRegistro.CONVERSOR_ITEM.get()), QUEIMA);
    }

    static class CatRefino implements IRecipeCategory<Refino> {
        private final IDrawable fundo, icone, seta;

        CatRefino(IGuiHelper g) {
            fundo = g.createBlankDrawable(120, 36);
            icone = g.createDrawableItemStack(new ItemStack(EnergiaRegistro.REFINARIA_ITEM.get()));
            seta = g.getRecipeArrow();
        }

        public RecipeType<Refino> getRecipeType() { return REFINO; }
        public Component getTitle() { return Component.translatable("block.lanterna.refinaria_emocional"); }
        public IDrawable getBackground() { return fundo; }
        public IDrawable getIcon() { return icone; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, Refino rec, IFocusGroup f) {
            b.addSlot(RecipeIngredientRole.INPUT, 4, 10).addFluidStack(EnergiaRegistro.EMOCAO.get(), rec.mb()).setFluidRenderer(rec.mb(), false, 16, 16);
            b.addSlot(RecipeIngredientRole.INPUT, 26, 10).addItemStack(new ItemStack(Items.REDSTONE));
            b.addSlot(RecipeIngredientRole.OUTPUT, 96, 10).addItemStack(new ItemStack(EnergiaRegistro.CRISTAL.get()));
        }

        @Override
        public void draw(Refino rec, mezz.jei.api.gui.ingredient.IRecipeSlotsView v, GuiGraphics g, double mx, double my) {
            seta.draw(g, 58, 10);
            g.drawString(Minecraft.getInstance().font, Component.translatable("jei.lanterna.segundos", Producao.TICKS_REFINO / 20), 50, 28, 0xFF808080, false);
        }
    }

    static class CatQueima implements IRecipeCategory<Queima> {
        private final IDrawable fundo, icone;

        CatQueima(IGuiHelper g) {
            fundo = g.createBlankDrawable(120, 30);
            icone = g.createDrawableItemStack(new ItemStack(EnergiaRegistro.CONVERSOR_ITEM.get()));
        }

        public RecipeType<Queima> getRecipeType() { return QUEIMA; }
        public Component getTitle() { return Component.translatable("block.lanterna.conversor_luz"); }
        public IDrawable getBackground() { return fundo; }
        public IDrawable getIcon() { return icone; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, Queima rec, IFocusGroup f) {
            b.addSlot(RecipeIngredientRole.INPUT, 4, 7).addItemStack(rec.combustivel());
        }

        @Override
        public void draw(Queima rec, mezz.jei.api.gui.ingredient.IRecipeSlotsView v, GuiGraphics g, double mx, double my) {
            g.drawString(Minecraft.getInstance().font, Component.translatable("jei.lanterna.fe", String.format("%,d", rec.fe())), 26, 11, 0xFF2E9E50, false);
        }
    }
}
