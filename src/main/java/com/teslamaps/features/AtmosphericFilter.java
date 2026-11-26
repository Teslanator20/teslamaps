package com.teslamaps.features;

// Ported from SkyHanni's AtmosphericFilterDisplay.kt + SkyblockSeason.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class AtmosphericFilter {

    private static final long SB_EPOCH = 1560275700L;
    private static final String[] SEASONS = {"§dSpring", "§6Summer", "§eAutumn", "§9Winter"};
    private static final String[] PERKS = {
            "§7Gain §6+25☘ Farming Fortune§7.",
            "§7Gain §3+20☯ Farming Wisdom§7.",
            "§4Pests §7spawn §a15% §7more often.",
            "§7Visitors give §a5% §7more §cCopper."};
    private static final String[] SHORT_PERKS = {"§6+25☘", "§3+20☯", "§a15%+§4ൠ", "§a5%+§cC"};
    private static final String SAMPLE = "§dSpring§7: §7Gain §6+25☘ Farming Fortune§7.";

    private static String line;
    private static int width, ticks;
    private static HudEditElement hud;

    private AtmosphericFilter() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("atmosphericFilter", "Atmospheric Filter").section("Garden")
                .enabled(() -> cfg().atmosphericFilterHud)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE) + 8, () -> 16)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, mc.font.width(SAMPLE) + 8));
        Feature.of("AtmosphericFilter").section("Garden").when(() -> cfg().atmosphericFilterHud)
                .tick(AtmosphericFilter::tick)
                .hudHiddenInInventory("atmosphericfilter", AtmosphericFilter::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Atmospheric Filter");
                    b.toggle("Atmospheric Filter HUD", () -> c.atmosphericFilterHud, v -> c.atmosphericFilterHud = v)
                            .desc("Current SkyBlock season and the Atmospheric Filter perk it gives. Position in /tmap gui.");
                    b.toggle("  Only Show Perk", () -> c.atmosphericFilterOnlyBuff, v -> c.atmosphericFilterOnlyBuff = v);
                    b.toggle("  Short Season", () -> c.atmosphericFilterShortSeason, v -> c.atmosphericFilterShortSeason = v);
                    b.toggle("  Short Perk", () -> c.atmosphericFilterShortPerk, v -> c.atmosphericFilterShortPerk = v);
                    b.toggle("  Show Outside The Garden", () -> c.atmosphericFilterEverywhere, v -> c.atmosphericFilterEverywhere = v);
                });
    }

    /** 0 spring .. 3 winter. */
    public static int season() {
        long sec = System.currentTimeMillis() / 1000L - SB_EPOCH;
        int month = (int) (sec / 1200 / 31 % 12);
        return month / 3;
    }

    private static void tick() {
        if (++ticks % 20 != 0) return;
        TeslaMapsConfig c = cfg();
        if (!SkyblockUtils.isInSkyblock() || (!c.atmosphericFilterEverywhere && !SkyblockUtils.isInGarden())) {
            line = null;
            return;
        }
        int s = season();
        String perk = c.atmosphericFilterShortPerk ? SHORT_PERKS[s] : PERKS[s];
        String name = c.atmosphericFilterShortSeason ? SEASONS[s].substring(0, 6) : SEASONS[s];
        line = c.atmosphericFilterOnlyBuff ? perk : name + "§7: " + perk;
        width = Minecraft.getInstance().font.width(line) + 8;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String l = line;
        if (l == null || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), l, width);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text, int w) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        com.teslamaps.render.gui.HudStyle.panel(g, 0, 0, w, 16);
        g.text(mc.font, text, 4, 4, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
