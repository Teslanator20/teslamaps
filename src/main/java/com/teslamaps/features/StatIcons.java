package com.teslamaps.features;

// Ported from SkyHanni's ColorfulItemStats
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StatIcons {

    private record Icon(String glyph, int color) {}

    private static final Pattern STAT = Pattern.compile("([a-zA-Z ]+): ([-+]?[\\d.,%s]+)(?:\\s|$)");
    private static final Map<String, Icon> ICONS = new HashMap<>();

    private static final int RED = 0xFF5555, DARK_RED = 0xAA0000, GREEN = 0x55FF55, DARK_GREEN = 0x00AA00,
            WHITE = 0xFFFFFF, BLUE = 0x5555FF, YELLOW = 0xFFFF55, AQUA = 0x55FFFF, DARK_AQUA = 0x00AAAA,
            GOLD = 0xFFAA00, PURPLE = 0xAA00AA, PINK = 0xFF55FF;

    private static void put(String name, String glyph, int color) { ICONS.put(name, new Icon(glyph, color)); }

    static {
        put("Damage", "❁", RED);
        put("Health", "❤", RED);
        put("Defense", "❈", GREEN);
        put("True Defense", "❂", WHITE);
        put("Strength", "❁", RED);
        put("Crit Chance", "☣", BLUE);
        put("Crit Damage", "☠", BLUE);
        put("Bonus Attack Speed", "⚔", YELLOW);
        put("Attack Speed", "⚔", YELLOW);
        put("Ferocity", "⫽", RED);
        put("Swing Range", "Ⓢ", YELLOW);
        put("Intelligence", "✎", AQUA);
        put("Ability Damage", "๑", RED);
        put("Health Regen", "❣", RED);
        put("Vitality", "♨", DARK_RED);
        put("Mending", "☄", GREEN);
        put("Breaking Power", "Ⓟ", DARK_GREEN);
        put("Mining Speed", "⸕", GOLD);
        put("Mining Spread", "▚", YELLOW);
        put("Gemstone Spread", "▚", YELLOW);
        put("Pristine", "✧", PURPLE);
        put("Bonus Pest Chance", "ൠ", DARK_GREEN);
        put("Overbloom", "✿", YELLOW);
        put("Sweep", "∮", DARK_GREEN);
        put("Fishing Speed", "☂", AQUA);
        put("Sea Creature Chance", "α", DARK_AQUA);
        put("Double Hook Chance", "⚓", BLUE);
        put("Trophy Chance", "♔", GOLD);
        put("Treasure Chance", "⛃", GOLD);
        put("Speed", "✦", WHITE);
        put("Magic Find", "✯", AQUA);
        put("Pet Luck", "♣", PINK);
        put("Heat Resistance", "♨", RED);
        put("Cold Resistance", "❄", AQUA);
        put("Respiration", "⚶", DARK_AQUA);
        put("Pressure Resistance", "❍", BLUE);
        put("Fear", "☠", PURPLE);
        put("Pull", "ᛷ", AQUA);
        put("Rift Time", "ф", GREEN);
        put("Mana Regen", "⚡", AQUA);
        put("Hearts", "♡", RED);
    }

    private static ItemStack lastStack;
    private static int lastInSize;
    private static List<Component> lastOut;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature feature = Feature.of("StatIcons")
                .when(() -> cfg().statIcons)
                .config("Tooltips & Value", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Stat Icons In Tooltips", () -> c.statIcons, v -> c.statIcons = v)
                            .desc("Adds the coloured stat symbol behind each stat value, like Strength: +100❁.");
                    b.toggle("  Icon Replaces % and s", () -> c.statIconsReplace, v -> {
                        c.statIconsReplace = v;
                        lastStack = null;
                    });
                });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (cfg().statIcons && feature.active()) onTooltip(stack, lines);
        });
    }

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        if (stack == lastStack && lines.size() == lastInSize && lastOut != null) {
            lines.clear();
            lines.addAll(lastOut);
            return;
        }
        int inSize = lines.size();
        boolean replace = cfg().statIconsReplace;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            Matcher m = STAT.matcher(line.getString());
            if (!m.find()) continue;
            String stat = m.group(1).trim();
            Icon icon = ICONS.get(stat);
            if (icon == null) {
                if (stat.endsWith(" Fortune")) icon = new Icon("☘", GOLD);
                else if (stat.endsWith(" Wisdom")) icon = new Icon("☯", DARK_AQUA);
                else continue;
            }
            String bonus = m.group(2);
            String shown = bonus;
            if (replace && (bonus.endsWith("%") || (stat.equals("Rift Time") && bonus.endsWith("s")))) {
                shown = bonus.substring(0, bonus.length() - 1);
            }
            Component out = insert(line, m.start(2), bonus.length(), shown, icon);
            if (out != null) lines.set(i, out);
        }
        lastStack = stack;
        lastInSize = inSize;
        lastOut = List.copyOf(lines);
    }

    private static Component insert(Component line, int start, int len, String shown, Icon icon) {
        List<Style> styles = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        line.visit((style, text) -> {
            styles.add(style);
            parts.add(text);
            return Optional.empty();
        }, Style.EMPTY);
        MutableComponent out = Component.empty();
        int pos = 0, end = start + len;
        boolean done = false;
        for (int k = 0; k < parts.size(); k++) {
            String p = parts.get(k);
            Style st = styles.get(k);
            int a = pos, b = pos + p.length();
            pos = b;
            if (done || b <= start || a >= end) {
                if (!p.isEmpty()) out.append(Component.literal(p).setStyle(st));
                continue;
            }
            if (a > start || b < end) return null;
            int s = start - a, e = end - a;
            if (s > 0) out.append(Component.literal(p.substring(0, s)).setStyle(st));
            out.append(Component.literal(shown).setStyle(st));
            out.append(Component.literal(icon.glyph).setStyle(st.withColor(icon.color)));
            if (e < p.length()) out.append(Component.literal(p.substring(e)).setStyle(st));
            done = true;
        }
        return done ? out : null;
    }
}
