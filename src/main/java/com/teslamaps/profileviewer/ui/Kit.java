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
        // both lines are clipped to the box, they used to run straight out of it
        int inner = Math.max(10, width - 10);
        Ui content = Ui.col(1,
                new Ui.Label(clip(key, (int) (inner / Ui.SMALL)), NeuStyle.TEXT_GRAY, Ui.SMALL),
                new Ui.Label(clip(value, inner), valueColor));
        return new Ui.Frame(width, content.height() + 10,
                Ui.row(0, Ui.V.CENTER, new Ui.Spacer(5, 0), content),
                NeuStyle::slimBox, Ui.H.START, Ui.V.CENTER);
    }

    public record TileData(String key, String value, int color) {}

    /** Tiles fill the width; if their text does not fit they wrap into a second row instead of being cut. */
    public static Ui tileRow(int width, List<TileData> tiles) {
        int gap = 6;
        var font = Minecraft.getInstance().font;

        int need = 0;
        for (TileData tile : tiles) {
            need = Math.max(need, Math.max((int) (font.width(tile.key()) * Ui.SMALL), font.width(tile.value())) + 12);
        }

        int perRow = Math.max(1, Math.min(tiles.size(), (width + gap) / (need + gap)));
        int rows = (tiles.size() + perRow - 1) / perRow;
        perRow = (tiles.size() + rows - 1) / rows;

        List<Ui> built = new ArrayList<>(rows);
        for (int from = 0; from < tiles.size(); from += perRow) {
            List<TileData> slice = tiles.subList(from, Math.min(from + perRow, tiles.size()));
            int each = (width - gap * (slice.size() - 1)) / slice.size();
            List<Ui> children = new ArrayList<>(slice.size());
            for (TileData tile : slice) children.add(tile(each, tile.key(), tile.value(), tile.color()));
            built.add(Ui.row(gap, Ui.V.TOP, children));
        }

        return built.size() == 1 ? built.getFirst() : Ui.col(gap, built);
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

    /** 2.500 - thousands separated with dots. */
    public static String dots(long value) {
        return String.format(java.util.Locale.GERMAN, "%,d", value);
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
