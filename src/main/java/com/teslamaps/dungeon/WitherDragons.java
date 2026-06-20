package com.teslamaps.dungeon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.regex.Pattern;

/**
 * M7 Wither Dragons (ported from Odin) — STAGE 1: spawn detection (via the spawn FLAME particle),
 * a spawn countdown timer, spawn boxes, and spawn alerts. Death is detected when a dragon's statue
 * block breaks. (Entity health, ice-spray, priority and stack-aimer are later stages.)
 */
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

    private static boolean dragonsPhase = false;
    private static final Pattern WK_REGEX = Pattern.compile("\\[BOSS] Wither King:");

    private static boolean active() {
        DungeonFloor f = DungeonManager.getCurrentFloor();
        return TeslaMapsConfig.get().witherDragons && DungeonManager.isInDungeon()
                && f != null && f.getLevel() == 7 && dragonsPhase;
    }

    /** Spawn FLAME particle (count 20, y=19, integer x/z in a dragon's range) -> that dragon is spawning. */
    public static void onParticlePacket(ClientboundLevelParticlesPacket p) {
        if (!active()) return;
        if (p.getCount() != 20 || p.getY() != 19.0 || p.getParticle().getType() != ParticleTypes.FLAME
                || p.getXDist() != 2f || p.getYDist() != 3f || p.getZDist() != 2f || p.getMaxSpeed() != 0f
                || p.getX() % 1 != 0.0 || p.getZ() % 1 != 0.0) return;

        for (Dragon d : Dragon.values()) {
            if (d.state != State.DEAD) continue;
            if (p.getX() >= d.xMin && p.getX() <= d.xMax && p.getZ() >= d.zMin && p.getZ() <= d.zMax) {
                d.state = State.SPAWNING;
                d.timeToSpawn = 100;
                if (TeslaMapsConfig.get().witherDragonMsg) msg("§" + d.colorCode + d.dispName + " §fdragon is spawning.");
                if (TeslaMapsConfig.get().witherDragonTitle) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.gui.setTimes(0, 30, 5);
                        mc.gui.setTitle(Component.literal("§" + d.colorCode + d.dispName + " spawning!"));
                    }
                }
                break;
            }
        }
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
        // The dragons phase begins once the Wither King appears (after Necron).
        if (WK_REGEX.matcher(message).find() || message.equals("[BOSS] Necron: All this, for nothing...")) {
            dragonsPhase = true;
        }
    }

    /** A dragon's statue block breaking = that dragon died. */
    public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        if (!active() || !newState.isAir()) return;
        for (Dragon d : Dragon.values()) {
            if (d.statuePos.equals(pos)) { d.state = State.DEAD; d.timeToSpawn = 0; break; }
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
    }

    private static String timerColor(int t) {
        return t <= 20 ? "§c" : t <= 60 ? "§e" : "§a";
    }

    public static void reset() {
        dragonsPhase = false;
        for (Dragon d : Dragon.values()) { d.state = State.DEAD; d.timeToSpawn = 0; d.timesSpawned = 0; }
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(s));
    }
}
