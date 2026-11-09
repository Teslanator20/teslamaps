package com.teslamaps.features;

// Ported from NoammAddons' floor7/LeapCounter.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.registry.HudEditElement.HudCtx;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PlayersLeaped {

    private static final String SAMPLE = "§40§9/4 Players Leaped";

    private static final AABB[] P3_SECTIONS = {
            new AABB(90, 158, 123, 111, 105, 32),
            new AABB(16, 158, 122, 111, 105, 143),
            new AABB(19, 158, 48, -3, 106, 142),
            new AABB(91, 158, 50, -3, 106, 30),
    };
    private static final AABB IN_CORE = new AABB(68, 106, 54, 42, 155, 119);

    private interface Check { boolean test(double x, double y, double z); }

    private enum Region {
        SS_BOX(new AABB(106, 119, 92, 109, 121, 96), 3, (x, y, z) -> p3Section(x, y, z) == 1),
        EE2_BOX(new AABB(57, 108, 130, 59, 110, 132), 4, (x, y, z) -> p3Section(x, y, z) == 2),
        HEE2_BOX(new AABB(57, 132, 138, 62, 133, 140), 4, (x, y, z) -> p3Section(x, y, z) == 2),
        EE3_BOX(new AABB(1, 108, 101, 3, 110, 107), 3, (x, y, z) -> p3Section(x, y, z) == 3),
        CORE_BOX(new AABB(51, 114, 54, 58, 117, 49), 4, (x, y, z) -> p3Section(x, y, z) == 4),
        INCORE_BOX(new AABB(51, 114, 54, 58, 117, 49).move(0, 0, 6), 4, (x, y, z) -> IN_CORE.contains(x, y, z)),
        RELIC_BOX(new AABB(46.5, 3, 68.5, 62.5, 8, 84.5), 4, (x, y, z) -> phase(y) == 5);

        final AABB box;
        final int max;
        final Check check;
        final Set<Integer> leapedIds = new HashSet<>();
        boolean completed;
        long completeAt = -1;

        Region(AABB box, int max, Check check) { this.box = box; this.max = max; this.check = check; }

        int maxCount() { return Math.min(teammates().size(), max); }
    }

    private static HudEditElement hud;
    private static Region current;
    private static Object lastLevel;

    private PlayersLeaped() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("playersLeaped", "Players Leaped").ctx(HudCtx.DUNGEON)
                .enabled(() -> cfg().playersLeaped)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("PlayersLeaped").when(() -> cfg().playersLeaped)
                .tick(PlayersLeaped::tick)
                .hud("playersleaped", PlayersLeaped::render)
                .config("F7 / M7", b -> {
                    b.label("Players Leaped");
                    b.toggle("Players Leaped Counter", () -> cfg().playersLeaped, v -> cfg().playersLeaped = v)
                            .desc("NoammAddons' \"0/4 Players Leaped\" at SS, EE2, EE3, Core and P5. Position in /tmap gui.");
                    b.toggle("  Everyone Leaped Alert", () -> cfg().playersLeapedAlert, v -> cfg().playersLeapedAlert = v);
                });
    }

    private static int phase(double y) {
        if (!inF7Boss()) return -1;
        if (y > 210) return 1;
        if (y > 155) return 2;
        if (y > 100) return 3;
        if (y > 45) return 4;
        return 5;
    }

    private static int p3Section(double x, double y, double z) {
        Player p = Minecraft.getInstance().player;
        if (p == null || phase(p.getY()) != 3) return -1;
        for (int i = 0; i < P3_SECTIONS.length; i++) if (P3_SECTIONS[i].contains(x, y, z)) return i + 1;
        return -1;
    }

    private static boolean inF7Boss() {
        DungeonFloor f = DungeonManager.getCurrentFloor();
        return f != null && f.getLevel() == 7 && DungeonManager.isInBossArea();
    }

    private static List<Player> teammates() {
        Minecraft mc = Minecraft.getInstance();
        List<Player> out = new ArrayList<>();
        if (mc.player == null || mc.level == null) return out;
        String self = mc.player.getName().getString();
        for (var dp : PlayerTracker.getPlayers()) {
            if (dp.getName().equals(self) || dp.getUuid() == null) continue;
            Player pl = mc.level.getPlayerByUUID(dp.getUuid());
            if (pl != null && pl != mc.player) out.add(pl);
        }
        return out;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            for (Region r : Region.values()) { r.leapedIds.clear(); r.completed = false; r.completeAt = -1; }
            current = null;
        }
        long now = System.currentTimeMillis();
        for (Region r : Region.values()) if (r.completeAt > 0 && now >= r.completeAt) { r.completed = true; r.completeAt = -1; }

        current = null;
        if (!inF7Boss()) return;
        for (Region r : Region.values()) {
            if (!r.completed && r.box.contains(mc.player.position())) { current = r; break; }
        }
        if (current == null) return;

        for (Player pl : teammates()) {
            if (current.leapedIds.contains(pl.getId())) continue;
            if (!current.check.test(pl.getX(), pl.getY(), pl.getZ())) continue;
            current.leapedIds.add(pl.getId());
            int max = current.maxCount();
            if (current.leapedIds.size() == max && cfg().playersLeapedAlert) {
                mc.gui.hud.setTimes(10, 40, 10);
                mc.gui.hud.setTitle(Component.literal("§aEveryone Leaped!"));
                mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }
            if (current.leapedIds.size() >= max && current.completeAt < 0) current.completeAt = now + 1000;
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        Region r = current;
        if (r == null || mc.gui.hud.isHidden()) return;
        int max = r.maxCount();
        if (max <= 0) return;
        int count = r.leapedIds.size();
        String start = max - count <= 1 ? "§9" : "§4";
        draw(g, mc, hud.x(), hud.y(), hud.scale(), start + count + "§9/" + max + " Players Leaped");
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String s) {
        int w = mc.font.width(SAMPLE);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.text(mc.font, s, (w - mc.font.width(s)) / 2, 0, 0xFFFFFFFF);
        pose.popMatrix();
    }
}
