package com.teslamaps.dungeon;

// Ported from legitcatmod's Rush.kt (RushSplits)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.map.CheckmarkState;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.map.RoomType;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class RushSplits {

    private static final Pattern WITHER_KEY = Pattern.compile("^(?:\\[[^]]+] )?\\w{1,16} has obtained Wither Key!$|^A Wither Key was picked up!$");
    private static final Pattern BLOOD_KEY = Pattern.compile("^(?:\\[[^]]+] )?\\w{1,16} has obtained Blood Key!$|^A Blood Key was picked up!$");
    private static final Pattern WITHER_DOOR = Pattern.compile("^\\w{1,16} opened a WITHER door!$");
    private static final String BLOOD_DOOR = "The BLOOD DOOR has been opened!";
    private static final String MORT = "[NPC] Mort: Here, I found this map when I first entered the dungeon.";

    private static final String[] HEAD = {"§6Room", "§fClear", "§7Key", "§8Door"};
    private static final String[][] SAMPLE = {HEAD, {"§6Layers", "§f10.00", "§71.00", "§80.40"}, {"§6Mines", "§f9.00", "§72.30", "§80.30"}};
    private static final int LINE = 10;

    private static final class Timer {
        final long start;
        long end = -1;
        Timer(long start) { this.start = start; }
        void stop(long now) { if (end < 0) end = now; }
        String text(long now) { return String.format(Locale.ROOT, "%.2f", ((end < 0 ? now : end) - start) / 1000.0); }
    }

    private static final class RushRoom {
        final DungeonRoom room;
        final Timer cleared;
        Timer key;
        Timer door;
        RushRoom(DungeonRoom room, long start) { this.room = room; this.cleared = new Timer(start); }
    }

    private static final List<RushRoom> rooms = new ArrayList<>();
    private static RushRoom current = null;
    private static boolean started = false, done = false;
    private static long nextStart = 0;
    private static Object lastLevel = null;

    private static volatile String[][] table = null;
    private static volatile int[] colX = null;
    private static HudEditElement hud;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("rushSplits", "Rush Splits").ctx(HudEditElement.HudCtx.DUNGEON).section("Score & Splits")
                .enabled(() -> cfg().rushSplits)
                .size(() -> width(SAMPLE, columns(SAMPLE)), () -> SAMPLE.length * LINE)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, columns(SAMPLE)));
        Feature.of("RushSplits").section("Score & Splits").when(() -> cfg().rushSplits)
                .tick(RushSplits::tick)
                .serverChat(RushSplits::onChat)
                .hud("rushsplits", RushSplits::render)
                .config("Score & Splits", b -> {
                    b.toggle("Rush Splits (Clear / Key / Door)", () -> cfg().rushSplits, v -> cfg().rushSplits = v);
                    b.classFilter("  Only as Class", cfg().rushSplitsClasses);
                });
    }

    private static void reset() {
        rooms.clear();
        current = null;
        started = false;
        done = false;
        table = null;
    }

    private static void onChat(String raw) {
        if (!DungeonManager.isInDungeon()) return;
        String msg = StringUtil.stripTrim(raw);
        long now = System.currentTimeMillis();
        if (msg.equals(MORT)) {
            reset();
            started = true;
            nextStart = now;
            return;
        }
        if (!started) return;
        boolean key = WITHER_KEY.matcher(msg).matches() || BLOOD_KEY.matcher(msg).matches();
        boolean bloodDoor = msg.equals(BLOOD_DOOR);
        boolean door = bloodDoor || WITHER_DOOR.matcher(msg).matches();
        if (bloodDoor) done = true;
        if (key) {
            if (current == null) return;
            if (current.key != null) current.key.stop(now);
            else {
                current.cleared.stop(now);
                current.key = new Timer(now);
                current.key.stop(now);
            }
            current.door = new Timer(now);
        } else if (door) {
            if (current != null && current.door != null) current.door.stop(now);
            current = null;
            nextStart = now;
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        if (!started) return;
        long now = System.currentTimeMillis();
        if (current == null && !done) {
            DungeonRoom r = DungeonManager.getCurrentRoom();
            if (r != null && r.getType() != RoomType.ENTRANCE && r.getType() != RoomType.BLOOD && !listed(r)) {
                current = new RushRoom(r, nextStart);
                rooms.add(current);
            }
        }
        if (current != null && current.cleared.end < 0) {
            CheckmarkState st = current.room.getCheckmarkState();
            if (st == CheckmarkState.WHITE || st == CheckmarkState.GREEN) {
                current.cleared.stop(now);
                if (current.key == null) current.key = new Timer(now);
            }
        }
        if (rooms.isEmpty()) {
            table = null;
            return;
        }
        String[][] t = new String[rooms.size() + 1][];
        t[0] = HEAD;
        for (int i = 0; i < rooms.size(); i++) {
            RushRoom rr = rooms.get(i);
            String name = rr.room.getName() == null || "Unknown".equals(rr.room.getName()) ? "???" : rr.room.getName();
            t[i + 1] = new String[]{"§6" + name, "§f" + rr.cleared.text(now),
                    rr.key != null ? "§7" + rr.key.text(now) : "", rr.door != null ? "§8" + rr.door.text(now) : ""};
        }
        colX = columns(t);
        table = t;
    }

    private static boolean listed(DungeonRoom r) {
        for (RushRoom rr : rooms) if (rr.room == r) return true;
        return false;
    }

    private static int[] columns(String[][] t) {
        Font f = Minecraft.getInstance().font;
        int[] x = new int[5];
        for (int c = 0; c < 4; c++) {
            int w = 0;
            for (String[] row : t) w = Math.max(w, f.width(row[c]));
            x[c + 1] = x[c] + w + 5;
        }
        return x;
    }

    private static int width(String[][] t, int[] x) {
        return x[4] - 5;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String[][] t = table;
        int[] x = colX;
        if (t == null || x == null || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || !DungeonManager.isInDungeon()) return;
        if (!cfg().rushSplitsClasses.allowsLocal()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), t, x);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String[][] t, int[] cols) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, -2, -2, width(t, cols) + 4, t.length * LINE + 2);
        for (int r = 0; r < t.length; r++) {
            for (int c = 0; c < 4; c++) {
                if (!t[r][c].isEmpty()) g.text(mc.font, t[r][c], cols[c], r * LINE, 0xFFFFFFFF, true);
            }
        }
        g.pose().popMatrix();
    }
}
