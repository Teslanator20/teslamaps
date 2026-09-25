package com.teslamaps.features.hunting;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.EstimatedValue;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The two shard panels next to the container: what the hunting box is worth, and what is
 * still missing to max every attribute, cheapest first.
 */
public class ShardOverlay {

    private static final int ROW = 10;
    private static final int PAD = 4;
    private static final int MAX_ROWS = 14;

    public static final String SORT_NEXT = "Price to Next Tier";
    public static final String SORT_MAX = "Price to Max";
    public static final String[] SORTS = { SORT_NEXT, SORT_MAX };

    public static final String PRICE_INSTANT = "Instant Buy";
    public static final String PRICE_ORDER = "Buy Order";
    public static final String[] PRICE_SOURCES = { PRICE_INSTANT, PRICE_ORDER };

    private record Row(String text, List<String> tooltip, String bazaarQuery, int need, int slot, double sortKey, String key) {}

    private static final int SEARCH_H = ROW + 2;
    private static String query = "";
    private static boolean focused = false;
    private static List<String> buttons = List.of();
    private static int searchTop = -1, buttonTop = -1, panelLeft = 0;

    private static float scroll = 0;
    private static String lastClickQuery = null;
    private static int lastClickAmount = 0;
    private static int listTop = 0, listLeft = 0, listWidth = 0, visibleRows = 0;

    // built on the tick, drawn every frame - rebuilding 380 shards per frame was the whole cost
    private static List<String> header = List.of();
    private static List<String> footer = List.of();
    private static List<Row> rows = List.of();
    private static java.util.Map<Integer, Integer> slotTiers = java.util.Map.of();
    private static java.util.Set<Integer> disabledSlots = java.util.Set.of();
    private static int panelWidth = 0;

    public static boolean shouldRender() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (config.huntingBoxValue && HuntingBox.inBoxScreen()) return true;
        return config.shardOverlay && HuntingBox.inOverlayScreen();
    }

    /** Called from the throttled tick, never from the render path. */
    public static void update() {
        if (!shouldRender() && !menuExtrasWanted()) {
            header = footer = List.of();
            rows = List.of();
            slotTiers = java.util.Map.of();
            disabledSlots = java.util.Set.of();
            return;
        }
        PriceManager.ensureFresh();

        List<String> newHeader = new ArrayList<>();
        List<String> newFooter = new ArrayList<>();
        List<Row> newRows = shouldRender()
                ? (HuntingBox.inBoxScreen() ? boxRows(newHeader, newFooter) : overlayRows(newHeader, newFooter))
                : List.of();

        if (!ShardData.loaded()) newFooter.add("§7loading shard data…");
        else if (!PriceManager.isLoaded()) newFooter.add("§7loading prices…");

        if (config().shardPanelSearch && !query.isEmpty()) {
            List<Row> kept = new ArrayList<>();
            for (Row r : newRows) if (r.key().contains(query)) kept.add(r);
            newRows = kept;
        }
        buttons = shouldRender() && !HuntingBox.inBoxScreen() && config().shardPanelButtons ? buttonLines() : List.of();
        if (!config().shardPanelSearch) focused = false;

        header = newHeader;
        footer = newFooter;
        rows = newRows;

        Minecraft mc = Minecraft.getInstance();
        int width = 0;
        for (String l : header) width = Math.max(width, mc.font.width(l));
        for (String l : footer) width = Math.max(width, mc.font.width(l));
        for (Row r : rows) width = Math.max(width, mc.font.width(r.text()));
        for (String l : buttons) width = Math.max(width, mc.font.width(l));
        if (config().shardPanelSearch) width = Math.max(width, 120);
        panelWidth = Math.min(width, 240);

        updateMenuExtras(mc);
    }

    private static TeslaMapsConfig config() {
        return TeslaMapsConfig.get();
    }

    private static List<String> buttonLines() {
        TeslaMapsConfig c = config();
        List<String> out = new ArrayList<>();
        out.add("§7Sorted By: §e" + c.shardSort);
        out.add("§7Price Source: §e" + c.shardPriceSource);
        out.add(onOff("Hide Maxed", c.shardHideMaxed));
        out.add(onOff("Only Not Unlocked", c.shardOnlyMissing));
        out.add(onOff("Include Hunting Box", c.shardIncludeBox));
        out.add(onOff("Include Bazaar Orders", c.shardIncludeOrders));
        out.add(onOff("Only Current Menu", c.shardOnlyCurrent));
        if (c.shardIncludeBox) out.add("§cReset hunting box shards");
        return out;
    }

    private static String onOff(String label, boolean on) {
        return "§7" + label + ": " + (on ? "§aon" : "§coff");
    }

    private static void pressButton(int index) {
        TeslaMapsConfig c = config();
        switch (index) {
            case 0 -> c.shardSort = SORT_MAX.equals(c.shardSort) ? SORT_NEXT : SORT_MAX;
            case 1 -> c.shardPriceSource = PRICE_ORDER.equals(c.shardPriceSource) ? PRICE_INSTANT : PRICE_ORDER;
            case 2 -> c.shardHideMaxed = !c.shardHideMaxed;
            case 3 -> c.shardOnlyMissing = !c.shardOnlyMissing;
            case 4 -> c.shardIncludeBox = !c.shardIncludeBox;
            case 5 -> c.shardIncludeOrders = !c.shardIncludeOrders;
            case 6 -> c.shardOnlyCurrent = !c.shardOnlyCurrent;
            case 7 -> ShardData.resetBox();
            default -> { return; }
        }
        TeslaMapsConfig.save();
        update();
    }

    private static boolean menuExtrasWanted() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        return (config.shardTierStack || config.shardHighlightDisabled || config.shardHighlightProgress)
                && HuntingBox.inOverlayScreen();
    }

    /** Tier and disabled state per slot, so the render pass only does lookups. */
    private static void updateMenuExtras(Minecraft mc) {
        if (!menuExtrasWanted() || !(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            slotTiers = java.util.Map.of();
            disabledSlots = java.util.Set.of();
            return;
        }
        TeslaMapsConfig config = TeslaMapsConfig.get();
        java.util.Map<Integer, Integer> tiers = new java.util.HashMap<>();
        java.util.Set<Integer> disabled = new java.util.HashSet<>();
        List<net.minecraft.world.inventory.Slot> slots = cs.getMenu().slots;
        for (int i = 0; i < slots.size(); i++) {
            net.minecraft.world.inventory.Slot slot = slots.get(i);
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            if (slot.getItem().isEmpty()) continue;
            int tier = HuntingBox.menuTier(slot.getItem());
            if (tier < 0 || (tier == 0 && !config.shardTierColors && !config.shardHighlightProgress)) continue;
            tiers.put(i, tier);
            if (HuntingBox.isDisabled(slot.getItem())) disabled.add(i);
        }
        slotTiers = tiers;
        disabledSlots = disabled;
    }

    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        if (header.isEmpty() && rows.isEmpty() && footer.isEmpty()) return;
        draw(ctx, mc, cs, header, rows, footer, mouseX, mouseY);
    }

    // ---------------------------------------------------------------- hunting box value

    private static List<Row> boxRows(List<String> header, List<String> footer) {
        int totalShards = 0;
        double totalSell = 0, totalBuy = 0;
        List<Row> rows = new ArrayList<>();

        for (HuntingBox.BoxEntry entry : HuntingBox.boxEntries()) {
            if (entry.amount() <= 0) continue;
            totalShards += entry.amount();

            double perSell = price(entry.shard(), PRICE_ORDER);
            double perBuy = price(entry.shard(), PRICE_INSTANT);
            double sell = perSell * entry.amount();
            double buy = perBuy * entry.amount();
            totalSell += sell;
            totalBuy += buy;

            List<String> tooltip = new ArrayList<>();
            tooltip.add(rarityColor(entry.shard()) + entry.shard().displayName());
            tooltip.add("");
            tooltip.add("§7Owned: §a" + separators(entry.amount()));
            tooltip.add("§7Instant sell each: §6" + EstimatedValue.fmt(perSell));
            tooltip.add("§7Instant buy each: §6" + EstimatedValue.fmt(perBuy));
            tooltip.add("");
            tooltip.add("§7Total instant sell: §6" + EstimatedValue.fmt(sell));
            tooltip.add("§7Total instant buy: §6" + EstimatedValue.fmt(buy));

            rows.add(new Row(" " + rarityColor(entry.shard()) + entry.shard().displayName()
                    + " §8x" + entry.amount() + " §7- §6" + EstimatedValue.fmt(sell),
                    tooltip, entry.shard().displayName(), 0, entry.slot(), sell,
                    entry.shard().displayName().toLowerCase(Locale.ROOT)));
        }
        rows.sort(Comparator.comparingDouble(Row::sortKey).reversed());

        header.add("§6§lHunting Box Value");
        header.add("§7Shards: §a" + separators(totalShards));
        header.add("§7Instant sell: §6" + EstimatedValue.fmt(totalSell));
        header.add("§7Instant buy: §6" + EstimatedValue.fmt(totalBuy));
        if (rows.isEmpty()) footer.add("§7No shards in the box");
        return rows;
    }

    // ---------------------------------------------------------------- what is left to max

    private static List<Row> overlayRows(List<String> header, List<String> footer) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        boolean byMax = SORT_MAX.equals(config.shardSort);
        java.util.Map<String, Integer> slotOfShard = shardSlots();

        int unlocked = 0, maxed = 0, levels = 0, known = 0;
        double totalToMax = 0;
        List<Row> rows = new ArrayList<>();

        for (ShardData.Shard shard : ShardData.all()) {
            if (!ShardData.isConsumable(shard)) continue;
            int syphoned = ShardData.syphoned(shard);
            if (TeslaMapsConfig.get().shardsSyphoned.containsKey(shard.bazaarId())) known++;

            int[] tiers = ShardData.tiers(shard, syphoned);
            int tier = tiers[0], toNext = tiers[1], toMax = tiers[2];
            if (tier >= 10) maxed++;
            if (tier > 0) { unlocked++; levels += tier; }

            int onOrder = config.shardIncludeOrders ? BazaarOrders.openBuy(shard) : 0;
            int held = ShardData.inInventory(shard);
            int covered = (config.shardIncludeBox ? ShardData.inBox(shard) : 0) + onOrder + held;
            int needNext = Math.max(0, toNext - covered);
            int needMax = Math.max(0, toMax - covered);

            double per = price(shard, config.shardPriceSource);
            double priceNext = per * needNext;
            double priceMax = per * needMax;
            totalToMax += priceMax;

            if (config.shardHideMaxed && tier >= 10) continue;
            if (config.shardOnlyMissing && tier > 0) continue;
            if (config.shardOnlyCurrent && !slotOfShard.containsKey(shard.bazaarId())) continue;

            int need = byMax ? needMax : needNext;
            double shown = byMax ? priceMax : priceNext;

            List<String> tooltip = new ArrayList<>();
            tooltip.add(rarityColor(shard) + shard.displayName() + " §8(" + shard.rarity().toLowerCase(Locale.ROOT) + ")");
            tooltip.add("§7Attribute: §f" + shard.abilityName());
            tooltip.add("§7Tier: §e" + tier + "§7/§e10");
            tooltip.add("§7Price per shard: §6" + EstimatedValue.fmt(per));
            tooltip.add("§7In hunting box: §a" + separators(ShardData.inBox(shard)));
            if (held > 0) tooltip.add("§7In inventory: §b" + separators(held));
            if (onOrder > 0) tooltip.add("§7On order: §b" + separators(onOrder));
            if (tier < 10) {
                tooltip.add("");
                tooltip.add("§7To next tier: §a" + separators(needNext) + " §7= §6" + EstimatedValue.fmt(priceNext));
                tooltip.add("§7To max: §a" + separators(needMax) + " §7= §6" + EstimatedValue.fmt(priceMax));
            }
            tooltip.add("");
            boolean bought = onOrder > 0 || held > 0;
            tooltip.add(need > 0 ? "§eClick: bazaar lookup + " + need + " copied to the clipboard"
                    : bought ? "§bAlready bought" + (onOrder > 0 ? " - waiting on the order" : " - still in your inventory")
                    : "§aNothing needed");

            String priceText = tier >= 10 ? "§a§lMaxed"
                    : need > 0 ? "§6" + EstimatedValue.fmt(shown) + " §8(" + need + "x)"
                    : bought ? "§bAlready bought"
                    : "§aowned";
            // nothing to buy sinks to the bottom, so the top of the list is what to buy next
            double sortKey = need > 0 ? shown : Double.MAX_VALUE;
            rows.add(new Row(" " + rarityColor(shard) + shard.displayName() + " §7" + tier + " " + priceText,
                    tooltip, need > 0 ? shard.displayName() : null, need,
                    slotOfShard.getOrDefault(shard.bazaarId(), -1), sortKey,
                    shard.displayName().toLowerCase(Locale.ROOT) + " " + shard.abilityName().toLowerCase(Locale.ROOT)));
        }
        rows.sort(Comparator.comparingDouble(Row::sortKey));

        int max = ShardData.maxShards();
        header.add("§6§lShard Overlay");
        header.add("§7Unlocked: §a" + unlocked + "§7/§a" + max);
        header.add("§7Maxed: §a" + maxed + "§7/§a" + max);
        header.add("§7Levels: §a" + levels + "§7/§a" + max * 10);
        if (max > 0 && known < max) {
            header.add("§cNo data for " + (max - known) + " shards - open /attributemenu");
        }
        footer.add("§7Total to max: §6" + EstimatedValue.fmt(totalToMax));
        footer.add("§8sorted by " + (byMax ? "price to max" : "price to next tier"));
        if (rows.isEmpty()) footer.add("§7Nothing to show - check the filters");
        return rows;
    }

    /** Which slot of the open menu holds which shard, so a hovered row can point at it. */
    private static java.util.Map<String, Integer> shardSlots() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return java.util.Map.of();

        java.util.Map<String, Integer> out = new java.util.HashMap<>();
        List<net.minecraft.world.inventory.Slot> slots = cs.getMenu().slots;
        for (int i = 0; i < slots.size(); i++) {
            net.minecraft.world.inventory.Slot slot = slots.get(i);
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            if (slot.getItem().isEmpty()) continue;
            ShardData.Shard shard = HuntingBox.shardOf(slot.getItem());
            if (shard != null) out.putIfAbsent(shard.bazaarId(), i);
        }
        return out;
    }

    // ---------------------------------------------------------------- drawing

    private static void draw(GuiGraphicsExtractor ctx, Minecraft mc, AbstractContainerScreen<?> cs,
                             List<String> header, List<Row> rows, List<String> footer, int mouseX, int mouseY) {
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        visibleRows = Math.min(MAX_ROWS, rows.size());
        int lineCount = header.size() + visibleRows + footer.size() + buttons.size();
        if (lineCount == 0) return;
        boolean search = config().shardPanelSearch;

        int width = panelWidth;
        int x = acc.getX() + 176 + 6;
        if (x + width + PAD * 2 > mc.getWindow().getGuiScaledWidth()) {
            x = Math.max(2, acc.getX() - width - PAD * 2 - 6);
        }
        int y = acc.getY();
        int height = lineCount * ROW + (search ? SEARCH_H : 0);
        int ow = width + PAD * 2, oh = height + PAD * 2;
        x = com.teslamaps.features.ContainerPanelMover.x(com.teslamaps.features.ContainerPanelMover.SHARD, x - PAD, ow) + PAD;
        y = com.teslamaps.features.ContainerPanelMover.y(com.teslamaps.features.ContainerPanelMover.SHARD, y - PAD, oh) + PAD;
        com.teslamaps.features.ContainerPanelMover.record(com.teslamaps.features.ContainerPanelMover.SHARD, x - PAD, y - PAD, ow, oh);
        panelLeft = x;

        HudStyle.panel(ctx, x - PAD, y - PAD, width + PAD * 2f, height + PAD * 2f);

        int cursor = y;
        for (String line : header) {
            ctx.text(mc.font, line, x, cursor, 0xFFFFFFFF);
            cursor += ROW;
        }

        searchTop = -1;
        if (search) {
            searchTop = cursor;
            int bw = width, bh = ROW;
            ctx.fill(x, cursor, x + bw, cursor + bh, focused ? 0xFF202020 : 0xC0202020);
            int border = focused ? 0xFFFFD040 : 0xFF606060;
            ctx.fill(x, cursor, x + bw, cursor + 1, border);
            ctx.fill(x, cursor + bh - 1, x + bw, cursor + bh, border);
            ctx.fill(x, cursor, x + 1, cursor + bh, border);
            ctx.fill(x + bw - 1, cursor, x + bw, cursor + bh, border);
            String label = query.isEmpty() ? (focused ? "§7type to search…" : "§8click to search")
                    : "§f" + query + (focused ? "§e_" : "");
            ctx.text(mc.font, label, x + 3, cursor + 1, 0xFFFFFFFF);
            cursor += SEARCH_H;
        }

        listLeft = x;
        listTop = cursor;
        listWidth = width;
        int maxScroll = Math.max(0, rows.size() - visibleRows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int offset = (int) scroll;

        List<String> hover = null;
        lastClickQuery = null;
        lastClickAmount = 0;
        int hoverSlot = -1;
        for (int i = 0; i < visibleRows; i++) {
            Row row = rows.get(offset + i);
            boolean hovered = mouseX >= x - PAD && mouseX <= x + width + PAD
                    && mouseY >= cursor && mouseY < cursor + ROW;
            if (hovered) {
                ctx.fill(x - PAD + 1, cursor, x + width + PAD - 1, cursor + ROW, 0x33FFFFFF);
                hover = row.tooltip();
                lastClickQuery = row.bazaarQuery();
                lastClickAmount = row.need();
                hoverSlot = row.slot();
            }
            ctx.text(mc.font, row.text(), x, cursor, 0xFFFFFFFF);
            cursor += ROW;
        }

        if (hoverSlot >= 0 && hoverSlot < cs.getMenu().slots.size()) {
            net.minecraft.world.inventory.Slot slot = cs.getMenu().slots.get(hoverSlot);
            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            ctx.fill(sx, sy, sx + 16, sy + 16, 0x66FFFF55);
            ctx.fill(sx, sy, sx + 16, sy + 1, 0xFFFFFF55);
            ctx.fill(sx, sy + 15, sx + 16, sy + 16, 0xFFFFFF55);
            ctx.fill(sx, sy, sx + 1, sy + 16, 0xFFFFFF55);
            ctx.fill(sx + 15, sy, sx + 16, sy + 16, 0xFFFFFF55);
        }

        for (String line : footer) {
            ctx.text(mc.font, line, x, cursor, 0xFFFFFFFF);
            cursor += ROW;
        }

        buttonTop = buttons.isEmpty() ? -1 : cursor;
        for (String line : buttons) {
            boolean hovered = mouseX >= x - PAD && mouseX <= x + width + PAD && mouseY >= cursor && mouseY < cursor + ROW;
            if (hovered) ctx.fill(x - PAD + 1, cursor, x + width + PAD - 1, cursor + ROW, 0x33FFFFFF);
            ctx.text(mc.font, line, x, cursor, 0xFFFFFFFF);
            cursor += ROW;
        }

        if (maxScroll > 0) {
            int trackTop = listTop;
            int trackHeight = visibleRows * ROW;
            int thumb = Math.max(6, trackHeight / (maxScroll + 1));
            int thumbY = trackTop + (int) ((trackHeight - thumb) * (scroll / maxScroll));
            ctx.fill(x + width + 1, trackTop, x + width + 3, trackTop + trackHeight, 0x66000000);
            ctx.fill(x + width + 1, thumbY, x + width + 3, thumbY + thumb, 0xFFAAAAAA);
        }

        if (hover != null) {
            List<Component> lines = new ArrayList<>(hover.size());
            for (String line : hover) lines.add(Component.literal(line));
            ctx.setComponentTooltipForNextFrame(mc.font, lines, mouseX, mouseY);
        }
    }

    /**
     * Attribute menu extras: the tier where vanilla would draw a stack size (the items are all
     * stacks of one) and a red tint over the attributes that are switched off.
     */
    private static final String[] TIER_TEXT = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"};

    public static void renderMenuExtras(GuiGraphicsExtractor ctx) {
        if (slotTiers.isEmpty()) return;
        TeslaMapsConfig config = TeslaMapsConfig.get();

        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        List<net.minecraft.world.inventory.Slot> slots = cs.getMenu().slots;

        for (java.util.Map.Entry<Integer, Integer> entry : slotTiers.entrySet()) {
            int index = entry.getKey();
            if (index >= slots.size()) continue;
            net.minecraft.world.inventory.Slot slot = slots.get(index);
            int tier = entry.getValue();
            int x = acc.getX() + slot.x, y = acc.getY() + slot.y;

            if (config.shardHighlightProgress) {
                ctx.fill(x, y, x + 16, y + 16, tier >= 10 ? 0x6655FF55 : 0x66FF5555);
            } else if (config.shardHighlightDisabled && disabledSlots.contains(index)) {
                ctx.fill(x, y, x + 16, y + 16, 0x66FF5555);
            }
            if (config.shardTierStack && (tier > 0 || config.shardTierColors)
                    && com.teslamaps.features.SlotText.shown(com.teslamaps.features.SlotText.SHARD)
                    && !(tier >= 10 && config.shardHideMaxedTier)) {
                String text = TIER_TEXT[Math.min(tier, 10)];
                int color = !config.shardTierColors ? (tier >= 10 ? 0xFF55FF55 : 0xFFFFFFFF)
                        : tier == 0 ? 0xFFFF5555 : tier <= 5 ? 0xFFFFFF55 : tier <= 9 ? 0xFF55FF55 : 0xFFFFAA00;
                ctx.text(mc.font, text, x + 17 - mc.font.width(text), y + 9, color);
            }
        }
    }

    // ---------------------------------------------------------------- input

    public static boolean mouseScrolled(double amount) {
        if (!shouldRender() || rows.size() <= visibleRows) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) return false;
        scroll = Math.max(0, Math.min(rows.size() - visibleRows, scroll - (float) amount));
        return true;
    }

    public static boolean handleClick(double mouseX, double mouseY, int button) {
        if (!shouldRender()) return false;
        boolean inPanel = mouseX >= panelLeft - PAD && mouseX <= panelLeft + panelWidth + PAD;
        if (config().shardPanelSearch && searchTop >= 0) {
            boolean inBox = inPanel && mouseY >= searchTop && mouseY < searchTop + ROW;
            if (inBox && button == 1) {
                setQuery("");
                return true;
            }
            if (button == 0) focused = inBox;
            if (inBox) return true;
        }
        if (button == 0 && inPanel && buttonTop >= 0 && mouseY >= buttonTop && mouseY < buttonTop + buttons.size() * ROW) {
            pressButton((int) ((mouseY - buttonTop) / ROW));
            return true;
        }
        if (button != 0 || lastClickQuery == null) return false;
        if (mouseX < listLeft - PAD || mouseX > listLeft + listWidth + PAD) return false;
        if (mouseY < listTop || mouseY >= listTop + visibleRows * ROW) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return false;

        // the bazaar asks for the amount in a sign, so have it ready to paste
        if (lastClickAmount > 0) mc.keyboardHandler.setClipboard(String.valueOf(lastClickAmount));
        com.teslamaps.features.bazaar.BazaarPurchaseHelper.expect(lastClickQuery + " Shard");
        mc.getConnection().sendCommand("bz " + lastClickQuery + " Shard");
        return true;
    }

    private static void setQuery(String q) {
        if (q.equals(query)) return;
        query = q;
        scroll = 0;
        update();
    }

    public static boolean onKeyPressed(int key) {
        if (!focused || !config().shardPanelSearch || !shouldRender()) return false;
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            if (!query.isEmpty()) setQuery(query.substring(0, query.length() - 1));
        } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
            focused = false;
        }
        return true;
    }

    public static boolean charTyped(int codepoint) {
        if (!focused || !config().shardPanelSearch || !shouldRender()) return false;
        if (codepoint < 32 || codepoint == 127) return false;
        setQuery(query + Character.toString(Character.toLowerCase(codepoint)));
        return true;
    }

    // ---------------------------------------------------------------- helpers

    private static double price(ShardData.Shard shard, String source) {
        Double value = PRICE_ORDER.equals(source)
                ? PriceManager.bazaarSellOrNull(shard.bazaarId())
                : PriceManager.bazaarBuyOrNull(shard.bazaarId());
        return value == null ? 0 : value;
    }

    private static String rarityColor(ShardData.Shard shard) {
        return switch (shard.rarity()) {
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC" -> "§d";
            default -> "§f";
        };
    }

    private static String separators(int value) {
        return NumberFormat.getNumberInstance(Locale.US).format(value);
    }

    private ShardOverlay() {}
}
