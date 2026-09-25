package com.teslamaps.features;

// Ported from SkyHanni's features/inventory/caketracker/CakeTracker.kt (slimmed: no scroll/click, prices in the panel)
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.ProfiledFile;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CakeTracker {

    private static final Pattern NAME = Pattern.compile("^New Year Cake \\(Year ([\\d,]+)\\)$");
    private static final Pattern PURCHASED = Pattern.compile("^You purchased New Year Cake \\(Year ([\\d,]+)\\) for [\\d,]+ coins!$");
    private static final String CLAIMED = "You claimed a New Year Cake!";
    private static final Pattern CONTAINER = Pattern.compile(
            "^(Ender Chest \\(\\d{1,2}/\\d{1,2}\\)|.*Backpack \\(Slot #\\d{1,2}\\)|New Year Cake Bag|(Large )?Chest)$");
    private static final Pattern AUCTION = Pattern.compile("^(Auctions Browser|Auctions: \".*)$");
    private static final long SB_EPOCH = 1560275700000L, YEAR_MS = 446_400_000L, DAY_MS = 1_200_000L;
    private static final int MAX_ROWS = 18;
    private static final Type TYPE = new TypeToken<Set<Integer>>() {}.getType();
    private static final ProfiledFile FILE = new ProfiledFile("cakes.json", null);
    private static final String[] SHOW = {"Missing", "Owned"};

    private static Feature feature;
    private static HudEditElement hud;
    private static Set<Integer> owned = new HashSet<>();
    private static boolean loaded;

    private static Object lastScreen;
    private static long openedAt;
    private static boolean cakeMenu, auction;
    private static Set<Integer> inMenu = new HashSet<>();
    private static Map<Integer, Integer> highlights = new HashMap<>();
    private static List<String> lines = List.of();
    private static int linesWidth;
    private static boolean dirty = true;
    private static int ticks;

    private CakeTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("cakeTracker", "New Year Cake Tracker").section("Inventory")
                .enabled(() -> cfg().cakeTracker)
                .size(() -> Math.max(100, linesWidth), () -> Math.max(20, lines.size() * 10))
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, lines.isEmpty() ? List.of("§c§lNew §f§lYear §c§lCake §f§lTracker", "§fYears §c1§f-§c42") : lines));
        feature = Feature.of("CakeTracker").section("Inventory").when(() -> cfg().cakeTracker)
                .tick(CakeTracker::tick)
                .serverChat(CakeTracker::onChat)
                .hudForeground("caketracker", CakeTracker::render)
                .config("Inventory", b -> {
                    b.label("New Year Cakes");
                    b.toggle("New Year Cake Tracker", () -> cfg().cakeTracker, v -> cfg().cakeTracker = v)
                            .desc("Remembers the cake years you own (cake bag, chests, backpacks, purchases). Panel in those menus and the AH (/tmap gui)");
                    b.dropdown("  Show", SHOW, () -> cfg().cakeTrackerShow, v -> { cfg().cakeTrackerShow = v; dirty = true; });
                    b.toggle("  Mark Cakes In AH", () -> cfg().cakeTrackerAhHighlight, v -> cfg().cakeTrackerAhHighlight = v)
                            .desc("Green = owned, red = missing");
                    b.button("  Reset Owned Cakes", () -> { ensure(); owned.clear(); save(); dirty = true; });
                });
        SbProfile.onChange(() -> { loaded = false; dirty = true; });
    }

    static int currentYear() {
        long el = System.currentTimeMillis() - SB_EPOCH;
        int year = (int) (el / YEAR_MS) + 1;
        long day = (el % YEAR_MS) / DAY_MS;
        return day >= 11 * 31 + 28 ? year : year - 1;
    }

    private static int year(ItemStack st) {
        if (st.isEmpty()) return -1;
        Matcher m = NAME.matcher(StringUtil.stripTrim(st.getHoverName().getString()));
        if (!m.matches()) return -1;
        try { return Integer.parseInt(m.group(1).replace(",", "")); } catch (NumberFormatException e) { return -1; }
    }

    private static void add(int y) {
        if (y <= 0) return;
        ensure();
        if (owned.add(y)) { save(); dirty = true; }
    }

    private static void remove(int y) {
        ensure();
        if (owned.remove(y)) { save(); dirty = true; }
    }

    private static void onChat(String text) {
        String t = StringUtil.stripTrim(text);
        if (t.equals(CLAIMED)) { add(currentYear()); return; }
        if (!t.startsWith("You purchased New Year Cake")) return;
        Matcher m = PURCHASED.matcher(t);
        if (m.matches()) add(Integer.parseInt(m.group(1).replace(",", "")));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!SkyblockUtils.isInSkyblock()) return;
        ensure();
        if (++ticks % 40 == 0) {
            for (int i = 0; i < 36; i++) add(year(mc.player.getInventory().getItem(i)));
            dirty = true;
        }
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            lastScreen = null;
            cakeMenu = auction = false;
            highlights = Map.of();
            inMenu.clear();
        } else {
            if (cs != lastScreen) {
                lastScreen = cs;
                openedAt = System.currentTimeMillis();
                String title = StringUtil.stripTrim(cs.getTitle().getString());
                cakeMenu = CONTAINER.matcher(title).matches();
                auction = AUCTION.matcher(title).matches();
                inMenu.clear();
                highlights = Map.of();
            }
            if (System.currentTimeMillis() - openedAt >= 500 && ticks % 5 == 0) scanMenu(cs);
        }
        if (dirty) rebuild();
    }

    private static void scanMenu(AbstractContainerScreen<?> cs) {
        if (cakeMenu) {
            Set<Integer> now = new HashSet<>();
            for (Slot s : cs.getMenu().slots) {
                if (s.container instanceof Inventory) continue;
                int y = year(s.getItem());
                if (y > 0) now.add(y);
            }
            for (int y : now) if (!inMenu.contains(y)) add(y);
            for (int y : inMenu) if (!now.contains(y)) remove(y);
            inMenu = now;
        }
        if (auction && cfg().cakeTrackerAhHighlight) {
            Map<Integer, Integer> hl = new HashMap<>();
            List<Slot> slots = cs.getMenu().slots;
            for (int i = 0; i < slots.size(); i++) {
                Slot s = slots.get(i);
                if (s.container instanceof Inventory) continue;
                int y = year(s.getItem());
                if (y > 0) hl.put(i, owned.contains(y) ? 0x55FF55 : 0xFF5555);
            }
            highlights = hl;
        }
    }

    private static double price(int year) {
        Double p = PriceManager.lowestBinOrNull("NEW_YEAR_CAKE+" + year);
        return p == null ? 0 : p;
    }

    private static void rebuild() {
        dirty = false;
        int cur = currentYear();
        boolean missing = !"Owned".equals(cfg().cakeTrackerShow);
        TreeSet<Integer> list = new TreeSet<>();
        if (missing) { for (int y = 1; y <= cur; y++) if (!owned.contains(y)) list.add(y); }
        else list.addAll(owned);

        List<String> out = new ArrayList<>();
        out.add("§c§lNew §f§lYear §c§lCake §f§lTracker");
        int missingCount = 0;
        double cost = 0;
        for (int y = 1; y <= cur; y++) if (!owned.contains(y)) { missingCount++; cost += price(y); }
        out.add("§aHave: " + owned.size() + " §7| §cMissing: " + missingCount);
        if (missingCount > 0 && cost > 0) out.add("§7Missing cost: §6" + ItemPrice.format(cost));
        if (list.isEmpty()) {
            out.add(missing ? "§a§lAll cakes owned!" : "§c§lNo cakes yet");
        } else {
            String col = missing ? "§c" : "§a";
            List<String> ranges = new ArrayList<>();
            int start = -1, end = -1;
            for (int y : list) {
                if (start < 0) { start = end = y; continue; }
                if (y == end + 1) { end = y; continue; }
                ranges.add(range(col, start, end, missing));
                start = end = y;
            }
            ranges.add(range(col, start, end, missing));
            for (int i = 0; i < ranges.size() && i < MAX_ROWS; i++) out.add(ranges.get(i));
            if (ranges.size() > MAX_ROWS) out.add("§7+" + (ranges.size() - MAX_ROWS) + " more");
        }
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (String s : out) w = Math.max(w, mc.font.width(s));
        lines = out;
        linesWidth = w;
    }

    private static String range(String col, int a, int b, boolean withPrice) {
        String s = a == b ? "§fYear " + col + a : "§fYears " + col + a + "§f-" + col + b;
        if (withPrice && a == b) {
            double p = price(a);
            if (p > 0) s += " §7" + ItemPrice.format(p);
        }
        return s;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || (!cakeMenu && !auction) || lines.isEmpty()) return;
        draw(g, Minecraft.getInstance(), hud.x(), hud.y(), hud.scale(), lines);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l) {
        int w = 0;
        if (l == lines) w = linesWidth;
        else for (String s : l) w = Math.max(w, mc.font.width(s));
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, l.size() * 10 + 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * 10, 0xFFFFFFFF, true);
        pose.popMatrix();
    }

    /** From ContainerOverlays. */
    public static void renderSlots(GuiGraphicsExtractor ctx) {
        Map<Integer, Integer> hl = highlights;
        if (hl.isEmpty() || !auction || feature == null || !feature.active() || !cfg().cakeTrackerAhHighlight) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        List<Slot> slots = cs.getMenu().slots;
        for (Map.Entry<Integer, Integer> e : hl.entrySet()) {
            if (e.getKey() >= slots.size()) continue;
            Slot s = slots.get(e.getKey());
            int sx = acc.getX() + s.x, sy = acc.getY() + s.y;
            ctx.fill(sx, sy, sx + 16, sy + 16, 0x66000000 | e.getValue());
        }
    }

    private static void ensure() {
        if (loaded && !FILE.stale()) return;
        loaded = true;
        owned = new HashSet<>();
        Path p = FILE.path();
        if (p == null) return;
        try {
            if (Files.exists(p)) {
                Set<Integer> s = new Gson().fromJson(Files.readString(p), TYPE);
                if (s != null) owned.addAll(s);
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("cakes.json: {}", e.toString());
        }
        dirty = true;
    }

    private static void save() {
        Path p = FILE.path();
        if (p == null) return;
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, new Gson().toJson(new TreeSet<>(owned)));
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("cakes.json: {}", e.toString());
        }
    }
}
