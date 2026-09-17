package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

// Kills per hour out of the Bestiary block in the tab list. The tab reports
// "<mob> <tier>: <total kills>/<needed for the next tier>" - the left number is the
// running total, not tier progress, so kills are just the delta between two reads.
// Kills to max is nowhere in the tab, the Bestiary menu supplies it when it is opened.
public class BestiaryTracker {

    private static final Pattern ENTRY = Pattern.compile("^([A-Za-z][A-Za-z'. -]*?) (\\d+): ([\\d,]+)/([\\d,]+)$");
    private static final Pattern MAXED = Pattern.compile("^([A-Za-z][A-Za-z'. -]*?) (\\d+): MAX$");
    private static final Pattern HEADER = Pattern.compile("^[A-Za-z][A-Za-z ]*:$");

    // the tab has no kills-to-max, so the Bestiary menu fills that in when it is opened
    private static final Pattern MENU_TITLE = Pattern.compile("(?:\\(\\d+/\\d+\\) )?Bestiary ➜ .+");
    private static final Pattern MENU_NAME = Pattern.compile("^(?<name>.+?)(?: (?:[IVXLC]+|\\d+))?$");
    private static final Pattern OVERALL = Pattern.compile("Overall Progress: [\\d.]+%(?: \\(MAX!\\))?");
    private static final Pattern BAR = Pattern.compile("([\\dkKmMbB,.]+)/([\\dkKmMbB,.]+)$");

    // A gap longer than this is a break, not farming. Rare mobs need the long one: a few
    // minutes between kills is normal there, the common ones would just soak up AFK time.
    private static final long IDLE_CAP_MS = 5 * 60_000L;
    private static final long RARE_IDLE_CAP_MS = 10 * 60_000L;
    private static final long RARE_BELOW_KILLS = 1000;

    private static final class Stat {
        int tier;
        long current, needed;
        boolean max, seeded;
        long kills;
        long activeMs, lastActivity;
        long idleCap = IDLE_CAP_MS;

        void touch() {
            long now = System.currentTimeMillis();
            if (lastActivity > 0) activeMs += Math.min(now - lastActivity, idleCap);
            lastActivity = now;
        }

        long liveActiveMs() {
            if (lastActivity == 0) return 0;
            return activeMs + Math.min(System.currentTimeMillis() - lastActivity, idleCap);
        }

        double hours() { return liveActiveMs() / 3_600_000.0; }
    }

    // bundled kills-to-max for every family, the config only holds what differs
    private static Map<String, Long> defaults = null;

    private static Map<String, Long> defaults() {
        if (defaults != null) return defaults;
        Map<String, Long> loaded = new java.util.HashMap<>();
        try (var in = BestiaryTracker.class.getResourceAsStream("/assets/teslamaps/data/bestiary_max.json")) {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> e : root.entrySet()) {
                loaded.put(e.getKey(), e.getValue().getAsLong());
            }
        } catch (Exception e) {
            com.teslamaps.TeslaMaps.LOGGER.warn("[Bestiary] bestiary_max.json failed: {}", e.toString());
        }
        defaults = loaded;
        prune();
        return defaults;
    }

    /** Drops config entries the bundled file already covers, so only real corrections stay. */
    private static void prune() {
        Map<String, Long> stored = TeslaMapsConfig.get().bestiaryKillsToMax;
        if (stored.isEmpty()) return;
        boolean changed = stored.entrySet().removeIf(e -> e.getValue().equals(defaults.get(e.getKey())));
        if (changed) TeslaMapsConfig.save();
    }

    /** Config first, so a menu read can correct a stale bundled value. */
    private static Long killsToMax(String name) {
        Long stored = TeslaMapsConfig.get().bestiaryKillsToMax.get(name);
        if (stored != null) return stored;
        Long bundled = defaults().get(name);
        return bundled != null ? bundled : repoMax(name);
    }

    // the PV bestiary data (NEU repo) covers what bestiary_max.json misses (new mobs)
    private static Object repoCacheFor = null;
    private static Map<String, Long> repoCache = Map.of();

    private static boolean repoLoadAsked = false;

    private static Long repoMax(String name) {
        var cats = com.teslamaps.profileviewer.data.BestiaryRepo.categories();
        if (cats.isEmpty()) {
            if (!repoLoadAsked) {
                repoLoadAsked = true;
                com.teslamaps.profileviewer.data.BestiaryRepo.load();
            }
            cats = com.teslamaps.profileviewer.data.BestiaryRepo.categories();
        }
        if (cats != repoCacheFor) {
            Map<String, Long> map = new java.util.HashMap<>();
            java.util.ArrayDeque<com.teslamaps.profileviewer.data.BestiaryRepo.Category> queue =
                    new java.util.ArrayDeque<>(cats);
            while (!queue.isEmpty()) {
                var cat = queue.poll();
                queue.addAll(cat.subs());
                for (var mob : cat.mobs()) {
                    if (mob.steps().isEmpty()) continue;
                    String plain = StringUtil.strip(mob.name()).trim().toLowerCase();
                    map.putIfAbsent(plain, (long) mob.steps().getLast());
                }
            }
            repoCache = map;
            repoCacheFor = cats;
        }
        return repoCache.get(name.toLowerCase());
    }

    /** Manual override in minutes, null when the mob runs on the automatic cap. */
    public static Integer idleOverride(String name) {
        for (Map.Entry<String, Integer> e : TeslaMapsConfig.get().bestiaryIdleMinutes.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }

    /** Minutes rounded from whatever cap the mob ends up with, for chat output. */
    public static int idleMinutes(String name) {
        Stat s = stats.get(name);
        long cap = s != null ? s.idleCap : idleCap(name, 0);
        return (int) (cap / 60_000L);
    }

    /** Sets or clears (minutes <= 0) the manual cap. Returns the name it was stored under. */
    public static String setIdle(String name, int minutes) {
        String resolved = trackedName(name);
        if (resolved == null) {
            resolved = name.trim();
            for (String s : seen) if (s.equalsIgnoreCase(resolved)) resolved = s;
        }
        Map<String, Integer> map = TeslaMapsConfig.get().bestiaryIdleMinutes;
        map.entrySet().removeIf(e -> e.getKey().equalsIgnoreCase(name));
        if (minutes > 0) map.put(resolved, minutes);
        Stat s = stats.get(resolved);
        if (s != null) s.idleCap = idleCap(resolved, s.needed);
        TeslaMapsConfig.save();
        return resolved;
    }

    /** Few kills to max means a rare mob: long gaps between kills are the norm there. */
    private static long idleCap(String name, long needed) {
        Integer override = idleOverride(name);
        if (override != null && override > 0) return override * 60_000L;
        Long toMax = killsToMax(name);
        long reference = toMax != null ? toMax : needed;
        return reference > 0 && reference < RARE_BELOW_KILLS ? RARE_IDLE_CAP_MS : IDLE_CAP_MS;
    }

    private static final Map<String, Stat> stats = new LinkedHashMap<>();
    private static final List<String> seen = new ArrayList<>();
    private static int tickCounter = 0;
    private static String lastProfile = "";

    public static void reset() {
        cachedLines = null;
        displayedCache = null;
        stats.clear();
        seen.clear();
    }

    public static void resetSession() {
        cachedLines = null;
        for (Stat s : stats.values()) {
            s.kills = 0;
            s.activeMs = 0;
            s.lastActivity = 0;
        }
    }

    public static List<String> tracked() {
        return TeslaMapsConfig.get().bestiaryTracked;
    }

    /** Returns the stored casing when the mob is already on the list, null otherwise. */
    public static String trackedName(String name) {
        for (String t : tracked()) if (t.equalsIgnoreCase(name)) return t;
        return null;
    }

    /** Adds a mob, preferring the casing the tab list uses. Null when it was already tracked. */
    public static String add(String name) {
        String resolved = addQuiet(name);
        if (resolved != null) TeslaMapsConfig.save();
        return resolved;
    }

    private static String addQuiet(String name) {
        if (trackedName(name) != null) return null;
        String resolved = name.trim();
        for (String s : seen) if (s.equalsIgnoreCase(resolved)) resolved = s;
        tracked().add(resolved);
        cachedLines = null;
        displayedCache = null;
        return resolved;
    }

    public static String remove(String name) {
        String hit = trackedName(name);
        if (hit == null) return null;
        tracked().remove(hit);
        stats.remove(hit);
        TeslaMapsConfig.get().bestiaryIdleMinutes.remove(hit);
        cachedLines = null;
        displayedCache = null;
        TeslaMapsConfig.save();
        return hit;
    }

    /** Adds every Bestiary mob currently in the tab. Returns what was newly added. */
    public static List<String> addAllFromTab() {
        List<String> added = new ArrayList<>();
        for (String name : currentTabMobs()) {
            String hit = addQuiet(name);
            if (hit != null) added.add(hit);
        }
        if (!added.isEmpty()) TeslaMapsConfig.save();
        return added;
    }

    /** Auto mode: every non-maxed mob the tab shows joins the list, a maxed one leaves it. */
    private static void autoTrack() {
        boolean changed = false;
        for (String name : currentTabMobs()) {
            if (addQuiet(name) != null) changed = true;
        }
        for (String name : new ArrayList<>(tracked())) {
            Stat s = stats.get(name);
            if (s == null) {
                for (Map.Entry<String, Stat> e : stats.entrySet()) {
                    if (e.getKey().equalsIgnoreCase(name)) { s = e.getValue(); break; }
                }
            }
            if (s != null && s.max) {
                tracked().remove(name);
                cachedLines = null;
                displayedCache = null;
                changed = true;
            }
        }
        if (changed) TeslaMapsConfig.save();
    }

    /** The non-maxed mobs the tab shows right now, in tab order. */
    public static List<String> currentTabMobs() {
        List<String> out = new ArrayList<>();
        for (String line : tabEntries()) {
            Matcher m = ENTRY.matcher(line);
            if (!m.matches()) continue;
            String name = m.group(1).trim();
            if (name.isEmpty() || isSkill(name) || out.contains(name)) continue;
            out.add(name);
        }
        return out;
    }

    public static int clear() {
        int n = tracked().size();
        tracked().clear();
        cachedLines = null;
        displayedCache = null;
        TeslaMapsConfig.save();
        return n;
    }

    public static void tick() {
        if (++tickCounter % 20 != 0) return;
        readTabList();
        if (TeslaMapsConfig.get().bestiaryAutoTrack) autoTrack();
        readMenu();
    }

    /** "Overall Progress" in the Bestiary menu is the only place the kills to max show up. */
    private static void readMenu() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs)) return;
        if (!MENU_TITLE.matcher(StringUtil.stripTrim(cs.getTitle().getString())).matches()) return;

        boolean found = false;
        for (net.minecraft.world.inventory.Slot slot : cs.getMenu().slots) {
            net.minecraft.world.item.ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            net.minecraft.world.item.component.ItemLore lore =
                    stack.get(net.minecraft.core.component.DataComponents.LORE);
            if (lore == null) continue;

            Matcher n = MENU_NAME.matcher(StringUtil.stripTrim(stack.getHoverName().getString()));
            if (!n.matches()) continue;
            String name = n.group("name").trim();
            if (name.isEmpty()) continue;

            boolean overall = false;
            for (Component raw : lore.lines()) {
                String line = StringUtil.strip(raw.getString()).trim();
                if (OVERALL.matcher(line).matches()) { overall = true; continue; }
                if (!overall) continue;
                Matcher bar = BAR.matcher(line);
                if (!bar.find()) continue;
                long toMax = parseAmount(bar.group(2));
                if (toMax > 0 && !Long.valueOf(toMax).equals(killsToMax(name))) {
                    TeslaMapsConfig.get().bestiaryKillsToMax.put(name, toMax);
                    found = true;
                }
                break;
            }
        }
        if (found) {
            cachedLines = null;
            TeslaMapsConfig.save();
        }
    }

    private static void readTabList() {
        List<String> use = tabEntries();
        if (use.isEmpty()) return;

        // another profile has its own kill counts, keeping the old ones fakes a huge gain
        if (!tabProfile.equals(lastProfile)) {
            lastProfile = tabProfile;
            stats.clear();
            seen.clear();
            cachedLines = null;
            displayedCache = null;
        }

        for (String line : use) update(line);
    }

    private static String tabProfile = "";

    /** The Bestiary block of the tab list, or the loose entries when the block was not found. */
    private static List<String> tabEntries() {
        List<String> lines = TabListUtils.getOrderedLines();
        tabProfile = lastProfile;
        if (lines.isEmpty()) return List.of();

        List<String> section = null;
        List<String> loose = new ArrayList<>();
        String profile = lastProfile;
        for (int i = 0; i < lines.size(); i++) {
            String line = StringUtil.strip(lines.get(i)).trim();
            if (line.startsWith("Profile: ")) {
                profile = line.substring(9);
                continue;
            }
            if (line.equals("Bestiary:")) {
                section = new ArrayList<>();
                for (int j = i + 1; j < lines.size(); j++) {
                    String next = StringUtil.strip(lines.get(j)).trim();
                    if (next.isEmpty() || HEADER.matcher(next).matches()) break;
                    section.add(next);
                }
                continue;
            }
            if (ENTRY.matcher(line).matches() || MAXED.matcher(line).matches()) loose.add(line);
        }
        tabProfile = profile;

        // the section is the truth, the loose scan only covers a tab we failed to order
        return section != null && !section.isEmpty() ? section : loose;
    }

    private static void update(String line) {
        Matcher max = MAXED.matcher(line);
        if (max.matches()) {
            String name = max.group(1).trim();
            if (isSkill(name)) return;
            note(name);
            Stat s = stats.computeIfAbsent(name, k -> new Stat());
            s.tier = Integer.parseInt(max.group(2));
            s.max = true;
            s.seeded = true;
            return;
        }
        Matcher m = ENTRY.matcher(line);
        if (!m.matches()) return;
        String name = m.group(1).trim();
        if (isSkill(name)) return;
        note(name);

        int tier = Integer.parseInt(m.group(2));
        long current = parseNum(m.group(3));
        long needed = parseNum(m.group(4));

        Stat s = stats.computeIfAbsent(name, k -> new Stat());
        s.max = false;
        s.idleCap = idleCap(name, needed);
        // the tab counts total kills, not tier progress - a tier up does not reset it
        if (s.seeded && current > s.current) {
            s.kills += current - s.current;
            s.touch();
        }
        s.seeded = true;
        s.tier = tier;
        s.current = current;
        s.needed = needed;
    }

    private static void note(String name) {
        if (!seen.contains(name)) seen.add(name);
    }

    // skills sit in the same tab with the same shape ("Fishing 50: MAX")
    private static boolean isSkill(String name) {
        return SkillTracker.skillOf(name) != null;
    }

    private static List<String> displayedCache = null;
    private static long displayedAt = 0;

    // shouldRender() runs per frame from the HUD and the inventory drag layer
    private static List<String> displayed() {
        long now = System.currentTimeMillis();
        if (displayedCache != null && now - displayedAt < 250) return displayedCache;
        displayedAt = now;
        return displayedCache = buildDisplayed();
    }

    /** Only what /tmap be added, so the HUD stays empty until it is asked for. */
    private static List<String> buildDisplayed() {
        List<String> out = new ArrayList<>();
        // a mob can be added before the tab was ever read, so the casing has to be forgiving
        for (String name : tracked()) {
            for (String key : stats.keySet()) {
                if (key.equalsIgnoreCase(name)) { out.add(key); break; }
            }
        }
        return out;
    }

    public static boolean shouldRender() {
        return TeslaMapsConfig.get().bestiaryTracker && !displayed().isEmpty();
    }

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        if (!shouldRender()) return;
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        draw(ctx, Minecraft.getInstance(), cfg.bestiaryTrackerX, cfg.bestiaryTrackerY,
                cfg.bestiaryTrackerScale, lines());
    }

    /** The reset button, only reachable from the inventory. */
    public static void onClick(double mouseX, double mouseY, int button) {
        if (!shouldRender() || !inInventory()) return;
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        String[] ls = lines();
        float scale = cfg.bestiaryTrackerScale;
        double localX = (mouseX - cfg.bestiaryTrackerX) / scale;
        double localY = (mouseY - cfg.bestiaryTrackerY) / scale;

        if ((int) Math.floor(localY / 10.0) != ls.length - 1) return;
        String last = ls[ls.length - 1];
        int cut = last.indexOf(RESET);
        String before = cut > 0 ? last.substring(0, cut) : "";
        if (localX >= Minecraft.getInstance().font.width(before) - 2) resetSession();
    }

    private static final String RESET = "§c[Reset]";

    private static String[] cachedLines = null;
    private static long cachedAt = 0;
    private static boolean cachedInv = false;

    private static String[] lines() {
        long now = System.currentTimeMillis();
        boolean inv = inInventory();
        if (cachedLines != null && inv == cachedInv && now - cachedAt < 250) return cachedLines;
        cachedLines = buildLines();
        cachedAt = now;
        cachedInv = inv;
        return cachedLines;
    }

    private static String[] buildLines() {
        List<String> out = new ArrayList<>();
        out.add("§6§lBestiary");

        long longest = 0;
        for (String name : displayed()) {
            Stat s = stats.get(name);
            if (s == null) continue;
            longest = Math.max(longest, s.liveActiveMs());
            out.add("§e" + name + " §7" + (s.max ? "§6MAX" : "T" + s.tier));
            double h = s.hours();
            boolean warm = s.liveActiveMs() >= 30_000 && h > 0;
            out.add("§7Kills/h: §b" + (warm ? fmt(s.kills / h) : "…") + " §8(" + fmt(s.kills) + ")");
            double kph = warm ? s.kills / h : 0;
            if (!s.max) {
                out.add(goal("Next", s.current, s.needed, kph));
                Long toMax = killsToMax(name);
                if (toMax != null && toMax > s.needed) out.add(goal("Max", s.current, toMax, kph));
            }
        }

        if (inInventory()) out.add("§7Session: §f" + fmtDuration(longest) + " " + RESET);
        return out.toArray(new String[0]);
    }

    private static String goal(String label, long current, long target, double killsPerHour) {
        String left = "§8(" + fmt(current) + "/" + fmt(target) + ") "
                + String.format("%.1f%%", target > 0 ? Math.min(100.0, current * 100.0 / target) : 0.0);
        if (killsPerHour <= 0 || target <= current) return "§7" + label + ": " + left;
        return "§7" + label + ": §a" + fmtEta((target - current) / killsPerHour) + " " + left;
    }

    private static boolean inInventory() {
        return Minecraft.getInstance().screen
                instanceof net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
    }

    public static void drawPreview(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale) {
        draw(ctx, mc, x, y, scale, sampleLines());
    }

    private static String[] sampleLines() {
        return new String[]{
            "§6§lBestiary",
            "§eGoblin Raider §7T14",
            "§7Kills/h: §b12.4k §8(1.203k)",
            "§7Next: §a17m §8(790/1.0k)",
            "§7Max: §a4h 12m §8(790/52.0k)"
        };
    }

    public static int width(float scale) {
        return (int) (boxWidth(Minecraft.getInstance().font, shouldRender() ? lines() : sampleLines()) * scale);
    }

    public static int height(float scale) {
        String[] ls = shouldRender() ? lines() : sampleLines();
        return (int) ((ls.length * 10 + 4) * scale);
    }

    public static int[] previewSize(net.minecraft.client.gui.Font font) {
        String[] ls = sampleLines();
        return new int[]{boxWidth(font, ls), ls.length * 10 + 4};
    }

    private static String[] widthCacheFor = null;
    private static int widthCache = 0;

    private static int boxWidth(net.minecraft.client.gui.Font font, String[] ls) {
        if (ls == widthCacheFor) return widthCache;
        int w = 0;
        for (String l : ls) w = Math.max(w, font.width(l));
        widthCacheFor = ls;
        return widthCache = w + 8;
    }

    private static String[] seqCacheFor = null;
    private static FormattedCharSequence[] seqCache = null;

    private static FormattedCharSequence[] sequences(String[] ls) {
        if (ls == seqCacheFor) return seqCache;
        FormattedCharSequence[] out = new FormattedCharSequence[ls.length];
        for (int i = 0; i < ls.length; i++) out[i] = Component.literal(ls[i]).getVisualOrderText();
        seqCacheFor = ls;
        return seqCache = out;
    }

    private static void draw(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale, String[] ls) {
        int w = boxWidth(mc.font, ls) - 8;
        FormattedCharSequence[] seq = sequences(ls);
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        com.teslamaps.render.gui.TeslaGui.backdropRect(ctx, -4, -4, w + 8, ls.length * 10 + 4);
        for (int i = 0; i < seq.length; i++) ctx.text(mc.font, seq[i], 0, i * 10, 0xFFFFFFFF);
        pose.popMatrix();
    }

    /** Chat summary behind /tmap be. */
    public static List<String> report() {
        List<String> out = new ArrayList<>();
        if (tracked().isEmpty()) {
            out.add("§b[TeslaMaps] §fNo mob tracked §7· /tmap be add <mob>");
        } else {
            out.add("§b[TeslaMaps] §fTracked: §e" + String.join("§7, §e", tracked()));
        }
        for (String name : displayed()) {
            Stat s = stats.get(name);
            if (s == null) continue;
            double h = s.hours();
            String rate = s.liveActiveMs() >= 30_000 && h > 0 ? fmt(s.kills / h) : "…";
            out.add("§7 " + name + " §8T" + s.tier + " §7· §b" + rate + "/h §8(" + fmt(s.kills)
                    + " kills) §8idle " + idleMinutes(name) + "m"
                    + (idleOverride(name) != null ? "*" : ""));
        }
        out.add("§7Auto: " + (TeslaMapsConfig.get().bestiaryAutoTrack ? "§aon" : "§coff"));
        out.add("§7/tmap be add <mob> [idle min] · remove · auto · clear");
        return out;
    }

    public static Collection<String> suggestions(boolean forRemove) {
        return forRemove ? tracked() : seen;
    }

    private static String fmtDuration(long ms) {
        long total = ms / 1000;
        long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        if (h > 0) return h + "h " + String.format("%02dm", m);
        if (m > 0) return m + "m " + String.format("%02ds", s);
        return s + "s";
    }

    private static String fmtEta(double hours) {
        long minutes = Math.round(hours * 60);
        if (minutes < 60) return minutes + "m";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private static String fmt(double v) {
        if (v >= 1_000_000_000) return String.format("%.2fB", v / 1_000_000_000);
        if (v >= 1_000_000) return String.format("%.2fM", v / 1_000_000);
        if (v >= 1_000) return String.format("%.1fk", v / 1_000);
        return String.format("%.0f", v);
    }

    // the menu bar abbreviates ("52k/1.2m")
    private static long parseAmount(String raw) {
        String s = raw.replace(",", "").trim();
        if (s.isEmpty()) return 0;
        double mult = 1;
        char last = s.charAt(s.length() - 1);
        if (last == 'k' || last == 'K') mult = 1_000;
        else if (last == 'm' || last == 'M') mult = 1_000_000;
        else if (last == 'b' || last == 'B') mult = 1_000_000_000L;
        if (mult > 1) s = s.substring(0, s.length() - 1);
        try {
            return (long) (Double.parseDouble(s) * mult);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long parseNum(String s) {
        try {
            return Long.parseLong(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
