package com.teslamaps.features.cosmetic;

// Ported from FloydAddons' features/impl/cosmetic/FloydConeHatLayer.java
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

public class ConeHatLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private static final int SEGMENTS = 64;

    public ConeHatLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float f, float g) {
        if (state.isInvisible || !ConeHat.activeFor(state.id)) return;
        var c = com.teslamaps.config.TeslaMapsConfig.get();
        float height = c.coneHatHeight, radius = c.coneHatRadius;
        pose.pushPose();
        ModelPart head = getParentModel().head;
        head.translateAndRotate(pose);
        pose.mulPose(Axis.ZP.rotation(-head.zRot));
        pose.mulPose(Axis.XP.rotation(-head.xRot));
        pose.translate(0f, c.coneHatYOffset, 0f);
        pose.mulPose(Axis.YP.rotationDegrees(ConeHat.rotation()));
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(ConeHat.texture()),
                (entry, vc) -> drawCone(entry, vc, light, height, radius));
        pose.popPose();
    }

    private static void drawCone(PoseStack.Pose pose, VertexConsumer vc, int light, float height, float radius) {
        float apexY = -height;
        float inv = 1f / (float) Math.sqrt(height * height + radius * radius);
        float horiz = height * inv, vert = radius * inv;
        for (int i = 0; i < SEGMENTS; i++) {
            double a1 = 2.0 * Math.PI * i / SEGMENTS, a2 = 2.0 * Math.PI * (i + 1) / SEGMENTS;
            float x1 = (float) (radius * Math.cos(a1)), z1 = (float) (radius * Math.sin(a1));
            float x2 = (float) (radius * Math.cos(a2)), z2 = (float) (radius * Math.sin(a2));
            float u1 = (float) i / SEGMENTS, u2 = (float) (i + 1) / SEGMENTS;
            double mid = (a1 + a2) / 2.0;
            float nx = (float) Math.cos(mid) * horiz, nz = (float) Math.sin(mid) * horiz;
            vertex(vc, pose, 0f, apexY, 0f, u1, 0f, light, nx, vert, nz);
            vertex(vc, pose, 0f, apexY, 0f, u2, 0f, light, nx, vert, nz);
            vertex(vc, pose, x2, 0f, z2, u2, 1f, light, nx, vert, nz);
            vertex(vc, pose, x1, 0f, z1, u1, 1f, light, nx, vert, nz);
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int light, float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
