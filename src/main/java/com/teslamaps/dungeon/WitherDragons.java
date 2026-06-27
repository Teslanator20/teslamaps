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
package com.teslamaps.dungeon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WitherDragons {

    public enum State { SPAWNING, ALIVE, DEAD }

    public enum Dragon {
        RED("Red", 'c', 0xFFFF5555, new BlockPos(27, 14, 59), new BlockPos(32, 22, 59),
                new AABB(14.5, 13, 45.5, 39.5, 28, 70.5), 24, 30, 56, 62),
        ORANGE("Orange", '6', 0xFFFFAA00, new BlockPos(85, 14, 56), new BlockPos(80, 23, 56),
                new AABB(72, 8, 47, 102, 28, 77), 82, 88, 53, 59),
        GREEN("Green", 'a', 0xFF55FF55, new BlockPos(27, 14, 94), new BlockPos(32, 23, 94),
                new AABB(7, 8, 80, 37, 28, 110), 23, 29, 91, 97),
        BLUE("Blue", 'b', 0xFF55FFFF, new BlockPos(84, 14, 94), new BlockPos(79, 23, 94),
                new AABB(71.5, 13, 82.5, 96.5, 26, 107.5), 82, 88, 91, 97),
        PURPLE("Purple", '5', 0xFFAA00AA, new BlockPos(56, 14, 125), new BlockPos(56, 22, 120),
                new AABB(45.5, 13, 113.5, 68.5, 23, 136.5), 53, 59, 122, 128);

        public final String dispName;
        public final char colorCode;
        public final int colorArgb;
        public final BlockPos spawnPos, statuePos;
        public final AABB box;
        public final double xMin, xMax, zMin, zMax;
        public State state = State.DEAD;
        public int timeToSpawn = 0;
        public int timesSpawned = 0;

        Dragon(String dispName, char colorCode, int colorArgb, BlockPos spawnPos, BlockPos statuePos, AABB box,
               double xMin, double xMax, double zMin, double zMax) {
            this.dispName = dispName; this.colorCode = colorCode; this.colorArgb = colorArgb;
            this.spawnPos = spawnPos; this.statuePos = statuePos; this.box = box;
            this.xMin = xMin; this.xMax = xMax; this.zMin = zMin; this.zMax = zMax;
        }
    }

    // Odin's two priority orderings. Berserk/Mage use DRAGON_LIST, the other classes use it reversed.
    private static final Dragon[] DEFAULT_ORDER = {Dragon.RED, Dragon.ORANGE, Dragon.BLUE, Dragon.PURPLE, Dragon.GREEN};
    private static final Dragon[] DRAGON_LIST = {Dragon.ORANGE, Dragon.GREEN, Dragon.RED, Dragon.BLUE, Dragon.PURPLE};

    private static boolean dragonsPhase = false;
    private static Dragon priorityDragon = null;
    private static int powerLevel = 0;
    private static int timeLevel = 0;
    private static final Pattern WK_REGEX = Pattern.compile("\\[BOSS] Wither King:");
    private static final Pattern POWER_REGEX = Pattern.compile("Blessing of Power (X{0,3}(?:IX|IV|V?I{0,3}))");
    private static final Pattern TIME_REGEX = Pattern.compile("Blessing of Time (V)");

    private static boolean active() {
        DungeonFloor f = DungeonManager.getCurrentFloor();
        return TeslaMapsConfig.get().witherDragons && DungeonManager.isInDungeon()
                && f != null && f.getLevel() == 7 && dragonsPhase;
    }

    public static void onParticlePacket(ClientboundLevelParticlesPacket p) {
        if (!active()) return;
        if (p.getCount() != 20 || p.getY() != 19.0 || p.getParticle().getType() != ParticleTypes.FLAME
                || p.getXDist() != 2f || p.getYDist() != 3f || p.getZDist() != 2f || p.getMaxSpeed() != 0f
                || p.getX() % 1 != 0.0 || p.getZ() % 1 != 0.0) return;

        List<Dragon> spawning = new ArrayList<>();
        int spawnedCount = 0;
        for (Dragon d : Dragon.values()) {
            spawnedCount += d.timesSpawned;
            if (d.state == State.SPAWNING) {
                if (!spawning.contains(d)) spawning.add(d);
                continue;
            }
            if (d.state != State.DEAD) continue;
            if (p.getX() >= d.xMin && p.getX() <= d.xMax && p.getZ() >= d.zMin && p.getZ() <= d.zMax) {
                d.state = State.SPAWNING;
                d.timeToSpawn = 100;
                spawning.add(d);
                if (TeslaMapsConfig.get().witherDragonMsg) msg("§" + d.colorCode + d.dispName + " §fdragon is spawning.");
                if (TeslaMapsConfig.get().witherDragonTitle) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.gui.setTimes(0, 30, 5);
                        mc.gui.setTitle(Component.literal("§" + d.colorCode + d.dispName + " spawning!"));
                    }
                }
            }
        }

        // Split: once 2 dragons are up (or 2+ have spawned this run), pick the one YOU should take.
        if (!spawning.isEmpty() && (spawning.size() == 2 || spawnedCount >= 2) && priorityDragon == null) {
            priorityDragon = findPriority(spawning);
            if (TeslaMapsConfig.get().witherDragonPriority) {
                String list = spawning.stream().map(d -> "§" + d.colorCode + d.dispName).collect(Collectors.joining(", "));
                msg(list + " §r-> §" + priorityDragon.colorCode + priorityDragon.dispName + " §7is your priority dragon!");
            }
        }
    }

    private static Dragon findPriority(List<Dragon> spawning) {
        if (!TeslaMapsConfig.get().witherDragonPriority) {
            Dragon best = spawning.get(0);
            for (Dragon d : spawning) if (indexOf(DEFAULT_ORDER, d) < indexOf(DEFAULT_ORDER, best)) best = d;
            return best;
        }
        return sortPriority(spawning);
    }

    private static Dragon sortPriority(List<Dragon> spawning) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        double totalPower = powerLevel * (c.witherDragonPaulBuff ? 1.25 : 1.0) + (timeLevel > 0 ? 2.5 : 0.0);
        String playerClass = localClass();
        boolean hasPurple = spawning.contains(Dragon.PURPLE);

        Dragon[] priorityList;
        if (totalPower >= c.witherDragonNormalPower || (hasPurple && totalPower >= c.witherDragonEasyPower)) {
            boolean bersOrMage = "Berserk".equals(playerClass) || "Mage".equals(playerClass);
            priorityList = bersOrMage ? DRAGON_LIST : reversed(DRAGON_LIST);
        } else {
            priorityList = DEFAULT_ORDER;
        }

        final Dragon[] order = priorityList;
        spawning.sort((a, b) -> indexOf(order, a) - indexOf(order, b));

        if (totalPower >= c.witherDragonEasyPower) {
            boolean splitTarget = hasPurple || c.witherDragonSoloDebuffAll;
            if ((c.witherDragonSoloDebuff == 1 && "Tank".equals(playerClass) && splitTarget)
                    || ("Healer".equals(playerClass) && splitTarget)) {
                spawning.sort((a, b) -> indexOf(order, b) - indexOf(order, a));
            }
        }
        return spawning.get(0);
    }

    private static int indexOf(Dragon[] arr, Dragon d) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == d) return i;
        return -1;
    }

    private static Dragon[] reversed(Dragon[] arr) {
        Dragon[] out = new Dragon[arr.length];
        for (int i = 0; i < arr.length; i++) out[i] = arr[arr.length - 1 - i];
        return out;
    }

    private static String localClass() {
        return PlayerTracker.getLocalClass();
    }

    public static void onTabFooter(String footer) {
        if (!DungeonManager.isInDungeon()) return;
        Matcher pm = POWER_REGEX.matcher(footer);
        if (pm.find()) powerLevel = romanToInt(pm.group(1));
        Matcher tm = TIME_REGEX.matcher(footer);
        if (tm.find()) timeLevel = romanToInt(tm.group(1));
    }

    public static void debugDump() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        double totalPower = powerLevel * (c.witherDragonPaulBuff ? 1.25 : 1.0) + (timeLevel > 0 ? 2.5 : 0.0);
        String you = localClass();
        msg("§6=== Dragon Priority Debug ===");
        msg("§7In dungeon: §f" + DungeonManager.isInDungeon() + " §7| dragonsPhase: §f" + dragonsPhase + " §7| active: §f" + active());
        msg("§7Power: §a" + powerLevel + " §7Time: §a" + timeLevel + " §7Paul: §f" + c.witherDragonPaulBuff
                + " §7-> totalPower: §6" + totalPower);
        msg("§7Your class: §" + classColor(you) + (you == null ? "Unknown" : you));

        msg("§7Party classes:");
        for (PlayerTracker.DungeonPlayer p : PlayerTracker.getPlayers()) {
            String cl = p.getDungeonClass();
            msg("  §f" + p.getName() + " §7- §" + classColor(cl) + cl);
        }

        boolean wouldSplit = totalPower >= c.witherDragonNormalPower
                || (totalPower >= c.witherDragonEasyPower);
        msg("§7Thresholds: normal §f" + c.witherDragonNormalPower + " §7easy §f" + c.witherDragonEasyPower
                + " §7-> would split (this power): §f" + wouldSplit);

        boolean bersOrMage = "Berserk".equals(you) || "Mage".equals(you);
        Dragon[] order = !wouldSplit ? DEFAULT_ORDER : (bersOrMage ? DRAGON_LIST : reversed(DRAGON_LIST));
        StringBuilder sb = new StringBuilder();
        for (Dragon d : order) sb.append("§").append(d.colorCode).append(d.dispName.charAt(0)).append(" ");
        msg("§7Order for your class: " + sb);

        msg("§7Dragon states:");
        for (Dragon d : Dragon.values()) {
            msg("  §" + d.colorCode + d.dispName + " §7state=§f" + d.state + " §7spawned=§f" + d.timesSpawned
                    + " §7timer=§f" + d.timeToSpawn);
        }
        msg("§7Current priority dragon: " + (priorityDragon == null ? "§fnone"
                : "§" + priorityDragon.colorCode + priorityDragon.dispName));
    }

    private static char classColor(String cl) {
        if (cl == null) return 'f';
        return switch (cl) {
            case "Archer" -> '6'; case "Berserk" -> '4'; case "Healer" -> 'd';
            case "Mage" -> 'b'; case "Tank" -> '2'; default -> 'f';
        };
    }

    private static int romanToInt(String s) {
        int total = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = switch (s.charAt(i)) {
                case 'I' -> 1; case 'V' -> 5; case 'X' -> 10;
                case 'L' -> 50; case 'C' -> 100; default -> 0;
            };
            if (v < prev) total -= v; else { total += v; prev = v; }
        }
        return total;
    }

    public static void tick() {
        if (!DungeonManager.isInDungeon()) { reset(); return; }
        if (!active()) return;
        for (Dragon d : Dragon.values()) {
            if (d.timeToSpawn > 0) {
                d.timeToSpawn--;
            } else if (d.state == State.SPAWNING) {
                d.state = State.ALIVE;
                d.timesSpawned++;
                if (TeslaMapsConfig.get().witherDragonMsg) msg("§" + d.colorCode + d.dispName + " §fdragon spawned.");
            }
        }
    }

    public static void onChatMessage(String message) {
        message = message.replaceAll("(?i)§[0-9A-FK-OR]", "");
        if (WK_REGEX.matcher(message).find() || message.equals("[BOSS] Necron: All this, for nothing...")) {
            dragonsPhase = true;
        }
    }

    public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        if (!active() || !newState.isAir()) return;
        for (Dragon d : Dragon.values()) {
            if (d.statuePos.equals(pos)) {
                d.state = State.DEAD; d.timeToSpawn = 0;
                if (priorityDragon == d) priorityDragon = null;
                break;
            }
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!active()) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        for (Dragon d : Dragon.values()) {
            if (c.witherDragonTimer && d.timeToSpawn > 0) {
                String time = timerColor(d.timeToSpawn) + String.format("%.1fs", d.timeToSpawn / 20f);
                ESPRenderer.drawText(matrices, "§" + d.colorCode + d.dispName.charAt(0) + ": " + time,
                        new Vec3(d.spawnPos.getX() + 0.5, d.spawnPos.getY() + 0.5, d.spawnPos.getZ() + 0.5), 2f, cameraPos);
            }
            if (c.witherDragonBoxes && d.state != State.DEAD) {
                ESPRenderer.drawBoxOutline(matrices, d.box, d.colorArgb, 3f, cameraPos, true);
            }
        }
        if (c.witherDragonPriority && c.witherDragonPriorityTracer
                && priorityDragon != null && priorityDragon.state == State.SPAWNING) {
            ESPRenderer.drawTracerFromCamera(matrices,
                    new Vec3(priorityDragon.spawnPos.getX() + 0.5, priorityDragon.spawnPos.getY() + 0.5,
                            priorityDragon.spawnPos.getZ() + 0.5),
                    priorityDragon.colorArgb, cameraPos);
        }
    }

    private static String timerColor(int t) {
        return t <= 20 ? "§c" : t <= 60 ? "§e" : "§a";
    }

    public static void reset() {
        dragonsPhase = false;
        priorityDragon = null;
        powerLevel = 0;
        timeLevel = 0;
        for (Dragon d : Dragon.values()) { d.state = State.DEAD; d.timeToSpawn = 0; d.timesSpawned = 0; }
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(s));
    }
}
