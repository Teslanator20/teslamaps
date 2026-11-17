package com.teslamaps.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.teslamaps.TeslaMaps;
import com.teslamaps.utils.KeyNames;
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

    public static final int CONFIG_VERSION = 4;
    static final String KEY = "configVersion";

    private record Step(int version, String what, UnaryOperator<JsonObject> fix) {}

    private static List<Step> steps;

    private ConfigMigrator() {}

    private static List<Step> steps() {
        if (steps != null) return steps;
        List<Step> s = new ArrayList<>();
        // v1: version counter introduced, nothing to fix
        s.add(move(2, "safariSparklingParty", "safariSparklingChannel"));
        s.add(transform(2, "safariSparklingChannel", ConfigMigrator::boolToChannel));
        s.add(move(2, "safariShareTimeParty", "safariShareTimeChannel"));
        s.add(transform(2, "safariShareTimeChannel", ConfigMigrator::boolToChannel));
        s.add(new Step(3, "glfw keys -> sdl scancodes", ConfigMigrator::glfwToSdl));
        s.add(new Step(4, "harp + insta loadout keys -> sdl", ConfigMigrator::keyArraysToSdl));
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

    // old party-chat boolean -> Announce channel
    private static JsonElement boolToChannel(JsonElement v) {
        if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean())
            return new JsonPrimitive(v.getAsBoolean() ? "Party" : "Nobody");
        return v;
    }

    private static final java.util.regex.Pattern KEY_FIELD = java.util.regex.Pattern.compile("\\w*(Key|Keybind)|leapKey\\w+|farmKeys\\w+");

    // v3 skipped these two arrays; harp only while it still holds the old glfw 1-7 default
    private static JsonObject keyArraysToSdl(JsonObject json) {
        if (json.get("harpKeys") instanceof JsonArray a && a.toString().equals("[49,50,51,52,53,54,55]"))
            for (int i = 0; i < a.size(); i++) a.set(i, new JsonPrimitive(KeyNames.fromGlfw(a.get(i).getAsInt())));
        if (json.get("instaLoadoutKeys") instanceof JsonArray a)
            for (int i = 0; i < a.size(); i++)
                if (isNum(a.get(i))) a.set(i, new JsonPrimitive(KeyNames.fromGlfw(a.get(i).getAsInt())));
        return json;
    }

    // 26.3: binds are SDL scancodes and 1-based mouse buttons, modifiers are SDL bits
    private static JsonObject glfwToSdl(JsonObject json) {
        for (java.util.Map.Entry<String, JsonElement> e : json.entrySet()) {
            if (KEY_FIELD.matcher(e.getKey()).matches() && isNum(e.getValue()))
                e.setValue(new JsonPrimitive(KeyNames.fromGlfw(e.getValue().getAsInt())));
        }
        for (String arr : new String[]{"wardrobeKeys", "loadoutKeys", "petKeys"}) {
            if (!(json.get(arr) instanceof JsonArray a)) continue;
            for (int i = 0; i < a.size(); i++)
                if (isNum(a.get(i))) a.set(i, new JsonPrimitive(KeyNames.fromGlfw(a.get(i).getAsInt())));
        }
        if (json.get("featureToggleBinds") instanceof JsonObject m) {
            for (java.util.Map.Entry<String, JsonElement> e : m.entrySet())
                if (isNum(e.getValue())) e.setValue(new JsonPrimitive(KeyNames.fromGlfw(e.getValue().getAsInt())));
        }
        if (json.get("admSimSettings") instanceof JsonObject m) {
            for (java.util.Map.Entry<String, JsonElement> e : m.entrySet()) {
                if (!e.getKey().endsWith("Keybind") || !e.getValue().isJsonPrimitive()) continue;
                try {
                    e.setValue(new JsonPrimitive(Integer.toString(KeyNames.fromGlfw(Integer.parseInt(e.getValue().getAsString())))));
                } catch (NumberFormatException ignored) {}
            }
        }
        if (json.get("keybinds") instanceof JsonArray binds) {
            for (JsonElement el : binds) {
                if (!(el instanceof JsonObject kb)) continue;
                for (String f : new String[]{"key", "seqKey", "key2"})
                    if (isNum(kb.get(f))) kb.addProperty(f, KeyNames.fromGlfw(kb.get(f).getAsInt()));
                if (isNum(kb.get("modifiers"))) kb.addProperty("modifiers", KeyNames.modsFromGlfw(kb.get("modifiers").getAsInt()));
            }
        }
        return json;
    }

    private static boolean isNum(JsonElement v) {
        return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber();
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
