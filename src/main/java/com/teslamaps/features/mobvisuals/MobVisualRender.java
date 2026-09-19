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
