package com.teslamaps.features;

// Ported from SkyOcean's PestBaitType.kt (spray -> pest table from SkyHanni's PestType.kt)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.ArrayList;
import java.util.List;

public final class PestBaitPanel {

    private static final String[][] SPRAYS = {
            {"COMPOST", "Compost", "Earthworm, Mosquito"},
            {"PLANT_MATTER", "Plant Matter", "Locust, Slug"},
            {"DUNG", "Dung", "Beetle, Fly"},
            {"HONEY_JAR", "Honey Jar", "Cricket, Moth"},
            {"CHEESE_FUEL", "Tasty Cheese", "Mite, Rat"},
            {"JELLY", "Jelly", "Praying Mantis, Firefly, Dragonfly"},
            {"MOONDEW", "Moondew", "Lunar Moth"},
    };

    private static final EcoPanel panel = new EcoPanel(EcoPanel.Side.RIGHT);
    private static Feature feature;
    private static Screen lastScreen;

    private PestBaitPanel() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("PestBaitPanel").section("Garden").when(() -> cfg().pestBaitPanel)
                .tick(PestBaitPanel::tick)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Pest Bait Overview", () -> c.pestBaitPanel, v -> c.pestBaitPanel = v)
                            .desc("Panel next to Pest, Mouse and Vermin Trap menus: which spray/bait attracts which pests. Click a row to get 64 from your sacks (/gfs).")
                            .tags("pest", "bait", "spray", "trap", "gfs");
                });
    }

    private static boolean trap(Screen s) {
        if (!(s instanceof AbstractContainerScreen<?>)) return false;
        String t = StringUtil.stripTrim(s.getTitle().getString());
        return t.equals("Pest Trap") || t.equals("Mouse Trap") || t.equals("Vermin Trap");
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!SkyblockUtils.isInGarden() || !trap(mc.screen)) {
            lastScreen = null;
            panel.clear();
            return;
        }
        if (lastScreen == mc.screen) return;
        lastScreen = mc.screen;
        List<EcoPanel.Row> rows = new ArrayList<>(SPRAYS.length + 1);
        rows.add(EcoPanel.Row.of("§ePest Bait Types"));
        for (String[] s : SPRAYS) {
            String id = s[0], name = s[1];
            rows.add(new EcoPanel.Row("§a" + name + "§8: §7" + s[2],
                    EcoPanel.tip(List.of("§7Click to get 64 §a" + name + "§7 from sacks!")),
                    () -> gfs(id), -1));
        }
        panel.set(rows);
    }

    private static void gfs(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendCommand("gfs " + id + " 64");
    }

    public static void render(GuiGraphicsExtractor g, int mx, int my) {
        if (feature == null || !feature.active() || panel.empty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != lastScreen || !(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        panel.render(g, cs, 0, mx, my);
    }

    public static boolean click(double mx, double my, int button) {
        if (feature == null || !feature.active() || panel.empty()) return false;
        if (Minecraft.getInstance().screen != lastScreen) return false;
        return panel.click(mx, my, button);
    }
}
