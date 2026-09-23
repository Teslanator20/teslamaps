package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.map.RoomType;
import com.teslamaps.render.EspStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SecretClickHighlight {

    private static final class Flash {
        final AABB box;
        boolean locked;
        long until;
        Flash(AABB box, long until) { this.box = box; this.until = until; }
    }

    private static final List<Flash> flashes = new ArrayList<>();
    private static Flash lastClicked; // for the locked->red recolor
    private static final EspStyle LOCKED = new EspStyle();
    private static int lastItemId = -1;

    public static void onSecretClick(BlockPos pos) {
        com.teslamaps.dungeon.DungeonWaypoints.onSecret(pos, 0);
        if (!TeslaMapsConfig.get().secretClickHighlight || !DungeonManager.isInDungeon()) return;
        if (suppressed()) return; // not in puzzles / on the F7 Simon Says device
        Flash f = add(pos);
        f.locked = false;
        lastClicked = f;
    }

    /** Sound packet: Hypixel plays the bat hurt/death sound at 0.1 volume when a secret bat dies. */
    public static void onSound(SoundEvent sound, float volume, double x, double y, double z) {
        if (volume != 0.1f || (sound != SoundEvents.BAT_HURT && sound != SoundEvents.BAT_DEATH)) return;
        if (!Minecraft.getInstance().isSameThread()) return;
        if (!DungeonManager.isInDungeon() || DungeonManager.isInBossArea()) return;
        BlockPos pos = BlockPos.containing(x, y, z);
        com.teslamaps.dungeon.DungeonWaypoints.onSecret(pos, 5);
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.secretClickHighlight && c.secretClickBats) add(pos);
    }

    /** Item picked up or removed next to the player. */
    public static void onItemGone(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || mc.player == null || mc.level == null) return;
        if (!DungeonManager.isInDungeon() || DungeonManager.isInBossArea()) return;
        Entity e = mc.level.getEntity(entityId);
        if (!(e instanceof ItemEntity item) || e.distanceTo(mc.player) > 6) return;
        if (!SecretItemPickup.isDungeonDrop(item.getItem().getHoverName().getString())) return;
        if (entityId == lastItemId) return; // take + remove packet for the same item
        lastItemId = entityId;
        com.teslamaps.dungeon.DungeonWaypoints.onSecret(e.blockPosition(), 3);
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.secretClickHighlight && c.secretClickItems) add(e.blockPosition());
    }

    private static Flash add(BlockPos pos) {
        AABB cell = new AABB(pos);
        for (Flash f : flashes) {
            if (!f.box.intersects(cell)) continue;
            f.until = now() + duration();
            return f;
        }
        Flash f = new Flash(bounds(pos.immutable()), now() + duration());
        flashes.add(f);
        return f;
    }

    private static AABB bounds(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
            if (!shape.isEmpty()) return shape.bounds().move(pos);
        }
        return new AABB(pos);
    }

    private static long duration() {
        return (long) (TeslaMapsConfig.get().secretClickDuration * 1000);
    }

    public static void onChestLocked() {
        if (lastClicked == null) return;
        lastClicked.locked = true;
        lastClicked.until = now() + duration();
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.secretClickHighlight || flashes.isEmpty()) return;
        EspStyle style = EspStyle.of(c.secretClickStyle);
        LOCKED.color = c.secretClickLockedColor;
        LOCKED.mode = style.mode;
        LOCKED.lineWidth = style.lineWidth;
        LOCKED.tracer = false;
        LOCKED.throughWalls = style.throughWalls;
        long now = now();
        for (Iterator<Flash> it = flashes.iterator(); it.hasNext(); ) {
            Flash f = it.next();
            if (now >= f.until) { it.remove(); if (f == lastClicked) lastClicked = null; continue; }
            (f.locked ? LOCKED : style).drawBox(matrices, f.box, cameraPos);
        }
    }

    private static boolean suppressed() {
        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room != null && room.getType() == RoomType.PUZZLE) return true; // puzzle rooms
        // F7/M7 boss room (Simon Says / Arrows devices) is off the explored grid -> no current room
        if (room == null) {
            String floor = DungeonManager.getFloorName();
            if (floor != null && (floor.contains("F7") || floor.contains("M7"))) return true;
        }
        return false;
    }

    public static void reset() { flashes.clear(); lastClicked = null; }

    private static long now() { return System.currentTimeMillis(); }
}
