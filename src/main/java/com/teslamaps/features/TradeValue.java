package com.teslamaps.features;

// Ported from SkyHanni's TradeValue
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Player trade menu: value of both sides, grouped per item, coins included. */
public final class TradeValue {

    private static final int LINE = 10;

    private static List<String> yours = List.of(), other = List.of();
    private static int yoursW, otherW;
    private static int yourKey, otherKey;
    private static Screen lastScreen;
    private static int ticks;

    private TradeValue() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("TradeValue").when(() -> cfg().tradeValue).tick(TradeValue::tick);
    }

    private static boolean inTrade(Screen s) {
        return s instanceof AbstractContainerScreen<?> cs
                && StringUtil.strip(cs.getTitle().getString()).startsWith("You     ");
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!inTrade(mc.screen)) {
            lastScreen = null;
            yours = other = List.of();
            return;
        }
        AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) mc.screen;
        if (cs != lastScreen) {
            lastScreen = cs;
            yourKey = otherKey = 0;
            ticks = 0;
        }
        if (ticks++ % 4 != 0) return;
        PriceManager.ensureFresh();
        List<Slot> slots = cs.getMenu().slots;
        List<ItemStack> mine = new ArrayList<>(16), theirs = new ArrayList<>(16);
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int idx = row * 9 + col;
                if (idx >= slots.size() || col == 4) continue;
                ItemStack st = slots.get(idx).getItem();
                if (st.isEmpty()) continue;
                (col < 4 ? mine : theirs).add(st);
            }
        }
        int mk = key(mine), ok = key(theirs);
        if (mk != yourKey) {
            yourKey = mk;
            yours = build("§eTrade Value Your", mine);
            yoursW = width(mc, yours);
        }
        if (ok != otherKey) {
            otherKey = ok;
            other = build("§eTrade Value Other", theirs);
            otherW = width(mc, other);
        }
    }

    private static int key(List<ItemStack> items) {
        int h = 1 + PriceManager.version();
        for (ItemStack st : items) h = h * 31 + System.identityHashCode(st) + st.getCount();
        return h;
    }

    private static List<String> build(String header, List<ItemStack> items) {
        record Group(String name, int count, double value) {}
        Map<String, Group> groups = new LinkedHashMap<>();
        double coins = 0, total = 0;
        for (ItemStack st : items) {
            List<String> lore = ItemUtil.loreLines(st);
            if (ItemUtil.loreContains(st, "Lump-sum amount")) {
                if (coins == 0 && !lore.isEmpty()) coins = parseCoins(lore.get(lore.size() - 1));
                continue;
            }
            String name = st.getHoverName().getString();
            double v = EstimatedValue.compute(st) * st.getCount();
            total += v;
            Group g = groups.get(name);
            groups.put(name, g == null ? new Group(name, st.getCount(), v) : new Group(name, g.count + st.getCount(), g.value + v));
        }
        total += coins;
        List<Group> sorted = new ArrayList<>(groups.values());
        sorted.sort((a, b) -> Double.compare(b.value, a.value));
        List<String> out = new ArrayList<>(sorted.size() + 3);
        out.add(header);
        out.add("§7Total: §6§l" + EstimatedValue.fmt(total) + (PriceManager.isLoaded() ? "" : " §7(loading prices…)"));
        if (coins > 0) out.add("§6Coins§7: §6" + EstimatedValue.fmt(coins));
        for (Group g : sorted) {
            out.add((g.count > 1 ? "§7" + g.count + "x §r" : "") + g.name + "§7: "
                    + (g.value > 0 ? "§6" + EstimatedValue.fmt(g.value) : "§8?"));
        }
        return out;
    }

    private static double parseCoins(String line) {
        String t = line.replace("(", "").replace(")", "").replace(",", "").trim().toLowerCase(java.util.Locale.ROOT);
        if (t.isEmpty()) return 0;
        char u = t.charAt(t.length() - 1);
        double mul = u == 'k' ? 1e3 : u == 'm' ? 1e6 : u == 'b' ? 1e9 : 1;
        if (mul != 1) t = t.substring(0, t.length() - 1);
        try { return Double.parseDouble(t) * mul; } catch (NumberFormatException e) { return 0; }
    }

    private static int width(Minecraft mc, List<String> lines) {
        int w = 0;
        for (String s : lines) w = Math.max(w, mc.font.width(s));
        return w;
    }

    public static boolean shouldRender() {
        return cfg().tradeValue && (!yours.isEmpty() || !other.isEmpty()) && inTrade(Minecraft.getInstance().screen);
    }

    /** From ContainerOverlays: yours left of the menu, theirs right of it. */
    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int pad = HudStyle.padding();
        int screenW = mc.getWindow().getGuiScaledWidth();
        List<String> a = yours, b = other;
        if (!a.isEmpty()) draw(ctx, mc, a, Math.max(pad + 2, acc.getX() - 6 - yoursW), acc.getY(), yoursW, pad);
        if (!b.isEmpty()) {
            int x = acc.getX() + acc.getImageWidth() + 6;
            if (x + otherW + pad > screenW) x = Math.max(pad + 2, screenW - otherW - pad - 2);
            draw(ctx, mc, b, x, acc.getY(), otherW, pad);
        }
    }

    private static void draw(GuiGraphicsExtractor ctx, Minecraft mc, List<String> ls, int x, int y, int w, int pad) {
        int fit = Math.max(3, (mc.getWindow().getGuiScaledHeight() - y - pad) / LINE);
        int n = Math.min(ls.size(), fit);
        HudStyle.panel(ctx, x - pad, y - pad, w + pad * 2, n * LINE + pad * 2);
        for (int i = 0; i < n; i++) ctx.text(mc.font, ls.get(i), x, y + i * LINE, 0xFFFFFFFF);
    }
}
