package com.teslamaps.dungeon;

// Ported from NoammAddons' WitherDragons.kt (highlight glow + centered spawn timer)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.LegitMode;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.EntityGlow;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

public final class DragonExtras {

    private DragonExtras() {}

    private static String centerText = null;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("DragonGlow").legit().when(() -> cfg().dragonGlow).tick(DragonExtras::tickGlow);
        Feature.of("DragonCenterTimer").when(() -> cfg().witherDragons && cfg().witherDragonCenterTimer)
                .tick(DragonExtras::tickCenter)
                .hudHiddenInInventory("dragoncentertimer", DragonExtras::renderCenter);
    }

    private static boolean p5() {
        Minecraft mc = Minecraft.getInstance();
        DungeonFloor f = DungeonManager.getCurrentFloor();
        return mc.player != null && f != null && f.getLevel() == 7 && DungeonManager.isInDungeon() && mc.player.getY() <= 45;
    }

    private static void tickGlow() {
        if (LegitMode.blocksCheats() || !p5()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (WitherDragons.Dragon dragon : WitherDragons.Dragon.values()) {
            if (dragon.state != WitherDragons.State.ALIVE || dragon.entityUUID == null) continue;
            DragonCheck.HealthEntry entry = DragonCheck.dragonHealthMap.get(dragon.entityUUID);
            Entity e = entry != null ? entry.dragon() : null;
            if (e == null) {
                for (EnderDragon d : mc.level.getEntitiesOfClass(EnderDragon.class, mc.player.getBoundingBox().inflate(200))) {
                    if (dragon.entityUUID.equals(d.getUUID())) { e = d; break; }
                }
            }
            if (e != null && e.isAlive()) EntityGlow.mark(e, dragon.colorArgb & 0xFFFFFF);
        }
    }

    private static void tickCenter() {
        WitherDragons.Dragon d = WitherDragons.priorityDragon;
        if (d == null || d.state != WitherDragons.State.SPAWNING || d.timeToSpawn <= 0 || !p5()) {
            centerText = null;
            return;
        }
        String color = d.timeToSpawn <= 20 ? "§c" : d.timeToSpawn <= 60 ? "§e" : "§a";
        boolean symbol = cfg().witherDragonTimerSymbol;
        String t = switch (cfg().witherDragonTimerStyle) {
            case 0 -> (d.timeToSpawn * 50) + (symbol ? "ms" : "");
            case 1 -> String.format(java.util.Locale.ROOT, "%.1f", d.timeToSpawn / 20f) + (symbol ? "s" : "");
            default -> d.timeToSpawn + (symbol ? "t" : "");
        };
        centerText = "§" + d.colorCode + d.dispName.charAt(0) + ": " + color + t;
    }

    private static void renderCenter(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        String text = centerText;
        if (text == null) return;
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(w / 2f, h * 0.4f);
        pose.scale(3f, 3f);
        ctx.text(mc.font, text, -mc.font.width(text) / 2, 0, 0xFFFFFFFF);
        pose.popMatrix();
    }
}
