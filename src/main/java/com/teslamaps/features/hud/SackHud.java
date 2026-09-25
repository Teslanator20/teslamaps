package com.teslamaps.features.hud;

// Ported from Skysoft's features/inventory/sacks/SackHud + SackHudRender (items picked in the config list instead of an inventory bar)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.SackApi;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.SackItems;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SackHud {

    private record Row(String text, ItemStack icon) {}

    private static final long HIGHLIGHT_MS = 3000;
    private static final int ROW = 12;
    private static final List<Row> SAMPLE = List.of(new Row("§eSack Items", ItemStack.EMPTY),
            new Row("§fEnchanted Sugar §7x§a1,204", ItemStack.EMPTY), new Row("§fFine Jade Gemstone §7x§e312", ItemStack.EMPTY));

    private static List<Row> rows = List.of();
    private static int width;
    private static int timer;
    private static HudEditElement hud;
    private static final Map<String, Long> last = new HashMap<>();
    private static final Map<String, Long> changedAt = new HashMap<>();
    private static final Map<String, Boolean> changedUp = new HashMap<>();
    private static List<String> options = List.of();

    private SackHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("sackHud", "Sacks Tracker").section("Inventory")
                .enabled(() -> cfg().sackHud)
                .size(() -> rowsWidth(rows.isEmpty() ? SAMPLE : rows), () -> height(rows.isEmpty() ? SAMPLE.size() : rows.size()))
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, rows.isEmpty() ? SAMPLE : rows, rowsWidth(rows.isEmpty() ? SAMPLE : rows)));
        Feature.of("SackHud").section("Inventory").when(() -> cfg().sackHud)
                .tick(SackHud::tick)
                .hudForeground("sackhud", SackHud::render)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Sacks Tracker");
                    b.toggle("Sacks Tracker HUD", () -> c.sackHud, v -> c.sackHud = v)
                            .desc("Shows the sack amount of the items you pick below, kept live by [Sacks] messages. Needs Live Sack Contents. Position in /tmap gui");
                    b.searchList("  Tracked Items", SackHud::options,
                            n -> c.sackHudItems.stream().anyMatch(s -> s.equalsIgnoreCase(n)),
                            n -> { if (!c.sackHudItems.removeIf(s -> s.equalsIgnoreCase(n))) c.sackHudItems.add(n); });
                    b.slider("  Max Items", 1, 20, () -> (float) c.sackHudMax, v -> c.sackHudMax = Math.round(v));
                    b.toggle("  Only In Menus", () -> c.sackHudOnlyMenus, v -> c.sackHudOnlyMenus = v);
                    b.toggle("  Hide When Empty", () -> c.sackHudHideEmpty, v -> c.sackHudHideEmpty = v);
                    b.toggle("  Highlight Changes", () -> c.sackHudHighlight, v -> c.sackHudHighlight = v);
                    b.toggle("  Icons", () -> c.sackHudIcons, v -> c.sackHudIcons = v);
                });
    }

    private static List<String> options() {
        if (!options.isEmpty() && options.size() > cfg().sackHudItems.size()) return options;
        Set<String> out = new LinkedHashSet<>(cfg().sackHudItems);
        out.addAll(new java.util.TreeSet<>(SackItems.displayNames()));
        List<String> l = new ArrayList<>(out);
        if (!SackItems.displayNames().isEmpty()) options = l;
        return l;
    }

    private static void tick() {
        if (timer++ % 10 != 0) return;
        TeslaMapsConfig c = cfg();
        if (!SkyblockUtils.isInSkyblock()) {
            rows = List.of();
            return;
        }
        List<Row> out = new ArrayList<>();
        out.add(new Row("§eSack Items", ItemStack.EMPTY));
        if (!SackApi.enabled()) {
            out.add(new Row("§cTurn on Live Sack Contents", ItemStack.EMPTY));
        } else if (c.sackHudItems.isEmpty()) {
            if (c.sackHudHideEmpty) {
                rows = List.of();
                width = 0;
                return;
            }
            out.add(new Row("§7Pick items in Inventory settings", ItemStack.EMPTY));
        } else {
            long now = System.currentTimeMillis();
            int n = 0;
            for (String name : c.sackHudItems) {
                if (n++ >= c.sackHudMax) {
                    out.add(new Row("§7... " + (c.sackHudItems.size() - c.sackHudMax) + " more", ItemStack.EMPTY));
                    break;
                }
                String key = name.toLowerCase(Locale.ROOT);
                long amount = SackApi.amount(name);
                Long prev = last.put(key, amount);
                if (prev != null && prev != amount && prev >= 0 && amount >= 0) {
                    changedAt.put(key, now);
                    changedUp.put(key, amount > prev);
                }
                String col = "§f";
                Long at = changedAt.get(key);
                if (c.sackHudHighlight && at != null && now - at < HIGHLIGHT_MS) col = changedUp.getOrDefault(key, true) ? "§a" : "§c";
                String num = amount < 0 ? "§8?" : col + String.format(Locale.ROOT, "%,d", amount);
                ItemStack icon = c.sackHudIcons ? SkyblockItems.stackNow(SkyblockItems.idForName(name)) : ItemStack.EMPTY;
                out.add(new Row("§f" + name + " §7x" + num, icon));
            }
        }
        rows = out;
        width = rowsWidth(out);
    }

    private static boolean icons(List<Row> rs) {
        for (Row r : rs) if (!r.icon.isEmpty()) return true;
        return false;
    }

    private static int rowsWidth(List<Row> rs) {
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (Row r : rs) w = Math.max(w, mc.font.width(r.text));
        return w + 8 + (icons(rs) ? ROW + 2 : 0);
    }

    private static int height(int n) {
        return n * ROW + 4;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<Row> rs, int w) {
        if (rs.isEmpty()) return;
        boolean icons = icons(rs);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, w, height(rs.size()));
        for (int i = 0; i < rs.size(); i++) {
            Row r = rs.get(i);
            int ry = 2 + i * ROW;
            int tx = 4;
            if (icons) {
                if (!r.icon.isEmpty()) {
                    g.pose().pushMatrix();
                    g.pose().translate(4, ry);
                    g.pose().scale(0.75f, 0.75f);
                    g.item(r.icon, 0, 0);
                    g.pose().popMatrix();
                }
                tx += ROW + 2;
            }
            g.text(mc.font, r.text, tx, ry + 2, 0xFFFFFFFF, true);
        }
        g.pose().popMatrix();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || rows.isEmpty()) return;
        if (cfg().sackHudOnlyMenus && !(mc.screen instanceof AbstractContainerScreen<?>)) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), rows, width);
    }
}
