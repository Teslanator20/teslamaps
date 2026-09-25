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

    private static final java.util.regex.Pattern PUZZLE_FAIL = java.util.regex.Pattern.compile("^PUZZLE FAIL! (\\w{1,16}) .*$");
    private static final java.util.regex.Pattern QUIZ_FAIL = java.util.regex.Pattern.compile("^\\[STATUE] Oruo the Omniscient: (\\w{1,16}) chose the wrong answer!.*$");

    private static final java.util.regex.Pattern DRAFT_RESET = java.util.regex.Pattern.compile("^You used the Architect's First Draft to reset (.+)!$");

    private record Pending(long dueTick, Runnable action) {}
    private static final java.util.List<Pending> pending = new java.util.ArrayList<>();

    public static void init() {
        TeslaMaps.debug("AutoGFS initialized");
    }

    public static void tick() {
        if (!TeslaMapsConfig.get().autoGFS) return;

        tickCounter++;

        for (int i = pending.size() - 1; i >= 0; i--) {
            if (pending.get(i).dueTick() <= tickCounter) pending.remove(i).action().run();
        }

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
        if (!TeslaMapsConfig.get().autoGFS || LegitMode.blocksCheats()) return;

        message = message.replaceAll("(?i)§[0-9A-FK-OR]", "");

        if (TeslaMapsConfig.get().autoGFSOnStart && !dungeonStartHandled) {
            if (message.contains(DUNGEON_START_MSG1) || message.contains(DUNGEON_START_MSG2)) {
                dungeonStartHandled = true;
                TeslaMaps.debug("[AutoGFS] Dungeon started, refilling items...");
                scheduleRefill(20); // 1 second delay
            }
        }

        if (TeslaMapsConfig.get().autoGFSDraftAnnounce && DungeonManager.isInDungeon()) {
            java.util.regex.Matcher dm = DRAFT_RESET.matcher(message.trim());
            if (dm.matches()) Announce.party("Used Draft to Reset " + dm.group(1));
        }

        if (TeslaMapsConfig.get().autoGFSDraft) {
            if (failedBySelf(message.trim())) {
                TeslaMaps.debug("[AutoGFS] You failed the puzzle, getting draft...");
                if (TeslaMapsConfig.get().autoGFSDraftAlert) {
                    Minecraft.getInstance().gui.setTitle(net.minecraft.network.chat.Component.literal("\u00A7c\u00A7lDRAFT"));
                }
                scheduleCommand(30, "gfs architect's first draft 1");
            }
        }
    }

    // the fail message names the player who failed — only pull a draft when that's exactly us
    private static boolean failedBySelf(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        String self = mc.getUser().getName();
        if (self == null) return false;
        java.util.regex.Matcher m = PUZZLE_FAIL.matcher(message);
        if (!m.matches()) m = QUIZ_FAIL.matcher(message);
        return m.matches() && m.group(1).equals(self);
    }

    private static void scheduleRefill(int delayTicks) {
        pending.add(new Pending(tickCounter + delayTicks, AutoGFS::refillItems));
    }

    private static void scheduleCommand(int delayTicks, String command) {
        pending.add(new Pending(tickCounter + delayTicks, () -> sendCommand(command)));
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

        TeslaMaps.debug("[AutoGFS] Refilling items...");

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
            TeslaMaps.debug("[AutoGFS] Sent command: /" + command);
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
