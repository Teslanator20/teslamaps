package com.teslamaps.adm.leap.pov;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;

// Ported from ADM's pov/EntityCullingBridge.kt
/** EntityCulling off around a preview's extract (its verdicts were raytraced from your camera). Reflection, optional. */
final class EntityCullingBridge {

    private EntityCullingBridge() {}

    private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("entityculling");
    private static Field enabledField;
    private static Class<?> modBase;
    private static boolean looked;

    private static void look() {
        if (looked) return;
        looked = true;
        if (!PRESENT) return;
        try { enabledField = Class.forName("dev.tr7zw.entityculling.versionless.EntityCullingVersionlessBase").getField("enabled"); } catch (Throwable ignored) { }
        try { modBase = Class.forName("dev.tr7zw.entityculling.EntityCullingModBase"); } catch (Throwable ignored) { }
    }

    static Boolean disable() {
        look();
        if (enabledField == null) return null;
        try {
            boolean was = enabledField.getBoolean(null);
            enabledField.setBoolean(null, false);
            return was;
        } catch (Throwable t) {
            return null;
        }
    }

    static void restore(Boolean previous) {
        if (enabledField == null || previous == null) return;
        try { enabledField.setBoolean(null, previous); } catch (Throwable ignored) { }
    }

    static void requestRecull() {
        look();
        if (modBase == null) return;
        try {
            Object instance = modBase.getField("instance").get(null);
            if (instance == null) return;
            Object task = modBase.getField("cullTask").get(instance);
            if (task == null) return;
            task.getClass().getField("requestCull").setBoolean(task, true);
        } catch (Throwable ignored) { }
    }
}
