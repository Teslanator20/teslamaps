package com.teslamaps.features;

// Concept from SkyblockAddons' ActionBarParser (tickers) + RenderListener.drawScorpionFoilTicker
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ScorpionFoilTicker {

    private static final char FULL = 'Ⓞ';
    private static final char EMPTY = 'ⓞ';
    private static final String SAMPLE = "§6ⓄⓄⓄ§8ⓞⓞ";

    private static volatile int full, max;
    private static volatile long seenAt;
    private static String text;
    private static HudEditElement hud;

    private ScorpionFoilTicker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("scorpionFoilTicker", "Ticker / Charges").section("Combat")
                .enabled(() -> cfg().scorpionFoilTicker)
                .size(() -> width(SAMPLE), () -> 16)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("ScorpionFoilTicker").section("Combat").when(() -> cfg().scorpionFoilTicker)
                .tick(ScorpionFoilTicker::tick)
                .hudHiddenInInventory("scorpionfoilticker", ScorpionFoilTicker::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Tickers");
                    b.toggle("Ticker / Charges Display", () -> c.scorpionFoilTicker, v -> c.scorpionFoilTicker = v)
                            .desc("Ticker charges (Scorpion Foil) from the action bar as a row of filled and empty dots. Position in /tmap gui")
                            .tags("scorpion", "foil", "ticker", "charges");
                });
    }

    /** Gui.setOverlayMessage, main thread. */
    public static void onActionBar(Component message) {
        if (!cfg().scorpionFoilTicker || message == null) return;
        String s = message.getString();
        if (s.indexOf(FULL) < 0 && s.indexOf(EMPTY) < 0) return;
        int f = 0, e = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == FULL) f++;
            else if (ch == EMPTY) e++;
        }
        full = f;
        max = f + e;
        seenAt = System.currentTimeMillis();
    }

    private static void tick() {
        if (max == 0 || System.currentTimeMillis() - seenAt > 3000) {
            text = null;
            return;
        }
        int f = full, m = max;
        StringBuilder b = new StringBuilder("§6");
        for (int i = 0; i < f; i++) b.append(FULL);
        b.append("§8");
        for (int i = f; i < m; i++) b.append(EMPTY);
        text = b.toString();
    }

    private static int width(String s) {
        return Minecraft.getInstance().font.width(s) + 8;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String t = text;
        if (t == null || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), t);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String s) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, width(s), 16);
        g.text(mc.font, s, 4, 4, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
