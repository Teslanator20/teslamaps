package com.teslamaps.features.foraging;

// Categories from SkyHanni's ForagingTracker.kt + repo constants/foraging/TreeGiftBonusDrops.json
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.RepoCache;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Which bonus drops the compact tree gift line keeps. */
final class GiftDropFilter {

    enum Cat { UNCOMMON, BOOKS, BOOSTERS, SHARDS, RUNES, MISC, MOBS }

    private static final String URL =
            "https://raw.githubusercontent.com/hannibal002/SkyHanni-REPO/main/constants/foraging/TreeGiftBonusDrops.json";
    private static final Map<Cat, Set<String>> ids = new EnumMap<>(Cat.class);
    private static volatile boolean fetched;

    static {
        ids.put(Cat.UNCOMMON, Set.of("STRETCHING_STICKS", "DEEP_ROOT"));
        ids.put(Cat.BOOKS, Set.of("ULTIMATE_FIRST_IMPRESSION;1", "ULTIMATE_MISSILE;1", "KARMA;1"));
        ids.put(Cat.BOOSTERS, Set.of("SWEEP_BOOSTER", "FORAGING_WISDOM_BOOSTER"));
        ids.put(Cat.SHARDS, Set.of("ATTRIBUTE_SHARD_CHOP;1", "ATTRIBUTE_SHARD_REPTILOID;1"));
        ids.put(Cat.RUNES, Set.of("AXE_FADING_WHITE_RUNE;1", "AXE_FADING_GREEN_RUNE;1", "AXE_FADING_BLUE_RUNE;1"));
        ids.put(Cat.MISC, Set.of("TREE_THE_FISH", "PET_ITEM_FORAGING_SKILL_BOOST_EPIC"));
        ids.put(Cat.MOBS, Set.of("Phanpyre", "Phanflare", "Dreadwing", "Groundhog", "Firefox", "Drybark", "Puck", "Grizzly Bear"));
    }

    private GiftDropFilter() {}

    static boolean anyHidden() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.foragingGiftHideUncommon || c.foragingGiftHideBooks || c.foragingGiftHideBoosters
                || c.foragingGiftHideShards || c.foragingGiftHideRunes || c.foragingGiftHideMisc || c.foragingGiftHideMobs;
    }

    static boolean hidden(Cat cat) {
        if (cat == null) return false;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return switch (cat) {
            case UNCOMMON -> c.foragingGiftHideUncommon;
            case BOOKS -> c.foragingGiftHideBooks;
            case BOOSTERS -> c.foragingGiftHideBoosters;
            case SHARDS -> c.foragingGiftHideShards;
            case RUNES -> c.foragingGiftHideRunes;
            case MISC -> c.foragingGiftHideMisc;
            case MOBS -> c.foragingGiftHideMobs;
        };
    }

    static void fetch() {
        if (fetched) return;
        fetched = true;
        Thread t = new Thread(() -> {
            try {
                JsonElement json = RepoCache.fetch("sh-TreeGiftBonusDrops", URL, 10);
                if (!(json instanceof JsonObject o)) return;
                put(o, "uncommon_drops", Cat.UNCOMMON);
                put(o, "enchanted_books", Cat.BOOKS);
                put(o, "boosters", Cat.BOOSTERS);
                put(o, "shards", Cat.SHARDS);
                put(o, "runes", Cat.RUNES);
                put(o, "misc_drops", Cat.MISC);
                put(o, "mobs", Cat.MOBS);
            } catch (Exception ignored) {
            }
        }, "teslamaps-giftdrops");
        t.setDaemon(true);
        t.start();
    }

    private static void put(JsonObject o, String key, Cat cat) {
        if (!(o.get(key) instanceof JsonArray arr) || arr.isEmpty()) return;
        java.util.Set<String> set = new java.util.HashSet<>();
        for (JsonElement e : arr) set.add(e.getAsString());
        synchronized (ids) {
            ids.put(cat, Set.copyOf(set));
        }
    }

    static boolean isMob(String name) {
        synchronized (ids) {
            return ids.get(Cat.MOBS).contains(name);
        }
    }

    /** Category of a bonus drop by its chat name, null when it is in none (always shown). */
    static Cat of(String itemName) {
        String name = itemName.trim();
        if (name.startsWith("Enchanted Book")) return Cat.BOOKS;
        String id = SkyblockItems.idForName(name);
        synchronized (ids) {
            for (Map.Entry<Cat, Set<String>> e : ids.entrySet()) {
                if (e.getKey() == Cat.MOBS) continue;
                for (String listed : e.getValue()) {
                    if (listed.equals(id)) return e.getKey();
                    String n = SkyblockItems.name(listed);
                    if (n != null && StringUtil.strip(n).trim().equalsIgnoreCase(name)) return e.getKey();
                }
            }
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (com.teslamaps.features.hunting.ShardData.byDisplayName(name) != null || lower.endsWith(" shard")) return Cat.SHARDS;
        if (lower.endsWith(" rune")) return Cat.RUNES;
        if (lower.endsWith(" booster")) return Cat.BOOSTERS;
        return null;
    }
}
