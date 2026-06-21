/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.features;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

public class AutoGFS {
    private static int tickCounter = 0;
    private static long lastRefillTime = 0;
    private static boolean dungeonStartHandled = false;

    private static final String DUNGEON_START_MSG1 = "[NPC] Mort: Here, I found this map when I first entered the dungeon.";
    private static final String DUNGEON_START_MSG2 = "[NPC] Mort: Right-click the Orb for spells, and Left-click (or Drop) to use your Ultimate!";

    private static final String PUZZLE_FAIL_PREFIX = "PUZZLE FAIL!";
    private static final String STATUE_FAIL_PREFIX = "[STATUE] Oruo the Omniscient:";

    public static void init() {
        TeslaMaps.LOGGER.info("AutoGFS initialized");
    }

    public static void tick() {
        if (!TeslaMapsConfig.get().autoGFS) return;

        tickCounter++;

        if (TeslaMapsConfig.get().autoGFSTimer) {
            int intervalTicks = TeslaMapsConfig.get().autoGFSInterval * 20;
            if (tickCounter % intervalTicks == 0) {
                refillItems();
            }
        }

        if (!DungeonManager.isInDungeon()) {
            dungeonStartHandled = false;
        }
    }

    public static void onChatMessage(String message) {
        if (!TeslaMapsConfig.get().autoGFS) return;

        message = message.replaceAll("(?i)§[0-9A-FK-OR]", "");

        if (TeslaMapsConfig.get().autoGFSOnStart && !dungeonStartHandled) {
            if (message.contains(DUNGEON_START_MSG1) || message.contains(DUNGEON_START_MSG2)) {
                dungeonStartHandled = true;
                TeslaMaps.LOGGER.info("[AutoGFS] Dungeon started, refilling items...");
                scheduleRefill(20); // 1 second delay
            }
        }

        if (TeslaMapsConfig.get().autoGFSDraft) {
            if (message.contains(PUZZLE_FAIL_PREFIX) ||
                (message.contains(STATUE_FAIL_PREFIX) && message.contains("chose the wrong answer"))) {
                TeslaMaps.LOGGER.info("[AutoGFS] Puzzle failed, getting draft...");
                scheduleCommand(30, "gfs architect's first draft 1");
            }
        }
    }

    private static void scheduleRefill(int delayTicks) {
        new Thread(() -> {
            try {
                Thread.sleep(delayTicks * 50L); // 50ms per tick
                Minecraft.getInstance().execute(AutoGFS::refillItems); // inventory/networking on main thread
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private static void scheduleCommand(int delayTicks, String command) {
        new Thread(() -> {
            try {
                Thread.sleep(delayTicks * 50L);
                Minecraft.getInstance().execute(() -> sendCommand(command));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    public static void refillItems() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        if (TeslaMapsConfig.get().autoGFSDungeonOnly && !DungeonManager.isInDungeon()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastRefillTime < 2000) return;
        lastRefillTime = now;

        TeslaMaps.LOGGER.info("[AutoGFS] Refilling items...");

        boolean hasPearls = false;
        boolean hasJerry = false;
        boolean hasTNT = false;
        int pearlCount = 0;
        int jerryCount = 0;
        int tntCount = 0;

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

            if (itemId.contains("ender_pearl")) {
                hasPearls = true;
                pearlCount += stack.getCount();
            }

            String name = stack.getHoverName().getString().toLowerCase();
            if (name.contains("inflatable jerry")) {
                hasJerry = true;
                jerryCount += stack.getCount();
            }

            if (name.contains("superboom") || name.contains("super boom")) {
                hasTNT = true;
                tntCount += stack.getCount();
            }
        }

        if (TeslaMapsConfig.get().autoGFSPearls && hasPearls && pearlCount < 16) {
            int needed = 16 - pearlCount;
            sendCommand("gfs ender_pearl " + needed);
        }

        if (TeslaMapsConfig.get().autoGFSJerry && hasJerry && jerryCount < 64) {
            int needed = 64 - jerryCount;
            sendCommand("gfs inflatable_jerry " + needed);
        }

        if (TeslaMapsConfig.get().autoGFSTNT && hasTNT && tntCount < 64) {
            int needed = 64 - tntCount;
            sendCommand("gfs superboom_tnt " + needed);
        }
    }

    private static void sendCommand(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (mc.getConnection() != null) {
            mc.getConnection().sendCommand(command);
            TeslaMaps.LOGGER.info("[AutoGFS] Sent command: /" + command);
        }
    }

    public static void forceRefill() {
        lastRefillTime = 0; // Reset cooldown
        refillItems();
    }

    public static void gfsEnderPearls() {
        gfsTopUp("ender_pearl", 16, s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString().contains("ender_pearl"));
    }

    public static void gfsSuperboom() {
        gfsTopUp("superboom_tnt", 64, s -> {
            String n = s.getHoverName().getString().toLowerCase();
            return n.contains("superboom") || n.contains("super boom");
        });
    }

    private static void gfsTopUp(String gfsId, int max, java.util.function.Predicate<ItemStack> match) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && match.test(stack)) count += stack.getCount();
        }
        int needed = max - count;
        if (needed > 0) sendCommand("gfs " + gfsId + " " + needed);
    }
}
