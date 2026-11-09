package com.teslamaps.features.visitors;

// After SkyHanni's GardenVisitorShoppingList, GanKura's VisitorPanel (click = /bz)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.EstimatedValue;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class VisitorShoppingList {

    private static final int LINE = 11;
    private static final int PAD = 4;

    private record Row(String text, String id, String item, Visitors.Visitor ref) {}

    private static List<Row> rows = List.of();
    private static int width = 80;
    private static int builtVersion = -1, builtPrices = -1, ticks;
    private static int boxX, boxY;
    private static float boxScale = 1f;
    private static boolean drawn;
    private static HudEditElement hud;
    private static Feature feature;

    private static final List<Row> SAMPLE = List.of(
            new Row("§7Visitor Shopping List §7(§6412k§7)", null, null, null),
            new Row("§fEnchanted Hay Bale §ex12 §7(§6240k§7) §7(§ax20 §7in sacks)", "ENCHANTED_HAY_BALE", null, null),
            new Row("§fEnchanted Cookie §ex2 §7(§6172k§7)", "ENCHANTED_COOKIE", null, null),
            new Row("", null, null, null),
            new Row("§e2 §7Visitors:", null, null, null),
            new Row(" §7- §9Lazy Miner", null, null, null),
            new Row(" §7- §7§mRhys", null, null, null));

    private VisitorShoppingList() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("visitorShoppingList", "Visitor Shopping List").ctx(HudEditElement.HudCtx.GARDEN)
                .section("Garden").enabled(() -> cfg().visitorShoppingList)
                .size(() -> width + PAD * 2, () -> Math.max(1, rows.size()) * LINE + PAD * 2)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, sampleWidth(mc)));
        feature = Feature.of("VisitorShoppingList").section("Garden").when(() -> cfg().visitorShoppingList)
                .tick(VisitorShoppingList::tick)
                .hud("visitorshoppinglist", VisitorShoppingList::renderHud)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Visitor Helper");
                    b.toggle("Visitor Shopping List", () -> c.visitorShoppingList, v -> c.visitorShoppingList = v)
                            .desc("Items every visitor wants, added up: amount, price and what your sacks hold. Open each visitor once to read their wishes. Click an item: /bz, click a visitor: leave them out.")
                            .tags("visitor", "shopping", "needs", "bazaar");
                    b.toggle("  Show Prices", () -> c.visitorShoppingPrices, v -> c.visitorShoppingPrices = v);
                    b.toggle("  Show Sack Amounts", () -> c.visitorShoppingSacks, v -> c.visitorShoppingSacks = v);
                    b.toggle("  Also In The Bazaar", () -> c.visitorShoppingBazaar, v -> c.visitorShoppingBazaar = v);
                    b.toggle("  Also On The Farming Islands", () -> c.visitorShoppingIslands, v -> c.visitorShoppingIslands = v);
                });
    }

    private static int sampleWidth(Minecraft mc) {
        int w = 0;
        for (Row r : SAMPLE) w = Math.max(w, mc.font.width(r.text) + (r.id != null ? 12 : 0));
        return w;
    }

    private static void tick() {
        if (ticks++ % 10 != 0) return;
        int ver = Visitors.version(), pv = PriceManager.version();
        if (ver == builtVersion && pv == builtPrices) return;
        builtVersion = ver;
        builtPrices = pv;
        build(Minecraft.getInstance());
    }

    private static void build(Minecraft mc) {
        Map<String, Integer> need = new LinkedHashMap<>();
        List<Visitors.Visitor> fresh = new ArrayList<>(), known = new ArrayList<>();
        for (Visitors.Visitor v : Visitors.all()) {
            if (v.status == Visitors.Status.ACCEPTED || v.status == Visitors.Status.REFUSED) continue;
            if (!v.read) { fresh.add(v); continue; }
            known.add(v);
            if (Visitors.ignored(v)) continue;
            for (Map.Entry<String, Integer> e : v.items.entrySet()) need.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        List<Row> out = new ArrayList<>();
        if (!need.isEmpty()) {
            double total = 0;
            int header = out.size();
            out.add(null);
            for (Map.Entry<String, Integer> e : need.entrySet()) {
                String name = e.getKey();
                int amount = e.getValue();
                StringBuilder sb = new StringBuilder("§f").append(name).append(" §ex").append(fmt(amount));
                if (cfg().visitorShoppingPrices) {
                    double p = Visitors.buyPrice(name) * amount;
                    total += p;
                    if (p > 0) sb.append(" §7(§6").append(EstimatedValue.fmt(p)).append("§7)");
                }
                if (cfg().visitorShoppingSacks) {
                    int has = Visitors.having(name);
                    if (has > 0) sb.append(" §7(").append(has >= amount ? "§a" : "§e").append("x").append(fmt(has)).append(" §7have)");
                }
                out.add(new Row(sb.toString(), Visitors.idOf(name), name, null));
            }
            out.set(header, new Row("§7Visitor Shopping List" + (total > 0 ? " §7(§6" + EstimatedValue.fmt(total) + "§7)" : ""), null, null, null));
        }
        section(out, fresh, "New Visitor");
        section(out, known, "Visitor");
        rows = out;
        int w = 40;
        for (Row r : out) w = Math.max(w, mc.font.width(r.text) + (r.id != null ? 12 : 0));
        width = w;
    }

    private static void section(List<Row> out, List<Visitors.Visitor> list, String noun) {
        if (list.isEmpty()) return;
        if (!out.isEmpty()) out.add(new Row("", null, null, null));
        out.add(new Row("§e" + list.size() + " §7" + noun + (list.size() == 1 ? "" : "s") + ":", null, null, null));
        for (Visitors.Visitor v : list) {
            String name = v.read && Visitors.ignored(v) ? "§7§m" + v.name : v.colored;
            out.add(new Row(" §7- " + name, null, null, v));
        }
    }

    private static String fmt(int n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static boolean where(Minecraft mc) {
        if (SkyblockUtils.isInGarden()) return true;
        return cfg().visitorShoppingIslands && "The Farming Islands".equalsIgnoreCase(SkyblockUtils.getArea());
    }

    private static boolean inBazaar(AbstractContainerScreen<?> cs) {
        String t = StringUtil.stripTrim(cs.getTitle().getString());
        return t.startsWith("Bazaar") || t.contains(" ➜ ");
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        drawn = false;
        if (mc.gui.screen() instanceof AbstractContainerScreen<?> || rows.isEmpty() || !where(mc)) return;
        drawAt(g, mc);
    }

    /** Container pass: player inventory and visitor menu in the garden, bazaar anywhere when enabled. */
    public static void renderContainer(GuiGraphicsExtractor g) {
        drawn = false;
        if (feature == null || !feature.active() || rows.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        boolean show = inBazaar(cs) ? cfg().visitorShoppingBazaar && SkyblockUtils.isInSkyblock()
                : where(mc) && (cs instanceof InventoryScreen || Visitors.open() != null);
        if (show) drawAt(g, mc);
    }

    private static void drawAt(GuiGraphicsExtractor g, Minecraft mc) {
        boxX = hud.x();
        boxY = hud.y();
        boxScale = hud.scale();
        draw(g, mc, boxX, boxY, boxScale, rows, width);
        drawn = true;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float s, List<Row> list, int w) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(s, s);
        HudStyle.panel(g, 0, 0, w + PAD * 2, list.size() * LINE + PAD * 2);
        int row = PAD;
        for (Row r : list) {
            int tx = PAD;
            if (r.id != null) {
                ItemStack icon = SkyblockItems.stackNow(r.id);
                if (!icon.isEmpty()) {
                    pose.pushMatrix();
                    pose.translate(PAD, row - 1);
                    pose.scale(0.625f, 0.625f);
                    g.item(icon, 0, 0);
                    pose.popMatrix();
                }
                tx += 12;
            }
            g.text(mc.font, r.text, tx, row + 1, 0xFFFFFFFF, true);
            row += LINE;
        }
        pose.popMatrix();
    }

    /** Mouse click on a container screen: item row = /bz, visitor row = toggle ignore. */
    public static boolean click(double mx, double my, int button) {
        if (!drawn || button != 0 || feature == null || !feature.active()) return false;
        Minecraft mc = Minecraft.getInstance();
        double lx = (mx - boxX) / boxScale, ly = (my - boxY) / boxScale;
        if (lx < 0 || lx > width + PAD * 2 || ly < PAD) return false;
        int i = (int) ((ly - PAD) / LINE);
        if (i < 0 || i >= rows.size()) return false;
        Row r = rows.get(i);
        if (r.ref != null && r.ref.read) {
            Visitors.toggleIgnored(r.ref);
            return true;
        }
        if (r.item != null && mc.getConnection() != null) {
            mc.getConnection().sendCommand("bz " + r.item);
            return true;
        }
        return false;
    }
}
