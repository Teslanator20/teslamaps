package com.teslamaps.dungeon;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Dungeon run splits (ported from Odin, lean version: live HUD + chat output, no persistent PBs).
 * Chat-regex driven: on "Starting in 1 second." the split list for the current floor is built,
 * each split's time is captured when its message appears, and the splits are printed to chat
 * once the run is defeated.
 */
public class Splits {

    public static class Split {
        public final Pattern pattern;
        public final String name;   // may contain § color codes
        public long time = 0L;      // 0 = not reached yet
        Split(String regex, String name) {
            this.pattern = Pattern.compile(regex);
            this.name = name;
        }
    }

    private static final List<Split> active = new ArrayList<>();
    private static long startTime = 0L;
    private static boolean finished = false;

    // ---- common (all floors) ----
    private static final String MORT = "\\[NPC] Mort: Here, I found this map when I first entered the dungeon\\.|\\[NPC] Mort: Right-click the Orb for spells, and Left-click \\(or Drop\\) to use your Ultimate!";
    private static final String BLOOD_OPEN = "^\\[BOSS] The Watcher: (Congratulations, you made it through the Entrance\\.|Ah, you've finally arrived\\.|Ah, we meet again\\.\\.\\.|So you made it this far\\.\\.\\. interesting\\.|You've managed to scratch and claw your way here, eh\\?|I'm starting to get tired of seeing you around here\\.\\.\\.|Oh\\.\\. hello\\?|Things feel a little more roomy now, eh\\?)$|^The BLOOD DOOR has been opened!$";
    private static final String PORTAL_ENTRY = "\\[BOSS] The Watcher: You have proven yourself\\. You may pass\\.";
    private static final String TOTAL = "^\\s*☠ Defeated (.+) in 0?([\\dhms ]+?)\\s*(\\(NEW RECORD!\\))?$";

    private static void buildSplits() {
        active.clear();
        DungeonFloor floor = DungeonManager.getCurrentFloor();
        if (floor == null || floor == DungeonFloor.UNKNOWN) return;

        active.add(new Split(MORT, "§2Blood Open"));
        active.add(new Split(BLOOD_OPEN, "§bBlood Clear"));
        active.add(new Split(PORTAL_ENTRY, "§dPortal Entry"));

        switch (floor.getLevel()) {
            case 1 -> {
                active.add(new Split("\\[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable\\.", "§cBonzo's Sike"));
                active.add(new Split("\\[BOSS] Bonzo: Oh I'm dead!", "§4Cleared"));
            }
            case 2 -> {
                active.add(new Split("\\[BOSS] Scarf: This is where the journey ends for you, Adventurers\\.", "§cScarf's minions"));
                active.add(new Split("\\[BOSS] Scarf: Did you forget\\? I was taught by the best! Let's dance\\.", "§4Cleared"));
            }
            case 3 -> {
                active.add(new Split("\\[BOSS] The Professor: I was burdened with terrible news recently\\.\\.\\.", "§cThe Guardians"));
                active.add(new Split("\\[BOSS] The Professor: Oh\\? You found my Guardians' one weakness\\?", "§aThe Professor"));
                active.add(new Split("\\[BOSS] The Professor: What\\?! My Guardian power is unbeatable!", "§4Cleared"));
            }
            case 4 -> active.add(new Split("\\[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!", "§4Cleared"));
            case 5 -> active.add(new Split("\\[BOSS] Livid: Welcome, you've arrived right on time\\. I am Livid, the Master of Shadows\\.", "§4Cleared"));
            case 6 -> {
                active.add(new Split("\\[BOSS] Sadan: So you made it all the way here\\.\\.\\. Now you wish to defy me\\? Sadan\\?!", "§cTerracottas"));
                active.add(new Split("\\[BOSS] Sadan: ENOUGH!", "§aGiants"));
                active.add(new Split("\\[BOSS] Sadan: You did it\\. I understand now, you have earned my respect\\.", "§4Cleared"));
            }
            case 7 -> {
                active.add(new Split("\\[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!", "§5Maxor"));
                active.add(new Split("\\[BOSS] Storm: Pathetic Maxor, just like expected\\.", "§3Storm"));
                active.add(new Split("\\[BOSS] Goldor: Who dares trespass into my domain\\?", "§6Terminals"));
                active.add(new Split("The Core entrance is opening!", "§7Goldor"));
                active.add(new Split("\\[BOSS] Necron: You went further than any human before, congratulations\\.", "§cNecron"));
                active.add(new Split("\\[BOSS] Necron: All this, for nothing\\.\\.\\.", "§4Cleared"));
            }
            default -> { }
        }

        active.add(new Split(TOTAL, "§1Total"));
    }

    public static void onChatMessage(String message) {
        if (!TeslaMapsConfig.get().splitsEnabled) return;

        if (message.equals("Starting in 1 second.")) {
            buildSplits();
            startTime = System.currentTimeMillis();
            finished = false;
            return;
        }

        if (active.isEmpty() || finished) return;

        for (Split split : active) {
            if (split.time != 0L) continue;
            if (split.pattern.matcher(message).matches()) {
                split.time = System.currentTimeMillis();
                if (split == active.get(active.size() - 1)) {
                    finished = true;
                    sendSplits();
                }
                break;
            }
        }
    }

    private static void sendSplits() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §7Splits:"));
        for (Split split : active) {
            if (split.time == 0L) continue;
            mc.player.sendSystemMessage(Component.literal(split.name + " §7- §f" + formatTime(split.time - startTime)));
        }
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.splitsEnabled || active.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int nameCol = 0;
        for (Split split : active) nameCol = Math.max(nameCol, mc.font.width(split.name));
        nameCol += 6;

        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(config.splitsX, config.splitsY);
        pose.scale(config.splitsScale, config.splitsScale);

        long now = System.currentTimeMillis();
        boolean runningShown = false;
        int y = 0;
        for (Split split : active) {
            String timeStr;
            if (split.time != 0L) {
                timeStr = formatTime(split.time - startTime);
            } else if (!runningShown && !finished) {
                timeStr = "§7" + formatTime(now - startTime);
                runningShown = true;
            } else {
                timeStr = "§8-";
            }
            context.text(mc.font, split.name, 0, y, 0xFFFFFFFF);
            context.text(mc.font, timeStr, nameCol, y, 0xFFFFFFFF);
            y += 9;
        }
        pose.popMatrix();
    }

    /** Width of the rendered HUD (for the edit-screen hitbox). */
    public static int hudWidth() {
        Minecraft mc = Minecraft.getInstance();
        int nameCol = 0;
        for (Split split : active) nameCol = Math.max(nameCol, mc.font.width(split.name));
        return nameCol + 6 + mc.font.width("00:00.0");
    }

    public static int hudHeight() {
        return Math.max(1, active.size()) * 9;
    }

    public static boolean hasSplits() {
        return !active.isEmpty();
    }

    public static void reset() {
        active.clear();
        startTime = 0L;
        finished = false;
    }

    private static String formatTime(long ms) {
        long totalSec = ms / 1000;
        long tenths = (ms % 1000) / 100;
        long minutes = totalSec / 60;
        long seconds = totalSec % 60;
        if (minutes >= 60) {
            long hours = minutes / 60;
            minutes %= 60;
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d.%d", minutes, seconds, tenths);
    }
}
