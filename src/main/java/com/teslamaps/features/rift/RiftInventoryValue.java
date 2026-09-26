package com.teslamaps.features.rift;

// After SkyHanni's ShowMotesNpcSellPrice.kt (processItems / drawDisplay)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.EstimatedValue;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RiftInventoryValue {

    private static final int LINE = 10;
    private static final int HEADER = 2;
    private static final int MAX = 12;

    private static final class Entry {
        final String name;
        double value;
        int amount;
        final List<Integer> slots = new ArrayList<>(2);

        Entry(String name) { this.name = name; }
    }

    private static Feature feature;
    private static List<String> lines = List.of();
    private static List<Entry> shown = List.of();
    private static int width, ticks;

    private RiftInventoryValue() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Rift", "Skyblock", true);
        feature = Feature.of("RiftInventoryValue").section("Rift").when(() -> cfg().riftInvValue)
                .tick(RiftInventoryValue::tick)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Rift Inventory Value", () -> c.riftInvValue, v -> c.riftInvValue = v)
                            .desc("Panel next to open containers in the Rift: motes value per item and the total. Hover a row to see its slots.");
                    b.toggle("  Long Numbers", () -> c.riftInvValueLong, v -> c.riftInvValueLong = v);
                });
    }

    public static boolean shouldRender() {
        return feature != null && !lines.isEmpty() && feature.active()
                && Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!RiftZone.inRift() || !(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            if (!lines.isEmpty()) {
                lines = List.of();
                shown = List.of();
            }
            return;
        }
        if (ticks++ % 10 != 0) return;
        Map<String, Entry> grouped = new LinkedHashMap<>();
        double total = 0;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack item = slot.getItem();
            double v = MotesPrice.value(item);
            if (v <= 0) continue;
            total += v;
            String id = ItemUtil.skyblockId(item);
            Entry e = grouped.get(id);
            if (e == null) grouped.put(id, e = new Entry(StringUtil.stripTrim(item.getHoverName().getString())));
            e.value += v;
            e.amount += item.getCount();
            e.slots.add(slot.index);
        }
        if (grouped.isEmpty()) {
            lines = List.of();
            shown = List.of();
            return;
        }
        List<Entry> entries = new ArrayList<>(grouped.values());
        entries.sort((a, b) -> Double.compare(b.value, a.value));
        if (entries.size() > MAX) entries = entries.subList(0, MAX);
        List<String> out = new ArrayList<>(entries.size() + HEADER);
        out.add("§d§lRift Inventory Value");
        out.add("§7Total: §b" + fmt(total) + " Motes");
        for (Entry e : entries) {
            String count = e.amount > 1 ? " §7x" + e.amount : "";
            out.add(" §f" + e.name + count + " §7- §d" + fmt(e.value));
        }
        int w = 0;
        for (String l : out) w = Math.max(w, mc.font.width(l));
        width = w;
        shown = entries;
        lines = out;
    }

    private static String fmt(double v) {
        return cfg().riftInvValueLong ? MotesPrice.num(Math.round(v * 10) / 10.0) : EstimatedValue.shortFormat(v);
    }

    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        List<String> ls = lines;
        List<Entry> sh = shown;
        if (ls.isEmpty()) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int x = acc.getX() + acc.getImageWidth() + 6;
        int y = acc.getY();
        int pad = HudStyle.padding();
        HudStyle.panel(ctx, x - pad, y - pad, width + pad * 2, ls.size() * LINE + pad * 2);
        for (int i = 0; i < ls.size(); i++) ctx.text(mc.font, ls.get(i), x, y + i * LINE, 0xFFFFFFFF);
        int first = ls.size() - sh.size();
        if (mouseX < x || mouseX > x + width || mouseY < y) return;
        int row = (mouseY - y) / LINE - first;
        if (row < 0 || row >= sh.size()) return;
        Entry e = sh.get(row);
        for (Slot slot : cs.getMenu().slots) {
            if (!e.slots.contains(slot.index)) continue;
            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            ctx.fill(sx, sy, sx + 16, sy + 16, 0x80FF55FF);
        }
    }
}
