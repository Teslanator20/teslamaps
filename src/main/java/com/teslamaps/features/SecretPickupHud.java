package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.ArrayList;
import java.util.List;

// legitcatmod ItemHighlight.java (pickup timer)
public final class SecretPickupHud {

    private static final int PICKUP_AGE = 11;
    private static final double RANGE = 3.5;
    private static final String[] SAMPLE = {"§c9", "§aDONE"};

    private static HudEditElement hud;
    private static boolean running;
    private static final List<String> lines = new ArrayList<>();
    private static List<String> shown = List.of();

    private SecretPickupHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("secretPickupHud", "Secret Pickup Timer").ctx(HudEditElement.HudCtx.DUNGEON).section("Timers")
                .enabled(() -> cfg().secretPickupHud)
                .size(() -> Minecraft.getInstance().font.width("DONE"), () -> 19)
                .scaleRange(0.5f, 5.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, List.of(SAMPLE)));
        Feature.of("SecretPickupHud").section("Timers").when(() -> cfg().secretPickupHud)
                .serverChat(SecretPickupHud::onChat)
                .tick(SecretPickupHud::tick)
                .hud("secretpickup", SecretPickupHud::render)
                .config("Timers", b -> {
                    b.toggle("Secret Pickup Timer HUD", () -> cfg().secretPickupHud, v -> cfg().secretPickupHud = v)
                            .desc("Ticks until each secret item within 3.5 blocks can be picked up, move it in /tmap gui");
                });
    }

    private static void onChat(String raw) {
        String t = StringUtil.strip(raw).trim();
        if (t.equals("[NPC] Mort: Here, I found this map when I first entered the dungeon.")) {
            running = true;
        } else if (running && (t.startsWith("☠ Defeated ") || t.contains("EXTRA STATS"))) {
            running = false;
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!DungeonManager.isInDungeon()) running = false;
        if (!running || mc.player == null || mc.level == null) { shown = List.of(); return; }
        lines.clear();
        for (ItemEntity item : mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(RANGE))) {
            if (item.distanceTo(mc.player) >= RANGE) continue;
            if (!SecretItemPickup.isDungeonDrop(item.getItem().getHoverName().getString())) continue;
            int age = item.getAge();
            lines.add(age < PICKUP_AGE ? "§c" + (PICKUP_AGE - age) : "§aDONE");
        }
        shown = lines.isEmpty() ? List.of() : List.copyOf(lines);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = shown;
        if (l.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), l);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        int ly = 0;
        for (String s : l) {
            g.text(mc.font, s, 0, ly, 0xFFFFFFFF, true);
            ly += 10;
        }
        g.pose().popMatrix();
    }
}
