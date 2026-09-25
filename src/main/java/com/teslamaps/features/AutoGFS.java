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

    private static final java.util.regex.Pattern STORM_LIGHTNING = java.util.regex.Pattern.compile("^\\[BOSS] Storm: (ENERGY HEED MY CALL|THUNDER LET ME BE YOUR CATALYST)!$");
    private static final String WK_P5 = "[BOSS] Wither King: I no longer wish to fight, but I know that will not stop you.";
    private static final String CORE_OPEN = "The Core entrance is opening!";
    private static boolean stormTwilightDone = false;
    private static Object lastLevel = null;
    private static boolean pearlKeyDown = false;

    public static void register() {
        com.teslamaps.registry.Feature.of("AutoGFSPearlKey").section("Auto")
                .when(() -> TeslaMapsConfig.get().autoGFSPearlKey != -1)
                .tick(AutoGFS::pearlKeyTick);
    }

    private static void pearlKeyTick() {
        Minecraft mc = Minecraft.getInstance();
        boolean down = mc.screen == null && com.teslamaps.utils.KeyNames.isDown(TeslaMapsConfig.get().autoGFSPearlKey);
        if (down && !pearlKeyDown) gfsPearlsToStack();
        pearlKeyDown = down;
    }

    private record Pending(long dueTick, Runnable action) {}
    private static final java.util.List<Pending> pending = new java.util.ArrayList<>();

    public static void init() {
        TeslaMaps.debug("AutoGFS initialized");
    }

    public static void tick() {
        if (!TeslaMapsConfig.get().autoGFS) return;

        tickCounter++;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            stormTwilightDone = false;
        }

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
        TeslaMapsConfig c = TeslaMapsConfig.get();
        message = message.replaceAll("(?i)§[0-9A-FK-OR]", "");
        boolean clickMode = DRAFT_CLICK.equals(c.draftMode);

        if (c.autoGFSDraft && clickMode && failedByUs(message.trim(), c.draftPartyFails)) {
            draftAlert();
            Minecraft.getInstance().gui.getChat().addClientSystemMessage(net.minecraft.network.chat.Component
                    .literal("§b§l[TeslaMaps] §eClick here to get Architect's First Draft")
                    .withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/gfs architect's first draft 1"))
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
                                    net.minecraft.network.chat.Component.literal("/gfs architect's first draft 1")))));
        }

        if (!c.autoGFS || LegitMode.blocksCheats()) return;

        if (c.autoGFSOnStart && !dungeonStartHandled) {
            if (message.contains(DUNGEON_START_MSG1) || message.contains(DUNGEON_START_MSG2)) {
                dungeonStartHandled = true;
                TeslaMaps.debug("[AutoGFS] Dungeon started, refilling items...");
                scheduleRefill(20); // 1 second delay
            }
        }

        if (c.autoGFSTwilight) twilight(message.trim());

        if (c.autoGFSDraftAnnounce && DungeonManager.isInDungeon()) {
            java.util.regex.Matcher dm = DRAFT_RESET.matcher(message.trim());
            if (dm.matches()) Announce.party("Used Draft to Reset " + dm.group(1));
        }

        if (c.autoGFSDraft && !clickMode) {
            if (failedByUs(message.trim(), c.draftPartyFails)) {
                TeslaMaps.debug("[AutoGFS] Puzzle failed, getting draft...");
                draftAlert();
                scheduleCommand(30, "gfs architect's first draft 1");
            }
        }
    }

    // NoammAddons: M7 twilight arrow poison at fixed boss lines, per class
    private static void twilight(String msg) {
        if (!DungeonManager.isInDungeon() || !DungeonManager.isInBossArea()) return;
        if (DungeonManager.getCurrentFloor() != com.teslamaps.dungeon.DungeonFloor.M7) return;
        String clazz = com.teslamaps.player.PlayerTracker.getLocalClass();
        if (clazz == null || "EMPTY".equals(clazz)) return;
        boolean dps = clazz.equals("Archer") || clazz.equals("Berserk");
        int amount = TeslaMapsConfig.get().autoGFSTwilightAmount;
        if (dps && msg.equals(CORE_OPEN)) {
            scheduleCommand(10, "gfs twilight_arrow_poison " + amount);
        } else if (!dps && msg.equals(WK_P5)) {
            scheduleCommand(10, "gfs twilight_arrow_poison " + amount);
        } else if (!stormTwilightDone && clazz.equals("Archer") && STORM_LIGHTNING.matcher(msg).matches()) {
            stormTwilightDone = true;
            scheduleCommand(10, "gfs twilight_arrow_poison " + amount);
        }
    }

    private static boolean localDead() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !DungeonManager.isInDungeon()) return false;
        return com.teslamaps.player.PlayerTracker.getPlayer(mc.player.getName().getString())
                .map(p -> !p.isAlive()).orElse(false);
    }

    private static boolean inKuudra() {
        return com.teslamaps.utils.SkyblockUtils.getArea().toLowerCase(java.util.Locale.ROOT).contains("kuudra");
    }

    public static final String DRAFT_AUTO = "Auto";
    public static final String DRAFT_CLICK = "Click in Chat";
    public static final String[] DRAFT_MODES = {DRAFT_AUTO, DRAFT_CLICK};

    private static void draftAlert() {
        if (TeslaMapsConfig.get().autoGFSDraftAlert) {
            Minecraft.getInstance().gui.setTitle(net.minecraft.network.chat.Component.literal("§c§lDRAFT"));
        }
    }

    // the fail message names the player who failed; party = any name, but only inside a dungeon
    private static boolean failedByUs(String message, boolean party) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        String self = mc.getUser().getName();
        if (self == null) return false;
        java.util.regex.Matcher m = PUZZLE_FAIL.matcher(message);
        if (!m.matches()) m = QUIZ_FAIL.matcher(message);
        if (!m.matches()) return false;
        if (m.group(1).equals(self)) return true;
        return party && DungeonManager.isInDungeon();
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

        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.autoGFSDungeonOnly && !DungeonManager.isInDungeon() && !(c.autoGFSKuudra && inKuudra())) {
            return;
        }
        if (c.autoGFSSkipDead && localDead()) return;

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
        int leapCount = 0;

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

            if (name.contains("spirit leap")) leapCount += stack.getCount();

            if (name.contains("superboom") || name.contains("super boom")) {
                hasTNT = true;
                tntCount += stack.getCount();
            }
        }

        int min = Math.max(1, c.autoGFSMinMissing);
        if (c.autoGFSPearls && hasPearls && 16 - pearlCount >= min) {
            int needed = 16 - pearlCount;
            sendCommand("gfs ender_pearl " + needed);
        }

        if (c.autoGFSJerry && hasJerry && 64 - jerryCount >= min) {
            int needed = 64 - jerryCount;
            sendCommand("gfs inflatable_jerry " + needed);
        }

        if (c.autoGFSTNT && hasTNT && 64 - tntCount >= min) {
            int needed = 64 - tntCount;
            sendCommand("gfs superboom_tnt " + needed);
        }

        if (c.autoGFSLeaps && leapCount > 0 && 16 - leapCount >= 4) {
            sendCommand("gfs spirit_leap " + (16 - leapCount));
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

    public static void gfsJerry() {
        gfsTopUp("inflatable_jerry", 64, s -> s.getHoverName().getString().toLowerCase().contains("inflatable jerry"));
    }

    public static void gfsLeaps() {
        gfsTopUp("spirit_leap", 16, s -> s.getHoverName().getString().toLowerCase().contains("spirit leap"));
    }

    // Shizo: pearls up to the next multiple of 16
    public static void gfsPearlsToStack() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains("ender_pearl")) count += stack.getCount();
        }
        int needed = count % 16 == 0 ? 16 : 16 - count % 16;
        sendCommand("gfs ender_pearl " + needed);
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
