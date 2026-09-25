package com.teslamaps.features.croesus;

// Ported from Devonian's LootLogger.kt
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LootLogger {

    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("teslamaps").resolve("loot_log.json");
    private static final Gson GSON = new GsonBuilder().create();
    private static final String[] CHESTS = {"Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock"};
    private static final Pattern RUN_TITLE = Pattern.compile("^(Master )?(?:The )?Catacombs - (?:Floor ([IVX]+)|Entrance)$");
    private static final Pattern REWARDS = Pattern.compile("^(BEDROCK|OBSIDIAN|EMERALD|DIAMOND|GOLD|WOOD) CHEST REWARDS$");
    private static final Pattern COINS = Pattern.compile("^([0-9,]+) Coins$");

    public static final class Entry {
        long t;
        String floor;
        String chest;
        double cost;
        boolean key;
        boolean kismet;
        double value;
        double essence;
        List<String> items = new ArrayList<>();
    }

    private static List<Entry> log;
    private static Entry pending;
    private static long pendingAt;
    private static String croesusFloor;
    private static Object lastScreen;

    private LootLogger() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("LootLogger").when(() -> cfg().lootLogger)
                .tick(LootLogger::tick)
                .serverChat(LootLogger::onChat);
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("loot")
                        .executes(c -> summary(c.getSource(), null, 0))
                        .then(ClientCommands.argument("floor", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    String rem = b.getRemaining().toUpperCase(Locale.ROOT);
                                    for (DungeonFloor f : DungeonFloor.values()) {
                                        if (f != DungeonFloor.UNKNOWN && f.name().startsWith(rem)) b.suggest(f.name());
                                    }
                                    if ("E".startsWith(rem)) b.suggest("E");
                                    return b.buildFuture();
                                })
                                .executes(c -> summary(c.getSource(), StringArgumentType.getString(c, "floor"), 0))
                                .then(ClientCommands.argument("days", IntegerArgumentType.integer(1, 3650))
                                        .executes(c -> summary(c.getSource(), StringArgumentType.getString(c, "floor"),
                                                IntegerArgumentType.getInteger(c, "days"))))))));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            lastScreen = null;
            return;
        }
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        Matcher run = RUN_TITLE.matcher(title);
        if (run.matches()) {
            croesusFloor = run.group(2) == null ? "E" : (run.group(1) != null ? "M" : "F") + roman(run.group(2));
            return;
        }
        String chest = chestName(title);
        if (chest == null) return;
        if (cs != lastScreen) {
            lastScreen = cs;
            pending = null;
        }
        Entry e = snapshot(cs, chest);
        if (e != null) {
            pending = e;
            pendingAt = System.currentTimeMillis();
        }
    }

    private static String chestName(String title) {
        if (!title.endsWith(" Chest")) return null;
        String n = title.substring(0, title.length() - 6);
        for (String c : CHESTS) if (c.equals(n)) return c;
        return null;
    }

    private static Entry snapshot(AbstractContainerScreen<?> cs, String chest) {
        ItemLore contents = null;
        boolean rerolled = false;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            ItemLore lore = item.get(DataComponents.LORE);
            if (lore == null) continue;
            boolean hasContents = false, hasCost = false;
            for (Component c : lore.lines()) {
                String l = StringUtil.stripTrim(c.getString());
                if (l.equals("Contents")) hasContents = true;
                else if (l.equals("Cost")) hasCost = true;
                else if (l.contains("You already rerolled a chest!")) rerolled = true;
            }
            if (hasContents && hasCost && contents == null) contents = lore;
        }
        if (contents == null) return null;

        Entry e = new Entry();
        e.chest = chest;
        boolean inContents = false, inCost = false;
        double coins = 0;
        for (Component c : contents.lines()) {
            String l = StringUtil.stripTrim(c.getString());
            if (l.isEmpty()) continue;
            if (l.equals("Contents")) { inContents = true; inCost = false; continue; }
            if (l.equals("Cost")) { inCost = true; inContents = false; continue; }
            if (inCost) {
                Matcher m = COINS.matcher(l);
                if (m.matches()) coins += parse(m.group(1));
                else if (l.equals("Dungeon Chest Key")) e.key = true;
                else if (l.startsWith("Click") || l.startsWith("NOTE")) inCost = false;
            } else if (inContents) {
                e.items.add(l);
            }
        }
        CroesusProfit.ChestValue v = CroesusProfit.evaluate(contents);
        e.value = v.value();
        e.essence = v.essence();
        e.kismet = v.kismet() || rerolled;
        e.cost = coins;
        if (e.key) {
            double k = PriceManager.getPrice("DUNGEON_CHEST_KEY");
            if (k > 0) e.cost += k;
        }
        if (e.kismet && cfg().croesusKismetCost) {
            double k = PriceManager.getPrice("KISMET_FEATHER");
            if (k > 0) e.cost += k;
        }
        return e;
    }

    private static void onChat(String raw) {
        Matcher m = REWARDS.matcher(StringUtil.stripTrim(raw));
        if (!m.matches()) return;
        Entry e = pending;
        pending = null;
        if (e == null || System.currentTimeMillis() - pendingAt > 60_000 || !e.chest.equalsIgnoreCase(m.group(1))) return;
        String floor = null;
        if (DungeonManager.isInDungeon()) {
            DungeonFloor f = DungeonManager.getCurrentFloor();
            if (f != null && f != DungeonFloor.UNKNOWN) floor = f.name();
        }
        if (floor == null) floor = croesusFloor;
        if (floor == null) return;
        e.floor = floor;
        e.t = System.currentTimeMillis();
        load().add(e);
        save();
    }

    private static List<Entry> load() {
        if (log != null) return log;
        log = new ArrayList<>();
        try {
            if (Files.exists(FILE)) {
                List<Entry> read = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8),
                        new TypeToken<List<Entry>>() {}.getType());
                if (read != null) {
                    for (Entry e : read) {
                        if (e == null || e.floor == null || e.chest == null) continue;
                        if (e.items == null) e.items = new ArrayList<>();
                        log.add(e);
                    }
                }
            }
        } catch (Exception ex) {
            TeslaMaps.LOGGER.warn("loot log load failed: {}", ex.toString());
        }
        return log;
    }

    private static void save() {
        String json = GSON.toJson(log);
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(FILE.getParent());
                Files.writeString(FILE, json, StandardCharsets.UTF_8);
            } catch (Exception ex) {
                TeslaMaps.LOGGER.warn("loot log save failed: {}", ex.toString());
            }
        });
    }

    private static int summary(FabricClientCommandSource src, String floor, int days) {
        List<Entry> all = load();
        long since = days > 0 ? System.currentTimeMillis() - days * 86_400_000L : 0;
        if (floor == null) {
            Map<String, double[]> per = new LinkedHashMap<>();
            for (Entry e : all) {
                if (e.t < since) continue;
                double[] a = per.computeIfAbsent(e.floor, k -> new double[4]);
                a[0]++;
                a[1] += e.cost;
                a[2] += e.value - e.cost;
                a[3] += e.value - e.essence - e.cost;
            }
            if (per.isEmpty()) {
                src.sendFeedback(Component.literal("§c[TeslaMaps] No chests logged yet."
                        + (cfg().lootLogger ? "" : " Turn on Loot Logger (Croesus).")));
                return 0;
            }
            src.sendFeedback(Component.literal("§b[TeslaMaps] Loot Logger §7(/tmap loot <floor> [days])"));
            List<String> keys = new ArrayList<>(per.keySet());
            keys.sort(null);
            for (String k : keys) {
                double[] a = per.get(k);
                src.sendFeedback(Component.literal("§a" + k + " §7" + (int) a[0] + " chests §8| §eSpent §6" + fmt(a[1])
                        + " §8| §eProfit " + col(a[3]) + " §7(" + fmt(a[2]) + " with essence)"));
            }
            return 1;
        }
        String f = floor.toUpperCase(Locale.ROOT);
        List<Entry> list = new ArrayList<>();
        for (Entry e : all) if (e.floor.equals(f) && e.t >= since) list.add(e);
        if (list.isEmpty()) {
            src.sendFeedback(Component.literal("§c[TeslaMaps] No chests logged for " + f + (days > 0 ? " in the last " + days + " days" : "") + "."));
            return 0;
        }
        Map<String, Integer> chests = new LinkedHashMap<>();
        Map<String, Integer> items = new HashMap<>();
        double spent = 0, profit = 0, profitNoEss = 0;
        int keys = 0, kismets = 0;
        for (Entry e : list) {
            chests.merge(e.chest, 1, Integer::sum);
            for (String it : e.items) {
                String name = it;
                int qty = 1;
                int x = it.lastIndexOf(" x");
                if (x > 0) {
                    try {
                        qty = Integer.parseInt(it.substring(x + 2).replace(",", ""));
                        name = it.substring(0, x);
                    } catch (NumberFormatException ignored) {
                    }
                }
                items.merge(name, qty, Integer::sum);
            }
            spent += e.cost;
            profit += e.value - e.cost;
            profitNoEss += e.value - e.essence - e.cost;
            if (e.key) keys++;
            if (e.kismet) kismets++;
        }
        src.sendFeedback(Component.literal("§b[TeslaMaps] Loot Logger §a" + f + (days > 0 ? " §7(last " + days + " days)" : "")));
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(items.entrySet());
        sorted.sort((a, b) -> Integer.compare(a.getValue(), b.getValue()));
        for (Map.Entry<String, Integer> it : sorted) {
            src.sendFeedback(Component.literal("§8- §f" + it.getKey() + "§7: §6" + String.format(Locale.US, "%,d", it.getValue())));
        }
        StringBuilder sb = new StringBuilder("§bChests§7: ");
        boolean first = true;
        for (String c : CHESTS) {
            Integer n = chests.get(c);
            if (n == null) continue;
            if (!first) sb.append("§8, ");
            sb.append("§f").append(c).append(" §6").append(n);
            first = false;
        }
        src.sendFeedback(Component.literal(sb.toString()));
        src.sendFeedback(Component.literal("§eKeys used§7: §f" + keys + " §8| §eKismets§7: §f" + kismets));
        src.sendFeedback(Component.literal("§eTotal Spent§7: §6" + fmt(spent)));
        src.sendFeedback(Component.literal("§eProfit§7: " + col(profitNoEss)));
        src.sendFeedback(Component.literal("§eProfit incl. Essence§7: " + col(profit)));
        src.sendFeedback(Component.literal("§8Item values are the prices at the time the chest was bought, cost subtracted."));
        return 1;
    }

    private static String col(double v) {
        return (v >= 0 ? "§a+" : "§c") + fmt(v);
    }

    private static String fmt(double v) {
        double a = Math.abs(v);
        if (a >= 1_000_000_000) return String.format(Locale.US, "%.2fB", v / 1_000_000_000);
        if (a >= 1_000_000) return String.format(Locale.US, "%.2fM", v / 1_000_000);
        if (a >= 1_000) return String.format(Locale.US, "%.1fk", v / 1_000);
        return String.format(Locale.US, "%.0f", v);
    }

    private static double parse(String s) {
        try { return Double.parseDouble(s.replace(",", "")); } catch (Exception e) { return 0; }
    }

    private static int roman(String r) {
        return switch (r) {
            case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4;
            case "V" -> 5; case "VI" -> 6; case "VII" -> 7; default -> 0;
        };
    }
}
