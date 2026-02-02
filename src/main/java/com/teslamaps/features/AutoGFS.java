package com.teslamaps.features;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

/**
 * Auto Get From Sack (GFS) feature.
 * Automatically refills items from sacks using /gfs command.
 * Based on OdinClient's AutoGFS.
 */
public class AutoGFS {
    private static int tickCounter = 0;
    private static long lastRefillTime = 0;
    private static boolean dungeonStartHandled = false;

    // Chat messages that indicate dungeon start
    private static final String DUNGEON_START_MSG1 = "[NPC] Mort: Here, I found this map when I first entered the dungeon.";
    private static final String DUNGEON_START_MSG2 = "[NPC] Mort: Right-click the Orb for spells, and Left-click (or Drop) to use your Ultimate!";

    // Puzzle fail messages for auto draft
    private static final String PUZZLE_FAIL_PREFIX = "PUZZLE FAIL!";
    private static final String STATUE_FAIL_PREFIX = "[STATUE] Oruo the Omniscient:";

    public static void init() {
        TeslaMaps.LOGGER.info("AutoGFS initialized");
    }

    /**
     * Called every tick.
     */
    public static void tick() {
        if (!TeslaMapsConfig.get().autoGFS) return;

        tickCounter++;

        // Timer-based refill
        if (TeslaMapsConfig.get().autoGFSTimer) {
            int intervalTicks = TeslaMapsConfig.get().autoGFSInterval * 20;
            if (tickCounter % intervalTicks == 0) {
                refillItems();
            }
        }

        // Reset dungeon start handler when not in dungeon
        if (!DungeonManager.isInDungeon()) {
            dungeonStartHandled = false;
        }
    }

    /**
     * Called when a chat message is received.
     */
    public static void onChatMessage(String message) {
        if (!TeslaMapsConfig.get().autoGFS) return;

        // Check for dungeon start
        if (TeslaMapsConfig.get().autoGFSOnStart && !dungeonStartHandled) {
            if (message.contains(DUNGEON_START_MSG1) || message.contains(DUNGEON_START_MSG2)) {
                dungeonStartHandled = true;
                TeslaMaps.LOGGER.info("[AutoGFS] Dungeon started, refilling items...");
                // Delay refill slightly to avoid issues
                scheduleRefill(20); // 1 second delay
            }
        }

        // Check for puzzle fail (for auto draft)
        if (TeslaMapsConfig.get().autoGFSDraft) {
            if (message.contains(PUZZLE_FAIL_PREFIX) ||
                (message.contains(STATUE_FAIL_PREFIX) && message.contains("chose the wrong answer"))) {
                TeslaMaps.LOGGER.info("[AutoGFS] Puzzle failed, getting draft...");
                scheduleCommand(30, "gfs architect's first draft 1");
            }
        }
    }

    /**
     * Schedule a refill after a delay.
     */
    private static void scheduleRefill(int delayTicks) {
        // Simple delayed execution using tick counter
        final int targetTick = tickCounter + delayTicks;
        new Thread(() -> {
            try {
                Thread.sleep(delayTicks * 50L); // 50ms per tick
                refillItems();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Schedule a command after a delay.
     */
    private static void scheduleCommand(int delayTicks, String command) {
        new Thread(() -> {
            try {
                Thread.sleep(delayTicks * 50L);
                sendCommand(command);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Refill items from sacks.
     */
    public static void refillItems() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen != null) return;

        // Check location restrictions
        if (TeslaMapsConfig.get().autoGFSDungeonOnly && !DungeonManager.isInDungeon()) {
            return;
        }

        // Cooldown check (minimum 2 seconds between refills)
        long now = System.currentTimeMillis();
        if (now - lastRefillTime < 2000) return;
        lastRefillTime = now;

        TeslaMaps.LOGGER.info("[AutoGFS] Refilling items...");

        // Check inventory for items and refill
        boolean hasPearls = false;
        boolean hasJerry = false;
        boolean hasTNT = false;
        int pearlCount = 0;
        int jerryCount = 0;
        int tntCount = 0;

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            String itemId = Registries.ITEM.getId(stack.getItem()).toString();

            // Check for ender pearls
            if (itemId.contains("ender_pearl")) {
                hasPearls = true;
                pearlCount += stack.getCount();
            }

            // Check for inflatable jerry (by name since it's a custom item)
            String name = stack.getName().getString().toLowerCase();
            if (name.contains("inflatable jerry")) {
                hasJerry = true;
                jerryCount += stack.getCount();
            }

            // Check for superboom TNT
            if (name.contains("superboom") || name.contains("super boom")) {
                hasTNT = true;
                tntCount += stack.getCount();
            }
        }

        // Refill pearls if enabled and we have some (don't get if we have none - player might not want them)
        if (TeslaMapsConfig.get().autoGFSPearls && hasPearls && pearlCount < 16) {
            int needed = 16 - pearlCount;
            sendCommand("gfs ender_pearl " + needed);
        }

        // Refill jerry if enabled and we have some
        if (TeslaMapsConfig.get().autoGFSJerry && hasJerry && jerryCount < 64) {
            int needed = 64 - jerryCount;
            sendCommand("gfs inflatable_jerry " + needed);
        }

        // Refill TNT if enabled and we have some
        if (TeslaMapsConfig.get().autoGFSTNT && hasTNT && tntCount < 64) {
            int needed = 64 - tntCount;
            sendCommand("gfs superboom_tnt " + needed);
        }
    }

    /**
     * Send a command to the server.
     */
    private static void sendCommand(String command) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // Use network handler to send command
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendChatCommand(command);
            TeslaMaps.LOGGER.info("[AutoGFS] Sent command: /" + command);
        }
    }

    /**
     * Force a manual refill (can be called from command or keybind).
     */
    public static void forceRefill() {
        lastRefillTime = 0; // Reset cooldown
        refillItems();
    }
}
