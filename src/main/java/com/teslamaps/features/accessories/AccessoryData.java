package com.teslamaps.features.accessories;

// Ported from Skyblocker's AccessoriesHelper.java, families from NEU's misc.json talisman_upgrades
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ProfiledFile;
import com.teslamaps.utils.RepoCache;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Which accessories the profile owns (read from the Accessory Bag) and how they relate by family. */
public final class AccessoryData {

    private static final String MISC_URL =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/misc.json";
    private static final Pattern BAG_TITLE = Pattern.compile("^Accessory Bag(?: \\((\\d+)/\\d+\\))?$");

    public enum Status {
        COLLECTED("§a✔", "§aCollected"),
        UPGRADE("§e✦", "§eUpgrade"),
        UPGRADABLE("§6↑", "§6Upgradable"),
        DOWNGRADE("§7↓", "§7Downgrade"),
        MISSING("§c✖", "§cMissing");

        public final String symbol, label;

        Status(String symbol, String label) {
            this.symbol = symbol;
            this.label = label;
        }
    }

    public record Report(Status status, String detail) {}

    private static class Saved {
        Map<Integer, List<String>> pages = new HashMap<>();
        Map<Integer, List<String>> recomb = new HashMap<>();
    }

    private static final Gson GSON = new Gson();
    private static final ProfiledFile FILE = new ProfiledFile("accessories.json",
            FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("accessories.json"));

    private static Saved saved = new Saved();
    private static boolean loaded;

    private static volatile Map<String, String> family = Map.of();
    private static volatile Map<String, Integer> tier = Map.of();
    private static volatile Map<String, Integer> familyMax = Map.of();
    private static volatile Set<String> ignored = Set.of();
    private static volatile boolean repoLoaded, repoLoading;

    private static int version;
    private static int builtVersion = -1;
    private static int builtItems = -1;
    private static Set<String> owned = Set.of();
    private static Set<String> dupes = Set.of();
    private static Set<String> recombed = Set.of();
    private static Map<String, Integer> ownedTier = Map.of();
    private static final Map<String, Report> reports = new HashMap<>();

    private static Object lastScreen;
    private static long openedMs;
    private static long lastCaptureMs;

    private AccessoryData() {}

    static {
        com.teslamaps.utils.SbProfile.onChange(() -> {
            if (loaded && FILE.stale()) load();
        });
    }

    public static void tick() {
        ensure();
        loadRepo();
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            lastScreen = null;
            return;
        }
        Matcher m = BAG_TITLE.matcher(StringUtil.stripTrim(cs.getTitle().getString()));
        if (!m.matches()) return;
        long now = System.currentTimeMillis();
        if (cs != lastScreen) {
            lastScreen = cs;
            openedMs = now;
            lastCaptureMs = 0;
        }
        if (now - openedMs < 500 || now - lastCaptureMs < 500) return;
        lastCaptureMs = now;
        int page = m.group(1) == null ? 1 : Integer.parseInt(m.group(1));
        List<String> ids = new ArrayList<>();
        List<String> rec = new ArrayList<>();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            String id = ItemUtil.skyblockId(st);
            if (id.isEmpty()) continue;
            ids.add(id);
            CustomData d = st.get(DataComponents.CUSTOM_DATA);
            if (d != null && d.copyTag().getIntOr("rarity_upgrades", 0) > 0) rec.add(id);
        }
        if (ids.equals(saved.pages.get(page)) && rec.equals(saved.recomb.get(page))) return;
        saved.pages.put(page, ids);
        saved.recomb.put(page, rec);
        version++;
        save();
    }

    private static void ensure() {
        if (!loaded) load();
    }

    private static void load() {
        loaded = true;
        saved = new Saved();
        Path p = FILE.path();
        try {
            if (Files.exists(p)) {
                Saved s = GSON.fromJson(Files.readString(p), new TypeToken<Saved>() {}.getType());
                if (s != null) {
                    if (s.pages == null) s.pages = new HashMap<>();
                    if (s.recomb == null) s.recomb = new HashMap<>();
                    saved = s;
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to load accessories.json", e);
        }
        version++;
    }

    private static void save() {
        Path p = FILE.path();
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(saved));
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to save accessories.json", e);
        }
    }

    private static void loadRepo() {
        if (repoLoaded || repoLoading) return;
        repoLoading = true;
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject misc = RepoCache.fetch("neu-misc", MISC_URL, 15).getAsJsonObject();
                Map<String, List<String>> up = new HashMap<>();
                JsonObject t = misc.getAsJsonObject("talisman_upgrades");
                if (t != null) {
                    for (Map.Entry<String, JsonElement> e : t.entrySet()) {
                        List<String> l = new ArrayList<>();
                        for (JsonElement el : e.getValue().getAsJsonArray()) l.add(el.getAsString());
                        up.put(e.getKey(), l);
                    }
                }
                Set<String> ign = new HashSet<>();
                if (misc.has("ignored_talisman")) for (JsonElement el : misc.getAsJsonArray("ignored_talisman")) ign.add(el.getAsString());

                // union of every upgrade edge; tier = how many members list this one as their upgrade
                Map<String, String> parent = new HashMap<>();
                Map<String, Integer> tiers = new HashMap<>();
                for (Map.Entry<String, List<String>> e : up.entrySet()) {
                    tiers.putIfAbsent(e.getKey(), 0);
                    for (String u : e.getValue()) {
                        union(parent, e.getKey(), u);
                        tiers.merge(u, 1, Integer::sum);
                    }
                }
                Map<String, String> fam = new HashMap<>();
                Map<String, Integer> max = new HashMap<>();
                for (String id : tiers.keySet()) {
                    String root = find(parent, id);
                    fam.put(id, root);
                    max.merge(root, tiers.get(id), Math::max);
                }
                family = fam;
                tier = tiers;
                familyMax = max;
                ignored = ign;
                repoLoaded = true;
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[Accessories] misc.json failed: {}", e.toString());
            } finally {
                repoLoading = false;
            }
        });
    }

    private static String find(Map<String, String> parent, String x) {
        String p = parent.getOrDefault(x, x);
        if (p.equals(x)) return x;
        String r = find(parent, p);
        parent.put(x, r);
        return r;
    }

    private static void union(Map<String, String> parent, String a, String b) {
        String ra = find(parent, a), rb = find(parent, b);
        if (!ra.equals(rb)) parent.put(rb, ra);
    }

    /** Recomputes the owned sets when the bag or the repo changed. */
    private static void rebuild() {
        ensure();
        int items = SkyblockItems.accessoryIds().size() + (repoLoaded ? 1 : 0);
        if (builtVersion == version && builtItems == items) return;
        builtVersion = version;
        builtItems = items;
        Set<String> o = new HashSet<>(), d = new HashSet<>(), r = new HashSet<>();
        for (List<String> page : saved.pages.values()) for (String id : page) if (!o.add(id)) d.add(id);
        for (List<String> page : saved.recomb.values()) r.addAll(page);
        Map<String, Integer> ot = new HashMap<>();
        for (String id : o) {
            String f = family.get(id);
            if (f != null) ot.merge(f, tier.getOrDefault(id, 0), Math::max);
        }
        owned = o;
        dupes = d;
        recombed = r;
        ownedTier = ot;
        reports.clear();
    }

    public static boolean hasData() {
        ensure();
        return !saved.pages.isEmpty();
    }

    public static int version() {
        rebuild();
        return builtVersion * 31 + builtItems;
    }

    public static boolean isAccessory(String id) {
        return id != null && !id.isEmpty() && SkyblockItems.accessoryIds().contains(id) && !ignored.contains(id);
    }

    public static boolean isDuplicate(String id) {
        rebuild();
        return dupes.contains(id);
    }

    public static Set<String> owned() {
        rebuild();
        return owned;
    }

    public static boolean isRecombed(String id) {
        rebuild();
        return recombed.contains(id);
    }

    public static String familyOf(String id) {
        return family.get(id);
    }

    public static int tierOf(String id) {
        return tier.getOrDefault(id, 0);
    }

    public static int familyMaxTier(String fam) {
        return familyMax.getOrDefault(fam, 0);
    }

    /** Highest owned tier in the id's family, -1 if none; for family-less ids 0 when owned. */
    public static int ownedTierFor(String id) {
        rebuild();
        String f = family.get(id);
        if (f == null) return owned.contains(id) ? 0 : -1;
        return ownedTier.getOrDefault(f, -1);
    }

    /** Owned id of the highest tier in the family, null if none. */
    public static String ownedBestInFamily(String id) {
        rebuild();
        String f = family.get(id);
        if (f == null) return owned.contains(id) ? id : null;
        String best = null;
        int bt = -1;
        for (String o : owned) {
            if (!f.equals(family.get(o))) continue;
            int t = tier.getOrDefault(o, 0);
            if (t > bt) { bt = t; best = o; }
        }
        return best;
    }

    public static Report report(String id) {
        if (!isAccessory(id) || !hasData()) return null;
        rebuild();
        Report cached = reports.get(id);
        if (cached != null) return cached;
        Report r = compute(id);
        reports.put(id, r);
        return r;
    }

    private static Report compute(String id) {
        String f = family.get(id);
        if (f == null) return new Report(owned.contains(id) ? Status.COLLECTED : Status.MISSING, "");
        int t = tier.getOrDefault(id, 0) + 1;
        int max = familyMax.getOrDefault(f, 0) + 1;
        int have = ownedTier.getOrDefault(f, -1) + 1;
        if (have == 0) return new Report(Status.MISSING, "(" + t + "/" + max + ")");
        if (t == max && have == max) return new Report(Status.COLLECTED, "");
        if (t > have) return new Report(Status.UPGRADE, "(" + have + "→" + t + "/" + max + ")");
        if (t < have) return new Report(Status.DOWNGRADE, "(" + have + "→" + t + "/" + max + ")");
        if (t < max) return new Report(Status.UPGRADABLE, "(" + have + "/" + max + ")");
        return new Report(Status.COLLECTED, "");
    }

    /** Magical power per rarity. */
    public static int mp(String rarity) {
        return switch (rarity == null ? "" : rarity) {
            case "COMMON", "SPECIAL" -> 3;
            case "UNCOMMON", "VERY_SPECIAL" -> 5;
            case "RARE" -> 8;
            case "EPIC" -> 12;
            case "LEGENDARY" -> 16;
            case "MYTHIC" -> 22;
            default -> 0;
        };
    }

    /** Rarity a recombobulator turns this one into, null when none. */
    public static String recombed(String rarity) {
        return switch (rarity == null ? "" : rarity) {
            case "COMMON" -> "UNCOMMON";
            case "UNCOMMON" -> "RARE";
            case "RARE" -> "EPIC";
            case "EPIC" -> "LEGENDARY";
            case "LEGENDARY" -> "MYTHIC";
            case "SPECIAL" -> "VERY_SPECIAL";
            default -> null;
        };
    }
}
