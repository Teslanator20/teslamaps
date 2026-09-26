package com.teslamaps.features.fishing;

// Ported from SkyHanni's SeaCreatureTracker.kt, "since" counters after SkyblockExtras' Fishing Counter
import com.google.gson.Gson;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ProfiledFile;
import com.teslamaps.utils.SeaCreatureList;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.FishingRodItem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SeaCreatureTracker {

    private static final class Data {
        Map<String, Long> total = new HashMap<>();
        Map<String, Long> since = new HashMap<>();
        Map<String, Long> sinceAt = new HashMap<>();
    }

    private static final List<String> SAMPLE = List.of("§b§lSea Creatures §7(Session)",
            " §e12 §aSea Walker", " §e3 §9Sea Guardian", " §e1 §6Water Hydra", " §e16 §7Total",
            "§7Since §6Sea Emperor§7: §e211 §8(1h 12m)");
    private static final Gson GSON = new Gson();
    private static final ProfiledFile FILE = new ProfiledFile("sea_creature_tracker.json",
            FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("sea_creature_tracker.json"));
    private static final long DOUBLE_HOOK_MS = 2_000;

    private static Data data = new Data();
    private static boolean loaded;
    private static final Map<String, Long> session = new HashMap<>();
    private static String lastVariant;
    private static long lastCatchAt;
    private static long doubleHookAt;
    private static boolean changed = true;
    private static int ticks;
    private static List<String> lines = List.of();
    private static int width;
    private static HudEditElement hud;

    private SeaCreatureTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("seaCreatureTracker", "Sea Creature Tracker").section("Fishing")
                .enabled(() -> cfg().seaCreatureTracker)
                .size(() -> TextHud.width(lines.isEmpty() ? SAMPLE : lines), () -> TextHud.height((lines.isEmpty() ? SAMPLE : lines).size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        Feature.of("SeaCreatureTracker").section("Fishing").when(() -> cfg().seaCreatureTracker)
                .tick(SeaCreatureTracker::tick)
                .serverChat(SeaCreatureTracker::onChat)
                .hudHiddenInInventory("seacreaturetracker", SeaCreatureTracker::render)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Sea Creature Tracker");
                    b.toggle("Sea Creature Tracker", () -> c.seaCreatureTracker, v -> c.seaCreatureTracker = v)
                            .desc("Counts every sea creature you catch, most caught first, plus how many catches and how long "
                                    + "since the last rare one of the area (Jawbus, Thunder, Emperor, ...). Position in /tmap gui")
                            .tags("sea creature", "tracker", "since", "jawbus", "thunder", "emperor", "pity");
                    b.toggle("  Show Total Instead Of Session", () -> c.seaCreatureTrackerTotal, v -> { c.seaCreatureTrackerTotal = v; changed = true; });
                    b.toggle("  Show Percentage", () -> c.seaCreatureTrackerPercent, v -> { c.seaCreatureTrackerPercent = v; changed = true; });
                    b.toggle("  Double Hook Counts Twice", () -> c.seaCreatureTrackerDouble, v -> c.seaCreatureTrackerDouble = v);
                    b.toggle("  Only Current Area", () -> c.seaCreatureTrackerArea, v -> { c.seaCreatureTrackerArea = v; changed = true; })
                            .desc("Only creatures of the group you caught last (water, lava, winter, spooky, ...)");
                    b.toggle("  Since Last Rare", () -> c.seaCreatureTrackerSince, v -> { c.seaCreatureTrackerSince = v; changed = true; });
                    b.toggle("  Only While Fishing", () -> c.seaCreatureTrackerOnlyFishing, v -> c.seaCreatureTrackerOnlyFishing = v)
                            .desc("Rod in hand or a catch in the last 2 minutes");
                    b.buttonRow(new String[] {"Reset Session", "Reset Total"}, new Runnable[] {
                            () -> { session.clear(); changed = true; },
                            () -> { ensure(); data = new Data(); save(); changed = true; }});
                });
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (t.startsWith("It's a Double Hook!") || t.equals("Double Hook!")) {
            doubleHookAt = System.currentTimeMillis();
            return;
        }
        String name = SeaCreatureList.nameFor(t);
        if (name == null) return;
        ensure();
        long now = System.currentTimeMillis();
        boolean dh = now - doubleHookAt < DOUBLE_HOOK_MS;
        doubleHookAt = 0;
        long amount = dh && cfg().seaCreatureTrackerDouble ? 2 : 1;
        session.merge(name, amount, Long::sum);
        data.total.merge(name, amount, Long::sum);
        String variant = SeaCreatureList.variant(name);
        lastVariant = variant;
        lastCatchAt = now;
        if (variant != null) {
            for (String other : SeaCreatureList.names()) {
                if (!variant.equals(SeaCreatureList.variant(other))) continue;
                SeaCreatureList.Info info = SeaCreatureList.info(other);
                if (info == null || !info.rare()) continue;
                if (other.equals(name)) {
                    data.since.put(other, 0L);
                    data.sinceAt.put(other, now);
                } else {
                    data.since.merge(other, amount, Long::sum);
                }
            }
        }
        save();
        changed = true;
    }

    private static void tick() {
        if (changed || ++ticks >= 20) {
            ticks = 0;
            changed = false;
            rebuild();
        }
    }

    private static void rebuild() {
        ensure();
        TeslaMapsConfig c = cfg();
        Map<String, Long> src = c.seaCreatureTrackerTotal ? data.total : session;
        List<Map.Entry<String, Long>> rows = new ArrayList<>();
        long sum = 0;
        for (Map.Entry<String, Long> e : src.entrySet()) {
            if (c.seaCreatureTrackerArea && lastVariant != null && !lastVariant.equals(SeaCreatureList.variant(e.getKey()))) continue;
            rows.add(e);
            sum += e.getValue();
        }
        List<String> out = new ArrayList<>();
        if (rows.isEmpty() && !c.seaCreatureTrackerSince) { lines = List.of(); return; }
        rows.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        out.add("§b§lSea Creatures §7(" + (c.seaCreatureTrackerTotal ? "Total" : "Session") + ")");
        for (Map.Entry<String, Long> e : rows) {
            SeaCreatureList.Info info = SeaCreatureList.info(e.getKey());
            String color = info == null ? "§f" : info.color();
            String pct = c.seaCreatureTrackerPercent && sum > 0
                    ? String.format(Locale.ROOT, " §7%.1f%%", e.getValue() * 100.0 / sum) : "";
            out.add(" §e" + String.format(Locale.ROOT, "%,d", e.getValue()) + " " + color + e.getKey() + pct);
        }
        if (!rows.isEmpty()) out.add(" §e" + String.format(Locale.ROOT, "%,d", sum) + " §7Total");
        if (c.seaCreatureTrackerSince && lastVariant != null) {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Long> e : data.since.entrySet()) {
                if (!lastVariant.equals(SeaCreatureList.variant(e.getKey()))) continue;
                SeaCreatureList.Info info = SeaCreatureList.info(e.getKey());
                Long at = data.sinceAt.get(e.getKey());
                out.add("§7Since " + (info == null ? "§f" : info.color()) + e.getKey() + "§7: §e"
                        + String.format(Locale.ROOT, "%,d", e.getValue())
                        + (at == null ? "" : " §8(" + duration(now - at) + ")"));
            }
        }
        if (out.size() == 1) { lines = List.of(); return; }
        lines = out;
        width = TextHud.width(out);
    }

    static String duration(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 86400) return s / 86400 + "d " + s / 3600 % 24 + "h";
        if (s >= 3600) return s / 3600 + "h " + s / 60 % 60 + "m";
        return s / 60 + "m " + s % 60 + "s";
    }

    private static boolean fishing(Minecraft mc) {
        if (mc.player == null) return false;
        return mc.player.getMainHandItem().getItem() instanceof FishingRodItem
                || System.currentTimeMillis() - lastCatchAt < 120_000;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || lines.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || cfg().seaCreatureTrackerOnlyFishing && !fishing(mc)) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }

    private static void ensure() {
        if (loaded && !FILE.stale()) return;
        loaded = true;
        data = new Data();
        Path p = FILE.path();
        try {
            if (Files.exists(p)) {
                Data read = GSON.fromJson(Files.readString(p), Data.class);
                if (read != null) {
                    if (read.total == null) read.total = new HashMap<>();
                    if (read.since == null) read.since = new HashMap<>();
                    if (read.sinceAt == null) read.sinceAt = new HashMap<>();
                    data = read;
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to load sea creature tracker: {}", e.toString());
        }
    }

    private static void save() {
        try {
            Path p = FILE.path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(data));
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to save sea creature tracker: {}", e.toString());
        }
    }
}
