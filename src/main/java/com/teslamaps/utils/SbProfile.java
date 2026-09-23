package com.teslamaps.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.registry.Feature;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Active SkyBlock profile (player uuid + profile name), like SkyHanni/Skysoft key their storage.
 * Name comes from the join chat line or the "Profile: X" tab line; the last one per player is
 * remembered so data resolves before Hypixel tells us again.
 */
public final class SbProfile {

    public static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("sbprofiles");
    private static final Path LAST = ROOT.resolve("last.json");
    private static final Gson GSON = new Gson();

    private static final Pattern TAB = Pattern.compile("^Profile: ([A-Za-z0-9 ]+?)(?:\\s*[♲Ⓑ☀].*)?$");
    private static final String[] CHAT = {"You are playing on profile: ", "Your profile was changed to: "};

    private static Map<String, String> last;
    private static String key;
    private static int version;
    private static int ticks;

    private SbProfile() {}

    public static void register() {
        Feature.of("SbProfile").tickAlways(SbProfile::tick).serverChat(SbProfile::onChat);
    }

    /** "uuid/profile", or null when this player never showed a profile. */
    public static String key() {
        if (key == null) refresh();
        return key;
    }

    /** Bumps whenever {@link #key()} changes, cheap to compare every call. */
    public static int version() {
        if (key == null) refresh();
        return version;
    }

    /** Folder for the active profile's files, null when unknown. */
    public static Path dir() {
        String k = key();
        return k == null ? null : ROOT.resolve(k);
    }

    public static String name() {
        String k = key();
        return k == null ? null : k.substring(k.indexOf('/') + 1);
    }

    private static void tick() {
        if (++ticks % 20 != 0) return;
        refresh();
        for (String line : TabListUtils.getTabListLines()) {
            Matcher m = TAB.matcher(StringUtil.stripTrim(line));
            if (m.matches()) { set(m.group(1)); return; }
        }
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        for (String p : CHAT) {
            if (!t.startsWith(p)) continue;
            String n = t.substring(p.length());
            int cut = n.indexOf(" (");
            if (cut >= 0) n = n.substring(0, cut);
            set(n);
            return;
        }
    }

    private static String uuid() {
        UUID id = Minecraft.getInstance().getUser().getProfileId();
        return id == null ? null : id.toString();
    }

    private static String normalize(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", "").trim();
        return n.isEmpty() ? null : n;
    }

    private static void set(String rawName) {
        String n = normalize(rawName);
        String u = uuid();
        if (n == null || u == null) return;
        loadLast();
        if (!n.equals(last.get(u))) {
            last.put(u, n);
            saveLast();
        }
        refresh();
    }

    private static void refresh() {
        loadLast();
        String u = uuid();
        String n = u == null ? null : last.get(u);
        String k = n == null ? null : u + "/" + n;
        if (k != null && !k.equals(key)) {
            key = k;
            version++;
            TeslaMaps.LOGGER.info("SkyBlock profile: {}", k);
        }
    }

    private static void loadLast() {
        if (last != null) return;
        last = new HashMap<>();
        try {
            if (Files.exists(LAST)) {
                Map<String, String> m = GSON.fromJson(Files.readString(LAST), new TypeToken<Map<String, String>>() {}.getType());
                if (m != null) last.putAll(m);
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to load sbprofiles/last.json", e);
        }
    }

    private static void saveLast() {
        try {
            Files.createDirectories(ROOT);
            Files.writeString(LAST, GSON.toJson(last));
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to save sbprofiles/last.json", e);
        }
    }
}
