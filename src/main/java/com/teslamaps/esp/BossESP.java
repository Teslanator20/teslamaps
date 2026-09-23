package com.teslamaps.esp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Boxes the F7 Wither bosses (Maxor/Storm/Goldor/Necron — WitherBoss entities). Depth Check off = through walls.
public class BossESP {

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.bossESP) return;
        if (!DungeonManager.isInDungeon()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // only during the boss fight, not the dungeon clear
        if (!DungeonManager.isInBossArea()) return;

        int color = TeslaMapsConfig.parseColor(c.colorBossESP);
        boolean throughWalls = !c.bossESPDepthCheck;

        for (Entity e : com.teslamaps.utils.EntityCache.byType(WitherBoss.class)) {
            if (!(e instanceof WitherBoss wb) || wb.isRemoved() || wb.isInvisible()) continue;
            // player-summoned withers (wither armor/cloak, abilities) spawn with 800 invuln ticks,
            // the real bosses use 200 (Maxor) or 0/1 — filter like NoammAddons
            if (wb.getInvulnerableTicks() > 400) continue;
            AABB box = ESPRenderer.lerpBox(wb);
            if (c.bossESPFilled) {
                ESPRenderer.drawFilledBox(matrices, box, color, cameraPos, throughWalls);
            } else {
                ESPRenderer.drawBoxOutline(matrices, box, color, 2.0f, cameraPos, throughWalls);
            }
        }
    }
}
