package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One key per boolean setting that flips it (Odin's module keybinds), optional chat line. */
public final class FeatureToggleKeys {
    private FeatureToggleKeys() {}

    private static final Set<Integer> held = new HashSet<>();
    private static final Map<String, Field> FIELDS = new HashMap<>();
    private static List<String> names;
    private static long screenAt;
    private static String picked;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("FeatureToggleKeys")
                .when(() -> cfg().featureToggleKeys && !cfg().featureToggleBinds.isEmpty())
                .tickAlways(FeatureToggleKeys::tick);
    }

    /** Rows under Interface > Config Hotkey. */
    public static void config(com.teslamaps.screen.config.ConfigBuilder b, TeslaMapsConfig c) {
        b.toggle("Feature Toggle Keys", () -> c.featureToggleKeys, v -> c.featureToggleKeys = v)
                .desc("Bind a key to any on/off setting. Pressing it in game flips that setting.")
                .tags("keybind", "hotkey", "toggle", "module");
        b.toggle("  Chat Message", () -> c.featureToggleChat, v -> c.featureToggleChat = v)
                .desc("\"<Setting> enabled/disabled\" in chat after each press");
        b.searchList("  Setting", FeatureToggleKeys::names,
                        id -> id.equals(picked) || c.featureToggleBinds.containsKey(id),
                        id -> picked = id)
                .names(id -> pretty(id) + (c.featureToggleBinds.containsKey(id)
                        ? " §7[" + com.teslamaps.utils.KeyNames.display(c.featureToggleBinds.get(id)) + "]" : "")
                        + (id.equals(picked) ? " §b<" : ""))
                .desc("Pick a setting (marked <), then bind its key below. Bound ones show their key.");
        b.keybind("  Key For Picked Setting",
                () -> picked == null ? -1 : c.featureToggleBinds.getOrDefault(picked, -1),
                k -> {
                    if (picked == null) return;
                    if (k == -1) c.featureToggleBinds.remove(picked);
                    else c.featureToggleBinds.put(picked, k);
                });
        b.button("  Clear All Toggle Keys", () -> c.featureToggleBinds.clear());
    }

    private static List<String> names() {
        if (names == null) {
            List<String> out = new ArrayList<>();
            for (Field f : TeslaMapsConfig.class.getFields()) {
                if (f.getType() != boolean.class || Modifier.isStatic(f.getModifiers())) continue;
                out.add(f.getName());
                FIELDS.put(f.getName(), f);
            }
            out.sort(String::compareToIgnoreCase);
            names = out;
        }
        return names;
    }

    static String pretty(String field) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < field.length(); i++) {
            char ch = field.charAt(i);
            if (i == 0) sb.append(Character.toUpperCase(ch));
            else if (Character.isUpperCase(ch) && !Character.isUpperCase(field.charAt(i - 1))) sb.append(' ').append(ch);
            else sb.append(ch);
        }
        return sb.toString();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;
        if (mc.gui.screen() != null) {
            screenAt = System.currentTimeMillis();
            held.clear();
            return;
        }
        boolean fire = System.currentTimeMillis() - screenAt >= cfg().hotkeyChatDelay;
        for (Map.Entry<String, Integer> e : cfg().featureToggleBinds.entrySet()) {
            int key = e.getValue();
            if (key == -1) continue;
            boolean down = com.teslamaps.utils.KeyNames.isDown(key);
            if (down && held.add(key)) {
                if (fire) flip(mc, e.getKey());
            } else if (!down) {
                held.remove(key);
            }
        }
    }

    private static void flip(Minecraft mc, String name) {
        names();
        Field f = FIELDS.get(name);
        if (f == null) return;
        try {
            boolean v = !f.getBoolean(cfg());
            f.setBoolean(cfg(), v);
            TeslaMapsConfig.save();
            if (cfg().featureToggleChat && mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §f" + pretty(name) + (v ? " §aenabled" : " §cdisabled")));
            }
        } catch (IllegalAccessException ignored) {
        }
    }
}
