/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * Copyright (c) 2026 Teslanator20.
 *
 * See the LICENSE file in the project root for full terms.
 */
package com.teslamaps.render;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.DungeonScore;
import com.teslamaps.dungeon.MimicDetector;
import com.teslamaps.map.CheckmarkState;
import com.teslamaps.map.DoorType;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.map.RoomType;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.scanner.ComponentGrid;
import com.teslamaps.scanner.DoorScanner;
import com.teslamaps.scanner.MapScanner;
import com.teslamaps.utils.TabListUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class MapRenderer {
    private static final int ROOM_SIZE = 24;
    private static final int DOOR_SIZE = 4;
    private static final int MAP_PADDING = 8;
    private static final int CELL_SIZE = ROOM_SIZE + DOOR_SIZE;

    private static int minGridX = 0, maxGridX = 5;
    private static int minGridZ = 0, maxGridZ = 5;
    private static int totalSecrets = 0;

    private static int lastMapX, lastMapY, lastMapW, lastMapH;
    public static int mapX() { return lastMapX; }
    public static int mapY() { return lastMapY; }
    public static int mapW() { return lastMapW; }
    public static int mapH() { return lastMapH; }

    public record PlayerMarker(int x, int y, String name, boolean self) {}
    private static final List<PlayerMarker> playerMarkers = new ArrayList<>();
    public static List<PlayerMarker> getPlayerMarkers() { return playerMarkers; }

    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.mapEnabled) return;
        if (config.onlyShowInDungeon && !DungeonManager.isInDungeon()) return;
        if (config.mapOnlyInBoss && !DungeonManager.isInBoss()) return;
        renderAt(context, config.mapX, config.mapY, config.mapScale, false);
    }

    public static void renderAt(GuiGraphicsExtractor context, int baseX, int baseY, float scale, boolean leapMode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        TeslaMapsConfig config = TeslaMapsConfig.get();
        playerMarkers.clear();

        boolean legit = com.teslamaps.features.LegitMode.isFiltering();
        Collection<DungeonRoom> rooms = DungeonManager.getGrid().getAllRooms().stream()
                .filter(r -> r.getType() != RoomType.UNKNOWN && !"Unknown".equals(r.getName()))
                .filter(r -> !legit || r.isExplored())
                .toList();
        calculateDungeonBounds(rooms);

        int gridWidth = maxGridX - minGridX + 1;
        int gridHeight = maxGridZ - minGridZ + 1;
        int mapWidth = (int)((MAP_PADDING * 2 + gridWidth * CELL_SIZE) * scale);
        int mapHeight = (int)((MAP_PADDING * 2 + gridHeight * CELL_SIZE) * scale);
        int infoHeight = leapMode ? 0 : (int)(36 * scale * config.infoTextScale);
        int totalHeight = mapHeight + infoHeight;

        lastMapX = baseX; lastMapY = baseY; lastMapW = mapWidth; lastMapH = mapHeight;

        if (config.showMapBackground || leapMode) {
            drawBackground(context, baseX, baseY, mapWidth, totalHeight);
        }
        drawRoomConnections(context, rooms, baseX, baseY, scale, legit);

        Set<DungeonRoom> drawnRooms = new HashSet<>();
        for (DungeonRoom room : rooms) {
            if (!drawnRooms.contains(room)) { drawRoom(context, room, baseX, baseY, scale); drawnRooms.add(room); }
        }
        if (legit) {
            drawGuessRooms(context, baseX, baseY, scale);
        }
        drawnRooms.clear();
        for (DungeonRoom room : rooms) {
            if (!drawnRooms.contains(room) && room.isIdentified()) { drawRoomName(context, room, baseX, baseY, scale); drawnRooms.add(room); }
        }

        if (config.showPlayerMarker || leapMode) {
            drawPlayerMarker(context, mc, baseX, baseY, scale);
        }
        if (!leapMode) {
            drawInfoText(context, mc, baseX, baseY + mapHeight + 2, mapWidth, scale * config.infoTextScale);
        }
    }

    private static void calculateDungeonBounds(Collection<DungeonRoom> rooms) {
        // Fixed full grid so the map border stays the same size from the start
        // instead of growing as rooms get discovered.
        minGridX = 0; maxGridX = ComponentGrid.GRID_SIZE - 1;
        minGridZ = 0; maxGridZ = ComponentGrid.GRID_SIZE - 1;
        totalSecrets = 0;

        Set<DungeonRoom> counted = new HashSet<>();
        for (DungeonRoom room : rooms) {
            if (!counted.contains(room) && room.getSecrets() > 0) {
                totalSecrets += room.getSecrets();
                counted.add(room);
            }
        }
    }

    private static void drawInfoText(GuiGraphicsExtractor context, Minecraft mc, int x, int y, int width, float scale) {
        var textRenderer = mc.font;
        TeslaMapsConfig config = TeslaMapsConfig.get();

        // Render in scaled local space so the info box shrinks/grows with the map.
        int lw = (int)(width / scale); // logical (unscaled) width
        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);

        double secretsPercent = TabListUtils.getSecretsPercentage();

        int foundSecretsOverall = TabListUtils.getSecretsFound();
        if (foundSecretsOverall < 0) {
            foundSecretsOverall = (int) Math.round(totalSecrets * secretsPercent / 100.0);
        }

        int crypts = TabListUtils.getCryptsFound();
        if (crypts < 0) crypts = 0;
        double neededSecretsPercent = DungeonScore.getNeededSecretsPercentFor300(crypts);
        int neededSecretsOverall = (int) Math.ceil(totalSecrets * neededSecretsPercent / 100.0);

        int remainingSecrets = Math.max(0, neededSecretsOverall - foundSecretsOverall);

        String secretsText = secretsPercent >= 0
                ? String.format("Secrets: %d/%d/%d", foundSecretsOverall, remainingSecrets, totalSecrets)
                : "Secrets: ?/?/" + totalSecrets;

        int secretsColor = (remainingSecrets == 0) ? 0xFF55FF55 : 0xFFFFFFFF;
        context.text(textRenderer, secretsText, 4, 2, secretsColor);

        if (config.showCrypts) {
            int cryptsFound = TabListUtils.getCryptsFound();
            String cryptsText;

            if (cryptsFound >= 0) {
                if (config.showTotalCrypts) {
                    int totalCrypts = DungeonManager.getTotalCrypts();
                    cryptsText = "Crypts: " + cryptsFound + "/" + totalCrypts;
                } else {
                    cryptsText = "Crypts: " + cryptsFound;
                }
            } else {
                if (config.showTotalCrypts) {
                    int totalCrypts = DungeonManager.getTotalCrypts();
                    cryptsText = "Crypts: ?/" + totalCrypts;
                } else {
                    cryptsText = "Crypts: ?";
                }
            }

            int cryptsColor;
            if (cryptsFound >= 5) {
                cryptsColor = 0xFF55FF55;
            } else if (cryptsFound == 4) {
                cryptsColor = 0xFFFF8800;
            } else {
                cryptsColor = 0xFFFF5555;
            }

            int cryptsWidth = textRenderer.width(cryptsText);
            context.text(textRenderer, cryptsText, lw - cryptsWidth - 4, 2, cryptsColor);
        }

        int line2Y = 12;

        if (config.showMimicStatus) {
            boolean mimicKilled = MimicDetector.isMimicKilled();
            boolean mimicFound = !MimicDetector.getMimicRooms().isEmpty();

            String mimicText;
            int mimicColor;

            if (mimicKilled) {
                mimicText = "Mimic: \u2714";
                mimicColor = 0xFF55FF55;
            } else if (mimicFound) {
                mimicText = "Mimic: \u2718";
                mimicColor = 0xFFFF5555;
            } else {
                mimicText = "Mimic: ?";
                mimicColor = 0xFFAAAAAA;
            }

            context.text(textRenderer, mimicText, 4, line2Y, mimicColor);

            if (config.showPrinceStatus && runHasPrinceRoom()) {
                boolean killed = DungeonScore.isPrinceKilled();
                String princeText = killed ? "Prince: \u2714" : "Prince: \u2718";
                int princeColor = killed ? 0xFF55FF55 : 0xFFFF5555;
                int mimicWidth = textRenderer.width(mimicText);
                context.text(textRenderer, princeText, 4 + mimicWidth + 10, line2Y, princeColor);
            }
        } else if (config.showPrinceStatus && runHasPrinceRoom()) {
            boolean killed = DungeonScore.isPrinceKilled();
            String princeText = killed ? "Prince: \u2714" : "Prince: \u2718";
            int princeColor = killed ? 0xFF55FF55 : 0xFFFF5555;
            context.text(textRenderer, princeText, 4, line2Y, princeColor);
        }

        if (config.showDungeonScore) {
            int score = DungeonScore.calculateScore();
            String scoreText = "Score: " + score;

            int scoreColor;
            if (score >= 300) {
                scoreColor = 0xFF55FF55; // Green
            } else if (score >= 270) {
                scoreColor = 0xFFFFFF55; // Yellow
            } else if (score >= 230) {
                scoreColor = 0xFFFF8C00; // Orange
            } else {
                scoreColor = 0xFFFFFFFF;
            }

            int scoreWidth = textRenderer.width(scoreText);
            context.text(textRenderer, scoreText, lw - scoreWidth - 4, line2Y, scoreColor);
        }

        matrices.popMatrix();
    }

    private static int getRequiredSecretPercentForDisplay() {
        DungeonFloor floor = DungeonManager.getCurrentFloor();
        if (floor.isMaster()) return 100;

        return switch (floor) {
            case F1 -> 30;
            case F2 -> 40;
            case F3 -> 50;
            case F4 -> 60;
            case F5 -> 70;
            case F6 -> 85;
            case F7 -> 100;
            default -> 100;
        };
    }

    private static void drawBackground(GuiGraphicsExtractor context, int baseX, int baseY, int width, int height) {
        int bgColor = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorBackground);
        int borderColor = 0xFF333333; // Border is slightly lighter than background
        context.fill(baseX, baseY, baseX + width, baseY + height, bgColor);
        context.fill(baseX, baseY, baseX + width, baseY + 2, borderColor);
        context.fill(baseX, baseY + height - 2, baseX + width, baseY + height, borderColor);
        context.fill(baseX, baseY, baseX + 2, baseY + height, borderColor);
        context.fill(baseX + width - 2, baseY, baseX + width, baseY + height, borderColor);
    }

    private static int gridToPixelX(int gridX, int baseX, float scale) {
        return baseX + (int)((MAP_PADDING + (gridX - minGridX) * CELL_SIZE) * scale);
    }

    private static int gridToPixelY(int gridZ, int baseY, float scale) {
        return baseY + (int)((MAP_PADDING + (gridZ - minGridZ) * CELL_SIZE) * scale);
    }

    private static void drawRoomConnections(GuiGraphicsExtractor context, Collection<DungeonRoom> rooms, int baseX, int baseY, float scale, boolean legit) {
        int doorThickness = (int)(DOOR_SIZE * scale * 2);
        if (doorThickness < 5) doorThickness = 5;

        Set<String> drawnConnections = new HashSet<>();

        for (DungeonRoom room : rooms) {
            for (int[] comp : room.getComponents()) {
                int gx = comp[0];
                int gz = comp[1];

                DungeonRoom rightRoom = DungeonManager.getGrid().getRoom(gx + 1, gz);
                if (rightRoom != null && rightRoom != room && (!legit || rightRoom.isExplored())) {
                    String key = gx + "," + gz + "-" + (gx+1) + "," + gz;
                    if (!drawnConnections.contains(key)) {
                        DoorType doorType = DoorScanner.getDoorType(gx, gz, gx + 1, gz);
                        boolean bothScanned = room.getName() != null && rightRoom.getName() != null;
                        if (doorType != DoorType.NONE && bothScanned) {
                            int color = getDoorColor(doorType);
                            int x1 = gridToPixelX(gx, baseX, scale) + (int)(ROOM_SIZE * scale) - 1;
                            int yCenter = gridToPixelY(gz, baseY, scale) + (int)(ROOM_SIZE/2 * scale);
                            int y1 = yCenter - doorThickness / 2;
                            int x2 = gridToPixelX(gx + 1, baseX, scale) + 1;
                            int y2 = yCenter + doorThickness / 2;
                            context.fill(x1, y1, x2, y2, color);
                        }
                        drawnConnections.add(key);
                    }
                }

                DungeonRoom bottomRoom = DungeonManager.getGrid().getRoom(gx, gz + 1);
                if (bottomRoom != null && bottomRoom != room && (!legit || bottomRoom.isExplored())) {
                    String key = gx + "," + gz + "-" + gx + "," + (gz+1);
                    if (!drawnConnections.contains(key)) {
                        DoorType doorType = DoorScanner.getDoorType(gx, gz, gx, gz + 1);
                        boolean bothScanned = room.getName() != null && bottomRoom.getName() != null;
                        if (doorType != DoorType.NONE && bothScanned) {
                            int color = getDoorColor(doorType);
                            int xCenter = gridToPixelX(gx, baseX, scale) + (int)(ROOM_SIZE/2 * scale);
                            int x1 = xCenter - doorThickness / 2;
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

    // Legit Mode: a plain dark-grey 1x1 placeholder behind each door to an undiscovered cell.
    private static void drawGuessRooms(GuiGraphicsExtractor context, int baseX, int baseY, float scale) {
        List<com.teslamaps.map.LegitGuess.Door> guessDoors = com.teslamaps.map.LegitGuess.getGuessDoors();
        if (guessDoors.isEmpty()) return;

        int gray = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorUnexplored);
        int roomSize = (int) (ROOM_SIZE * scale);

        Set<Long> drawn = new HashSet<>();
        for (com.teslamaps.map.LegitGuess.Door d : guessDoors) {
            long ck = ((long) d.nx() << 32) | (d.nz() & 0xffffffffL);
            if (!drawn.add(ck)) continue;
            int px = gridToPixelX(d.nx(), baseX, scale);
            int py = gridToPixelY(d.nz(), baseY, scale);
            context.fill(px, py, px + roomSize, py + roomSize, gray);
        }

        for (com.teslamaps.map.LegitGuess.Door d : guessDoors) {
            drawDoorBetween(context, d.gx(), d.gz(), d.nx(), d.nz(), getDoorColor(d.type()), baseX, baseY, scale);
        }
    }

    private static void drawDoorBetween(GuiGraphicsExtractor context, int gx, int gz, int nx, int nz, int color, int baseX, int baseY, float scale) {
        int doorThickness = (int) (DOOR_SIZE * scale * 2);
        if (doorThickness < 5) doorThickness = 5;

        int ax, az, bx, bz;
        if (gx != nx) { ax = Math.min(gx, nx); bx = Math.max(gx, nx); az = gz; bz = gz; }
        else { az = Math.min(gz, nz); bz = Math.max(gz, nz); ax = gx; bx = gx; }

        if (bx == ax + 1) { // horizontal door
            int x1 = gridToPixelX(ax, baseX, scale) + (int) (ROOM_SIZE * scale) - 1;
            int yCenter = gridToPixelY(az, baseY, scale) + (int) (ROOM_SIZE / 2 * scale);
            int x2 = gridToPixelX(bx, baseX, scale) + 1;
            context.fill(x1, yCenter - doorThickness / 2, x2, yCenter + doorThickness / 2, color);
        } else { // vertical door
            int xCenter = gridToPixelX(ax, baseX, scale) + (int) (ROOM_SIZE / 2 * scale);
            int y1 = gridToPixelY(az, baseY, scale) + (int) (ROOM_SIZE * scale) - 1;
            int y2 = gridToPixelY(bz, baseY, scale) + 1;
            context.fill(xCenter - doorThickness / 2, y1, xCenter + doorThickness / 2, y2, color);
        }
    }

    private static void drawRoom(GuiGraphicsExtractor context, DungeonRoom room, int baseX, int baseY, float scale) {
        int color = getRoomColor(room);

        int roomMinGX = Integer.MAX_VALUE, roomMinGZ = Integer.MAX_VALUE;
        int roomMaxGX = Integer.MIN_VALUE, roomMaxGZ = Integer.MIN_VALUE;

        for (int[] component : room.getComponents()) {
            roomMinGX = Math.min(roomMinGX, component[0]);
            roomMinGZ = Math.min(roomMinGZ, component[1]);
            roomMaxGX = Math.max(roomMaxGX, component[0]);
            roomMaxGZ = Math.max(roomMaxGZ, component[1]);
        }

        int componentCount = room.getComponents().size();
        int width = roomMaxGX - roomMinGX + 1;
        int height = roomMaxGZ - roomMinGZ + 1;

        if (componentCount == width * height) {
            int pixelX = gridToPixelX(roomMinGX, baseX, scale);
            int pixelY = gridToPixelY(roomMinGZ, baseY, scale);
            int pixelW = (int)((width * CELL_SIZE - DOOR_SIZE) * scale);
            int pixelH = (int)((height * CELL_SIZE - DOOR_SIZE) * scale);

            context.fill(pixelX, pixelY, pixelX + pixelW, pixelY + pixelH, color);

            TeslaMapsConfig config = TeslaMapsConfig.get();
            boolean hideSecrets = config.hideSecretsWhenDone && room.getCheckmarkState() == CheckmarkState.GREEN;
            boolean hideOneSecret = config.hideOneSecretCount && room.getSecrets() == 1;
            if (config.showSecretCount && room.getSecrets() > 0 && !hideSecrets && !hideOneSecret) {
                String secretText;
                int foundSecrets = room.getFoundSecrets();
                int maxSecrets = room.getSecrets();

                if (foundSecrets >= 0) {
                    secretText = foundSecrets + "/" + maxSecrets;
                } else {
                    secretText = String.valueOf(maxSecrets);
                }
                context.text(Minecraft.getInstance().font,
                        secretText, pixelX + 2, pixelY + 2, getTextColor(room));
            }

        } else {
            int cellScaled = (int)(CELL_SIZE * scale);
            int roomSizeScaled = (int)(ROOM_SIZE * scale);
            if (roomSizeScaled < 1) roomSizeScaled = 1;

            for (int[] component : room.getComponents()) {
                int gridX = component[0];
                int gridZ = component[1];

                int pixelX = gridToPixelX(gridX, baseX, scale);
                int pixelY = gridToPixelY(gridZ, baseY, scale);

                context.fill(pixelX, pixelY, pixelX + roomSizeScaled, pixelY + roomSizeScaled, color);

                boolean hasRight = hasComponent(room, gridX + 1, gridZ);
                boolean hasBottom = hasComponent(room, gridX, gridZ + 1);
                boolean hasDiagonal = hasComponent(room, gridX + 1, gridZ + 1);

                int nextPixelX = gridToPixelX(gridX + 1, baseX, scale);
                int nextPixelY = gridToPixelY(gridZ + 1, baseY, scale);

                if (hasRight) {
                    int cx = pixelX + roomSizeScaled;
                    int cy = pixelY;
                    context.fill(cx, cy, nextPixelX, cy + roomSizeScaled, color);
                }

                if (hasBottom) {
                    int cx = pixelX;
                    int cy = pixelY + roomSizeScaled;
                    context.fill(cx, cy, cx + roomSizeScaled, nextPixelY, color);
                }

                if (hasRight && hasBottom && hasDiagonal) {
                    int cx = pixelX + roomSizeScaled;
                    int cy = pixelY + roomSizeScaled;
                    context.fill(cx, cy, nextPixelX, nextPixelY, color);
                }
            }

            int[] primary = room.getPrimaryComponent();
            int pixelX = gridToPixelX(primary[0], baseX, scale);
            int pixelY = gridToPixelY(primary[1], baseY, scale);

            TeslaMapsConfig configIrregular = TeslaMapsConfig.get();
            boolean hideSecretsIrregular = configIrregular.hideSecretsWhenDone && room.getCheckmarkState() == CheckmarkState.GREEN;
            boolean hideOneSecretIrregular = configIrregular.hideOneSecretCount && room.getSecrets() == 1;
            if (configIrregular.showSecretCount && room.getSecrets() > 0 && !hideSecretsIrregular && !hideOneSecretIrregular) {
                String secretText;
                int foundSecrets = room.getFoundSecrets();
                int maxSecrets = room.getSecrets();

                if (foundSecrets >= 0) {
                    secretText = foundSecrets + "/" + maxSecrets;
                } else {
                    secretText = String.valueOf(maxSecrets);
                }
                context.text(Minecraft.getInstance().font,
                        secretText, pixelX + 2, pixelY + 2, getTextColor(room));
            }

        }

        drawPrinceIcon(context, room, baseX, baseY, scale);
    }

    private static boolean runHasPrinceRoom() {
        for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
            if (room.getRoomData() != null && room.getRoomData().getPrince()) return true;
        }
        return false;
    }

    private static final net.minecraft.resources.Identifier[] CROWN_TEX = {
            net.minecraft.resources.Identifier.fromNamespaceAndPath("teslamaps", "textures/map/crown1.png"),
            net.minecraft.resources.Identifier.fromNamespaceAndPath("teslamaps", "textures/map/crown2.png"),
            net.minecraft.resources.Identifier.fromNamespaceAndPath("teslamaps", "textures/map/crown3.png"),
            net.minecraft.resources.Identifier.fromNamespaceAndPath("teslamaps", "textures/map/crown4.png"),
            net.minecraft.resources.Identifier.fromNamespaceAndPath("teslamaps", "textures/map/crown5.png"),
    };

    private static void drawPrinceIcon(GuiGraphicsExtractor context, DungeonRoom room, int baseX, int baseY, float scale) {
        if (!TeslaMapsConfig.get().showPrinceIcon) return;
        if (DungeonScore.isPrinceKilled()) return;
        if (room.getRoomData() == null || !room.getRoomData().getPrince()) return;

        int[] primary = room.getPrimaryComponent();
        int cellX = gridToPixelX(primary[0], baseX, scale);
        int cellY = gridToPixelY(primary[1], baseY, scale);
        int cellSize = (int)(ROOM_SIZE * scale);

        int variant = TeslaMapsConfig.get().princeCrownVariant;
        if (variant >= 1 && variant <= CROWN_TEX.length) {
            int sz = Math.max(11, (int)(14 * scale));
            int px = cellX + cellSize - sz - 1;
            int py = cellY + 1;
            context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    CROWN_TEX[variant - 1], px, py, 0f, 0f, sz, sz, 128, 128, 128, 128);
            return;
        }

        int s = Math.max(8, (int)(9 * scale));
        int ix = cellX + cellSize - s - 2; // top-right corner, away from secret count
        int iy = cellY + 2;
        int gold = 0xFFFFD700;
        int shade = 0xFFB8860B;
        int jewel = 0xFFE03A3A;

        int bandH = Math.max(2, s / 4);
        int bandTop = iy + s - bandH;
        int peakH = bandTop - iy;

        context.fill(ix, bandTop, ix + s, iy + s, gold);              // base band
        context.fill(ix, iy + s - 1, ix + s, iy + s, shade);          // band bottom edge

        int mid = ix + s / 2;
        fillPointUp(context, ix, bandTop, peakH * 3 / 5, gold);       // left point (shorter)
        fillPointUp(context, ix + s - 1, bandTop, peakH * 3 / 5, gold); // right point (shorter)
        fillPointUp(context, mid, bandTop, peakH, gold);              // center point (tallest)

        context.fill(mid, iy, mid + 1, iy + 1, jewel);               // center jewel tip
    }

    // triangle pointing up: tip at (tipX, baseY-height), base on the band
    private static void fillPointUp(GuiGraphicsExtractor context, int tipX, int baseY, int height, int color) {
        for (int row = 0; row < height; row++) {
            int y = baseY - 1 - row;
            int half = (row * 2) / Math.max(1, height) + 1; // widens toward the band
            context.fill(tipX - half, y, tipX + half + 1, y + 1, color);
        }
    }

    private static boolean hasComponent(DungeonRoom room, int x, int z) {
        for (int[] comp : room.getComponents()) {
            if (comp[0] == x && comp[1] == z) return true;
        }
        return false;
    }

    private static boolean holdingLeap() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        String id = com.teslamaps.utils.ItemUtil.skyblockId(mc.player.getMainHandItem());
        return id.equals("SPIRIT_LEAP") || id.equals("INFINITE_SPIRIT_LEAP");
    }

    private static void drawRoomName(GuiGraphicsExtractor context, DungeonRoom room, int baseX, int baseY, float scale) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.showRoomNames) return;
        if (config.roomNamesOnlyWithLeap && !holdingLeap()) return;
        if (room.getName() == null) return;

        if (config.showNamesOnlyForPuzzles && room.getType() != com.teslamaps.map.RoomType.PUZZLE) {
            return; // Only show puzzle room names
        }

        if (config.hideEntranceBloodFairyNames) {
            var type = room.getType();
            if (type == com.teslamaps.map.RoomType.ENTRANCE ||
                type == com.teslamaps.map.RoomType.BLOOD ||
                type == com.teslamaps.map.RoomType.FAIRY) {
                return; // Hide entrance/blood/fairy names
            }
        }

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

        int centerX, centerY;

        int componentCount = room.getComponents().size();
        if (componentCount == 3 && componentCount != width * height) {
            int topRowCount = 0, bottomRowCount = 0;
            int leftColCount = 0, rightColCount = 0;
            for (int[] comp : room.getComponents()) {
                if (comp[1] == roomMinGZ) topRowCount++;
                if (comp[1] == roomMaxGZ) bottomRowCount++;
                if (comp[0] == roomMinGX) leftColCount++;
                if (comp[0] == roomMaxGX) rightColCount++;
            }

            int partMinGX = roomMinGX, partMaxGX = roomMaxGX;
            int partMinGZ = roomMinGZ, partMaxGZ = roomMaxGZ;

            if (topRowCount == 2 && bottomRowCount == 1) {
                partMaxGZ = roomMinGZ;  // Just the top row
            } else if (bottomRowCount == 2 && topRowCount == 1) {
                partMinGZ = roomMaxGZ;  // Just the bottom row
            }

            if (leftColCount == 2 && rightColCount == 1) {
                partMaxGX = roomMinGX;  // Just the left column
            } else if (rightColCount == 2 && leftColCount == 1) {
                partMinGX = roomMaxGX;  // Just the right column
            }

            int minPixelX = gridToPixelX(partMinGX, baseX, scale);
            int maxPixelX = gridToPixelX(partMaxGX, baseX, scale) + (int)(ROOM_SIZE * scale);
            int minPixelY = gridToPixelY(partMinGZ, baseY, scale);
            int maxPixelY = gridToPixelY(partMaxGZ, baseY, scale) + (int)(ROOM_SIZE * scale);

            centerX = (minPixelX + maxPixelX) / 2;
            centerY = (minPixelY + maxPixelY) / 2;
        } else {
            int minPixelX = gridToPixelX(roomMinGX, baseX, scale);
            int maxPixelX = gridToPixelX(roomMaxGX, baseX, scale) + (int)(ROOM_SIZE * scale);
            int minPixelY = gridToPixelY(roomMinGZ, baseY, scale);
            int maxPixelY = gridToPixelY(roomMaxGZ, baseY, scale) + (int)(ROOM_SIZE * scale);

            centerX = (minPixelX + maxPixelX) / 2;
            centerY = (minPixelY + maxPixelY) / 2;
        }

        String displayName = getDisplayName(room);

        int textColor = getTextColor(room);

        var textRenderer = Minecraft.getInstance().font;

        float textScale = 0.5f * TeslaMapsConfig.get().roomNameScale;

        String[] words = displayName.split(" ");
        java.util.List<String> lines = new java.util.ArrayList<>();

        int maxLineWidth = (int)((width * CELL_SIZE - 4) * scale / textScale);

        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            if (currentLine.length() == 0) {
                currentLine.append(word);
            } else {
                String testLine = currentLine + " " + word;
                if (textRenderer.width(testLine) <= maxLineWidth) {
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

        int lineHeight = (int)(textRenderer.lineHeight * textScale);
        int totalHeight = lines.size() * lineHeight;
        int startY = centerY - totalHeight / 2;

        var matrices = context.pose();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int textWidth = textRenderer.width(line);

            matrices.pushMatrix();
            matrices.translate(centerX, startY + i * lineHeight);
            matrices.scale(textScale, textScale);

            context.text(textRenderer, line, -textWidth / 2, 0, textColor);

            matrices.popMatrix();
        }
    }

    private static String getDisplayName(DungeonRoom room) {
        String name = room.getName();
        if (name == null) return "?";

        RoomType type = room.getType();

        if (type == RoomType.BLOOD) {
            return "Blood";
        }
        if (type == RoomType.ENTRANCE) {
            return "Start";
        }
        if (type == RoomType.FAIRY) {
            return "Fairy";
        }

        return name;
    }

    private static int getTextColor(DungeonRoom room) {
        TeslaMapsConfig config = TeslaMapsConfig.get();

        if (room.getType() == RoomType.BLOOD) {
            return TeslaMapsConfig.parseColor(config.colorTextCleared);
        }

        if (!config.showCheckmarks) {
            return TeslaMapsConfig.parseColor(config.colorTextUnexplored);
        }

        CheckmarkState state = room.getCheckmarkState();

        if (state == CheckmarkState.GREEN && config.showGreenCheckmarks) {
            return TeslaMapsConfig.parseColor(config.colorTextGreen);
        }

        if (state == CheckmarkState.WHITE && config.showWhiteCheckmarks) {
            return TeslaMapsConfig.parseColor(config.colorTextCleared);
        }

        if (state == CheckmarkState.FAILED && config.showFailedCheckmarks) {
            return TeslaMapsConfig.parseColor(config.colorCheckFailed);
        }

        return TeslaMapsConfig.parseColor(config.colorTextUnexplored);
    }

    private static int getRoomColor(DungeonRoom room) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        RoomType type = room.getType();
        if (type == null) type = RoomType.UNKNOWN;

        if (MimicDetector.isMimicRoom(room) && !MimicDetector.isMimicKilled()) {
            return 0xFFFF0000; // Bright red for mimic room
        }

        return switch (type) {
            case ENTRANCE -> TeslaMapsConfig.parseColor(config.colorEntrance);
            case BLOOD -> TeslaMapsConfig.parseColor(config.colorBlood);
            case TRAP -> TeslaMapsConfig.parseColor(config.colorTrap);
            case PUZZLE -> {
                if (room.getCheckmarkState() == CheckmarkState.UNEXPLORED) {
                    yield TeslaMapsConfig.parseColor(config.colorPuzzleUnexplored);
                }
                yield TeslaMapsConfig.parseColor(config.colorPuzzle);
            }
            case FAIRY -> TeslaMapsConfig.parseColor(config.colorFairy);
            case YELLOW -> TeslaMapsConfig.parseColor(config.colorMiniboss);
            default -> {
                if (room.getCheckmarkState() == CheckmarkState.UNEXPLORED) {
                    yield TeslaMapsConfig.parseColor(config.colorUnexplored);
                }
                yield TeslaMapsConfig.parseColor(config.colorNormal);
            }
        };
    }

    private static void drawCheckmark(GuiGraphicsExtractor context, int x, int y, int size, CheckmarkState state) {
        TeslaMapsConfig config = TeslaMapsConfig.get();

        if (!config.showCheckmarks) return; // master switch

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
            int textWidth = Minecraft.getInstance().font.width(symbol);

            int color = switch (state) {
                case WHITE -> TeslaMapsConfig.parseColor(config.colorCheckWhite);
                case GREEN -> TeslaMapsConfig.parseColor(config.colorCheckGreen);
                case FAILED -> TeslaMapsConfig.parseColor(config.colorCheckFailed);
                default -> 0xFFFFFFFF;
            };

            context.text(Minecraft.getInstance().font,
                    symbol, centerX - textWidth / 2, centerY - 4, color);
        }
    }

    private static void drawPlayerMarker(GuiGraphicsExtractor context, Minecraft mc, int baseX, int baseY, float scale) {
        if (mc.level == null || mc.player == null) return;

        TeslaMapsConfig config = TeslaMapsConfig.get();
        int headSize = (int)(8 * scale * config.playerHeadScale);

        if (config.showSelfMarker) {
            String selfName = config.showPlayerNames ? mc.player.getName().getString() : null;
            renderPlayerHead(context, mc, baseX, baseY, scale, headSize,
                    mc.player.getX(), mc.player.getZ(), mc.player.getYRot(),
                    mc.player.getUUID(), 0x00000000, selfName, true, mc.player.getName().getString());
        }

        if (!config.showOtherPlayers) return;

        List<int[]> mapPositions = MapScanner.getMapPlayerPositions();
        PlayerTracker.DungeonPlayer[] players = PlayerTracker.getPlayersOrdered();
        int playerIdx = 1; // Start at 1, index 0 is self

        for (int[] pos : mapPositions) {
            int mapX = pos[0];
            int mapZ = pos[1];
            int rotation = pos[2];
            int isLocal = pos[3];

            if (isLocal == 1) continue;

            PlayerTracker.DungeonPlayer dungeonPlayer = null;
            while (playerIdx < players.length) {
                dungeonPlayer = players[playerIdx];
                playerIdx++;
                if (dungeonPlayer != null && dungeonPlayer.isAlive()) break;
                dungeonPlayer = null;
            }

            java.util.UUID uuid = dungeonPlayer != null ? dungeonPlayer.getUuid() : null;
            String name = (dungeonPlayer != null && config.showPlayerNames) ? dungeonPlayer.getName() : null;

            net.minecraft.world.entity.player.Player playerEntity = uuid != null ? mc.level.getPlayerByUUID(uuid) : null;

            String recordName = dungeonPlayer != null ? dungeonPlayer.getName() : null;
            if (playerEntity != null) {
                renderPlayerHead(context, mc, baseX, baseY, scale, headSize,
                        playerEntity.getX(), playerEntity.getZ(), playerEntity.getYRot(),
                        uuid, 0x00000000, name, false, recordName);
            } else {
                double[] renderPos = MapScanner.mapToRenderPosition(mapX, mapZ, ROOM_SIZE, DOOR_SIZE);
                int pixelX = baseX + (int)(renderPos[0] * scale);
                int pixelY = baseY + (int)(renderPos[1] * scale);
                float yaw = rotation * 22.5f;

                renderPlayerHeadAtPixel(context, pixelX, pixelY, headSize, yaw, uuid, name, false, recordName);
            }
        }
    }

    private static void renderPlayerHeadAtPixel(GuiGraphicsExtractor context, int pixelX, int pixelY, int headSize, float yaw,
                                                 java.util.UUID uuid, String name, boolean isLocal, String recordName) {
        if (recordName != null) playerMarkers.add(new PlayerMarker(pixelX, pixelY, recordName, isLocal));
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int halfSize = headSize / 2;

        if (config.useHeadsInsteadOfMarkers && uuid != null) {
            if (config.rotatePlayerHeads) {
                PlayerHeadRenderer.drawPlayerHeadRotated(context, pixelX - halfSize, pixelY - halfSize, headSize, uuid, yaw);
            } else {
                PlayerHeadRenderer.drawPlayerHead(context, pixelX - halfSize, pixelY - halfSize, headSize, uuid);
            }
        } else {
            int color = isLocal ? 0xFF55FF55 : 0xFF55FFFF;
            context.fill(pixelX - halfSize, pixelY - halfSize, pixelX + halfSize, pixelY + halfSize, color);

            if (config.rotatePlayerHeads) {
                double rad = Math.toRadians(yaw - 180);
                int len = headSize;
                int dx = (int)(Math.sin(rad) * len);
                int dy = -(int)(Math.cos(rad) * len);
                context.fill(pixelX + dx - 1, pixelY + dy - 1, pixelX + dx + 1, pixelY + dy + 1, color);
            }
        }

        if (name != null && config.showPlayerNames) {
            var textRenderer = Minecraft.getInstance().font;
            int textWidth = textRenderer.width(name);
            context.text(textRenderer, name, pixelX - textWidth / 2, pixelY + halfSize + 2, 0xFFFFFFFF);
        }
    }

    private static void renderPlayerHead(GuiGraphicsExtractor context, Minecraft mc,
                                          int baseX, int baseY, float scale, int headSize,
                                          double worldX, double worldZ, float yaw,
                                          java.util.UUID uuid, int borderColor, String name, boolean isLocal, String recordName) {
        int[] gridPos = ComponentGrid.worldToGrid(worldX, worldZ);
        if (gridPos == null) return;

        int[] worldCenter = ComponentGrid.gridToWorld(gridPos[0], gridPos[1]);
        double roomOffsetX = (worldX - worldCenter[0] + ComponentGrid.HALF_ROOM_SIZE) / ComponentGrid.ROOM_SIZE;
        double roomOffsetZ = (worldZ - worldCenter[1] + ComponentGrid.HALF_ROOM_SIZE) / ComponentGrid.ROOM_SIZE;

        int pixelX = gridToPixelX(gridPos[0], baseX, scale) + (int)(roomOffsetX * ROOM_SIZE * scale);
        int pixelY = gridToPixelY(gridPos[1], baseY, scale) + (int)(roomOffsetZ * ROOM_SIZE * scale);

        if (recordName != null) playerMarkers.add(new PlayerMarker(pixelX, pixelY, recordName, isLocal));

        int halfSize = headSize / 2;

        if ((borderColor & 0xFF000000) != 0) {
            context.fill(pixelX - halfSize - 1, pixelY - halfSize - 1,
                         pixelX + halfSize + 1, pixelY + halfSize + 1, borderColor);
        }

        boolean useHeads = TeslaMapsConfig.get().useHeadsInsteadOfMarkers;
        float rotation = yaw + 180; // Convert yaw to map orientation

        if (useHeads) {
            if (TeslaMapsConfig.get().rotatePlayerHeads) {
                PlayerHeadRenderer.drawPlayerHeadRotated(context, pixelX - halfSize, pixelY - halfSize, headSize, uuid, rotation);
            } else {
                PlayerHeadRenderer.drawPlayerHead(context, pixelX - halfSize, pixelY - halfSize, headSize, uuid);

                double rad = Math.toRadians(rotation);
                int arrowDist = halfSize + 3;
                int arrowX = pixelX + (int)(Math.sin(rad) * arrowDist);
                int arrowY = pixelY - (int)(Math.cos(rad) * arrowDist);

                int arrowSize = Math.max(2, (int)(2 * scale));
                context.fill(arrowX - arrowSize, arrowY - arrowSize, arrowX + arrowSize, arrowY + arrowSize, borderColor);
            }
        } else {
            int markerSize = Math.max(10, headSize);
            int markerColor = 0xFF00FF00; // Bright green (same as marker.png)

            double rad = Math.toRadians(rotation);
            double sin = Math.sin(rad);
            double cos = Math.cos(rad);

            float tipLen = markerSize * 0.5f;     // Distance from center to tip
            float backLen = markerSize * 0.45f;   // Distance from center to back
            float sideLen = markerSize * 0.35f;   // Half-width at back

            int tipX = pixelX + (int)(sin * tipLen);
            int tipY = pixelY - (int)(cos * tipLen);

            int backLeftX = pixelX + (int)(-sin * backLen - cos * sideLen);
            int backLeftY = pixelY - (int)(-cos * backLen + sin * sideLen);

            int backRightX = pixelX + (int)(-sin * backLen + cos * sideLen);
            int backRightY = pixelY - (int)(-cos * backLen - sin * sideLen);

            drawFilledTriangle(context, tipX, tipY, backLeftX, backLeftY, backRightX, backRightY, markerColor);
        }

        if (name != null && TeslaMapsConfig.get().showPlayerNames) {
            var textRenderer = mc.font;
            int textWidth = textRenderer.width(name);
            float textScale = 0.5f;
            var matrices = context.pose();
            matrices.pushMatrix();
            matrices.translate(pixelX, pixelY + halfSize + 3);
            matrices.scale(textScale, textScale);
            context.text(textRenderer, name, -textWidth / 2, 0, 0xFFFFFFFF);
            matrices.popMatrix();
        }
    }

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

    private static void drawThickLine(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int thickness, int color) {
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

    private static void drawFilledTriangle(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int x3, int y3, int color) {
        if (y1 > y2) { int t = y1; y1 = y2; y2 = t; t = x1; x1 = x2; x2 = t; }
        if (y1 > y3) { int t = y1; y1 = y3; y3 = t; t = x1; x1 = x3; x3 = t; }
        if (y2 > y3) { int t = y2; y2 = y3; y3 = t; t = x2; x2 = x3; x3 = t; }

        if (y3 == y1) {
            int minX = Math.min(x1, Math.min(x2, x3));
            int maxX = Math.max(x1, Math.max(x2, x3));
            context.fill(minX, y1, maxX + 1, y1 + 1, color);
            return;
        }

        for (int y = y1; y <= y3; y++) {
            int startX, endX;

            if (y < y2) {
                if (y2 == y1) {
                    startX = x1;
                } else {
                    startX = x1 + (x2 - x1) * (y - y1) / (y2 - y1);
                }
            } else {
                if (y3 == y2) {
                    startX = x2;
                } else {
                    startX = x2 + (x3 - x2) * (y - y2) / (y3 - y2);
                }
            }

            endX = x1 + (x3 - x1) * (y - y1) / (y3 - y1);

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
