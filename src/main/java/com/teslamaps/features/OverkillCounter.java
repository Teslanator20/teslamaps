// Ported from BirdAddon (BSD-3-Clause), see NOTICE.md
package com.teslamaps.features;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.M4Mobs;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// F4/M4 Thorn overkill counter — once the lantern ring is full (bear spawning), every further animal
// kill is "overkill". Ported from BirdAddon's OverkillDisplay (BSD-3).
public class OverkillCounter {
    public static final String SAMPLE_TEXT = "Overkill: 00";
    private static final BlockPos LAST_LANTERN = new BlockPos(7, 77, 34); // last ring block to light

    private static boolean ringFull = false; // overkill window open
    private static int overkill, oCows, oSheep, oChickens, oBats, oWolves, oRabbits;

    public static void onChatMessage(String message) {
        String c = message.replaceAll("(?i)§[0-9A-FK-OR]", "");
        if (c.contains("Welcome Adventurers! I am Thorn")) { resetCounts(); setWindow(false, "boss start"); }
        else if (c.contains("A Spirit Bear has appeared!")) setWindow(true, "bear appeared");
        else if (c.contains("The Spirit Bow has dropped!")) setWindow(false, "bear killed");
    }

    public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        if (!M4Mobs.inM4Boss() || !pos.equals(LAST_LANTERN)) return;
        if (oldState.getBlock() == Blocks.COAL_BLOCK && newState.getBlock() == Blocks.SEA_LANTERN) setWindow(true, "ring full");
        else if (oldState.getBlock() == Blocks.SEA_LANTERN && newState.getBlock() == Blocks.COAL_BLOCK) setWindow(false, "ring reset");
    }

    public static void onEntityEvent(Entity entity, int eventId) {
        if (eventId != 3 || entity == null || !ringFull || !M4Mobs.inM4Boss()) return; // 3 = death
        String type = null;
        if (entity instanceof Wolf) { oWolves++; type = "wolf"; }
        else if (entity instanceof Cow) { oCows++; type = "cow"; }
        else if (entity instanceof Rabbit) { oRabbits++; type = "rabbit"; }
        else if (entity instanceof Sheep) { oSheep++; type = "sheep"; }
        else if (entity instanceof Chicken) { oChickens++; type = "chicken"; }
        else if (entity instanceof Bat) { oBats++; type = "bat"; }
        if (type == null) return;
        overkill++;
        if (TeslaMapsConfig.get().debugMode) TeslaMaps.debug("[OverkillDbg] +{} -> total {}", type, overkill);
    }

    private static void setWindow(boolean open, String why) {
        if (ringFull == open) return;
        ringFull = open;
        if (TeslaMapsConfig.get().debugMode) TeslaMaps.debug("[OverkillDbg] window {} ({})", open ? "OPEN" : "closed", why);
    }

    public static void tick() {
        if (!DungeonManager.isInDungeon()) { resetCounts(); ringFull = false; }
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        if (!TeslaMapsConfig.get().overkillCounter || !M4Mobs.inM4Boss()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        draw(context, mc, TeslaMapsConfig.get().overkillX, TeslaMapsConfig.get().overkillY,
                TeslaMapsConfig.get().overkillScale, false);
    }

    /** Used by the HUD editor preview (example = sample values). */
    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        draw(context, mc, x, y, scale, true);
    }

    private static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale, boolean example) {
        boolean hideZero = TeslaMapsConfig.get().overkillHideZero;
        int ov = example ? 0 : overkill;
        int[] vals = example ? new int[]{0, 0, 0, 0, 0, 0} : new int[]{oCows, oSheep, oChickens, oBats, oWolves, oRabbits};
        String[] names = {"Cows: ", "Sheep: ", "Chickens: ", "Bats: ", "Wolves: ", "Rabbits: "};
        int[] colors = {0xFFA0764C, 0xFFFFFFFF, 0xFFFFFF55, 0xFF888888, 0xFFCCCCCC, 0xFFD2B48C};

        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int row = 0;
        context.text(mc.font, "Overkill: " + ov, 0, row, 0xFFFFFFFF);
        row += 9;
        for (int i = 0; i < names.length; i++) {
            if (hideZero && vals[i] == 0) continue;
            context.text(mc.font, names[i] + vals[i], 0, row, colors[i]);
            row += 9;
        }
        pose.popMatrix();
    }

    private static void resetCounts() {
        overkill = oCows = oSheep = oChickens = oBats = oWolves = oRabbits = 0;
    }

    public static void reset() { resetCounts(); ringFull = false; }
}
