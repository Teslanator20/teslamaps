package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.screen.MapConfigScreen;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Adds rows to one config-screen category. Every method appends its entry and returns it,
 * so {@code b.toggle(...).cheat()} works.
 */
public final class ConfigBuilder {

    private static final class Target {
        List<MapConfigScreen.SettingsEntry> list;
    }

    private final MapConfigScreen s;
    private final int x, w;
    private final Target target;

    public ConfigBuilder(MapConfigScreen screen, int x, int width) {
        this(screen, x, width, new Target());
    }

    private ConfigBuilder(MapConfigScreen screen, int x, int width, Target target) {
        this.s = screen;
        this.x = x;
        this.w = width;
        this.target = target;
    }

    /** Where the following rows go. */
    public void into(List<MapConfigScreen.SettingsEntry> list) {
        target.list = list;
    }

    /** Same category, narrower controls. */
    public ConfigBuilder withWidth(int width) {
        return new ConfigBuilder(s, x, width, target);
    }

    public MapConfigScreen screen() { return s; }
    public int x() { return x; }
    public int width() { return w; }
    public TeslaMapsConfig config() { return TeslaMapsConfig.get(); }

    public <T extends MapConfigScreen.SettingsEntry> T add(T e) {
        target.list.add(e);
        return e;
    }

    public MapConfigScreen.LabelEntry label(String text) {
        return add(s.new LabelEntry(x, text));
    }

    public MapConfigScreen.InfoEntry info(String text) {
        return add(s.new InfoEntry(x, text));
    }

    public MapConfigScreen.ToggleEntry toggle(String label, Supplier<Boolean> get, Consumer<Boolean> set) {
        return add(s.new ToggleEntry(x, w, label, get, set));
    }

    public MapConfigScreen.SliderEntry slider(String label, float min, float max, Supplier<Float> get, Consumer<Float> set) {
        return add(s.new SliderEntry(x, w, label, min, max, get, set));
    }

    public MapConfigScreen.SliderEntry slider(String label, float min, float max, Supplier<Float> get, Consumer<Float> set, int decimals) {
        return add(s.new SliderEntry(x, w, label, min, max, get, set, decimals));
    }

    public MapConfigScreen.ColorEntry color(String label, Supplier<String> get, Consumer<String> set) {
        return add(s.new ColorEntry(x, w, label, get, set));
    }

    public MapConfigScreen.ButtonEntry button(String label, Runnable action) {
        return add(s.new ButtonEntry(x, w, label, action));
    }

    public MapConfigScreen.ButtonRowEntry buttonRow(String[] labels, Runnable[] actions) {
        return add(s.new ButtonRowEntry(x, w, labels, actions));
    }

    public MapConfigScreen.KeybindEntry keybind(String label, IntSupplier get, IntConsumer set) {
        return add(s.new KeybindEntry(x, w, label, get, set));
    }

    public MapConfigScreen.DropdownEntry dropdown(String label, String[] options, Supplier<String> get, Consumer<String> set) {
        return add(s.new DropdownEntry(x, w, label, options, get, set));
    }

    public MapConfigScreen.SoundDropdownEntry soundDropdown(String label, String[] options, Supplier<String> get, Consumer<String> set) {
        return add(s.new SoundDropdownEntry(x, w, label, options, get, set));
    }

    /** Searchable multi-select list; add icons/colours/actions via the returned entry. */
    public MapConfigScreen.SearchListEntry searchList(String label, Supplier<List<String>> options,
                                                      java.util.function.Predicate<String> selected, Consumer<String> toggle) {
        return add(s.new SearchListEntry(x, w, label, options, selected, toggle));
    }

    /** The Custom ESP names as a searchable list with nearby mobs, icons and per-entry colours. */
    public MapConfigScreen.SearchListEntry customEspList(String label) {
        TeslaMapsConfig c = config();
        return searchList(label, () -> NearbyMobs.options(c.customESPMobs),
                id -> c.customESPMobs.stream().anyMatch(s -> s.equalsIgnoreCase(id)),
                id -> {
                    NearbyMobs.remember(id);
                    if (!c.customESPMobs.removeIf(s -> s.equalsIgnoreCase(id))) c.customESPMobs.add(id);
                })
                .icons(NearbyMobs::icon)
                .colors(id -> c.customESPColors.getOrDefault(id.toLowerCase(), c.colorESPCustom),
                        (id, hex) -> c.customESPColors.put(id.toLowerCase(), hex))
                .pick(NearbyMobs::looked)
                .clearAll(() -> {
                    for (String m : c.customESPMobs) NearbyMobs.remember(m);
                    c.customESPMobs.clear();
                    c.customESPTracers.clear();
                    c.customESPColors.clear();
                });
    }

    /** Mob-name list with nearby mobs as suggestions. */
    public MapConfigScreen.SearchListEntry mobList(String label, Supplier<List<String>> list) {
        return searchList(label, () -> NearbyMobs.options(list.get()),
                id -> list.get().stream().anyMatch(s -> s.equalsIgnoreCase(id)),
                id -> {
                    NearbyMobs.remember(id);
                    if (!list.get().removeIf(s -> s.equalsIgnoreCase(id))) list.get().add(id);
                })
                .icons(NearbyMobs::icon);
    }

    /** Drag-to-reorder list of a HUD's lines ({@link com.teslamaps.utils.LineOrder}). */
    public MapConfigScreen.LineOrderEntry lineOrder(String label, String id) {
        return add(s.new LineOrderEntry(x, w, label, id));
    }

    public MapConfigScreen.ClassFilterEntry classFilter(String label, TeslaMapsConfig.ClassFilter filter) {
        return add(s.new ClassFilterEntry(x, w, label, filter));
    }

    public MapConfigScreen.ProfileTilesEntry profileTiles() {
        return add(s.new ProfileTilesEntry(x, w));
    }

    private static final String[] PRICE_SOURCES = {"Bazaar Sell", "Bazaar Buy", "NPC", "Lowest BIN"};
    private static final int[] PRICE_SOURCE_IDS = {
            com.teslamaps.features.croesus.PriceManager.SOURCE_BAZAAR_SELL,
            com.teslamaps.features.croesus.PriceManager.SOURCE_BAZAAR_BUY,
            com.teslamaps.features.croesus.PriceManager.SOURCE_NPC,
            com.teslamaps.features.croesus.PriceManager.SOURCE_LOWEST_BIN};

    /** Dropdown over {@code PriceManager.SOURCE_*}. */
    public MapConfigScreen.DropdownEntry priceSource(String label, IntSupplier get, IntConsumer set) {
        return dropdown(label, PRICE_SOURCES, () -> {
            int v = get.getAsInt();
            for (int i = 0; i < PRICE_SOURCE_IDS.length; i++) if (PRICE_SOURCE_IDS[i] == v) return PRICE_SOURCES[i];
            return PRICE_SOURCES[0];
        }, v -> {
            for (int i = 0; i < PRICE_SOURCES.length; i++) if (PRICE_SOURCES[i].equals(v)) set.accept(PRICE_SOURCE_IDS[i]);
        });
    }

    /** Reset Session / Reset Total / Session-Total switch for one tracker. */
    public MapConfigScreen.ButtonRowEntry trackerButtons(com.teslamaps.features.tracker.Tracker t) {
        return buttonRow(new String[]{"Reset Session", "Reset Total", "Session / Total"},
                new Runnable[]{t::resetSession, t::resetTotal, t::toggleView});
    }

    /** Color, mode, line width, tracer and through-walls rows for one {@link com.teslamaps.render.EspStyle}. */
    public void espStyle(String name, Supplier<com.teslamaps.render.EspStyle> style) {
        Supplier<com.teslamaps.render.EspStyle> s = () -> com.teslamaps.render.EspStyle.of(style.get());
        color("  " + name + " Color", () -> s.get().color, v -> s.get().color = v);
        dropdown("  " + name + " Style", com.teslamaps.render.EspStyle.MODES, () -> s.get().mode, v -> s.get().mode = v);
        slider("  " + name + " Line Width", 1f, 8f, () -> s.get().lineWidth, v -> s.get().lineWidth = v);
        toggle("  " + name + " Tracer", () -> s.get().tracer, v -> s.get().tracer = v);
        toggle("  " + name + " Through Walls", () -> s.get().throughWalls, v -> s.get().throughWalls = v);
    }

    /** Nobody / Party / All dropdown for an {@link com.teslamaps.features.Announce} channel. */
    public MapConfigScreen.DropdownEntry channel(String label, Supplier<String> get, Consumer<String> set) {
        return dropdown(label, com.teslamaps.features.Announce.CHANNELS, get, set);
    }
}
