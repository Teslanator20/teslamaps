package com.teslamaps.features.fishing;

// Counts per trophy fish and tier, after SkyHanni's TrophyFishManager.kt (Odger menu, API import, live chat)
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ProfiledFile;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TrophyFishData {

    public static final String[] TIERS = {"BRONZE", "SILVER", "GOLD", "DIAMOND"};
    public static final String[] TIER_COLORS = {"§8", "§7", "§6", "§b"};

    public record Fish(String key, String display, String color) {}

    public static final List<Fish> FISH = List.of(
            new Fish("sulphur_skitter", "Sulphur Skitter", "§f"),
            new Fish("obfuscated_fish_1", "Obfuscated 1", "§f"),
            new Fish("steaming_hot_flounder", "Steaming-Hot Flounder", "§f"),
            new Fish("gusher", "Gusher", "§f"),
            new Fish("blobfish", "Blobfish", "§f"),
            new Fish("obfuscated_fish_2", "Obfuscated 2", "§a"),
            new Fish("slugfish", "Slugfish", "§a"),
            new Fish("flyfish", "Flyfish", "§a"),
            new Fish("obfuscated_fish_3", "Obfuscated 3", "§9"),
            new Fish("lava_horse", "Lavahorse", "§9"),
            new Fish("mana_ray", "Mana Ray", "§9"),
            new Fish("volcanic_stonefish", "Volcanic Stonefish", "§9"),
            new Fish("vanille", "Vanille", "§9"),
            new Fish("skeleton_fish", "Skeleton Fish", "§5"),
            new Fish("moldfin", "Moldfin", "§5"),
            new Fish("soul_fish", "Soul Fish", "§5"),
            new Fish("karate_fish", "Karate Fish", "§5"),
            new Fish("golden_fish", "Golden Fish", "§6"));

    // "TROPHY FISH! You caught a Lavahorse GOLD!" and the frog variant
    private static final Pattern CATCH = Pattern.compile("TROPHY \\w+! You caught an? (.+?) (BRONZE|SILVER|GOLD|DIAMOND)!");
    private static final Pattern ODGER_HAVE = Pattern.compile("^(Bronze|Silver|Gold|Diamond) ✔ \\(([\\d,]+)\\)$");
    private static final Pattern ODGER_MISSING = Pattern.compile("^(Bronze|Silver|Gold|Diamond) ✖$");

    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<LinkedHashMap<String, int[]>>() {}.getType();
    private static final ProfiledFile FILE = new ProfiledFile("trophy_fish.json",
            FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("trophy_fish.json"));

    private static Map<String, int[]> counts = new LinkedHashMap<>();
    private static boolean loaded;
    private static int version;
    private static Object lastScreen;
    private static int screenTicks;

    /** The last caught line, for the chat rewrite and the HUD highlight. */
    public static String lastRaw, lastKey;
    public static int lastTier = -1, lastAmount;
    public static long lastAt;

    private TrophyFishData() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("TrophyFishData").section("Fishing").when(() -> cfg().trophyFishHud || cfg().trophyFishMessages)
                .tick(TrophyFishData::tick)
                .serverChat(TrophyFishData::onChat);
    }

    public static int version() { return version; }

    public static int[] counts(String key) {
        ensure();
        int[] c = counts.get(key);
        return c == null ? new int[4] : c;
    }

    public static boolean empty() {
        ensure();
        return counts.isEmpty();
    }

    public static Fish byKey(String key) {
        for (Fish f : FISH) if (f.key.equals(key)) return f;
        return null;
    }

    static String keyFor(String name) {
        String n = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (n.startsWith("obfuscated")) {
            char last = n.charAt(n.length() - 1);
            if (last >= '1' && last <= '3') return "obfuscated_fish_" + last;
        }
        for (Fish f : FISH) {
            if (f.key.replace("_", "").equals(n) || f.display.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "").equals(n)) return f.key;
        }
        return null;
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.contains("TROPHY ")) return;
        Matcher m = CATCH.matcher(t);
        if (!m.find()) return;
        String key = keyFor(m.group(1));
        if (key == null) return;
        int tier = java.util.Arrays.asList(TIERS).indexOf(m.group(2));
        ensure();
        int[] c = counts.computeIfAbsent(key, k -> new int[4]);
        c[tier]++;
        lastRaw = raw;
        lastKey = key;
        lastTier = tier;
        lastAmount = c[tier];
        lastAt = System.currentTimeMillis();
        version++;
        save();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) { lastScreen = null; return; }
        if (cs != lastScreen) { lastScreen = cs; screenTicks = 0; }
        if (++screenTicks != 10 && screenTicks != 30) return;
        if (!StringUtil.stripTrim(cs.getTitle().getString()).equals("Trophy Fish")) return;
        ensure();
        int updated = 0;
        for (Slot s : cs.getMenu().slots) {
            if (s.container == mc.player.getInventory()) continue;
            ItemStack st = s.getItem();
            if (st.isEmpty()) continue;
            String key = keyFor(StringUtil.stripTrim(st.getHoverName().getString()));
            if (key == null) continue;
            int[] c = counts.computeIfAbsent(key, k -> new int[4]);
            boolean changed = false;
            for (String l : ItemUtil.loreLines(st)) {
                String p = l.trim();
                Matcher h = ODGER_HAVE.matcher(p);
                int tier = -1, amount = 0;
                if (h.matches()) {
                    tier = tierOf(h.group(1));
                    amount = Integer.parseInt(h.group(2).replace(",", ""));
                } else {
                    Matcher e = ODGER_MISSING.matcher(p);
                    if (e.matches()) tier = tierOf(e.group(1));
                }
                if (tier < 0 || c[tier] == amount) continue;
                c[tier] = amount;
                changed = true;
            }
            if (changed) updated++;
        }
        if (updated > 0) {
            version++;
            save();
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Updated " + updated + " trophy fish from Odger."));
        }
    }

    private static int tierOf(String s) {
        return java.util.Arrays.asList(TIERS).indexOf(s.toUpperCase(Locale.ROOT));
    }

    /** Pulls trophy_fish from the API for the selected profile; only ever raises counts. */
    public static void importFromApi() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String uuid = mc.player.getUUID().toString().replace("-", "");
        var profiles = new com.teslamaps.profileviewer.data.SkyblockProfiles(uuid, mc.player.getName().getString());
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Loading trophy fish from the API..."));
        profiles.load().thenRun(() -> mc.execute(() -> {
            var p = profiles.getSelectedProfile();
            JsonElement tf = p == null ? null : p.getMemberData().get("trophy_fish");
            if (mc.player == null) return;
            if (tf == null || !tf.isJsonObject()) {
                mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §cNo trophy fish data in the API."));
                return;
            }
            JsonObject o = tf.getAsJsonObject();
            ensure();
            int raised = 0;
            for (Fish f : FISH) {
                int[] c = counts.computeIfAbsent(f.key, k -> new int[4]);
                for (int i = 0; i < 4; i++) {
                    JsonElement v = o.get(f.key + "_" + TIERS[i].toLowerCase(Locale.ROOT));
                    int n = v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() ? v.getAsInt() : 0;
                    if (n > c[i]) { c[i] = n; raised++; }
                }
            }
            version++;
            save();
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Trophy fish imported (" + raised + " values raised)."));
        }));
    }

    private static void ensure() {
        if (loaded && !FILE.stale()) return;
        loaded = true;
        counts = new LinkedHashMap<>();
        version++;
        Path p = FILE.path();
        try {
            if (Files.exists(p)) {
                Map<String, int[]> read = GSON.fromJson(Files.readString(p), TYPE);
                if (read != null) {
                    read.values().removeIf(v -> v == null || v.length != 4);
                    counts = read;
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to load trophy fish: {}", e.toString());
        }
    }

    private static void save() {
        try {
            Path p = FILE.path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(counts, TYPE));
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to save trophy fish: {}", e.toString());
        }
    }
}
