package com.teslamaps.features.events;

// Ported from SkyHanni's CarnivalShopHelper.kt
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Carnival / event token shops: remaining tokens to max every upgrade, per shop and in total. */
public final class CarnivalShopHelper {

    private static final String URL =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/carnivalshops.json";
    private static final Pattern UPGRADE = Pattern.compile("^(.+?) ([IVXLCDM]+)$");
    private static final Pattern TOKENS = Pattern.compile("Your Tokens: ([\\d,]+)");

    private record Upgrade(String name, List<Integer> costs) {}

    private static final Map<String, List<Upgrade>> shops = new LinkedHashMap<>();
    private static volatile boolean loaded, loading;
    private static long lastTry;

    private static Feature feature;
    private static Screen lastScreen;
    private static String shop;
    private static boolean overview;
    private static int ticks;
    private static final Map<ItemStack, List<String>> tips = new IdentityHashMap<>();

    private CarnivalShopHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("CarnivalShopHelper").section(Carnival.CATEGORY)
                .when(() -> cfg().carnivalShopHelper)
                .tick(CarnivalShopHelper::tick)
                .config(Carnival.CATEGORY, b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Token Shop Helper", () -> c.carnivalShopHelper, v -> c.carnivalShopHelper = v)
                            .desc("Carnival token shops: the Carnival Tokens item lists every upgrade's remaining cost, tokens needed and still missing. The Souvenir Shop / Carnival Perks menus show the total over all shops (open each shop once).")
                            .tags("carnival", "token", "shop", "souvenir", "perks");
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if ((shop == null && !overview) || feature == null || !feature.active() || lines.isEmpty()) return;
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
                shop = null;
                overview = false;
                tips.clear();
            }
            return;
        }
        if (cs != lastScreen) {
            lastScreen = cs;
            ticks = 0;
            tips.clear();
            String title = StringUtil.stripTrim(cs.getTitle().getString());
            overview = title.equals("Souvenir Shop") || title.equals("Carnival Perks");
            shop = overview ? null : title;
        }
        if ((shop == null && !overview) || ticks++ % 10 != 0) return;
        load();
        if (!loaded) return;
        if (shop != null && shopKey(shop) == null) {
            shop = null;
            return;
        }
        if (shop != null) buildShop(cs);
        else buildOverview(cs);
    }

    private static String shopKey(String title) {
        for (String k : shops.keySet()) if (k.equalsIgnoreCase(title)) return k;
        return null;
    }

    private static Map<String, Map<String, Integer>> store() {
        return cfg().carnivalShopProgress;
    }

    private static String storeKey(String shopName) {
        return Carnival.profile() + "|" + shopName;
    }

    private static void buildShop(AbstractContainerScreen<?> cs) {
        String key = shopKey(shop);
        List<Upgrade> repo = shops.get(key);
        int size = Math.min(cs.getMenu().slots.size() - 36, 54);
        if (size <= 32) return;
        Map<String, Integer> purchased = new HashMap<>();
        for (int i = 11; i <= 15; i++) {
            ItemStack stack = cs.getMenu().getSlot(i).getItem();
            if (stack.isEmpty()) continue;
            Matcher m = UPGRADE.matcher(StringUtil.stripTrim(stack.getHoverName().getString()));
            if (!m.matches()) continue;
            int tier = roman(m.group(2));
            boolean maxed = ItemUtil.loreContains(stack, "UNLOCKED");
            purchased.put(m.group(1), maxed ? tier : tier - 1);
        }
        ItemStack footer = cs.getMenu().getSlot(32).getItem();
        if (footer.isEmpty() || !StringUtil.stripTrim(footer.getHoverName().getString()).equals("Carnival Tokens")) return;
        int owned = 0;
        for (String l : ItemUtil.loreLines(footer)) {
            Matcher m = TOKENS.matcher(l);
            if (m.find()) owned = (int) parse(m.group(1));
        }
        if (!purchased.isEmpty()) {
            Map<String, Integer> old = store().get(storeKey(key));
            if (!purchased.equals(old)) {
                store().put(storeKey(key), purchased);
                TeslaMapsConfig.save();
            }
        }

        List<String> t = new ArrayList<>();
        t.add("");
        t.add("§bRemaining " + key.replace('_', ' ') + " Token Upgrades:");
        long total = 0;
        for (Upgrade u : repo) {
            int have = findHave(purchased, u.name());
            long left = remaining(u, have);
            if (left <= 0) continue;
            total += left;
            t.add("  §a" + u.name() + " §b" + have + " §7-> §b" + u.costs().size() + "§7: §8" + fmt(left));
        }
        List<String> unknown = new ArrayList<>();
        for (String name : purchased.keySet()) {
            boolean known = false;
            for (Upgrade u : repo) if (u.name().equalsIgnoreCase(name)) known = true;
            if (!known) unknown.add(name);
        }
        if (total == 0) t.add("  §a§lAll upgrades purchased!");
        else needed(t, total, owned, "");
        if (!unknown.isEmpty()) {
            t.add("§cUpgrades not in repo:");
            for (String n : unknown) t.add("  §4" + n);
        }
        tips.clear();
        tips.put(footer, t);
    }

    private static void buildOverview(AbstractContainerScreen<?> cs) {
        List<String> t = new ArrayList<>();
        t.add("");
        t.add("§bRemaining Event Shop Token Upgrades:");
        long sum = 0;
        int found = 0;
        for (Map.Entry<String, List<Upgrade>> e : shops.entrySet()) {
            String name = e.getKey().replace('_', ' ');
            Map<String, Integer> p = store().get(storeKey(e.getKey()));
            if (p == null) {
                t.add("§7" + name + ": §copen shop to load...");
                continue;
            }
            found++;
            long left = 0;
            for (Upgrade u : e.getValue()) left += remaining(u, findHave(p, u.name()));
            t.add(left == 0 ? "§a" + name + "§7: §aall upgrades purchased!" : "§7" + name + ": §8" + fmt(left) + " tokens needed");
            sum += left;
        }
        if (sum > 0) needed(t, sum, 0, found != shops.size() ? "*" : "");
        Map<ItemStack, List<String>> next = new IdentityHashMap<>();
        int size = Math.min(cs.getMenu().slots.size() - 36, 54);
        for (int i = 0; i < size; i++) {
            ItemStack stack = cs.getMenu().getSlot(i).getItem();
            if (stack.isEmpty()) continue;
            String n = StringUtil.stripTrim(stack.getHoverName().getString());
            if (n.equals("Carnival Tokens") || shopKey(n) != null) next.put(stack, t);
        }
        tips.clear();
        tips.putAll(next);
    }

    private static void needed(List<String> t, long total, int owned, String mark) {
        t.add("§7Total Tokens Needed: §8" + fmt(total) + mark);
        if (owned > 0) t.add("§7Tokens Owned: §8" + fmt(owned));
        long missing = total - owned;
        if (missing > 0) t.add("§7Additional Tokens Needed: §8" + fmt(missing) + mark);
        else t.add("§eYou have enough tokens");
    }

    private static int findHave(Map<String, Integer> purchased, String name) {
        for (Map.Entry<String, Integer> e : purchased.entrySet()) if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
        return 0;
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
                JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonObject("carnivalTokenShops");
                Map<String, List<Upgrade>> parsed = new LinkedHashMap<>();
                for (var s : root.entrySet()) {
                    List<Upgrade> list = new ArrayList<>();
                    for (var e : s.getValue().getAsJsonObject().entrySet()) {
                        JsonObject o = e.getValue().getAsJsonObject();
                        if (!o.has("name") || !o.has("costs")) continue;
                        List<Integer> costs = new ArrayList<>();
                        for (JsonElement c : o.getAsJsonArray("costs")) costs.add(c.getAsInt());
                        list.add(new Upgrade(o.get("name").getAsString(), costs));
                    }
                    parsed.put(s.getKey().replace('_', ' '), list);
                }
                Minecraft.getInstance().execute(() -> {
                    shops.clear();
                    shops.putAll(parsed);
                    loaded = true;
                });
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Carnival shop data failed: {}", e.toString());
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
