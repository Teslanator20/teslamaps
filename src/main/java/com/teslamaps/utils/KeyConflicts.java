package com.teslamaps.utils;

// Idea from Skyblocker's ShortcutsConfigListWidget / KeybindWidget conflict check
import com.mojang.blaze3d.platform.InputConstants;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Who else uses a key: TeslaMaps binds, hotkeys, and every registered KeyMapping (vanilla + other mods). */
public final class KeyConflicts {

    private static List<Field> fields;

    private KeyConflicts() {}

    public static void add(Map<Integer, List<String>> out, int key, String label) {
        if (key == -1) return;
        out.computeIfAbsent(key, k -> new ArrayList<>()).add(label);
    }

    /** Plain hotkeys (no modifier, no held key), except {@code skip}. */
    public static void addHotkeys(Map<Integer, List<String>> out, TeslaMapsConfig.Keybind skip) {
        for (TeslaMapsConfig.Keybind kb : TeslaMapsConfig.get().keybinds) {
            if (kb == skip || !kb.enabled || kb.modifiers != 0 || kb.key2 != -1) continue;
            String what = kb.message == null || kb.message.isBlank() ? "(empty)" : kb.message;
            add(out, kb.key, "Hotkey: " + what);
        }
    }

    public static void addMappings(Map<Integer, List<String>> out) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        for (KeyMapping km : mc.options.keyMappings) {
            if (km.isUnbound()) continue;
            InputConstants.Key k;
            try {
                k = InputConstants.getKey(km.saveString());
            } catch (Exception e) {
                continue;
            }
            int key = switch (k.getType()) {
                case KEYBOARD -> k.getValue();
                case MOUSE -> KeyNames.forMouse(k.getValue());
                default -> -1;
            };
            add(out, key, "Controls: " + Component.translatable(km.getName()).getString());
        }
    }

    /** Every int config field named *Key / *Keybind, labelled from the field name. */
    public static void addConfigFields(Map<Integer, List<String>> out) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        for (Field f : fields()) {
            try {
                add(out, f.getInt(c), "TeslaMaps: " + words(f.getName()));
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    public static Map<Integer, List<String>> all(TeslaMapsConfig.Keybind skipHotkey) {
        Map<Integer, List<String>> out = new HashMap<>();
        addConfigFields(out);
        addHotkeys(out, skipHotkey);
        addMappings(out);
        return out;
    }

    private static List<Field> fields() {
        if (fields == null) {
            List<Field> l = new ArrayList<>();
            for (Field f : TeslaMapsConfig.class.getFields()) {
                if (f.getType() != int.class || Modifier.isStatic(f.getModifiers())) continue;
                String n = f.getName();
                if (n.endsWith("Key") || n.endsWith("Keybind")) l.add(f);
            }
            fields = l;
        }
        return fields;
    }

    private static String words(String camel) {
        StringBuilder sb = new StringBuilder(camel.length() + 8);
        for (int i = 0; i < camel.length(); i++) {
            char ch = camel.charAt(i);
            if (i == 0) sb.append(Character.toUpperCase(ch));
            else {
                if (Character.isUpperCase(ch)) sb.append(' ');
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
