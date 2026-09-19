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
            // second line hangs below the first, so the name stays on the mob's centre
            for (int i = 0; i < lines.length; i++) {
                Vec3 at = center.add(0, -i * 0.25 * c.mobVisualNameplateScale, 0);
                ESPRenderer.drawText(matrices, lines[i], at, c.mobVisualNameplateScale, cameraPos);
            }
        }
    }
}
