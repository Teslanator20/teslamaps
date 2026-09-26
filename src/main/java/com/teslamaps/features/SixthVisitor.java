package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.SoundOptions;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hypixel stops showing a timer once five visitors wait, so the sixth one arrives unannounced.
 * The interval is learned from the tab list while the queue still counts down, then run locally.
 * Ported from SkyHanni's GardenVisitorTimer.
 */
public class SixthVisitor {

    private static final Pattern COUNT = Pattern.compile("Visitors: \\((\\d+)\\)");
    private static final Pattern NEXT = Pattern.compile("Next Visitor: (.+)");
    private static final Pattern TIME = Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?");

    private static final long MIN_INTERVAL = 60_000L;
    private static final long MAX_INTERVAL = 60 * 60_000L;
    private static final long WARN_COOLDOWN = 2 * 60_000L;
    private static final long ENTER_GRACE = 5_000L;

    private static int ticks = 0;
    private static int lastVisitors = -1;
    private static long enteredGarden = 0L;
    private static long lastWarn = 0L;

    public static void tick() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.sixthVisitorWarning && !c.visitorHud) return;

        if (!SkyblockUtils.isInGarden()) {
            lastVisitors = -1;
            enteredGarden = 0L;
            hudLines = List.of();
            return;
        }
        if (enteredGarden == 0L) enteredGarden = System.currentTimeMillis();

        if (++ticks < 5) return;
        ticks = 0;

        int visitors = -1;
        String info = null;
        List<String> names = new ArrayList<>();
        int namesLeft = 0;
        for (String raw : c.visitorHud ? TabListUtils.getOrderedLines() : TabListUtils.getTabListLines()) {
            String clean = StringUtil.strip(raw);
            Matcher count = COUNT.matcher(clean);
            if (count.find()) {
                visitors = Integer.parseInt(count.group(1));
                namesLeft = c.visitorHud ? visitors : 0;
                continue;
            }
            // SkyHanni's visitorsInTabList: the next <count> lines are the names
            if (namesLeft > 0) {
                namesLeft--;
                String name = raw.trim();
                if (!clean.isBlank()) names.add(name.startsWith("§") ? name : "§f" + name);
                continue;
            }
            Matcher next = NEXT.matcher(clean);
            if (next.find()) info = next.group(1).trim();
        }
        if (c.visitorHud) buildHud(visitors, info, names);
        if (!c.sixthVisitorWarning) return;
        if (visitors < 0 || info == null || info.equals("Not Unlocked!")) return;

        if (info.equals("Queue Full!")) {
            queueFull(c, visitors);
        } else {
            long millis = parseTime(info);
            // the displayed countdown restarts at the full interval whenever a visitor arrives
            if (lastVisitors >= 0 && visitors - lastVisitors == 1
                    && millis >= MIN_INTERVAL && millis <= MAX_INTERVAL) {
                c.visitorInterval = millis;
                TeslaMapsConfig.save();
            }
            if (c.sixthVisitorArrival != 0L) {
                c.sixthVisitorArrival = 0L;
                TeslaMapsConfig.save();
            }
        }
        lastVisitors = visitors;
    }

    private static void queueFull(TeslaMapsConfig c, int visitors) {
        long now = System.currentTimeMillis();

        // the queue just filled up, so the sixth one is a full interval out
        if (lastVisitors >= 0 && visitors - lastVisitors == 1) {
            c.sixthVisitorArrival = now + c.visitorInterval;
            TeslaMapsConfig.save();
            return;
        }
        if (c.sixthVisitorArrival == 0L) {
            c.sixthVisitorArrival = now + c.visitorInterval;
            TeslaMapsConfig.save();
            return;
        }
        if (now < c.sixthVisitorArrival) return;

        // do not fire on a stale estimate the moment the garden loads
        if (now - enteredGarden < ENTER_GRACE) return;
        if (now - lastWarn < WARN_COOLDOWN) return;
        lastWarn = now;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.gui.setTimes(0, 40, 5);
        mc.gui.setTitle(Component.literal("§a§l6TH VISITOR READY"));
        if (c.sixthVisitorVolume > 0f) {
            LoudSound.play(SoundOptions.resolve(c.sixthVisitorSound), c.sixthVisitorVolume, 1.0f);
        }
    }

    private static final int LINE = 10;
    private static List<String> hudLines = List.of();
    private static int hudWidth = 0;
    private static long nextAt = 0L;
    private static int queued = -1;
    private static List<String> queuedNames = List.of();
    private static HudEditElement hud;

    public static void register() {
        hud = HudEditElement.of("visitorHud", "Visitors").ctx(HudEditElement.HudCtx.GARDEN).section("Garden")
                .enabled(() -> TeslaMapsConfig.get().visitorHud)
                .size(() -> Math.max(90, hudWidth), () -> Math.max(1, hudLines.size()) * LINE)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s,
                        List.of("§e3 §fvisitors §7(Next in §b3m 12s§7)", " §aJacob", " §9Lazy Miner", " §6Madame Eleanor"), 160));
        Feature.of("VisitorHud").section("Garden").when(() -> TeslaMapsConfig.get().visitorHud)
                .hud("visitorhud", SixthVisitor::render);
    }

    // SkyHanni's GardenVisitorTimer display: count, countdown, then the waiting names (Skyblocker's widget)
    private static void buildHud(int visitors, String info, List<String> names) {
        long now = System.currentTimeMillis();
        if (visitors < 0 || info == null || info.equals("Not Unlocked!")) {
            queued = -1;
            hudLines = List.of();
            return;
        }
        queued = visitors;
        queuedNames = TeslaMapsConfig.get().visitorHudNames ? names : List.of();
        if (!info.equals("Queue Full!")) {
            long millis = parseTime(info);
            // keep counting locally between tab updates, drop back to the tab when it drifts
            if (millis >= 0 && (nextAt == 0L || Math.abs(nextAt - now - millis) > 1500L || nextAt < now)) nextAt = now + millis;
        } else {
            nextAt = 0L;
        }
        refreshHud(now);
    }

    private static void refreshHud(long now) {
        if (queued < 0) return;
        String when;
        if (nextAt != 0L) {
            when = "§7(Next in §b" + fmt(nextAt - now) + "§7)";
        } else {
            long left = remaining();
            when = left > 0 ? "§7(Queue full, 6th in §b~" + fmt(left) + "§7)" : "§7(§cQueue full§7)";
        }
        List<String> lines = new ArrayList<>(queuedNames.size() + 1);
        lines.add("§e" + queued + " §f" + (queued == 1 ? "visitor " : "visitors ") + when);
        for (String n : queuedNames) lines.add(" " + n);
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (String l : lines) w = Math.max(w, mc.font.width(l));
        hudWidth = w;
        hudLines = lines;
    }

    private static String fmt(long millis) {
        long s = Math.max(0, millis / 1000L);
        if (s >= 3600) return (s / 3600) + "h " + (s % 3600) / 60 + "m";
        return s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s";
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (hud == null || hudLines.isEmpty() || mc.options.hideGui || !SkyblockUtils.isInGarden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), hudLines, hudWidth);
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

    /** Farming speeds the visitor timer up; Hypixel counts crops, so mirror it locally. */
    public static void onCropBreak() {
        shift(100L);
    }

    public static void onChatMessage(String text) {
        if (!TeslaMapsConfig.get().sixthVisitorWarning) return;
        String clean = StringUtil.strip(text);
        if (clean.contains("for killing a") && clean.startsWith("You received ")) shift(30_000L);
    }

    private static void shift(long millis) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.sixthVisitorArrival == 0L) return;
        c.sixthVisitorArrival -= millis;
    }

    /** Remaining estimate in millis, or -1 when the queue is not full. */
    public static long remaining() {
        long arrival = TeslaMapsConfig.get().sixthVisitorArrival;
        if (arrival == 0L) return -1;
        return arrival - System.currentTimeMillis();
    }

    private static long parseTime(String text) {
        Matcher m = TIME.matcher(text.replace(" ", ""));
        if (!m.matches()) return -1;
        long millis = 0;
        if (m.group(1) != null) millis += Long.parseLong(m.group(1)) * 3600_000L;
        if (m.group(2) != null) millis += Long.parseLong(m.group(2)) * 60_000L;
        if (m.group(3) != null) millis += Long.parseLong(m.group(3)) * 1000L;
        return millis;
    }
}
