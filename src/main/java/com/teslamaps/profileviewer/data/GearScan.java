package com.teslamaps.profileviewer.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Best mining / foraging gear across every container, ported from skyblock-pv's ProfileMapper. */
public class GearScan {

    public record Gear(List<InventoryDecoder.Slot> armor, List<InventoryDecoder.Slot> equipment,
                       List<InventoryDecoder.Slot> tools) {}

    private static final List<String> ARMOR_SLOTS = List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");

    private static final Set<String> MINING_TOOLS = Set.of(
            "TITANIUM_PICKAXE", "RUSTY_TITANIUM_PICKAXE", "JUNGLE_PICKAXE", "WOOD_PICKAXE", "LAPIS_PICKAXE",
            "BANDAGED_MITHRIL_PICKAXE", "MITHRIL_PICKAXE", "REFINED_MITHRIL_PICKAXE", "IRON_PICKAXE", "GOLD_PICKAXE",
            "ZOMBIE_PICKAXE", "ALPHA_PICK", "PROMISING_PICKAXE", "DIAMOND_PICKAXE", "PICKONIMBUS",
            "REFINED_TITANIUM_PICKAXE", "FRACTURED_MITHRIL_PICKAXE", "STONE_PICKAXE", "ROOKIE_PICKAXE",
            "MITHRIL_DRILL_1", "MITHRIL_DRILL_2", "TITANIUM_DRILL_1", "TITANIUM_DRILL_2", "TITANIUM_DRILL_3",
            "TITANIUM_DRILL_4", "GEMSTONE_DRILL_1", "GEMSTONE_DRILL_2", "GEMSTONE_DRILL_3", "GEMSTONE_DRILL_4",
            "DIVAN_DRILL", "GEMSTONE_GAUNTLET", "CHISEL", "REINFORCED_CHISEL", "GLACITE_CHISEL", "PERFECT_CHISEL");

    // worst to best, the index doubles as the score
    private static final List<String> MINING_ARMOR = List.of(
            "MINER_OUTFIT_HELMET", "MINER_OUTFIT_CHESTPLATE", "MINER_OUTFIT_LEGGINGS", "MINER_OUTFIT_BOOTS",
            "LAPIS_ARMOR_HELMET", "LAPIS_ARMOR_CHESTPLATE", "LAPIS_ARMOR_LEGGINGS", "LAPIS_ARMOR_BOOTS",
            "TANK_MINER_HELMET", "TANK_MINER_CHESTPLATE", "TANK_MINER_LEGGINGS", "TANK_MINER_BOOTS",
            "HARDENED_DIAMOND_HELMET", "HARDENED_DIAMOND_CHESTPLATE", "HARDENED_DIAMOND_LEGGINGS", "HARDENED_DIAMOND_BOOTS",
            "MINERAL_HELMET", "MINERAL_CHESTPLATE", "MINERAL_LEGGINGS", "MINERAL_BOOTS",
            "GLOSSY_MINERAL_HELMET", "GLOSSY_MINERAL_CHESTPLATE", "GLOSSY_MINERAL_LEGGINGS", "GLOSSY_MINERAL_BOOTS",
            "GOBLIN_HELMET", "GOBLIN_CHESTPLATE", "GOBLIN_LEGGINGS", "GOBLIN_BOOTS",
            "GLACITE_HELMET", "GLACITE_CHESTPLATE", "GLACITE_LEGGINGS", "GLACITE_BOOTS",
            "HEAT_HELMET", "HEAT_CHESTPLATE", "HEAT_LEGGINGS", "HEAT_BOOTS",
            "ARMOR_OF_YOG_HELMET", "ARMOR_OF_YOG_CHESTPLATE", "ARMOR_OF_YOG_LEGGINGS", "ARMOR_OF_YOG_BOOTS",
            "FLAME_BREAKER_HELMET", "FLAME_BREAKER_CHESTPLATE", "FLAME_BREAKER_LEGGINGS", "FLAME_BREAKER_BOOTS",
            "SORROW_HELMET", "SORROW_CHESTPLATE", "SORROW_LEGGINGS", "SORROW_BOOTS",
            "DIVAN_HELMET", "DIVAN_CHESTPLATE", "DIVAN_LEGGINGS", "DIVAN_BOOTS");

    // necklace, cloak, belt, gloves
    private static final List<List<String>> MINING_EQUIPMENT = List.of(
            List.of("MITHRIL_NECKLACE", "TITANIUM_NECKLACE", "AMBER_NECKLACE", "DIVAN_PENDANT"),
            List.of("ANCIENT_CLOAK", "MITHRIL_CLOAK", "TITANIUM_CLOAK", "SAPPHIRE_CLOAK"),
            List.of("MITHRIL_BELT", "TITANIUM_BELT", "JADE_BELT"),
            List.of("GLOWSTONE_GAUNTLET", "VANQUISHED_GLOWSTONE_GAUNTLET", "MITHRIL_GAUNTLET", "TITANIUM_GAUNTLET",
                    "AMETHYST_GAUNTLET", "DWARVEN_HANDWARMERS"));

    private static final List<String> FORAGING_ARMOR = List.of(
            "FIG_HELMET", "FIG_CHESTPLATE", "FIG_LEGGINGS", "FIG_BOOTS");
    private static final List<List<String>> FORAGING_EQUIPMENT = List.of(
            List.of("MANGROVE_LOCKET"), List.of("DAVIDS_CLOAK"), List.of(), List.of("MANGROVE_GRIPPERS"));
    private static final Set<String> FORAGING_TOOLS = Set.of(
            "JUNGLE_AXE", "EFFICIENT_AXE", "FIG_AXE", "FIGSTONE_AXE");

    private static JsonObject lastMember;
    private static Gear mining;
    private static Gear foraging;

    public static void clear() {
        lastMember = null;
        mining = null;
        foraging = null;
    }

    public static Gear mining(JsonObject member) {
        scan(member);
        return mining;
    }

    public static Gear foraging(JsonObject member) {
        scan(member);
        return foraging;
    }

    /** Decodes once per profile; the pages ask on every rebuild. */
    private static void scan(JsonObject member) {
        if (member == lastMember && mining != null) return;
        List<InventoryDecoder.Slot> items = items(member);
        mining = new Gear(bestArmor(items, MINING_ARMOR), bestEquipment(items, MINING_EQUIPMENT), tools(items, MINING_TOOLS));
        foraging = new Gear(bestArmor(items, FORAGING_ARMOR), bestEquipment(items, FORAGING_EQUIPMENT), tools(items, FORAGING_TOOLS));
        lastMember = member;
    }

    private static List<InventoryDecoder.Slot> items(JsonObject member) {
        List<InventoryDecoder.Slot> out = new ArrayList<>();
        JsonObject inv = member != null && member.has("inventory") && member.get("inventory").isJsonObject()
                ? member.getAsJsonObject("inventory") : null;
        if (inv == null) return out;
        for (String key : List.of("inv_contents", "inv_armor", "equipment_contents", "ender_chest_contents",
                "personal_vault_contents", "wardrobe_contents")) {
            out.addAll(InventoryDecoder.decode(inv, key));
        }
        if (inv.has("backpack_contents") && inv.get("backpack_contents").isJsonObject()) {
            JsonObject packs = inv.getAsJsonObject("backpack_contents");
            for (Map.Entry<String, JsonElement> pack : packs.entrySet()) {
                out.addAll(InventoryDecoder.decode(packs, pack.getKey()));
            }
        }
        out.removeIf(slot -> slot.isEmpty() || slot.skyblockId == null);
        return out;
    }

    private static List<InventoryDecoder.Slot> tools(List<InventoryDecoder.Slot> items, Set<String> ids) {
        List<InventoryDecoder.Slot> out = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (InventoryDecoder.Slot slot : items) {
            if (ids.contains(slot.skyblockId) && seen.add(slot.skyblockId)) out.add(slot);
        }
        return out;
    }

    private static List<InventoryDecoder.Slot> bestArmor(List<InventoryDecoder.Slot> items, List<String> armor) {
        List<InventoryDecoder.Slot> out = new ArrayList<>(4);
        for (String piece : ARMOR_SLOTS) {
            InventoryDecoder.Slot best = null;
            int score = -1;
            for (InventoryDecoder.Slot slot : items) {
                if (!slot.skyblockId.endsWith(piece)) continue;
                int index = armor.indexOf(slot.skyblockId);
                if (index > score) {
                    score = index;
                    best = slot;
                }
            }
            out.add(best);
        }
        return out;
    }

    private static List<InventoryDecoder.Slot> bestEquipment(List<InventoryDecoder.Slot> items, List<List<String>> slots) {
        List<InventoryDecoder.Slot> out = new ArrayList<>(slots.size());
        for (List<String> list : slots) {
            InventoryDecoder.Slot best = null;
            int score = -1;
            for (InventoryDecoder.Slot slot : items) {
                int index = list.indexOf(slot.skyblockId);
                if (index > score) {
                    score = index;
                    best = slot;
                }
            }
            out.add(best);
        }
        return out;
    }

    /** Armor, equipment and the first four tools as a 3x4 grid of slots. */
    public static com.teslamaps.profileviewer.ui.Ui grid(Gear gear, int slot) {
        List<com.teslamaps.profileviewer.ui.Ui> columns = new ArrayList<>(3);
        for (List<InventoryDecoder.Slot> list : List.of(gear.armor(), gear.equipment(), gear.tools())) {
            List<com.teslamaps.profileviewer.ui.Ui> cells = new ArrayList<>(4);
            for (int i = 0; i < 4; i++) {
                cells.add(com.teslamaps.profileviewer.ui.SlotIcon.of(i < list.size() ? list.get(i) : null, slot));
            }
            columns.add(com.teslamaps.profileviewer.ui.Ui.col(0, cells));
        }
        return com.teslamaps.profileviewer.ui.Ui.row(2, com.teslamaps.profileviewer.ui.Ui.V.TOP, columns);
    }
}
