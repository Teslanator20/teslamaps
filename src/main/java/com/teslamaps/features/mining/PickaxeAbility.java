package com.teslamaps.features.mining;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Drill / pickaxe ability cooldown, read off the "Pickaxe Ability:" tab widget.
 * Needs the widget enabled via /tab.
 */
public class PickaxeAbility {

    private static final String HEADER = "Pickaxe Ability:";
    private static final Pattern ENTRY = Pattern.compile("^([A-Za-z' ]+): (.+)$");
    private static final Pattern SECONDS = Pattern.compile("^(?:(\\d+)m ?)?(?:(\\d+)s)?$");

    private static final String[] SAMPLE = { "§6Pickobulus§8: §a Available" };

    private static String ability = "";
    private static String state = "";
    private static int ticks = 0;

    public static void tick() {
        if (++ticks < 5) return;
        ticks = 0;

        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.pickaxeAbilityDisplay) return;
        if (c.pickaxeAbilityMiningOnly && !Mining.onMiningIsland()) return;

        boolean inWidget = false;
        for (String raw : TabListUtils.getOrderedLines()) {
            String line = StringUtil.stripTrim(raw);
            if (line.equals(HEADER)) {
                inWidget = true;
                continue;
            }
            if (!inWidget) continue;
            Matcher m = ENTRY.matcher(line);
            if (m.matches()) {
                ability = m.group(1);
                state = m.group(2);
                return;
            }
            break;
        }
        ability = "";
        state = "";
    }

    public static boolean ready() {
        return state.equalsIgnoreCase("Available");
    }

    /** Remaining cooldown in seconds, or -1 when ready / unknown. */
    public static int secondsLeft() {
        if (state.isEmpty() || ready()) return -1;
        Matcher m = SECONDS.matcher(state.trim());
        if (!m.matches()) return -1;
        int minutes = m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
        int seconds = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        return minutes * 60 + seconds;
    }

    public static List<String> lines() {
        List<String> out = new ArrayList<>();
        if (ability.isEmpty()) return out;
        int left = secondsLeft();
        String color = ready() ? "§a" : left >= 0 && left <= 5 ? "§e" : "§c";
        out.add("§6" + ability + "§8: " + color + state);
        return out;
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.pickaxeAbilityDisplay) return;
        if (com.teslamaps.utils.HudVisibility.suppressed()) return;
        if (c.pickaxeAbilityMiningOnly && !Mining.onMiningIsland()) return;
        if (c.pickaxeAbilityOnlyOnCooldown && ready()) return;

        List<String> lines = lines();
        if (lines.isEmpty()) return;
        draw(context, Minecraft.getInstance(), c.pickaxeAbilityX, c.pickaxeAbilityY, c.pickaxeAbilityScale, lines);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        List<String> lines = lines();
        draw(context, mc, x, y, scale, lines.isEmpty() ? List.of(SAMPLE) : lines);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale, List<String> lines) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int row = 0;
        for (String line : lines) {
            context.text(mc.font, line, 0, row * (mc.font.lineHeight + 1), 0xFFFFFFFF);
            row++;
        }
        pose.popMatrix();
    }

    public static int[] previewSize(Font font) {
        List<String> lines = lines();
        if (lines.isEmpty()) lines = List.of(SAMPLE);
        int w = 0;
        for (String line : lines) w = Math.max(w, font.width(line));
        return new int[] { w, lines.size() * (font.lineHeight + 1) };
    }
}
