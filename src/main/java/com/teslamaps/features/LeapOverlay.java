package com.teslamaps.features;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.player.PlayerTracker.DungeonPlayer;
import com.teslamaps.render.MapRenderer;
import com.teslamaps.render.PlayerHeadRenderer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.lwjgl.glfw.GLFW;
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
            return "Spirit Leap".equals(cleanTitle) || "Teleport to Player".equals(cleanTitle);
        }
        return false;
    }

    private static final Pattern LEAPED_PATTERN = Pattern.compile("You have teleported to (\\w{1,16})!");

    /** Announce leaps to party chat (Odin "Leap Announce"). */
    public static void onChatMessage(String message) {
        if (!TeslaMapsConfig.get().leapAnnounce || !DungeonManager.isInDungeon()) return;
        Matcher m = LEAPED_PATTERN.matcher(message);
        if (m.find() && mc.getConnection() != null) {
            mc.getConnection().sendCommand("pc Leaped to " + m.group(1) + "!");
        }
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

        pollKeybinds(screen);

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

        // Dungeon map at the top-center (click it to leap to the nearest player). Hidden in the
        // boss room (no dungeon grid there) — the rest of the leap menu still works.
        if (TeslaMapsConfig.get().leapShowMap && DungeonManager.isInDungeon() && !DungeonManager.isInBoss()) {
            float mapScale = TeslaMapsConfig.get().mapScale;
            int mw = MapRenderer.mapW();
            int mapBaseX = mw > 0 ? screenWidth / 2 - mw / 2 : screenWidth / 2 - 60;
            MapRenderer.renderAt(context, mapBaseX, 6, mapScale, true);
        }

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

        // Draw the player head (Odin style), or a colored class-letter box as fallback.
        int iconSize = (int)(32 * scale);
        int iconX = x + (int)(4 * scale);
        int iconY = y + (height - iconSize) / 2;

        if (TeslaMapsConfig.get().leapShowHeads && player.uuid != null) {
            PlayerHeadRenderer.drawPlayerHead(context, iconX, iconY, iconSize, player.uuid);
        } else {
            context.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, classColor);
            String letter = CLASS_LETTERS.getOrDefault(player.dungeonClass, "?");
            int letterX = iconX + (iconSize - textRenderer.width(letter)) / 2;
            int letterY = iconY + (iconSize - 8) / 2;
            context.text(textRenderer, letter, letterX, letterY, 0xFFFFFFFF, true);
        }

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

    private static final Set<Integer> heldLeapKeys = new HashSet<>();

    /** Poll the leap keybinds (Corners or Class mode) while the menu is open and leap on press. */
    private static void pollKeybinds(ContainerScreen screen) {
        if (mc.getWindow() == null) return;
        long handle = mc.getWindow().handle();
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if ("Class".equals(c.leapKeybindMode)) {
            pollKey(handle, c.leapKeyArcher, () -> leapToClass(screen, "Archer"));
            pollKey(handle, c.leapKeyBerserk, () -> leapToClass(screen, "Berserk"));
            pollKey(handle, c.leapKeyHealer, () -> leapToClass(screen, "Healer"));
            pollKey(handle, c.leapKeyMage, () -> leapToClass(screen, "Mage"));
            pollKey(handle, c.leapKeyTank, () -> leapToClass(screen, "Tank"));
        } else {
            pollKey(handle, c.leapKeyTL, () -> leapToQuadrant(screen, 0));
            pollKey(handle, c.leapKeyTR, () -> leapToQuadrant(screen, 1));
            pollKey(handle, c.leapKeyBL, () -> leapToQuadrant(screen, 2));
            pollKey(handle, c.leapKeyBR, () -> leapToQuadrant(screen, 3));
        }
    }

    private static void pollKey(long handle, int key, Runnable action) {
        if (key < 0) return;
        boolean down = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        boolean was = heldLeapKeys.contains(key);
        if (down && !was) action.run();
        if (down) heldLeapKeys.add(key); else heldLeapKeys.remove(key);
    }

    private static void leapToQuadrant(ContainerScreen screen, int q) {
        LeapPlayer p = leapPlayers[q];
        if (p == null || p.isDead) return;
        leapToPlayer(screen, p.name);
    }

    private static void leapToClass(ContainerScreen screen, String clazz) {
        for (LeapPlayer p : leapPlayers) {
            if (p != null && !p.isDead && clazz.equals(p.dungeonClass)) { leapToPlayer(screen, p.name); return; }
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

        // Click on the embedded map -> leap to the player whose marker is nearest the click.
        if (TeslaMapsConfig.get().leapShowMap && !DungeonManager.isInBoss()) {
            int mx = MapRenderer.mapX(), my = MapRenderer.mapY(), mw = MapRenderer.mapW(), mh = MapRenderer.mapH();
            if (mw > 0 && mouseX >= mx && mouseX <= mx + mw && mouseY >= my && mouseY <= my + mh) {
                String nearest = null;
                double best = Double.MAX_VALUE;
                for (MapRenderer.PlayerMarker m : MapRenderer.getPlayerMarkers()) {
                    if (m.self()) continue;
                    double d = (m.x() - mouseX) * (m.x() - mouseX) + (m.y() - mouseY) * (m.y() - mouseY);
                    if (d < best) { best = d; nearest = m.name(); }
                }
                if (nearest != null) leapToPlayer(screen, nearest);
                return true; // consume the click whether or not a player was found
            }
        }

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
            // Only the leap GUI's own slots — not the player's inventory (skull items there are
            // gear like Bonzo's Mask / Necron's Head, not leap targets).
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
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
            // Skip the player's own inventory — its skull items (Bonzo's Mask, Necron's Head, …)
            // are NOT leap targets and would show up as fake players.
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || stack.getItem() != Items.PLAYER_HEAD) continue;

            String itemName = ChatFormatting.stripFormatting(stack.getHoverName().getString());
            if (itemName == null || itemName.isEmpty()) continue;

            DungeonPlayer dp = playerMap.get(itemName);
            String dungeonClass = dp != null ? dp.getDungeonClass() : null;
            boolean isDead = dp != null && !dp.isAlive();
            UUID uuid = dp != null ? dp.getUuid() : null;

            allPlayers.add(new LeapPlayer(itemName, dungeonClass, isDead, slot.index, uuid));
        }

        // Apply the selected sorting mode
        sortPlayers(allPlayers);
    }

    private static int classOrder(String c) {
        if (c == null) return 99;
        return switch (c) {
            case "Archer" -> 0; case "Berserk" -> 1; case "Healer" -> 2; case "Mage" -> 3; case "Tank" -> 4;
            default -> 98;
        };
    }

    /** Dispatch on the configured sort mode and fill the 4 quadrants. */
    private static void sortPlayers(List<LeapPlayer> players) {
        String mode = TeslaMapsConfig.get().leapSortMode;
        if ("Odin".equals(mode)) { odinSort(players); return; }

        switch (mode) {
            case "Class A-Z" -> players.sort(Comparator.comparingInt((LeapPlayer p) -> classOrder(p.dungeonClass)).thenComparing(p -> p.name.toLowerCase()));
            case "Name A-Z" -> players.sort(Comparator.comparing(p -> p.name.toLowerCase()));
            case "Custom" -> {
                List<String> order = TeslaMapsConfig.get().leapCustomOrder;
                players.sort(Comparator.comparingInt(p -> {
                    int i = order.indexOf(p.name.toLowerCase());
                    return i == -1 ? Integer.MAX_VALUE : i;
                }));
            }
            default -> { } // "None": keep menu order
        }
        for (int i = 0; i < players.size() && i < 4; i++) leapPlayers[i] = players.get(i);
    }

    /**
     * Odin sorting: place players in their default class quadrant, overflow to empty slots.
     */
    private static void odinSort(List<LeapPlayer> players) {
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
        final UUID uuid;

        LeapPlayer(String name, String dungeonClass, boolean isDead, int slotId, UUID uuid) {
            this.name = name;
            this.dungeonClass = dungeonClass;
            this.isDead = isDead;
            this.slotId = slotId;
            this.uuid = uuid;
        }
    }
}
