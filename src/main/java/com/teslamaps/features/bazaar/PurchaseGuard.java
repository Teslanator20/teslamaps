package com.teslamaps.features.bazaar;

// After NEU's BazaarPriceWarning, HexPriceWarning, AuctionBINWarning and WarningPopUp
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PurchaseGuard {

    private static final Pattern BZ_PRICE = Pattern.compile("^Price: ([\\d,.]+) coins");
    private static final Pattern HEX_COINS = Pattern.compile("^([\\d,.]+) Coins$");
    private static final Pattern ITEM_PRICE = Pattern.compile("^Item price: ([\\d,]+) coins");
    private static final int W = 200, H = 96;

    private static Screen screen;
    private static int slotId = -1;
    private static String title, itemLine, costLine, extraLine;

    private PurchaseGuard() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("PurchaseGuard").when(() -> cfg().purchaseWarning)
                .tick(PurchaseGuard::tick)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Expensive Purchase Warnings", () -> c.purchaseWarning, v -> c.purchaseWarning = v)
                            .desc("Asks before a bazaar instant buy or a hex upgrade over the limit, and before a BIN listed far "
                                    + "below or above the lowest BIN. Y / Enter confirms, N / Esc cancels.")
                            .tags("bazaar", "hex", "bin", "warning", "confirm", "overpay", "undercut");
                    b.slider("  Bazaar Instant Buy Limit (M)", 0, 500, () -> c.purchaseWarnBazaarM, v -> c.purchaseWarnBazaarM = v, 1)
                            .desc("0 = off");
                    b.slider("  Hex Upgrade Limit (M)", 0, 500, () -> c.purchaseWarnHexM, v -> c.purchaseWarnHexM = v, 1)
                            .desc("0 = off");
                    b.toggle("  BIN Price Check", () -> c.purchaseWarnBin, v -> c.purchaseWarnBin = v);
                    b.slider("    Undercut Warning (%)", 0, 100, () -> c.purchaseWarnBinUnder, v -> c.purchaseWarnBinUnder = v, 0)
                            .desc("Warn when the BIN is this much below the lowest BIN, 0 = off");
                    b.slider("    Overcut Warning (%)", 0, 1000, () -> c.purchaseWarnBinOver, v -> c.purchaseWarnBinOver = v, 0)
                            .desc("Warn when the BIN is this much above the lowest BIN, 0 = off");
                });
    }

    private static boolean on() {
        return cfg().purchaseWarning && SkyblockUtils.isInSkyblock();
    }

    private static void tick() {
        if (screen != null && Minecraft.getInstance().gui.screen() != screen) clear();
    }

    private static void clear() {
        screen = null;
        slotId = -1;
    }

    public static boolean pending() {
        return screen != null && Minecraft.getInstance().gui.screen() == screen;
    }

    /** Slot click hook: true = swallow the click. */
    public static boolean blockClick(AbstractContainerScreen<?> cs, Slot slot, int button, ContainerInput type) {
        if (!on()) return false;
        if (pending()) return true;
        if (slot == null || slot.container instanceof Inventory || slot.getItem().isEmpty()) return false;
        if (type == ContainerInput.THROW || type == ContainerInput.CLONE) return false;
        String t = StringUtil.stripTrim(cs.getTitle().getString());
        ItemStack stack = slot.getItem();
        String name = StringUtil.stripTrim(stack.getHoverName().getString());
        List<String> lore = ItemUtil.loreLines(stack);
        TeslaMapsConfig c = cfg();

        if (c.purchaseWarnBazaarM > 0 && !lore.isEmpty() && lore.get(lore.size() - 1).trim().equals("Click to buy now!")) {
            for (String l : lore) {
                Matcher m = BZ_PRICE.matcher(l.trim());
                if (!m.find()) continue;
                double price = BazaarOrderTracker.parseDouble(m.group(1));
                if (price > c.purchaseWarnBazaarM * 1_000_000.0) {
                    show(cs, slot, "Bazaar Instant Buy", name, "§7will cost you §6" + ItemPrice.format(price) + " §7coins", null);
                    return true;
                }
                return false;
            }
        }

        if (c.purchaseWarnHexM > 0) {
            for (int i = 0; i + 1 < lore.size(); i++) {
                if (!lore.get(i).trim().equals("Bazaar Price")) continue;
                Matcher m = HEX_COINS.matcher(lore.get(i + 1).trim());
                if (!m.find()) break;
                double price = BazaarOrderTracker.parseDouble(m.group(1));
                if (price >= c.purchaseWarnHexM * 1_000_000.0) {
                    show(cs, slot, "Hex Upgrade", name, "§7will cost you §6" + ItemPrice.format(price) + " §7coins", null);
                    return true;
                }
                break;
            }
        }

        if (c.purchaseWarnBin && t.startsWith("Create BIN Auction") && slot.index == 29) {
            List<Slot> slots = cs.getMenu().slots;
            if (slots.size() <= 31) return false;
            Matcher m = ITEM_PRICE.matcher(StringUtil.stripTrim(slots.get(31).getItem().getHoverName().getString()));
            if (!m.find()) return false;
            double selling = BazaarOrderTracker.parseDouble(m.group(1));
            ItemStack sold = slots.get(13).getItem();
            String id = ItemUtil.skyblockId(sold);
            if (id == null || id.isEmpty() || selling <= 0) return false;
            Double lbin = PriceManager.lowestBinOrNull(id);
            if (lbin == null || lbin <= 0) return false;
            double worth = lbin * Math.max(1, sold.getCount());
            boolean under = c.purchaseWarnBinUnder > 0 && selling < worth * (1 - c.purchaseWarnBinUnder / 100.0);
            boolean over = c.purchaseWarnBinOver > 0 && selling > worth * (1 + c.purchaseWarnBinOver / 100.0);
            if (!under && !over) return false;
            long pct = Math.round(Math.abs(selling - worth) * 100 / worth);
            show(cs, slot, "BIN Auction", StringUtil.stripTrim(sold.getHoverName().getString()),
                    "§7has a lowest BIN of §6" + ItemPrice.format(worth) + " §7coins",
                    "§7Sell it for §6" + ItemPrice.format(selling) + " §7(" + (under ? "§c-" : "§a+") + pct + "%§7)?");
            return true;
        }
        return false;
    }

    private static void show(AbstractContainerScreen<?> cs, Slot slot, String what, String item, String cost, String extra) {
        screen = cs;
        slotId = slot.index;
        title = what;
        itemLine = "§7[ §f" + item + " §7]";
        costLine = cost;
        extraLine = extra;
    }

    private static void confirm() {
        Minecraft mc = Minecraft.getInstance();
        int id = slotId;
        AbstractContainerScreen<?> cs = screen instanceof AbstractContainerScreen<?> s ? s : null;
        clear();
        if (cs == null || mc.gameMode == null || mc.player == null || id < 0) return;
        mc.gameMode.handleContainerInput(cs.getMenu().containerId, id, 0, ContainerInput.PICKUP, mc.player);
    }

    /** Container key hook: true = handled. */
    public static boolean onKey(int key) {
        if (!pending()) return false;
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_Y || key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || key == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
            confirm();
            return true;
        }
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_N || key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            clear();
            return true;
        }
        return true;
    }

    /** Mouse click hook: true = handled. */
    public static boolean onMouse(MouseButtonEvent click) {
        if (!pending()) return false;
        Minecraft mc = Minecraft.getInstance();
        int cx = mc.getWindow().getGuiScaledWidth() / 2, cy = mc.getWindow().getGuiScaledHeight() / 2;
        int by = cy + H / 2 - 22;
        double x = click.x(), y = click.y();
        if (y >= by && y < by + 16) {
            if (x >= cx - 50 && x < cx - 4) { confirm(); return true; }
            if (x >= cx + 4 && x < cx + 50) { clear(); return true; }
        }
        return true;
    }

    public static void render(GuiGraphicsExtractor g) {
        if (!pending()) return;
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int cx = sw / 2, cy = sh / 2;
        g.fill(0, 0, sw, sh, 0x80000000);
        HudStyle.panel(g, cx - W / 2f, cy - H / 2f, W, H);
        int y = cy - H / 2 + 6;
        g.centeredText(mc.font, "§c§lAre you SURE?", cx, y, 0xFFFFFFFF);
        g.centeredText(mc.font, "§e" + title, cx, y + 12, 0xFFFFFFFF);
        g.centeredText(mc.font, itemLine, cx, y + 24, 0xFFFFFFFF);
        g.centeredText(mc.font, costLine, cx, y + 36, 0xFFFFFFFF);
        if (extraLine != null) g.centeredText(mc.font, extraLine, cx, y + 48, 0xFFFFFFFF);
        int by = cy + H / 2 - 22;
        g.fill(cx - 50, by, cx - 4, by + 16, 0xC0204020);
        g.fill(cx + 4, by, cx + 50, by + 16, 0xC0402020);
        g.centeredText(mc.font, "§a[Y]es", cx - 27, by + 4, 0xFFFFFFFF);
        g.centeredText(mc.font, "§c[N]o", cx + 27, by + 4, 0xFFFFFFFF);
    }
}
