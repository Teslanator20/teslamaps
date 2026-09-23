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

    public MapConfigScreen.ClassFilterEntry classFilter(String label, TeslaMapsConfig.ClassFilter filter) {
        return add(s.new ClassFilterEntry(x, w, label, filter));
    }

    public MapConfigScreen.ProfileTilesEntry profileTiles() {
        return add(s.new ProfileTilesEntry(x, w));
    }
}
