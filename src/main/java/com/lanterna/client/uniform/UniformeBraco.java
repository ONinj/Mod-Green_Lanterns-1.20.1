package com.lanterna.client.uniform;

import com.lanterna.LanternaMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Primeira pessoa: o braço do jogador aparece com a manga do uniforme do anel (o mesmo uniforme/traje escolhido),
 * e a transformação de ligar/desligar o anel acontece também no braço que você vê.
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UniformeBraco {
    private static final EquipmentSlot[] PEITO = {EquipmentSlot.CHEST};

    private static boolean dentro;
    private static com.lanterna.armor.client.UniformeRenderer pintor;
    private static final java.util.Map<String, net.minecraft.world.item.ItemStack> PECAS = new java.util.HashMap<>();

    @SubscribeEvent
    public static void onArm(RenderArmEvent event) {
        if (dentro) return; // o braço normal desenhado daqui de dentro dispara o evento de novo
        AbstractClientPlayer player = event.getPlayer();
        float p = UniformTransition.progress(player, Minecraft.getInstance().getFrameTime());
        if (p <= 0f || player.isInvisible() || !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) return;
        String var = com.lanterna.client.visual.ClientVisual.of(player.getId()).traje();
        if ("nenhum".equals(var)) return;
        String corpsId = UniformTransition.corpsOf(player);
        String tropa = com.lanterna.armor.ModArmorItems.tropaOf(LanternaMod.id(corpsId));
        if (tropa == null || UniformTextures.hasCustom(corpsId) || !UniformDissolve3D.exists(tropa)) return;
        if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        boolean traje = var.equals("a") || var.equals("b");
        net.minecraft.world.item.ItemStack peca = PECAS.computeIfAbsent(tropa + "|" + var, k -> {
            var it = traje ? com.lanterna.armor.ModTrajes.peca(tropa, var, EquipmentSlot.CHEST) : com.lanterna.armor.ModArmorItems.pieceFor(tropa, EquipmentSlot.CHEST);
            return it == null ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(it);
        });
        if (!(peca.getItem() instanceof com.lanterna.armor.UniformeTropaItem item)) return;

        // o braço normal primeiro (isso também posiciona o modelo), depois a manga do uniforme por cima
        event.setCanceled(true);
        var ps = event.getPoseStack();
        var buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        boolean direito = event.getArm() == HumanoidArm.RIGHT;
        dentro = true;
        try {
            if (direito) renderer.renderRightHand(ps, buffers, light, player);
            else renderer.renderLeftHand(ps, buffers, light, player);
        } finally {
            dentro = false;
        }
        if (pintor == null) pintor = new com.lanterna.armor.client.UniformeRenderer();
        var geo = pintor.getGeoModel();
        var baked = geo.getBakedModel(geo.getModelResource(item));
        var osso = baked.getBone(direito ? "armorRightArm" : "armorLeftArm").orElse(null);
        if (osso == null) return;
        net.minecraft.resources.ResourceLocation tex = geo.getTextureResource(item);
        net.minecraft.resources.ResourceLocation brilho = new net.minecraft.resources.ResourceLocation(tex.getNamespace(), tex.getPath().replace(".png", "_glowmask.png"));
        int cor = com.lanterna.client.ClientVisibleRings.of(player.getId()).color();
        float cr = Math.min(1f, ((cor >> 16) & 255) / 255f + 0.3f), cg = Math.min(1f, ((cor >> 8) & 255) / 255f + 0.3f), cb = Math.min(1f, (cor & 255) / 255f + 0.3f);

        ps.pushPose();
        // do espaço do braço do jogador para o espaço do modelo 3D (o Bedrock tem x e y invertidos)
        (direito ? renderer.getModel().rightArm : renderer.getModel().leftArm).translateAndRotate(ps);
        ps.scale(-1, -1, 1);
        ps.translate(-osso.getPivotX() / 16f, -osso.getPivotY() / 16f, -osso.getPivotZ() / 16f);
        var corpo = buffers.getBuffer(net.minecraft.client.renderer.RenderType.armorCutoutNoCull(tex));
        var luz = buffers.getBuffer(net.minecraft.client.renderer.RenderType.eyes(brilho));
        var faixa = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(tex));
        int ov = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
        for (var cubo : osso.getCubes()) {
            // a manga se forma da mão até o ombro (e se desfaz ao contrário)
            float t = alturaNoBraco(cubo, osso.getPivotY() / 16f);
            if (t > p) continue;
            ps.pushPose();
            if (p < 1f && p - t < 0.12f) {
                pintor.renderCube(ps, cubo, faixa, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, ov, cr, cg, cb, 1f);
            } else {
                pintor.renderCube(ps, cubo, corpo, light, ov, 1f, 1f, 1f, 1f);
                pintor.renderCube(ps, cubo, luz, light, ov, 1f, 1f, 1f, 1f);
            }
            ps.popPose();
        }
        ps.popPose();
    }

    /** 0 = mão, 1 = ombro (pela altura do centro do cubo). */
    private static float alturaNoBraco(software.bernie.geckolib.cache.object.GeoCube cubo, float ombro) {
        float sy = 0;
        int n = 0;
        for (var q : cubo.quads()) {
            if (q == null) continue;
            for (var v : q.vertices()) { sy += v.position().y(); n++; }
        }
        if (n == 0) return 0f;
        float y = sy / n;
        // o braço vai de y 12 (mão) a 24 (ombro), em pixels do modelo
        return Math.max(0f, Math.min(1f, (y - 12f / 16f) / (12f / 16f)));
    }

    /**
     * Em primeira pessoa a transformação também se vê na tela: uma onda de luz na cor da Tropa sobe da parte de baixo
     * da tela até o topo (ligando) ou desce (desligando), com as bordas brilhando.
     */
    public static final net.minecraftforge.client.gui.overlay.IGuiOverlay HUD = (gui, g, pt, w, h) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson() || !UniformTransition.transitioning(mc.player)) return;
        float p = UniformTransition.progress(mc.player, pt);
        int cor = com.lanterna.client.ClientVisibleRings.of(mc.player.getId()).color() & 0xFFFFFF;
        float forca = (float) Math.sin(p * Math.PI);
        // bordas
        int borda = (int) (Math.min(w, h) * 0.18f);
        for (int i = 0; i < borda; i += 2) {
            float k = 1f - i / (float) borda;
            int a = (int) (150 * forca * k * k);
            int c = (a << 24) | cor;
            g.fill(0, i, w, i + 2, c);
            g.fill(0, h - i - 2, w, h - i, c);
            g.fill(i, 0, i + 2, h, c);
            g.fill(w - i - 2, 0, w - i, h, c);
        }
        // a faixa de luz que atravessa a tela (de baixo para cima)
        int y = (int) (h * (1f - p));
        int faixa = Math.max(6, h / 14);
        for (int i = 0; i < faixa; i++) {
            float k = 1f - Math.abs(i - faixa / 2f) / (faixa / 2f);
            int a = (int) (110 * forca * k);
            g.fill(0, y + i - faixa / 2, w, y + i - faixa / 2 + 1, (a << 24) | 0xFFFFFF);
            g.fill(0, y + i - faixa / 2, w, y + i - faixa / 2 + 1, ((a / 2) << 24) | cor);
        }
    };

    private UniformeBraco() {}
}
