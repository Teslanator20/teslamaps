package com.teslamaps.features.theend;

// Ported from SkyHanni's DragonProfitTracker.kt, ProfitPerDragon.kt and the chat side of DragonFeatures.kt
import com.google.gson.JsonObject;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.RepoCache;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DragonProfitTracker {

    private static final String REPO = "https://raw.githubusercontent.com/hannibal002/SkyHanni-REPO/main/constants/DragonProfitTrackerItems.json";
    private static final String[] TYPES = {"Protector", "Old", "Unstable", "Young", "Strong", "Wise", "Superior"};
    private static final Pattern SPAWN = Pattern.compile("^☬ The (\\w+) Dragon has spawned!$");
    private static final Pattern DOWN = Pattern.compile("^(\\w+) DRAGON DOWN!$");
    private static final Pattern TOP = Pattern.compile("^1st Damager - .+ - ([\\d,.]+)$");
    private static final Pattern POSITION = Pattern.compile("^Your Damage: ([\\d,.]+) (?:\\(NEW RECORD!\\) )?\\(Position #(\\d+)\\)$");
    private static final String EYES = "SUMMONING_EYE_PLACED";
    private static final String UNKNOWN = "UNKNOWN";

    private static Tracker tracker;
    private static HudEditElement hud;
    private static volatile Map<String, Integer> weights = Map.of();
    private static boolean loading;
    private static long lastTry;

    private static int eyes;
    private static String type;
    private static boolean ending;
    private static double topDamage;
    private static Object lastLevel;
    private static int scanTicks = -1;
    private static String lootType;
    private static int lootPlace;
    private static int lootEyes;
    private static double lootWeight;
    private static final Map<String, Integer> loot = new LinkedHashMap<>();
    private static final Set<UUID> scanned = new HashSet<>();
    private static boolean show;

    private DragonProfitTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        tracker = Tracker.create("dragon_profit", "§b§lDragon Profit Tracker")
                .priceSource(() -> cfg().dragonProfitPriceSource)
                .name(EYES, "§5Summoning Eyes Placed");
        for (String t : TYPES) {
            String k = killKey(t);
            tracker.fixedValue(k, 0).name(k, "§e" + t + " Dragon Kills");
        }
        tracker.fixedValue(killKey(UNKNOWN), 0).name(killKey(UNKNOWN), "§eUnknown Dragon Kills");
        hud = HudEditElement.of("dragonProfitTracker", "Dragon Profit Tracker").section("The End").ctx(HudEditElement.HudCtx.THE_END)
                .enabled(() -> cfg().dragonProfitTracker)
                .size(() -> Math.max(80, tracker.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, tracker.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> tracker.draw(g, mc, x, y, s));
        Feature.of("DragonProfitTracker").section("The End").when(() -> cfg().dragonProfitTracker)
                .tick(DragonProfitTracker::tick)
                .serverChat(DragonProfitTracker::onChat)
                .hud("dragonprofittracker", DragonProfitTracker::render)
                .config("The End", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Dragon Profit");
                    b.toggle("Dragon Profit Tracker", () -> c.dragonProfitTracker, v -> c.dragonProfitTracker = v)
                            .desc("Dragon kills per type, your placed eyes as cost, loot from the drops plus fragments/pearls estimated from your weight. Shown in The End (/tmap gui).")
                            .tags("dragon", "summoning eye", "fragments", "weight", "profit");
                    b.toggle("  Count Leeched Dragons", () -> c.dragonProfitLeeched, v -> c.dragonProfitLeeched = v)
                            .desc("Also count dragons you placed no eye for");
                    b.toggle("  Profit Chat Message", () -> c.dragonProfitChat, v -> c.dragonProfitChat = v)
                            .desc("\"Profit for Dragon\" line after each kill, hover for the loot");
                    b.priceSource("  Price Source", () -> c.dragonProfitPriceSource, v -> c.dragonProfitPriceSource = v);
                    b.trackerButtons(tracker);
                });
    }

    private static String killKey(String t) {
        return "DRAGON_KILL_" + t.toUpperCase(Locale.ROOT);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            resetFight();
            scanned.clear();
            scanTicks = -1;
        }
        Double eye = PriceManager.getPriceOrNull("SUMMONING_EYE", cfg().dragonProfitPriceSource);
        tracker.fixedValue(EYES, eye == null ? 0 : -eye);
        tracker.tick();
        show = SkyblockUtils.isInArea("The End");
        if (weights.isEmpty()) load();
        if (scanTicks >= 0) {
            scanTicks++;
            if (scanTicks % 20 == 0) scan(mc);
            if (scanTicks >= 60) {
                scanTicks = -1;
                finishLoot();
            }
        }
    }

    private static void resetFight() {
        eyes = 0;
        type = null;
        ending = false;
        topDamage = 0;
    }

    private static void onChat(String text) {
        if (!show) return;
        String t = StringUtil.stripTrim(text);
        if (t.startsWith("☬ You placed a Summoning Eye!")) { eyes++; return; }
        if (t.equals("You recovered a Summoning Eye!")) { eyes = Math.max(0, eyes - 1); return; }
        Matcher m = SPAWN.matcher(t);
        if (m.matches()) {
            type = known(m.group(1));
            if (eyes > 0) tracker.add(EYES, eyes);
            return;
        }
        m = DOWN.matcher(t);
        if (m.matches() && !m.group(1).equals("ENDSTONE")) {
            ending = true;
            if (type == null) type = known(m.group(1));
            return;
        }
        if (!ending) return;
        m = TOP.matcher(t);
        if (m.matches()) {
            topDamage = parse(m.group(1));
            return;
        }
        m = POSITION.matcher(t);
        if (!m.matches()) return;
        double damage = parse(m.group(1));
        int place = (int) parse(m.group(2));
        String dragon = type == null ? UNKNOWN : type;
        double weight = placementWeight(damage == 0 ? -1 : place) + 100 * (eyes + damage / (topDamage != 0 ? topDamage : 1));
        if (damage > 0 && (eyes > 0 || cfg().dragonProfitLeeched)) {
            tracker.add(killKey(dragon), 1);
            tracker.add("ESSENCE_DRAGON", dragon.equals("Superior") ? 10 : 5);
            lootType = dragon;
            lootPlace = place;
            lootEyes = eyes;
            lootWeight = weight;
            loot.clear();
            scanTicks = 0;
        }
        resetFight();
    }

    private static String known(String name) {
        for (String t : TYPES) if (t.equalsIgnoreCase(name)) return t;
        return UNKNOWN;
    }

    private static double parse(String s) {
        try { return Double.parseDouble(s.replace(",", "")); } catch (NumberFormatException e) { return 0; }
    }

    private static int placementWeight(int place) {
        return switch (place) {
            case -1 -> 10;
            case 1 -> 200;
            case 2 -> 175;
            case 3 -> 150;
            case 4 -> 125;
            case 5 -> 110;
            case 6, 7, 8 -> 100;
            case 9, 10 -> 90;
            case 11, 12 -> 80;
            default -> 70;
        };
    }

    private static void scan(Minecraft mc) {
        if (mc.level == null) return;
        Map<String, Integer> w = weights;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand) || !e.hasCustomName() || scanned.contains(e.getUUID())) continue;
            String raw = e.getCustomName().getString();
            int amount = 1;
            int x = raw.lastIndexOf("§8x");
            String nameRaw = raw;
            if (x >= 0) {
                try { amount = Integer.parseInt(StringUtil.stripTrim(raw.substring(x + 3))); } catch (NumberFormatException ignored) {}
                nameRaw = raw.substring(0, x);
            }
            String id = idFor(nameRaw);
            if (id == null || !w.containsKey(id)) continue;
            scanned.add(e.getUUID());
            loot.merge(id, amount, Integer::sum);
        }
    }

    private static String idFor(String raw) {
        String name = StringUtil.stripTrim(raw);
        if (name.equals("Ender Dragon")) return raw.contains("§6") ? "ENDER_DRAGON;4" : "ENDER_DRAGON;3";
        String id = PriceManager.idForName(name);
        return id != null ? id : SkyblockItems.idForName(name);
    }

    private static void finishLoot() {
        double weight = lootWeight;
        Map<String, Integer> w = weights;
        if (!loot.isEmpty() && weight >= 290) weight -= w.getOrDefault(loot.keySet().iterator().next(), 0);
        if (!lootType.equals(UNKNOWN)) {
            int frags = (int) Math.floor(weight / 22);
            weight -= frags * 22;
            if (frags > 0) loot.merge(lootType.toUpperCase(Locale.ROOT) + "_FRAGMENT", frags, Integer::sum);
        }
        int ePearls = (int) Math.floor(weight / 15);
        weight -= ePearls * 15;
        ePearls += standardEnchantedPearls(lootPlace);
        if (ePearls > 0) loot.merge("ENCHANTED_ENDER_PEARL", ePearls, Integer::sum);
        int pearls = (int) Math.floor(weight / 5) + standardPearls(lootPlace);
        if (pearls > 0) loot.merge("ENDER_PEARL", pearls, Integer::sum);

        List<String> hover = new ArrayList<>();
        double total = 0;
        Map<String, Double> values = new HashMap<>();
        for (Map.Entry<String, Integer> e : loot.entrySet()) {
            String key = e.getKey();
            tracker.add(key, e.getValue());
            double v = tracker.value(key, e.getValue());
            values.put(key, v);
            total += v;
        }
        loot.entrySet().stream().sorted((a, b) -> Double.compare(values.getOrDefault(b.getKey(), 0.0), values.getOrDefault(a.getKey(), 0.0)))
                .forEach(e -> hover.add("§eFound §f" + e.getValue() + "x " + display(e.getKey()) + " §6"
                        + com.teslamaps.utils.ItemPrice.format(values.getOrDefault(e.getKey(), 0.0))));
        double eyeCost = -tracker.value(EYES, lootEyes);
        total -= eyeCost;
        loot.clear();

        Minecraft mc = Minecraft.getInstance();
        if (!cfg().dragonProfitChat || mc.player == null) return;
        String msg = "Profit for Dragon§e: " + (total < 0 ? "§c-" : "§6") + com.teslamaps.utils.ItemPrice.format(Math.abs(total));
        hover.add("§cPlaced §5Summoning Eye§7: §c-" + com.teslamaps.utils.ItemPrice.format(eyeCost));
        hover.add("§e" + msg);
        Component tip = Component.literal(String.join("\n", hover));
        mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §e" + msg)
                .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(tip))));
    }

    private static String display(String id) {
        String n = SkyblockItems.coloredName(id);
        return n != null ? n : id;
    }

    private static int standardPearls(int place) {
        if (place == 1) return 30;
        if (place == 2) return 25;
        if (place == 3) return 22;
        if (place == 4) return 20;
        if (place == 5) return 18;
        if (place >= 6 && place <= 12) return 15;
        if (place >= 13 && place <= 18) return 12;
        if (place >= 19 && place <= 25) return 10;
        return 5;
    }

    private static int standardEnchantedPearls(int place) {
        if (place >= 1 && place <= 5) return 8 - place;
        if (place >= 6 && place <= 12) return 2;
        if (place >= 13 && place <= 18) return 1;
        return 0;
    }

    private static void load() {
        long now = System.currentTimeMillis();
        if (loading || now - lastTry < 120_000L) return;
        lastTry = now;
        loading = true;
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject items = RepoCache.fetch("DragonProfitTrackerItems", REPO, 20).getAsJsonObject().getAsJsonObject("items");
                Map<String, Integer> out = new HashMap<>();
                for (var e : items.entrySet()) out.put(e.getKey(), e.getValue().getAsJsonObject().get("weight").getAsInt());
                weights = out;
            } catch (Exception e) {
                com.teslamaps.TeslaMaps.LOGGER.warn("[DragonProfit] loot table failed: {}", e.toString());
            } finally {
                loading = false;
            }
        });
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!show || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        tracker.draw(g, mc, hud.x(), hud.y(), hud.scale());
    }
}
