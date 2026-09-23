package com.teslamaps.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.teslamaps.TeslaMaps;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Versioned fixes on the raw config json, before Gson maps it onto the POJO. Ported from
 * SkyHanni's ConfigUpdaterMigrator: the file stores {@code configVersion}; every step above
 * it runs once, in order. A renamed field gets a {@link #move}, so the user keeps the value.
 * <p>
 * Adding a step: bump {@link #CONFIG_VERSION}, add one line in {@link #steps()} with that number.
 * Old one-shot flags in {@code normalize()} (migratePestEsp, deployableTypesRepaired,
 * kuudraShortcutsAdded) stay as they are.
 */
public final class ConfigMigrator {

    public static final int CONFIG_VERSION = 1;
    static final String KEY = "configVersion";

    private record Step(int version, String what, UnaryOperator<JsonObject> fix) {}

    private static List<Step> steps;

    private ConfigMigrator() {}

    private static List<Step> steps() {
        if (steps != null) return steps;
        List<Step> s = new ArrayList<>();
        // v1: version counter introduced, nothing to fix
        // e.g. s.add(move(2, "oldFieldName", "newFieldName"));
        for (Step step : s) {
            if (step.version() < 1 || step.version() > CONFIG_VERSION) {
                String msg = "ConfigMigrator: step '" + step.what() + "' has version " + step.version()
                        + ", CONFIG_VERSION is " + CONFIG_VERSION;
                if (FabricLoader.getInstance().isDevelopmentEnvironment()) throw new IllegalStateException(msg);
                TeslaMaps.LOGGER.error(msg);
            }
        }
        s.sort((a, b) -> Integer.compare(a.version(), b.version()));
        steps = s;
        return s;
    }

    /** Renames a field; an existing value under the new name wins. */
    static Step move(int version, String from, String to) {
        return new Step(version, "move " + from + " -> " + to, json -> {
            JsonElement v = json.remove(from);
            if (v != null && !json.has(to)) json.add(to, v);
            return json;
        });
    }

    /** Rewrites a field's value, only when it is present. */
    static Step transform(int version, String field, UnaryOperator<JsonElement> f) {
        return new Step(version, "transform " + field, json -> {
            JsonElement v = json.get(field);
            if (v != null) {
                JsonElement out = f.apply(v);
                if (out == null) json.remove(field);
                else json.add(field, out);
            }
            return json;
        });
    }

    static Step remove(int version, String field) {
        return new Step(version, "remove " + field, json -> {
            json.remove(field);
            return json;
        });
    }

    /**
     * Brings one config/profile json up to {@link #CONFIG_VERSION}. A file newer than this build is
     * left alone. When a step fails the original json comes back unchanged. Before any step runs,
     * {@code source} (may be null) is copied to {@code <name>.bak-v<old version>}.
     */
    public static JsonObject migrate(JsonObject json, String label, Path source) {
        int from = 0;
        try {
            if (json.has(KEY)) from = json.get(KEY).getAsInt();
        } catch (Exception ignored) {}
        if (from > CONFIG_VERSION) {
            TeslaMaps.LOGGER.warn("{} has config version {}, this build knows {} - not migrating", label, from, CONFIG_VERSION);
            return json;
        }
        if (from == CONFIG_VERSION) return json;

        List<Step> todo = new ArrayList<>();
        for (Step s : steps()) if (s.version() > from) todo.add(s);

        JsonObject work = json.deepCopy();
        try {
            if (!todo.isEmpty() && source != null && Files.exists(source)) {
                Files.copy(source, source.resolveSibling(source.getFileName() + ".bak-v" + from),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            for (Step s : todo) {
                work = s.fix().apply(work);
                TeslaMaps.LOGGER.info("{}: config fix v{} {}", label, s.version(), s.what());
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("{}: config migration from v{} failed, loading it unmigrated", label, from, e);
            return json;
        }
        work.add(KEY, new JsonPrimitive(CONFIG_VERSION));
        return work;
    }
}
