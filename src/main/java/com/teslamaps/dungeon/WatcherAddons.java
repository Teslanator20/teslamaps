package com.teslamaps.dungeon;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ServerTickTimer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

// Times the Watcher (blood camp) dialogue and first move. Optional live HUD shows Dialogue / Move / Camp.
public class WatcherAddons {

    private static final String DIALOGUE = "[BOSS] The Watcher: Let's see how you can handle this.";
    private static final String PASS = "[BOSS] The Watcher: You have proven yourself. You may pass.";

    private static long startMs = 0L;     // first "[BOSS] The Watcher:" line of the fight
    private static long dialogueMs = 0L;  // time taken to reach the dialogue line
    private static long moveMs = 0L;      // time taken until the Watcher first moves
    private static long startTick = 0L, dialogueTicks = 0L, moveTicks = 0L;
    private static boolean graded = false;
    private static Vec3 dialoguePos = null; // watcher position captured at dialogue (to detect its move)
    private static Zombie ownWatcher = null;
    private static int scanCooldown = 0;

    public static void onChatMessage(String raw) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.watcherHud && !c.watcherChatSplits) return;
        if (!DungeonManager.isInDungeon()) return;
        String msg = raw.replaceAll("(?i)§[0-9A-FK-OR]", "");

        if (msg.equals("Starting in 1 second.")) { reset(); return; }

        if (startMs == 0L && msg.startsWith("[BOSS] The Watcher:")) {
            startMs = System.currentTimeMillis();
            startTick = ServerTickTimer.now();
        } else if (msg.equals(DIALOGUE)) {
            if (startMs == 0L || graded) return;
            dialogueMs = System.currentTimeMillis() - startMs;
            dialogueTicks = ServerTickTimer.now() - startTick;
            graded = true;
        } else if (msg.equals(PASS)) {
            if (startMs == 0L) return;
            long full = System.currentTimeMillis() - startMs, fullT = ServerTickTimer.now() - startTick;
            if (c.watcherChatSplits) splits(full, fullT);
            reset();
        }
    }

    // tracks the Watcher's first movement after the dialogue phase
    public static void tick() {
        if (startMs == 0L || !graded || moveMs != 0L) return;
        Zombie w = BloodCamp.getWatcherEntity();
        if (w == null && TeslaMapsConfig.get().watcherChatSplits) w = findWatcher();
        if (w == null) return;
        if (dialoguePos == null) {
            dialoguePos = w.position();
        } else if (w.position().distanceTo(dialoguePos) > 1.0) {
            moveMs = System.currentTimeMillis() - startMs;
            moveTicks = ServerTickTimer.now() - startTick;
        }
    }

    // Devonian WatcherSplits: the " The Watcher " nametag stand sits one id above the zombie
    private static Zombie findWatcher() {
        if (ownWatcher != null && ownWatcher.isAlive()) return ownWatcher;
        ownWatcher = null;
        if (--scanCooldown > 0) return null;
        scanCooldown = 10;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        for (Entity e : com.teslamaps.utils.EntityCache.all()) {
            if (!(e instanceof ArmorStand) || !e.getName().getString().contains("The Watcher")) continue;
            if (mc.level.getEntity(e.getId() - 1) instanceof Zombie z) return ownWatcher = z;
            for (Zombie z : mc.level.getEntitiesOfClass(Zombie.class, e.getBoundingBox().inflate(2)))
                return ownWatcher = z;
        }
        return null;
    }

    private static void splits(long fullMs, long fullTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(
                "§b[TeslaMaps] §dDialogue§r: " + (dialogueMs > 0 ? time(dialogueMs, dialogueTicks) : "?")
                        + " §f| §dMove§r: " + (moveMs > 0 ? time(moveMs, moveTicks) : "?")
                        + " §f| §dBlood Clear§r: " + time(fullMs, fullTicks)));
    }

    private static boolean hudAllowed() {
        return TeslaMapsConfig.get().watcherHudAllClasses
                || "Mage".equals(com.teslamaps.player.PlayerTracker.getLocalClass());
    }

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.watcherHud || startMs == 0L) return;
        if (!hudAllowed()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        long now = System.currentTimeMillis(), nowT = ServerTickTimer.now();
        String dlg = dialogueMs > 0 ? time(dialogueMs, dialogueTicks) : time(now - startMs, nowT - startTick);
        String mv = moveMs > 0 ? time(moveMs, moveTicks) : "-";
        String camp = time(now - startMs, nowT - startTick);

        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(c.watcherHudX, c.watcherHudY);
        pose.scale(c.watcherHudScale, c.watcherHudScale);
        int y = 0;
        ctx.text(mc.font, "§c§lWatcher Stats:", 0, y, 0xFFFFFFFF); y += 10;
        ctx.text(mc.font, "§dDialogue§r: " + dlg, 0, y, 0xFFFFFFFF); y += 9;
        ctx.text(mc.font, "§dMove§r: " + mv, 0, y, 0xFFFFFFFF); y += 9;
        ctx.text(mc.font, "§dCamp§r: " + camp, 0, y, 0xFFFFFFFF);
        pose.popMatrix();
    }

    public static void reset() {
        startMs = 0L;
        dialogueMs = 0L;
        moveMs = 0L;
        startTick = dialogueTicks = moveTicks = 0L;
        graded = false;
        dialoguePos = null;
        ownWatcher = null;
    }

    private static String time(long ms, long ticks) {
        return switch (TeslaMapsConfig.get().watcherTimeFormat) {
            case 1 -> ticks + "t";
            case 2 -> fmt(ms) + " §7(" + ticks + "t)";
            default -> fmt(ms);
        };
    }

    private static String fmt(long ms) {
        return String.format("%.1fs", ms / 1000.0);
    }
}
