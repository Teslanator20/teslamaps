package com.teslamaps.features;

// Ported from NoammAddons' floor7/TickTimers.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.registry.HudEditElement.HudCtx;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

public final class F7TickTimers {

    private static final String SAMPLE = "§aStart: 150";
    private static final String[] FORMATS = {"Seconds", "Ticks"};

    private static HudEditElement hud;
    private static int startTickTime = -1, goldorTickTime = -1, padTickTime = -1, pyTickTime = -1;
    private static boolean stormActive, pyTriggered;
    private static long lastServerTick = -1;
    private static Object lastLevel;

    private F7TickTimers() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("f7TickTimers", "F7 Tick Timers").ctx(HudCtx.DUNGEON)
                .enabled(() -> cfg().f7TickTimers)
                .size(() -> Minecraft.getInstance().font.width("§aStart: 4.15s"), () -> 9)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("F7TickTimers").when(() -> cfg().f7TickTimers)
                .tick(F7TickTimers::tick)
                .serverChat(F7TickTimers::onChat)
                .hud("f7ticktimers", F7TickTimers::render)
                .config("F7 / M7", b -> {
                    b.label("F7 Tick Timers");
                    b.toggle("F7 Tick Timers", () -> cfg().f7TickTimers, v -> cfg().f7TickTimers = v)
                            .desc("NoammAddons' single-line server tick timer. Position in /tmap gui.");
                    b.toggle("  Prefix", () -> cfg().f7TickPrefix, v -> cfg().f7TickPrefix = v);
                    b.toggle("  Suffix", () -> cfg().f7TickSuffix, v -> cfg().f7TickSuffix = v);
                    b.dropdown("  Format", FORMATS, () -> FORMATS[cfg().f7TickFormat == 1 ? 1 : 0],
                            v -> cfg().f7TickFormat = v.equals("Ticks") ? 1 : 0);
                    b.toggle("  Maxor Start", () -> cfg().f7TickMaxor, v -> cfg().f7TickMaxor = v);
                    b.toggle("  Goldor Death Ticks", () -> cfg().f7TickGoldor, v -> cfg().f7TickGoldor = v);
                    b.toggle("  Storm Pad Timer", () -> cfg().f7TickPad, v -> cfg().f7TickPad = v);
                    b.toggle("  Storm PY Timer", () -> cfg().f7TickPy, v -> cfg().f7TickPy = v);
                });
    }

    private static void reset() {
        padTickTime = -1;
        goldorTickTime = -1;
        startTickTime = -1;
        stormActive = false;
        pyTickTime = -1;
        pyTriggered = false;
    }

    private static void onChat(String raw) {
        TeslaMapsConfig c = cfg();
        switch (StringUtil.stripTrim(raw)) {
            case "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!" -> { if (c.f7TickMaxor) startTickTime = 83; }
            case "[BOSS] Storm: ENERGY HEED MY CALL!", "[BOSS] Storm: THUNDER LET ME BE YOUR CATALYST!" -> {
                if (c.f7TickPy && !pyTriggered) {
                    pyTriggered = true;
                    pyTickTime = 62;
                }
            }
            case "[BOSS] Storm: I should have known that I stood no chance." -> {
                if (pyTriggered) {
                    pyTriggered = false;
                    pyTickTime = -1;
                }
                if (stormActive) {
                    stormActive = false;
                    padTickTime = -1;
                }
            }
            case "[BOSS] Goldor: Who dares trespass into my domain?" -> { if (c.f7TickGoldor) goldorTickTime = 60; }
            case "The Core entrance is opening!" -> goldorTickTime = -1;
            case "[BOSS] Storm: Pathetic Maxor, just like expected." -> {
                if (c.f7TickPad) {
                    padTickTime = 20;
                    stormActive = true;
                }
            }
            default -> {}
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        long now = ServerTickTimer.now();
        int dt = lastServerTick < 0 ? 1 : (int) Math.max(0, Math.min(40, now - lastServerTick));
        lastServerTick = now;
        for (int i = 0; i < dt; i++) serverTick();
    }

    private static void serverTick() {
        if (startTickTime != -1) startTickTime--;

        if (stormActive && padTickTime != -1) {
            padTickTime--;
            if (padTickTime <= 0) padTickTime = 20;
        }

        if (cfg().f7TickPy && pyTickTime >= 0) pyTickTime--;

        if (goldorTickTime >= 0) {
            goldorTickTime--;
            if (goldorTickTime == 0) goldorTickTime = 60;
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !DungeonManager.isInDungeon()) return;
        String text;
        if (startTickTime != -1) text = formatTimer(startTickTime, 83, "§aStart:");
        else if (goldorTickTime != -1) text = formatTimer(goldorTickTime, 60, "§7Goldor:");
        else if (pyTickTime != -1) text = formatTimer(pyTickTime, 75, "§5PY:");
        else if (padTickTime != -1) text = formatTimer(padTickTime, 20, "§bPad:");
        else return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), text);
    }

    private static String formatTimer(int time, int max, String prefixText) {
        TeslaMapsConfig c = cfg();
        String color = time >= max * 0.66 ? "§a" : time >= max * 0.33 ? "§6" : "§c";
        boolean ticks = c.f7TickFormat == 1;
        String timeDisplay = ticks ? String.valueOf(time) : String.format(Locale.ROOT, "%.2f", time / 20f);
        String prefix = c.f7TickPrefix ? prefixText + " " : "";
        String suffix = c.f7TickSuffix ? (ticks ? "t" : "s") : "";
        return prefix + color + timeDisplay + suffix;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String s) {
        int w = mc.font.width("§aStart: 4.15s");
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.text(mc.font, s, (w - mc.font.width(s)) / 2, 0, 0xFFFFFFFF);
        pose.popMatrix();
    }
}
