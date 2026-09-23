package com.teslamaps.features.invbuttons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Share codes for the button layout, and import of NEU / Firmament button configs found in
 * local launcher profiles. Ported from Skysoft's InventoryButtonLayoutSharing / ImportDiscovery / ImportParser.
 */
public final class ButtonShare {

    private static final String PREFIX = "TMIB1:";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int MAX_BUTTONS = 256;
    private static final int MAX_COMMAND = 256;
    private static final int MAX_ICON = 1024;
    private static final int MAX_COORD = 4096;
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private static final int MAX_DECOMPRESSED = 2_000_000;
    private static final int MAX_PROFILES = 256;

    private ButtonShare() {}

    // --- share code ---

    public static String export(List<InventoryButton> buttons) {
        List<InventoryButton> active = new ArrayList<>();
        for (InventoryButton b : buttons) if (b != null && b.isActive()) active.add(b.copy());
        byte[] json = GSON.toJson(active).getBytes(StandardCharsets.UTF_8);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(out)) { gz.write(json); }
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Our share code, or a plain JSON button array. Throws IllegalArgumentException with a readable reason. */
    public static List<InventoryButton> importCode(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) throw new IllegalArgumentException("Clipboard is empty.");
        String json;
        if (t.startsWith(PREFIX)) {
            if (t.length() > 1_000_000) throw new IllegalArgumentException("Share code is too large.");
            try {
                json = gunzip(Base64.getUrlDecoder().decode(t.substring(PREFIX.length())));
            } catch (Exception e) {
                throw new IllegalArgumentException("Share code is not valid.");
            }
        } else if (t.startsWith("[")) {
            json = t;
        } else {
            throw new IllegalArgumentException("Clipboard has no button share code.");
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Share code is not valid.");
        }
        if (!root.isJsonArray()) throw new IllegalArgumentException("Share code is not valid.");
        List<InventoryButton> out = readButtons(root.getAsJsonArray(), false);
        if (out.isEmpty()) throw new IllegalArgumentException("Share code has no buttons.");
        return out;
    }

    private static String gunzip(byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(data))) {
            byte[] buf = new byte[8192];
            int total = 0, n;
            while ((n = gz.read(buf)) > 0) {
                total += n;
                if (total > MAX_DECOMPRESSED) throw new IllegalArgumentException("too large");
                out.write(buf, 0, n);
            }
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static List<InventoryButton> readButtons(JsonArray array, boolean firmament) {
        List<InventoryButton> out = new ArrayList<>();
        int n = 0;
        for (JsonElement el : array) {
            if (n++ >= MAX_BUTTONS) break;
            if (!el.isJsonObject()) continue;
            InventoryButton b;
            try {
                b = GSON.fromJson(el, InventoryButton.class);
            } catch (Exception e) {
                continue;
            }
            if (b == null || b.command == null || b.command.isBlank()) continue;
            if (Math.abs(b.x) > MAX_COORD || Math.abs(b.y) > MAX_COORD) continue;
            if (b.command.length() > MAX_COMMAND || (b.icon != null && b.icon.length() > MAX_ICON)) continue;
            if (firmament && (b.icon == null || b.icon.isBlank())) continue;
            b.command = b.command.trim();
            if (b.command.startsWith("/")) b.command = b.command.substring(1);
            b.icon = normalizeIcon(b.icon);
            if (!(b.scale >= 0.5f && b.scale <= 2f)) b.scale = 1f;
            b.backgroundIndex = Math.max(0, Math.min(ButtonTexture.BACKGROUND_TYPES - 1, b.backgroundIndex));
            out.add(b);
        }
        return out;
    }

    /** "/give @p diamond_sword{...}" -> "diamond_sword"; everything else as is. */
    private static String normalizeIcon(String icon) {
        if (icon == null) return "";
        String t = icon.trim();
        if (t.startsWith("/give ") || t.startsWith("give ")) {
            for (String part : t.split("\\s+")) {
                if (part.equals("/give") || part.equals("give") || part.startsWith("@")) continue;
                int cut = part.length();
                int a = part.indexOf('['), c = part.indexOf('{');
                if (a >= 0) cut = Math.min(cut, a);
                if (c >= 0) cut = Math.min(cut, c);
                return part.substring(0, cut);
            }
            return "";
        }
        return t;
    }

    // --- import from other mods ---

    public enum Kind { NEU, FIRMAMENT }

    public record Source(Kind kind, Path path, Path settings, String profile) {
        public String label() {
            return (kind == Kind.NEU ? "NEU" : "Firmament") + " · " + profile;
        }
    }

    /** Every NEU / Firmament button file in the known launcher folders, newest first. */
    public static List<Source> discover() {
        Map<Path, Source> found = new LinkedHashMap<>();
        for (Path root : roots()) {
            for (Source s : candidates(root)) found.putIfAbsent(s.path().toAbsolutePath().normalize(), s);
        }
        List<Source> out = new ArrayList<>(found.values());
        out.sort(Comparator.comparingLong((Source s) -> modified(s.path())).reversed());
        return out;
    }

    private static long modified(Path p) {
        try { return Files.getLastModifiedTime(p).toMillis(); } catch (Exception e) { return 0; }
    }

    private static Set<Path> roots() {
        Set<Path> roots = new LinkedHashSet<>();
        Path game = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        roots.add(game);
        String appData = System.getenv("APPDATA");
        String home = System.getProperty("user.home");
        if (appData != null && !appData.isBlank()) {
            Path a = Path.of(appData);
            roots.add(a.resolve(".minecraft"));
            roots.addAll(profiles(a.resolve("com.modrinth.theseus/profiles")));
            roots.addAll(profiles(a.resolve("ModrinthApp/profiles")));
            roots.addAll(profiles(a.resolve("PrismLauncher/instances")));
            roots.addAll(profiles(a.resolve("gdlauncher_next/instances")));
        }
        if (home != null && !home.isBlank()) roots.addAll(profiles(Path.of(home).resolve("curseforge/minecraft/Instances")));
        Path parent = game.getParent();
        if (parent != null) {
            String name = parent.getFileName() == null ? "" : parent.getFileName().toString().toLowerCase();
            if (name.equals("profiles") || name.equals("instances")) roots.addAll(profiles(parent));
        }
        return roots;
    }

    private static Set<Path> profiles(Path container) {
        Set<Path> out = new LinkedHashSet<>();
        if (!Files.isDirectory(container)) return out;
        try (Stream<Path> s = Files.list(container)) {
            s.filter(Files::isDirectory).limit(MAX_PROFILES).forEach(p -> {
                out.add(p);
                out.add(p.resolve(".minecraft"));
            });
        } catch (Exception ignored) {}
        return out;
    }

    private static List<Source> candidates(Path root) {
        List<Source> out = new ArrayList<>();
        String profile = root.getFileName() != null && root.getFileName().toString().equals(".minecraft") && root.getParent() != null
                ? String.valueOf(root.getParent().getFileName())
                : String.valueOf(root.getFileName());
        for (Path p : List.of(root.resolve("config/notenoughupdates/configNew.json"), root.resolve("notenoughupdates/configNew.json"))) {
            if (Files.isRegularFile(p)) { out.add(new Source(Kind.NEU, p, null, profile)); break; }
        }
        for (Path dir : List.of(root.resolve("config/firmament"), root.resolve("firmament"))) {
            Path p = Files.isRegularFile(dir.resolve("storage/inventory-buttons.json"))
                    ? dir.resolve("storage/inventory-buttons.json") : dir.resolve("inventory-buttons.json");
            if (Files.isRegularFile(p)) {
                Path settings = dir.resolve("inventory-buttons-config.json");
                out.add(new Source(Kind.FIRMAMENT, p, Files.isRegularFile(settings) ? settings : null, profile));
                break;
            }
        }
        return out;
    }

    /** Buttons of one source; throws IllegalArgumentException with a readable reason. */
    public static List<InventoryButton> read(Source source) {
        JsonObject root = readJson(source.path());
        JsonArray arr = null;
        boolean onlyInv = false;
        if (source.kind() == Kind.NEU) {
            JsonObject hidden = obj(root, "hidden");
            if (hidden != null && hidden.get("inventoryButtons") instanceof JsonArray a) arr = a;
        } else {
            if (root.get("buttons") instanceof JsonArray a) arr = a;
            else if (obj(root, "inventory-buttons") != null && obj(root, "inventory-buttons").get("buttons") instanceof JsonArray a) arr = a;
            if (source.settings() != null) {
                JsonObject s = readJson(source.settings());
                JsonObject inner = obj(s, "inventory-buttons-config");
                JsonObject cfg = inner != null ? inner : s;
                try { onlyInv = cfg.has("only-inv") && cfg.get("only-inv").getAsBoolean(); } catch (Exception ignored) {}
            }
        }
        if (arr == null) throw new IllegalArgumentException("No inventory buttons in " + source.path().getFileName());
        List<InventoryButton> out = readButtons(arr, source.kind() == Kind.FIRMAMENT);
        if (source.kind() == Kind.FIRMAMENT) for (InventoryButton b : out) b.playerInvOnly = onlyInv;
        return out;
    }

    private static JsonObject obj(JsonObject o, String key) {
        return o != null && o.get(key) instanceof JsonObject j ? j : null;
    }

    private static JsonObject readJson(Path p) {
        try {
            if (Files.size(p) > MAX_FILE_BYTES) throw new IllegalArgumentException("File is larger than 2 MB.");
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(r).getAsJsonObject();
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read " + p.getFileName());
        }
    }
}
