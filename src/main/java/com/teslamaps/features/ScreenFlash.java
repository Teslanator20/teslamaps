package com.teslamaps.features;

// Ported from Bax's AutomationFlashOverlay / ScreenFlashAction, with a fade-out
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ScreenFlash {

    private static long start, end;
    private static int color;

    private ScreenFlash() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ScreenFlash").when(() -> cfg().screenFlash)
                .hud("screenflash", ScreenFlash::render)
                .config("Interface", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Screen Flash");
                    b.toggle("Screen Flash on Alerts", () -> c.screenFlash, v -> c.screenFlash = v)
                            .desc("Flashes the whole screen in a colour on top of the title and sound of the alerts below");
                    b.color("  Flash Color", () -> c.screenFlashColor, v -> c.screenFlashColor = v);
                    b.slider("  Duration (ms)", 100, 3000, () -> (float) c.screenFlashMs, v -> c.screenFlashMs = Math.round(v / 50f) * 50);
                    b.toggle("  Rare Sea Creature", () -> c.screenFlashRareCatch, v -> c.screenFlashRareCatch = v);
                    b.toggle("  Pest Warnings", () -> c.screenFlashPests, v -> c.screenFlashPests = v);
                    b.toggle("  Kick Timer Done", () -> c.screenFlashKicked, v -> c.screenFlashKicked = v);
                    b.toggle("  Effect Running Out", () -> c.screenFlashEffects, v -> c.screenFlashEffects = v);
                    b.toggle("  No Bits Available", () -> c.screenFlashBits, v -> c.screenFlashBits = v);
                    b.button("  Test Flash", () -> flash(TeslaMapsConfig.parseColor(c.screenFlashColor), c.screenFlashMs));
                });
    }

    /** Flash for an alert whose sub-toggle is on, in the configured colour and length. */
    public static void alert(boolean enabled) {
        TeslaMapsConfig c = cfg();
        if (!c.screenFlash || !enabled) return;
        flash(TeslaMapsConfig.parseColor(c.screenFlashColor), c.screenFlashMs);
    }

    public static void flash(int argb, int ms) {
        long now = System.currentTimeMillis();
        color = argb;
        start = now;
        end = Math.max(end, now + Math.max(50, ms));
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        long now = System.currentTimeMillis();
        if (now >= end) return;
        float t = (end - now) / (float) Math.max(1, end - start);
        int a = (int) (((color >>> 24) & 0xFF) * Math.min(1f, t));
        if (a <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        g.fill(0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), (a << 24) | (color & 0xFFFFFF));
    }
}
