package com.teslamaps.adm.leap.splits;

// Ported from ADM's splits/OdinSplitsLook.kt and Odin's Splits / SplitsManager
import com.teslamaps.adm.Adm;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** The ADM splits HUD: Odin's split rows, drawn Odin's way or the ADM (Engineer) way with Pace, lag and grades. */
public final class AdmSplitsHud {

    private AdmSplitsHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static final class Row {
        final Pattern regex;
        final String name;
        long time, ticks;
        Row(String regex, String name) { this.regex = Pattern.compile(regex); this.name = name; }
    }

    private static final String MORT = "\\[NPC] Mort: Here, I found this map when I first entered the dungeon\\.|\\[NPC] Mort: Right-click the Orb for spells, and Left-click \\(or Drop\\) to use your Ultimate!";
    private static final String BLOOD_OPEN = "^\\[BOSS] The Watcher: (Congratulations, you made it through the Entrance\\.|Ah, you've finally arrived\\.|Ah, we meet again\\.\\.\\.|So you made it this far\\.\\.\\. interesting\\.|You've managed to scratch and claw your way here, eh\\?|I'm starting to get tired of seeing you around here\\.\\.\\.|Oh\\.\\. hello\\?|Things feel a little more roomy now, eh\\?)$|^The BLOOD DOOR has been opened!$";
    private static final String PORTAL_ENTRY = "\\[BOSS] The Watcher: You have proven yourself\\. You may pass\\.";
    private static final String CLEARED = "^\\s*☠ Defeated (.+) in 0?([\\dhms ]+?)\\s*(\\(NEW RECORD!\\))?$";

    private static String[][] floorSplits(int level) {
        return switch (level) {
            case 1 -> new String[][]{{"^\\[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable\\.$", "§cBonzo's Sike"}, {"\\[BOSS] Bonzo: Oh I'm dead!", "§4Cleared"}};
            case 2 -> new String[][]{{"^\\[BOSS] Scarf: This is where the journey ends for you, Adventurers\\.$", "§cScarf's minions"}, {"^\\[BOSS] Scarf: Did you forget\\? I was taught by the best! Let's dance\\.$", "§4Cleared"}};
            case 3 -> new String[][]{{"^\\[BOSS] The Professor: I was burdened with terrible news recently\\.\\.\\.$", "§cThe Guardians"}, {"^\\[BOSS] The Professor: Oh\\? You found my Guardians' one weakness\\?$", "§aThe Professor"}, {"^\\[BOSS] The Professor: What\\?! My Guardian power is unbeatable!$", "§4Cleared"}};
            case 4 -> new String[][]{{"^\\[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!$", "§4Cleared"}};
            case 5 -> new String[][]{{"^\\[BOSS] Livid: Welcome, you've arrived right on time\\. I am Livid, the Master of Shadows\\.$", "§4Cleared"}};
            case 6 -> new String[][]{{"^\\[BOSS] Sadan: So you made it all the way here\\.\\.\\. Now you wish to defy me\\? Sadan\\?!$", "§cTerracottas"}, {"^\\[BOSS] Sadan: ENOUGH!$", "§aGiants"}, {"^\\[BOSS] Sadan: You did it\\. I understand now, you have earned my respect\\.$", "§4Cleared"}};
            case 7 -> new String[][]{{"^\\[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!$", "§5Maxor"}, {"\\[BOSS] Storm: Pathetic Maxor, just like expected\\.", "§3Storm"},
                    {"\\[BOSS] Goldor: Who dares trespass into my domain\\?", "§6Terminals"}, {"The Core entrance is opening!", "§7Goldor"},
                    {"\\[BOSS] Necron: You went further than any human before, congratulations\\.", "§cNecron"}, {Pattern.quote(SplitTracker.RELICS_PLACED), "§4Cleared"}};
            default -> new String[0][];
        };
    }

    private static final List<String> FLOOR7_NAMES = List.of("§5Maxor", "§3Storm", "§6Terminals", "§7Goldor", "§cNecron", "§4Cleared");
    private static final List<String> PB_NAMES;
    static {
        List<String> l = new ArrayList<>(List.of("§2Blood Open", "§bBlood Clear", "§dPortal Entry"));
        l.addAll(FLOOR7_NAMES);
        PB_NAMES = List.copyOf(l);
    }

    private static final List<Row> rows = new ArrayList<>();
    private static HudEditElement displayHud, currentHud;
    private static boolean failed;

    public static boolean overridesOurs() { return Adm.overrides(cfg().admSplits); }

    private static boolean engineer() { return !"Odin".equals(cfg().admSplitsLook); }

    public static void register() {
        displayHud = HudEditElement.of("admSplits", "ADM Splits").ctx(HudEditElement.HudCtx.DUNGEON).section(Adm.SECTION)
                .enabled(() -> cfg().admSplits)
                .size(() -> previewSize()[0], () -> previewSize()[1])
                .preview((g, mc, x, y, s) -> at(g, x, y, s, () -> drawDisplay(g, true)));
        currentHud = HudEditElement.of("admSplitsCurrent", "ADM Current Split").ctx(HudEditElement.HudCtx.DUNGEON).section(Adm.SECTION)
                .enabled(() -> cfg().admSplits && cfg().admSplitsCurrent)
                .size(() -> 80, () -> 9)
                .preview((g, mc, x, y, s) -> at(g, x, y, s, () -> drawCurrent(g, true)));

        Feature.of("AdmSplits").section(Adm.SECTION).when(() -> cfg().admSplits)
                .serverChat(AdmSplitsHud::onChat)
                .tick(AdmSplitsHud::tick)
                .hud("admsplits", AdmSplitsHud::render)
                .config("ADM Splits", b -> {
                    b.label("Splits");
                    b.toggle("ADM Splits", () -> cfg().admSplits, v -> cfg().admSplits = v)
                            .desc("ADM's own splits HUD (Odin's split rows). Replaces the TeslaMaps splits HUD while on. Position in /tmap gui.");
                    b.dropdown("Look", new String[]{"Odin", "ADM"}, () -> cfg().admSplitsLook, v -> cfg().admSplitsLook = v)
                            .desc("Odin's look, or ADM's: lines as Name > time (ticks), with a Pace line on top, the projected finish from the targets under Pace.");
                    b.toggle("Color Based Off Time", () -> cfg().admSplitsColorByTime, v -> cfg().admSplitsColorByTime = v)
                            .desc("Floor 7 split times coloured by how fast they were (dark green to black), here and in Sub Splits. Off: each time in its split's own colour.");
                    b.toggle("Show PB", () -> cfg().admSplitsShowPb, v -> cfg().admSplitsShowPb = v)
                            .desc("Times that tie or beat your best for that split in gold. Off: they get their colour from the time like any other.");
                    b.toggle("Fixed Width", () -> cfg().admSplitsFixedWidth, v -> cfg().admSplitsFixedWidth = v)
                            .desc("Always use a fixed HUD width, right-aligning the times.");
                    b.toggle("Boss Entry Split", () -> cfg().admSplitsBossEntry, v -> cfg().admSplitsBossEntry = v)
                            .desc("Split for boss entry.");
                    b.toggle("Show 0 splits", () -> cfg().admSplitsShow0, v -> cfg().admSplitsShow0 = v)
                            .desc("Shows splits which have their time at 0.");
                    b.toggle("Show Tick Time", () -> cfg().admSplitsTickTime, v -> cfg().admSplitsTickTime = v)
                            .desc("Show tick-based time alongside real time.");
                    b.toggle("Show Lag", () -> cfg().admSplitsShowLag, v -> cfg().admSplitsShowLag = v)
                            .desc("ADM look: the Lag line on top, the time lost to lag so far this run (floor 7).");
                    b.toggle("Show Pace", () -> cfg().admSplitsShowPace, v -> cfg().admSplitsShowPace = v)
                            .desc("ADM look: the Pace line, the projected finish.");
                    b.toggle("Enter After Entry", () -> cfg().admSplitsEnterAfterEntry, v -> cfg().admSplitsEnterAfterEntry = v)
                            .desc("ADM look: only show the Enter line (Boss Entry) once you are in the boss, not counting up through the clear.");
                    b.dropdown("Pace From", new String[]{"Recent Runs", "Targets"}, () -> cfg().admSplitsPaceFrom, v -> cfg().admSplitsPaceFrom = v)
                            .desc("What Pace counts each split still to come as. Recent Runs: your typical time for it, the median of your last 20 (until 5 are kept, its target). Targets: the target boxes (or PBs).");
                    b.dropdown("Pace Targets", new String[]{"F7", "M7"}, () -> cfg().admSplitsPaceFloor, v -> cfg().admSplitsPaceFloor = v)
                            .desc("Which floor's targets Edit Targets and Fill From PBs work on. Pace uses the ones for the floor you are on.");
                    b.button("Edit Targets", () -> Minecraft.getInstance().setScreen(new AdmSplitTargetsScreen(Minecraft.getInstance().screen, "M7".equals(cfg().admSplitsPaceFloor))))
                            .desc("How long each split should take, in seconds (61.5) or minutes (1:01.5). Blank uses your PB for it (on F7 without a PB, its dark green time).");
                    b.button("Fill From PBs", () -> fillFromPbs("M7".equals(cfg().admSplitsPaceFloor)))
                            .desc("Fills the targets (F7 or M7, as Pace Targets) with your PB for each split, fixed from now on.");
                    b.toggle("Current Split HUD", () -> cfg().admSplitsCurrent, v -> cfg().admSplitsCurrent = v)
                            .desc("Shows only the current split and its tick time.");
                });
    }

    // ------------------------------------------------------------------ rows (Odin's SplitsManager)

    private static void onChat(String raw) {
        String msg = StringUtil.strip(raw);
        if (msg.equals("Starting in 1 second.")) { startRun(); return; }
        for (Row r : rows) {
            if (!r.regex.matcher(msg).matches()) continue;
            if (r.time != 0L) return;
            r.time = System.currentTimeMillis();
            r.ticks = ServerTickTimer.now();
            return;
        }
    }

    private static Object lastLevel;

    private static void tick() {
        Object level = Minecraft.getInstance().level;
        if (level != lastLevel) { lastLevel = level; rows.clear(); }
    }

    private static void startRun() {
        rows.clear();
        DungeonFloor floor = DungeonManager.getCurrentFloor();
        if (floor == null || floor == DungeonFloor.UNKNOWN) return;
        rows.add(new Row(MORT, "§2Blood Open"));
        rows.add(new Row(BLOOD_OPEN, "§bBlood Clear"));
        rows.add(new Row(PORTAL_ENTRY, "§dPortal Entry"));
        for (String[] s : floorSplits(floor.getLevel())) rows.add(new Row(s[0], s[1]));
        rows.add(new Row(CLEARED, "§1Total"));
    }

    private static List<EngineerLook.Row> currentRows() {
        if (rows.isEmpty()) return List.of();
        List<EngineerLook.Row> out = new ArrayList<>();
        if (rows.get(0).time == 0L) {
            for (Row r : rows) out.add(new EngineerLook.Row(r.name, 0L, 0L, false));
            return out;
        }
        Row last = rows.get(rows.size() - 1);
        long latestTime = last.time != 0L ? last.time : System.currentTimeMillis();
        long latestTicks = last.ticks != 0L ? last.ticks : ServerTickTimer.now();
        long[] times = new long[rows.size()], ticks = new long[rows.size()];
        times[rows.size() - 1] = latestTime - rows.get(0).time;
        ticks[rows.size() - 1] = latestTicks - rows.get(0).ticks;
        int current = -1;
        for (int i = 0; i < rows.size() - 1; i++) {
            Row next = rows.get(i + 1);
            if (next.time != 0L) {
                times[i] = next.time - rows.get(i).time;
                ticks[i] = next.ticks - rows.get(i).ticks;
            } else {
                times[i] = latestTime - rows.get(i).time;
                ticks[i] = latestTicks - rows.get(i).ticks;
                current = i;
                break;
            }
        }
        for (int i = 0; i < rows.size(); i++) out.add(new EngineerLook.Row(rows.get(i).name, times[i], ticks[i], i == current));
        return out;
    }

    // ------------------------------------------------------------------ targets, PBs

    private static String[] boxes(boolean master) {
        String raw = master ? cfg().admSplitsTargetsM7 : cfg().admSplitsTargetsF7;
        int n = EngineerLook.targetLabels(master).size();
        String[] out = new String[n];
        Arrays.fill(out, "");
        if (raw != null && !raw.isEmpty()) {
            String[] p = raw.split("\\|", -1);
            for (int i = 0; i < Math.min(n, p.length); i++) out[i] = p[i];
        }
        return out;
    }

    static String box(boolean master, int i) { return boxes(master)[i]; }

    static void setBox(boolean master, int i, String v) {
        String[] b = boxes(master);
        b[i] = v == null ? "" : v.replace("|", "");
        String joined = String.join("|", b);
        if (master) cfg().admSplitsTargetsM7 = joined; else cfg().admSplitsTargetsF7 = joined;
    }

    private static SplitPace.Clocks secs(double s) { return new SplitPace.Clocks(Math.round(s * 1000), Math.round(s * 20)); }

    private static SplitPace.Clocks target(int i, boolean master) {
        String[] b = boxes(master);
        if (i < b.length) {
            Double s = EngineerLook.parseSeconds(b[i]);
            if (s != null) return secs(s);
        }
        return pb(i, master);
    }

    public static SplitPace.Clocks paceTarget(String label, boolean master) {
        int i = SplitPace.ORDER.indexOf(label);
        if (i < 0) return null;
        SplitPace.Clocks t = typical(i, master);
        return t != null ? t : target(i, master);
    }

    private static SplitPace.Clocks typical(int i, boolean master) {
        if (!"Recent Runs".equals(cfg().admSplitsPaceFrom)) return null;
        if (i >= SplitPace.ALL.size()) return null;
        return DungeonSplits.typical(master ? "M7" : "F7", SplitPace.ALL.get(i));
    }

    static SplitPace.Clocks pb(int i, boolean master) {
        String floor = master ? "M7" : "F7";
        String id = i < SplitPace.ALL.size() ? SplitPace.SPLIT_IDS.get(SplitPace.ALL.get(i)) : null;
        SplitPace.Clocks ours = null;
        if (id != null) {
            Long b = DungeonSplits.bestOf(floor, id);
            if (b != null && SubSplitGrades.canBeBest(id, b, floor)) ours = SplitPace.clocks(id, b);
        }
        SplitPace.Clocks odin = null;
        if (i < PB_NAMES.size()) {
            Long ms = cfg().splitPbs == null ? null : cfg().splitPbs.get(floor + ":" + EngineerLook.strip(PB_NAMES.get(i)));
            if (ms != null) {
                SplitPace.Clocks c = secs(ms / 1000.0);
                boolean ok = id != null ? SubSplitGrades.canBeBest(id, SubSplitGrades.value(id, c.ms(), c.ticks()), floor) : c.ms() >= 1000;
                if (ok) odin = c;
            }
        }
        if (ours == null) return odin;
        if (odin == null) return ours;
        return odin.ms() < ours.ms() ? odin : ours;
    }

    static SplitPace.Clocks darkGreen(int i, boolean master) {
        return master || i >= SplitPace.ORDER.size() ? null : SplitPace.ref(SplitPace.ORDER.get(i));
    }

    private static List<Double> targets(EngineerLook.Place place, boolean master) {
        if (place != EngineerLook.Place.FLOOR7) return null;
        List<Double> out = new ArrayList<>();
        int n = EngineerLook.targetLabels(master).size();
        for (int i = 0; i < n; i++) {
            SplitPace.Clocks c = typical(i, master);
            if (c == null) c = target(i, master);
            if (c == null) c = darkGreen(i, master);
            out.add(c == null ? null : c.ms() / 1000.0);
        }
        return out;
    }

    static void fillFromPbs(boolean master) {
        int filled = 0;
        int n = EngineerLook.targetLabels(master).size();
        for (int i = 0; i < n; i++) {
            SplitPace.Clocks pb = pb(i, master);
            if (pb == null) continue;
            setBox(master, i, EngineerLook.formatSeconds(pb.ms() / 1000.0));
            filled++;
        }
        TeslaMapsConfig.save();
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§8[§6ADM§8] " + (filled == 0
                ? "§7No " + (master ? "M7" : "F7") + " PBs yet - finish a run first."
                : "§aFilled §f" + filled + " §atargets from your " + (master ? "M7" : "F7") + " PBs.")));
    }

    // ------------------------------------------------------------------ drawing

    private static EngineerLook.Options options() {
        TeslaMapsConfig c = cfg();
        return new EngineerLook.Options(c.admSplitsBossEntry, c.admSplitsShow0, c.admSplitsTickTime, c.admSplitsEnterAfterEntry,
                c.admSplitsShowPace, c.admSplitsShowLag);
    }

    private static EngineerLook.Place place() {
        if (!DungeonManager.isInDungeon()) return EngineerLook.Place.OTHER;
        DungeonFloor f = DungeonManager.getCurrentFloor();
        if (f == null) return EngineerLook.Place.DUNGEON;
        return f.getLevel() == 7 ? EngineerLook.Place.FLOOR7 : EngineerLook.Place.DUNGEON;
    }

    private static boolean master() {
        DungeonFloor f = DungeonManager.getCurrentFloor();
        return f != null && f.isMaster();
    }

    private static String grade(String name, long ms, long ticks, boolean over, boolean master) {
        String id = SubSplitGrades.MAIN_SPLITS.get(EngineerLook.strip(name));
        if (id == null) return null;
        String floor = master ? "M7" : "F7";
        long value = SubSplitGrades.value(id, ms, ticks);
        DungeonSplits.recordBest(floor, id, value, over);
        String c = SubSplitGrades.colour(id, value, over, DungeonSplits.bestOf(floor, id), floor, "");
        return c.isEmpty() ? null : c;
    }

    private static List<EngineerLook.Row> exampleRows() {
        List<String> names = new ArrayList<>(List.of("§2Blood Open", "§bBlood Clear", "§dPortal Entry"));
        names.addAll(FLOOR7_NAMES);
        names.add("§1Total");
        double[] secs = {59.0, 30.1, 4.2, 25.5, 45.9, 35.0, 3.1, 0.0, 0.0, 0.0};
        List<EngineerLook.Row> out = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) out.add(new EngineerLook.Row(names.get(i), (long) (secs[i] * 1000), (long) (secs[i] * 20) - (secs[i] > 0 ? 1 : 0), i == 6));
        return out;
    }

    private static final int LINE = 9;

    private static void at(GuiGraphicsExtractor g, int x, int y, float s, java.util.function.Supplier<int[]> draw) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s, s);
        try { draw.get(); } finally { g.pose().popMatrix(); }
    }

    private static int[] lastSize = {120, 90};

    private static int[] previewSize() { return lastSize; }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (Minecraft.getInstance().options.hideGui || failed) return;
        try {
            if (place() != EngineerLook.Place.OTHER) at(g, displayHud.x(), displayHud.y(), displayHud.scale(), () -> drawDisplay(g, false));
            if (cfg().admSplitsCurrent) at(g, currentHud.x(), currentHud.y(), currentHud.scale(), () -> drawCurrent(g, false));
        } catch (Throwable t) {
            failed = true;
            com.teslamaps.TeslaMaps.LOGGER.error("[adm] splits HUD failed - off for this session", t);
        }
    }

    private static int[] drawDisplay(GuiGraphicsExtractor g, boolean example) {
        int[] wh = engineer() ? drawEngineer(g, example) : drawOdin(g, example);
        if (example && wh[0] > 0) lastSize = wh;
        return wh;
    }

    private static int[] drawEngineer(GuiGraphicsExtractor g, boolean example) {
        boolean master = example ? "M7".equals(cfg().admSplitsPaceFloor) : master();
        EngineerLook.Place place = example ? EngineerLook.Place.FLOOR7 : place();
        List<EngineerLook.Row> rows = example ? exampleRows() : currentRows();
        EngineerLook.Options opts = options();
        boolean live = !example && place == EngineerLook.Place.FLOOR7;
        SplitPace.Clocks pace = live && !master && DungeonSplits.tracking() ? DungeonSplits.pace() : null;
        List<EngineerLook.Line> lines = EngineerLook.lines(rows, opts, place, master, targets(place, master),
                live ? (n, ms, t, over) -> grade(n, ms, t, over, master) : null,
                pace == null ? null : new long[]{pace.ms(), pace.ticks()},
                live && DungeonSplits.tracking() ? DungeonSplits.lag() : null);
        if (lines.isEmpty()) return new int[]{0, 0};
        Font font = Minecraft.getInstance().font;
        if (!cfg().admSplitsFixedWidth) {
            int width = 0;
            for (int i = 0; i < lines.size(); i++) {
                String s = EngineerLook.text(lines.get(i));
                g.text(font, s, 0, i * LINE, 0xFFFFFFFF, true);
                width = Math.max(width, font.width(s));
            }
            return new int[]{width, lines.size() * LINE};
        }
        int nameW = 0;
        for (String l : EngineerLook.allLabels(rows, opts, place, master)) nameW = Math.max(nameW, font.width(l));
        String arrow = " §b> ";
        int arrowW = font.width(arrow);
        String widest = opts.showTicks() ? "59m 59.9s §8(§759m 59.9s§8)" : "59m 59.9s";
        int restW = font.width(widest);
        for (EngineerLook.Line l : lines) restW = Math.max(restW, font.width(l.colour() + l.time() + (l.ticks() != null ? " §8(§7" + l.ticks() + "§8)" : "")));
        for (int i = 0; i < lines.size(); i++) {
            EngineerLook.Line l = lines.get(i);
            int y = i * LINE;
            g.text(font, l.label(), nameW - font.width(l.label()), y, 0xFFFFFFFF, true);
            g.text(font, arrow, nameW, y, 0xFFFFFFFF, true);
            g.text(font, l.colour() + l.time() + (l.ticks() != null ? " §8(§7" + l.ticks() + "§8)" : ""), nameW + arrowW, y, 0xFFFFFFFF, true);
        }
        return new int[]{nameW + arrowW + restW, lines.size() * LINE};
    }

    private static String formatTime(long ms) {
        double s = ms / 1000.0;
        long h = (long) (s / 3600), m = (long) (s % 3600 / 60);
        double sec = s % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (m > 0 || h > 0) sb.append(m).append("m ");
        sb.append(String.format(Locale.ROOT, "%.2f", sec).replaceAll("\\.?0+$", "")).append("s");
        return sb.toString();
    }

    private static int[] drawOdin(GuiGraphicsExtractor g, boolean example) {
        Font font = Minecraft.getInstance().font;
        boolean ticksOn = cfg().admSplitsTickTime;
        int timeWidth = 75 + (ticksOn ? 20 : -20);
        List<EngineerLook.Row> rows = example ? exampleRows() : currentRows();
        if (rows.isEmpty()) return new int[]{0, 0};
        List<EngineerLook.Row> segments = rows.subList(0, rows.size() - 1);
        int labelWidth = 0;
        for (EngineerLook.Row r : segments) labelWidth = Math.max(labelWidth, font.width(r.name()));
        int totalWidth = labelWidth + 4 + timeWidth + 2;
        for (int i = 0; i < segments.size(); i++) {
            EngineerLook.Row r = segments.get(i);
            if (r.ms() == 0L && !cfg().admSplitsShow0) continue;
            String time = formatTime(r.ms());
            g.text(font, r.name(), 0, i * 9, 0xFFFFFFFF, true);
            String display = ticksOn ? time + " §8(§7" + String.format(Locale.ROOT, "%.2f", r.ticks() / 20f) + "§8)" : time;
            int tx = cfg().admSplitsFixedWidth ? labelWidth + 4 + timeWidth - font.width(display) : labelWidth + 4;
            g.text(font, display, tx, i * 9, 0xFFFFFFFF, true);
        }
        if (cfg().admSplitsBossEntry && rows.size() > 3) {
            int y = segments.size() * 9;
            g.text(font, "§9Boss Entry", 0, y, 0xFFFFFFFF, true);
            long ms = 0, tk = 0;
            for (int k = 0; k < Math.min(3, segments.size()); k++) { ms += segments.get(k).ms(); tk += segments.get(k).ticks(); }
            String display = ticksOn ? formatTime(ms) + " §8(§7" + String.format(Locale.ROOT, "%.2f", tk / 20f) + "§8)" : formatTime(ms);
            int tx = cfg().admSplitsFixedWidth ? labelWidth + 4 + timeWidth - font.width(display) : labelWidth + 4;
            g.text(font, display, tx, y, 0xFFFFFFFF, true);
        }
        return new int[]{totalWidth, 9 * (rows.size() + (cfg().admSplitsBossEntry ? 1 : 0))};
    }

    private static int[] drawCurrent(GuiGraphicsExtractor g, boolean example) {
        Font font = Minecraft.getInstance().font;
        if (!engineer()) {
            if (example) { g.text(font, "§70s", 0, 0, 0xFFFFFFFF, true); return new int[]{font.width("§70s"), 9}; }
            for (EngineerLook.Row r : currentRows()) {
                if (!r.current()) continue;
                String t = "§7" + String.format(Locale.ROOT, "%.2f", r.ticks() / 20f) + "s";
                int w = font.width(t) + 2;
                g.text(font, t, 0, 0, 0xFFFFFFFF, true);
                return new int[]{w, 9};
            }
            return new int[]{0, 0};
        }
        EngineerLook.Place place = example ? EngineerLook.Place.FLOOR7 : place();
        EngineerLook.Line line = EngineerLook.currentLine(example ? exampleRows() : currentRows(), options(), place, !example && master());
        if (line == null) return new int[]{0, 0};
        String s = EngineerLook.text(line);
        g.text(font, s, 0, 0, 0xFFFFFFFF, true);
        return new int[]{font.width(s) + 2, LINE};
    }
}
