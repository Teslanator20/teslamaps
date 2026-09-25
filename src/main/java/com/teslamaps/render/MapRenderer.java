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
    private static final int MAP_PADDING = 8;
    private static final int CELL_SIZE = 28;
    private static int roomSize = 24;
    private static int doorGap = 4;

    private static int minGridX = 0, maxGridX = 5;
    private static int minGridZ = 0, maxGridZ = 5;
    private static int totalSecrets = 0;
    private static float offX = 0, offZ = 0;

    private static int lastMapX, lastMapY, lastMapW, lastMapH;
    public static int mapX() { return lastMapX; }
    public static int mapY() { return lastMapY; }
    public static int mapW() { return lastMapW; }
    public static int mapH() { return lastMapH; }

    public record PlayerMarker(int x, int y, String name, boolean self) {}
    private static final List<PlayerMarker> playerMarkers = new ArrayList<>();
    public static List<PlayerMarker> getPlayerMarkers() { return playerMarkers; }

    private static String highlightName = null;
    /** Player whose map head is drawn twice as large (leap overlay hover), null for none. */
    public static void setHighlight(String name) { highlightName = name; }

    private static final List<DungeonRoom> visibleRooms = new ArrayList<>();

    // Room names are static, so the word wrap is kept until the wrap width changes.
    private static final java.util.HashMap<String, List<String>> wrapCache = new java.util.HashMap<>();
    private static int wrapCacheWidth = -1;

    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.mapEnabled) return;
        if (config.onlyShowInDungeon && !DungeonManager.isInDungeon()) return;
        if (config.mapOnlyInBoss && !DungeonManager.isInBoss()) return;
        if (config.mapHideInBoss && isInBossArea(mc)) return;
        renderAt(context, config.mapX, config.mapY, config.mapScale, false);
    }

    // Boss area = scoreboard boss state, or being outside the dungeon room grid while still in catacombs
    // (the boss arena sits outside the room grid). The grid check covers floors where isInBoss() never fires.
    private static boolean isInBossArea(Minecraft mc) {
        if (DungeonManager.isInBoss()) return true;
        if (mc.player == null || !DungeonManager.isInDungeon()) return false;
        return ComponentGrid.worldToGrid((int) mc.player.getX(), (int) mc.player.getZ()) == null;
    }

    public static void renderAt(GuiGraphicsExtractor context, int baseX, int baseY, float scale, boolean leapMode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        TeslaMapsConfig config = TeslaMapsConfig.get();
        playerMarkers.clear();
        roomSize = Math.max(12, Math.min(CELL_SIZE - 1, Math.round(config.mapRoomSize)));
        doorGap = CELL_SIZE - roomSize;

        boolean legit = com.teslamaps.features.LegitMode.isFiltering();
        // Reused instead of a fresh stream/list every frame.
        visibleRooms.clear();
        for (DungeonRoom r : DungeonManager.getGrid().getAllRooms()) {
            if (r.getType() == RoomType.UNKNOWN || "Unknown".equals(r.getName())) continue;
            if (legit && !r.isExplored()) continue;
            visibleRooms.add(r);
        }
        Collection<DungeonRoom> rooms = visibleRooms;
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
        if (config.mapBgImage) {
            int in = Math.round(config.mapBgImageInset * scale);
            com.teslamaps.render.gui.MapImage.draw(context, config.mapBgImageName, baseX + in, baseY + in,
                    mapWidth - 2 * in, totalHeight - 2 * in, config.mapBgImageAlpha);
        }
        boolean cached = config.mapTextureCache && !leapMode;
        if (config.mapHypixelFallback && !(cached && config.mapFallbackTyped)) {
            if (config.mapFallbackTyped) drawTypedFallback(context, rooms, baseX, baseY, scale);
            else drawHypixelFallback(context, rooms, baseX, baseY, scale);
        }
        if (cached) {
            MapTexture.draw(context, baseX, baseY, mapWidth, mapHeight, scale, g -> {
                rasterPass = true;
                try {
                    if (config.mapHypixelFallback && config.mapFallbackTyped) drawTypedFallback(g, rooms, baseX, baseY, scale);
                    drawRoomConnections(g, rooms, baseX, baseY, scale, legit);
                    for (DungeonRoom room : rooms) drawRoom(g, room, baseX, baseY, scale);
                } finally {
                    rasterPass = false;
                }
            });
        } else {
            drawRoomConnections(context, rooms, baseX, baseY, scale, legit);
        }

        // getAllRooms() is already deduped, so no per-frame "already drawn" set.
        skipFills = cached;
        try {
            for (DungeonRoom room : rooms) {
                drawRoom(context, room, baseX, baseY, scale);
            }
        } finally {
            skipFills = false;
        }
        if (legit) {
            drawGuessRooms(context, baseX, baseY, scale);
        }
        if (config.mapCheckmarkIcons && config.showCheckmarks) {
            for (DungeonRoom room : rooms) drawRoomCheckmark(context, room, baseX, baseY, scale);
        }
        for (DungeonRoom room : rooms) {
            if (room.isIdentified()) drawRoomName(context, room, baseX, baseY, scale);
        }

        if (config.starMobRadar && !leapMode && !com.teslamaps.features.LegitMode.blocksCheats()) {
            drawStarMobs(context, baseX, baseY, scale);
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
        offX = 0; offZ = 0;
        if (TeslaMapsConfig.get().mapCenterSmallFloors) {
            // FunnyMap calibrateMap: F1 4x5, F2/F3 5x5
            int w = 6, h = 6;
            switch (DungeonManager.getCurrentFloor()) {
                case F1, M1 -> { w = 4; h = 5; }
                case F2, F3, M2, M3 -> { w = 5; h = 5; }
                default -> {}
            }
            for (DungeonRoom room : rooms) {
                for (int[] c : room.getComponents()) {
                    if (c[0] >= w || c[1] >= h) { w = 6; h = 6; }
                }
            }
            offX = (ComponentGrid.GRID_SIZE - w) * CELL_SIZE / 2f;
            offZ = (ComponentGrid.GRID_SIZE - h) * CELL_SIZE / 2f;
        }

        for (DungeonRoom room : rooms) {
            if (room.getSecrets() > 0) totalSecrets += room.getSecrets();
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

        // the tab list only refreshes every 50ms; parse it once per refresh, not per frame
        java.util.List<String> tabLines = TabListUtils.getTabListLines();
        if (tabLines != infoTabSource) {
            infoTabSource = tabLines;
            infoSecretsPercent = TabListUtils.getSecretsPercentage(tabLines);
            infoSecretsFound = TabListUtils.getSecretsFound(tabLines);
            infoCryptsFound = TabListUtils.getCryptsFound(tabLines);
        }
        double secretsPercent = infoSecretsPercent;

        int total = DungeonScore.totalSecrets(totalSecrets, infoSecretsFound, secretsPercent);
        int foundSecretsOverall = infoSecretsFound;
        if (foundSecretsOverall < 0) {
            foundSecretsOverall = (int) Math.round(total * secretsPercent / 100.0);
        }

        int cryptsFound = infoCryptsFound;
        int crypts = cryptsFound < 0 ? 0 : cryptsFound;
        double neededSecretsPercent = DungeonScore.getNeededSecretsPercentFor300(crypts);
        int neededSecretsOverall = (int) Math.ceil(total * neededSecretsPercent / 100.0);

        int remainingSecrets = Math.max(0, neededSecretsOverall - foundSecretsOverall);
        int shownNeeded = config.scoreSecretsShowNeeded ? neededSecretsOverall : remainingSecrets;

        String secretsText = secretsText(secretsPercent >= 0, foundSecretsOverall, shownNeeded, total);

        int secretsColor = (remainingSecrets == 0) ? 0xFF55FF55 : 0xFFFFFFFF;
        context.text(textRenderer, secretsText, 4, 2, secretsColor);

        if (config.showCrypts) {
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
            if (config.batBonusScore && DungeonScore.isBatKilled()) {
                int batWidth = textRenderer.width("Bat ✔");
                context.text(textRenderer, "Bat ✔", lw - scoreWidth - batWidth - 10, line2Y, 0xFF55FF55);
            }
        }

        matrices.popMatrix();
    }

    private static java.util.List<String> infoTabSource;
    private static double infoSecretsPercent = -1;
    private static int infoSecretsFound = -1, infoCryptsFound = -1;
    private static String secretsTextCache;
    private static long secretsTextKey = Long.MIN_VALUE;

    private static String secretsText(boolean known, int found, int remaining, int total) {
        long key = known ? (((long) (found & 0xFFFFF) << 40) | ((long) (remaining & 0xFFFFF) << 20) | (total & 0xFFFFF))
                : -1L - total;
        if (key != secretsTextKey || secretsTextCache == null) {
            secretsTextKey = key;
            secretsTextCache = known ? "Secrets: " + found + "/" + remaining + "/" + total : "Secrets: ?/?/" + total;
        }
        return secretsTextCache;
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

    private static final int MAP_RADIUS = 6;

    /**
     * Frame only. The map itself is drawn on top afterwards and is untouched by
     * this, so the panel can be restyled without the rooms moving a pixel.
     */
    private static void drawBackground(GuiGraphicsExtractor context, int baseX, int baseY, int width, int height) {
        int bgColor = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorBackground);

        com.teslamaps.render.gui.TeslaGui.shadow(context, baseX, baseY + 1, width, height,
                MAP_RADIUS, 4, com.teslamaps.render.gui.TeslaGui.withAlpha(0x000000, 55));
        // The player picks the background colour, so it is kept rather than
        // replaced by the panel's own.
        com.teslamaps.render.gui.TeslaGui.roundedRect(context, baseX, baseY, width, height,
                MAP_RADIUS, bgColor);
        com.teslamaps.render.gui.TeslaGui.roundedRectGradient(context, baseX + 1, baseY + 1,
                width - 2, Math.min(height * 0.5f, 14), MAP_RADIUS - 1,
                com.teslamaps.render.gui.TeslaGui.withAlpha(0xFFFFFF, 16),
                com.teslamaps.render.gui.TeslaGui.withAlpha(0xFFFFFF, 0));
        TeslaMapsConfig c = TeslaMapsConfig.get();
        String style = c.mapBorderStyle == null ? "Default" : c.mapBorderStyle;
        float t = Math.max(0.5f, c.mapBorderWidth);
        switch (style) {
            case "None" -> {}
            case "Solid" -> com.teslamaps.render.gui.TeslaGui.roundedOutline(context, baseX + t / 2, baseY + t / 2,
                    width - t, height - t, MAP_RADIUS, t, TeslaMapsConfig.parseColor(c.colorMapBorder));
            case "Chroma" -> {
                // IllegalMap/Floppa chroma border
                int alpha = TeslaMapsConfig.parseColor(c.colorMapBorder) & 0xFF000000;
                float hue = (System.currentTimeMillis() % 1_000_000L) * c.mapBorderChromaSpeed / 4000f;
                int top = alpha | (java.awt.Color.HSBtoRGB(hue % 1f, 1f, 1f) & 0xFFFFFF);
                int bottom = alpha | (java.awt.Color.HSBtoRGB((hue + 0.35f) % 1f, 1f, 1f) & 0xFFFFFF);
                com.teslamaps.render.gui.TeslaGui.roundedOutlineGradient(context, baseX + t / 2, baseY + t / 2,
                        width - t, height - t, MAP_RADIUS, t, top, bottom);
            }
            default -> com.teslamaps.render.gui.TeslaGui.roundedOutline(context, baseX + 0.5f, baseY + 0.5f,
                    width - 1, height - 1, MAP_RADIUS, 1.0f,
                    com.teslamaps.render.gui.TeslaGui.withAlpha(0xFFFFFF, 40));
        }
    }

    private static int gridToPixelX(int gridX, int baseX, float scale) {
        return baseX + (int)((MAP_PADDING + offX + (gridX - minGridX) * CELL_SIZE) * scale);
    }

    private static int gridToPixelY(int gridZ, int baseY, float scale) {
        return baseY + (int)((MAP_PADDING + offZ + (gridZ - minGridZ) * CELL_SIZE) * scale);
    }

    private static void drawRoomConnections(GuiGraphicsExtractor context, Collection<DungeonRoom> rooms, int baseX, int baseY, float scale, boolean legit) {
        int doorThickness = doorThickness(scale);

        // Every cell belongs to exactly one room and each edge is only tested from its
        // left/top cell, so no dedup set is needed.
        for (DungeonRoom room : rooms) {
            for (int[] comp : room.getComponents()) {
                int gx = comp[0];
                int gz = comp[1];

                DungeonRoom rightRoom = DungeonManager.getGrid().getRoom(gx + 1, gz);
                if (rightRoom != null && rightRoom != room && (!legit || rightRoom.isExplored())) {
                    DoorType doorType = mapFixDoor(DoorScanner.getDoorType(gx, gz, gx + 1, gz), gx, gz, true);
                    boolean bothScanned = room.getName() != null && rightRoom.getName() != null;
                    if (doorType != DoorType.NONE && bothScanned) {
                        int color = connectionColor(doorType, room, rightRoom, gx, gz, gx + 1, gz);
                        int x1 = gridToPixelX(gx, baseX, scale) + (int)(roomSize * scale) - 1;
                        int yCenter = gridToPixelY(gz, baseY, scale) + (int)(roomSize/2 * scale);
                        int y1 = yCenter - doorThickness / 2;
                        int x2 = gridToPixelX(gx + 1, baseX, scale) + 1;
                        int y2 = yCenter + doorThickness / 2;
                        context.fill(x1, y1, x2, y2, color);
                    }
                }

                DungeonRoom bottomRoom = DungeonManager.getGrid().getRoom(gx, gz + 1);
                if (bottomRoom != null && bottomRoom != room && (!legit || bottomRoom.isExplored())) {
                    DoorType doorType = mapFixDoor(DoorScanner.getDoorType(gx, gz, gx, gz + 1), gx, gz, false);
                    boolean bothScanned = room.getName() != null && bottomRoom.getName() != null;
                    if (doorType != DoorType.NONE && bothScanned) {
                        int color = connectionColor(doorType, room, bottomRoom, gx, gz, gx, gz + 1);
                        int xCenter = gridToPixelX(gx, baseX, scale) + (int)(roomSize/2 * scale);
                        int x1 = xCenter - doorThickness / 2;
                        int y1 = gridToPixelY(gz, baseY, scale) + (int)(roomSize * scale) - 1;
                        int x2 = xCenter + doorThickness / 2;
                        int y2 = gridToPixelY(gz + 1, baseY, scale) + 1;
                        context.fill(x1, y1, x2, y2, color);
                    }
                }
            }
        }
    }

    private static int connectionColor(DoorType type, DungeonRoom a, DungeonRoom b, int x1, int z1, int x2, int z2) {
        if (type == DoorType.NORMAL && TeslaMapsConfig.get().mapDoorsRoomColor) {
            return getRoomColor(b.getCheckmarkState() == CheckmarkState.UNEXPLORED || a.getCheckmarkState() != CheckmarkState.UNEXPLORED ? b : a);
        }
        int color = TeslaMapsConfig.get().mapColorOpenDoors && DoorScanner.isOpened(x1, z1, x2, z2)
                ? TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorDoorOpenWither) : getDoorColor(type);
        if ((a.getCheckmarkState() == CheckmarkState.UNEXPLORED || b.getCheckmarkState() == CheckmarkState.UNEXPLORED)
                && typedUndiscovered()) {
            color = undiscoveredColor(color);
        }
        return color;
    }

    private static int doorThickness(float scale) {
        float w = TeslaMapsConfig.get().mapDoorWidth;
        return Math.max((int) (w * scale), Math.max(1, Math.round(w * 5f / 8f)));
    }

    // Hypixel's map shows a wither door where our scan saw a normal one
    private static DoorType mapFixDoor(DoorType type, int gx, int gz, boolean right) {
        if (type != DoorType.NORMAL) return type;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.mapHypixelFallback || !c.mapFallbackTyped) return type;
        DoorType m = right ? com.teslamaps.map.MapTileTypes.doorRight(gx, gz) : com.teslamaps.map.MapTileTypes.doorDown(gx, gz);
        return m == DoorType.WITHER ? DoorType.WITHER : type;
    }

    private static int typedCellColor(int gx, int gz) {
        RoomType t = com.teslamaps.map.MapTileTypes.type(gx, gz);
        if (t == null) return 0;
        if (t == RoomType.UNKNOWN) return TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorUnexplored);
        return typeColor(null, t, false);
    }

    // FunnyMapExtras MapUpdate.updateRooms: cells our scan has not filled, typed by their map colour
    private static void drawTypedFallback(GuiGraphicsExtractor context, Collection<DungeonRoom> rooms, int baseX, int baseY, float scale) {
        if (!com.teslamaps.map.MapTileTypes.ready()) return;
        int n = ComponentGrid.GRID_SIZE;
        java.util.Arrays.fill(covered, false);
        for (DungeonRoom room : rooms) {
            for (int[] c : room.getComponents()) {
                if (c[0] >= 0 && c[0] < n && c[1] >= 0 && c[1] < n) covered[c[0] + c[1] * n] = true;
            }
        }
        int rs = (int) (roomSize * scale);
        for (int gz = 0; gz < n; gz++) {
            for (int gx = 0; gx < n; gx++) {
                boolean cov = covered[gx + gz * n];
                int color = cov ? 0 : typedCellColor(gx, gz);
                int x0 = gridToPixelX(gx, baseX, scale), y0 = gridToPixelY(gz, baseY, scale);
                if (color != 0) {
                    context.fill(x0, y0, x0 + rs, y0 + rs, color);
                    if (gx + 1 < n && !covered[gx + 1 + gz * n] && com.teslamaps.map.MapTileTypes.joinRight(gx, gz)) {
                        context.fill(x0 + rs, y0, gridToPixelX(gx + 1, baseX, scale), y0 + rs, color);
                    }
                    if (gz + 1 < n && !covered[gx + (gz + 1) * n] && com.teslamaps.map.MapTileTypes.joinDown(gx, gz)) {
                        context.fill(x0, y0 + rs, x0 + rs, gridToPixelY(gz + 1, baseY, scale), color);
                    }
                    if (gx + 1 < n && gz + 1 < n && com.teslamaps.map.MapTileTypes.joinRight(gx, gz)
                            && com.teslamaps.map.MapTileTypes.joinDown(gx, gz)
                            && com.teslamaps.map.MapTileTypes.joinRight(gx, gz + 1)
                            && !covered[gx + 1 + (gz + 1) * n]) {
                        context.fill(x0 + rs, y0 + rs, gridToPixelX(gx + 1, baseX, scale), gridToPixelY(gz + 1, baseY, scale), color);
                    }
                }
                if (gx + 1 < n && (!cov || !covered[gx + 1 + gz * n])) {
                    DoorType d = com.teslamaps.map.MapTileTypes.doorRight(gx, gz);
                    if (d != null && com.teslamaps.map.MapTileTypes.type(gx + 1, gz) != null) {
                        drawDoorBetween(context, gx, gz, gx + 1, gz, getDoorColor(d), baseX, baseY, scale);
                    }
                }
                if (gz + 1 < n && (!cov || !covered[gx + (gz + 1) * n])) {
                    DoorType d = com.teslamaps.map.MapTileTypes.doorDown(gx, gz);
                    if (d != null && com.teslamaps.map.MapTileTypes.type(gx, gz + 1) != null) {
                        drawDoorBetween(context, gx, gz, gx, gz + 1, getDoorColor(d), baseX, baseY, scale);
                    }
                }
            }
        }
    }

    private static final int[] mobPx = new int[2];

    private static boolean worldToPixel(double worldX, double worldZ, int baseX, int baseY, float scale, int[] out) {
        int[] gridPos = ComponentGrid.worldToGrid(worldX, worldZ);
        if (gridPos == null) return false;
        int[] worldCenter = ComponentGrid.gridToWorld(gridPos[0], gridPos[1]);
        double ox = (worldX - worldCenter[0] + ComponentGrid.HALF_ROOM_SIZE) / ComponentGrid.ROOM_SIZE;
        double oz = (worldZ - worldCenter[1] + ComponentGrid.HALF_ROOM_SIZE) / ComponentGrid.ROOM_SIZE;
        out[0] = gridToPixelX(gridPos[0], baseX, scale) + (int) (ox * roomSize * scale);
        out[1] = gridToPixelY(gridPos[1], baseY, scale) + (int) (oz * roomSize * scale);
        return true;
    }

    // IllegalMap renderRadar
    private static void drawStarMobs(GuiGraphicsExtractor context, int baseX, int baseY, float scale) {
        List<com.teslamaps.features.StarMobRadar.Mob> mobs = com.teslamaps.features.StarMobRadar.mobs();
        if (mobs.isEmpty()) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        int size = Math.max(4, (int) (10 * scale * c.starMobRadarSize));
        int half = size / 2;
        int dotColor = TeslaMapsConfig.parseColor(c.colorStarMobRadar);
        int border = TeslaMapsConfig.parseColor(c.colorStarMobRadarBorder);
        var pose = context.pose();
        for (var m : mobs) {
            if (!worldToPixel(m.x(), m.z(), baseX, baseY, scale, mobPx)) continue;
            int color = c.starMobRadarMinibossColors && m.color() != 0 ? m.color() : dotColor;
            pose.pushMatrix();
            pose.translate(mobPx[0], mobPx[1]);
            if (c.starMobRadarHeads && m.icon() != com.teslamaps.features.StarMobRadar.ICON_NONE) {
                pose.rotate((float) Math.toRadians(m.yaw() + 180));
                if (c.starMobRadarBorder) {
                    int b = half + Math.max(1, size / 12);
                    context.fill(-b, -b, b, b, border);
                }
                pose.scale(size / 16f, size / 16f);
                context.item(com.teslamaps.features.StarMobRadar.icon(m.icon()), -8, -8);
            } else {
                int r = Math.max(2, size / 4);
                if (c.starMobRadarBorder) context.fill(-r - 1, -r - 1, r + 1, r + 1, border);
                context.fill(-r, -r, r, r, color);
            }
            pose.popMatrix();
        }
    }

    private static void mapText(GuiGraphicsExtractor context, net.minecraft.client.gui.Font font, String s, int x, int y, int color) {
        String mode = TeslaMapsConfig.get().mapTextShadow;
        if ("None".equals(mode)) {
            context.text(font, s, x, y, color, false);
        } else if ("Outline".equals(mode)) {
            int black = color & 0xFF000000;
            if (com.teslamaps.render.gui.OutlinedText.draw(context, font, s, x, y, color, black)) return;
            context.text(font, s, x + 1, y, black, false);
            context.text(font, s, x - 1, y, black, false);
            context.text(font, s, x, y + 1, black, false);
            context.text(font, s, x, y - 1, black, false);
            context.text(font, s, x, y, color, false);
        } else {
            context.text(font, s, x, y, color);
        }
    }

    private static final boolean[] covered = new boolean[ComponentGrid.GRID_SIZE * ComponentGrid.GRID_SIZE];

    // cells our scan could not fill get Hypixel's own map pixels
    private static void drawHypixelFallback(GuiGraphicsExtractor context, Collection<DungeonRoom> rooms, int baseX, int baseY, float scale) {
        if (!com.teslamaps.map.HypixelMapFallback.ready() || MapScanner.mapCornerX < 0 || MapScanner.mapCornerY < 0) return;
        int n = ComponentGrid.GRID_SIZE;
        java.util.Arrays.fill(covered, false);
        for (DungeonRoom room : rooms) {
            for (int[] c : room.getComponents()) {
                if (c[0] >= 0 && c[0] < n && c[1] >= 0 && c[1] < n) covered[c[0] + c[1] * n] = true;
            }
        }
        var tex = com.teslamaps.map.HypixelMapFallback.texture();
        int rs = MapScanner.mapRoomSize, gap = MapScanner.mapGapSize;
        for (int gz = 0; gz < n; gz++) {
            for (int gx = 0; gx < n; gx++) {
                if (covered[gx + gz * n]) continue;
                int u = MapScanner.mapCornerX + gx * (rs + gap);
                int v = MapScanner.mapCornerY + gz * (rs + gap);
                int x0 = gridToPixelX(gx, baseX, scale), y0 = gridToPixelY(gz, baseY, scale);
                int xr = x0 + (int) (roomSize * scale), yb = y0 + (int) (roomSize * scale);
                int x1 = gridToPixelX(gx + 1, baseX, scale), y1 = gridToPixelY(gz + 1, baseY, scale);
                blitMap(context, tex, x0, y0, xr, yb, u, v, rs, rs);
                blitMap(context, tex, xr, y0, x1, yb, u + rs, v, gap, rs);
                blitMap(context, tex, x0, yb, xr, y1, u, v + rs, rs, gap);
                blitMap(context, tex, xr, yb, x1, y1, u + rs, v + rs, gap, gap);
                if (gx > 0 && covered[gx - 1 + gz * n]) {
                    int xl = gridToPixelX(gx - 1, baseX, scale) + (int) (roomSize * scale);
                    blitMap(context, tex, xl, y0, x0, yb, u - gap, v, gap, rs);
                }
                if (gz > 0 && covered[gx + (gz - 1) * n]) {
                    int yt = gridToPixelY(gz - 1, baseY, scale) + (int) (roomSize * scale);
                    blitMap(context, tex, x0, yt, xr, y0, u, v - gap, rs, gap);
                }
            }
        }
    }

    private static void blitMap(GuiGraphicsExtractor context, net.minecraft.resources.Identifier tex,
                                int x0, int y0, int x1, int y1, int u, int v, int uw, int vh) {
        if (x1 <= x0 || y1 <= y0 || u < 0 || v < 0 || u + uw > 128 || v + vh > 128) return;
        context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, tex,
                x0, y0, u, v, x1 - x0, y1 - y0, uw, vh, 128, 128);
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

    private static final Set<Long> guessDrawn = new HashSet<>();

    // Legit Mode: a plain dark-grey 1x1 placeholder behind each door to an undiscovered cell.
    private static void drawGuessRooms(GuiGraphicsExtractor context, int baseX, int baseY, float scale) {
        List<com.teslamaps.map.LegitGuess.Door> guessDoors = com.teslamaps.map.LegitGuess.getGuessDoors();
        if (guessDoors.isEmpty()) return;

        int gray = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorUnexplored);
        int rs = (int) (roomSize * scale);

        Set<Long> drawn = guessDrawn;
        drawn.clear();
        for (com.teslamaps.map.LegitGuess.Door d : guessDoors) {
            long ck = ((long) d.nx() << 32) | (d.nz() & 0xffffffffL);
            if (!drawn.add(ck)) continue;
            int px = gridToPixelX(d.nx(), baseX, scale);
            int py = gridToPixelY(d.nz(), baseY, scale);
            int[] guess = com.teslamaps.map.LegitGuess.colorsAt(d.nx(), d.nz());
            if (guess == null) {
                context.fill(px, py, px + rs, py + rs, gray);
            } else if (guess.length == 1) {
                context.fill(px, py, px + rs, py + rs, guess[0]);
            } else {
                // Odin renderPathHints: split tile, each part darker(0.5)
                for (int i = 0; i < guess.length; i++) {
                    int x0 = px + rs * i / guess.length, x1 = px + rs * (i + 1) / guess.length;
                    context.fill(x0, py, x1, py + rs, darkenColor(guess[i], 0.5f));
                }
            }
            TeslaMapsConfig c = TeslaMapsConfig.get();
            if (c.mapCheckmarkIcons && c.showCheckmarks && c.mapCheckmarkUnopened) {
                drawCheckmark(context, px + rs / 2, py + rs / 2,
                        Math.max(4, Math.round(roomSize * scale * c.mapCheckmarkSize)), CheckmarkState.UNEXPLORED);
            }
        }

        for (com.teslamaps.map.LegitGuess.Door d : guessDoors) {
            drawDoorBetween(context, d.gx(), d.gz(), d.nx(), d.nz(), getDoorColor(d.type()), baseX, baseY, scale);
        }
    }

    private static void drawDoorBetween(GuiGraphicsExtractor context, int gx, int gz, int nx, int nz, int color, int baseX, int baseY, float scale) {
        int doorThickness = doorThickness(scale);

        int ax, az, bx, bz;
        if (gx != nx) { ax = Math.min(gx, nx); bx = Math.max(gx, nx); az = gz; bz = gz; }
        else { az = Math.min(gz, nz); bz = Math.max(gz, nz); ax = gx; bx = gx; }

        if (bx == ax + 1) { // horizontal door
            int x1 = gridToPixelX(ax, baseX, scale) + (int) (roomSize * scale) - 1;
            int yCenter = gridToPixelY(az, baseY, scale) + (int) (roomSize / 2 * scale);
            int x2 = gridToPixelX(bx, baseX, scale) + 1;
            context.fill(x1, yCenter - doorThickness / 2, x2, yCenter + doorThickness / 2, color);
        } else { // vertical door
            int xCenter = gridToPixelX(ax, baseX, scale) + (int) (roomSize / 2 * scale);
            int y1 = gridToPixelY(az, baseY, scale) + (int) (roomSize * scale) - 1;
            int y2 = gridToPixelY(bz, baseY, scale) + 1;
            context.fill(xCenter - doorThickness / 2, y1, xCenter + doorThickness / 2, y2, color);
        }
    }

    // map texture cache: the raster pass only paints room fills, the live pass skips them
    private static boolean rasterPass, skipFills;

    private static void roomFill(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1, int color) {
        if (!skipFills) context.fill(x0, y0, x1, y1, color);
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
            int pixelW = (int)((width * CELL_SIZE - doorGap) * scale);
            int pixelH = (int)((height * CELL_SIZE - doorGap) * scale);

            roomFill(context, pixelX, pixelY, pixelX + pixelW, pixelY + pixelH, color);

            if (rasterPass) return;
            TeslaMapsConfig config = TeslaMapsConfig.get();
            boolean peek = com.teslamaps.features.MapPeek.active();
            boolean hideSecrets = !peek && config.hideSecretsWhenDone && room.getCheckmarkState() == CheckmarkState.GREEN;
            boolean hideOneSecret = !peek && config.hideOneSecretCount && room.getSecrets() == 1;
            if ((config.showSecretCount || peek) && room.getSecrets() > 0 && !hideSecrets && !hideOneSecret) {
                String secretText;
                int foundSecrets = room.getFoundSecrets();
                int maxSecrets = room.getSecrets();

                if (foundSecrets >= 0) {
                    secretText = foundSecrets + "/" + maxSecrets;
                } else {
                    secretText = String.valueOf(maxSecrets);
                }
                mapText(context, Minecraft.getInstance().font,
                        secretText, pixelX + 2, pixelY + 2,
                        TeslaMapsConfig.parseColor(config.colorSecretCount));
            }

        } else {
            int cellScaled = (int)(CELL_SIZE * scale);
            int roomSizeScaled = (int)(roomSize * scale);
            if (roomSizeScaled < 1) roomSizeScaled = 1;

            for (int[] component : room.getComponents()) {
                int gridX = component[0];
                int gridZ = component[1];

                int pixelX = gridToPixelX(gridX, baseX, scale);
                int pixelY = gridToPixelY(gridZ, baseY, scale);

                roomFill(context, pixelX, pixelY, pixelX + roomSizeScaled, pixelY + roomSizeScaled, color);

                boolean hasRight = hasComponent(room, gridX + 1, gridZ);
                boolean hasBottom = hasComponent(room, gridX, gridZ + 1);
                boolean hasDiagonal = hasComponent(room, gridX + 1, gridZ + 1);

                int nextPixelX = gridToPixelX(gridX + 1, baseX, scale);
                int nextPixelY = gridToPixelY(gridZ + 1, baseY, scale);

                if (hasRight) {
                    int cx = pixelX + roomSizeScaled;
                    int cy = pixelY;
                    roomFill(context, cx, cy, nextPixelX, cy + roomSizeScaled, color);
                }

                if (hasBottom) {
                    int cx = pixelX;
                    int cy = pixelY + roomSizeScaled;
                    roomFill(context, cx, cy, cx + roomSizeScaled, nextPixelY, color);
                }

                if (hasRight && hasBottom && hasDiagonal) {
                    int cx = pixelX + roomSizeScaled;
                    int cy = pixelY + roomSizeScaled;
                    roomFill(context, cx, cy, nextPixelX, nextPixelY, color);
                }
            }

            if (rasterPass) return;
            int[] primary = room.getPrimaryComponent();
            int pixelX = gridToPixelX(primary[0], baseX, scale);
            int pixelY = gridToPixelY(primary[1], baseY, scale);

            TeslaMapsConfig configIrregular = TeslaMapsConfig.get();
            boolean peekIrregular = com.teslamaps.features.MapPeek.active();
            boolean hideSecretsIrregular = !peekIrregular && configIrregular.hideSecretsWhenDone && room.getCheckmarkState() == CheckmarkState.GREEN;
            boolean hideOneSecretIrregular = !peekIrregular && configIrregular.hideOneSecretCount && room.getSecrets() == 1;
            if ((configIrregular.showSecretCount || peekIrregular) && room.getSecrets() > 0 && !hideSecretsIrregular && !hideOneSecretIrregular) {
                String secretText;
                int foundSecrets = room.getFoundSecrets();
                int maxSecrets = room.getSecrets();

                if (foundSecrets >= 0) {
                    secretText = foundSecrets + "/" + maxSecrets;
                } else {
                    secretText = String.valueOf(maxSecrets);
                }
                mapText(context, Minecraft.getInstance().font,
                        secretText, pixelX + 2, pixelY + 2,
                        TeslaMapsConfig.parseColor(configIrregular.colorSecretCount));
            }

        }

        drawPrinceIcon(context, room, baseX, baseY, scale);
    }

    public static boolean runHasPrinceRoom() {
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
        int cellSize = (int)(roomSize * scale);

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
        return id.equals("SPIRIT_LEAP") || id.equals("INFINITE_SPIRIT_LEAP") || id.equals("HAUNT_ABILITY");
    }

    private static boolean nameVisible(DungeonRoom room) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!room.isIdentified() || room.getName() == null) return false;
        if (com.teslamaps.features.MapPeek.active()) return true;
        if (!config.showRoomNames) return false;
        if (config.roomNamesOnlyWithLeap && !holdingLeap()) return false;
        if (config.showNamesOnlyForPuzzles && room.getType() != RoomType.PUZZLE) return false;
        if (config.hideEntranceBloodFairyNames) {
            var type = room.getType();
            if (type == RoomType.ENTRANCE || type == RoomType.BLOOD || type == RoomType.FAIRY) return false;
        }
        return true;
    }

    private static void drawRoomName(GuiGraphicsExtractor context, DungeonRoom room, int baseX, int baseY, float scale) {
        if (!nameVisible(room)) return;

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
            int maxPixelX = gridToPixelX(partMaxGX, baseX, scale) + (int)(roomSize * scale);
            int minPixelY = gridToPixelY(partMinGZ, baseY, scale);
            int maxPixelY = gridToPixelY(partMaxGZ, baseY, scale) + (int)(roomSize * scale);

            centerX = (minPixelX + maxPixelX) / 2;
            centerY = (minPixelY + maxPixelY) / 2;
        } else {
            int minPixelX = gridToPixelX(roomMinGX, baseX, scale);
            int maxPixelX = gridToPixelX(roomMaxGX, baseX, scale) + (int)(roomSize * scale);
            int minPixelY = gridToPixelY(roomMinGZ, baseY, scale);
            int maxPixelY = gridToPixelY(roomMaxGZ, baseY, scale) + (int)(roomSize * scale);

            centerX = (minPixelX + maxPixelX) / 2;
            centerY = (minPixelY + maxPixelY) / 2;
        }

        String align = TeslaMapsConfig.get().mapNameAlign;
        if (align != null && !"Center L".equals(align)) {
            if ("Center".equals(align)) {
                double sx = 0, sz = 0;
                for (int[] comp : room.getComponents()) { sx += comp[0]; sz += comp[1]; }
                centerX = cellCenterX(sx / componentCount, baseX, scale);
                centerY = cellCenterY(sz / componentCount, baseY, scale);
            } else {
                int[] cell = cornerCell(room, align);
                centerX = cellCenterX(cell[0], baseX, scale);
                centerY = cellCenterY(cell[1], baseY, scale);
                width = 1;
            }
        }

        String displayName = getDisplayName(room);

        int textColor = getTextColor(room);

        var textRenderer = Minecraft.getInstance().font;

        float textScale = 0.5f * TeslaMapsConfig.get().roomNameScale;

        int maxLineWidth = (int)((width * CELL_SIZE - 4) * scale / textScale);

        if (maxLineWidth != wrapCacheWidth) {
            wrapCache.clear();
            wrapCacheWidth = maxLineWidth;
        }
        java.util.List<String> lines = wrapCache.get(displayName);
        if (lines == null) {
            lines = new java.util.ArrayList<>();
            StringBuilder currentLine = new StringBuilder();
            for (String word : displayName.split(" ")) {
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
            wrapCache.put(displayName, lines);
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

            mapText(context, textRenderer, line, -textWidth / 2, 0, textColor);

            matrices.popMatrix();
        }
    }

    private static int cellCenterX(double gx, int baseX, float scale) {
        return baseX + (int) ((MAP_PADDING + offX + (gx - minGridX) * CELL_SIZE + roomSize / 2.0) * scale);
    }

    private static int cellCenterY(double gz, int baseY, float scale) {
        return baseY + (int) ((MAP_PADDING + offZ + (gz - minGridZ) * CELL_SIZE + roomSize / 2.0) * scale);
    }

    // Devonian DungeonMapRoomInfoAlignment: which cell of the room
    private static int[] cornerCell(DungeonRoom room, String align) {
        int[] best = room.getComponents().get(0);
        int bestScore = Integer.MIN_VALUE;
        for (int[] p : room.getComponents()) {
            int s = switch (align) {
                case "Top Right" -> p[0] * 8 - p[1];
                case "Bottom Left" -> p[1] * 8 - p[0];
                case "Bottom Right" -> p[0] + p[1] * 8;
                default -> -(p[0] + p[1] * 8);
            };
            if (s > bestScore) { bestScore = s; best = p; }
        }
        return best;
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
            if (config.mapWhiteCheckBlood && effectiveState(room) != CheckmarkState.WHITE
                    && room.getCheckmarkState() != CheckmarkState.GREEN) {
                return TeslaMapsConfig.parseColor(config.colorTextUnexplored);
            }
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
            switch (config.mapMimicRoomStyle) {
                case "Tint" -> { return shiftColor(baseRoomColor(room, type), 0xFFFF0000, 0.2f); }
                case "Custom" -> { return TeslaMapsConfig.parseColor(config.colorMimicRoom); }
                default -> { return 0xFFFF0000; }
            }
        }
        return baseRoomColor(room, type);
    }

    private static boolean typedUndiscovered() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.mapDarkenUndiscovered || c.mapGrayUndiscovered;
    }

    // FunnyMap renderRooms: darken(1 - percent), then grayscale
    private static int undiscoveredColor(int color) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.mapDarkenUndiscovered) color = darkenColor(color, 1f - c.mapDarkenPercent);
        if (c.mapGrayUndiscovered) {
            int gray = Math.round(((color >> 16) & 0xFF) * 0.299f + ((color >> 8) & 0xFF) * 0.587f + (color & 0xFF) * 0.114f);
            color = (color & 0xFF000000) | (gray << 16) | (gray << 8) | gray;
        }
        return color;
    }

    // IllegalMap colorShift
    private static int shiftColor(int from, int to, float t) {
        int r = Math.round(((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t));
        int g = Math.round(((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t));
        int b = Math.round((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
        return (from & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int baseRoomColor(DungeonRoom room, RoomType type) {
        if (room.getCheckmarkState() == CheckmarkState.UNEXPLORED && typedUndiscovered()) {
            return undiscoveredColor(typeColor(room, type, false));
        }
        return typeColor(room, type, true);
    }

    private static int typeColor(DungeonRoom room, RoomType type, boolean unexploredColors) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        return switch (type) {
            case ENTRANCE -> TeslaMapsConfig.parseColor(config.colorEntrance);
            case BLOOD -> TeslaMapsConfig.parseColor(config.colorBlood);
            case TRAP -> TeslaMapsConfig.parseColor(config.colorTrap);
            case PUZZLE -> {
                if (unexploredColors && room.getCheckmarkState() == CheckmarkState.UNEXPLORED) {
                    yield TeslaMapsConfig.parseColor(config.colorPuzzleUnexplored);
                }
                yield TeslaMapsConfig.parseColor(config.colorPuzzle);
            }
            case FAIRY -> TeslaMapsConfig.parseColor(config.colorFairy);
            case YELLOW -> TeslaMapsConfig.parseColor(config.colorMiniboss);
            case RARE -> {
                if (!config.mapRareRoomColor) yield typeColor(room, RoomType.NORMAL, unexploredColors);
                yield TeslaMapsConfig.parseColor(config.colorRare);
            }
            default -> {
                if (unexploredColors && room.getCheckmarkState() == CheckmarkState.UNEXPLORED) {
                    yield TeslaMapsConfig.parseColor(config.colorUnexplored);
                }
                yield TeslaMapsConfig.parseColor(config.colorNormal);
            }
        };
    }

    private static final String[] CHECK_SETS = {"illegalmap", "neu", "vanilla_new", "vanilla_old"};
    private static final String[] CHECK_STYLE_NAMES = {"IllegalMap", "NEU", "Vanilla New", "Vanilla Old"};
    private static String checkStyleKey = null;
    private static int checkStyleIdx = -1;
    // per set: white, green, failed, question
    private static final int[][] CHECK_DIMS = {{10, 10, 10, 62}, {100, 100, 100, 100}, {18, 18, 8, 18}, {8, 7, 8, 7}};
    private static final net.minecraft.resources.Identifier[][] CHECK_TEX = new net.minecraft.resources.Identifier[4][4];
    static {
        String[] files = {"white", "green", "failed", "question"};
        for (int s = 0; s < 4; s++) for (int i = 0; i < 4; i++)
            CHECK_TEX[s][i] = net.minecraft.resources.Identifier.fromNamespaceAndPath("teslamaps",
                    "textures/map/checks/" + CHECK_SETS[s] + "/" + files[i] + ".png");
    }

    private static int checkIndex(CheckmarkState state) {
        return switch (state) {
            case WHITE -> 0;
            case GREEN -> 1;
            case FAILED -> 2;
            case UNEXPLORED -> 3;
            default -> -1;
        };
    }

    private static CheckmarkState effectiveState(DungeonRoom room) {
        CheckmarkState state = room.getCheckmarkState();
        if (room.getType() == RoomType.BLOOD && (state == CheckmarkState.UNEXPLORED || state == CheckmarkState.NONE)
                && com.teslamaps.features.BloodCheck.done()) return CheckmarkState.WHITE;
        return state;
    }

    private static void drawRoomCheckmark(GuiGraphicsExtractor context, DungeonRoom room, int baseX, int baseY, float scale) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        CheckmarkState state = effectiveState(room);
        if (state == CheckmarkState.UNEXPLORED && !config.mapCheckmarkUnopened) return;
        if (config.mapCheckmarkHideWithName && nameVisible(room)) return;
        int cx, cy;
        if (config.mapCheckmarkAlign != null && !"Default".equals(config.mapCheckmarkAlign)) {
            int[] cell = cornerCell(room, config.mapCheckmarkAlign);
            cx = cellCenterX(cell[0], baseX, scale);
            cy = cellCenterY(cell[1], baseY, scale);
        } else {
            double[] c = checkmarkCenter(room);
            cx = cellCenterX(c[0], baseX, scale);
            cy = cellCenterY(c[1], baseY, scale);
        }
        drawCheckmark(context, cx, cy, Math.max(4, Math.round(roomSize * scale * config.mapCheckmarkSize)), state);
    }

    // Devonian getCenterOf: L rooms use the corner cell, others the mean of their cells
    private record CenterCache(int size, boolean center, double[] value) {}
    private static final java.util.Map<DungeonRoom, CenterCache> CENTER_CACHE = new java.util.WeakHashMap<>();

    private static double[] checkmarkCenter(DungeonRoom room) {
        boolean center = TeslaMapsConfig.get().mapCheckmarkCenter;
        int size = room.getComponents().size();
        CenterCache cc = CENTER_CACHE.get(room);
        if (cc == null || cc.size() != size || cc.center() != center) {
            cc = new CenterCache(size, center, computeCheckmarkCenter(room));
            CENTER_CACHE.put(room, cc);
        }
        return cc.value();
    }

    private static double[] computeCheckmarkCenter(DungeonRoom room) {
        List<int[]> comps = room.getComponents();
        if (!TeslaMapsConfig.get().mapCheckmarkCenter) {
            int[] best = comps.get(0);
            for (int[] p : comps) if (p[0] + p[1] < best[0] + best[1]) best = p;
            return new double[]{best[0], best[1]};
        }
        if (comps.size() == 3) {
            for (int[] p : comps) {
                boolean h = hasComponent(room, p[0] + 1, p[1]) || hasComponent(room, p[0] - 1, p[1]);
                boolean v = hasComponent(room, p[0], p[1] + 1) || hasComponent(room, p[0], p[1] - 1);
                if (h && v) return new double[]{p[0], p[1]};
            }
        }
        double sx = 0, sz = 0;
        for (int[] p : comps) { sx += p[0]; sz += p[1]; }
        return new double[]{sx / comps.size(), sz / comps.size()};
    }

    private static void drawCheckmark(GuiGraphicsExtractor context, int centerX, int centerY, int size, CheckmarkState state) {
        TeslaMapsConfig config = TeslaMapsConfig.get();

        if (!config.showCheckmarks) return; // master switch

        if (state == CheckmarkState.WHITE && !config.showWhiteCheckmarks) return;
        if (state == CheckmarkState.GREEN && !config.showGreenCheckmarks) return;
        if (state == CheckmarkState.FAILED && !config.showFailedCheckmarks) return;
        int idx = checkIndex(state);
        if (idx < 0) return;

        if (!java.util.Objects.equals(config.mapCheckmarkStyle, checkStyleKey)) {
            checkStyleKey = config.mapCheckmarkStyle;
            checkStyleIdx = -1;
            for (int i = 0; i < CHECK_STYLE_NAMES.length; i++) if (CHECK_STYLE_NAMES[i].equals(checkStyleKey)) checkStyleIdx = i;
        }
        int set = checkStyleIdx;
        if (set >= 0) {
            int tw = CHECK_DIMS[set][idx];
            context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, CHECK_TEX[set][idx],
                    centerX - size / 2, centerY - size / 2, 0f, 0f, size, size, tw, tw, tw, tw);
            return;
        }

        String symbol = switch (state) {
            case WHITE, GREEN -> "\u2714";
            case FAILED -> "\u2716";
            default -> "?";
        };
        int textWidth = Minecraft.getInstance().font.width(symbol);

        int color = switch (state) {
            case WHITE -> TeslaMapsConfig.parseColor(config.colorCheckWhite);
            case GREEN -> TeslaMapsConfig.parseColor(config.colorCheckGreen);
            case FAILED -> TeslaMapsConfig.parseColor(config.colorCheckFailed);
            default -> TeslaMapsConfig.parseColor(config.colorTextUnexplored);
        };

        context.text(Minecraft.getInstance().font,
                symbol, centerX - textWidth / 2, centerY - 4, color);
    }

    private static void drawPlayerMarker(GuiGraphicsExtractor context, Minecraft mc, int baseX, int baseY, float scale) {
        if (mc.level == null || mc.player == null) return;

        TeslaMapsConfig config = TeslaMapsConfig.get();
        int headSize = (int)(8 * scale * config.playerHeadScale);

        if (config.showSelfMarker) {
            String selfName = config.showPlayerNames ? mc.player.getName().getString() : null;
            renderPlayerHead(context, mc, baseX, baseY, scale, headSize,
                    mc.player.getX(), mc.player.getZ(), mc.player.getYRot(),
                    mc.player.getUUID(), PlayerTracker.getLocalClass(), selfName, true, mc.player.getName().getString());
        }

        if (!config.showOtherPlayers) return;

        List<int[]> mapPositions = MapScanner.getMapPlayerPositions();
        PlayerTracker.DungeonPlayer[] players = PlayerTracker.getPlayersOrdered();
        int playerIdx = 1; // Start at 1, index 0 is self
        int slot = 0;

        for (int[] pos : mapPositions) {
            int mapX = pos[0];
            int mapZ = pos[1];
            int rotation = pos[2];
            int isLocal = pos[3];

            if (isLocal == 1) continue;
            int mySlot = slot++;

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
            String clazz = dungeonPlayer != null ? dungeonPlayer.getDungeonClass() : null;
            if (playerEntity != null) {
                renderPlayerHead(context, mc, baseX, baseY, scale, headSize,
                        playerEntity.getX(), playerEntity.getZ(), playerEntity.getYRot(),
                        uuid, clazz, name, false, recordName);
            } else {
                double[] renderPos = MapScanner.mapToRenderPosition(mapX, mapZ, roomSize, doorGap);
                double rx = renderPos[0], rz = renderPos[1];
                float yaw = rotation * 22.5f;
                if (config.mapInterpolatePlayers && mySlot < LERP_SLOTS) {
                    float[] l = lerp(mySlot, rx, rz, yaw);
                    rx = l[0]; rz = l[1]; yaw = l[2];
                }
                int pixelX = baseX + (int)((MAP_PADDING + offX + rx) * scale);
                int pixelY = baseY + (int)((MAP_PADDING + offZ + rz) * scale);

                drawHeadMarker(context, pixelX, pixelY, headSize, yaw, uuid, name, false, recordName, clazz);
            }
        }
    }

    // NEU dmPlayerInterp: glide between two map decoration updates, snap on big jumps
    private static final int LERP_SLOTS = 8;
    private static final long LERP_MS = 500;
    private static final float[][] lerpFrom = new float[LERP_SLOTS][3], lerpTo = new float[LERP_SLOTS][3];
    private static final long[] lerpAt = new long[LERP_SLOTS];
    private static final float[] lerpOut = new float[3];

    private static float[] lerp(int i, double x, double z, float yaw) {
        long now = System.currentTimeMillis();
        float[] from = lerpFrom[i], to = lerpTo[i];
        float t = Math.clamp((now - lerpAt[i]) / (float) LERP_MS, 0f, 1f);
        float cx = from[0] + (to[0] - from[0]) * t;
        float cz = from[1] + (to[1] - from[1]) * t;
        float cyaw = from[2] + angleDelta(from[2], to[2]) * t;
        if (lerpAt[i] == 0 || to[0] != (float) x || to[1] != (float) z || to[2] != yaw) {
            float dx = (float) x - cx, dz = (float) z - cz;
            boolean snap = lerpAt[i] == 0 || dx * dx + dz * dz > (roomSize / 2f) * (roomSize / 2f);
            from[0] = snap ? (float) x : cx; from[1] = snap ? (float) z : cz; from[2] = snap ? yaw : cyaw;
            to[0] = (float) x; to[1] = (float) z; to[2] = yaw;
            lerpAt[i] = now;
            cx = from[0]; cz = from[1]; cyaw = from[2];
        }
        lerpOut[0] = cx; lerpOut[1] = cz; lerpOut[2] = cyaw;
        return lerpOut;
    }

    private static float angleDelta(float a, float b) {
        float d = (b - a) % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    // head + class-colored border rotate together via the pose matrix, name stays upright below
    private static void drawHeadMarker(GuiGraphicsExtractor context, int pixelX, int pixelY, int headSize,
                                       float rotationDeg, java.util.UUID uuid, String name, boolean isLocal,
                                       String recordName, String clazz) {
        if (recordName != null) playerMarkers.add(new PlayerMarker(pixelX, pixelY, recordName, isLocal));
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (highlightName != null && highlightName.equals(recordName)) headSize *= 2;
        int half = headSize / 2;
        boolean knownClass = clazz != null && !"EMPTY".equals(clazz);
        int classColor = knownClass ? com.teslamaps.dungeon.ClassColors.or(clazz, getDungeonClassColor(clazz)) : 0xFFDDDDDD;

        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(pixelX, pixelY);
        int hpRing = isLocal ? 0 : com.teslamaps.features.TeammateHealth.mapColor(recordName);
        if (hpRing != 0) {
            int t = Math.max(1, Math.round(headSize / 8f));
            int o = half + t * 3, i = half + t;
            context.fill(-o, -o, o, -i, hpRing);
            context.fill(-o, i, o, o, hpRing);
            context.fill(-o, -i, -i, i, hpRing);
            context.fill(i, -i, o, i, hpRing);
        }
        float rad = config.rotatePlayerHeads ? (float) Math.toRadians(rotationDeg) : 0f;
        if (rad != 0f) pose.rotate(rad);

        if (isLocal && config.mapSelfVanillaArrow) {
            int s = Math.max(8, headSize);
            context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, VANILLA_ARROW,
                    -s / 2, -s / 2, 0f, 0f, s, s, 8, 8, 8, 8);
        } else if (config.mapGlowArrow && (isLocal || !config.useHeadsInsteadOfMarkers || uuid == null)) {
            drawGlowArrow(context, Math.max(10, headSize) / 20f, knownClass ? classColor : 0xFF00FF00);
        } else if (config.useHeadsInsteadOfMarkers && uuid != null) {
            if (config.mapHeadBorder) {
                int t = Math.max(1, Math.round(headSize / 8f));
                int b = half + t;
                context.fill(-b, -b, b, -half, classColor);
                context.fill(-b, half, b, b, classColor);
                context.fill(-b, -half, -half, half, classColor);
                context.fill(half, -half, b, half, classColor);
            }
            PlayerHeadRenderer.drawPlayerHead(context, -half, -half, headSize, uuid);
        } else {
            int markerSize = Math.max(10, headSize);
            int tipLen = (int) (markerSize * 0.5f);
            int backLen = (int) (markerSize * 0.45f);
            int sideLen = (int) (markerSize * 0.35f);
            drawFilledTriangle(context, 0, -tipLen, -sideLen, backLen, sideLen, backLen,
                    knownClass ? classColor : 0xFF00FF00);
        }

        if (rad != 0f) pose.rotate(-rad);

        if (name != null && config.showPlayerNames
                && (!config.playerNamesOnlyWithLeap || holdingLeap())) {
            var font = Minecraft.getInstance().font;
            pose.translate(0, half + 3);
            float ns = 0.5f * config.mapPlayerNameScale;
            pose.scale(ns, ns);
            String shown = config.mapPlayerClassName && knownClass ? className(clazz) : name;
            int nameColor = knownClass && !"White".equals(config.mapPlayerNameColor) ? classColor : 0xFFFFFFFF;
            context.text(font, shown, -font.width(shown) / 2, 0, nameColor, true);
        }
        pose.popMatrix();
    }

    private static void renderPlayerHead(GuiGraphicsExtractor context, Minecraft mc,
                                          int baseX, int baseY, float scale, int headSize,
                                          double worldX, double worldZ, float yaw,
                                          java.util.UUID uuid, String clazz, String name, boolean isLocal, String recordName) {
        int[] gridPos = ComponentGrid.worldToGrid(worldX, worldZ);
        if (gridPos == null) return;

        int[] worldCenter = ComponentGrid.gridToWorld(gridPos[0], gridPos[1]);
        double roomOffsetX = (worldX - worldCenter[0] + ComponentGrid.HALF_ROOM_SIZE) / ComponentGrid.ROOM_SIZE;
        double roomOffsetZ = (worldZ - worldCenter[1] + ComponentGrid.HALF_ROOM_SIZE) / ComponentGrid.ROOM_SIZE;

        int pixelX = gridToPixelX(gridPos[0], baseX, scale) + (int)(roomOffsetX * roomSize * scale);
        int pixelY = gridToPixelY(gridPos[1], baseY, scale) + (int)(roomOffsetZ * roomSize * scale);

        drawHeadMarker(context, pixelX, pixelY, headSize, yaw + 180, uuid, name, isLocal, recordName, clazz);
    }

    private static final net.minecraft.resources.Identifier VANILLA_ARROW =
            net.minecraft.resources.Identifier.withDefaultNamespace("textures/map/decorations/player.png");

    private static final String[] CLASS_NAMES = {"Healer", "Mage", "Berserk", "Archer", "Tank"};

    private static String className(String clazz) {
        for (String c : CLASS_NAMES) if (c.equalsIgnoreCase(clazz)) return c;
        return clazz;
    }

    // Odin's class palette (DungeonEnums.kt)
    private static int getDungeonClassColor(String dungeonClass) {
        if (dungeonClass == null) return 0xFF5555FF;
        return switch (dungeonClass.toUpperCase()) {
            case "HEALER" -> 0xFFFF55FF; // Light Purple
            case "MAGE" -> 0xFF55FFFF;   // Aqua
            case "BERSERK" -> 0xFFAA0000; // Dark Red
            case "ARCHER" -> 0xFFFFAA00; // Gold
            case "TANK" -> 0xFF00AA00;   // Dark Green
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

    // Oracle Economy TerritoryMapScreen.renderPlayerMarker: notched arrow, outer glow / inner glow / core
    // baked once as a white alpha mask, tinted per marker
    private static final int ARROW_X0 = -12, ARROW_Y0 = -16, ARROW_W = 24, ARROW_H = 27;
    private static net.minecraft.resources.Identifier arrowTex = null;

    private static net.minecraft.resources.Identifier arrowTexture() {
        if (arrowTex != null) return arrowTex;
        float[] alpha = new float[ARROW_W * ARROW_H];
        float[] passAlpha = {0x1A / 255f, 0x44 / 255f, 1f};
        int[] expands = {3, 1, 0};
        int hw = 9, h = 20, notchHW = 6, notchD = 7;
        int tipY = -h * 2 / 3;
        int baseY = tipY + h;
        for (int pass = 0; pass < 3; pass++) {
            int ex = expands[pass];
            float a = passAlpha[pass];
            for (int row = tipY - ex; row <= baseY + ex; row++) {
                float triT = Math.clamp((float) (row - tipY) / h, 0f, 1f);
                int rowHW = (int) (hw * triT + ex + 0.5f);
                if (rowHW <= 0) continue;
                int notchStart = baseY - notchD;
                int nHW = 0;
                if (row > notchStart - ex) {
                    float nT = Math.clamp((float) (row - notchStart) / notchD, 0f, 1f);
                    nHW = Math.max(0, (int) (notchHW * nT + 0.5f) - ex);
                    if (nHW >= rowHW) nHW = 0;
                }
                for (int x = -rowHW; x < rowHW; x++) {
                    if (nHW > 0 && x >= -nHW && x < nHW) continue;
                    int i = (row - ARROW_Y0) * ARROW_W + (x - ARROW_X0);
                    alpha[i] = alpha[i] + a * (1f - alpha[i]);
                }
            }
        }
        com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(ARROW_W, ARROW_H, true);
        for (int y = 0; y < ARROW_H; y++)
            for (int x = 0; x < ARROW_W; x++)
                img.setPixel(x, y, (Math.round(alpha[y * ARROW_W + x] * 255f) << 24) | 0xFFFFFF);
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath("teslamaps", "map/glow_arrow");
        Minecraft.getInstance().getTextureManager()
                .register(id, new net.minecraft.client.renderer.texture.DynamicTexture(() -> "TeslaMaps glow arrow", img));
        arrowTex = id;
        return id;
    }

    private static void drawGlowArrow(GuiGraphicsExtractor context, float s, int color) {
        float pulse = (float) (0.9 + 0.1 * Math.sin(System.currentTimeMillis() / 150.0));
        int r = (int) (((color >> 16) & 0xFF) * pulse);
        int g = (int) (((color >> 8) & 0xFF) * pulse);
        int b = (int) ((color & 0xFF) * pulse);
        int rgb = (r << 16) | (g << 8) | b;
        var pose = context.pose();
        pose.pushMatrix();
        pose.scale(s, s);
        context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, arrowTexture(),
                ARROW_X0, ARROW_Y0, 0f, 0f, ARROW_W, ARROW_H, ARROW_W, ARROW_H, ARROW_W, ARROW_H, 0xFF000000 | rgb);
        pose.popMatrix();
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
