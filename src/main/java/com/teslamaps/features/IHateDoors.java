package com.teslamaps.features;

// Ported from NoammAddons' features/impl/dungeon/IHateDoors.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.scanner.ComponentGrid;
import com.teslamaps.scanner.CoreHasher;
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

public final class IHateDoors {

    public static final String[] COLORS = {"Default", "White", "Black", "Cyan", "Light Blue", "Red", "Pink", "Orange",
            "Magenta", "Yellow", "Lime", "Gray", "Light Gray", "Purple", "Blue", "Brown", "Green"};

    private static final int ENTRANCE = 0, WITHER = 1, BLOOD = 2;

    private record Door(int type, BlockPos[] blocks, Block[] sources) {}

    private static final List<Door> doors = new ArrayList<>();
    private static final boolean[] scanned = new boolean[11 * 11];
    private static Object level;
    private static int scanCooldown;

    private IHateDoors() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("IHateDoors").section("ESP").legit().when(() -> cfg().iHateDoors)
                .tick(IHateDoors::tick)
                .config("ESP", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("I Hate Doors");
                    b.toggle("Glass Dungeon Doors", () -> c.iHateDoors, v -> c.iHateDoors = v).cheat()
                            .desc("Turns entrance, wither and blood doors into stained glass so you can see through them.");
                    b.toggle("  Entrance Door", () -> c.iHateDoorsEntrance, v -> c.iHateDoorsEntrance = v);
                    b.dropdown("    Entrance Glass", COLORS, () -> c.iHateDoorsEntranceColor, v -> c.iHateDoorsEntranceColor = v);
                    b.toggle("  Wither Door", () -> c.iHateDoorsWither, v -> c.iHateDoorsWither = v);
                    b.dropdown("    Wither Glass", COLORS, () -> c.iHateDoorsWitherColor, v -> c.iHateDoorsWitherColor = v);
                    b.toggle("  Blood Door", () -> c.iHateDoorsBlood, v -> c.iHateDoorsBlood = v);
                    b.dropdown("    Blood Glass", COLORS, () -> c.iHateDoorsBloodColor, v -> c.iHateDoorsBloodColor = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lvl = mc.level;
        if (lvl != level) {
            level = lvl;
            doors.clear();
            java.util.Arrays.fill(scanned, false);
        }
        if (!DungeonManager.isInDungeon() || DungeonManager.isInBossArea() || LegitMode.blocksCheats()) return;
        if (--scanCooldown <= 0) {
            scanCooldown = 20;
            scan(lvl);
        }
        TeslaMapsConfig c = cfg();
        for (Door d : doors) {
            BlockState glass = switch (d.type) {
                case ENTRANCE -> c.iHateDoorsEntrance ? glass(c.iHateDoorsEntranceColor) : null;
                case WITHER -> c.iHateDoorsWither ? glass(c.iHateDoorsWitherColor) : null;
                default -> c.iHateDoorsBlood ? glass(c.iHateDoorsBloodColor) : null;
            };
            if (glass == null) continue;
            for (int i = 0; i < d.blocks.length; i++) {
                if (lvl.getBlockState(d.blocks[i]).getBlock() == d.sources[i]) lvl.setBlockAndUpdate(d.blocks[i], glass);
            }
        }
    }

    private static void scan(ClientLevel lvl) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int gx = 0; gx <= 10; gx++) {
            for (int gz = 0; gz <= 10; gz++) {
                if ((gx + gz) % 2 == 0 || scanned[gx * 11 + gz]) continue;
                int x = ComponentGrid.DUNGEON_MIN_X + ComponentGrid.HALF_ROOM_SIZE + gx * 16;
                int z = ComponentGrid.DUNGEON_MIN_Z + ComponentGrid.HALF_ROOM_SIZE + gz * 16;
                if (!CoreHasher.isPositionLoaded(lvl, x, z)) continue;
                scanned[gx * 11 + gz] = true;
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
                if (type < 0) continue;
                List<BlockPos> blocks = new ArrayList<>();
                List<Block> sources = new ArrayList<>();
                for (int y = 69; y <= 72; y++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            Block b = lvl.getBlockState(m.set(x + dx, y, z + dz)).getBlock();
                            if (typeOf(b) != type) continue;
                            blocks.add(m.immutable());
                            sources.add(b);
                        }
                    }
                }
                doors.add(new Door(type, blocks.toArray(new BlockPos[0]), sources.toArray(new Block[0])));
            }
        }
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
