package com.teslamaps.features;

// Ported from NoammAddons' features/impl/dungeon/IHateDoors.kt; render-only here, the world keeps the real blocks
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.scanner.ComponentGrid;
import com.teslamaps.scanner.CoreHasher;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dungeon doors drawn as stained glass. Only the chunk mesh changes (DoorGlassModel): the blocks stay
 * coal / red terracotta / infested stone for the server, collision, door ESP and the map scanner.
 */
public final class IHateDoors {

    public static final String[] COLORS = {"Default", "White", "Black", "Cyan", "Light Blue", "Red", "Pink", "Orange",
            "Magenta", "Yellow", "Lime", "Gray", "Light Gray", "Purple", "Blue", "Brown", "Green"};

    private static final int ENTRANCE = 0, WITHER = 1, BLOOD = 2;

    private record Door(int type, BlockPos[] blocks) {}

    private static final List<Door> doors = new ArrayList<>();
    private static final boolean[] scanned = new boolean[11 * 11];
    /** Door block -> glass to draw there. Read from chunk-build threads. */
    private static final Map<Long, BlockState> glassAt = new ConcurrentHashMap<>();
    private static Object level;
    private static int scanCooldown, offTicks;
    private static String lastLook = "";

    /** Feature on and inside a dungeon: the door blocks get the wrapper model. */
    public static volatile boolean active;

    private IHateDoors() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("IHateDoors").section("ESP").tickAlways(IHateDoors::tick)
                .config("ESP", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("I Hate Doors");
                    b.toggle("Glass Dungeon Doors", () -> c.iHateDoors, v -> c.iHateDoors = v).cheat()
                            .desc("Entrance, wither and blood doors are drawn as stained glass so you can see through them. "
                                    + "Render only: the blocks stay real for the server, collision, Door ESP and the map.");
                    b.toggle("  Entrance Door", () -> c.iHateDoorsEntrance, v -> c.iHateDoorsEntrance = v);
                    b.dropdown("    Entrance Glass", COLORS, () -> c.iHateDoorsEntranceColor, v -> c.iHateDoorsEntranceColor = v);
                    b.toggle("  Wither Door", () -> c.iHateDoorsWither, v -> c.iHateDoorsWither = v);
                    b.dropdown("    Wither Glass", COLORS, () -> c.iHateDoorsWitherColor, v -> c.iHateDoorsWitherColor = v);
                    b.toggle("  Blood Door", () -> c.iHateDoorsBlood, v -> c.iHateDoorsBlood = v);
                    b.dropdown("    Blood Glass", COLORS, () -> c.iHateDoorsBloodColor, v -> c.iHateDoorsBloodColor = v);
                    b.toggle("  Blood Room Walls", () -> c.iHateDoorsBloodWalls, v -> c.iHateDoorsBloodWalls = v)
                            .desc("Stone brick and mossy stone brick walls inside the blood room, above the floor");
                    b.dropdown("    Wall Glass", COLORS, () -> c.iHateDoorsBloodWallsColor, v -> c.iHateDoorsBloodWallsColor = v);
                });
    }

    /** Glass to draw at this block, null = draw the real block. */
    public static BlockState glassAt(BlockPos pos, BlockState state) {
        if (!active) return null;
        BlockState door = glassAt.get(pos.asLong());
        if (door != null) return door;
        if (bloodWalls && wallBlock(state.getBlock()) && pos.getY() > FLOOR_Y && inBlood(pos.getX(), pos.getZ())) return wallGlass;
        return null;
    }

    /** The block types a door can be made of; only these get the wrapper model. */
    public static boolean doorBlock(Block b) {
        return typeOf(b) >= 0;
    }

    /** Blood room wall blocks; only while that option is on. */
    public static boolean wallBlock(Block b) {
        return bloodWalls && (b == Blocks.STONE_BRICKS || b == Blocks.MOSSY_STONE_BRICKS);
    }

    private static final int FLOOR_Y = 68;
    private static volatile boolean bloodWalls;
    private static volatile BlockState wallGlass = Blocks.GLASS.defaultBlockState();
    /** Blood room components as {minX, maxX, minZ, maxZ}. */
    private static volatile int[][] bloodBoxes = new int[0][];
    private static String lastBlood = "";

    private static boolean inBlood(int x, int z) {
        for (int[] b : bloodBoxes) if (x >= b[0] && x <= b[1] && z >= b[2] && z <= b[3]) return true;
        return false;
    }

    private static void updateBloodWalls(Minecraft mc, TeslaMapsConfig c) {
        List<int[]> boxes = new ArrayList<>();
        if (c.iHateDoorsBloodWalls) {
            for (com.teslamaps.map.DungeonRoom r : DungeonManager.getGrid().getAllRooms()) {
                if (r.getType() != com.teslamaps.map.RoomType.BLOOD) continue;
                for (int[] comp : r.getComponents()) {
                    int cx = ComponentGrid.DUNGEON_MIN_X + ComponentGrid.HALF_ROOM_SIZE + comp[0] * ComponentGrid.TOTAL_SIZE;
                    int cz = ComponentGrid.DUNGEON_MIN_Z + ComponentGrid.HALF_ROOM_SIZE + comp[1] * ComponentGrid.TOTAL_SIZE;
                    boxes.add(new int[]{cx - ComponentGrid.HALF_ROOM_SIZE, cx + ComponentGrid.HALF_ROOM_SIZE,
                            cz - ComponentGrid.HALF_ROOM_SIZE, cz + ComponentGrid.HALF_ROOM_SIZE});
                }
            }
        }
        StringBuilder key = new StringBuilder(c.iHateDoorsBloodWalls + "|" + c.iHateDoorsBloodWallsColor);
        for (int[] b : boxes) key.append('|').append(b[0]).append(',').append(b[2]);
        String k = key.toString();
        if (k.equals(lastBlood)) return;
        boolean wasOn = bloodWalls;
        int[][] old = bloodBoxes;
        bloodWalls = c.iHateDoorsBloodWalls && !boxes.isEmpty();
        wallGlass = glass(c.iHateDoorsBloodWallsColor);
        bloodBoxes = boxes.toArray(new int[0][]);
        lastBlood = k;
        // the wrapper set changed for every stone brick: full rebuild; else only the room
        if (wasOn != bloodWalls) {
            if (mc.levelExtractor != null) mc.levelExtractor.allChanged();
            return;
        }
        if (mc.levelExtractor == null) return;
        for (int[][] set : new int[][][]{old, bloodBoxes}) {
            for (int[] b : set) mc.levelExtractor.setBlocksDirty(b[0], FLOOR_Y, b[2], b[1], 140, b[3]);
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lvl = mc.level;
        TeslaMapsConfig c = cfg();
        boolean enabled = c.iHateDoors && c.section("ESP") && lvl != null && !LegitMode.blocksCheats();
        boolean want = enabled && (DungeonManager.isInDungeon() || devTest());
        // a dungeon check that drops out for a moment must not rebuild every chunk twice
        if (!want && active && enabled) {
            if (++offTicks < 60) want = true;
        } else {
            offTicks = 0;
        }
        if (lvl != level) {
            level = lvl;
            doors.clear();
            glassAt.clear();
            java.util.Arrays.fill(scanned, false);
            bloodBoxes = new int[0][];
            bloodWalls = false;
            lastBlood = "";
        }
        if (want != active) {
            active = want;
            if (mc.levelExtractor != null && lvl != null) mc.levelExtractor.allChanged();
        }
        if (!active || lvl == null) return;

        String look = c.iHateDoorsEntrance + "|" + c.iHateDoorsEntranceColor + "|" + c.iHateDoorsWither + "|"
                + c.iHateDoorsWitherColor + "|" + c.iHateDoorsBlood + "|" + c.iHateDoorsBloodColor;
        boolean lookChanged = !look.equals(lastLook);
        lastLook = look;

        if (--scanCooldown <= 0) {
            scanCooldown = 20;
            updateBloodWalls(mc, c);
            if (devTest()) scanAround(lvl, mc.player.blockPosition());
            else if (!DungeonManager.isInBossArea()) scan(lvl);
        }
        for (Door d : doors) {
            BlockState glass = glassFor(c, d.type);
            for (BlockPos p : d.blocks) {
                long key = p.asLong();
                BlockState old = glassAt.get(key);
                if (glass == null) {
                    if (old != null) { glassAt.remove(key); dirty(mc, p); }
                } else if (old != glass) {
                    glassAt.put(key, glass);
                    dirty(mc, p);
                } else if (lookChanged) {
                    dirty(mc, p);
                }
            }
        }
    }

    private static void dirty(Minecraft mc, BlockPos p) {
        if (mc.levelExtractor != null) mc.levelExtractor.setBlocksDirty(p.getX(), p.getY(), p.getZ(), p.getX(), p.getY(), p.getZ());
    }

    private static BlockState glassFor(TeslaMapsConfig c, int type) {
        return switch (type) {
            case ENTRANCE -> c.iHateDoorsEntrance ? glass(c.iHateDoorsEntranceColor) : null;
            case WITHER -> c.iHateDoorsWither ? glass(c.iHateDoorsWitherColor) : null;
            default -> c.iHateDoorsBlood ? glass(c.iHateDoorsBloodColor) : null;
        };
    }

    private static void scan(ClientLevel lvl) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int gx = 0; gx <= 10; gx++) {
            for (int gz = 0; gz <= 10; gz++) {
                if ((gx + gz) % 2 == 0 || scanned[gx * 11 + gz]) continue;
                int x = ComponentGrid.DUNGEON_MIN_X + ComponentGrid.HALF_ROOM_SIZE + gx * 16;
                int z = ComponentGrid.DUNGEON_MIN_Z + ComponentGrid.HALF_ROOM_SIZE + gz * 16;
                if (!CoreHasher.isPositionLoaded(lvl, x, z)) continue;
                int entrance = 0, wither = 0, blood = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int t = typeOf(lvl.getBlockState(m.set(x + dx, 69, z + dz)).getBlock());
                        if (t == ENTRANCE) entrance++;
                        else if (t == WITHER) wither++;
                        else if (t == BLOOD) blood++;
                    }
                }
                int type = entrance >= 2 ? ENTRANCE : wither >= 2 ? WITHER : blood >= 4 ? BLOOD : -1;
                // no door yet: far chunks arrive late, so keep looking instead of giving up
                if (type < 0) continue;
                scanned[gx * 11 + gz] = true;
                List<BlockPos> blocks = new ArrayList<>();
                for (int y = 69; y <= 72; y++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (typeOf(lvl.getBlockState(m.set(x + dx, y, z + dz)).getBlock()) == type) blocks.add(m.immutable());
                        }
                    }
                }
                doors.add(new Door(type, blocks.toArray(new BlockPos[0])));
            }
        }
    }

    // dev client only (debugMode): every coal / red terracotta near the player counts as a door
    private static boolean devTest() {
        return FabricLoader.getInstance().isDevelopmentEnvironment() && cfg().debugMode;
    }

    private static void scanAround(ClientLevel lvl, BlockPos center) {
        doors.clear();
        List<BlockPos> wither = new ArrayList<>(), blood = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-8, -4, -8), center.offset(8, 4, 8))) {
            int t = typeOf(lvl.getBlockState(p).getBlock());
            if (t == WITHER) wither.add(p.immutable());
            else if (t == BLOOD) blood.add(p.immutable());
        }
        doors.add(new Door(WITHER, wither.toArray(new BlockPos[0])));
        doors.add(new Door(BLOOD, blood.toArray(new BlockPos[0])));
    }

    private static int typeOf(Block b) {
        if (b == Blocks.COAL_BLOCK) return WITHER;
        if (b == Blocks.DYED_TERRACOTTA.red()) return BLOOD;
        if (b == Blocks.INFESTED_STONE || b == Blocks.INFESTED_COBBLESTONE || b == Blocks.INFESTED_STONE_BRICKS
                || b == Blocks.INFESTED_MOSSY_STONE_BRICKS || b == Blocks.INFESTED_CRACKED_STONE_BRICKS
                || b == Blocks.INFESTED_CHISELED_STONE_BRICKS || b == Blocks.INFESTED_DEEPSLATE) return ENTRANCE;
        return -1;
    }

    private static BlockState glass(String color) {
        if (color == null || color.equals("Default")) return Blocks.GLASS.defaultBlockState();
        String id = color.toLowerCase(java.util.Locale.ROOT).replace(' ', '_') + "_stained_glass";
        return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)).defaultBlockState();
    }
}
