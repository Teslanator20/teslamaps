package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.SoundOptions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

/** Real-time HUD and alerts for Agatha's and Jacob's recurring contests. */
public final class ContestTimers {

    static final long MINUTE_MS = 60_000L;
    static final long START_OFFSET_MS = 15 * MINUTE_MS;
    static final long FORAGING_PERIOD_MS = 20 * MINUTE_MS;
    static final long FARMING_PERIOD_MS = 60 * MINUTE_MS;
    static final long CONTEST_DURATION_MS = 20 * MINUTE_MS;

    private static long lastAlertCheck;

    private ContestTimers() {}

    public static void tick() {
        tickScore();
        tickAlerts();
    }

    private static void tickAlerts() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.contestAlerts || !SkyblockUtils.isInSkyblock()
                || (!c.contestShowForaging && !c.contestShowFarming)) {
            lastAlertCheck = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        long previous = lastAlertCheck;
        lastAlertCheck = now;

        // Do not replay missed alerts after joining, sleeping the PC, or changing the system clock.
        if (previous == 0L || now <= previous || now - previous > 30_000L) return;

        boolean foragingStart = c.contestShowForaging
                && crossed(previous, now, START_OFFSET_MS, FORAGING_PERIOD_MS);
        boolean farmingStart = c.contestShowFarming
                && crossed(previous, now, START_OFFSET_MS, FARMING_PERIOD_MS);
        if (foragingStart || farmingStart) alert(c, foragingStart, farmingStart, 0);

        int warningSeconds = Math.max(0, c.contestWarnBefore);
        if (warningSeconds == 0) return;
        long warningOffset = START_OFFSET_MS - warningSeconds * 1000L;
        boolean foragingWarning = c.contestShowForaging
                && crossed(previous, now, warningOffset, FORAGING_PERIOD_MS);
        boolean farmingWarning = c.contestShowFarming
                && crossed(previous, now, warningOffset, FARMING_PERIOD_MS);
        if (foragingWarning || farmingWarning) {
            alert(c, foragingWarning, farmingWarning, warningSeconds);
        }
    }

    private static boolean crossed(long previous, long now, long offset, long period) {
        return Math.floorDiv(previous - offset, period) < Math.floorDiv(now - offset, period);
    }

    private static void alert(TeslaMapsConfig c, boolean foraging, boolean farming, int secondsBefore) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String name = contestName(foraging, farming);
        String action = secondsBefore == 0
                ? (foraging && farming ? " §ehave started!" : " §ehas started!")
                : " §estarts in §f" + format(secondsBefore) + "§e.";
        if (c.contestAlertChat) {
            mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] " + name + action));
        }

        if (c.contestAlertTitle) {
            mc.gui.setTimes(0, 40, 5);
            mc.gui.setTitle(Component.literal(name));
            mc.gui.setSubtitle(Component.literal(secondsBefore == 0
                    ? "§aContest started!"
                    : "§eStarts in §f" + format(secondsBefore)));
        }
        if (c.contestAlertVolume > 0f) {
            LoudSound.play(SoundOptions.resolve(c.contestAlertSound), c.contestAlertVolume,
                    secondsBefore == 0 ? 1.2f : 1.0f);
        }
        if (secondsBefore == 0 && c.contestAnnounceParty) party("Contest Started");
    }

    private static void party(String line) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.getConnection() != null) mc.getConnection().sendCommand("pc " + line);
        });
    }

    // Miria's Contest, Galatea. The score sits in the tab widget under the contest header.
    private static final java.util.regex.Pattern MIRIA =
            java.util.regex.Pattern.compile("^Miria's Contest\\b.*");

    private static boolean ticketAnnounced = false;
    private static int ticketTicks = 0;
    private static long currentScore = -1;
    private static long ticketHideUntil = 0;   // real-time end of the contest the ticket was earned in

    private static void tickScore() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if ((!c.contestTicketAnnounce && !c.contestHideAtTicket) || !SkyblockUtils.isInSkyblock()) {
            ticketAnnounced = false;
            currentScore = -1;
            return;
        }
        if (++ticketTicks < 20) return;
        ticketTicks = 0;

        currentScore = miriaScore();
        if (currentScore < 0) { ticketAnnounced = false; return; }   // no contest running
        if (currentScore < c.contestTicketScore) return;
        // latch until the current contest ends, so a lobby swap (widget gone) can't unhide the line
        long now = System.currentTimeMillis();
        ticketHideUntil = now + remaining(now, START_OFFSET_MS, FORAGING_PERIOD_MS);
        if (!c.contestTicketAnnounce || ticketAnnounced) return;
        ticketAnnounced = true;
        party("Ticket Acquired!");
    }

    /** Ticket is in — the foraging line has nothing left to say until the next contest. */
    public static boolean ticketDone() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.contestHideAtTicket && System.currentTimeMillis() < ticketHideUntil;
    }

    /** -1 when no Miria's Contest widget is up. */
    private static long miriaScore() {
        List<String> lines = com.teslamaps.utils.TabListUtils.getOrderedLines();
        long best = -1;
        boolean inWidget = false;
        for (String raw : lines) {
            String line = com.teslamaps.utils.StringUtil.stripTrim(raw);
            if (MIRIA.matcher(line).matches()) {
                inWidget = true;
                best = 0;
                if (TeslaMapsConfig.get().debugMode) {
                    com.teslamaps.TeslaMaps.LOGGER.info("[Contest] miria header: {}", line);
                }
                continue;
            }
            if (!inWidget) continue;
            if (line.isEmpty() || line.endsWith(":")) break;   // next widget
            if (TeslaMapsConfig.get().debugMode) {
                com.teslamaps.TeslaMaps.LOGGER.info("[Contest] miria line: {}", line);
            }
            best = Math.max(best, largestNumber(line));
        }
        return best;
    }

    /** Biggest plain number in the line, commas allowed. Percentages and timers stay small. */
    private static long largestNumber(String line) {
        long best = -1;
        long current = -1;
        for (int i = 0; i <= line.length(); i++) {
            char ch = i < line.length() ? line.charAt(i) : ' ';
            if (ch >= '0' && ch <= '9') {
                current = (current < 0 ? 0 : current) * 10 + (ch - '0');
            } else if (ch != ',') {
                best = Math.max(best, current);
                current = -1;
            }
        }
        return best;
    }

    private static String contestName(boolean foraging, boolean farming) {
        if (foraging && farming) return "§6Foraging + Farming Contests";
        if (foraging) return "§2Agatha's Foraging Contest";
        return "§aJacob's Farming Contest";
    }

    // rebuilt once a second, not once a frame
    private static long cachedSecond = -1L;
    private static List<String> cachedLines = List.of();

    public static List<String> lines() {
        long now = System.currentTimeMillis();
        if (now / 1000L != cachedSecond) {
            cachedSecond = now / 1000L;
            cachedLines = lines(now, TeslaMapsConfig.get());
        }
        return cachedLines;
    }

    static List<String> lines(long now, TeslaMapsConfig c) {
        List<String> out = new ArrayList<>();
        out.add("§6Contest Timers");

        if (c.contestShowForaging && !ticketDone()) {
            long left = remaining(now, START_OFFSET_MS, FORAGING_PERIOD_MS);
            out.add("§2Foraging§8: §aLIVE §f" + formatMillis(left));
        }
        if (c.contestShowFarming) {
            long elapsed = Math.floorMod(now - START_OFFSET_MS, FARMING_PERIOD_MS);
            if (elapsed < CONTEST_DURATION_MS) {
                out.add("§aFarming§8: §aLIVE §f" + formatMillis(CONTEST_DURATION_MS - elapsed));
            } else {
                out.add("§aFarming§8: §7in §f" + formatMillis(FARMING_PERIOD_MS - elapsed));
            }
        }
        return out;
    }

    static long remaining(long now, long offset, long period) {
        long elapsed = Math.floorMod(now - offset, period);
        return period - elapsed;
    }

    private static String formatMillis(long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        return format(seconds);
    }

    private static String format(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    public static boolean shouldRender() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.contestTimer) return false;
        if (c.contestTimerSkyblockOnly && !SkyblockUtils.isInSkyblock()) return false;
        return lines().size() > 1;   // header only = nothing to show
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        // container screens keep it up, so it stays draggable from the inventory
        if (mc.screen != null
                && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)
                && !(mc.screen instanceof AbstractContainerScreen)) return;
        if (!shouldRender()) return;
        draw(context, mc, c.contestTimerX, c.contestTimerY, c.contestTimerScale, lines());
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        draw(context, mc, x, y, scale, lines());
    }

    private static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y,
                             float scale, List<String> lines) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int width = 0;
        for (String line : lines) width = Math.max(width, mc.font.width(line));
        com.teslamaps.render.gui.TeslaGui.backdropRect(context, -4, -4,
                width + 8, lines.size() * (mc.font.lineHeight + 1) + 4);
        for (int row = 0; row < lines.size(); row++) {
            context.text(mc.font, lines.get(row), 0, row * (mc.font.lineHeight + 1), 0xFFFFFFFF);
        }
        pose.popMatrix();
    }

    public static int width(float scale) {
        return (int) (previewSize(Minecraft.getInstance().font)[0] * scale);
    }

    public static int height(float scale) {
        return (int) (previewSize(Minecraft.getInstance().font)[1] * scale);
    }

    public static int[] previewSize(Font font) {
        List<String> lines = lines();
        int width = 0;
        for (String line : lines) width = Math.max(width, font.width(line));
        return new int[] { width, lines.size() * (font.lineHeight + 1) };
    }

}
