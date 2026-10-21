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
        long tick;
        String tickText;
        Long pbAtStart;
        String delta;               // "+1.23s" vs the PB from before this run
        Split(String regex, String name) {
            this.pattern = Pattern.compile(regex);
            this.name = name;
        }
        Split(Pattern pattern, String name) {
            this.pattern = pattern;
            this.name = name;
        }
    }

    private static final List<Split> active = new ArrayList<>();
    private static long startTime = 0L;
    private static boolean finished = false;
    private static int pendingSend = 0; // ticks until end-of-run messages are sent (avoids interrupting Hypixel's run summary)
    private static String bossEntryTick;
    private static String currentLine;
    private static String doorsLine, score300Time, score300Tick;
    private static int scoreCalcCooldown;
    private static final String SCORE_300 = "§e300 Score";
    private static final String BOSS_ENTRY = "§eBoss Entry";
    private static final String CURRENT_SAMPLE = "§bBlood Clear§f: 412t §8(20.6s)";
    private static com.teslamaps.registry.HudEditElement currentHud;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        currentHud = com.teslamaps.registry.HudEditElement.of("currentSplitHud", "Current Split")
                .ctx(com.teslamaps.registry.HudEditElement.HudCtx.DUNGEON).section("Score & Splits")
                .enabled(() -> cfg().splitsEnabled && cfg().splitsCurrentHud)
                .size(() -> Minecraft.getInstance().font.width(CURRENT_SAMPLE), () -> 9)
                .scaleRange(0.5f, 5.0f)
                .preview((g, mc, x, y, s) -> drawCurrent(g, mc, x, y, s, CURRENT_SAMPLE));
        com.teslamaps.registry.Feature.of("CurrentSplitHud").section("Score & Splits")
                .when(() -> cfg().splitsEnabled && cfg().splitsCurrentHud)
                .hud("currentsplit", Splits::renderCurrent);
    }

    private static boolean hasBossEntry() {
        return active.size() > 4;
    }

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

        List<Split> custom = customSplits(floor);
        if (custom != null) active.addAll(custom);
        else for (String[] d : defaultBossSplits(floor)) active.add(new Split(d[0], d[1]));

        active.add(new Split(TOTAL, "§1Total"));
        // M7 last phase used to be keyed "Cleared"
        var pbs = TeslaMapsConfig.get().splitPbs;
        String oldKey = pbKey("Cleared"), newKey = pbKey("Wither King");
        if (floor.isMaster() && floor.getLevel() == 7 && pbs.containsKey(oldKey) && !pbs.containsKey(newKey)) pbs.put(newKey, pbs.get(oldKey));
        for (Split s : active) s.pbAtStart = TeslaMapsConfig.get().splitPbs.get(pbKey(s.name));
    }

    /** The floor's built-in boss splits as {regex, name}. */
    public static List<String[]> defaultBossSplits(DungeonFloor floor) {
        List<String[]> d = new ArrayList<>();
        switch (floor.getLevel()) {
            case 1 -> {
                d.add(new String[]{"\\[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable\\.", "§cBonzo's Sike"});
                d.add(new String[]{"\\[BOSS] Bonzo: Oh I'm dead!", "§4Cleared"});
            }
            case 2 -> {
                d.add(new String[]{"\\[BOSS] Scarf: This is where the journey ends for you, Adventurers\\.", "§cScarf's minions"});
                d.add(new String[]{"\\[BOSS] Scarf: Did you forget\\? I was taught by the best! Let's dance\\.", "§4Cleared"});
            }
            case 3 -> {
                d.add(new String[]{"\\[BOSS] The Professor: I was burdened with terrible news recently\\.\\.\\.", "§cThe Guardians"});
                d.add(new String[]{"\\[BOSS] The Professor: Oh\\? You found my Guardians' one weakness\\?", "§aThe Professor"});
                d.add(new String[]{"\\[BOSS] The Professor: What\\?! My Guardian power is unbeatable!", "§4Cleared"});
            }
            case 4 -> d.add(new String[]{"\\[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!", "§4Cleared"});
            case 5 -> d.add(new String[]{"\\[BOSS] Livid: Welcome, you've arrived right on time\\. I am Livid, the Master of Shadows\\.", "§4Cleared"});
            case 6 -> {
                d.add(new String[]{"\\[BOSS] Sadan: So you made it all the way here\\.\\.\\. Now you wish to defy me\\? Sadan\\?!", "§cTerracottas"});
                d.add(new String[]{"\\[BOSS] Sadan: ENOUGH!", "§aGiants"});
                d.add(new String[]{"\\[BOSS] Sadan: You did it\\. I understand now, you have earned my respect\\.", "§4Cleared"});
            }
            case 7 -> {
                d.add(new String[]{"\\[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!", "§5Maxor"});
                d.add(new String[]{"\\[BOSS] Storm: Pathetic Maxor, just like expected\\.", "§3Storm"});
                d.add(new String[]{"\\[BOSS] Goldor: Who dares trespass into my domain\\?", "§6Terminals"});
                d.add(new String[]{"The Core entrance is opening!", "§7Goldor"});
                d.add(new String[]{"\\[BOSS] Necron: You went further than any human before, congratulations\\.", "§cNecron"});
                d.add(new String[]{"\\[BOSS] Necron: All this, for nothing\\.\\.\\.", floor.isMaster() ? "§4Wither King" : "§4Cleared"});
            }
            default -> { }
        }
        return d;
    }

    private static List<Split> customSplits(DungeonFloor floor) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.customSplits) return null;
        List<TeslaMapsConfig.CustomSplit> defs = c.customSplitDefs.get(floor.name());
        if (defs == null) return null;
        List<Split> out = new ArrayList<>();
        for (TeslaMapsConfig.CustomSplit cs : defs) {
            if (cs == null || cs.message == null || cs.message.isBlank()) continue;
            String name = cs.name == null || cs.name.isBlank() ? "Split " + (out.size() + 1) : cs.name.replace('&', '§');
            out.add(new Split(customPattern(cs.message.trim()), name));
        }
        return out.isEmpty() ? null : out;
    }

    private static Pattern customPattern(String msg) {
        if (msg.length() > 2 && msg.startsWith("/") && msg.endsWith("/")) {
            try {
                return Pattern.compile(msg.substring(1, msg.length() - 1));
            } catch (java.util.regex.PatternSyntaxException ignored) { }
        }
        return Pattern.compile(".*" + Pattern.quote(msg) + ".*");
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
            bossEntryTick = null;
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
                split.tick = com.teslamaps.utils.ServerTickTimer.now();
                if (i == 3 && hasBossEntry() && cfg().splitsBossEntry && active.get(0).time != 0L)
                    sendSplit(BOSS_ENTRY, split.time - active.get(0).time);

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
        TeslaMapsConfig c = cfg();
        int n = active.size();
        if (!c.splitsEnabled || n == 0) { currentLine = null; doorsLine = null; score300Time = null; return; }
        long now = com.teslamaps.utils.ServerTickTimer.now();
        extraRows(c, now);
        if (c.splitsTickTime) {
            Split first = active.get(0), last = active.get(n - 1);
            for (int i = 0; i < n - 1; i++) {
                Split s = active.get(i), next = active.get(i + 1);
                if (s.time == 0L) s.tickText = null;
                else s.tickText = tickText((next.time != 0L ? next.tick : now) - s.tick);
            }
            last.tickText = first.time == 0L ? null : tickText((last.time != 0L ? last.tick : now) - first.tick);
            if (hasBossEntry() && first.time != 0L) {
                Split boss = active.get(3);
                bossEntryTick = tickText((boss.time != 0L ? boss.tick : now) - first.tick);
            } else bossEntryTick = null;
        }
        if (c.splitsCurrentHud && !finished) {
            Split cur = null;
            for (int i = n - 2; i >= 0; i--) {
                if (active.get(i).time != 0L) { cur = active.get(i); break; }
            }
            if (cur == null) { currentLine = null; return; }
            long t = now - cur.tick;
            currentLine = cur.name + "§f: " + t + "t §8(" + (t / 20) + "." + (t % 20 / 2) + "s)";
        } else currentLine = null;
    }

    private static void extraRows(TeslaMapsConfig c, long now) {
        doorsLine = c.splitsWitherDoors
                ? "§8Wither Doors: §7" + com.teslamaps.scanner.DoorScanner.countType(com.teslamaps.map.DoorType.WITHER) : null;
        if (!c.splits300Score) { score300Time = null; return; }
        Split first = active.get(0);
        long at = DungeonScore.reached300Ms();
        if (at == 0L && !finished && --scoreCalcCooldown <= 0) {
            scoreCalcCooldown = 10;
            DungeonScore.calculateScore();
            at = DungeonScore.reached300Ms();
        }
        if (first.time == 0L) { score300Time = "§8-"; score300Tick = null; return; }
        if (at != 0L) {
            score300Time = formatTime(Math.max(0, at - first.time));
            score300Tick = tickText(DungeonScore.reached300Tick() - first.tick);
        } else if (finished) {
            score300Time = "§8-";
            score300Tick = null;
        } else {
            score300Time = "§7" + formatTime(System.currentTimeMillis() - first.time);
            score300Tick = tickText(now - first.tick);
        }
    }

    private static String tickText(long ticks) {
        if (ticks < 0) ticks = 0;
        return "§8(" + (ticks / 20) + "." + (ticks % 20 / 2) + ")";
    }

    private static void renderCurrent(GuiGraphicsExtractor g, DeltaTracker d) {
        String l = currentLine;
        if (l == null || currentHud == null) return;
        if (com.teslamaps.adm.leap.splits.AdmSplitsHud.overridesOurs()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        drawCurrent(g, mc, currentHud.x(), currentHud.y(), currentHud.scale(), l);
    }

    private static void drawCurrent(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(mc.font, text, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
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
        if (hasBossEntry() && TeslaMapsConfig.get().splitsBossEntry && first.time != 0L && active.get(3).time != 0L)
            sb.append("\n  ").append(BOSS_ENTRY).append(" §7- §f").append(formatTime(active.get(3).time - first.time)).append(pbSuffix(BOSS_ENTRY));
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
        if (com.teslamaps.adm.sim.P3Sim.inSim()) return;
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
        if (com.teslamaps.adm.leap.splits.AdmSplitsHud.overridesOurs()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int nameCol = 0;
        for (Split split : active) nameCol = Math.max(nameCol, mc.font.width(split.name));
        String s300 = score300Time, doors = doorsLine;
        if (s300 != null) nameCol = Math.max(nameCol, mc.font.width(SCORE_300));
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
        boolean showTicks = config.splitsTickTime;
        boolean bossRow = config.splitsBossEntry && hasBossEntry();
        int rows = (bossRow ? n + 1 : n) + (s300 != null ? 1 : 0) + (doors != null ? 1 : 0);

        // Widest row decides the backdrop; the times are monospaced enough that
        // the longest name plus a full time and PB is a safe upper bound.
        int widest = nameCol + mc.font.width("00:00.0");
        if (showPb) widest += mc.font.width(" (00:00.0)") + 4;
        if (showDelta) widest += mc.font.width("+00.00s") + 4;
        if (showTicks) widest += mc.font.width("(0000.0)") + 4;
        TeslaGui.backdrop(context, 0, 0, widest, rows * 9 - 1);

        int y = 0;
        if (doors != null) {
            context.text(mc.font, doors, 0, y, 0xFFFFFFFF);
            y += 9;
        }
        for (int i = 0; i < n; i++) {
            Split split = active.get(i);
            if (s300 != null && i == Math.min(3, n - 1)) {
                context.text(mc.font, SCORE_300, 0, y, 0xFFFFFFFF);
                context.text(mc.font, s300, nameCol, y, 0xFFFFFFFF);
                String t3 = score300Tick;
                if (showTicks && t3 != null) context.text(mc.font, t3, nameCol + mc.font.width(s300) + 4, y, 0xFFFFFFFF);
                y += 9;
            }
            if (bossRow && i == n - 1) {
                Split boss = active.get(3);
                String bt;
                long bossMs = -1;
                if (firstTime == 0L) bt = "§8-";
                else if (boss.time != 0L) { bossMs = boss.time - firstTime; bt = formatTime(bossMs); }
                else bt = "§7" + formatTime(now - firstTime);
                Long bpb = showPb ? config.splitPbs.get(pbKey(BOSS_ENTRY)) : null;
                int bc = bossMs >= 0 && bpb != null ? (bossMs <= bpb ? 0xFF55FF55 : 0xFFFF5555) : 0xFFFFFFFF;
                context.text(mc.font, BOSS_ENTRY, 0, y, 0xFFFFFFFF);
                context.text(mc.font, bt, nameCol, y, bc);
                int bx = nameCol + mc.font.width(bt) + 4;
                String btk = bossEntryTick;
                if (showTicks && btk != null) {
                    context.text(mc.font, btk, bx, y, 0xFFFFFFFF);
                    bx += mc.font.width(btk) + 4;
                }
                if (bpb != null) context.text(mc.font, "§8(" + formatTime(bpb) + ")", bx, y, 0xFFFFFFFF);
                y += 9;
            }
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
            String tk = split.tickText;
            if (showTicks && tk != null) {
                context.text(mc.font, tk, px, y, 0xFFFFFFFF);
                px += mc.font.width(tk) + 4;
            }
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
        if (TeslaMapsConfig.get().splitsTickTime) w += 4 + mc.font.width("(0000.0)");
        return w;
    }

    public static int hudHeight() {
        int rows = active.size() + (TeslaMapsConfig.get().splitsBossEntry && hasBossEntry() ? 1 : 0)
                + (score300Time != null ? 1 : 0) + (doorsLine != null ? 1 : 0);
        return Math.max(1, rows) * 9;
    }

    public static boolean hasSplits() {
        return !active.isEmpty();
    }

    public static void reset() {
        active.clear();
        startTime = 0L;
        finished = false;
        pendingSend = 0;
        bossEntryTick = null;
        currentLine = null;
        doorsLine = score300Time = score300Tick = null;
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
