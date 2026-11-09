package com.teslamaps.features.crimson;

// After SkyHanni's nether/VolcanoExplosivityDisplay.kt and DulkirMod's MatchoAlert
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

public final class VolcanoHud {

    private static final List<String> SAMPLE = List.of("§bVolcano Explosivity§7: §cERUPTING");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, ticks;
    private static Object lastLevel;
    private static boolean matchoAlerted;

    private VolcanoHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("volcanoHud", "Volcano Explosivity").section("Crimson Isle")
                .enabled(() -> cfg().volcanoHud)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width, () -> TextHud.height(1))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? TextHud.width(SAMPLE) : width));
        Feature.of("VolcanoHud").section("Crimson Isle").when(() -> cfg().volcanoHud || cfg().matchoAlert)
                .tick(VolcanoHud::tick)
                .hudHiddenInInventory("volcanohud", VolcanoHud::render)
                .config("Crimson Isle", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Volcano");
                    b.toggle("Volcano Explosivity HUD", () -> c.volcanoHud, v -> c.volcanoHud = v)
                            .desc("Shows the Volcano status from the tab list (needs the Volcano tab widget). Move it in /tmap gui")
                            .tags("volcano", "matcho", "lava", "crimson");
                    b.toggle("  Matcho Alert", () -> c.matchoAlert, v -> c.matchoAlert = v)
                            .desc("Title + sound once per lobby as soon as the volcano is no longer INACTIVE (Matcho can spawn). Works without the HUD");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            matchoAlerted = false;
            lines = List.of();
        }
        if (++ticks % 20 != 0) return;
        if (!Crimson.here()) {
            lines = List.of();
            return;
        }
        String status = null;
        for (String l : TabListUtils.getTabListLines()) {
            String s = StringUtil.stripTrim(l);
            if (s.startsWith("Volcano: ")) {
                status = s.substring(9).trim();
                break;
            }
        }
        if (status == null || status.isEmpty()) {
            lines = List.of();
            return;
        }
        String col = status.equals("INACTIVE") ? "§7" : status.equals("ERUPTING") ? "§c" : "§6";
        lines = List.of("§bVolcano Explosivity§7: " + col + status);
        width = TextHud.width(lines);
        if (cfg().matchoAlert && !matchoAlerted && !status.equals("INACTIVE")) {
            matchoAlerted = true;
            Crimson.title("§b§lMatcho", "§7Volcano: " + col + status, SoundEvents.NOTE_BLOCK_PLING.value());
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (!cfg().volcanoHud || mc.gui.hud.isHidden() || lines.isEmpty() || hud == null) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
