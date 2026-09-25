package com.teslamaps.features;

// After SkyHanni's features/event/hoppity/summary/HoppityEventSummary.kt + HoppityLiveDisplay.kt (chat patterns from HoppityEggsManager)
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HoppityEventStats {

    public static final class Year {
        public Map<String, Integer> eggs = new LinkedHashMap<>();
        public Map<String, Integer> rarities = new LinkedHashMap<>();
        public int uniques, duplicates, strays;
        public long chocolate;
        public boolean summarized;
    }

    private static final Pattern EGG = Pattern.compile("^HOPPITY'S HUNT You found a (?:Chocolate )?(.+?) Egg\\b.*!$");
    private static final Pattern RABBIT = Pattern.compile("^HOPPITY'S HUNT You found (.+) \\((COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE)\\)!$");
    private static final Pattern NEW_CHOC = Pattern.compile("^NEW RABBIT! \\+([\\d,]+) Chocolate");
    private static final Pattern DUPE = Pattern.compile("^DUPLICATE RABBIT! \\+([\\d,]+) Chocolate");
    private static final Pattern STRAY_CHOC = Pattern.compile("You caught a stray .*?\\+([\\d,]+) Chocolate");
    private static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE"};
    private static final String[] RARITY_COLORS = {"§f", "§a", "§9", "§5", "§6", "§d", "§b"};
    private static final long EPOCH = 1560275700000L, YEAR_MS = 446_400_000L, MONTH_MS = YEAR_MS / 12;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<TreeMap<Integer, Year>>() {}.getType();
    private static final List<String> SAMPLE = List.of("§d§lHoppity's Hunt §7(Year 420)", "§7Eggs: §f42 §8(§fB 14 §fL 14 §fD 14§8)",
            "§aNew: §f12 §7Dupes: §f30 §7Strays: §f5", "§6Chocolate: §f12.3M");

    private static TreeMap<Integer, Year> years;
    private static boolean dirty;
    private static long dirtySince;
    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, timer;
    private static final Map<Integer, String> straySlots = new HashMap<>();
    private static Object lastScreen;

    private HoppityEventStats() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("hoppityLive", "Hoppity Live Stats").section("Inventory")
                .enabled(() -> cfg().hoppityLiveStats)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width, () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? TextHud.width(SAMPLE) : width));
        Feature.of("HoppityEventStats").section("Inventory").when(() -> cfg().hoppityLiveStats || cfg().hoppityEventSummary)
                .tick(HoppityEventStats::tick)
                .serverChat(HoppityEventStats::onChat)
                .hudHiddenInInventory("hoppitylive", HoppityEventStats::render)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Hoppity's Hunt");
                    b.toggle("Hoppity Live Stats", () -> c.hoppityLiveStats, v -> c.hoppityLiveStats = v)
                            .desc("Eggs per meal, new/duplicate rabbits, strays and chocolate of this Hoppity year (/tmap gui).");
                    b.toggle("  Show Outside The Event", () -> c.hoppityLiveAlways, v -> c.hoppityLiveAlways = v);
                    b.toggle("Hoppity Event Summary", () -> c.hoppityEventSummary, v -> c.hoppityEventSummary = v)
                            .desc("Chat summary when the event ends. Past years: /tmap hoppity <year>");
                });
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("hoppity")
                .executes(c -> { print(latestYear()); return 1; })
                .then(ClientCommands.argument("year", IntegerArgumentType.integer(1))
                        .executes(c -> { print(IntegerArgumentType.getInteger(c, "year")); return 1; }));
    }

    private static int sbYear() {
        return (int) ((System.currentTimeMillis() - EPOCH) / YEAR_MS) + 1;
    }

    private static boolean eventActive() {
        long inYear = (System.currentTimeMillis() - EPOCH) % YEAR_MS;
        return inYear / MONTH_MS < 3;
    }

    private static TreeMap<Integer, Year> years() {
        if (years == null) years = load();
        return years;
    }

    private static Year current() {
        return years().computeIfAbsent(sbYear(), k -> new Year());
    }

    private static int latestYear() {
        TreeMap<Integer, Year> y = years();
        return y.isEmpty() ? sbYear() : y.lastKey();
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (t.startsWith("HOPPITY'S HUNT You found ")) {
            Matcher m = EGG.matcher(t);
            if (m.matches()) {
                current().eggs.merge(m.group(1).trim(), 1, Integer::sum);
                changed();
                return;
            }
            m = RABBIT.matcher(t);
            if (m.matches()) {
                current().rarities.merge(m.group(2), 1, Integer::sum);
                changed();
            }
        } else if (t.startsWith("NEW RABBIT!")) {
            Year y = current();
            y.uniques++;
            Matcher m = NEW_CHOC.matcher(t);
            if (m.find()) y.chocolate += num(m.group(1));
            changed();
        } else if (t.startsWith("DUPLICATE RABBIT!")) {
            Year y = current();
            y.duplicates++;
            Matcher m = DUPE.matcher(t);
            if (m.find()) y.chocolate += num(m.group(1));
            changed();
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        scanStrays(mc);
        if (++timer % 20 != 0) return;
        if (dirty && System.currentTimeMillis() - dirtySince > 10_000) save();

        boolean active = eventActive();
        if (!active && cfg().hoppityEventSummary) {
            for (var e : years().entrySet()) {
                if (e.getValue().summarized || e.getKey() > sbYear()) continue;
                e.getValue().summarized = true;
                print(e.getKey());
                changed();
            }
        }
        if (!cfg().hoppityLiveStats || (!active && !cfg().hoppityLiveAlways)) {
            lines = List.of();
            return;
        }
        int year = active ? sbYear() : latestYear();
        lines = build(year, years().get(year));
        width = TextHud.width(lines);
    }

    // CAUGHT! strays in the Chocolate Factory, one count per slot and name
    private static void scanStrays(Minecraft mc) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            if (lastScreen != null) { lastScreen = null; straySlots.clear(); }
            return;
        }
        if (lastScreen != mc.screen) { lastScreen = mc.screen; straySlots.clear(); }
        if (!eventActive() || !StringUtil.stripTrim(cs.getTitle().getString()).equals("Chocolate Factory")) return;
        var slots = cs.getMenu().slots;
        for (int i = 0; i < Math.min(27, slots.size()); i++) {
            ItemStack st = slots.get(i).getItem();
            if (st.isEmpty()) { straySlots.remove(i); continue; }
            String name = StringUtil.stripTrim(st.getHoverName().getString());
            if (!name.endsWith("CAUGHT!")) { straySlots.remove(i); continue; }
            if (name.equals(straySlots.get(i))) continue;
            straySlots.put(i, name);
            Year y = current();
            y.strays++;
            Matcher m = STRAY_CHOC.matcher(String.join(" ", ItemUtil.loreLines(st)));
            if (m.find()) y.chocolate += num(m.group(1));
            changed();
        }
    }

    private static List<String> build(int year, Year y) {
        List<String> out = new ArrayList<>();
        out.add("§d§lHoppity's Hunt §7(Year " + year + ")");
        if (y == null) {
            out.add("§7No data yet");
            return out;
        }
        int eggs = 0;
        StringBuilder meals = new StringBuilder();
        for (var e : y.eggs.entrySet()) {
            eggs += e.getValue();
            if (!meals.isEmpty()) meals.append(" ");
            meals.append("§f").append(e.getKey()).append(" §e").append(e.getValue());
        }
        out.add("§7Eggs: §f" + eggs);
        if (!meals.isEmpty()) out.add(" " + meals);
        out.add("§aNew: §f" + y.uniques + " §7Dupes: §f" + y.duplicates + " §7Strays: §f" + y.strays);
        StringBuilder rar = new StringBuilder();
        for (int i = 0; i < RARITIES.length; i++) {
            Integer n = y.rarities.get(RARITIES[i]);
            if (n == null) continue;
            if (!rar.isEmpty()) rar.append(" ");
            rar.append(RARITY_COLORS[i]).append(RARITIES[i].charAt(0)).append(RARITIES[i].substring(1, 3).toLowerCase(Locale.ROOT))
                    .append(" §f").append(n);
        }
        if (!rar.isEmpty()) out.add(" " + rar);
        out.add("§6Chocolate: §f" + com.teslamaps.utils.ItemPrice.format(y.chocolate));
        return out;
    }

    private static void print(int year) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§d§m                                        "));
        for (String s : build(year, years().get(year))) mc.player.sendSystemMessage(Component.literal(s));
        mc.player.sendSystemMessage(Component.literal("§d§m                                        "));
    }

    private static void changed() {
        if (!dirty) dirtySince = System.currentTimeMillis();
        dirty = true;
        timer = 19;
    }

    private static long num(String s) {
        try {
            return Long.parseLong(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("hoppity_events.json");
    }

    private static TreeMap<Integer, Year> load() {
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p)) {
                TreeMap<Integer, Year> m = GSON.fromJson(r, TYPE);
                if (m != null) {
                    m.values().removeIf(java.util.Objects::isNull);
                    for (Year y : m.values()) {
                        if (y.eggs == null) y.eggs = new LinkedHashMap<>();
                        if (y.rarities == null) y.rarities = new LinkedHashMap<>();
                    }
                    return m;
                }
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[Hoppity] load failed: {}", e.toString());
            }
        }
        return new TreeMap<>();
    }

    private static void save() {
        dirty = false;
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p)) {
                GSON.toJson(years(), TYPE, w);
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[Hoppity] save failed: {}", e.toString());
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || lines.isEmpty() || hud == null) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
