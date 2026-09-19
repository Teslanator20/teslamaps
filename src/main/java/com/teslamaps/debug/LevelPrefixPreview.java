package com.teslamaps.debug;

import com.teslamaps.render.gui.AnimFades;
import com.teslamaps.render.gui.ChromaColors;
import com.teslamaps.utils.OkLabColor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.function.Consumer;

/** /tmap debug testcolors - level prefix candidates beyond Hypixel's last one (480). */
public class LevelPrefixPreview {

    public static void print(Consumer<Component> out, int only) {
        AnimFades.previewActive = true;
        if (only == 0) {
            out.accept(Component.literal("§8──── §fHypixel §7(one colour every 40, last one is 480) §8────"));
            out.accept(row("ref", solid(420, 0xFFAA00, false), "Gold · 400-439"));
            out.accept(row("ref", solid(480, 0xAA0000, false), "Dark Red · 480+"));
        }

        if (only == 0 || only == 520) {
            out.accept(Component.literal("§8──── §c520 §7ember §8────"));
            out.accept(row("520.1", solid(520, 0xFF3B1F, false), "Molten"));
            out.accept(row("520.2", fade(520, false, 0xAA0000, 0xFF5555), "Blood fade"));
            out.accept(row("520.3", fade(520, false, 0xAA0000, 0xFFAA00), "Ember"));
            out.accept(row("520.4", fade(520, true, 0xFF5555, 0xFFAA00), "Fire bold"));
            out.accept(row("520.5", split(520, 0xAA0000, false, 0xFF5555, 0xFFFF55), "Dark red brackets, torch digits"));
            out.accept(row("520.6", fade(520, false, 0xFFAA00, 0xFF5555, 0xAA0000), "Flame 3-stop"));
            out.accept(row("520.7", solid(520, 0xC41E3A, true), "Cardinal bold"));
            out.accept(row("520.8", fade(520, false, 0xFF5555, 0x3C0000), "Ash"));
            out.accept(row("520.9", split(520, 0x550000, false, 0xFF7A00, 0xFF7A00), "Coal brackets, ember digits"));
            out.accept(row("520.10", fade(520, false, 0xFF2D00, 0xFFD000), "Lava"));
            out.accept(row("520.11", anim(520, "ember_flow", false), "§oanimiert§7 Ember flow · dunkelrot ⇄ gold"));
            out.accept(row("520.12", anim(520, "ember_shine", false), "§oanimiert§7 Ember shine · Lichtband läuft durch"));
            out.accept(row("520.13", anim(520, "ember_pulse", true), "§oanimiert§7 Ember pulse bold"));
            out.accept(row("520.14", anim(520, "flame_wave", false), "§oanimiert§7 Flame wave · gold/rot/dunkelrot"));
        }

        if (only == 0 || only == 560) {
            out.accept(Component.literal("§8──── §d560 §7arcane §8────"));
            out.accept(row("560.1", solid(560, 0xB026FF, false), "Neon purple"));
            out.accept(row("560.2", fade(560, false, 0xAA00AA, 0x5555FF), "Arcane"));
            out.accept(row("560.3", fade(560, false, 0xFF55FF, 0x8000FF), "Orchid"));
            out.accept(row("560.4", fade(560, false, 0xFF55FF, 0xAA00AA, 0x5555FF), "Nebula 3-stop"));
            out.accept(row("560.5", split(560, 0xAA00AA, true, 0xFF55FF, 0xFF55FF), "Dark purple brackets, pink digits"));
            out.accept(row("560.6", fade(560, false, 0x5555FF, 0x55FFFF), "Ice magic"));
            out.accept(row("560.7", solid(560, 0x7B2FFF, true), "Amethyst bold"));
            out.accept(row("560.8", fade(560, false, 0x8000FF, 0xFF0080), "Magenta shift"));
            out.accept(row("560.9", fade(560, false, 0x6A00FF, 0x1A0033), "Void"));
            out.accept(row("560.10", fade(560, false, 0x55FFFF, 0x5555FF, 0xAA00AA), "Twilight"));
            out.accept(row("560.11", anim(560, "arcane_flow", false), "§oanimiert§7 Arcane flow · lila ⇄ blau"));
            out.accept(row("560.12", anim(560, "arcane_shine", false), "§oanimiert§7 Arcane shine · weißes Band"));
            out.accept(row("560.13", anim(560, "arcane_pulse", true), "§oanimiert§7 Arcane pulse bold · blau ⇄ aqua"));
            out.accept(row("560.14", anim(560, "nebula_wave", false), "§oanimiert§7 Nebula wave · pink/lila/blau"));
        }

        if (only == 0 || only == 600) {
            out.accept(Component.literal("§8──── §e600 §7celestial §8────"));
            out.accept(row("600.1", fade(600, false, 0xFFD700, 0xFFFFFF), "Divine"));
            out.accept(row("600.2", solid(600, 0xFFE97F, false), "Starlight"));
            out.accept(row("600.3", fade(600, false, 0xFFFFFF, 0x55FFFF), "Frost"));
            out.accept(row("600.4", fade(600, false, 0xFFAA00, 0xFFFF55, 0xFFFFFF), "Sun 3-stop"));
            out.accept(row("600.5", split(600, 0xFFFFFF, true, 0xFFD700, 0xFFD700), "White brackets, gold digits"));
            out.accept(row("600.6", fade(600, false, 0x00FFC8, 0x00A2FF), "Aurora"));
            out.accept(row("600.7", solid(600, 0x00FFB3, true), "Mint bold"));
            out.accept(row("600.8", fade(600, false, 0xFFFFFF, 0xFFAA00, 0xFF5555), "Sunset"));
            out.accept(row("600.9", fade(600, false, 0xC0C0C0, 0xFFFFFF), "Platinum"));
            out.accept(row("600.10", fade(600, false, 0xFFD700, 0xFFFFFF, 0xFFD700), "Halo"));
            out.accept(row("600.11", anim(600, "divine_flow", false), "§oanimiert§7 Divine flow · gold ⇄ weiß"));
            out.accept(row("600.12", anim(600, "divine_shine", true), "§oanimiert§7 Divine shine bold · Goldglanz"));
            out.accept(row("600.13", anim(600, "frost_pulse", false), "§oanimiert§7 Frost pulse · weiß ⇄ aqua"));
            out.accept(row("600.14", anim(600, "sun_wave", false), "§oanimiert§7 Sun wave · gold/gelb/weiß"));
        }

        if (only == 0 || only == 620) {
            out.accept(Component.literal("§8──── §f620 §7MAX §8────"));
            out.accept(row("620.1", shader(620, ChromaColors.CHROMA, false), "Chroma, animated"));
            out.accept(row("620.2", shader(620, ChromaColors.FADE, false), "Config fade shader, like [TeslaMaps]"));
            out.accept(row("620.3", fade(620, false, 0xFF0000, 0xFFFF00, 0x00FF00, 0x00FFFF, 0x0000FF, 0xFF00FF), "Rainbow, static"));
            out.accept(row("620.4", fade(620, true, 0xFFD700, 0xFFFFFF, 0xFFD700), "Godly bold"));
            out.accept(row("620.5", split(620, 0xFFFFFF, true, 0xFF0000, 0x00FF00, 0x00AAFF), "White brackets, rainbow digits"));
            out.accept(row("620.6", fade(620, true, 0x00FFFF, 0xFF00FF), "Cyber bold"));
            out.accept(row("620.7", fade(620, false, 0xFFFFFF, 0xFFD700, 0xFF5555), "Phoenix"));
            out.accept(row("620.8", underlined(620, 0xFFFFFF), "Pure white, bold underlined"));
            out.accept(row("620.9", fade(620, false, 0x8000FF, 0xFFFFFF, 0x00FFFF), "Astral"));
            out.accept(row("620.10", fade(620, true, 0xFF0080, 0xFFD700), "Sunfire bold"));
            out.accept(row("620.11", anim(620, "max_rainbow", false), "§oanimiert§7 Rainbow flow · eigener Lauf, ruhiger als Chroma"));
            out.accept(row("620.12", anim(620, "max_shine", true), "§oanimiert§7 Max shine bold · Gold mit weißem Band"));
            out.accept(row("620.13", anim(620, "max_pulse", true), "§oanimiert§7 Max pulse bold · weiß ⇄ gold"));
            out.accept(row("620.14", anim(620, "max_wave", false), "§oanimiert§7 Max wave · pink/gold/aqua"));
        }

        if (only == 0) {
            out.accept(Component.literal("§8· §7/tmap debug testcolors <520|560|600|620> for one block only"));
        }
    }

    /** The picked set: Hypixel's 12 up to 480, then ours. */
    public static Component prefix(int level) {
        if (level >= 620) return anim(level, "max_wave", false);
        if (level >= 600) return anim(level, "arcane_shine", false);
        if (level >= 560) return split(level, 0x550000, false, 0xFF7A00, 0xFF7A00);
        if (level >= 520) return anim(level, "sun_wave", false);
        int[] colors = {
                0xAAAAAA, 0xFFFFFF, 0xFFFF55, 0x55FF55, 0x00AA00, 0x55FFFF, 0x00AAAA,
                0x5555FF, 0xFF55FF, 0xAA00AA, 0xFFAA00, 0xFF5555, 0xAA0000,
        };
        return solid(level, colors[Math.max(0, Math.min(colors.length - 1, level / 40))], false);
    }

    private static final int[] STEPS = {
            1, 40, 80, 120, 160, 200, 240, 280, 320, 360, 400, 440, 480, 520, 560, 600, 620,
    };

    public static void printFinal(Consumer<Component> out) {
        AnimFades.previewActive = true;
        out.accept(Component.literal("§8──── §flevel prefixes §71 - 620 §8────"));
        for (int level : STEPS) {
            out.accept(Component.empty()
                    .append(prefix(level))
                    .append(Component.literal(" §b[MVP§c+§b] ffnn"))
                    .append(Component.literal(level >= 520 ? " §8· §7neu" : "")));
        }
        MutableComponent oneLine = Component.empty();
        for (int level : STEPS) {
            oneLine.append(prefix(level)).append(Component.literal(" "));
        }
        out.accept(oneLine);
    }

    private static Component row(String id, Component prefix, String name) {
        return Component.literal("§8" + pad(id) + " ")
                .append(prefix)
                .append(Component.literal(" §b[MVP§c+§b] Teslanator §8· §7" + name));
    }

    private static String pad(String id) {
        StringBuilder sb = new StringBuilder(id);
        while (sb.length() < 6) sb.append(' ');
        return sb.toString();
    }

    private static String text(int level) {
        return "[" + level + "]";
    }

    private static MutableComponent solid(int level, int rgb, boolean bold) {
        return Component.literal(text(level)).setStyle(Style.EMPTY.withColor(rgb).withBold(bold));
    }

    private static MutableComponent shader(int level, TextColor color, boolean bold) {
        return Component.literal(text(level)).setStyle(Style.EMPTY.withColor(color).withBold(bold));
    }

    /** Shader fade, the whole prefix moves as one. */
    private static MutableComponent anim(int level, String fade, boolean bold) {
        TextColor color = AnimFades.color(fade);
        if (color == null) return solid(level, 0xFFFFFF, bold);
        return Component.literal(text(level)).setStyle(Style.EMPTY.withColor(color).withBold(bold));
    }

    private static MutableComponent underlined(int level, int rgb) {
        return Component.literal(text(level))
                .setStyle(Style.EMPTY.withColor(rgb).withBold(true).withUnderlined(true));
    }

    /** Gradient across the whole prefix, brackets included. */
    private static MutableComponent fade(int level, boolean bold, int... stops) {
        return gradient(text(level), bold, stops);
    }

    /** Solid brackets, gradient on the digits. */
    private static MutableComponent split(int level, int bracket, boolean bold, int... stops) {
        Style brackets = Style.EMPTY.withColor(bracket).withBold(bold);
        return Component.literal("[").setStyle(brackets)
                .append(gradient(String.valueOf(level), bold, stops))
                .append(Component.literal("]").setStyle(brackets));
    }

    private static MutableComponent gradient(String s, boolean bold, int... stops) {
        MutableComponent out = Component.empty();
        int last = Math.max(1, s.length() - 1);
        for (int i = 0; i < s.length(); i++) {
            int rgb = sample(stops, i / (float) last);
            out.append(Component.literal(String.valueOf(s.charAt(i)))
                    .setStyle(Style.EMPTY.withColor(rgb).withBold(bold)));
        }
        return out;
    }

    private static int sample(int[] stops, float t) {
        if (stops.length == 1) return stops[0];
        float scaled = t * (stops.length - 1);
        int i = Math.min((int) scaled, stops.length - 2);
        return OkLabColor.interpolate(stops[i], stops[i + 1], scaled - i);
    }
}
