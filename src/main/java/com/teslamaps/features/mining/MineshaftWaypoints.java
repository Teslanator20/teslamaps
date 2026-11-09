package com.teslamaps.features.mining;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.esp.CorpseFinder;
import com.teslamaps.features.Announce;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.PartyTracker;
import com.teslamaps.utils.ScoreboardUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Ported from SkyHanni's MineshaftWaypoints.kt: entrance + ladder bottom from the spawn position and facing
public final class MineshaftWaypoints {

    private static final int BLOCKS_FORWARD = 7, BLOCKS_DOWN = -15;

    private static Object spawnLevel;
    private static BlockPos spawn;
    private static float spawnYaw;
    private static Object builtFor;
    private static BlockPos entrance, ladder;
    private static boolean keyDown;
    private static long lastShare;
    private static Object joinLevel;
    private static BlockPos joinPos;
    private static long joinedAt;
    private static int ticks;
    private static String exitLabel = "§aExit";

    private MineshaftWaypoints() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("MineshaftWaypoints").section("Mining").when(() -> cfg().mineshaftWaypoints || cfg().mineshaftExitWaypoint)
                .tick(MineshaftWaypoints::tick)
                .world(MineshaftWaypoints::render);
    }

    /** handleMovePlayer HEAD: the first absolute teleport in a world is the mineshaft spawn. */
    public static void onPositionPacket(ClientboundPlayerPositionPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || !(cfg().mineshaftWaypoints || cfg().mineshaftExitWaypoint) || mc.level == null || spawnLevel == mc.level) return;
        if (!packet.relatives().isEmpty()) return;
        spawnLevel = mc.level;
        spawn = BlockPos.containing(packet.change().position()).below();
        spawnYaw = packet.change().yRot();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean slow = ++ticks % 10 == 0;
        if (joinLevel != mc.level) {
            joinLevel = mc.level;
            joinPos = mc.player.blockPosition().below();
            joinedAt = System.currentTimeMillis();
        }
        if (builtFor != mc.level) {
            entrance = ladder = null;
            if (slow && inMineshaft()) {
                if (spawnLevel == mc.level && spawn != null) {
                    builtFor = mc.level;
                    Direction d = Direction.fromYRot(spawnYaw);
                    int dx = d.getStepX(), dz = d.getStepZ();
                    entrance = spawn;
                    ladder = spawn.offset(dx * BLOCKS_FORWARD - dz * 2, BLOCKS_DOWN, dz * BLOCKS_FORWARD + dx * 2);
                } else if (System.currentTimeMillis() - joinedAt < 15_000) {
                    builtFor = mc.level;
                    entrance = joinPos;
                }
            }
        }
        if (entrance != null && cfg().mineshaftExitWaypoint && slow) {
            exitLabel = "§aExit §7(" + Math.round(Math.sqrt(mc.player.distanceToSqr(Vec3.atCenterOf(entrance)))) + "m)";
        }

        if (!cfg().mineshaftWaypoints) return;
        boolean down = mc.gui.screen() == null && cfg().mineshaftShareKey != -1 && KeyNames.isDown(cfg().mineshaftShareKey);
        if (down && !keyDown) shareNearest();
        keyDown = down;
    }

    private static boolean inMineshaft() {
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            if (ScoreboardUtils.cleanLine(line).contains("Mineshaft")) return true;
        }
        return false;
    }

    private static void shareNearest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || System.currentTimeMillis() - lastShare < 500) return;
        BlockPos me = mc.player.blockPosition();
        String line = null;
        double best = 25;
        if (entrance != null && showEntrance() && entrance.distSqr(me) <= best) {
            best = entrance.distSqr(me);
            line = chatLine(entrance, "Entrance");
        }
        if (ladder != null && cfg().mineshaftWpLadder && ladder.distSqr(me) <= best) {
            best = ladder.distSqr(me);
            line = chatLine(ladder, "Ladder");
        }
        if (cfg().mineshaftWpCorpses && CorpseFinder.nearestDistSq(me) <= best) {
            String corpse = CorpseFinder.nearestShareLine(me, 5);
            if (corpse != null) line = corpse;
        }
        if (line == null) return;
        lastShare = System.currentTimeMillis();
        Announce.send(PartyTracker.inParty() ? Announce.PARTY : Announce.ALL, line);
    }

    private static String chatLine(BlockPos p, String type) {
        return "x: " + p.getX() + ", y: " + p.getY() + ", z: " + p.getZ() + " | (" + type + ")";
    }

    private static boolean showEntrance() {
        return cfg().mineshaftExitWaypoint || cfg().mineshaftWaypoints && cfg().mineshaftWpEntrance;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (entrance != null && showEntrance()) {
            if (cfg().mineshaftExitWaypoint) draw(pose, cam, entrance, exitLabel, 0x7020CC00);
            else draw(pose, cam, entrance, "§eEntrance", 0x6655FF55);
        }
        if (ladder != null && cfg().mineshaftWaypoints && cfg().mineshaftWpLadder) draw(pose, cam, ladder, "§eLadder", 0x66FFAA00);
    }

    private static void draw(PoseStack pose, Vec3 cam, BlockPos p, String label, int color) {
        ESPRenderer.drawFilledBox(pose, new AABB(p), color, cam, true);
        ESPRenderer.drawText(pose, label, new Vec3(p.getX() + 0.5, p.getY() + 1.5, p.getZ() + 0.5), 1f, cam);
    }
}
