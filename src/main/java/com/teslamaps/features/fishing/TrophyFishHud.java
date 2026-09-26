package com.teslamaps.features.fishing;

// Ported from SkyHanni's TrophyFishDisplay.kt (text table, no icons)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TrophyFishHud {

    private static final long HIGHLIGHT_MS = 5_000;
    private static final int GAP = 6;

    private static List<String[]> rows = List.of();
    private static int[] colX = new int[0];
    private static int tableW, tableH;
    private static String title = "§e§lTrophy Fish";
    private static int builtVersion = -1;
    private static boolean highlightOn;
    private static int ticks;
    private static HudEditElement hud;

    private TrophyFishHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("trophyFishHud", "Trophy Fish").section("Fishing")
                .enabled(() -> cfg().trophyFishHud)
                .size(() -> Math.max(tableW, 80) + 8, () -> Math.max(tableH, 20) + 6)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s));
        Feature.of("TrophyFishHud").section("Fishing").when(() -> cfg().trophyFishHud)
                .tick(TrophyFishHud::tick)
                .hud("trophyfishhud", TrophyFishHud::render)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Trophy Fish");
                    b.toggle("Trophy Fish Display", () -> c.trophyFishHud, v -> c.trophyFishHud = v)
                            .desc("Table of all trophy fish with Bronze / Silver / Gold / Diamond and total, counted live. "
                                    + "Talk to Odger (Trophy Fish menu) or use Import From API for the start values. Crimson Isle only. Position in /tmap gui")
                            .tags("trophy", "fish", "odger", "crimson", "lava");
                    b.dropdown("  Only Show Missing", new String[] {"None", "Bronze", "Silver", "Gold", "Diamond"},
                            () -> c.trophyFishHudMissing, v -> { c.trophyFishHudMissing = v; builtVersion = -1; });
                    b.dropdown("  Sort By", new String[] {"Total", "Name", "Rarity"},
                            () -> c.trophyFishHudSort, v -> { c.trophyFishHudSort = v; builtVersion = -1; });
                    b.dropdown("  Show", new String[] {"Always", "Rod In Hand", "Inventory Open"},
                            () -> c.trophyFishHudWhen, v -> c.trophyFishHudWhen = v);
                    b.toggle("  Highlight New Catch (5s)", () -> c.trophyFishHudHighlight, v -> c.trophyFishHudHighlight = v);
                    b.toggle("  Checkmarks Instead Of Numbers", () -> c.trophyFishHudCheck, v -> { c.trophyFishHudCheck = v; builtVersion = -1; });
                    b.button("  Import From API", TrophyFishData::importFromApi);
                });
    }

    private static void tick() {
        boolean hl = cfg().trophyFishHudHighlight && System.currentTimeMillis() - TrophyFishData.lastAt < HIGHLIGHT_MS;
        if (builtVersion == TrophyFishData.version() && hl == highlightOn && ++ticks < 100) return;
        ticks = 0;
        highlightOn = hl;
        builtVersion = TrophyFishData.version();
        rebuild(hl);
    }

    private static void rebuild(boolean hl) {
        TeslaMapsConfig c = cfg();
        List<String[]> out = new ArrayList<>();
        if (TrophyFishData.empty()) {
            out.add(new String[] {"§cNo data - talk to Odger"});
        } else {
            int missing = switch (c.trophyFishHudMissing) {
                case "Bronze" -> 0;
                case "Silver" -> 1;
                case "Gold" -> 2;
                case "Diamond" -> 3;
                default -> -1;
            };
            List<TrophyFishData.Fish> fish = new ArrayList<>(TrophyFishData.FISH);
            switch (c.trophyFishHudSort) {
                case "Name" -> fish.sort((a, b) -> a.display().compareToIgnoreCase(b.display()));
                case "Total" -> fish.sort((a, b) -> Integer.compare(total(b.key()), total(a.key())));
                default -> {}
            }
            out.add(new String[] {"", "§8B", "§7S", "§6G", "§bD", "§5Σ"});
            for (TrophyFishData.Fish f : fish) {
                int[] n = TrophyFishData.counts(f.key());
                if (missing >= 0) {
                    boolean has = false;
                    for (int i = missing; i < 4; i++) if (n[i] > 0) has = true;
                    if (has) continue;
                }
                boolean fresh = hl && f.key().equals(TrophyFishData.lastKey);
                String[] r = new String[6];
                r[0] = f.color() + f.display();
                for (int i = 0; i < 4; i++) {
                    String col = fresh && i == TrophyFishData.lastTier ? "§a" : TrophyFishData.TIER_COLORS[i];
                    if (n[i] == 0) r[i + 1] = c.trophyFishHudCheck ? "§c✖" : col + "0";
                    else r[i + 1] = col + (c.trophyFishHudCheck ? "§l✔" : String.format(Locale.ROOT, "%,d", n[i]));
                }
                r[5] = (fresh ? "§a" : "§5") + String.format(Locale.ROOT, "%,d", total(f.key()));
                out.add(r);
            }
            if (out.size() == 1) {
                out.clear();
                out.add(new String[] {"§eAll " + c.trophyFishHudMissing + " trophy fish caught!"});
            }
        }
        Font font = Minecraft.getInstance().font;
        int cols = 0;
        for (String[] r : out) cols = Math.max(cols, r.length);
        int[] w = new int[cols];
        for (String[] r : out) {
            if (r.length == 1) continue;
            for (int i = 0; i < r.length; i++) w[i] = Math.max(w[i], font.width(r[i]));
        }
        int[] x = new int[cols];
        int acc = 0;
        for (int i = 0; i < cols; i++) { x[i] = acc; acc += w[i] + GAP; }
        int width = Math.max(acc - GAP, font.width(title));
        for (String[] r : out) if (r.length == 1) width = Math.max(width, font.width(r[0]));
        rows = out;
        colX = x;
        tableW = width;
        tableH = (out.size() + 1) * 10;
    }

    private static int total(String key) {
        int[] n = TrophyFishData.counts(key);
        return n[0] + n[1] + n[2] + n[3];
    }

    private static boolean shown(Minecraft mc) {
        if (!SkyblockUtils.getArea().equalsIgnoreCase("Crimson Isle")) return false;
        return switch (cfg().trophyFishHudWhen) {
            case "Rod In Hand" -> com.teslamaps.features.FishingHelpers.holdingRod();
            case "Inventory Open" -> mc.screen instanceof InventoryScreen;
            default -> true;
        };
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || rows.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !shown(mc)) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale());
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale) {
        if (rows.isEmpty()) rebuild(false);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, tableW + 8, tableH + 6);
        g.text(mc.font, title, 4, 4, 0xFFFFFFFF, true);
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            int ry = 14 + i * 10;
            for (int j = 0; j < r.length; j++) g.text(mc.font, r[j], 4 + (r.length == 1 ? 0 : colX[j]), ry, 0xFFFFFFFF, true);
        }
        g.pose().popMatrix();
    }
}
