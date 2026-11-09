package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.ArrayList;
import java.util.List;

// legitcatmod ItemHighlight.java (pickup timer + secret spawn timer)
public final class SecretPickupHud {

    private static final int PICKUP_AGE = 11;
    private static final double RANGE = 3.5;
    private static final String[] SAMPLE = {"§69", "§aDONE"};

    private static HudEditElement hud;
    private static boolean running;
    private static long startTick;
    private static final List<String> lines = new ArrayList<>();
    private static List<String> shown = List.of();
    private static final it.unimi.dsi.fastutil.ints.IntOpenHashSet boxIds = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
    private static final List<ItemEntity> boxItems = new ArrayList<>();
    private static final it.unimi.dsi.fastutil.ints.IntArrayList boxColors = new it.unimi.dsi.fastutil.ints.IntArrayList();

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
                .world(SecretPickupHud::renderWorld)
                .config("Timers", b -> {
                    b.toggle("Secret Pickup Timer HUD", () -> cfg().secretPickupHud, v -> cfg().secretPickupHud = v)
                            .desc("Ticks until each secret item within 3.5 blocks can be picked up, move it in /tmap gui");
                    b.toggle("  Secret Spawn Tick", () -> cfg().secretPickupHudSpawnTick, v -> cfg().secretPickupHudSpawnTick = v)
                            .desc("20-tick secret spawn cycle above the items");
                    b.toggle("  Replace Items With Box", () -> cfg().secretPickupBox, v -> cfg().secretPickupBox = v)
                            .desc("Red = too far, yellow = not pickable yet, green = ready");
                    b.slider("    Box Size", 0.3f, 2.0f, () -> cfg().secretPickupBoxSize, v -> cfg().secretPickupBoxSize = v, 1);
                });
    }

    private static void onChat(String raw) {
        String t = StringUtil.strip(raw).trim();
        if (t.equals("[NPC] Mort: Here, I found this map when I first entered the dungeon.")) {
            running = true;
            startTick = ServerTickTimer.now();
        } else if (running && (t.startsWith("☠ Defeated ") || t.contains("EXTRA STATS"))) {
            running = false;
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!DungeonManager.isInDungeon()) running = false;
        boxIds.clear();
        boxItems.clear();
        boxColors.clear();
        if (!running || mc.player == null || mc.level == null) { shown = List.of(); return; }
        if (cfg().secretPickupBox) {
            for (ItemEntity item : mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(48))) {
                if (!SecretItemPickup.isDungeonDrop(item.getItem().getHoverName().getString())) continue;
                int color = item.distanceTo(mc.player) >= RANGE ? 0xFFFF0000 : item.getAge() < PICKUP_AGE ? 0xFFFFFF00 : 0xFF00FF00;
                boxIds.add(item.getId());
                boxItems.add(item);
                boxColors.add(color);
            }
        }
        lines.clear();
        if (cfg().secretPickupHudSpawnTick) {
            int away = 20 - (int) ((ServerTickTimer.now() - startTick) % 20);
            String c = away < 5 ? "§a" : away < 10 ? "§6" : "§c";
            lines.add(c + away);
        }
        for (ItemEntity item : mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(RANGE))) {
            if (item.distanceTo(mc.player) >= RANGE) continue;
            if (!SecretItemPickup.isDungeonDrop(item.getItem().getHoverName().getString())) continue;
            int age = item.getAge();
            lines.add(age < PICKUP_AGE ? "§c" + (PICKUP_AGE - age) : "§aDONE");
        }
        shown = lines.isEmpty() ? List.of() : List.copyOf(lines);
    }

    public static boolean shouldHide(net.minecraft.world.entity.Entity e) {
        if (boxIds.isEmpty() || !(e instanceof ItemEntity)) return false;
        TeslaMapsConfig c = cfg();
        return c.secretPickupHud && c.secretPickupBox && c.section("Timers") && boxIds.contains(e.getId());
    }

    private static void renderWorld(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.world.phys.Vec3 cam) {
        if (!cfg().secretPickupBox || boxItems.isEmpty()) return;
        double h = 0.3 * cfg().secretPickupBoxSize;
        for (int i = 0; i < boxItems.size(); i++) {
            ItemEntity item = boxItems.get(i);
            if (item.isRemoved()) continue;
            net.minecraft.world.phys.AABB b = com.teslamaps.render.ESPRenderer.lerpBox(item);
            double cx = (b.minX + b.maxX) / 2, cz = (b.minZ + b.maxZ) / 2;
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(cx - h, b.minY, cz - h, cx + h, b.minY + 2 * h, cz + h);
            com.teslamaps.render.ESPRenderer.drawFilledBox(pose, box, boxColors.getInt(i), cam, true);
        }
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
