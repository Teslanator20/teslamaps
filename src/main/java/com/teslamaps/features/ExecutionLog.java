package com.teslamaps.features;

// Ported from Bax's AutomationExecutionLog.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ExecutionLog {

    private static final long SHOW_MS = 3000, FADE_MS = 1000;
    private static final String SAMPLE = "§7Fired: §fHotkey: /warp dh";

    private static HudEditElement hud;
    private static String last = "";
    private static long lastAt;

    private ExecutionLog() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("executionLog", "Last Fired Trigger")
                .enabled(() -> cfg().executionLog)
                .size(() -> Math.max(Minecraft.getInstance().font.width(SAMPLE), Minecraft.getInstance().font.width(last)), () -> 9)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, 0xFF));
        Feature.of("ExecutionLog").when(() -> cfg().executionLog)
                .hud("executionlog", ExecutionLog::render)
                .config("Dev & Debug", b -> {
                    b.label("Debug");
                    b.toggle("Last Fired Trigger", () -> cfg().executionLog, v -> cfg().executionLog = v)
                            .desc("Shows for 3s which hotkey, reminder, custom todo or container trigger just fired (/tmap gui)");
                });
    }

    /** Any user trigger that just fired. Main thread. */
    public static void log(String name) {
        if (!cfg().executionLog || name == null || name.isBlank()) return;
        last = "§7Fired: §f" + name;
        lastAt = System.currentTimeMillis();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        long age = System.currentTimeMillis() - lastAt;
        if (hud == null || age > SHOW_MS || last.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        int alpha = age < SHOW_MS - FADE_MS ? 0xFF : (int) (0xFF * (SHOW_MS - age) / (float) FADE_MS);
        if (alpha < 8) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), last, alpha);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text, int alpha) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(mc.font, text, 0, 0, (alpha << 24) | 0xFFFFFF, true);
        g.pose().popMatrix();
    }
}
