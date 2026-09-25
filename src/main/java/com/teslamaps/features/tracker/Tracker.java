package com.teslamaps.features.tracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.SkyblockItems;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * Counts things per session and all-time, prices them, and renders a HUD block.
 * Keys are SkyBlock item ids (priced through {@link PriceManager}) or free labels
 * (priced 0 unless {@link #fixedValue} says otherwise). Coins go under {@link #COINS}.
 *
 * Only the all-time counts are saved, to {@code config/teslamaps/trackers/<id>.json}.
 */
public final class Tracker {

    public enum View { SESSION, TOTAL }

    public static final String COINS = "SKYBLOCK_COIN";

    private static final Map<String, Tracker> ALL = new LinkedHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, Long>>() {}.getType();
    private static final long SAVE_DELAY = 30_000L;

    public final String id;
    private String title;
    private final Map<String, Long> session = new LinkedHashMap<>();
    private final Map<String, Long> total = new LinkedHashMap<>();
    private final Map<String, Double> fixed = new LinkedHashMap<>();
    private final Map<String, String> names = new LinkedHashMap<>();
    private IntSupplier priceSource = () -> PriceManager.SOURCE_BAZAAR_SELL;
    private IntSupplier idleSeconds = () -> 60;
    private int maxRows = 12;
    private View view = View.SESSION;

    private long sessionStart, lastActivity, activeMillis;
    private boolean loaded, dirty;
    private long dirtySince;
    private List<String> lines = List.of();
    private int ticks;
    private boolean changed = true;

    private Tracker(String id, String title) {
        this.id = id;
        this.title = title;
    }

    /** One tracker per id; calling again returns the same one. */
    public static Tracker create(String id, String title) {
        return ALL.computeIfAbsent(id, k -> new Tracker(id, title));
    }

    public static Tracker get(String id) {
        return ALL.get(id);
    }

    public static Collection<Tracker> all() {
        return ALL.values();
    }

    /** {@code PriceManager.SOURCE_*}; usually a config dropdown. */
    public Tracker priceSource(IntSupplier source) { this.priceSource = source; return this; }
    /** A break longer than this does not count towards the hourly rate. */
    public Tracker idleSeconds(IntSupplier seconds) { this.idleSeconds = seconds; return this; }
    public Tracker maxRows(int rows) { this.maxRows = rows; return this; }
    public Tracker title(String t) { if (!t.equals(title)) { title = t; changed = true; } return this; }
    /** Value of one unit of a key no price source knows (a label, a mob kill, ...). */
    public Tracker fixedValue(String key, double coins) { fixed.put(key, coins); return this; }
    /** Display name for a key that is not an item id. */
    public Tracker name(String key, String display) { names.put(key, display); return this; }
    /** Extra line under the rows, built on rebuild. */
    public Tracker extra(java.util.function.Supplier<String> line) { this.extra = line; return this; }
    private java.util.function.Supplier<String> extra;

    public View view() { return view; }
    public void view(View v) { this.view = v; rebuild(); }
    public void toggleView() { view(view == View.SESSION ? View.TOTAL : View.SESSION); }

    public void add(String key, long amount) {
        if (key == null || amount == 0) return;
        load();
        session.merge(key, amount, Long::sum);
        total.merge(key, amount, Long::sum);
        markDirty();
        markActivity();
        changed = true;
        if (!COINS.equals(key)) {
            PriceManager.ensureFresh();
            if (amount > 0 && fixed.get(key) == null) com.teslamaps.features.ValuableDropWarning.onGain(key, amount);
        }
    }

    public void addCoins(long coins) {
        add(COINS, coins);
    }

    public long count(String key, View v) {
        load();
        return map(v).getOrDefault(key, 0L);
    }

    /** Keeps the hourly clock running without adding anything. */
    public void markActivity() {
        long now = System.currentTimeMillis();
        if (sessionStart == 0) sessionStart = now;
        else if (now - lastActivity <= idleMillis()) activeMillis += now - lastActivity;
        lastActivity = now;
    }

    public boolean idle() {
        return sessionStart == 0 || System.currentTimeMillis() - lastActivity > idleMillis();
    }

    /** Time actually spent, breaks excluded. */
    public long activeMillis() {
        if (sessionStart == 0) return 0;
        long open = System.currentTimeMillis() - lastActivity;
        return activeMillis + (open <= idleMillis() ? open : 0);
    }

    public void resetSession() {
        session.clear();
        sessionStart = lastActivity = activeMillis = 0;
        rebuild();
    }

    public void resetTotal() {
        load();
        total.clear();
        markDirty();
        save();
        rebuild();
    }

    public double value(String key, long amount) {
        if (COINS.equals(key)) return amount;
        Double f = fixed.get(key);
        if (f != null) return f * amount;
        Double p = PriceManager.getPriceOrNull(key, priceSource.getAsInt());
        return p == null ? 0 : p * amount;
    }

    public double profit(View v) {
        load();
        double sum = 0;
        for (var e : map(v).entrySet()) sum += value(e.getKey(), e.getValue());
        return sum;
    }

    /** Session profit per active hour, 0 before the first minute. */
    public double perHour() {
        long ms = activeMillis();
        return ms < 60_000 ? 0 : profit(View.SESSION) * 3_600_000.0 / ms;
    }

    /** Idle clock, deferred save and the HUD lines. Call from the feature's registry tick. */
    public void tick() {
        load();
        if (dirty && System.currentTimeMillis() - dirtySince > SAVE_DELAY) save();
        // the clock and prices move on their own, a second is fine for those
        if (changed || ++ticks >= 20) {
            ticks = 0;
            changed = false;
            rebuild();
        }
    }

    public List<String> lines() {
        return lines;
    }

    private void rebuild() {
        Map<String, Long> m = map(view);
        List<Map.Entry<String, Long>> rows = new ArrayList<>(m.entrySet());
        rows.sort((a, b) -> Double.compare(value(b.getKey(), b.getValue()), value(a.getKey(), a.getValue())));

        List<String> out = new ArrayList<>();
        out.add(title + " §7(" + (view == View.SESSION ? "Session" : "Total") + ")");
        int shown = 0;
        for (var e : rows) {
            if (shown++ >= maxRows) break;
            double v = value(e.getKey(), e.getValue());
            if (COINS.equals(e.getKey())) {
                out.add(display(COINS) + "§7: §6" + ItemPrice.format(v));
                continue;
            }
            out.add("§f" + String.format(java.util.Locale.ROOT, "%,d", e.getValue()) + "x " + display(e.getKey())
                    + (v > 0 ? " §7- §6" + ItemPrice.format(v) : ""));
        }
        if (rows.size() > maxRows) out.add("§7+" + (rows.size() - maxRows) + " more");
        String ex = extra == null ? null : extra.get();
        if (ex != null) out.add(ex);
        out.add("§eProfit: §6" + ItemPrice.format(profit(view)));
        if (view == View.SESSION) {
            out.add("§eper hour: §6" + ItemPrice.format(perHour()) + (idle() && sessionStart > 0 ? " §7(paused)" : ""));
            out.add("§eTime: §f" + formatTime(activeMillis()));
        }
        lines = out;
    }

    private String display(String key) {
        String n = names.get(key);
        if (n != null) return n;
        if (COINS.equals(key)) return "§6Coins";
        String item = SkyblockItems.coloredName(key);
        return item != null ? item : key;
    }

    private static String formatTime(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format("%dh %02dm", s / 3600, s / 60 % 60) : String.format("%dm %02ds", s / 60, s % 60);
    }

    public void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale) {
        List<String> l = lines;
        if (l.isEmpty()) return;
        int[] size = size(mc.font);
        int pad = HudStyle.padding();
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -pad, -pad, size[0] + pad * 2, size[1] + pad * 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * (mc.font.lineHeight + 1), 0xFFFFFFFF);
        pose.popMatrix();
    }

    /** Unscaled content size, for HudEditElement. */
    public int[] size(Font font) {
        int w = 0;
        for (String s : lines) w = Math.max(w, font.width(s));
        return new int[] { w, lines.size() * (font.lineHeight + 1) };
    }

    private Map<String, Long> map(View v) {
        return v == View.SESSION ? session : total;
    }

    private long idleMillis() {
        return Math.max(10, idleSeconds.getAsInt()) * 1000L;
    }

    private void markDirty() {
        if (!dirty) dirtySince = System.currentTimeMillis();
        dirty = true;
    }

    private Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("trackers").resolve(id + ".json");
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        Path p = path();
        if (!Files.exists(p)) return;
        try (Reader r = Files.newBufferedReader(p)) {
            Map<String, Long> m = GSON.fromJson(r, TYPE);
            if (m != null) total.putAll(m);
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[Tracker] {} load failed: {}", id, e.toString());
        }
    }

    public void save() {
        if (!loaded) return;
        dirty = false;
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p)) {
                GSON.toJson(total, TYPE, w);
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[Tracker] {} save failed: {}", id, e.toString());
        }
    }

    public static void saveAll() {
        for (Tracker t : ALL.values()) if (t.dirty) t.save();
    }
}
