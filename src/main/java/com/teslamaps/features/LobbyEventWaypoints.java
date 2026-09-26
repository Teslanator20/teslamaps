package com.teslamaps.features;

// Ported from SkyHanni's lobby/waypoints (EasterEggWaypoints, HalloweenBasketWaypoints, PresentWaypoints);
// basket + present spots from SkyHanni-REPO (island_graphs/MAIN_LOBBY, EventWaypoints)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class LobbyEventWaypoints {

    private static final int[][] EGGS = {
            {-45, 93, 3}, {-27, 80, 8}, {-12, 65, 3}, {20, 68, -30}, {19, 58, -45}, {35, 64, 20}, {85, 67, -27}, {91, 63, -117},
            {129, 71, -141}, {69, 60, -165}, {67, 105, -137}, {-5, 61, -182}, {-123, 55, -182}, {-127, 70, -180}, {-158, 66, -130},
            {-179, 82, -16}, {-140, 100, 0}, {-48, 87, 30}, {-43, 87, 39}, {-15, 65, 109}, {34, 59, 118}, {49, 65, 91}, {96, 69, 120},
            {162, 60, 130}, {177, 96, -6}, {188, 57, -46}, {-47, 65, 152}, {-71, 61, 203}, {-68, 54, 243}, {55, 64, 251}};
    private static final int[][] BASKETS = {
            {-89, 61, 133}, {6, 70, 55}, {91, 68, 132}, {105, 68, 104}, {123, 52, -84}, {-29, 83, 122}, {63, 106, -136}, {-41, 93, -25},
            {-54, 87, -41}, {-18, 86, -84}, {10, 65, -4}, {-43, 88, 48}, {-42, 85, 85}, {-109, 63, 93}, {10, 65, 102}, {130, 52, 177},
            {-84, 120, 1}, {-200, 91, 0}, {-161, 63, 55}, {-73, 62, -161}, {-144, 73, -135}, {108, 69, -9}, {141, 61, 3}, {130, 88, -14},
            {83, 63, -69}};
    private static final int[][] PRESENTS = {
            {-39, 92, -26}, {15, 64, 182}, {-6, 65, 124}, {25, 66, 96}, {64, 58, 106}, {70, 67, 151}, {103, 68, 140}, {-60, 85, -40},
            {-19, 87, -79}, {-53, 83, -87}, {-108, 64, -71}, {-51, 94, -2}, {-160, 57, -102}, {-223, 51, -1}, {-178, 63, 39}, {35, 64, 20},
            {81, 75, 7}, {153, 64, -41}, {105, 87, -49}, {143, 80, -103}, {133, 68, -136}, {115, 58, -175}, {-2, 60, 174}, {12, 51, -238},
            {59, 105, -137}, {57, 62, -80}, {-75, 128, 58}, {-68, 120, -3}, {-146, 106, 22}, {-135, 69, -13}, {11, 32, 0}, {-22, 62, -125},
            {-90, 59, -171}, {0, 55, 252}, {-131, 71, -128}, {-98, 110, 46}, {-53, 92, 22}, {-50, 85, 32}, {-131, 55, 176}, {-77, 60, 169}};

    private static final Pattern PRESENT_FOUND = Pattern.compile("^You found a.*present! \\(\\d+/\\d+\\)$");

    private enum Kind { NONE, EASTER, HALLOWEEN, CHRISTMAS }

    private static final class Spot {
        final BlockPos pos;
        final String label;
        boolean found;
        Spot(int[] p, String label) { this.pos = new BlockPos(p[0], p[1], p[2]); this.label = label; }
    }

    private static Kind kind = Kind.NONE;
    private static List<Spot> spots = List.of();
    private static Spot closest;
    private static int color;
    private static int ticks;

    private LobbyEventWaypoints() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("LobbyEventWaypoints").section("Secrets & Waypoints").when(() -> cfg().lobbyEventWaypoints)
                .tickAlways(LobbyEventWaypoints::tick)
                .serverChat(LobbyEventWaypoints::onChat)
                .world(LobbyEventWaypoints::render)
                .config("Secrets & Waypoints", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Hypixel Lobby Events");
                    b.toggle("Lobby Event Waypoints", () -> c.lobbyEventWaypoints, v -> { c.lobbyEventWaypoints = v; reset(); })
                            .desc("Main Hypixel lobby (not SkyBlock): waypoints to the event collectibles. Found ones disappear via chat.");
                    b.toggle("  Easter Eggs", () -> c.lobbyEasterEggs, v -> { c.lobbyEasterEggs = v; reset(); });
                    b.toggle("  Halloween Baskets", () -> c.lobbyHalloweenBaskets, v -> { c.lobbyHalloweenBaskets = v; reset(); });
                    b.toggle("  Christmas Presents", () -> c.lobbyPresents, v -> { c.lobbyPresents = v; reset(); });
                    b.toggle("  Only Closest", () -> c.lobbyWaypointsOnlyClosest, v -> c.lobbyWaypointsOnlyClosest = v);
                });
    }

    private static void reset() {
        kind = Kind.NONE;
        spots = List.of();
        closest = null;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { if (kind != Kind.NONE) reset(); return; }
        if (++ticks % 20 != 0) return;
        Kind k = detect();
        if (k != kind) {
            kind = k;
            closest = null;
            spots = build(k);
        }
        if (spots.isEmpty()) return;
        Spot best = null;
        double bd = Double.MAX_VALUE;
        for (Spot s : spots) {
            if (s.found) continue;
            double d = mc.player.distanceToSqr(s.pos.getX() + 0.5, s.pos.getY() + 0.5, s.pos.getZ() + 0.5);
            if (d < bd) { bd = d; best = s; }
        }
        closest = best;
    }

    private static Kind detect() {
        if (!SkyblockUtils.isOnHypixel() || SkyblockUtils.isInSkyblock()) return Kind.NONE;
        if (!StringUtil.stripTrim(ScoreboardUtils.getScoreboardTitle()).equals("HYPIXEL")) return Kind.NONE;
        TeslaMapsConfig c = cfg();
        boolean easter = false, basket = false;
        for (String l : ScoreboardUtils.getScoreboardLines()) {
            String s = ScoreboardUtils.cleanLine(l);
            if (s.startsWith("Easter Eggs:")) easter = true;
            else if (s.startsWith("Baskets Found:")) basket = true;
        }
        if (easter && c.lobbyEasterEggs) return Kind.EASTER;
        if (basket && c.lobbyHalloweenBaskets) return Kind.HALLOWEEN;
        if (c.lobbyPresents && LocalDate.now().getMonth() == Month.DECEMBER) return Kind.CHRISTMAS;
        return Kind.NONE;
    }

    private static List<Spot> build(Kind k) {
        int[][] src;
        String prefix;
        switch (k) {
            case EASTER -> { src = EGGS; prefix = "§3Egg #"; color = 0x55FFFF; }
            case HALLOWEEN -> { src = BASKETS; prefix = "§dBasket #"; color = 0xFF55FF; }
            case CHRISTMAS -> { src = PRESENTS; prefix = "§6Present "; color = 0xFFAA00; }
            default -> { return List.of(); }
        }
        List<Spot> out = new ArrayList<>(src.length);
        for (int i = 0; i < src.length; i++) out.add(new Spot(src[i], prefix + (i + 1)));
        return out;
    }

    private static void onChat(String text) {
        if (kind == Kind.NONE) return;
        String t = StringUtil.stripTrim(text);
        boolean found = switch (kind) {
            case EASTER -> t.startsWith("You found an Easter Egg!") || t.equals("You have received the super reward!")
                    || t.equals("You already found this egg!");
            case HALLOWEEN -> t.startsWith("You found a Candy Basket!") || t.equals("You already found this Candy Basket!");
            case CHRISTMAS -> t.equals("You have already found this present!") || (t.startsWith("You found a") && PRESENT_FOUND.matcher(t).matches());
            default -> false;
        };
        boolean all = t.equals("Congratulations! You found all Candy Baskets!")
                || t.equals("Congratulations! You found all the presents in every lobby!");
        if (all) {
            for (Spot s : spots) s.found = true;
            closest = null;
            return;
        }
        if (!found) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Spot best = null;
        double bd = Double.MAX_VALUE;
        for (Spot s : spots) {
            double d = mc.player.distanceToSqr(s.pos.getX() + 0.5, s.pos.getY() + 0.5, s.pos.getZ() + 0.5);
            if (d < bd) { bd = d; best = s; }
        }
        if (best != null) best.found = true;
        if (best == closest) closest = null;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (spots.isEmpty()) return;
        if (cfg().lobbyWaypointsOnlyClosest) {
            Spot s = closest;
            if (s != null && !s.found) WaypointSet.draw(pose, cam, s.pos, color, s.label, true, true, true);
            return;
        }
        for (int i = 0, n = spots.size(); i < n; i++) {
            Spot s = spots.get(i);
            if (!s.found) WaypointSet.draw(pose, cam, s.pos, color, s.label, s == closest, true, true);
        }
    }
}
