/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from BirdAddon
 * (https://github.com/BarefootBird/BirdAddon, BSD 3-Clause). See NOTICE.md for
 * attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.player.PlayerTracker;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class CustomTitles {
    private static final BlockPos WATCH_POS = new BlockPos(7, 77, 34); // last bear-trigger lantern
    private static final int SPAWN_DELAY = 68; // ~3.4s between spawn-start and the bear appearing
    private static final int TITLE_TICKS = 20; // how long a title stays

    // "[CROWD] <player>: <taunt>" lines Thorn's crowd shouts when you miss a bow shot
    private static final Pattern BOW_MISS = Pattern.compile(
        "^\\[CROWD] [^:]+: (Yeah!!! Keep dodging them Thorn!|[A-Za-z0-9_]+ missed the shot! No way!! Hahaha|"
        + "My goodness, [A-Za-z0-9_]+ really can't aim!!|Alright those humans are a joke, missing easy shots like that\\.\\.\\.|"
        + "[A-Za-z0-9_]+ has no thumbs!)$");
    private static final String BOW_PICKUP = "You picked up the Spirit Bow! Use it to attack Thorn!";

    private static int spawnCountdown = -1;

    public static void tick() {
        if (!inThornBoss()) {
            spawnCountdown = -1;
            return;
        }
        if (spawnCountdown > 0) {
            spawnCountdown--;
            if (spawnCountdown == 0 && TeslaMapsConfig.get().titleBearEvents) {
                setTitle("§5Bear Spawned");
            }
        }
    }

    public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        if (!TeslaMapsConfig.get().titleBearEvents) return;
        if (!pos.equals(WATCH_POS) || !inThornBoss()) return;

        if (oldState.getBlock() == Blocks.COAL_BLOCK && newState.getBlock() == Blocks.SEA_LANTERN) {
            setTitle("§cSTOP KILLING");
            spawnCountdown = SPAWN_DELAY;
        } else if (oldState.getBlock() == Blocks.SEA_LANTERN && newState.getBlock() == Blocks.COAL_BLOCK) {
            setTitle("§aResume Killing");
            spawnCountdown = -1;
        }
    }

    public static void onChatMessage(String rawText) {
        if (!inThornBoss()) return;
        String text = rawText.replaceAll("(?i)§[0-9A-FK-OR]", "");

        if (text.equals(BOW_PICKUP)) {
            if (TeslaMapsConfig.get().titleBowPickup) {
                setTitle(isTank() ? "§aBow Picked Up" : "§cBow Picked Up");
            }
            return;
        }
        if (TeslaMapsConfig.get().titleBowMiss && BOW_MISS.matcher(text).matches()) {
            setTitle("§cBow Missed");
        }
    }

    // cancel Hypixel's own bow/bear titles during the boss so our custom ones aren't fought over
    public static boolean shouldHideDefaultTitle() {
        return TeslaMapsConfig.get().hideDefaultTitles && inThornBoss();
    }

    private static void setTitle(String title) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setTimes(0, TITLE_TICKS, 5);
        mc.gui.setTitle(Component.literal(title));
    }

    private static boolean inThornBoss() {
        if (!DungeonManager.isInDungeon()) return false;
        DungeonFloor floor = DungeonManager.getCurrentFloor();
        if (floor == null || floor.getLevel() != 4) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        double dx = mc.player.getX() - (WATCH_POS.getX() + 0.5);
        double dz = mc.player.getZ() - (WATCH_POS.getZ() + 0.5);
        return dx * dx + dz * dz <= 200 * 200;
    }

    private static boolean isTank() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        String playerName = mc.player.getName().getString();
        for (PlayerTracker.DungeonPlayer dp : PlayerTracker.getPlayers()) {
            if (dp.getName().equals(playerName)) {
                return "Tank".equals(dp.getDungeonClass());
            }
        }
        return false;
    }
}
