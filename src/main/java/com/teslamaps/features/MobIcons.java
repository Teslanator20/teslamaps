package com.teslamaps.features;

// After SkyOcean's features/textures/MobIcons.kt, without the custom font: glyph -> text
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class MobIcons {

    private static final char[] GLYPHS = {
            '', '', '', '', '', '', '', '', '', '',
            '', '', '', '', '', '', '', '', '', '',
            '', '', '', '', ''};
    private static final String[] NAMES = {
            "Undead", "Skeletal", "Ender", "Arthropod", "Humanoid", "Infernal", "Cubic", "Frozen", "Spooky", "Mythological",
            "Wither", "Subterranean", "Aquatic", "Pest", "Animal", "Magmatic", "Elusive", "Construct", "Arcane", "Shielded",
            "Airborne", "Glacial", "Woodland", "Critter", "Timid"};
    private static final String[] SHORTS = {
            "UND", "SKEL", "END", "ARTH", "HUM", "INF", "CUB", "FRO", "BOO", "MYTH",
            "WITH", "SUB", "AQUA", "PEST", "ANI", "MAGM", "ELUS", "CONST", "ARC", "SHIE",
            "AIR", "GLAC", "WOOD", "CRIT", "TIM"};
    private static final List<String> OPTIONS = Arrays.asList(NAMES);
    public static final String[] STYLES = {"Short", "Full"};

    private record Entry(Component src, Component out) {}

    private static final Int2ObjectOpenHashMap<Entry> cache = new Int2ObjectOpenHashMap<>();
    private static int settingsKey;
    private static Object lastLevel;
    private static int timer;

    private MobIcons() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("MobIcons").section("Mob Visuals").when(() -> cfg().mobIcons)
                .tick(MobIcons::tick)
                .config("Mob Visuals", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Mob Type Icons");
                    b.toggle("Readable Mob Type Icons", () -> c.mobIcons, v -> c.mobIcons = v)
                            .desc("Replaces the mob type symbols in name tags (Undead, Ender, ...) with text.");
                    b.dropdown("  Text", STYLES, () -> c.mobIconsStyle, v -> c.mobIconsStyle = v)
                            .desc("Short = UND, SKEL, ... Full = Undead, Skeletal, ...");
                    b.searchList("  Custom Colors", () -> OPTIONS,
                                    n -> c.mobIconColors.containsKey(n),
                                    n -> { if (c.mobIconColors.remove(n) == null) c.mobIconColors.put(n, "FFFFFF"); })
                            .colors(n -> c.mobIconColors.getOrDefault(n, "FFFFFF"), (n, hex) -> c.mobIconColors.put(n, hex))
                            .desc("Ticked types get their own color, the rest keep Hypixel's.");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        int key = c.mobIconsStyle.hashCode() * 31 + c.mobIconColors.hashCode();
        if (key != settingsKey || mc.level != lastLevel || (++timer % 200 == 0 && cache.size() > 1000)) {
            settingsKey = key;
            lastLevel = mc.level;
            cache.clear();
        }
    }

    /** From MobIconsMixin, render thread. */
    public static Component apply(Entity e, Component name) {
        if (name == null) return null;
        TeslaMapsConfig c = cfg();
        if (!c.mobIcons || !c.section("Mob Visuals")) return name;
        Component src = e.getCustomName();
        if (src == null) return name;
        Entry hit = cache.get(e.getId());
        if (hit != null && hit.src == src) return hit.out == null ? name : hit.out;
        Component out = rewrite(name, c);
        cache.put(e.getId(), new Entry(src, out));
        return out == null ? name : out;
    }

    private static int glyphIndex(char ch) {
        if (ch != '' && (ch < '' || ch > '')) return -1;
        for (int i = 0; i < GLYPHS.length; i++) if (GLYPHS[i] == ch) return i;
        return -1;
    }

    private static Component rewrite(Component name, TeslaMapsConfig c) {
        boolean[] found = {false};
        boolean full = "Full".equals(c.mobIconsStyle);
        MutableComponent root = Component.empty();
        name.visit((style, text) -> {
            int start = 0;
            String codes = "";
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '§' && i + 1 < text.length()) {
                    char f = Character.toLowerCase(text.charAt(i + 1));
                    boolean color = (f >= '0' && f <= '9') || (f >= 'a' && f <= 'f') || f == 'r';
                    codes = color ? "§" + f : codes + "§" + f;
                    i++;
                    continue;
                }
                int g = glyphIndex(ch);
                if (g < 0) continue;
                found[0] = true;
                if (i > start) root.append(Component.literal(text.substring(start, i)).setStyle(style));
                String label = full ? NAMES[g] : SHORTS[g];
                String hex = c.mobIconColors.get(NAMES[g]);
                Style ls = style.withFont(FontDescription.DEFAULT);
                if (hex != null) {
                    ls = ls.withColor(TextColor.fromRgb(TeslaMapsConfig.parseColor(hex) & 0xFFFFFF));
                    root.append(Component.literal(label).setStyle(ls));
                } else {
                    root.append(Component.literal(codes + label).setStyle(ls));
                }
                start = i + 1;
                if (start < text.length()) {
                    String rest = text.substring(start);
                    text = codes + rest;
                    i = codes.length() - 1;
                    start = 0;
                }
            }
            if (start < text.length()) root.append(Component.literal(text.substring(start)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return found[0] ? root : null;
    }
}
