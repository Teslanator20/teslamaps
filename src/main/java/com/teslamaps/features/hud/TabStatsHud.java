package com.teslamaps.features.hud;

// HUD-037, after GanKura's FerocityScanner/FerocityHud and SkyHanni's FerocityDisplay.kt, any tab stat
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.screen.TextFieldsScreen;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TabStatsHud {

    private static final List<String> SAMPLE = List.of("§cFerocity: §f⫽123");
    // tab needs this many lines before a missing stat counts as missing
    private static final int MIN_LINES = 20;
    private static final Map<String, String> COLORS = Map.ofEntries(
            Map.entry("ferocity", "§c"), Map.entry("strength", "§c"), Map.entry("health", "§c"),
            Map.entry("defense", "§a"), Map.entry("speed", "§f"), Map.entry("intelligence", "§b"),
            Map.entry("crit chance", "§9"), Map.entry("crit damage", "§9"), Map.entry("attack speed", "§e"),
            Map.entry("magic find", "§b"), Map.entry("pet luck", "§d"), Map.entry("true defense", "§f"),
            Map.entry("mining speed", "§6"), Map.entry("mining fortune", "§6"), Map.entry("farming fortune", "§6"),
            Map.entry("foraging fortune", "§6"), Map.entry("sea creature chance", "§3"), Map.entry("fishing speed", "§b"),
            Map.entry("ability damage", "§c"), Map.entry("vitality", "§4"), Map.entry("mending", "§a"));

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, timer;

    private TabStatsHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("tabStatsHud", "Tab Stats")
                .enabled(() -> cfg().tabStatsHud)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width, () -> TextHud.height(Math.max(1, lines.size())))
                .preview((g, mc, x, y, s) -> {
                    if (lines.isEmpty()) TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE));
                    else TextHud.draw(g, mc, x, y, s, lines, width);
                });
        Feature.of("TabStatsHud").when(() -> cfg().tabStatsHud)
                .tick(TabStatsHud::tick)
                .hudHiddenInInventory("tabstatshud", TabStatsHud::render)
                .config("Interface", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Tab Stats HUD");
                    b.toggle("Tab Stats HUD", () -> c.tabStatsHud, v -> { c.tabStatsHud = v; timer = 0; })
                            .desc("Shows stats from the tab list Stats widget (Ferocity by default). "
                                    + "The stat has to be enabled in the widget (/tab). Position in /tmap gui.")
                            .tags("ferocity", "stat");
                    b.button("  Edit Stats", () -> Minecraft.getInstance().setScreen(new TextFieldsScreen(
                            Minecraft.getInstance().screen, "Tab Stats HUD", "Stat names, comma separated (e.g. Ferocity, Magic Find)",
                            List.of(new TextFieldsScreen.Field("Stats", () -> c.tabStatsHudStats, v -> { c.tabStatsHudStats = v; timer = 0; })),
                            200)));
                });
    }

    private static void tick() {
        if (timer++ % 10 != 0) return;
        if (!SkyblockUtils.isInSkyblock()) {
            lines = List.of();
            return;
        }
        List<String> tab = TabListUtils.getTabListLines();
        List<String> out = new ArrayList<>();
        for (String raw : cfg().tabStatsHudStats.split(",")) {
            String stat = raw.trim();
            if (stat.isEmpty()) continue;
            String prefix = stat.toLowerCase(java.util.Locale.ROOT) + ":";
            String value = null;
            for (String line : tab) {
                String s = StringUtil.stripTrim(line);
                if (s.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                    value = s.substring(prefix.length()).trim();
                    break;
                }
            }
            String color = COLORS.getOrDefault(stat.toLowerCase(java.util.Locale.ROOT), "§e");
            String name = StringUtil.titleCase(stat);
            if (value != null) out.add(color + name + ": §f" + value);
            else if (tab.size() >= MIN_LINES) out.add(color + name + ": §7not in tab §8(/tab)");
        }
        lines = out;
        width = TextHud.width(out);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || lines.isEmpty()) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
