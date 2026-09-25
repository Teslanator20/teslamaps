package com.teslamaps.features;

// Ported from DulkirMod's features/KeeperWaypoints.kt and NametagCleaner.kt (keeper focus)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SkyblockUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

public final class KeeperWaypoints {

    private static final Vec3[] SPOTS = {
            new Vec3(-208.5, 44.5, -259.5), new Vec3(-311.5, 43.5, -232.5), new Vec3(-230.5, 57.5, -307.5),
            new Vec3(-269.5, 47.5, -166.5), new Vec3(-292.5, 47.5, -167.5), new Vec3(-291.5, 47.5, -183.5),
            new Vec3(-282.5, 47.5, -195.5), new Vec3(-262.5, 49.5, -191.5), new Vec3(-269.5, 61.5, -159.5)};
    private static final String[] LABELS = {"§d1", "§d2", "§d3", "§d4", "§d5", "§d6", "§d7", "§d8", "§d9"};

    private static boolean inDen;
    private static IntOpenHashSet hidden = new IntOpenHashSet();
    private static int timer;

    private KeeperWaypoints() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("KeeperWaypoints").section("Combat").when(() -> cfg().keeperWaypoints || cfg().keeperFocusMode)
                .tick(KeeperWaypoints::tick)
                .world(KeeperWaypoints::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Spider's Den");
                    b.toggle("Arachne's Keeper Spawns", () -> c.keeperWaypoints, v -> c.keeperWaypoints = v)
                            .desc("The 9 Arachne's Keeper spawn points as numbered waypoints.")
                            .tags("keeper", "arachne", "spider", "waypoint");
                    b.toggle("  Keeper Focus Mode", () -> c.keeperFocusMode, v -> c.keeperFocusMode = v)
                            .desc("Hides every name tag in the Spider's Den that does not contain \"Keeper\".");
                });
    }

    public static boolean shouldHide(Entity e) {
        return e instanceof ArmorStand && !hidden.isEmpty() && hidden.contains(e.getId()) && cfg().keeperFocusMode && cfg().section("Combat");
    }

    private static void tick() {
        if (++timer % 10 != 0) return;
        inDen = SkyblockUtils.isInArea("Spider's Den");
        TeslaMapsConfig c = cfg();
        IntOpenHashSet next = new IntOpenHashSet();
        if (inDen && c.keeperWaypoints && c.keeperFocusMode) {
            Minecraft mc = Minecraft.getInstance();
            for (Entity e : mc.level.getEntities((Entity) null, mc.player.getBoundingBox().inflate(96),
                    x -> x instanceof ArmorStand && x.hasCustomName())) {
                Component n = e.getCustomName();
                if (n != null && !n.getString().contains("Keeper")) next.add(e.getId());
            }
        }
        hidden = next;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!inDen || !cfg().keeperWaypoints) return;
        for (int i = 0; i < SPOTS.length; i++) ESPRenderer.drawText(pose, LABELS[i], SPOTS[i], 2.5f, cam, true, true);
    }
}
