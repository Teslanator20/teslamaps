package com.teslamaps.features.mobvisuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Draws the tracers and nameplates that {@link MobVisuals} picked out on the tick. */
public final class MobVisualRender {

    private MobVisualRender() {}

    public static void register() {
        com.teslamaps.registry.Feature.of("MobVisualNameplates").section("Mob Visuals")
                .when(() -> TeslaMapsConfig.get().mobVisualNameplateHud)
                .hud("mobvisualnameplates", MobVisualRender::renderHud);
    }

    private static final double REFERENCE_GUI_SCALE = 4.0;

    private static void renderHud(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker d) {
        if (MobVisuals.nameplates.isEmpty() || !MobVisuals.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        org.joml.Vector3fc fwd = camera.forwardVector();
        float sw = mc.getWindow().getGuiScaledWidth();
        float sh = mc.getWindow().getGuiScaledHeight();
        float scale = (float) (REFERENCE_GUI_SCALE / mc.getWindow().getGuiScale()) * TeslaMapsConfig.get().mobVisualNameplateScale;

        for (Map.Entry<Entity, String> entry : MobVisuals.nameplates.entrySet()) {
            Entity entity = entry.getKey();
            if (entity.isRemoved()) continue;
            Vec3 center = entity.getPosition(partial).add(0, MobVisuals.renderAnchorHeight(entity), 0);
            double tx = center.x - cam.x, ty = center.y - cam.y, tz = center.z - cam.z;
            if (tx * tx + ty * ty + tz * tz < 1.0E-4) continue;
            // behind the camera the projection mirrors onto the screen
            if (tx * fwd.x() + ty * fwd.y() + tz * fwd.z() <= 0) continue;

            Vec3 ndc = mc.gameRenderer.projectPointToScreen(center);
            float x = (float) (ndc.x * 0.5 + 0.5) * sw;
            float y = (float) (0.5 - ndc.y * 0.5) * sh;

            String[] lines = entry.getValue().split("\n");
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(scale, scale);
            int top = -lines.length * mc.font.lineHeight / 2;
            for (int i = 0; i < lines.length; i++) {
                g.text(mc.font, lines[i], -mc.font.width(lines[i]) / 2, top + i * mc.font.lineHeight, 0xFFFFFFFF, true);
            }
            g.pose().popMatrix();
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (MobVisuals.tracers.isEmpty() && MobVisuals.nameplates.isEmpty()) return;
        if (!MobVisuals.active()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        TeslaMapsConfig c = TeslaMapsConfig.get();

        for (Map.Entry<Entity, Integer> entry : MobVisuals.tracers.entrySet()) {
            Entity entity = entry.getKey();
            if (entity.isRemoved()) continue;

            Vec3 target = entity.getPosition(partial)
                    .add(0, MobVisuals.renderAnchorHeight(entity), 0);
            ESPRenderer.drawTracerFromCamera(matrices, target, entry.getValue(), cameraPos);
        }

        if (c.mobVisualNameplateHud) return;
        for (Map.Entry<Entity, String> entry : MobVisuals.nameplates.entrySet()) {
            Entity entity = entry.getKey();
            if (entity.isRemoved()) continue;

            Vec3 center = entity.getPosition(partial)
                    .add(0, MobVisuals.renderAnchorHeight(entity), 0);

            String[] lines = entry.getValue().split("\n");
            // ESPRenderer grows text with distance, so the row gap has to grow with it -
            // a fixed world offset would drift apart the further away the mob is
            float dist = (float) center.distanceTo(cameraPos);
            float factor = c.mobVisualNameplateScale * 0.025f * Math.max(1f, dist / 12f);
            double rowGap = mc.font.lineHeight * factor;

            for (int i = 0; i < lines.length; i++) {
                Vec3 at = center.add(0, -i * rowGap, 0);
                ESPRenderer.drawText(matrices, lines[i], at, c.mobVisualNameplateScale, cameraPos);
            }
        }
    }
}
