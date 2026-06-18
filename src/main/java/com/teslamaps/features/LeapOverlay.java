package com.teslamaps.features;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.player.PlayerTracker.DungeonPlayer;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Enhanced Spirit Leap overlay - renders a custom 2x2 grid with player info.
 * Spirit Leap menu overlay with class icons and sorting.
 */
public class LeapOverlay {
    private static final Minecraft mc = Minecraft.getInstance();

    // Class color mappings 
    private static final Map<String, Integer> CLASS_COLORS = new HashMap<>();
    private static final Map<String, String> CLASS_LETTERS = new HashMap<>();
    private static final Map<String, Integer> CLASS_DEFAULT_QUADRANT = new HashMap<>();
    private static final Map<String, Integer> CLASS_PRIORITY = new HashMap<>();

    static {
        // Colors
        CLASS_COLORS.put("Archer", 0xFFFFAA00);     // Gold
        CLASS_COLORS.put("Berserk", 0xFFAA0000);    // Dark Red
        CLASS_COLORS.put("Healer", 0xFFFF55FF);     // Light Purple
        CLASS_COLORS.put("Mage", 0xFF55FFFF);       // Aqua
        CLASS_COLORS.put("Tank", 0xFF00AA00);       // Dark Green

        // Letters
        CLASS_LETTERS.put("Archer", "A");
        CLASS_LETTERS.put("Berserk", "B");
        CLASS_LETTERS.put("Healer", "H");
        CLASS_LETTERS.put("Mage", "M");
        CLASS_LETTERS.put("Tank", "T");

        // Default quadrants (0=top-left, 1=top-right, 2=bottom-left, 3=bottom-right)
        CLASS_DEFAULT_QUADRANT.put("Archer", 0);
        CLASS_DEFAULT_QUADRANT.put("Berserk", 1);
        CLASS_DEFAULT_QUADRANT.put("Healer", 2);
        CLASS_DEFAULT_QUADRANT.put("Mage", 3);
        CLASS_DEFAULT_QUADRANT.put("Tank", 3);

        // Priority (lower = placed first)
        CLASS_PRIORITY.put("Berserk", 0);
        CLASS_PRIORITY.put("Tank", 1);
        CLASS_PRIORITY.put("Archer", 2);
        CLASS_PRIORITY.put("Healer", 2);
        CLASS_PRIORITY.put("Mage", 2);
    }

    // Store players for current leap menu (4 quadrants)
    private static final LeapPlayer[] leapPlayers = new LeapPlayer[4];
    private static long lastScanTime = 0;
    private static boolean menuOpen = false;

    // Quadrant layout - scaled sizes
    private static final int BASE_BOX_WIDTH = 100;
    private static final int BASE_BOX_HEIGHT = 50;
    private static final int BASE_GAP = 8;

    /**
     * Check if we should render the custom leap overlay.
     */
    public static boolean shouldRender() {
        if (!TeslaMapsConfig.get().leapOverlay) return false;
        if (!DungeonManager.isInDungeon()) return false;
        if (mc.screen == null) return false;

        if (mc.screen instanceof ContainerScreen containerScreen) {
            String title = containerScreen.getTitle().getString();
            String cleanTitle = ChatFormatting.stripFormatting(title);
            return "Spirit Leap".equals(cleanTitle);
        }
        return false;
    }

    /**
     * Render the custom leap menu overlay.
     */
    public static void render(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (!shouldRender()) {
            menuOpen = false;
            return;
        }

        ContainerScreen screen = (ContainerScreen) mc.screen;
        if (screen == null) return;

        // Scan for players if menu just opened or periodically
        long now = System.currentTimeMillis();
        if (!menuOpen || now - lastScanTime > 500) {
            scanLeapPlayers(screen);
            lastScanTime = now;
            menuOpen = true;
        }

        // Check if any players
        boolean hasPlayers = false;
        for (LeapPlayer p : leapPlayers) {
            if (p != null) {
                hasPlayers = true;
                break;
            }
        }
        if (!hasPlayers) return;

        Font textRenderer = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Scale based on screen size (smaller on smaller screens)
        float scale = Math.min(screenWidth / 400f, screenHeight / 250f);
        scale = Math.max(0.5f, Math.min(scale, 1.5f)); // Clamp between 0.5 and 1.5

        int boxWidth = (int) (BASE_BOX_WIDTH * scale);
        int boxHeight = (int) (BASE_BOX_HEIGHT * scale);
        int gap = (int) (BASE_GAP * scale);

        // Calculate center position for 2x2 grid
        int totalWidth = boxWidth * 2 + gap;
        int totalHeight = boxHeight * 2 + gap;
        int startX = (screenWidth - totalWidth) / 2;
        int startY = (screenHeight - totalHeight) / 2;

        // Render semi-transparent background
        context.fill(startX - 8, startY - 8, startX + totalWidth + 8, startY + totalHeight + 8, 0xC0000000);

        // Render each player in their quadrant
        for (int i = 0; i < 4; i++) {
            LeapPlayer player = leapPlayers[i];
            if (player == null) continue;

            // Calculate quadrant position (0=top-left, 1=top-right, 2=bottom-left, 3=bottom-right)
            int col = i % 2;
            int row = i / 2;
            int boxX = startX + col * (boxWidth + gap);
            int boxY = startY + row * (boxHeight + gap);

            // Check if mouse is hovering
            boolean hovered = mouseX >= boxX && mouseX < boxX + boxWidth &&
                              mouseY >= boxY && mouseY < boxY + boxHeight;

            renderPlayerBox(context, textRenderer, player, boxX, boxY, boxWidth, boxHeight, hovered, scale);
        }
    }

    /**
     * Render a single player box.
     */
    private static void renderPlayerBox(GuiGraphicsExtractor context, Font textRenderer,
                                         LeapPlayer player, int x, int y, int width, int height,
                                         boolean hovered, float scale) {
        // Get class color
        int classColor = CLASS_COLORS.getOrDefault(player.dungeonClass, 0xFFFFFFFF);

        // Background color - lighter if hovered
        int bgColor = hovered ? 0xE0505050 : 0xC0303030;
        if (player.isDead) {
            bgColor = hovered ? 0xE0602020 : 0xC0401010;
        }

        // Draw box background
        context.fill(x, y, x + width, y + height, bgColor);

        // Draw colored border
        int borderColor = player.isDead ? 0xFFFF0000 : classColor;
        int borderWidth = Math.max(1, (int)(2 * scale));
        context.fill(x, y, x + width, y + borderWidth, borderColor); // Top
        context.fill(x, y + height - borderWidth, x + width, y + height, borderColor); // Bottom
        context.fill(x, y, x + borderWidth, y + height, borderColor); // Left
        context.fill(x + width - borderWidth, y, x + width, y + height, borderColor); // Right

        // Draw class icon box with letter
        int iconSize = (int)(32 * scale);
        int iconX = x + (int)(4 * scale);
        int iconY = y + (height - iconSize) / 2;

        // Draw colored box
        context.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, classColor);

        // Draw class letter centered
        String letter = CLASS_LETTERS.getOrDefault(player.dungeonClass, "?");
        int letterX = iconX + (iconSize - textRenderer.width(letter)) / 2;
        int letterY = iconY + (iconSize - 8) / 2;
        context.text(textRenderer, letter, letterX, letterY, 0xFFFFFFFF, true);

        // Draw player name
        int textX = iconX + iconSize + (int)(6 * scale);
        int nameY = y + (int)(8 * scale);
        int nameColor = player.isDead ? 0xFFFF5555 : 0xFFFFFFFF;
        context.text(textRenderer, player.name, textX, nameY, nameColor, true);

        // Draw class name (or DEAD)
        int classY = y + (int)(20 * scale);
        if (player.isDead) {
            context.text(textRenderer, "DEAD", textX, classY, 0xFFFF0000, true);
        } else {
            String classText = player.dungeonClass != null ? player.dungeonClass : "Unknown";
            context.text(textRenderer, classText, textX, classY, classColor, true);
        }
    }

    /**
     * Handle mouse click on the leap menu.
     * Clicking anywhere in a screen quadrant selects that player.
     * Returns true if click was handled.
     */
    public static boolean handleClick(double mouseX, double mouseY, int button) {
        if (!shouldRender()) return false;
        if (button != 0) return false; // Only left click

        ContainerScreen screen = (ContainerScreen) mc.screen;
        if (screen == null) return false;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int halfWidth = screenWidth / 2;
        int halfHeight = screenHeight / 2;

        // Determine which screen quadrant was clicked (full screen hitboxes)
        int quadrant;
        if (mouseX < halfWidth && mouseY < halfHeight) {
            quadrant = 0; // Top-left
        } else if (mouseX >= halfWidth && mouseY < halfHeight) {
            quadrant = 1; // Top-right
        } else if (mouseX < halfWidth && mouseY >= halfHeight) {
            quadrant = 2; // Bottom-left
        } else {
            quadrant = 3; // Bottom-right
        }

        LeapPlayer player = leapPlayers[quadrant];
        if (player == null) {
            return true; // Still consume click even if no player in quadrant
        }

        if (player.isDead) {
            TeslaMaps.LOGGER.info("[LeapOverlay] Cannot leap to dead player: {}", player.name);
            return true;
        }

        leapToPlayer(screen, player.name);
        return true;
    }

    /**
     * Find and click the slot for the given player name.
     */
    private static void leapToPlayer(ContainerScreen screen, String playerName) {
        var handler = screen.getMenu();

        for (Slot slot : handler.slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || stack.getItem() != Items.PLAYER_HEAD) continue;

            String itemName = ChatFormatting.stripFormatting(stack.getHoverName().getString());
            if (itemName != null && itemName.equals(playerName)) {
                if (mc.gameMode != null && mc.player != null) {
                    mc.gameMode.handleContainerInput(handler.containerId, slot.index, 0,
                        net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
                    TeslaMaps.LOGGER.info("[LeapOverlay] Leaping to {}", playerName);
                }
                return;
            }
        }
        TeslaMaps.LOGGER.warn("[LeapOverlay] Could not find slot for player: {}", playerName);
    }

    /**
     * Scan the leap menu for players and sort by class.
     */
    private static void scanLeapPlayers(ContainerScreen screen) {
        // Clear array
        Arrays.fill(leapPlayers, null);

        var handler = screen.getMenu();
        List<DungeonPlayer> dungeonPlayers = PlayerTracker.getPlayers();
        Map<String, DungeonPlayer> playerMap = new HashMap<>();
        for (DungeonPlayer dp : dungeonPlayers) {
            playerMap.put(dp.getName(), dp);
        }

        // Collect all players from slots
        List<LeapPlayer> allPlayers = new ArrayList<>();
        for (Slot slot : handler.slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || stack.getItem() != Items.PLAYER_HEAD) continue;

            String itemName = ChatFormatting.stripFormatting(stack.getHoverName().getString());
            if (itemName == null || itemName.isEmpty()) continue;

            DungeonPlayer dp = playerMap.get(itemName);
            String dungeonClass = dp != null ? dp.getDungeonClass() : null;
            boolean isDead = dp != null && !dp.isAlive();

            allPlayers.add(new LeapPlayer(itemName, dungeonClass, isDead, slot.index));
        }

        // Apply sorting algorithm
        sortPlayersByClass(allPlayers);
    }

    /**
     * Sort players into quadrants by class.
     * Places players in their default quadrant, with overflow going to empty slots.
     */
    private static void sortPlayersByClass(List<LeapPlayer> players) {
        // Sort by priority (lower = processed first)
        players.sort((a, b) -> {
            int prioA = CLASS_PRIORITY.getOrDefault(a.dungeonClass, 99);
            int prioB = CLASS_PRIORITY.getOrDefault(b.dungeonClass, 99);
            return Integer.compare(prioA, prioB);
        });

        List<LeapPlayer> secondRound = new ArrayList<>();

        // First pass: try to place each player in their default quadrant
        for (LeapPlayer player : players) {
            int defaultQuadrant = CLASS_DEFAULT_QUADRANT.getOrDefault(player.dungeonClass, 0);
            if (leapPlayers[defaultQuadrant] == null) {
                leapPlayers[defaultQuadrant] = player;
            } else {
                secondRound.add(player);
            }
        }

        // Second pass: fill remaining empty quadrants
        for (LeapPlayer player : secondRound) {
            for (int i = 0; i < 4; i++) {
                if (leapPlayers[i] == null) {
                    leapPlayers[i] = player;
                    break;
                }
            }
        }
    }

    public static void reset() {
        Arrays.fill(leapPlayers, null);
        menuOpen = false;
    }

    /**
     * Data class for leap menu players.
     */
    private static class LeapPlayer {
        final String name;
        final String dungeonClass;
        final boolean isDead;
        final int slotId;

        LeapPlayer(String name, String dungeonClass, boolean isDead, int slotId) {
            this.name = name;
            this.dungeonClass = dungeonClass;
            this.isDead = isDead;
            this.slotId = slotId;
        }
    }
}
