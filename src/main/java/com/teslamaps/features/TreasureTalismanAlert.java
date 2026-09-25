package com.teslamaps.features;

// WpcMod's treasure talisman drop alert
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.LoudSound;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

public class TreasureTalismanAlert {

    private static final Set<Integer> seen = new HashSet<>();
    private static Object lastLevel = null;
    private static int ticks = 0;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("TreasureTalismanAlert").section("Secrets & Waypoints").when(() -> cfg().treasureTalismanAlert)
                .tick(TreasureTalismanAlert::tick)
                .config("Secrets & Waypoints", b -> b.toggle("Treasure Talisman Alert",
                        () -> cfg().treasureTalismanAlert, v -> cfg().treasureTalismanAlert = v)
                        .desc("Title, chat line and sound when a Treasure Talisman drops in a dungeon"));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            seen.clear();
        }
        if (++ticks % 4 != 0 || !DungeonManager.isInDungeon()) return;
        for (ItemEntity item : EntityCache.byType(ItemEntity.class)) {
            if (seen.contains(item.getId()) || !isTalisman(item.getItem())) continue;
            seen.add(item.getId());
            alert();
        }
    }

    private static boolean isTalisman(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if ("TREASURE_TALISMAN".equals(ItemUtil.skyblockId(stack))) return true;
        return stack.getHoverName().getString().contains("Treasure Talisman");
    }

    private static void alert() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.gui.setTimes(0, 40, 10);
        mc.gui.setTitle(Component.literal("§6§lTreasure Talisman!"));
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §6A Treasure Talisman dropped!"));
        LoudSound.play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }
}
