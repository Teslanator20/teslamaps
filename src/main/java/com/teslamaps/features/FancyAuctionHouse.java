package com.teslamaps.features;

// Lean port of Skyblocker's skyblock/auction (AuctionBrowserScreen, AuctionViewScreen, EditBidPopup):
// drawn over the Hypixel chest, every button clicks the real slot
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.AbstractSignEditScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.TeslaGui;
import com.teslamaps.utils.Calculator;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.SignBlockEntity;

import java.util.List;
import java.util.Locale;

public final class FancyAuctionHouse {

    private enum Mode { NONE, BROWSER, VIEW, CONFIRM }

    private enum State { NONE, CANT_AFFORD, AFFORD, TOP_BID, COLLECT, CANCELLABLE, OWN, GRACE }

    // browser slots
    private static final int PREV = 46, RESET = 47, SEARCH = 48, BACK = 49, SORT = 50, RARITY = 51, TYPE = 52, NEXT = 53;
    private static final int CELL = 18;
    private static final int BW = 184, BH = 122;
    private static final int VW = 184, VH = 124;

    private static Mode mode = Mode.NONE;
    private static Screen modeScreen;
    private static boolean vanilla, own;
    private static long lastClick, viewAt, editBidAt;

    // browser state, built in tick
    private static final int[] ITEM_SLOTS = new int[24];
    private static final boolean[] TAB_SELECTED = new boolean[6];
    private static int page = 1, pages = 1;
    private static boolean prevOk, nextOk, resetOk;
    private static String search = "", sortText = "", rarityText = "", typeText = "";

    // view state
    private static State state = State.NONE;
    private static boolean bin, wonAuction, changeProfile;
    private static int buySlot = -1;
    private static String price = "?", minBid = "", youPay = "", grace = "", title = "";

    static {
        int n = 0;
        for (int i = 10; i < 45 && n < 24; i++) {
            int col = i % 9;
            if (col > 1 && col < 8) ITEM_SLOTS[n++] = i;
        }
    }

    private FancyAuctionHouse() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static final Feature FEATURE = Feature.of("FancyAuctionHouse").section("Inventory")
            .when(() -> cfg().fancyAuctionBrowser || cfg().fancyAuctionView);

    public static void register() {
        FEATURE.tick(FancyAuctionHouse::tick)
                .config("Inventory", b -> {
                    b.label("Fancy Auction House");
                    b.toggle("Fancy Auction Browser", () -> cfg().fancyAuctionBrowser, v -> cfg().fancyAuctionBrowser = v)
                            .desc("Auctions Browser as a compact panel: category tabs, 24-item grid, search, page x/y with scrollbar, sort / rarity / BIN buttons (left click forward, right click back), arrow keys or scroll to flip pages.")
                            .tags("ah", "auction", "browser", "fancy");
                    b.toggle("Fancy Auction View", () -> cfg().fancyAuctionView, v -> cfg().fancyAuctionView = v)
                            .desc("Single auction as a clear card: big item and price, state (Can't Afford, Top Bid, Your Auction, grace period countdown), one Buy/Bid/Collect/Cancel button, own confirm popup and an Edit Bid popup with Set Minimum Bid and a calculator.")
                            .tags("ah", "auction", "bid", "bin", "fancy");
                    b.info("Each panel has a Vanilla button that shows Hypixel's menu until the auction house is closed");
                });
        ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
            if (!(screen instanceof AbstractSignEditScreen) || !cfg().fancyAuctionView || !FEATURE.active()) return;
            if (System.currentTimeMillis() - editBidAt > 3000) return;
            editBidAt = 0;
            SignBlockEntity sign = ((AbstractSignEditScreenAccessor) screen).getSign();
            if (sign == null) return;
            String[] lines = new String[4];
            for (int i = 0; i < 4; i++) lines[i] = sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).getMessages(false).get(i).getString();
            String min = minBid;
            mc.execute(() -> {
                EditBidScreen next = new EditBidScreen(sign, lines, min);
                mc.gui.screen = next;
                next.init(width, height);
            });
        });
    }

    private static Mode modeFor(String t, int rows) {
        if (rows != 6 && rows != 4 && rows != 5) return Mode.NONE;
        String low = t.toLowerCase(Locale.ROOT);
        if (cfg().fancyAuctionBrowser && rows == 6 && (low.equals("auctions browser") || low.startsWith("auctions: "))) return Mode.BROWSER;
        if (cfg().fancyAuctionView && low.endsWith("auction view")) return Mode.VIEW;
        if (cfg().fancyAuctionView && (low.equals("confirm purchase") || low.equals("confirm bid"))
                && System.currentTimeMillis() - viewAt < 15_000) return Mode.CONFIRM;
        return Mode.NONE;
    }

    private static boolean ahTitle(String t) {
        String low = t.toLowerCase(Locale.ROOT);
        return low.contains("auction") || low.startsWith("confirm") || low.equals("bid history");
    }

    /** Worked out once per screen, so render and input never touch the title. */
    private static Mode modeOf(AbstractContainerScreen<?> cs) {
        if (cs == modeScreen) return mode;
        modeScreen = cs;
        String t = StringUtil.stripTrim(cs.getTitle().getString());
        if (!ahTitle(t)) vanilla = false;
        int rows = cs.getMenu().slots.size() / 9 - 4;
        mode = modeFor(t, rows);
        if (mode == Mode.VIEW) resetView(t);
        build(cs);
        return mode;
    }

    private static void build(AbstractContainerScreen<?> cs) {
        if (mode == Mode.BROWSER) buildBrowser(cs);
        else if (mode == Mode.VIEW) {
            buildView(cs, cs.getMenu().slots.size() / 9 - 4);
            viewAt = System.currentTimeMillis();
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) {
            if (!(mc.gui.screen() instanceof EditBidScreen) && !(mc.gui.screen() instanceof AbstractSignEditScreen)) vanilla = false;
            modeScreen = null;
            mode = Mode.NONE;
            return;
        }
        if (cs == modeScreen) build(cs);
        else modeOf(cs);
    }

    private static boolean active(AbstractContainerScreen<?> cs) {
        return FEATURE.active() && !vanilla && modeOf(cs) != Mode.NONE;
    }

    public static boolean blocking(AbstractContainerScreen<?> cs) {
        return active(cs);
    }

    // ---------------------------------------------------------------- browser data

    private static void buildBrowser(AbstractContainerScreen<?> cs) {
        List<Slot> slots = cs.getMenu().slots;
        if (slots.size() < 54) return;
        for (int i = 0; i < 6; i++) {
            TAB_SELECTED[i] = false;
            List<String> lore = ItemUtil.loreLines(slots.get(i * 9).getItem());
            for (int j = lore.size() - 1; j >= 0; j--) {
                String low = lore.get(j).toLowerCase(Locale.ROOT);
                if (low.contains("currently")) { TAB_SELECTED[i] = true; break; }
                if (low.contains("click")) break;
            }
        }
        ItemStack prev = slots.get(PREV).getItem(), next = slots.get(NEXT).getItem();
        prevOk = prev.is(Items.ARROW);
        nextOk = next.is(Items.ARROW);
        if (prevOk) parsePage(prev);
        else if (nextOk) parsePage(next);
        else { page = 1; pages = 1; }
        resetOk = slots.get(RESET).getItem().is(Items.ANVIL);
        search = "";
        for (String l : ItemUtil.loreLines(slots.get(SEARCH).getItem())) {
            if (!l.contains("Filtered:")) continue;
            int c = l.indexOf(':');
            search = l.substring(c + 1).trim();
            break;
        }
        sortText = option(slots.get(SORT).getItem());
        rarityText = option(slots.get(RARITY).getItem());
        typeText = option(slots.get(TYPE).getItem());
    }

    private static void parsePage(ItemStack arrow) {
        List<String> lore = ItemUtil.loreLines(arrow);
        if (lore.isEmpty()) return;
        String s = lore.getFirst().trim();
        if (!s.startsWith("(") || !s.endsWith(")") || s.indexOf('/') < 0) return;
        try {
            String[] parts = s.substring(1, s.length() - 1).split("/");
            page = Integer.parseInt(parts[0].replace(",", "").trim());
            pages = Math.max(page, Integer.parseInt(parts[1].replace(",", "").trim()));
        } catch (Exception ignored) {}
    }

    /** The "▶ option" line of a Hypixel toggle item. */
    private static String option(ItemStack st) {
        for (String l : ItemUtil.loreLines(st)) {
            int i = l.indexOf('▶');
            if (i >= 0) return l.substring(i + 1).trim();
        }
        return st.isEmpty() ? "" : StringUtil.stripTrim(st.getHoverName().getString());
    }

    // ---------------------------------------------------------------- view data

    private static void resetView(String t) {
        title = t;
        bin = t.toLowerCase(Locale.ROOT).contains("bin");
        state = State.NONE;
        buySlot = -1;
        price = "?";
        minBid = "";
        youPay = "";
        grace = "";
        wonAuction = true;
        changeProfile = false;
        own = false;
    }

    private static void buildView(AbstractContainerScreen<?> cs, int rows) {
        List<Slot> slots = cs.getMenu().slots;
        int n = Math.min(rows * 9, slots.size());
        State found = State.NONE;
        int slotFound = -1;
        boolean collect = false;
        for (int i = 0; i < n; i++) {
            if (i == 13) continue;
            ItemStack st = slots.get(i).getItem();
            if (st.isEmpty() || st.is(Items.STAINED_GLASS_PANE.black())) continue;
            State s = State.NONE;
            if (st.is(Items.POISONOUS_POTATO)) s = State.CANT_AFFORD;
            else if (st.is(Items.GOLD_NUGGET)) s = State.AFFORD;
            else if (st.is(Items.GOLD_BLOCK)) s = State.TOP_BID;
            else if (st.is(Items.NAME_TAG)) s = State.COLLECT;
            else if (st.is(Items.DYED_TERRACOTTA.red())) s = State.CANCELLABLE;
            else if (BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().endsWith("_bed")) s = State.GRACE;
            String name = StringUtil.stripTrim(st.getHoverName().getString()).toLowerCase(Locale.ROOT);
            if (name.contains("collect auction") && !collect) {
                collect = true;
                readPrice(st);
                found = State.COLLECT;
                slotFound = i;
                continue;
            }
            if (s == State.NONE || found != State.NONE) continue;
            found = s;
            slotFound = i;
            if (st.is(Items.NAME_TAG)) changeProfile = true;
            readPrice(st);
        }
        if (own && found != State.CANCELLABLE && found != State.COLLECT) found = State.OWN;
        buySlot = slotFound;
        state = found;
        grace = "";
        if (state == State.GRACE) {
            for (String l : ItemUtil.loreLines(slots.get(13).getItem())) {
                String line = l.trim();
                if (line.startsWith("Can buy in:")) { grace = line.substring(11).trim(); break; }
            }
            if (grace.isEmpty()) {
                Double coins = Calculator.eval(price.replace("coins", "").trim().replace(",", ""));
                Double purse = Calculator.eval("purse");
                state = coins != null && purse != null && coins > purse ? State.CANT_AFFORD : State.AFFORD;
            }
        }
    }

    private static void readPrice(ItemStack st) {
        String look = bin ? "price:" : "new bid:";
        String p = null, mb = null, pay = "";
        for (String raw : ItemUtil.loreLines(st)) {
            String l = raw.trim();
            String low = l.toLowerCase(Locale.ROOT);
            int c = l.indexOf(':');
            String val = c >= 0 ? l.substring(c + 1).trim() : "";
            if (low.startsWith(look)) p = val;
            else if (!bin && low.startsWith("minimum bid:")) mb = val.replace("coins", "").replace(",", "").trim();
            else if (low.startsWith("you pay:")) pay = val;
            else if (low.startsWith("top bid:")) wonAuction = false;
            else if (low.contains("correct profile")) changeProfile = true;
            else if (low.contains("own auction")) own = true;
            if (p == null && low.startsWith("buy it now:")) p = val;
        }
        if (p != null) price = p;
        minBid = mb != null ? mb : price.replace("coins", "").replace(",", "").trim();
        youPay = pay;
    }

    private static String status() {
        return switch (state) {
            case CANT_AFFORD -> "§cCan't Afford";
            case TOP_BID -> "§eAlready Top Bid";
            case OWN -> "§bYour Auction";
            case GRACE -> "§eCan buy in: §f" + (grace.isEmpty() ? "?" : grace);
            case COLLECT -> changeProfile ? "§bOn a different profile" : wonAuction ? "§aAuction ended" : "§cYou didn't win";
            case CANCELLABLE -> "§7Your auction";
            case AFFORD -> !bin && !youPay.isEmpty() ? "§7You pay: §6" + youPay : "";
            default -> "§7Waiting for the server...";
        };
    }

    private static String buttonLabel() {
        return switch (state) {
            case COLLECT -> changeProfile ? "§bChange Profile" : wonAuction ? "Collect Auction" : "Collect Bid";
            case CANCELLABLE -> "§cCancel Auction";
            default -> bin ? "Buy" : "Bid";
        };
    }

    private static boolean buttonActive() {
        return buySlot >= 0 && state != State.CANT_AFFORD && state != State.GRACE && state != State.OWN && state != State.NONE;
    }

    // ---------------------------------------------------------------- layout

    private static float scale(int w, int h, int pw, int ph) {
        float s = Math.min((w - 60) / (float) pw, (h - 30) / (float) ph);
        s = (float) Math.floor(s * 2) / 2f;
        return Math.max(1f, Math.min(2f, s));
    }

    private record Box(float s, int ox, int oy) {
        double lx(double mx) { return (mx - ox) / s; }
        double ly(double my) { return (my - oy) / s; }
    }

    private static Box box(int w, int h) {
        int pw = mode == Mode.BROWSER ? BW : VW, ph = mode == Mode.BROWSER ? BH : VH;
        float s = scale(w, h, pw + (mode == Mode.BROWSER ? 28 : 0), ph);
        int ox = Math.round((w - pw * s) / 2f + (mode == Mode.BROWSER ? 12 * s : 0));
        int oy = Math.round((h - ph * s) / 2f);
        return new Box(s, ox, oy);
    }

    private static boolean in(double x, double y, int bx, int by, int bw, int bh) {
        return x >= bx && x < bx + bw && y >= by && y < by + bh;
    }

    // browser button row: {x, width}
    private static final int[][] FILTERS = {{8, 14}, {26, 48}, {78, 48}, {130, 46}};
    private static final int FILTER_Y = 88, FILTER_H = 14;
    private static final int GRID_X = 8, GRID_Y = 22, SCROLL_X = 8 + 8 * CELL + 6;

    // ---------------------------------------------------------------- render

    public static boolean render(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, int mx, int my) {
        if (!active(cs)) return false;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, 0xC0101014);
        Box b = box(w, h);
        double lx = b.lx(mx), ly = b.ly(my);
        g.pose().pushMatrix();
        g.pose().translate(b.ox, b.oy);
        g.pose().scale(b.s, b.s);
        Object tip = switch (mode) {
            case BROWSER -> renderBrowser(cs, g, font, lx, ly);
            case VIEW -> renderView(cs, g, font, lx, ly);
            default -> renderConfirm(cs, g, font, lx, ly);
        };
        g.pose().popMatrix();
        if (tip instanceof ItemStack st && !st.isEmpty()) g.setTooltipForNextFrame(font, st, mx, my);
        else if (tip instanceof String s) g.setComponentTooltipForNextFrame(font, List.of(Component.literal(s)), mx, my);
        return true;
    }

    private static void panel(GuiGraphicsExtractor g, int pw, int ph) {
        TeslaGui.hudPanel(g, 0, 0, pw, ph, 5);
    }

    private static boolean button(GuiGraphicsExtractor g, Font font, int x, int y, int bw, int bh, String text,
                                  double lx, double ly, boolean enabled) {
        boolean hover = enabled && in(lx, ly, x, y, bw, bh);
        g.fill(x, y, x + bw, y + bh, !enabled ? 0xFF1E1E24 : hover ? 0xFF505060 : 0xFF2C2C36);
        String t = font.plainSubstrByWidth(text, bw - 4);
        g.centeredText(font, (enabled ? "" : "§8") + t, x + bw / 2, y + (bh - 8) / 2, 0xFFFFFFFF);
        return hover;
    }

    private static Object renderBrowser(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, Font font, double lx, double ly) {
        List<Slot> slots = cs.getMenu().slots;
        Object tip = null;
        for (int i = 0; i < 6; i++) {
            int tx = -24, ty = 4 + i * 20;
            ItemStack icon = slots.get(i * 9).getItem();
            if (icon.isEmpty()) continue;
            boolean hover = in(lx, ly, tx, ty, 22, 18);
            g.fill(tx, ty, tx + 24, ty + 18, TAB_SELECTED[i] ? 0xFF3F8FD0 : hover ? 0xFF505060 : 0xFF24242C);
            g.item(icon, tx + 4, ty + 1);
            if (hover) tip = icon;
        }
        panel(g, BW, BH);

        g.fill(6, 5, 118, 17, in(lx, ly, 6, 5, 112, 12) ? 0xFFAAAAAA : 0xFF555555);
        g.fill(7, 6, 117, 16, 0xFF000000);
        g.text(font, search.isEmpty() ? "§8Search..." : font.plainSubstrByWidth(search, 106), 9, 7, 0xFFFFFFFF, false);
        String pg = page + "/" + pages;
        g.text(font, "§7" + pg, 122, 7, 0xFFFFFFFF, false);
        if (button(g, font, BW - 18, 5, 12, 12, "<", lx, ly, true)) tip = slots.get(BACK).getItem();

        for (int n = 0; n < 24; n++) {
            int sx = GRID_X + (n % 8) * CELL, sy = GRID_Y + (n / 8) * CELL;
            g.fill(sx, sy, sx + CELL - 1, sy + CELL - 1, 0xFF2A2A32);
            Slot slot = slots.get(ITEM_SLOTS[n]);
            ItemStack st = slot.getItem();
            if (st.isEmpty() || st.is(Items.STAINED_GLASS_PANE.black())) continue;
            g.pose().pushMatrix();
            g.pose().translate(sx + 1 - slot.x, sy + 1 - slot.y);
            AuctionPriceCompare.drawHighlight(g, slot);
            g.pose().popMatrix();
            g.item(st, sx + 1, sy + 1);
            g.itemDecorations(font, st, sx + 1, sy + 1);
            if (in(lx, ly, sx, sy, CELL, CELL)) {
                g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x60FFFFFF);
                tip = st;
            }
        }

        int top = GRID_Y, len = 3 * CELL - 1;
        g.fill(SCROLL_X, top, SCROLL_X + 10, top + len, 0xFF1A1A20);
        int thumb = 14;
        int ty = pages <= 1 ? top : top + Math.round((float) (Math.min(page, pages) - 1) / (pages - 1) * (len - thumb));
        g.fill(SCROLL_X + 1, ty, SCROLL_X + 9, ty + thumb, in(lx, ly, SCROLL_X, top, 10, len) ? 0xFFAAAAAA : 0xFF777777);
        if (prevOk) g.centeredText(font, in(lx, ly, SCROLL_X, top - 12, 10, 10) ? "§f▲" : "§7▲", SCROLL_X + 5, top - 11, 0xFFFFFFFF);
        if (nextOk) g.centeredText(font, in(lx, ly, SCROLL_X, top + len + 2, 10, 10) ? "§f▼" : "§7▼", SCROLL_X + 5, top + len + 3, 0xFFFFFFFF);

        String[] labels = {"↻", sortText, rarityText, typeText};
        int[] slotIds = {RESET, SORT, RARITY, TYPE};
        for (int i = 0; i < 4; i++) {
            int[] f = FILTERS[i];
            boolean enabled = i != 0 || resetOk;
            if (button(g, font, f[0], FILTER_Y, f[1], FILTER_H, labels[i].isEmpty() ? "-" : labels[i], lx, ly, enabled)) {
                tip = slots.get(slotIds[i]).getItem();
            }
        }
        g.text(font, "§8↑↓ / scroll: pages", 8, 108, 0xFFFFFFFF, false);
        button(g, font, BW - 50, 105, 44, 13, "Vanilla", lx, ly, true);
        return tip;
    }

    private static Object renderView(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, Font font, double lx, double ly) {
        List<Slot> slots = cs.getMenu().slots;
        Object tip = null;
        panel(g, VW, VH);
        g.text(font, "§f" + font.plainSubstrByWidth(title, VW - 30), 6, 6, 0xFFFFFFFF, false);
        if (button(g, font, VW - 18, 4, 12, 12, "<", lx, ly, true)) tip = "Back";

        ItemStack item = slots.size() > 13 ? slots.get(13).getItem() : ItemStack.EMPTY;
        g.pose().pushMatrix();
        g.pose().translate(VW / 2f - 16, 18);
        g.pose().scale(2f, 2f);
        g.item(item, 0, 0);
        g.itemDecorations(font, item, 0, 0);
        g.pose().popMatrix();
        if (in(lx, ly, VW / 2 - 16, 18, 32, 32)) tip = item;

        boolean editable = !bin && state != State.COLLECT && state != State.CANT_AFFORD && state != State.OWN
                && state != State.CANCELLABLE && state != State.NONE;
        g.centeredText(font, state == State.COLLECT ? "§7Auction ended" : bin ? "§7Price" : "§7New Bid", VW / 2, 56, 0xFFFFFFFF);
        boolean priceHover = editable && in(lx, ly, 20, 66, VW - 40, 10);
        String p = "§6§l" + price;
        g.centeredText(font, priceHover ? "§e§nClick to edit bid" : p, VW / 2, 67, 0xFFFFFFFF);
        String st = status();
        if (!st.isEmpty()) g.centeredText(font, font.plainSubstrByWidth(st, VW - 10), VW / 2, 80, 0xFFFFFFFF);

        if (state != State.OWN) {
            String label = buttonLabel();
            int bw = Math.max(70, font.width(label) + 10);
            if (button(g, font, (VW - bw) / 2, 94, bw, 16, label, lx, ly, buttonActive()) && buySlot >= 0) {
                tip = slots.get(buySlot).getItem();
            }
        }
        button(g, font, VW - 50, VH - 16, 44, 12, "Vanilla", lx, ly, true);
        return tip;
    }

    private static Object renderConfirm(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, Font font, double lx, double ly) {
        panel(g, VW, 76);
        String t = StringUtil.stripTrim(cs.getTitle().getString());
        g.centeredText(font, "§f§l" + t, VW / 2, 8, 0xFFFFFFFF);
        g.centeredText(font, (bin ? "§7Price: " : "§7New Bid: ") + "§6§l" + price, VW / 2, 24, 0xFFFFFFFF);
        Object tip = null;
        List<Slot> slots = cs.getMenu().slots;
        if (button(g, font, 14, 42, 70, 16, "§aConfirm", lx, ly, true) && slots.size() > 11) tip = slots.get(11).getItem();
        if (button(g, font, VW - 84, 42, 70, 16, "§cCancel", lx, ly, true) && slots.size() > 15) tip = slots.get(15).getItem();
        button(g, font, VW - 50, 62, 44, 11, "Vanilla", lx, ly, true);
        return tip;
    }

    // ---------------------------------------------------------------- input

    public static boolean click(AbstractContainerScreen<?> cs, MouseButtonEvent e) {
        if (!active(cs)) return false;
        Minecraft mc = Minecraft.getInstance();
        Box b = box(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        double lx = b.lx(e.x()), ly = b.ly(e.y());
        int button = com.teslamaps.utils.KeyNames.toContainerButton(e.button());
        if (button > 1) return true;
        switch (mode) {
            case BROWSER -> clickBrowser(cs, lx, ly, button);
            case VIEW -> clickView(cs, lx, ly);
            default -> clickConfirm(cs, lx, ly);
        }
        return true;
    }

    private static void clickBrowser(AbstractContainerScreen<?> cs, double lx, double ly, int button) {
        for (int i = 0; i < 6; i++) {
            if (in(lx, ly, -24, 4 + i * 20, 24, 18)) {
                if (!TAB_SELECTED[i]) send(cs, i * 9, 0);
                return;
            }
        }
        if (in(lx, ly, 6, 5, 112, 12)) { send(cs, SEARCH, button); return; }
        if (in(lx, ly, BW - 18, 5, 12, 12)) { send(cs, BACK, 0); return; }
        for (int n = 0; n < 24; n++) {
            int sx = GRID_X + (n % 8) * CELL, sy = GRID_Y + (n / 8) * CELL;
            if (in(lx, ly, sx, sy, CELL, CELL)) {
                if (!cs.getMenu().slots.get(ITEM_SLOTS[n]).getItem().isEmpty()) send(cs, ITEM_SLOTS[n], button);
                return;
            }
        }
        int top = GRID_Y, len = 3 * CELL - 1;
        if (in(lx, ly, SCROLL_X, top - 12, 10, 12 + len / 2)) { if (prevOk) send(cs, PREV, 0); return; }
        if (in(lx, ly, SCROLL_X, top + len / 2, 10, len / 2 + 14)) { if (nextOk) send(cs, NEXT, 0); return; }
        int[] slotIds = {RESET, SORT, RARITY, TYPE};
        for (int i = 0; i < 4; i++) {
            int[] f = FILTERS[i];
            if (in(lx, ly, f[0], FILTER_Y, f[1], FILTER_H)) {
                if (i != 0 || resetOk) send(cs, slotIds[i], i == 0 ? 0 : button);
                return;
            }
        }
        if (in(lx, ly, BW - 50, 105, 44, 13)) vanilla = true;
    }

    private static void clickView(AbstractContainerScreen<?> cs, double lx, double ly) {
        if (in(lx, ly, VW - 18, 4, 12, 12)) { send(cs, BACK, 0); return; }
        if (in(lx, ly, VW - 50, VH - 16, 44, 12)) { vanilla = true; return; }
        boolean editable = !bin && state != State.COLLECT && state != State.CANT_AFFORD && state != State.OWN
                && state != State.CANCELLABLE && state != State.NONE;
        if (editable && in(lx, ly, 20, 66, VW - 40, 10)) {
            editBidAt = System.currentTimeMillis();
            send(cs, 31, 0);
            return;
        }
        if (state == State.OWN || !buttonActive()) return;
        Font font = Minecraft.getInstance().font;
        int bw = Math.max(70, font.width(buttonLabel()) + 10);
        if (in(lx, ly, (VW - bw) / 2, 94, bw, 16)) send(cs, buySlot, 0);
    }

    private static void clickConfirm(AbstractContainerScreen<?> cs, double lx, double ly) {
        if (in(lx, ly, 14, 42, 70, 16)) send(cs, 11, 0);
        else if (in(lx, ly, VW - 84, 42, 70, 16)) send(cs, 15, 0);
        else if (in(lx, ly, VW - 50, 62, 44, 11)) vanilla = true;
    }

    public static boolean scrolled(double amount) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || !active(cs) || mode != Mode.BROWSER) return false;
        if (amount > 0 && prevOk) send(cs, PREV, 0);
        else if (amount < 0 && nextOk) send(cs, NEXT, 0);
        return true;
    }

    public static boolean keyPressed(AbstractContainerScreen<?> cs, int key) {
        if (!active(cs) || mode != Mode.BROWSER) return false;
        if ((key == com.mojang.blaze3d.platform.InputConstants.KEY_UP || key == com.mojang.blaze3d.platform.InputConstants.KEY_LEFT) && prevOk) { send(cs, PREV, 0); return true; }
        if ((key == com.mojang.blaze3d.platform.InputConstants.KEY_DOWN || key == com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT) && nextOk) { send(cs, NEXT, 0); return true; }
        return false;
    }

    private static void send(AbstractContainerScreen<?> cs, int slot, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null || slot < 0 || slot >= cs.getMenu().slots.size()) return;
        long now = System.currentTimeMillis();
        if (now - lastClick < 150) return;
        lastClick = now;
        mc.gameMode.handleContainerInput(cs.getMenu().containerId, slot, button, ContainerInput.PICKUP, mc.player);
    }

    // ---------------------------------------------------------------- edit bid popup

    private static final class EditBidScreen extends Screen {

        private static final int PW = 200, PH = 110;
        private final SignBlockEntity sign;
        private final String[] lines;
        private final String min;
        private EditBox box;
        private boolean sent;
        private int px, py;

        EditBidScreen(SignBlockEntity sign, String[] lines, String min) {
            super(Component.literal("Edit Bid"));
            this.sign = sign;
            this.lines = lines;
            this.min = min == null ? "" : min;
        }

        @Override
        protected void init() {
            super.init();
            px = (width - PW) / 2;
            py = (height - PH) / 2;
            box = new EditBox(font, px + 20, py + 30, PW - 40, 14, Component.literal("Bid"));
            box.setMaxLength(32);
            box.setHint(Component.literal("10m, 1.5b, 2*500k"));
            addRenderableWidget(box);
            setInitialFocus(box);
        }

        private String value() {
            Double v = Calculator.eval(box.getValue().replace("coins", "").trim());
            return v == null ? box.getValue().trim() : Long.toString(Math.round(v));
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            g.fill(0, 0, width, height, 0xC0000000);
            TeslaGui.hudPanel(g, px, py, PW, PH, 6);
            g.centeredText(font, "§l- Set Bid -", px + PW / 2, py + 12, 0xFFFFFFFF);
            super.extractRenderState(g, mx, my, delta);
            Double v = Calculator.eval(box.getValue().replace("coins", "").trim());
            String calc = box.getValue().isBlank() ? "" : v == null ? "§cInvalid" : "§a= " + Calculator.format(Math.round(v)) + " coins";
            g.centeredText(font, calc, px + PW / 2, py + 50, 0xFFFFFFFF);
            g.centeredText(font, "§7Minimum Bid: §6" + (min.isEmpty() ? "?" : Calculator.format(parse(min))), px + PW / 2, py + 62, 0xFFFFFFFF);
            btn(g, px + 12, py + 80, 84, "Set Minimum Bid", mx, my, !min.isEmpty());
            btn(g, px + PW - 96, py + 80, 84, "Done", mx, my, true);
        }

        private static double parse(String s) {
            try { return Double.parseDouble(s.replace(",", "")); } catch (NumberFormatException e) { return 0; }
        }

        private void btn(GuiGraphicsExtractor g, int x, int y, int w, String text, int mx, int my, boolean on) {
            boolean hover = on && mx >= x && mx < x + w && my >= y && my < y + 18;
            g.fill(x, y, x + w, y + 18, !on ? 0xFF1E1E24 : hover ? 0xFF505060 : 0xFF2C2C36);
            g.centeredText(font, (on ? "" : "§8") + text, x + w / 2, y + 5, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
            double mx = e.x(), my = e.y();
            if (e.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && my >= py + 80 && my < py + 98) {
                if (!min.isEmpty() && mx >= px + 12 && mx < px + 96) { finish(min); return true; }
                if (mx >= px + PW - 96 && mx < px + PW - 12) { finish(value()); return true; }
            }
            return super.mouseClicked(e, doubled);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
                finish(value());
                return true;
            }
            return super.keyPressed(event);
        }

        private void finish(String text) {
            send(text);
            onClose();
        }

        private void send(String line0) {
            if (sent) return;
            sent = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) return;
            mc.getConnection().send(new ServerboundSignUpdatePacket(sign.getBlockPos(),
                    java.util.List.of(line0, lines[1], lines[2], lines[3]), net.minecraft.world.level.block.entity.SignTextSlot.FRONT));
        }

        @Override
        public void onClose() {
            send(lines[0]);
            super.onClose();
        }

        @Override
        public boolean isPauseScreen() { return false; }
    }
}
