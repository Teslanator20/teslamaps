/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file is ported from OdinClient/OdinLegacy's WorldScanner.kt
 * (https://github.com/odtheking/OdinLegacy, BSD 3-Clause, (c) 2023-2025
 * odtheking). Modified by Teslanator20 2026. See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.TabListUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Crystal Hollows structure scanner (Golden Dragon nest, crystals, mob spots, grottos).
// Block sequences and quarter bounds follow Odin's WorldScanner.
public class WorldScanner {

    private static final int GOLD = 0xDCFFAA00, GREEN = 0xDC55FF55, AQUA = 0xDC55FFFF,
            DARK_PURPLE = 0xDCAA00AA, WHITE = 0xDCFFFFFF, LIGHT_PURPLE = 0xDCFF55FF;

    private enum Quarter { NUCLEUS, JUNGLE, PRECURSOR, GOBLIN, MITHRIL, MAGMA, ANY }

    private static boolean inQuarter(Quarter q, int x, int y, int z) {
        return switch (q) {
            case NUCLEUS   -> x >= 449 && x <= 576 && z >= 449 && z <= 576;
            case JUNGLE    -> x <= 576 && z <= 576;
            case PRECURSOR -> x > 448 && z > 448;
            case GOBLIN    -> x <= 576 && z > 448;
            case MITHRIL   -> x > 448 && z <= 576;
            case MAGMA     -> y < 80;
            case ANY       -> true;
        };
    }

    // blocks = vertical sequence upward from the anchor block, null = wildcard
    // category: 0=crystal, 1=mob spot, 2=grotto, 3=dragon, 4=worm
    private enum Structure {
        KING("King",
                new Block[]{Blocks.RED_WOOL, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_STAIRS},
                GOLD, Quarter.GOBLIN, new BlockPos(1, -1, 2), 0),
        QUEEN("Queen",
                new Block[]{Blocks.STONE, Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD, Blocks.CAULDRON},
                GOLD, Quarter.ANY, BlockPos.ZERO, 0),
        DIVAN("Divan",
                new Block[]{Blocks.QUARTZ_PILLAR, Blocks.QUARTZ_STAIRS, Blocks.STONE_BRICK_STAIRS, Blocks.CHISELED_STONE_BRICKS},
                GREEN, Quarter.MITHRIL, BlockPos.ZERO, 0),
        CITY("City",
                new Block[]{Blocks.STONE_BRICKS, Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.COBBLESTONE_STAIRS, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.DARK_OAK_STAIRS},
                AQUA, Quarter.PRECURSOR, new BlockPos(24, 0, -17), 0),
        TEMPLE("Temple",
                new Block[]{Blocks.BEDROCK, Blocks.BEDROCK, Blocks.BEDROCK, Blocks.BEDROCK, Blocks.STONE, Blocks.CLAY, Blocks.CLAY, Blocks.CLAY, Blocks.OAK_LEAVES, Blocks.OAK_LEAVES, Blocks.LIME_TERRACOTTA, Blocks.LIME_TERRACOTTA, Blocks.GREEN_TERRACOTTA},
                DARK_PURPLE, Quarter.ANY, new BlockPos(-45, 47, -18), 0),
        BAL("Bal",
                new Block[]{Blocks.LAVA, Blocks.BARRIER, Blocks.BARRIER, Blocks.BARRIER, Blocks.BARRIER, Blocks.BARRIER, Blocks.BARRIER, Blocks.BARRIER, Blocks.BARRIER, Blocks.BARRIER, Blocks.BARRIER},
                GOLD, Quarter.MAGMA, new BlockPos(0, 1, 0), 0),
        CORLEONE_DOCK("Corleone Dock",
                new Block[]{Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS,
                        null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null,
                        Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.FIRE, Blocks.STONE_BRICKS},
                GREEN, Quarter.MITHRIL, new BlockPos(23, 11, 17), 1),
        CORLEONE_HOLE("Corleone Hole",
                new Block[]{Blocks.SMOOTH_STONE_SLAB, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS, Blocks.POLISHED_GRANITE},
                GREEN, Quarter.MITHRIL, new BlockPos(-18, -1, 29), 1),
        KEY_GUARDIAN_SPIRAL("Key Guardian Spiral",
                new Block[]{Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_PLANKS, Blocks.GLOWSTONE},
                DARK_PURPLE, Quarter.JUNGLE, BlockPos.ZERO, 1),
        KEY_GUARDIAN_TOWER("Key Guardian Tower",
                new Block[]{Blocks.STONE, Blocks.POLISHED_GRANITE, Blocks.JUNGLE_SLAB},
                DARK_PURPLE, Quarter.JUNGLE, BlockPos.ZERO, 1),
        XALX("Xalx",
                new Block[]{Blocks.STONE, Blocks.COAL_BLOCK, Blocks.FIRE, Blocks.NETHER_QUARTZ_ORE, null, null, null, null, null, null, null},
                GREEN, Quarter.GOBLIN, new BlockPos(-2, 1, -2), 1),
        PETE("Pete",
                new Block[]{Blocks.NETHERRACK, Blocks.FIRE, Blocks.IRON_BARS, null, null, null, null, null, null, null},
                GOLD, Quarter.GOBLIN, BlockPos.ZERO, 1),
        ODAWA("Odawa",
                new Block[]{Blocks.JUNGLE_LOG, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.JUNGLE_LOG},
                GREEN, Quarter.JUNGLE, BlockPos.ZERO, 1),
        GOLDEN_DRAGON("Golden Dragon",
                new Block[]{Blocks.STONE, Blocks.RED_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.PLAYER_HEAD, Blocks.RED_WOOL},
                WHITE, Quarter.ANY, new BlockPos(0, -3, 5), 3);

        final String displayName;
        final Block[] blocks;
        final int color;
        final Quarter quarter;
        final BlockPos offset;
        final int category;

        Structure(String displayName, Block[] blocks, int color, Quarter quarter, BlockPos offset, int category) {
            this.displayName = displayName;
            this.blocks = blocks;
            this.color = color;
            this.quarter = quarter;
            this.offset = offset;
            this.category = category;
        }
    }

    private record WaypointData(BlockPos pos, int color, int category) {}

    private static final Map<String, WaypointData> waypoints = new ConcurrentHashMap<>();
    private static final Set<Long> scannedChunks = ConcurrentHashMap.newKeySet();
    private static boolean inHollows = false;
    private static int areaCheckTicks = 20;
    private static Object lastLevel = null;

    public static void init() {
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            if (!enabled() || !inHollows) return;
            if (scannedChunks.add(chunk.getPos().pack())) scanChunk(chunk);
        });
    }

    private static boolean enabled() {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        return cfg.chScanner && cfg.section("Mining");
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) { lastLevel = mc.level; clear(); }
        if (!TeslaMapsConfig.get().chScanner) {
            if (!waypoints.isEmpty() || !scannedChunks.isEmpty()) clear();
            return;
        }
        if (++areaCheckTicks >= 20) {
            areaCheckTicks = 0;
            boolean now = checkHollows();
            if (inHollows && !now) clear();
            inHollows = now;
        }
        // catch-up for chunks that loaded before the area was known (world join burst)
        if (inHollows && mc.player != null && mc.level != null) {
            int pcx = mc.player.chunkPosition().x(), pcz = mc.player.chunkPosition().z();
            int r = Math.min(mc.options.renderDistance().get(), 12);
            int budget = 2;
            for (int dx = -r; dx <= r && budget > 0; dx++) {
                for (int dz = -r; dz <= r && budget > 0; dz++) {
                    int cx = pcx + dx, cz = pcz + dz;
                    if (scannedChunks.contains(net.minecraft.world.level.ChunkPos.pack(cx, cz))) continue;
                    LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (chunk == null) continue;
                    scannedChunks.add(net.minecraft.world.level.ChunkPos.pack(cx, cz));
                    scanChunk(chunk);
                    budget--;
                }
            }
        }
    }

    private static boolean checkHollows() {
        for (String line : TabListUtils.getTabListLines()) {
            if (line == null) continue;
            String clean = line.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
            int i = clean.indexOf("Area:");
            if (i >= 0) return clean.substring(i + 5).trim().startsWith("Crystal Hollows");
        }
        return false;
    }

    private static void clear() {
        waypoints.clear();
        scannedChunks.clear();
        inHollows = false;
        areaCheckTicks = 20;
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().chScanner || !inHollows || waypoints.isEmpty()) return;
        boolean tracers = TeslaMapsConfig.get().chScanTracers;
        for (Map.Entry<String, WaypointData> e : waypoints.entrySet()) {
            WaypointData w = e.getValue();
            if (!categoryEnabled(w.category())) continue;
            BlockPos p = w.pos();
            AABB box = new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1);
            ESPRenderer.drawBoxOutline(matrices, box, w.color(), 1.5f, cameraPos);
            Vec3 center = new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            int dist = (int) cameraPos.distanceTo(center);
            // scale with distance so labels stay readable across the map
            float txtScale = Math.max(4f, dist / 10f);
            ESPRenderer.drawText(matrices, "§f" + e.getKey() + " §7" + dist + "m",
                    new Vec3(center.x, p.getY() + 1.5 + txtScale * 0.15, center.z), txtScale, cameraPos);
            if (tracers) ESPRenderer.drawTracerFromCamera(matrices, center, w.color(), cameraPos);
        }
    }

    private static boolean categoryEnabled(int category) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        return switch (category) {
            case 0 -> cfg.chScanCrystals;
            case 1 -> cfg.chScanMobSpots;
            case 2 -> cfg.chScanGrottos;
            case 3 -> cfg.chScanDragon;
            case 4 -> cfg.chScanWorm;
            default -> false;
        };
    }

    private static void scanChunk(LevelChunk chunk) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        int chunkX = chunk.getPos().x(), chunkZ = chunk.getPos().z();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int worldX = chunkX * 16 + lx;
                int worldZ = chunkZ * 16 + lz;

                for (int y = 0; y <= 170; y++) {
                    BlockState state = blockAt(chunk, lx, y, lz);
                    if (state.isAir()) continue;
                    Block block = state.getBlock();

                    if (block == Blocks.RED_WOOL) {
                        tryAdd(cfg.chScanCrystals, Structure.KING, chunk, lx, y, lz, worldX, worldZ);
                    } else if (block == Blocks.STONE) {
                        tryAdd(cfg.chScanCrystals, Structure.QUEEN, chunk, lx, y, lz, worldX, worldZ);
                        tryAdd(cfg.chScanMobSpots, Structure.KEY_GUARDIAN_TOWER, chunk, lx, y, lz, worldX, worldZ);
                        tryAdd(cfg.chScanMobSpots, Structure.XALX, chunk, lx, y, lz, worldX, worldZ);
                        tryAdd(cfg.chScanDragon, Structure.GOLDEN_DRAGON, chunk, lx, y, lz, worldX, worldZ);
                    } else if (block == Blocks.QUARTZ_PILLAR) {
                        tryAdd(cfg.chScanCrystals, Structure.DIVAN, chunk, lx, y, lz, worldX, worldZ);
                    } else if (block == Blocks.STONE_BRICKS) {
                        tryAdd(cfg.chScanCrystals, Structure.CITY, chunk, lx, y, lz, worldX, worldZ);
                        tryAdd(cfg.chScanMobSpots, Structure.CORLEONE_DOCK, chunk, lx, y, lz, worldX, worldZ);
                    } else if (block == Blocks.BEDROCK) {
                        tryAdd(cfg.chScanCrystals, Structure.TEMPLE, chunk, lx, y, lz, worldX, worldZ);
                    } else if (block == Blocks.LAVA) {
                        tryAdd(cfg.chScanCrystals, Structure.BAL, chunk, lx, y, lz, worldX, worldZ);
                        if (cfg.chScanWorm && !waypoints.containsKey("Worm Fishing")
                                && y > 63 && blockAt(chunk, lx, y + 1, lz).isAir()
                                && ((worldX >= 564 && worldZ >= 513) || (worldX >= 513 && worldZ >= 564))) {
                            addWaypoint("Worm Fishing", new BlockPos(worldX, y, worldZ), GOLD, 4);
                        }
                    } else if (block == Blocks.SMOOTH_STONE_SLAB) {
                        tryAdd(cfg.chScanMobSpots, Structure.CORLEONE_HOLE, chunk, lx, y, lz, worldX, worldZ);
                    } else if (block == Blocks.JUNGLE_STAIRS) {
                        tryAdd(cfg.chScanMobSpots, Structure.KEY_GUARDIAN_SPIRAL, chunk, lx, y, lz, worldX, worldZ);
                    } else if (block == Blocks.NETHERRACK) {
                        tryAdd(cfg.chScanMobSpots, Structure.PETE, chunk, lx, y, lz, worldX, worldZ);
                    } else if (block == Blocks.JUNGLE_LOG) {
                        tryAdd(cfg.chScanMobSpots, Structure.ODAWA, chunk, lx, y, lz, worldX, worldZ);
                    } else if ((block == Blocks.MAGENTA_STAINED_GLASS || block == Blocks.MAGENTA_STAINED_GLASS_PANE)
                            && cfg.chScanGrottos && !waypoints.containsKey("Fairy Grotto")
                            && !inQuarter(Quarter.NUCLEUS, worldX, y, worldZ)) {
                        addWaypoint("Fairy Grotto", new BlockPos(worldX, y, worldZ), LIGHT_PURPLE, 2);
                    }
                }
            }
        }
    }

    private static void tryAdd(boolean toggle, Structure s, LevelChunk chunk, int lx, int y, int lz, int worldX, int worldZ) {
        if (!toggle || waypoints.containsKey(s.displayName)) return;
        if (!inQuarter(s.quarter, worldX, y, worldZ)) return;
        if (!checkSeq(chunk, lx, y, lz, s.blocks)) return;
        addWaypoint(s.displayName, new BlockPos(worldX, y, worldZ).offset(s.offset), s.color, s.category);
    }

    // direct section access, avoids BlockPos allocation on the hot path
    private static BlockState blockAt(LevelChunk chunk, int lx, int absY, int lz) {
        int sectionIdx = chunk.getSectionIndex(absY);
        LevelChunkSection[] sections = chunk.getSections();
        if (sectionIdx < 0 || sectionIdx >= sections.length) return Blocks.AIR.defaultBlockState();
        LevelChunkSection section = sections[sectionIdx];
        if (section.hasOnlyAir()) return Blocks.AIR.defaultBlockState();
        return section.getBlockState(lx & 15, absY & 15, lz & 15);
    }

    private static boolean checkSeq(LevelChunk chunk, int lx, int startY, int lz, Block[] seq) {
        for (int i = 0; i < seq.length; i++) {
            if (seq[i] == null) continue;
            if (blockAt(chunk, lx, startY + i, lz).getBlock() != seq[i]) return false;
        }
        return true;
    }

    private static void addWaypoint(String name, BlockPos pos, int color, int category) {
        if (waypoints.putIfAbsent(name, new WaypointData(pos.immutable(), color, category)) != null) return;
        if (!TeslaMapsConfig.get().chScanChat) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal(
                    "§6[TeslaMaps] §f" + name + " §7found at §f" + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
        });
    }
}
