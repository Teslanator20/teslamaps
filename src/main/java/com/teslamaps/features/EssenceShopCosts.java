package com.teslamaps.features;

// Ported from SkyHanni's EssenceShopHelper and Skyblocker's EssenceShopPrice
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Essence shops: coin value of every upgrade's cost, remaining essence to max and what it costs on the bazaar. */
public final class EssenceShopCosts {

    private static final String URL =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/essenceshops.json";
    private static final Pattern SHOP = Pattern.compile("^(.+) Essence Shop$");
    private static final Pattern UPGRADE = Pattern.compile("^(.+?)(?: ([IVXLCDM]+))?$");
    private static final Pattern COST = Pattern.compile("Cost ([\\d,]+) ([A-Za-z]+) Essence");
    private static final Pattern OWNED = Pattern.compile("^Your .+ Essence: ([\\d,]+)");

    private record Upgrade(String name, List<Integer> costs) {}

    private static final Map<String, List<Upgrade>> shops = new HashMap<>();
    private static volatile boolean loaded, loading;
    private static long lastTry;

    private static Feature feature;
    private static Screen lastScreen;
    private static String type;
    private static int ticks;
    private static final Map<ItemStack, List<String>> tips = new IdentityHashMap<>();

    private EssenceShopCosts() {}

    public static void register() {
        feature = Feature.of("EssenceShopCosts").section("Inventory")
                .when(() -> TeslaMapsConfig.get().essenceShopCosts)
                .tick(EssenceShopCosts::tick);
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (type == null || feature == null || !feature.active() || lines.isEmpty()) return;
            List<String> t = tips.get(stack);
            if (t == null) return;
            for (String s : t) lines.add(Component.literal(s));
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            if (lastScreen != null) {
                lastScreen = null;
                type = null;
                tips.clear();
            }
            return;
        }
        if (cs != lastScreen) {
            lastScreen = cs;
            ticks = 0;
            tips.clear();
            Matcher m = SHOP.matcher(StringUtil.stripTrim(cs.getTitle().getString()));
            type = m.matches() ? m.group(1) : null;
        }
        if (type == null || ticks++ % 10 != 0) return;
        load();
        PriceManager.ensureFresh();
        build(cs);
    }

    private static void build(AbstractContainerScreen<?> cs) {
        String essenceId = "ESSENCE_" + type.toUpperCase(Locale.ROOT).replace(' ', '_');
        Double sell = PriceManager.bazaarSellOrNull(essenceId);
        Double buy = PriceManager.bazaarBuyOrNull(essenceId);
        List<Upgrade> repo = shops.get(essenceId);
        Map<String, Integer> purchased = new HashMap<>();
        Map<ItemStack, List<String>> next = new IdentityHashMap<>();
        int owned = 0;
        ItemStack header = null;

        int size = Math.min(cs.getMenu().slots.size() - 36, 54);
        for (int i = 0; i < size; i++) {
            ItemStack stack = cs.getMenu().getSlot(i).getItem();
            if (stack.isEmpty()) continue;
            List<String> lore = ItemUtil.loreLines(stack);
            String name = StringUtil.stripTrim(stack.getHoverName().getString());
            if (i == 4) {
                header = stack;
                for (String l : lore) {
                    Matcher o = OWNED.matcher(l);
                    if (o.find()) owned = (int) parse(o.group(1));
                }
                continue;
            }
            if (i < 10 || i > 43) continue;

            List<String> t = new ArrayList<>();
            Matcher cost = COST.matcher(String.join(" ", lore));
            if (cost.find() && sell != null) {
                long amount = parse(cost.group(1));
                t.add("§bEssence Cost: §3" + fmt(amount * sell) + " coins §7(" + fmt(sell) + " each)");
            }

            Matcher up = UPGRADE.matcher(name);
            if (repo != null && up.matches()) {
                int tier = up.group(2) == null ? 1 : roman(up.group(2));
                boolean maxed = lore.contains("UNLOCKED");
                int have = maxed ? tier : tier - 1;
                purchased.put(up.group(1), have);
                for (Upgrade u : repo) {
                    if (!u.name().equalsIgnoreCase(up.group(1))) continue;
                    long left = remaining(u, have);
                    if (left > 0) t.add("§7To max: §d" + fmt(left) + " " + type + " Essence"
                            + (sell != null ? " §7(§6" + fmt(left * sell) + " coins§7)" : ""));
                    break;
                }
            }
            if (!t.isEmpty()) next.put(stack, t);
        }

        if (header != null && repo != null) {
            List<String> t = new ArrayList<>();
            long total = 0;
            t.add("");
            t.add("§bRemaining " + type + " Essence Upgrades:");
            for (Upgrade u : repo) {
                Integer have = purchased.get(u.name());
                if (have == null) continue;
                long left = remaining(u, have);
                if (left <= 0) continue;
                total += left;
                t.add("  §a" + u.name() + " §b" + have + " §7-> §b" + u.costs().size() + "§7: §8" + fmt(left));
            }
            if (total == 0) {
                t.add("  §a§lAll upgrades purchased!");
            } else {
                t.add("§7Sum Essence Needed: §8" + fmt(total));
                if (owned > 0) t.add("§7Essence Owned: §8" + fmt(owned));
                long missing = total - owned;
                if (missing > 0) {
                    t.add("§7Additional Essence Needed: §8" + fmt(missing));
                    if (buy != null) t.add("  §7BZ Instant Buy: §6" + fmt(missing * buy));
                    if (sell != null) t.add("  §7BZ Buy Order: §6" + fmt(missing * sell));
                } else {
                    t.add("§eYou have enough essence");
                }
            }
            next.put(header, t);
        }
        tips.clear();
        tips.putAll(next);
    }

    private static long remaining(Upgrade u, int have) {
        long sum = 0;
        for (int i = Math.max(0, have); i < u.costs().size(); i++) sum += u.costs().get(i);
        return sum;
    }

    private static void load() {
        long now = System.currentTimeMillis();
        if (loaded || loading || now - lastTry < 300_000L) return;
        lastTry = now;
        loading = true;
        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                HttpRequest req = HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(20)).GET().build();
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) return;
                JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
                Map<String, List<Upgrade>> parsed = new HashMap<>();
                for (var shop : root.entrySet()) {
                    List<Upgrade> list = new ArrayList<>();
                    for (var e : shop.getValue().getAsJsonObject().entrySet()) {
                        JsonObject o = e.getValue().getAsJsonObject();
                        if (!o.has("name") || !o.has("costs")) continue;
                        List<Integer> costs = new ArrayList<>();
                        for (JsonElement c : o.getAsJsonArray("costs")) costs.add(c.getAsInt());
                        list.add(new Upgrade(o.get("name").getAsString(), costs));
                    }
                    parsed.put(shop.getKey(), list);
                }
                Minecraft.getInstance().execute(() -> {
                    shops.clear();
                    shops.putAll(parsed);
                    loaded = true;
                });
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Essence shop data failed: {}", e.toString());
            } finally {
                loading = false;
            }
        });
    }

    private static int roman(String s) {
        int total = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = switch (s.charAt(i)) {
                case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50;
                case 'C' -> 100; case 'D' -> 500; case 'M' -> 1000; default -> 0;
            };
            total += v < prev ? -v : v;
            prev = Math.max(prev, v);
        }
        return total;
    }

    private static long parse(String s) {
        try {
            return Long.parseLong(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String fmt(double v) {
        return NumberFormat.getIntegerInstance(Locale.US).format(Math.round(v));
    }
}
