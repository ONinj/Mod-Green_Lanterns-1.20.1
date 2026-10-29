package com.lanterna.mission;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Uma missão: uma lista de objetivos (todos ao mesmo tempo), recompensas e a próxima missão da linha.
 *
 * @param id        id único (ex: "verde_1", "main_odym_3")
 * @param line      linha da missão ("inicio", "principal", "ryut")
 * @param color     cor da missão no HUD (cor da Tropa/planeta)
 * @param objectives objetivos
 * @param rewards   itens de recompensa
 * @param affinity  afinidade com o anel ganha ao concluir
 * @param next      missão que começa sozinha ao concluir (ou null)
 */
public record Mission(String id, String line, int color, List<Objective> objectives, Supplier<List<ItemStack>> rewards,
                      int affinity, @Nullable String next, @Nullable String titleArg) {

    public Component title() {
        return titleArg == null ? Component.translatable("mission.lanterna." + id + ".title")
                : Component.translatable("mission.lanterna." + id + ".title", Component.translatable(titleArg));
    }

    public Component description() {
        return Component.translatable("mission.lanterna." + id + ".desc");
    }

    public boolean main() {
        return line.equals("principal");
    }
}
