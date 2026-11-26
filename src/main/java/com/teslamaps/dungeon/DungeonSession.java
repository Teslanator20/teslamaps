package com.teslamaps.dungeon;

// SkyblockExtras "Show Dungeon Counter" / "Cata EXP Tracker" / "Show Run Overview" / "Show Frag Counter"
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DungeonSession {

    private static final String SECTION = "Score & Splits";
    private static final int ROW = 10;
    private static final Pattern TEAM_SCORE = Pattern.compile("^\\s*Team Score: (\\d+) \\((.{1,2})\\).*$");
    private static final Pattern DEFEATED = Pattern.compile("^\\s*☠ Defeated .+ in 0?([\\dhms ]+?)\\s*(?:\\(NEW RECORD!\\))?$");
    private static final Pattern CATA_XP = Pattern.compile("^\\s*\\+([\\d,.]+) Catacombs Experience.*$");
    private static final Pattern TIME_PART = Pattern.compile("(\\d+)([hms])");
    private static final List<String> SAMPLE = List.of(
            "§bDungeon Session §8(§f12 runs§8)",
            "§eF7§7: §f4 §8(avg §a5:48§8)",
            "§cM7§7: §f8 §8(avg §a6:10§8)",
            "§7Cata XP: §b1.2M §8(§b410k/h§8)",
            "§7Fragments: §d5");

    private static final class FloorStats { int runs; long totalSec; int timed; }

    private static final Map<String, FloorStats> floors = new LinkedHashMap<>();
    private static int runs;
    private static double xp;
    private static long sessionStart;
    private static int frags;
    private static int fragBaseline = -1;
    private static Object fragLevel;

    private static String pendingFloor;
    private static int pendingScore = -1;
    private static String pendingRank;
    private static int pendingSec = -1;
    private static double pendingXp;
    private static long overviewAt;

    private static volatile List<String> lines = List.of();
    private static int linesWidth;
    private static int tickCounter;
    private static HudEditElement el;

    private DungeonSession() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        el = HudEditElement.of("dungeonSession", "Dungeon Session").ctx(HudEditElement.HudCtx.ANY).section(SECTION)
                .enabled(() -> cfg().dungeonSession)
                .size(() -> width(SAMPLE), () -> SAMPLE.size() * ROW)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, width(SAMPLE)));
        Feature.of("DungeonSession").section(SECTION).when(() -> cfg().dungeonSession)
                .tick(DungeonSession::tick)
                .serverChat(DungeonSession::onChat)
                .hud("dungeonsession", DungeonSession::render)
                .config(SECTION, b -> {
                    b.label("Dungeon Session");
                    b.toggle("Dungeon Session Counter", () -> cfg().dungeonSession, v -> cfg().dungeonSession = v)
                            .desc("Runs per floor with average time, Cata XP and XP/h since the first run, move it in /tmap gui");
                    b.toggle("  Run Overview In Chat", () -> cfg().dungeonSessionOverview, v -> cfg().dungeonSessionOverview = v)
                            .desc("One line after every run: floor, time, rank, session runs and XP/h");
                    b.toggle("  Fragment Counter", () -> cfg().dungeonSessionFrags, v -> cfg().dungeonSessionFrags = v)
                            .desc("Boss fragments picked up this session (counted from the inventory)");
                    b.button("  Reset Session", DungeonSession::reset);
                    b.info("Cata XP needs the extra stats block (Auto Extra Stats)");
                });
    }

    public static void reset() {
        floors.clear();
        runs = 0;
        xp = 0;
        sessionStart = 0;
        frags = 0;
        fragBaseline = -1;
        pendingFloor = null;
        overviewAt = 0;
        lines = List.of();
    }

    private static void onChat(String raw) {
        if (!DungeonManager.isInDungeon()) return;
        String s = StringUtil.strip(raw);
        Matcher m;
        if ((m = TEAM_SCORE.matcher(s)).matches()) {
            pendingFloor = DungeonManager.getFloorName();
            if (pendingFloor.isEmpty() || pendingFloor.equals("UNKNOWN")) pendingFloor = "?";
            pendingScore = Integer.parseInt(m.group(1));
            pendingRank = m.group(2);
            pendingSec = -1;
            pendingXp = 0;
            long start = DungeonScore.getStartingTime();
            if (start > 0) pendingSec = (int) ((System.currentTimeMillis() - start) / 1000);
            if (sessionStart == 0) sessionStart = start > 0 ? start : System.currentTimeMillis();
            runs++;
            floors.computeIfAbsent(pendingFloor, k -> new FloorStats()).runs++;
            overviewAt = System.currentTimeMillis() + 6000;
        } else if (pendingFloor != null && (m = DEFEATED.matcher(s)).matches()) {
            int sec = parseTime(m.group(1));
            if (sec > 0) pendingSec = sec;
        } else if (pendingFloor != null && (m = CATA_XP.matcher(s)).matches()) {
            try {
                double v = Double.parseDouble(m.group(1).replace(",", ""));
                xp += v;
                pendingXp += v;
            } catch (NumberFormatException ignored) {}
            overviewAt = System.currentTimeMillis() + 500;
        }
    }

    private static int parseTime(String t) {
        Matcher m = TIME_PART.matcher(t);
        int sec = 0;
        while (m.find()) {
            int v = Integer.parseInt(m.group(1));
            sec += switch (m.group(2)) { case "h" -> v * 3600; case "m" -> v * 60; default -> v; };
        }
        return sec;
    }

    private static void finishRun() {
        FloorStats f = floors.get(pendingFloor);
        if (f != null && pendingSec > 0) { f.totalSec += pendingSec; f.timed++; }
        if (cfg().dungeonSessionOverview) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String rc = DungeonScore.rankColor(pendingRank == null ? "" : pendingRank);
                StringBuilder sb = new StringBuilder("§8[§bSession§8] §7Run #").append(runs).append(" §e").append(pendingFloor);
                if (pendingSec > 0) sb.append(" §7in §a").append(clock(pendingSec));
                sb.append(" §8| ").append(rc).append(pendingRank).append(" §7(").append(pendingScore).append(")");
                if (pendingXp > 0) sb.append(" §8| §b+").append(big(pendingXp)).append(" XP");
                sb.append(" §8| §7Session: §f").append(runs).append(" runs");
                double h = hours();
                if (xp > 0 && h > 0) sb.append(" §8| §b").append(big(xp / h)).append(" XP/h");
                mc.player.sendSystemMessage(Component.literal(sb.toString()));
            }
        }
        pendingFloor = null;
    }

    private static double hours() {
        return sessionStart == 0 ? 0 : (System.currentTimeMillis() - sessionStart) / 3_600_000.0;
    }

    private static void tick() {
        if (overviewAt > 0 && System.currentTimeMillis() >= overviewAt) {
            overviewAt = 0;
            if (pendingFloor != null) finishRun();
        }
        if (++tickCounter % 20 != 0) return;
        if (cfg().dungeonSessionFrags) countFrags();
        if (runs == 0 && !DungeonManager.isInDungeon()) { lines = List.of(); return; }
        List<String> out = new ArrayList<>();
        out.add("§bDungeon Session §8(§f" + runs + (runs == 1 ? " run" : " runs") + "§8)");
        for (Map.Entry<String, FloorStats> e : floors.entrySet()) {
            FloorStats f = e.getValue();
            String col = e.getKey().startsWith("M") ? "§c" : "§e";
            String avg = f.timed > 0 ? " §8(avg §a" + clock((int) (f.totalSec / f.timed)) + "§8)" : "";
            out.add(col + e.getKey() + "§7: §f" + f.runs + avg);
        }
        double h = hours();
        if (xp > 0) out.add("§7Cata XP: §b" + big(xp) + (h > 0 ? " §8(§b" + big(xp / h) + "/h§8)" : ""));
        if (cfg().dungeonSessionFrags) out.add("§7Fragments: §d" + frags);
        linesWidth = width(out);
        lines = out;
    }

    private static void countFrags() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.level != fragLevel) { fragLevel = mc.level; fragBaseline = -1; }
        if (!DungeonManager.isInDungeon()) return;
        int total = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            if (st.getHoverName().getString().endsWith("Fragment")) total += st.getCount();
        }
        if (fragBaseline >= 0 && total > fragBaseline) frags += total - fragBaseline;
        fragBaseline = total;
    }

    private static String clock(int sec) {
        return sec / 60 + ":" + (sec % 60 < 10 ? "0" : "") + sec % 60;
    }

    private static String big(double v) {
        if (v >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", v / 1_000_000);
        if (v >= 1_000) return String.format(Locale.ROOT, "%.0fk", v / 1_000);
        return String.valueOf((long) v);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = lines;
        if (l.isEmpty() || el == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, el.x(), el.y(), el.scale(), l, linesWidth);
    }

    private static int width(List<String> l) {
        var font = Minecraft.getInstance().font;
        int w = 0;
        for (String s : l) w = Math.max(w, font.width(s));
        return w;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l, int w) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, l.size() * ROW + 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * ROW, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
