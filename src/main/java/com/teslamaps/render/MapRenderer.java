package com.teslamaps.render;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.CheckmarkState;
import com.teslamaps.map.DoorType;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.map.RoomType;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.scanner.ComponentGrid;
import com.teslamaps.scanner.CryptScanner;
import com.teslamaps.scanner.DoorScanner;
import com.teslamaps.scanner.MapScanner;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders the dungeon map overlay on the HUD.
 */
public class MapRenderer {
    // Map rendering constants
    private static final int ROOM_SIZE = 24;
    private static final int DOOR_SIZE = 4;
    private static final int MAP_PADDING = 8;
    private static final int CELL_SIZE = ROOM_SIZE + DOOR_SIZE;

    // Cached dungeon bounds for dynamic sizing
    private static int minGridX = 0, maxGridX = 5;
    private static int minGridZ = 0, maxGridZ = 5;
    private static int totalSecrets = 0;

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!TeslaMapsConfig.get().mapEnabled) return;

        // Only show in dungeon if option enabled
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (config.onlyShowInDungeon && !DungeonManager.isInDungeon()) return;

        int baseX = config.mapX;
        int baseY = config.mapY;
        float scale = config.mapScale;

        Collection<DungeonRoom> rooms = DungeonManager.getGrid().getAllRooms();

        // Calculate actual dungeon bounds from rooms
        calculateDungeonBounds(rooms);

        // Calculate dynamic map size based on actual room extent
        int gridWidth = maxGridX - minGridX + 1;
        int gridHeight = maxGridZ - minGridZ + 1;
        int mapWidth = (int)((MAP_PADDING * 2 + gridWidth * CELL_SIZE) * scale);
        int mapHeight = (int)((MAP_PADDING * 2 + gridHeight * CELL_SIZE) * scale);

        // Add space for info text below map
        int infoHeight = 24; // Space for secrets/crypts text
        int totalHeight = mapHeight + infoHeight;

        // Draw background (including info area)
        if (config.showMapBackground) {
            drawBackground(context, baseX, baseY, mapWidth, totalHeight);
        }

        // First pass: Draw room connections (yellow lines between adjacent rooms)
        drawRoomConnections(context, rooms, baseX, baseY, scale);

        // Second pass: Draw rooms
        Set<DungeonRoom> drawnRooms = new HashSet<>();
        for (DungeonRoom room : rooms) {
            if (!drawnRooms.contains(room)) {
                drawRoom(context, room, baseX, baseY, scale);
                drawnRooms.add(room);
            }
        }

        // Third pass: Draw room names on top
        drawnRooms.clear();
        for (DungeonRoom room : rooms) {
            if (!drawnRooms.contains(room)) {
                drawRoomName(context, room, baseX, baseY, scale);
                drawnRooms.add(room);
            }
        }

        // Draw player marker
        if (config.showPlayerMarker) {
            drawPlayerMarker(context, mc, baseX, baseY, scale);
        }

        // Draw info text (secrets, crypts) below the map
        drawInfoText(context, mc, baseX, baseY + mapHeight + 2, mapWidth);
    }

    /**
     * Calculate the actual bounds of the dungeon from placed rooms.
     */
    private static void calculateDungeonBounds(Collection<DungeonRoom> rooms) {
        if (rooms.isEmpty()) {
            minGridX = 0; maxGridX = 5;
            minGridZ = 0; maxGridZ = 5;
            totalSecrets = 0;
            return;
        }

        minGridX = Integer.MAX_VALUE;
        maxGridX = Integer.MIN_VALUE;
        minGridZ = Integer.MAX_VALUE;
        maxGridZ = Integer.MIN_VALUE;
        totalSecrets = 0;

        Set<DungeonRoom> counted = new HashSet<>();
        for (DungeonRoom room : rooms) {
            for (int[] comp : room.getComponents()) {
                if (comp[0] < minGridX) minGridX = comp[0];
                if (comp[0] > maxGridX) maxGridX = comp[0];
                if (comp[1] < minGridZ) minGridZ = comp[1];
                if (comp[1] > maxGridZ) maxGridZ = comp[1];
            }
            // Count total secrets (avoid counting same room twice)
            if (!counted.contains(room) && room.getSecrets() > 0) {
                totalSecrets += room.getSecrets();
                counted.add(room);
            }
        }

        // Ensure at least 2x2 grid for small dungeons
        if (maxGridX - minGridX < 1) maxGridX = minGridX + 1;
        if (maxGridZ - minGridZ < 1) maxGridZ = minGridZ + 1;
    }

    /**
     * Draw info text (secrets, crypts) below the map.
     */
    private static void drawInfoText(DrawContext context, MinecraftClient mc, int x, int y, int width) {
        var textRenderer = mc.textRenderer;

        // Get secrets found from tab list
        int secretsFound = TabListUtils.getSecretsFound();
        String secretsText = secretsFound >= 0
                ? "Secrets: " + secretsFound + "/" + totalSecrets
                : "Secrets: ?/" + totalSecrets;

        // Get crypts from tab list and scanner
        int cryptsFound = TabListUtils.getCryptsFound();
        int totalCrypts = CryptScanner.getCryptCount();
        String cryptsText;
        if (cryptsFound >= 0) {
            cryptsText = "Crypts: " + cryptsFound + "/" + totalCrypts;
        } else {
            cryptsText = "Crypts: ?/" + totalCrypts;
        }

        // Draw secrets on left, crypts on right
        int secretsColor = (secretsFound >= totalSecrets && totalSecrets > 0) ? 0xFF55FF55 : 0xFFFFFFFF;
        int cryptsColor = (cryptsFound >= 5) ? 0xFF55FF55 : 0xFFFFFFFF; // 5 crypts is usually the target

        context.drawTextWithShadow(textRenderer, secretsText, x + 4, y + 4, secretsColor);

        int cryptsWidth = textRenderer.getWidth(cryptsText);
        context.drawTextWithShadow(textRenderer, cryptsText, x + width - cryptsWidth - 4, y + 4, cryptsColor);
    }

    private static void drawBackground(DrawContext context, int baseX, int baseY, int width, int height) {
        int bgColor = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorBackground);
        int borderColor = 0xFF333333; // Border is slightly lighter than background
        context.fill(baseX, baseY, baseX + width, baseY + height, bgColor);
        // Border
        context.fill(baseX, baseY, baseX + width, baseY + 2, borderColor);
        context.fill(baseX, baseY + height - 2, baseX + width, baseY + height, borderColor);
        context.fill(baseX, baseY, baseX + 2, baseY + height, borderColor);
        context.fill(baseX + width - 2, baseY, baseX + width, baseY + height, borderColor);
    }

    /**
     * Convert grid X coordinate to pixel X, accounting for dynamic dungeon bounds.
     */
    private static int gridToPixelX(int gridX, int baseX, float scale) {
        return baseX + (int)((MAP_PADDING + (gridX - minGridX) * CELL_SIZE) * scale);
    }

    /**
     * Convert grid Z coordinate to pixel Y, accounting for dynamic dungeon bounds.
     */
    private static int gridToPixelY(int gridZ, int baseY, float scale) {
        return baseY + (int)((MAP_PADDING + (gridZ - minGridZ) * CELL_SIZE) * scale);
    }

    /**
     * Draw door connections between rooms - only where doors actually exist
     */
    private static void drawRoomConnections(DrawContext context, Collection<DungeonRoom> rooms, int baseX, int baseY, float scale) {
        // Make doors wider - use 2x the door size for better visibility
        int doorThickness = (int)(DOOR_SIZE * scale * 2);
        if (doorThickness < 5) doorThickness = 5;

        // Track which connections we've drawn to avoid duplicates
        Set<String> drawnConnections = new HashSet<>();

        for (DungeonRoom room : rooms) {
            for (int[] comp : room.getComponents()) {
                int gx = comp[0];
                int gz = comp[1];

                // Check right neighbor
                DungeonRoom rightRoom = DungeonManager.getGrid().getRoom(gx + 1, gz);
                if (rightRoom != null && rightRoom != room) {
                    String key = gx + "," + gz + "-" + (gx+1) + "," + gz;
                    if (!drawnConnections.contains(key)) {
                        // Only draw if there's an actual door here AND both rooms have names (properly scanned)
                        DoorType doorType = DoorScanner.getDoorType(gx, gz, gx + 1, gz);
                        boolean bothScanned = room.getName() != null && rightRoom.getName() != null;
                        if (doorType != DoorType.NONE && bothScanned) {
                            int color = getDoorColor(doorType);
                            // Overlap by 1 pixel on each side to prevent gaps
                            int x1 = gridToPixelX(gx, baseX, scale) + (int)(ROOM_SIZE * scale) - 1;
                            // Center the door vertically using doorThickness/2
                            int yCenter = gridToPixelY(gz, baseY, scale) + (int)(ROOM_SIZE/2 * scale);
                            int y1 = yCenter - doorThickness / 2;
                            int x2 = gridToPixelX(gx + 1, baseX, scale) + 1;
                            int y2 = yCenter + doorThickness / 2;
                            context.fill(x1, y1, x2, y2, color);
                        }
                        drawnConnections.add(key);
                    }
                }

                // Check bottom neighbor
                DungeonRoom bottomRoom = DungeonManager.getGrid().getRoom(gx, gz + 1);
                if (bottomRoom != null && bottomRoom != room) {
                    String key = gx + "," + gz + "-" + gx + "," + (gz+1);
                    if (!drawnConnections.contains(key)) {
                        // Only draw if there's an actual door here AND both rooms have names (properly scanned)
                        DoorType doorType = DoorScanner.getDoorType(gx, gz, gx, gz + 1);
                        boolean bothScanned = room.getName() != null && bottomRoom.getName() != null;
                        if (doorType != DoorType.NONE && bothScanned) {
                            int color = getDoorColor(doorType);
                            // Center the door horizontally using doorThickness/2
                            int xCenter = gridToPixelX(gx, baseX, scale) + (int)(ROOM_SIZE/2 * scale);
                            int x1 = xCenter - doorThickness / 2;
                            // Overlap by 1 pixel on each side to prevent gaps
                            int y1 = gridToPixelY(gz, baseY, scale) + (int)(ROOM_SIZE * scale) - 1;
                            int x2 = xCenter + doorThickness / 2;
                            int y2 = gridToPixelY(gz + 1, baseY, scale) + 1;
                            context.fill(x1, y1, x2, y2, color);
                        }
                        drawnConnections.add(key);
                    }
                }
            }
        }
    }

    /**
     * Get the color for a door type.
     */
    private static int getDoorColor(DoorType type) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        return switch (type) {
            case NORMAL -> TeslaMapsConfig.parseColor(config.colorDoorNormal);
            case WITHER -> TeslaMapsConfig.parseColor(config.colorDoorWither);
            case BLOOD -> TeslaMapsConfig.parseColor(config.colorDoorBlood);
            case ENTRANCE -> TeslaMapsConfig.parseColor(config.colorDoorEntrance);
            default -> TeslaMapsConfig.parseColor(config.colorDoorNormal);
        };
    }

    private static void drawRoom(DrawContext context, DungeonRoom room, int baseX, int baseY, float scale) {
        int color = getRoomColor(room);

        // Calculate bounding box of all components to draw as one unified shape
        int roomMinGX = Integer.MAX_VALUE, roomMinGZ = Integer.MAX_VALUE;
        int roomMaxGX = Integer.MIN_VALUE, roomMaxGZ = Integer.MIN_VALUE;

        for (int[] component : room.getComponents()) {
            roomMinGX = Math.min(roomMinGX, component[0]);
            roomMinGZ = Math.min(roomMinGZ, component[1]);
            roomMaxGX = Math.max(roomMaxGX, component[0]);
            roomMaxGZ = Math.max(roomMaxGZ, component[1]);
        }

        // For rectangular rooms (1x1, 1x2, 2x1, 2x2), draw as single rectangle
        int componentCount = room.getComponents().size();
        int width = roomMaxGX - roomMinGX + 1;
        int height = roomMaxGZ - roomMinGZ + 1;

        if (componentCount == width * height) {
            // It's a perfect rectangle - draw as one big square
            int pixelX = gridToPixelX(roomMinGX, baseX, scale);
            int pixelY = gridToPixelY(roomMinGZ, baseY, scale);
            int pixelW = (int)((width * CELL_SIZE - DOOR_SIZE) * scale);
            int pixelH = (int)((height * CELL_SIZE - DOOR_SIZE) * scale);

            context.fill(pixelX, pixelY, pixelX + pixelW, pixelY + pixelH, color);

            // Draw secret count in corner (format: found/max or just max if unknown)
            boolean hideSecrets = TeslaMapsConfig.get().hideSecretsWhenDone && room.getCheckmarkState() == CheckmarkState.GREEN;
            if (TeslaMapsConfig.get().showSecretCount && room.getSecrets() > 0 && !hideSecrets) {
                String secretText;
                int foundSecrets = room.getFoundSecrets();
                int maxSecrets = room.getSecrets();
                if (foundSecrets >= 0) {
                    // Show found/max format
                    secretText = foundSecrets + "/" + maxSecrets;
                } else {
                    // Unknown found count, just show max
                    secretText = String.valueOf(maxSecrets);
                }
                context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                        secretText, pixelX + 2, pixelY + 2, TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorSecretCount));
            }

            // Checkmarks are shown via text color now:
            // Gray text = unexplored/no mark
            // White text = white mark (cleared)
            // Green text = green mark (all secrets found)
        } else {
            // L-shape or irregular - draw each component and connect them
            // Calculate positions to avoid rounding gaps
            int cellScaled = (int)(CELL_SIZE * scale);
            int roomSizeScaled = (int)(ROOM_SIZE * scale);
            if (roomSizeScaled < 1) roomSizeScaled = 1;

            for (int[] component : room.getComponents()) {
                int gridX = component[0];
                int gridZ = component[1];

                int pixelX = gridToPixelX(gridX, baseX, scale);
                int pixelY = gridToPixelY(gridZ, baseY, scale);

                // Draw the room square
                context.fill(pixelX, pixelY, pixelX + roomSizeScaled, pixelY + roomSizeScaled, color);

                boolean hasRight = hasComponent(room, gridX + 1, gridZ);
                boolean hasBottom = hasComponent(room, gridX, gridZ + 1);
                boolean hasDiagonal = hasComponent(room, gridX + 1, gridZ + 1);

                // Calculate next cell positions to ensure seamless connections
                int nextPixelX = gridToPixelX(gridX + 1, baseX, scale);
                int nextPixelY = gridToPixelY(gridZ + 1, baseY, scale);

                // Right connector - extend to next cell to avoid gaps
                if (hasRight) {
                    int cx = pixelX + roomSizeScaled;
                    int cy = pixelY;
                    context.fill(cx, cy, nextPixelX, cy + roomSizeScaled, color);
                }

                // Bottom connector - extend to next cell to avoid gaps
                if (hasBottom) {
                    int cx = pixelX;
                    int cy = pixelY + roomSizeScaled;
                    context.fill(cx, cy, cx + roomSizeScaled, nextPixelY, color);
                }

                // Fill corner ONLY for 2x2 rooms (all 4 components exist)
                if (hasRight && hasBottom && hasDiagonal) {
                    int cx = pixelX + roomSizeScaled;
                    int cy = pixelY + roomSizeScaled;
                    context.fill(cx, cy, nextPixelX, nextPixelY, color);
                }
            }

            // Draw secret count and checkmark on first component
            int[] primary = room.getPrimaryComponent();
            int pixelX = gridToPixelX(primary[0], baseX, scale);
            int pixelY = gridToPixelY(primary[1], baseY, scale);

            // Draw secret count (format: found/max or just max if unknown)
            boolean hideSecretsIrregular = TeslaMapsConfig.get().hideSecretsWhenDone && room.getCheckmarkState() == CheckmarkState.GREEN;
            if (TeslaMapsConfig.get().showSecretCount && room.getSecrets() > 0 && !hideSecretsIrregular) {
                String secretText;
                int foundSecrets = room.getFoundSecrets();
                int maxSecrets = room.getSecrets();
                if (foundSecrets >= 0) {
                    secretText = foundSecrets + "/" + maxSecrets;
                } else {
                    secretText = String.valueOf(maxSecrets);
                }
                context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                        secretText, pixelX + 2, pixelY + 2, TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorSecretCount));
            }

            // Checkmarks shown via text color - no need to draw symbol
        }
    }

    private static boolean hasComponent(DungeonRoom room, int x, int z) {
        for (int[] comp : room.getComponents()) {
            if (comp[0] == x && comp[1] == z) return true;
        }
        return false;
    }

    /**
     * Draw room name - always shown, with full names and line wrapping
     */
    private static void drawRoomName(DrawContext context, DungeonRoom room, int baseX, int baseY, float scale) {
        if (!TeslaMapsConfig.get().showRoomNames) return;
        if (room.getName() == null) return;

        // Calculate bounding box of room
        int roomMinGX = Integer.MAX_VALUE, roomMinGZ = Integer.MAX_VALUE;
        int roomMaxGX = Integer.MIN_VALUE, roomMaxGZ = Integer.MIN_VALUE;

        for (int[] comp : room.getComponents()) {
            roomMinGX = Math.min(roomMinGX, comp[0]);
            roomMinGZ = Math.min(roomMinGZ, comp[1]);
            roomMaxGX = Math.max(roomMaxGX, comp[0]);
            roomMaxGZ = Math.max(roomMaxGZ, comp[1]);
        }

        int width = roomMaxGX - roomMinGX + 1;
        int height = roomMaxGZ - roomMinGZ + 1;

        // Calculate center of the room (in grid coordinates)
        double centerGridX = roomMinGX + (width - 1) / 2.0;
        double centerGridZ = roomMinGZ + (height - 1) / 2.0;

        // For L-shaped rooms (3 components, not rectangular), adjust center to be in the 2x1 part
        int componentCount = room.getComponents().size();
        if (componentCount == 3 && componentCount != width * height) {
            // Count components in each row and column
            int topRowCount = 0, bottomRowCount = 0;
            int leftColCount = 0, rightColCount = 0;
            for (int[] comp : room.getComponents()) {
                if (comp[1] == roomMinGZ) topRowCount++;
                if (comp[1] == roomMaxGZ) bottomRowCount++;
                if (comp[0] == roomMinGX) leftColCount++;
                if (comp[0] == roomMaxGX) rightColCount++;
            }

            // Adjust Z axis based on which row has more components (the 2x1 part)
            if (topRowCount > bottomRowCount) {
                centerGridZ = roomMinGZ;  // Center in top row
            } else if (bottomRowCount > topRowCount) {
                centerGridZ = roomMaxGZ;  // Center in bottom row
            }

            // Adjust X axis based on which column has more components (the 2x1 part)
            if (leftColCount > rightColCount) {
                centerGridX = roomMinGX;  // Center in left column
            } else if (rightColCount > leftColCount) {
                centerGridX = roomMaxGX;  // Center in right column
            }
        }

        // Convert grid center to pixel coordinates (accounting for dynamic offset)
        int centerX = gridToPixelX((int)centerGridX, baseX, scale) + (int)(ROOM_SIZE / 2.0 * scale);
        int centerY = gridToPixelY((int)centerGridZ, baseY, scale) + (int)(ROOM_SIZE / 2.0 * scale);

        // Get full display name
        String displayName = getDisplayName(room);

        // Use different colors based on room state
        int textColor = getTextColor(room);

        var textRenderer = MinecraftClient.getInstance().textRenderer;

        // Text scale (smaller text, with config multiplier)
        float textScale = 0.5f * TeslaMapsConfig.get().roomNameScale;

        // Split into lines if needed (split on spaces for wrapping)
        String[] words = displayName.split(" ");
        java.util.List<String> lines = new java.util.ArrayList<>();

        // Max width per line (based on room size in pixels, accounting for text scale)
        int maxLineWidth = (int)((width * CELL_SIZE - 4) * scale / textScale);

        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            if (currentLine.length() == 0) {
                currentLine.append(word);
            } else {
                String testLine = currentLine + " " + word;
                if (textRenderer.getWidth(testLine) <= maxLineWidth) {
                    currentLine.append(" ").append(word);
                } else {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                }
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        // Draw each line centered with scaling
        int lineHeight = (int)(textRenderer.fontHeight * textScale);
        int totalHeight = lines.size() * lineHeight;
        int startY = centerY - totalHeight / 2;

        var matrices = context.getMatrices();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int textWidth = textRenderer.getWidth(line);

            matrices.pushMatrix();
            matrices.translate(centerX, startY + i * lineHeight);
            matrices.scale(textScale, textScale);

            context.drawTextWithShadow(textRenderer, line, -textWidth / 2, 0, textColor);

            matrices.popMatrix();
        }
    }

    /**
     * Get display name - always show full names
     */
    private static String getDisplayName(DungeonRoom room) {
        String name = room.getName();
        if (name == null) return "?";

        RoomType type = room.getType();

        // Special display names
        if (type == RoomType.BLOOD) {
            return "Blood";
        }
        if (type == RoomType.ENTRANCE) {
            return "Start";
        }
        if (type == RoomType.FAIRY) {
            return "Fairy";
        }

        // Always show full name
        return name;
    }

    /**
     * Get text color based on room state (respects checkmark visibility settings)
     */
    private static int getTextColor(DungeonRoom room) {
        TeslaMapsConfig config = TeslaMapsConfig.get();

        // Blood room is always white
        if (room.getType() == RoomType.BLOOD) {
            return TeslaMapsConfig.parseColor(config.colorTextCleared);
        }

        CheckmarkState state = room.getCheckmarkState();

        // Green text when all secrets found (if enabled)
        if (state == CheckmarkState.GREEN && config.showGreenCheckmarks) {
            return TeslaMapsConfig.parseColor(config.colorTextGreen);
        }

        // White text when cleared (if enabled)
        if (state == CheckmarkState.WHITE && config.showWhiteCheckmarks) {
            return TeslaMapsConfig.parseColor(config.colorTextCleared);
        }

        // Red text when failed (if enabled)
        if (state == CheckmarkState.FAILED && config.showFailedCheckmarks) {
            return TeslaMapsConfig.parseColor(config.colorCheckFailed);
        }

        // Gray text when unexplored or checkmark type not shown
        return TeslaMapsConfig.parseColor(config.colorTextUnexplored);
    }

    private static int getRoomColor(DungeonRoom room) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        RoomType type = room.getType();
        if (type == null) type = RoomType.UNKNOWN;

        // Special room types ALWAYS show their color
        return switch (type) {
            case ENTRANCE -> TeslaMapsConfig.parseColor(config.colorEntrance);
            case BLOOD -> TeslaMapsConfig.parseColor(config.colorBlood);
            case TRAP -> TeslaMapsConfig.parseColor(config.colorTrap);
            case PUZZLE -> TeslaMapsConfig.parseColor(config.colorPuzzle);
            case FAIRY -> TeslaMapsConfig.parseColor(config.colorFairy);
            case YELLOW -> TeslaMapsConfig.parseColor(config.colorMiniboss);
            default -> {
                // Normal/Unknown rooms: explored = light brown, unexplored = dark gray
                if (room.getCheckmarkState() == CheckmarkState.UNEXPLORED) {
                    yield TeslaMapsConfig.parseColor(config.colorUnexplored);
                }
                yield TeslaMapsConfig.parseColor(config.colorNormal);
            }
        };
    }

    private static void drawCheckmark(DrawContext context, int x, int y, int size, CheckmarkState state) {
        TeslaMapsConfig config = TeslaMapsConfig.get();

        // Check if this checkmark type should be shown
        if (state == CheckmarkState.WHITE && !config.showWhiteCheckmarks) return;
        if (state == CheckmarkState.GREEN && !config.showGreenCheckmarks) return;
        if (state == CheckmarkState.FAILED && !config.showFailedCheckmarks) return;

        String symbol = switch (state) {
            case WHITE, GREEN -> "\u2714";
            case FAILED -> "\u2716";
            default -> "";
        };
        if (!symbol.isEmpty()) {
            int centerX = x + size / 2;
            int centerY = y + size / 2;
            int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(symbol);

            // Use config colors
            int color = switch (state) {
                case WHITE -> TeslaMapsConfig.parseColor(config.colorCheckWhite);
                case GREEN -> TeslaMapsConfig.parseColor(config.colorCheckGreen);
                case FAILED -> TeslaMapsConfig.parseColor(config.colorCheckFailed);
                default -> 0xFFFFFFFF;
            };

            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    symbol, centerX - textWidth / 2, centerY - 4, color);
        }
    }

    /**
     * Draw player markers:
     * 1. Always render local player from mc.player (faster, no map needed)
     * 2. For other players, use entity position if available, otherwise map decoration
     * 3. Show direction with arrow indicator
     */
    private static void drawPlayerMarker(DrawContext context, MinecraftClient mc, int baseX, int baseY, float scale) {
        if (mc.world == null || mc.player == null) return;

        // Apply head scale from config
        int headSize = (int)(8 * scale * TeslaMapsConfig.get().playerHeadScale);

        // 1. Render local player directly from mc.player (if enabled)
        if (TeslaMapsConfig.get().showSelfMarker) {
            renderPlayerHead(context, mc, baseX, baseY, scale, headSize,
                    mc.player.getX(), mc.player.getZ(), mc.player.getYaw(),
                    mc.player.getUuid(),
                    0x00000000, // No border for self (transparent)
                    null, // no name for self
                    true);
        }

        // 2. Render other players from map decorations (if enabled)
        if (!TeslaMapsConfig.get().showOtherPlayers) return;
        PlayerTracker.DungeonPlayer[] orderedPlayers = PlayerTracker.getPlayersOrdered();
        List<int[]> mapDecorations = MapScanner.getMapPlayerPositions();
        int playerIndex = 1; // Start at 1 (0 is local player)

        // Calculate local player's approximate map position to skip their marker
        double[] localRenderPos = null;
        int[] localGridPos = ComponentGrid.worldToGrid(mc.player.getX(), mc.player.getZ());
        if (localGridPos != null) {
            int[] worldCenter = ComponentGrid.gridToWorld(localGridPos[0], localGridPos[1]);
            double roomOffsetX = (mc.player.getX() - worldCenter[0] + ComponentGrid.HALF_ROOM_SIZE) / ComponentGrid.ROOM_SIZE;
            double roomOffsetZ = (mc.player.getZ() - worldCenter[1] + ComponentGrid.HALF_ROOM_SIZE) / ComponentGrid.ROOM_SIZE;
            double localMapX = localGridPos[0] * (MapScanner.mapRoomSize + MapScanner.mapGapSize) + roomOffsetX * MapScanner.mapRoomSize + MapScanner.mapCornerX;
            double localMapZ = localGridPos[1] * (MapScanner.mapRoomSize + MapScanner.mapGapSize) + roomOffsetZ * MapScanner.mapRoomSize + MapScanner.mapCornerY;
            localRenderPos = new double[]{localMapX, localMapZ};
        }

        // Find which decoration is closest to local player and skip it
        int closestIndex = -1;
        double closestDist = Double.MAX_VALUE;
        if (localRenderPos != null) {
            for (int i = 0; i < mapDecorations.size(); i++) {
                int[] pos = mapDecorations.get(i);
                double dist = Math.abs(pos[0] - localRenderPos[0]) + Math.abs(pos[1] - localRenderPos[1]);
                if (dist < closestDist) {
                    closestDist = dist;
                    closestIndex = i;
                }
            }
        }

        for (int i = 0; i < mapDecorations.size(); i++) {
            int[] mapPos = mapDecorations.get(i);

            // Skip the decoration closest to local player (that's us)
            if (i == closestIndex && closestDist < 20) continue;

            // Find the corresponding DungeonPlayer for this map decoration (if available)
            PlayerTracker.DungeonPlayer dungeonPlayer = null;
            if (orderedPlayers != null) {
                while (playerIndex < orderedPlayers.length) {
                    PlayerTracker.DungeonPlayer candidate = orderedPlayers[playerIndex];
                    if (candidate != null && candidate.isAlive()) {
                        dungeonPlayer = candidate;
                        playerIndex++;
                        break;
                    }
                    playerIndex++;
                }
            }

            double x, z;
            float rotation;
            java.util.UUID uuid = null;
            String name = null;
            // Try to get player info from DungeonPlayer
            if (dungeonPlayer != null) {
                uuid = dungeonPlayer.getUuid();
                name = TeslaMapsConfig.get().showPlayerNames ? dungeonPlayer.getName() : null;
            }

            // Try to get player entity for accurate position (only if UUID available)
            net.minecraft.entity.player.PlayerEntity playerEntity = null;
            if (uuid != null) {
                playerEntity = mc.world.getPlayerByUuid(uuid);
            }

            if (playerEntity != null) {
                // Use entity position (smooth, accurate)
                x = playerEntity.getX();
                z = playerEntity.getZ();
                rotation = playerEntity.getYaw();
            } else {
                // Fall back to map decoration position (works for far players)
                // Convert map coords directly to render position
                double[] renderPos = MapScanner.mapToRenderPosition(mapPos[0], mapPos[1], ROOM_SIZE, DOOR_SIZE);
                x = renderPos[0];
                z = renderPos[1];
                rotation = mapPos[2] * 22.5f - 180;

                // Render directly at map position instead of converting through world coords
                int pixelX = baseX + (int)((MAP_PADDING + x) * scale);
                int pixelY = baseY + (int)((MAP_PADDING + z) * scale);

                int halfSize = headSize / 2;

                // Draw head (use default skin if no UUID)
                if (TeslaMapsConfig.get().rotatePlayerHeads) {
                    PlayerHeadRenderer.drawPlayerHeadRotated(context, pixelX - halfSize, pixelY - halfSize, headSize, uuid, rotation);
                } else {
                    PlayerHeadRenderer.drawPlayerHead(context, pixelX - halfSize, pixelY - halfSize, headSize, uuid);
                }

                // Draw player name
                if (name != null) {
                    var textRenderer = mc.textRenderer;
                    int textWidth = textRenderer.getWidth(name);
                    float textScale = 0.5f;
                    var matrices = context.getMatrices();
                    matrices.pushMatrix();
                    matrices.translate(pixelX, pixelY + halfSize + 3);
                    matrices.scale(textScale, textScale);
                    context.drawTextWithShadow(textRenderer, name, -textWidth / 2, 0, 0xFFFFFFFF);
                    matrices.popMatrix();
                }

                continue; // Already rendered, skip the renderPlayerHead call
            }

            renderPlayerHead(context, mc, baseX, baseY, scale, headSize,
                    x, z, rotation,
                    uuid,
                    0x00000000, // No border
                    name,
                    false);
        }
    }

    /**
     * Render a single player head at the given world position with direction indicator.
     */
    private static void renderPlayerHead(DrawContext context, MinecraftClient mc,
                                          int baseX, int baseY, float scale, int headSize,
                                          double worldX, double worldZ, float yaw,
                                          java.util.UUID uuid, int borderColor, String name, boolean isLocal) {
        // Convert world position to map pixel position
        int[] gridPos = ComponentGrid.worldToGrid(worldX, worldZ);
        if (gridPos == null) return;

        int[] worldCenter = ComponentGrid.gridToWorld(gridPos[0], gridPos[1]);
        double roomOffsetX = (worldX - worldCenter[0] + ComponentGrid.HALF_ROOM_SIZE) / ComponentGrid.ROOM_SIZE;
        double roomOffsetZ = (worldZ - worldCenter[1] + ComponentGrid.HALF_ROOM_SIZE) / ComponentGrid.ROOM_SIZE;

        // Use dynamic offset for pixel position
        int pixelX = gridToPixelX(gridPos[0], baseX, scale) + (int)(roomOffsetX * ROOM_SIZE * scale);
        int pixelY = gridToPixelY(gridPos[1], baseY, scale) + (int)(roomOffsetZ * ROOM_SIZE * scale);

        int halfSize = headSize / 2;

        // Draw border (skip if transparent)
        if ((borderColor & 0xFF000000) != 0) {
            context.fill(pixelX - halfSize - 1, pixelY - halfSize - 1,
                         pixelX + halfSize + 1, pixelY + halfSize + 1, borderColor);
        }

        // Check if using heads or simple markers
        boolean useHeads = TeslaMapsConfig.get().useHeadsInsteadOfMarkers;
        float rotation = yaw + 180; // Convert yaw to map orientation

        if (useHeads) {
            // Draw head - optionally rotated
            if (TeslaMapsConfig.get().rotatePlayerHeads) {
                PlayerHeadRenderer.drawPlayerHeadRotated(context, pixelX - halfSize, pixelY - halfSize, headSize, uuid, rotation);
            } else {
                PlayerHeadRenderer.drawPlayerHead(context, pixelX - halfSize, pixelY - halfSize, headSize, uuid);

                // Draw direction arrow only when not rotating heads
                double rad = Math.toRadians(rotation);
                int arrowDist = halfSize + 3;
                int arrowX = pixelX + (int)(Math.sin(rad) * arrowDist);
                int arrowY = pixelY - (int)(Math.cos(rad) * arrowDist);

                // Draw arrow as small triangle
                int arrowSize = Math.max(2, (int)(2 * scale));
                context.fill(arrowX - arrowSize, arrowY - arrowSize, arrowX + arrowSize, arrowY + arrowSize, borderColor);
            }
        } else {
            // Draw a clean filled arrow marker (matches marker.png style)
            // Note: In 1.21.10, drawTexture() requires RenderPipeline as first parameter,
            // making simple texture rendering complex. Using programmatic triangle instead.
            int markerSize = Math.max(10, headSize);
            int markerColor = 0xFF00FF00; // Bright green (same as marker.png)

            // Rotation in radians
            double rad = Math.toRadians(rotation);
            double sin = Math.sin(rad);
            double cos = Math.cos(rad);

            // Simple arrow shape - tip and two back corners forming a triangle
            float tipLen = markerSize * 0.5f;     // Distance from center to tip
            float backLen = markerSize * 0.45f;   // Distance from center to back
            float sideLen = markerSize * 0.35f;   // Half-width at back

            // Tip point (front)
            int tipX = pixelX + (int)(sin * tipLen);
            int tipY = pixelY - (int)(cos * tipLen);

            // Back left corner
            int backLeftX = pixelX + (int)(-sin * backLen - cos * sideLen);
            int backLeftY = pixelY - (int)(-cos * backLen + sin * sideLen);

            // Back right corner
            int backRightX = pixelX + (int)(-sin * backLen + cos * sideLen);
            int backRightY = pixelY - (int)(-cos * backLen - sin * sideLen);

            // Draw single filled triangle (cleaner than arrow with notch)
            drawFilledTriangle(context, tipX, tipY, backLeftX, backLeftY, backRightX, backRightY, markerColor);
        }

        // Draw player name below head/marker
        if (name != null && TeslaMapsConfig.get().showPlayerNames) {
            var textRenderer = mc.textRenderer;
            int textWidth = textRenderer.getWidth(name);
            float textScale = 0.5f;
            var matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(pixelX, pixelY + halfSize + 3);
            matrices.scale(textScale, textScale);
            context.drawTextWithShadow(textRenderer, name, -textWidth / 2, 0, 0xFFFFFFFF);
            matrices.popMatrix();
        }
    }

    /**
     * Get color for dungeon class (Skyblocker-style colors).
     */
    private static int getDungeonClassColor(String dungeonClass) {
        if (dungeonClass == null) return 0xFF5555FF;
        return switch (dungeonClass.toUpperCase()) {
            case "HEALER" -> 0xFFFF55FF; // Pink
            case "MAGE" -> 0xFF55FFFF;   // Cyan
            case "BERSERK" -> 0xFFFF5555; // Red
            case "ARCHER" -> 0xFF55FF55; // Green
            case "TANK" -> 0xFFAAAAAA;   // Gray
            default -> 0xFF5555FF;        // Blue (default)
        };
    }

    /**
     * Draw a thick line between two points.
     */
    private static void drawThickLine(DrawContext context, int x1, int y1, int x2, int y2, int thickness, int color) {
        // Use Bresenham-style approach with thickness
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int steps = Math.max(dx, dy);
        if (steps == 0) {
            context.fill(x1 - thickness/2, y1 - thickness/2, x1 + thickness/2, y1 + thickness/2, color);
            return;
        }

        float xInc = (float)(x2 - x1) / steps;
        float yInc = (float)(y2 - y1) / steps;

        float x = x1;
        float y = y1;
        int half = thickness / 2;

        for (int i = 0; i <= steps; i++) {
            context.fill((int)x - half, (int)y - half, (int)x + half + 1, (int)y + half + 1, color);
            x += xInc;
            y += yInc;
        }
    }

    /**
     * Draw a filled triangle using horizontal scan lines.
     */
    private static void drawFilledTriangle(DrawContext context, int x1, int y1, int x2, int y2, int x3, int y3, int color) {
        // Sort vertices by Y coordinate
        if (y1 > y2) { int t = y1; y1 = y2; y2 = t; t = x1; x1 = x2; x2 = t; }
        if (y1 > y3) { int t = y1; y1 = y3; y3 = t; t = x1; x1 = x3; x3 = t; }
        if (y2 > y3) { int t = y2; y2 = y3; y3 = t; t = x2; x2 = x3; x3 = t; }

        // Degenerate case
        if (y3 == y1) {
            int minX = Math.min(x1, Math.min(x2, x3));
            int maxX = Math.max(x1, Math.max(x2, x3));
            context.fill(minX, y1, maxX + 1, y1 + 1, color);
            return;
        }

        // Fill using horizontal lines
        for (int y = y1; y <= y3; y++) {
            int startX, endX;

            if (y < y2) {
                // Upper half
                if (y2 == y1) {
                    startX = x1;
                } else {
                    startX = x1 + (x2 - x1) * (y - y1) / (y2 - y1);
                }
            } else {
                // Lower half
                if (y3 == y2) {
                    startX = x2;
                } else {
                    startX = x2 + (x3 - x2) * (y - y2) / (y3 - y2);
                }
            }

            // Long edge (y1 to y3)
            endX = x1 + (x3 - x1) * (y - y1) / (y3 - y1);

            // Make sure startX <= endX
            if (startX > endX) { int t = startX; startX = endX; endX = t; }

            context.fill(startX, y, endX + 1, y + 1, color);
        }
    }

    private static int darkenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int)(((color >> 16) & 0xFF) * factor);
        int g = (int)(((color >> 8) & 0xFF) * factor);
        int b = (int)((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int applyOpacity(int color, float opacity) {
        int a = (int)(((color >> 24) & 0xFF) * opacity);
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
