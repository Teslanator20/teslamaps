package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// The exploding frog drops a lily pad item display where it lands, which is easy to lose
// in the water. Nothing else in the fishing areas renders a lily pad that way.
public class ExplodingFrogESP {

    private static final double RANGE = 64;

    private static final List<Display.ItemDisplay> found = new ArrayList<>();
    private static int tickCounter = 0;

    public static void tick() {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.explodingFrogESP || LegitMode.blocksCheats()) {
            found.clear();
            return;
        }
        if (++tickCounter % 5 != 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            found.clear();
            return;
        }

        found.clear();
        for (Display.ItemDisplay display : mc.level.getEntitiesOfClass(Display.ItemDisplay.class,
                mc.player.getBoundingBox().inflate(RANGE))) {
            ItemStack stack = display.getSlot(0).get();
            if (stack.is(Items.LILY_PAD)) found.add(display);
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.explodingFrogESP || found.isEmpty() || LegitMode.blocksCheats()) return;

        int color = TeslaMapsConfig.parseColor(cfg.colorExplodingFrog);
        for (Display.ItemDisplay display : found) {
            if (!display.isAlive()) continue;
            Vec3 pos = display.position();
            AABB box = new AABB(pos.x - 0.5, pos.y - 0.1, pos.z - 0.5,
                    pos.x + 0.5, pos.y + 0.9, pos.z + 0.5);
            ESPRenderer.drawESPBox(matrices, box, color, cameraPos);
            ESPRenderer.drawText(matrices, "§aExploding Frog", pos.add(0, 1.2, 0), 1.2f, cameraPos);
            if (cfg.explodingFrogTracer) {
                ESPRenderer.drawTracerFromCamera(matrices, box.getCenter(), color, cameraPos);
            }
        }
    }
}
