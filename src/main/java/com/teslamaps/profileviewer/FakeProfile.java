package com.teslamaps.profileviewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.teslamaps.api.HypixelApi;
import com.teslamaps.profileviewer.screen.ProfileViewerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

/** Dev helper: /tmap pv fake - opens the viewer on made up data so the layout can be checked offline. */
public class FakeProfile {
    private static final String[] SKILLS = {
            "FARMING", "MINING", "COMBAT", "FORAGING", "FISHING", "ENCHANTING",
            "ALCHEMY", "CARPENTRY", "RUNECRAFTING", "SOCIAL", "TAMING",
    };

    public static void open() {
        String name = "FakePlayer";
        String uuid = "0123456789abcdef0123456789abcdef";

        HypixelApi.injectFake(name, uuid, build(uuid));
        ProfileViewerScreen.open(name);
    }

    private static JsonObject container(String data) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", 0);
        obj.addProperty("data", data);
        return obj;
    }

    private static JsonObject bagContents() {
        JsonObject bags = new JsonObject();
        bags.add("talisman_bag", container(encode(new String[]{
                "WITHER_ARTIFACT", "POWER_ARTIFACT", "SPEED_ARTIFACT", "CANDY_ARTIFACT",
                "HEGEMONY_ARTIFACT", "TREASURE_ARTIFACT", "SHADY_RING", "CROOKED_ARTIFACT",
                "MASTER_SKULL_TIER_7", "DANTE_TALISMAN", "POTION_AFFINITY_ARTIFACT",
        })));
        return bags;
    }

    /** Builds the same gzipped 1.8 item nbt hypixel sends, so the decoder gets real input. */
    private static String encode(String[] ids) {
        try {
            ListTag items = new ListTag();
            for (String id : ids) {
                CompoundTag item = new CompoundTag();
                if (id != null) {
                    item.putByte("Count", (byte) 1);
                    CompoundTag tag = new CompoundTag();
                    CompoundTag extra = new CompoundTag();
                    extra.putString("id", id);
                    tag.put("ExtraAttributes", extra);
                    CompoundTag display = new CompoundTag();
                    display.putString("Name", "§a" + id.replace('_', ' '));
                    ListTag lore = new ListTag();
                    lore.add(StringTag.valueOf("§7Fake item for layout tests"));
                    display.put("Lore", lore);
                    tag.put("display", display);
                    item.put("tag", tag);
                }
                items.add(item);
            }
            CompoundTag root = new CompoundTag();
            root.put("i", items);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(root, out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    private static JsonObject build(String uuid) {
        JsonObject member = new JsonObject();

        JsonObject experience = new JsonObject();
        long[] xp = {130_000_000L, 111_672_425L, 96_000_000L, 55_172_425L, 25_522_425L,
                8_022_425L, 3_022_425L, 522_425L, 94_500L, 94_500L, 47_472_425L};
        for (int i = 0; i < SKILLS.length; i++) {
            experience.addProperty("SKILL_" + SKILLS[i], xp[i]);
        }
        JsonObject playerData = new JsonObject();
        playerData.add("experience", experience);
        member.add("player_data", playerData);

        JsonObject currencies = new JsonObject();
        currencies.addProperty("coin_purse", 12_345_678.0);
        member.add("currencies", currencies);

        JsonObject fairy = new JsonObject();
        fairy.addProperty("total_collected", 242);
        member.add("fairy_soul", fairy);

        // dungeons
        JsonObject catacombs = new JsonObject();
        catacombs.addProperty("experience", 200_000_000.0);
        JsonObject completions = new JsonObject();
        JsonObject sPlus = new JsonObject();
        JsonObject fastest = new JsonObject();
        for (int floor = 1; floor <= 7; floor++) {
            completions.addProperty(String.valueOf(floor), floor * 40);
            sPlus.addProperty(String.valueOf(floor), 260_000 - floor * 8_000);
            fastest.addProperty(String.valueOf(floor), 320_000 - floor * 8_000);
        }
        catacombs.add("tier_completions", completions);
        catacombs.add("fastest_time_s_plus", sPlus);
        catacombs.add("fastest_time", fastest);

        JsonObject master = new JsonObject();
        JsonObject masterCompletions = new JsonObject();
        JsonObject masterSPlus = new JsonObject();
        for (int floor = 1; floor <= 7; floor++) {
            masterCompletions.addProperty(String.valueOf(floor), floor * 11);
            masterSPlus.addProperty(String.valueOf(floor), 300_000 - floor * 5_000);
        }
        master.add("tier_completions", masterCompletions);
        master.add("fastest_time_s_plus", masterSPlus);

        JsonObject types = new JsonObject();
        types.add("catacombs", catacombs);
        types.add("master_catacombs", master);

        JsonObject classes = new JsonObject();
        String[] classNames = {"healer", "mage", "berserk", "archer", "tank"};
        long[] classXp = {60_000_000L, 111_672_425L, 25_522_425L, 8_022_425L, 3_022_425L};
        for (int i = 0; i < classNames.length; i++) {
            JsonObject cls = new JsonObject();
            cls.addProperty("experience", classXp[i]);
            classes.add(classNames[i], cls);
        }

        JsonObject dungeons = new JsonObject();
        dungeons.add("dungeon_types", types);
        dungeons.add("player_classes", classes);
        dungeons.addProperty("secrets", 48_211);
        member.add("dungeons", dungeons);

        // pets
        JsonArray pets = new JsonArray();
        String[][] petData = {
                {"GOLDEN_DRAGON", "LEGENDARY"}, {"BLACK_CAT", "LEGENDARY"}, {"ENDER_DRAGON", "EPIC"},
                {"BLUE_WHALE", "LEGENDARY"}, {"TIGER", "RARE"}, {"WOLF", "EPIC"},
                {"GRIFFIN", "MYTHIC"}, {"PHOENIX", "LEGENDARY"}, {"BAT", "COMMON"},
        };
        for (int i = 0; i < petData.length; i++) {
            JsonObject pet = new JsonObject();
            pet.addProperty("type", petData[i][0]);
            pet.addProperty("tier", petData[i][1]);
            pet.addProperty("exp", 25_000_000.0 / (i + 1));
            pet.addProperty("active", i == 0);
            pets.add(pet);
        }
        JsonObject petsData = new JsonObject();
        petsData.add("pets", pets);
        member.add("pets_data", petsData);

        // containers, encoded exactly like hypixel does it
        JsonObject inventory = new JsonObject();
        inventory.add("inv_contents", container(encode(new String[]{
                "HYPERION", "NECRON_HANDLE", "TERMINATOR", "JUJU_SHORTBOW", "GIANTS_SWORD",
                "ASPECT_OF_THE_VOID", "SPIRIT_SCEPTRE", "MIDAS_STAFF", "BONE_BOOMERANG",
                "SUPERBOOM_TNT", "SPIRIT_LEAP", "INFINITE_SUPERBOOM_TNT", "DIAMOND_PICKAXE",
                "GOLDEN_PICKAXE", "TITANIUM_DRILL_DR_X355", "ROCK_GEMSTONE", "ENCHANTED_LAPIS_LAZULI",
                "TREECAPITATOR_AXE", null, null, null, null, "HOT_POTATO_BOOK", "RECOMBOBULATOR_3000",
                null, null, null, null, null, null, null, null, null, null, null, null,
        })));
        inventory.add("ender_chest_contents", container(encode(new String[]{
                "ENCHANTED_DIAMOND_BLOCK", "ENCHANTED_EMERALD_BLOCK", "ENCHANTED_GOLD_BLOCK",
                "SUPER_ENCHANTED_EGG", "ENCHANTED_CAKE", "GOLDEN_POWDER", "REVENANT_CATALYST",
                "NECRON_BLADE", "SHADOW_FURY", null, null, null, "ENCHANTED_BOOK", "WITHER_CATALYST",
        })));
        inventory.add("bag_contents", bagContents());
        inventory.add("wardrobe_contents", container(encode(new String[]{
                "NECRON_HELMET", "NECRON_CHESTPLATE", "NECRON_LEGGINGS", "NECRON_BOOTS",
                "STORM_HELMET", "STORM_CHESTPLATE", "STORM_LEGGINGS", "STORM_BOOTS",
        })));
        inventory.add("inv_armor", container(encode(new String[]{
                "NECRON_BOOTS", "NECRON_LEGGINGS", "NECRON_CHESTPLATE", "NECRON_HELMET",
        })));
        member.add("inventory", inventory);

        JsonObject accessoryStorage = new JsonObject();
        accessoryStorage.addProperty("highest_magical_power", 1432);
        member.add("accessory_bag_storage", accessoryStorage);
        member.addProperty("wardrobe_equipped_slot", 2);

        JsonObject leveling = new JsonObject();
        leveling.addProperty("experience", 38_420);
        member.add("leveling", leveling);

        JsonObject entry = new JsonObject();
        entry.addProperty("profile_id", "fake-profile");
        entry.addProperty("cute_name", "Mango");
        entry.addProperty("current", true);
        entry.add("data", member);
        entry.add("raw", member);

        JsonObject second = entry.deepCopy();
        second.addProperty("cute_name", "Papaya");
        second.addProperty("current", false);

        JsonObject profiles = new JsonObject();
        profiles.add("Mango", entry);
        profiles.add("Papaya", second);

        JsonObject result = new JsonObject();
        result.add("profiles", profiles);
        return result;
    }
}
