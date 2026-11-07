package com.lanterna.client.energia;

import com.lanterna.client.render.LanternaRenderTypes;
import com.lanterna.energia.MaquinaBlock;
import com.lanterna.energia.MineradoraBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A Mineradora Planetária montada em volta do painel: torre de perfuração (estilo plataforma) sobre o poço atrás do
 * painel, com a broca girando e subindo/descendo e anéis de energia descendo pelo eixo; ao lado, um braço
 * escavadeira que vai ao poço, enche a caçamba e despeja no coletor na frente. Monta subindo do chão quando é
 * instalada. Luz no topo: verde trabalhando, vermelha sem energia, amarela cheia, cinza sem dono, piscando danificada.
 * Coordenadas locais: o painel olha para -Z; a torre fica atrás (+Z).
 */
public class MineradoraRenderer implements BlockEntityRenderer<MineradoraBE> {
    public MineradoraRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public boolean shouldRenderOffScreen(MineradoraBE be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    private static final float PX = 0.5f, PZ = 3.5f;     // centro do poço
    private static final float AX = 3.3f, AZ = 1.2f;     // eixo do braço
    private static final float BX = 3.3f, BZ = -1.6f;    // coletor

    @Override
    public void render(MineradoraBE be, float pt, PoseStack ps, MultiBufferSource buf, int luzBloco, int overlay) {
        if (be.getLevel() == null || !be.instalada) return;
        Direction f = be.getBlockState().getValue(MaquinaBlock.FACING);
        int luz = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());
        float gt = be.getLevel().getGameTime() + pt;
        float mont = Mth.clamp((gt - be.montadaEm) / 70f, 0, 1);
        float a = be.anim(pt);
        boolean trabalhando = be.estado == 1;

        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180 - f.toYRot()));
        ps.translate(-0.5, 0, -0.5);

        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        TextureAtlasSprite ferro = Pecas.sprite("iron_block"), escuro = Pecas.sprite("polished_blackstone"), bigorna = Pecas.sprite("anvil"),
                amarelo = Pecas.sprite("yellow_concrete"), netherite = Pecas.sprite("netherite_block"), diamante = Pecas.sprite("diamond_block"),
                deepslate = Pecas.sprite("polished_deepslate"), vidro = Pecas.sprite("glass"), funil = Pecas.sprite("hopper_outside"),
                funilTopo = Pecas.sprite("hopper_inside"), pedra = Pecas.sprite("cobblestone");

        // ---- torre de perfuração (sobe do chão na montagem) ----
        float alturaTorre = 8.6f * suave(mont / 0.6f);
        float topo = alturaTorre;
        ps.pushPose();
        // sapatas
        for (int i = 0; i < 4; i++) {
            float sx = (i & 1) == 0 ? -1.7f : 1.7f, sz = (i & 2) == 0 ? -1.7f : 1.7f;
            Pecas.caixa(ps, vc, deepslate, PX + sx - 0.45f, 0, PZ + sz - 0.45f, PX + sx + 0.45f, 0.35f, PZ + sz + 0.45f, luz, 0xFFFFFF);
        }
        if (alturaTorre > 0.4f) {
            Vector3f[] base = new Vector3f[4], cima = new Vector3f[4];
            for (int i = 0; i < 4; i++) {
                float sx = (i == 0 || i == 3) ? -1 : 1, sz = (i < 2) ? -1 : 1;
                base[i] = new Vector3f(PX + sx * 1.7f, 0.3f, PZ + sz * 1.7f);
                cima[i] = new Vector3f(PX + sx * 0.4f, topo, PZ + sz * 0.4f);
                Pecas.viga(ps, vc, bigorna, base[i], cima[i], 0.22f, luz, 0xD8D8D8);
            }
            // anéis horizontais e as cruzes entre eles
            int niveis = 4;
            for (int n = 1; n <= niveis; n++) {
                float t0 = (n - 1) / (float) niveis, t1 = n / (float) niveis;
                if (t1 * 8.6f > alturaTorre + 0.01f) break;
                for (int i = 0; i < 4; i++) {
                    int j = (i + 1) % 4;
                    Vector3f a1 = lerp(base[i], cima[i], t1), b1 = lerp(base[j], cima[j], t1);
                    Vector3f a0 = lerp(base[i], cima[i], t0), b0 = lerp(base[j], cima[j], t0);
                    Pecas.viga(ps, vc, ferro, a1, b1, 0.12f, luz, 0xFFFFFF);
                    Pecas.viga(ps, vc, ferro, a0, b1, 0.08f, luz, 0xC0C0C0);
                }
            }
            // coroa e polia
            if (mont > 0.55f) {
                Pecas.caixa(ps, vc, escuro, PX - 0.75f, topo - 0.1f, PZ - 0.75f, PX + 0.75f, topo + 0.35f, PZ + 0.75f, luz, 0xFFFFFF);
                ps.pushPose();
                ps.translate(PX, topo + 0.75f, PZ);
                ps.mulPose(Axis.XP.rotationDegrees(a * 9f));
                Pecas.caixa(ps, vc, netherite, -0.12f, -0.38f, -0.38f, 0.12f, 0.38f, 0.38f, luz, 0xFFFFFF);
                ps.popPose();
                Pecas.caixa(ps, vc, ferro, PX - 0.06f, topo + 0.3f, PZ - 0.06f, PX + 0.06f, topo + 0.8f, PZ + 0.06f, luz, 0xFFFFFF);
            }
        }
        // ---- broca: eixo girando, subindo e descendo ----
        if (mont > 0.6f) {
            float sobe = trabalhando ? 0.9f + 1.1f * Mth.sin(a * 0.035f) : 1.8f;
            float pontaY = -0.6f + sobe;
            ps.pushPose();
            ps.translate(PX, 0, PZ);
            ps.mulPose(Axis.YP.rotationDegrees(a * 20f));
            Pecas.caixa(ps, vc, ferro, -0.13f, pontaY + 0.9f, -0.13f, 0.13f, topo - 0.1f, 0.13f, luz, 0xE0E0E0);
            // cabeça da broca: degraus afinando
            for (int k = 0; k < 4; k++) {
                float r = 0.42f - k * 0.1f;
                Pecas.caixa(ps, vc, diamante, -r, pontaY + 0.9f - (k + 1) * 0.22f, -r, r, pontaY + 0.9f - k * 0.22f, r, luz, 0xFFFFFF);
            }
            ps.popPose();
        }
        // ---- base do braço e cabine ----
        float montBraco = suave((mont - 0.35f) / 0.5f);
        if (montBraco > 0) {
            ps.pushPose();
            ps.translate(AX, 0, AZ);
            ps.scale(montBraco, montBraco, montBraco);
            Pecas.caixa(ps, vc, deepslate, -0.8f, 0, -0.8f, 0.8f, 0.45f, 0.8f, luz, 0xFFFFFF);
            float[] braco = braco(a, trabalhando);
            ps.mulPose(Axis.YP.rotation(braco[0]));
            Pecas.caixa(ps, vc, amarelo, -0.65f, 0.45f, -0.65f, 0.65f, 0.95f, 0.65f, luz, 0xFFFFFF);
            Pecas.caixa(ps, vc, amarelo, -0.55f, 0.95f, -0.75f, 0.15f, 1.75f, 0.05f, luz, 0xFFFFFF);  // cabine
            Pecas.caixa(ps, vc, vidro, -0.5f, 1.05f, 0.04f, 0.1f, 1.65f, 0.08f, luz, 0xFFFFFF);
            Pecas.caixa(ps, vc, escuro, -0.2f, 0.45f, -0.95f, 0.5f, 1.1f, -0.65f, luz, 0xFFFFFF); // contrapeso
            // lança e braço de baixo
            float alcance = braco[1], desce = braco[2];
            Vector3f ombro = new Vector3f(0.25f, 1.0f, 0.3f);
            Vector3f cotovelo = new Vector3f(0.25f, 3.1f, alcance * 0.62f);
            Vector3f ponta = new Vector3f(0.25f, 3.1f - desce, alcance);
            Pecas.viga(ps, vc, amarelo, ombro, cotovelo, 0.32f, luz, 0xFFFFFF);
            Pecas.viga(ps, vc, amarelo, cotovelo, ponta, 0.26f, luz, 0xFFFFFF);
            Pecas.viga(ps, vc, ferro, new Vector3f(0.25f, 0.95f, 0.1f), lerp(ombro, cotovelo, 0.5f), 0.1f, luz, 0xFFFFFF); // pistão
            // caçamba (vira para despejar)
            ps.pushPose();
            ps.translate(ponta.x, ponta.y, ponta.z);
            ps.mulPose(Axis.XP.rotationDegrees(braco[3]));
            Pecas.caixa(ps, vc, netherite, -0.42f, -0.55f, -0.35f, 0.42f, -0.05f, 0.35f, luz, 0xFFFFFF);
            Pecas.caixa(ps, vc, ferro, -0.42f, -0.62f, 0.25f, 0.42f, -0.5f, 0.42f, luz, 0xFFFFFF);
            if (braco[4] > 0) Pecas.caixa(ps, vc, pedra, -0.32f, -0.12f, -0.25f, 0.32f, 0.02f, 0.25f, luz, 0xFFFFFF);
            ps.popPose();
            ps.popPose();
            // coletor
            Pecas.caixa(ps, vc, funil, BX - 0.65f, 0, BZ - 0.65f, BX + 0.65f, 0.9f, BZ + 0.65f, luz, 0xFFFFFF);
            Pecas.caixa(ps, vc, funilTopo, BX - 0.5f, 0.9f, BZ - 0.5f, BX + 0.5f, 0.92f, BZ + 0.5f, luz, 0x909090);
        }
        ps.popPose();

        // ---- luz: anéis de energia descendo pelo eixo e o farol do topo ----
        int cor = switch (be.estado) {
            case 1 -> 0x4CFF8A;
            case 2 -> 0xFF3A30;
            case 3 -> 0xFFD040;
            case 5 -> (((int) gt / 6) % 2 == 0) ? 0xFF3A30 : 0x301010;
            default -> 0x808080;
        };
        VertexConsumer brilho = buf.getBuffer(LanternaRenderTypes.BRILHO);
        Matrix4f m = ps.last().pose();
        if (mont > 0.6f) {
            float pulso = trabalhando ? 0.75f + 0.25f * Mth.sin(gt * 0.3f) : 0.45f;
            cubo(brilho, m, PX, topo + 1.0f, PZ, 0.16f, cor, pulso);
            if (trabalhando) {
                for (int k = 0; k < 3; k++) {
                    float t = ((a * 0.02f + k / 3f) % 1f);
                    float y = topo - 0.4f - t * (topo - 0.8f);
                    anel(brilho, m, PX, y, PZ, 0.32f + 0.1f * t, 0x4CFF8A, 0.8f * (1 - t));
                }
            }
            // luzinhas nos cantos da torre
            for (int i = 0; i < 4; i++) {
                float sx = (i == 0 || i == 3) ? -1 : 1, sz = (i < 2) ? -1 : 1;
                cubo(brilho, m, PX + sx * 1.05f, topo * 0.5f, PZ + sz * 1.05f, 0.05f, cor, 0.6f);
            }
        }
        // montagem: grade de luz subindo
        if (mont < 1) {
            float y = mont * 9f;
            anel(brilho, m, PX, y, PZ, 2.1f, 0x4CFF8A, 0.9f * (1 - mont));
            anel(brilho, m, AX, Math.min(y, 2f), AZ, 1f, 0x4CFF8A, 0.9f * (1 - mont));
        }
        // tela do painel
        VertexConsumer corpo = buf.getBuffer(LanternaRenderTypes.ENERGIA);
        float tela = 0.55f + 0.1f * Mth.sin(gt * 0.15f);
        quad(corpo, m, 0.2f, 0.95f, -0.01f, 0.8f, 1.35f, -0.01f, cor, tela);
        ps.popPose();
    }

    /**
     * Braço: {giro (rad), alcance, descida, inclinação da caçamba (graus), cheia (0/1)}.
     * Ciclo de 180 ticks: vai ao poço, cava, volta ao coletor, despeja.
     */
    private static float[] braco(float a, boolean trabalhando) {
        float giroPoco = (float) Math.atan2(PX - AX, PZ - AZ), giroColetor = (float) Math.atan2(BX - AX, BZ - AZ);
        float alcPoco = Mth.sqrt((PX - AX) * (PX - AX) + (PZ - AZ) * (PZ - AZ)) - 0.9f;
        float alcCol = Mth.sqrt((BX - AX) * (BX - AX) + (BZ - AZ) * (BZ - AZ));
        if (!trabalhando) return new float[]{giroColetor, alcCol, 1.0f, 0, 0};
        float t = a % 180f;
        float giro, alc, desce, inc = 0, cheia = 0;
        if (t < 45) {           // gira para o poço
            float k = suave(t / 45f);
            giro = lerpAng(giroColetor, giroPoco, k); alc = Mth.lerp(k, alcCol, alcPoco); desce = 1.0f;
        } else if (t < 85) {    // desce, cava e sobe
            float k = (t - 45) / 40f;
            giro = giroPoco + Mth.sin(k * Mth.PI * 2) * 0.05f; alc = alcPoco; desce = 1.0f + 2.3f * Mth.sin(k * Mth.PI);
            inc = -25 + 50 * k; cheia = k > 0.5f ? 1 : 0;
        } else if (t < 130) {   // volta ao coletor
            float k = suave((t - 85) / 45f);
            giro = lerpAng(giroPoco, giroColetor, k); alc = Mth.lerp(k, alcPoco, alcCol); desce = 1.0f; inc = 25; cheia = 1;
        } else if (t < 160) {   // despeja
            float k = (t - 130) / 30f;
            giro = giroColetor; alc = alcCol; desce = 1.0f + 0.5f * Mth.sin(k * Mth.PI); inc = 25 - 120 * Mth.sin(k * Mth.PI); cheia = k < 0.4f ? 1 : 0;
        } else {
            giro = giroColetor; alc = alcCol; desce = 1.0f;
        }
        return new float[]{giro, alc, desce, inc, cheia};
    }

    private static float lerpAng(float a, float b, float t) {
        float d = Mth.wrapDegrees((float) Math.toDegrees(b - a));
        return a + (float) Math.toRadians(d) * t;
    }

    private static float suave(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static Vector3f lerp(Vector3f a, Vector3f b, float t) {
        return new Vector3f(a).lerp(b, t);
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int rgb, float a) {
        vc.vertex(m, x, y, z).color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, a).uv(x * 0.3f + z * 0.3f, y * 0.3f).endVertex();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, float a) {
        v(vc, m, x0, y0, z0, rgb, a);
        v(vc, m, x1, y0, z1, rgb, a);
        v(vc, m, x1, y1, z1, rgb, a);
        v(vc, m, x0, y1, z0, rgb, a);
    }

    private static void cubo(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float r, int rgb, float a) {
        float x0 = cx - r, x1 = cx + r, y0 = cy - r, y1 = cy + r, z0 = cz - r, z1 = cz + r;
        quad(vc, m, x0, y0, z0, x1, y1, z0, rgb, a);
        quad(vc, m, x0, y0, z1, x1, y1, z1, rgb, a);
        quad(vc, m, x0, y0, z0, x0, y1, z1, rgb, a);
        quad(vc, m, x1, y0, z0, x1, y1, z1, rgb, a);
        v(vc, m, x0, y1, z0, rgb, a); v(vc, m, x1, y1, z0, rgb, a); v(vc, m, x1, y1, z1, rgb, a); v(vc, m, x0, y1, z1, rgb, a);
        v(vc, m, x0, y0, z0, rgb, a); v(vc, m, x1, y0, z0, rgb, a); v(vc, m, x1, y0, z1, rgb, a); v(vc, m, x0, y0, z1, rgb, a);
    }

    /** Anel quadrado horizontal de luz (contorno com 4 faixas). */
    private static void anel(VertexConsumer vc, Matrix4f m, float cx, float y, float cz, float r, int rgb, float a) {
        if (a <= 0.01f) return;
        float w = 0.07f;
        float[][] lados = {{-r, -r, r, -r + w}, {-r, r - w, r, r}, {-r, -r, -r + w, r}, {r - w, -r, r, r}};
        for (float[] l : lados) {
            v(vc, m, cx + l[0], y, cz + l[1], rgb, a);
            v(vc, m, cx + l[2], y, cz + l[1], rgb, a);
            v(vc, m, cx + l[2], y, cz + l[3], rgb, a);
            v(vc, m, cx + l[0], y, cz + l[3], rgb, a);
        }
    }
}
