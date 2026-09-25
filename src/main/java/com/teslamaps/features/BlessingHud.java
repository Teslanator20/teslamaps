package com.teslamaps.features;

// Ported from Odin's BlessingDisplay.kt (+ Devonian's roman numerals / hide 0)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.WitherDragons;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.ArrayList;
import java.util.List;

public final class BlessingHud {

    private static final String SECTION = "Score & Splits";
    private static final String[] COLORS = {"§4", "§5", "§7", "§c", "§b"};
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
            "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"};
    private static final int ROW = 10;
    private static final List<String> SAMPLE = List.of("§4Power: §a19", "§5Time: §a5", "§bWisdom: §a3");

    private static volatile List<String> lines = List.of();
    private static int linesWidth = 0;
    private static ClientLevel lastLevel;
    private static HudEditElement el;

    private BlessingHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        el = HudEditElement.of("blessingHud", "Blessings").ctx(HudEditElement.HudCtx.DUNGEON).section(SECTION)
                .enabled(() -> cfg().blessingHud)
                .size(() -> width(SAMPLE), () -> SAMPLE.size() * ROW)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, width(SAMPLE)));
        Feature.of("BlessingHud").section(SECTION).when(() -> cfg().blessingHud)
                .tick(BlessingHud::tick)
                .hud("blessinghud", BlessingHud::render);
    }

    private static boolean shown(TeslaMapsConfig c, int i) {
        return switch (i) {
            case 0 -> c.blessingHudPower;
            case 1 -> c.blessingHudTime;
            case 2 -> c.blessingHudStone;
            case 3 -> c.blessingHudLife;
            default -> c.blessingHudWisdom;
        };
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            WitherDragons.blessings = new int[5];
        }
        if (!DungeonManager.isInDungeon()) {
            lines = List.of();
            return;
        }
        TeslaMapsConfig c = cfg();
        int[] b = WitherDragons.blessings;
        List<String> out = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            if (b[i] <= 0 || !shown(c, i)) continue;
            String lvl = c.blessingHudRoman && b[i] < ROMAN.length ? ROMAN[b[i]] : String.valueOf(b[i]);
            out.add(COLORS[i] + WitherDragons.BLESSING_NAMES[i] + ": §a" + lvl);
        }
        if (out.equals(lines)) return;
        linesWidth = width(out);
        lines = out;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = lines;
        if (l.isEmpty() || el == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, el.x(), el.y(), el.scale(), l, linesWidth);
    }

    private static int width(List<String> l) {
        var font = Minecraft.getInstance().font;
        int w = 0;
        for (String s : l) w = Math.max(w, font.width(s));
        return w;
    }

    public static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l, int w) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, l.size() * ROW + 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * ROW, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
