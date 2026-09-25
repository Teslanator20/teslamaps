package com.teslamaps.features.bazaar;

// Panel after NEU's AuctionProfit, slot colors after SkyHanni's AuctionsHighlighter
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.EstimatedValue;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ManageAuctions {

    private static final Pattern BIN = Pattern.compile("^Buy it now: ([\\d,]+) coins");
    private static final Pattern BID = Pattern.compile("^(?:Top bid|Starting bid): ([\\d,]+) coins");
    private static final Pattern SOLD_FOR = Pattern.compile("^Sold for: ([\\d,]+) coins");

    private static Screen screen;
    private static int ticks;
    private static int[] slotColor = new int[0];
    private static List<String> lines = List.of();
    private static int width;

    private ManageAuctions() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ManageAuctions").when(() -> cfg().manageAuctionsHelper)
                .tick(ManageAuctions::tick)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Manage Auctions Helper", () -> c.manageAuctionsHelper, v -> c.manageAuctionsHelper = v)
                            .desc("Manage Auctions: sold / expired auctions get colored, a panel shows coins to collect, "
                                    + "value if everything sells and the expired / open count")
                            .tags("auction", "manage", "sold", "expired", "ah");
                    b.toggle("  Summary Panel", () -> c.manageAuctionsPanel, v -> c.manageAuctionsPanel = v);
                    b.toggle("  Mark Listed Above Value", () -> c.manageAuctionsOverpriced, v -> c.manageAuctionsOverpriced = v)
                            .desc("BIN price higher than the estimated item value");
                    b.color("  Sold Color", () -> c.colorAuctionSold, v -> c.colorAuctionSold = v);
                    b.color("  Expired Color", () -> c.colorAuctionExpired, v -> c.colorAuctionExpired = v);
                    b.color("  Above Value Color", () -> c.colorAuctionOverpriced, v -> c.colorAuctionOverpriced = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)
                || !StringUtil.stripTrim(cs.getTitle().getString()).equals("Manage Auctions")) {
            if (screen != null) { screen = null; slotColor = new int[0]; lines = List.of(); }
            return;
        }
        if (cs != screen) { screen = cs; ticks = 0; }
        if (ticks++ % 10 != 0) return;
        build(cs);
    }

    private static void build(AbstractContainerScreen<?> cs) {
        TeslaMapsConfig c = cfg();
        List<Slot> slots = cs.getMenu().slots;
        int[] colors = new int[slots.size()];
        int sold = TeslaMapsConfig.parseColor(c.colorAuctionSold) & 0xFFFFFF;
        int expired = TeslaMapsConfig.parseColor(c.colorAuctionExpired) & 0xFFFFFF;
        int over = TeslaMapsConfig.parseColor(c.colorAuctionOverpriced) & 0xFFFFFF;
        double collect = 0, ifSold = 0;
        int nExpired = 0, nOpen = 0, nSold = 0;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.container instanceof Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            List<String> lore = ItemUtil.loreLines(stack);
            double price = 0, soldFor = 0;
            boolean bin = false, isSold = false, isExpired = false, open = false;
            for (String raw : lore) {
                String l = raw.trim();
                Matcher m;
                if ((m = BIN.matcher(l)).find()) { price = BazaarOrderTracker.parseDouble(m.group(1)); bin = true; }
                else if ((m = BID.matcher(l)).find()) price = BazaarOrderTracker.parseDouble(m.group(1));
                else if ((m = SOLD_FOR.matcher(l)).find()) soldFor = BazaarOrderTracker.parseDouble(m.group(1));
                else if (l.equals("Status: Sold!") || l.equals("Status: Ended!")) isSold = true;
                else if (l.equals("Status: Expired!")) isExpired = true;
                else if (l.startsWith("Ends in")) open = true;
            }
            if (isSold) {
                nSold++;
                colors[i] = sold;
                double v = soldFor > 0 ? soldFor : price;
                if (v > 0) collect += afterTax(v);
            } else if (isExpired) {
                nExpired++;
                colors[i] = expired;
            } else if (open && price > 0) {
                nOpen++;
                ifSold += price;
                if (bin && c.manageAuctionsOverpriced) {
                    double est = EstimatedValue.compute(stack) * Math.max(1, stack.getCount());
                    if (est > 0 && price > est) colors[i] = over;
                }
            }
        }
        slotColor = colors;
        if (!c.manageAuctionsPanel || nSold + nExpired + nOpen == 0) {
            lines = List.of();
            return;
        }
        List<String> out = new ArrayList<>();
        out.add("§e§lAuctions");
        out.add("§7To collect: §6" + ItemPrice.format(collect) + " §8(" + nSold + ")");
        out.add("§7If all sell: §6" + ItemPrice.format(ifSold) + " §8(" + nOpen + " open)");
        out.add("§7Expired: " + (nExpired > 0 ? "§c" : "§a") + nExpired);
        lines = out;
        width = TextHud.width(out);
    }

    private static double afterTax(double coins) {
        if (coins < 10_000_000) return coins / 1.01;
        if (coins < 100_000_000) return coins / 1.02;
        return coins / 1.025;
    }

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (screen == null || mc.screen != screen || !cfg().manageAuctionsHelper) return;
        AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) screen;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        List<Slot> slots = cs.getMenu().slots;
        int[] colors = slotColor;
        for (int i = 0; i < colors.length && i < slots.size(); i++) {
            if (colors[i] == 0) continue;
            Slot s = slots.get(i);
            int x = acc.getX() + s.x, y = acc.getY() + s.y;
            g.fill(x, y, x + 16, y + 16, 0x70000000 | colors[i]);
        }
        if (!lines.isEmpty()) {
            int x = acc.getX() - width - 4;
            TextHud.draw(g, mc, Math.max(2, x), acc.getY(), 1.0f, lines, width);
        }
    }
}
