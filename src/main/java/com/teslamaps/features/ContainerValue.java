package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.CroesusProfit;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Container value side panel. Built every 5 ticks, render only draws. Grouping, hover
 * breakdown + slot highlight and the item/min-value limits after SkyHanni's ChestValue,
 * the sack "Stored:" amount after Skyblocker's ChestValue.
 */
public class ContainerValue {

    private static final int LINE = 10;
    private static final int HEADER = 3;

    private static final class Entry {
        final String name;
        double value;
        int amount;
        final List<Integer> slots = new ArrayList<>(2);
        final ItemStack stack;
        String hoverKey;

        Entry(String name, ItemStack stack) {
            this.name = name;
            this.stack = stack;
        }
    }

    private static final String[] SAMPLE = {
            "§6§lContainer Value", "§7Total: §61.2B", "§7Top 5:",
            " §fHyperion §7- §6900M", " §fNecron's Handle §7- §6250M", " §fWither Catalyst §7x32 §7- §68M"};

    private static List<String> lines = List.of();
    private static List<Entry> shown = List.of();
    private static final Map<String, List<String>> breakdowns = new HashMap<>();
    private static Screen lastScreen;
    private static int ticks;

    public static void register() {
        Feature.of("ContainerValue")
                .when(() -> TeslaMapsConfig.get().containerValue)
                .tick(ContainerValue::tick);
        HudEditElement.of("containerValue", "Container Value").section("Tooltips & Value")
                .enabled(() -> TeslaMapsConfig.get().containerValue && TeslaMapsConfig.get().containerValueFreePos)
                .size(() -> previewWidth(), () -> SAMPLE.length * LINE)
                .scaleRange(0.5f, 2.0f)
                .preview((g, m, x, y, s) -> drawLines(g, m, List.of(SAMPLE), x, y, s));
    }

    private static int previewWidth() {
        int w = 0;
        for (String s : SAMPLE) w = Math.max(w, Minecraft.getInstance().font.width(s));
        return w;
    }

    public static boolean shouldRender() {
        if (!TeslaMapsConfig.get().containerValue) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) return false;
        // both panels sit right of the container, so the shard ones win where they apply
        if (!TeslaMapsConfig.get().containerValueFreePos) {
            if (TeslaMapsConfig.get().section("Shards")
                    && com.teslamaps.features.hunting.ShardOverlay.shouldRender()) return false;
            if (CroesusProfit.shouldRender()) return false;
            if (com.teslamaps.features.rift.RiftInventoryValue.shouldRender()) return false;
        }
        return !lines.isEmpty();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            if (lastScreen != null) {
                lastScreen = null;
                lines = List.of();
                shown = List.of();
                breakdowns.clear();
            }
            return;
        }
        if (lastScreen != mc.screen) {
            lastScreen = mc.screen;
            breakdowns.clear();
            ticks = 0;
        }
        if (ticks++ % 5 != 0) return;
        PriceManager.ensureFresh();
        build(cs);
    }

    private static void build(AbstractContainerScreen<?> cs) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        boolean sacks = com.teslamaps.utils.StringUtil.stripTrim(cs.getTitle().getString()).contains("Sack");
        double total = 0;
        Map<String, Entry> grouped = new LinkedHashMap<>();
        List<Entry> entries = new ArrayList<>();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            int amount = item.getCount();
            double unit = EstimatedValue.compute(item);
            if (sacks) {
                int stored = ItemIndex.storedAmount(item);
                if (stored > 0) {
                    amount = stored;
                    if (unit <= 0) unit = sackPrice(item);
                }
            }
            double v = unit * amount;
            if (v <= 0) continue;
            total += v;
            String name = strip(item.getHoverName().getString());
            Entry e = c.containerValueGroup ? grouped.get(name) : null;
            if (e == null) {
                e = new Entry(name, item);
                if (c.containerValueGroup) grouped.put(name, e);
                entries.add(e);
            }
            e.value += v;
            e.amount += amount;
            e.slots.add(slot.index);
        }
        if (entries.isEmpty()) {
            lines = List.of();
            shown = List.of();
            return;
        }
        entries.sort((a, b) -> Double.compare(b.value, a.value));

        double min = c.containerValueHideBelow * 1000.0;
        List<Entry> top = new ArrayList<>();
        for (Entry e : entries) {
            if (top.size() >= Math.max(1, c.containerValueTop)) break;
            if (e.value < min) break;
            top.add(e);
        }

        List<String> out = new ArrayList<>(top.size() + HEADER + 1);
        out.add("§6§lContainer Value");
        out.add("§7Total: §6" + EstimatedValue.fmt(total));
        if (!PriceManager.isLoaded()) out.add("§7loading prices…");
        out.add("§7Top " + top.size() + ":");
        for (Entry e : top) {
            String count = e.amount > 1 && (c.containerValueGroup || sacks) ? " §7x" + e.amount : "";
            out.add(" §f" + e.name + count + " §7- §6" + EstimatedValue.fmt(e.value));
        }
        lines = out;
        shown = top;
    }

    private static double sackPrice(ItemStack item) {
        String id = PriceManager.idForName(item.getHoverName().getString());
        if (id == null) return 0;
        Double p = PriceManager.getPriceOrNull(id, EstimatedValue.priceSource());
        return p == null ? 0 : p;
    }

    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        List<String> ls = lines;
        if (ls.isEmpty()) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        TeslaMapsConfig c = TeslaMapsConfig.get();

        int x, y;
        float scale;
        if (c.containerValueFreePos) {
            x = c.containerValueX;
            y = c.containerValueY;
            scale = c.containerValueScale;
        } else {
            x = acc.getX() + 176 + 6;
            y = acc.getY();
            scale = 1f;
        }
        int w = 0;
        for (String l : ls) w = Math.max(w, mc.font.width(l));
        int pad = HudStyle.padding();
        HudStyle.panel(ctx, x - pad * scale, y - pad * scale, (w + pad * 2) * scale, (ls.size() * LINE + pad * 2) * scale);
        drawLines(ctx, mc, ls, x, y, scale);

        if (!c.containerValueHover) return;
        int firstEntry = ls.size() - shown.size();
        float lx = (mouseX - x) / scale, ly = (mouseY - y) / scale;
        if (lx < 0 || lx > w || ly < 0) return;
        int row = (int) (ly / LINE) - firstEntry;
        if (row < 0 || row >= shown.size()) return;
        Entry e = shown.get(row);
        for (Slot slot : cs.getMenu().slots) {
            if (!e.slots.contains(slot.index)) continue;
            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            ctx.fill(sx, sy, sx + 16, sy + 16, 0x80FFAA00);
        }
        if (e.hoverKey == null) e.hoverKey = e.slots.get(0) + "|" + e.name;
        List<String> detail = breakdowns.computeIfAbsent(e.hoverKey, k -> EstimatedValue.display(e.stack));
        if (detail.isEmpty()) return;
        int dw = 0;
        for (String l : detail) dw = Math.max(dw, mc.font.width(l));
        int dx = Math.round(x + (w + pad * 3) * scale);
        if (dx + dw * scale > mc.getWindow().getGuiScaledWidth()) dx = Math.round(x - (dw + pad * 3) * scale);
        int dy = Math.round(y + (firstEntry + row) * LINE * scale);
        HudStyle.panel(ctx, dx - pad * scale, dy - pad * scale, (dw + pad * 2) * scale, (detail.size() * LINE + pad * 2) * scale);
        drawLines(ctx, mc, detail, dx, dy, scale);
    }

    private static void drawLines(GuiGraphicsExtractor ctx, Minecraft mc, List<String> ls, int x, int y, float scale) {
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int ly = 0;
        for (String l : ls) {
            ctx.text(mc.font, l, 0, ly, 0xFFFFFFFF);
            ly += LINE;
        }
        pose.popMatrix();
    }

    private static String strip(String s) {
        return com.teslamaps.utils.StringUtil.stripTrim(s);
    }
}
