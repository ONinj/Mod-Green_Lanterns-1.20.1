package com.lanterna.morte;

import com.lanterna.LanternaMod;
import com.lanterna.lore.LoreWorldData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * 0.50: Ryut deixou de ter cidade e Tropa (só o Santuário da Forja, com o Coletor, e o altar do Devorador de Ecos).
 *
 * Mundos já criados tinham a cidade inteira gerada em Ryut. Na primeira vez que o mundo abre nesta versão, a pasta do
 * planeta é guardada com outro nome (nada é apagado: "dimensions/lanterna/ryut_cidade_antiga") e o planeta nasce de
 * novo, já no desenho novo. Isso acontece antes de o mundo carregar, então ninguém vê nada pela metade.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RyutReforma {
    private static final String MARCA = "lanterna_ryut_0_50.txt";
    private static boolean refeito;

    @SubscribeEvent
    public static void antes(ServerAboutToStartEvent e) {
        refeito = false;
        try {
            Path raiz = e.getServer().getWorldPath(LevelResource.ROOT);
            Path marca = raiz.resolve(MARCA);
            if (Files.exists(marca)) return;
            Path ryut = raiz.resolve("dimensions").resolve(LanternaMod.MODID).resolve("ryut");
            if (Files.isDirectory(ryut)) {
                Path destino = ryut.resolveSibling("ryut_cidade_antiga");
                for (int i = 2; Files.exists(destino); i++) destino = ryut.resolveSibling("ryut_cidade_antiga_" + i);
                Files.move(ryut, destino);
                refeito = true;
                LanternaMod.LOGGER.info("Lanterna: Ryut sem cidade (0.50): o planeta antigo foi guardado em {}", destino);
            }
            Files.writeString(marca, "Ryut ja no desenho da 0.50 (so o Santuario da Forja). Apague este arquivo apenas se souber o que faz.\n");
        } catch (Exception ex) {
            LanternaMod.LOGGER.warn("Lanterna: não consegui refazer Ryut: {}", ex.toString());
        }
    }

    @SubscribeEvent
    public static void depois(ServerStartedEvent e) {
        if (!refeito) return;
        refeito = false;
        LoreWorldData d = LoreWorldData.get(e.getServer());
        for (String s : new ArrayList<>(d.sites())) if (s.startsWith("ryut:") || s.equals("arena_muro:ryut")) d.resetSite(s);
        com.lanterna.cidade.CidadeUnica.esquecer();
    }

    private RyutReforma() {}
}
