package com.teslamaps.features;

// After SkyHanni's SackDisplay (stored/capacity, gemstone qualities) and SkyOcean's SackValue
import com.teslamaps.config.TeslaMapsConfig;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Sack menus: every item with stored/capacity, unit price and total value. */
public final class SackTable {

    private static final int LINE = 10;
    private static final Pattern STORED = Pattern.compile("Stored: ([\\d,.]+[kKmMbB]?)/([\\d,.]+[kKmMbB]?)");
    private static final Pattern GEM_COUNT = Pattern.compile("^(Rough|Flawed|Fine): ([\\d,.]+[kKmM]?)");
    private static final Pattern GEM_NAME = Pattern.compile("(?:(?:Rough|Flawed|Fine) )?(\\w+) Gemstones?");

    private record Row(String text, double value, int slot) {}

    private static List<String> lines = List.of();
    private static int[] rowSlots = new int[0];
    private static int width;
    private static Screen lastScreen;
    private static int ticks;

    private SackTable() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SackTable")
                .when(() -> cfg().containerValue && cfg().sackTable)
                .tick(SackTable::tick);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)
                || !StringUtil.stripTrim(cs.getTitle().getString()).contains("Sack")) {
            lastScreen = null;
            lines = List.of();
            return;
        }
        if (lastScreen != mc.screen) {
            lastScreen = mc.screen;
            ticks = 0;
        }
        if (ticks++ % 5 != 0) return;
        PriceManager.ensureFresh();
        build(cs, mc);
    }

    private static void build(AbstractContainerScreen<?> cs, Minecraft mc) {
        int source = EstimatedValue.priceSource();
        List<Row> rows = new ArrayList<>();
        double total = 0;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            String name = StringUtil.stripTrim(st.getHoverName().getString());
            boolean gem = false;
            for (String l : ItemUtil.loreLines(st)) {
                String line = l.trim();
                Matcher g = GEM_COUNT.matcher(line);
                if (g.find()) {
                    Matcher n = GEM_NAME.matcher(name);
                    if (!n.find()) continue;
                    gem = true;
                    long amount = parse(g.group(2));
                    if (amount <= 0) continue;
                    String quality = g.group(1);
                    String gemName = n.group(1);
                    Double p = PriceManager.getPriceOrNull(
                            quality.toUpperCase(Locale.ROOT) + "_" + gemName.toUpperCase(Locale.ROOT) + "_GEM", source);
                    double unit = p == null ? 0 : p;
                    double v = unit * amount;
                    total += v;
                    rows.add(new Row("§f" + quality + " " + gemName + " §e" + fmtAmount(amount)
                            + " §7- §6" + EstimatedValue.fmt(v), v, slot.index));
                    continue;
                }
                if (gem) continue;
                Matcher m = STORED.matcher(line);
                if (!m.find()) continue;
                long stored = parse(m.group(1)), cap = parse(m.group(2));
                if (stored <= 0) break;
                double unit = EstimatedValue.compute(st);
                if (unit <= 0) {
                    String id = PriceManager.idForName(st.getHoverName().getString());
                    Double p = id == null ? null : PriceManager.getPriceOrNull(id, source);
                    unit = p == null ? 0 : p;
                }
                double v = unit * stored;
                total += v;
                String amountCol = cap > 0 && stored >= cap ? "§c" : "§e";
                rows.add(new Row("§f" + name + " " + amountCol + fmtAmount(stored) + "§7/" + fmtAmount(cap)
                        + " §7- §6" + EstimatedValue.fmt(v), v, slot.index));
                break;
            }
        }
        if (rows.isEmpty()) {
            lines = List.of();
            return;
        }
        rows.sort((a, b) -> Double.compare(b.value, a.value));
        List<String> out = new ArrayList<>(rows.size() + 2);
        out.add("§6§lSack Value");
        out.add("§7Total: §6" + EstimatedValue.fmt(total) + (PriceManager.isLoaded() ? "" : " §7(loading prices…)"));
        int[] slots = new int[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            out.add(rows.get(i).text);
            slots[i] = rows.get(i).slot;
        }
        int w = 0;
        for (String s : out) w = Math.max(w, mc.font.width(s));
        width = w;
        rowSlots = slots;
        lines = out;
    }

    public static boolean shouldRender() {
        return cfg().containerValue && cfg().sackTable && !lines.isEmpty()
                && Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>;
    }

    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        List<String> ls = lines;
        int[] slots = rowSlots;
        if (ls.isEmpty()) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int pad = HudStyle.padding();
        int x = Math.max(pad + 2, acc.getX() - 6 - width);
        int y = Math.max(pad + 2, acc.getY());
        int fit = Math.max(3, (mc.getWindow().getGuiScaledHeight() - y - pad) / LINE);
        int n = Math.min(ls.size(), fit);
        HudStyle.panel(ctx, x - pad, y - pad, width + pad * 2, n * LINE + pad * 2);
        for (int i = 0; i < n; i++) ctx.text(mc.font, ls.get(i), x, y + i * LINE, 0xFFFFFFFF);

        if (mouseX < x || mouseX > x + width || mouseY < y) return;
        int row = (mouseY - y) / LINE - 2;
        if (row < 0 || row >= n - 2 || row >= slots.length) return;
        int idx = slots[row];
        if (idx < 0 || idx >= cs.getMenu().slots.size()) return;
        Slot slot = cs.getMenu().slots.get(idx);
        int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
        ctx.fill(sx, sy, sx + 16, sy + 16, 0x80FFAA00);
    }

    private static String fmtAmount(long v) {
        if (v >= 1_000_000) return trim(v / 1_000_000.0) + "M";
        if (v >= 10_000) return trim(v / 1_000.0) + "k";
        return Long.toString(v);
    }

    private static String trim(double d) {
        long r = Math.round(d * 10);
        return r % 10 == 0 ? Long.toString(r / 10) : (r / 10) + "." + (r % 10);
    }

    private static long parse(String s) {
        try {
            String t = s.replace(",", "");
            char u = Character.toLowerCase(t.charAt(t.length() - 1));
            double mul = u == 'k' ? 1e3 : u == 'm' ? 1e6 : u == 'b' ? 1e9 : 1;
            if (mul != 1) t = t.substring(0, t.length() - 1);
            return Math.round(Double.parseDouble(t) * mul);
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
