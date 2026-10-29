package com.lanterna.client.uniform;

import com.lanterna.LanternaMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * De onde vem a textura do uniforme de cada Tropa:
 *
 *  1) Se existir  .minecraft/config/lanterna/uniformes/<tropa>.png  (ex: green.png), usa ela.
 *     É uma skin normal de 64x64: pinte só o uniforme e deixe o resto transparente.
 *  2) Senão, usa o uniforme provisório que vem no mod.
 */
public final class UniformTextures {
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();
    private static final Map<String, NativeImage> SOURCE = new HashMap<>();

    /** Cópia da imagem do uniforme (para montar a animação pixel por pixel). Pode ser null. */
    public static NativeImage image(String corps) {
        get(corps);
        return SOURCE.computeIfAbsent(corps, c -> {
            try (InputStream in = Minecraft.getInstance().getResourceManager()
                    .open(LanternaMod.id("textures/uniform/" + c + ".png"))) {
                return NativeImage.read(in);
            } catch (Exception e) {
                return null;
            }
        });
    }

    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve("lanterna").resolve("uniformes");
    }

    public static ResourceLocation get(String corps) {
        return CACHE.computeIfAbsent(corps, UniformTextures::load);
    }

    private static ResourceLocation load(String corps) {
        Path custom = folder().resolve(corps + ".png");
        if (Files.isRegularFile(custom)) {
            try (InputStream in = Files.newInputStream(custom)) {
                NativeImage image = NativeImage.read(in);
                NativeImage copy = new NativeImage(image.getWidth(), image.getHeight(), false);
                copy.copyFrom(image);
                SOURCE.put(corps, copy);
                ResourceLocation id = LanternaMod.id("dynamic/uniform_" + corps);
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
                LanternaMod.LOGGER.info("Lanterna: uniforme personalizado carregado: {}", custom);
                return id;
            } catch (Exception e) {
                LanternaMod.LOGGER.error("Lanterna: não consegui ler {}: {}", custom, e.getMessage());
            }
        }
        return LanternaMod.id("textures/uniform/" + corps + ".png");
    }

    /** Cria a pasta com um LEIA-ME explicando como trocar os uniformes. */
    public static void createFolder() {
        try {
            Path dir = folder();
            Files.createDirectories(dir);
            Path readme = dir.resolve("LEIA-ME.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme, """
                        UNIFORMES DO MOD LANTERNA
                        =========================
                        Coloque aqui uma skin de 64x64 para cada Tropa, com estes nomes:

                          green.png   yellow.png   red.png    blue.png    violet.png
                          indigo.png  orange.png   black.png  white.png

                        Pinte SÓ o uniforme e deixe o resto transparente: o que for
                        transparente mostra a skin do jogador por baixo (o rosto, por exemplo).
                        O uniforme aparece quando o anel é ligado (tecla G).
                        Depois de trocar uma textura, reinicie o jogo.
                        """);
            }
        } catch (Exception e) {
            LanternaMod.LOGGER.warn("Lanterna: não consegui criar a pasta de uniformes: {}", e.getMessage());
        }
    }

    private UniformTextures() {}
}
