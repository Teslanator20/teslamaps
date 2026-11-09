package com.teslamaps.features;

// After SkyHanni's garden/pests/PesthunterProfit + LoreCostUtils
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.debug.Perf;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Next to Pesthunter's Wares: item value minus material cost, per pest spent, best first. */
public final class PesthunterProfit {

    private record Row(String text, int slot, double perPest, List<Component> tip) {}

    private static final int LINE = 10;
    private static final Pattern TRADE = Pattern.compile("^(?:Left )?Click to (?:trade|unlock|buy)!$|^You can't afford this.*$");
    private static final Pattern AMOUNT_FIRST = Pattern.compile("^([\\d,]+) ([\\w' ]+)$");
    private static final Pattern AMOUNT_AFTER = Pattern.compile("^(.+?)(?: ?x([\\d,]+))?$");
    private static final Pattern NAME_AMOUNT = Pattern.compile("^(.+?) x([\\d,]+)$");

    private static Feature feature;
    private static Screen lastScreen;
    private static int ticks, width;
    private static List<Row> rows = List.of();
    private static String header = "";

    private PesthunterProfit() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("PesthunterProfit").section("Garden").when(() -> cfg().pesthunterProfit)
                .tick(PesthunterProfit::tick)
                .config("Garden", b -> {
                    b.label("Pesthunter Shop");
                    b.toggle("Pesthunter Shop Profit", () -> cfg().pesthunterProfit, v -> cfg().pesthunterProfit = v)
                            .desc("Pesthunter's Wares: table of item value minus material cost, per pest spent, best first. Hover a row for details and the slot.")
                            .tags("pest", "pesthunter", "phillip", "wares", "profit");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)
                || !StringUtil.stripTrim(cs.getTitle().getString()).equals("Pesthunter's Wares")) {
            if (lastScreen != null) {
                lastScreen = null;
                rows = List.of();
            }
            return;
        }
        if (cs != lastScreen) {
            lastScreen = cs;
            ticks = 0;
        }
        if (ticks++ % 10 != 0) return;
        PriceManager.ensureFresh();
        build(cs, mc);
    }

    private static void build(AbstractContainerScreen<?> cs, Minecraft mc) {
        int source = EstimatedValue.priceSource();
        List<Row> out = new ArrayList<>();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            List<String> lore = ItemUtil.loreLines(st);
            boolean trade = false;
            for (String l : lore) if (TRADE.matcher(l.trim()).matches()) { trade = true; break; }
            if (!trade) continue;

            String name = StringUtil.stripTrim(st.getHoverName().getString());
            int amount = st.getCount();
            Matcher na = NAME_AMOUNT.matcher(name);
            if (na.matches()) {
                name = na.group(1);
                amount = parseInt(na.group(2));
            }
            double price = value(st, name, source) * Math.max(1, amount);
            if (price <= 0) continue;

            long pests = 0;
            double cost = 0;
            boolean in = false;
            for (String raw : lore) {
                String l = raw.trim();
                if (!in) {
                    if (l.equals("Cost") || l.equals("Cost:") || l.equals("Costs")) in = true;
                    continue;
                }
                if (l.isEmpty()) break;
                Matcher f = AMOUNT_FIRST.matcher(l);
                if (f.matches()) {
                    String what = f.group(2).trim();
                    long n = parseInt(f.group(1));
                    if (what.equalsIgnoreCase("Pests") || what.equalsIgnoreCase("Pest")) { pests += n; continue; }
                    if (what.equalsIgnoreCase("Coins") || what.equalsIgnoreCase("Coin")) { cost += n; continue; }
                    String id = PriceManager.idForName(what);
                    Double p = id == null ? null : PriceManager.getPriceOrNull(id, source);
                    if (p != null) { cost += p * n; continue; }
                }
                Matcher a = AMOUNT_AFTER.matcher(l);
                if (!a.matches()) continue;
                String id = PriceManager.idForName(a.group(1).trim());
                Double p = id == null ? null : PriceManager.getPriceOrNull(id, source);
                if (p == null) continue;
                cost += p * (a.group(2) == null ? 1 : parseInt(a.group(2)));
            }
            if (pests <= 0) continue;
            double profit = price - cost;
            double per = profit / pests;
            String col = per > 0 ? "§6" : "§c";
            List<Component> tip = new ArrayList<>(6);
            tip.add(st.getHoverName().copy());
            tip.add(Component.empty());
            tip.add(Component.literal("§7Item price: §6" + EstimatedValue.fmt(price)));
            tip.add(Component.literal("§7Material cost: §6" + EstimatedValue.fmt(cost)));
            tip.add(Component.literal("§7Final profit: §6" + EstimatedValue.fmt(profit)));
            tip.add(Component.literal("§7Pests: §2" + pests + " §7-> §6" + EstimatedValue.fmt(per) + " §7per pest"));
            out.add(new Row("§f" + name + (amount > 1 ? " §8x" + amount : "") + " §7- " + col + EstimatedValue.fmt(per) + "§7/pest",
                    slot.index, per, tip));
        }
        out.sort((x, y) -> Double.compare(y.perPest, x.perPest));
        header = "§e§lPesthunter Shop Profit" + (PriceManager.isLoaded() ? "" : " §7(loading prices…)");
        int w = mc.font.width(header);
        for (Row r : out) w = Math.max(w, mc.font.width(r.text));
        width = w;
        rows = out;
    }

    private static double value(ItemStack st, String name, int source) {
        double v = EstimatedValue.compute(st);
        if (v > 0) return v;
        String id = ItemUtil.skyblockId(st);
        if (id.isEmpty()) id = PriceManager.idForName(name);
        Double p = id == null ? null : PriceManager.getPriceOrNull(id, source);
        return p == null ? 0 : p;
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.replace(",", "")); } catch (NumberFormatException e) { return 1; }
    }

    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        List<Row> rs = rows;
        if (rs.isEmpty() || feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || cs != lastScreen) return;
        Perf.begin("PesthunterProfit.render");
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int pad = HudStyle.padding();
        int x = Math.max(pad + 2, acc.getX() - 6 - width);
        int y = Math.max(pad + 2, acc.getY());
        int fit = Math.max(2, (mc.getWindow().getGuiScaledHeight() - y - pad) / LINE - 1);
        int n = Math.min(rs.size(), fit);
        HudStyle.panel(ctx, x - pad, y - pad, width + pad * 2, (n + 1) * LINE + pad * 2);
        ctx.text(mc.font, header, x, y, 0xFFFFFFFF);
        for (int i = 0; i < n; i++) ctx.text(mc.font, rs.get(i).text, x, y + (i + 1) * LINE, 0xFFFFFFFF);

        int row = mouseX < x || mouseX > x + width || mouseY < y + LINE ? -1 : (mouseY - y) / LINE - 1;
        if (row >= 0 && row < n) {
            Row r = rs.get(row);
            ctx.fill(x - 1, y + (row + 1) * LINE - 1, x + width + 1, y + (row + 2) * LINE - 1, 0x30FFFFFF);
            if (r.slot < cs.getMenu().slots.size()) {
                Slot slot = cs.getMenu().slots.get(r.slot);
                int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
                ctx.fill(sx, sy, sx + 16, sy + 16, 0x8055FF55);
            }
            ctx.setComponentTooltipForNextFrame(mc.font, r.tip, mouseX, mouseY);
        }
        Perf.end();
    }
}
