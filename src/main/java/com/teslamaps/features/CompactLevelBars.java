package com.teslamaps.features;

// Ported from SkyOcean's CompactLevelBarsModifier.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Leveling menu: each ▶ task and its progress bar become one "▶ Task (x/y XP)" line. */
public final class CompactLevelBars {

    private static Feature feature;

    private CompactLevelBars() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("CompactLevelBars").when(() -> cfg().compactLevelBars)
                .config("Tooltips & Value", b -> b.toggle("Compact Level Bars",
                        () -> cfg().compactLevelBars, v -> cfg().compactLevelBars = v)
                        .desc("Ways to Level Up / Skill Related Tasks: one line per task with x/y XP"));
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (cfg().compactLevelBars && feature.active()) modify(stack, lines);
        });
    }

    private static boolean menu() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() == null) return false;
        String title = StringUtil.stripTrim(mc.gui.screen().getTitle().getString());
        return title.equals("Ways to Level Up") || title.equals("Skill Related Tasks");
    }

    private static void modify(ItemStack stack, List<Component> lines) {
        if (!menu() || !StringUtil.stripTrim(stack.getHoverName().getString()).toLowerCase().endsWith(" tasks")) return;
        int first = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (stripped(lines.get(i)).startsWith("▶")) { first = i; break; }
        }
        if (first < 0) return;

        List<Component> out = new ArrayList<>(lines.subList(0, first));
        int i = first;
        boolean changed = false;
        while (i < lines.size()) {
            Component line = lines.get(i);
            if (!stripped(line).startsWith("▶")) {
                if (!changed) return;
                if (!out.isEmpty() && !stripped(out.getLast()).isEmpty()) out.add(Component.empty());
                out.addAll(lines.subList(i, lines.size()));
                break;
            }
            if (i + 1 >= lines.size() || stripped(lines.get(i + 1)).contains("▶")) {
                out.add(line);
                i++;
                continue;
            }
            long[] xp = parse(stripped(lines.get(i + 1)));
            if (xp == null) {
                out.add(line);
                i++;
                continue;
            }
            String cur = commas(xp[0]);
            String req = commas(xp[1]);
            out.add(Component.empty().append(line.copy()).append(Component.literal(
                    " §8(" + (xp[0] >= xp[1] ? "§a" : "§c") + cur + "§8/§a" + req + " §bXP§8)")));
            changed = true;
            i += 2;
            while (i < lines.size() && !stripped(lines.get(i)).isEmpty() && !stripped(lines.get(i)).startsWith("▶")) i++;
            while (i < lines.size() && stripped(lines.get(i)).isEmpty()) i++;
        }
        if (!changed) return;
        lines.clear();
        lines.addAll(out);
    }

    private static String commas(long v) {
        String s = Long.toString(v);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) sb.append(',');
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    private static String stripped(Component c) {
        return StringUtil.stripTrim(c.getString());
    }

    /** "12/396" or "1,200/2,000 XP" after the bar, null if the line has no x/y. */
    private static long[] parse(String s) {
        int slash = s.indexOf('/');
        if (slash < 0) return null;
        int a = slash - 1;
        while (a >= 0 && (Character.isDigit(s.charAt(a)) || s.charAt(a) == ',')) a--;
        int b = slash + 1;
        while (b < s.length() && (Character.isDigit(s.charAt(b)) || s.charAt(b) == ',')) b++;
        String left = s.substring(a + 1, slash).replace(",", "");
        String right = s.substring(slash + 1, b).replace(",", "");
        if (left.isEmpty() || right.isEmpty()) return null;
        try {
            return new long[]{Long.parseLong(left), Long.parseLong(right)};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
