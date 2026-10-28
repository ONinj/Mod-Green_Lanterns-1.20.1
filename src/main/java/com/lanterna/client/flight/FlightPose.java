package com.lanterna.client.flight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Pose de voo do anel:
 *  - parado no ar: braços levemente abertos, pernas soltas balançando
 *  - em movimento: corpo deitado na direção do olhar, braço da mão principal
 *    esticado para frente (punho na frente), o outro colado ao corpo, pernas retas
 */
public final class FlightPose {

    /**
     * Inclina o corpo inteiro. Chamado (via Mixin) logo depois que o Minecraft gira o modelo
     * para a direção do corpo, então aqui basta deitar em volta do eixo "de lado".
     */
    public static void tiltBody(Player player, PoseStack poseStack, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player && mc.screen != null) return; // na tela do inventário fica em pé
        float k = ClientFlight.tilt(player, partialTick);
        if (k <= 0.001f) return;

        float pitch = player.getViewXRot(partialTick);
        float angle = k * (-90f - pitch);

        poseStack.translate(0, 0.9, 0);
        poseStack.mulPose(Axis.XP.rotationDegrees(angle));
        poseStack.translate(0, -0.9, 0);
    }

    /** Ajusta braços, pernas e cabeça (chamado no fim da animação do modelo, via Mixin). */
    public static void applyLimbs(HumanoidModel<?> model, LivingEntity entity, float ageInTicks) {
        if (!(entity instanceof Player player) || !ClientFlight.isFlying(player) || player.onGround()) return;

        float partial = Minecraft.getInstance().getFrameTime();
        float k = ClientFlight.tilt(player, partial);
        float bob = Mth.sin(ageInTicks * 0.12f);

        // Sem agachar no ar (Shift é "descer")
        model.crouching = false;
        model.body.xRot = 0;
        model.body.y = 0;
        model.head.y = 0;
        model.rightArm.y = 2;
        model.leftArm.y = 2;
        model.rightLeg.y = 12;
        model.leftLeg.y = 12;
        model.rightLeg.z = 0.1f;
        model.leftLeg.z = 0.1f;

        boolean rightMain = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
        var front = rightMain ? model.rightArm : model.leftArm;
        var back = rightMain ? model.leftArm : model.rightArm;
        float side = rightMain ? 1f : -1f;

        // --- Pairando (k = 0) ---
        float hoverFrontX = -0.25f + bob * 0.05f;
        float hoverBackX = -0.15f - bob * 0.05f;
        float hoverZ = 0.25f;
        float hoverLegX = 0.12f + bob * 0.06f;

        // --- Voando deitado (k = 1) ---
        float flyFrontX = -(float) Math.PI;      // braço esticado para frente
        float flyBackX = 0.05f;                  // braço colado ao corpo
        float flyLegX = 0.04f * Mth.sin(ageInTicks * 0.3f);

        front.xRot = Mth.lerp(k, hoverFrontX, flyFrontX);
        front.yRot = Mth.lerp(k, 0f, 0f);
        front.zRot = side * Mth.lerp(k, hoverZ, 0.05f);

        back.xRot = Mth.lerp(k, hoverBackX, flyBackX);
        back.yRot = 0;
        back.zRot = -side * Mth.lerp(k, hoverZ, 0.08f);

        model.rightLeg.xRot = Mth.lerp(k, hoverLegX, flyLegX);
        model.leftLeg.xRot = Mth.lerp(k, -hoverLegX * 0.6f, -flyLegX);
        model.rightLeg.yRot = 0;
        model.leftLeg.yRot = 0;
        model.rightLeg.zRot = Mth.lerp(k, 0.05f, 0.02f);
        model.leftLeg.zRot = Mth.lerp(k, -0.05f, -0.02f);

        // Cabeça olhando para frente mesmo com o corpo deitado
        model.head.xRot = Mth.lerp(k, model.head.xRot, -(float) Math.PI * 0.42f);
        model.head.yRot = Mth.lerp(k, model.head.yRot, 0f);
        model.hat.copyFrom(model.head);
    }

    private FlightPose() {}
}
