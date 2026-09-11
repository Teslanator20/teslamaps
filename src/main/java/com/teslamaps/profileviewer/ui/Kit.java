package com.teslamaps.profileviewer.ui;

import com.teslamaps.profileviewer.screen.NeuStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared building blocks for the stat pages, ported from schrumbo's PageKit. */
public class Kit {

    private static final Map<String, ItemStack> icons = new HashMap<>();

    public static ItemStack icon(String name) {
        return icons.computeIfAbsent(name, key -> {
            Identifier id = Identifier.tryParse(key.contains(":") ? key : "minecraft:" + key);
            if (id == null) return ItemStack.EMPTY;
            return BuiltInRegistries.ITEM.get(id).map(holder -> new ItemStack(holder.value())).orElse(ItemStack.EMPTY);
        });
    }

    public static String clip(String text, int maxWidth) {
        var font = Minecraft.getInstance().font;
        if (font.width(text) <= maxWidth) return text;
        String cut = text;
        while (!cut.isEmpty() && font.width(cut + "...") > maxWidth) cut = cut.substring(0, cut.length() - 1);
        return cut + "...";
    }

    /** A section caption above its content. */
    public static Ui section(String title, Ui content) {
        return Ui.col(3, new Ui.Label(title, NeuStyle.TEXT_GREEN), content);
    }

    /** Key above value inside a slim box. */
    public static Ui tile(int width, String key, String value, int valueColor) {
        Ui content = Ui.col(1,
                new Ui.Label(key, NeuStyle.TEXT_GRAY, Ui.SMALL),
                new Ui.Label(value, valueColor));
        return new Ui.Frame(width, content.height() + 10,
                Ui.row(0, Ui.V.CENTER, new Ui.Spacer(5, 0), content),
                NeuStyle::slimBox, Ui.H.START, Ui.V.CENTER);
    }

    public record TileData(String key, String value, int color) {}

    public static Ui tileRow(int width, List<TileData> tiles) {
        int gap = 6;
        int each = (width - gap * (tiles.size() - 1)) / tiles.size();
        List<Ui> children = new ArrayList<>(tiles.size());
        for (TileData tile : tiles) children.add(tile(each, tile.key(), tile.value(), tile.color()));
        return Ui.row(gap, Ui.V.TOP, children);
    }

    /** Lays rows into balanced columns. */
    public static Ui grid(List<Ui> rows, int columns, int columnGap, int rowGap) {
        if (rows.isEmpty()) return new Ui.Label("No data", NeuStyle.TEXT_GRAY);
        int perColumn = (rows.size() + columns - 1) / columns;
        List<Ui> built = new ArrayList<>(columns);
        for (int c = 0; c < columns; c++) {
            int from = Math.min(c * perColumn, rows.size());
            int to = Math.min(from + perColumn, rows.size());
            if (from >= to) continue;
            built.add(Ui.col(rowGap, new ArrayList<>(rows.subList(from, to))));
        }
        return Ui.row(columnGap, Ui.V.TOP, built);
    }

    public static int cellWidth(int width, int columns, int columnGap) {
        return (width - columnGap * (columns - 1)) / columns;
    }

    /** The vanilla xp bar NEU uses everywhere. */
    public static Ui bar(int width, double progress) {
        return new Ui.Bar(width, 5, progress, NeuStyle::bar);
    }

    public static Ui bar(int width, double progress, int color) {
        return new Ui.Bar(width, 5, progress, (ctx, x, y, w, p) -> NeuStyle.bar(ctx, x, y, w, p, color));
    }

    /** 1,234 / 12.3M / 4.5B, the short form the hypixel menus use. */
    public static String compact(long value) {
        if (value < 1_000) return String.valueOf(value);
        if (value < 1_000_000) return trim(value / 1_000.0) + "K";
        if (value < 1_000_000_000) return trim(value / 1_000_000.0) + "M";
        return trim(value / 1_000_000_000.0) + "B";
    }

    private static String trim(double value) {
        String text = String.format(java.util.Locale.ROOT, "%.2f", value);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text;
    }

    /** wild_rose -> Wild Rose */
    public static String prettify(String key) {
        String[] words = key.replace('_', ' ').toLowerCase().split(" ");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
