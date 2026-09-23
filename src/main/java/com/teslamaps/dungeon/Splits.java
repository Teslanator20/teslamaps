package com.teslamaps.dungeon;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.TeslaGui;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Splits {

    public static class Split {
        public final Pattern pattern;
        public final String name;   // may contain § color codes
        public long time = 0L;      // 0 = not reached yet
        Long pbAtStart;
        String delta;               // "+1.23s" vs the PB from before this run
        Split(String regex, String name) {
            this.pattern = Pattern.compile(regex);
            this.name = name;
        }
    }

    private static final List<Split> active = new ArrayList<>();
    private static long startTime = 0L;
    private static boolean finished = false;
    private static int pendingSend = 0; // ticks until end-of-run messages are sent (avoids interrupting Hypixel's run summary)

    private static final String MORT = "\\[NPC] Mort: Here, I found this map when I first entered the dungeon\\.|\\[NPC] Mort: Right-click the Orb for spells, and Left-click \\(or Drop\\) to use your Ultimate!";
    private static final String BLOOD_OPEN = "^\\[BOSS] The Watcher: (Congratulations, you made it through the Entrance\\.|Ah, you've finally arrived\\.|Ah, we meet again\\.\\.\\.|So you made it this far\\.\\.\\. interesting\\.|You've managed to scratch and claw your way here, eh\\?|I'm starting to get tired of seeing you around here\\.\\.\\.|Oh\\.\\. hello\\?|Things feel a little more roomy now, eh\\?)$|^The BLOOD DOOR has been opened!$";
    private static final String PORTAL_ENTRY = "\\[BOSS] The Watcher: You have proven yourself\\. You may pass\\.";
    private static final String TOTAL = "^\\s*☠ Defeated (.+) in 0?([\\dhms ]+?)\\s*(\\(NEW RECORD!\\))?$";

    private static void buildSplits() {
        active.clear();
        DungeonFloor floor = DungeonManager.getCurrentFloor();
        if (floor == null || floor == DungeonFloor.UNKNOWN) return;

        active.add(new Split(MORT, "§2Blood Open"));
        active.add(new Split(BLOOD_OPEN, "§bBlood Clear"));
        active.add(new Split(PORTAL_ENTRY, "§dPortal Entry"));

        switch (floor.getLevel()) {
            case 1 -> {
                active.add(new Split("\\[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable\\.", "§cBonzo's Sike"));
                active.add(new Split("\\[BOSS] Bonzo: Oh I'm dead!", "§4Cleared"));
            }
            case 2 -> {
                active.add(new Split("\\[BOSS] Scarf: This is where the journey ends for you, Adventurers\\.", "§cScarf's minions"));
                active.add(new Split("\\[BOSS] Scarf: Did you forget\\? I was taught by the best! Let's dance\\.", "§4Cleared"));
            }
            case 3 -> {
                active.add(new Split("\\[BOSS] The Professor: I was burdened with terrible news recently\\.\\.\\.", "§cThe Guardians"));
                active.add(new Split("\\[BOSS] The Professor: Oh\\? You found my Guardians' one weakness\\?", "§aThe Professor"));
                active.add(new Split("\\[BOSS] The Professor: What\\?! My Guardian power is unbeatable!", "§4Cleared"));
            }
            case 4 -> active.add(new Split("\\[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!", "§4Cleared"));
            case 5 -> active.add(new Split("\\[BOSS] Livid: Welcome, you've arrived right on time\\. I am Livid, the Master of Shadows\\.", "§4Cleared"));
            case 6 -> {
                active.add(new Split("\\[BOSS] Sadan: So you made it all the way here\\.\\.\\. Now you wish to defy me\\? Sadan\\?!", "§cTerracottas"));
                active.add(new Split("\\[BOSS] Sadan: ENOUGH!", "§aGiants"));
                active.add(new Split("\\[BOSS] Sadan: You did it\\. I understand now, you have earned my respect\\.", "§4Cleared"));
            }
            case 7 -> {
                active.add(new Split("\\[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!", "§5Maxor"));
                active.add(new Split("\\[BOSS] Storm: Pathetic Maxor, just like expected\\.", "§3Storm"));
                active.add(new Split("\\[BOSS] Goldor: Who dares trespass into my domain\\?", "§6Terminals"));
                active.add(new Split("The Core entrance is opening!", "§7Goldor"));
                active.add(new Split("\\[BOSS] Necron: You went further than any human before, congratulations\\.", "§cNecron"));
                active.add(new Split("\\[BOSS] Necron: All this, for nothing\\.\\.\\.", floor.isMaster() ? "§4Wither King" : "§4Cleared"));
            }
            default -> { }
        }

        active.add(new Split(TOTAL, "§1Total"));
        for (Split s : active) s.pbAtStart = TeslaMapsConfig.get().splitPbs.get(pbKey(s.name));
    }

    private static void setDelta(Split s, long durationMs) {
        if (s.pbAtStart == null) return;
        long d = durationMs - s.pbAtStart;
        s.delta = (d <= 0 ? "§a" : "§c") + String.format(java.util.Locale.ROOT, "%+.2fs", d / 1000.0);
    }

    public static void onChatMessage(String message) {
        if (!TeslaMapsConfig.get().splitsEnabled) return;

        message = message.replaceAll("(?i)§[0-9A-FK-OR]", "");

        if (message.equals("Starting in 1 second.")) {
            buildSplits();
            startTime = System.currentTimeMillis();
            finished = false;
            pendingSend = 0;
            return;
        }

        if (active.isEmpty() || finished) return;

        int n = active.size();
        for (int i = 0; i < n; i++) {
            Split split = active.get(i);
            if (split.time != 0L) continue;
            if (split.pattern.matcher(message).matches()) {
                split.time = System.currentTimeMillis();

                if (i > 0) {
                    Split prev = active.get(i - 1);
                    if (prev.time != 0L) setDelta(prev, split.time - prev.time);
                }
                if (i == n - 1) {
                    if (active.get(0).time != 0L) setDelta(split, split.time - active.get(0).time);
                    finished = true;
                    pendingSend = 10; // delay so we don't cut into Hypixel's run-summary message
                } else if (i > 0) {
                    Split prev = active.get(i - 1);
                    if (prev.time != 0L) sendSplit(prev.name, split.time - prev.time);
                }
                break;
            }
        }
    }

    public static void tick() {
        if (pendingSend > 0 && --pendingSend == 0) sendEndOfRun();
    }

    private static void sendEndOfRun() {
        int n = active.size();
        if (n == 0) return;
        Split last = active.get(n - 1);
        if (n >= 2) {
            Split prev = active.get(n - 2);
            if (prev.time != 0L && last.time != 0L) sendSplit(prev.name, last.time - prev.time);
        }
        long firstTime = active.get(0).time;
        if (firstTime != 0L && last.time != 0L) sendSplit(last.name, last.time - firstTime);
        if (TeslaMapsConfig.get().splitsSendAllOnEnd) sendAllSplits();
    }

    private static void sendSplit(String name, long durationMs) {
        updatePb(name, durationMs);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] " + name + " §7- §f" + formatTime(durationMs)));
    }

    private static void sendAllSplits() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        StringBuilder sb = new StringBuilder("§a[TeslaMaps] §6§lRun Splits");
        for (int k = 1; k < active.size(); k++) {
            Split prev = active.get(k - 1), cur = active.get(k);
            if (prev.time != 0L && cur.time != 0L)
                sb.append("\n  ").append(prev.name).append(" §7- §f").append(formatTime(cur.time - prev.time)).append(pbSuffix(prev.name));
        }
        Split first = active.get(0), last = active.get(active.size() - 1);
        if (first.time != 0L && last.time != 0L)
            sb.append("\n  §eTotal §7- §f").append(formatTime(last.time - first.time)).append(pbSuffix(last.name));
        mc.player.sendSystemMessage(Component.literal(sb.toString()));
    }

    private static String pbSuffix(String name) {
        Long pb = TeslaMapsConfig.get().splitPbs.get(pbKey(name));
        return pb == null ? "" : " §8(" + formatTime(pb) + ")";
    }

    private static String pbKey(String name) {
        String floor = DungeonManager.getFloorName();
        if (floor == null || floor.isEmpty()) floor = "?";
        return floor + ":" + name.replaceAll("(?i)§[0-9A-FK-OR]", "");
    }

    private static void updatePb(String name, long durationMs) {
        if (durationMs <= 0) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        String key = pbKey(name);
        Long pb = c.splitPbs.get(key);
        if (pb == null || durationMs < pb) {
            c.splitPbs.put(key, durationMs);
            TeslaMapsConfig.save();
        }
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.splitsEnabled || active.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int nameCol = 0;
        for (Split split : active) nameCol = Math.max(nameCol, mc.font.width(split.name));
        nameCol += 6;

        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(config.splitsX, config.splitsY);
        pose.scale(config.splitsScale, config.splitsScale);

        long now = System.currentTimeMillis();
        int n = active.size();
        long firstTime = active.get(0).time;
        long latest = active.get(n - 1).time != 0L ? active.get(n - 1).time : now;

        boolean showPb = config.splitsShowPb;
        boolean showDelta = config.splitsShowDelta;

        // Widest row decides the backdrop; the times are monospaced enough that
        // the longest name plus a full time and PB is a safe upper bound.
        int widest = nameCol + mc.font.width("00:00.0");
        if (showPb) widest += mc.font.width(" (00:00.0)") + 4;
        if (showDelta) widest += mc.font.width("+00.00s") + 4;
        TeslaGui.backdrop(context, 0, 0, widest, n * 9 - 1);

        int y = 0;
        for (int i = 0; i < n; i++) {
            Split split = active.get(i);
            String timeStr;
            long frozenMs = -1; // a finalized phase duration (used for the PB comparison); -1 = live/not started
            if (i == n - 1) {
                if (firstTime == 0L) {
                    timeStr = "§8-";
                } else {
                    timeStr = formatTime(latest - firstTime);
                    if (active.get(n - 1).time != 0L) frozenMs = latest - firstTime; // finalized total
                }
            } else if (split.time == 0L) {
                timeStr = "§8-";                                     // this phase not started yet
            } else {
                Split next = active.get(i + 1);
                if (next.time != 0L) {
                    frozenMs = next.time - split.time;
                    timeStr = formatTime(frozenMs);                   // frozen: this phase's duration
                } else {
                    timeStr = "§7" + formatTime(now - split.time);    // current phase, counting from 0
                }
            }

            Long pb = showPb ? config.splitPbs.get(pbKey(split.name)) : null;
            int timeColor = 0xFFFFFFFF;
            if (frozenMs >= 0 && pb != null) timeColor = frozenMs <= pb ? 0xFF55FF55 : 0xFFFF5555; // green = beat PB
            context.text(mc.font, split.name, 0, y, 0xFFFFFFFF);
            context.text(mc.font, timeStr, nameCol, y, timeColor);
            int px = nameCol + mc.font.width(timeStr) + 4;
            if (showDelta && split.delta != null && frozenMs >= 0) {
                context.text(mc.font, split.delta, px, y, 0xFFFFFFFF);
                px += mc.font.width(split.delta) + 4;
            }
            if (pb != null) {
                context.text(mc.font, "§8(" + formatTime(pb) + ")", px, y, 0xFFFFFFFF);
            }
            y += 9;
        }
        pose.popMatrix();
    }

    public static int hudWidth() {
        Minecraft mc = Minecraft.getInstance();
        int nameCol = 0;
        for (Split split : active) nameCol = Math.max(nameCol, mc.font.width(split.name));
        int w = nameCol + 6 + mc.font.width("00:00.0");
        if (TeslaMapsConfig.get().splitsShowPb) w += 8 + mc.font.width("00:00.0");
        if (TeslaMapsConfig.get().splitsShowDelta) w += 4 + mc.font.width("+00.00s");
        return w;
    }

    public static int hudHeight() {
        return Math.max(1, active.size()) * 9;
    }

    public static boolean hasSplits() {
        return !active.isEmpty();
    }

    public static void reset() {
        active.clear();
        startTime = 0L;
        finished = false;
        pendingSend = 0;
    }

    private static String formatTime(long ms) {
        long totalSec = ms / 1000;
        long tenths = (ms % 1000) / 100;
        long minutes = totalSec / 60;
        long seconds = totalSec % 60;
        if (minutes >= 60) {
            long hours = minutes / 60;
            minutes %= 60;
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d.%d", minutes, seconds, tenths);
    }
}
