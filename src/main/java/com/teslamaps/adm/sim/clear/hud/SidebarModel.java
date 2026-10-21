// Ported from ADM's p3sim/clear/hud/HudModel.kt
package com.teslamaps.adm.sim.clear.hud;

import com.teslamaps.adm.sim.run.KeyState;
import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.RunStage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** The sidebar of Hypixel's dungeon, line by line. Pure. */
public final class SidebarModel {
    private SidebarModel() {}

    /** The objective Odin recognises as SkyBlock. */
    public static final String OBJECTIVE = "SBScoreboard";

    /** A sidebar shows at most 15 lines. */
    public static final int MAX_LINES = 15;

    /** Devonian reads only teams named `team_<n>`. */
    public static String teamName(int score) { return "team_" + score; }

    /** A unique, invisible score holder for each score. */
    public static String holder(int score) { return "§" + HOLDER_CHARS.charAt(Math.clamp(score - 1, 0, HOLDER_CHARS.length() - 1)); }

    private static final String HOLDER_CHARS = "ghijpqstuvwxyz!";

    /** The lines, top to bottom, with `§` codes. At most MAX_LINES. */
    public static List<String> lines(HudContext ctx, UUID viewer) {
        List<String> out = ctx.started() ? running(ctx, viewer) : lobby(ctx, viewer);
        // With the keys line and four teammates the sidebar is 16 lines and Hypixel drops the top (date) line.
        return out.size() > MAX_LINES ? new ArrayList<>(out.subList(out.size() - MAX_LINES, out.size())) : out;
    }

    private static String floorLine(HudContext ctx) { return " §7" + HudGlyphs.LOCATION + " §cThe Catacombs §7(" + ctx.floor + ")"; }

    /** The SkyBlock clock line: starts at 6:10pm and moves 10 minutes every CLOCK_TICKS_PER_STEP ticks. */
    public static String clockLine(int sinceBegin) {
        int minutes = HudConstants.CLOCK_START_MINUTES + 10 * (Math.max(0, sinceBegin) / HudConstants.CLOCK_TICKS_PER_STEP);
        int day = minutes % (24 * 60);
        int h24 = day / 60;
        int m = day % 60;
        String ampm = h24 >= 12 ? "pm" : "am";
        int h12 = h24 % 12 == 0 ? 12 : h24 % 12;
        return " §7" + h12 + ":" + HudFormat.two(m) + ampm;
    }

    /** Seconds the pre-start counter shows: rounded up, never below 1. */
    public static int startingSeconds(int ticksToStart) { return Math.max(1, (Math.max(0, ticksToStart) + 19) / 20); }

    /** Ticks before T0 at which `Auto-closing in` gives way to `Starting in`. */
    public static final int STARTING_FROM_TICKS = 80;

    /** The first value of the `Auto-closing in` counter (1:56), falling one per second. */
    public static final int AUTO_CLOSING_SECONDS = 116;

    private static List<String> lobby(HudContext ctx, UUID viewer) {
        List<String> out = new ArrayList<>();
        boolean starting = ctx.t0Known && ctx.ticksToStart() <= STARTING_FROM_TICKS;
        out.add("§7" + ctx.dateText + " §8" + HudConstants.LOBBY_SERVER);
        out.add("  ");
        out.add(HudConstants.SEASON);
        out.add(clockLine(ctx.sinceBegin));
        out.add(floorLine(ctx));
        out.add("      ");
        List<PlayerRecord> members = ctx.membersFor(viewer);
        for (int i = 0; i < Math.min(5, members.size()); i++) {
            PlayerRecord p = members.get(i);
            boolean ready = starting || Boolean.TRUE.equals(p.extras.get("ready"));
            out.add((ready ? "§a" : "§c") + "[" + HudFormat.classLetter(p.dungeonClass) + "] " + HudConstants.NAME_COLOUR + HudFormat.sidebarName(p.name)
                    + " §7[Lv" + ctx.stats.classLevel + "]");
        }
        out.add("       ");
        if (starting) {
            int secs = startingSeconds(ctx.ticksToStart());
            out.add("Starting in: §a" + (secs / 60) + "§a:" + HudFormat.two(secs % 60));
        } else {
            int secs = Math.max(1, AUTO_CLOSING_SECONDS - Math.max(0, ctx.sinceBegin) / 20);
            out.add("Auto-closing in: §c" + (secs / 60) + ":" + HudFormat.two(secs % 60));
        }
        out.add("        ");
        out.add(HudConstants.FOOTER);
        return out;
    }

    private static List<String> running(HudContext ctx, UUID viewer) {
        List<String> out = new ArrayList<>();
        out.add("§7" + ctx.dateText + " §8" + HudConstants.LOBBY_SERVER + " " + ctx.floor.toLowerCase(java.util.Locale.ROOT));
        out.add("  ");
        out.add(HudConstants.SEASON);
        out.add(clockLine(ctx.sinceBegin));
        out.add(floorLine(ctx));
        out.add("      ");
        if (keysShown(ctx)) out.add(keysLine(ctx));
        out.add("Time Elapsed: §a" + HudFormat.elapsed(ctx.elapsedTicks()));
        out.add(clearedLine(ctx));
        out.add("          ");
        List<PlayerRecord> others = new ArrayList<>();
        for (PlayerRecord p : ctx.membersFor(viewer)) {
            if (others.size() >= 4) break;
            if (!Objects.equals(p.uuid, viewer)) others.add(p);
        }
        if (others.isEmpty()) out.add("§3§lSolo");
        else for (PlayerRecord p : others) out.add(memberLine(ctx, p));
        out.addAll(ctx.extraSidebar);
        out.add("               ");
        out.add(HudConstants.FOOTER);
        return out;
    }

    /** The `Keys:` line stays from the start until the boss entry. */
    public static boolean keysShown(HudContext ctx) { return ctx.stage == RunStage.CLEAR || ctx.stage == RunStage.COUNTDOWN || ctx.stage == RunStage.BLOOD; }

    /** The blood slot shows the green tick only while the key is held. */
    public static String keysLine(HudContext ctx) {
        KeyState k = ctx.state.keys;
        String blood = k.bloodHeld ? "§c" + HudGlyphs.BLOCK + " §a" + HudGlyphs.TICK : "§c" + HudGlyphs.BLOCK + " §c" + HudGlyphs.CROSS;
        return "Keys: " + blood + " §8§8" + HudGlyphs.BLOCK + " §a" + k.witherHeld + "x";
    }

    public static String clearedColour(HudContext ctx, int pct) {
        if (pct >= ctx.clearedGreenFrom) return "§a";
        if (pct >= ctx.clearedGoldFrom) return "§6";
        return "§c";
    }

    /** `Cleared: 31% (53)`. Odin reads `^Cleared: (\d+)% \(\d+\)$`. */
    public static String clearedLine(HudContext ctx) {
        int pct = Math.clamp(ctx.state.counters.clearedPct(), 0, 100);
        return "Cleared: " + clearedColour(ctx, pct) + pct + "% §8(" + Math.max(0, ctx.state.score.score) + ")";
    }

    private static String hpColour(HudContext ctx, PlayerRecord p) {
        if (p.hpPercent >= HudConstants.HP_GREEN_FROM) return "§a";
        if (p.hpPercent >= HudConstants.HP_YELLOW_FROM) return "§e";
        return "§c";
    }

    /** next with its `Keys:` line kept as in previous unless refresh. */
    public static Map<Integer, String> holdKeys(Map<Integer, String> previous, Map<Integer, String> next, boolean refresh) {
        if (previous == null || refresh) return next;
        Map.Entry<Integer, String> at = null;
        for (Map.Entry<Integer, String> e : next.entrySet()) if (e.getValue().startsWith("Keys: ")) { at = e; break; }
        if (at == null) return next;
        String before = previous.get(at.getKey());
        if (before == null) return next;
        if (!before.startsWith("Keys: ") || before.equals(at.getValue())) return next;
        Map<Integer, String> out = new LinkedHashMap<>(next);
        out.put(at.getKey(), before);
        return out;
    }

    /** `[H] Name 13,879❤`, or `[M] Name DEAD` for a ghost. */
    public static String memberLine(HudContext ctx, PlayerRecord p) {
        String head = "§e[" + HudFormat.classLetter(p.dungeonClass) + "] " + HudConstants.NAME_COLOUR + HudFormat.sidebarName(p.name) + " ";
        return p.ghost ? head + "§cDEAD" : head + hpColour(ctx, p) + HudFormat.commas(ctx.hpOf(p)) + "§c" + HudGlyphs.HEART;
    }
}
