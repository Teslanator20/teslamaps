package com.teslamaps.features.diana;

// Ported from SkyHanni's BurrowWarpHelper.kt, warp points from Skysoft's DianaWarpPoint.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DianaWarp {

    private record Warp(String name, String command, double x, double y, double z, double extra) {}

    private static final Warp[] WARPS = {
            new Warp("Hub", "hub", 0.5, 77, -0.5, 0),
            new Warp("Castle", "castle", -250, 130, 45, 10),
            new Warp("Crypt", "crypt", -160.5, 62, -106.5, 15),
            new Warp("Dark Auction", "da", 91.5, 75, 173.5, 2),
            new Warp("Museum", "museum", 29, 72, 1, 2),
            new Warp("Wizard Tower", "wizard", 44.5, 119, 93.5, 0),
            new Warp("Stonks", "stonks", -36.5, 70, -81.5, 5),
            new Warp("Taylor", "taylor", 29.5, 73, -41.5, 2),
    };
    private static final List<String> NAMES = Arrays.stream(WARPS).map(Warp::name).toList();
    private static final String SAMPLE = "§bWarp to Dark Auction §7(§eK§7)";

    private static final Set<String> locked = new HashSet<>();
    private static Warp current, lastWarp;
    private static String text = "";
    private static long lastWarpAt;
    private static boolean keyDown;
    private static HudEditElement hud;
    private static Object lastLevel;

    private DianaWarp() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("dianaWarp", "Diana Nearest Warp").section("Events")
                .enabled(() -> cfg().dianaNearestWarp)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("DianaWarp").section("Events").when(() -> cfg().dianaNearestWarp)
                .tick(DianaWarp::tick)
                .hud("dianawarp", DianaWarp::render)
                .serverChat(DianaWarp::onChat)
                .config("Events", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Nearest Warp", () -> c.dianaNearestWarp, v -> c.dianaNearestWarp = v)
                            .desc("Shows \"Warp to X\" when a Hub warp saves enough walking to the guess or a shared rare mob; the key runs /warp X (/tmap gui)")
                            .tags("diana", "warp", "burrow", "nearest");
                    b.keybind("  Warp Key", () -> c.dianaWarpKey, v -> c.dianaWarpKey = v);
                    b.slider("  Minimum Blocks Saved", 5f, 100f, () -> c.dianaWarpMinSave, v -> c.dianaWarpMinSave = Math.round(v), 0);
                    b.searchList("  Ignored Warps", () -> NAMES, n -> c.dianaWarpIgnored.contains(n),
                            n -> { if (!c.dianaWarpIgnored.remove(n)) c.dianaWarpIgnored.add(n); });
                    b.button("  Reset Locked Warps", () -> {
                        locked.clear();
                        DianaBurrows.msg("§aLocked warps reset.");
                    });
                });
    }

    private static void onChat(String raw) {
        if (!StringUtil.stripTrim(raw).equals("You haven't unlocked this fast travel destination!")) return;
        if (lastWarp == null || System.currentTimeMillis() - lastWarpAt > 5000) return;
        locked.add(lastWarp.name);
        DianaBurrows.msg("§eYou can't use §b" + lastWarp.name + "§e yet, it is skipped now. Reset it in the config once unlocked.");
        lastWarp = null;
        current = null;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            current = null;
            lastWarp = null;
        }
        current = null;
        if (DianaBurrows.onHub()) {
            Vec3 target = DianaRareMobs.remoteTarget();
            if (target == null) {
                BlockPos g = DianaBurrows.target();
                if (g != null) target = Vec3.atCenterOf(g);
            }
            if (target != null) current = best(target, mc.player.position());
        }
        text = current == null ? "" : "§bWarp to " + current.name
                + (cfg().dianaWarpKey != -1 ? " §7(§e" + KeyNames.display(cfg().dianaWarpKey) + "§7)" : "");
        boolean down = mc.screen == null && cfg().dianaWarpKey != -1 && KeyNames.isDown(cfg().dianaWarpKey);
        if (down && !keyDown && current != null && System.currentTimeMillis() - lastWarpAt > 1000 && mc.getConnection() != null) {
            mc.getConnection().sendCommand("warp " + current.command);
            lastWarp = current;
            lastWarpAt = System.currentTimeMillis();
        }
        keyDown = down;
    }

    private static Warp best(Vec3 target, Vec3 player) {
        double playerDist = player.distanceTo(target);
        Warp best = null;
        double bestSave = cfg().dianaWarpMinSave;
        for (Warp w : WARPS) {
            if (locked.contains(w.name) || cfg().dianaWarpIgnored.contains(w.name)) continue;
            double route = Math.sqrt(target.distanceToSqr(w.x, w.y, w.z)) + w.extra;
            double save = playerDist - route;
            if (save >= bestSave) {
                bestSave = save;
                best = w;
            }
        }
        return best;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (hud == null || text.isEmpty() || mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), text);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String s) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(mc.font, s, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
