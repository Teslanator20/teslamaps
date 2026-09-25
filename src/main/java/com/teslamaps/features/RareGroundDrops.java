package com.teslamaps.features;

// Ported from Devonian's RareDungeonMobDropAlert.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.HashSet;
import java.util.Set;

/** Title when an Ice Spray Wand or Skeleton Master Chestplate lies on the ground in a dungeon. */
public final class RareGroundDrops {

    private static final Set<Integer> seen = new HashSet<>();
    private static ClientLevel lastLevel;
    private static int counter = 0;

    private RareGroundDrops() {}

    public static void register() {
        Feature.of("RareGroundDrops").section("Render").when(() -> TeslaMapsConfig.get().rareGroundDrops)
                .tick(RareGroundDrops::tick);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            seen.clear();
        }
        if (++counter % 5 != 0 || !DungeonManager.isInDungeon()) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            String name;
            if (e instanceof ArmorStand) {
                if (!e.hasCustomName() || e.getCustomName() == null) continue;
                name = e.getCustomName().getString();
            } else if (e instanceof ItemEntity item) {
                name = item.getItem().getHoverName().getString();
            } else continue;
            String title = match(name);
            if (title != null && seen.add(e.getId())) alert(mc, title);
        }
    }

    private static String match(String name) {
        if (name.length() < 14) return null;
        if (name.contains("Ice Spray Wand")) return "§bIce Spray Wand!";
        if (name.contains("Skeleton Master Chestplate")
                && (TeslaMapsConfig.get().rareGroundDropsSmAllFloors || DungeonManager.getCurrentFloor() == DungeonFloor.M7))
            return "§6Skeleton Master Chestplate!";
        return null;
    }

    private static void alert(Minecraft mc, String title) {
        if (mc.player == null) return;
        int ticks = Math.max(10, Math.round(TeslaMapsConfig.get().rareGroundDropsTime * 20));
        mc.gui.setTimes(0, ticks, 5);
        mc.gui.setTitle(Component.literal(title));
        mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 2.0f);
    }
}
