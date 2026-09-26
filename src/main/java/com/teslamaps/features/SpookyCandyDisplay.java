package com.teslamaps.features;

// After SkyHanni's ScoreboardEventSpooky.kt / SkyblockAddons' SpookyEventManager (tab footer "Your Candy:")
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonInfoHud;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SpookyCandyDisplay {

    private static final Pattern CANDY = Pattern.compile("Your Candy:\\s*([\\d,]+) Green,\\s*([\\d,]+) Purple\\s*\\(([\\d,]+) pts\\.\\)");
    private static final List<String> SAMPLE = List.of("§6Spooky Candy", "§a12 Green §5 3 Purple", "§627 §7pts.");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static String lastFooter = "";
    private static int width, timer;

    private SpookyCandyDisplay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Events", "Skyblock", true);
        hud = HudEditElement.of("spookyCandy", "Spooky Candy").section("Events")
                .enabled(() -> cfg().spookyCandyDisplay)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width, () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? TextHud.width(SAMPLE) : width));
        Feature.of("SpookyCandyDisplay").section("Events").when(() -> cfg().spookyCandyDisplay)
                .tick(SpookyCandyDisplay::tick)
                .hudHiddenInInventory("spookycandy", SpookyCandyDisplay::render)
                .config("Events", b -> b.toggle("Spooky Candy Display", () -> cfg().spookyCandyDisplay, v -> cfg().spookyCandyDisplay = v)
                        .desc("During the Spooky Festival: green and purple candy and your points from the tab footer (/tmap gui)."));
    }

    private static void tick() {
        if (++timer % 20 != 0) return;
        String footer = DungeonInfoHud.footer();
        if (footer.equals(lastFooter)) return;
        lastFooter = footer;
        int i = footer.indexOf("Your Candy:");
        if (i < 0) {
            lines = List.of();
            return;
        }
        Matcher m = CANDY.matcher(StringUtil.strip(footer.substring(i)));
        if (m.find()) {
            lines = List.of("§6Spooky Candy", "§a" + m.group(1) + " Green §5" + m.group(2) + " Purple", "§6" + m.group(3) + " §7pts.");
        } else {
            int nl = footer.indexOf('\n', i);
            String rest = StringUtil.strip(footer.substring(i + 11, nl < 0 ? footer.length() : nl)).trim();
            lines = rest.isEmpty() ? List.of() : List.of("§6Spooky Candy", "§f" + rest);
        }
        width = TextHud.width(lines);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || lines.isEmpty() || hud == null) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
