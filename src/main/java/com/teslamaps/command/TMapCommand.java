package com.teslamaps.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.database.RoomDatabase;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.map.DoorType;
import com.teslamaps.scanner.ComponentGrid;
import com.teslamaps.scanner.CoreHasher;
import com.teslamaps.scanner.DoorScanner;
import com.teslamaps.scanner.RoomScanner;
import com.teslamaps.screen.HudEditScreen;
import com.teslamaps.screen.MapConfigScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import com.teslamaps.utils.TabListUtils;
import com.teslamaps.profileviewer.api.HypixelApi;
import com.teslamaps.profileviewer.screen.ProfileViewerScreen;
import com.teslamaps.dungeon.termsim.PanesSimulator;
import com.teslamaps.dungeon.termsim.NumbersSimulator;
import com.teslamaps.dungeon.termsim.RubixSimulator;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TMapCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(ClientCommands.literal("tmap")
                .executes(context -> {
                    // Open config screen
                    Minecraft.getInstance().schedule(() -> {
                        Minecraft.getInstance().setScreen(new MapConfigScreen());
                    });
                    return 1;
                })
                .then(ClientCommands.literal("gui")
                        .executes(context -> {
                            // Open HUD edit screen
                            Minecraft.getInstance().schedule(() -> {
                                Minecraft.getInstance().setScreen(new HudEditScreen(null));
                            });
                            return 1;
                        }))
                .then(ClientCommands.literal("toggle")
                        .executes(context -> {
                            TeslaMapsConfig config = TeslaMapsConfig.get();
                            config.mapEnabled = !config.mapEnabled;
                            TeslaMapsConfig.save();
                            context.getSource().sendFeedback(Component.literal(
                                    "Map " + (config.mapEnabled ? "enabled" : "disabled")));
                            return 1;
                        }))
                .then(ClientCommands.literal("move")
                        .then(ClientCommands.argument("x", IntegerArgumentType.integer(0))
                                .then(ClientCommands.argument("y", IntegerArgumentType.integer(0))
                                        .executes(context -> {
                                            int x = IntegerArgumentType.getInteger(context, "x");
                                            int y = IntegerArgumentType.getInteger(context, "y");
                                            TeslaMapsConfig config = TeslaMapsConfig.get();
                                            config.mapX = x;
                                            config.mapY = y;
                                            TeslaMapsConfig.save();
                                            context.getSource().sendFeedback(Component.literal(
                                                    "Map moved to " + x + ", " + y));
                                            return 1;
                                        }))))
                .then(ClientCommands.literal("scale")
                        .then(ClientCommands.argument("scale", FloatArgumentType.floatArg(0.1f, 3.0f))
                                .executes(context -> {
                                    float scale = FloatArgumentType.getFloat(context, "scale");
                                    TeslaMapsConfig config = TeslaMapsConfig.get();
                                    config.mapScale = scale;
                                    TeslaMapsConfig.save();
                                    context.getSource().sendFeedback(Component.literal(
                                            "Map scale set to " + scale));
                                    return 1;
                                })))
                .then(ClientCommands.literal("debug")
                        .executes(context -> {
                            TeslaMapsConfig config = TeslaMapsConfig.get();
                            config.debugMode = !config.debugMode;
                            TeslaMapsConfig.save();
                            context.getSource().sendFeedback(Component.literal(
                                    "Debug mode " + (config.debugMode ? "enabled" : "disabled")));
                            return 1;
                        }))
                .then(ClientCommands.literal("msg")
                        .executes(context -> {
                            // Open the keybind message GUI
                            Minecraft.getInstance().schedule(() ->
                                    Minecraft.getInstance().setScreen(new com.teslamaps.screen.KeybindMessageScreen()));
                            return 1;
                        })
                        .then(ClientCommands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String msg = StringArgumentType.getString(context, "message");
                                    TeslaMapsConfig.get().keybindChatMessage = msg;
                                    TeslaMapsConfig.save();
                                    context.getSource().sendFeedback(Component.literal("Keybind message set to: " + msg));
                                    return 1;
                                })))
                .then(ClientCommands.literal("scan")
                        .executes(context -> {
                            context.getSource().sendFeedback(Component.literal("Forcing dungeon scan..."));
                            RoomScanner.forceScan();
                            int roomCount = DungeonManager.getGrid().getAllRooms().size();
                            context.getSource().sendFeedback(Component.literal("Scan complete. Found " + roomCount + " rooms."));
                            return 1;
                        }))
                .then(ClientCommands.literal("core")
                        .executes(context -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player == null || mc.level == null) {
                                context.getSource().sendFeedback(Component.literal("Not in world"));
                                return 0;
                            }
                            int x = (int) mc.player.getX();
                            int z = (int) mc.player.getZ();

                            // Get grid position
                            int[] gridPos = ComponentGrid.worldToGrid(x, z);
                            if (gridPos == null) {
                                context.getSource().sendFeedback(Component.literal("Not in dungeon area"));
                                return 0;
                            }

                            // Get center of this grid cell
                            int[] center = ComponentGrid.gridToWorld(gridPos[0], gridPos[1]);
                            int core = CoreHasher.calculateCore(mc.level, center[0], center[1]);

                            context.getSource().sendFeedback(Component.literal(
                                    String.format("Grid: [%d,%d] Center: [%d,%d] Core: %d",
                                            gridPos[0], gridPos[1], center[0], center[1], core)));

                            // Check if this core is in the database
                            var roomData = RoomDatabase.getInstance().findByCore(core);
                            if (roomData != null) {
                                context.getSource().sendFeedback(Component.literal("Matched room: " + roomData.getName()));
                            } else {
                                context.getSource().sendFeedback(Component.literal("No matching room in database"));
                            }

                            return 1;
                        }))
                .then(ClientCommands.literal("status")
                        .executes(context -> {
                            context.getSource().sendFeedback(Component.literal("=== TeslaMaps Status ==="));
                            context.getSource().sendFeedback(Component.literal("In Dungeon: " + DungeonManager.isInDungeon()));
                            context.getSource().sendFeedback(Component.literal("State: " + DungeonManager.getCurrentState()));
                            context.getSource().sendFeedback(Component.literal("Floor: " + DungeonManager.getCurrentFloor()));
                            context.getSource().sendFeedback(Component.literal("Rooms loaded: " + DungeonManager.getGrid().getAllRooms().size()));
                            context.getSource().sendFeedback(Component.literal("DB loaded: " + RoomDatabase.getInstance().isLoaded()));
                            context.getSource().sendFeedback(Component.literal("DB rooms: " + RoomDatabase.getInstance().getAllRooms().size()));
                            return 1;
                        }))
                .then(ClientCommands.literal("doors")
                        .executes(context -> {
                            context.getSource().sendFeedback(Component.literal("=== Door Debug ==="));
                            var allDoors = DoorScanner.getAllDoors();
                            context.getSource().sendFeedback(Component.literal("Total doors found: " + allDoors.size()));
                            int normal = 0, wither = 0, blood = 0, entrance = 0;
                            for (var entry : allDoors.entrySet()) {
                                DoorType type = entry.getValue();
                                switch (type) {
                                    case NORMAL -> normal++;
                                    case WITHER -> wither++;
                                    case BLOOD -> blood++;
                                    case ENTRANCE -> entrance++;
                                }
                            }
                            context.getSource().sendFeedback(Component.literal("Normal: " + normal + ", Wither: " + wither + ", Blood: " + blood + ", Entrance: " + entrance));
                            // Force rescan doors
                            context.getSource().sendFeedback(Component.literal("Rescanning doors..."));
                            DoorScanner.scanAllDoors();
                            context.getSource().sendFeedback(Component.literal("After rescan: " + DoorScanner.getAllDoors().size() + " doors"));
                            return 1;
                        }))
                .then(ClientCommands.literal("rooms")
                        .executes(context -> {
                            context.getSource().sendFeedback(Component.literal("=== Room Debug ==="));
                            for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
                                StringBuilder sb = new StringBuilder();
                                sb.append(room.getName()).append(" [");
                                for (int[] comp : room.getComponents()) {
                                    sb.append("(").append(comp[0]).append(",").append(comp[1]).append(")");
                                }
                                sb.append("] ").append(room.getCheckmarkState());
                                context.getSource().sendFeedback(Component.literal(sb.toString()));
                            }
                            return 1;
                        }))
                .then(ClientCommands.literal("map")
                        .executes(context -> {
                            Minecraft mc = Minecraft.getInstance();
                            context.getSource().sendFeedback(Component.literal("=== Map Item Debug ==="));

                            // Check hotbar
                            for (int i = 0; i < 9; i++) {
                                ItemStack stack = mc.player.getInventory().getItem(i);
                                if (stack.getItem() instanceof MapItem) {
                                    MapId mapId = stack.get(DataComponents.MAP_ID);
                                    context.getSource().sendFeedback(Component.literal("Slot " + i + ": Map found, ID=" + mapId));
                                    if (mapId != null) {
                                        MapItemSavedData state = MapItem.getSavedData(mapId, mc.level);
                                        if (state != null && state.colors != null) {
                                            context.getSource().sendFeedback(Component.literal("  MapState: colors length=" + state.colors.length));
                                            // Sample some colors from the map
                                            byte c1 = state.colors[64 + 64*128]; // center
                                            byte c2 = state.colors[32 + 32*128]; // top-left area
                                            context.getSource().sendFeedback(Component.literal("  Sample colors: center=" + c1 + ", topleft=" + c2));
                                        } else {
                                            context.getSource().sendFeedback(Component.literal("  MapState is null or has no colors"));
                                        }
                                    }
                                }
                            }

                            // Check offhand
                            ItemStack offhand = mc.player.getOffhandItem();
                            if (offhand.getItem() instanceof MapItem) {
                                context.getSource().sendFeedback(Component.literal("Offhand: Map found"));
                            }

                            return 1;
                        }))
                .then(ClientCommands.literal("mobs")
                        .executes(context -> {
                            Minecraft mc = Minecraft.getInstance();
                            context.getSource().sendFeedback(Component.literal("=== Nearby Entity Names ==="));
                            context.getSource().sendFeedback(Component.literal("ESP list: " + TeslaMapsConfig.get().customESPMobs));

                            int count = 0;
                            for (Entity entity : mc.level.entitiesForRendering()) {
                                if (!(entity instanceof LivingEntity)) continue;
                                if (entity == mc.player) continue;

                                double dist = entity.distanceToSqr(mc.player);
                                if (dist > 400) continue;  // Within 20 blocks

                                String name = entity.getName().getString();
                                String customName = entity.getCustomName() != null ? entity.getCustomName().getString() : "null";

                                // Check if matches ESP
                                boolean matchesESP = false;
                                for (String pattern : TeslaMapsConfig.get().customESPMobs) {
                                    if (name.toLowerCase().contains(pattern.toLowerCase()) ||
                                        (entity.getCustomName() != null && customName.toLowerCase().contains(pattern.toLowerCase()))) {
                                        matchesESP = true;
                                        break;
                                    }
                                }

                                // Show raw characters
                                StringBuilder hex = new StringBuilder();
                                for (char c : name.toCharArray()) {
                                    if (c > 127) {
                                        hex.append(String.format("[U+%04X]", (int) c));
                                    }
                                }

                                String espStatus = matchesESP ? " [ESP MATCH]" : "";
                                context.getSource().sendFeedback(Component.literal(
                                        String.format("%.1f: %s | custom: %s %s%s",
                                                Math.sqrt(dist), name, customName, hex.toString(), espStatus)));
                                count++;
                                if (count > 20) {
                                    context.getSource().sendFeedback(Component.literal("... and more"));
                                    break;
                                }
                            }

                            if (count == 0) {
                                context.getSource().sendFeedback(Component.literal("No entities nearby"));
                            }

                            return 1;
                        }))
                .then(ClientCommands.literal("add")
                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    TeslaMapsConfig config = TeslaMapsConfig.get();

                                    // Check if already exists (case-insensitive)
                                    boolean exists = config.customESPMobs.stream()
                                            .anyMatch(s -> s.equalsIgnoreCase(name));

                                    if (exists) {
                                        context.getSource().sendFeedback(Component.literal("'" + name + "' is already in ESP list"));
                                    } else {
                                        config.customESPMobs.add(name);
                                        TeslaMapsConfig.save();
                                        context.getSource().sendFeedback(Component.literal("Added '" + name + "' to ESP list"));
                                    }
                                    return 1;
                                })))
                .then(ClientCommands.literal("remove")
                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    TeslaMapsConfig config = TeslaMapsConfig.get();

                                    // Remove case-insensitive
                                    boolean removed = config.customESPMobs.removeIf(s -> s.equalsIgnoreCase(name));

                                    if (removed) {
                                        TeslaMapsConfig.save();
                                        context.getSource().sendFeedback(Component.literal("Removed '" + name + "' from ESP list"));
                                    } else {
                                        context.getSource().sendFeedback(Component.literal("'" + name + "' not found in ESP list"));
                                    }
                                    return 1;
                                })))
                .then(ClientCommands.literal("list")
                        .executes(context -> {
                            TeslaMapsConfig config = TeslaMapsConfig.get();
                            if (config.customESPMobs.isEmpty()) {
                                context.getSource().sendFeedback(Component.literal("ESP list is empty"));
                            } else {
                                context.getSource().sendFeedback(Component.literal("=== Custom ESP List ==="));
                                for (String mob : config.customESPMobs) {
                                    context.getSource().sendFeedback(Component.literal("  - " + mob));
                                }
                            }
                            return 1;
                        }))
                .then(ClientCommands.literal("clear")
                        .executes(context -> {
                            TeslaMapsConfig config = TeslaMapsConfig.get();
                            int count = config.customESPMobs.size();
                            config.customESPMobs.clear();
                            TeslaMapsConfig.save();
                            context.getSource().sendFeedback(Component.literal("Cleared " + count + " entries from ESP list"));
                            return 1;
                        }))
                .then(ClientCommands.literal("crypts")
                        .executes(context -> {
                            int cryptsFound = TabListUtils.getCryptsFound();
                            int totalCrypts = DungeonManager.getTotalCrypts();
                            context.getSource().sendFeedback(Component.literal("=== Crypts Info ==="));
                            if (cryptsFound >= 0) {
                                context.getSource().sendFeedback(Component.literal("Crypts: " + cryptsFound + "/" + totalCrypts + " (5 for S+)"));
                                if (cryptsFound >= 5) {
                                    context.getSource().sendFeedback(Component.literal("✓ Crypt bonus achieved!"));
                                } else {
                                    context.getSource().sendFeedback(Component.literal("Need " + (5 - cryptsFound) + " more for bonus"));
                                }
                            } else {
                                context.getSource().sendFeedback(Component.literal("Total crypts in dungeon: " + totalCrypts));
                            }
                            // Show breakdown by room
                            java.util.Set<DungeonRoom> shown = new java.util.HashSet<>();
                            for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
                                if (!shown.contains(room) && room.getCrypts() > 0) {
                                    context.getSource().sendFeedback(Component.literal("  " + room.getName() + ": " + room.getCrypts()));
                                    shown.add(room);
                                }
                            }
                            return 1;
                        }))
                .then(ClientCommands.literal("secrets")
                        .executes(context -> {
                            double secretsPercent = TabListUtils.getSecretsPercentage();
                            int totalSecrets = 0;
                            for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
                                if (room.getSecrets() > 0) {
                                    totalSecrets += room.getSecrets();
                                }
                            }
                            context.getSource().sendFeedback(Component.literal("=== Secrets Info ==="));
                            if (secretsPercent >= 0) {
                                context.getSource().sendFeedback(Component.literal(String.format("Secrets found: %.1f%%", secretsPercent)));
                                context.getSource().sendFeedback(Component.literal("Total secrets in map: " + totalSecrets));
                            } else {
                                context.getSource().sendFeedback(Component.literal("Total secrets in map: " + totalSecrets));
                                context.getSource().sendFeedback(Component.literal("Found count: Not in dungeon"));
                            }
                            return 1;
                        }))
                .then(ClientCommands.literal("scanblocks")
                        .executes(context -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player == null || mc.level == null) {
                                context.getSource().sendFeedback(Component.literal("Not in world"));
                                return 0;
                            }

                            int px = (int) mc.player.getX();
                            int py = (int) mc.player.getY();
                            int pz = (int) mc.player.getZ();

                            context.getSource().sendFeedback(Component.literal("=== Block Scan at " + px + ", " + py + ", " + pz + " ==="));

                            // Count blocks in area around player
                            Map<String, Integer> blockCounts = new HashMap<>();
                            int radius = 3;

                            // Count smooth stone slabs specifically
                            int smoothSlabCount = 0;
                            for (int x = px - radius; x <= px + radius; x++) {
                                for (int y = py - radius; y <= py + radius; y++) {
                                    for (int z = pz - radius; z <= pz + radius; z++) {
                                        BlockPos pos = new BlockPos(x, y, z);
                                        Block block = mc.level.getBlockState(pos).getBlock();
                                        String name = block.getName().getString();
                                        blockCounts.merge(name, 1, Integer::sum);
                                        if (name.contains("Smooth Stone Slab")) {
                                            smoothSlabCount++;
                                        }
                                    }
                                }
                            }

                            // Sort by count and show top blocks
                            blockCounts.entrySet().stream()
                                    .filter(e -> !e.getKey().equals("Air"))
                                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                                    .limit(15)
                                    .forEach(e -> context.getSource().sendFeedback(
                                            Component.literal("  " + e.getValue() + "x " + e.getKey())));

                            context.getSource().sendFeedback(Component.literal("Smooth slabs in area: " + smoothSlabCount + " (need 10+ for crypt)"));

                            // Check if standing on a block that could be a crypt
                            BlockPos below = new BlockPos(px, py - 1, pz);
                            Block blockBelow = mc.level.getBlockState(below).getBlock();
                            context.getSource().sendFeedback(Component.literal("Standing on: " + blockBelow.getName().getString()));

                            return 1;
                        }))
        );

        /* DISABLED - Profile Viewer
        // Register /pv command for Profile Viewer
        dispatcher.register(ClientCommands.literal("pv")
                .executes(context -> {
                    // No player specified - view own profile
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        ProfileViewerScreen.open(mc.player.getName().getString());
                    }
                    return 1;
                })
                .then(ClientCommands.argument("player", StringArgumentType.greedyString())
                        .executes(context -> {
                            String playerName = StringArgumentType.getString(context, "player");
                            ProfileViewerScreen.open(playerName);
                            return 1;
                        }))
        );
        */

        /* DISABLED - Terminal Simulator
        // Register /termsim command for Terminal Simulator
        dispatcher.register(ClientCommands.literal("termsim")
                .executes(context -> {
                    // Open random terminal simulator
                    Minecraft mc = Minecraft.getInstance();
                    Random rand = new Random();
                    int choice = rand.nextInt(3);
                    mc.send(() -> {
                        switch (choice) {
                            case 0 -> mc.setScreen(new PanesSimulator());
                            case 1 -> mc.setScreen(new NumbersSimulator());
                            case 2 -> mc.setScreen(new RubixSimulator());
                        }
                    });
                    context.getSource().sendFeedback(Text.literal("Opening random terminal simulator..."));
                    return 1;
                })
                .then(ClientCommands.literal("panes")
                        .executes(context -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.send(() -> mc.setScreen(new PanesSimulator()));
                            context.getSource().sendFeedback(Text.literal("Opening Correct all the panes! simulator..."));
                            return 1;
                        }))
                .then(ClientCommands.literal("numbers")
                        .executes(context -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.send(() -> mc.setScreen(new NumbersSimulator()));
                            context.getSource().sendFeedback(Text.literal("Opening Click in order! simulator..."));
                            return 1;
                        }))
                .then(ClientCommands.literal("rubix")
                        .executes(context -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.send(() -> mc.setScreen(new RubixSimulator()));
                            context.getSource().sendFeedback(Text.literal("Opening Change all to same color! simulator..."));
                            return 1;
                        }))
                .then(ClientCommands.literal("help")
                        .executes(context -> {
                            context.getSource().sendFeedback(Text.literal("=== Terminal Simulator ==="));
                            context.getSource().sendFeedback(Text.literal("/termsim - Open random terminal"));
                            context.getSource().sendFeedback(Text.literal("/termsim panes - Correct all the panes!"));
                            context.getSource().sendFeedback(Text.literal("/termsim numbers - Click in order!"));
                            context.getSource().sendFeedback(Text.literal("/termsim rubix - Change all to same color!"));
                            return 1;
                        }))
        );
        */
    }
}
