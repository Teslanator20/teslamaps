package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.StringUtil;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Crystal Hollows structure scanner.
// Block sequences follow shizo's CH Scanner, extended with legitcatmod's longer columns.
// Quarter bounds follow legitcatmod, area/NPC verification follows Skyblocker's CrystalsLocationsManager.
public class WorldScanner {

    private static final int GOLD = 0xDCFFAA00, GREEN = 0xDC55FF55, AQUA = 0xDC55FFFF,
            DARK_PURPLE = 0xDCAA00AA, WHITE = 0xDCFFFFFF, LIGHT_PURPLE = 0xDCFF55FF;

    // Hypixel's CH bounds, x/z 202..823, y 31..188
    private static boolean inBounds(int x, int y, int z) {
        return x >= 202 && x <= 823 && z >= 202 && z <= 823 && y >= 31 && y <= 188;
    }

    // quarter bounds overlap by design - structures sit right on the seams
    private enum Quarter { NUCLEUS, JUNGLE, PRECURSOR, GOBLIN, MITHRIL, MAGMA, ANY }

    private static boolean inQuarter(Quarter q, int x, int y, int z) {
        return switch (q) {
            case NUCLEUS   -> x >= 463 && x <= 560 && z >= 460 && z <= 563 && y > 64;
            case JUNGLE    -> x <= 576 && z <= 576;
            case PRECURSOR -> x > 448 && z > 448;
            case GOBLIN    -> x <= 576 && z > 448;
            case MITHRIL   -> x > 448 && z <= 576;
            case MAGMA     -> y < 80;
            case ANY       -> true;
        };
    }

    // blocks = vertical sequence upward from the anchor block, null = wildcard
    // lift = y offset from the anchor to the useful height; x/z come from the match centroid,
    //        shizo's fixed x/z offsets only fit the one column its pattern was written for
    // minHits = matching columns needed before the waypoint shows. Only for short generic
    //           patterns - a long column like the dock has exactly ONE matching x/z, so
    //           anything above 1 there means the waypoint never appears at all.
    // category: 0=crystal, 1=mob spot, 2=grotto, 3=dragon
    private enum Structure {
        KING("King",
                new Block[]{Blocks.RED_WOOL, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_STAIRS},
                GOLD, Quarter.GOBLIN, -1, 1, 0, null, "[NPC] King Yolkar:"),
        QUEEN("Queen",
                new Block[]{Blocks.STONE, Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD, Blocks.CAULDRON},
                GOLD, Quarter.GOBLIN, 5, 1, 0, "Goblin Queen's Den", "Amber Crystal"),
        DIVAN("Divan",
                new Block[]{Blocks.QUARTZ_PILLAR, Blocks.QUARTZ_STAIRS, Blocks.STONE_BRICK_STAIRS, Blocks.CHISELED_STONE_BRICKS},
                GREEN, Quarter.MITHRIL, 5, 1, 0, "Mines of Divan", "Jade Crystal"),
        CITY("City",
                new Block[]{Blocks.STONE_BRICKS, Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.COBBLESTONE_STAIRS, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.DARK_OAK_STAIRS},
                AQUA, Quarter.PRECURSOR, 0, 1, 0, "Lost Precursor City", "Sapphire Crystal"),
        TEMPLE("Temple",
                new Block[]{Blocks.BEDROCK, Blocks.BEDROCK, Blocks.BEDROCK, Blocks.BEDROCK, Blocks.STONE, Blocks.CLAY, Blocks.CLAY, Blocks.CLAY, Blocks.OAK_LEAVES, Blocks.OAK_LEAVES, Blocks.LIME_TERRACOTTA, Blocks.LIME_TERRACOTTA, Blocks.GREEN_TERRACOTTA},
                DARK_PURPLE, Quarter.JUNGLE, 47, 1, 0, "Jungle Temple", "[NPC] Kalhuiki Door Guardian:"),
        BAL("Bal",
                new Block[]{Blocks.LAVA, Blocks.BARRIER, Blocks.BARRIER, Blocks.BARRIER, Blocks.BARRIER, Blocks.BARRIER, Blocks.BARRIER, Blocks.BARRIER, Blocks.BARRIER, Blocks.BARRIER, Blocks.BARRIER},
                GOLD, Quarter.MAGMA, 1, 1, 0, "Khazad-dûm", "Topaz Crystal"),
        CORLEONE_DOCK("Corleone Dock",
                new Block[]{Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS,
                        null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null,
                        Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.FIRE, Blocks.STONE_BRICKS},
                GREEN, Quarter.MITHRIL, 11, 1, 1, "Corleone", null),
        // legitcatmod's hideout column instead of shizo's four generic precursor blocks
        CORLEONE_HIDEOUT("Corleone Hideout",
                new Block[]{Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB,
                        Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR,
                        Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR,
                        Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR,
                        Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.STONE_BRICK_SLAB},
                GREEN, Quarter.MITHRIL, 0, 1, 1, null, null),
        KEY_GUARDIAN_SPIRAL("Key Guardian",
                new Block[]{Blocks.OAK_FENCE, Blocks.JUNGLE_SLAB, Blocks.POLISHED_GRANITE,
                        Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD},
                DARK_PURPLE, Quarter.JUNGLE, 0, 1, 1, null, null),
        KEY_GUARDIAN_TEMPLE("Key Guardian",
                new Block[]{Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_PLANKS, Blocks.POLISHED_GRANITE,
                        Blocks.JUNGLE_SLAB, Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_STAIRS,
                        Blocks.AIR, Blocks.JUNGLE_STAIRS, Blocks.POLISHED_GRANITE},
                DARK_PURPLE, Quarter.JUNGLE, 0, 1, 1, null, null),
        SLIME_TREE_TRUNK("Slime Tree",
                new Block[]{Blocks.POLISHED_GRANITE, Blocks.GRANITE, Blocks.GRANITE, Blocks.GRANITE,
                        Blocks.GRANITE, Blocks.GRANITE, Blocks.AIR},
                GREEN, Quarter.JUNGLE, 6, 1, 1, null, null),
        SLIME_TREE_CANOPY("Slime Tree",
                new Block[]{Blocks.GLOWSTONE, Blocks.GREEN_CARPET, Blocks.AIR},
                GREEN, Quarter.JUNGLE, -28, 1, 1, null, null),
        XALX("Xalx",
                new Block[]{Blocks.STONE, Blocks.COAL_BLOCK, Blocks.FIRE, Blocks.NETHER_QUARTZ_ORE,
                        Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR},
                GREEN, Quarter.GOBLIN, 1, 1, 1, null, "[NPC] Xalx:"),
        XALX_HUT("Xalx",
                new Block[]{Blocks.GREEN_WOOL, Blocks.GREEN_TERRACOTTA, Blocks.GREEN_TERRACOTTA, Blocks.GREEN_WOOL,
                        Blocks.AIR, Blocks.SPRUCE_SLAB, Blocks.OAK_FENCE, Blocks.TORCH},
                GREEN, Quarter.GOBLIN, 0, 1, 1, null, null),
        PETE("Pete",
                new Block[]{Blocks.NETHERRACK, Blocks.FIRE, Blocks.IRON_BARS,
                        Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR},
                GOLD, Quarter.GOBLIN, 0, 1, 1, null, null),
        // legitcatmod's column is our four blocks plus seven more, same anchor, far fewer false hits
        ODAWA("Odawa",
                new Block[]{Blocks.JUNGLE_LOG, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.JUNGLE_LOG,
                        Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.JUNGLE_LOG, Blocks.JUNGLE_LOG,
                        Blocks.JUNGLE_LOG, Blocks.HAY_BLOCK, Blocks.YELLOW_TERRACOTTA},
                GREEN, Quarter.JUNGLE, 0, 1, 1, null, "[NPC] Odawa:"),
        GOLDEN_DRAGON("Golden Dragon",
                new Block[]{Blocks.STONE, Blocks.RED_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.PLAYER_HEAD, Blocks.RED_WOOL},
                WHITE, Quarter.ANY, -3, 1, 3, "Dragon's Lair", "[NPC] Golden Dragon:"),
        // legitcatmod's smaller landmarks, category 5
        JUNGLE_LAGOON_PILLARS("Jungle Lagoon Pillars",
                new Block[]{Blocks.GRASS_BLOCK, Blocks.COBBLESTONE_WALL, Blocks.TORCH, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.STONE},
                GREEN, Quarter.JUNGLE, 0, 1, 5, null, null),
        PURPLE_TREE("Purple Tree",
                new Block[]{Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.OAK_WOOD, Blocks.SPRUCE_PLANKS, Blocks.OAK_WOOD, Blocks.SPRUCE_STAIRS, Blocks.AIR, Blocks.SPRUCE_STAIRS, Blocks.OAK_WOOD, Blocks.OAK_WOOD, Blocks.OAK_WOOD, Blocks.SPRUCE_SLAB},
                LIGHT_PURPLE, Quarter.ANY, 0, 1, 5, null, null),
        MINI_JUNGLE_TEMPLE("Mini Jungle Temple",
                new Block[]{Blocks.POLISHED_ANDESITE, Blocks.ANDESITE, Blocks.STONE_BRICK_STAIRS, Blocks.ANDESITE, Blocks.ANDESITE, Blocks.STONE, Blocks.ANDESITE, Blocks.STONE, Blocks.ANDESITE, Blocks.ANDESITE, Blocks.STONE_BRICK_STAIRS, Blocks.ANDESITE, Blocks.STONE, Blocks.ANDESITE},
                GREEN, Quarter.JUNGLE, 0, 1, 5, null, null),
        SMALL_TEMPLE("Small Temple",
                new Block[]{Blocks.STONE_BRICK_SLAB, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.JUNGLE_SLAB, Blocks.BLUE_TERRACOTTA, Blocks.POLISHED_GRANITE},
                GREEN, Quarter.ANY, 0, 1, 5, null, null),
        JUNGLE_LAGOON_TEMPLE("Jungle Lagoon Temple",
                new Block[]{Blocks.DIRT, Blocks.POLISHED_GRANITE, Blocks.JUNGLE_FENCE, Blocks.JUNGLE_FENCE, Blocks.JUNGLE_PLANKS, Blocks.AIR, Blocks.JUNGLE_SLAB, Blocks.POLISHED_GRANITE, Blocks.JUNGLE_PLANKS},
                GREEN, Quarter.JUNGLE, 0, 1, 5, null, null),
        CRYSTAL_TREE("Crystal Tree",
                new Block[]{Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD, Blocks.ACACIA_WOOD, Blocks.GLOWSTONE, Blocks.LIGHT_GRAY_CARPET},
                LIGHT_PURPLE, Quarter.ANY, 0, 1, 5, null, null),
        JUNGLE_BRIDGES("Jungle Bridges",
                new Block[]{Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_PLANKS, Blocks.GRANITE, Blocks.GRANITE},
                GREEN, Quarter.JUNGLE, 0, 1, 5, null, null),
        JUNGLE_GORGE("Jungle Gorge",
                new Block[]{Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_PLANKS, Blocks.GRANITE, Blocks.JUNGLE_STAIRS, Blocks.GRANITE, Blocks.GRANITE, Blocks.GRANITE, Blocks.GRANITE},
                GREEN, Quarter.JUNGLE, 0, 1, 5, null, null),
        TINY_POND("Tiny Pond",
                new Block[]{Blocks.LIME_TERRACOTTA, Blocks.WATER, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.STONE, Blocks.STONE, Blocks.GRASS_BLOCK, Blocks.AZURE_BLUET},
                GREEN, Quarter.ANY, 0, 1, 5, null, null),
        JUNGLE_LUXURIANT_CAVERN("Jungle Luxuriant Cavern",
                new Block[]{Blocks.LILY_PAD, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.OAK_LEAVES, Blocks.OAK_LEAVES},
                GREEN, Quarter.JUNGLE, 0, 1, 5, null, null),
        JUNGLE_VALLEY("Jungle Valley",
                new Block[]{Blocks.LIME_TERRACOTTA, Blocks.OAK_LEAVES, Blocks.OAK_LEAVES, Blocks.OAK_LEAVES, Blocks.OAK_LEAVES, Blocks.OAK_LEAVES, Blocks.OAK_LEAVES, Blocks.GREEN_WOOL},
                GREEN, Quarter.JUNGLE, 0, 1, 5, null, null),
        SLUDGE_CAVERN("Sludge Cavern",
                new Block[]{Blocks.DIRT, Blocks.CLAY, Blocks.CLAY, Blocks.CLAY, Blocks.GRASS_BLOCK, Blocks.BLUE_ORCHID},
                GREEN, Quarter.ANY, 0, 1, 5, null, null),
        JUNGLE_CAVERN("Jungle Cavern",
                new Block[]{Blocks.LIME_TERRACOTTA, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.OAK_LEAVES, Blocks.OAK_LEAVES, Blocks.OAK_LEAVES, Blocks.OAK_LEAVES, Blocks.OAK_LEAVES, Blocks.OAK_LEAVES, Blocks.OAK_LEAVES, Blocks.OAK_LEAVES, Blocks.GREEN_TERRACOTTA, Blocks.LIME_TERRACOTTA, Blocks.LIME_TERRACOTTA},
                GREEN, Quarter.JUNGLE, 0, 1, 5, null, null),
        STUCK_SLUDGE("Stuck Sludge",
                new Block[]{Blocks.STONE_BRICKS, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.POLISHED_GRANITE, Blocks.GRANITE, Blocks.GRANITE},
                GREEN, Quarter.ANY, 0, 1, 5, null, null),
        GOBLIN_HALL("Goblin Hall",
                new Block[]{Blocks.SPRUCE_PLANKS, null, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_STAIRS, null, null, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_STAIRS, null, null, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_STAIRS, null, Blocks.SPRUCE_PLANKS},
                GOLD, Quarter.GOBLIN, 0, 1, 5, null, null),
        RUINED_FORGE("Ruined Forge",
                new Block[]{Blocks.POLISHED_ANDESITE, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.OAK_FENCE, Blocks.OAK_FENCE, Blocks.OAK_FENCE, Blocks.OAK_FENCE, Blocks.OAK_FENCE, Blocks.OAK_FENCE, Blocks.OAK_FENCE, Blocks.OAK_FENCE, Blocks.OAK_LOG, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.AIR, Blocks.OAK_LOG},
                AQUA, Quarter.ANY, 0, 1, 5, null, null),
        JADE_PEDESTAL("Jade Pedestal",
                new Block[]{Blocks.ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.AIR, Blocks.SEA_LANTERN, Blocks.LIME_STAINED_GLASS, Blocks.LIME_STAINED_GLASS},
                AQUA, Quarter.ANY, 0, 1, 5, null, null),
        CRYSTAL_TRAIN("Crystal Train",
                new Block[]{Blocks.STONE_BRICK_STAIRS, Blocks.COBBLESTONE_STAIRS, Blocks.COBBLESTONE_STAIRS, Blocks.POLISHED_GRANITE, Blocks.GRANITE, Blocks.GRANITE, Blocks.AIR, Blocks.AIR, Blocks.AIR},
                AQUA, Quarter.ANY, 0, 1, 5, null, null),
        MINIATURE_FORGE("Miniature Forge",
                new Block[]{Blocks.COBBLESTONE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.STONE},
                AQUA, Quarter.ANY, 0, 1, 5, null, null),
        GRANITE_WALKWAY("Granite Walkway",
                new Block[]{Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICK_STAIRS, Blocks.POLISHED_ANDESITE, Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL, Blocks.POLISHED_ANDESITE},
                AQUA, Quarter.ANY, 0, 1, 5, null, null),
        GRUNT_RAILS("Grunt Rails",
                new Block[]{Blocks.SPRUCE_PLANKS, Blocks.AIR, Blocks.OAK_WALL_SIGN, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.SPRUCE_PLANKS, Blocks.TNT},
                AQUA, Quarter.ANY, 0, 1, 5, null, null),
        RAIL_CROSSING("Rail Crossing",
                new Block[]{Blocks.STONE, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.SPRUCE_STAIRS, Blocks.OAK_LOG, Blocks.OAK_FENCE_GATE},
                AQUA, Quarter.ANY, 0, 1, 5, null, null),
        WATERFALL("Waterfall",
                new Block[]{Blocks.SPRUCE_FENCE, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.OAK_WOOD, Blocks.RAIL},
                AQUA, Quarter.ANY, 0, 1, 5, null, null),
        DOUBLE_RAILWAYS("Double Railways",
                new Block[]{Blocks.FERN, Blocks.AIR, Blocks.STONE_BRICK_STAIRS, Blocks.POLISHED_ANDESITE, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_SLAB},
                AQUA, Quarter.ANY, 0, 1, 5, null, null),
        PRECURSOR_TALL_PILLARS("Precursor Tall Pillars",
                new Block[]{Blocks.POLISHED_DIORITE, Blocks.POLISHED_DIORITE, Blocks.DIORITE, Blocks.DIORITE, Blocks.POLISHED_DIORITE, Blocks.POLISHED_DIORITE, Blocks.POLISHED_ANDESITE, Blocks.DIORITE, Blocks.DIORITE, Blocks.DIORITE, Blocks.DIORITE, Blocks.POLISHED_DIORITE, Blocks.DIORITE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_DIORITE, Blocks.DIORITE, Blocks.DIORITE, Blocks.DIORITE, Blocks.DIORITE, Blocks.POLISHED_DIORITE},
                AQUA, Quarter.PRECURSOR, 0, 1, 5, null, null),
        MANSION("Mansion",
                new Block[]{Blocks.CYAN_TERRACOTTA, Blocks.CYAN_TERRACOTTA, Blocks.CYAN_TERRACOTTA, Blocks.CYAN_TERRACOTTA, Blocks.CYAN_TERRACOTTA, Blocks.AIR, Blocks.AIR, Blocks.CRACKED_STONE_BRICKS, Blocks.WALL_TORCH, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICKS},
                LIGHT_PURPLE, Quarter.ANY, 0, 1, 5, null, null);

        final String displayName;
        final Block[] blocks;
        final int color;
        final Quarter quarter;
        final int lift;
        final int minHits;
        final int category;
        final String area;        // scoreboard sub area while standing inside
        final String chatTrigger; // server line that only fires inside the structure

        Structure(String displayName, Block[] blocks, int color, Quarter quarter, int lift, int minHits, int category,
                  String area, String chatTrigger) {
            this.displayName = displayName;
            this.blocks = blocks;
            this.color = color;
            this.quarter = quarter;
            this.lift = lift;
            this.minHits = minHits;
            this.category = category;
            this.area = area;
            this.chatTrigger = chatTrigger;
        }
    }

    // anchor = a block the pattern matched, kept so the hit can be re-checked later
    private record WaypointData(BlockPos pos, int color, int category, Structure structure, BlockPos anchor, boolean verified) {}

    // matching columns of one structure instance; the waypoint sits in the middle of them
    private static final class Cluster {
        final BlockPos first;
        long sx, sy, sz;
        int n;

        Cluster(BlockPos p) {
            first = p.immutable();
            add(p);
        }

        void add(BlockPos p) {
            sx += p.getX(); sy += p.getY(); sz += p.getZ(); n++;
        }

        boolean near(BlockPos p) {
            int dx = p.getX() - first.getX(), dz = p.getZ() - first.getZ(), dy = p.getY() - first.getY();
            return dx * dx + dz * dz <= 64 * 64 && Math.abs(dy) <= 48;
        }

        BlockPos center() {
            return new BlockPos((int) (sx / n), (int) (sy / n), (int) (sz / n));
        }
    }

    // first block -> landmark patterns starting with it, and every block any pattern starts with
    private static final Map<Block, List<Structure>> LANDMARKS_BY_FIRST = new java.util.HashMap<>();
    private static final Set<Block> FIRST_BLOCKS = new java.util.HashSet<>(List.of(Blocks.LAVA,
            Blocks.MAGENTA_STAINED_GLASS, Blocks.MAGENTA_STAINED_GLASS_PANE));
    static {
        for (Structure s : Structure.values()) {
            FIRST_BLOCKS.add(s.blocks[0]);
            if (s.category == 5) LANDMARKS_BY_FIRST.computeIfAbsent(s.blocks[0], k -> new ArrayList<>()).add(s);
        }
    }

    private static volatile Thread scanThread;
    private static final java.util.concurrent.ExecutorService SCAN_THREAD =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "TeslaMaps-StructureScanner");
                t.setDaemon(true);
                scanThread = t;
                return t;
            });
    private static volatile int generation;
    private static volatile int taskGen = -1;

    private static final Map<String, WaypointData> waypoints = new ConcurrentHashMap<>();
    private static final Map<Structure, List<Cluster>> clusters = new ConcurrentHashMap<>();
    private static final Set<Long> scannedChunks = ConcurrentHashMap.newKeySet();
    private static boolean inHollows = false;
    private static int areaCheckTicks = 20;
    private static int validateTicks = 0;
    private static Object lastLevel = null;

    public static void init() {
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            if (!enabled() || !inHollows) return;
            // fresh lobbies stream empty chunks first and re-send them filled -
            // always rescan on a re-send, never mark an empty chunk as scanned
            if (!chunkReady(chunk)) return;
            scannedChunks.add(chunk.getPos().pack());
            submit(chunk);
        });
    }

    // off-thread like legitcatmod's StructureFinder-Scanner, so chunk streaming never stalls the frame
    private static void submit(LevelChunk chunk) {
        int gen = generation;
        Snap snap = Snap.copy(chunk);
        SCAN_THREAD.execute(() -> {
            if (gen != generation) return;
            taskGen = gen;
            try {
                scanChunk(snap);
            } catch (RuntimeException ignored) {
            } finally {
                taskGen = -1;
            }
        });
    }

    // results of a task queued before clear() belong to the old lobby
    private static boolean stale() {
        return Thread.currentThread() == scanThread && taskGen != generation;
    }

    /** Chunk sections; a copy for the scan thread so it never reads a live chunk. */
    private record Snap(int x, int z, int minSection, LevelChunkSection[] sections) {
        static Snap live(LevelChunk c) {
            return new Snap(c.getPos().x(), c.getPos().z(), c.getMinSectionY(), c.getSections());
        }

        static Snap copy(LevelChunk c) {
            LevelChunkSection[] src = c.getSections(), out = new LevelChunkSection[src.length];
            for (int i = 0; i < src.length; i++) {
                LevelChunkSection sec = src[i];
                if (sec != null && !sec.hasOnlyAir()) out[i] = sec.copy();
            }
            return new Snap(c.getPos().x(), c.getPos().z(), c.getMinSectionY(), out);
        }

        int index(int y) { return (y >> 4) - minSection; }
    }

    private static boolean chunkReady(LevelChunk chunk) {
        int minX = chunk.getPos().x() * 16, minZ = chunk.getPos().z() * 16;
        if (minX + 15 < 202 || minX > 823 || minZ + 15 < 202 || minZ > 823) return true;
        for (LevelChunkSection s : chunk.getSections()) {
            if (s != null && !s.hasOnlyAir()) return true;
        }
        return false;
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
            if (inHollows) checkSubArea();
        }
        if (!inHollows) return;

        // catch-up for chunks that loaded before the area was known (world join burst)
        if (mc.player != null && mc.level != null) {
            int pcx = mc.player.chunkPosition().x(), pcz = mc.player.chunkPosition().z();
            int r = Math.min(mc.options.renderDistance().get(), 12);
            int budget = 2;
            for (int dx = -r; dx <= r && budget > 0; dx++) {
                for (int dz = -r; dz <= r && budget > 0; dz++) {
                    int cx = pcx + dx, cz = pcz + dz;
                    if (scannedChunks.contains(net.minecraft.world.level.ChunkPos.pack(cx, cz))) continue;
                    LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (chunk == null || !chunkReady(chunk)) continue;
                    scannedChunks.add(net.minecraft.world.level.ChunkPos.pack(cx, cz));
                    submit(chunk);
                    budget--;
                }
            }
        }

        if (++validateTicks >= 40) {
            validateTicks = 0;
            revalidate();
        }
    }

    private static boolean checkHollows() {
        for (String line : TabListUtils.getTabListLines()) {
            if (line == null) continue;
            String clean = StringUtil.stripTrim(line);
            int i = clean.indexOf("Area:");
            if (i >= 0) return clean.substring(i + 5).trim().startsWith("Crystal Hollows");
        }
        return false;
    }

    // the scoreboard names the structure the player is standing in, so it is ground truth
    private static void checkSubArea() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            if (line == null || line.indexOf('⏣') < 0) continue;
            String clean = StringUtil.stripTrim(line).replace("⏣", "").trim();
            if (clean.isEmpty()) continue;
            for (Structure s : Structure.values()) {
                if (s.area != null && s.area.equalsIgnoreCase(clean)) {
                    verify(s, mc.player.blockPosition());
                    return;
                }
            }
            if (clean.equalsIgnoreCase("Fairy Grotto")) {
                verifyDirect("Fairy Grotto", mc.player.blockPosition(), LIGHT_PURPLE, 2);
            }
            return;
        }
    }

    public static void onChatMessage(String text) {
        if (!TeslaMapsConfig.get().chScanner || !inHollows) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String clean = StringUtil.stripTrim(text);
        for (Structure s : Structure.values()) {
            if (s.chatTrigger != null && clean.contains(s.chatTrigger)) {
                verify(s, mc.player.blockPosition());
                return;
            }
        }
    }

    // a scanned hit can be a false positive or sit on the wrong column of the structure;
    // ground truth only moves it when it is nowhere near where the player actually stands
    private static void verify(Structure s, BlockPos playerPos) {
        WaypointData old = waypoints.get(s.displayName);
        if (old != null && old.verified()) return;
        if (old != null && old.pos().distSqr(playerPos) <= 64 * 64) {
            waypoints.put(s.displayName, new WaypointData(old.pos(), old.color(), old.category(), s, old.anchor(), true));
            return;
        }
        waypoints.put(s.displayName, new WaypointData(playerPos.immutable(), s.color, s.category, s, null, true));
        announce(s.displayName, playerPos);
    }

    private static void verifyDirect(String name, BlockPos playerPos, int color, int category) {
        WaypointData old = waypoints.get(name);
        if (old != null && old.verified()) return;
        if (old != null && old.pos().distSqr(playerPos) <= 64 * 64) {
            waypoints.put(name, new WaypointData(old.pos(), old.color(), old.category(), old.structure(), old.anchor(), true));
            return;
        }
        waypoints.put(name, new WaypointData(playerPos.immutable(), color, category, null, null, true));
        announce(name, playerPos);
    }

    // drop scanned hits whose column no longer matches - a wrong waypoint used to stick for the whole session
    private static void revalidate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (Map.Entry<String, WaypointData> e : waypoints.entrySet()) {
            WaypointData w = e.getValue();
            if (w.verified() || w.anchor() == null || w.structure() == null) continue;
            BlockPos a = w.anchor();
            LevelChunk chunk = mc.level.getChunkSource().getChunk(a.getX() >> 4, a.getZ() >> 4, ChunkStatus.FULL, false);
            if (chunk == null) continue;
            if (!checkSeq(Snap.live(chunk), a.getX() & 15, a.getY(), a.getZ() & 15, w.structure().blocks)) {
                waypoints.remove(e.getKey());
                clusters.remove(w.structure());
            }
        }
    }

    private static void clear() {
        generation++;
        waypoints.clear();
        clusters.clear();
        scannedChunks.clear();
        inHollows = false;
        areaCheckTicks = 20;
        validateTicks = 0;
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().chScanner || !inHollows || waypoints.isEmpty()) return;
        boolean tracers = TeslaMapsConfig.get().chScanTracers;
        for (Map.Entry<String, WaypointData> e : waypoints.entrySet()) {
            WaypointData w = e.getValue();
            if (!categoryEnabled(w.category()) || !structureOn(e.getKey())) continue;
            BlockPos p = w.pos();
            AABB box = new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1);
            ESPRenderer.drawBoxOutline(matrices, box, w.color(), 1.5f, cameraPos);
            Vec3 center = new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            int dist = (int) cameraPos.distanceTo(center);
            String label = "§f" + e.getKey() + (w.verified() ? " §a✔" : " §7?") + " §7" + dist + "m";
            ESPRenderer.drawText(matrices, label, new Vec3(center.x, p.getY() + 1.5, center.z), 2f, cameraPos);
            if (tracers) ESPRenderer.drawTracerFromCamera(matrices, center, w.color(), cameraPos);
        }
    }

    public interface Visitor { void visit(String name, BlockPos pos, int color); }

    /** Found structures plus named waypoints, for the Hollows minimap. */
    public static void visit(Visitor v) {
        visitScanned(v);
        com.teslamaps.features.mining.HollowsWaypoints.visit(v);
    }

    public static void visitScanned(Visitor v) {
        if (!inHollows) return;
        for (Map.Entry<String, WaypointData> e : waypoints.entrySet()) {
            WaypointData w = e.getValue();
            if (categoryEnabled(w.category()) && structureOn(e.getKey())) v.visit(e.getKey(), w.pos(), w.color());
        }
    }

    private static boolean categoryEnabled(int category) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        return switch (category) {
            case 0 -> cfg.chScanCrystals;
            case 1 -> cfg.chScanMobSpots;
            case 2 -> cfg.chScanGrottos;
            case 3 -> cfg.chScanDragon;
            case 5 -> cfg.chScanLandmarks;
            default -> false;
        };
    }

    /** Per-structure switch, keyed by the waypoint name; absent = on. */
    public static boolean structureOn(String name) {
        Boolean on = TeslaMapsConfig.get().chScanStructures.get(name);
        return on == null || on;
    }

    /** Every waypoint name with its category (0 crystal, 1 mob spot, 2 grotto, 3 dragon, 5 landmark). */
    public static Map<String, Integer> structureNames() {
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        for (Structure s : Structure.values()) out.putIfAbsent(s.displayName, s.category);
        out.put("Fairy Grotto", 2);
        return out;
    }

    private static void scanChunk(Snap chunk) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        int chunkX = chunk.x(), chunkZ = chunk.z();
        // palette pre-filter: a section without any first block cannot anchor a match
        LevelChunkSection[] sections = chunk.sections();
        boolean[] relevant = new boolean[sections.length];
        boolean any = false;
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection sec = sections[i];
            relevant[i] = sec != null && !sec.hasOnlyAir() && sec.maybeHas(st -> FIRST_BLOCKS.contains(st.getBlock()));
            any |= relevant[i];
        }
        if (!any) return;
        boolean landmarks = cfg.chScanLandmarks;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int worldX = chunkX * 16 + lx;
                int worldZ = chunkZ * 16 + lz;
                if (worldX < 202 || worldX > 823 || worldZ < 202 || worldZ > 823) continue;

                for (int y = 31; y <= 188; y++) {
                    int si = chunk.index(y);
                    if (si < 0 || si >= relevant.length || !relevant[si]) continue;
                    BlockState state = blockAt(chunk, lx, y, lz);
                    if (state.isAir()) continue;
                    Block block = state.getBlock();
                    if (landmarks) {
                        List<Structure> extra = LANDMARKS_BY_FIRST.get(block);
                        if (extra != null) {
                            for (Structure s : extra) tryAdd(true, s, chunk, lx, y, lz, worldX, worldZ);
                        }
                    }

                    if (block == Blocks.RED_WOOL) {
                        tryAdd(cfg.chScanCrystals, Structure.KING, chunk, lx, y, lz, worldX, worldZ);
                    } else if (block == Blocks.STONE) {
                        tryAdd(cfg.chScanCrystals, Structure.QUEEN, chunk, lx, y, lz, worldX, worldZ);
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
                    } else if (block == Blocks.SMOOTH_STONE_SLAB) {
                        tryAdd(cfg.chScanMobSpots, Structure.CORLEONE_HIDEOUT, chunk, lx, y, lz, worldX, worldZ);
                    } else if (block == Blocks.POLISHED_GRANITE) {
                        tryAdd(cfg.chScanMobSpots, Structure.SLIME_TREE_TRUNK, chunk, lx, y, lz, worldX, worldZ);
                    } else if (block == Blocks.GLOWSTONE) {
                        tryAdd(cfg.chScanMobSpots, Structure.SLIME_TREE_CANOPY, chunk, lx, y, lz, worldX, worldZ);
                    } else if (block == Blocks.JUNGLE_STAIRS) {
                        tryAdd(cfg.chScanMobSpots, Structure.KEY_GUARDIAN_TEMPLE, chunk, lx, y, lz, worldX, worldZ);
                    } else if (block == Blocks.OAK_FENCE) {
                        tryAdd(cfg.chScanMobSpots, Structure.KEY_GUARDIAN_SPIRAL, chunk, lx, y, lz, worldX, worldZ);
                    } else if (block == Blocks.GREEN_WOOL) {
                        tryAdd(cfg.chScanMobSpots, Structure.XALX_HUT, chunk, lx, y, lz, worldX, worldZ);
                    } else if (block == Blocks.NETHERRACK) {
                        tryAdd(cfg.chScanMobSpots, Structure.PETE, chunk, lx, y, lz, worldX, worldZ);
                    } else if (block == Blocks.JUNGLE_LOG) {
                        tryAdd(cfg.chScanMobSpots, Structure.ODAWA, chunk, lx, y, lz, worldX, worldZ);
                    } else if ((block == Blocks.MAGENTA_STAINED_GLASS || block == Blocks.MAGENTA_STAINED_GLASS_PANE)
                            && cfg.chScanGrottos && structureOn("Fairy Grotto") && !waypoints.containsKey("Fairy Grotto")
                            && !inQuarter(Quarter.NUCLEUS, worldX, y, worldZ)) {
                        addWaypoint("Fairy Grotto", new BlockPos(worldX, y, worldZ), LIGHT_PURPLE, 2, null, null);
                    }
                }
            }
        }
    }

    private static void tryAdd(boolean toggle, Structure s, Snap chunk, int lx, int y, int lz, int worldX, int worldZ) {
        if (!toggle || !structureOn(s.displayName)) return;
        WaypointData existing = waypoints.get(s.displayName);
        if (existing != null && existing.verified()) return;
        if (!inQuarter(s.quarter, worldX, y, worldZ)) return;
        if (!checkSeq(chunk, lx, y, lz, s.blocks)) return;

        BlockPos anchor = new BlockPos(worldX, y, worldZ);
        List<Cluster> list = clusters.computeIfAbsent(s, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        Cluster hit = null;
        for (Cluster c : list) {
            if (c.near(anchor)) { c.add(anchor); hit = c; break; }
        }
        if (hit == null) {
            if (list.size() >= 8) return;
            hit = new Cluster(anchor);
            list.add(hit);
        }

        Cluster best = hit;
        for (Cluster c : list) if (c.n > best.n) best = c;
        if (best.n < s.minHits) return;

        BlockPos center = best.center();
        BlockPos target = new BlockPos(center.getX(), center.getY() + s.lift, center.getZ());
        if (!inBounds(target.getX(), target.getY(), target.getZ())) return;
        putWaypoint(s.displayName, target, s.color, s.category, s, best.first);
    }

    // direct section access, avoids BlockPos allocation on the hot path
    private static BlockState blockAt(Snap chunk, int lx, int absY, int lz) {
        int sectionIdx = chunk.index(absY);
        LevelChunkSection[] sections = chunk.sections();
        if (sectionIdx < 0 || sectionIdx >= sections.length) return Blocks.AIR.defaultBlockState();
        LevelChunkSection section = sections[sectionIdx];
        if (section == null || section.hasOnlyAir()) return Blocks.AIR.defaultBlockState();
        return section.getBlockState(lx & 15, absY & 15, lz & 15);
    }

    // AIR in a pattern means "empty", and the hollows are full of cave_air
    private static boolean checkSeq(Snap chunk, int lx, int startY, int lz, Block[] seq) {
        for (int i = 0; i < seq.length; i++) {
            if (seq[i] == null) continue;
            int y = startY + i;
            if (y > 255) return false;
            BlockState st = blockAt(chunk, lx, y, lz);
            if (seq[i] == Blocks.AIR) {
                if (!st.isAir()) return false;
            } else if (st.getBlock() != seq[i]) {
                return false;
            }
        }
        return true;
    }

    private static void addWaypoint(String name, BlockPos pos, int color, int category, Structure s, BlockPos anchor) {
        if (stale()) return;
        if (waypoints.putIfAbsent(name, new WaypointData(pos.immutable(), color, category,
                s, anchor == null ? null : anchor.immutable(), false)) != null) return;
        announce(name, pos);
    }

    // the centroid shifts while more chunks of the same structure load, so an existing
    // unverified waypoint gets moved instead of staying on the first column that matched
    private static void putWaypoint(String name, BlockPos pos, int color, int category, Structure s, BlockPos anchor) {
        if (stale()) return;
        boolean[] added = {false};
        WaypointData next = new WaypointData(pos.immutable(), color, category, s, anchor.immutable(), false);
        waypoints.compute(name, (k, old) -> {
            if (old != null && old.verified()) return old;
            added[0] = old == null;
            return next;
        });
        if (added[0]) announce(name, pos);
    }

    private static void announce(String name, BlockPos pos) {
        if (!TeslaMapsConfig.get().chScanChat) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        BlockPos p = pos.immutable();
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal(
                    "§6[TeslaMaps] §f" + name + " §7found at §f" + p.getX() + " " + p.getY() + " " + p.getZ()));
        });
    }

    // /tmap chscan
    public static List<String> report() {
        List<String> out = new ArrayList<>();
        if (!inHollows) { out.add("§7not in the Crystal Hollows"); return out; }
        if (waypoints.isEmpty()) { out.add("§7nothing found yet"); return out; }
        for (Map.Entry<String, WaypointData> e : waypoints.entrySet()) {
            WaypointData w = e.getValue();
            out.add((w.verified() ? "§a✔ " : "§7? ") + "§f" + e.getKey() + " §7"
                    + w.pos().getX() + " " + w.pos().getY() + " " + w.pos().getZ());
        }
        return out;
    }

    // /tmap chscan column - the real block column under the player, for writing patterns
    public static List<String> columnDump() {
        List<String> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { out.add("§7no world"); return out; }
        BlockPos p = mc.player.blockPosition();
        LevelChunk chunk = mc.level.getChunkSource().getChunk(p.getX() >> 4, p.getZ() >> 4, ChunkStatus.FULL, false);
        if (chunk == null) { out.add("§7chunk not loaded"); return out; }
        Snap snap = Snap.live(chunk);
        out.add("§6column at §f" + p.getX() + " " + p.getY() + " " + p.getZ());
        String run = null;
        int runStart = 0, runLen = 0;
        for (int dy = -6; dy <= 32; dy++) {
            int y = p.getY() + dy;
            if (y < 0 || y > 255) continue;
            BlockState st = blockAt(snap, p.getX() & 15, y, p.getZ() & 15);
            String name = st.isAir() ? "AIR" : net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(st.getBlock()).getPath().toUpperCase();
            if (name.equals(run)) { runLen++; continue; }
            if (run != null) out.add("§7y" + runStart + (runLen > 1 ? "-" + (runStart + runLen - 1) : "")
                    + " §f" + run + (runLen > 1 ? " §7x" + runLen : ""));
            run = name; runStart = y; runLen = 1;
        }
        if (run != null) out.add("§7y" + runStart + (runLen > 1 ? "-" + (runStart + runLen - 1) : "")
                + " §f" + run + (runLen > 1 ? " §7x" + runLen : ""));
        return out;
    }

    public static void reset() {
        waypoints.clear();
        clusters.clear();
        scannedChunks.clear();
        validateTicks = 0;
    }
}
