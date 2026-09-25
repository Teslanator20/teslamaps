package com.teslamaps.features;

// Ported from SkyHanni's BazaarBestSellMethod.kt, MaxPurseItems.kt and BazaarLimitTracker.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.features.qol.QolStore;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BazaarHelpers {

    private static final double DAILY_LIMIT = 15_000_000_000.0;
    private static final Pattern TRANSACTION = Pattern.compile(
            "^\\[Bazaar] (Bought|Buy Order Setup!|Sold|Sell Offer Setup!|Order Flipped!) [\\d,]+x (.+) for ([\\d,.]+) coins");
    private static final Pattern UNIT = Pattern.compile("Price per unit: ([\\d,.]+) coins");
    private static final Pattern EACH = Pattern.compile("([\\d,.]+) coins each");
    private static final Pattern PURSE = Pattern.compile("^(?:Purse|Piggy): ([\\d,]+(?:\\.\\d+)?)");

    private static final EcoPanel panel = new EcoPanel(EcoPanel.Side.LEFT);
    private static Screen lastScreen;
    private static int ticks;
    private static Feature feature;

    private BazaarHelpers() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("BazaarHelpers").section("Inventory")
                .when(() -> cfg().bazaarHelpers)
                .tick(BazaarHelpers::tick)
                .serverChat(BazaarHelpers::onChat)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Bazaar Helpers");
                    b.toggle("Bazaar Helpers", () -> c.bazaarHelpers, v -> c.bazaarHelpers = v)
                            .desc("Small panel left of the bazaar: sell difference, max items with your purse, daily trade limit.")
                            .tags("bazaar", "purse", "limit", "sell offer", "instant sell");
                    b.toggle("  Sell Difference", () -> c.bazaarBestSell, v -> c.bazaarBestSell = v)
                            .desc("Product page: coins you lose selling your inventory amount instantly instead of with a sell offer.");
                    b.toggle("  Max Items With Purse", () -> c.bazaarMaxPurse, v -> c.bazaarMaxPurse = v)
                            .desc("Product page: how many you can afford with a buy order (+0.1) or an instant buy.");
                    b.toggle("  Daily Trade Limit", () -> c.bazaarDailyLimit, v -> c.bazaarDailyLimit = v)
                            .desc("Coins traded today (UTC) against the 15B daily limit, counted from your bazaar chat messages.");
                });
    }

    private static void onChat(String text) {
        String t = StringUtil.stripTrim(text);
        if (!t.startsWith("[Bazaar]")) return;
        Matcher m = TRANSACTION.matcher(t);
        if (!m.find() || m.group(1).equals("Order Flipped!")) return;
        double coins;
        try { coins = Double.parseDouble(m.group(3).replace(",", "")); } catch (NumberFormatException e) { return; }
        coins = Math.min(coins, Integer.MAX_VALUE);
        QolStore.Account a = QolStore.account();
        checkDay(a);
        boolean before = a.bazaarLimitCoins >= DAILY_LIMIT;
        a.bazaarLimitCoins += coins;
        QolStore.saveAccount();
        Minecraft mc = Minecraft.getInstance();
        if (!before && a.bazaarLimitCoins >= DAILY_LIMIT && cfg().bazaarDailyLimit && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §cYou reached your daily trade limit in the bazaar!"));
        }
    }

    private static void checkDay(QolStore.Account a) {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        if (!today.equals(a.bazaarLimitDay)) {
            a.bazaarLimitDay = today;
            a.bazaarLimitCoins = 0;
        }
    }

    private static boolean inBazaar(AbstractContainerScreen<?> cs) {
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        return title.startsWith("Bazaar") || title.contains("Bazaar Orders") || title.equals("How many do you want?")
                || title.equals("How much do you want to pay?") || title.startsWith("Confirm ")
                || title.equals("At what price are you selling?") || title.equals("Order options");
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || !inBazaar(cs)) {
            lastScreen = null;
            panel.clear();
            return;
        }
        if (lastScreen != mc.screen) {
            lastScreen = mc.screen;
            ticks = 0;
        }
        if (ticks++ % 5 != 0) return;
        PriceManager.ensureFresh();

        List<EcoPanel.Row> rows = new ArrayList<>();
        var slots = cs.getMenu().slots;
        boolean product = slots.size() > 13
                && StringUtil.stripTrim(slots.get(10).getItem().getHoverName().getString()).equals("Buy Instantly");
        if (product) {
            ItemStack item = slots.get(13).getItem();
            String name = StringUtil.stripTrim(item.getHoverName().getString());
            String id = PriceManager.idForName(name);
            if (id == null) id = SkyblockItems.idForName(name);
            if (id == null) id = ItemUtil.skyblockId(item);
            if (cfg().bazaarBestSell) sellDiff(rows, mc, cs, id, item.getHoverName().getString());
            if (cfg().bazaarMaxPurse) maxPurse(rows, cs);
        }
        if (cfg().bazaarDailyLimit) {
            QolStore.Account a = QolStore.account();
            checkDay(a);
            double used = a.bazaarLimitCoins;
            double left = DAILY_LIMIT - used;
            String col = left <= 0 ? "§c" : left < DAILY_LIMIT * 0.25 ? "§6" : left < DAILY_LIMIT * 0.5 ? "§e" : "§a";
            if (!rows.isEmpty()) rows.add(EcoPanel.Row.of(""));
            rows.add(EcoPanel.Row.of("§aBazaar Daily Limit:"));
            rows.add(EcoPanel.Row.of(col + String.format(Locale.ROOT, "%,d", (long) used) + "§7/" + EstimatedValue.fmt(DAILY_LIMIT) + " coins"));
            if (left <= 0) rows.add(EcoPanel.Row.of("§cLimit reached!"));
        }
        if (rows.isEmpty()) panel.clear();
        else panel.set(rows);
    }

    private static void sellDiff(List<EcoPanel.Row> rows, Minecraft mc, AbstractContainerScreen<?> cs, String id, String display) {
        if (id == null || id.isEmpty() || mc.player == null) return;
        long have = 0;
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && id.equals(ItemUtil.skyblockId(st))) have += st.getCount();
        }
        ItemStack carried = cs.getMenu().getCarried();
        if (!carried.isEmpty() && id.equals(ItemUtil.skyblockId(carried))) have += carried.getCount();
        if (have <= 0) return;
        Double buy = PriceManager.bazaarBuyOrNull(id), sell = PriceManager.bazaarSellOrNull(id);
        if (buy == null || sell == null) return;
        double diff = (buy - sell) * have;
        rows.add(EcoPanel.Row.of(display + "§7 sell difference: §6" + EstimatedValue.fmt(diff) + " coins"));
        rows.add(EcoPanel.Row.of("§8(" + String.format(Locale.ROOT, "%,d", have) + " in your inventory)"));
    }

    private static void maxPurse(List<EcoPanel.Row> rows, AbstractContainerScreen<?> cs) {
        double purse = purse();
        if (purse < 0) return;
        Double order = null, instant = null;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            String name = StringUtil.stripTrim(st.getHoverName().getString());
            boolean isOrder = name.equals("Create Buy Order"), isInstant = name.equals("Buy Instantly");
            if (!isOrder && !isInstant) continue;
            for (String l : ItemUtil.loreLines(st)) {
                Matcher m = (isOrder ? EACH : UNIT).matcher(l);
                if (!m.find()) continue;
                try {
                    double v = Double.parseDouble(m.group(1).replace(",", ""));
                    if (isOrder) order = v + 0.1;
                    else instant = v;
                } catch (NumberFormatException ignored) {}
                break;
            }
        }
        if (order == null && instant == null) return;
        if (!rows.isEmpty()) rows.add(EcoPanel.Row.of(""));
        rows.add(EcoPanel.Row.of("§7Max items with purse"));
        rows.add(EcoPanel.Row.of("§7Buy order +0.1: §e" + fmtCount(order == null ? 0 : purse / order) + "x"));
        rows.add(EcoPanel.Row.of("§7Instant buy: §e" + fmtCount(instant == null ? 0 : purse / instant) + "x"));
    }

    private static String fmtCount(double v) {
        return String.format(Locale.ROOT, "%,d", (long) Math.floor(v));
    }

    private static double purse() {
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            String t = ScoreboardUtils.cleanLine(line);
            if (!t.startsWith("Purse") && !t.startsWith("Piggy")) continue;
            Matcher m = PURSE.matcher(t);
            if (!m.find()) continue;
            try { return Double.parseDouble(m.group(1).replace(",", "")); } catch (NumberFormatException e) { return -1; }
        }
        return -1;
    }

    public static void render(GuiGraphicsExtractor g, int mx, int my) {
        if (feature == null || !feature.active() || panel.empty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != lastScreen || !(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        panel.render(g, cs, 0, mx, my);
    }
}
