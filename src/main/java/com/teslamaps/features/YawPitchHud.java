package com.teslamaps.features;

// After SkyHanni's GardenYawAndPitch.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Normalized yaw and pitch, for lining up farm designs. */
public final class YawPitchHud {

    private static final int LINE = 10;

    private static List<String> lines = List.of();
    private static int width;
    private static float lastYaw = Float.NaN, lastPitch = Float.NaN;
    private static long lastChange;
    private static boolean show;
    private static Object lastTool;

    private YawPitchHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        HudEditElement.of("yawPitchHud", "Yaw / Pitch").section("Garden").ctx(HudEditElement.HudCtx.GARDEN)
                .enabled(() -> cfg().yawPitchHud)
                .size(() -> Math.max(70, width), () -> LINE * (cfg().yawPitchShowPitch ? 2 : 1))
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s,
                        cfg().yawPitchShowPitch ? List.of("§aYaw: §f90.0", "§aPitch: §f2.5") : List.of("§aYaw: §f90.0"), 70));
        Feature.of("YawPitchHud").section("Garden").when(() -> cfg().yawPitchHud)
                .tick(YawPitchHud::tick)
                .hudHiddenInInventory("yawpitchhud", YawPitchHud::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Yaw / Pitch");
                    b.toggle("Yaw / Pitch Display", () -> c.yawPitchHud, v -> c.yawPitchHud = v)
                            .desc("Normalized yaw and pitch while holding a farming tool in the Garden (/tmap gui)")
                            .tags("yaw", "pitch", "angle", "rotation", "farm");
                    b.toggle("  Show Pitch", () -> c.yawPitchShowPitch, v -> c.yawPitchShowPitch = v);
                    b.toggle("  Show Everywhere", () -> c.yawPitchEverywhere, v -> c.yawPitchEverywhere = v)
                            .desc("Also without a farming tool and outside the Garden");
                    b.slider("  Yaw Decimals", 0f, 6f, () -> (float) c.yawPitchYawDecimals,
                            v -> c.yawPitchYawDecimals = Math.round(v), 0);
                    b.slider("  Pitch Decimals", 0f, 6f, () -> (float) c.yawPitchPitchDecimals,
                            v -> c.yawPitchPitchDecimals = Math.round(v), 0);
                    b.slider("  Hide After Idle (sec, 0 = never)", 0f, 30f, () -> (float) c.yawPitchTimeout,
                            v -> c.yawPitchTimeout = Math.round(v), 0);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        boolean tool = FarmingSensitivity.isFarmingTool(mc.player.getMainHandItem());
        if (!c.yawPitchEverywhere && (!tool || !SkyblockUtils.isInGarden())) {
            show = false;
            return;
        }
        Object held = mc.player.getMainHandItem().getItem();
        long now = System.currentTimeMillis();
        if (held != lastTool) {
            lastTool = held;
            lastChange = now;
        }
        float yaw = normalize(mc.player.getYRot());
        float pitch = mc.player.getXRot();
        if (yaw != lastYaw || pitch != lastPitch || lines.size() != (c.yawPitchShowPitch ? 2 : 1)) {
            if (yaw != lastYaw || pitch != lastPitch) lastChange = now;
            lastYaw = yaw;
            lastPitch = pitch;
            String y = "§aYaw: §f" + round(yaw, c.yawPitchYawDecimals);
            lines = c.yawPitchShowPitch ? List.of(y, "§aPitch: §f" + round(pitch, c.yawPitchPitchDecimals)) : List.of(y);
            int w = 0;
            for (String s : lines) w = Math.max(w, mc.font.width(s));
            width = w;
        }
        show = c.yawPitchTimeout <= 0 || now - lastChange <= c.yawPitchTimeout * 1000L;
    }

    private static float normalize(float yaw) {
        float y = (yaw + 180f) % 360f;
        if (y < 0) y += 360f;
        return y - 180f;
    }

    private static String round(float v, int decimals) {
        return new BigDecimal(Float.toString(v)).setScale(Math.max(0, decimals), RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!show || lines.isEmpty()) return;
        TeslaMapsConfig c = cfg();
        draw(g, Minecraft.getInstance(), c.yawPitchHudX, c.yawPitchHudY, c.yawPitchHudScale, lines, width);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l, int w) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, l.size() * LINE + 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * LINE, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
