package com.teslamaps.features.foraging;

// Concept from SkyblockAddons' TabListParser RAIN_TIME_PATTERN_S + RenderListener
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Park rain left, read off the "Rain:" tab line. */
public final class ParkRainTimer {

    private static final Pattern RAIN = Pattern.compile("Rain: (?<time>[0-9dhms ]+)");
    private static final String SAMPLE = "§9Rain: §f4m 20s";

    private static String text;
    private static int ticks;
    private static HudEditElement hud;
    private static ItemStack icon;

    private ParkRainTimer() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("parkRainTimer", "Park Rain Timer").ctx(HudEditElement.HudCtx.ANY).section("Foraging")
                .enabled(() -> cfg().parkRainTimer)
                .size(() -> width(SAMPLE), () -> 18)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, text == null ? SAMPLE : text));
        Feature.of("ParkRainTimer").section("Foraging").when(() -> cfg().parkRainTimer)
                .tick(ParkRainTimer::tick)
                .hudHiddenInInventory("parkraintimer", ParkRainTimer::render)
                .config("Foraging", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Park Rain Timer", () -> c.parkRainTimer, v -> c.parkRainTimer = v)
                            .desc("How long the Park rain lasts, from the Rain: line of the tab list (only shown while that line exists). Position in /tmap gui")
                            .tags("rain", "rainmaker", "park", "timer");
                });
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        if (!SkyblockUtils.isInSkyblock()) {
            text = null;
            return;
        }
        String found = null;
        for (String line : TabListUtils.getTabListLines()) {
            Matcher m = RAIN.matcher(StringUtil.strip(line).trim());
            if (m.matches()) { found = "§9Rain: §f" + m.group("time").trim(); break; }
        }
        text = found;
    }

    private static int width(String s) {
        return Minecraft.getInstance().font.width(s) + 22;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || text == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), text);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String s) {
        if (icon == null) icon = new ItemStack(Items.WATER_BUCKET);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, width(s), 18);
        g.item(icon, 2, 1);
        g.text(mc.font, s, 19, 5, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
