package com.teslamaps.profileviewer.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.api.DungeonData;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SkyBlock xp per category, a subset of NEU's LevelPage task classes (sblevels.json from the NEU
 * repo). Whatever cannot be read from the api lands in "Other" as total minus the computed part.
 */
public class SbLevels {
    public record Line(String name, long got, long max) {}

    public record Category(String name, String icon, long xp, long max, List<Line> lines) {}

    private static JsonObject constants;

    private static JsonObject constants() {
        if (constants != null) return constants;
        try (InputStream stream = SbLevels.class.getResourceAsStream("/assets/teslamaps/pv/sblevels.json")) {
            constants = stream == null ? new JsonObject()
                    : JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to read sblevels.json: {}", e.toString());
            constants = new JsonObject();
        }
        return constants;
    }

    private static JsonObject obj(JsonObject parent, String... path) {
        JsonObject node = parent;
        for (String key : path) {
            if (node == null || !node.has(key) || !node.get(key).isJsonObject()) return null;
            node = node.getAsJsonObject(key);
        }
        return node;
    }

    private static long num(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonPrimitive()) return 0;
        try {
            return parent.get(key).getAsLong();
        } catch (Exception e) {
            return 0;
        }
    }

    private static int arr(JsonObject parent, String key, int index) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonArray()) return 0;
        JsonArray array = parent.getAsJsonArray(key);
        return index < array.size() ? array.get(index).getAsInt() : 0;
    }

    private static List<String> strings(JsonObject parent, String key) {
        List<String> out = new ArrayList<>();
        if (parent != null && parent.has(key) && parent.get(key).isJsonArray()) {
            for (JsonElement element : parent.getAsJsonArray(key)) {
                if (element.isJsonPrimitive()) out.add(element.getAsString());
            }
        }
        return out;
    }

    public static List<Category> compute(SkyblockProfile profile) {
        JsonObject c = constants();
        JsonObject member = profile.getMemberData();
        List<Category> out = new ArrayList<>();
        out.add(core(c, profile, member));
        out.add(dungeon(c, profile.getDungeonData(), member));
        out.add(slaying(c, member));
        out.add(skillRelated(c, member));

        long computed = 0;
        for (Category category : out) computed += category.xp();
        long total = num(obj(member, "leveling"), "experience");
        JsonObject maxes = obj(c, "category_xp");
        long otherMax = num(maxes, "essence_shop_task") + num(maxes, "miscellaneous_task")
                + num(maxes, "story_task") + num(maxes, "event_task");
        out.add(new Category("Other", "book", Math.max(0, total - computed), otherMax, List.of(
                new Line("Essence shops, misc, story, events", Math.max(0, total - computed), otherMax))));
        return out;
    }

    private static Category core(JsonObject c, SkyblockProfile profile, JsonObject member) {
        JsonObject task = obj(c, "core_task");
        long skills = 0;
        for (String key : List.of("taming", "mining", "foraging", "enchanting", "carpentry", "farming", "combat", "fishing", "alchemy")) {
            SkillData skill = profile.getSkills().get(key);
            if (skill == null) continue;
            for (int i = 1; i <= skill.getLevel(); i++) skills += i <= 10 ? 5 : i <= 25 ? 10 : i <= 50 ? 20 : 30;
        }

        JsonObject soul = obj(member, "fairy_soul");
        long fairy = (num(soul, "total_collected") - num(soul, "unspent_souls")) / 5 * Math.max(1, num(task, "fairy_souls_xp"));

        long collections = 0;
        if (CollectionRepo.isLoaded()) {
            for (CollectionRepo.Category category : CollectionRepo.categories()) {
                for (CollectionRepo.Entry entry : category.items()) {
                    collections += (long) CollectionRepo.tier(entry, profile.getCollections().getOrDefault(entry.id(), 0L))
                            * num(task, "collections_xp");
                }
            }
        }

        long minions = 0;
        for (Set<Integer> tiers : MinionData.craftedBy(member).values()) {
            for (int tier : tiers) minions += MinionData.tierXp(tier);
        }

        long bank = 0;
        long travel = 0;
        JsonObject bankXp = obj(task, "bank_upgrades_xp");
        for (String name : strings(obj(member, "leveling"), "completed_tasks")) {
            if (name.startsWith("FAST_TRAVEL_")) travel += num(task, "fast_travel_unlocked_xp");
            if (bankXp != null && bankXp.has(name)) bank += bankXp.get(name).getAsInt();
        }

        List<Line> lines = List.of(
                new Line("Skill Level Up", skills, num(task, "skill_level_up")),
                new Line("Fairy Souls", fairy, num(task, "fairy_souls")),
                new Line("Collections", collections, num(task, "collections")),
                new Line("Craft Minions", minions, num(task, "craft_minions")),
                new Line("Bank Upgrades", bank, num(task, "bank_upgrades")),
                new Line("Fast Travel Scrolls", travel, num(task, "fast_travel_unlocked")));
        return category("Core", "nether_star", lines, num(obj(c, "category_xp"), "core_task"));
    }

    private static Category dungeon(JsonObject c, DungeonData data, JsonObject member) {
        JsonObject task = obj(c, "dungeon_task");
        long level = 0;
        long classes = 0;
        long floors = 0;
        if (data != null) {
            for (int i = 1; i <= data.getCatacombsLevel(); i++) level += i < 40 ? 20 : 40;
            for (DungeonData.ClassData cls : data.getClasses().values()) {
                classes += Math.min(50, cls.getLevel()) * num(task, "class_xp");
            }
        }
        JsonObject normal = obj(member, "dungeons", "dungeon_types", "catacombs", "tier_completions");
        JsonObject master = obj(member, "dungeons", "dungeon_types", "master_catacombs", "tier_completions");
        for (int i = 0; i <= 7; i++) {
            if (normal != null && normal.has(String.valueOf(i))) floors += arr(task, "complete_catacombs", i);
            if (master != null && master.has(String.valueOf(i))) floors += num(task, "complete_master_catacombs");
        }
        List<Line> lines = List.of(
                new Line("Catacombs Level Up", level, num(task, "catacombs_level_up")),
                new Line("Class Level Up", classes, num(task, "class_level_up")),
                new Line("Complete Dungeons", floors, num(task, "complete_dungeon")));
        return category("Dungeon", "wither_skeleton_skull", lines, num(obj(c, "category_xp"), "dungeon_task"));
    }

    private static final Map<String, long[]> SLAYER_XP = Map.of(
            "zombie", new long[]{5, 15, 200, 1000, 5000, 20000, 100000, 400000, 1000000},
            "spider", new long[]{5, 25, 200, 1000, 5000, 20000, 100000, 400000, 1000000},
            "wolf", new long[]{10, 30, 250, 1500, 5000, 20000, 100000, 400000, 1000000},
            "enderman", new long[]{10, 30, 250, 1500, 5000, 20000, 100000, 400000, 1000000},
            "blaze", new long[]{10, 30, 250, 1500, 5000, 20000, 100000, 400000, 1000000},
            "vampire", new long[]{20, 75, 240, 840, 2400});

    private static final List<String> KUUDRA_TIERS = List.of("none", "hot", "burning", "fiery", "infernal");

    private static Category slaying(JsonObject c, JsonObject member) {
        JsonObject task = obj(c, "slaying_task");
        JsonObject bosses = obj(member, "slayer", "slayer_bosses");
        long levels = 0;
        long defeats = 0;
        for (Map.Entry<String, long[]> slayer : SLAYER_XP.entrySet()) {
            JsonObject boss = obj(bosses, slayer.getKey());
            long xp = num(boss, "xp");
            int level = 0;
            for (long threshold : slayer.getValue()) if (xp >= threshold) level++;
            for (int i = 0; i < level; i++) levels += arr(task, "slayer_level_up_xp", i);
            for (int tier = 0; tier < 5; tier++) {
                if (num(boss, "boss_kills_tier_" + tier) > 0) defeats += arr(task, "defeat_slayers_xp", tier);
            }
        }

        long kuudra = 0;
        JsonObject kuudraTiers = obj(member, "nether_island_player_data", "kuudra_completed_tiers");
        long kuudraCollection = 0;
        for (int i = 0; i < KUUDRA_TIERS.size(); i++) {
            long runs = num(kuudraTiers, KUUDRA_TIERS.get(i));
            if (runs > 0) kuudra += arr(task, "defeat_kuudra_xp", i);
            kuudraCollection += (i + 1) * runs;
        }

        long bossCollection = 0;
        JsonObject collections = obj(task, "boss_collections_xp");
        long[] kuudraSteps = {10, 100, 500, 2000, 5000};
        for (int i = 0; i < kuudraSteps.length; i++) {
            if (kuudraCollection >= kuudraSteps[i]) bossCollection += arr(collections, "kuudra_collection_xp", i);
        }
        JsonObject normal = obj(member, "dungeons", "dungeon_types", "catacombs", "tier_completions");
        JsonObject master = obj(member, "dungeons", "dungeon_types", "master_catacombs", "tier_completions");
        int[] low = {25, 50, 100, 150, 250, 1000};
        int[] thorn = {25, 50, 150, 250, 400, 1000};
        int[] high = {50, 100, 150, 250, 500, 750, 1000};
        for (int floor = 1; floor <= 7; floor++) {
            long runs = num(normal, String.valueOf(floor)) + 2 * num(master, String.valueOf(floor));
            int[] steps = floor <= 3 ? low : floor == 4 ? thorn : high;
            for (int i = 0; i < steps.length; i++) {
                if (runs >= steps[i]) bossCollection += arr(collections, "dungeon_collection_xp", i);
            }
        }

        long mythos = Math.min(num(task, "mythological_kills"), num(obj(member, "player_stats", "mythos"), "kills") / 100);

        List<Line> lines = List.of(
                new Line("Slayer Level Up", levels, num(task, "slayer_level_up")),
                new Line("Defeat Slayers", defeats, num(task, "defeat_slayers")),
                new Line("Boss Collections", bossCollection, num(task, "boss_collections")),
                new Line("Defeat Kuudra", kuudra, num(task, "defeat_kuudra")),
                new Line("Mythological Kills", mythos, num(task, "mythological_kills")));
        return category("Slaying", "golden_sword", lines, num(obj(c, "category_xp"), "slaying_task"));
    }

    private static Category skillRelated(JsonObject c, JsonObject member) {
        JsonObject task = obj(c, "skill_related_task");
        JsonObject mining = obj(task, "mining");
        JsonObject farming = obj(task, "farming");
        JsonObject fishing = obj(task, "fishing");

        SkillTreeInfo hotm = new SkillTreeInfo(member, SkillTreeInfo.Type.MINING);
        long hotmXp = 0;
        for (int i = 1; i <= hotm.level; i++) hotmXp += arr(mining, "hotm_xp", i - 1);

        long commissions = 0;
        for (String objective : strings(obj(member, "objectives"), "tutorial")) {
            for (int i = 1; i <= 6; i++) {
                if (objective.equals("commission_milestone_reward_skyblock_xp_tier_" + i)) {
                    commissions += arr(mining, "commission_milestone_xp", i - 1);
                }
            }
        }

        JsonObject glacite = obj(member, "glacite_player_data");
        long fossils = strings(glacite, "fossils_donated").size() * num(mining, "fossil_research_xp");

        JsonObject milestone = obj(member, "player_stats", "pets", "milestone");
        long rock = 0;
        long ores = num(milestone, "ores_mined");
        for (int i = 0; i < 5; i++) if (ores >= arr(mining, "rock_milestone_required", i)) rock += num(mining, "rock_milestone_xp");
        long dolphin = 0;
        long seaCreatures = num(milestone, "sea_creatures_killed");
        for (int i = 0; i < 5; i++) {
            if (seaCreatures >= arr(fishing, "dolphin_milestone_required", i)) dolphin += num(fishing, "dolphin_milestone_xp");
        }

        JsonObject perks = obj(member, "jacobs_contest", "perks");
        long anita = (num(perks, "double_drops") + num(perks, "farming_level_cap")) * num(farming, "anita_shop_upgrades_xp");

        long trophy = 0;
        JsonObject fish = obj(member, "trophy_fish");
        if (fish != null) {
            for (String key : fish.keySet()) {
                if (key.endsWith("_bronze")) trophy += arr(fishing, "trophy_fish_xp", 0);
                else if (key.endsWith("_silver")) trophy += arr(fishing, "trophy_fish_xp", 1);
                else if (key.endsWith("_gold")) trophy += arr(fishing, "trophy_fish_xp", 2);
                else if (key.endsWith("_diamond")) trophy += arr(fishing, "trophy_fish_xp", 3);
            }
        }

        List<Line> lines = List.of(
                new Line("Heart of the Mountain", hotmXp, num(mining, "hotm")),
                new Line("Commission Milestones", commissions, num(mining, "commission_milestone")),
                new Line("Fossil Research", fossils, num(mining, "fossil_research")),
                new Line("Rock Milestone", rock, num(mining, "rock_milestone")),
                new Line("Anita's Shop", anita, num(farming, "anita_shop_upgrades")),
                new Line("Trophy Fish", trophy, num(fishing, "trophy_fish")),
                new Line("Dolphin Milestone", dolphin, num(fishing, "dolphin_milestone")));
        return category("Skill Related", "diamond_pickaxe", lines, num(obj(c, "category_xp"), "skill_related_task"));
    }

    private static Category category(String name, String icon, List<Line> lines, long max) {
        long xp = 0;
        for (Line line : lines) xp += line.got();
        return new Category(name, icon, xp, max, lines);
    }
}
