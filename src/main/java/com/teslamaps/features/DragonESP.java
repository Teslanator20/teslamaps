package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.WitherDragons;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.EntityGlow;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DragonESP {

    private static int bossColor = -1;
    private static final java.util.List<net.minecraft.world.entity.boss.wither.WitherBoss> withers = new java.util.ArrayList<>();

    public static void register() {
        Feature.of("WitherHighlight").when(() -> TeslaMapsConfig.get().witherHighlight)
                .tick(DragonESP::tickWither);
    }

    // Ported from NoammAddons' WitherESP.kt: glow on the boss wither, colour from the bossbar
    private static void tickWither() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        DungeonFloor f = DungeonManager.getCurrentFloor();
        withers.clear();
        if (f == null || f.getLevel() != 7 || !DungeonManager.isInBossArea()) { bossColor = -1; return; }
        if (mc.level == null || mc.player == null) return;

        withers.addAll(mc.level.getEntitiesOfClass(net.minecraft.world.entity.boss.wither.WitherBoss.class,
                mc.player.getBoundingBox().inflate(60)));

        bossColor = c.witherHighlightPerBoss ? colorFromBossbar(mc, c) : -1;

        if (!c.witherHighlightGlow || LegitMode.blocksCheats() || mc.player.getY() <= 45) return;
        int rgb = (bossColor != -1 ? bossColor : TeslaMapsConfig.parseColor(c.witherHighlightColor)) & 0xFFFFFF;
        for (net.minecraft.world.entity.boss.wither.WitherBoss w : withers) {
            if (w.isInvisible() || w.getInvulnerableTicks() == 800) continue;
            EntityGlow.mark(w, rgb);
        }
    }

    private static int colorFromBossbar(Minecraft mc, TeslaMapsConfig c) {
        var events = ((com.teslamaps.mixin.BossHealthOverlayAccessor) mc.gui.getBossOverlay()).teslamaps$getEvents();
        if (events == null) return -1;
        for (net.minecraft.client.gui.components.LerpingBossEvent ev : events.values()) {
            if (ev.getName() == null) continue;
            String name = ev.getName().getString();
            String hex = name.contains("Maxor") ? c.witherColorMaxor
                    : name.contains("Storm") ? c.witherColorStorm
                    : name.contains("Goldor") ? c.witherColorGoldor
                    : name.contains("Necron") ? c.witherColorNecron : null;
            if (hex != null) return TeslaMapsConfig.parseColor(hex) & 0xFFFFFF;
        }
        return -1;
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.dragonBoxes && !c.dragonHealth && !c.witherHighlight) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        DungeonFloor f = DungeonManager.getCurrentFloor();
        if (!DungeonManager.isInDungeon() || f == null || f.getLevel() != 7) return;

        // wither highlight only during the actual boss fight (boss arena = outside the room grid)
        boolean inBossArea = com.teslamaps.scanner.ComponentGrid.worldToGrid((int) mc.player.getX(), (int) mc.player.getZ()) == null;
        boolean glowing = c.witherHighlightGlow && !LegitMode.blocksCheats();
        if (c.witherHighlight && inBossArea && !glowing) {
            int wc = c.witherHighlightPerBoss && bossColor != -1 ? 0xFF000000 | bossColor
                    : TeslaMapsConfig.parseColor(c.witherHighlightColor);
            for (net.minecraft.world.entity.boss.wither.WitherBoss w : withers) {
                if (w.isRemoved()) continue;
                AABB wb = w.getBoundingBox();
                if (c.witherHighlightBox) ESPRenderer.drawFilledBox(matrices, wb, (wc & 0xFFFFFF) | 0x66000000, cameraPos);
                else ESPRenderer.drawBoxOutline(matrices, wb, wc, 3.0f, cameraPos);
            }
        }

        for (EnderDragon d : mc.level.getEntitiesOfClass(EnderDragon.class, mc.player.getBoundingBox().inflate(150))) {
            if (d.getHealth() <= 0) continue; // dying -> skip
            int color = nearestColor(d.position());
            AABB box = d.getBoundingBox();
            if (c.dragonBoxes) ESPRenderer.drawBoxOutline(matrices, box, color, 4.0f, cameraPos);
            if (c.dragonHealth) {
                int pct = (int) (d.getMaxHealth() > 0 ? d.getHealth() / d.getMaxHealth() * 100 : 0);
                String hpColor = pct > 50 ? "§a" : pct > 25 ? "§e" : "§c";
                Vec3 top = new Vec3(d.getX(), box.maxY + 1.0, d.getZ());
                ESPRenderer.drawText(matrices, hpColor + pct + "%", top, 1.4f, cameraPos);
            }
        }
    }

    private static int nearestColor(Vec3 pos) {
        WitherDragons.Dragon best = null;
        double bestD = Double.MAX_VALUE;
        for (WitherDragons.Dragon dr : WitherDragons.Dragon.values()) {
            double dx = dr.spawnPos.getX() - pos.x, dy = dr.spawnPos.getY() - pos.y, dz = dr.spawnPos.getZ() - pos.z;
            double dist = dx * dx + dy * dy + dz * dz;
            if (dist < bestD) { bestD = dist; best = dr; }
        }
        return best == null ? 0xFFFFFFFF : (0xFF000000 | (best.colorArgb & 0xFFFFFF));
    }
}
