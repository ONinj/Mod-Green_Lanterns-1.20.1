package com.lanterna.client.uniform;

import com.lanterna.LanternaMod;
import com.lanterna.client.ClientVisibleRings;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * O uniforme como troca de skin: a roupa é pintada pixel por pixel na própria skin do jogador (no mesmo modelo, sem
 * espessura nenhuma), em vez de uma armadura 3D por cima. As texturas vêm de tools/achatar_trajes.py, que "achata" cada
 * uniforme/traje 3D no layout de skin (textures/skin_traje/<arquivo>_<wide|slim>.png, 4x de resolução).
 *
 * Só a camada de baixo da skin é trocada: o cabelo/chapéu da segunda camada continua por cima, então a máscara fica por
 * baixo dele. A jaqueta, as mangas e a calça da segunda camada somem enquanto o traje está vestido.
 *
 * A transformação: o alfa de cada pixel guarda quando o líquido chega nele (FluxoUniforme), e o shader traje_pele só
 * mostra o que já foi coberto, com a beirada acesa na cor da Tropa.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PeleTraje {
    private static ShaderInstance shader;
    /** Só para comparar nos testes com o uniforme 3D antigo. */
    public static boolean desligado;

    @SubscribeEvent
    public static void shaders(RegisterShadersEvent e) throws java.io.IOException {
        e.registerShader(new ShaderInstance(e.getResourceProvider(), LanternaMod.id("traje_pele"), DefaultVertexFormat.NEW_ENTITY),
                s -> shader = s);
    }

    private static final RenderStateShard.ShaderStateShard SHADER = new RenderStateShard.ShaderStateShard(() -> shader);
    private static final Map<ResourceLocation, RenderType> TIPOS = new HashMap<>();

    private static RenderType tipo(ResourceLocation tex) {
        return TIPOS.computeIfAbsent(tex, t -> Tipo.criar(t));
    }

    /** Acesso aos estados protegidos do RenderType. */
    private static final class Tipo extends RenderType {
        private Tipo(String a, VertexFormat b, VertexFormat.Mode c, int d, boolean e, boolean f, Runnable g, Runnable h) {
            super(a, b, c, d, e, f, g, h);
        }

        static RenderType criar(ResourceLocation tex) {
            return create("lanterna_traje_pele", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, false,
                    CompositeState.builder()
                            .setShaderState(SHADER)
                            .setTextureState(new TextureStateShard(tex, false, false))
                            .setTransparencyState(NO_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .createCompositeState(true));
        }
    }

    // ------------------------------------------------------------------ texturas

    private record Pele(ResourceLocation tex, @Nullable ResourceLocation brilho) {}

    private static final Map<String, Pele> PELES = new HashMap<>();
    private static final Map<String, Boolean> EXISTE = new HashMap<>();

    /** O arquivo do uniforme que o jogador veste agora (uniforme_<tropa> ou traje_<tropa>_<variação>), ou null. */
    @Nullable
    public static String arquivo(AbstractClientPlayer player) {
        if (desligado) return null;
        String var = com.lanterna.client.visual.ClientVisual.of(player.getId()).traje();
        if ("nenhum".equals(var)) return null;
        String corpsId = UniformTransition.corpsOf(player);
        if (UniformTextures.hasCustom(corpsId)) return null;
        String tropa = com.lanterna.armor.ModArmorItems.tropaOf(LanternaMod.id(corpsId));
        if (tropa == null) return null;
        String arq = com.lanterna.visual.Visual.eSegundaPele(var) ? "traje_" + tropa + "_" + var : "uniforme_" + tropa;
        return existe(arq + "_wide") ? arq : null;
    }

    private static boolean existe(String nome) {
        return EXISTE.computeIfAbsent(nome, n -> Minecraft.getInstance().getResourceManager()
                .getResource(LanternaMod.id("textures/skin_traje/" + n + ".png")).isPresent());
    }

    private static boolean slim(AbstractClientPlayer p) {
        return "slim".equals(p.getModelName());
    }

    @Nullable
    private static Pele pele(String arq, boolean slim) {
        String k = arq + "_" + (slim ? "slim" : "wide");
        if (PELES.containsKey(k)) return PELES.get(k);
        Pele pl = null;
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(LanternaMod.id("textures/skin_traje/" + k + ".png"))) {
            NativeImage img = NativeImage.read(in);
            int esc = img.getWidth() / 64;
            // alfa = quando o líquido chega no pixel (1..255); 0 = sem traje (mostra a skin)
            for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
                int c = img.getPixelRGBA(x, y);
                if (((c >>> 24) & 255) < 24) { img.setPixelRGBA(x, y, 0); continue; }
                float t = FluxoUniforme.pele(x / esc, y / esc);
                int a = 1 + Math.round(Math.max(0f, Math.min(1f, t < 0 ? 0f : t)) * 254f);
                img.setPixelRGBA(x, y, (c & 0x00FFFFFF) | (a << 24));
            }
            ResourceLocation tex = LanternaMod.id("dynamic/pele_" + k);
            Minecraft.getInstance().getTextureManager().register(tex, new DynamicTexture(img));
            ResourceLocation brilho = existe(k + "_glow") ? LanternaMod.id("textures/skin_traje/" + k + "_glow.png") : null;
            pl = new Pele(tex, brilho);
        } catch (Exception e) {
            LanternaMod.LOGGER.warn("Lanterna: não deu para carregar a skin do traje {}: {}", k, e.toString());
        }
        PELES.put(k, pl);
        return pl;
    }

    public static void limpar() {
        var tm = Minecraft.getInstance().getTextureManager();
        PELES.values().forEach(p -> { if (p != null) tm.release(p.tex); });
        PELES.clear();
        EXISTE.clear();
    }

    // ------------------------------------------------------------------ desenho

    /** Corpo inteiro (terceira pessoa). Devolve false se esse uniforme não tem a versão skin. */
    public static boolean corpo(PoseStack ps, MultiBufferSource buf, int luz, AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> m, float p) {
        String arq = arquivo(player);
        if (arq == null) return false;
        Pele pl = pele(arq, slim(player));
        if (pl == null) return false;
        int cor = ClientVisibleRings.of(player.getId()).color();
        float r = ((cor >> 16) & 255) / 255f, g = ((cor >> 8) & 255) / 255f, b = (cor & 255) / 255f;
        int ov = LivingEntityRenderer.getOverlayCoords(player, 0f);
        // só as partes de baixo (a segunda camada da skin fica como está); onde há armadura de verdade, não pinta
        ModelPart[] partes = {m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
        boolean[] cobre = {
                player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),
                player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
                player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
                player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
                player.getItemBySlot(EquipmentSlot.LEGS).isEmpty() && player.getItemBySlot(EquipmentSlot.FEET).isEmpty(),
                player.getItemBySlot(EquipmentSlot.LEGS).isEmpty() && player.getItemBySlot(EquipmentSlot.FEET).isEmpty()};
        var vc = buf.getBuffer(tipo(pl.tex));
        for (int i = 0; i < partes.length; i++) {
            if (!cobre[i] || !partes[i].visible) continue;
            partes[i].render(ps, vc, luz, ov, r, g, b, Math.max(0.0005f, p));
        }
        // o que brilha no escuro (lentes, emblema, fibras de energia), depois de formado
        if (p >= 1f && pl.brilho != null) {
            var eyes = buf.getBuffer(RenderType.eyes(pl.brilho));
            for (int i = 0; i < partes.length; i++) {
                if (!cobre[i] || !partes[i].visible) continue;
                partes[i].render(ps, eyes, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
            }
        }
        return true;
    }

    /** Só um braço (primeira pessoa), já posicionado pelo desenho da mão normal. */
    public static boolean braco(PoseStack ps, MultiBufferSource buf, int luz, AbstractClientPlayer player, ModelPart braco, float p) {
        String arq = arquivo(player);
        if (arq == null) return false;
        Pele pl = pele(arq, slim(player));
        if (pl == null) return false;
        int cor = ClientVisibleRings.of(player.getId()).color();
        braco.render(ps, buf.getBuffer(tipo(pl.tex)), luz, OverlayTexture.NO_OVERLAY,
                ((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f, Math.max(0.0005f, p));
        if (p >= 1f && pl.brilho != null)
            braco.render(ps, buf.getBuffer(RenderType.eyes(pl.brilho)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        return true;
    }

    private PeleTraje() {}
}
