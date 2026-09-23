package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

// Bazaar order books list orders with a "By: PlayerName" lore line - highlight your own in green
public class BazaarOwnOrders {

    private static final IdentityHashMap<ItemStack, Boolean> cache = new IdentityHashMap<>();
    private static final IdentityHashMap<ItemStack, Boolean> filledCache = new IdentityHashMap<>();

    // Order status after Skysoft's BazaarTrackerScreens + SkyHanni's BazaarOrderApi, icons after Skyblocker's BazaarHelper.
    private enum Status { FILLED, OUTBID, COMPETITIVE, NONE }
    private record OrderInfo(Status status, boolean partial, int clock) {}

    private static final Pattern NAME = Pattern.compile("^(BUY|SELL) (.+)$");
    private static final Pattern PRICE = Pattern.compile("^Price per unit: ([\\d,.]+) coins");
    private static final Pattern FILLED = Pattern.compile("^Filled: ([\\d,.kKmM]+)/[\\d,.kKmM]+ (\\S+)");
    private static final Pattern SETUP = Pattern.compile("^\\[Bazaar] (?:Buy Order|Sell Offer) Setup! [\\d,]+x (.+) for [\\d,.]+ coins\\.");
    private static final double EPSILON = 0.01;

    private static final IdentityHashMap<ItemStack, OrderInfo> statusCache = new IdentityHashMap<>();
    private static long statusCacheMarket = -1;
    // item name -> when an order for it was set up; warn only once the market data is newer
    private static final Map<String, Long> setupAt = new ConcurrentHashMap<>();

    public static void register() {
        Feature.of("BazaarOrderStatus")
                .when(() -> TeslaMapsConfig.get().bazaarOrderStatus)
                .serverChat(BazaarOwnOrders::onChat);
    }

    private static void onChat(String text) {
        String l = com.teslamaps.utils.StringUtil.stripTrim(text);
        if (!l.startsWith("[Bazaar]")) return;
        Matcher m = SETUP.matcher(l);
        if (m.find()) setupAt.put(m.group(1).toLowerCase(), System.currentTimeMillis());
    }

    public static void render(GuiGraphicsExtractor ctx) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.bazaarOwnOrders && !config.bazaarOrderFilled && !config.bazaarOrderStatus) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || mc.player == null) return;

        String title = com.teslamaps.utils.StringUtil.strip(cs.getTitle().getString());
        if (!title.contains("Bazaar") && !title.contains("➜") && !title.contains("Orders")) return;
        boolean statusMode = config.bazaarOrderStatus && title.contains("Bazaar Orders");
        if (statusMode) {
            PriceManager.ensureFresh();
            long market = PriceManager.bazaarUpdatedAt();
            if (market != statusCacheMarket || statusCache.size() > 256) {
                statusCache.clear();
                statusCacheMarket = market;
            }
        }

        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        String ownName = mc.player.getName().getString();

        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;

            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            if (statusMode) {
                OrderInfo info = status(item);
                if (info != null && (info.status() != Status.NONE || info.partial() || info.clock() != 0)) {
                    drawStatus(ctx, mc, config, info, sx, sy);
                    continue;
                }
            }
            if (config.bazaarOwnOrders && isOwnOrder(item, ownName)) {
                box(ctx, sx, sy, 0x6655FF55, 0xFF55FF55);
            }
            if (config.bazaarOrderFilled && isFilled(item)) {
                ctx.text(mc.font, "✔", sx + 1, sy, 0xFF55FF55);
            }
        }
    }

    private static void box(GuiGraphicsExtractor ctx, int sx, int sy, int fill, int outline) {
        ctx.fill(sx, sy, sx + 16, sy + 16, fill);
        ctx.fill(sx, sy, sx + 16, sy + 1, outline);
        ctx.fill(sx, sy + 15, sx + 16, sy + 16, outline);
        ctx.fill(sx, sy, sx + 1, sy + 16, outline);
        ctx.fill(sx + 15, sy, sx + 16, sy + 16, outline);
    }

    private static void drawStatus(GuiGraphicsExtractor ctx, Minecraft mc, TeslaMapsConfig c, OrderInfo info, int sx, int sy) {
        String hex = switch (info.status()) {
            case FILLED -> c.colorBazaarOrderFilled;
            case OUTBID -> c.colorBazaarOrderOutbid;
            case COMPETITIVE -> c.colorBazaarOrderCompetitive;
            case NONE -> null;
        };
        if (hex != null) {
            int rgb = TeslaMapsConfig.parseColor(hex) & 0x00FFFFFF;
            box(ctx, sx, sy, 0x66000000 | rgb, 0xFF000000 | rgb);
        }
        if (info.status() == Status.FILLED) ctx.text(mc.font, "✔", sx + 1, sy, 0xFF55FF55);
        else if (info.partial()) ctx.text(mc.font, "%", sx + 1, sy, 0xFFE6BA0B);
        if (info.clock() != 0) ctx.text(mc.font, "⏰", sx + 9, sy, info.clock());
    }

    private static OrderInfo status(ItemStack item) {
        if (statusCache.containsKey(item)) return statusCache.get(item);
        OrderInfo info = parse(item);
        statusCache.put(item, info);
        return info;
    }

    private static OrderInfo parse(ItemStack item) {
        String name = com.teslamaps.utils.StringUtil.stripTrim(item.getHoverName().getString());
        Matcher nm = NAME.matcher(name);
        if (!nm.matches()) return null;
        boolean buy = nm.group(1).equals("BUY");
        String itemName = nm.group(2);

        ItemLore lore = item.get(DataComponents.LORE);
        if (lore == null) return null;
        Double price = null;
        boolean full = false, partial = false;
        int clock = 0;
        for (Component cmp : lore.lines()) {
            String l = com.teslamaps.utils.StringUtil.stripTrim(cmp.getString());
            Matcher pm = PRICE.matcher(l);
            if (pm.find()) {
                try { price = Double.parseDouble(pm.group(1).replace(",", "")); } catch (NumberFormatException ignored) {}
                continue;
            }
            Matcher fm = FILLED.matcher(l);
            if (fm.find()) {
                full = fm.group(2).equals("100%!");
                partial = !full && !fm.group(1).equals("0");
                continue;
            }
            if (l.equals("Expired!")) clock = 0xFFE60B1E;
            else if (l.startsWith("Expires in") && clock == 0) clock = 0xFFE6BA0B;
        }
        if (full) return new OrderInfo(Status.FILLED, false, clock);
        if (price == null) return new OrderInfo(Status.NONE, partial, clock);
        return new OrderInfo(market(buy, itemName, price), partial, clock);
    }

    private static Status market(boolean buy, String itemName, double price) {
        String id = PriceManager.idForName(itemName);
        if (id == null) id = com.teslamaps.utils.SkyblockItems.idForName(itemName);
        if (id == null) return Status.NONE;
        Double best = buy ? PriceManager.bazaarBuyOrderOrNull(id) : PriceManager.bazaarSellOfferOrNull(id);
        if (best == null) return Status.NONE;
        boolean ok = buy ? price + EPSILON >= best : price <= best + EPSILON;
        if (ok) return Status.COMPETITIVE;
        Long placed = setupAt.get(itemName.toLowerCase());
        // market data older than the order would flag every fresh order
        if (placed != null && PriceManager.bazaarUpdatedAt() <= placed) return Status.COMPETITIVE;
        return Status.OUTBID;
    }

    private static boolean isFilled(ItemStack item) {
        Boolean cached = filledCache.get(item);
        if (cached != null) return cached;
        if (filledCache.size() > 512) filledCache.clear();

        boolean filled = false;
        ItemLore lore = item.get(DataComponents.LORE);
        if (lore != null) {
            for (Component cmp : lore.lines()) {
                String l = com.teslamaps.utils.StringUtil.stripTrim(cmp.getString());
                if (l.startsWith("Filled:") && l.endsWith("100%!")) {
                    filled = true;
                    break;
                }
            }
        }
        filledCache.put(item, filled);
        return filled;
    }

    private static boolean isOwnOrder(ItemStack item, String ownName) {
        Boolean cached = cache.get(item);
        if (cached != null) return cached;
        if (cache.size() > 512) cache.clear();

        boolean own = false;
        ItemLore lore = item.get(DataComponents.LORE);
        if (lore != null) {
            for (Component cmp : lore.lines()) {
                String l = com.teslamaps.utils.StringUtil.stripTrim(cmp.getString());
                if (!l.startsWith("By: ")) continue;
                String name = l.substring(4).trim();
                int sp = name.lastIndexOf(' ');
                if (sp >= 0) name = name.substring(sp + 1); // drop the "[MVP+]" rank prefix
                if (name.equalsIgnoreCase(ownName) || name.equalsIgnoreCase("You")) own = true;
                break;
            }
        }
        cache.put(item, own);
        return own;
    }
}
