package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.DungeonScore;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.map.RoomType;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.scanner.ComponentGrid;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ColorPortal {

    private static AABB portal = null;
    private static int scanCd = 0;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!TeslaMapsConfig.get().colorPortal || mc.player == null || mc.level == null
                || !DungeonManager.isInDungeon() || DungeonManager.isInBossArea()) {
            portal = null;
            return;
        }
        if (portal != null || --scanCd > 0) return;
        scanCd = 20;

        DungeonRoom blood = null;
        for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
            if (room.getType() == RoomType.BLOOD) { blood = room; break; }
        }
        if (blood != null) {
            for (int[] c : blood.getComponents()) {
                int[] corner = ComponentGrid.gridToWorldCorner(c[0], c[1]);
                AABB box = scan(mc, corner[0], 60, corner[1], ComponentGrid.ROOM_SIZE, 20, ComponentGrid.ROOM_SIZE);
                if (box != null) { portal = box; return; }
            }
            return;
        }
        BlockPos p = mc.player.blockPosition();
        portal = scan(mc, p.getX() - 10, p.getY() - 6, p.getZ() - 10, 21, 13, 21);
    }

    // one box over every portal block, found once per run
    private static AABB scan(Minecraft mc, int x0, int y0, int z0, int sx, int sy, int sz) {
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        int minX = Integer.MAX_VALUE, minY = 0, minZ = 0, maxX = 0, maxY = 0, maxZ = 0;
        for (int x = x0; x < x0 + sx; x++)
            for (int z = z0; z < z0 + sz; z++) {
                if (!mc.level.hasChunkAt(bp.set(x, y0, z))) continue;
                for (int y = y0; y < y0 + sy; y++) {
                    if (!mc.level.getBlockState(bp.set(x, y, z)).is(Blocks.NETHER_PORTAL)) continue;
                    if (minX == Integer.MAX_VALUE) {
                        minX = maxX = x; minY = maxY = y; minZ = maxZ = z;
                    } else {
                        minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                        maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
                    }
                }
            }
        return minX == Integer.MAX_VALUE ? null : new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().colorPortal || portal == null) return;
        ESPRenderer.drawFilledBox(matrices, portal, colorForScore(DungeonScore.getScore()), cameraPos);
    }

    private static int colorForScore(int score) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (score < 270) return TeslaMapsConfig.parseColor(c.colorPortalLow);
        if (score < 300) return TeslaMapsConfig.parseColor(c.colorPortalS);
        return TeslaMapsConfig.parseColor(c.colorPortalSPlus);
    }
}
