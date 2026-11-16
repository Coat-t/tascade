package com.coatt.tascade.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.CrossbowPosing;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

@Environment(EnvType.CLIENT)
public final class PlayerModels {
    private static final float RADIANS_PER_DEGREE = (float) (Math.PI / 180.0);
    private static final int FULL_BRIGHT = LightmapTextureManager.pack(15, 15);
    private static final PlayerEntityModel<LivingEntity> WIDE = model(false);
    private static final PlayerEntityModel<LivingEntity> SLIM = model(true);

    private PlayerModels() {}

    public static void draw(String uuid, int x, int y, int height, int mouseX, int mouseY) {
        PlayerListEntry entry = PlayerHeads.entry(uuid);
        if (entry == null) return;
        Identifier skin = entry.getSkinTexture();
        Identifier cape = entry.getCapeTexture();
        PlayerEntityModel<LivingEntity> model = "slim".equals(entry.getModel()) ? SLIM : WIDE;

        // same turning as InventoryScreen.drawEntity
        float centerX = x + height / 4f;
        float centerY = y + height / 2f;
        float turn = (float) Math.atan((centerX - mouseX) / 40f) * 20f;
        float tilt = (float) Math.atan((y + height / 8f - mouseY) / 40f) * 20f;
        pose(model, turn, -tilt);

        RenderSystem.pushMatrix();
        RenderSystem.translatef(centerX, centerY, 50f);
        // a model faces -z and the screen is seen from +z
        RenderSystem.scalef(1f, 1f, -1f);
        MatrixStack matrices = new MatrixStack();
        // the model spans y = -0.5 (head top) to 1.5 (feet)
        matrices.scale(height / 2f, height / 2f, height / 2f);
        matrices.multiply(Vector3f.POSITIVE_X.getDegreesQuaternion(-tilt));
        matrices.multiply(Vector3f.POSITIVE_Y.getDegreesQuaternion(turn));
        matrices.translate(0.0, -0.5, 0.0);

        VertexConsumerProvider.Immediate buffers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        RenderSystem.runAsFancy(() -> {
            model.render(matrices, buffers.getBuffer(model.getLayer(skin)), FULL_BRIGHT, OverlayTexture.DEFAULT_UV,
                    1f, 1f, 1f, 1f);
            if (cape != null) {
                // where CapeFeatureRenderer hangs it from a standing player
                matrices.push();
                matrices.translate(0.0, 0.0, 0.125);
                matrices.multiply(Vector3f.POSITIVE_X.getDegreesQuaternion(6f));
                matrices.multiply(Vector3f.POSITIVE_Y.getDegreesQuaternion(180f));
                model.renderCape(matrices, buffers.getBuffer(RenderLayer.getEntitySolid(cape)), FULL_BRIGHT,
                        OverlayTexture.DEFAULT_UV);
                matrices.pop();
            }
            buffers.draw();
        });
        RenderSystem.popMatrix();
    }

    private static PlayerEntityModel<LivingEntity> model(boolean slim) {
        PlayerEntityModel<LivingEntity> model = new PlayerEntityModel<>(0f, slim);
        // models start out as a baby's, LivingEntityRenderer sets this per entity
        model.child = false;
        return model;
    }

    private static void pose(PlayerEntityModel<LivingEntity> model, float headYaw, float headPitch) {
        model.head.yaw = headYaw * RADIANS_PER_DEGREE;
        model.head.pitch = headPitch * RADIANS_PER_DEGREE;
        model.helmet.copyPositionAndRotation(model.head);

        // the slim model's arms are built half a pixel lower
        model.rightArm.pivotY = 2f;
        model.leftArm.pivotY = 2f;
        model.rightArm.pitch = 0f;
        model.rightArm.roll = 0f;
        model.leftArm.pitch = 0f;
        model.leftArm.roll = 0f;
        CrossbowPosing.method_29350(model.rightArm, model.leftArm, Util.getMeasuringTimeMs() / 50f);
        // the sleeves are built out of place until put on the arms
        model.rightSleeve.copyPositionAndRotation(model.rightArm);
        model.leftSleeve.copyPositionAndRotation(model.leftArm);
    }
}
