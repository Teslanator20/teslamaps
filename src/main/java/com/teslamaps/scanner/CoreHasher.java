package com.teslamaps.scanner;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Calculates core hash signatures for room identification.
 * This exactly matches IllegalMap's getCore() algorithm.
 *
 * IMPORTANT: We must use 1.8.9 block IDs because rooms.json was generated with those IDs.
 */
public class CoreHasher {
    // Blocks to treat as air (ID 0) when calculating hash
    // 101 = Iron Bars, 54 = Chest (1.8.9 IDs)
    private static final Set<String> BLACKLISTED_BLOCKS = Set.of(
            "minecraft:iron_bars",
            "minecraft:chest",
            "minecraft:trapped_chest"
    );

    // Mapping from modern block identifiers to 1.8.9 numeric block IDs
    // This is required because rooms.json cores were generated with 1.8.9 IDs
    private static final Map<String, Integer> LEGACY_BLOCK_IDS = new HashMap<>();

    static {
        // Basic blocks
        LEGACY_BLOCK_IDS.put("minecraft:air", 0);
        LEGACY_BLOCK_IDS.put("minecraft:stone", 1);
        LEGACY_BLOCK_IDS.put("minecraft:grass_block", 2);
        LEGACY_BLOCK_IDS.put("minecraft:dirt", 3);
        LEGACY_BLOCK_IDS.put("minecraft:cobblestone", 4);
        LEGACY_BLOCK_IDS.put("minecraft:oak_planks", 5);
        LEGACY_BLOCK_IDS.put("minecraft:spruce_planks", 5);
        LEGACY_BLOCK_IDS.put("minecraft:birch_planks", 5);
        LEGACY_BLOCK_IDS.put("minecraft:jungle_planks", 5);
        LEGACY_BLOCK_IDS.put("minecraft:acacia_planks", 5);
        LEGACY_BLOCK_IDS.put("minecraft:dark_oak_planks", 5);
        LEGACY_BLOCK_IDS.put("minecraft:bedrock", 7);
        LEGACY_BLOCK_IDS.put("minecraft:water", 9);
        LEGACY_BLOCK_IDS.put("minecraft:lava", 11);
        LEGACY_BLOCK_IDS.put("minecraft:sand", 12);
        LEGACY_BLOCK_IDS.put("minecraft:gravel", 13);
        LEGACY_BLOCK_IDS.put("minecraft:gold_ore", 14);
        LEGACY_BLOCK_IDS.put("minecraft:iron_ore", 15);
        LEGACY_BLOCK_IDS.put("minecraft:coal_ore", 16);
        LEGACY_BLOCK_IDS.put("minecraft:oak_log", 17);
        LEGACY_BLOCK_IDS.put("minecraft:spruce_log", 17);
        LEGACY_BLOCK_IDS.put("minecraft:birch_log", 17);
        LEGACY_BLOCK_IDS.put("minecraft:jungle_log", 17);
        LEGACY_BLOCK_IDS.put("minecraft:oak_leaves", 18);
        LEGACY_BLOCK_IDS.put("minecraft:spruce_leaves", 18);
        LEGACY_BLOCK_IDS.put("minecraft:birch_leaves", 18);
        LEGACY_BLOCK_IDS.put("minecraft:jungle_leaves", 18);
        LEGACY_BLOCK_IDS.put("minecraft:sponge", 19);
        LEGACY_BLOCK_IDS.put("minecraft:glass", 20);
        LEGACY_BLOCK_IDS.put("minecraft:lapis_ore", 21);
        LEGACY_BLOCK_IDS.put("minecraft:lapis_block", 22);
        LEGACY_BLOCK_IDS.put("minecraft:dispenser", 23);
        LEGACY_BLOCK_IDS.put("minecraft:sandstone", 24);
        LEGACY_BLOCK_IDS.put("minecraft:note_block", 25);
        LEGACY_BLOCK_IDS.put("minecraft:sticky_piston", 29);
        LEGACY_BLOCK_IDS.put("minecraft:cobweb", 30);
        LEGACY_BLOCK_IDS.put("minecraft:piston", 33);
        LEGACY_BLOCK_IDS.put("minecraft:piston_head", 34);
        LEGACY_BLOCK_IDS.put("minecraft:white_wool", 35);
        LEGACY_BLOCK_IDS.put("minecraft:orange_wool", 35);
        LEGACY_BLOCK_IDS.put("minecraft:magenta_wool", 35);
        LEGACY_BLOCK_IDS.put("minecraft:light_blue_wool", 35);
        LEGACY_BLOCK_IDS.put("minecraft:yellow_wool", 35);
        LEGACY_BLOCK_IDS.put("minecraft:lime_wool", 35);
        LEGACY_BLOCK_IDS.put("minecraft:pink_wool", 35);
        LEGACY_BLOCK_IDS.put("minecraft:gray_wool", 35);
        LEGACY_BLOCK_IDS.put("minecraft:light_gray_wool", 35);
        LEGACY_BLOCK_IDS.put("minecraft:cyan_wool", 35);
        LEGACY_BLOCK_IDS.put("minecraft:purple_wool", 35);
        LEGACY_BLOCK_IDS.put("minecraft:blue_wool", 35);
        LEGACY_BLOCK_IDS.put("minecraft:brown_wool", 35);
        LEGACY_BLOCK_IDS.put("minecraft:green_wool", 35);
        LEGACY_BLOCK_IDS.put("minecraft:red_wool", 35);
        LEGACY_BLOCK_IDS.put("minecraft:black_wool", 35);
        LEGACY_BLOCK_IDS.put("minecraft:gold_block", 41);
        LEGACY_BLOCK_IDS.put("minecraft:iron_block", 42);
        LEGACY_BLOCK_IDS.put("minecraft:smooth_stone_slab", 44);
        LEGACY_BLOCK_IDS.put("minecraft:stone_slab", 44);
        LEGACY_BLOCK_IDS.put("minecraft:sandstone_slab", 44);
        LEGACY_BLOCK_IDS.put("minecraft:cobblestone_slab", 44);
        LEGACY_BLOCK_IDS.put("minecraft:brick_slab", 44);
        LEGACY_BLOCK_IDS.put("minecraft:stone_brick_slab", 44);
        LEGACY_BLOCK_IDS.put("minecraft:bricks", 45);
        LEGACY_BLOCK_IDS.put("minecraft:tnt", 46);
        LEGACY_BLOCK_IDS.put("minecraft:bookshelf", 47);
        LEGACY_BLOCK_IDS.put("minecraft:mossy_cobblestone", 48);
        LEGACY_BLOCK_IDS.put("minecraft:obsidian", 49);
        LEGACY_BLOCK_IDS.put("minecraft:torch", 50);
        LEGACY_BLOCK_IDS.put("minecraft:wall_torch", 50);
        LEGACY_BLOCK_IDS.put("minecraft:fire", 51);
        LEGACY_BLOCK_IDS.put("minecraft:spawner", 52);
        LEGACY_BLOCK_IDS.put("minecraft:oak_stairs", 53);
        LEGACY_BLOCK_IDS.put("minecraft:chest", 54);
        LEGACY_BLOCK_IDS.put("minecraft:diamond_ore", 56);
        LEGACY_BLOCK_IDS.put("minecraft:diamond_block", 57);
        LEGACY_BLOCK_IDS.put("minecraft:crafting_table", 58);
        LEGACY_BLOCK_IDS.put("minecraft:farmland", 60);
        LEGACY_BLOCK_IDS.put("minecraft:furnace", 61);
        LEGACY_BLOCK_IDS.put("minecraft:ladder", 65);
        LEGACY_BLOCK_IDS.put("minecraft:rail", 66);
        LEGACY_BLOCK_IDS.put("minecraft:cobblestone_stairs", 67);
        LEGACY_BLOCK_IDS.put("minecraft:lever", 69);
        LEGACY_BLOCK_IDS.put("minecraft:stone_pressure_plate", 70);
        LEGACY_BLOCK_IDS.put("minecraft:redstone_ore", 73);
        LEGACY_BLOCK_IDS.put("minecraft:redstone_torch", 76);
        LEGACY_BLOCK_IDS.put("minecraft:redstone_wall_torch", 76);
        LEGACY_BLOCK_IDS.put("minecraft:stone_button", 77);
        LEGACY_BLOCK_IDS.put("minecraft:snow", 78);
        LEGACY_BLOCK_IDS.put("minecraft:ice", 79);
        LEGACY_BLOCK_IDS.put("minecraft:snow_block", 80);
        LEGACY_BLOCK_IDS.put("minecraft:cactus", 81);
        LEGACY_BLOCK_IDS.put("minecraft:clay", 82);
        LEGACY_BLOCK_IDS.put("minecraft:jukebox", 84);
        LEGACY_BLOCK_IDS.put("minecraft:oak_fence", 85);
        LEGACY_BLOCK_IDS.put("minecraft:pumpkin", 86);
        LEGACY_BLOCK_IDS.put("minecraft:netherrack", 87);
        LEGACY_BLOCK_IDS.put("minecraft:soul_sand", 88);
        LEGACY_BLOCK_IDS.put("minecraft:glowstone", 89);
        LEGACY_BLOCK_IDS.put("minecraft:jack_o_lantern", 91);
        LEGACY_BLOCK_IDS.put("minecraft:white_stained_glass", 95);
        LEGACY_BLOCK_IDS.put("minecraft:stone_bricks", 98);
        LEGACY_BLOCK_IDS.put("minecraft:mossy_stone_bricks", 98);
        LEGACY_BLOCK_IDS.put("minecraft:cracked_stone_bricks", 98);
        LEGACY_BLOCK_IDS.put("minecraft:chiseled_stone_bricks", 98);
        LEGACY_BLOCK_IDS.put("minecraft:infested_stone", 97);
        LEGACY_BLOCK_IDS.put("minecraft:infested_cobblestone", 97);
        LEGACY_BLOCK_IDS.put("minecraft:infested_stone_bricks", 97);
        LEGACY_BLOCK_IDS.put("minecraft:infested_mossy_stone_bricks", 97);
        LEGACY_BLOCK_IDS.put("minecraft:infested_cracked_stone_bricks", 97);
        LEGACY_BLOCK_IDS.put("minecraft:infested_chiseled_stone_bricks", 97);
        LEGACY_BLOCK_IDS.put("minecraft:brown_mushroom_block", 99);
        LEGACY_BLOCK_IDS.put("minecraft:red_mushroom_block", 100);
        LEGACY_BLOCK_IDS.put("minecraft:iron_bars", 101);
        LEGACY_BLOCK_IDS.put("minecraft:glass_pane", 102);
        LEGACY_BLOCK_IDS.put("minecraft:melon", 103);
        LEGACY_BLOCK_IDS.put("minecraft:vine", 106);
        LEGACY_BLOCK_IDS.put("minecraft:oak_fence_gate", 107);
        LEGACY_BLOCK_IDS.put("minecraft:brick_stairs", 108);
        LEGACY_BLOCK_IDS.put("minecraft:stone_brick_stairs", 109);
        LEGACY_BLOCK_IDS.put("minecraft:mycelium", 110);
        LEGACY_BLOCK_IDS.put("minecraft:lily_pad", 111);
        LEGACY_BLOCK_IDS.put("minecraft:nether_bricks", 112);
        LEGACY_BLOCK_IDS.put("minecraft:nether_brick_fence", 113);
        LEGACY_BLOCK_IDS.put("minecraft:nether_brick_stairs", 114);
        LEGACY_BLOCK_IDS.put("minecraft:enchanting_table", 116);
        LEGACY_BLOCK_IDS.put("minecraft:end_portal_frame", 120);
        LEGACY_BLOCK_IDS.put("minecraft:end_stone", 121);
        LEGACY_BLOCK_IDS.put("minecraft:redstone_lamp", 123);
        LEGACY_BLOCK_IDS.put("minecraft:sandstone_stairs", 128);
        LEGACY_BLOCK_IDS.put("minecraft:emerald_ore", 129);
        LEGACY_BLOCK_IDS.put("minecraft:ender_chest", 130);
        LEGACY_BLOCK_IDS.put("minecraft:tripwire_hook", 131);
        LEGACY_BLOCK_IDS.put("minecraft:tripwire", 132);
        LEGACY_BLOCK_IDS.put("minecraft:emerald_block", 133);
        LEGACY_BLOCK_IDS.put("minecraft:spruce_stairs", 134);
        LEGACY_BLOCK_IDS.put("minecraft:birch_stairs", 135);
        LEGACY_BLOCK_IDS.put("minecraft:jungle_stairs", 136);
        LEGACY_BLOCK_IDS.put("minecraft:cobblestone_wall", 139);
        LEGACY_BLOCK_IDS.put("minecraft:mossy_cobblestone_wall", 139);
        LEGACY_BLOCK_IDS.put("minecraft:trapped_chest", 146);
        LEGACY_BLOCK_IDS.put("minecraft:light_weighted_pressure_plate", 147);
        LEGACY_BLOCK_IDS.put("minecraft:heavy_weighted_pressure_plate", 148);
        LEGACY_BLOCK_IDS.put("minecraft:daylight_detector", 151);
        LEGACY_BLOCK_IDS.put("minecraft:redstone_block", 152);
        LEGACY_BLOCK_IDS.put("minecraft:nether_quartz_ore", 153);
        LEGACY_BLOCK_IDS.put("minecraft:hopper", 154);
        LEGACY_BLOCK_IDS.put("minecraft:quartz_block", 155);
        LEGACY_BLOCK_IDS.put("minecraft:chiseled_quartz_block", 155);
        LEGACY_BLOCK_IDS.put("minecraft:quartz_pillar", 155);
        LEGACY_BLOCK_IDS.put("minecraft:quartz_stairs", 156);
        LEGACY_BLOCK_IDS.put("minecraft:activator_rail", 157);
        LEGACY_BLOCK_IDS.put("minecraft:dropper", 158);
        // Terracotta (stained clay in 1.8.9)
        LEGACY_BLOCK_IDS.put("minecraft:white_terracotta", 159);
        LEGACY_BLOCK_IDS.put("minecraft:orange_terracotta", 159);
        LEGACY_BLOCK_IDS.put("minecraft:magenta_terracotta", 159);
        LEGACY_BLOCK_IDS.put("minecraft:light_blue_terracotta", 159);
        LEGACY_BLOCK_IDS.put("minecraft:yellow_terracotta", 159);
        LEGACY_BLOCK_IDS.put("minecraft:lime_terracotta", 159);
        LEGACY_BLOCK_IDS.put("minecraft:pink_terracotta", 159);
        LEGACY_BLOCK_IDS.put("minecraft:gray_terracotta", 159);
        LEGACY_BLOCK_IDS.put("minecraft:light_gray_terracotta", 159);
        LEGACY_BLOCK_IDS.put("minecraft:cyan_terracotta", 159);
        LEGACY_BLOCK_IDS.put("minecraft:purple_terracotta", 159);
        LEGACY_BLOCK_IDS.put("minecraft:blue_terracotta", 159);
        LEGACY_BLOCK_IDS.put("minecraft:brown_terracotta", 159);
        LEGACY_BLOCK_IDS.put("minecraft:green_terracotta", 159);
        LEGACY_BLOCK_IDS.put("minecraft:red_terracotta", 159);
        LEGACY_BLOCK_IDS.put("minecraft:black_terracotta", 159);
        LEGACY_BLOCK_IDS.put("minecraft:terracotta", 172);
        LEGACY_BLOCK_IDS.put("minecraft:coal_block", 173);
        LEGACY_BLOCK_IDS.put("minecraft:packed_ice", 174);
        LEGACY_BLOCK_IDS.put("minecraft:acacia_stairs", 163);
        LEGACY_BLOCK_IDS.put("minecraft:dark_oak_stairs", 164);
        LEGACY_BLOCK_IDS.put("minecraft:slime_block", 165);
        LEGACY_BLOCK_IDS.put("minecraft:barrier", 166);
        LEGACY_BLOCK_IDS.put("minecraft:iron_trapdoor", 167);
        LEGACY_BLOCK_IDS.put("minecraft:prismarine", 168);
        LEGACY_BLOCK_IDS.put("minecraft:prismarine_bricks", 168);
        LEGACY_BLOCK_IDS.put("minecraft:dark_prismarine", 168);
        LEGACY_BLOCK_IDS.put("minecraft:sea_lantern", 169);
        LEGACY_BLOCK_IDS.put("minecraft:hay_block", 170);
        // Carpet
        LEGACY_BLOCK_IDS.put("minecraft:white_carpet", 171);
        LEGACY_BLOCK_IDS.put("minecraft:orange_carpet", 171);
        LEGACY_BLOCK_IDS.put("minecraft:magenta_carpet", 171);
        LEGACY_BLOCK_IDS.put("minecraft:light_blue_carpet", 171);
        LEGACY_BLOCK_IDS.put("minecraft:yellow_carpet", 171);
        LEGACY_BLOCK_IDS.put("minecraft:lime_carpet", 171);
        LEGACY_BLOCK_IDS.put("minecraft:pink_carpet", 171);
        LEGACY_BLOCK_IDS.put("minecraft:gray_carpet", 171);
        LEGACY_BLOCK_IDS.put("minecraft:light_gray_carpet", 171);
        LEGACY_BLOCK_IDS.put("minecraft:cyan_carpet", 171);
        LEGACY_BLOCK_IDS.put("minecraft:purple_carpet", 171);
        LEGACY_BLOCK_IDS.put("minecraft:blue_carpet", 171);
        LEGACY_BLOCK_IDS.put("minecraft:brown_carpet", 171);
        LEGACY_BLOCK_IDS.put("minecraft:green_carpet", 171);
        LEGACY_BLOCK_IDS.put("minecraft:red_carpet", 171);
        LEGACY_BLOCK_IDS.put("minecraft:black_carpet", 171);
        // Stained glass
        LEGACY_BLOCK_IDS.put("minecraft:orange_stained_glass", 95);
        LEGACY_BLOCK_IDS.put("minecraft:magenta_stained_glass", 95);
        LEGACY_BLOCK_IDS.put("minecraft:light_blue_stained_glass", 95);
        LEGACY_BLOCK_IDS.put("minecraft:yellow_stained_glass", 95);
        LEGACY_BLOCK_IDS.put("minecraft:lime_stained_glass", 95);
        LEGACY_BLOCK_IDS.put("minecraft:pink_stained_glass", 95);
        LEGACY_BLOCK_IDS.put("minecraft:gray_stained_glass", 95);
        LEGACY_BLOCK_IDS.put("minecraft:light_gray_stained_glass", 95);
        LEGACY_BLOCK_IDS.put("minecraft:cyan_stained_glass", 95);
        LEGACY_BLOCK_IDS.put("minecraft:purple_stained_glass", 95);
        LEGACY_BLOCK_IDS.put("minecraft:blue_stained_glass", 95);
        LEGACY_BLOCK_IDS.put("minecraft:brown_stained_glass", 95);
        LEGACY_BLOCK_IDS.put("minecraft:green_stained_glass", 95);
        LEGACY_BLOCK_IDS.put("minecraft:red_stained_glass", 95);
        LEGACY_BLOCK_IDS.put("minecraft:black_stained_glass", 95);
        // Stained glass panes
        LEGACY_BLOCK_IDS.put("minecraft:white_stained_glass_pane", 160);
        LEGACY_BLOCK_IDS.put("minecraft:orange_stained_glass_pane", 160);
        LEGACY_BLOCK_IDS.put("minecraft:magenta_stained_glass_pane", 160);
        LEGACY_BLOCK_IDS.put("minecraft:light_blue_stained_glass_pane", 160);
        LEGACY_BLOCK_IDS.put("minecraft:yellow_stained_glass_pane", 160);
        LEGACY_BLOCK_IDS.put("minecraft:lime_stained_glass_pane", 160);
        LEGACY_BLOCK_IDS.put("minecraft:pink_stained_glass_pane", 160);
        LEGACY_BLOCK_IDS.put("minecraft:gray_stained_glass_pane", 160);
        LEGACY_BLOCK_IDS.put("minecraft:light_gray_stained_glass_pane", 160);
        LEGACY_BLOCK_IDS.put("minecraft:cyan_stained_glass_pane", 160);
        LEGACY_BLOCK_IDS.put("minecraft:purple_stained_glass_pane", 160);
        LEGACY_BLOCK_IDS.put("minecraft:blue_stained_glass_pane", 160);
        LEGACY_BLOCK_IDS.put("minecraft:brown_stained_glass_pane", 160);
        LEGACY_BLOCK_IDS.put("minecraft:green_stained_glass_pane", 160);
        LEGACY_BLOCK_IDS.put("minecraft:red_stained_glass_pane", 160);
        LEGACY_BLOCK_IDS.put("minecraft:black_stained_glass_pane", 160);
        // Flowers and plants
        LEGACY_BLOCK_IDS.put("minecraft:dandelion", 37);
        LEGACY_BLOCK_IDS.put("minecraft:poppy", 38);
        LEGACY_BLOCK_IDS.put("minecraft:blue_orchid", 38);
        LEGACY_BLOCK_IDS.put("minecraft:allium", 38);
        LEGACY_BLOCK_IDS.put("minecraft:azure_bluet", 38);
        LEGACY_BLOCK_IDS.put("minecraft:red_tulip", 38);
        LEGACY_BLOCK_IDS.put("minecraft:orange_tulip", 38);
        LEGACY_BLOCK_IDS.put("minecraft:white_tulip", 38);
        LEGACY_BLOCK_IDS.put("minecraft:pink_tulip", 38);
        LEGACY_BLOCK_IDS.put("minecraft:oxeye_daisy", 38);
        LEGACY_BLOCK_IDS.put("minecraft:brown_mushroom", 39);
        LEGACY_BLOCK_IDS.put("minecraft:red_mushroom", 40);
        LEGACY_BLOCK_IDS.put("minecraft:short_grass", 31);
        LEGACY_BLOCK_IDS.put("minecraft:tall_grass", 31);
        LEGACY_BLOCK_IDS.put("minecraft:fern", 31);
        LEGACY_BLOCK_IDS.put("minecraft:dead_bush", 32);
        LEGACY_BLOCK_IDS.put("minecraft:seagrass", 31);
        LEGACY_BLOCK_IDS.put("minecraft:tall_seagrass", 31);
        // Doors and trapdoors
        LEGACY_BLOCK_IDS.put("minecraft:oak_door", 64);
        LEGACY_BLOCK_IDS.put("minecraft:spruce_door", 193);
        LEGACY_BLOCK_IDS.put("minecraft:birch_door", 194);
        LEGACY_BLOCK_IDS.put("minecraft:jungle_door", 195);
        LEGACY_BLOCK_IDS.put("minecraft:acacia_door", 196);
        LEGACY_BLOCK_IDS.put("minecraft:dark_oak_door", 197);
        LEGACY_BLOCK_IDS.put("minecraft:iron_door", 71);
        LEGACY_BLOCK_IDS.put("minecraft:oak_trapdoor", 96);
        // Skulls
        LEGACY_BLOCK_IDS.put("minecraft:skeleton_skull", 144);
        LEGACY_BLOCK_IDS.put("minecraft:skeleton_wall_skull", 144);
        LEGACY_BLOCK_IDS.put("minecraft:wither_skeleton_skull", 144);
        LEGACY_BLOCK_IDS.put("minecraft:wither_skeleton_wall_skull", 144);
        LEGACY_BLOCK_IDS.put("minecraft:zombie_head", 144);
        LEGACY_BLOCK_IDS.put("minecraft:zombie_wall_head", 144);
        LEGACY_BLOCK_IDS.put("minecraft:player_head", 144);
        LEGACY_BLOCK_IDS.put("minecraft:player_wall_head", 144);
        LEGACY_BLOCK_IDS.put("minecraft:creeper_head", 144);
        LEGACY_BLOCK_IDS.put("minecraft:creeper_wall_head", 144);
        // Anvils
        LEGACY_BLOCK_IDS.put("minecraft:anvil", 145);
        LEGACY_BLOCK_IDS.put("minecraft:chipped_anvil", 145);
        LEGACY_BLOCK_IDS.put("minecraft:damaged_anvil", 145);
        // Concrete and concrete powder (1.12+, but Hypixel has them)
        LEGACY_BLOCK_IDS.put("minecraft:white_concrete", 251);
        LEGACY_BLOCK_IDS.put("minecraft:orange_concrete", 251);
        LEGACY_BLOCK_IDS.put("minecraft:magenta_concrete", 251);
        LEGACY_BLOCK_IDS.put("minecraft:light_blue_concrete", 251);
        LEGACY_BLOCK_IDS.put("minecraft:yellow_concrete", 251);
        LEGACY_BLOCK_IDS.put("minecraft:lime_concrete", 251);
        LEGACY_BLOCK_IDS.put("minecraft:pink_concrete", 251);
        LEGACY_BLOCK_IDS.put("minecraft:gray_concrete", 251);
        LEGACY_BLOCK_IDS.put("minecraft:light_gray_concrete", 251);
        LEGACY_BLOCK_IDS.put("minecraft:cyan_concrete", 251);
        LEGACY_BLOCK_IDS.put("minecraft:purple_concrete", 251);
        LEGACY_BLOCK_IDS.put("minecraft:blue_concrete", 251);
        LEGACY_BLOCK_IDS.put("minecraft:brown_concrete", 251);
        LEGACY_BLOCK_IDS.put("minecraft:green_concrete", 251);
        LEGACY_BLOCK_IDS.put("minecraft:red_concrete", 251);
        LEGACY_BLOCK_IDS.put("minecraft:black_concrete", 251);
        // Signs
        LEGACY_BLOCK_IDS.put("minecraft:oak_sign", 63);
        LEGACY_BLOCK_IDS.put("minecraft:oak_wall_sign", 68);
        LEGACY_BLOCK_IDS.put("minecraft:spruce_sign", 63);
        LEGACY_BLOCK_IDS.put("minecraft:spruce_wall_sign", 68);
        // Beds
        LEGACY_BLOCK_IDS.put("minecraft:white_bed", 26);
        LEGACY_BLOCK_IDS.put("minecraft:orange_bed", 26);
        LEGACY_BLOCK_IDS.put("minecraft:magenta_bed", 26);
        LEGACY_BLOCK_IDS.put("minecraft:light_blue_bed", 26);
        LEGACY_BLOCK_IDS.put("minecraft:yellow_bed", 26);
        LEGACY_BLOCK_IDS.put("minecraft:lime_bed", 26);
        LEGACY_BLOCK_IDS.put("minecraft:pink_bed", 26);
        LEGACY_BLOCK_IDS.put("minecraft:gray_bed", 26);
        LEGACY_BLOCK_IDS.put("minecraft:light_gray_bed", 26);
        LEGACY_BLOCK_IDS.put("minecraft:cyan_bed", 26);
        LEGACY_BLOCK_IDS.put("minecraft:purple_bed", 26);
        LEGACY_BLOCK_IDS.put("minecraft:blue_bed", 26);
        LEGACY_BLOCK_IDS.put("minecraft:brown_bed", 26);
        LEGACY_BLOCK_IDS.put("minecraft:green_bed", 26);
        LEGACY_BLOCK_IDS.put("minecraft:red_bed", 26);
        LEGACY_BLOCK_IDS.put("minecraft:black_bed", 26);
        // Banners
        LEGACY_BLOCK_IDS.put("minecraft:white_banner", 176);
        LEGACY_BLOCK_IDS.put("minecraft:white_wall_banner", 177);
        // Pistons
        LEGACY_BLOCK_IDS.put("minecraft:moving_piston", 36);
        // End rod
        LEGACY_BLOCK_IDS.put("minecraft:end_rod", 198);
        // Chorus
        LEGACY_BLOCK_IDS.put("minecraft:chorus_plant", 199);
        LEGACY_BLOCK_IDS.put("minecraft:chorus_flower", 200);
        // Purpur
        LEGACY_BLOCK_IDS.put("minecraft:purpur_block", 201);
        LEGACY_BLOCK_IDS.put("minecraft:purpur_pillar", 202);
        LEGACY_BLOCK_IDS.put("minecraft:purpur_stairs", 203);
        LEGACY_BLOCK_IDS.put("minecraft:purpur_slab", 205);
        // End bricks
        LEGACY_BLOCK_IDS.put("minecraft:end_stone_bricks", 206);
        // Magma
        LEGACY_BLOCK_IDS.put("minecraft:magma_block", 213);
        // Nether wart block
        LEGACY_BLOCK_IDS.put("minecraft:nether_wart_block", 214);
        // Red nether bricks
        LEGACY_BLOCK_IDS.put("minecraft:red_nether_bricks", 215);
        // Bone block
        LEGACY_BLOCK_IDS.put("minecraft:bone_block", 216);
        // Observer
        LEGACY_BLOCK_IDS.put("minecraft:observer", 218);
        // Shulker boxes
        LEGACY_BLOCK_IDS.put("minecraft:white_shulker_box", 219);
        LEGACY_BLOCK_IDS.put("minecraft:orange_shulker_box", 220);
        LEGACY_BLOCK_IDS.put("minecraft:purple_shulker_box", 229);
        // Glazed terracotta
        LEGACY_BLOCK_IDS.put("minecraft:white_glazed_terracotta", 235);
        LEGACY_BLOCK_IDS.put("minecraft:orange_glazed_terracotta", 236);
        // Structure block
        LEGACY_BLOCK_IDS.put("minecraft:structure_block", 255);
        // Cave air and void air map to regular air
        LEGACY_BLOCK_IDS.put("minecraft:cave_air", 0);
        LEGACY_BLOCK_IDS.put("minecraft:void_air", 0);
        // Double slabs - important for dungeons
        LEGACY_BLOCK_IDS.put("minecraft:smooth_stone", 43);
        // Water variants
        LEGACY_BLOCK_IDS.put("minecraft:flowing_water", 8);
        LEGACY_BLOCK_IDS.put("minecraft:flowing_lava", 10);
        // Brewing stand
        LEGACY_BLOCK_IDS.put("minecraft:brewing_stand", 117);
        // Cauldron
        LEGACY_BLOCK_IDS.put("minecraft:cauldron", 118);
        LEGACY_BLOCK_IDS.put("minecraft:water_cauldron", 118);
        // Flower pot
        LEGACY_BLOCK_IDS.put("minecraft:flower_pot", 140);
        LEGACY_BLOCK_IDS.put("minecraft:potted_dandelion", 140);
        LEGACY_BLOCK_IDS.put("minecraft:potted_poppy", 140);
        LEGACY_BLOCK_IDS.put("minecraft:potted_oak_sapling", 140);
        LEGACY_BLOCK_IDS.put("minecraft:potted_spruce_sapling", 140);
        LEGACY_BLOCK_IDS.put("minecraft:potted_birch_sapling", 140);
        LEGACY_BLOCK_IDS.put("minecraft:potted_jungle_sapling", 140);
        LEGACY_BLOCK_IDS.put("minecraft:potted_red_mushroom", 140);
        LEGACY_BLOCK_IDS.put("minecraft:potted_brown_mushroom", 140);
        LEGACY_BLOCK_IDS.put("minecraft:potted_cactus", 140);
        LEGACY_BLOCK_IDS.put("minecraft:potted_dead_bush", 140);
        LEGACY_BLOCK_IDS.put("minecraft:potted_fern", 140);
        // Skull
        LEGACY_BLOCK_IDS.put("minecraft:dragon_head", 144);
        LEGACY_BLOCK_IDS.put("minecraft:dragon_wall_head", 144);
        // Redstone components
        LEGACY_BLOCK_IDS.put("minecraft:redstone_wire", 55);
        LEGACY_BLOCK_IDS.put("minecraft:repeater", 93);
        LEGACY_BLOCK_IDS.put("minecraft:comparator", 149);
        // Cake
        LEGACY_BLOCK_IDS.put("minecraft:cake", 92);
        // Saplings
        LEGACY_BLOCK_IDS.put("minecraft:oak_sapling", 6);
        LEGACY_BLOCK_IDS.put("minecraft:spruce_sapling", 6);
        LEGACY_BLOCK_IDS.put("minecraft:birch_sapling", 6);
        LEGACY_BLOCK_IDS.put("minecraft:jungle_sapling", 6);
        LEGACY_BLOCK_IDS.put("minecraft:acacia_sapling", 6);
        LEGACY_BLOCK_IDS.put("minecraft:dark_oak_sapling", 6);
        // Sugar cane, wheat, etc
        LEGACY_BLOCK_IDS.put("minecraft:sugar_cane", 83);
        LEGACY_BLOCK_IDS.put("minecraft:wheat", 59);
        LEGACY_BLOCK_IDS.put("minecraft:carrots", 141);
        LEGACY_BLOCK_IDS.put("minecraft:potatoes", 142);
        LEGACY_BLOCK_IDS.put("minecraft:nether_wart", 115);
        // End portal
        LEGACY_BLOCK_IDS.put("minecraft:end_portal", 119);
        // Dragon egg
        LEGACY_BLOCK_IDS.put("minecraft:dragon_egg", 122);
        // Command block
        LEGACY_BLOCK_IDS.put("minecraft:command_block", 137);
        LEGACY_BLOCK_IDS.put("minecraft:chain_command_block", 211);
        LEGACY_BLOCK_IDS.put("minecraft:repeating_command_block", 210);
        // Beacon
        LEGACY_BLOCK_IDS.put("minecraft:beacon", 138);
        // Acacia/dark oak logs
        LEGACY_BLOCK_IDS.put("minecraft:acacia_log", 162);
        LEGACY_BLOCK_IDS.put("minecraft:dark_oak_log", 162);
        // Acacia/dark oak leaves
        LEGACY_BLOCK_IDS.put("minecraft:acacia_leaves", 161);
        LEGACY_BLOCK_IDS.put("minecraft:dark_oak_leaves", 161);
        // Fences
        LEGACY_BLOCK_IDS.put("minecraft:spruce_fence", 188);
        LEGACY_BLOCK_IDS.put("minecraft:birch_fence", 189);
        LEGACY_BLOCK_IDS.put("minecraft:jungle_fence", 190);
        LEGACY_BLOCK_IDS.put("minecraft:dark_oak_fence", 191);
        LEGACY_BLOCK_IDS.put("minecraft:acacia_fence", 192);
        // Fence gates
        LEGACY_BLOCK_IDS.put("minecraft:spruce_fence_gate", 183);
        LEGACY_BLOCK_IDS.put("minecraft:birch_fence_gate", 184);
        LEGACY_BLOCK_IDS.put("minecraft:jungle_fence_gate", 185);
        LEGACY_BLOCK_IDS.put("minecraft:dark_oak_fence_gate", 186);
        LEGACY_BLOCK_IDS.put("minecraft:acacia_fence_gate", 187);
        // Buttons
        LEGACY_BLOCK_IDS.put("minecraft:oak_button", 143);
        // Pressure plates
        LEGACY_BLOCK_IDS.put("minecraft:oak_pressure_plate", 72);
        LEGACY_BLOCK_IDS.put("minecraft:spruce_pressure_plate", 72);
        LEGACY_BLOCK_IDS.put("minecraft:birch_pressure_plate", 72);
        LEGACY_BLOCK_IDS.put("minecraft:jungle_pressure_plate", 72);
        LEGACY_BLOCK_IDS.put("minecraft:acacia_pressure_plate", 72);
        LEGACY_BLOCK_IDS.put("minecraft:dark_oak_pressure_plate", 72);
        // Powered/detector rails
        LEGACY_BLOCK_IDS.put("minecraft:powered_rail", 27);
        LEGACY_BLOCK_IDS.put("minecraft:detector_rail", 28);
        // Podzol and coarse dirt
        LEGACY_BLOCK_IDS.put("minecraft:podzol", 3);
        LEGACY_BLOCK_IDS.put("minecraft:coarse_dirt", 3);
        // Granite, diorite, andesite
        LEGACY_BLOCK_IDS.put("minecraft:granite", 1);
        LEGACY_BLOCK_IDS.put("minecraft:polished_granite", 1);
        LEGACY_BLOCK_IDS.put("minecraft:diorite", 1);
        LEGACY_BLOCK_IDS.put("minecraft:polished_diorite", 1);
        LEGACY_BLOCK_IDS.put("minecraft:andesite", 1);
        LEGACY_BLOCK_IDS.put("minecraft:polished_andesite", 1);
    }

    /**
     * Get the 1.8.9 legacy block ID for a modern block.
     */
    private static int getLegacyBlockId(String blockName) {
        Integer id = LEGACY_BLOCK_IDS.get(blockName);
        if (id != null) {
            return id;
        }
        // Unknown block - return 0 (air) to avoid breaking hash
        return 0;
    }

    /**
     * Calculate the core hash for a room at the given center coordinates.
     * This matches IllegalMap's getCore() function exactly.
     *
     * @param world The world to scan
     * @param centerX X coordinate of room center
     * @param centerZ Z coordinate of room center
     * @return The core hash value
     */
    public static int calculateCore(World world, int centerX, int centerZ) {
        StringBuilder blockIds = new StringBuilder();

        // Scan from y=140 down to y=12, matching IllegalMap
        for (int y = 140; y >= 12; y--) {
            BlockPos pos = new BlockPos(centerX, y, centerZ);
            Block block = world.getBlockState(pos).getBlock();
            String blockName = Registries.BLOCK.getId(block).toString();

            // Blacklisted blocks count as air (0)
            if (BLACKLISTED_BLOCKS.contains(blockName)) {
                blockIds.append("0");
            } else {
                // Use legacy 1.8.9 block ID
                int legacyId = getLegacyBlockId(blockName);
                blockIds.append(legacyId);
            }
        }

        // Use Java's string hashCode, matching IllegalMap's hashCode function
        return hashCode(blockIds.toString());
    }

    /**
     * Java's String.hashCode() algorithm.
     * This matches: s.split('').reduce((a,b)=>{a=((a<<5)-a)+b.charCodeAt(0);return a&a},0)
     */
    private static int hashCode(String s) {
        int hash = 0;
        for (int i = 0; i < s.length(); i++) {
            hash = ((hash << 5) - hash) + s.charAt(i);
        }
        return hash;
    }

    /**
     * Check if a position is loaded and valid for scanning.
     */
    public static boolean isPositionLoaded(World world, int x, int z) {
        return world.isChunkLoaded(x >> 4, z >> 4);
    }

    /**
     * Scan and identify a room at the given grid position.
     */
    public static int scanRoomCore(int gridX, int gridZ) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return 0;

        // Get room center coordinates
        int[] center = ComponentGrid.gridToWorld(gridX, gridZ);
        int centerX = center[0];
        int centerZ = center[1];

        // Check if the chunk is loaded
        if (!isPositionLoaded(mc.world, centerX, centerZ)) {
            return 0;
        }

        return calculateCore(mc.world, centerX, centerZ);
    }
}
