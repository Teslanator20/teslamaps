package com.teslamaps.features.rift;

// Ported from SkyHanni's WoodenButtonsHelper.kt, spots from its RiftWoodenButtons.json
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.navigation.IslandNavigation;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/** Buttons Enigma Soul: the 56 wooden buttons as waypoints, hit ones disappear. */
public final class WoodenButtonsHelper {

    // spot x y z, then 4 buttons x y z
    private static final int[][] SPOTS = {
            {-84, 72, -129, -83, 74, -127, -83, 75, -127, -86, 74, -127, -86, 75, -127},
            {-90, 72, -130, -88, 74, -127, -88, 75, -127, -91, 74, -127, -91, 75, -127},
            {-90, 72, -111, -91, 74, -113, -91, 75, -113, -89, 74, -113, -89, 75, -113},
            {-60, 71, -123, -59, 72, -121, -59, 73, -121, -61, 72, -121, -61, 73, -121},
            {-65, 72, -123, -64, 72, -121, -64, 73, -121, -66, 72, -121, -66, 73, -121},
            {-114, 73, -107, -112, 75, -108, -112, 76, -108, -112, 75, -106, -112, 76, -106},
            {-114, 73, -99, -112, 75, -100, -112, 76, -100, -112, 75, -98, -112, 76, -98},
            {-107, 73, -91, -109, 78, -96, -109, 79, -96, -105, 78, -96, -105, 79, -96},
            {-54, 69, -91, -46, 77, -93, -46, 78, -93, -46, 77, -89, -46, 78, -89},
            {-43, 69, -83, -44, 74, -86, -44, 75, -86, -42, 74, -86, -42, 75, -86},
            {-33, 70, -84, -34, 74, -86, -34, 75, -86, -32, 74, -86, -32, 75, -86},
            {-85, 72, -91, -87, 73, -91, -87, 74, -91, -87, 73, -89, -87, 74, -89},
            {-85, 71, -82, -87, 73, -83, -87, 74, -83, -87, 73, -81, -87, 74, -81},
            {-92, 71, -74, -94, 76, -79, -94, 77, -79, -90, 76, -79, -90, 77, -79},
    };
    private static final String HIT = "You have hit ";
    private static final String HIT_TAIL = "/56 of the wooden buttons!";
    private static final String ALL = "You've hit all 56 wooden buttons!";

    private static BlockPos[][] buttons;
    private static final Set<BlockPos> hit = new HashSet<>();
    private static final Set<BlockPos> powered = new HashSet<>();
    private static BlockPos lastPowered;
    private static long lastPoweredAt;
    private static boolean allHit, show;
    private static int spot = -1, navigatedSpot = -1;
    private static Object lastLevel;

    private WoodenButtonsHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Rift", "Skyblock", true);
        Feature.of("WoodenButtonsHelper").section("Rift").when(() -> cfg().riftWoodenButtons)
                .tick(WoodenButtonsHelper::tick)
                .world(WoodenButtonsHelper::render)
                .serverChat(WoodenButtonsHelper::onChat)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Wooden Buttons");
                    b.toggle("Wooden Buttons Helper", () -> c.riftWoodenButtons, v -> c.riftWoodenButtons = v)
                            .desc("Buttons Enigma Soul: shows the nearest \"Hit Buttons Here!\" spot and its wooden buttons. "
                                    + "Hit buttons (click or Berberis Blowgun) disappear.")
                            .tags("enigma soul", "buttons", "dreadfarm", "blowgun", "waypoint");
                    b.color("  Button Color", () -> c.riftWoodenButtonsColor, v -> c.riftWoodenButtonsColor = v);
                    b.toggle("  Path To Next Spot", () -> c.riftWoodenButtonsPath, v -> c.riftWoodenButtonsPath = v)
                            .desc("Starts island navigation to the next spot with buttons left");
                });
    }

    private static BlockPos[][] buttons() {
        if (buttons == null) {
            BlockPos[][] out = new BlockPos[SPOTS.length][4];
            for (int i = 0; i < SPOTS.length; i++) {
                int[] s = SPOTS[i];
                for (int j = 0; j < 4; j++) out[i][j] = new BlockPos(s[3 + j * 3], s[4 + j * 3], s[5 + j * 3]);
            }
            buttons = out;
        }
        return buttons;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            hit.clear();
            powered.clear();
            allHit = false;
            spot = navigatedSpot = -1;
        }
        show = false;
        if (allHit || !SkyblockUtils.isInArea("The Rift")) return;
        BlockPos[][] all = buttons();
        Vec3 p = mc.player.position();
        long now = System.currentTimeMillis();
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < SPOTS.length; i++) {
            int[] s = SPOTS[i];
            double d = p.distanceToSqr(s[0] + 0.5, s[1], s[2] + 0.5);
            boolean left = false;
            for (BlockPos b : all[i]) {
                if (d < 64 * 64) {
                    BlockState st = mc.level.getBlockState(b);
                    boolean on = st.getBlock() instanceof ButtonBlock && st.getValue(ButtonBlock.POWERED);
                    if (on && powered.add(b) && !hit.contains(b)) {
                        lastPowered = b;
                        lastPoweredAt = now;
                    } else if (!on) powered.remove(b);
                }
                if (!hit.contains(b)) left = true;
            }
            if (left && d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        spot = bestDist < 120 * 120 ? best : -1;
        show = spot >= 0;
        if (show && cfg().riftWoodenButtonsPath && spot != navigatedSpot) {
            int[] s = SPOTS[spot];
            if (IslandNavigation.navigate(new Vec3(s[0] + 0.5, s[1], s[2] + 0.5), "§eButton Spot")) navigatedSpot = spot;
        }
    }

    private static void onChat(String raw) {
        if (!SkyblockUtils.isInArea("The Rift")) return;
        String t = StringUtil.stripTrim(raw);
        if (t.equals(ALL)) {
            allHit = true;
            show = false;
            return;
        }
        if (!t.startsWith(HIT) || !t.endsWith(HIT_TAIL)) return;
        long now = System.currentTimeMillis();
        if (lastPowered != null && now - lastPoweredAt < 3000 && !hit.contains(lastPowered)) {
            hit.add(lastPowered);
            lastPowered = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        BlockPos near = null;
        double nearDist = 6 * 6;
        for (BlockPos[] row : buttons()) {
            for (BlockPos b : row) {
                if (hit.contains(b)) continue;
                double d = mc.player.distanceToSqr(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
                if (d < nearDist) {
                    nearDist = d;
                    near = b;
                }
            }
        }
        if (near != null) hit.add(near);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!show || spot < 0) return;
        int[] s = SPOTS[spot];
        Vec3 center = new Vec3(s[0] + 0.5, s[1], s[2] + 0.5);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double dist = mc.player.position().distanceTo(center);
        if (dist > 2.5) ESPRenderer.drawText(pose, "§eHit Buttons Here!", center.add(0, 1, 0), 1.25f, cam);
        if (dist > 15) return;
        int color = TeslaMapsConfig.parseColor(cfg().riftWoodenButtonsColor);
        for (BlockPos b : buttons()[spot]) {
            if (hit.contains(b)) continue;
            AABB box = new AABB(b).deflate(0.3);
            ESPRenderer.drawFilledBox(pose, box, (color & 0xFFFFFF) | 0x80000000, cam, true);
            ESPRenderer.drawBoxOutline(pose, box, color | 0xFF000000, 2f, cam, true);
        }
    }
}
