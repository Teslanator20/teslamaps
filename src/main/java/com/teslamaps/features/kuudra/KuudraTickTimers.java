package com.teslamaps.features.kuudra;

// Ported from Shizo's kuudra/general/KuudraTickTimers.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

public final class KuudraTickTimers {

    private static final String SUPPLY_PREFIX = "§b§lSupplies spawn in: ";
    private static final String BUILD_PREFIX = "§b§lBuild starts in: ";
    private static final ServerTickTimer SUPPLIES = new ServerTickTimer();
    private static final ServerTickTimer BUILD = new ServerTickTimer();
    private static String supplyLine, buildLine;
    private static HudEditElement supplyHud, buildHud;

    private KuudraTickTimers() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        supplyHud = HudEditElement.of("kuudraSupplyTimer", "Kuudra Supplies Timer").section("Kuudra")
                .enabled(() -> cfg().kuudraTickTimers)
                .size(() -> Minecraft.getInstance().font.width(SUPPLY_PREFIX + "§a9.00s"), () -> 9)
                .scaleRange(0.5f, 5.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SUPPLY_PREFIX + format(180, 180)));
        buildHud = HudEditElement.of("kuudraBuildTimer", "Kuudra Build Timer").section("Kuudra")
                .enabled(() -> cfg().kuudraTickTimers)
                .size(() -> Minecraft.getInstance().font.width(BUILD_PREFIX + "§a4.00s"), () -> 9)
                .scaleRange(0.5f, 5.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, BUILD_PREFIX + format(80, 80)));
        Kuudra.LISTENERS.add((prev, now) -> {
            if (now == Kuudra.Phase.NONE) {
                SUPPLIES.stop();
                BUILD.stop();
            }
        });
        Feature.of("KuudraTickTimers").section("Kuudra").when(() -> cfg().kuudraTickTimers)
                .serverChat(KuudraTickTimers::onChat)
                .tick(KuudraTickTimers::tick)
                .hud("kuudratimers", KuudraTickTimers::render)
                .config("Kuudra", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Kuudra Tick Timers", () -> c.kuudraTickTimers, v -> c.kuudraTickTimers = v)
                            .desc("Server-tick countdowns: supplies spawn (9s after Elle fishes) and build start (4s after 6/6). Move them in /tmap gui");
                    b.toggle("  Show Ticks", () -> c.kuudraTickTimersTicks, v -> c.kuudraTickTimersTicks = v);
                });
    }

    private static void onChat(String raw) {
        if (!Kuudra.inKuudra()) return;
        String s = StringUtil.stripTrim(raw);
        if (s.equals(Kuudra.FISH_UP)) SUPPLIES.start(180);
        else if (s.equals(Kuudra.SUPPLIES_DONE)) BUILD.start(80);
    }

    private static void tick() {
        supplyLine = SUPPLIES.active() ? SUPPLY_PREFIX + format(SUPPLIES.remaining(), 180) : null;
        buildLine = BUILD.active() ? BUILD_PREFIX + format(BUILD.remaining(), 80) : null;
    }

    private static String format(long t, int max) {
        String color = t >= max * 0.66 ? "§a" : t >= max * 0.33 ? "§6" : "§c";
        return color + (cfg().kuudraTickTimersTicks ? t + "t" : String.format(Locale.ROOT, "%.2fs", t / 20.0));
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        String s = supplyLine, b = buildLine;
        if (s != null && supplyHud != null) draw(g, mc, supplyHud.x(), supplyHud.y(), supplyHud.scale(), s);
        if (b != null && buildHud != null) draw(g, mc, buildHud.x(), buildHud.y(), buildHud.scale(), b);
    }

    static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(mc.font, text, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
