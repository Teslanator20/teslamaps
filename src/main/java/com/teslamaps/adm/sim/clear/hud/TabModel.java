// Ported from ADM's p3sim/clear/hud/TabModel.kt
package com.teslamaps.adm.sim.clear.hud;

import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.PuzzleRecord;
import com.teslamaps.adm.sim.run.PuzzleStatus;
import com.teslamaps.adm.sim.run.RunStage;
import com.teslamaps.adm.sim.run.RunState;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The dungeon tab list: 80 rows (4 columns of 20) of invisible fake players whose display names carry the text, as Hypixel lays them
 * out. Pure; the packets are built in HudPackets.
 */
public final class TabModel {
    private TabModel() {}

    public static final int ROWS = 80;
    public static final int COLUMN = 20;
    public static final int PARTY_ROWS = 16;

    /** The profile name of row i: `!A-a` ... `!D-t`. */
    public static String profileName(int i) { return "!" + (char) ('A' + i / COLUMN) + "-" + (char) ('a' + i % COLUMN); }

    /** A stable fake UUID per row (name-based, version 3). */
    public static UUID profileId(int i) { return UUID.nameUUIDFromBytes(("ads:tab:" + profileName(i)).getBytes(StandardCharsets.UTF_8)); }

    /** Hypixel's blank tab rows are empty text, not a space. */
    private static final String BLANK = "";

    public static final int PLAYER_STATS_AT = 20;
    public static final int DUNGEON_STATS_AT = 40;
    public static final int ACCOUNT_AT = 60;

    /** The most puzzle rows the block has room for. */
    public static final int PUZZLE_ROWS = 5;

    /** One decimal with a trailing `.0` dropped (`0%`, `2%`, `12.5%`). */
    public static String percent(double v) {
        String s = String.format(java.util.Locale.ROOT, "%.1f", v);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s + "%";
    }

    private static void padTo(List<String> rows, int n) { while (rows.size() < n) rows.add(BLANK); }

    /** The 80 rows for viewer's tab. */
    public static List<String> rows(HudContext ctx, UUID viewer) {
        RunState st = ctx.state;
        PlayerRecord me = viewer == null ? null : st.players.get(viewer);
        List<String> rows = new ArrayList<>(ROWS);
        List<PlayerRecord> members = ctx.membersFor(viewer);

        // Column A: the party, a header and five slots of four rows (the last slot has no blank row after it).
        rows.add("         Party (" + members.size() + ")");
        for (int slot = 0; slot < 5; slot++) {
            PlayerRecord p = slot < members.size() ? members.get(slot) : null;
            if (p == null) { rows.add(BLANK); rows.add(BLANK); rows.add(BLANK); }
            else {
                rows.add(memberRow(ctx, p));
                rows.add(ultimateRow(ctx, p));
                rows.add(" Revive Stones: " + reviveStones(ctx, p));
            }
            if (slot < 4) rows.add(BLANK);
        }

        // Column B: the player stats, then the team numbers.
        padTo(rows, PLAYER_STATS_AT);
        rows.add("       Player Stats");
        rows.add("Downed: NONE");
        rows.add(" Time: N/A");
        rows.add(" Revive: N/A");
        padTo(rows, PLAYER_STATS_AT + 5);
        rows.add("Team Deaths: " + st.deaths.teamDeaths);
        rows.add(" Team Damage Dealt: " + HudFormat.abbreviate(ctx.teamDamage) + HudGlyphs.HEART);
        rows.add(" Team Healing Done: " + HudFormat.abbreviate(ctx.teamHealing) + HudGlyphs.HEART);
        Integer milestone = me != null && me.extras.get("milestone") instanceof Number n ? n.intValue() : null;
        rows.add(!ctx.started() || milestone == null ? " Your Milestone: " + HudGlyphs.SKULL + MILESTONES.get(0)
                : " Your Milestone: " + HudGlyphs.SKULL + MILESTONES.get(Math.clamp(milestone, 0, MILESTONES.size() - 1)));
        double pct = me != null && me.extras.get("milestonePct") instanceof Number n ? n.doubleValue() : 0.0;
        int ms = milestone != null ? milestone : 0;
        int next = Math.clamp(ms + 1, 0, MILESTONES.size() - 1);
        rows.add(ms >= MILESTONES.size() - 1 ? BLANK : " (" + percent(pct) + " to " + MILESTONES.get(next) + ")");
        int own = me != null ? me.secretsFound : 0;
        rows.add("Discoveries: " + discoveries(own));
        rows.add(" Secrets Found: " + own);
        rows.add(" Crypts: " + st.secrets.crypts);

        // Column C: the dungeon stats and the puzzles.
        padTo(rows, DUNGEON_STATS_AT);
        rows.add("       Dungeon Stats");
        rows.add("Dungeon: Catacombs");
        rows.add(" Opened Rooms: " + st.counters.opened);
        rows.add(" Completed Rooms: " + st.counters.completed);
        rows.add(" Secrets Found: " + percent(st.secrets.percent()));
        rows.add(" Time: " + timeRow(ctx));
        rows.add(BLANK);
        rows.add("Puzzles: (" + st.puzzles.size() + ")");
        for (int i = 0; i < Math.min(PUZZLE_ROWS, st.puzzles.size()); i++) {
            PuzzleRecord pz = st.puzzles.get(i);
            rows.add(puzzleRow(pz.revealed, pz.tabName, pz.status, ctx.failedBy.get(i)));
        }
        rows.addAll(ctx.extraTab);
        if (rows.size() > ACCOUNT_AT) rows.subList(ACCOUNT_AT, rows.size()).clear();

        // Column D: the account block (plain filler; the sim has no SkyBlock profile).
        padTo(rows, ACCOUNT_AT);
        rows.add("       Account Info");
        rows.add("Profile: Practice");
        rows.add(" Pet Sitter: N/A");
        rows.add("Skills: Combat 60: MAX");
        rows.add(" Speed: 700");
        padTo(rows, ROWS);
        return new ArrayList<>(rows.subList(0, Math.min(ROWS, rows.size())));
    }

    /** The discoveries row: the player's secrets plus the non-secret finds. */
    public static int discoveries(int secrets) { return (int) Math.ceil(secrets * HudConstants.DISCOVERY_RATIO); }

    private static final List<String> MILESTONES = List.of("⓿", "❶", "❷", "❸", "❹", "❺", "❻", "❼", "❽", "❾", "❿");

    private static String timeRow(HudContext ctx) { return !ctx.started() ? "Soon!" : HudFormat.elapsed(ctx.elapsedTicks()); }

    /** The PlayerRecord.extras key of the ticks an ultimate still has to cool down. */
    public static final String ULTIMATE_COOLDOWN_KEY = "ultimateCooldownTicks";

    /** The PlayerRecord.extras key of a member's Revive Stone count, for members the HUD cannot count itself (bots). */
    public static final String REVIVE_STONES_KEY = "reviveStones";

    /** `Ultimate: N/A` until T0, `Ready`, or the cooldown clock. */
    public static String ultimateRow(HudContext ctx, PlayerRecord p) {
        if (!ctx.started()) return " Ultimate: N/A";
        int left = p.extras.get(ULTIMATE_COOLDOWN_KEY) instanceof Number n ? n.intValue() : 0;
        return left > 0 ? " Ultimate: " + HudFormat.elapsed(left) : " Ultimate: Ready";
    }

    /** The Revive Stones a member carries. */
    public static int reviveStones(HudContext ctx, PlayerRecord p) {
        Integer counted = ctx.reviveStones.get(p.uuid);
        if (counted != null) return counted;
        if (p.extras.get(REVIVE_STONES_KEY) instanceof Number n) return Math.max(n.intValue(), 0);
        return 0;
    }

    public static String memberRow(HudContext ctx, PlayerRecord p) {
        String cls = HudFormat.className(p.dungeonClass);
        // Until T0 every member reads EMPTY.
        int level = p.extras.get("classLevel") instanceof Number n ? n.intValue() : ctx.stats.classLevel;
        String tail;
        if (p.ghost) tail = "DEAD";
        else if (cls == null || !ctx.started()) tail = "EMPTY";
        else tail = cls + " " + HudFormat.roman(level);
        int sb = p.extras.get("sbLevel") instanceof Number n ? n.intValue() : ctx.stats.sbLevel;
        // A bot's identity carries the glyph real players wear after their name; everyone else reads the skull.
        String glyph = p.extras.get("glyph") instanceof String g ? g.replaceAll("§.", "") : null;
        String mid;
        if (glyph == null) mid = " " + HudGlyphs.SKULL;
        else if (glyph.isEmpty()) mid = "";
        else mid = " " + glyph;
        return "[" + sb + "] " + p.name + mid + " (" + tail + ")";
    }

    /** ` ???: [✦]` before the puzzle is known, ` Boulder: [✦] `, ` Boulder: [✔] `, ` Boulder: [✖] (name)`. */
    public static String puzzleRow(boolean revealed, String tabName, PuzzleStatus status, String failedBy) {
        String mark = switch (status) {
            case SOLVED -> "✔";
            case FAILED -> "✖";
            case UNSOLVED -> "✦";
        };
        if (!revealed) return " ???: [" + mark + "]";
        return status == PuzzleStatus.FAILED && failedBy != null ? " " + tabName + ": [" + mark + "] (" + failedBy + ")" : " " + tabName + ": [" + mark + "] ";
    }

    /** The tab header (Hypixel's two-line text). */
    public static final String HEADER = "§bYou are playing on §e§lMC.HYPIXEL.NET";

    private static final List<String> BLESSING_ORDER = List.of("Power", "Life", "Wisdom", "Stone", "Time");

    private static final String STORE_LINE = "§aRanks, Boosters & MORE! §c§lSTORE.HYPIXEL.NET";

    /** Ticks before T0 at which the Dungeon Buffs block appears (recorded: 15). */
    private static final int BUFFS_BEFORE_T0 = 15;

    /** The footer: before the run just the store line; then the active effects and the Dungeon Buffs block. */
    public static String footer(HudContext ctx) {
        if (ctx.stage == RunStage.COUNTDOWN && !(ctx.t0Known && ctx.ticksToStart() <= BUFFS_BEFORE_T0)) return STORE_LINE;
        Map<String, Integer> b = ctx.state.secrets.blessings;
        List<String> found = new ArrayList<>();
        for (String name : BLESSING_ORDER) {
            int lvl = 0;
            for (Map.Entry<String, Integer> e : b.entrySet()) if (e.getKey().equalsIgnoreCase(name)) { lvl = e.getValue() != null ? e.getValue() : 0; break; }
            if (lvl > 0) found.add("§fBlessing of " + name + " " + HudFormat.roman(lvl));
        }
        List<String> buffs = found.isEmpty() ? List.of("§7No Buffs active. Find them by exploring the Dungeon!") : found;
        List<String> all = new ArrayList<>(List.of("", "§a§lActive Effects", "§7No effects active.", "", "§6§lDungeon Buffs"));
        all.addAll(buffs);
        all.add("");
        all.add(STORE_LINE);
        return String.join("\n", all);
    }
}
