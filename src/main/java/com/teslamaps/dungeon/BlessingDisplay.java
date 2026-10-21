package com.teslamaps.dungeon;

// Ported from NoammAddons' visual/BlessingDisplay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.registry.HudEditElement.HudCtx;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BlessingDisplay {

    private enum Blessing {
        POWER("Power", "Blessing of Power (X{0,3}(IX|IV|V?I{0,3}))", () -> cfg().blessingPower, () -> cfg().blessingPowerColor),
        LIFE("Life", "Blessing of Life (X{0,3}(IX|IV|V?I{0,3}))", () -> cfg().blessingLife, () -> cfg().blessingLifeColor),
        WISDOM("Wisdom", "Blessing of Wisdom (X{0,3}(IX|IV|V?I{0,3}))", () -> cfg().blessingWisdom, () -> cfg().blessingWisdomColor),
        STONE("Stone", "Blessing of Stone (X{0,3}(IX|IV|V?I{0,3}))", () -> cfg().blessingStone, () -> cfg().blessingStoneColor),
        TIME("Time", "Blessing of Time (V)", () -> cfg().blessingTime, () -> cfg().blessingTimeColor);

        final String display;
        final Pattern regex;
        final Supplier<Boolean> on;
        final Supplier<String> color;
        int current;

        Blessing(String display, String regex, Supplier<Boolean> on, Supplier<String> color) {
            this.display = display; this.regex = Pattern.compile(regex); this.on = on; this.color = color;
        }
    }

    private record Line(String text, int color) {}

    private static HudEditElement hud;
    private static String parsed = null;
    private static Object lastLevel;

    private BlessingDisplay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("blessingDisplay", "Blessing Display").ctx(HudCtx.DUNGEON)
                .enabled(() -> cfg().blessingDisplay)
                .size(() -> Minecraft.getInstance().font.width("Wisdom 5"), () -> 9 * 5)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, lines(true)));
        Feature.of("BlessingDisplay").when(() -> cfg().blessingDisplay)
                .tick(BlessingDisplay::tick)
                .hud("blessingdisplay", BlessingDisplay::render)
                .config("Score & Splits", b -> {
                    b.label("Blessing Display");
                    b.toggle("Blessing Display", () -> cfg().blessingDisplay, v -> cfg().blessingDisplay = v)
                            .desc("One line per active blessing, own colors. Position in /tmap gui.");
                    b.toggle("  Power", () -> cfg().blessingPower, v -> cfg().blessingPower = v);
                    b.color("    Power Color", () -> cfg().blessingPowerColor, v -> cfg().blessingPowerColor = v);
                    b.toggle("  Time", () -> cfg().blessingTime, v -> cfg().blessingTime = v);
                    b.color("    Time Color", () -> cfg().blessingTimeColor, v -> cfg().blessingTimeColor = v);
                    b.toggle("  Wisdom", () -> cfg().blessingWisdom, v -> cfg().blessingWisdom = v);
                    b.color("    Wisdom Color", () -> cfg().blessingWisdomColor, v -> cfg().blessingWisdomColor = v);
                    b.toggle("  Life", () -> cfg().blessingLife, v -> cfg().blessingLife = v);
                    b.color("    Life Color", () -> cfg().blessingLifeColor, v -> cfg().blessingLifeColor = v);
                    b.toggle("  Stone", () -> cfg().blessingStone, v -> cfg().blessingStone = v);
                    b.color("    Stone Color", () -> cfg().blessingStoneColor, v -> cfg().blessingStoneColor = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            for (Blessing b : Blessing.values()) b.current = 0;
            parsed = null;
        }
        if (!DungeonManager.isInDungeon()) return;
        String f = DungeonInfoHud.footer();
        if (f == null || f.equals(parsed)) return;
        parsed = f;
        String clean = StringUtil.strip(f);
        for (Blessing b : Blessing.values()) {
            Matcher m = b.regex.matcher(clean);
            if (m.find()) b.current = WitherDragons.romanToInt(m.group(1));
        }
    }

    private static List<Line> lines(boolean example) {
        List<Line> out = new ArrayList<>();
        for (Blessing b : Blessing.values()) {
            int value = example ? 5 : b.current;
            if (!b.on.get() || value <= 0) continue;
            out.add(new Line(b.display + " §f" + value, TeslaMapsConfig.parseColor(b.color.get()) | 0xFF000000));
        }
        return out;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !DungeonManager.isInDungeon()) return;
        List<Line> ls = lines(false);
        if (ls.isEmpty()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), ls);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<Line> ls) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int row = 0;
        for (Line l : ls) {
            g.text(mc.font, l.text(), 0, row, l.color());
            row += 9;
        }
        pose.popMatrix();
    }
}
