package com.teslamaps.adm.sim.settings;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.screen.config.ConfigBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/** The ADS module's settings (Odin settings in ADM), stored as strings in TeslaMapsConfig.admSimSettings. */
public final class SimSettings {
    private SimSettings() {}

    private static final List<SimSetting> ALL = new ArrayList<>();

    public static synchronized List<SimSetting> all() { return new ArrayList<>(ALL); }

    private static synchronized <T extends SimSetting> T add(T s) {
        ALL.removeIf(o -> o.name.equals(s.name));
        ALL.add(s);
        return s;
    }

    public static BoolSetting bool(String name, boolean def, String desc) { return add(new BoolSetting(name, def, desc)); }

    public static NumSetting num(String name, double def, double min, double max, double step, String unit, String desc) {
        return add(new NumSetting(name, def, min, max, step, unit, desc));
    }

    public static SelSetting sel(String name, String def, List<String> options, String desc) {
        return add(new SelSetting(name, options.indexOf(def), options, desc));
    }

    public static SelSetting sel(String name, Enum<?> def, String desc) {
        List<String> opts = new ArrayList<>();
        for (Enum<?> e : def.getDeclaringClass().getEnumConstants()) opts.add(e.name());
        return add(new SelSetting(name, def.ordinal(), opts, desc));
    }

    public static KeySetting key(String name, int defGlfwKey, String desc) { return add(new KeySetting(name, defGlfwKey, desc)); }

    public static StrSetting str(String name, String def, int maxLength, String desc) { return add(new StrSetting(name, def, maxLength, desc)); }

    static String raw(String name) {
        Map<String, String> m = TeslaMapsConfig.get().admSimSettings;
        return m == null ? null : m.get(name);
    }

    static void store(String name, String v) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.admSimSettings == null) c.admSimSettings = new java.util.LinkedHashMap<>();
        if (v.equals(c.admSimSettings.get(name))) return;
        c.admSimSettings.put(name, v);
        TeslaMapsConfig.save();
    }

    /** Key settings fire on press, outside screens (Odin's KeybindSetting.onPress). */
    public static void tickKeys() {
        Minecraft mc = Minecraft.getInstance();
        long h = mc.getWindow().handle();
        for (SimSetting s : all()) {
            if (!(s instanceof KeySetting k)) continue;
            int key = k.key();
            boolean down = mc.gui.screen() == null && com.teslamaps.utils.KeyNames.isDown(key);
            if (down && !k.down && k.onPress != null) {
                try { k.onPress.run(); } catch (Throwable t) { com.teslamaps.adm.sim.Ec.LOG.error("[ads] key {} failed", k.name, t); }
            }
            k.down = down;
        }
    }

    /** The config rows, one per setting, in registration order. */
    public static void rows(ConfigBuilder b) {
        for (SimSetting s : all()) {
            switch (s) {
                case BoolSetting x -> b.toggle(x.name, x::get, x::set).desc(x.desc);
                case NumSetting x -> b.slider(x.name + (x.unit.isEmpty() ? "" : " (" + x.unit.trim() + ")"), (float) x.min, (float) x.max,
                        x::getFloat, v -> x.set(v), x.integral() ? 0 : decimals(x.step)).desc(x.desc);
                case SelSetting x -> b.dropdown(x.name, x.options.toArray(new String[0]), x::get, x::set).desc(x.desc);
                case KeySetting x -> b.keybind(x.name, x::key, x::setKey).desc(x.desc);
                case StrSetting x -> b.info(x.name + ": " + x.get());
                default -> {}
            }
        }
    }

    private static int decimals(double step) {
        if (step >= 1) return 0;
        if (step >= 0.1) return 1;
        return 2;
    }
}
