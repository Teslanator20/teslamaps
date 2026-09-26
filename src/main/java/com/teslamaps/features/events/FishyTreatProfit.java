package com.teslamaps.features.events;

// Ported from SkyHanni's FishyTreatProfit.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.debug.Perf;
import com.teslamaps.features.EstimatedValue;
import com.teslamaps.features.LoreCosts;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Lukas the Aquarist: profit per Fishy Treat for every item, sorted, next to the menu. */
public final class FishyTreatProfit {

    private record Row(String name, String value, int slot) {}

    private static Feature feature;
    private static Screen lastScreen;
    private static boolean inShop;
    private static int ticks;
    private static List<Row> rows = List.of();
    private static int panelW;
    private static final Map<ItemStack, List<String>> tips = new IdentityHashMap<>();

    private FishyTreatProfit() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("FishyTreatProfit").section("Events")
                .when(() -> cfg().fishyTreatProfit)
                .tick(FishyTreatProfit::tick)
                .config("Events", b -> b.toggle("Fishy Treat Profit", () -> cfg().fishyTreatProfit,
                                v -> cfg().fishyTreatProfit = v)
                        .desc("Lukas the Aquarist's shop: profit per Fishy Treat for every item (extra materials taken off), "
                                + "sorted next to the menu. Hover a row to find the slot, details in the tooltip")
                        .tags("fishy treat", "lukas", "seal", "beach ball"));
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!inShop || feature == null || !feature.active() || lines.isEmpty()) return;
            List<String> t = tips.get(stack);
            if (t == null) return;
            for (String s : t) lines.add(Component.literal(s));
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            if (lastScreen != null) reset();
            return;
        }
        if (cs != lastScreen) {
            reset();
            lastScreen = cs;
            inShop = StringUtil.stripTrim(cs.getTitle().getString()).equals("Lukas the Aquarist");
        }
        if (!inShop || ticks++ % 10 != 0) return;
        PriceManager.ensureFresh();
        build(cs);
    }

    private static void reset() {
        lastScreen = null;
        inShop = false;
        rows = List.of();
        tips.clear();
        ticks = 0;
    }

    private static void build(AbstractContainerScreen<?> cs) {
        tips.clear();
        List<Object[]> sorted = new ArrayList<>();
        List<Slot> slots = cs.getMenu().slots;
        for (int i = 0; i <= 44 && i < slots.size(); i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            List<String> lore = ItemUtil.loreLines(stack);
            if (!LoreCosts.hasTradeLine(lore)) continue;
            List<LoreCosts.Cost> costs = LoreCosts.read(lore);
            if (costs.isEmpty()) continue;
            long treats = 0;
            double extra = 0;
            List<String> mats = new ArrayList<>();
            for (LoreCosts.Cost c : costs) {
                if (c.name().startsWith("Fishy Treat")) { treats += c.amount(); continue; }
                double cp = LoreCosts.price(c);
                extra += cp;
                mats.add(" §8- §7" + c.name() + " §8x" + c.amount() + (cp > 0 ? " §7(§6" + EstimatedValue.fmt(cp) + "§7)" : ""));
            }
            if (treats <= 0) continue;
            String name = StringUtil.stripTrim(stack.getHoverName().getString());
            String id = ItemUtil.skyblockId(stack);
            if (id.isEmpty()) id = PriceManager.idForName(name);
            if (id == null) continue;
            Double p = PriceManager.getPriceOrNull(id, LoreCosts.source());
            if (p == null) continue;
            double price = p * stack.getCount();
            double per = (price - extra) / treats;
            List<String> t = new ArrayList<>();
            t.add("");
            t.add("§e§lFishy Treat Profit");
            t.add("§7Sell price: §6" + EstimatedValue.fmt(price));
            t.add("§7Additional cost: §6" + EstimatedValue.fmt(extra));
            t.addAll(mats);
            t.add("§7Fishy Treats required: §c" + treats);
            t.add("§7Profit per Fishy Treat: " + color(per) + EstimatedValue.fmt(per));
            tips.put(stack, t);
            sorted.add(new Object[]{name, per, i});
        }
        sorted.sort((a, b) -> Double.compare((double) b[1], (double) a[1]));
        var font = Minecraft.getInstance().font;
        int w = font.width("Profit per Fishy Treat");
        List<Row> out = new ArrayList<>();
        for (Object[] o : sorted) {
            double per = (double) o[1];
            Row row = new Row((String) o[0], color(per) + EstimatedValue.fmt(per), (int) o[2]);
            w = Math.max(w, font.width(row.name()) + 10 + font.width(row.value()));
            out.add(row);
        }
        panelW = w;
        rows = out;
    }

    private static String color(double v) { return v > 0 ? "§6" : "§c"; }

    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (!inShop || rows.isEmpty() || feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || cs != lastScreen) return;
        Perf.begin("FishyTreatProfit.render");
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        List<Row> r = rows;
        int lh = mc.font.lineHeight + 1;
        int w = panelW;
        int x = acc.getX() + acc.getImageWidth() + 8;
        int y = acc.getY() + 4;
        int h = (r.size() + 1) * lh;
        com.teslamaps.render.gui.HudStyle.panel(ctx, x - 4, y - 4, w + 8, h + 6);
        ctx.text(mc.font, "§e§lProfit per Fishy Treat", x, y, 0xFFFFFFFF);
        List<Slot> slots = cs.getMenu().slots;
        for (int i = 0; i < r.size(); i++) {
            Row row = r.get(i);
            int ry = y + (i + 1) * lh;
            boolean hover = mouseX >= x - 2 && mouseX <= x + w + 2 && mouseY >= ry - 1 && mouseY < ry + lh - 1;
            ctx.text(mc.font, row.name(), x, ry, hover ? 0xFFFFFFFF : 0xFFAAAAAA);
            ctx.text(mc.font, row.value(), x + w - mc.font.width(row.value()), ry, 0xFFFFFFFF);
            if (hover && row.slot() < slots.size()) {
                Slot s = slots.get(row.slot());
                int sx = acc.getX() + s.x, sy = acc.getY() + s.y;
                ctx.fill(sx, sy, sx + 16, sy + 16, 0x8855FF55);
            }
        }
        Perf.end();
    }
}
