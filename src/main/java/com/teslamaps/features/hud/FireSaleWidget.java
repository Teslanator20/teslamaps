package com.teslamaps.features.hud;

// After Skyblocker's skyblock/tabhud/widget/FireSaleWidget
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Running Fire Sales from the tab list with an available/total bar, or when the next one starts. */
public final class FireSaleWidget {

    private record Row(String text, float progress) {}

    private static final Pattern HEADER = Pattern.compile("^Fire Sales?:?\\s*(.*)$");
    private static final Pattern ITEM = Pattern.compile("^(.+): ([\\d,]+)/([\\d.,]+)(k?)$");
    private static final int ROW = 11, BAR_W = 60;

    private static List<Row> rows = List.of();
    private static final List<Row> SAMPLE = List.of(new Row("§3§lFire Sales", -1),
            new Row("§fDoom Bone Hat §7412/2500", 412 / 2500f), new Row("§7Starts in: §e2h 10m", -1));
    private static int width, timer;
    private static HudEditElement hud;

    private FireSaleWidget() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("fireSaleWidget", "Fire Sales")
                .enabled(() -> cfg().fireSaleWidget)
                .size(() -> rowsWidth(shown()), () -> shown().size() * ROW + 4)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, shown(), rowsWidth(shown())));
        Feature.of("FireSaleWidget").when(() -> cfg().fireSaleWidget)
                .tick(FireSaleWidget::tick)
                .hudHiddenInInventory("firesalewidget", FireSaleWidget::render)
                .config("HUD Widgets", b -> {
                    b.label("Fire Sale Widget");
                    b.toggle("Fire Sale Widget", () -> cfg().fireSaleWidget, v -> cfg().fireSaleWidget = v)
                            .desc("Running Fire Sales from the tab list with an available/total bar, or when the next one starts. Needs the Fire Sales tab widget. Position in /tmap gui")
                            .tags("fire sale", "firesale", "limited");
                });
    }

    private static List<Row> shown() {
        return rows.isEmpty() ? SAMPLE : rows;
    }

    private static void tick() {
        if (timer++ % 20 != 0) return;
        List<Row> out = new ArrayList<>();
        boolean in = false;
        for (String raw : TabListUtils.getOrderedLines()) {
            String l = StringUtil.stripTrim(raw);
            if (!in) {
                Matcher h = HEADER.matcher(l);
                if (h.matches()) {
                    in = true;
                    out.add(new Row("§3§lFire Sales" + (h.group(1).isEmpty() ? "" : " §7" + h.group(1)), -1));
                }
                continue;
            }
            if (l.isEmpty()) break;
            Matcher m = ITEM.matcher(l);
            if (m.matches()) {
                try {
                    int avail = Integer.parseInt(m.group(2).replace(",", ""));
                    float total = Float.parseFloat(m.group(3).replace(",", "")) * (m.group(4).isEmpty() ? 1 : 1000);
                    out.add(new Row("§f" + m.group(1) + " §7" + avail + "/" + Math.round(total),
                            total <= 0 ? 0 : Math.min(1f, avail / total)));
                    continue;
                } catch (NumberFormatException ignored) {}
            }
            String low = l.toLowerCase(Locale.ROOT);
            out.add(new Row((low.contains("start") ? "§e" : "§7") + l, -1));
            if (out.size() > 12) break;
        }
        rows = out.size() <= 1 ? List.of() : out;
        width = rowsWidth(rows);
    }

    private static int rowsWidth(List<Row> rs) {
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (Row r : rs) w = Math.max(w, mc.font.width(r.text) + (r.progress >= 0 ? BAR_W + 6 : 0));
        return w + 8;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<Row> rs, int w) {
        if (rs.isEmpty()) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, w, rs.size() * ROW + 4);
        for (int i = 0; i < rs.size(); i++) {
            Row r = rs.get(i);
            int ry = 3 + i * ROW;
            g.text(mc.font, r.text, 4, ry, 0xFFFFFFFF, true);
            if (r.progress < 0) continue;
            int bx = w - 4 - BAR_W;
            g.fill(bx, ry + 1, bx + BAR_W, ry + 7, 0xFF303030);
            int fillW = Math.round(BAR_W * r.progress);
            int col = r.progress > 0.5f ? 0xFF55FF55 : r.progress > 0.2f ? 0xFFFFFF55 : 0xFFFF5555;
            if (fillW > 0) g.fill(bx, ry + 1, bx + fillW, ry + 7, col);
        }
        g.pose().popMatrix();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || rows.isEmpty()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), rows, width);
    }
}
