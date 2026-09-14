package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.SoundOptions;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

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
        if (!c.sixthVisitorWarning) return;

        if (!SkyblockUtils.isInGarden()) {
            lastVisitors = -1;
            enteredGarden = 0L;
            return;
        }
        if (enteredGarden == 0L) enteredGarden = System.currentTimeMillis();

        if (++ticks < 5) return;
        ticks = 0;

        int visitors = -1;
        String info = null;
        for (String raw : TabListUtils.getTabListLines()) {
            String clean = StringUtil.strip(raw);
            Matcher count = COUNT.matcher(clean);
            if (count.find()) {
                visitors = Integer.parseInt(count.group(1));
                continue;
            }
            Matcher next = NEXT.matcher(clean);
            if (next.find()) info = next.group(1).trim();
        }
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
