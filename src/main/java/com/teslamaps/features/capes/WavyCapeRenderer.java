package com.teslamaps.features.capes;

// Ported from WaveyCapes' CustomCapeRenderer (smooth style) + CustomCapeRenderLayer
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class WavyCapeRenderer {

    private static final int PART_COUNT = 16;
    private static final float CAPE_WIDTH = 10F / 16F;
    private static final float CAPE_HEIGHT = 1F;
    private static final float CAPE_DEPTH = 1F / 16F;
    private static final float TEXEL_U = 1F / 64F;
    private static final float TEXEL_V = 1F / 32F;

    /** Returns false when no simulation exists yet - caller falls back to the rigid cape. */
    public static boolean submit(PoseStack pose, SubmitNodeCollector collector, int light,
                                 AvatarRenderState state, HumanoidModel<AvatarRenderState> model,
                                 Identifier texture, boolean chestplate) {
        StickSimulation3d sim = CapeSimulation.get(state.id);
        if (sim == null || sim.empty()) return false;

        float delta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Entity entity = Minecraft.getInstance().level == null ? null
                : Minecraft.getInstance().level.getEntity(state.id);
        boolean underwater = entity != null && entity.isUnderWater();

        model.resetPose();
        model.setupAnim(state);
        ModelPart root = model.root();

        pose.pushPose();
        root.translateAndRotate(pose);
        root.getChild("body").translateAndRotate(pose);
        if (chestplate) {
            pose.translate(0.0F, -0.053125F, 0.06875F);
        }

        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(texture), (p, vc) -> {
            PoseStack shared = new PoseStack();
            shared.last().set(p);
            renderSmoothCape(shared, vc, sim, delta, light, underwater);
        });
        pose.popPose();
        return true;
    }

    private static void renderSmoothCape(PoseStack poseStack, VertexConsumer vc, StickSimulation3d sim,
                                         float delta, int light, boolean underwater) {
        Matrix4f[] positionMatrices = new Matrix4f[PART_COUNT];
        Vector3f[] frontNormals = new Vector3f[PART_COUNT];
        Vector3f[] backNormals = new Vector3f[PART_COUNT];
        boolean inverse = light == 15728880;
        float h = CAPE_HEIGHT / PART_COUNT;

        for (int part = 0; part < PART_COUNT; part++) {
            modifyPoseStack(poseStack, sim, delta, part, underwater);
            positionMatrices[part] = new Matrix4f(poseStack.last().pose());
            Matrix4f prev = positionMatrices[Math.max(part - 1, 0)];
            frontNormals[part] = getNormalVec(prev, prev, positionMatrices[part],
                    new Vector3f(CAPE_WIDTH / 2F, part * h, -CAPE_DEPTH),
                    new Vector3f(-CAPE_WIDTH / 2F, part * h, -CAPE_DEPTH),
                    new Vector3f(CAPE_WIDTH / 2F, (part + 1) * h, -CAPE_DEPTH), inverse);
            backNormals[part] = getNormalVec(prev, prev, positionMatrices[part],
                    new Vector3f(CAPE_WIDTH / 2F, (part + 1) * h, 0),
                    new Vector3f(-CAPE_WIDTH / 2F, (part + 1) * h, 0),
                    new Vector3f(CAPE_WIDTH / 2F, part * h, 0), inverse);
            poseStack.popPose();
        }

        for (int part = 0; part < PART_COUNT; part++) {
            Matrix4f mat = positionMatrices[part];
            Matrix4f matPrev = positionMatrices[Math.max(part - 1, 0)];

            if (part == 0) {
                float minU = 1 * TEXEL_U;
                float maxU = 11 * TEXEL_U;
                float minV = 0;
                float maxV = TEXEL_V;
                Vector3f n = getNormalVec(mat, mat, mat,
                        new Vector3f(CAPE_WIDTH / 2, 0, 0), new Vector3f(-CAPE_WIDTH / 2, 0, 0),
                        new Vector3f(CAPE_WIDTH / 2, 0, CAPE_DEPTH), inverse);
                addVertex(vc, mat, CAPE_WIDTH / 2, 0, 0, maxU, maxV, light, n);
                addVertex(vc, mat, -CAPE_WIDTH / 2, 0, 0, minU, maxV, light, n);
                addVertex(vc, mat, -CAPE_WIDTH / 2, 0, -CAPE_DEPTH, minU, minV, light, n);
                addVertex(vc, mat, CAPE_WIDTH / 2, 0, -CAPE_DEPTH, maxU, minV, light, n);
            }

            if (part == PART_COUNT - 1) {
                float minU = 11 * TEXEL_U;
                float maxU = 21 * TEXEL_U;
                float minV = 0;
                float maxV = TEXEL_V;
                Vector3f n = getNormalVec(mat, mat, mat,
                        new Vector3f(CAPE_WIDTH / 2F, CAPE_HEIGHT, -CAPE_DEPTH),
                        new Vector3f(-CAPE_WIDTH / 2F, CAPE_HEIGHT, -CAPE_DEPTH),
                        new Vector3f(CAPE_WIDTH / 2F, CAPE_HEIGHT, 0), inverse);
                addVertex(vc, mat, CAPE_WIDTH / 2F, CAPE_HEIGHT, -CAPE_DEPTH, maxU, minV, light, n);
                addVertex(vc, mat, -CAPE_WIDTH / 2F, CAPE_HEIGHT, -CAPE_DEPTH, minU, minV, light, n);
                addVertex(vc, mat, -CAPE_WIDTH / 2F, CAPE_HEIGHT, 0, minU, maxV, light, n);
                addVertex(vc, mat, CAPE_WIDTH / 2F, CAPE_HEIGHT, 0, maxU, maxV, light, n);
            }

            float minU = 0;
            float maxU = 1 * TEXEL_U;
            float minV = TEXEL_V * (part + 1);
            float maxV = minV + TEXEL_V;

            Vector3f n = getNormalVec(mat, mat, matPrev,
                    new Vector3f(-CAPE_WIDTH / 2F, (part + 1) * h, 0),
                    new Vector3f(-CAPE_WIDTH / 2F, (part + 1) * h, -CAPE_DEPTH),
                    new Vector3f(-CAPE_WIDTH / 2F, part * h, 0), inverse);
            addVertex(vc, mat, -CAPE_WIDTH / 2F, (part + 1) * h, 0, minU, maxV, light, n);
            addVertex(vc, mat, -CAPE_WIDTH / 2F, (part + 1) * h, -CAPE_DEPTH, maxU, maxV, light, n);
            addVertex(vc, matPrev, -CAPE_WIDTH / 2F, part * h, -CAPE_DEPTH, maxU, minV, light, n);
            addVertex(vc, matPrev, -CAPE_WIDTH / 2F, part * h, 0, minU, minV, light, n);

            minU = 11 * TEXEL_U;
            maxU = 12 * TEXEL_U;
            n = getNormalVec(mat, mat, matPrev,
                    new Vector3f(CAPE_WIDTH / 2F, (part + 1) * h, -CAPE_DEPTH),
                    new Vector3f(CAPE_WIDTH / 2F, (part + 1) * h, 0),
                    new Vector3f(CAPE_WIDTH / 2F, part * h, -CAPE_DEPTH), inverse);
            addVertex(vc, mat, CAPE_WIDTH / 2F, (part + 1) * h, -CAPE_DEPTH, minU, maxV, light, n);
            addVertex(vc, mat, CAPE_WIDTH / 2F, (part + 1) * h, 0, maxU, maxV, light, n);
            addVertex(vc, matPrev, CAPE_WIDTH / 2F, part * h, 0, maxU, minV, light, n);
            addVertex(vc, matPrev, CAPE_WIDTH / 2F, part * h, -CAPE_DEPTH, minU, minV, light, n);

            minU = 1 * TEXEL_U;
            maxU = 11 * TEXEL_U;
            Vector3f nTop = frontNormals[part].add(frontNormals[Math.max(part - 1, 0)], new Vector3f()).div(2);
            Vector3f nBottom = frontNormals[part].add(frontNormals[Math.min(part + 1, PART_COUNT - 1)], new Vector3f()).div(2);
            addVertex(vc, matPrev, CAPE_WIDTH / 2, part * h, -CAPE_DEPTH, maxU, minV, light, nTop);
            addVertex(vc, matPrev, -CAPE_WIDTH / 2, part * h, -CAPE_DEPTH, minU, minV, light, nTop);
            addVertex(vc, mat, -CAPE_WIDTH / 2, (part + 1) * h, -CAPE_DEPTH, minU, maxV, light, nBottom);
            addVertex(vc, mat, CAPE_WIDTH / 2, (part + 1) * h, -CAPE_DEPTH, maxU, maxV, light, nBottom);

            minU = 12 * TEXEL_U;
            maxU = 22 * TEXEL_U;
            nTop = backNormals[part].add(backNormals[Math.max(part - 1, 0)], new Vector3f()).div(2);
            nBottom = backNormals[part].add(backNormals[Math.min(part + 1, PART_COUNT - 1)], new Vector3f()).div(2);
            addVertex(vc, matPrev, CAPE_WIDTH / 2, part * h, 0, minU, minV, light, nTop);
            addVertex(vc, matPrev, -CAPE_WIDTH / 2, part * h, 0, maxU, minV, light, nTop);
            addVertex(vc, mat, -CAPE_WIDTH / 2, (part + 1) * h, 0, maxU, maxV, light, nBottom);
            addVertex(vc, mat, CAPE_WIDTH / 2, (part + 1) * h, 0, minU, maxV, light, nBottom);
        }
    }

    private static void modifyPoseStack(PoseStack poseStack, StickSimulation3d sim, float delta,
                                        int part, boolean underwater) {
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.0D, 0.125D);

        float x = sim.points.get(part).getLerpX(delta) - sim.points.get(0).getLerpX(delta);
        if (x > 0) {
            x = 0;
        }
        float y = sim.points.get(0).getLerpY(delta) - part - sim.points.get(part).getLerpY(delta);
        float z = sim.points.get(0).getLerpZ(delta) - sim.points.get(part).getLerpZ(delta);
        float partRotation = getRotation(delta, part, sim);
        float naturalWindSwing = getNaturalWindSwing(part, underwater);

        poseStack.rotate(Axis.XP.rotationDegrees(6.0F + naturalWindSwing));
        poseStack.rotate(Axis.YP.rotationDegrees(180.0F));
        poseStack.translate(-z / PART_COUNT, y / PART_COUNT, x / PART_COUNT);
        // offsetting so the rotation is on the cape part
        poseStack.translate(0, 0.48F / 16F, -0.48F / 16F);
        poseStack.translate(0, part * 1F / PART_COUNT, 0);
        poseStack.rotate(Axis.XP.rotationDegrees(-partRotation));
        poseStack.translate(0, -part * 1F / PART_COUNT, 0);
        poseStack.translate(0, -0.48F / 16F, 0.48F / 16F);
    }

    private static float getRotation(float delta, int part, StickSimulation3d sim) {
        if (part == PART_COUNT - 1) {
            return getRotation(delta, part - 1, sim);
        }
        StickSimulation3d.Point a = sim.points.get(part);
        StickSimulation3d.Point b = sim.points.get(part + 1);
        return (float) (Math.toDegrees(Math.atan2(b.getLerpX(delta) - a.getLerpX(delta),
                b.getLerpY(delta) - a.getLerpY(delta))) + 180);
    }

    /** WaveyCapes' WindMode.WAVES, amplitude scaled by the waviness slider. */
    private static float getNaturalWindSwing(int part, boolean underwater) {
        float waviness = TeslaMapsConfig.get().capeWaviness;
        long highlightedPart = (System.currentTimeMillis() / (underwater ? 9 : 3)) % 360;
        float relativePart = (float) (part + 1) / PART_COUNT;
        return (float) (Math.sin(Math.toRadians(relativePart * 360 - highlightedPart)) * 3) * waviness;
    }

    private static Vector3f getNormalVec(Matrix4f m1, Matrix4f m2, Matrix4f m3,
                                         Vector3f v1, Vector3f v2, Vector3f v3, boolean inverse) {
        Vector4f a = m1.transform(new Vector4f(v1, 1));
        Vector4f b = m2.transform(new Vector4f(v2, 1));
        Vector4f c = m3.transform(new Vector4f(v3, 1));
        Vector3f ab = new Vector3f(b.x - a.x, b.y - a.y, b.z - a.z);
        Vector3f ac = new Vector3f(c.x - a.x, c.y - a.y, c.z - a.z);
        ab.cross(ac);
        if (ab.lengthSquared() < 1.0E-8F) {
            ab.set(0, 0, inverse ? 1 : -1);
            return ab;
        }
        ab.normalize();
        return inverse ? ab.mul(-1) : ab;
    }

    private static void addVertex(VertexConsumer vc, Matrix4f matrix, float x, float y, float z,
                                  float u, float v, int light, Vector3f n) {
        vc.addVertex(matrix, x, y, z).setColor(1f, 1f, 1f, 1f).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
    }
}
