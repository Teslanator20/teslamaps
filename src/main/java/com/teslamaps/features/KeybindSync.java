package com.teslamaps.features;

// Ported from FloydAddons' KeybindSync: every key setting mirrored as a vanilla KeyMapping
import com.mojang.blaze3d.platform.InputConstants;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.KeyNames;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * The mod still polls its raw key ints. The KeyMappings only exist so the Controls screen lists
 * them and shows conflicts; they never trigger anything themselves.
 */
public final class KeybindSync {

    private KeybindSync() {}

    private static final class Bind {
        final Field field;
        final KeyMapping mapping;
        int lastCfg = Integer.MIN_VALUE, lastMap = Integer.MIN_VALUE;

        Bind(Field field, KeyMapping mapping) {
            this.field = field;
            this.mapping = mapping;
        }
    }

    private static final List<Bind> BINDS = new ArrayList<>();

    public static void register() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Feature.of("KeybindSync").when(() -> TeslaMapsConfig.get().keybindsInControls && !BINDS.isEmpty())
                .tickAlways(KeybindSync::tick)
                .config("Interface", b -> b.toggle("Keybinds In Controls Menu", () -> c.keybindsInControls, v -> c.keybindsInControls = v)
                        .desc("Lists every TeslaMaps key setting under Options > Controls > TeslaMaps, so conflicts with vanilla and other mods show red. Changes sync both ways. Needs a restart.")
                        .tags("keybind", "controls", "conflict", "key mapping"));
        if (!c.keybindsInControls) return;
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(TeslaMaps.MOD_ID, "keybinds"));
        for (Field f : TeslaMapsConfig.class.getFields()) {
            if (f.getType() != int.class || Modifier.isStatic(f.getModifiers())) continue;
            String n = f.getName();
            if (!n.endsWith("Key") && !n.endsWith("Keybind")) continue;
            try {
                KeyMapping m = KeyMappingHelper.registerKeyMapping(
                        new KeyMapping(label(n), InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), cat));
                BINDS.add(new Bind(f, m));
            } catch (RuntimeException e) {
                TeslaMaps.LOGGER.warn("KeybindSync: {} skipped", n, e);
            }
        }
    }

    private static String label(String field) {
        String base = field.endsWith("Keybind") ? field.substring(0, field.length() - 7) : field.substring(0, field.length() - 3);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < base.length(); i++) {
            char ch = base.charAt(i);
            if (i == 0) sb.append(Character.toUpperCase(ch));
            else if (Character.isUpperCase(ch) || Character.isDigit(ch) && !Character.isDigit(base.charAt(i - 1))) sb.append(' ').append(ch);
            else sb.append(ch);
        }
        return "TeslaMaps: " + sb;
    }

    private static int fromKey(InputConstants.Key k) {
        if (k == null || k.equals(InputConstants.UNKNOWN)) return -1;
        return k.getType() == InputConstants.Type.MOUSE ? KeyNames.forMouse(k.getValue()) : k.getValue();
    }

    private static InputConstants.Key toKey(int v) {
        if (v == -1 || v < 0 && !KeyNames.isMouse(v)) return InputConstants.UNKNOWN;
        return KeyNames.isMouse(v) ? InputConstants.Type.MOUSE.getOrCreate(KeyNames.mouseButton(v))
                : InputConstants.Type.KEYSYM.getOrCreate(v);
    }

    private static int tickCounter;

    private static void tick() {
        if (++tickCounter % 10 != 0) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        boolean remap = false, save = false;
        for (Bind b : BINDS) {
            int cfg;
            try {
                cfg = b.field.getInt(c);
            } catch (IllegalAccessException e) {
                continue;
            }
            int map = fromKey(KeyMappingHelper.getBoundKeyOf(b.mapping));
            if (b.lastCfg == Integer.MIN_VALUE || cfg != b.lastCfg) {
                // config wins on start and whenever it changed
                if (map != cfg) {
                    b.mapping.setKey(toKey(cfg));
                    remap = true;
                }
                map = cfg;
            } else if (map != b.lastMap) {
                try {
                    b.field.setInt(c, map);
                    save = true;
                } catch (IllegalAccessException ignored) {
                }
                cfg = map;
            }
            b.lastCfg = cfg;
            b.lastMap = map;
        }
        if (remap) KeyMapping.resetMapping();
        if (save) TeslaMapsConfig.save();
    }
}
