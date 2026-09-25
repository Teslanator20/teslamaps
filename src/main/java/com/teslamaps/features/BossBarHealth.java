package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.mixin.LerpingBossEventAccessor;
import java.util.Locale;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;

public class BossBarHealth {

    public static Component onRender(LerpingBossEvent event, Component originalName) {
        if (!TeslaMapsConfig.get().bossbarHealth) return originalName;
        if (!DungeonManager.isInDungeon()) return originalName;
        Component interest = SadanFeatures.interestName(event);
        if (interest != null) return interest;

        float maxHealth = getMaxHealth(originalName.getString().replaceAll("(?i)§[0-9A-FK-OR]", "").trim());
        if (maxHealth <= 0) return originalName;

        float percent = ((LerpingBossEventAccessor) event).teslamaps$getTargetPercent();
        float currentHealth = Math.round(percent * maxHealth);

        return originalName.copy().append(Component.literal(
                " §r§8- §a" + formatHealth(currentHealth) + "§7/§a" + formatHealth(maxHealth) + "§c❤"));
    }

    private static float getMaxHealth(String name) {
        String floorName = DungeonManager.getFloorName();
        boolean master = floorName != null && floorName.startsWith("M");
        DungeonFloor floor = DungeonManager.getCurrentFloor();

        return switch (name) {
            case "The Watcher" -> 12f + (floor != null ? floor.getLevel() : 0);
            case "Thorn" -> master ? 6f : 4f;
            case "Maxor" -> master ? 800_000_000f : 100_000_000f;
            case "Storm" -> master ? 1_000_000_000f : 400_000_000f;
            case "Goldor" -> master ? 1_200_000_000f : 750_000_000f;
            case "Necron" -> master ? 1_400_000_000f : 1_000_000_000f;
            default -> 0f;
        };
    }

    private static String formatHealth(float health) {
        if (health >= 1_000_000_000f) {
            float h = health / 1_000_000_000f;
            if (h % 1.0f == 0f) return (int) h + "B";
            return String.format(Locale.US, "%.1fB", h);
        }
        if (health >= 1_000_000f) return (int) (health / 1_000_000f) + "M";
        if (health >= 1000f) return (int) (health / 1000f) + "k";
        return String.valueOf((int) health);
    }
}
