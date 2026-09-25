package com.teslamaps.features;

// Ported from SkyHanni's ReplaceRomanNumerals.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Roman numerals in tooltips become digits where a keyword marks them as a level/tier. */
public final class RomanNumerals {

    private static final String R = "(?<roman>[IVXLCDM]+)";
    private static final String SKILLS = "(?:Farming|Mining|Combat|Foraging|Fishing|Enchanting|Alchemy|Carpentry|Taming|Hunting|Runecrafting|Social)";

    private static final List<Pattern> BASE = List.of(
            Pattern.compile("\\bLevel " + R + "\\b"),
            Pattern.compile("\\bTier " + R + "\\b"),
            Pattern.compile("\\bChapter " + R + "\\b"),
            Pattern.compile("\\bSkill " + R + "\\b"),
            Pattern.compile("\\bMilestone " + R + "\\b"),
            Pattern.compile("\\bFloor " + R + "\\b"),
            Pattern.compile("\\bCollection " + R + "\\b"),
            Pattern.compile("\\bProgress to (?:\\w+ )+" + R + ":"),
            Pattern.compile("\\b" + SKILLS + " " + R + "\\b"),
            Pattern.compile("\\bHeart of the \\w+ " + R + "\\b"),
            Pattern.compile("\\b" + R + " Rewards?:"));
    private static final Pattern NAMED_TIER = Pattern.compile("^(?:[\\w']+ )+" + R + "\\s*$");
    private static final List<Pattern> WITH_CONTEXT;
    static {
        List<Pattern> l = new ArrayList<>(BASE);
        l.add(NAMED_TIER);
        WITH_CONTEXT = List.copyOf(l);
    }
    private static final Pattern STANDALONE = Pattern.compile("\\b" + R + "\\b");
    private static final Pattern VALID = Pattern.compile("^M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$");

    private static final Pattern TASK_LIST = Pattern.compile("^\\d+ tasks?$");
    private static final Pattern PERK_SHOP = Pattern.compile("Perk Shop|^Purchase ");
    private static final Pattern CROP_MILESTONE = Pattern.compile("increase your .+ tier!");
    private static final Pattern COLLECTION_ITEM = Pattern.compile("Total Collected:|View all your ");

    private static final int NONE = 0, CONTEXT = 1, TITLE_ONLY = 2;

    private static Feature feature;
    private static List<Component> lastIn = List.of();
    private static List<Component> lastOut = List.of();
    private static String lastTitle = "";

    private RomanNumerals() {}

    public static void register() {
        feature = Feature.of("RomanNumerals").when(() -> TeslaMapsConfig.get().romanNumerals);
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (TeslaMapsConfig.get().romanNumerals && feature.active() && SkyblockUtils.isInSkyblock()) onTooltip(lines);
        });
    }

    private static void onTooltip(List<Component> lines) {
        if (lines.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        String title = mc.screen == null ? "" : StringUtil.stripTrim(mc.screen.getTitle().getString());
        if (title.equals(lastTitle) && lines.equals(lastIn)) {
            copyInto(lines, lastOut);
            return;
        }
        List<Component> in = List.copyOf(lines);
        int ctx = context(title, lines);
        List<Pattern> patterns = ctx != NONE ? WITH_CONTEXT : BASE;
        String titleNumeral = ctx == TITLE_ONLY ? null : findNumeral(lines.get(0), patterns);
        for (int i = 0; i < lines.size(); i++) {
            List<Pattern> p = ctx == TITLE_ONLY && i != 0 ? BASE : patterns;
            Component r = replace(lines.get(i), p, i == 0 ? null : titleNumeral);
            if (r != null) lines.set(i, r);
        }
        lastTitle = title;
        lastIn = in;
        lastOut = List.copyOf(lines);
    }

    private static void copyInto(List<Component> lines, List<Component> out) {
        for (int i = 0; i < lines.size() && i < out.size(); i++) lines.set(i, out.get(i));
    }

    private static int context(String title, List<Component> lines) {
        if (title.endsWith(" Upgrades")) return CONTEXT;
        if (title.endsWith(" Collection")) return TITLE_ONLY;
        for (Component c : lines) {
            String s = StringUtil.stripTrim(c.getString());
            if (TASK_LIST.matcher(s).matches() || PERK_SHOP.matcher(s).find() || CROP_MILESTONE.matcher(s).find()) return CONTEXT;
            if (COLLECTION_ITEM.matcher(s).find()) return TITLE_ONLY;
        }
        return NONE;
    }

    private static String findNumeral(Component title, List<Pattern> patterns) {
        String s = StringUtil.strip(title.getString());
        for (Pattern p : patterns) {
            Matcher m = p.matcher(s);
            while (m.find()) {
                String roman = m.group("roman");
                if (valid(roman)) return roman;
            }
        }
        return null;
    }

    private static Component replace(Component line, List<Pattern> patterns, String titleNumeral) {
        MutableComponent out = Component.empty();
        boolean[] changed = {false};
        line.visit((style, string) -> {
            String r = replaceText(string, patterns, titleNumeral);
            if (!r.equals(string)) changed[0] = true;
            out.append(Component.literal(r).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return changed[0] ? out : null;
    }

    private static String replaceText(String text, List<Pattern> patterns, String titleNumeral) {
        String result = text;
        for (Pattern p : patterns) result = replaceIn(result, p, null);
        if (titleNumeral != null) result = replaceIn(result, STANDALONE, titleNumeral);
        return result;
    }

    private static String replaceIn(String text, Pattern p, String only) {
        Matcher m = p.matcher(text);
        StringBuilder sb = null;
        int last = 0;
        while (m.find()) {
            String roman = m.group("roman");
            if (!valid(roman) || (only != null && !only.equals(roman))) continue;
            if (sb == null) sb = new StringBuilder(text.length());
            sb.append(text, last, m.start("roman")).append(toInt(roman));
            last = m.end("roman");
        }
        if (sb == null) return text;
        return sb.append(text, last, text.length()).toString();
    }

    private static boolean valid(String s) {
        return !s.isEmpty() && VALID.matcher(s).matches();
    }

    private static int toInt(String s) {
        int total = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = switch (s.charAt(i)) {
                case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50;
                case 'C' -> 100; case 'D' -> 500; case 'M' -> 1000; default -> 0;
            };
            total += v < prev ? -v : v;
            prev = Math.max(prev, v);
        }
        return total;
    }
}
