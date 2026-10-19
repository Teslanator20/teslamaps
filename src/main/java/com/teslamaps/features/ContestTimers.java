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
    static final long DA_OFFSET_MS = 55 * MINUTE_MS;

    private static final long ALERT_5MIN_MS = 5 * MINUTE_MS;
    private static final long ALERT_90SEC_MS = 90_000L;
    private static final long DISCORD_RING_MS = 2 * MINUTE_MS;

    private static long lastForaging = Long.MIN_VALUE;
    private static long lastFarming = Long.MIN_VALUE;
    private static long alerted5 = Long.MIN_VALUE;
    private static long alerted90 = Long.MIN_VALUE;
    private static long discord5 = Long.MIN_VALUE;
    private static long discordRing = Long.MIN_VALUE;

    private ContestTimers() {}

    public static void tick() {
        tickScore();
        tickAlerts();
    }

    private static void tickAlerts() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!SkyblockUtils.isInSkyblock()) {
            lastForaging = Long.MIN_VALUE;
            lastFarming = Long.MIN_VALUE;
            return;
        }

        long now = System.currentTimeMillis();
        long contest = Math.floorDiv(now - START_OFFSET_MS, FORAGING_PERIOD_MS);
        long farmingContest = Math.floorDiv(now - START_OFFSET_MS, FARMING_PERIOD_MS);
        boolean foragingStart = lastForaging != Long.MIN_VALUE && lastForaging != contest;
        boolean farmingStart = lastFarming != Long.MIN_VALUE && lastFarming != farmingContest;
        lastForaging = contest;
        lastFarming = farmingContest;

        if (foragingStart && c.contestAnnounceParty) party("Contest Started");
        if (c.contestAlertStart) {
            boolean foraging = foragingStart && c.contestShowForaging;
            boolean farming = farmingStart && c.contestShowFarming;
            if (foraging || farming) startAlert(c, foraging, farming);
        }

        if (c.contestDiscordAlert && !hasTicket()) tickDiscord(now, contest);
        if (!c.contestAlert5Min && !c.contestAlert90Sec) return;
        if (hasTicket()) return;

        long left = remaining(now, START_OFFSET_MS, FORAGING_PERIOD_MS);
        if (c.contestAlert90Sec && left <= ALERT_90SEC_MS) {
            if (alerted90 != contest) {
                alerted90 = contest;
                alert(c, left);
            }
            return;
        }
        if (c.contestAlert5Min && left <= ALERT_5MIN_MS && alerted5 != contest) {
            alerted5 = contest;
            alert(c, left);
        }
    }

    private static void tickDiscord(long now, long contest) {
        long left = remaining(now, START_OFFSET_MS, FORAGING_PERIOD_MS);
        if (left <= DISCORD_RING_MS) {
            if (discordRing == contest) return;
            discordRing = contest;
            DiscordAlert.ring("No ticket yet! Foraging contest ends in " + formatMillis(left));
        } else if (left <= ALERT_5MIN_MS && discord5 != contest) {
            discord5 = contest;
            DiscordAlert.message("No ticket yet! Foraging contest ends in " + formatMillis(left));
        }
    }

    private static void alert(TeslaMapsConfig c, long millisLeft) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String left = formatMillis(millisLeft);
        if (c.contestAlertChat) {
            mc.player.sendSystemMessage(Component.literal(
                    "§a[TeslaMaps] §cNo ticket yet! §eContest ends in §f" + left));
        }
        if (c.contestAlertTitle) {
            mc.gui.setTimes(0, 40, 5);
            mc.gui.setTitle(Component.literal("§cNo Ticket!"));
            mc.gui.setSubtitle(Component.literal("§eEnds in §f" + left));
        }
        if (c.contestAlertVolume > 0f) {
            LoudSound.play(SoundOptions.resolve(c.contestAlertSound), c.contestAlertVolume, 1.2f);
        }
    }

    private static void startAlert(TeslaMapsConfig c, boolean foraging, boolean farming) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String name = contestName(foraging, farming);
        if (c.contestAlertChat) {
            mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] " + name
                    + (foraging && farming ? " §ehave started!" : " §ehas started!")));
        }
        if (c.contestAlertTitle) {
            mc.gui.setTimes(0, 40, 5);
            mc.gui.setTitle(Component.literal(name));
            mc.gui.setSubtitle(Component.literal("§aContest started!"));
        }
        if (c.contestAlertVolume > 0f) {
            LoudSound.play(SoundOptions.resolve(c.contestAlertSound), c.contestAlertVolume, 1.2f);
        }
    }

    private static String contestName(boolean foraging, boolean farming) {
        if (foraging && farming) return "§6Foraging + Farming Contests";
        if (foraging) return "§2Agatha's Foraging Contest";
        return "§aJacob's Farming Contest";
    }

    private static void party(String line) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            com.teslamaps.features.ChatQueue.command("pc " + line);
        });
    }

    // Miria's Contest, Galatea. The score sits in the tab widget under the contest header.
    private static final java.util.regex.Pattern MIRIA =
            java.util.regex.Pattern.compile("^Miria's Contest\\b.*");

    private static boolean ticketAnnounced = false;
    private static int ticketTicks = 0;
    private static long currentScore = -1;
    private static long ticketHideUntil = 0;   // real-time end of the contest the ticket was earned in
    private static long scoreContest = Long.MIN_VALUE;
    private static long contestBaseline = -1;  // widget value at the contest switch (may still be the old score)
    private static boolean armed = false;

    private static void tickScore() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if ((!c.contestTicketAnnounce && !c.contestHideAtTicket
                && !c.contestAlert5Min && !c.contestAlert90Sec && !c.contestDiscordAlert)
                || !SkyblockUtils.isInSkyblock()) {
            ticketAnnounced = false;
            currentScore = -1;
            return;
        }
        if (++ticketTicks < 20) return;
        ticketTicks = 0;

        long now = System.currentTimeMillis();
        long contest = Math.floorDiv(now - START_OFFSET_MS, FORAGING_PERIOD_MS);
        currentScore = miriaScore();

        if (contest != scoreContest) {
            // the widget can still carry the finished contest's score into the next one
            scoreContest = contest;
            contestBaseline = currentScore;
            armed = false;
            ticketAnnounced = false;
            ticketHideUntil = 0;
        }

        if (currentScore < 0) { ticketAnnounced = false; return; }   // no contest running
        if (currentScore < c.contestTicketScore || currentScore > contestBaseline) armed = true;
        if (currentScore < c.contestTicketScore || !armed) return;
        // latch until the current contest ends, so a lobby swap (widget gone) can't unhide the line
        ticketHideUntil = now + remaining(now, START_OFFSET_MS, FORAGING_PERIOD_MS);
        if (DiscordAlert.isRinging()) DiscordAlert.stop();
        if (!c.contestTicketAnnounce || ticketAnnounced) return;
        ticketAnnounced = true;
        party("Ticket Acquired!");
    }

    /** Ticket score reached in the contest that is still running. */
    private static boolean hasTicket() {
        return System.currentTimeMillis() < ticketHideUntil;
    }

    /** Ticket is in — the foraging line has nothing left to say until the next contest. */
    public static boolean ticketDone() {
        return TeslaMapsConfig.get().contestHideAtTicket && hasTicket();
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

    /** Biggest plain number in the line, commas allowed. Positions (#123) and percentages are not scores. */
    private static long largestNumber(String line) {
        long best = -1;
        long current = -1;
        int start = -1;
        for (int i = 0; i <= line.length(); i++) {
            char ch = i < line.length() ? line.charAt(i) : ' ';
            if (ch >= '0' && ch <= '9') {
                if (current < 0) { current = 0; start = i; }
                current = current * 10 + (ch - '0');
            } else if (ch != ',') {
                if (current >= 0 && !isPosition(line, start) && ch != '%') best = Math.max(best, current);
                current = -1;
            }
        }
        return best;
    }

    private static boolean isPosition(String line, int start) {
        for (int i = start - 1; i >= 0; i--) {
            char ch = line.charAt(i);
            if (ch == '#') return true;
            if (ch != ' ') return false;
        }
        return false;
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
            JacobContests.appendLines(out, now);
        }
        if (c.contestShowDarkAuction) {
            long elapsed = Math.floorMod(now - DA_OFFSET_MS, FARMING_PERIOD_MS);
            out.add("§5Dark Auction§8: §7in §f" + formatMillis(FARMING_PERIOD_MS - elapsed));
        }
        if (c.contestTabEvent) appendTabEvent(out);
        return out;
    }

    // tab widget "Event: <name>" with " Ends In: …" / " Starts In: …" below
    private static void appendTabEvent(List<String> out) {
        List<String> lines = com.teslamaps.utils.TabListUtils.getOrderedLines();
        for (int i = 0; i < lines.size(); i++) {
            String line = com.teslamaps.utils.StringUtil.stripTrim(lines.get(i));
            if (!line.startsWith("Event:")) continue;
            String name = line.substring(6).trim();
            if (name.isEmpty()) return;
            for (int j = i + 1; j < lines.size() && j <= i + 4; j++) {
                String l = com.teslamaps.utils.StringUtil.stripTrim(lines.get(j));
                if (l.isEmpty() || l.endsWith(":")) break;
                String low = l.toLowerCase(java.util.Locale.ROOT);
                if (low.startsWith("ends in:")) {
                    out.add("§d" + name + "§8: §aends in §f" + l.substring(8).trim());
                    return;
                }
                if (low.startsWith("starts in:")) {
                    out.add("§d" + name + "§8: §7starts in §f" + l.substring(10).trim());
                    return;
                }
            }
            out.add("§d" + name);
            return;
        }
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
